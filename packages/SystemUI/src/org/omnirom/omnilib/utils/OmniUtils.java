/*
* Copyright (C) 2017-2021 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.omnirom.omnilib.utils;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.UserHandle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

public class OmniUtils {

    public static final String OMNILIB_PACKAGE_NAME = "org.omnirom.omnilib";

    public static int getQSColumnsCount(Context context, int resourceCount) {
        final int QS_COLUMNS_MIN = 2;
        final Resources res = context.getResources();
        int value = QS_COLUMNS_MIN;
        if (res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            value = Settings.System.getIntForUser(
                    context.getContentResolver(), "qs_layout_columns",
                    resourceCount, UserHandle.USER_CURRENT);
        } else {
            value = Settings.System.getIntForUser(
                    context.getContentResolver(), "qs_layout_columns_landscape",
                    resourceCount, UserHandle.USER_CURRENT);
        }
        return Math.max(QS_COLUMNS_MIN, value);
    }

    public static int getQuickQSColumnsCount(Context context, int resourceCount) {
        return getQSColumnsCount(context, resourceCount);
    }

    public static boolean getQSTileLabelHide(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.OMNI_QS_TILE_LABEL_HIDE,
                0, UserHandle.USER_CURRENT) != 0;
    }

    public static boolean getQSTileVerticalLayout(Context context, int defaultValue) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.OMNI_QS_TILE_VERTICAL_LAYOUT,
                defaultValue, UserHandle.USER_CURRENT) != 0;
    }
}
