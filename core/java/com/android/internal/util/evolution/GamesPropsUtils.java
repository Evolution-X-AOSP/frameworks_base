/*
 * Copyright (C) 2023-2024 The Android Open Source Project
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

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GamesPropsUtils {

    private static final String TAG = GamesPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final Map<String, Map<String, Object>> propsToChange = new HashMap<>();
    private static final Map<String, String[]> packagesToChange = new HashMap<>();

    static {
        propsToChange.put("BS4", createBS4Props());
        packagesToChange.put("BS4", new String[]{
                "com.proximabeta.mf.uamo"
        });

        propsToChange.put("F5", createF5Props());
        packagesToChange.put("F5", new String[]{
                "com.dts.freefiremax",
                "com.dts.freefireth",
                "com.mobile.legends"
        });

        propsToChange.put("iQ11", createiQ11Props());
        packagesToChange.put("iQ11", new String[]{
                "com.tencent.KiHan",
                "com.tencent.tmgp.cf",
                "com.tencent.tmgp.cod",
                "com.tencent.tmgp.gnyx"
        });

        propsToChange.put("MI11T", createMI11TProps());
        packagesToChange.put("MI11T", new String[]{
                "com.ea.gp.apexlegendsmobilefps",
                "com.levelinfinite.hotta.gp",
                "com.supercell.clashofclans",
                "com.vng.mlbbvn"
        });

        propsToChange.put("MI13P", createMI13PProps());
        packagesToChange.put("MI13P", new String[]{
                "com.levelinfinite.sgameGlobal",
                "com.tencent.tmgp.sgame"
        });

        propsToChange.put("NX729J", createNX729JProps());
        packagesToChange.put("NX729J", new String[]{
                "com.YoStar.AetherGazer"
        });

        propsToChange.put("OP8P", createOP8PProps());
        packagesToChange.put("OP8P", new String[]{
                "com.netease.lztgglobal",
                "com.pubg.imobile",
                "com.pubg.krmobile",
                "com.rekoo.pubgm",
                "com.riotgames.league.wildrift",
                "com.riotgames.league.wildrifttw",
                "com.riotgames.league.wildriftvn",
                "com.tencent.ig",
                "com.tencent.tmgp.pubgmhd",
                "com.vng.pubgmobile"
        });

        propsToChange.put("OP9P", createOP9PProps());
        packagesToChange.put("OP9P", new String[]{
                "com.epicgames.fortnite",
                "com.epicgames.portal",
                "com.tencent.lolm",
                "jp.konami.pesam"
        });

        propsToChange.put("ROG3", createROG3Props());
        packagesToChange.put("ROG3", new String[]{
                "com.ea.gp.fifamobile",
                "com.pearlabyss.blackdesertm",
                "com.pearlabyss.blackdesertm.gl"
        });

        propsToChange.put("ROG6", createROG6Props());
        packagesToChange.put("ROG6", new String[]{
                "com.activision.callofduty.shooter",
                "com.gameloft.android.ANMP.GloftA9HM",
                "com.madfingergames.legends",
                "com.riotgames.league.teamfighttactics",
                "com.riotgames.league.teamfighttacticstw",
                "com.riotgames.league.teamfighttacticsvn"
        });

        propsToChange.put("XP5", createXP5Props());
        packagesToChange.put("XP5", new String[]{
                "com.tencent.tmgp.kr.codm",
                "com.garena.game.codm",
                "com.vng.codmvn"
        });
    }

    private static Map<String, Object> createBS4Props() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "blackshark");
        props.put("MANUFACTURER", "blackshark");
        props.put("MODEL", "SHARK PRS-A0");
        return props;
    }

    private static Map<String, Object> createF5Props() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "Xiaomi");
        props.put("MANUFACTURER", "Xiaomi");
        props.put("MODEL", "23049PCD8G");
        return props;
    }

    private static Map<String, Object> createiQ11Props() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "vivo");
        props.put("MANUFACTURER", "vivo");
        props.put("MODEL", "V2243A");
        return props;
    }

    private static Map<String, Object> createMI11TProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "Xiaomi");
        props.put("MANUFACTURER", "Xiaomi");
        props.put("MODEL", "21081111RG");
        return props;
    }

    private static Map<String, Object> createMI13PProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "Xiaomi");
        props.put("MANUFACTURER", "Xiaomi");
        props.put("MODEL", "2210132C");
        return props;
    }

    private static Map<String, Object> createNX729JProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "nubia");
        props.put("DEVICE", "NX729J");
        props.put("MANUFACTURER", "nubia");
        props.put("MODEL", "NX729J");
        return props;
    }

    private static Map<String, Object> createOP8PProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "OnePlus");
        props.put("DEVICE", "OnePlus8Pro");
        props.put("MANUFACTURER", "OnePlus");
        props.put("MODEL", "IN2020");
        return props;
    }

    private static Map<String, Object> createOP9PProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "OnePlus");
        props.put("DEVICE", "OnePlus9Pro");
        props.put("MANUFACTURER", "OnePlus");
        props.put("MODEL", "LE2101");
        return props;
    }

    private static Map<String, Object> createROG3Props() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "asus");
        props.put("MANUFACTURER", "asus");
        props.put("MODEL", "ASUS_I003D");
        return props;
    }

    private static Map<String, Object> createROG6Props() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "asus");
        props.put("MANUFACTURER", "asus");
        props.put("MODEL", "ASUS_AI2201");
        return props;
    }

    private static Map<String, Object> createXP5Props() {
        Map<String, Object> props = new HashMap<>();
        props.put("BRAND", "Sony");
        props.put("MANUFACTURER", "Sony");
        props.put("MODEL", "SO-52A");
        return props;
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();

        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        if (!SystemProperties.getBoolean("persist.sys.pixelprops.games", false)) {
            return;
        }
        for (String device : packagesToChange.keySet()) {
            String[] packages = packagesToChange.get(device);
            if (Arrays.asList(packages).contains(packageName)) {
                dlog("Defining props for: " + packageName);
                Map<String, Object> props = propsToChange.get(device);
                for (Map.Entry<String, Object> prop : props.entrySet()) {
                    String key = prop.getKey();
                    Object value = prop.getValue();
                    setPropValue(key, value);
                }
                break;
            }
        }
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

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }
}
