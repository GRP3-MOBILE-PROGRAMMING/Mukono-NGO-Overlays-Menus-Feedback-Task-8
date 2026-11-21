package com.example.mukonongomap.performance;

/**
 * Minimal view of a Firebase {@code Trace}. The journey classes depend on this
 * interface instead of Firebase directly, so they can be unit-tested on the
 * Jenkins agent without a device.
 */
public interface PerfTrace {
    void start();

    void stop();

    /** Custom attribute (dimension). Firebase allows at most 5 per trace. */
    void putAttribute(String name, String value);

    void putMetric(String name, long value);

    void incrementMetric(String name, long by);
}
