/*
 * Copyright (C) 2019 The PixelDust Project
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

package com.android.systemui.statusbar.policy;

import static com.android.systemui.statusbar.StatusBarIconView.STATE_DOT;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN;
import static com.android.systemui.statusbar.StatusBarIconView.STATE_ICON;

import java.text.DecimalFormat;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.StatusIconDisplayable;

/** @hide */
public class StatusBarNetworkTraffic extends NetworkTraffic implements StatusIconDisplayable {

    public static final String SLOT = "networktraffic";

    private static final int INTERVAL = 1500; //ms
    private static final int KB = 1024;
    private static final int MB = KB * KB;
    private static final int GB = MB * KB;
    private static final String symbol = "/s";

    private boolean mIsEnabled;
    private boolean mAttached;
    private long totalRxBytes;
    private long totalTxBytes;
    private long lastUpdateTime;
    private int txtImgPadding;
    private int mAutoHideThreshold;
    private int mTintColor;
    private int mVisibleState = -1;
    private boolean mTrafficVisible = false;
    private boolean mSystemIconVisible = true;
    private boolean indicatorUp = false;
    private boolean indicatorDown = false;
    private boolean mShowArrow;
    private String txtFont;

    private boolean mScreenOn = true;

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long timeDelta = SystemClock.elapsedRealtime() - lastUpdateTime;

            if (timeDelta < INTERVAL * .95) {
                if (msg.what != 1) {
                    // we just updated the view, nothing further to do
                    return;
                }
                if (timeDelta < 1) {
                    // Can't div by 0 so make sure the value displayed is minimal
                    timeDelta = Long.MAX_VALUE;
                }
            }
            lastUpdateTime = SystemClock.elapsedRealtime();

            // Calculate the data rate from the change in total bytes and time
            long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            long newTotalTxBytes = TrafficStats.getTotalTxBytes();
            long rxData = newTotalRxBytes - totalRxBytes;
            long txData = newTotalTxBytes - totalTxBytes;

            if (shouldHide(rxData, txData, timeDelta)) {
                setText("");
                mTrafficVisible = false;
            } else if (shouldShowUpload(rxData, txData, timeDelta)) {
                // Show information for uplink if it's called for
                CharSequence output = formatOutput(timeDelta, txData, symbol);

                // Update view if there's anything new to show
                if (output != getText()) {
                    setTypeface(Typeface.create(txtFont, Typeface.NORMAL));
                    setGravity(Gravity.CENTER);
                    setMinLines(2);
                    setMaxLines(2);
                    setLineSpacing(0.75f, 0.75f);
                    setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_UNIFORM);
                    setText(output);
                    indicatorUp = true;
                }
                mTrafficVisible = true;
            } else {
                // Add information for downlink if it's called for
                CharSequence output = formatOutput(timeDelta, rxData, symbol);

                // Update view if there's anything new to show
                if (output != getText()) {
                    setTypeface(Typeface.create(txtFont, Typeface.NORMAL));
                    setGravity(Gravity.CENTER);
                    setMinLines(2);
                    setMaxLines(2);
                    setLineSpacing(0.75f, 0.75f);
                    setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_UNIFORM);
                    setText(output);
                    indicatorDown = true;
                }
                mTrafficVisible = true;
            }
            updateVisibility();
            if (mShowArrow)
                updateTrafficDrawable();

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            clearHandlerCallbacks();
            mTrafficHandler.postDelayed(mRunnable, INTERVAL);
        }

        private CharSequence formatOutput(long timeDelta, long data, String symbol) {
            long speed = (long)(data / (timeDelta / 1000F));

            return formatDecimal(speed);
        }

        private CharSequence formatDecimal(long speed) {
            DecimalFormat mDecimalFormat;
            String mUnit;
            String formatSpeed;
            SpannableString spanUnitString;
            SpannableString spanSpeedString;

            if (speed >= GB) {
                mUnit = "GB";
                mDecimalFormat = new DecimalFormat("0.00");
                formatSpeed =  mDecimalFormat.format(speed / (float)GB);
            } else if (speed >= 100 * MB) {
                mDecimalFormat = new DecimalFormat("000");
                mUnit = "MB";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= 10 * MB) {
                mDecimalFormat = new DecimalFormat("00.0");
                mUnit = "MB";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= MB) {
                mDecimalFormat = new DecimalFormat("0.00");
                mUnit = "MB";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else if (speed >= 100 * KB) {
                mDecimalFormat = new DecimalFormat("000");
                mUnit = "KB";
                formatSpeed =  mDecimalFormat.format(speed / (float)KB);
            } else if (speed >= 10 * KB) {
                mDecimalFormat = new DecimalFormat("00.0");
                mUnit = "KB";
                formatSpeed =  mDecimalFormat.format(speed / (float)MB);
            } else {
                mDecimalFormat = new DecimalFormat("0.00");
                mUnit = "KB";
                formatSpeed = mDecimalFormat.format(speed / (float)KB);
            }

            spanSpeedString = new SpannableString(formatSpeed);
            spanSpeedString.setSpan(new RelativeSizeSpan(0.75f), 0, (formatSpeed).length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            spanUnitString = new SpannableString(mUnit + symbol);
            spanUnitString.setSpan(new RelativeSizeSpan(0.70f), 0, (mUnit + symbol).length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return TextUtils.concat(spanSpeedString, "\n", spanUnitString);
        }

        private boolean shouldHide(long rxData, long txData, long timeDelta) {
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KB;
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KB;
            return !getConnectAvailable() ||
                    (speedRxKB < mAutoHideThreshold &&
                    speedTxKB < mAutoHideThreshold);
        }

        private boolean shouldShowUpload(long rxData, long txData, long timeDelta) {
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KB;
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KB;

            return (speedTxKB > speedRxKB);
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_STATE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_ARROW), false,
                    this, UserHandle.USER_ALL);
        }

        /*
         *  @hide
         */
        @Override
        public void onChange(boolean selfChange) {
            setMode();
            updateSettings();
        }
    }

    /*
     *  @hide
     */
    public StatusBarNetworkTraffic(Context context) {
        this(context, null);
    }

    /*
     *  @hide
     */
    public StatusBarNetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     *  @hide
     */
    public StatusBarNetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        txtFont = getResources().getString(com.android.internal.R.string.config_headlineFontFamilyMedium);
        txtImgPadding = resources.getDimensionPixelSize(R.dimen.net_traffic_sb_txt_img_padding);
        mTintColor = resources.getColor(android.R.color.white);
        Handler mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        setMode();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) && mScreenOn) {
                updateSettings();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOn = true;
                updateSettings();
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
                clearHandlerCallbacks();
            }
        }
    };

    private boolean getConnectAvailable() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = (connManager != null) ? connManager.getActiveNetworkInfo() : null;
        return network != null;
    }

    private void updateSettings() {
        updateVisibility();
        if (mIsEnabled) {
            if (mAttached) {
                totalRxBytes = TrafficStats.getTotalRxBytes();
                lastUpdateTime = SystemClock.elapsedRealtime();
                mTrafficHandler.sendEmptyMessage(1);
            }
            updateTrafficDrawable();
            return;
        } else {
            clearHandlerCallbacks();
        }
    }

    private void setMode() {
        ContentResolver resolver = mContext.getContentResolver();
        mIsEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 0,
                UserHandle.USER_CURRENT) == 1;
        mAutoHideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 0,
                UserHandle.USER_CURRENT);
        mShowArrow = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_ARROW, 1,
                UserHandle.USER_CURRENT) == 1;
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.removeMessages(0);
        mTrafficHandler.removeMessages(1);
    }

    private void updateTrafficDrawable() {
        int indicatorDrawable;
        if (mIsEnabled && mShowArrow) {
            if (indicatorUp) {
                indicatorDrawable = R.drawable.stat_sys_network_traffic_up_arrow;
                Drawable d = getContext().getDrawable(indicatorDrawable);
                d.setColorFilter(mTintColor, Mode.MULTIPLY);
                setCompoundDrawablePadding(txtImgPadding);
                setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
            } else if (indicatorDown) {
                indicatorDrawable = R.drawable.stat_sys_network_traffic_down_arrow;
                Drawable d = getContext().getDrawable(indicatorDrawable);
                d.setColorFilter(mTintColor, Mode.MULTIPLY);
                setCompoundDrawablePadding(txtImgPadding);
                setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
            } else {
                setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        } else {
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
        setTextColor(mTintColor);
        indicatorUp = false;
        indicatorDown = false;
    }

    public void onDensityOrFontScaleChanged() {
        final Resources resources = getResources();
        setCompoundDrawablePadding(txtImgPadding);
        setTypeface(Typeface.create(txtFont, Typeface.NORMAL));
        setGravity(Gravity.CENTER);
        setMinLines(2);
        setMaxLines(2);
        setLineSpacing(0.75f, 0.75f);
        setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_UNIFORM);
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public String getSlot() {
        return SLOT;
    }

    @Override
    public boolean isIconVisible() {
        return mIsEnabled;
    }

    @Override
    public int getVisibleState() {
        return mVisibleState;
    }

    @Override
    public void setVisibleState(int state, boolean animate) {
        if (state == mVisibleState) {
            return;
        }
        mVisibleState = state;

        switch (state) {
            case STATE_ICON:
                mSystemIconVisible = true;
                break;
            case STATE_DOT:
            case STATE_HIDDEN:
            default:
                mSystemIconVisible = false;
                break;
        }
        updateVisibility();
    }

    @Override
    protected String getSystemSettingKey() {
        return Settings.System.NETWORK_TRAFFIC_STATE;
    }

    @Override
    protected void updateVisibility() {
        if (mIsEnabled && mTrafficVisible && mSystemIconVisible) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    public void setStaticDrawableColor(int color) {
        mTintColor = color;
        setTextColor(mTintColor);
        updateTrafficDrawable();
    }

    @Override
    public void setDecorColor(int color) {
    }
} 
