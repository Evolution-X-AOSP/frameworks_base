/*
 * Copyright (C) 2020 The Pixel Experience Project
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

import android.os.Build;
import android.util.Log;

import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final Map<String, Object> propsToChangePixelXL;
    private static final Map<String, Object> propsToChangePixel2;
    private static final Map<String, Object> propsToChangePixel3XL;
    private static final Map<String, Object> propsToChangePixel5a;
    private static final Map<String, Object> propsToChangeOnePlus9Pro;

    private static final String[] packagesToChangePixelXL = {
            "com.google.android.apps.photos",
            "com.samsung.accessory",
            "com.samsung.accessory.fridaymgr",
            "com.samsung.accessory.berrymgr",
            "com.samsung.accessory.neobeanmgr",
            "com.samsung.android.app.watchmanager",
            "com.samsung.android.geargplugin",
            "com.samsung.android.gearnplugin",
            "com.samsung.android.modenplugin",
            "com.samsung.android.neatplugin",
            "com.samsung.android.waterplugin"
    };

    private static final String[] packagesToChangePixel2 = {
            "com.google.android.gms",
            "com.google.android.gms.location.history"
    };

    private static final String[] packagesToChangePixel3XL = {
            "com.google.android.googlequicksearchbox"
    };

    private static final String[] packagesToChangePixel5a = {
            "com.breel.wallpapers20",
            "com.google.android.apps.customization.pixel",
            "com.google.android.apps.maps",
            "com.google.android.apps.safetyhub",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.turbo",
            "com.google.android.apps.turboadapter",
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.wallpaper.pixel",
            "com.google.android.as",
            "com.google.android.inputmethod.latin",
            "com.google.pixel.dynamicwallpapers",
            "com.google.pixel.livewallpaper"
    };

    private static final String[] packagesToChangeOnePlus9Pro = {
            "com.google.android.apps.wearables.maestro.companion"
    };

    static {
        propsToChangePixelXL = new HashMap<>();
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
        propsToChangePixel2 = new HashMap<>();
        propsToChangePixel2.put("BRAND", "google");
        propsToChangePixel2.put("MANUFACTURER", "Google");
        propsToChangePixel2.put("DEVICE", "walleye");
        propsToChangePixel2.put("PRODUCT", "walleye");
        propsToChangePixel2.put("MODEL", "Pixel 2");
        propsToChangePixel2.put("FINGERPRINT", "google/walleye/walleye:8.1.0/OPM1.171019.011/4448085:user/release-keys");
        propsToChangePixel3XL = new HashMap<>();
        propsToChangePixel3XL.put("BRAND", "google");
        propsToChangePixel3XL.put("MANUFACTURER", "Google");
        propsToChangePixel3XL.put("DEVICE", "crosshatch");
        propsToChangePixel3XL.put("PRODUCT", "crosshatch");
        propsToChangePixel3XL.put("MODEL", "Pixel 3 XL");
        propsToChangePixel3XL.put("FINGERPRINT", "google/crosshatch/crosshatch:11/RQ3A.210905.001/7511028:user/release-keys");
        propsToChangePixel5a = new HashMap<>();
        propsToChangePixel5a.put("BRAND", "google");
        propsToChangePixel5a.put("MANUFACTURER", "Google");
        propsToChangePixel5a.put("DEVICE", "barbet");
        propsToChangePixel5a.put("PRODUCT", "barbet");
        propsToChangePixel5a.put("MODEL", "Pixel 5a");
        propsToChangePixel5a.put("FINGERPRINT", "google/barbet/barbet:11/RD2A.210905.002/7513089:user/release-keys");
        propsToChangeOnePlus9Pro = new HashMap<>();
        propsToChangeOnePlus9Pro.put("BRAND", "OnePlus");
        propsToChangeOnePlus9Pro.put("MANUFACTURER", "OnePlus");
        propsToChangeOnePlus9Pro.put("DEVICE", "OnePlus9Pro");
        propsToChangeOnePlus9Pro.put("PRODUCT", "OnePlus9Pro_EEA");
        propsToChangeOnePlus9Pro.put("MODEL", "LE2123");
        propsToChangeOnePlus9Pro.put("FINGERPRINT", "OnePlus/OnePlus9Pro_EEA/OnePlus9Pro:11/RKQ1.201105.002/2107082109:user/release-keys");
    }

    public static void setProps(String packageName) {
        if (packageName == null){
            return;
        }
        if (Arrays.asList(packagesToChangePixelXL).contains(packageName)){
            if (DEBUG){
                Log.d(TAG, "Defining props for: " + packageName);
            }
            for (Map.Entry<String, Object> prop : propsToChangePixelXL.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                setPropValue(key, value);
            }
        }
        if (Arrays.asList(packagesToChangePixel2).contains(packageName)){
            if (DEBUG){
                Log.d(TAG, "Defining props for: " + packageName);
            }
            for (Map.Entry<String, Object> prop : propsToChangePixel2.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                setPropValue(key, value);
            }
        }
        if (Arrays.asList(packagesToChangePixel3XL).contains(packageName)){
            if (DEBUG){
                Log.d(TAG, "Defining props for: " + packageName);
            }
            for (Map.Entry<String, Object> prop : propsToChangePixel3XL.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                setPropValue(key, value);
            }
        }
        if (Arrays.asList(packagesToChangePixel5a).contains(packageName)){
            if (DEBUG){
                Log.d(TAG, "Defining props for: " + packageName);
            }
            for (Map.Entry<String, Object> prop : propsToChangePixel5a.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                setPropValue(key, value);
            }
        }
        if (Arrays.asList(packagesToChangeOnePlus9Pro).contains(packageName)){
            if (DEBUG){
                Log.d(TAG, "Defining props for: " + packageName);
            }
            for (Map.Entry<String, Object> prop : propsToChangeOnePlus9Pro.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                setPropValue(key, value);
            }
        }
        // Set proper indexing fingerprint
        if (packageName.equals("com.google.android.settings.intelligence")){
            setPropValue("FINGERPRINT", Build.EVOLUTION_FINGERPRINT);
        }
    }

    private static void setPropValue(String key, Object value){
        try {
            if (DEBUG){
                Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            }
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }
}
