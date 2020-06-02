/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.annotation.ColorInt;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.notification.ZenModeConfig;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.StatsLog;
import android.view.ContextThemeWrapper;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import com.android.settingslib.Utils;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.Dependency;
import com.android.systemui.DualToneHandler;
import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.privacy.OngoingPrivacyChip;
import com.android.systemui.privacy.PrivacyDialogBuilder;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyItemControllerKt;
import com.android.systemui.qs.QSDetail.Callback;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.TintedIconManager;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.DateView;
import com.android.systemui.statusbar.policy.NetworkTraffic;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.ZenModeController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * View that contains the top-most bits of the screen (primarily the status bar with date, time, and
 * battery) and also contains the {@link QuickQSPanel} along with some of the panel's inner
 * contents.
 */
public class QuickStatusBarHeader extends RelativeLayout implements
        View.OnClickListener, NextAlarmController.NextAlarmChangeCallback,
        ZenModeController.Callback {
    private static final String TAG = "QuickStatusBarHeader";
    private static final boolean DEBUG = false;

    /** Delay for auto fading out the long press tooltip after it's fully visible (in ms). */
    private static final long AUTO_FADE_OUT_DELAY_MS = DateUtils.SECOND_IN_MILLIS * 6;
    private static final int FADE_ANIMATION_DURATION_MS = 300;
    private static final int TOOLTIP_NOT_YET_SHOWN_COUNT = 0;
    public static final int MAX_TOOLTIP_SHOWN_COUNT = 2;

    private final Handler mHandler = new Handler();
    private final NextAlarmController mAlarmController;
    private final ZenModeController mZenController;
    private final StatusBarIconController mStatusBarIconController;
    private final ActivityStarter mActivityStarter;

    private QSPanel mQsPanel;

    private boolean mExpanded;
    private boolean mListening;
    private boolean mQsDisabled;
    private boolean isHideBattIcon;

    private QSCarrierGroup mCarrierGroup;
    protected QuickQSPanel mHeaderQsPanel;
    protected QSTileHost mHost;
    private TintedIconManager mIconManager;
    private TouchAnimator mStatusIconsAlphaAnimator;
    private TouchAnimator mHeaderTextContainerAlphaAnimator;
    private TouchAnimator mPrivacyChipAlphaAnimator;
    private DualToneHandler mDualToneHandler;

    private View mSystemIconsView;
    private View mQuickQsStatusIcons;
    private View mHeaderTextContainerView;

    private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private boolean mLandscape;
    private boolean mHeaderImageEnabled;
    private int mHeaderImageHeight;

    private ImageView mNextAlarmIcon;
    /** {@link TextView} containing the actual text indicating when the next alarm will go off. */
    private TextView mNextAlarmTextView;
    private View mNextAlarmContainer;
    private View mStatusSeparator;
    private ImageView mRingerModeIcon;
    private TextView mRingerModeTextView;
    private View mRingerContainer;
    private Clock mClockView;
    private DateView mDateView;
    private NetworkTraffic mTraffic;
    private OngoingPrivacyChip mPrivacyChip;
    private Space mSpace;
    private BatteryMeterView mBatteryMeterView;
    private boolean mPermissionsHubEnabled;

    private PrivacyItemController mPrivacyItemController;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_BATTERY_MODE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_BATTERY_STYLE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_HIDE_BATTERY), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.SHOW_QS_CLOCK), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.OMNI_STATUS_BAR_CUSTOM_HEADER), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT), false,
                    this, UserHandle.USER_ALL);
            }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private SettingsObserver mSettingsObserver = new SettingsObserver(mHandler);

    private final BroadcastReceiver mRingerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mRingerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1);
            updateStatusText();
        }
    };
    private boolean mHasTopCutout = false;
    private boolean mPrivacyChipLogged = false;

    private final DeviceConfig.OnPropertyChangedListener mPropertyListener =
            new DeviceConfig.OnPropertyChangedListener() {
                @Override
                public void onPropertyChanged(String namespace, String name, String value) {
                    if (DeviceConfig.NAMESPACE_PRIVACY.equals(namespace)
                            && SystemUiDeviceConfigFlags.PROPERTY_PERMISSIONS_HUB_ENABLED.equals(
                            name)) {
                        mPermissionsHubEnabled = Boolean.valueOf(value);
                        StatusIconContainer iconContainer = findViewById(R.id.statusIcons);
                        iconContainer.setIgnoredSlots(getIgnoredIconSlots());
                    }
                }
            };

    private PrivacyItemController.Callback mPICCallback = new PrivacyItemController.Callback() {
        @Override
        public void privacyChanged(List<PrivacyItem> privacyItems) {
            mPrivacyChip.setPrivacyList(privacyItems);
            setChipVisibility(!privacyItems.isEmpty());
        }
    };

    @Inject
    public QuickStatusBarHeader(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            NextAlarmController nextAlarmController, ZenModeController zenModeController,
            StatusBarIconController statusBarIconController,
            ActivityStarter activityStarter, PrivacyItemController privacyItemController) {
        super(context, attrs);
        mAlarmController = nextAlarmController;
        mZenController = zenModeController;
        mStatusBarIconController = statusBarIconController;
        mActivityStarter = activityStarter;
        mPrivacyItemController = privacyItemController;
        mDualToneHandler = new DualToneHandler(
                new ContextThemeWrapper(context, R.style.QSHeaderTheme));
        mSettingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);
        mSystemIconsView = findViewById(R.id.quick_status_bar_system_icons);
        mQuickQsStatusIcons = findViewById(R.id.quick_qs_status_icons);
        StatusIconContainer iconContainer = findViewById(R.id.statusIcons);
        // Ignore privacy icons because they show in the space above QQS
        iconContainer.addIgnoredSlots(getIgnoredIconSlots());
        iconContainer.setShouldRestrictIcons(false);
        mIconManager = new TintedIconManager(iconContainer);

        mBatteryMeterView = findViewById(R.id.battery);
        mBatteryMeterView.setForceShowPercent(true);
        mBatteryMeterView.setIgnoreTunerUpdates(true);
        mBatteryMeterView.setOnClickListener(this);
        mBatteryMeterView.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);

        // Views corresponding to the header info section (e.g. ringer and next alarm).
        mHeaderTextContainerView = findViewById(R.id.header_text_container);
        mStatusSeparator = findViewById(R.id.status_separator);
        mNextAlarmIcon = findViewById(R.id.next_alarm_icon);
        mNextAlarmTextView = findViewById(R.id.next_alarm_text);
        mNextAlarmContainer = findViewById(R.id.alarm_container);
        mNextAlarmContainer.setOnClickListener(this::onClick);
        mRingerModeIcon = findViewById(R.id.ringer_mode_icon);
        mRingerModeTextView = findViewById(R.id.ringer_mode_text);
        mRingerContainer = findViewById(R.id.ringer_container);
        mRingerContainer.setOnClickListener(this::onClick);
        mPrivacyChip = findViewById(R.id.privacy_chip);
        mPrivacyChip.setOnClickListener(this::onClick);
        mCarrierGroup = findViewById(R.id.carrier_group);


        updateResources();

        Rect tintArea = new Rect(0, 0, 0, 0);
        int colorForeground = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorForeground);
        float intensity = getColorIntensity(colorForeground);
        int fillColor = mDualToneHandler.getSingleColor(intensity);

        // Set light text on the header icons because they will always be on a black background
        applyDarkness(R.id.clock, tintArea, 0, DarkIconDispatcher.DEFAULT_ICON_TINT);

        // Set the correct tint for the status icons so they contrast
        mIconManager.setTint(fillColor);
        mNextAlarmIcon.setImageTintList(ColorStateList.valueOf(fillColor));
        mRingerModeIcon.setImageTintList(ColorStateList.valueOf(fillColor));

        mClockView = findViewById(R.id.clock);
        mClockView.setOnClickListener(this);
        mClockView.setQsHeader();
        mDateView = findViewById(R.id.date);
        mDateView.setOnClickListener(this);
        mSpace = findViewById(R.id.space);
        mDateView.setOnClickListener(this);
        mTraffic = findViewById(R.id.networkTraffic);

        mRingerModeTextView.setSelected(true);
        mNextAlarmTextView.setSelected(true);
        updateSettings();

        mPermissionsHubEnabled = PrivacyItemControllerKt.isPermissionsHubEnabled();
        // Change the ignored slots when DeviceConfig flag changes
        DeviceConfig.addOnPropertyChangedListener(DeviceConfig.NAMESPACE_PRIVACY,
                mContext.getMainExecutor(), mPropertyListener);

    }

    private List<String> getIgnoredIconSlots() {
        ArrayList<String> ignored = new ArrayList<>();
        ignored.add(mContext.getResources().getString(
                com.android.internal.R.string.status_bar_camera));
        ignored.add(mContext.getResources().getString(
                com.android.internal.R.string.status_bar_microphone));
        if (mPermissionsHubEnabled) {
            ignored.add(mContext.getResources().getString(
                    com.android.internal.R.string.status_bar_location));
        }

        return ignored;
    }

    private void updateStatusText() {
        boolean changed = updateRingerStatus() || updateAlarmStatus();

        if (changed) {
            boolean alarmVisible = mNextAlarmTextView.getVisibility() == View.VISIBLE;
            boolean ringerVisible = mRingerModeTextView.getVisibility() == View.VISIBLE;
            mStatusSeparator.setVisibility(alarmVisible && ringerVisible ? View.VISIBLE
                    : View.GONE);
        }
    }

    private void setChipVisibility(boolean chipVisible) {
        if (chipVisible && mPermissionsHubEnabled) {
            mPrivacyChip.setVisibility(View.VISIBLE);
            // Makes sure that the chip is logged as viewed at most once each time QS is opened
            // mListening makes sure that the callback didn't return after the user closed QS
            if (!mPrivacyChipLogged && mListening) {
                mPrivacyChipLogged = true;
                StatsLog.write(StatsLog.PRIVACY_INDICATORS_INTERACTED,
                        StatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__CHIP_VIEWED);
            }
        } else {
            mPrivacyChip.setVisibility(View.GONE);
        }
    }

    private boolean updateRingerStatus() {
        boolean isOriginalVisible = mRingerModeTextView.getVisibility() == View.VISIBLE;
        CharSequence originalRingerText = mRingerModeTextView.getText();

        boolean ringerVisible = false;
        if (!ZenModeConfig.isZenOverridingRinger(mZenController.getZen(),
                mZenController.getConsolidatedPolicy())) {
            if (mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
                mRingerModeIcon.setImageResource(R.drawable.ic_volume_ringer_vibrate);
                mRingerModeTextView.setText(R.string.qs_status_phone_vibrate);
                ringerVisible = true;
            } else if (mRingerMode == AudioManager.RINGER_MODE_SILENT) {
                mRingerModeIcon.setImageResource(R.drawable.ic_volume_ringer_mute);
                mRingerModeTextView.setText(R.string.qs_status_phone_muted);
                ringerVisible = true;
            }
        }
        mRingerModeIcon.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);
        mRingerModeTextView.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);
        mRingerContainer.setVisibility(ringerVisible ? View.VISIBLE : View.GONE);

        return isOriginalVisible != ringerVisible ||
                !Objects.equals(originalRingerText, mRingerModeTextView.getText());
    }

    private boolean updateAlarmStatus() {
        boolean isOriginalVisible = mNextAlarmTextView.getVisibility() == View.VISIBLE;
        CharSequence originalAlarmText = mNextAlarmTextView.getText();

        boolean alarmVisible = false;
        if (mNextAlarm != null) {
            alarmVisible = true;
            mNextAlarmTextView.setText(formatNextAlarm(mNextAlarm));
        }
        mNextAlarmIcon.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);
        mNextAlarmTextView.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);
        mNextAlarmContainer.setVisibility(alarmVisible ? View.VISIBLE : View.GONE);

        return isOriginalVisible != alarmVisible ||
                !Objects.equals(originalAlarmText, mNextAlarmTextView.getText());
    }

    private void applyDarkness(int id, Rect tintArea, float intensity, int color) {
        View v = findViewById(id);
        if (v instanceof DarkReceiver) {
            ((DarkReceiver) v).onDarkChanged(tintArea, intensity, color);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        mTraffic.useWallpaperTextColor(mLandscape);
        updateResources();

        // Update color schemes in landscape to use wallpaperTextColor
        boolean shouldUseWallpaperTextColor =
                newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        mClockView.useWallpaperTextColor(shouldUseWallpaperTextColor);
        updateStatusbarProperties();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateResources();
    }

    /**
     * The height of QQS should always be the status bar height + 128dp. This is normally easy, but
     * when there is a notch involved the status bar can remain a fixed pixel size.
     */
    private void updateMinimumHeight() {
        int sbHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_height);
        int qqsHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.qs_quick_header_panel_height);
        setMinimumHeight(sbHeight + qqsHeight);
    }

    private void updateResources() {
        Resources resources = mContext.getResources();
        updateMinimumHeight();

        // Update height for a few views, especially due to landscape mode restricting space.
        /*mHeaderTextContainerView.getLayoutParams().height =
                resources.getDimensionPixelSize(R.dimen.qs_header_tooltip_height);
        mHeaderTextContainerView.setLayoutParams(mHeaderTextContainerView.getLayoutParams());*/

        int topMargin = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height) + (mHeaderImageEnabled ?
                mHeaderImageHeight : 0);

        mSystemIconsView.getLayoutParams().height = topMargin;
        mSystemIconsView.setLayoutParams(mSystemIconsView.getLayoutParams());

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (mQsDisabled) {
            lp.height = topMargin;
        } else {
            int qsHeight = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.quick_qs_total_height);

            if (mHeaderImageEnabled) {
                qsHeight += mHeaderImageHeight;
            }

            lp.height = Math.max(getMinimumHeight(), qsHeight);
        }

        setLayoutParams(lp);

        updateStatusIconAlphaAnimator();
        updateHeaderTextContainerAlphaAnimator();
        updatePrivacyChipAlphaAnimator();
    }

    private void updateQSBatteryMode() {
        int showEstimate = Settings.System.getInt(mContext.getContentResolver(),
        Settings.System.QS_BATTERY_MODE, 0);
        if (showEstimate == 0) {
            mBatteryMeterView.setShowPercent(0);
            mBatteryMeterView.setPercentShowMode(BatteryMeterView.MODE_OFF);
        } else if (showEstimate == 1) {
            mBatteryMeterView.setShowPercent(0);
            mBatteryMeterView.setPercentShowMode(BatteryMeterView.MODE_ON);
        } else if (showEstimate == 2) {
            mBatteryMeterView.setShowPercent(1);
            mBatteryMeterView.setPercentShowMode(BatteryMeterView.MODE_OFF);
        } else if (showEstimate == 3 || showEstimate == 4) {
            mBatteryMeterView.setShowPercent(0);
            mBatteryMeterView.setPercentShowMode(BatteryMeterView.MODE_ESTIMATE);
        }
        mBatteryMeterView.updatePercentView();
        mBatteryMeterView.updateVisibility();
        mBatteryMeterView.setVisibility(isHideBattIcon ? View.GONE : View.VISIBLE);
    }

    private void updateSBBatteryStyle() {
        mBatteryMeterView.setBatteryStyle(Settings.System.getInt(mContext.getContentResolver(),
        Settings.System.STATUS_BAR_BATTERY_STYLE, 0));
        mBatteryMeterView.updateBatteryStyle();
        mBatteryMeterView.updatePercentView();
        mBatteryMeterView.updateVisibility();
    }

    private void updateQSClock() {
        int show = Settings.System.getInt(mContext.getContentResolver(),
        Settings.System.SHOW_QS_CLOCK, 1);
        mClockView.setClockVisibleByUser(show == 1);
    }

    private void updateStatusIconAlphaAnimator() {
        mStatusIconsAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mQuickQsStatusIcons, "alpha", 1, 0, 0)
                .build();
    }

    private void updateHeaderTextContainerAlphaAnimator() {
        mHeaderTextContainerAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mHeaderTextContainerView, "alpha", 0, 0, 1)
                .build();
    }

    private void updatePrivacyChipAlphaAnimator() {
        mPrivacyChipAlphaAnimator = new TouchAnimator.Builder()
                .addFloat(mPrivacyChip, "alpha", 1, 0, 1)
                .build();
    }

    public void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        mHeaderQsPanel.setExpanded(expanded);
        updateEverything();
    }

    /**
     * Animates the inner contents based on the given expansion details.
     *
     * @param forceExpanded whether we should show the state expanded forcibly
     * @param expansionFraction how much the QS panel is expanded/pulled out (up to 1f)
     * @param panelTranslationY how much the panel has physically moved down vertically (required
     *                          for keyguard animations only)
     */
    public void setExpansion(boolean forceExpanded, float expansionFraction,
                             float panelTranslationY) {
        final float keyguardExpansionFraction = forceExpanded ? 1f : expansionFraction;
        if (mStatusIconsAlphaAnimator != null) {
            mStatusIconsAlphaAnimator.setPosition(keyguardExpansionFraction);
        }

        if (forceExpanded) {
            // If the keyguard is showing, we want to offset the text so that it comes in at the
            // same time as the panel as it slides down.
            mHeaderTextContainerView.setTranslationY(panelTranslationY);
        } else {
            mHeaderTextContainerView.setTranslationY(0f);
        }

        if (mHeaderTextContainerAlphaAnimator != null) {
            mHeaderTextContainerAlphaAnimator.setPosition(keyguardExpansionFraction);
            if (keyguardExpansionFraction > 0) {
                mHeaderTextContainerView.setVisibility(VISIBLE);
            } else {
                mHeaderTextContainerView.setVisibility(INVISIBLE);
            }
        }
        if (mPrivacyChipAlphaAnimator != null) {
            mPrivacyChip.setExpanded(expansionFraction > 0.5);
            mPrivacyChipAlphaAnimator.setPosition(keyguardExpansionFraction);
        }
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        mHeaderTextContainerView.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        mQuickQsStatusIcons.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
        updateResources();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mStatusBarIconController.addIconGroup(mIconManager);
        requestApplyInsets();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        DisplayCutout cutout = insets.getDisplayCutout();
        Pair<Integer, Integer> padding = PhoneStatusBarView.cornerCutoutMargins(
                cutout, getDisplay());
        if (padding == null) {
            mSystemIconsView.setPaddingRelative(
                    getResources().getDimensionPixelSize(R.dimen.status_bar_padding_start),
                    getResources().getDimensionPixelSize(R.dimen.status_bar_padding_top),
                    getResources().getDimensionPixelSize(R.dimen.status_bar_padding_end),
                    0);
        } else {
            mSystemIconsView.setPadding(
                    padding.first,
                    getResources().getDimensionPixelSize(R.dimen.status_bar_padding_top),
                    padding.second, 0);

        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mSpace.getLayoutParams();
        if (cutout != null) {
            Rect topCutout = cutout.getBoundingRectTop();
            if (topCutout.isEmpty()) {
                mHasTopCutout = false;
                lp.width = 0;
                mSpace.setVisibility(View.GONE);
            } else {
                mHasTopCutout = true;
                lp.width = topCutout.width();
                mSpace.setVisibility(View.VISIBLE);
            }
        }
        mSpace.setLayoutParams(lp);
        setChipVisibility(mPrivacyChip.getVisibility() == View.VISIBLE);
        return super.onApplyWindowInsets(insets);
    }

    @Override
    @VisibleForTesting
    public void onDetachedFromWindow() {
        setListening(false);
        mStatusBarIconController.removeIconGroup(mIconManager);
        super.onDetachedFromWindow();
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mHeaderQsPanel.setListening(listening);
        mListening = listening;
        mCarrierGroup.setListening(mListening);

        if (listening) {
            mZenController.addCallback(this);
            mAlarmController.addCallback(this);
            mContext.registerReceiver(mRingerReceiver,
                    new IntentFilter(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION));
            mPrivacyItemController.addCallback(mPICCallback);
        } else {
            mZenController.removeCallback(this);
            mAlarmController.removeCallback(this);
            mPrivacyItemController.removeCallback(mPICCallback);
            mContext.unregisterReceiver(mRingerReceiver);
            mPrivacyChipLogged = false;
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mClockView || v == mNextAlarmTextView) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    AlarmClock.ACTION_SHOW_ALARMS), 0);
        } else if (v == mNextAlarmContainer && mNextAlarmContainer.isVisibleToUser()) {
            if (mNextAlarm.getShowIntent() != null) {
                mActivityStarter.postStartActivityDismissingKeyguard(
                        mNextAlarm.getShowIntent());
            } else {
                Log.d(TAG, "No PendingIntent for next alarm. Using default intent");
                mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                        AlarmClock.ACTION_SHOW_ALARMS), 0);
            }
        } else if (v == mPrivacyChip) {
            // Makes sure that the builder is grabbed as soon as the chip is pressed
            PrivacyDialogBuilder builder = mPrivacyChip.getBuilder();
            if (builder.getAppsAndTypes().size() == 0) return;
            Handler mUiHandler = new Handler(Looper.getMainLooper());
            StatsLog.write(StatsLog.PRIVACY_INDICATORS_INTERACTED,
                    StatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__CHIP_CLICKED);
            mUiHandler.post(() -> {
                mActivityStarter.postStartActivityDismissingKeyguard(
                        new Intent(Intent.ACTION_REVIEW_ONGOING_PERMISSION_USAGE), 0);
                mHost.collapsePanels();
            });
        } else if (v == mRingerContainer && mRingerContainer.isVisibleToUser()) {
            mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                    Settings.ACTION_SOUND_SETTINGS), 0);
        } else if (v == mBatteryMeterView) {
            Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(new Intent(
                    Intent.ACTION_POWER_USAGE_SUMMARY),0);
        } else if (v == mDateView) {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(Long.toString(System.currentTimeMillis()));
            Intent todayIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            Dependency.get(ActivityStarter.class).postStartActivityDismissingKeyguard(todayIntent, 0);
        }
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        updateStatusText();
    }

    @Override
    public void onZenChanged(int zen) {
        updateStatusText();
    }

    @Override
    public void onConfigChanged(ZenModeConfig config) {
        updateStatusText();
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    public void setQSPanel(final QSPanel qsPanel) {
        mQsPanel = qsPanel;
        setupHost(qsPanel.getHost());
    }

    public void setupHost(final QSTileHost host) {
        mHost = host;
        //host.setHeaderView(mExpandIndicator);
        mHeaderQsPanel.setQSPanelAndHeader(mQsPanel, this);
        mHeaderQsPanel.setHost(host, null /* No customization in header */);


        Rect tintArea = new Rect(0, 0, 0, 0);
        int colorForeground = Utils.getColorAttrDefaultColor(getContext(),
                android.R.attr.colorForeground);
        float intensity = getColorIntensity(colorForeground);
        int fillColor = mDualToneHandler.getSingleColor(intensity);

        // Use SystemUI context to get battery meter colors, and let it use the default tint (white)
        mBatteryMeterView.setColorsFromContext(mHost.getContext());
        mBatteryMeterView.onDarkChanged(new Rect(), 0, DarkIconDispatcher.DEFAULT_ICON_TINT);
    }

    public void setCallback(Callback qsPanelCallback) {
        mHeaderQsPanel.setCallback(qsPanelCallback);
    }

    private String formatNextAlarm(AlarmManager.AlarmClockInfo info) {
        if (info == null) {
            return "";
        }
        String skeleton = android.text.format.DateFormat
                .is24HourFormat(mContext, ActivityManager.getCurrentUser()) ? "EHm" : "Ehma";
        String pattern = android.text.format.DateFormat
                .getBestDateTimePattern(Locale.getDefault(), skeleton);
        return android.text.format.DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    public static float getColorIntensity(@ColorInt int color) {
        return color == Color.WHITE ? 0 : 1;
    }

    public void setMargins(int sideMargins) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            // Prevents these views from getting set a margin.
            // The Icon views all have the same padding set in XML to be aligned.
            if (v == mSystemIconsView || v == mQuickQsStatusIcons || v == mHeaderQsPanel
                    || v == mHeaderTextContainerView) {
                continue;
            }
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
            lp.leftMargin = sideMargins;
            lp.rightMargin = sideMargins;
        }
    }

    private void updateSettings() {
        isHideBattIcon = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_HIDE_BATTERY, 0,
                UserHandle.USER_CURRENT) == 1;
        updateHeaderImage();
        updateQSBatteryMode();
        updateSBBatteryStyle();
        updateQSClock();
        updateResources();
        updateStatusbarProperties();
    }

    // Update color schemes in landscape to use wallpaperTextColor
    private void updateStatusbarProperties() {
        boolean shouldUseWallpaperTextColor = mLandscape && !mHeaderImageEnabled;
        mClockView.useWallpaperTextColor(shouldUseWallpaperTextColor);
    }

    private void updateHeaderImage() {
        mHeaderImageEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.OMNI_STATUS_BAR_CUSTOM_HEADER, 0,
                UserHandle.USER_CURRENT) == 1;
        int mImageHeight = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_HEIGHT, 25,
                UserHandle.USER_CURRENT);
        switch (mImageHeight) {
            case 0:
                mHeaderImageHeight = 0;
                break;
            case 1:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_1);
                break;
            case 2:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_2);
                break;
            case 3:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_3);
                break;
            case 4:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_4);
                break;
            case 5:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_5);
                break;
            case 6:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_6);
                break;
            case 7:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_7);
                break;
            case 8:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_8);
                break;
            case 9:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9);
                break;
            case 10:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10);
                break;
            case 11:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11);
                break;
            case 12:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12);
                break;
            case 13:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13);
                break;
            case 14:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14);
                break;
            case 15:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15);
                break;
            case 16:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16);
                break;
            case 17:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17);
                break;
            case 18:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18);
                break;
            case 19:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19);
                break;
            case 20:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20);
                break;
            case 21:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21);
                break;
            case 22:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22);
                break;
            case 23:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23);
                break;
            case 24:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24);
                break;
            case 25:
            default:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25);
                break;
            case 26:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26);
                break;
            case 27:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27);
                break;
            case 28:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28);
                break;
            case 29:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29);
                break;
            case 30:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30);
                break;
            case 31:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31);
                break;
            case 32:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32);
                break;
            case 33:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33);
                break;
            case 34:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_34);
                break;
            case 35:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_35);
                break;
            case 36:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_36);
                break;
            case 37:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_37);
                break;
            case 38:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_38);
                break;
            case 39:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_39);
                break;
            case 40:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_40);
                break;
            case 41:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_41);
                break;
            case 42:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_42);
                break;
            case 43:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_43);
                break;
            case 44:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_44);
                break;
            case 45:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_45);
                break;
            case 46:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_46);
                break;
            case 47:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_47);
                break;
            case 48:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_48);
                break;
            case 49:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_49);
                break;
            case 50:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_50);
                break;
            case 51:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51);
                break;
            case 52:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52);
                break;
            case 53:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53);
                break;
            case 54:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54);
                break;
            case 55:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55);
                break;
            case 56:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56);
                break;
            case 57:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57);
                break;
            case 58:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58);
                break;
            case 59:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59);
                break;
            case 60:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60);
                break;
            case 61:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61);
                break;
            case 62:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62);
                break;
            case 63:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63);
                break;
            case 64:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64);
                break;
            case 65:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65);
                break;
            case 66:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66);
                break;
            case 67:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67);
                break;
            case 68:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68);
                break;
            case 69:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69);
                break;
            case 70:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70);
                break;
            case 71:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71);
                break;
            case 72:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72);
                break;
            case 73:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73);
                break;
            case 74:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74);
                break;
            case 75:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75);
                break;
            case 76:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76);
                break;
            case 77:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77);
                break;
            case 78:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78);
                break;
            case 79:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79);
                break;
            case 80:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80);
                break;
            case 81:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81);
                break;
            case 82:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82);
                break;
            case 83:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83);
                break;
            case 84:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84);
                break;
            case 85:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85);
                break;
            case 86:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86);
                break;
            case 87:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87);
                break;
            case 88:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88);
                break;
            case 89:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89);
                break;
            case 90:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90);
                break;
            case 91:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91);
                break;
            case 92:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92);
                break;
            case 93:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93);
                break;
            case 94:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94);
                break;
            case 95:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95);
                break;
            case 96:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96);
                break;
            case 97:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97);
                break;
            case 98:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98);
                break;
            case 99:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99);
                break;
            case 100:
                mHeaderImageHeight = mContext.getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100);
                break;
        }
    }
}
