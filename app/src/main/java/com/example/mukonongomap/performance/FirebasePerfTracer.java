package com.example.mukonongomap.performance;

import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.HttpMetric;
import com.google.firebase.perf.metrics.Trace;

/** Production {@link PerfTracer} backed by Firebase Performance Monitoring. */
public final class FirebasePerfTracer implements PerfTracer {

    private final FirebasePerformance perf;

    public FirebasePerfTracer(FirebasePerformance perf) {
        this.perf = perf;
    }

    @Override
    public PerfTrace newTrace(String name) {
        final Trace trace = perf.newTrace(name);
        return new PerfTrace() {
            @Override public void start() { trace.start(); }
            @Override public void stop() { trace.stop(); }
            @Override public void putAttribute(String n, String v) { trace.putAttribute(n, v); }
            @Override public void putMetric(String n, long v) { trace.putMetric(n, v); }
            @Override public void incrementMetric(String n, long by) { trace.incrementMetric(n, by); }
        };
    }

    @Override
    public PerfHttpMetric newHttpMetric(String url, String method) {
        final HttpMetric metric = perf.newHttpMetric(url, method);
        return new PerfHttpMetric() {
            @Override public void start() { metric.start(); }
            @Override public void stop() { metric.stop(); }
            @Override public void setHttpResponseCode(int code) { metric.setHttpResponseCode(code); }
            @Override public void setResponsePayloadSize(long bytes) { metric.setResponsePayloadSize(bytes); }
            @Override public void setResponseContentType(String type) { metric.setResponseContentType(type); }
            @Override public void putAttribute(String n, String v) { metric.putAttribute(n, v); }
        };
    }
}
