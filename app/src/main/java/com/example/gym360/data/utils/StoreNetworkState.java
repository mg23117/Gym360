package com.example.gym360.data.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

public final class StoreNetworkState {
    private StoreNetworkState() {
    }

    public static boolean online(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null)
            return false;
        Network active = cm.getActiveNetwork();
        NetworkCapabilities cap = active == null ? null : cm.getNetworkCapabilities(active);
        return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}

