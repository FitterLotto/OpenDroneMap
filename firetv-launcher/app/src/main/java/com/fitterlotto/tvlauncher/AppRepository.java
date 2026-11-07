package com.fitterlotto.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds installed apps and stores favorites / recently used apps. */
final class AppRepository {
    private static final int MAX_RECENTS = 12;
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_RECENTS = "recents";

    private final Context context;
    private final SharedPreferences prefs;

    AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    /** All launchable apps, TV (leanback) entries preferred, sorted by name. */
    List<AppEntry> loadApps() {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        addFromCategory(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPackage);
        addFromCategory(pm, Intent.CATEGORY_LAUNCHER, byPackage);
        byPackage.remove(context.getPackageName());

        List<AppEntry> apps = new ArrayList<>(byPackage.values());
        final Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> collator.compare(a.label, b.label));
        return apps;
    }

    private void addFromCategory(PackageManager pm, String category, Map<String, AppEntry> out) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> results = pm.queryIntentActivities(query, 0);
        for (ResolveInfo ri : results) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || out.containsKey(ai.packageName)) continue;

            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(new ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            Drawable banner = null;
            try {
                banner = ai.loadBanner(pm);
                if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            } catch (Exception ignored) {
                // Some apps ship broken resources; fall back to the icon.
            }
            CharSequence label = ri.loadLabel(pm);
            out.put(ai.packageName, new AppEntry(
                    ai.packageName,
                    label != null ? label.toString() : ai.packageName,
                    launch,
                    banner,
                    ri.loadIcon(pm)));
        }
    }

    // ---- Favorites -------------------------------------------------------

    List<String> getFavorites() { return readList(KEY_FAVORITES); }

    boolean isFavorite(String pkg) { return getFavorites().contains(pkg); }

    void toggleFavorite(String pkg) {
        List<String> favs = getFavorites();
        if (!favs.remove(pkg)) favs.add(pkg);
        writeList(KEY_FAVORITES, favs);
    }

    /** Moves a favorite by delta positions (-1 = left, +1 = right). */
    void moveFavorite(String pkg, int delta) {
        List<String> favs = getFavorites();
        int i = favs.indexOf(pkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= favs.size()) return;
        Collections.swap(favs, i, j);
        writeList(KEY_FAVORITES, favs);
    }

    // ---- Recents ---------------------------------------------------------

    List<String> getRecents() { return readList(KEY_RECENTS); }

    void recordLaunch(String pkg) {
        List<String> recents = getRecents();
        recents.remove(pkg);
        recents.add(0, pkg);
        while (recents.size() > MAX_RECENTS) recents.remove(recents.size() - 1);
        writeList(KEY_RECENTS, recents);
    }

    void forget(String pkg) {
        List<String> recents = getRecents();
        if (recents.remove(pkg)) writeList(KEY_RECENTS, recents);
    }

    // ---- Storage helpers -------------------------------------------------

    private List<String> readList(String key) {
        String raw = prefs.getString(key, "");
        if (TextUtils.isEmpty(raw)) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private void writeList(String key, List<String> values) {
        prefs.edit().putString(key, TextUtils.join(",", values)).apply();
    }
}
