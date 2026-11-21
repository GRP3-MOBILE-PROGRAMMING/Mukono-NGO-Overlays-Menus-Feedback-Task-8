package com.example.mukonongomap.performance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * In-memory tracer that fails the test on anything Firebase would silently
 * drop in production: bad names, &gt;5 attributes, writes after stop(), double start/stop.
 */
final class FakePerfTracer implements PerfTracer {

    static final Pattern ATTRIBUTE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,39}");

    final List<FakeTrace> traces = new ArrayList<>();
    final List<FakeHttp> httpMetrics = new ArrayList<>();

    @Override
    public PerfTrace newTrace(String name) {
        checkTraceOrMetricName(name);
        FakeTrace t = new FakeTrace(name);
        traces.add(t);
        return t;
    }

    @Override
    public PerfHttpMetric newHttpMetric(String url, String method) {
        FakeHttp h = new FakeHttp(url, method);
        httpMetrics.add(h);
        return h;
    }

    FakeTrace only(String name) {
        FakeTrace found = null;
        for (FakeTrace t : traces) {
            if (t.name.equals(name)) {
                if (found != null) throw new AssertionError("more than one trace named " + name);
                found = t;
            }
        }
        if (found == null) throw new AssertionError("no trace named " + name + " in " + names());
        return found;
    }

    List<String> names() {
        List<String> n = new ArrayList<>();
        for (FakeTrace t : traces) n.add(t.name);
        return n;
    }

    static void checkTraceOrMetricName(String name) {
        if (name == null || name.isEmpty() || name.length() > 100
                || name.startsWith("_") || !name.trim().equals(name)) {
            throw new AssertionError("invalid Firebase trace/metric name: '" + name + "'");
        }
    }

    static void checkAttribute(Map<String, String> attrs, String name, String value) {
        if (!ATTRIBUTE_NAME.matcher(name).matches()) {
            throw new AssertionError("invalid attribute name: " + name);
        }
        if (value == null || value.isEmpty() || value.length() > 100) {
            throw new AssertionError("invalid attribute value for " + name + ": " + value);
        }
        if (!attrs.containsKey(name) && attrs.size() >= 5) {
            throw new AssertionError("more than 5 attributes; adding " + name + " to " + attrs.keySet());
        }
    }

    static final class FakeTrace implements PerfTrace {
        final String name;
        final Map<String, String> attributes = new LinkedHashMap<>();
        final Map<String, Long> metrics = new LinkedHashMap<>();
        boolean started;
        boolean stopped;

        FakeTrace(String name) {
            this.name = name;
        }

        @Override
        public void start() {
            if (started) throw new AssertionError(name + " started twice");
            started = true;
        }

        @Override
        public void stop() {
            if (!started) throw new AssertionError(name + " stopped before start");
            if (stopped) throw new AssertionError(name + " stopped twice");
            stopped = true;
        }

        @Override
        public void putAttribute(String n, String v) {
            requireOpen();
            checkAttribute(attributes, n, v);
            attributes.put(n, v);
        }

        @Override
        public void putMetric(String n, long v) {
            requireOpen();
            checkTraceOrMetricName(n);
            if (!metrics.containsKey(n) && metrics.size() >= 32) throw new AssertionError("more than 32 metrics");
            metrics.put(n, v);
        }

        @Override
        public void incrementMetric(String n, long by) {
            requireOpen();
            checkTraceOrMetricName(n);
            metrics.put(n, metrics.getOrDefault(n, 0L) + by);
        }

        long metric(String n) {
            Long v = metrics.get(n);
            if (v == null) throw new AssertionError(name + " has no metric " + n + "; has " + metrics.keySet());
            return v;
        }

        private void requireOpen() {
            if (stopped) throw new AssertionError("write to " + name + " after stop()");
        }
    }

    static final class FakeHttp implements PerfHttpMetric {
        final String url;
        final String method;
        final Map<String, String> attributes = new LinkedHashMap<>();
        int responseCode = -1;
        long responseBytes = -1;
        String contentType;
        boolean started;
        boolean stopped;

        FakeHttp(String url, String method) {
            this.url = url;
            this.method = method;
        }

        @Override public void start() { started = true; }

        @Override
        public void stop() {
            if (stopped) throw new AssertionError("http metric stopped twice");
            stopped = true;
        }

        @Override public void setHttpResponseCode(int code) { responseCode = code; }

        @Override public void setResponsePayloadSize(long bytes) { responseBytes = bytes; }

        @Override public void setResponseContentType(String type) { contentType = type; }

        @Override
        public void putAttribute(String n, String v) {
            checkAttribute(attributes, n, v);
            attributes.put(n, v);
        }
    }
}
