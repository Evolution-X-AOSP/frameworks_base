/*
 * Copyright (C) 2016 The CyanogenMod Project
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
package com.android.server.custom.display;

import android.content.Context;
import android.hardware.display.ColorDisplayManager;
import android.os.Handler;
import android.util.Log;

import com.android.server.custom.LineageBaseFeature;
import com.android.server.custom.display.LiveDisplayService.State;
import com.android.server.custom.display.TwilightTracker.TwilightState;

import java.util.BitSet;

import static com.android.server.custom.display.LiveDisplayService.ALL_CHANGED;
import static com.android.server.custom.display.LiveDisplayService.DISPLAY_CHANGED;
import static com.android.server.custom.display.LiveDisplayService.MODE_CHANGED;
import static com.android.server.custom.display.LiveDisplayService.TWILIGHT_CHANGED;

public abstract class LiveDisplayFeature extends LineageBaseFeature {

    protected static final String TAG = "LiveDisplay";
    protected static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    protected final boolean mNightDisplayAvailable;

    private State mState;

    public LiveDisplayFeature(Context context, Handler handler) {
        super(context, handler);
        mNightDisplayAvailable = ColorDisplayManager.isNightDisplayAvailable(mContext);
    }

    public abstract boolean getCapabilities(final BitSet caps);

    protected abstract void onUpdate();

    void update(final int flags, final State state) {
        mState = state;
        if ((flags & DISPLAY_CHANGED) != 0) {
            onScreenStateChanged();
        }
        if (((flags & TWILIGHT_CHANGED) != 0) && mState.mTwilight != null) {
            onTwilightUpdated();
        }
        if ((flags & MODE_CHANGED) != 0) {
            onUpdate();
        }
        if (flags == ALL_CHANGED) {
            onSettingsChanged(null);
        }
    }

    protected void onScreenStateChanged() { }

    protected void onTwilightUpdated() { }


    protected final boolean isLowPowerMode() {
        return mState.mLowPowerMode;
    }

    protected final int getMode() {
        return mState.mMode;
    }

    protected final boolean isScreenOn() {
        return mState.mScreenOn;
    }

    protected final TwilightState getTwilight() {
        return mState.mTwilight;
    }

    public final boolean isNight() {
        return mState.mTwilight != null && mState.mTwilight.isNight();
    }
}
