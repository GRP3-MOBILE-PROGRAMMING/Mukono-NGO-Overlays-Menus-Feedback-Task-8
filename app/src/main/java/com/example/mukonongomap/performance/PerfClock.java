package com.example.mukonongomap.performance;

/**
 * Monotonic millisecond clock. On Android pass {@code SystemClock::elapsedRealtime};
 * in tests pass a fake so durations are deterministic.
 */
public interface PerfClock {
    long nowMillis();
}
