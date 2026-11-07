package com.fitterlotto.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The home screen: info header plus rows of Favorites, Recent, All apps and System. */
public class MainActivity extends Activity {

    private static final long WEATHER_REFRESH_MS = 30 * 60 * 1000L;
    private static final int TILE_W_DP = 192;
    private static final int TILE_H_DP = 108;
    private static final int TILE_GAP_DP = 22;

    private AppRepository repo;
    private final WeatherClient weatherClient = new WeatherClient();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout rowsContainer;
    private ScrollView scroll;
    private TextView greeting, weatherNow, weatherDetail, statusNetwork, statusApps;

    private final Map<String, AppEntry> appsByPackage = new HashMap<>();
    private List<AppEntry> apps = new ArrayList<>();
    private long lastWeatherFetch;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getData() != null
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                repo.forget(intent.getData().getSchemeSpecificPart());
            }
            refreshApps();
        }
    };

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateNetworkStatus();
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repo = new AppRepository(this);

        rowsContainer = findViewById(R.id.rows);
        scroll = findViewById(R.id.scroll);
        greeting = findViewById(R.id.greeting);
        weatherNow = findViewById(R.id.weather_now);
        weatherDetail = findViewById(R.id.weather_detail);
        statusNetwork = findViewById(R.id.status_network);
        statusApps = findViewById(R.id.status_apps);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshApps();
        updateGreeting();
        updateNetworkStatus();
        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, 60_000L);
        if (System.currentTimeMillis() - lastWeatherFetch > WEATHER_REFRESH_MS) refreshWeather();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
        weatherClient.shutdown();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: jump back to the top.
        scroll.smoothScrollTo(0, 0);
        focusFirstTile();
    }

    @Override
    public void onBackPressed() {
        // A launcher has nowhere to go back to.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof String) {
                String pkg = packageFromTag((String) focused.getTag());
                AppEntry app = pkg != null ? appsByPackage.get(pkg) : null;
                if (app != null) {
                    showAppOptions(app);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- Header ----------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String text;
        if (hour < 5) text = "Good night";
        else if (hour < 12) text = "Good morning";
        else if (hour < 17) text = "Good afternoon";
        else if (hour < 22) text = "Good evening";
        else text = "Good night";
        greeting.setText("·  " + text);
    }

    @SuppressWarnings("deprecation")
    private void updateNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        String text;
        if (info == null || !info.isConnected()) {
            text = "●  Offline";
        } else if (info.getType() == ConnectivityManager.TYPE_ETHERNET) {
            text = "●  Ethernet connected";
        } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
            text = "●  Wi‑Fi connected";
        } else {
            text = "●  Online";
        }
        statusNetwork.setText(text);
    }

    private void refreshWeather() {
        lastWeatherFetch = System.currentTimeMillis();
        weatherClient.fetch(w -> {
            if (isFinishing()) return;
            if (w == null) {
                weatherNow.setText("--°");
                weatherDetail.setText("Weather unavailable");
                lastWeatherFetch = System.currentTimeMillis() - WEATHER_REFRESH_MS + 5 * 60 * 1000L; // retry in 5 min
                return;
            }
            weatherNow.setText(w.symbol + "  " + w.temp + "°" + w.unit);
            String place = TextUtils.isEmpty(w.city) ? "" : w.city + "  ·  ";
            weatherDetail.setText(place + w.condition + "  ·  H " + w.high + "°  L " + w.low + "°");
        });
    }

    // ---- Rows ------------------------------------------------------------

    private void refreshApps() {
        apps = repo.loadApps();
        appsByPackage.clear();
        for (AppEntry a : apps) appsByPackage.put(a.packageName, a);
        statusApps.setText(apps.size() + " apps installed");
        rebuildRows();
    }

    private void rebuildRows() {
        View focused = getCurrentFocus();
        Object focusTag = focused != null ? focused.getTag() : null;

        rowsContainer.removeAllViews();

        List<AppEntry> favorites = resolve(repo.getFavorites());
        if (!favorites.isEmpty()) addScrollingRow("★  Favorites", "fav", favorites);

        List<AppEntry> recents = resolve(repo.getRecents());
        if (!recents.isEmpty()) addScrollingRow("Recently used", "recent", recents);

        addGrid("All apps", "all", apps);
        addSystemRow();

        View target = focusTag != null ? rowsContainer.findViewWithTag(focusTag) : null;
        if (target != null) target.requestFocus();
        else focusFirstTile();
    }

    private List<AppEntry> resolve(List<String> packages) {
        List<AppEntry> out = new ArrayList<>();
        for (String p : packages) {
            AppEntry a = appsByPackage.get(p);
            if (a != null) out.add(a);
        }
        return out;
    }

    private void addScrollingRow(String title, String key, List<AppEntry> entries) {
        rowsContainer.addView(rowTitle(title));
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        hsv.setClipChildren(false);
        hsv.setPadding(0, dp(10), dp(24), dp(6));
        LinearLayout strip = tileStrip();
        for (AppEntry a : entries) strip.addView(appTile(key, a));
        hsv.addView(strip);
        rowsContainer.addView(hsv);
    }

    private void addGrid(String title, String key, List<AppEntry> entries) {
        rowsContainer.addView(rowTitle(title + "  (" + entries.size() + ")"));
        int available = getResources().getDisplayMetrics().widthPixels - dp(96);
        int columns = Math.max(1, available / dp(TILE_W_DP + TILE_GAP_DP));
        LinearLayout strip = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % columns == 0) {
                strip = tileStrip();
                strip.setPadding(0, dp(10), 0, dp(6));
                rowsContainer.addView(strip);
            }
            strip.addView(appTile(key, entries.get(i)));
        }
    }

    private void addSystemRow() {
        rowsContainer.addView(rowTitle("System"));
        LinearLayout strip = tileStrip();
        strip.setPadding(0, dp(10), 0, dp(6));
        strip.addView(glyphTile("sys:settings", "⚙", "Settings",
                () -> startSafely(new Intent(Settings.ACTION_SETTINGS))));
        strip.addView(glyphTile("sys:network", "📶", "Network",
                () -> startSafely(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        strip.addView(glyphTile("sys:apps", "▦", "Manage apps",
                () -> startSafely(new Intent(Settings.ACTION_APPLICATION_SETTINGS))));
        strip.addView(glyphTile("sys:weather", "↻", "Refresh weather", () -> {
            weatherDetail.setText("Updating…");
            refreshWeather();
        }));
        rowsContainer.addView(strip);
    }

    private TextView rowTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color(R.color.text_primary));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setPadding(dp(4), dp(18), 0, 0);
        return tv;
    }

    private LinearLayout tileStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        return strip;
    }

    // ---- Tiles -----------------------------------------------------------

    private View appTile(String rowKey, final AppEntry app) {
        View content;
        if (app.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.banner);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            content = iv;
        } else {
            content = iconWithLabel(app.icon, app.label);
        }
        return tile(rowKey + ":" + app.packageName, content, app.label,
                v -> launch(app),
                v -> {
                    showAppOptions(app);
                    return true;
                });
    }

    private View glyphTile(String tag, String glyph, String label, final Runnable action) {
        TextView g = new TextView(this);
        g.setText(glyph);
        g.setGravity(Gravity.CENTER);
        g.setTextColor(color(R.color.accent));
        g.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        return tile(tag, g, label, v -> action.run(), null);
    }

    private View iconWithLabel(Drawable icon, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        box.addView(iv, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(color(R.color.text_primary));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(8), dp(6), dp(8), 0);
        box.addView(tv);
        return box;
    }

    /** A focusable card with a caption beneath it that brightens on focus. */
    private View tile(String tag, View content, String caption,
                      View.OnClickListener onClick, View.OnLongClickListener onLongClick) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);

        final FrameLayout card = new FrameLayout(this);
        card.setTag(tag);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setBackgroundResource(R.drawable.tile_bg);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setContentDescription(caption);
        card.setOnClickListener(onClick);
        if (onLongClick != null) card.setOnLongClickListener(onLongClick);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(card, new LinearLayout.LayoutParams(dp(TILE_W_DP), dp(TILE_H_DP)));

        final TextView label = new TextView(this);
        label.setText(caption);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(color(R.color.text_secondary));
        label.setPadding(dp(4), dp(8), dp(4), 0);
        column.addView(label, new LinearLayout.LayoutParams(dp(TILE_W_DP), ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnFocusChangeListener((v, hasFocus) -> {
            float s = hasFocus ? 1.08f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(hasFocus ? dp(8) : 0).setDuration(140).start();
            label.setTextColor(color(hasFocus ? R.color.text_primary : R.color.text_secondary));
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(TILE_GAP_DP));
        column.setLayoutParams(lp);
        return column;
    }

    private void focusFirstTile() {
        View first = findFirstFocusable(rowsContainer);
        if (first != null) first.requestFocus();
    }

    private View findFirstFocusable(View v) {
        if (v.isFocusable() && v.getTag() != null) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findFirstFocusable(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    // ---- Actions ---------------------------------------------------------

    private void launch(AppEntry app) {
        try {
            startActivity(app.launchIntent);
            repo.recordLaunch(app.packageName);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Couldn't open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void startSafely(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showAppOptions(final AppEntry app) {
        final boolean fav = repo.isFavorite(app.packageName);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add("Open");
        actions.add(() -> launch(app));

        labels.add(fav ? "Remove from Favorites" : "Add to Favorites");
        actions.add(() -> {
            repo.toggleFavorite(app.packageName);
            rebuildRows();
        });

        if (fav) {
            List<String> favs = repo.getFavorites();
            int idx = favs.indexOf(app.packageName);
            if (idx > 0) {
                labels.add("Move left in Favorites");
                actions.add(() -> {
                    repo.moveFavorite(app.packageName, -1);
                    rebuildRows();
                });
            }
            if (idx >= 0 && idx < favs.size() - 1) {
                labels.add("Move right in Favorites");
                actions.add(() -> {
                    repo.moveFavorite(app.packageName, 1);
                    rebuildRows();
                });
            }
        }

        labels.add("App info");
        actions.add(() -> startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.packageName))));

        labels.add("Uninstall");
        actions.add(() -> startSafely(new Intent(Intent.ACTION_DELETE,
                Uri.parse("package:" + app.packageName))));

        new AlertDialog.Builder(this, R.style.LauncherDialog)
                .setTitle(app.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    // ---- Helpers ---------------------------------------------------------

    private static String packageFromTag(String tag) {
        int i = tag.indexOf(':');
        if (i < 0 || tag.startsWith("sys:")) return null;
        return tag.substring(i + 1);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    @SuppressWarnings("deprecation")
    private int color(int resId) {
        return getResources().getColor(resId);
    }
}
