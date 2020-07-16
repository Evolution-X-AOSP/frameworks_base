/*
 * Copyright (C) 2018 The OmniROM Project
 *               2020 The LineageOS Project
 *               2020 shagbag913
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

import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import com.android.systemui.R;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.statusbar.policy.BatteryController;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import javax.inject.Inject;

public class AODTile extends QSTileImpl<State> implements
        BatteryController.BatteryStateChangeCallback {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_aod);
    private final BatteryController mBatteryController;

    @Inject
    public AODTile(QSHost host, BatteryController batteryController) {
        super(host);
        mBatteryController = batteryController;
        batteryController.observe(getLifecycle(), this);
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        refreshState();
    }

    private int getAodState() {
        int aodState = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, 0, UserHandle.USER_CURRENT);
        if (aodState == 0) {
            aodState = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.DOZE_ON_CHARGE, 0, UserHandle.USER_CURRENT) == 1 ? 2 : 0;
        }
        return aodState;
    }

    @Override
    public boolean isAvailable() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_dozeAlwaysOnDisplayAvailable);
    }

    @Override
    public State newTileState() {
        State state = new State();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    public void handleClick() {
        int aodState = getAodState();
        if (aodState < 2) {
            aodState++;
        } else {
            aodState = 0;
        }
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, aodState == 2 ? 0 : aodState,
                UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.DOZE_ON_CHARGE, aodState == 2 ? 1 : 0, UserHandle.USER_CURRENT);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        if (mBatteryController.isAodPowerSave()) {
            return mContext.getString(R.string.quick_settings_aod_off_powersave_label);
        }
        switch (getAodState()) {
            case 1:
                return mContext.getString(R.string.quick_settings_aod_label);
            case 2:
                return mContext.getString(R.string.quick_settings_aod_on_charge_label);
            default:
                return mContext.getString(R.string.quick_settings_aod_off_label);
        }
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        state.icon = mIcon;
        state.label = getTileLabel();
        if (mBatteryController.isAodPowerSave()) {
            state.state = Tile.STATE_UNAVAILABLE;
        } else {
            state.state = getAodState() == 0 ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE;

        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EVO_SETTINGS;
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
