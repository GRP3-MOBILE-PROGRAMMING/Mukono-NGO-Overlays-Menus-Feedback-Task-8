package com.example.mukonongomap.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OtherJourneysTest {

    @Test
    public void confirmDelivery_countsRetriesAndOutcome() {
        TestSupport s = new TestSupport("phone");
        s.network = NetworkClass.CELL_SLOW;
        ConfirmDeliveryJourney j = new ConfirmDeliveryJourney(s.ctx);
        j.start();
        j.onRetry();
        j.onRetry();
        j.complete(ConfirmDeliveryJourney.Outcome.QUEUED_OFFLINE);
        j.complete(ConfirmDeliveryJourney.Outcome.SYNCED); // ignored

        FakePerfTracer.FakeTrace t = s.tracer.only(TraceNames.CONFIRM_DELIVERY);
        assertEquals(2, t.metric(TraceNames.M_RETRIES));
        assertEquals("queued_offline", t.attributes.get(TraceNames.A_OUTCOME));
        assertEquals("cell_2g", t.attributes.get(TraceNames.A_NETWORK));
        assertTrue(t.stopped);
    }

    @Test
    public void reportIssue_measuresFirstSheetToSecondSheetTransition() {
        TestSupport s = new TestSupport("phone");
        ReportIssueSheetJourney j = new ReportIssueSheetJourney(s.ctx);
        j.onReportIssueTapped();
        s.clock.advance(340);
        j.onSecondSheetShown();
        j.onSecondSheetShown(); // ignored

        FakePerfTracer.FakeTrace t = s.tracer.only(TraceNames.REPORT_ISSUE_SHEET_TRANSITION);
        assertEquals(340, t.metric(TraceNames.M_TRANSITION_MS));
        assertEquals("shown", t.attributes.get(TraceNames.A_OUTCOME));
        assertTrue(t.stopped);
    }

    @Test
    public void reportIssue_abandonedBeforeSecondSheet() {
        TestSupport s = new TestSupport("phone");
        ReportIssueSheetJourney j = new ReportIssueSheetJourney(s.ctx);
        j.onReportIssueTapped();
        j.onAbandoned();
        j.onSecondSheetShown(); // ignored after abandon
        FakePerfTracer.FakeTrace t = s.tracer.only(TraceNames.REPORT_ISSUE_SHEET_TRANSITION);
        assertEquals("abandoned", t.attributes.get(TraceNames.A_OUTCOME));
        assertTrue(t.stopped);
    }

    @Test
    public void reportIssue_secondSheetWithoutTap_isIgnored() {
        TestSupport s = new TestSupport("phone");
        new ReportIssueSheetJourney(s.ctx).onSecondSheetShown();
        assertTrue(s.tracer.traces.isEmpty());
    }

    @Test
    public void manifestSideSheet_loadThenScrollMetrics() {
        TestSupport s = new TestSupport("tablet");
        s.network = NetworkClass.WIFI;
        ManifestSideSheetJourney j = new ManifestSideSheetJourney(s.ctx);
        j.onOpenRequested();
        s.clock.advance(600);
        j.onManifestFetched(320, false);
        s.clock.advance(450);
        j.onManifestRendered();

        FrameStats fs = j.frameStats();
        fs.onScrollSessionStarted();
        for (int i = 0; i < 90; i++) fs.recordFrame(8_000_000L, 0, 0, 0, 0, 0, 0);
        for (int i = 0; i < 9; i++) fs.recordFrame(40_000_000L, 1_000_000L, 25_000_000L, 9_000_000L, 3_000_000L, 2_000_000L, 1);
        fs.recordFrame(800_000_000L, 0, 0, 790_000_000L, 0, 0, 40);
        j.onClosed();

        FakePerfTracer.FakeTrace load = s.tracer.only(TraceNames.SIDE_SHEET_MANIFEST_LOAD);
        assertEquals(600, load.metric(TraceNames.M_FETCH_MS));
        assertEquals(450, load.metric(TraceNames.M_BIND_MS));
        assertEquals(320, load.metric(TraceNames.M_MANIFEST_ITEMS));
        assertEquals("201_500", load.attributes.get(TraceNames.A_LIST_SIZE));
        assertEquals("tablet", load.attributes.get(TraceNames.A_DEVICE_CLASS));

        FakePerfTracer.FakeTrace scroll = s.tracer.only(TraceNames.SIDE_SHEET_MANIFEST_SCROLL);
        assertTrue(scroll.stopped);
        assertEquals(100, scroll.metric(TraceNames.M_FRAMES_TOTAL));
        assertEquals(10, scroll.metric(TraceNames.M_FRAMES_SLOW));
        assertEquals(1, scroll.metric(TraceNames.M_FRAMES_FROZEN));
        assertEquals(49, scroll.metric(TraceNames.M_FRAMES_DROPPED));
        assertEquals(1000, scroll.metric(TraceNames.M_SLOW_FRAME_PCT_X100)); // 10.00 %
        assertEquals(800, scroll.metric(TraceNames.M_MAX_FRAME_MS));
        assertEquals(225, scroll.metric(TraceNames.M_SLOW_ANIMATION_MS));
        assertEquals(81 + 790, scroll.metric(TraceNames.M_SLOW_LAYOUT_MEASURE_MS));
        assertEquals(1, scroll.metric(TraceNames.M_SCROLL_SESSIONS));
    }

    @Test
    public void manifestSideSheet_closedBeforeRender_marksAbandoned() {
        TestSupport s = new TestSupport("tablet");
        ManifestSideSheetJourney j = new ManifestSideSheetJourney(s.ctx);
        j.onOpenRequested();
        j.onClosed();
        assertEquals("abandoned", s.tracer.only(TraceNames.SIDE_SHEET_MANIFEST_LOAD).attributes.get(TraceNames.A_OUTCOME));
        assertEquals(1, s.tracer.traces.size());
    }

    @Test
    public void sizeBuckets() {
        assertEquals("0_50", ManifestSideSheetJourney.sizeBucket(0));
        assertEquals("0_50", ManifestSideSheetJourney.sizeBucket(50));
        assertEquals("51_200", ManifestSideSheetJourney.sizeBucket(51));
        assertEquals("201_500", ManifestSideSheetJourney.sizeBucket(500));
        assertEquals("500_plus", ManifestSideSheetJourney.sizeBucket(501));
    }

    @Test
    public void frameStats_emptyIsZeroNotDivideByZero() {
        assertEquals(0, new FrameStats().slowFramePctX100());
    }

    @Test
    public void frameStats_exactly16msIsNotSlow() {
        FrameStats fs = new FrameStats();
        fs.recordFrame(16_000_000L);
        fs.recordFrame(16_000_001L);
        assertEquals(1, fs.slow());
        assertFalse(fs.frozen() > 0);
    }

    @Test
    public void networkClassification() {
        assertEquals(NetworkClass.OFFLINE, NetworkClass.classify(false, true, 50_000));
        assertEquals(NetworkClass.WIFI, NetworkClass.classify(true, true, 100));
        assertEquals(NetworkClass.UNKNOWN, NetworkClass.classify(true, false, 0));
        assertEquals(NetworkClass.CELL_SLOW, NetworkClass.classify(true, false, 120));
        assertEquals(NetworkClass.CELL_MEDIUM, NetworkClass.classify(true, false, 500));
        assertEquals(NetworkClass.CELL_FAST, NetworkClass.classify(true, false, 5_000));
    }
}
