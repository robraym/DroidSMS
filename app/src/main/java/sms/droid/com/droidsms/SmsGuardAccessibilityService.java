package sms.droid.com.droidsms;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.biometric.BiometricManager;

public class SmsGuardAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppLockGuard";
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private String lastPackageName = "";
    private String lastPromptPackageName = "";
    private long lastPromptAt = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        debug("service connected");
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
        if (SystemPackages.isHomeOrLauncherSurface(this, packageName)) {
            debug("event package=" + packageName + " decision=home_launcher clear_unlock");
            AuthStore.clearUnlock(this);
            lastPromptPackageName = "";
            lastPromptAt = 0;
            lastPackageName = packageName;
            return;
        }

        if (SystemPackages.shouldIgnoreAccessibilityEvent(this, packageName)) {
            debug("event package=" + packageName + " decision=ignored_system");
            lastPackageName = packageName;
            return;
        }

        if (!isBiometricReady()) {
            debug("event package=" + packageName + " decision=biometric_not_ready");
            AuthStore.clearUnlock(this);
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        }

        if (TrustedWifi.isCurrentWifiTrusted(this) && !AuthStore.isRemovalControlPackage(packageName)) {
            debug("event package=" + packageName + " decision=trusted_wifi_skip");
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        }

        boolean protectedPackage = AuthStore.isPackageProtected(this, packageName);

        if (!protectedPackage) {
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
            lastPackageName = packageName;
            return;
        }

        long now = System.currentTimeMillis();
        if (TextUtils.equals(lastPromptPackageName, packageName) && now - lastPromptAt < 2500) {
            debug("event package=" + packageName + " decision=debounced");
            return;
        }

        lastPackageName = packageName;
        lastPromptPackageName = packageName;
        lastPromptAt = now;

        Intent intent = new Intent(this, GuardActivity.class);
        intent.putExtra(GuardActivity.EXTRA_TARGET_PACKAGE, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        debug("event package=" + packageName + " decision=start_guard");
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Toast.makeText(this, R.string.app_protection_disabled_message, Toast.LENGTH_SHORT).show();
        return super.onUnbind(intent);
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
        return biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }
}
