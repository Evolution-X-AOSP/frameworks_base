package com.android.internal.util.evolution;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.provider.Settings;

public class HideDeveloperStatusUtils {
    private static Set<String> mApps = new HashSet<>();
    private static final Set<String> settingsToHide = new HashSet<>(Arrays.asList(
        Settings.Global.ADB_ENABLED,
        Settings.Global.ADB_WIFI_ENABLED,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
    ));

    public static boolean shouldHideDevStatus(ContentResolver cr, String packageName, String name) {
        return getApps(cr).contains(packageName) && settingsToHide.contains(name);
    }

    private static Set<String> getApps(ContentResolver cr) {
        String apps = Settings.Secure.getString(cr, Settings.Secure.HIDE_DEVELOPER_STATUS);
        if (apps != null) {
            mApps = new HashSet<>(Arrays.asList(apps.split(",")));
        } else {
            mApps = new HashSet<>();
        }
        return mApps;
    }

    public void addApp(Context mContext, String packageName, int userId) {
        mApps.add(packageName);
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, String.join(",", mApps), userId);
    }

    public void removeApp(Context mContext, String packageName, int userId) {
        mApps.remove(packageName);
        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, String.join(",", mApps), userId);
    }

    public void setApps(Context mContext, int userId) {
        String apps = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.HIDE_DEVELOPER_STATUS, userId);
        if (apps != null) {
            mApps = new HashSet<>(Arrays.asList(apps.split(",")));
        } else {
            mApps = new HashSet<>();
        }
    }
}
