package com.example.mukonongomap.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.mukonongomap.BuildConfig;
import com.example.mukonongomap.performance.ConfirmDeliveryJourney;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends "Confirm Delivery" to the server (POST to CONFIRM_DELIVERY_URL).
 *
 * Up to 3 attempts with back-off (1 s, 2 s). If all fail - the usual case on a
 * weak 2G link - the confirmation is saved in an on-device outbox, so no
 * delivery is lost, and the outcome is QUEUED_OFFLINE.
 */
public final class DeliveryRepository {

    public interface Callback {
        /** Called before each retry (main thread). */
        void onRetry();

        /** Called once at the end (main thread). */
        void onComplete(ConfirmDeliveryJourney.Outcome outcome);
    }

    static final int MAX_ATTEMPTS = 3;
    private static final String PREFS = "delivery_outbox";
    private static final String KEY_PENDING = "pending";

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public DeliveryRepository(Context context) {
        this.app = context.getApplicationContext();
    }

    public void confirm(String beneficiaryId, Callback callback) {
        io.execute(() -> {
            String url = BuildConfig.CONFIRM_DELIVERY_URL;
            if (url != null && !url.isEmpty()) {
                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    try {
                        int code = post(url, body(beneficiaryId));
                        if (code >= 200 && code < 300) {
                            main.post(() -> callback.onComplete(ConfirmDeliveryJourney.Outcome.SYNCED));
                            return;
                        }
                    } catch (IOException | JSONException ignored) {
                        // network problem: retry below
                    }
                    if (attempt < MAX_ATTEMPTS) {
                        main.post(callback::onRetry);
                        sleep(1_000L * attempt);
                    }
                }
            }
            queueOffline(beneficiaryId);
            main.post(() -> callback.onComplete(ConfirmDeliveryJourney.Outcome.QUEUED_OFFLINE));
        });
    }

    /** Number of confirmations waiting to be synced. */
    public int pendingCount() {
        return prefs().getStringSet(KEY_PENDING, new HashSet<>()).size();
    }

    public void shutdown() {
        io.shutdownNow();
    }

    private String body(String beneficiaryId) throws JSONException {
        return new JSONObject()
                .put("beneficiaryId", beneficiaryId)
                .put("deliveredAt", System.currentTimeMillis())
                .put("app", "mukono-ngo-field")
                .toString();
    }

    private void queueOffline(String beneficiaryId) {
        SharedPreferences p = prefs();
        Set<String> pending = new HashSet<>(p.getStringSet(KEY_PENDING, new HashSet<>()));
        pending.add(beneficiaryId + "@" + System.currentTimeMillis());
        p.edit().putStringSet(KEY_PENDING, pending).apply();
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int post(String url, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(10_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try {
            try (OutputStream out = c.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return c.getResponseCode();
        } finally {
            c.disconnect();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
