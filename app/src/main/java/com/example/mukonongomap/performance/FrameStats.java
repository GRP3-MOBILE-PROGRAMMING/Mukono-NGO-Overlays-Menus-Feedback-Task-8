package com.example.mukonongomap.performance;

/**
 * Accumulates per-frame rendering data while the manifest list is scrolling.
 *
 * Thresholds match Firebase's automatic screen traces so the custom numbers
 * line up with the console: slow &gt; 16 ms, frozen &gt; 700 ms.
 *
 * For slow frames it also sums time per rendering phase (from FrameMetrics,
 * API 24+). That breakdown is what turns "the side sheet janks" into a
 * diagnosis:
 *  - input / animation high  -> work in scroll callbacks, onBindViewHolder, inflation during scroll
 *  - layout/measure high     -> nested weights / deep hierarchies in list rows, requestLayout storms
 *  - draw high               -> heavy onDraw, many overlapping backgrounds, shadows
 *  - sync/GPU high           -> large bitmaps uploaded during scroll, overdraw, GPU-bound tablet
 *
 * Thread-safe: frames arrive on the FrameMetrics handler thread, the trace is
 * written on the main thread.
 */
public final class FrameStats {

    public static final long SLOW_FRAME_NANOS = 16_000_000L;
    public static final long FROZEN_FRAME_NANOS = 700_000_000L;
    private static final long NANOS_PER_MS = 1_000_000L;

    private long total;
    private long slow;
    private long frozen;
    private long dropped;
    private long maxFrameNanos;
    private long scrollSessions;

    private long slowInputNanos;
    private long slowAnimationNanos;
    private long slowLayoutMeasureNanos;
    private long slowDrawNanos;
    private long slowSyncGpuNanos;

    public synchronized void onScrollSessionStarted() {
        scrollSessions++;
    }

    /** Frame with total duration only (API 23 Choreographer fallback). */
    public synchronized void recordFrame(long totalNanos) {
        count(totalNanos);
    }

    /** Frame with phase breakdown from android.view.FrameMetrics (API 24+). */
    public synchronized void recordFrame(long totalNanos, long inputNanos, long animationNanos,
                                         long layoutMeasureNanos, long drawNanos, long syncGpuNanos,
                                         int droppedSinceLast) {
        boolean isSlow = count(totalNanos);
        dropped += Math.max(0, droppedSinceLast);
        if (isSlow) {
            slowInputNanos += inputNanos;
            slowAnimationNanos += animationNanos;
            slowLayoutMeasureNanos += layoutMeasureNanos;
            slowDrawNanos += drawNanos;
            slowSyncGpuNanos += syncGpuNanos;
        }
    }

    private boolean count(long totalNanos) {
        total++;
        if (totalNanos > maxFrameNanos) maxFrameNanos = totalNanos;
        if (totalNanos > FROZEN_FRAME_NANOS) frozen++;
        if (totalNanos > SLOW_FRAME_NANOS) {
            slow++;
            return true;
        }
        return false;
    }

    /** Slow frames as a percentage x100 (1234 = 12.34%). Metrics must be integers. */
    public synchronized long slowFramePctX100() {
        return total == 0 ? 0 : (slow * 10_000L) / total;
    }

    /** Writes all rendering metrics onto the trace (call just before stop()). */
    public synchronized void writeTo(PerfTrace trace) {
        trace.putMetric(TraceNames.M_FRAMES_TOTAL, total);
        trace.putMetric(TraceNames.M_FRAMES_SLOW, slow);
        trace.putMetric(TraceNames.M_FRAMES_FROZEN, frozen);
        trace.putMetric(TraceNames.M_FRAMES_DROPPED, dropped);
        trace.putMetric(TraceNames.M_SLOW_FRAME_PCT_X100, slowFramePctX100());
        trace.putMetric(TraceNames.M_MAX_FRAME_MS, maxFrameNanos / NANOS_PER_MS);
        trace.putMetric(TraceNames.M_SCROLL_SESSIONS, scrollSessions);
        trace.putMetric(TraceNames.M_SLOW_INPUT_MS, slowInputNanos / NANOS_PER_MS);
        trace.putMetric(TraceNames.M_SLOW_ANIMATION_MS, slowAnimationNanos / NANOS_PER_MS);
        trace.putMetric(TraceNames.M_SLOW_LAYOUT_MEASURE_MS, slowLayoutMeasureNanos / NANOS_PER_MS);
        trace.putMetric(TraceNames.M_SLOW_DRAW_MS, slowDrawNanos / NANOS_PER_MS);
        trace.putMetric(TraceNames.M_SLOW_GPU_MS, slowSyncGpuNanos / NANOS_PER_MS);
    }

    public synchronized long total() { return total; }

    public synchronized long slow() { return slow; }

    public synchronized long frozen() { return frozen; }

    public synchronized long scrollSessions() { return scrollSessions; }
}
