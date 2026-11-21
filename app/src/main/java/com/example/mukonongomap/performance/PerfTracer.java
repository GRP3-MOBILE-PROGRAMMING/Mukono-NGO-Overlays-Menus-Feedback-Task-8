package com.example.mukonongomap.performance;

/** Factory for traces and HTTP metrics. Production: {@link FirebasePerfTracer}. */
public interface PerfTracer {
    PerfTrace newTrace(String name);

    /**
     * @param url    full request URL; Firebase groups these into URL patterns in the console
     * @param method HTTP method, e.g. "GET"
     */
    PerfHttpMetric newHttpMetric(String url, String method);

    /** Tracer that records nothing; used before init and when collection is disabled. */
    PerfTracer NO_OP = new PerfTracer() {
        @Override
        public PerfTrace newTrace(String name) {
            return new PerfTrace() {
                @Override public void start() { }
                @Override public void stop() { }
                @Override public void putAttribute(String n, String v) { }
                @Override public void putMetric(String n, long v) { }
                @Override public void incrementMetric(String n, long by) { }
            };
        }

        @Override
        public PerfHttpMetric newHttpMetric(String url, String method) {
            return new PerfHttpMetric() {
                @Override public void start() { }
                @Override public void stop() { }
                @Override public void setHttpResponseCode(int code) { }
                @Override public void setResponsePayloadSize(long bytes) { }
                @Override public void setResponseContentType(String contentType) { }
                @Override public void putAttribute(String n, String v) { }
            };
        }
    };
}
