/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.util.evolution;

import android.app.Application;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** @hide */
public final class AttestationHooks {
    private static final String TAG = "Attestation";
    private static final boolean DEBUG = false;

    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";

    private static final Map<String, Object> sP1Props = new HashMap<>();
    static {
        sP1Props.put("BRAND", "google");
        sP1Props.put("MANUFACTURER", "Google");
        sP1Props.put("DEVICE", "marlin");
        sP1Props.put("PRODUCT", "marlin");
        sP1Props.put("MODEL", "Pixel XL");
        sP1Props.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
    }

    private static final Map<String, Object> sP7Props = new HashMap<>();
    static {
        sP7Props.put("BRAND", "google");
        sP7Props.put("MANUFACTURER", "Google");
        sP7Props.put("DEVICE", "cheetah");
        sP7Props.put("PRODUCT", "cheetah");
        sP7Props.put("MODEL", "Pixel 7 Pro");
        sP7Props.put("FINGERPRINT", "google/cheetah/cheetah:13/TQ2A.230305.008.C1/9619669:user/release-keys");
    }

    private static volatile boolean sIsGms = false;
    private static volatile boolean sIsFinsky = false;

    private AttestationHooks() { }

    private static void setBuildField(String key, String value) {
        try {
            // Unlock
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionField(String key, Integer value) {
        try {
            // Unlock
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);

            // Edit
            field.set(null, value);

            // Lock
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void spoofBuildGms() {
        // Alter model name and fingerprint to avoid hardware attestation enforcement
        setBuildField("FINGERPRINT", "google/marlin/marlin:7.1.2/NJH47F/4146041:user/release-keys");
        setBuildField("PRODUCT", "marlin");
        setBuildField("DEVICE", "marlin");
        setBuildField("MODEL", "Pixel XL");
        setVersionField("DEVICE_INITIAL_SDK_INT", Build.VERSION_CODES.N_MR1);
    }

    public static void initApplicationBeforeOnCreate(Application app) {
        if (PACKAGE_GMS.equals(app.getPackageName())) {
            final String processName = Application.getProcessName();
            if (processName.toLowerCase().contains("unstable")) {
                sIsGms = true;
                spoofBuildGms();
            } else {
                dlog("Spoofing Pixel 7 Pro for Google Play Services");
                sP7Props.forEach((k, v) -> setPropValue(k, v));
            }
        }

        if (PACKAGE_FINSKY.equals(app.getPackageName())) {
            sIsFinsky = true;
        }

        if (PACKAGE_GPHOTOS.equals(app.getPackageName())) {
            if (!SystemProperties.getBoolean("persist.sys.pixelprops.gphotos", false)) {
                dlog("Photos spoofing disabled by system prop");
                return;
            } else {
                dlog("Spoofing Pixel XL for Google Photos");
                sP1Props.forEach((k, v) -> setPropValue(k, v));
            }
        }
    }

    private static void setPropValue(String key, Object value){
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return sIsGms && Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet or Play Integrity
        if (isCallerSafetyNet() || sIsFinsky) {
            Log.i(TAG, "Blocked key attestation sIsGms=" + sIsGms + " sIsFinsky=" + sIsFinsky);
            throw new UnsupportedOperationException();
        }
    }

    public static void dlog(String msg) {
      if (DEBUG) Log.d(TAG, msg);
    }
}
