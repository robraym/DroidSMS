package sms.droid.com.droidsms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

final class TrustedWifi {
    private static final String UNKNOWN_SSID = "<unknown ssid>";

    private TrustedWifi() {
    }

    static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static String getCurrentSsid(Context context) {
        if (!hasLocationPermission(context)) {
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

        String ssid = normalizeSsid(wifiInfo.getSSID());
        if (TextUtils.isEmpty(ssid) || TextUtils.equals(ssid, UNKNOWN_SSID)) {
            return "";
        }
        return ssid;
    }

    static boolean isCurrentWifiTrusted(Context context) {
        String currentSsid = getCurrentSsid(context);
        return !TextUtils.isEmpty(currentSsid)
                && AuthStore.getTrustedWifiSsids(context).contains(currentSsid);
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
