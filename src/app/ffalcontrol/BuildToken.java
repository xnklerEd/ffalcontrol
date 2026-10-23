package app.ffalcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.security.SecureRandom;

/**
 * Per-install API token.
 *
 * The HTTP endpoint binds to 127.0.0.1 and is only reachable through `adb forward`, but it still
 * requires a bearer token. The token is generated once per install, stored in the app's private
 * SharedPreferences, and printed to logcat on startup so the operator can read it with:
 *
 *     adb logcat -d | grep "FFalControl token"
 *
 * No token is compiled into the APK, so a published build carries no shared secret.
 */
final class BuildToken {

    private static final String PREFS = "ffalcontrol";
    private static final String KEY = "api_token";
    private static final String TAG = "FFalControl";

    private static String cached;

    private BuildToken() {}

    static synchronized String value(Context context) {
        if (cached != null) return cached;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY, null);
        if (token == null || token.length() != 64) {
            token = randomHex();
            prefs.edit().putString(KEY, token).apply();
            Log.i(TAG, "FFalControl token: " + token);
        }
        cached = token;
        return token;
    }

    private static String randomHex() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
}
