package sms.droid.com.droidsms;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public class GuardActivity extends AppCompatActivity {
    public static final String EXTRA_TARGET_PACKAGE = "sms.droid.com.droidsms.extra.TARGET_PACKAGE";
    public static final String EXTRA_WIFI_CONFIRMATION_REQUIRED =
            "sms.droid.com.droidsms.extra.WIFI_CONFIRMATION_REQUIRED";
    private static final String TAG = "AppLockGuard";
    private static final int REQUEST_TRUSTED_WIFI_PERMISSION = 3001;
    private static final int AUTHENTICATORS_WITH_BIOMETRIC = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static final int AUTHENTICATORS_DEVICE_CREDENTIAL =
            BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    private String targetPackage;
    private boolean promptShown;
    private boolean authenticated;
    private boolean wifiConfirmationRequired;
    private boolean waitingForWifiPermission;
    private View panelTrustedWifiCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        wifiConfirmationRequired = getIntent().getBooleanExtra(EXTRA_WIFI_CONFIRMATION_REQUIRED, false);
        if (TextUtils.isEmpty(targetPackage) || SystemPackages.shouldIgnoreAccessibilityEvent(this, targetPackage)) {
            debug("guard finish invalid_target=" + targetPackage);
            finish();
            return;
        }
        debug("guard created target=" + targetPackage);
        protectBackground();
        setContentView(R.layout.activity_guard);
        bindTrustedWifiPanel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String newTargetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        if (!TextUtils.equals(targetPackage, newTargetPackage)) {
            targetPackage = newTargetPackage;
        }
        wifiConfirmationRequired = intent.getBooleanExtra(EXTRA_WIFI_CONFIRMATION_REQUIRED, false);
        waitingForWifiPermission = false;
        hideTrustedWifiPanel();
        if (!AuthStore.isUnlocked(this, targetPackage)) {
            promptShown = false;
            authenticated = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wifiConfirmationRequired && !authenticated) {
            handleTrustedWifiConfirmation();
            return;
        }

        if (!promptShown) {
            promptShown = true;
            showBiometricPrompt();
        }
    }

    private void handleTrustedWifiConfirmation() {
        TrustedWifi.TrustState trustState = TrustedWifi.getTrustState(this);
        if (trustState == TrustedWifi.TrustState.TRUSTED) {
            debug("guard trusted_wifi_confirmed target=" + targetPackage);
            authenticated = true;
            AuthStore.markUnlocked(this, targetPackage);
            finish();
            return;
        }

        if (trustState == TrustedWifi.TrustState.CONFIRMATION_REQUIRED) {
            if (!TrustedWifi.hasRequiredPermission(this)) {
                requestTrustedWifiPermission();
                return;
            }

            if (!TrustedWifi.isLocationEnabled(this)) {
                showTrustedWifiPanel();
                return;
            }
        }

        debug("guard trusted_wifi_unconfirmed_fallback target=" + targetPackage);
        wifiConfirmationRequired = false;
        showBiometricFallback();
    }

    private void requestTrustedWifiPermission() {
        if (waitingForWifiPermission) {
            return;
        }

        waitingForWifiPermission = true;
        requestPermissions(
                new String[]{TrustedWifi.getRequiredPermission()},
                REQUEST_TRUSTED_WIFI_PERMISSION);
    }

    private void showBiometricFallback() {
        if (promptShown) {
            return;
        }
        promptShown = true;
        hideTrustedWifiPanel();
        showBiometricPrompt();
    }

    private void showBiometricPrompt() {
        hideTrustedWifiPanel();
        if (!isBiometricReady()) {
            debug("guard biometric_not_ready target=" + targetPackage);
            closeToHome();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                debug("guard auth_success target=" + targetPackage);
                authenticated = true;
                AuthStore.markUnlocked(GuardActivity.this, targetPackage);
                finish();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                debug("guard auth_error target=" + targetPackage + " code=" + errorCode + " message=" + errString);
                if (errorCode == BiometricPrompt.ERROR_CANCELED && !authenticated) {
                    promptShown = false;
                    return;
                }
                closeToHome();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(getAllowedAuthenticators())
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_TRUSTED_WIFI_PERMISSION) {
            return;
        }

        waitingForWifiPermission = false;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            handleTrustedWifiConfirmation();
            return;
        }

        debug("guard trusted_wifi_permission_denied target=" + targetPackage);
        wifiConfirmationRequired = false;
        showBiometricFallback();
    }

    private void bindTrustedWifiPanel() {
        panelTrustedWifiCheck = findViewById(R.id.panelTrustedWifiCheck);
        TextView btnOpenLocationSettings = findViewById(R.id.btnOpenLocationSettings);
        TextView btnUseBiometricNow = findViewById(R.id.btnUseBiometricNow);
        btnOpenLocationSettings.setOnClickListener(view -> openLocationSettingsForWifi());
        btnUseBiometricNow.setOnClickListener(view -> {
            debug("guard trusted_wifi_manual_biometric target=" + targetPackage);
            wifiConfirmationRequired = false;
            showBiometricFallback();
        });
    }

    private void showTrustedWifiPanel() {
        if (panelTrustedWifiCheck != null) {
            panelTrustedWifiCheck.setVisibility(View.VISIBLE);
        }
    }

    private void hideTrustedWifiPanel() {
        if (panelTrustedWifiCheck != null) {
            panelTrustedWifiCheck.setVisibility(View.GONE);
        }
    }

    private void openLocationSettingsForWifi() {
        debug("guard trusted_wifi_open_location_settings target=" + targetPackage);
        AuthStore.allowSettingsNavigation(this);
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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

    private void protectBackground() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0f;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            attributes.setBlurBehindRadius(0);
        }

        window.setAttributes(attributes);
    }

    private void closeToHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void debug(String message) {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message);
        }
    }
}
