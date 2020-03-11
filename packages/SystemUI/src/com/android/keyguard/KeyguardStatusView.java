/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.clock.CustomTextClock;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.TimeZone;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;

    private LinearLayout mStatusViewContainer;
    private TextView mLogoutView;
    private KeyguardClockSwitch mClockView;
    private CustomTextClock mTextClock;
    private TextView mOwnerInfo;
    private TextClock mDefaultClockView;
    private KeyguardSliceView mKeyguardSlice;
    private View mNotificationIcons;
    private View mKeyguardSliceView;
    private View mSmallClockView;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    private boolean mPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;

    /**
     * Bottom margin that defines the margin between bottom of smart space and top of notification
     * icons on AOD.
     */
    private int mIconTopMargin;
    private int mIconTopMarginWithHeader;
    private boolean mShowingHeader;

    private int mClockSelection;
    private int mDateSelection;
    private int mTextClockAlignment;

    // Date styles paddings
    private int mDateVerPadding;
    private int mDateHorPadding;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
        }

        @Override
        public void onTimeZoneChanged(TimeZone timeZone) {
            updateTimeZone(timeZone);
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
                mClockView.refreshLockFont();
                refreshLockDateFont();
                mClockView.refreshclocksize();
                mKeyguardSlice.refreshdatesize();
                refreshOwnerInfoSize();
                refreshOwnerInfoFont();
            }
        }

        @Override
        public void onStartedWakingUp() {
            setEnableMarquee(true);
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refreshFormat();
            updateOwnerInfo();
            updateLogoutView();
            mClockView.refreshLockFont();
            refreshLockDateFont();
            mClockView.refreshclocksize();
            mKeyguardSlice.refreshdatesize();
            updateDateStyles();
            refreshOwnerInfoSize();
            refreshOwnerInfoFont();
        }

        @Override
        public void onLogoutEnabledChanged() {
            updateLogoutView();
        }
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIActivityManager = ActivityManager.getService();
        mLockPatternUtils = new LockPatternUtils(getContext());
        mHandler = new Handler(Looper.myLooper());
        onDensityOrFontScaleChanged();
    }

    /**
     * If we're presenting a custom clock of just the default one.
     */
    public boolean hasCustomClock() {
        return mClockView.hasCustomClock();
    }

    public boolean hasCustomClockInBigContainer() {
        return mClockView.hasCustomClockInBigContainer();
    }

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        mClockView.setHasVisibleNotifications(hasVisibleNotifications);
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, "Schedule setEnableMarquee: " + (enabled ? "Enable" : "Disable"));
        if (enabled) {
            if (mPendingMarqueeStart == null) {
                mPendingMarqueeStart = () -> {
                    setEnableMarqueeImpl(true);
                    mPendingMarqueeStart = null;
                };
                mHandler.postDelayed(mPendingMarqueeStart, MARQUEE_DELAY_MS);
            }
        } else {
            if (mPendingMarqueeStart != null) {
                mHandler.removeCallbacks(mPendingMarqueeStart);
                mPendingMarqueeStart = null;
            }
            setEnableMarqueeImpl(false);
        }
    }

    private void setEnableMarqueeImpl(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mStatusViewContainer = findViewById(R.id.status_view_container);
        mLogoutView = findViewById(R.id.logout);
        mNotificationIcons = findViewById(R.id.clock_notification_icon_container);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.keyguard_clock_container);
        mClockView.setShowCurrentUserTime(true);
        mTextClock = findViewById(R.id.custom_text_clock_view);
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);
        mKeyguardSliceView = findViewById(R.id.keyguard_status_area);
        mClockView.refreshLockFont();
        refreshLockDateFont();
        mClockView.refreshclocksize();
        updateDateStyles();
        mKeyguardSlice.refreshdatesize();
        refreshOwnerInfoSize();
        refreshOwnerInfoFont();
        mTextColor = mClockView.getCurrentTextColor();

        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();
        updateSettings();
    }

    public KeyguardSliceView getKeyguardSliceView() {
        return mKeyguardSlice;
    }

    /**
     * Moves clock, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        final boolean hasHeader = mKeyguardSlice.hasHeader();
        mClockView.setKeyguardShowingHeader(hasHeader);
        if (mShowingHeader == hasHeader) {
            return;
        }
        mShowingHeader = hasHeader;
        if (mNotificationIcons != null) {
            // Update top margin since header has appeared/disappeared.
            MarginLayoutParams params = (MarginLayoutParams) mNotificationIcons.getLayoutParams();
            params.setMargins(params.leftMargin,
                    hasHeader ? mIconTopMarginWithHeader : mIconTopMargin,
                    params.rightMargin,
                    params.bottomMargin);
            mNotificationIcons.setLayoutParams(params);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        if (mClockView != null) {
            mClockView.refreshclocksize();
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        }
        loadBottomMargin();
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
    }

    private void refreshTime() {
        mClockView.refresh();

        if (mClockSelection == 0) { // default
            mClockView.setFormat12Hour(Patterns.clockView12);
            mClockView.setFormat24Hour(Patterns.clockView24);
        } else if (mClockSelection == 1) { // bold
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong>:mm"));
        } else if (mClockSelection == 2) { // accent
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">hh:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">kk:mm</font>"));
        } else if (mClockSelection == 3) { // accent hour
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">hh</font>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">kk</font>:mm"));
        } else if (mClockSelection == 4) { // accent min
            mClockView.setFormat12Hour(Html.fromHtml("hh<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("kk<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">:mm</font>"));
        } else if (mClockSelection == 5) { // sammy
            mClockView.setFormat12Hour("hh\nmm");
            mClockView.setFormat24Hour("kk\nmm");
        } else if (mClockSelection == 6) { // sammy bold
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><br>mm"));
        } else if (mClockSelection == 7) { // sammy accent
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">hh<br>mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">kk<br>mm</font>"));
        } else if (mClockSelection == 8) { // sammy accent hour
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">hh</font><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">kk</font><br>mm"));
        } else if (mClockSelection == 9) { // sammy accent min
            mClockView.setFormat12Hour(Html.fromHtml("hh<br><font color=" + getResources().getColor(R.color.accent_device_default_light) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("kk<br><font color=" + getResources().getColor(R.color.accent_device_default_light) + ">mm</font>"));
        } else if (mClockSelection == 10 || mClockSelection == 11) { // text
            mTextClock.onTimeChanged();
        }
    }

    private void updateTimeZone(TimeZone timeZone) {
        mClockView.onTimeZoneChanged(timeZone);
    }

    private int getLockDateFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_DATE_FONTS, 28);
    }

    private int getOwnerInfoFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_OWNERINFO_FONTS, 28);
    }

    private int getOwnerInfoSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKOWNER_FONT_SIZE, 18);
    }

    private void refreshFormat() {
        Patterns.update(mContext);
        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    public int getLogoutButtonHeight() {
        if (mLogoutView == null) {
            return 0;
        }
        return mLogoutView.getVisibility() == VISIBLE ? mLogoutView.getHeight() : 0;
    }

    private void updateDateStyles() {
        final ContentResolver resolver = getContext().getContentResolver();

        mDateSelection = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.LOCKSCREEN_DATE_SELECTION, 0, UserHandle.USER_CURRENT);

        switch (mDateSelection) {
            case 0: // default
            default:
                mKeyguardSlice.setViewBackgroundResource(0);
                mDateVerPadding = 0;
                mDateHorPadding = 0;
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 1: // semi-transparent box
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_str_border));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 2: // semi-transparent box (round)
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_border));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 3: // Q-Now Playing background
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.ambient_indication_pill_background));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 4: // accent box
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 5: // accent box transparent
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 6: // gradient box
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 7: // Dark Accent border
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_borderacc));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 8: // Dark Gradient border
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_bordergrad));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
        }
        updateClockAlignment();
    }

    private void refreshLockDateFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockDateFont = isPrimary ? getLockDateFont() : 28;
        switch (lockDateFont) {
            case 0:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case 1:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case 2:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case 3:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case 4:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case 5:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case 6:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case 7:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case 8:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case 9:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case 10:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case 11:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case 12:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case 13:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case 14:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case 15:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case 16:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case 17:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case 18:
                mKeyguardSlice.setViewsTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case 19:
                mKeyguardSlice.setViewsTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case 20:
                mKeyguardSlice.setViewsTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case 21:
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case 22:
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case 23:
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case 24:
                mKeyguardSlice.setViewsTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
            case 25:
                mKeyguardSlice.setViewsTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                break;
            case 26:
                mKeyguardSlice.setViewsTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
                break;
            case 27:
                mKeyguardSlice.setViewsTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                break;
            case 28:
                mKeyguardSlice.setViewsTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                break;
            case 29:
                mKeyguardSlice.setViewsTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                break;
            case 30:
                mKeyguardSlice.setViewsTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
                break;
            case 31:
                mKeyguardSlice.setViewsTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                break;
            case 32:
                mKeyguardSlice.setViewsTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                break;
            case 33:
                mKeyguardSlice.setViewsTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                break;
            case 34:
                mKeyguardSlice.setViewsTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                break;
            case 35:
                mKeyguardSlice.setViewsTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                break;
            case 36:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sourcesanspro-sys", Typeface.NORMAL));
                break;
            case 37:
                mKeyguardSlice.setViewsTypeface(Typeface.create("circularstd-sys", Typeface.NORMAL));
                break;
            case 38:
                mKeyguardSlice.setViewsTypeface(Typeface.create("oneplusslate-sys", Typeface.NORMAL));
                break;
            case 39:
                mKeyguardSlice.setViewsTypeface(Typeface.create("aclonica-sys", Typeface.NORMAL));
                break;
            case 40:
                mKeyguardSlice.setViewsTypeface(Typeface.create("amarante-sys", Typeface.NORMAL));
                break;
            case 41:
                mKeyguardSlice.setViewsTypeface(Typeface.create("bariol-sys", Typeface.NORMAL));
                break;
            case 42:
                mKeyguardSlice.setViewsTypeface(Typeface.create("cagliostro-sys", Typeface.NORMAL));
                break;
            case 43:
                mKeyguardSlice.setViewsTypeface(Typeface.create("coolstory-sys", Typeface.NORMAL));
                break;
            case 44:
                mKeyguardSlice.setViewsTypeface(Typeface.create("lgsmartgothic-sys", Typeface.NORMAL));
                break;
            case 45:
                mKeyguardSlice.setViewsTypeface(Typeface.create("rosemary-sys", Typeface.NORMAL));
                break;
            case 46:
                mKeyguardSlice.setViewsTypeface(Typeface.create("sonysketch-sys", Typeface.NORMAL));
                break;
            case 47:
                mKeyguardSlice.setViewsTypeface(Typeface.create("surfer-sys", Typeface.NORMAL));
                break;
        }
    }

    public float getClockTextSize() {
        return mClockView.getTextSize();
    }

    /**
     * Returns the preferred Y position of the clock.
     *
     * @param totalHeight The height available to position the clock.
     * @return Y position of clock.
     */
    public int getClockPreferredY(int totalHeight) {
        return mClockView.getPreferredY(totalHeight);
    }

    private void updateLogoutView() {
        if (mLogoutView == null) {
            return;
        }
        mLogoutView.setVisibility(shouldShowLogout() ? VISIBLE : GONE);
        // Logout button will stay in language of user 0 if we don't set that manually.
        mLogoutView.setText(mContext.getResources().getString(
                com.android.internal.R.string.global_action_logout));
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String info = mLockPatternUtils.getDeviceOwnerInfo();
        if (info == null) {
            final ContentResolver resolver = mContext.getContentResolver();
            boolean mClockSelection = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT) == 9
                    || Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT) == 10;
            int mTextClockAlign = Settings.System.getIntForUser(resolver,
                    Settings.System.TEXT_CLOCK_ALIGNMENT, 0, UserHandle.USER_CURRENT);

            if (mClockSelection) {
                switch (mTextClockAlign) {
                    case 0:
                    case 4:
                    default:
                        mOwnerInfo.setPaddingRelative(updateTextClockPadding() + 8, 0, 0, 0);
                        mOwnerInfo.setGravity(Gravity.START);
                        break;
                    case 1:
                        mOwnerInfo.setPaddingRelative(0, 0, 0, 0);
                        mOwnerInfo.setGravity(Gravity.CENTER);
                        break;
                    case 2:
                    case 3:
                        mOwnerInfo.setPaddingRelative(0, 0, updateTextClockPadding() + 8, 0);
                        mOwnerInfo.setGravity(Gravity.END);
                        break;
                }
            } else {
                mOwnerInfo.setPaddingRelative(0, 0, 0, 0);
                mOwnerInfo.setGravity(Gravity.CENTER);
            }

            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        mOwnerInfo.setText(info);
        updateDark();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).removeCallback(this);
    }

    @Override
    public void onLocaleListChanged() {
        refreshFormat();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardStatusView:");
        pw.println("  mOwnerInfo: " + (mOwnerInfo == null
                ? "null" : mOwnerInfo.getVisibility() == VISIBLE));
        pw.println("  mPulsing: " + mPulsing);
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        if (mLogoutView != null) {
            pw.println("  logout visible: " + (mLogoutView.getVisibility() == VISIBLE));
        }
        if (mClockView != null) {
            mClockView.dump(fd, pw, args);
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.dump(fd, pw, args);
        }
    }

    private void loadBottomMargin() {
        mIconTopMargin = getResources().getDimensionPixelSize(R.dimen.widget_vertical_padding);
        mIconTopMarginWithHeader = getResources().getDimensionPixelSize(
                R.dimen.widget_vertical_padding_with_header);
    }

    public void refreshOwnerInfoSize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int ownerInfoSize = isPrimary ? getOwnerInfoSize() : 18;

        if (ownerInfoSize == 10) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
        } else if (ownerInfoSize == 11) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
        } else if (ownerInfoSize == 12) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
        } else if (ownerInfoSize == 13) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
        } else if (ownerInfoSize == 14) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
        }  else if (ownerInfoSize == 15) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
        } else if (ownerInfoSize == 16) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
        } else if (ownerInfoSize == 17) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
        } else if (ownerInfoSize == 18) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        } else if (ownerInfoSize == 19) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
        } else if (ownerInfoSize == 20) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
        } else if (ownerInfoSize == 21) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        } else if (ownerInfoSize == 22) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
        } else if (ownerInfoSize == 23) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
        } else if (ownerInfoSize == 24) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
        } else if (ownerInfoSize == 25) {
        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
        }
    }

    private void refreshOwnerInfoFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int ownerinfoFont = isPrimary ? getOwnerInfoFont() : 28;

        switch (ownerinfoFont) {
            case 0:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case 1:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case 2:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case 3:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case 4:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case 5:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case 6:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case 7:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case 8:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case 9:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case 10:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case 11:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case 12:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case 13:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case 14:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case 15:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case 16:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case 17:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case 18:
                mOwnerInfo.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case 19:
                mOwnerInfo.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case 20:
                mOwnerInfo.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case 21:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case 22:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case 23:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case 24:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
            case 25:
                mOwnerInfo.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                break;
            case 26:
                mOwnerInfo.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
                break;
            case 27:
                mOwnerInfo.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                break;
            case 28:
                mOwnerInfo.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                break;
            case 29:
                mOwnerInfo.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                break;
            case 30:
                mOwnerInfo.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
                break;
            case 31:
                mOwnerInfo.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                break;
            case 32:
                mOwnerInfo.setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                break;
            case 33:
                mOwnerInfo.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                break;
            case 34:
                mOwnerInfo.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                break;
            case 35:
                mOwnerInfo.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                break;
            case 36:
                mOwnerInfo.setTypeface(Typeface.create("sourcesanspro-sys", Typeface.NORMAL));
                break;
            case 37:
                mOwnerInfo.setTypeface(Typeface.create("circularstd-sys", Typeface.NORMAL));
                break;
            case 38:
                mOwnerInfo.setTypeface(Typeface.create("oneplusslate-sys", Typeface.NORMAL));
                break;
            case 39:
                mOwnerInfo.setTypeface(Typeface.create("aclonica-sys", Typeface.NORMAL));
                break;
            case 40:
                mOwnerInfo.setTypeface(Typeface.create("amarante-sys", Typeface.NORMAL));
                break;
            case 41:
                mOwnerInfo.setTypeface(Typeface.create("bariol-sys", Typeface.NORMAL));
                break;
            case 42:
                mOwnerInfo.setTypeface(Typeface.create("cagliostro-sys", Typeface.NORMAL));
                break;
            case 43:
                mOwnerInfo.setTypeface(Typeface.create("coolstory-sys", Typeface.NORMAL));
                break;
            case 44:
                mOwnerInfo.setTypeface(Typeface.create("lgsmartgothic-sys", Typeface.NORMAL));
                break;
            case 45:
                mOwnerInfo.setTypeface(Typeface.create("rosemary-sys", Typeface.NORMAL));
                break;
            case 46:
                mOwnerInfo.setTypeface(Typeface.create("sonysketch-sys", Typeface.NORMAL));
                break;
            case 47:
                mOwnerInfo.setTypeface(Typeface.create("surfer-sys", Typeface.NORMAL));
                break;
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                mKeyguardSlice.getLayoutParams();
        mClockSelection = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT);
        final boolean mShowClock = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK, 1, UserHandle.USER_CURRENT) == 1;

        mClockView = findViewById(R.id.keyguard_clock_container);
        mDefaultClockView = findViewById(R.id.default_clock_view);
        mClockView.setVisibility(mDarkAmount != 1
                ? (mShowClock ? View.VISIBLE : View.GONE) : View.VISIBLE);
        mSmallClockView = findViewById(R.id.clock_view);
        mTextClock = findViewById(R.id.custom_text_clock_view);

        if (mClockSelection == 5 || mClockSelection == 6
                || mClockSelection == 7 || mClockSelection == 8)
            mDefaultClockView.setLineSpacing(0, 0.8f);

        if (mClockSelection != 9 && mClockSelection != 10) {
            mTextClock.setVisibility(View.GONE);
            mSmallClockView.setVisibility(View.VISIBLE);
            params.addRule(RelativeLayout.BELOW, R.id.clock_view);
        } else {
            mTextClock.setVisibility(View.VISIBLE);
            mSmallClockView.setVisibility(View.GONE);
            params.addRule(RelativeLayout.BELOW, R.id.custom_text_clock_view);
        }
        updateDateStyles();
    }

    public void updateAll() {
        updateSettings();
    }

    private void updateClockAlignment() {
        final ContentResolver resolver = getContext().getContentResolver();

        mTextClockAlignment = Settings.System.getIntForUser(resolver,
                Settings.System.TEXT_CLOCK_ALIGNMENT, 0, UserHandle.USER_CURRENT);

        mTextClock = findViewById(R.id.custom_text_clock_view);

        if (mClockSelection == 9 || mClockSelection == 10) {
            switch (mTextClockAlignment) {
                case 0:
                default:
                    mTextClock.setGravity(Gravity.START);
                    mTextClock.setPaddingRelative(updateTextClockPadding(), 0, 0, 0);
                    mKeyguardSlice.setGravity(Gravity.START);
                    mKeyguardSlice.setPaddingRelative(updateTextClockPadding(), 0, 0, 0);
                    break;
                case 1:
                    mTextClock.setGravity(Gravity.CENTER);
                    mTextClock.setPaddingRelative(0, 0, 0, 0);
                    mKeyguardSlice.setGravity(Gravity.CENTER);
                    mKeyguardSlice.setPaddingRelative(0, 0, 0, 0);
                    break;
                case 2:
                    mTextClock.setGravity(Gravity.END);
                    mTextClock.setPaddingRelative(0, 0, updateTextClockPadding(), 0);
                    mKeyguardSlice.setGravity(Gravity.END);
                    mKeyguardSlice.setPaddingRelative(0, 0, updateTextClockPadding(), 0);
                    break;
                case 3:
                    mTextClock.setGravity(Gravity.START);
                    mTextClock.setPaddingRelative(updateTextClockPadding(), 0, 0, 0);
                    mKeyguardSlice.setGravity(Gravity.END);
                    mKeyguardSlice.setPaddingRelative(0, 0, updateTextClockPadding(), 0);
                    break;
                case 4:
                    mTextClock.setGravity(Gravity.END);
                    mTextClock.setPaddingRelative(0, 0, updateTextClockPadding(), 0);
                    mKeyguardSlice.setGravity(Gravity.START);
                    mKeyguardSlice.setPaddingRelative(updateTextClockPadding(), 0, 0, 0);
                    break;
            }
        } else {
            mKeyguardSlice.setPaddingRelative(0, 0, 0, 0);
            mKeyguardSlice.setGravity(Gravity.CENTER);
        }
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            cacheKey = key;
        }
    }

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        mClockView.setDarkAmount(darkAmount);
        updateDark();
    }

    private void updateDark() {
        boolean dark = mDarkAmount == 1;
        if (mLogoutView != null) {
            mLogoutView.setAlpha(dark ? 0 : 1);
        }

        if (mOwnerInfo != null) {
            boolean hasText = !TextUtils.isEmpty(mOwnerInfo.getText());
            mOwnerInfo.setVisibility(hasText ? VISIBLE : GONE);
            layoutOwnerInfo();
        }

        final int blendedTextColor = ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
        updateSettings();
    }

    private void layoutOwnerInfo() {
        if (mOwnerInfo != null && mOwnerInfo.getVisibility() != GONE) {
            // Animate owner info during wake-up transition
            mOwnerInfo.setAlpha(1f - mDarkAmount);

            float ratio = mDarkAmount;
            // Calculate how much of it we should crop in order to have a smooth transition
            int collapsed = mOwnerInfo.getTop() - mOwnerInfo.getPaddingTop();
            int expanded = mOwnerInfo.getBottom() + mOwnerInfo.getPaddingBottom();
            int toRemove = (int) ((expanded - collapsed) * ratio);
            setBottom(getMeasuredHeight() - toRemove);
            if (mNotificationIcons != null) {
                // We're using scrolling in order not to overload the translation which is used
                // when appearing the icons
                mNotificationIcons.setScrollY(toRemove);
            }
        } else if (mNotificationIcons != null){
            mNotificationIcons.setScrollY(0);
        }
    }

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
    }

    private boolean shouldShowLogout() {
        return KeyguardUpdateMonitor.getInstance(mContext).isLogoutEnabled()
                && KeyguardUpdateMonitor.getCurrentUser() != UserHandle.USER_SYSTEM;
    }

    private void onLogoutClicked(View view) {
        int currentUserId = KeyguardUpdateMonitor.getCurrentUser();
        try {
            mIActivityManager.switchUser(UserHandle.USER_SYSTEM);
            mIActivityManager.stopUser(currentUserId, true /*force*/, null);
        } catch (RemoteException re) {
            Log.e(TAG, "Failed to logout user", re);
        }
    }

    private int updateTextClockPadding() {
        final ContentResolver resolver = getContext().getContentResolver();
        int mTextClockPadding = Settings.System.getIntForUser(resolver,
                Settings.System.TEXT_CLOCK_PADDING, 55, UserHandle.USER_CURRENT);

        switch (mTextClockPadding) {
            case 0:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_0);
            case 1:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_1);
            case 2:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_2);
            case 3:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_3);
            case 4:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_4);
            case 5:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_5);
            case 6:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_6);
            case 7:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_7);
            case 8:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_8);
            case 9:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_9);
            case 10:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_10);
            case 11:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_11);
            case 12:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_12);
            case 13:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_13);
            case 14:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_14);
            case 15:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_15);
            case 16:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_16);
            case 17:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_17);
            case 18:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_18);
            case 19:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_19);
            case 20:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_20);
            case 21:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_21);
            case 22:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_22);
            case 23:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_23);
            case 24:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_24);
            case 25:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_25);
            case 26:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_26);
            case 27:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_27);
            case 28:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_28);
            case 29:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_29);
            case 30:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_30);
            case 31:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_31);
            case 32:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_32);
            case 33:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_33);
            case 34:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_34);
            case 35:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_35);
            case 36:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_36);
            case 37:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_37);
            case 38:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_38);
            case 39:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_39);
            case 40:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_40);
            case 41:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_41);
            case 42:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_42);
            case 43:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_43);
            case 44:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_44);
            case 45:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_45);
            case 46:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_46);
            case 47:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_47);
            case 48:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_48);
            case 49:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_49);
            case 50:
                return (int) mContext.getResources().getDimension(R.dimen.lock_date_font_size_50);
            case 51:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_51);
            case 52:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_52);
            case 53:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_53);
            case 54:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_54);
            case 55:
            default:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_55);
            case 56:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_56);
            case 57:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_57);
            case 58:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_58);
            case 59:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_59);
            case 60:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_60);
            case 61:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_61);
            case 62:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_62);
            case 63:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_63);
            case 64:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_64);
            case 65:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_65);
            case 66:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_66);
            case 67:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_67);
            case 68:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_68);
            case 69:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_69);
            case 70:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_70);
            case 71:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_71);
            case 72:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_72);
            case 73:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_73);
            case 74:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_74);
            case 75:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_75);
            case 76:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_76);
            case 77:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_77);
            case 78:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_78);
            case 79:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_79);
            case 80:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_80);
            case 81:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_81);
            case 82:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_82);
            case 83:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_83);
            case 84:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_84);
            case 85:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_85);
            case 86:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_86);
            case 87:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_87);
            case 88:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_88);
            case 89:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_89);
            case 90:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_90);
            case 91:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_91);
            case 92:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_92);
            case 93:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_93);
            case 94:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_94);
            case 95:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_95);
            case 96:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_96);
            case 97:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_97);
            case 98:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_98);
            case 99:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_99);
            case 100:
                return (int) mContext.getResources().getDimension(R.dimen.lock_clock_font_size_100);
        }
    }
}
