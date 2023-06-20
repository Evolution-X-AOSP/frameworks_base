/*
 * Copyright (C) 2021 Havoc-OS
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
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.SettingObserver;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.settings.SecureSettings;

import java.time.format.DateTimeFormatter;
import java.time.LocalTime;

import javax.inject.Inject;

public class SleepModeTile extends SecureQSTile<QSTile.BooleanState> {

    public static final String TILE_SPEC = "sleep_mode";

    private static final ComponentName SLEEP_MODE_SETTING_COMPONENT = new ComponentName(
            "com.android.settings", "com.android.settings.Settings$SleepModeActivity");

    private static final Intent SLEEP_MODE_SETTINGS =
            new Intent().setComponent(SLEEP_MODE_SETTING_COMPONENT);

    private final Icon mIcon = ResourceIcon.get(com.android.internal.R.drawable.ic_sleep);

    private final SettingObserver mSetting;

    private boolean mIsTurningOn = false;

    @Inject
    public SleepModeTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            SecureSettings secureSettings,
            KeyguardStateController keyguardStateController,
            UserTracker userTracker
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger, keyguardStateController);

        mSetting = new SettingObserver(secureSettings, mHandler, Settings.Secure.SLEEP_MODE_ENABLED,
                userTracker.getUserId()) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
        SettingsObserver settingsObserver = new SettingsObserver(mainHandler);
        settingsObserver.observe();
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable View view, boolean keyguardShowing) {
        if (checkKeyguard(view, keyguardShowing)) {
            return;
        }
        if (mIsTurningOn) {
            return;
        }
        mIsTurningOn = true;
        setEnabled(!mState.value);
        refreshState();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mIsTurningOn = false;
            }
        }, 1500);
    }

    private void setEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.SLEEP_MODE_ENABLED, enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean sleep = value != 0;
        final int mode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SLEEP_MODE_AUTO_MODE, 0, UserHandle.USER_CURRENT);
        final boolean sleepModeOn = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SLEEP_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;

        String timeValue = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.SLEEP_MODE_AUTO_TIME, UserHandle.USER_CURRENT);
        if (timeValue == null || timeValue.equals("")) timeValue = "20:00,07:00";
        String[] time = timeValue.split(",", 0);
        String outputFormat = DateFormat.is24HourFormat(mContext) ? "HH:mm" : "h:mm a";
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputFormat);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime sinceValue = LocalTime.parse(time[0], formatter);
        LocalTime tillValue = LocalTime.parse(time[1], formatter);

        state.value = sleep;
        state.label = mContext.getString(R.string.quick_settings_sleep_mode_label);
        state.icon = mIcon;
        state.contentDescription = TextUtils.isEmpty(state.secondaryLabel)
                ? state.label
                : TextUtils.concat(state.label, ", ", state.secondaryLabel);
        switch (mode) {
            default:
            case 0:
                state.secondaryLabel = null;
                break;
            case 1:
                state.secondaryLabel = mContext.getResources().getString(sleepModeOn
                    ? R.string.quick_settings_night_secondary_label_until_sunrise
                    : R.string.quick_settings_night_secondary_label_on_at_sunset);
                break;
            case 2:
                if (sleepModeOn) {
                    state.secondaryLabel = mContext.getResources().getString(
                            R.string.quick_settings_secondary_label_until, tillValue.format(outputFormatter));
                } else {
                    state.secondaryLabel = mContext.getResources().getString(
                            R.string.quick_settings_night_secondary_label_on_at, sinceValue.format(outputFormatter));
                }
                break;
            case 3:
                if (sleepModeOn) {
                    state.secondaryLabel = mContext.getResources().getString(
                            R.string.quick_settings_secondary_label_until, tillValue.format(outputFormatter));
                } else {
                    state.secondaryLabel = mContext.getResources().getString(
                            R.string.quick_settings_night_secondary_label_on_at_sunset);
                }
                break;
            case 4:
                if (sleepModeOn) {
                    state.secondaryLabel = mContext.getResources().getString(
                            R.string.quick_settings_night_secondary_label_until_sunrise);
                } else {
                    state.secondaryLabel = mContext.getResources().getString(
                            R.string.quick_settings_night_secondary_label_on_at, sinceValue.format(outputFormatter));
                }
                break;
        }
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EVO_QS_TILES;
    }

    @Override
    public Intent getLongClickIntent() {
        return SLEEP_MODE_SETTINGS;
    }

    @Override
    protected void handleSetListening(boolean listening) {
    }

    @Override
    public CharSequence getTileLabel() {
        return getState().label;
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SLEEP_MODE_ENABLED), false, this,
                    UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SLEEP_MODE_AUTO_MODE), false, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }
    }
}
