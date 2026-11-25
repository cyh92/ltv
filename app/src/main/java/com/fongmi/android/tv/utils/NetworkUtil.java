package com.fongmi.android.tv.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.fongmi.android.tv.App;

/**
 * 0.1.104：网络类型判断，供预载的“仅 Wi-Fi”开关用。
 * 只读当前网络的传输类型，不发起任何网络请求；
 * 权限 ACCESS_NETWORK_STATE 已在 AndroidManifest 声明。
 */
public final class NetworkUtil {

    private NetworkUtil() {
    }

    public static boolean isWifi() {
        try {
            ConnectivityManager manager = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network network = manager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception ignored) {
            return false;
        }
    }
}
