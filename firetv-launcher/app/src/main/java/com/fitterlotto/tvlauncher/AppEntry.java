package com.fitterlotto.tvlauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One launchable app on the home screen. */
final class AppEntry {
    final String packageName;
    final String label;
    final Intent launchIntent;
    final Drawable banner; // 16:9 TV banner, may be null
    final Drawable icon;

    AppEntry(String packageName, String label, Intent launchIntent, Drawable banner, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.launchIntent = launchIntent;
        this.banner = banner;
        this.icon = icon;
    }
}
