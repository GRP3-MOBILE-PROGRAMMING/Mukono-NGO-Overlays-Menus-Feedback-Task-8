package com.example.mukonongomap.performance;

/**
 * Journey 1: QR code scan to beneficiary profile in the bottom sheet.
 *
 * <pre>
 * qr_scan_to_profile  |---------------------------------------------------------|
 *   qr_camera_init    |--camera--|
 *   qr_decode                    |---aim + decode---|
 *   qr_beneficiary_fetch                            |--network--|   (+ HttpMetric)
 *   qr_profile_sheet_render                                     |--sheet--|
 * </pre>
 *
 * Every phase duration is also written as a metric on the parent trace, so one
 * console row shows where the time went. Create one instance per scan. Methods
 * are synchronized because camera/analyzer callbacks arrive on worker threads.
 * Calls out of order are ignored rather than throwing - a monitoring bug must
 * never crash a volunteer's app in the field.
 */
public final class QrScanJourney {

    public enum Outcome {
        SUCCESS("success"),
        PERMISSION_DENIED("permission_denied"),
        DECODE_FAILED("decode_failed"),
        NOT_FOUND("not_found"),
        FETCH_FAILED("fetch_failed"),
        CANCELLED("cancelled");

        final String value;

        Outcome(String value) {
            this.value = value;
        }
    }

    private final TraceContext ctx;

    private PerfTrace total;
    private PerfTrace cameraTrace;
    private PerfTrace decodeTrace;
    private PerfTrace fetchTrace;
    private PerfTrace sheetTrace;
    private PerfHttpMetric http;

    private long cameraStartedAt;
    private long decodeStartedAt;
    private long fetchStartedAt;
    private long sheetStartedAt;

    private long framesAnalyzed;
    private long decodeCpuTotalMs;
    private long decodeCpuMaxMs;

    private boolean finished;

    public QrScanJourney(TraceContext ctx) {
        this.ctx = ctx;
    }

    /** Volunteer tapped "Scan". */
    public synchronized void start() {
        if (total != null) return;
        total = ctx.startTrace(TraceNames.QR_SCAN_TO_PROFILE);
    }

    /** Just before CameraX bindToLifecycle / Camera2 openCamera. */
    public synchronized void onCameraOpenRequested() {
        if (!active() || cameraTrace != null) return;
        cameraTrace = ctx.startTrace(TraceNames.QR_CAMERA_INIT);
        cameraStartedAt = ctx.clock.nowMillis();
    }

    /** First preview frame is on screen (e.g. PreviewView StreamState.STREAMING). */
    public synchronized void onCameraPreviewStarted() {
        if (!active() || cameraTrace == null || decodeTrace != null) return;
        long ms = ctx.clock.nowMillis() - cameraStartedAt;
        cameraTrace.putMetric(TraceNames.M_CAMERA_INIT_MS, ms);
        cameraTrace.stop();
        cameraTrace = null;
        total.putMetric(TraceNames.M_CAMERA_INIT_MS, ms);

        decodeTrace = ctx.startTrace(TraceNames.QR_DECODE);
        decodeStartedAt = ctx.clock.nowMillis();
    }

    /**
     * Called by the image analyzer for every frame it processes, decoded or not.
     * Separates "the decoder is slow on this phone" (high CPU per frame) from
     * "the volunteer took a while to aim" (many frames, low CPU each).
     */
    public synchronized void onFrameAnalyzed(long processingMillis) {
        if (!active() || decodeTrace == null) return;
        framesAnalyzed++;
        decodeCpuTotalMs += processingMillis;
        if (processingMillis > decodeCpuMaxMs) decodeCpuMaxMs = processingMillis;
    }

    /** A QR code was decoded into a beneficiary ID. */
    public synchronized void onQrDecoded() {
        if (!active() || decodeTrace == null) return;
        long ms = ctx.clock.nowMillis() - decodeStartedAt;
        decodeTrace.putMetric(TraceNames.M_DECODE_MS, ms);
        decodeTrace.putMetric(TraceNames.M_DECODE_FRAMES, framesAnalyzed);
        decodeTrace.putMetric(TraceNames.M_DECODE_CPU_MS, decodeCpuTotalMs);
        decodeTrace.putMetric(TraceNames.M_DECODE_CPU_MAX_MS, decodeCpuMaxMs);
        decodeTrace.stop();
        decodeTrace = null;
        total.putMetric(TraceNames.M_DECODE_MS, ms);
        total.putMetric(TraceNames.M_DECODE_FRAMES, framesAnalyzed);
    }

    /** Request to fetch the beneficiary record is about to be sent. */
    public synchronized void onBeneficiaryRequestStarted(String url) {
        if (!active() || fetchTrace != null) return;
        fetchTrace = ctx.startTrace(TraceNames.QR_BENEFICIARY_FETCH);
        http = ctx.tracer.newHttpMetric(url, "GET");
        http.putAttribute(TraceNames.A_NETWORK, ctx.network.current().value());
        http.start();
        fetchStartedAt = ctx.clock.nowMillis();
    }

    /**
     * Response received and parsed.
     *
     * @param fromCache true when served from the offline cache (no network hit)
     */
    public synchronized void onBeneficiaryResponse(int httpCode, long responseBytes,
                                                   String contentType, boolean fromCache) {
        if (!active() || fetchTrace == null) return;
        long ms = ctx.clock.nowMillis() - fetchStartedAt;
        String source = fromCache ? "cache" : "network";

        http.setHttpResponseCode(httpCode);
        http.setResponsePayloadSize(responseBytes);
        if (contentType != null) http.setResponseContentType(contentType);
        http.stop();
        http = null;

        fetchTrace.putAttribute(TraceNames.A_DATA_SOURCE, source);
        fetchTrace.putAttribute(TraceNames.A_OUTCOME, httpCode >= 200 && httpCode < 300 ? "ok" : "http_" + httpCode);
        fetchTrace.putMetric(TraceNames.M_FETCH_MS, ms);
        fetchTrace.putMetric(TraceNames.M_RESPONSE_BYTES, responseBytes);
        fetchTrace.stop();
        fetchTrace = null;

        total.putAttribute(TraceNames.A_DATA_SOURCE, source);
        total.putMetric(TraceNames.M_FETCH_MS, ms);
    }

    /** Request failed without a response (timeout, DNS, no route). */
    public synchronized void onBeneficiaryRequestFailed() {
        if (!active() || fetchTrace == null) return;
        long ms = ctx.clock.nowMillis() - fetchStartedAt;
        http.stop();
        http = null;
        fetchTrace.putAttribute(TraceNames.A_OUTCOME, "io_error");
        fetchTrace.putMetric(TraceNames.M_FETCH_MS, ms);
        fetchTrace.stop();
        fetchTrace = null;
        total.putMetric(TraceNames.M_FETCH_MS, ms);
    }

    /**
     * The network failed but the profile was found in the on-device cache, so the
     * journey continues. Tags the end-to-end trace so offline scans can be filtered.
     */
    public synchronized void onServedFromOfflineCache() {
        if (!active()) return;
        total.putAttribute(TraceNames.A_DATA_SOURCE, "offline_cache");
    }

    /** The profile bottom sheet is about to be shown (dialog.show() / setState(STATE_EXPANDED)). */
    public synchronized void onProfileSheetExpandStarted() {
        if (!active() || sheetTrace != null) return;
        sheetTrace = ctx.startTrace(TraceNames.QR_PROFILE_SHEET_RENDER);
        sheetStartedAt = ctx.clock.nowMillis();
    }

    /**
     * Sheet reached STATE_EXPANDED and its content has drawn (call from the
     * next OnPreDrawListener / View.post after the state callback). Completes the journey.
     */
    public synchronized void onProfileSheetExpanded() {
        if (!active() || sheetTrace == null) return;
        long ms = ctx.clock.nowMillis() - sheetStartedAt;
        sheetTrace.putMetric(TraceNames.M_SHEET_RENDER_MS, ms);
        sheetTrace.stop();
        sheetTrace = null;
        total.putMetric(TraceNames.M_SHEET_RENDER_MS, ms);
        finish(Outcome.SUCCESS);
    }

    /** Ends the journey. Any phase still open is closed and tagged "abandoned". */
    public synchronized void finish(Outcome outcome) {
        if (!active()) return;
        abandon(cameraTrace);
        abandon(decodeTrace);
        abandon(fetchTrace);
        abandon(sheetTrace);
        cameraTrace = decodeTrace = fetchTrace = sheetTrace = null;
        if (http != null) {
            http.stop();
            http = null;
        }
        total.putAttribute(TraceNames.A_OUTCOME, outcome.value);
        total.stop();
        finished = true;
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    private boolean active() {
        return total != null && !finished;
    }

    private static void abandon(PerfTrace t) {
        if (t == null) return;
        t.putAttribute(TraceNames.A_OUTCOME, "abandoned");
        t.stop();
    }
}
