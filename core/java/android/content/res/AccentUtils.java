package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/** @hide */
public class AccentUtils {
    private static final String TAG = "AccentUtils";

    private static ArrayList<String> accentResources = new ArrayList<>(
            Arrays.asList("accent_device_default_light",
                    "accent_device_default_dark"));

    private static final String ACCENT_COLOR_PROP = "persist.sys.theme.accentcolor";

    static boolean isResourceAccent(String resName) {
        for (String ar : accentResources)
            if (resName.contains(ar))
                return true;
        return false;
    }

    public static int getNewAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_COLOR_PROP);
    }

    private static int getAccentColor(int defaultColor, String property) {
        try {
            String colorValue = SystemProperties.get(property, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
