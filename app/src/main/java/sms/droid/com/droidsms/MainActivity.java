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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private static final int RISK_HIGH = 3;
    private static final int RISK_MODERATE = 2;
    private static final int RISK_LOW = 1;

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

        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(getColor(R.color.textPrimary));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.addView(title);

        TextView summary = new TextView(this);
        summary.setText(summaryRes);
        summary.setTextColor(getColor(R.color.textSecondary));
        summary.setTextSize(13);
        summary.setPadding(0, dp(4), 0, dp(10));
        section.addView(summary);

        for (AppEntry app : apps) {
            section.addView(createAppRow(app), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        listApps.addView(section, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
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

    private View createAppRow(AppEntry app) {
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
            if (shouldHideFromProtectedList(packageName, appLabel)) {
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

        List<AppEntry> apps = new ArrayList<>(appsByPackage.values());
        Collections.sort(apps, (first, second) ->
                first.label.toLowerCase(Locale.getDefault()).compareTo(second.label.toLowerCase(Locale.getDefault())));
        return apps;
    }

    private int getRiskLevel(String packageName, String label) {
        String normalizedPackage = packageName.toLowerCase(Locale.ROOT);
        String normalizedLabel = label.toLowerCase(Locale.ROOT);
        String value = normalizedPackage + " " + normalizedLabel;

        if (containsAny(normalizedPackage,
                "com.google.android.apps.messaging",
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
                "app lock", "applock", "calculator vault", "gallery vault", "vault");
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
