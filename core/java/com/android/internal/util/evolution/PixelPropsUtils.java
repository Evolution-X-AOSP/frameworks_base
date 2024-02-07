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
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.util.evolution.EvolutionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Random;

public class PixelPropsUtils {

    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";
    private static final String PACKAGE_SI = "com.google.android.settings.intelligence";
    private static final String PACKAGE_SETUP_WIZARD = "com.google.android.setupwizard";
    private static final String PACKAGE_VELVET = "com.google.android.googlequicksearchbox";
    private static final String SAMSUNG = "com.samsung.";
    private static final String SPOOF_CTS = "persist.sys.velvet.cts";
    private static final String SPOOF_MUSIC_APPS = "persist.sys.disguise_props_for_music_app";
    private static final String SPOOF_PIF = "persist.sys.pif";
    private static final String SPOOF_PIXEL_PROPS = "persist.sys.pixelprops";
    private static final String SPOOF_PIXEL_RECENT = "persist.sys.pixelprops.recent";
    private static final String SPOOF_PIXEL_RECENT_ALL = "persist.sys.pixelprops.recent.all";

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final Boolean sEnablePixelProps =
            Resources.getSystem().getBoolean(R.bool.config_enablePixelProps);

    private static final Map<String, Object> propsToChangeGeneric;
    private static final Map<String, Object> propsToChangeRecentPixel;
    private static final Map<String, Object> propsToChangePixelTablet;
    private static final Map<String, Object> propsToChangePixelXL;
    private static final Map<String, Object> propsToChangeMeizu;
    private static final Map<String, ArrayList<String>> propsToKeep;

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
            "com.google.android.setupwizard",
            "com.google.ar.core"
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

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

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
        propsToChangeRecentPixel.put("ID", "UQ1A.240205.004");
        propsToChangeRecentPixel.put("FINGERPRINT", "google/husky/husky:14/UQ1A.240205.004/11269751:user/release-keys");
        propsToChangePixelTablet = new HashMap<>();
        propsToChangePixelTablet.put("BRAND", "google");
        propsToChangePixelTablet.put("MANUFACTURER", "Google");
        propsToChangePixelTablet.put("DEVICE", "tangorpro");
        propsToChangePixelTablet.put("PRODUCT", "tangorpro");
        propsToChangePixelTablet.put("HARDWARE", "tangorpro");
        propsToChangePixelTablet.put("MODEL", "Pixel Tablet");
        propsToChangePixelTablet.put("ID", "UQ1A.240205.002");
        propsToChangePixelTablet.put("FINGERPRINT", "google/tangorpro/tangorpro:14/UQ1A.240205.002/11224170:user/release-keys");
        propsToChangePixelXL = new HashMap<>();
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("HARDWARE", "marlin");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("ID", "QP1A.191005.007.A3");
        propsToChangePixelXL.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
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
        return packageName.startsWith("com.google.android.GoogleCamera")
            || Arrays.asList(customGoogleCameraPackages).contains(packageName);
    }

    private static boolean shouldTryToSpoofDevice() {
        final String processName = Application.getProcessName();
        if (!processName.toLowerCase().contains("unstable")
                && !processName.toLowerCase().contains("chimera")
                && !processName.toLowerCase().contains("pixelmigrate")
                && !processName.toLowerCase().contains("instrumentation")) {
            return false;
        }
        final boolean[] shouldCertify = {true};
        final boolean was = isGmsAddAccountActivityOnTop();
        final String reason = "GmsAddAccountActivityOnTop";
        dlog("Skip spoofing build for GMS, because " + reason + "!");
        TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                boolean isNow = isGmsAddAccountActivityOnTop();
                if (isNow ^ was) {
                    dlog(String.format("%s changed: isNow=%b, was=%b, killing myself!", reason, isNow, was));
                    shouldCertify[0] = false;
                }
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
        return shouldCertify[0];
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

    private static volatile boolean sIsGms, sIsFinsky, sIsSetupWizard, sIsGoogle;
    private static volatile String sProcessName;

    public static void setProps(Context context) {
        propsToChangeGeneric.forEach((k, v) -> setPropValue(k, v));

        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();
        Context appContext = app.getApplicationContext();
        if (packageName == null || processName == null || packageName.isEmpty()) {
            return;
        }
        final boolean sIsTablet = isDeviceTablet(appContext);
        sProcessName = processName;
        sIsGoogle = packageName.toLowerCase().contains("google") || processName.toLowerCase().contains("google");
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        sIsSetupWizard = packageName.equals(PACKAGE_SETUP_WIZARD);
        final boolean sIsExcludedPackage = Arrays.asList(packagesToKeep).contains(packageName);
        if (isGoogleCameraPackage(packageName)) {
            return;
        }
        if (Arrays.asList(packagesToKeep).contains(packageName)) {
            return;
        }
        Map<String, Object> propsToChange = new HashMap<>();
        if (sIsGms) {
            if (!SystemProperties.getBoolean(SPOOF_PIF, true)) {
                dlog("PIF is disabled by system prop");
                return;
            }
            if (shouldTryToSpoofDevice()) {
                dlog("Spoofing build for GMS to pass Play Integrity API");
                setPropValue("TIME", System.currentTimeMillis());
                spoofBuildGms(context);
            } else {
                Process.killProcess(Process.myPid());
            }
        } else if (sIsGoogle) || packageName.startsWith(SAMSUNG)
                || Arrays.asList(extraPackagesToChange).contains(packageName)) {

            if (!sEnablePixelProps || !SystemProperties.getBoolean(SPOOF_PIXEL_PROPS, true)) {
                dlog("Pixel props is disabled by config or system prop");
                return;
            } else if (processName.toLowerCase().contains("ui")
                    && processName.toLowerCase().contains("gservice")
                    && processName.toLowerCase().contains("gapps")
                    && processName.toLowerCase().contains("learning")
                    && processName.toLowerCase().contains("search")
                    && processName.toLowerCase().contains("persistent")) {
                return;
            } else if (packageName.equals(PACKAGE_GPHOTOS)) {
                if (SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", true)) {
                    propsToChange.putAll(propsToChangePixelXL);
                }
            } else if (sIsTablet) {
                propsToChange.putAll(propsToChangePixelTablet);
            } else {
                propsToChange.putAll(propsToChangeRecentPixel);
            }
        } else if (SystemProperties.getBoolean(SPOOF_MUSIC_APPS, false)
                && Arrays.asList(packagesToChangeMeizu).contains(packageName)) {
            propsToChange.putAll(propsToChangeMeizu);
        }
        if (propsToChange == null || propsToChange.isEmpty()) return;
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
            ActivityTaskManager.RootTaskInfo focusedTask =
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
        if (!shouldTryToSpoofDevice() || sIsSetupWizard) {
            Process.killProcess(Process.myPid());
        }
        if ((isCallerSafetyNet() || sIsFinsky)) {
            dlog("Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
