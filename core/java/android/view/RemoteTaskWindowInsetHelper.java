/*
 * Copyright (C) 2023 Microsoft Corporation
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

package android.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.DeviceIntegrationUtils;

import static android.view.WindowInsets.Type.NAVIGATION_BARS;
import static android.view.WindowInsets.Type.STATUS_BARS;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

/**
 * Handle WindowInsets update when task is moved from different displays,
 * i.e. from VD to MD, or MD to VD
 */
class RemoteTaskWindowInsetHelper {
   @SuppressLint("NewApi")
   public static final int NAV_BAR_MODE_THREE_BUTTON = 0;

   private int mLastDisplayId;
   private int mCurrentDisplayId;
   private final Context mContext;
   private final boolean mDeviceIntegrationDisabled;

   public RemoteTaskWindowInsetHelper(Context context) {
      mContext = context;
      mLastDisplayId = mContext.getDisplayId();
      mCurrentDisplayId = mContext.getDisplayId();
      mDeviceIntegrationDisabled = DeviceIntegrationUtils.DISABLE_DEVICE_INTEGRATION;
   }

   public void updateDisplayId(int id) {
      if (mCurrentDisplayId != id) {
         mLastDisplayId = mCurrentDisplayId;
         mCurrentDisplayId = id;
      }
   }

   /**
    * Update the inset source's frame if it is moved from VD to MD, or MD to VD
    * @param source    InsetsSource, could be modified in-place.
    * @param frame     The frame to calculate the insets relative to.
    * @return          The updated insets source.
    */
   public InsetsSource updateInsetSourceIfNeeded(InsetsSource source, Rect frame) {
      if (mDeviceIntegrationDisabled || !isDisplayChanged() || source == null) {
         return source;
      }
      final int type = source.getType();
      if (mCurrentDisplayId <= Display.DEFAULT_DISPLAY) {
         // this is for vd to md scenario
         switch (type) {
            case STATUS_BARS:
               source.setFrame(0, 0, frame.right, getStatusBarHeight());
               break;
            case NAVIGATION_BARS:
               updateNavigationBar(source, frame);
               break;
            default:
               // other types of InsetsSource are not handled
               break;
         }
      } else {
         // this is for md to vd scenario
         source.setFrame(0, 0, 0, 0);
      }

      return source;
   }

   public boolean isDisplayChanged() {
      return mCurrentDisplayId != mLastDisplayId;
   }

   private int getStatusBarHeight() {
      final int statusBarHeightId = mContext.getResources().getIdentifier( "status_bar_height", "dimen", "android");
      return mContext.getResources().getDimensionPixelSize(statusBarHeightId);
   }

   private int getNavigationBarHeight(int orientation) {
      String name;
      if (orientation == ORIENTATION_PORTRAIT) {
         name = "navigation_bar_height";
      } else {
         name = "navigation_bar_height_landscape";
      }
      final int navBarHeightId = mContext.getResources().getIdentifier(name, "dimen", "android");
      return mContext.getResources().getDimensionPixelSize(navBarHeightId);
   }

   /**
    * returns mode of navigation bar
    * @return 0 if it uses three-button navigation; 2 if it uses full screen gesture.
    */
   private int getNavigationBarInteractionMode() {
      Resources resources = mContext.getResources();
      int resourceId = resources.getIdentifier("config_navBarInteractionMode", "integer", "android");
      if (resourceId > 0) {
         return resources.getInteger(resourceId);
      }
      return NAV_BAR_MODE_THREE_BUTTON;
   }

   private void updateNavigationBar(InsetsSource source, Rect frame) {
      if (mDeviceIntegrationDisabled) {
         return;
      }

      final int rotation = mContext.getSystemService(DisplayManager.class).getDisplay(mCurrentDisplayId).getRotation();
      switch(rotation) {
         case ROTATION_0:
         case ROTATION_180:
            source.setFrame(0, frame.bottom - getNavigationBarHeight(ORIENTATION_PORTRAIT), frame.right, frame.bottom);
            break;
         case ROTATION_90:
            if (getNavigationBarInteractionMode() == NAV_BAR_MODE_THREE_BUTTON) {
               source.setFrame(frame.right - getNavigationBarHeight(ORIENTATION_LANDSCAPE), 0, frame.right, frame.bottom);
            } else {
               source.setFrame(0, frame.bottom - getNavigationBarHeight(ORIENTATION_LANDSCAPE), frame.right, frame.bottom);
            }
            break;
         case ROTATION_270:
            if (getNavigationBarInteractionMode() == NAV_BAR_MODE_THREE_BUTTON) {
               source.setFrame(0, 0, getNavigationBarHeight(ORIENTATION_LANDSCAPE), frame.bottom);
            } else {
               source.setFrame(0, frame.bottom - getNavigationBarHeight(ORIENTATION_LANDSCAPE), frame.right, frame.bottom);
            }
            break;
         default:
            break;
      }
   }
}
