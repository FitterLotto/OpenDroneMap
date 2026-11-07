package com.fitterlotto.tvlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Makes TV Home act as the home screen on Fire OS, which doesn't let users pick a
 * third-party launcher. When the system home screen comes to the front, because Home
 * was pressed or the device started up, this service opens TV Home instead.
 *
 * Only the home screen itself is redirected. Other screens in Amazon's launcher
 * package (search, Your Apps & Channels, profiles) are left alone unless they open
 * right after a Home press.
 */
public class HomeRedirectService extends AccessibilityService {

    /** Amazon's home-screen packages across Fire OS versions. */
    private static final Set<String> KNOWN_HOME_PACKAGES = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.amazon.tv.leanbacklauncher"));

    /** How long after a Home press any window from a home package is redirected. */
    private static final long HOME_PRESS_WINDOW_MS = 2500;
    /** Ignore repeat events right after a redirect so we don't loop. */
    private static final long COOLDOWN_MS = 1200;

    private static final String PREFS = "launcher";
    private static final String KEY_BYPASS_UNTIL = "home_bypass_until";

    private final Set<String> homePackages = new HashSet<>();
    private String homeActivityClass;
    private long lastHomePress;
    private long lastRedirect;

    /** Lets the next visit to Amazon's home screen through (the "Amazon Home" tile). */
    static void allowAmazonHomeBriefly(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(KEY_BYPASS_UNTIL, SystemClock.elapsedRealtime() + 15_000L)
                .apply();
    }

    static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(context, HomeRedirectService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String s : splitter) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        homePackages.clear();
        homePackages.addAll(KNOWN_HOME_PACKAGES);
        // Also learn whatever the system currently treats as the home screen.
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri != null && ri.activityInfo != null
                && !getPackageName().equals(ri.activityInfo.packageName)
                && !"android".equals(ri.activityInfo.packageName)) {
            homePackages.add(ri.activityInfo.packageName);
            homeActivityClass = ri.activityInfo.name;
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && event.getAction() == KeyEvent.ACTION_UP) {
            lastHomePress = SystemClock.elapsedRealtime();
        }
        return false; // never swallow keys
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !homePackages.contains(pkg.toString())) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirect < COOLDOWN_MS) return;
        long bypassUntil = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_BYPASS_UNTIL, 0);
        if (now < bypassUntil) return;

        boolean afterHomePress = now - lastHomePress < HOME_PRESS_WINDOW_MS;
        CharSequence cls = event.getClassName();
        boolean isHomeActivity = cls != null && homeActivityClass != null
                && homeActivityClass.contentEquals(cls);
        if (!afterHomePress && !isHomeActivity) return;

        lastRedirect = now;
        lastHomePress = 0;
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(open);
        } catch (RuntimeException ignored) {
            // Nothing sensible to do; Amazon's home stays on screen.
        }
    }

    @Override
    public void onInterrupt() {
    }
}
