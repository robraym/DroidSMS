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

    private String targetPackage;
    private boolean promptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        setContentView(R.layout.activity_guard);
        protectBackground();
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
                closeToHome();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setNegativeButtonText(getString(R.string.biometric_negative))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private boolean isBiometricReady() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void protectBackground() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.82f;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            attributes.setBlurBehindRadius(90);
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
