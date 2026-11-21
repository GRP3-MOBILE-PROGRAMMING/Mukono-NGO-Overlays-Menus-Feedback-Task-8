package com.example.mukonongomap.performance;

/**
 * Journey 4: delivery manifest in the warehouse tablet side sheet.
 *
 * <pre>
 * side_sheet_manifest_load    open requested -> data fetched -> list bound and drawn
 * side_sheet_manifest_scroll  sheet visible  -> sheet closed  (rendering metrics, see FrameStats)
 * </pre>
 *
 * The scroll trace carries {@code list_size_bucket} so you can prove (or rule
 * out) that jank grows with manifest length - the usual sign of a non-recycled
 * list (LinearLayout inside ScrollView) or expensive row binding.
 */
public final class ManifestSideSheetJourney {

    private final TraceContext ctx;
    private final FrameStats frameStats = new FrameStats();

    private PerfTrace loadTrace;
    private PerfTrace scrollTrace;
    private long openStartedAt;
    private long fetchedAt = -1;
    private int itemCount;
    private boolean loadDone;
    private boolean finished;

    public ManifestSideSheetJourney(TraceContext ctx) {
        this.ctx = ctx;
    }

    /** Feed this to {@code FrameMetricsScrollMonitor}. */
    public FrameStats frameStats() {
        return frameStats;
    }

    public synchronized void onOpenRequested() {
        if (finished || loadTrace != null) return;
        loadTrace = ctx.startTrace(TraceNames.SIDE_SHEET_MANIFEST_LOAD);
        openStartedAt = ctx.clock.nowMillis();
    }

    public synchronized void onManifestFetched(int items, boolean fromCache) {
        if (finished || loadTrace == null || loadDone || fetchedAt >= 0) return;
        fetchedAt = ctx.clock.nowMillis();
        itemCount = items;
        loadTrace.putMetric(TraceNames.M_FETCH_MS, fetchedAt - openStartedAt);
        loadTrace.putMetric(TraceNames.M_MANIFEST_ITEMS, items);
        loadTrace.putAttribute(TraceNames.A_DATA_SOURCE, fromCache ? "cache" : "network");
    }

    /**
     * Adapter bound and first frame drawn (post from an OnPreDrawListener on the list).
     * Stops the load trace and starts the scroll trace.
     */
    public synchronized void onManifestRendered() {
        if (finished || loadTrace == null || loadDone) return;
        long now = ctx.clock.nowMillis();
        if (fetchedAt >= 0) loadTrace.putMetric(TraceNames.M_BIND_MS, now - fetchedAt);
        loadTrace.putAttribute(TraceNames.A_LIST_SIZE, sizeBucket(itemCount));
        loadTrace.stop();
        loadDone = true;

        scrollTrace = ctx.startTrace(TraceNames.SIDE_SHEET_MANIFEST_SCROLL);
        scrollTrace.putAttribute(TraceNames.A_LIST_SIZE, sizeBucket(itemCount));
        scrollTrace.putMetric(TraceNames.M_MANIFEST_ITEMS, itemCount);
    }

    public synchronized void onLoadFailed() {
        if (finished || loadTrace == null || loadDone) return;
        loadTrace.putAttribute(TraceNames.A_OUTCOME, "failed");
        loadTrace.stop();
        loadDone = true;
        finished = true;
    }

    /** Side sheet closed (or activity paused). Writes rendering metrics and stops. */
    public synchronized void onClosed() {
        if (finished) return;
        if (loadTrace != null && !loadDone) {
            loadTrace.putAttribute(TraceNames.A_OUTCOME, "abandoned");
            loadTrace.stop();
        }
        if (scrollTrace != null) {
            frameStats.writeTo(scrollTrace);
            scrollTrace.stop();
        }
        finished = true;
    }

    static String sizeBucket(int items) {
        if (items <= 50) return "0_50";
        if (items <= 200) return "51_200";
        if (items <= 500) return "201_500";
        return "500_plus";
    }
}
