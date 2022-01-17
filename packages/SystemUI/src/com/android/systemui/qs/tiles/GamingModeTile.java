/*
 * Copyright (C) 2018 FireHound
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.media.AudioManager;
import android.provider.Settings;
import android.provider.Settings.System;
import android.service.quicksettings.Tile;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSIconView;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.Prefs;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.SystemSetting;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;
import com.android.systemui.SysUIToast;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import javax.inject.Inject;

/** Quick settings tile: Gaming Mode tile **/
public class GamingModeTile extends QSTileImpl<BooleanState> {
    private static final int NOTIFICATION_ID = 10000;
    private static final String CHANNEL_ID = "gaming_mode";

    // saved settings state keys
    private static final String KEY_HEADSUP_STATE = "gaming_mode_state_headsup";
    private static final String KEY_ZEN_STATE = "gaming_mode_state_zen";
    // private static final String KEY_NAVBAR_STATE = "gaming_mode_state_navbar";
    // private static final String KEY_HW_KEYS_STATE = "gaming_mode_state_hw_keys";
    private static final String KEY_BRIGHTNESS_STATE = "gaming_mode_state_brightness";
    private static final String KEY_MEDIA_LEVEL = "gaming_mode_level_media";

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_gaming_mode);
    private final SystemSetting mSetting;
    private AudioManager mAudio;
    private NotificationManager mNm;
    private ContentResolver mResolver;
    private boolean mHasHWKeys;

    @Inject
    public GamingModeTile(QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            BroadcastDispatcher broadcastDispatcher,
            KeyguardStateController keyguardStateController
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mSetting = new SystemSetting(mContext, mHandler, System.ENABLE_GAMING_MODE) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
        mResolver = mContext.getContentResolver();
        mAudio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mNm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // find out if a physical navbar is present
        Configuration c = mContext.getResources().getConfiguration();
        mHasHWKeys = c.navigation != Configuration.NAVIGATION_NONAV;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick(@Nullable View view) {
        if (Prefs.getBoolean(mContext, Prefs.Key.QS_GAMING_MODE_DIALOG_SHOWN, false)) {
            enableGamingMode();
            return;
        }
        showGamingModeWhatsThisDialog();
    }

    private void showGamingModeWhatsThisDialog() {
        SystemUIDialog dialog = new SystemUIDialog(mContext);
        dialog.setTitle(R.string.gaming_mode_dialog_title);
        dialog.setMessage(R.string.gaming_mode_dialog_message);
        dialog.setPositiveButton(com.android.internal.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.putBoolean(mContext, Prefs.Key.QS_GAMING_MODE_DIALOG_SHOWN, true);
                    }
                });
        dialog.setShowForAllUsers(true);
        dialog.show();
    }

    public void enableGamingMode() {
        handleState(!mState.value);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleLongClick(@Nullable View view) {
        showGamingModeWhatsThisDialog();
    }

    private void handleState(boolean enabled) {
        if (enabled) {
            saveSettingsState();

            boolean headsUpEnabled = Settings.System.getInt(mResolver,
                    Settings.System.GAMING_MODE_HEADS_UP, 1) == 1;
            boolean zenEnabled = Settings.System.getInt(mResolver,
                    Settings.System.GAMING_MODE_ZEN, 0) == 1;
            boolean navBarEnabled = Settings.System.getInt(mResolver,
                    Settings.System.GAMING_MODE_NAVBAR, 0) == 1;
            boolean hwKeysEnabled = Settings.System.getInt(mResolver,
                    Settings.System.GAMING_MODE_HW_BUTTONS, 1) == 1;
            boolean brightnessEnabled = Settings.System.getInt(mResolver,
                    Settings.System.GAMING_MODE_BRIGHTNESS_ENABLED, 0) == 1;
            boolean mediaEnabled = Settings.System.getInt(mResolver,
                    Settings.System.GAMING_MODE_MEDIA_ENABLED, 0) == 1;

            if (headsUpEnabled) {
                Settings.Global.putInt(mResolver,
                        Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 0);
            }

            if (zenEnabled) {
                mNm.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            }

            // if (navBarEnabled) {
            //     Settings.System.putInt(mResolver,
            //             Settings.System.FORCE_SHOW_NAVBAR, 0);
            // }
            //
            // if (hwKeysEnabled && mHasHWKeys) {
            //     Settings.Secure.putInt(mResolver,
            //             Settings.Secure.HARDWARE_KEYS_DISABLE, 1);
            // }

            if (brightnessEnabled) {
                Settings.System.putInt(mResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            }

            if (mediaEnabled) {
                int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int level = Settings.System.getInt(mResolver,
                        Settings.System.GAMING_MODE_MEDIA, 80);
                level = Math.round((float)max * ((float)level / 100f));
                mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, level,
                        AudioManager.FLAG_SHOW_UI);
            }
        } else {
            restoreSettingsState();
        }
        setNotification(enabled);

        Settings.System.putInt(mResolver,
                Settings.System.ENABLE_GAMING_MODE, enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean enable = value != 0;
        if (state.slash == null) {
            state.slash = new SlashState();
        }
        state.icon = mIcon;
        state.value = enable;
        state.slash.isSlashed = !state.value;
        state.label = mContext.getString(R.string.gaming_mode_tile_title);
        if (enable) {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.gaming_mode_tile_title);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EVO_QS_TILES;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_gaming_mode_off);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // no-op
    }

    private void saveSettingsState() {
        Prefs.putInt(mContext, KEY_HEADSUP_STATE, Settings.Global.getInt(mResolver,
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1));
        Prefs.putInt(mContext, KEY_ZEN_STATE, Settings.Global.getInt(mResolver,
                Settings.Global.ZEN_MODE, 0) != 0 ? 1 : 0);
        // Prefs.putInt(mContext, KEY_NAVBAR_STATE, Settings.System.getInt(mResolver,
        //         Settings.System.FORCE_SHOW_NAVBAR, 1));
        // Prefs.putInt(mContext, KEY_HW_KEYS_STATE, Settings.Secure.getInt(mResolver,
        //         Settings.Secure.HARDWARE_KEYS_DISABLE, 0));
        Prefs.putInt(mContext, KEY_BRIGHTNESS_STATE, Settings.System.getInt(mResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
        // save current volume as percentage
        // we can restore it that way even if vol steps was changed in runtime
        int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curr = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
        Prefs.putInt(mContext, KEY_MEDIA_LEVEL,
                Math.round((float)curr * 100f / (float)max));
    }

    private void restoreSettingsState() {
        Settings.Global.putInt(mResolver,
                Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                Prefs.getInt(mContext, KEY_HEADSUP_STATE, 1));
        mNm.setInterruptionFilter(Prefs.getInt(mContext, KEY_ZEN_STATE, 0) == 1
                ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                : NotificationManager.INTERRUPTION_FILTER_ALL);
        // Settings.System.putInt(mResolver,
        //         Settings.System.FORCE_SHOW_NAVBAR,
        //         Prefs.getInt(mContext, KEY_NAVBAR_STATE, 1));
        // Settings.Secure.putInt(mResolver,
        //         Settings.Secure.HARDWARE_KEYS_DISABLE,
        //         Prefs.getInt(mContext, KEY_HW_KEYS_STATE, 0));
        Settings.System.putInt(mResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Prefs.getInt(mContext, KEY_BRIGHTNESS_STATE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
        int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int prevVol = Prefs.getInt(mContext, KEY_MEDIA_LEVEL, 0);
        mAudio.setStreamVolume(AudioManager.STREAM_MUSIC,
                Math.round((float)max * (float)prevVol / 100f),
                AudioManager.FLAG_SHOW_UI);
    }

    private void setNotification(boolean show) {
        if (show) {
            final Resources res = mContext.getResources();
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    res.getString(R.string.gaming_mode_tile_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(res.getString(R.string.accessibility_quick_settings_gaming_mode_on));
            channel.enableVibration(false);
            mNm.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_qs_gaming_mode)
                    .setContentTitle(res.getString(R.string.gaming_mode_tile_title))
                    .setContentText(res.getString(R.string.accessibility_quick_settings_gaming_mode_on))
                    .setShowWhen(true)
                    .setOngoing(true)
                    .build();
            mNm.notifyAsUser(null, NOTIFICATION_ID, notification, UserHandle.CURRENT);
        } else {
            mNm.cancelAsUser(null, NOTIFICATION_ID, UserHandle.CURRENT);
        }
    }
}
