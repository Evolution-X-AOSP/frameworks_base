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

package com.android.server.wm;

import android.os.DeviceIntegrationUtils;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * Responsible for managing black screen window.
 * The key function is to distribute the power event to black screen if it exists
 * Black screen can be dismissed by pressing power button
 */
public class BlackScreenWindowManager {

    private static final String TAG = BlackScreenWindowManager.class.getSimpleName();
    private static volatile BlackScreenWindowManager sInstance;

    private final boolean mDeviceIntegrationDisabled;
    private WindowState mBlackScreenWindow;

    BlackScreenWindowManager() {
        mDeviceIntegrationDisabled = DeviceIntegrationUtils.DISABLE_DEVICE_INTEGRATION;
    }

    public static BlackScreenWindowManager getInstance() {
        if (sInstance == null) {
            synchronized (BlackScreenWindowManager.class) {
                if (sInstance == null) {
                    sInstance = new BlackScreenWindowManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Called when add a window
     */
    public void onWindowAdded(WindowState windowState) {
        if (windowState.getAttrs().type
                == WindowManager.LayoutParams.TYPE_SYSTEM_BLACKSCREEN_OVERLAY) {
            mBlackScreenWindow = windowState;
        }
    }

    /**
     * Called when remove a window
     */
    public void onWindowRemoved(WindowState windowState) {
        if (windowState.getAttrs().type
                == WindowManager.LayoutParams.TYPE_SYSTEM_BLACKSCREEN_OVERLAY) {
            mBlackScreenWindow = null;
        }
    }

    /**
     * Intercept power key down event
     *
     * @return true if black screen window handle the event, otherwise false
     */
    public boolean interceptPowerKey() {
        if (!mDeviceIntegrationDisabled) {
            if (mBlackScreenWindow != null) {
                try {
                    mBlackScreenWindow.mClient.dispatchBlackScreenKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER));
                    mBlackScreenWindow = null;
                    return true;
                } catch (RemoteException e) {
                    Log.d(TAG, "interceptPowerKey RemoteException" + e.toString());
                }
            }
        }

        mBlackScreenWindow = null;
        return false;
    }
}
