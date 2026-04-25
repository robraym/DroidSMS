package sms.droid.com.droidsms;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class AuthStore {
    static final String PREFS_NAME = "droidsms_security";
    static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_UNLOCKED_PACKAGE = "unlocked_package";
    private static final String KEY_UNLOCKED_UNTIL = "unlocked_until";
    private static final long UNLOCK_WINDOW_MS = 8 * 1000;

    private AuthStore() {
    }

    static boolean hasPassword(Context context) {
        return prefs(context).contains(KEY_PASSWORD_HASH);
    }

    static void savePassword(Context context, String password) {
        prefs(context).edit().putString(KEY_PASSWORD_HASH, sha256(password)).apply();
    }

    static void clearPassword(Context context) {
        prefs(context).edit()
                .remove(KEY_PASSWORD_HASH)
                .remove(KEY_UNLOCKED_PACKAGE)
                .remove(KEY_UNLOCKED_UNTIL)
                .apply();
    }

    static boolean matchesPassword(Context context, String password) {
        return TextUtils.equals(prefs(context).getString(KEY_PASSWORD_HASH, ""), sha256(password));
    }

    static void markUnlocked(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        prefs(context).edit()
                .putString(KEY_UNLOCKED_PACKAGE, packageName)
                .putLong(KEY_UNLOCKED_UNTIL, System.currentTimeMillis() + UNLOCK_WINDOW_MS)
                .apply();
    }

    static boolean isUnlocked(Context context, String packageName) {
        SharedPreferences preferences = prefs(context);
        String unlockedPackage = preferences.getString(KEY_UNLOCKED_PACKAGE, "");
        long unlockedUntil = preferences.getLong(KEY_UNLOCKED_UNTIL, 0);
        return TextUtils.equals(unlockedPackage, packageName) && System.currentTimeMillis() < unlockedUntil;
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
