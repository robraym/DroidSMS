package sms.droid.com.droidsms;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_TARGET_PACKAGE = "sms.droid.com.droidsms.extra.TARGET_PACKAGE";

    private TextView txtTitle;
    private TextView txtSubtitle;
    private TextView txtStatus;
    private TextInputLayout inputPasswordLayout;
    private TextInputLayout inputConfirmPasswordLayout;
    private TextInputEditText inputPassword;
    private TextInputEditText inputConfirmPassword;
    private MaterialButton btnPrimary;
    private MaterialButton btnBiometric;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;
    private boolean createMode;
    private boolean settingsUnlocked;
    private String targetPackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, SmsLockDeviceAdminReceiver.class);
        bindViews();
        configureActions();
        showCurrentState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE);
        if (!hasTargetPackage()) {
            settingsUnlocked = false;
        }
        showCurrentState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (txtTitle != null) {
            showCurrentState();
        }
    }

    private void bindViews() {
        txtTitle = findViewById(R.id.txtTitle);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        txtStatus = findViewById(R.id.txtStatus);
        inputPasswordLayout = findViewById(R.id.inputPasswordLayout);
        inputConfirmPasswordLayout = findViewById(R.id.inputConfirmPasswordLayout);
        inputPassword = findViewById(R.id.inputPassword);
        inputConfirmPassword = findViewById(R.id.inputConfirmPassword);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnBiometric = findViewById(R.id.btnBiometric);
    }

    private void configureActions() {
        inputPassword.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnPrimary.performClick();
                return true;
            }
            return false;
        });
    }

    private void showCurrentState() {
        createMode = !AuthStore.hasPassword(this);

        txtTitle.setText(createMode || hasTargetPackage() ? R.string.auth_title : R.string.unlocked_title);
        txtSubtitle.setText(createMode
                ? R.string.auth_subtitle_create
                : hasTargetPackage() || !settingsUnlocked ? R.string.auth_subtitle_unlock : R.string.settings_subtitle);
        txtStatus.setText(createMode ? "" : getString(R.string.locked_state));

        inputPassword.setText("");
        inputConfirmPassword.setText("");
        inputPasswordLayout.setError(null);
        inputConfirmPasswordLayout.setError(null);
        inputPasswordLayout.setVisibility(!createMode && !hasTargetPackage() && settingsUnlocked
                ? android.view.View.GONE
                : android.view.View.VISIBLE);
        inputConfirmPasswordLayout.setVisibility(createMode ? android.view.View.VISIBLE : android.view.View.GONE);

        if (createMode) {
            btnPrimary.setText(R.string.save_password);
            btnPrimary.setOnClickListener(view -> savePassword());
            btnBiometric.setText(R.string.use_fingerprint);
            btnBiometric.setEnabled(false);
            return;
        }

        boolean needsUnlock = hasTargetPackage() || !settingsUnlocked;
        btnPrimary.setText(needsUnlock ? R.string.unlock : R.string.open_accessibility);
        btnPrimary.setOnClickListener(view -> {
            if (needsUnlock) {
                unlockWithPassword();
            } else {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        btnBiometric.setText(needsUnlock
                ? R.string.use_fingerprint
                : isDeviceAdminActive() ? R.string.open_messages : R.string.open_device_admin);
        btnBiometric.setEnabled(needsUnlock ? isBiometricReady() : true);
        btnBiometric.setOnClickListener(view -> {
            if (needsUnlock) {
                showBiometricPrompt();
            } else if (!isDeviceAdminActive()) {
                requestDeviceAdmin();
            } else {
                openDefaultMessages();
            }
        });

        txtStatus.setText(isDeviceAdminActive()
                ? getString(R.string.locked_state) + "\n" + getString(R.string.device_admin_active)
                : getString(R.string.locked_state));
    }

    private void savePassword() {
        String password = getText(inputPassword);
        String confirmation = getText(inputConfirmPassword);
        inputPasswordLayout.setError(null);
        inputConfirmPasswordLayout.setError(null);

        if (password.length() < 4) {
            inputPasswordLayout.setError(getString(R.string.password_short));
            return;
        }

        if (!TextUtils.equals(password, confirmation)) {
            inputConfirmPasswordLayout.setError(getString(R.string.password_mismatch));
            return;
        }

        AuthStore.savePassword(this, password);
        Toast.makeText(this, R.string.password_saved, Toast.LENGTH_SHORT).show();
        showCurrentState();
    }

    private void unlockWithPassword() {
        String password = getText(inputPassword);
        inputPasswordLayout.setError(null);

        if (password.isEmpty()) {
            inputPasswordLayout.setError(getString(R.string.password_required));
            return;
        }

        if (AuthStore.matchesPassword(this, password)) {
            unlockTargetPackage();
        } else {
            inputPasswordLayout.setError(getString(R.string.wrong_password));
        }
    }

    private void unlockTargetPackage() {
        if (hasTargetPackage()) {
            AuthStore.markUnlocked(this, targetPackage);
            openPackage(targetPackage);
            finish();
            return;
        }

        settingsUnlocked = true;
        showCurrentState();
    }

    private void showBiometricPrompt() {
        if (!isBiometricReady()) {
            Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(MainActivity.this, R.string.biometric_success, Toast.LENGTH_SHORT).show();
                unlockTargetPackage();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText(getString(R.string.biometric_negative))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private boolean isBiometricReady() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void openDefaultMessages() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_MESSAGING);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description));
        startActivity(intent);
    }

    private boolean isDeviceAdminActive() {
        return devicePolicyManager != null && devicePolicyManager.isAdminActive(deviceAdminComponent);
    }

    private void openPackage(String packageName) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            openDefaultMessages();
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private boolean hasTargetPackage() {
        return SmsGuardAccessibilityService.isProtectedPackage(targetPackage);
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}
