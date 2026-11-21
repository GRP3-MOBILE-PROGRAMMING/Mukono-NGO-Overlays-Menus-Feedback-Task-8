package com.example.mukonongomap.performance;

/** Fake clock + context builder shared by the journey tests. */
final class TestSupport {

    static final class FakeClock implements PerfClock {
        long now = 1_000;

        @Override
        public long nowMillis() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    final FakePerfTracer tracer = new FakePerfTracer();
    final FakeClock clock = new FakeClock();
    NetworkClass network = NetworkClass.CELL_MEDIUM;
    final TraceContext ctx;

    TestSupport(String deviceClass) {
        ctx = new TraceContext(tracer, clock, () -> network, deviceClass);
    }
}
