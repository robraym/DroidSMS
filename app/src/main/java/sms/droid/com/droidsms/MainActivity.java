package sms.droid.com.droidsms;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

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
    private static final String SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key";
    private static final String ACCESSIBILITY_INSTALLED_SERVICES_KEY = "accessibility_installed_services";
    private static final String ACTION_FACE_SETTINGS = "android.settings.FACE_SETTINGS";
    private static final String ACTION_FACE_ENROLL = "android.settings.FACE_ENROLL";
    private static final String ACTION_COMBINED_BIOMETRICS_SETTINGS =
            "android.settings.COMBINED_BIOMETRICS_SETTINGS";
    private static final String ACTION_MOTOROLA_FACE_ENROLL = "com.motorola.intent.action.FACE_ENROLL";
    private static final int REQUEST_TRUSTED_WIFI_PERMISSION = 1001;
    private static final int REQUEST_TRUSTED_BLUETOOTH_PERMISSION = 1002;
    private static final int AUTHENTICATORS_WITH_BIOMETRIC = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static final int AUTHENTICATORS_DEVICE_CREDENTIAL =
            BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static final int RISK_HIGH = 3;
    private static final int RISK_MODERATE = 2;
    private static final int RISK_LOW = 1;
    private static final int REQUIRED_SETUP_NONE = 0;
    private static final int REQUIRED_SETUP_DEVICE_ADMIN = 1;
    private static final int REQUIRED_SETUP_ACCESSIBILITY = 2;
    private static final String[] IMPORTANT_APP_PACKAGES = {
            "com.google.android.gm",
            "com.google.android.apps.photos",
            "com.google.android.apps.docs",
            "com.google.android.apps.nbu.files",
            "com.google.android.apps.maps",
            "com.android.chrome",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.google.android.calendar",
            "com.google.android.keep",
            "com.google.android.contacts",
            "com.google.android.apps.walletnfcrel",
            "com.samsung.android.email.provider",
            "com.android.email",
            "com.google.android.email",
            "com.microsoft.office.outlook",
            "ch.protonmail.android",
            "com.yahoo.mobile.client.android.mail",
            "me.bluemail.mail",
            "com.fsck.k9",
            "com.readdle.spark",
            "com.zoho.mail",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.android.vending",
            "com.android.settings",
            "com.samsung.android.app.settings",
            "com.samsung.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller"
    };

    private TextView txtTitle;
    private TextView txtSubtitle;
    private LinearLayout cardDeviceLockWarning;
    private LinearLayout cardNativeBiometric;
    private LinearLayout listApps;
    private TextView btnDeviceLockSettings;
    private TextView txtAccessibilitySummary;
    private TextView txtDeviceAdminSummary;
    private TextView txtMinimizeUnlockSummary;
    private TextView txtNativeBiometricSummary;
    private TextView txtTrustedWifiSummary;
    private TextView btnTrustedWifi;
    private TextView txtTrustedBluetoothSummary;
    private TextView btnTrustedBluetooth;
    private SwitchCompat switchAccessibility;
    private SwitchCompat switchDeviceAdmin;
    private SwitchCompat switchKeepUnlockedOnMinimize;
    private SwitchCompat switchNativeBiometric;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean settingsUnlocked;
    private boolean promptShown;
    private boolean hasObservedProtectionState;
    private boolean lastAccessibilityActive;
    private boolean lastDeviceAdminActive;
    private boolean waitingForRequiredSetupResult;
    private boolean pendingTrustedWifiSave;
    private boolean pendingBluetoothPicker;
    private int requiredSetupStep = REQUIRED_SETUP_NONE;
    private AlertDialog requiredSetupDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, SmsLockDeviceAdminReceiver.class);
        if (isSetupComplete() && isBiometricReady()) {
            setContentView(R.layout.activity_guard);
            showSettingsBiometricPrompt();
        } else {
            settingsUnlocked = true;
            showSettingsScreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsUnlocked && txtTitle != null) {
            AuthStore.clearSettingsNavigationAllowance(this);
            showSettingsState();
            continueRequiredSetupFlow();
            if (pendingTrustedWifiSave
                    && TrustedWifi.hasRequiredPermission(this)
                    && TrustedWifi.isLocationEnabled(this)) {
                saveCurrentTrustedWifi();
            } else if (pendingTrustedWifiSave
                    && TrustedWifi.hasRequiredPermission(this)) {
                pendingTrustedWifiSave = false;
            }
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
            settingsUnlocked = true;
            showSettingsScreen();
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
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(getAllowedAuthenticators())
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void bindViews() {
        txtTitle = findViewById(R.id.txtTitle);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        cardDeviceLockWarning = findViewById(R.id.cardDeviceLockWarning);
        cardNativeBiometric = findViewById(R.id.cardNativeBiometric);
        listApps = findViewById(R.id.listApps);
        btnDeviceLockSettings = findViewById(R.id.btnDeviceLockSettings);
        txtAccessibilitySummary = findViewById(R.id.txtAccessibilitySummary);
        txtDeviceAdminSummary = findViewById(R.id.txtDeviceAdminSummary);
        txtMinimizeUnlockSummary = findViewById(R.id.txtMinimizeUnlockSummary);
        txtNativeBiometricSummary = findViewById(R.id.txtNativeBiometricSummary);
        txtTrustedWifiSummary = findViewById(R.id.txtTrustedWifiSummary);
        btnTrustedWifi = findViewById(R.id.btnTrustedWifi);
        txtTrustedBluetoothSummary = findViewById(R.id.txtTrustedBluetoothSummary);
        btnTrustedBluetooth = findViewById(R.id.btnTrustedBluetooth);
        switchAccessibility = findViewById(R.id.switchAccessibility);
        switchDeviceAdmin = findViewById(R.id.switchDeviceAdmin);
        switchKeepUnlockedOnMinimize = findViewById(R.id.switchKeepUnlockedOnMinimize);
        switchNativeBiometric = findViewById(R.id.switchNativeBiometric);
    }

    private void showSettingsState() {
        boolean accessibilityActive = isAccessibilityServiceActive();
        boolean deviceAdminActive = isDeviceAdminActive();
        boolean deviceLockReady = isBiometricReady();

        txtTitle.setText(R.string.unlocked_title);
        txtSubtitle.setText(R.string.settings_subtitle);

        cardDeviceLockWarning.setVisibility(deviceLockReady ? View.GONE : View.VISIBLE);
        btnDeviceLockSettings.setOnClickListener(view -> openAllowedSettings(Settings.ACTION_SECURITY_SETTINGS));

        TrustedBluetooth.warmUp(this);
        showTrustedWifiState();
        showTrustedBluetoothState();
        showNativeBiometricState();
        showSecuritySummaries(accessibilityActive, deviceAdminActive);
        showMinimizeUnlockState();

        configureSwitch(switchAccessibility, accessibilityActive, deviceLockReady || accessibilityActive);
        switchAccessibility.setOnClickListener(view -> {
            if (deviceLockReady || accessibilityActive) {
                openAccessibilitySettingsWithDisclosure();
            }
            switchAccessibility.setChecked(accessibilityActive);
        });

        configureSwitch(switchDeviceAdmin, deviceAdminActive, deviceLockReady || deviceAdminActive);
        switchDeviceAdmin.setOnClickListener(view -> {
            if (!deviceLockReady && !deviceAdminActive) {
                switchDeviceAdmin.setChecked(false);
                return;
            }
            if (deviceAdminActive) {
                switchDeviceAdmin.setChecked(true);
                confirmDisableDeviceAdmin();
            } else {
                switchDeviceAdmin.setChecked(false);
                requestDeviceAdmin();
            }
        });

        notifyProtectionStateChanges(accessibilityActive, deviceAdminActive);
    }

    private void showMinimizeUnlockState() {
        boolean keepUnlocked = AuthStore.isKeepUnlockedOnMinimizeEnabled(this);
        txtMinimizeUnlockSummary.setText(keepUnlocked
                ? R.string.minimize_unlock_card_summary_active
                : R.string.minimize_unlock_card_summary_inactive);
        configureSwitch(switchKeepUnlockedOnMinimize, keepUnlocked, true);
        switchKeepUnlockedOnMinimize.setOnClickListener(view -> {
            boolean enabled = switchKeepUnlockedOnMinimize.isChecked();
            AuthStore.setKeepUnlockedOnMinimizeEnabled(this, enabled);
            txtMinimizeUnlockSummary.setText(enabled
                    ? R.string.minimize_unlock_card_summary_active
                    : R.string.minimize_unlock_card_summary_inactive);
        });
    }

    private void showSecuritySummaries(boolean accessibilityActive, boolean deviceAdminActive) {
        txtAccessibilitySummary.setText(accessibilityActive
                ? R.string.accessibility_card_summary_active
                : R.string.accessibility_card_summary_inactive);
        txtDeviceAdminSummary.setText(deviceAdminActive
                ? R.string.device_admin_card_summary_active
                : R.string.device_admin_card_summary);
    }

    private void showNativeBiometricState() {
        boolean nativeBiometricReady = isNativeBiometricReady();
        boolean nativeBiometricEnabled = AuthStore.isNativeBiometricEnabled(this) && nativeBiometricReady;

        if (!nativeBiometricReady) {
            txtNativeBiometricSummary.setText(R.string.biometric_card_summary_setup);
            configureSwitch(switchNativeBiometric, false, false);
            cardNativeBiometric.setOnClickListener(view -> openNativeBiometricSettings());
            switchNativeBiometric.setOnClickListener(view -> {
                switchNativeBiometric.setChecked(false);
                openNativeBiometricSettings();
            });
            return;
        }

        updateNativeBiometricSummary(nativeBiometricEnabled);
        configureSwitch(switchNativeBiometric, nativeBiometricEnabled, true);
        cardNativeBiometric.setOnClickListener(view ->
                setNativeBiometricPreference(!AuthStore.isNativeBiometricEnabled(this)));
        switchNativeBiometric.setOnClickListener(view -> {
            boolean enabled = switchNativeBiometric.isChecked();
            setNativeBiometricPreference(enabled);
        });
    }

    private void setNativeBiometricPreference(boolean enabled) {
        AuthStore.setNativeBiometricEnabled(this, enabled);
        switchNativeBiometric.setChecked(enabled);
        updateNativeBiometricSummary(enabled);
    }

    private void updateNativeBiometricSummary(boolean enabled) {
        txtNativeBiometricSummary.setText(enabled
                ? R.string.biometric_card_summary_ready
                : R.string.biometric_card_summary_disabled);
    }

    private void showTrustedWifiState() {
        List<String> trustedSsids = getSortedTrustedWifiSsids();
        if (!trustedSsids.isEmpty()) {
            txtTrustedWifiSummary.setText(getString(
                    TrustedWifi.isTrustedSessionActive(this)
                            ? R.string.trusted_wifi_configured_active
                            : R.string.trusted_wifi_configured,
                    TextUtils.join(", ", trustedSsids)));
            btnTrustedWifi.setText(R.string.trusted_wifi_manage);
            btnTrustedWifi.setOnClickListener(view -> showTrustedWifiDialog());
            return;
        }

        txtTrustedWifiSummary.setText(R.string.trusted_wifi_not_configured);
        btnTrustedWifi.setText(R.string.trusted_wifi_use_current);
        btnTrustedWifi.setOnClickListener(view -> saveCurrentTrustedWifi());
    }

    private void showTrustedBluetoothState() {
        List<TrustedBluetooth.DeviceGroup> trustedDeviceGroups = getSortedTrustedBluetoothDeviceGroups();
        if (!trustedDeviceGroups.isEmpty()) {
            txtTrustedBluetoothSummary.setText(getString(
                    TrustedBluetooth.isAnyTrustedDeviceConnected(this)
                            ? R.string.trusted_bluetooth_configured_active
                            : R.string.trusted_bluetooth_configured,
                    TextUtils.join(", ", getBluetoothDeviceLabels(trustedDeviceGroups))));
            btnTrustedBluetooth.setText(R.string.trusted_bluetooth_manage);
            btnTrustedBluetooth.setOnClickListener(view -> showTrustedBluetoothDialog());
            return;
        }

        txtTrustedBluetoothSummary.setText(R.string.trusted_bluetooth_not_configured);
        btnTrustedBluetooth.setText(R.string.trusted_bluetooth_add);
        btnTrustedBluetooth.setOnClickListener(view -> showBluetoothDevicePicker());
    }

    private List<String> getSortedTrustedWifiSsids() {
        List<String> trustedSsids = new ArrayList<>(AuthStore.getTrustedWifiSsids(this));
        Collections.sort(trustedSsids, String.CASE_INSENSITIVE_ORDER);
        return trustedSsids;
    }

    private List<TrustedBluetooth.DeviceGroup> getSortedTrustedBluetoothDeviceGroups() {
        List<TrustedBluetooth.Device> trustedDevices = new ArrayList<>();
        for (String address : AuthStore.getTrustedBluetoothAddresses(this)) {
            trustedDevices.add(new TrustedBluetooth.Device(
                    AuthStore.getTrustedBluetoothName(this, address),
                    address));
        }
        return TrustedBluetooth.groupDevices(trustedDevices);
    }

    private List<String> getBluetoothDeviceLabels(List<TrustedBluetooth.DeviceGroup> trustedDevices) {
        List<String> labels = new ArrayList<>();
        for (TrustedBluetooth.DeviceGroup device : trustedDevices) {
            labels.add(device.getLabel());
        }
        return labels;
    }

    private void showTrustedWifiDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trusted_wifi, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCloseTrustedWifi)
                .setOnClickListener(view -> dialog.dismiss());
        dialogView.findViewById(R.id.btnAddCurrentTrustedWifi)
                .setOnClickListener(view -> {
                    dialog.dismiss();
                    saveCurrentTrustedWifi();
                });

        populateTrustedWifiDialog(dialogView);
        dialog.setOnShowListener(dialogInterface -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void populateTrustedWifiDialog(View dialogView) {
        LinearLayout listTrustedWifi = dialogView.findViewById(R.id.listTrustedWifi);
        TextView btnAddCurrent = dialogView.findViewById(R.id.btnAddCurrentTrustedWifi);
        listTrustedWifi.removeAllViews();

        List<String> trustedSsids = getSortedTrustedWifiSsids();
        for (String ssid : trustedSsids) {
            listTrustedWifi.addView(createTrustedWifiRow(dialogView, ssid));
        }

        boolean canAddNetwork = trustedSsids.size() < AuthStore.MAX_TRUSTED_WIFI_NETWORKS;
        btnAddCurrent.setEnabled(canAddNetwork);
        btnAddCurrent.setAlpha(canAddNetwork ? 1f : 0.45f);
    }

    private View createTrustedWifiRow(View dialogView, String ssid) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView networkName = new TextView(this);
        networkName.setText(ssid);
        networkName.setTextColor(getColor(R.color.textPrimary));
        networkName.setTextSize(15);
        row.addView(networkName, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView btnRemove = new TextView(this);
        btnRemove.setBackgroundResource(R.drawable.settings_text_action_ripple);
        btnRemove.setClickable(true);
        btnRemove.setFocusable(true);
        btnRemove.setGravity(android.view.Gravity.CENTER);
        btnRemove.setMinWidth(dp(72));
        btnRemove.setMinimumHeight(dp(32));
        btnRemove.setText(R.string.trusted_wifi_remove);
        btnRemove.setTextColor(getColor(R.color.settingsButton));
        btnRemove.setTextSize(13);
        btnRemove.setTypeface(null, Typeface.BOLD);
        btnRemove.setOnClickListener(view -> {
            AuthStore.removeTrustedWifiSsid(this, ssid);
            TrustedWifi.clearTrustedSession(this);
            Toast.makeText(this, getString(R.string.trusted_wifi_removed, ssid), Toast.LENGTH_SHORT).show();
            showTrustedWifiState();
            populateTrustedWifiDialog(dialogView);
        });
        row.addView(btnRemove, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));
        return row;
    }

    private void saveCurrentTrustedWifi() {
        if (!TrustedWifi.hasRequiredPermission(this)) {
            pendingTrustedWifiSave = true;
            requestPermissions(new String[]{TrustedWifi.getRequiredPermission()}, REQUEST_TRUSTED_WIFI_PERMISSION);
            return;
        }
        pendingTrustedWifiSave = false;

        if (!TrustedWifi.isLocationEnabled(this)) {
            Toast.makeText(this, R.string.trusted_wifi_location_disabled, Toast.LENGTH_LONG).show();
            openAllowedSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            return;
        }

        String ssid = TrustedWifi.getCurrentSsid(this);
        if (ssid.isEmpty()) {
            Toast.makeText(this, R.string.trusted_wifi_unavailable, Toast.LENGTH_LONG).show();
            openAllowedSettings(Settings.ACTION_WIFI_SETTINGS);
            return;
        }

        Set<String> trustedSsids = AuthStore.getTrustedWifiSsids(this);
        if (trustedSsids.contains(ssid)) {
            TrustedWifi.markCurrentWifiSessionTrusted(this, ssid);
            Toast.makeText(this, R.string.trusted_wifi_already_saved, Toast.LENGTH_SHORT).show();
            showTrustedWifiState();
            return;
        }
        if (!AuthStore.addTrustedWifiSsid(this, ssid)) {
            Toast.makeText(this, R.string.trusted_wifi_limit_reached, Toast.LENGTH_LONG).show();
            return;
        }

        TrustedWifi.markCurrentWifiSessionTrusted(this, ssid);
        Toast.makeText(this, getString(R.string.trusted_wifi_saved, ssid), Toast.LENGTH_SHORT).show();
        showTrustedWifiState();
    }

    private void showTrustedBluetoothDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trusted_bluetooth, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCloseTrustedBluetooth)
                .setOnClickListener(view -> dialog.dismiss());
        dialogView.findViewById(R.id.btnAddTrustedBluetooth)
                .setOnClickListener(view -> {
                    dialog.dismiss();
                    showBluetoothDevicePicker();
                });

        populateTrustedBluetoothDialog(dialogView);
        dialog.setOnShowListener(dialogInterface -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void populateTrustedBluetoothDialog(View dialogView) {
        LinearLayout listTrustedBluetooth = dialogView.findViewById(R.id.listTrustedBluetooth);
        listTrustedBluetooth.removeAllViews();

        List<TrustedBluetooth.DeviceGroup> trustedDeviceGroups = getSortedTrustedBluetoothDeviceGroups();
        if (trustedDeviceGroups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.trusted_bluetooth_empty);
            empty.setTextColor(getColor(R.color.textSecondary));
            empty.setTextSize(14);
            listTrustedBluetooth.addView(empty);
            return;
        }

        for (TrustedBluetooth.DeviceGroup device : trustedDeviceGroups) {
            listTrustedBluetooth.addView(createTrustedBluetoothRow(dialogView, device));
        }
    }

    private View createTrustedBluetoothRow(View dialogView, TrustedBluetooth.DeviceGroup device) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView deviceName = new TextView(this);
        deviceName.setText(device.getLabel());
        deviceName.setTextColor(getColor(R.color.textPrimary));
        deviceName.setTextSize(15);
        textColumn.addView(deviceName);

        TextView deviceAddress = new TextView(this);
        deviceAddress.setText(device.getPrimaryAddress());
        deviceAddress.setTextColor(getColor(R.color.textSecondary));
        deviceAddress.setTextSize(12);
        deviceAddress.setVisibility(device.hasMultipleInternalDevices() ? View.GONE : View.VISIBLE);
        textColumn.addView(deviceAddress);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView btnRemove = new TextView(this);
        btnRemove.setBackgroundResource(R.drawable.settings_text_action_ripple);
        btnRemove.setClickable(true);
        btnRemove.setFocusable(true);
        btnRemove.setGravity(android.view.Gravity.CENTER);
        btnRemove.setMinWidth(dp(72));
        btnRemove.setMinimumHeight(dp(32));
        btnRemove.setText(R.string.trusted_bluetooth_remove);
        btnRemove.setTextColor(getColor(R.color.settingsButton));
        btnRemove.setTextSize(13);
        btnRemove.setTypeface(null, Typeface.BOLD);
        btnRemove.setOnClickListener(view -> {
            for (TrustedBluetooth.Device internalDevice : device.getDevices()) {
                AuthStore.removeTrustedBluetoothDevice(this, internalDevice.address);
            }
            Toast.makeText(
                    this,
                    getString(R.string.trusted_bluetooth_removed, device.getLabel()),
                    Toast.LENGTH_SHORT).show();
            showTrustedBluetoothState();
            populateTrustedBluetoothDialog(dialogView);
        });
        row.addView(btnRemove, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)));
        return row;
    }

    private void showBluetoothDevicePicker() {
        if (!TrustedBluetooth.hasRequiredPermission(this)) {
            pendingBluetoothPicker = true;
            requestPermissions(
                    new String[]{TrustedBluetooth.getRequiredPermission()},
                    REQUEST_TRUSTED_BLUETOOTH_PERMISSION);
            return;
        }
        pendingBluetoothPicker = false;

        List<TrustedBluetooth.DeviceGroup> devices = TrustedBluetooth.getBondedDeviceGroups(this);
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.trusted_bluetooth_unavailable, Toast.LENGTH_LONG).show();
            openAllowedSettings(Settings.ACTION_BLUETOOTH_SETTINGS);
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trusted_bluetooth_picker, null);
        LinearLayout listBluetoothDevices = dialogView.findViewById(R.id.listBluetoothDevices);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        for (TrustedBluetooth.DeviceGroup device : devices) {
            listBluetoothDevices.addView(createBluetoothPickerRow(dialog, device));
        }

        dialogView.findViewById(R.id.btnCloseBluetoothPicker)
                .setOnClickListener(view -> dialog.dismiss());
        dialog.setOnShowListener(dialogInterface -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private View createBluetoothPickerRow(AlertDialog dialog, TrustedBluetooth.DeviceGroup device) {
        LinearLayout row = new LinearLayout(this);
        row.setBackgroundResource(R.drawable.settings_text_action_ripple);
        row.setClickable(true);
        row.setFocusable(true);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView deviceName = new TextView(this);
        deviceName.setText(device.getLabel());
        deviceName.setTextColor(getColor(R.color.textPrimary));
        deviceName.setTextSize(15);
        deviceName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(deviceName);

        TextView deviceAddress = new TextView(this);
        deviceAddress.setText(device.getPrimaryAddress());
        deviceAddress.setTextColor(getColor(R.color.textSecondary));
        deviceAddress.setTextSize(12);
        deviceAddress.setVisibility(device.hasMultipleInternalDevices() ? View.GONE : View.VISIBLE);
        row.addView(deviceAddress);

        row.setOnClickListener(view -> {
            for (TrustedBluetooth.Device internalDevice : device.getDevices()) {
                AuthStore.addTrustedBluetoothDevice(this, internalDevice.address, device.getLabel());
            }
            TrustedBluetooth.warmUp(this);
            Toast.makeText(
                    this,
                    getString(R.string.trusted_bluetooth_saved, device.getLabel()),
                    Toast.LENGTH_SHORT).show();
            showTrustedBluetoothState();
            dialog.dismiss();
        });
        return row;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_TRUSTED_WIFI_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingTrustedWifiSave) {
                saveCurrentTrustedWifi();
            }
        } else if (requestCode == REQUEST_TRUSTED_WIFI_PERMISSION) {
            pendingTrustedWifiSave = false;
        } else if (requestCode == REQUEST_TRUSTED_BLUETOOTH_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingBluetoothPicker) {
                showBluetoothDevicePicker();
            }
        } else if (requestCode == REQUEST_TRUSTED_BLUETOOTH_PERMISSION) {
            pendingBluetoothPicker = false;
        }
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description));
        AuthStore.allowSettingsNavigation(this);
        startActivity(intent);
    }

    private void openNativeBiometricSettings() {
        AuthStore.allowSettingsNavigation(this);
        if (openFirstAvailableSettingsAction(
                ACTION_FACE_SETTINGS,
                ACTION_FACE_ENROLL,
                ACTION_MOTOROLA_FACE_ENROLL,
                ACTION_COMBINED_BIOMETRICS_SETTINGS)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent biometricEnroll = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
            biometricEnroll.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS_WITH_BIOMETRIC);
            if (canOpen(biometricEnroll)) {
                startActivity(biometricEnroll);
                return;
            }
        }

        Intent securitySettings = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        if (canOpen(securitySettings)) {
            startActivity(securitySettings);
            return;
        }

        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    private void openAllowedSettings(String action) {
        AuthStore.allowSettingsNavigation(this);
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private boolean openFirstAvailableSettingsAction(String... actions) {
        for (String action : actions) {
            Intent intent = new Intent(action);
            if (canOpen(intent)) {
                startActivity(intent);
                return true;
            }
        }

        return false;
    }

    private boolean canOpen(Intent intent) {
        return intent.resolveActivity(getPackageManager()) != null;
    }

    private void continueRequiredSetupFlow() {
        if (!isBiometricReady()) {
            dismissRequiredSetupDialog();
            return;
        }

        if (waitingForRequiredSetupResult) {
            boolean completedCurrentStep = requiredSetupStep == REQUIRED_SETUP_DEVICE_ADMIN
                    ? isDeviceAdminActive()
                    : isAccessibilityServiceActive();
            waitingForRequiredSetupResult = false;
            if (!completedCurrentStep) {
                finish();
                return;
            }
        }

        if (!isAccessibilityServiceActive()) {
            requiredSetupStep = REQUIRED_SETUP_ACCESSIBILITY;
            if (!AuthStore.hasAcceptedAccessibilityDisclosure(this)) {
                dismissRequiredSetupDialog();
                openAccessibilitySettingsWithDisclosure(true);
                return;
            }
            showRequiredSetupDialog(
                    R.string.required_accessibility_title,
                    R.string.accessibility_manual_steps_message,
                    () -> openAccessibilitySettings(true)
            );
            return;
        }

        if (!isDeviceAdminActive()) {
            requiredSetupStep = REQUIRED_SETUP_DEVICE_ADMIN;
            showRequiredSetupDialog(
                    R.string.required_device_admin_title,
                    R.string.required_device_admin_message,
                    () -> {
                        waitingForRequiredSetupResult = true;
                        requestDeviceAdmin();
                    }
            );
            return;
        }

        requiredSetupStep = REQUIRED_SETUP_NONE;
        dismissRequiredSetupDialog();
    }

    private void showRequiredSetupDialog(int titleRes, int messageRes, Runnable onContinue) {
        dismissRequiredSetupDialog();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_required_setup, null);
        requiredSetupDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        requiredSetupDialog.setCancelable(false);
        requiredSetupDialog.setCanceledOnTouchOutside(false);

        ((TextView) dialogView.findViewById(R.id.txtRequiredSetupTitle)).setText(titleRes);
        ((TextView) dialogView.findViewById(R.id.txtRequiredSetupMessage)).setText(messageRes);
        dialogView.findViewById(R.id.btnCloseRequiredSetup)
                .setOnClickListener(view -> finish());
        dialogView.findViewById(R.id.btnContinueRequiredSetup)
                .setOnClickListener(view -> {
                    dismissRequiredSetupDialog();
                    onContinue.run();
                });

        requiredSetupDialog.setOnShowListener(dialogInterface -> {
            Window window = requiredSetupDialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        requiredSetupDialog.show();
    }

    private void dismissRequiredSetupDialog() {
        if (requiredSetupDialog != null) {
            requiredSetupDialog.dismiss();
            requiredSetupDialog = null;
        }
    }

    private void openAccessibilitySettingsWithDisclosure() {
        openAccessibilitySettingsWithDisclosure(false);
    }

    private void openAccessibilitySettingsWithDisclosure(boolean requiredSetup) {
        if (AuthStore.hasAcceptedAccessibilityDisclosure(this)) {
            openAccessibilitySettings(requiredSetup);
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_accessibility_disclosure, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        CheckBox consentCheckBox = dialogView.findViewById(R.id.chkAccessibilityConsent);
        TextView btnAgree = dialogView.findViewById(R.id.btnAgreeAccessibilityDisclosure);
        TextView btnNotNow = dialogView.findViewById(R.id.btnDeclineAccessibilityDisclosure);

        btnAgree.setEnabled(false);
        btnAgree.setAlpha(0.45f);
        consentCheckBox.setOnCheckedChangeListener((buttonView, checked) -> {
            btnAgree.setEnabled(checked);
            btnAgree.setAlpha(checked ? 1f : 0.45f);
        });

        btnNotNow.setOnClickListener(view -> {
            dialog.dismiss();
            if (requiredSetup) {
                finish();
            }
        });
        btnAgree.setOnClickListener(view -> {
            if (!consentCheckBox.isChecked()) {
                return;
            }
            AuthStore.acceptAccessibilityDisclosure(this);
            dialog.dismiss();
            openAccessibilitySettings(requiredSetup);
        });

        dialog.setOnShowListener(dialogInterface -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void openAccessibilitySettings(boolean requiredSetup) {
        if (requiredSetup) {
            waitingForRequiredSetupResult = true;
        }
        AuthStore.allowSettingsNavigation(this);
        ComponentName service = new ComponentName(this, SmsGuardAccessibilityService.class);
        Intent serviceDetails = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        serviceDetails.putExtra(Intent.EXTRA_COMPONENT_NAME, service.flattenToShortString());

        try {
            ResolveInfo resolveInfo = getPackageManager().resolveActivity(serviceDetails, 0);
            String requiredPermission = resolveInfo != null && resolveInfo.activityInfo != null
                    ? resolveInfo.activityInfo.permission
                    : null;
            boolean canOpenDetails = resolveInfo != null
                    && (TextUtils.isEmpty(requiredPermission)
                    || getPackageManager().checkPermission(requiredPermission, getPackageName())
                    == PackageManager.PERMISSION_GRANTED);
            if (canOpenDetails) {
                startActivity(serviceDetails);
                return;
            }
        } catch (RuntimeException ignored) {
            // Some Android variants expose the action but reject direct navigation.
        }

        if (requiredSetup) {
            startActivity(createAccessibilitySettingsIntent());
            return;
        }

        showAccessibilityFallbackInstructions();
    }

    private Intent createAccessibilitySettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.putExtra(SETTINGS_FRAGMENT_ARGS_KEY, ACCESSIBILITY_INSTALLED_SERVICES_KEY);
        return intent;
    }

    private void showAccessibilityFallbackInstructions() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_required_setup, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        ((TextView) dialogView.findViewById(R.id.txtRequiredSetupTitle))
                .setText(R.string.required_accessibility_title);
        ((TextView) dialogView.findViewById(R.id.txtRequiredSetupMessage))
                .setText(R.string.accessibility_manual_steps_message);

        TextView btnClose = dialogView.findViewById(R.id.btnCloseRequiredSetup);
        btnClose.setText(R.string.cancel);
        btnClose.setOnClickListener(view -> dialog.dismiss());

        dialogView.findViewById(R.id.btnContinueRequiredSetup)
                .setOnClickListener(view -> {
            dialog.dismiss();
            AuthStore.allowSettingsNavigation(this);
            startActivity(createAccessibilitySettingsIntent());
        });

        dialog.setOnShowListener(dialogInterface -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void confirmDisableDeviceAdmin() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_disable_device_admin, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancelDisableDeviceAdmin)
                .setOnClickListener(view -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirmDisableDeviceAdmin)
                .setOnClickListener(view -> {
                    dialog.dismiss();
                    disableDeviceAdmin();
                });

        dialog.setOnShowListener(dialogInterface -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void disableDeviceAdmin() {
        if (!isDeviceAdminActive()) {
            showSettingsState();
            return;
        }

        devicePolicyManager.removeActiveAdmin(deviceAdminComponent);
        setDeviceAdminInactiveState();
        mainHandler.postDelayed(this::showSettingsState, 500);
        mainHandler.postDelayed(this::showSettingsState, 1200);
    }

    private void setDeviceAdminInactiveState() {
        configureSwitch(switchDeviceAdmin, false, isBiometricReady());
        switchDeviceAdmin.setOnClickListener(view -> requestDeviceAdmin());
    }

    private void configureSwitch(SwitchCompat switchView, boolean checked, boolean enabled) {
        switchView.setChecked(checked);
        switchView.setEnabled(enabled);
        switchView.setAlpha(enabled ? 1f : 0.45f);
    }

    private void notifyProtectionStateChanges(boolean accessibilityActive, boolean deviceAdminActive) {
        if (!hasObservedProtectionState) {
            lastAccessibilityActive = accessibilityActive;
            lastDeviceAdminActive = deviceAdminActive;
            hasObservedProtectionState = true;
            return;
        }

        if (lastAccessibilityActive != accessibilityActive) {
            Toast.makeText(this,
                    accessibilityActive
                            ? R.string.app_protection_enabled_message
                            : R.string.app_protection_disabled_message,
                    Toast.LENGTH_SHORT).show();
        }

        if (lastDeviceAdminActive != deviceAdminActive) {
            Toast.makeText(this,
                    deviceAdminActive
                            ? R.string.removal_protection_enabled_message
                            : R.string.removal_protection_disabled_message,
                    Toast.LENGTH_SHORT).show();
        }

        lastAccessibilityActive = accessibilityActive;
        lastDeviceAdminActive = deviceAdminActive;
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
        List<AppEntry> highRiskApps = new ArrayList<>();
        List<AppEntry> moderateRiskApps = new ArrayList<>();
        List<AppEntry> lowRiskApps = new ArrayList<>();

        for (AppEntry app : apps) {
            if (app.riskLevel == RISK_HIGH) {
                highRiskApps.add(app);
            } else if (app.riskLevel == RISK_MODERATE) {
                moderateRiskApps.add(app);
            } else {
                lowRiskApps.add(app);
            }
        }

        Set<String> defaultProtectedPackages = new HashSet<>();
        for (AppEntry app : highRiskApps) {
            defaultProtectedPackages.add(app.packageName);
        }
        AuthStore.initializeProtectedPackagesIfNeeded(this, defaultProtectedPackages);

        addRiskSection(R.string.risk_high_title, R.string.risk_high_summary, highRiskApps, false);
        addRiskSection(R.string.risk_moderate_title, R.string.risk_moderate_summary, moderateRiskApps, !highRiskApps.isEmpty());
        addRiskSection(R.string.risk_low_title, R.string.risk_low_summary, lowRiskApps,
                !highRiskApps.isEmpty() || !moderateRiskApps.isEmpty());
    }

    private void addRiskSection(int titleRes, int summaryRes, List<AppEntry> apps, boolean addDivider) {
        if (apps.isEmpty()) {
            return;
        }

        if (addDivider) {
            addSectionDivider();
        }

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundResource(R.drawable.settings_section_background);
        section.setPadding(dp(16), dp(16), dp(16), dp(8));

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        sectionHeader.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(getColor(R.color.textPrimary));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sectionHeader.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        SwitchCompat selectAll = createSectionSelectAll(apps);
        sectionHeader.addView(selectAll, new LinearLayout.LayoutParams(dp(56), dp(48)));
        section.addView(sectionHeader);

        TextView summary = new TextView(this);
        summary.setText(summaryRes);
        summary.setTextColor(getColor(R.color.textSecondary));
        summary.setTextSize(13);
        summary.setPadding(0, dp(4), 0, dp(10));
        section.addView(summary);

        for (AppEntry app : apps) {
            section.addView(createAppRow(app, () -> updateSectionSelectAll(selectAll, apps)), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        listApps.addView(section, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private SwitchCompat createSectionSelectAll(List<AppEntry> apps) {
        SwitchCompat selectAll = new SwitchCompat(this);
        selectAll.setThumbTintList(ContextCompat.getColorStateList(this, R.color.switch_thumb_tint));
        selectAll.setTrackTintList(ContextCompat.getColorStateList(this, R.color.switch_track_tint));
        selectAll.setShowText(false);
        updateSectionSelectAll(selectAll, apps);
        selectAll.setOnClickListener(view -> {
            boolean checked = selectAll.isChecked();
            for (AppEntry app : apps) {
                AuthStore.setPackageProtected(this, app.packageName, checked);
            }
            populateInstalledApps();
        });
        return selectAll;
    }

    private void updateSectionSelectAll(SwitchCompat selectAll, List<AppEntry> apps) {
        selectAll.setChecked(areAllAppsProtected(apps));
    }

    private boolean areAllAppsProtected(List<AppEntry> apps) {
        for (AppEntry app : apps) {
            if (!AuthStore.isPackageProtected(this, app.packageName)) {
                return false;
            }
        }
        return !apps.isEmpty();
    }

    private void addSectionDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.settingsCardStroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        );
        params.setMargins(0, dp(16), 0, 0);
        listApps.addView(divider, params);
    }

    private View createAppRow(AppEntry app, Runnable onSelectionChanged) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        row.addView(icon, iconParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setPadding(dp(12), 0, dp(8), 0);

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

        SwitchCompat appSwitch = new SwitchCompat(this);
        appSwitch.setThumbTintList(ContextCompat.getColorStateList(this, R.color.switch_thumb_tint));
        appSwitch.setTrackTintList(ContextCompat.getColorStateList(this, R.color.switch_track_tint));
        appSwitch.setShowText(false);
        boolean packageInstallerPackage = AuthStore.isPackageInstallerPackage(app.packageName);
        appSwitch.setChecked(AuthStore.isPackageProtected(this, app.packageName));
        appSwitch.setEnabled(!packageInstallerPackage);
        appSwitch.setAlpha(packageInstallerPackage ? 0.65f : 1f);
        appSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AuthStore.setPackageProtected(this, app.packageName, isChecked);
            onSelectionChanged.run();
        });
        row.setOnClickListener(view -> {
            if (!packageInstallerPackage) {
                appSwitch.setChecked(!appSwitch.isChecked());
            }
        });
        row.addView(appSwitch, new LinearLayout.LayoutParams(dp(56), dp(48)));

        return row;
    }

    private List<AppEntry> getLaunchableApps() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
        Map<String, AppEntry> appsByPackage = new HashMap<>();
        Set<String> nonProtectablePackages = SystemPackages.getNonProtectablePackages(this);

        for (ResolveInfo resolveInfo : resolveInfos) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (getPackageName().equals(packageName)
                    || appsByPackage.containsKey(packageName)
                    || shouldSkipFromProtectedList(nonProtectablePackages, packageName)) {
                continue;
            }

            CharSequence label = resolveInfo.loadLabel(packageManager);
            String appLabel = label == null ? packageName : label.toString();

            Drawable icon = resolveInfo.loadIcon(packageManager);
            appsByPackage.put(packageName, new AppEntry(
                    appLabel,
                    packageName,
                    icon,
                    getRiskLevel(packageName, appLabel)
            ));
        }

        addSettingsApp(packageManager, appsByPackage, nonProtectablePackages);
        addImportantInstalledApps(packageManager, appsByPackage, nonProtectablePackages);

        List<AppEntry> apps = new ArrayList<>(appsByPackage.values());
        Collections.sort(apps, (first, second) ->
                first.label.toLowerCase(Locale.getDefault()).compareTo(second.label.toLowerCase(Locale.getDefault())));
        return apps;
    }

    private void addImportantInstalledApps(PackageManager packageManager, Map<String, AppEntry> appsByPackage,
                                           Set<String> nonProtectablePackages) {
        for (String packageName : IMPORTANT_APP_PACKAGES) {
            if (getPackageName().equals(packageName)
                    || appsByPackage.containsKey(packageName)
                    || shouldSkipFromProtectedList(nonProtectablePackages, packageName)) {
                continue;
            }

            try {
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
                String appLabel = packageManager.getApplicationLabel(applicationInfo).toString();

                Drawable icon = packageManager.getApplicationIcon(applicationInfo);
                appsByPackage.put(packageName, new AppEntry(
                        appLabel,
                        packageName,
                        icon,
                        getRiskLevel(packageName, appLabel)
                ));
            } catch (PackageManager.NameNotFoundException ignored) {
                // App is not installed or not visible on this device.
            }
        }
    }

    private void addSettingsApp(PackageManager packageManager, Map<String, AppEntry> appsByPackage,
                                Set<String> nonProtectablePackages) {
        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        ResolveInfo resolveInfo = packageManager.resolveActivity(settingsIntent, 0);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return;
        }

        String packageName = resolveInfo.activityInfo.packageName;
        if (getPackageName().equals(packageName)
                || appsByPackage.containsKey(packageName)
                || AuthStore.isPackageInstallerPackage(packageName)) {
            return;
        }

        CharSequence label = resolveInfo.loadLabel(packageManager);
        String appLabel = label == null ? packageName : label.toString();
        Drawable icon = resolveInfo.loadIcon(packageManager);
        appsByPackage.put(packageName, new AppEntry(
                appLabel,
                packageName,
                icon,
                RISK_HIGH
        ));
    }

    private boolean shouldSkipFromProtectedList(Set<String> nonProtectablePackages, String packageName) {
        return AuthStore.isPackageInstallerPackage(packageName)
                || (nonProtectablePackages.contains(packageName) && !SystemPackages.isSettingsPackage(packageName));
    }

    private int getRiskLevel(String packageName, String label) {
        String normalizedPackage = packageName.toLowerCase(Locale.ROOT);
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        String value = normalizedPackage + " " + normalizedLabel;

        if (containsAny(normalizedPackage,
                "com.google.android.apps.messaging",
                "com.google.android.apps.photos",
                "com.google.android.apps.docs",
                "com.google.android.apps.nbu.files",
                "com.google.android.apps.walletnfcrel",
                "com.samsung.android.messaging",
                "com.android.mms",
                "com.android.messaging",
                "com.android.vending")) {
            return RISK_HIGH;
        }

        if (containsAny(value,
                "bank", "banco", "bradesco", "itau", "nubank", "santander", "caixa",
                "bb.android", "intermedium", "picpay", "paypal", "mercadopago", "wallet",
                "pay", "pagseguro", "stone", "xpinc", "clear", "rico", "binance", "coin",
                "crypto", "authenticator", "auth", "senha", "password", "keeper", "lastpass",
                "bitwarden", "1password", "email", "mail", "gmail", "outlook", "proton",
                "message", "mensagem", "sms", "mms", "whatsapp", "telegram", "signal",
                "play store", "google play", "loja play",
                "settings", "config", "arquivo", "files", "file", "gallery", "galeria",
                "photos", "fotos", "drive", "onedrive", "dropbox", "cloud")) {
            return RISK_HIGH;
        }

        if (containsAny(value,
                "browser", "chrome", "firefox", "edge", "samsung internet", "internet",
                "facebook", "instagram", "tiktok", "twitter", "linkedin", "snapchat",
                "discord", "teams", "slack", "zoom", "meet", "shopping", "shop", "amazon",
                "mercadolivre", "mercado livre", "shopee", "aliexpress", "uber", "99",
                "ifood", "health", "saude", "calendar", "calendario",
                "contacts", "contatos", "notes", "notas")) {
            return RISK_MODERATE;
        }

        return RISK_LOW;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    private static class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;
        final int riskLevel;

        AppEntry(String label, String packageName, Drawable icon, int riskLevel) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
            this.riskLevel = riskLevel;
        }
    }
}
