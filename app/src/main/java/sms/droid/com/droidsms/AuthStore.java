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
    static final int MAX_TRUSTED_WIFI_NETWORKS = 5;
    private static final String KEY_PROTECTED_PACKAGES = "protected_packages";
    private static final String KEY_TRUSTED_WIFI_SSID = "trusted_wifi_ssid";
    private static final String KEY_TRUSTED_WIFI_SSIDS = "trusted_wifi_ssids";
    private static final String KEY_UNLOCKED_PACKAGE = "unlocked_package";
    private static final String KEY_UNLOCKED_PACKAGES = "unlocked_packages";
    private static final String KEY_UNLOCKED_UNTIL = "unlocked_until";
    private static final String KEY_KEEP_UNLOCKED_ON_MINIMIZE = "keep_unlocked_on_minimize";
    private static final String KEY_SETTINGS_NAVIGATION_ALLOWED_UNTIL = "settings_navigation_allowed_until";
    private static final String KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted";
    private static final long UNLOCK_WINDOW_MS = 30 * 60 * 1000;
    private static final long SETTINGS_NAVIGATION_WINDOW_MS = 2 * 60 * 1000;
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
    private static final Set<String> PACKAGE_INSTALLER_PACKAGES = new HashSet<>(Arrays.asList(
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
                .remove(KEY_UNLOCKED_PACKAGES)
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

        Set<String> unlockedPackages = getUnlockedPackages(context);
        unlockedPackages.add(packageName);

        prefs(context).edit()
                .putStringSet(KEY_UNLOCKED_PACKAGES, unlockedPackages)
                .remove(KEY_UNLOCKED_PACKAGE)
                .putLong(KEY_UNLOCKED_UNTIL, System.currentTimeMillis() + UNLOCK_WINDOW_MS)
                .apply();
    }

    static boolean isUnlocked(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        long unlockedUntil = preferences.getLong(KEY_UNLOCKED_UNTIL, 0);
        if (System.currentTimeMillis() >= unlockedUntil) {
            clearUnlock(context);
            return false;
        }

        return getUnlockedPackages(context).contains(packageName);
    }

    static void clearUnlock(Context context) {
        prefs(context).edit()
                .remove(KEY_UNLOCKED_PACKAGE)
                .remove(KEY_UNLOCKED_PACKAGES)
                .remove(KEY_UNLOCKED_UNTIL)
                .apply();
    }

    private static Set<String> getUnlockedPackages(Context context) {
        SharedPreferences preferences = prefs(context);
        Set<String> unlockedPackages = new HashSet<>(
                preferences.getStringSet(KEY_UNLOCKED_PACKAGES, new HashSet<>()));
        String legacyPackage = preferences.getString(KEY_UNLOCKED_PACKAGE, "");
        if (!TextUtils.isEmpty(legacyPackage)) {
            unlockedPackages.add(legacyPackage);
        }
        return unlockedPackages;
    }

    static void allowSettingsNavigation(Context context) {
        prefs(context).edit()
                .putLong(KEY_SETTINGS_NAVIGATION_ALLOWED_UNTIL,
                        System.currentTimeMillis() + SETTINGS_NAVIGATION_WINDOW_MS)
                .apply();
    }

    static boolean isSettingsNavigationAllowed(Context context) {
        return System.currentTimeMillis()
                < prefs(context).getLong(KEY_SETTINGS_NAVIGATION_ALLOWED_UNTIL, 0);
    }

    static void clearSettingsNavigationAllowance(Context context) {
        prefs(context).edit()
                .remove(KEY_SETTINGS_NAVIGATION_ALLOWED_UNTIL)
                .apply();
    }

    static boolean isKeepUnlockedOnMinimizeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_UNLOCKED_ON_MINIMIZE, false);
    }

    static void setKeepUnlockedOnMinimizeEnabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putBoolean(KEY_KEEP_UNLOCKED_ON_MINIMIZE, enabled)
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
        return filterVisibleProtectedPackages(context, savedPackages);
    }

    static boolean isPackageProtected(Context context, String packageName) {
        if (SystemPackages.isInputMethodPackage(context, packageName)) {
            return false;
        }

        if (SystemPackages.isSecureFolderPackage(packageName)) {
            return false;
        }

        if (SystemPackages.isSettingsPackage(packageName)) {
            Set<String> protectedPackages = getProtectedPackages(context);
            for (String settingsPackage : SETTINGS_PACKAGES) {
                if (protectedPackages.contains(settingsPackage)) {
                    return true;
                }
            }
        }

        return !TextUtils.isEmpty(packageName)
                && getProtectedPackages(context).contains(packageName);
    }

    static boolean isPackageInstallerPackage(String packageName) {
        return !TextUtils.isEmpty(packageName) && PACKAGE_INSTALLER_PACKAGES.contains(packageName);
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

    static void initializeProtectedPackagesIfNeeded(Context context, Set<String> packageNames) {
        SharedPreferences preferences = prefs(context);
        if (preferences.contains(KEY_PROTECTED_PACKAGES)) {
            return;
        }

        preferences.edit()
                .putStringSet(KEY_PROTECTED_PACKAGES, filterVisibleProtectedPackages(context, packageNames))
                .apply();
    }

    static void setPackageProtected(Context context, String packageName, boolean protectedApp) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        Set<String> protectedPackages = getProtectedPackages(context);
        if (SystemPackages.isInputMethodPackage(context, packageName)) {
            protectedPackages.remove(packageName);
        } else if (SystemPackages.isSecureFolderPackage(packageName)) {
            protectedPackages.remove(packageName);
        } else if (SystemPackages.isSettingsPackage(packageName)) {
            if (protectedApp) {
                protectedPackages.addAll(SETTINGS_PACKAGES);
            } else {
                protectedPackages.removeAll(SETTINGS_PACKAGES);
            }
        } else if (isPackageInstallerPackage(packageName)) {
            protectedPackages.remove(packageName);
        } else if (protectedApp) {
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
        Set<String> inputMethodPackages = SystemPackages.getInputMethodPackages(context);
        for (String packageName : packageNames) {
            if (!inputMethodPackages.contains(packageName)
                    && !SystemPackages.isSecureFolderPackage(packageName)
                    && !isPackageInstallerPackage(packageName)) {
                filteredPackages.add(packageName);
            }
        }
        return filteredPackages;
    }

    static Set<String> getTrustedWifiSsids(Context context) {
        SharedPreferences preferences = prefs(context);
        Set<String> trustedSsids = new HashSet<>(
                preferences.getStringSet(KEY_TRUSTED_WIFI_SSIDS, new HashSet<>()));
        String legacySsid = preferences.getString(KEY_TRUSTED_WIFI_SSID, "");
        if (!TextUtils.isEmpty(legacySsid)) {
            trustedSsids.add(legacySsid);
            preferences.edit()
                    .putStringSet(KEY_TRUSTED_WIFI_SSIDS, trustedSsids)
                    .remove(KEY_TRUSTED_WIFI_SSID)
                    .apply();
        }
        return trustedSsids;
    }

    static boolean addTrustedWifiSsid(Context context, String ssid) {
        if (TextUtils.isEmpty(ssid)) {
            return false;
        }

        Set<String> trustedSsids = getTrustedWifiSsids(context);
        if (trustedSsids.contains(ssid)) {
            return true;
        }
        if (trustedSsids.size() >= MAX_TRUSTED_WIFI_NETWORKS) {
            return false;
        }

        trustedSsids.add(ssid);
        prefs(context).edit()
                .putStringSet(KEY_TRUSTED_WIFI_SSIDS, trustedSsids)
                .apply();
        return true;
    }

    static void removeTrustedWifiSsid(Context context, String ssid) {
        Set<String> trustedSsids = getTrustedWifiSsids(context);
        trustedSsids.remove(ssid);
        prefs(context).edit()
                .putStringSet(KEY_TRUSTED_WIFI_SSIDS, trustedSsids)
                .apply();
    }

    static void clearTrustedWifi(Context context) {
        prefs(context).edit()
                .remove(KEY_TRUSTED_WIFI_SSID)
                .remove(KEY_TRUSTED_WIFI_SSIDS)
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
