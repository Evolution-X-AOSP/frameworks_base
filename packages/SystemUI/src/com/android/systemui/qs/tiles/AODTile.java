/*
 * Copyright (C) 2018 The OmniROM Project
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

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.util.settings.SecureSettings;

import javax.inject.Inject;

public final class AODTile extends QSTileImpl<State> implements
        BatteryController.BatteryStateChangeCallback {

    private static final Intent LS_DISPLAY_SETTINGS = new Intent("android.settings.LOCK_SCREEN_SETTINGS");
    private static final Icon sIcon = ResourceIcon.get(R.drawable.ic_qs_aod);

    private final SecureSettings mSecureSettings;
    private final ContentObserver mObserver;
    private final BatteryController mBatteryController;
    private final AmbientDisplayConfiguration mConfig;

    private boolean mListening;

    private enum DozeState {
        OFF(0, 0),
        ALWAYS_ON_CHARGE(0, 1),
        ALWAYS_ON(1, 0);

        final int dozeAlwaysOnValue;
        final int dozeOnChargeValue;

        DozeState(int dozeAlwaysOnValue, int dozeOnChargeValue) {
            this.dozeAlwaysOnValue = dozeAlwaysOnValue;
            this.dozeOnChargeValue = dozeOnChargeValue;
        }
    };

    @Inject
    public AODTile(
        QSHost host,
        @Background Looper backgroundLooper,
        @Main Handler mainHandler,
        FalsingManager falsingManager,
        MetricsLogger metricsLogger,
        StatusBarStateController statusBarStateController,
        ActivityStarter activityStarter,
        QSLogger qsLogger,
        SecureSettings secureSettings,
        BatteryController batteryController
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mSecureSettings = secureSettings;
        mBatteryController = batteryController;
        mObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                refreshState();
            }
        };
        mConfig = new AmbientDisplayConfiguration(mContext);
    }

    @Override
    protected void handleInitialize() {
        super.handleInitialize();
        mUiHandler.post(() -> mBatteryController.observe(getLifecycle(), this));
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        refreshState();
    }

    private DozeState getDozeState() {
        final boolean alwaysOn = mSecureSettings.getIntForUser(
            Settings.Secure.DOZE_ALWAYS_ON, 0, UserHandle.USER_CURRENT) == 1;
        if (alwaysOn) return DozeState.ALWAYS_ON;
        final boolean alwaysOnCharge = mSecureSettings.getIntForUser(
            Settings.Secure.DOZE_ON_CHARGE, 0, UserHandle.USER_CURRENT) == 1;
        if (alwaysOnCharge) {
            return DozeState.ALWAYS_ON_CHARGE;
        } else {
            return DozeState.OFF;
        }
    }

    @Override
    public boolean isAvailable() {
        return mConfig.alwaysOnAvailable();
    }

    @Override
    public State newTileState() {
        final State state = new State();
        state.icon = sIcon;
        return state;
    }

    @Override
    public void handleClick(@Nullable View view) {
        final DozeState newState;
        switch (getDozeState()) {
            case OFF:
                newState = DozeState.ALWAYS_ON_CHARGE;
                break;
            case ALWAYS_ON_CHARGE:
                newState = DozeState.ALWAYS_ON;
                break;
            case ALWAYS_ON:
                newState = DozeState.OFF;
                break;
            default:
                newState = DozeState.OFF;
        }
        mSecureSettings.putIntForUser(
            Settings.Secure.DOZE_ALWAYS_ON, newState.dozeAlwaysOnValue,
            UserHandle.USER_CURRENT);
        mSecureSettings.putIntForUser(
            Settings.Secure.DOZE_ON_CHARGE, newState.dozeOnChargeValue,
            UserHandle.USER_CURRENT);
    }

    @Override
    public Intent getLongClickIntent() {
        return LS_DISPLAY_SETTINGS;
    }

    @Override
    public CharSequence getTileLabel() {
        if (mBatteryController.isAodPowerSave()) {
            return mContext.getString(R.string.quick_settings_aod_off_powersave_label);
        }
        switch (getDozeState()) {
            case ALWAYS_ON_CHARGE:
                return mContext.getString(R.string.quick_settings_aod_on_charge_label);
            case ALWAYS_ON:
                return mContext.getString(R.string.quick_settings_aod_label);
            default:
                return mContext.getString(R.string.quick_settings_aod_off_label);
        }
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.label = getTileLabel();
        if (mBatteryController.isAodPowerSave()) {
            state.state = Tile.STATE_UNAVAILABLE;
        } else {
            state.state = getDozeState() == DozeState.OFF ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EVO_QS_TILES;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (mListening) {
            mSecureSettings.registerContentObserverForUser(
                Settings.Secure.DOZE_ALWAYS_ON, mObserver, UserHandle.USER_ALL);
            mSecureSettings.registerContentObserverForUser(
                Settings.Secure.DOZE_ON_CHARGE, mObserver, UserHandle.USER_ALL);
        } else {
            mSecureSettings.unregisterContentObserver(mObserver);
        }
    }
}
