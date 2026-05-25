package sms.droid.com.droidsms;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class AuthStore {
    static final String PREFS_NAME = "droidsms_security";
    static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_PROTECTED_PACKAGES = "protected_packages";
    private static final String KEY_TRUSTED_WIFI_SSID = "trusted_wifi_ssid";
    private static final String KEY_UNLOCKED_PACKAGE = "unlocked_package";
    private static final String KEY_UNLOCKED_UNTIL = "unlocked_until";
    private static final String KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted";
    private static final long UNLOCK_WINDOW_MS = 30 * 60 * 1000;
    private static final Set<String> DEFAULT_PROTECTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.android.settings",
            "com.samsung.android.app.settings",
            "com.samsung.android.settings",
            "com.google.android.gm",
            "com.samsung.android.email.provider",
            "com.android.email",
            "com.google.android.email"
    ));
    private static final Set<String> SETTINGS_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.samsung.android.app.settings",
            "com.samsung.android.settings"
    ));
    private static final Set<String> REMOVAL_CONTROL_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oplus.packageinstaller",
            "com.huawei.android.packageinstaller",
            "com.vivo.packageinstaller"
    ));
    private static final Set<String> REMOVAL_ONLY_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oplus.packageinstaller",
            "com.huawei.android.packageinstaller",
            "com.vivo.packageinstaller"
    ));

    private AuthStore() {
    }

    static boolean hasPassword(Context context) {
        return prefs(context).contains(KEY_PASSWORD_HASH);
    }

    static void savePassword(Context context, String password) {
        prefs(context).edit().putString(KEY_PASSWORD_HASH, sha256(password)).apply();
    }

    static void clearPassword(Context context) {
        prefs(context).edit()
                .remove(KEY_PASSWORD_HASH)
                .remove(KEY_UNLOCKED_PACKAGE)
                .remove(KEY_UNLOCKED_UNTIL)
                .apply();
    }

    static boolean matchesPassword(Context context, String password) {
        return TextUtils.equals(prefs(context).getString(KEY_PASSWORD_HASH, ""), sha256(password));
    }

    static void markUnlocked(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        prefs(context).edit()
                .putString(KEY_UNLOCKED_PACKAGE, packageName)
                .putLong(KEY_UNLOCKED_UNTIL, System.currentTimeMillis() + UNLOCK_WINDOW_MS)
                .apply();
    }

    static boolean isUnlocked(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        String unlockedPackage = preferences.getString(KEY_UNLOCKED_PACKAGE, "");
        long unlockedUntil = preferences.getLong(KEY_UNLOCKED_UNTIL, 0);
        if (System.currentTimeMillis() >= unlockedUntil) {
            return false;
        }

        if (TextUtils.equals(unlockedPackage, packageName)) {
            return true;
        }

        return isRemovalControlPackage(unlockedPackage) && isRemovalControlPackage(packageName);
    }

    static void clearUnlock(Context context) {
        prefs(context).edit()
                .remove(KEY_UNLOCKED_PACKAGE)
                .remove(KEY_UNLOCKED_UNTIL)
                .apply();
    }

    static boolean hasAcceptedAccessibilityDisclosure(Context context) {
        return prefs(context).getBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, false);
    }

    static void acceptAccessibilityDisclosure(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, true)
                .apply();
    }

    static Set<String> getProtectedPackages(Context context) {
        SharedPreferences preferences = prefs(context);
        Set<String> savedPackages = preferences.getStringSet(KEY_PROTECTED_PACKAGES, null);
        if (savedPackages == null) {
            return new HashSet<>(DEFAULT_PROTECTED_PACKAGES);
        }
        Set<String> protectedPackages = filterVisibleProtectedPackages(context, savedPackages);
        if (isRemovalControlActive(context)) {
            protectedPackages.addAll(REMOVAL_CONTROL_PACKAGES);
        }
        return protectedPackages;
    }

    static boolean isPackageProtected(Context context, String packageName) {
        if (SystemPackages.isSettingsPackage(packageName)) {
            Set<String> protectedPackages = getProtectedPackages(context);
            for (String settingsPackage : SETTINGS_PACKAGES) {
                if (protectedPackages.contains(settingsPackage)) {
                    return true;
                }
            }
        }

        return !TextUtils.isEmpty(packageName)
                && ((isRemovalControlPackage(packageName) && isRemovalControlActive(context))
                || getProtectedPackages(context).contains(packageName));
    }

    static boolean isRemovalControlPackage(String packageName) {
        return !TextUtils.isEmpty(packageName) && REMOVAL_CONTROL_PACKAGES.contains(packageName);
    }

    static boolean isRemovalOnlyPackage(String packageName) {
        return !TextUtils.isEmpty(packageName) && REMOVAL_ONLY_PACKAGES.contains(packageName);
    }

    static boolean isRemovalControlActive(Context context) {
        DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName componentName = new ComponentName(context, SmsLockDeviceAdminReceiver.class);
        return devicePolicyManager != null && devicePolicyManager.isAdminActive(componentName);
    }

    static void setProtectedPackages(Context context, Set<String> packageNames) {
        prefs(context).edit()
                .putStringSet(KEY_PROTECTED_PACKAGES, filterVisibleProtectedPackages(context, packageNames))
                .apply();
    }

    static void setPackageProtected(Context context, String packageName, boolean protectedApp) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        Set<String> protectedPackages = getProtectedPackages(context);
        if (SystemPackages.isSettingsPackage(packageName)) {
            if (protectedApp) {
                protectedPackages.addAll(SETTINGS_PACKAGES);
            } else {
                protectedPackages.removeAll(SETTINGS_PACKAGES);
            }
        } else if (isRemovalOnlyPackage(packageName) && !isRemovalControlActive(context)) {
            protectedPackages.remove(packageName);
        } else if (protectedApp) {
            protectedPackages.add(packageName);
        } else if (isRemovalOnlyPackage(packageName) && isRemovalControlActive(context)) {
            protectedPackages.add(packageName);
        } else {
            protectedPackages.remove(packageName);
        }

        prefs(context).edit()
                .putStringSet(KEY_PROTECTED_PACKAGES, protectedPackages)
                .apply();
    }

    private static Set<String> filterVisibleProtectedPackages(Context context, Set<String> packageNames) {
        Set<String> filteredPackages = new HashSet<>();
        boolean removalControlActive = isRemovalControlActive(context);
        for (String packageName : packageNames) {
            if (!isRemovalOnlyPackage(packageName) || removalControlActive) {
                filteredPackages.add(packageName);
            }
        }
        return filteredPackages;
    }

    static String getTrustedWifiSsid(Context context) {
        return prefs(context).getString(KEY_TRUSTED_WIFI_SSID, "");
    }

    static void setTrustedWifiSsid(Context context, String ssid) {
        prefs(context).edit()
                .putString(KEY_TRUSTED_WIFI_SSID, ssid)
                .apply();
    }

    static void clearTrustedWifi(Context context) {
        prefs(context).edit()
                .remove(KEY_TRUSTED_WIFI_SSID)
                .apply();
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
