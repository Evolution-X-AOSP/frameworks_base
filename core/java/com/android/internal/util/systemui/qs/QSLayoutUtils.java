/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.internal.util.systemui.qs;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;

public class QSLayoutUtils {

   public static boolean getQSTileLabelHide(Context context) {
   return Settings.System.getIntForUser(context.getContentResolver(),
         Settings.System.QS_TILE_LABEL_HIDE,
         0, UserHandle.USER_CURRENT) == 1;
   }

   public static boolean getQSTileVerticalLayout(Context context) {
       return Settings.System.getIntForUser(context.getContentResolver(),
         Settings.System.QS_TILE_VERTICAL_LAYOUT,
         0, UserHandle.USER_CURRENT) == 1;
   }

   public static boolean updateLayout(Context context) {
       final IOverlayManager overlayManager = IOverlayManager.Stub.asInterface(ServiceManager.getService(
          	Context.OVERLAY_SERVICE));
       final int layout_qs = Settings.System.getIntForUser(context.getContentResolver(),
          	Settings.System.QS_LAYOUT,
          	42, UserHandle.USER_CURRENT);
       final int layout_qqs = Settings.System.getIntForUser(context.getContentResolver(),
          	Settings.System.QQS_LAYOUT,
          	22, UserHandle.USER_CURRENT);
       for (int i = 0; i < 2; ++i) {
          boolean isQs = i == 0;
          final int qs_row_count = (isQs ? layout_qs : layout_qqs) / 10;
          String overlay_layout = isQs ? "qs" : "qqs";
      	  String pkgName = String.format("com.android." + overlay_layout + ".portrait.layout_%sx%s", Integer.toString(qs_row_count), Integer.toString(layout_qs % 10));
      	  try {
            overlayManager.setEnabledExclusiveInCategory(pkgName, UserHandle.USER_CURRENT);
      	  } catch (RemoteException re) {
            return false;
      	  }
       }
       return true;
   }
}
