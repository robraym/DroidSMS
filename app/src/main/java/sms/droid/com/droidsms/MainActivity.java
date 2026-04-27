package sms.droid.com.droidsms;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    private TextView txtTitle;
    private TextView txtSubtitle;
    private TextView txtStatus;
    private LinearLayout cardAccessibility;
    private LinearLayout cardDeviceAdmin;
    private LinearLayout cardStatus;
    private LinearLayout listApps;
    private MaterialButton btnPrimary;
    private MaterialButton btnBiometric;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;
    private boolean settingsUnlocked;
    private boolean promptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, SmsLockDeviceAdminReceiver.class);
        setContentView(R.layout.activity_guard);
        showSettingsBiometricPrompt();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsUnlocked && txtTitle != null) {
            showSettingsState();
        }
    }

    private void showSettingsScreen() {
        setContentView(R.layout.activity_main);
        bindViews();
        populateInstalledApps();
        showSettingsState();
    }

    private void showSettingsBiometricPrompt() {
        if (!isBiometricReady()) {
            finish();
            return;
        }

        if (promptShown) {
            return;
        }

        promptShown = true;
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                settingsUnlocked = true;
                showSettingsScreen();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                finish();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void bindViews() {
        txtTitle = findViewById(R.id.txtTitle);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        txtStatus = findViewById(R.id.txtStatus);
        cardAccessibility = findViewById(R.id.cardAccessibility);
        cardDeviceAdmin = findViewById(R.id.cardDeviceAdmin);
        cardStatus = findViewById(R.id.cardStatus);
        listApps = findViewById(R.id.listApps);
        btnPrimary = findViewById(R.id.btnPrimary);
        btnBiometric = findViewById(R.id.btnBiometric);
    }

    private void showSettingsState() {
        boolean accessibilityActive = isAccessibilityServiceActive();
        boolean deviceAdminActive = isDeviceAdminActive();

        txtTitle.setText(R.string.unlocked_title);
        txtSubtitle.setText(R.string.settings_subtitle);

        cardAccessibility.setVisibility(accessibilityActive ? View.GONE : View.VISIBLE);
        btnPrimary.setText(R.string.open_accessibility);
        btnPrimary.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        cardDeviceAdmin.setVisibility(deviceAdminActive ? View.GONE : View.VISIBLE);
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
        cardStatus.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
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

    private void populateInstalledApps() {
        listApps.removeAllViews();

        List<AppEntry> apps = getLaunchableApps();
        addSelectAllRow(apps);

        for (AppEntry app : apps) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(10), 0, dp(10));

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            row.addView(icon, iconParams);

            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setPadding(dp(14), 0, dp(8), 0);

            TextView label = new TextView(this);
            label.setText(app.label);
            label.setTextColor(getColor(R.color.textPrimary));
            label.setTextSize(16);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            textColumn.addView(label);

            TextView packageName = new TextView(this);
            packageName.setText(app.packageName);
            packageName.setTextColor(getColor(R.color.textSecondary));
            packageName.setTextSize(12);
            textColumn.addView(packageName);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            row.addView(textColumn, textParams);

            CheckBox checkBox = new CheckBox(this);
            checkBox.setChecked(AuthStore.isPackageProtected(this, app.packageName));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                    AuthStore.setPackageProtected(this, app.packageName, isChecked));
            row.setOnClickListener(view -> checkBox.setChecked(!checkBox.isChecked()));
            row.addView(checkBox, new LinearLayout.LayoutParams(dp(48), dp(48)));

            listApps.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private void addSelectAllRow(List<AppEntry> apps) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(14));

        TextView icon = new TextView(this);
        icon.setBackgroundResource(R.drawable.settings_icon_green);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setText("T");
        icon.setTextColor(getColor(R.color.textPrimary));
        icon.setTextSize(20);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView label = new TextView(this);
        label.setText(R.string.select_all_apps);
        label.setTextColor(getColor(R.color.textPrimary));
        label.setTextSize(17);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(dp(14), 0, dp(8), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(areAllAppsProtected(apps));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Set<String> protectedPackages = new HashSet<>();
            if (isChecked) {
                for (AppEntry app : apps) {
                    protectedPackages.add(app.packageName);
                }
            }
            AuthStore.setProtectedPackages(this, protectedPackages);
            populateInstalledApps();
        });
        row.setOnClickListener(view -> checkBox.setChecked(!checkBox.isChecked()));
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(48), dp(48)));

        listApps.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private boolean areAllAppsProtected(List<AppEntry> apps) {
        if (apps.isEmpty()) {
            return false;
        }

        for (AppEntry app : apps) {
            if (!AuthStore.isPackageProtected(this, app.packageName)) {
                return false;
            }
        }
        return true;
    }

    private List<AppEntry> getLaunchableApps() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
        Map<String, AppEntry> appsByPackage = new HashMap<>();

        for (ResolveInfo resolveInfo : resolveInfos) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (getPackageName().equals(packageName) || appsByPackage.containsKey(packageName)) {
                continue;
            }

            CharSequence label = resolveInfo.loadLabel(packageManager);
            Drawable icon = resolveInfo.loadIcon(packageManager);
            appsByPackage.put(packageName, new AppEntry(
                    label == null ? packageName : label.toString(),
                    packageName,
                    icon
            ));
        }

        List<AppEntry> apps = new ArrayList<>(appsByPackage.values());
        Collections.sort(apps, (first, second) ->
                first.label.toLowerCase(Locale.getDefault()).compareTo(second.label.toLowerCase(Locale.getDefault())));
        return apps;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isBiometricReady() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private static class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;

        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
