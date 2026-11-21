package com.example.mukonongomap.performance;

/**
 * Journey 3: "Report Issue" bottom sheet transition.
 *
 * <pre>
 * first sheet  = beneficiary profile sheet (already open)
 * second sheet = Report Issue sheet
 *
 * report_issue_sheet_transition   tap "Report Issue" on the profile sheet
 *                                 -> profile sheet dismissed
 *                                 -> Report Issue sheet inflated, shown and first frame drawn
 * </pre>
 *
 * The swap (dismiss + inflate a 230-line layout + window animation) is where
 * low-end phones stutter, so it gets its own trace. The outcome attribute says
 * whether the second sheet appeared or the user left first.
 */
public final class ReportIssueSheetJourney {

    private final TraceContext ctx;

    private PerfTrace transitionTrace;
    private long startedAt;
    private boolean finished;

    public ReportIssueSheetJourney(TraceContext ctx) {
        this.ctx = ctx;
    }

    /** "Report Issue" tapped on the beneficiary profile sheet (first sheet). */
    public synchronized void onReportIssueTapped() {
        if (finished || transitionTrace != null) return;
        transitionTrace = ctx.startTrace(TraceNames.REPORT_ISSUE_SHEET_TRANSITION);
        startedAt = ctx.clock.nowMillis();
    }

    /** Report Issue sheet (second sheet) is showing and has drawn its first frame. */
    public synchronized void onSecondSheetShown() {
        if (finished || transitionTrace == null) return;
        transitionTrace.putMetric(TraceNames.M_TRANSITION_MS, ctx.clock.nowMillis() - startedAt);
        transitionTrace.putAttribute(TraceNames.A_OUTCOME, "shown");
        transitionTrace.stop();
        finished = true;
    }

    /** The screen went away (activity paused) before the second sheet appeared. */
    public synchronized void onAbandoned() {
        if (finished) return;
        if (transitionTrace != null) {
            transitionTrace.putAttribute(TraceNames.A_OUTCOME, "abandoned");
            transitionTrace.stop();
        }
        finished = true;
    }
}
