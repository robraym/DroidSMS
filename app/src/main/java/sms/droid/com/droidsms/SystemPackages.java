package sms.droid.com.droidsms;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SystemPackages {
    private SystemPackages() {
    }

    static boolean isHomeOrLauncherSurface(Context context, String packageName) {
        if (isSettingsPackage(packageName)) {
            return false;
        }

        return isLauncherPackage(packageName)
                || isLauncherSearchPackage(packageName)
                || isHomePackage(context, packageName);
    }

    static boolean isProtectablePackage(Context context, String packageName) {
        return !TextUtils.isEmpty(packageName)
                && !TextUtils.equals(packageName, context.getPackageName())
                && !isNeutralSystemPackage(context, packageName);
    }

    static boolean isSettingsPackage(String packageName) {
        return TextUtils.equals(packageName, "com.android.settings")
                || TextUtils.equals(packageName, "com.samsung.android.app.settings")
                || TextUtils.equals(packageName, "com.samsung.android.settings");
    }

    static boolean isSecureFolderPackage(String packageName) {
        return TextUtils.equals(packageName, "com.samsung.knox.securefolder")
                || TextUtils.equals(packageName, "com.samsung.android.knox.containercore")
                || TextUtils.equals(packageName, "com.samsung.android.container");
    }

    static boolean isSettingsCredentialComponent(CharSequence className) {
        if (className == null) {
            return false;
        }

        String normalizedClassName = className.toString().toLowerCase(java.util.Locale.ROOT);
        return normalizedClassName.contains("confirmdevicecredentialactivity");
    }

    static boolean isSettingsAddNetworkComponent(CharSequence className) {
        if (className == null) {
            return false;
        }

        String normalizedClassName = className.toString().toLowerCase(java.util.Locale.ROOT);
        return normalizedClassName.contains("wifi.addappnetworks")
                || normalizedClassName.contains("addappnetworksactivity");
    }

    static boolean shouldIgnoreAccessibilityEvent(Context context, String packageName) {
        if (isSettingsPackage(packageName)) {
            return false;
        }

        return TextUtils.equals(packageName, context.getPackageName())
                || TextUtils.equals(packageName, "android")
                || TextUtils.equals(packageName, "com.android.systemui")
                || isSecureFolderPackage(packageName)
                || isHomeOrLauncherSurface(context, packageName)
                || isInputMethodPackage(context, packageName)
                || isAssistantOrSearchPackage(context, packageName)
                || TextUtils.equals(packageName, "com.samsung.android.biometrics.app.setting")
                || TextUtils.equals(packageName, "com.samsung.android.spay")
                || TextUtils.equals(packageName, "com.google.android.permissioncontroller")
                || TextUtils.equals(packageName, "com.android.permissioncontroller");
    }

    static Set<String> getNonProtectablePackages(Context context) {
        Set<String> packageNames = new HashSet<>();
        packageNames.add(context.getPackageName());
        packageNames.add("android");
        packageNames.add("com.android.systemui");
        packageNames.add("com.samsung.android.biometrics.app.setting");
        packageNames.add("com.samsung.android.spay");
        packageNames.add("com.google.android.permissioncontroller");
        packageNames.add("com.android.permissioncontroller");
        packageNames.add("com.samsung.knox.securefolder");
        packageNames.add("com.samsung.android.knox.containercore");
        packageNames.add("com.samsung.android.container");

        addKnownLauncherPackages(packageNames);
        addKnownLauncherSearchPackages(packageNames);
        addKnownAssistantPackages(packageNames);
        packageNames.addAll(getInputMethodPackages(context));
        addHomePackages(context, packageNames);
        addSecureSettingPackage(context, packageNames, "assistant");
        addSecureSettingPackage(context, packageNames, "voice_interaction_service");
        return packageNames;
    }

    static boolean isNeutralSystemPackage(Context context, String packageName) {
        return shouldIgnoreAccessibilityEvent(context, packageName);
    }

    private static boolean isLauncherPackage(String packageName) {
        return TextUtils.equals(packageName, "com.sec.android.app.launcher")
                || TextUtils.equals(packageName, "com.android.launcher3")
                || TextUtils.equals(packageName, "com.google.android.apps.nexuslauncher")
                || TextUtils.equals(packageName, "com.miui.home")
                || TextUtils.equals(packageName, "com.oppo.launcher")
                || TextUtils.equals(packageName, "com.huawei.android.launcher")
                || TextUtils.equals(packageName, "net.oneplus.launcher")
                || TextUtils.equals(packageName, "com.vivo.launcher")
                || TextUtils.equals(packageName, "com.realme.launcher")
                || TextUtils.equals(packageName, "com.motorola.launcher3");
    }

    private static boolean isLauncherSearchPackage(String packageName) {
        return TextUtils.equals(packageName, "com.samsung.android.app.galaxyfinder")
                || TextUtils.equals(packageName, "com.samsung.android.finder")
                || TextUtils.equals(packageName, "com.google.android.googlequicksearchbox");
    }

    private static boolean isAssistantOrSearchPackage(Context context, String packageName) {
        Set<String> packageNames = new HashSet<>();
        addKnownAssistantPackages(packageNames);
        if (packageNames.contains(packageName)) {
            return true;
        }

        return TextUtils.equals(packageName, getPackageFromSecureSetting(context, "assistant"))
                || TextUtils.equals(packageName, getPackageFromSecureSetting(context, "voice_interaction_service"));
    }

    static boolean isInputMethodPackage(Context context, String packageName) {
        return getInputMethodPackages(context).contains(packageName);
    }

    static Set<String> getInputMethodPackages(Context context) {
        Set<String> packageNames = new HashSet<>();
        addKnownInputMethodPackages(packageNames);
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null) {
            return packageNames;
        }

        List<InputMethodInfo> inputMethods = inputMethodManager.getInputMethodList();
        for (InputMethodInfo inputMethodInfo : inputMethods) {
            packageNames.add(inputMethodInfo.getPackageName());
        }

        return packageNames;
    }

    private static void addKnownLauncherPackages(Set<String> packageNames) {
        packageNames.add("com.sec.android.app.launcher");
        packageNames.add("com.android.launcher3");
        packageNames.add("com.google.android.apps.nexuslauncher");
        packageNames.add("com.miui.home");
        packageNames.add("com.oppo.launcher");
        packageNames.add("com.huawei.android.launcher");
        packageNames.add("net.oneplus.launcher");
        packageNames.add("com.vivo.launcher");
        packageNames.add("com.realme.launcher");
        packageNames.add("com.motorola.launcher3");
    }

    private static void addKnownLauncherSearchPackages(Set<String> packageNames) {
        packageNames.add("com.samsung.android.app.galaxyfinder");
        packageNames.add("com.samsung.android.finder");
        packageNames.add("com.google.android.googlequicksearchbox");
    }

    private static void addKnownAssistantPackages(Set<String> packageNames) {
        packageNames.add("com.google.android.googlequicksearchbox");
        packageNames.add("com.google.android.apps.searchlite");
        packageNames.add("com.samsung.android.bixby.agent");
        packageNames.add("com.samsung.android.app.spage");
        packageNames.add("com.samsung.android.bixby.wakeup");
    }

    private static void addKnownInputMethodPackages(Set<String> packageNames) {
        packageNames.add("com.samsung.android.honeyboard");
        packageNames.add("com.google.android.inputmethod.latin");
        packageNames.add("com.touchtype.swiftkey");
        packageNames.add("com.microsoft.swiftkey");
    }

    private static void addSecureSettingPackage(Context context, Set<String> packageNames, String key) {
        String packageName = getPackageFromSecureSetting(context, key);
        if (!TextUtils.isEmpty(packageName)) {
            packageNames.add(packageName);
        }
    }

    private static String getPackageFromSecureSetting(Context context, String key) {
        String value = Settings.Secure.getString(context.getContentResolver(), key);
        if (TextUtils.isEmpty(value)) {
            return "";
        }

        int separator = value.indexOf('/');
        return separator > 0 ? value.substring(0, separator) : value;
    }

    private static boolean isHomePackage(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }

        Set<String> packageNames = new HashSet<>();
        addHomePackages(context, packageNames);
        return packageNames.contains(packageName);
    }

    private static void addHomePackages(Context context, Set<String> packageNames) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> homeActivities = packageManager.queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : homeActivities) {
            if (resolveInfo.activityInfo != null) {
                packageNames.add(resolveInfo.activityInfo.packageName);
            }
        }
    }
}
