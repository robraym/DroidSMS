package sms.droid.com.droidsms;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {
    private TextView txtTitle;
    private TextView txtSubtitle;
    private TextView txtStatus;
    private TextInputLayout inputPasswordLayout;
    private TextInputLayout inputConfirmPasswordLayout;
    private MaterialButton btnPrimary;
    private MaterialButton btnBiometric;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, SmsLockDeviceAdminReceiver.class);

        if (isSetupComplete()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        bindViews();
        showSettingsState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isSetupComplete()) {
            finish();
            return;
        }

        if (txtTitle != null) {
            showSettingsState();
        }
    }

    private void bindViews() {
        txtTitle = findViewById(R.id.txtTitle);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        txtStatus = findViewById(R.id.txtStatus);
        inputPasswordLayout = findViewById(R.id.inputPasswordLayout);
        inputConfirmPasswordLayout = findViewById(R.id.inputConfirmPasswordLayout);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnBiometric = findViewById(R.id.btnBiometric);
    }

    private void showSettingsState() {
        boolean accessibilityActive = isAccessibilityServiceActive();
        boolean deviceAdminActive = isDeviceAdminActive();

        txtTitle.setText(R.string.unlocked_title);
        txtSubtitle.setText(R.string.settings_subtitle);
        inputPasswordLayout.setVisibility(View.GONE);
        inputConfirmPasswordLayout.setVisibility(View.GONE);

        btnPrimary.setVisibility(accessibilityActive ? View.GONE : View.VISIBLE);
        btnPrimary.setText(R.string.open_accessibility);
        btnPrimary.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        btnBiometric.setVisibility(deviceAdminActive ? View.GONE : View.VISIBLE);
        btnBiometric.setText(R.string.open_device_admin);
        btnBiometric.setOnClickListener(view -> {
            requestDeviceAdmin();
        });

        String status = "";
        if (accessibilityActive) {
            status = getString(R.string.locked_state);
        }
        if (deviceAdminActive) {
            status = status.isEmpty()
                    ? getString(R.string.device_admin_active)
                    : status + "\n" + getString(R.string.device_admin_active);
        }
        txtStatus.setText(status);
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

    private boolean isAccessibilityServiceActive() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }

        ComponentName service = new ComponentName(this, SmsGuardAccessibilityService.class);
        String serviceName = service.flattenToString();
        return enabledServices.contains(serviceName);
    }

    private boolean isSetupComplete() {
        return isAccessibilityServiceActive() && isDeviceAdminActive();
    }
}
