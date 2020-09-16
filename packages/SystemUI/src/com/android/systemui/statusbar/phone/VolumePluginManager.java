/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2019 ArrowOS
 * Copyright (C) 2020 Potato Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.statusbar.phone;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.VisibleForTesting;

import com.android.internal.util.ArrayUtils;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.PluginEnablerImpl;
import com.android.systemui.shared.plugins.PluginEnabler;
import com.android.systemui.shared.plugins.PluginInstanceManager;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.shared.plugins.PluginPrefs;

import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Preference controller to allow users to choose an overlay from a list for a given category.
 * The chosen overlay is enabled along with its Ext overlays belonging to the same category.
 * A default option is also exposed that disables all overlays in the given category.
 */
public class VolumePluginManager extends BroadcastReceiver {
    private static final String TAG = "VolumePluginManager";
    private static final Uri SETTING_URI = Settings.System.getUriFor(
        Settings.System.SYSTEMUI_PLUGIN_VOLUME);

    static final String[] ALLOWED_PLUGINS = {
        "none",
        "co.potatoproject.plugin.volume.aosp",
        "co.potatoproject.plugin.volume.compact",
        "co.potatoproject.plugin.volume.oreo",
        "co.potatoproject.plugin.volume.tiled",
    };

    static final String DEFAULT_VOLUME_PLUGIN = ALLOWED_PLUGINS[1];
    static final String VOLUME_PLUGIN_ACTION = "com.android.systemui.action.PLUGIN_VOLUME";

    private PluginPrefs mPluginPrefs;
    private PluginEnabler mPluginEnabler;
    private PluginManager mManager;
    private PackageManager mPackageManager;
    private Handler mHandler;
    private CustomSettingsObserver mCustomSettingsObserver;
    private String mCurrentPlugin = DEFAULT_VOLUME_PLUGIN;
    private Context mContext;
    private ContentResolver mResolver;

    public VolumePluginManager() {}

    public VolumePluginManager(Context context, Handler handler) {
        mPluginEnabler = new PluginEnablerImpl(context);
        mPluginPrefs = new PluginPrefs(context);
        mManager = Dependency.get(PluginManager.class);
        mPackageManager = context.getPackageManager();
        mHandler = handler;
        mContext = context;
        mResolver = mContext.getContentResolver();
        mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
        mCustomSettingsObserver.observe();
        PackageInfo enabledPlugin = getEnabledPlugin(getPluginInfo());
        if (enabledPlugin != null)
            mCurrentPlugin = enabledPlugin.packageName;
        updateState();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mContext == null) mContext = context;
        if (mHandler == null) mHandler = new Handler(mContext.getMainLooper());
        if (mResolver == null) mResolver = mContext.getContentResolver();
        updateState();
    }

    private void setPlugin(String packageName) {
        if (mCurrentPlugin.equals(packageName)) {
            // Already set.
            return;
        }

        Handler handler = new Handler(mContext.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                togglePlugins(packageName);
            }
        };
        handler.post(runnable);
    }

    private Boolean togglePlugins(String currentPackageName) {
        List<PackageInfo> plugins = getPluginInfo();
        PackageInfo enabledPlugin = getEnabledPlugin(plugins);
        try {
            if (enabledPlugin != null) {
                String enabledPkg = enabledPlugin.packageName;
                ComponentName EnabledComponentName = new ComponentName(enabledPkg,
                        enabledPlugin.services[0].name);
                mPluginEnabler.setDisabled(EnabledComponentName, PluginEnabler.DISABLED_MANUALLY);
                sendBroadcast(enabledPkg);
            }
            for (PackageInfo plugin : plugins) {
                String pkg = plugin.packageName;

                if (currentPackageName.equals(pkg)) {
                    ComponentName componentName = new ComponentName(pkg,
                            plugin.services[0].name);
                    mPluginEnabler.setEnabled(componentName);
                    sendBroadcast(pkg);
                    break;
                }
            }
        } catch (Exception re) {
            Log.w(TAG, "Error handling overlays.", re);
            return false;
        }
        mCurrentPlugin = currentPackageName;
        return true;
    }

    public void updateState() {
        String value = Settings.System.getString(mResolver, Settings.System.SYSTEMUI_PLUGIN_VOLUME);
        if (value != mCurrentPlugin) {
            if(Arrays.asList(ALLOWED_PLUGINS).contains(value)) {
                setPlugin(value);
            } else {
                setPlugin(DEFAULT_VOLUME_PLUGIN);
            }
        }
    }

    private PackageInfo getEnabledPlugin(List<PackageInfo> plugins) {
        List<PackageInfo> packages = plugins;

        for(PackageInfo pkg : packages) {
            ComponentName componentName = new ComponentName(pkg.packageName,
                    pkg.services[0].name);
            if (!mPluginEnabler.isEnabled(componentName)) {
                continue;
            }
            return pkg;
        }

        return null;
    }

    private List<PackageInfo> getPluginInfo() {
        List<String> plugins = new ArrayList<String>();
        List<ResolveInfo> result = mPackageManager.queryIntentServices(
                new Intent(VOLUME_PLUGIN_ACTION), PackageManager.MATCH_DISABLED_COMPONENTS);
        for (ResolveInfo info : result) {
            String packageName = info.serviceInfo.packageName;
            plugins.add(packageName);
        }

        List<PackageInfo> apps = mPackageManager.getPackagesHoldingPermissions(new String[]{
                PluginInstanceManager.PLUGIN_PERMISSION},
                PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.GET_SERVICES);
        List<PackageInfo> returnList = new ArrayList<PackageInfo>();

        for(PackageInfo app : apps) {
            if (!plugins.contains(app.packageName)) continue;

            returnList.add(app);
        }

        return returnList;
    }

    private void sendBroadcast(String pkg) {
        final Intent intent = new Intent(PluginManager.PLUGIN_CHANGED,
                pkg != null ? Uri.fromParts("package", pkg, null) : null);
        mContext.sendBroadcast(intent);
    }

    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
	        mResolver.registerContentObserver(SETTING_URI,
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateState();
        }
    }
}
