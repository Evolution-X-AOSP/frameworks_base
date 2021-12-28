/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.os.UserHandle.USER_SYSTEM;

import android.app.UiModeManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

public class ThemesUtils {

    public static final String TAG = "ThemesUtils";

    // Statusbar Signal icons
    private static final String[] SIGNAL_BAR = {
            "com.custom.systemui.signalbar_a",
            "com.custom.systemui.signalbar_b",
            "com.custom.systemui.signalbar_c",
    };

    // Statusbar Wifi icons
    private static final String[] WIFI_BAR = {
            "com.custom.systemui.wifibar_a",
            "com.custom.systemui.wifibar_b",
            "com.custom.systemui.wifibar_c",
    };
}
