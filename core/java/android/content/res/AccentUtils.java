/**
 * Copyright (C) 2020 ion-OS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

public class AccentUtils {
    private static final String TAG = "AccentUtils";

    private static final String ACCENT_COLOR_PROP = "persist.sys.evolution.accent_color";
    private static final String QS_BG_COLOR_PROP = "persist.sys.evolution.qs_bg_color";

    static boolean isResourceAccent(String resName) {
        return resName.contains("accent_device_default_light")
                || resName.contains("accent_device_default_dark")
                || resName.contains("colorAccent");
    }

    static boolean isResourceQSbgColor(String resName) {
        return resName.contains("quick_settings_status_bar_background_color");
    }

    public static int getNewAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_COLOR_PROP);
    }

    public static int getNewQSbgColor(int defaultColor) {
        return getQSbgColor(defaultColor, QS_BG_COLOR_PROP);
    }

    private static int getAccentColor(int defaultColor, String property) {
        try {
            String colorValue = SystemProperties.get(property, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor : colorValue.equals("ff1a73e8")
                    ? defaultColor : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }

    private static int getQSbgColor(int defaultColor, String property) {
        try {
            String colorValue = SystemProperties.get(property, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set QSbg color: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
