package sms.droid.com.droidsms;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.biometric.BiometricManager;

public class SmsGuardAccessibilityService extends AccessibilityService {
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private String lastPackageName = "";
    private String lastPromptPackageName = "";
    private long lastPromptAt = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
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
        if (!isBiometricReady()) {
            AuthStore.clearUnlock(this);
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        }

        if (TrustedWifi.isCurrentWifiTrusted(this)) {
            lastPackageName = packageName;
            lastPromptPackageName = "";
            lastPromptAt = 0;
            return;
        }

        boolean protectedPackage = AuthStore.isPackageProtected(this, packageName);

        if (!protectedPackage) {
            if (!isNeutralSystemPackage(packageName)) {
                AuthStore.clearUnlock(this);
                lastPromptPackageName = "";
                lastPromptAt = 0;
            }
            lastPackageName = packageName;
            return;
        }

        if (AuthStore.isUnlocked(this, packageName)) {
            lastPackageName = packageName;
            return;
        }

        long now = System.currentTimeMillis();
        if (TextUtils.equals(lastPromptPackageName, packageName) && now - lastPromptAt < 2500) {
            return;
        }

        lastPackageName = packageName;
        lastPromptPackageName = packageName;
        lastPromptAt = now;

        Intent intent = new Intent(this, GuardActivity.class);
        intent.putExtra(GuardActivity.EXTRA_TARGET_PACKAGE, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
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

    private boolean isNeutralSystemPackage(String packageName) {
        return TextUtils.equals(packageName, getPackageName())
                || TextUtils.equals(packageName, "android")
                || TextUtils.equals(packageName, "com.android.systemui")
                || TextUtils.equals(packageName, "com.samsung.android.biometrics.app.setting")
                || TextUtils.equals(packageName, "com.google.android.permissioncontroller")
                || TextUtils.equals(packageName, "com.android.permissioncontroller");
    }

    private boolean isBiometricReady() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }
}
