package com.example.mukonongomap.performance;

/**
 * Coarse network class attached to every trace as {@code network_type}, so the
 * Firebase console can filter "QR scan on 2G-like links in Mukono" separately
 * from Wi-Fi in the warehouse.
 *
 * Classification uses the link's estimated downstream bandwidth rather than the
 * radio type, because a "4G" icon in a congested Kampala cell often performs like 3G,
 * and reading the radio type needs the READ_PHONE_STATE permission.
 */
public enum NetworkClass {
    OFFLINE("offline"),
    WIFI("wifi"),
    CELL_FAST("cell_fast"),   // >= 5 Mbps
    CELL_MEDIUM("cell_3g"),   // 500 kbps - 5 Mbps
    CELL_SLOW("cell_2g"),     // < 500 kbps
    UNKNOWN("unknown");

    static final int FAST_KBPS = 5_000;
    static final int MEDIUM_KBPS = 500;

    private final String value;

    NetworkClass(String value) {
        this.value = value;
    }

    /** Value written into the Firebase attribute. */
    public String value() {
        return value;
    }

    /**
     * @param connected      whether a validated internet connection exists
     * @param wifiOrEthernet transport is Wi-Fi or Ethernet
     * @param downstreamKbps link estimate from NetworkCapabilities; &lt;= 0 if unknown
     */
    public static NetworkClass classify(boolean connected, boolean wifiOrEthernet, int downstreamKbps) {
        if (!connected) return OFFLINE;
        if (wifiOrEthernet) return WIFI;
        if (downstreamKbps <= 0) return UNKNOWN;
        if (downstreamKbps >= FAST_KBPS) return CELL_FAST;
        if (downstreamKbps >= MEDIUM_KBPS) return CELL_MEDIUM;
        return CELL_SLOW;
    }
}
