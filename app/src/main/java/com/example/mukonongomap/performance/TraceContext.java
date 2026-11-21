package com.example.mukonongomap.performance;

/**
 * What every journey needs: a tracer, a clock, and the current network / device
 * class. Built once by {@link PerfMonitor} on Android, or directly in tests.
 */
public final class TraceContext {

    /** Supplies the network class at the moment a journey starts. */
    public interface NetworkProvider {
        NetworkClass current();
    }

    public final PerfTracer tracer;
    public final PerfClock clock;
    public final NetworkProvider network;
    /** "phone" or "tablet" (sw600dp). */
    public final String deviceClass;

    public TraceContext(PerfTracer tracer, PerfClock clock, NetworkProvider network, String deviceClass) {
        this.tracer = tracer;
        this.clock = clock;
        this.network = network;
        this.deviceClass = deviceClass;
    }

    /** Starts a trace with the two attributes every journey carries. */
    PerfTrace startTrace(String name) {
        PerfTrace t = tracer.newTrace(name);
        t.putAttribute(TraceNames.A_NETWORK, network.current().value());
        t.putAttribute(TraceNames.A_DEVICE_CLASS, deviceClass);
        t.start();
        return t;
    }
}
