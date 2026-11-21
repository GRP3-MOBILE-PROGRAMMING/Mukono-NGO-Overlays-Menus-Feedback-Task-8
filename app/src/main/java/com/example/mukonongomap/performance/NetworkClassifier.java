package com.example.mukonongomap.performance;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Reads the active network and maps it to a {@link NetworkClass}. Needs ACCESS_NETWORK_STATE. */
public final class NetworkClassifier implements TraceContext.NetworkProvider {

    private final ConnectivityManager cm;

    public NetworkClassifier(Context context) {
        this.cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public NetworkClass current() {
        try {
            if (cm == null) return NetworkClass.UNKNOWN;
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            if (caps == null) return NetworkClass.OFFLINE;
            boolean connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            return NetworkClass.classify(connected, wifi, caps.getLinkDownstreamBandwidthKbps());
        } catch (RuntimeException e) {
            // SecurityException on some OEM builds; never let monitoring crash the app.
            return NetworkClass.UNKNOWN;
        }
    }
}
