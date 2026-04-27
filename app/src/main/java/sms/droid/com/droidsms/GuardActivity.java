package sms.droid.com.droidsms;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public class GuardActivity extends AppCompatActivity {
    public static final String EXTRA_TARGET_PACKAGE = "sms.droid.com.droidsms.extra.TARGET_PACKAGE";
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    private String targetPackage;
    private boolean promptShown;
    private boolean retriedAfterSystemCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        protectBackground();
        setContentView(R.layout.activity_guard);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!promptShown) {
            promptShown = true;
            showBiometricPrompt();
        }
    }

    private void showBiometricPrompt() {
        if (!isBiometricReady()) {
            closeToHome();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                AuthStore.markUnlocked(GuardActivity.this, targetPackage);
                finish();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_CANCELED && !retriedAfterSystemCancel) {
                    retriedAfterSystemCancel = true;
                    promptShown = false;
                    getWindow().getDecorView().postDelayed(() -> {
                        if (!isFinishing()) {
                            showBiometricPrompt();
                        }
                    }, 250);
                    return;
                }
                closeToHome();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private boolean isBiometricReady() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
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
}
