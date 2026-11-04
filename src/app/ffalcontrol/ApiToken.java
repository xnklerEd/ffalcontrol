package app.ffalcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.security.SecureRandom;

/**
 * Per-install API token.
 *
 * The HTTP endpoint binds to 127.0.0.1 and is only reachable through {@code adb forward}, but it
 * still requires a bearer token. The token is generated once per install and stored in the app's
 * private SharedPreferences. Nothing is compiled into the APK.
 *
 * MainActivity logs {@code FFalControl token: ...} on every startup, under {@link #LOG_TAG}.
 * The same line is logged here when a token is first created.
 */
final class ApiToken {

    private static final String PREFS = "ffalcontrol";
    private static final String KEY = "api_token";
    static final String LOG_TAG = "FFalControl";

    private static String cached;

    private ApiToken() {}

    static synchronized String value(Context context) {
        if (cached != null) return cached;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY, null);
        if (token == null || token.length() != 64) {
            token = randomHex();
            prefs.edit().putString(KEY, token).apply();
            Log.i(LOG_TAG, "FFalControl token: " + token);
        }
        cached = token;
        return token;
    }

    private static String randomHex() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Hex.encode(bytes);
    }
}
