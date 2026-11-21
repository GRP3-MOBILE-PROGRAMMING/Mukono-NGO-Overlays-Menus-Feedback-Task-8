package com.example.mukonongomap.performance;

/**
 * Journey 2: "Confirm Delivery" tapped until the volunteer sees the result
 * (synced to server, or safely queued for offline sync).
 *
 * The {@code outcome} attribute matters most here: on weak links the slow
 * path is "retry twice then queue offline", and you want that split out in
 * the console instead of averaged into the fast Wi-Fi confirmations.
 *
 * The HTTP call itself is captured automatically by the perf Gradle plugin
 * (HttpURLConnection / OkHttp), so no HttpMetric is needed here.
 */
public final class ConfirmDeliveryJourney {

    public enum Outcome {
        SYNCED("synced"),
        QUEUED_OFFLINE("queued_offline"),
        FAILED("failed"),
        CANCELLED("cancelled");

        final String value;

        Outcome(String value) {
            this.value = value;
        }
    }

    private final TraceContext ctx;
    private PerfTrace trace;
    private boolean finished;

    public ConfirmDeliveryJourney(TraceContext ctx) {
        this.ctx = ctx;
    }

    /** In the button's OnClickListener, before any validation or I/O. */
    public synchronized void start() {
        if (trace != null) return;
        trace = ctx.startTrace(TraceNames.CONFIRM_DELIVERY);
    }

    /** Each time the upload is retried. */
    public synchronized void onRetry() {
        if (!active()) return;
        trace.incrementMetric(TraceNames.M_RETRIES, 1);
    }

    /** When the confirmation UI (snackbar / tick) is shown, or the error is shown. */
    public synchronized void complete(Outcome outcome) {
        if (!active()) return;
        trace.putAttribute(TraceNames.A_OUTCOME, outcome.value);
        trace.stop();
        finished = true;
    }

    private boolean active() {
        return trace != null && !finished;
    }
}
