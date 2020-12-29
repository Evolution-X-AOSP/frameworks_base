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

package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

import javax.inject.Inject;

/** Quick settings tile: HeadphonesBuddyTile **/
public class HeadphonesBuddyTile extends QSTileImpl<BooleanState> {

    private int mCurrentMode;
    private boolean mHeadsetIn = false;
    private static final int LEFT = -1;
    private static final int CENTER = 0;
    private static final int RIGHT = 1;

    @Inject
    public HeadphonesBuddyTile(QSHost host) {
        super(host);
        mHeadsetIn =  isHeadphonesPlugged();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = true;
        return state;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.hp_buddy);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EVO_QS_TILES;
    }

    @Override
    public void handleSetListening(boolean listening) {}

    @Override
    public void handleClick() {
        if (mHeadsetIn) {
            switchMode();
            refreshState();
        }
    }

    private int getBalanceStatus() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.MASTER_BALANCE, 0, UserHandle.USER_CURRENT);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (!mHeadsetIn) {
            state.label = mContext.getString(R.string.hp_buddy_not_connected);
            state.icon = ResourceIcon.get(R.drawable.ic_hp_disabled);
            state.contentDescription =  mContext.getString(R.string.hp_buddy_not_connected);
            state.state = Tile.STATE_UNAVAILABLE;
            swapBalance(CENTER);
            return;
        }
        mCurrentMode = getBalanceStatus();
        switch (mCurrentMode) {
            case LEFT:
                state.label = mContext.getString(R.string.hp_buddy_left);
                state.icon = ResourceIcon.get(R.drawable.ic_hp_left);
                state.contentDescription = mContext.getString(R.string.hp_buddy_left);
                state.state = Tile.STATE_ACTIVE;
                break;
            case RIGHT:
                state.label = mContext.getString(R.string.hp_buddy_right);
                state.icon = ResourceIcon.get(R.drawable.ic_hp_right);
                state.contentDescription =  mContext.getString(R.string.hp_buddy_right);
                state.state = Tile.STATE_ACTIVE;
                break;
            case CENTER:
                state.label = mContext.getString(R.string.hp_buddy_center);
                state.icon = ResourceIcon.get(R.drawable.ic_hp_center);
                state.contentDescription =  mContext.getString(R.string.hp_buddy_center);
                state.state = Tile.STATE_ACTIVE;
                break;
        }
    }

    private void switchMode() {
        mCurrentMode = getBalanceStatus();
        switch (mCurrentMode) {
            case LEFT:
                swapBalance(CENTER);
                refreshState();
                break;
            case RIGHT:
                swapBalance(LEFT);
                refreshState();
                break;
            case CENTER:
                swapBalance(RIGHT);
                refreshState();
                break;
        }
    }

    private void swapBalance(int mode) {
        Settings.System.putIntForUser(mContext.getContentResolver(),Settings.System.MASTER_BALANCE, mode,UserHandle.USER_CURRENT);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)){
                mHeadsetIn = intent.getIntExtra("state", 0) == 0 ? false : true;
                refreshState();
            }
        }
    };

    private boolean isHeadphonesPlugged(){
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for(AudioDeviceInfo deviceInfo : audioDevices){
            if(deviceInfo.getType()== AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || deviceInfo.getType()== AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP){
                return true;
            }
        }
        return false;
    }
}
