package sms.droid.com.droidsms;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SmsGuardAccessibilityService extends AccessibilityService {
    private static final Set<String> PROTECTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.android.settings",
            "com.samsung.android.app.settings",
            "com.google.android.gm",
            "com.samsung.android.email.provider",
            "com.android.email",
            "com.google.android.email"
    ));
    private String lastPackageName = "";

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
        boolean protectedPackage = PROTECTED_PACKAGES.contains(packageName);

        if (!protectedPackage) {
            lastPackageName = packageName;
            return;
        }

        if (TextUtils.equals(lastPackageName, packageName) || AuthStore.isUnlocked(this, packageName)) {
            lastPackageName = packageName;
            return;
        }

        lastPackageName = packageName;

        Intent intent = new Intent(this, GuardActivity.class);
        intent.putExtra(GuardActivity.EXTRA_TARGET_PACKAGE, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
    }

    static boolean isProtectedPackage(String packageName) {
        return !TextUtils.isEmpty(packageName) && PROTECTED_PACKAGES.contains(packageName);
    }
}
