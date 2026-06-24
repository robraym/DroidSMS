package sms.droid.com.droidsms;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.biometric.BiometricManager;

public class SmsGuardAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppLockGuard";
    private static final int AUTHENTICATORS_WITH_BIOMETRIC = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static final int AUTHENTICATORS_DEVICE_CREDENTIAL =
            BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private String lastPackageName = "";
    private String lastPromptPackageName = "";
    private String lastProtectedPackageName = "";
    private String pendingClosedPackageName = "";
    private boolean screenReceiverRegistered;
    private long lastPromptAt = 0;
    private boolean settingsAddNetworkFlowActive;
    private String activeGuardPackageName = "";
    private boolean guardWindowVisible;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback trustedWifiNetworkCallback;
    private boolean networkCallbackRegistered;
    private final BroadcastReceiver screenLockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                clearSessionState("screen_off");
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        debug("service connected");
        registerScreenLockReceiver();
        registerNetworkCallback();
        TrustedBluetooth.warmUp(this);
        Toast.makeText(this, R.string.app_protection_enabled_message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        CharSequence packageNameValue = event.getPackageName();
        if (packageNameValue == null) {
            return;
        }

        String packageName = packageNameValue.toString();
        CharSequence className = event.getClassName();
        boolean appLockPackage = TextUtils.equals(packageName, getPackageName());
        if (appLockPackage && !TextUtils.isEmpty(activeGuardPackageName)) {
            guardWindowVisible = true;
        } else if (!appLockPackage && !TextUtils.isEmpty(activeGuardPackageName)) {
            guardWindowVisible = false;
        }

        boolean settingsPackage = SystemPackages.isSettingsPackage(packageName);
        boolean settingsCredentialComponent = settingsPackage
                && SystemPackages.isSettingsCredentialComponent(className);
        boolean settingsAddNetworkComponent = settingsPackage
                && SystemPackages.isSettingsAddNetworkComponent(className);
        boolean settingsPanelComponent = settingsPackage
                && SystemPackages.isSettingsPanelComponent(className);

        if (settingsAddNetworkComponent || settingsPanelComponent) {
            settingsAddNetworkFlowActive = true;
        } else if (settingsPackage && !settingsCredentialComponent) {
            settingsAddNetworkFlowActive = false;
        }

        if (settingsCredentialComponent) {
            debug("event package=" + packageName + " class=" + className + " decision=settings_credential_allowed");
            pendingClosedPackageName = "";
            lastPromptPackageName = "";
            lastPromptAt = 0;
            lastPackageName = packageName;
            return;
        }

        if (SystemPackages.isSecureFolderPackage(packageName)) {
            if (hasUnlockedProtectedSession()) {
                debug("event package=" + packageName + " decision=secure_folder_boundary keep_closing_session");
                rememberPendingClosedPackage();
            } else {
                debug("event package=" + packageName + " decision=secure_folder_boundary clear_unlock");
                pendingClosedPackageName = "";
                AuthStore.clearUnlock(this);
            }
            AuthStore.clearSettingsNavigationAllowance(this);
            lastPromptPackageName = "";
            lastPromptAt = 0;
            lastPackageName = packageName;
            settingsAddNetworkFlowActive = false;
            clearActiveGuard();
            return;
        }

        if (SystemPackages.isHomeOrLauncherSurface(this, packageName)) {
            boolean keepUnlocked = AuthStore.isKeepUnlockedOnMinimizeEnabled(this);
            debug("event package=" + packageName + " decision=home_launcher "
                    + (keepUnlocked ? "keep_unlock" : "clear_unlock"));
            if (keepUnlocked) {
                pendingClosedPackageName = "";
            } else {
                rememberPendingClosedPackage();
                AuthStore.clearUnlock(this);
            }
            AuthStore.clearSettingsNavigationAllowance(this);
            lastPromptPackageName = "";
            lastPromptAt = 0;
            lastPackageName = packageName;
            settingsAddNetworkFlowActive = false;
            clearActiveGuard();
            return;
        }

        if (settingsPackage && AuthStore.isSettingsNavigationAllowed(this)) {
            debug("event package=" + packageName + " decision=settings_navigation_allowed");
            lastPackageName = packageName;
            return;
        }

        if (SystemPackages.shouldIgnoreAccessibilityEvent(this, packageName)) {
            debug("event package=" + packageName + " decision=ignored_system");
            lastPackageName = packageName;
            return;
        }

        if (TextUtils.equals(packageName, pendingClosedPackageName)) {
            debug("event package=" + packageName + " decision=closing_echo_skip");
            pendingClosedPackageName = "";
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        } else if (!SystemPackages.isNeutralSystemPackage(this, packageName)) {
            pendingClosedPackageName = "";
        }

        if (!isBiometricReady()) {
            debug("event package=" + packageName + " decision=biometric_not_ready");
            AuthStore.clearUnlock(this);
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        }

        TrustedWifi.TrustState wifiTrustState = TrustedWifi.getTrustState(this);
        if (wifiTrustState == TrustedWifi.TrustState.TRUSTED) {
            debug("event package=" + packageName + " decision=trusted_wifi_skip");
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        }

        if (TrustedBluetooth.isAnyTrustedDeviceConnected(this)) {
            debug("event package=" + packageName + " decision=trusted_bluetooth_skip");
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        }

        boolean protectedPackage = AuthStore.isPackageProtected(this, packageName);

        if (!protectedPackage) {
            if (settingsAddNetworkFlowActive && hasUnlockedSettingsSession()) {
                debug("event package=" + packageName + " decision=settings_add_network_caller_skip");
                lastPackageName = packageName;
                return;
            }

            debug("event package=" + packageName + " decision=not_protected");
            if (!SystemPackages.isNeutralSystemPackage(this, packageName)) {
                AuthStore.clearUnlock(this);
                lastPromptPackageName = "";
                lastPromptAt = 0;
            }
            lastPackageName = packageName;
            return;
        }

        if (AuthStore.isUnlocked(this, packageName)) {
            debug("event package=" + packageName + " decision=already_unlocked");
            if (TextUtils.equals(activeGuardPackageName, packageName)) {
                clearActiveGuard();
            }
            lastProtectedPackageName = packageName;
            lastPackageName = packageName;
            return;
        }

        long now = System.currentTimeMillis();
        if (TextUtils.equals(lastPromptPackageName, packageName) && now - lastPromptAt < 2500) {
            if (TextUtils.equals(activeGuardPackageName, packageName) && !guardWindowVisible) {
                debug("event package=" + packageName + " decision=guard_interrupted_restart");
            } else {
                debug("event package=" + packageName + " decision=debounced");
                return;
            }
        }

        startGuard(packageName, now, wifiTrustState == TrustedWifi.TrustState.CONFIRMATION_REQUIRED);
    }

    private void startGuard(String packageName, long now, boolean wifiConfirmationRequired) {
        lastPackageName = packageName;
        lastProtectedPackageName = packageName;
        lastPromptPackageName = packageName;
        lastPromptAt = now;
        activeGuardPackageName = packageName;
        guardWindowVisible = false;

        Intent intent = new Intent(this, GuardActivity.class);
        intent.putExtra(GuardActivity.EXTRA_TARGET_PACKAGE, packageName);
        intent.putExtra(GuardActivity.EXTRA_WIFI_CONFIRMATION_REQUIRED, wifiConfirmationRequired);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        debug("event package=" + packageName + " decision=start_guard wifi_confirmation="
                + wifiConfirmationRequired);
        startActivity(intent);
    }

    private void clearActiveGuard() {
        activeGuardPackageName = "";
        guardWindowVisible = false;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterScreenLockReceiver();
        unregisterNetworkCallback();
        Toast.makeText(this, R.string.app_protection_disabled_message, Toast.LENGTH_SHORT).show();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        unregisterScreenLockReceiver();
        unregisterNetworkCallback();
        super.onDestroy();
    }

    static boolean isProtectedPackage(String packageName) {
        return !TextUtils.isEmpty(packageName);
    }

    private void debug(String message) {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message);
        }
    }

    private boolean isBiometricReady() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(getAllowedAuthenticators())
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private boolean isNativeBiometricReady() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private int getAllowedAuthenticators() {
        return AuthStore.isNativeBiometricEnabled(this) && isNativeBiometricReady()
                ? AUTHENTICATORS_WITH_BIOMETRIC
                : AUTHENTICATORS_DEVICE_CREDENTIAL;
    }

    private boolean hasUnlockedProtectedSession() {
        return !TextUtils.isEmpty(lastProtectedPackageName)
                && AuthStore.isUnlocked(this, lastProtectedPackageName);
    }

    private boolean hasUnlockedSettingsSession() {
        return SystemPackages.isSettingsPackage(lastProtectedPackageName)
                && AuthStore.isUnlocked(this, lastProtectedPackageName);
    }

    private void clearSessionState(String reason) {
        debug("session decision=clear reason=" + reason);
        AuthStore.clearUnlock(this);
        AuthStore.clearSettingsNavigationAllowance(this);
        lastPromptPackageName = "";
        lastProtectedPackageName = "";
        pendingClosedPackageName = "";
        lastPromptAt = 0;
        settingsAddNetworkFlowActive = false;
        clearActiveGuard();
    }

    private void registerScreenLockReceiver() {
        if (screenReceiverRegistered) {
            return;
        }

        registerReceiver(screenLockReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        screenReceiverRegistered = true;
    }

    private void unregisterScreenLockReceiver() {
        if (!screenReceiverRegistered) {
            return;
        }

        unregisterReceiver(screenLockReceiver);
        screenReceiverRegistered = false;
    }

    private void registerNetworkCallback() {
        if (networkCallbackRegistered) {
            return;
        }

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }

        trustedWifiNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                TrustedWifi.refreshTrustedSessionState(SmsGuardAccessibilityService.this);
            }

            @Override
            public void onLost(Network network) {
                TrustedWifi.onWifiNetworkLost(SmsGuardAccessibilityService.this, network);
            }
        };

        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            connectivityManager.registerNetworkCallback(request, trustedWifiNetworkCallback);
            networkCallbackRegistered = true;
        } catch (RuntimeException exception) {
            trustedWifiNetworkCallback = null;
        }
    }

    private void unregisterNetworkCallback() {
        if (!networkCallbackRegistered || connectivityManager == null || trustedWifiNetworkCallback == null) {
            return;
        }

        try {
            connectivityManager.unregisterNetworkCallback(trustedWifiNetworkCallback);
        } catch (RuntimeException exception) {
            // Already unregistered by the system.
        }
        networkCallbackRegistered = false;
        trustedWifiNetworkCallback = null;
    }

    private void rememberPendingClosedPackage() {
        if (TextUtils.isEmpty(lastProtectedPackageName)
                || SystemPackages.isNeutralSystemPackage(this, lastProtectedPackageName)) {
            return;
        }

        pendingClosedPackageName = lastProtectedPackageName;
    }
}
