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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static final int RISK_HIGH = 3;
    private static final int RISK_MODERATE = 2;
    private static final int RISK_LOW = 1;
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
            "com.samsung.android.app.settings"
    };

    private TextView txtTitle;
    private TextView txtSubtitle;
    private LinearLayout cardDeviceLockWarning;
    private LinearLayout cardProtectionDisabledWarning;
    private LinearLayout listApps;
    private TextView btnDeviceLockSettings;
    private TextView btnReactivateProtection;
    private TextView txtTrustedWifiSummary;
    private TextView btnTrustedWifi;
    private SwitchCompat switchAccessibility;
    private SwitchCompat switchDeviceAdmin;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean settingsUnlocked;
    private boolean promptShown;
    private boolean hasObservedProtectionState;
    private boolean lastAccessibilityActive;
    private boolean lastDeviceAdminActive;

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
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void bindViews() {
        txtTitle = findViewById(R.id.txtTitle);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        cardDeviceLockWarning = findViewById(R.id.cardDeviceLockWarning);
        cardProtectionDisabledWarning = findViewById(R.id.cardProtectionDisabledWarning);
        listApps = findViewById(R.id.listApps);
        btnDeviceLockSettings = findViewById(R.id.btnDeviceLockSettings);
        btnReactivateProtection = findViewById(R.id.btnReactivateProtection);
        txtTrustedWifiSummary = findViewById(R.id.txtTrustedWifiSummary);
        btnTrustedWifi = findViewById(R.id.btnTrustedWifi);
        switchAccessibility = findViewById(R.id.switchAccessibility);
        switchDeviceAdmin = findViewById(R.id.switchDeviceAdmin);
    }

    private void showSettingsState() {
        boolean accessibilityActive = isAccessibilityServiceActive();
        boolean deviceAdminActive = isDeviceAdminActive();
        boolean deviceLockReady = isBiometricReady();

        txtTitle.setText(R.string.unlocked_title);
        txtSubtitle.setText(R.string.settings_subtitle);

        cardDeviceLockWarning.setVisibility(deviceLockReady ? View.GONE : View.VISIBLE);
        btnDeviceLockSettings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)));

        boolean shouldWarnProtectionDisabled = deviceLockReady && !accessibilityActive;
        cardProtectionDisabledWarning.setVisibility(shouldWarnProtectionDisabled ? View.VISIBLE : View.GONE);
        btnReactivateProtection.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        showTrustedWifiState();

        configureSwitch(switchAccessibility, accessibilityActive, deviceLockReady || accessibilityActive);
        switchAccessibility.setOnClickListener(view -> {
            if (deviceLockReady || accessibilityActive) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
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

    private void showTrustedWifiState() {
        String trustedSsid = AuthStore.getTrustedWifiSsid(this);
        if (!trustedSsid.isEmpty()) {
            txtTrustedWifiSummary.setText(getString(R.string.trusted_wifi_configured, trustedSsid));
            btnTrustedWifi.setText(R.string.trusted_wifi_remove);
            btnTrustedWifi.setOnClickListener(view -> {
                AuthStore.clearTrustedWifi(this);
                showTrustedWifiState();
            });
            return;
        }

        txtTrustedWifiSummary.setText(R.string.trusted_wifi_not_configured);
        btnTrustedWifi.setText(R.string.trusted_wifi_use_current);
        btnTrustedWifi.setOnClickListener(view -> saveCurrentTrustedWifi());
    }

    private void saveCurrentTrustedWifi() {
        if (!TrustedWifi.hasLocationPermission(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        String ssid = TrustedWifi.getCurrentSsid(this);
        if (ssid.isEmpty()) {
            Toast.makeText(this, R.string.trusted_wifi_unavailable, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            return;
        }

        AuthStore.setTrustedWifiSsid(this, ssid);
        Toast.makeText(this, getString(R.string.trusted_wifi_saved, ssid), Toast.LENGTH_SHORT).show();
        showTrustedWifiState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveCurrentTrustedWifi();
        }
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description));
        startActivity(intent);
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
        appSwitch.setChecked(AuthStore.isPackageProtected(this, app.packageName));
        appSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AuthStore.setPackageProtected(this, app.packageName, isChecked);
            onSelectionChanged.run();
        });
        row.setOnClickListener(view -> appSwitch.setChecked(!appSwitch.isChecked()));
        row.addView(appSwitch, new LinearLayout.LayoutParams(dp(56), dp(48)));

        return row;
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
            String appLabel = label == null ? packageName : label.toString();
            if (shouldHideFromProtectedList(packageName, appLabel)
                    && !AuthStore.isPackageProtected(this, packageName)) {
                continue;
            }

            Drawable icon = resolveInfo.loadIcon(packageManager);
            appsByPackage.put(packageName, new AppEntry(
                    appLabel,
                    packageName,
                    icon,
                    getRiskLevel(packageName, appLabel)
            ));
        }

        addImportantInstalledApps(packageManager, appsByPackage);

        List<AppEntry> apps = new ArrayList<>(appsByPackage.values());
        Collections.sort(apps, (first, second) ->
                first.label.toLowerCase(Locale.getDefault()).compareTo(second.label.toLowerCase(Locale.getDefault())));
        return apps;
    }

    private void addImportantInstalledApps(PackageManager packageManager, Map<String, AppEntry> appsByPackage) {
        for (String packageName : IMPORTANT_APP_PACKAGES) {
            if (getPackageName().equals(packageName) || appsByPackage.containsKey(packageName)) {
                continue;
            }

            try {
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
                String appLabel = packageManager.getApplicationLabel(applicationInfo).toString();
                if (shouldHideFromProtectedList(packageName, appLabel)
                        && !AuthStore.isPackageProtected(this, packageName)) {
                    continue;
                }

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

    private boolean shouldHideFromProtectedList(String packageName, String label) {
        String normalizedPackage = packageName.toLowerCase(Locale.ROOT);
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        String value = normalizedPackage + " " + normalizedLabel;

        return containsAny(value,
                "secure folder", "pasta segura", "knox secure folder", "securefolder",
                "app lock", "applock", "calculator vault", "gallery vault", "vault",
                "wallet", "carteira", "samsung pass", "samsungpass", "passkey",
                "authenticator", "autenticador", "bitwarden", "1password", "lastpass",
                "keeper", "password manager", "gerenciador de senhas",
                "bank", "banco", "bradesco", "itau", "itaú", "nubank", "santander",
                "caixa", "bb.android", "banco do brasil", "intermedium", "inter bank",
                "btgpactual", "btg", "c6bank", "c6 bank", "next", "neon", "sicredi",
                "sicoob", "banrisul", "original", "pan", "bmg", "banestes", "banese",
                "pagbank", "pagseguro", "picpay", "paypal", "mercadopago",
                "mercado pago", "recargapay", "iti", "stone", "ton", "sumup",
                "xpinc", "xp investimentos", "clear", "rico", "modalmais", "nuinvest",
                "binance", "coinbase", "crypto", "bitcoin", "btc", "ethereum",
                "trust wallet", "metamask",
                "whatsapp", "telegram", "signal");
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
        return biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS;
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
