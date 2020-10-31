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

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
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
import com.android.systemui.plugins.qs.DetailAdapter;
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

    // saved settings state keys
    private static final String KEY_HEADSUP_STATE = "gaming_mode_state_headsup";
    private static final String KEY_ZEN_STATE = "gaming_mode_state_zen";
    // private static final String KEY_NAVBAR_STATE = "gaming_mode_state_navbar";
    // private static final String KEY_HW_KEYS_STATE = "gaming_mode_state_hw_keys";
    private static final String KEY_BRIGHTNESS_STATE = "gaming_mode_state_brightness";
    private static final String KEY_MEDIA_LEVEL = "gaming_mode_level_media";

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_gaming_mode);
    private final SystemSetting mSetting;
    private final GamingModeTileDetailAdapter mDetailAdapter;
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
        mDetailAdapter = (GamingModeTileDetailAdapter) createDetailAdapter();
        mAudio = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mNm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // find out if a physical navbar is present
        Configuration c = mContext.getResources().getConfiguration();
        mHasHWKeys = c.navigation != Configuration.NAVIGATION_NONAV;
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected DetailAdapter createDetailAdapter() {
        return new GamingModeTileDetailAdapter();
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
                        enableGamingMode();
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
        showDetail(true);
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

    private class GamingModeTileDetailAdapter implements DetailAdapter {
        // private LinearLayout mHWButtonsLayout;
        private SeekBar mMediaSeekBar;
        private Switch mHeadsUpSwitch;
        private Switch mZenSwitch;
        // private Switch mNavBarSwitch;
        private Switch mHWSwitch;
        private Switch mBrightnessSwitch;
        private Switch mMediaSwitch;
        private TextView mMediaLabel;

        private final SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mMediaLabel.setText(String.valueOf(progress) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Settings.System.putInt(mResolver,
                        Settings.System.GAMING_MODE_MEDIA,
                        seekBar.getProgress());
            }
        };

        @Override
        public int getMetricsCategory() {
            return MetricsEvent.YASP;
        }

        @Override
        public CharSequence getTitle() {
            return mContext.getString(R.string.gaming_mode_dialog_title);
        }

        @Override
        public Boolean getToggleState() {
            return mState.value;
        }

        @Override
        public Intent getSettingsIntent() {
            return null;
        }

        @Override
        public void setToggleState(boolean state) {
            Settings.System.putInt(mResolver, System.ENABLE_GAMING_MODE, state ? 1 : 0);
            refreshState();
            if (!state) {
                showDetail(false);
            }
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final View view = convertView != null ? convertView : LayoutInflater.from(context).inflate(
                            R.layout.gaming_mode_tile_panel, parent, false);

            if (convertView == null) {
                mMediaLabel = (TextView) view.findViewById(R.id.media_percent);
                mMediaSeekBar = (SeekBar) view.findViewById(R.id.media_seekbar);
                int value = Settings.System.getInt(mResolver,
                        Settings.System.GAMING_MODE_MEDIA, 80);
                mMediaSeekBar.setProgress(value);
                mMediaLabel.setText(String.valueOf(value) + "%");
                mMediaSeekBar.setOnSeekBarChangeListener(mSeekBarListener);

                mMediaSwitch = (Switch) view.findViewById(R.id.media_switch);
                boolean enabled = Settings.System.getInt(mResolver,
                        Settings.System.GAMING_MODE_MEDIA_ENABLED, 0) == 1;
                mMediaSwitch.setChecked(enabled);
                mMediaSeekBar.setEnabled(enabled);
                mMediaSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Settings.System.putInt(mResolver,
                                Settings.System.GAMING_MODE_MEDIA_ENABLED,
                                isChecked ? 1 : 0);
                        mMediaSeekBar.setEnabled(isChecked);
                    }
                });

                mBrightnessSwitch = (Switch) view.findViewById(R.id.brightness_switch);
                enabled = Settings.System.getInt(mResolver,
                        Settings.System.GAMING_MODE_BRIGHTNESS_ENABLED, 0) == 1;
                mBrightnessSwitch.setChecked(enabled);
                mBrightnessSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Settings.System.putInt(mResolver,
                                Settings.System.GAMING_MODE_BRIGHTNESS_ENABLED,
                                isChecked ? 1 : 0);
                    }
                });

                mHeadsUpSwitch = (Switch) view.findViewById(R.id.heads_up_switch);
                enabled = Settings.System.getInt(mResolver,
                        Settings.System.GAMING_MODE_HEADS_UP, 1) == 1;
                mHeadsUpSwitch.setChecked(enabled);
                mHeadsUpSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Settings.System.putInt(mResolver,
                                Settings.System.GAMING_MODE_HEADS_UP,
                                isChecked ? 1 : 0);
                    }
                });

                mZenSwitch = (Switch) view.findViewById(R.id.zen_switch);
                enabled = Settings.System.getInt(mResolver,
                        Settings.System.GAMING_MODE_ZEN, 0) == 1;
                mZenSwitch.setChecked(enabled);
                mZenSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Settings.System.putInt(mResolver,
                                Settings.System.GAMING_MODE_ZEN,
                                isChecked ? 1 : 0);
                    }
                });

                // mNavBarSwitch = (Switch) view.findViewById(R.id.navbar_switch);
                // enabled = Settings.System.getInt(mResolver,
                //         Settings.System.GAMING_MODE_NAVBAR, 0) == 1;
                // mNavBarSwitch.setChecked(enabled);
                // mNavBarSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                //     public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //         Settings.System.putInt(mResolver,
                //                 Settings.System.GAMING_MODE_NAVBAR,
                //                 isChecked ? 1 : 0);
                //     }
                // });
                //
                // if (mHasHWKeys) {
                //     mHWButtonsLayout = (LinearLayout) view.findViewById(R.id.hw_buttons_layout);
                //     mHWButtonsLayout.setVisibility(View.VISIBLE);
                //
                //     mHWSwitch = (Switch) view.findViewById(R.id.hw_buttons_switch);
                //     mHWSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                //         public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //             Settings.System.putInt(mResolver,
                //                     Settings.System.GAMING_MODE_HW_BUTTONS,
                //                     isChecked ? 1 : 0);
                //         }
                //     });
                // }
            }
            return view;
        }
    }
}
