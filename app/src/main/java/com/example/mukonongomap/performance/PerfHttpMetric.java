package com.example.mukonongomap.performance;

/** Minimal view of a Firebase {@code HttpMetric} (a manually traced network request). */
public interface PerfHttpMetric {
    void start();

    void stop();

    void setHttpResponseCode(int code);

    void setResponsePayloadSize(long bytes);

    void setResponseContentType(String contentType);

    void putAttribute(String name, String value);
}
