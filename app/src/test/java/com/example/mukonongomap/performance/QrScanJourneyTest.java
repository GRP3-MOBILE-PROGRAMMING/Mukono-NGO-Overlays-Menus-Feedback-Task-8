package com.example.mukonongomap.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QrScanJourneyTest {

    private final TestSupport s = new TestSupport("phone");
    private final QrScanJourney scan = new QrScanJourney(s.ctx);

    @Test
    public void happyPath_recordsEveryPhaseOnChildAndParentTraces() {
        scan.start();
        scan.onCameraOpenRequested();
        s.clock.advance(420);
        scan.onCameraPreviewStarted();
        scan.onFrameAnalyzed(30);
        scan.onFrameAnalyzed(55);
        scan.onFrameAnalyzed(40);
        s.clock.advance(900);
        scan.onQrDecoded();
        scan.onBeneficiaryRequestStarted("https://api.example.org/beneficiaries/BEN-001");
        s.network = NetworkClass.CELL_SLOW; // should not change the already-started fetch trace
        s.clock.advance(1800);
        scan.onBeneficiaryResponse(200, 2048, "application/json", false);
        scan.onProfileSheetExpandStarted();
        s.clock.advance(250);
        scan.onProfileSheetExpanded();

        assertTrue(scan.isFinished());

        FakePerfTracer.FakeTrace camera = s.tracer.only(TraceNames.QR_CAMERA_INIT);
        assertEquals(420, camera.metric(TraceNames.M_CAMERA_INIT_MS));
        assertTrue(camera.stopped);

        FakePerfTracer.FakeTrace decode = s.tracer.only(TraceNames.QR_DECODE);
        assertEquals(900, decode.metric(TraceNames.M_DECODE_MS));
        assertEquals(3, decode.metric(TraceNames.M_DECODE_FRAMES));
        assertEquals(125, decode.metric(TraceNames.M_DECODE_CPU_MS));
        assertEquals(55, decode.metric(TraceNames.M_DECODE_CPU_MAX_MS));

        FakePerfTracer.FakeTrace fetch = s.tracer.only(TraceNames.QR_BENEFICIARY_FETCH);
        assertEquals(1800, fetch.metric(TraceNames.M_FETCH_MS));
        assertEquals("cell_3g", fetch.attributes.get(TraceNames.A_NETWORK));
        assertEquals("network", fetch.attributes.get(TraceNames.A_DATA_SOURCE));
        assertEquals("ok", fetch.attributes.get(TraceNames.A_OUTCOME));

        FakePerfTracer.FakeHttp http = s.tracer.httpMetrics.get(0);
        assertEquals("GET", http.method);
        assertEquals(200, http.responseCode);
        assertEquals(2048, http.responseBytes);
        assertTrue(http.stopped);

        FakePerfTracer.FakeTrace sheet = s.tracer.only(TraceNames.QR_PROFILE_SHEET_RENDER);
        assertEquals(250, sheet.metric(TraceNames.M_SHEET_RENDER_MS));

        FakePerfTracer.FakeTrace total = s.tracer.only(TraceNames.QR_SCAN_TO_PROFILE);
        assertTrue(total.stopped);
        assertEquals("success", total.attributes.get(TraceNames.A_OUTCOME));
        assertEquals("phone", total.attributes.get(TraceNames.A_DEVICE_CLASS));
        assertEquals(420, total.metric(TraceNames.M_CAMERA_INIT_MS));
        assertEquals(900, total.metric(TraceNames.M_DECODE_MS));
        assertEquals(1800, total.metric(TraceNames.M_FETCH_MS));
        assertEquals(250, total.metric(TraceNames.M_SHEET_RENDER_MS));
    }

    @Test
    public void cancelDuringDecode_closesOpenPhaseAsAbandoned() {
        scan.start();
        scan.onCameraOpenRequested();
        scan.onCameraPreviewStarted();
        scan.finish(QrScanJourney.Outcome.CANCELLED);

        assertEquals("abandoned", s.tracer.only(TraceNames.QR_DECODE).attributes.get(TraceNames.A_OUTCOME));
        assertTrue(s.tracer.only(TraceNames.QR_DECODE).stopped);
        assertEquals("cancelled", s.tracer.only(TraceNames.QR_SCAN_TO_PROFILE).attributes.get(TraceNames.A_OUTCOME));
    }

    @Test
    public void networkFailure_tagsIoErrorAndStopsHttpMetric() {
        scan.start();
        scan.onBeneficiaryRequestStarted("https://api.example.org/beneficiaries/BEN-002");
        s.clock.advance(15_000);
        scan.onBeneficiaryRequestFailed();
        scan.finish(QrScanJourney.Outcome.FETCH_FAILED);

        FakePerfTracer.FakeTrace fetch = s.tracer.only(TraceNames.QR_BENEFICIARY_FETCH);
        assertEquals("io_error", fetch.attributes.get(TraceNames.A_OUTCOME));
        assertEquals(15_000, fetch.metric(TraceNames.M_FETCH_MS));
        assertTrue(s.tracer.httpMetrics.get(0).stopped);
        assertEquals("fetch_failed", s.tracer.only(TraceNames.QR_SCAN_TO_PROFILE).attributes.get(TraceNames.A_OUTCOME));
    }

    @Test
    public void networkFailureThenOfflineCache_stillCompletes() {
        scan.start();
        scan.onBeneficiaryRequestStarted("https://example.org/BNF-2048.json");
        scan.onBeneficiaryRequestFailed();
        scan.onServedFromOfflineCache();
        scan.onProfileSheetExpandStarted();
        s.clock.advance(120);
        scan.onProfileSheetExpanded();

        FakePerfTracer.FakeTrace total = s.tracer.only(TraceNames.QR_SCAN_TO_PROFILE);
        assertEquals("offline_cache", total.attributes.get(TraceNames.A_DATA_SOURCE));
        assertEquals("success", total.attributes.get(TraceNames.A_OUTCOME));
        assertEquals("io_error", s.tracer.only(TraceNames.QR_BENEFICIARY_FETCH).attributes.get(TraceNames.A_OUTCOME));
    }

    @Test
    public void cachedProfile_isTaggedAsCache() {
        scan.start();
        scan.onBeneficiaryRequestStarted("https://api.example.org/beneficiaries/BEN-003");
        scan.onBeneficiaryResponse(304, 0, null, true);
        assertEquals("cache", s.tracer.only(TraceNames.QR_SCAN_TO_PROFILE).attributes.get(TraceNames.A_DATA_SOURCE));
        assertEquals("http_304", s.tracer.only(TraceNames.QR_BENEFICIARY_FETCH).attributes.get(TraceNames.A_OUTCOME));
    }

    @Test
    public void outOfOrderAndDuplicateCalls_areIgnoredNotFatal() {
        scan.onQrDecoded();              // before start
        scan.onProfileSheetExpanded();   // before start
        assertTrue(s.tracer.traces.isEmpty());

        scan.start();
        scan.start();
        scan.onCameraPreviewStarted();   // camera never requested
        scan.onProfileSheetExpanded();   // sheet never started
        assertFalse(scan.isFinished());

        scan.finish(QrScanJourney.Outcome.CANCELLED);
        scan.finish(QrScanJourney.Outcome.SUCCESS); // second finish ignored (fake throws on double stop)
        assertEquals(1, s.tracer.traces.size());
    }
}
