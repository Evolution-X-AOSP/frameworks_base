/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2022 StatiXOS
 *               2021-2022 crDroid Android Project
 *               2019-2023 Evolution X
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.evolution;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.util.evolution.EvolutionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PixelPropsUtils {

    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_GOOGLE = "com.google";
    private static final String PACKAGE_SAMSUNG = "com.samsung";
    private static final String PACKAGE_SI = "com.google.android.settings.intelligence";
    private static final String SPOOF_MUSIC_APPS = "persist.sys.disguise_props_for_music_app";
    private static final String SPOOF_PIF = "persist.sys.pif";
    private static final String SPOOF_PIXEL_PROPS = "persist.sys.pixelprops";

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final String DEVICE = "org.evolution.device";
    private static final boolean DEBUG = SystemProperties.getBoolean("persist.sys.pixelprops.debug", false);

    private static final Boolean sEnablePixelProps =
            Resources.getSystem().getBoolean(R.bool.config_enablePixelProps);

    private static final Map<String, Object> propsToChangeGeneric;
    private static final Map<String, Object> propsToChangeRecentPixel;
    private static final Map<String, Object> propsToChangePixelTablet;
    private static final Map<String, Object> propsToChangePixel5a;
    private static final Map<String, Object> propsToChangeMeizu;
    private static final Map<String, ArrayList<String>> propsToKeep;

    // Packages to Spoof as the most recent Pixel device
    private static final String[] packagesToChangeRecentPixel = {
            "com.google.android.apps.bard",
            "com.google.android.apps.customization.pixel",
            "com.google.android.apps.emojiwallpaper",
            "com.google.android.apps.privacy.wildlife",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.wallpaper.pixel",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.google.android.wallpaper.effects",
            "com.google.pixel.livewallpaper"
    };

    private static final String[] extraPackagesToChange = {
            "com.amazon.avod.thirdpartyclient",
            "com.android.chrome",
            "com.breel.wallpapers20",
            "com.disney.disneyplus",
            "com.microsoft.android.smsorganizer",
            "com.nhs.online.nhsonline",
            "com.nothing.smartcenter",
            "in.startv.hotstar",
            "jp.id_credit_sp2.android"
    };

    private static final String[] customGoogleCameraPackages = {
            "com.google.android.MTCL83",
            "com.google.android.UltraCVM",
            "com.google.android.apps.cameralite"
    };

    // Packages to Keep with original device
    private static final String[] packagesToKeep = {
            "com.google.android.apps.dreamlinerupdater",
            "com.google.android.apps.dreamliner",
            "com.google.android.apps.miphone.aiai.AiaiApplication",
            "com.google.android.apps.motionsense.bridge",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.apps.photos",
            "com.google.android.apps.pixelmigrate",
            "com.google.android.apps.recorder",
            "com.google.android.apps.restore",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.tips",
            "com.google.android.apps.tycho",
            "com.google.android.apps.wearables.maestro.companion",
            "com.google.android.apps.youtube.kids",
            "com.google.android.apps.youtube.music",
            "com.google.android.as",
            "com.google.android.backupuses",
            "com.google.android.backuptransport",
            "com.google.android.dialer",
            "com.google.android.euicc",
            "com.google.android.setupwizard",
            "com.google.android.youtube",
            "com.google.ar.core",
            "com.google.intelligence.sense",
            "com.google.oslo"
    };

    // Packages to Spoof as Meizu
    private static final String[] packagesToChangeMeizu = {
            "com.hihonor.cloudmusic",
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.kugou.android",
            "com.kugou.android.lite",
            "cmccwm.mobilemusic",
            "cn.kuwo.player",
            "com.meizu.media.music"
    };

    // Codenames for currently supported Pixels by Google
    private static final String[] pixelCodenames = {
            "husky",
            "shiba",
            "felix",
            "tangorpro",
            "lynx",
            "cheetah",
            "panther",
            "bluejay",
            "oriole",
            "raven",
            "barbet"
    };

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static volatile boolean sIsGms, sIsFinsky, sIsExcluded;
    private static volatile String sProcessName;

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put(PACKAGE_SI, new ArrayList<>(Collections.singletonList("FINGERPRINT")));
        propsToChangeGeneric = new HashMap<>();
        propsToChangeGeneric.put("TYPE", "user");
        propsToChangeGeneric.put("TAGS", "release-keys");
        propsToChangeRecentPixel = new HashMap<>();
        propsToChangeRecentPixel.put("BRAND", "google");
        propsToChangeRecentPixel.put("MANUFACTURER", "Google");
        propsToChangeRecentPixel.put("DEVICE", "husky");
        propsToChangeRecentPixel.put("PRODUCT", "husky");
        propsToChangeRecentPixel.put("HARDWARE", "husky");
        propsToChangeRecentPixel.put("MODEL", "Pixel 8 Pro");
        propsToChangeRecentPixel.put("ID", "AP1A.240405.002");
        propsToChangeRecentPixel.put("FINGERPRINT", "google/husky/husky:14/AP1A.240405.002/11480754:user/release-keys");
        propsToChangePixelTablet = new HashMap<>();
        propsToChangePixelTablet.put("BRAND", "google");
        propsToChangePixelTablet.put("MANUFACTURER", "Google");
        propsToChangePixelTablet.put("DEVICE", "tangorpro");
        propsToChangePixelTablet.put("PRODUCT", "tangorpro");
        propsToChangePixelTablet.put("HARDWARE", "tangorpro");
        propsToChangePixelTablet.put("MODEL", "Pixel Tablet");
        propsToChangePixelTablet.put("ID", "AP1A.240405.002");
        propsToChangePixelTablet.put("FINGERPRINT", "google/tangorpro/tangorpro:14/AP1A.240405.002/11480754:user/release-keys");
        propsToChangePixel5a = new HashMap<>();
        propsToChangePixel5a.put("BRAND", "google");
        propsToChangePixel5a.put("MANUFACTURER", "Google");
        propsToChangePixel5a.put("DEVICE", "barbet");
        propsToChangePixel5a.put("PRODUCT", "barbet");
        propsToChangePixel5a.put("HARDWARE", "barbet");
        propsToChangePixel5a.put("MODEL", "Pixel 5a");
        propsToChangePixel5a.put("ID", "AP1A.240405.002");
        propsToChangePixel5a.put("FINGERPRINT", "google/barbet/barbet:14/AP1A.240405.002/11480754:user/release-keys");
        propsToChangeMeizu = new HashMap<>();
        propsToChangeMeizu.put("BRAND", "meizu");
        propsToChangeMeizu.put("MANUFACTURER", "Meizu");
        propsToChangeMeizu.put("DEVICE", "m1892");
        propsToChangeMeizu.put("DISPLAY", "Flyme");
        propsToChangeMeizu.put("PRODUCT", "meizu_16thPlus_CN");
        propsToChangeMeizu.put("MODEL", "meizu 16th Plus");
    }

    public static String getBuildID(String fingerprint) {
        Pattern pattern = Pattern.compile("([A-Za-z0-9]+\\.\\d+\\.\\d+\\.\\w+)");
        Matcher matcher = pattern.matcher(fingerprint);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static String getDeviceName(String fingerprint) {
        String[] parts = fingerprint.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    private static boolean isGoogleCameraPackage(String packageName) {
        return packageName.contains("GoogleCamera")
                || Arrays.asList(customGoogleCameraPackages).contains(packageName);
    }

    private static boolean shouldTryToCertifyDevice() {
        final String processName = Application.getProcessName();
        if (!processName.toLowerCase().contains("unstable")
                && !processName.toLowerCase().contains("pixelmigrate")
                && !processName.toLowerCase().contains("instrumentation")) {
            return false;
        }
        final boolean was = isGmsAddAccountActivityOnTop();
        final String reason = "GmsAddAccountActivityOnTop";
        if (!was) {
            return true;
        }
        dlog("Skip spoofing build for GMS, because " + reason + "!");
        TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean isNow = isGmsAddAccountActivityOnTop();
                if (isNow ^ was) {
                    dlog(String.format("%s changed: isNow=%b, was=%b, killing myself!", reason, isNow, was));
                    Process.killProcess(Process.myPid());
                }
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
            return true;
        }
    }

    public static void spoofBuildGms(Context context) {
        String packageName = "com.goolag.pif";

        if (!EvolutionUtils.isPackageInstalled(context, packageName)) {
            Log.e(TAG, "'" + packageName + "' is not installed.");
            return;
        }

        PackageManager pm = context.getPackageManager();

        try {
            Resources resources = pm.getResourcesForApplication(packageName);

            int resourceId = resources.getIdentifier("device_arrays", "array", packageName);
            if (resourceId != 0) {
                String[] deviceArrays = resources.getStringArray(resourceId);

                if (deviceArrays.length > 0) {
                    int randomIndex = new Random().nextInt(deviceArrays.length);
                    int selectedArrayResId = resources.getIdentifier(deviceArrays[randomIndex], "array", packageName);
                    String selectedArrayName = resources.getResourceEntryName(selectedArrayResId);

                    String[] selectedDeviceProps = resources.getStringArray(selectedArrayResId);

                    dlog("PRODUCT: " + selectedDeviceProps[0]);
                    setPropValue("PRODUCT", selectedDeviceProps[0]);

                    dlog("DEVICE: " + (selectedDeviceProps[1].isEmpty() ? getDeviceName(selectedDeviceProps[5]) : selectedDeviceProps[1]));
                    setPropValue("DEVICE", selectedDeviceProps[1].isEmpty() ? getDeviceName(selectedDeviceProps[5]) : selectedDeviceProps[1]);

                    dlog("MANUFACTURER: " + selectedDeviceProps[2]);
                    setPropValue("MANUFACTURER", selectedDeviceProps[2]);

                    dlog("BRAND: " + selectedDeviceProps[3]);
                    setPropValue("BRAND", selectedDeviceProps[3]);

                    dlog("MODEL: " + selectedDeviceProps[4]);
                    setPropValue("MODEL", selectedDeviceProps[4]);

                    dlog("FINGERPRINT: " + selectedDeviceProps[5]);
                    setPropValue("FINGERPRINT", selectedDeviceProps[5]);

                    dlog("SECURITY_PATCH: " + selectedDeviceProps[6]);
                    setVersionFieldString("SECURITY_PATCH", selectedDeviceProps[6]);

                    if (!selectedDeviceProps[7].isEmpty() && selectedDeviceProps[7].matches("2[3-6]")) {
                        dlog("DEVICE_INITIAL_SDK_INT: " + selectedDeviceProps[7]);
                        setVersionFieldInt("DEVICE_INITIAL_SDK_INT", Integer.parseInt(selectedDeviceProps[7]));
                    } else {
                        Log.e(TAG, "Value for DEVICE_INITIAL_SDK_INT must be between 23-26!");
                    }

                    dlog("ID: " + (selectedDeviceProps[8].isEmpty() ? getBuildID(selectedDeviceProps[5]) : selectedDeviceProps[8]));
                    setPropValue("ID", selectedDeviceProps[8].isEmpty() ? getBuildID(selectedDeviceProps[5]) : selectedDeviceProps[8]);

                    dlog("TYPE: " + (selectedDeviceProps[9].isEmpty() ? "user" : selectedDeviceProps[9]));
                    setPropValue("TYPE", selectedDeviceProps[9].isEmpty() ? "user" : selectedDeviceProps[9]);

                    dlog("TAGS: " + (selectedDeviceProps[10].isEmpty() ? "release-keys" : selectedDeviceProps[10]));
                    setPropValue("TAGS", selectedDeviceProps[10].isEmpty() ? "release-keys" : selectedDeviceProps[10]);

                    Settings.System.putString(context.getContentResolver(), Settings.System.PPU_SPOOF_BUILD_GMS_ARRAY, selectedArrayName);
                } else {
                    Log.e(TAG, "No device arrays found.");
                }
            } else {
                Log.e(TAG, "Resource 'device_arrays' not found.");
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting resources for '" + packageName + "': " + e.getMessage());
        }
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();
        Map<String, Object> propsToChange = new HashMap<>();
        Context appContext = context.getApplicationContext();
        final boolean sIsTablet = isDeviceTablet(appContext);
        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsExcluded = Arrays.asList(packagesToKeep).contains(packageName) || isGoogleCameraPackage(packageName);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        propsToChangeGeneric.forEach((k, v) -> setPropValue(k, v));
        if (packageName == null || processName == null || packageName.isEmpty()) {
            return;
        }
        if (sIsExcluded) {
            return;
        }
        if (sIsGms) {
            if (shouldTryToCertifyDevice()) {
                if (!SystemProperties.getBoolean(SPOOF_PIF, true)) {
                    dlog("PIF is disabled by system prop");
                    return;
                } else {
                    dlog("Spoofing GMS to pass integrity");
                    setPropValue("TIME", System.currentTimeMillis());
                    spoofBuildGms(context);
                }
            }
        } else if ((packageName.toLowerCase().contains(PACKAGE_GOOGLE) && !sIsGms)
                || packageName.toLowerCase().contains(PACKAGE_SAMSUNG)
                || Arrays.asList(extraPackagesToChange).contains(packageName)) {

            boolean isPixelDevice = Arrays.asList(pixelCodenames).contains(SystemProperties.get(DEVICE));
            if (!sEnablePixelProps || !SystemProperties.getBoolean(SPOOF_PIXEL_PROPS, true)) {
                if (isPixelDevice) {
                    dlog("Pixel props is disabled due to being a currently supported Pixel device");
                    return;
                }
                dlog("Pixel props is disabled by config or system prop");
                return;
            } else if (Arrays.asList(packagesToChangeRecentPixel).contains(packageName)) {
                if (packageName.toLowerCase().contains("com.google.android.gms")) {
                    if (processName.toLowerCase().contains("update")) {
                        return;
                    } else if (processName.toLowerCase().contains("chimera")
                            || processName.toLowerCase().contains("gservice")
                            || processName.toLowerCase().contains("gapps")) {
                        propsToChange.putAll(propsToChangePixel5a);
                    }
                    setPropValue("TIME", System.currentTimeMillis());
                }
                dlog("Spoofing Pixel 8 Pro for Google Services");
                propsToChange.putAll(propsToChangeRecentPixel);
            } else if (sIsTablet) {
                dlog("Spoofing Pixel Tablet for Google Services");
                propsToChange.putAll(propsToChangePixelTablet);
            } else {
                dlog("Spoofing Pixel 5a for Google Services");
                propsToChange.putAll(propsToChangePixel5a);
            }
        } else if (SystemProperties.getBoolean(SPOOF_MUSIC_APPS, false)
                && Arrays.asList(packagesToChangeMeizu).contains(packageName)) {
            propsToChange.putAll(propsToChangeMeizu);
        }
        dlog("Defining props for: " + packageName);
        for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
            String key = prop.getKey();
            Object value = prop.getValue();
            if (propsToKeep.containsKey(packageName) && propsToKeep.get(packageName).contains(key)) {
                dlog("Not defining " + key + " prop for: " + packageName);
                continue;
            }
            dlog("Defining " + key + " prop for: " + packageName);
            setPropValue(key, value);
        }
        // Set proper indexing fingerprint
        if (packageName.equals(PACKAGE_SI)) {
            setPropValue("FINGERPRINT", String.valueOf(Build.TIME));
            return;
        }
    }

    private static boolean isDeviceTablet(Context context) {
        if (context == null) {
            return false;
        }
        Configuration configuration = context.getResources().getConfiguration();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        }
        return (configuration.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XHIGH
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XXHIGH
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XXXHIGH;
    }

    private static void setPropValue(String key, Object value) {
        try {
            dlog("Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setVersionField(String key, Object value) {
        try {
            dlog("Defining version field " + key + " to " + value.toString());
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set version field " + key, e);
        }
    }

    private static void setVersionFieldString(String key, String value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionFieldInt(String key, int value) {
        try {
            dlog("Defining version field " + key + " to " + value);
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final String callingPackage = context.getPackageManager().getNameForUid(callingUid);
        dlog("shouldBypassTaskPermission: callingPackage:" + callingPackage);
        return callingPackage != null && callingPackage.toLowerCase().contains("google");
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if ((isCallerSafetyNet() || sIsFinsky) && !sIsExcluded) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
