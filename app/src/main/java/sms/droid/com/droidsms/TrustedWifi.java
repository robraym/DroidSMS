package sms.droid.com.droidsms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

final class TrustedWifi {
    private static final String UNKNOWN_SSID = "<unknown ssid>";

    enum TrustState {
        TRUSTED,
        CONFIRMATION_REQUIRED,
        NOT_TRUSTED
    }

    private TrustedWifi() {
    }

    static String getRequiredPermission() {
        return Manifest.permission.ACCESS_FINE_LOCATION;
    }

    static boolean hasRequiredPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, getRequiredPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isLocationEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getApplicationContext()
                .getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return locationManager.isLocationEnabled();
            }
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception exception) {
            return false;
        }
    }

    static void markCurrentWifiSessionTrusted(Context context, String ssid) {
        Network wifiNetwork = getConnectedWifiNetwork(context);
        if (wifiNetwork == null || TextUtils.isEmpty(ssid)) {
            clearTrustedSession(context);
            return;
        }

        AuthStore.saveTrustedWifiSession(
                context,
                wifiNetwork.getNetworkHandle(),
                ssid,
                getBootCount(context));
    }

    static boolean isTrustedSessionActive(Context context) {
        long savedHandle = AuthStore.getTrustedWifiSessionHandle(context);
        String savedSsid = AuthStore.getTrustedWifiSessionSsid(context);
        if (savedHandle == 0L || TextUtils.isEmpty(savedSsid)) {
            return false;
        }

        int savedBootCount = AuthStore.getTrustedWifiSessionBootCount(context);
        int currentBootCount = getBootCount(context);
        if (savedBootCount >= 0 && currentBootCount >= 0 && savedBootCount != currentBootCount) {
            clearTrustedSession(context);
            return false;
        }

        if (!AuthStore.getTrustedWifiSsids(context).contains(savedSsid)
                || findWifiNetworkByHandle(context, savedHandle) == null) {
            clearTrustedSession(context);
            return false;
        }
        return true;
    }

    static void refreshTrustedSessionState(Context context) {
        isTrustedSessionActive(context);
    }

    static String getCurrentSsid(Context context) {
        if (!hasRequiredPermission(context)) {
            return "";
        }

        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return "";
        }

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return "";
        }

        String ssid;
        try {
            ssid = normalizeSsid(wifiInfo.getSSID());
        } catch (SecurityException exception) {
            return "";
        }
        if (TextUtils.isEmpty(ssid) || TextUtils.equals(ssid, UNKNOWN_SSID)) {
            return "";
        }
        return ssid;
    }

    static boolean isCurrentWifiTrusted(Context context) {
        return getTrustState(context) == TrustState.TRUSTED;
    }

    static TrustState getTrustState(Context context) {
        if (isTrustedSessionActive(context)) {
            return TrustState.TRUSTED;
        }

        if (AuthStore.getTrustedWifiSsids(context).isEmpty()) {
            return TrustState.NOT_TRUSTED;
        }

        if (getConnectedWifiNetwork(context) == null) {
            clearTrustedSession(context);
            return TrustState.NOT_TRUSTED;
        }

        if (!hasRequiredPermission(context) || !isLocationEnabled(context)) {
            return TrustState.CONFIRMATION_REQUIRED;
        }

        String currentSsid = getCurrentSsid(context);
        if (TextUtils.isEmpty(currentSsid)) {
            return TrustState.CONFIRMATION_REQUIRED;
        }

        if (AuthStore.getTrustedWifiSsids(context).contains(currentSsid)) {
            markCurrentWifiSessionTrusted(context, currentSsid);
            return TrustState.TRUSTED;
        }
        return TrustState.NOT_TRUSTED;
    }

    static void clearTrustedSession(Context context) {
        AuthStore.clearTrustedWifiSession(context);
    }

    static void onWifiNetworkLost(Context context, Network network) {
        if (network != null
                && AuthStore.getTrustedWifiSessionHandle(context) == network.getNetworkHandle()) {
            clearTrustedSession(context);
        }
    }

    private static Network getConnectedWifiNetwork(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return null;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (isWifiNetwork(connectivityManager, activeNetwork)) {
            return activeNetwork;
        }

        for (Network network : connectivityManager.getAllNetworks()) {
            if (isWifiNetwork(connectivityManager, network)) {
                return network;
            }
        }
        return null;
    }

    private static Network findWifiNetworkByHandle(Context context, long networkHandle) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return null;
        }

        for (Network network : connectivityManager.getAllNetworks()) {
            if (network.getNetworkHandle() == networkHandle && isWifiNetwork(connectivityManager, network)) {
                return network;
            }
        }
        return null;
    }

    private static boolean isWifiNetwork(ConnectivityManager connectivityManager, Network network) {
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private static int getBootCount(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.BOOT_COUNT,
                    -1);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static String normalizeSsid(String ssid) {
        if (TextUtils.isEmpty(ssid)) {
            return "";
        }

        String value = ssid.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
