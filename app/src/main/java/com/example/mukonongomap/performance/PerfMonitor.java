package com.example.mukonongomap.performance;

import android.content.Context;
import android.os.SystemClock;

import com.google.firebase.perf.FirebasePerformance;

/**
 * App-wide entry point. Call {@link #init} once from {@code Application.onCreate()},
 * then start journeys from screens:
 *
 * <pre>
 * QrScanJourney scan = PerfMonitor.newQrScan();
 * scan.start();
 * </pre>
 *
 * Before init (or in JVM unit tests) every journey uses a no-op tracer.
 */
public final class PerfMonitor {

    private static volatile TraceContext context = new TraceContext(
            PerfTracer.NO_OP, SystemClock::elapsedRealtime, () -> NetworkClass.UNKNOWN, "unknown");

    private PerfMonitor() { }

    public static void init(Context appContext, boolean collectionEnabled) {
        FirebasePerformance perf = FirebasePerformance.getInstance();
        perf.setPerformanceCollectionEnabled(collectionEnabled);

        Context app = appContext.getApplicationContext();
        String deviceClass = app.getResources().getConfiguration().smallestScreenWidthDp >= 600
                ? "tablet" : "phone";

        context = new TraceContext(
                new FirebasePerfTracer(perf),
                SystemClock::elapsedRealtime,
                new NetworkClassifier(app),
                deviceClass);
    }

    public static TraceContext context() {
        return context;
    }

    public static QrScanJourney newQrScan() {
        return new QrScanJourney(context);
    }

    public static ConfirmDeliveryJourney newConfirmDelivery() {
        return new ConfirmDeliveryJourney(context);
    }

    public static ReportIssueSheetJourney newReportIssue() {
        return new ReportIssueSheetJourney(context);
    }

    public static ManifestSideSheetJourney newManifestSideSheet() {
        return new ManifestSideSheetJourney(context);
    }
}
