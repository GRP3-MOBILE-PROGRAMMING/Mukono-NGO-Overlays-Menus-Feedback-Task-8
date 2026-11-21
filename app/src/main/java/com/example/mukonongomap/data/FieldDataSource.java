package com.example.mukonongomap.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.mukonongomap.BuildConfig;
import com.example.mukonongomap.performance.QrScanJourney;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads beneficiaries and the delivery manifest.
 *
 * Network first (HTTPS GET from FIELD_DATA_BASE_URL), then the copy bundled in
 * assets/field-data as an offline cache. Volunteers keep working when the
 * Kampala network drops, and the traces record which source was used.
 *
 * Callbacks run on the main thread.
 */
public final class FieldDataSource {

    public interface Callback<T> {
        /** value is null when neither the network nor the cache had it. */
        void onResult(T value, boolean fromCache);
    }

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final Context app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public FieldDataSource(Context context) {
        this.app = context.getApplicationContext();
    }

    public static String beneficiaryUrl(String id) {
        return BuildConfig.FIELD_DATA_BASE_URL + "beneficiaries/" + id + ".json";
    }

    public static String manifestUrl() {
        return BuildConfig.FIELD_DATA_BASE_URL + "manifest.json";
    }

    /** Fetches one beneficiary; reports network timing to the scan journey (may be null). */
    public void fetchBeneficiary(String id, QrScanJourney scan, Callback<Beneficiary> callback) {
        io.execute(() -> {
            String url = beneficiaryUrl(id);
            if (scan != null) scan.onBeneficiaryRequestStarted(url);
            Beneficiary value = null;
            boolean fromCache = false;
            try {
                HttpResult r = get(url);
                if (r.code == 200) {
                    value = Beneficiary.fromJson(r.body);
                    if (scan != null) scan.onBeneficiaryResponse(r.code, r.bytes, r.contentType, false);
                } else {
                    value = beneficiaryFromAssets(id);
                    fromCache = value != null;
                    if (scan != null) scan.onBeneficiaryResponse(r.code, r.bytes, r.contentType, fromCache);
                }
            } catch (IOException | org.json.JSONException e) {
                if (scan != null) scan.onBeneficiaryRequestFailed();
                value = beneficiaryFromAssets(id);
                fromCache = value != null;
                if (fromCache && scan != null) scan.onServedFromOfflineCache();
            }
            Beneficiary result = value;
            boolean cached = fromCache;
            main.post(() -> callback.onResult(result, cached));
        });
    }

    /** Fetches the warehouse delivery manifest. */
    public void fetchManifest(Callback<List<ManifestItem>> callback) {
        io.execute(() -> {
            List<ManifestItem> items = null;
            boolean fromCache = false;
            try {
                HttpResult r = get(manifestUrl());
                if (r.code == 200) items = ManifestItem.listFromJson(r.body);
            } catch (IOException | org.json.JSONException ignored) {
                // fall through to the cache
            }
            if (items == null) {
                try {
                    items = ManifestItem.listFromJson(readAsset("field-data/manifest.json"));
                    fromCache = true;
                } catch (IOException | org.json.JSONException ignored) {
                    // no data at all
                }
            }
            List<ManifestItem> result = items;
            boolean cached = fromCache;
            main.post(() -> callback.onResult(result, cached));
        });
    }

    public void shutdown() {
        io.shutdownNow();
    }

    // ---------------------------------------------------------------------------------

    private Beneficiary beneficiaryFromAssets(String id) {
        try {
            return Beneficiary.fromJson(readAsset("field-data/beneficiaries/" + id + ".json"));
        } catch (IOException | org.json.JSONException e) {
            return null;
        }
    }

    private String readAsset(String path) throws IOException {
        try (InputStream in = app.getAssets().open(path)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    static final class HttpResult {
        final int code;
        final String body;
        final long bytes;
        final String contentType;

        HttpResult(int code, String body, long bytes, String contentType) {
            this.code = code;
            this.body = body;
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    private static HttpResult get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            byte[] data = in == null ? new byte[0] : readAll(in);
            return new HttpResult(code, new String(data, StandardCharsets.UTF_8), data.length, c.getContentType());
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
