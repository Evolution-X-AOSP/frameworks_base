/*
 * Copyright (C) 2021 Yet Another AOSP Project
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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;

import java.text.DecimalFormat;

public class NetworkTraffic extends TextView {

    private static final int MSG_PERIODIC = 0;
    private static final int MSG_UPDATE = 1;
    private static final int INTERVAL = 1500; //ms
    private static final int BOTH = 0;
    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int COMBINED = 3;
    private static final int DYNAMIC = 4;
    private static final int KB = 1024;
    private static final int MB = KB * KB;
    private static final int GB = MB * KB;
    private static final String SYMBOL = "B/s";
    private static final String THREAD_NAME = "NetworkTraffic";
    static final int LOCATION_STATUSBAR = 0;
    static final int LOCATION_QS_HEADER = 1;
    static final int LOCATION_BOTH = 2;

    private static final DecimalFormat decimalFormat = new DecimalFormat("##0.#");
    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }

    private float mDisplayDensity;
    private long mLastUpdateTime;
    private int mTrafficType;
    private int mAutoHideThreshold;
    private int mFontSize;
    private boolean mShowArrow;
    private boolean mAttached;
    private boolean mIsConnected;
    private boolean mScreenOn = true;
    private boolean iBytes;
    private boolean oBytes;

    boolean mIsEnabled;
    int mLocation;

    private final int mResourceFontSize;
    private final HandlerThread mHandlerThread = new HandlerThread(THREAD_NAME);
    private Looper mLooper;
    private Handler mTrafficHandler;

    private final Handler.Callback mTrafficHandlerCallback = new Handler.Callback() {
        private long totalRxBytes;
        private long totalTxBytes;

        @Override
        public boolean handleMessage(Message msg) {
            final boolean isUpdate = msg.what == 1;
            long timeDelta = SystemClock.elapsedRealtime() - mLastUpdateTime;

            if ((timeDelta < INTERVAL * .95 && timeDelta < 1) || isUpdate) {
                timeDelta = Long.MAX_VALUE;
            }
            mLastUpdateTime = SystemClock.elapsedRealtime();

            long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            long newTotalTxBytes = TrafficStats.getTotalTxBytes();
            long rxData = 0;
            long txData = 0;
            iBytes = false;
            oBytes = false;

            if (!isUpdate) {
                // Calculate the data rate from the change in total bytes and time
                rxData = newTotalRxBytes - totalRxBytes;
                txData = newTotalTxBytes - totalTxBytes;
                iBytes = (rxData <= (mAutoHideThreshold * 1024L));
                oBytes = (txData <= (mAutoHideThreshold * 1024L));
            } else {
                totalRxBytes = 0;
                totalTxBytes = 0;
            }

            if (shouldHide(rxData, txData, timeDelta)) {
                getHandler().post(() -> {
                    setText("");
                    setVisibility(View.GONE);
                });
            } else if (!isUpdate) {
                String output;
                switch (mTrafficType) {
                    case UP:
                        output = formatOutput(timeDelta, txData);
                        break;
                    case DOWN:
                        output = formatOutput(timeDelta, rxData);
                        break;
                    case BOTH:
                        // Get information for uplink ready so the line return can be added
                        output = formatOutput(timeDelta, txData);
                        // Ensure text size is where it needs to be
                        output += "\n";
                        // Add information for downlink if it's called for
                        output += formatOutput(timeDelta, rxData);
                        break;
                    case DYNAMIC:
                        if (txData > rxData) {
                            output = formatOutput(timeDelta, txData);
                            iBytes = true;
                        } else {
                            output = formatOutput(timeDelta, rxData);
                            oBytes = true;
                        }
                        break;
                    default: // COMBINED
                        output = formatOutput(timeDelta, rxData + txData);
                        if (txData > rxData) iBytes = true;
                        else oBytes = true;
                        break;
                }
                if (mLocation == LOCATION_BOTH || mLocation == LOCATION_STATUSBAR) {
                    // don't show a vary tiny text (4dp minimum)
                    // if we reached this size just hide the entire view
                    final float dpTextSize = getTextSize() / mDisplayDensity;
                    if (dpTextSize < 4f) output = "";
                }
                // Update view if there's anything new to show
                final String out = output;
                getHandler().post(() -> {
                    if (!out.contentEquals(getText())) {
                        setText(out);
                        setVisibility(out != "" ? View.VISIBLE : View.GONE);
                    }
                });
            }
            getHandler().post(NetworkTraffic.this::updateTrafficDrawable);

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            mTrafficHandler.removeCallbacksAndMessages(null);
            if (!isDisabled() && mScreenOn) {
                mTrafficHandler.sendEmptyMessageDelayed(MSG_PERIODIC,
                        isUpdate ? 0 : INTERVAL);
            } else {
                getHandler().post(() -> {
                    setText("");
                    setVisibility(View.GONE);
                });
            }

            return true;
        }

        private String formatOutput(long timeDelta, long data) {
            long speed = (long)(data / (timeDelta / 1000F));
            if (speed < KB) return decimalFormat.format(speed) + SYMBOL;
            if (speed < MB) return decimalFormat.format(speed / (float)KB) + 'K' + SYMBOL;
            if (speed < GB) return decimalFormat.format(speed / (float)MB) + 'M' + SYMBOL;
            return decimalFormat.format(speed / (float)GB) + 'G' + SYMBOL;
        }

        private boolean shouldHide(long rxData, long txData, long timeDelta) {
            if (isDisabled()) return true;
            if (!mIsConnected) return true;
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KB;
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KB;
            if (mTrafficType == UP) return speedTxKB < mAutoHideThreshold;
            if (mTrafficType == DOWN) return speedRxKB < mAutoHideThreshold;
            return (speedRxKB < mAutoHideThreshold && speedTxKB < mAutoHideThreshold);
        }
    };

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                if (mScreenOn) return;
                mScreenOn = true;
                getHandler().post(NetworkTraffic.this::updateSettings);
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                if (!mScreenOn) return;
                mLastUpdateTime = 0;
                mScreenOn = false;
                mTrafficHandler.removeCallbacksAndMessages(null);
            }
        }
    };

    private final ConnectivityManager mConnectivityManager;
    private final ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            if (mIsConnected) return;
            mIsConnected = true;
            if (mScreenOn) getHandler().post(NetworkTraffic.this::updateSettings);
        }

        @Override
        public void onLost(Network network) {
            if (!mIsConnected) return;
            mIsConnected = false;
            if (mScreenOn) getHandler().post(NetworkTraffic.this::updateSettings);
        }
    };

    private final DisplayManager mDisplayManager;
    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) { /* Do nothing */ }

        @Override
        public void onDisplayRemoved(int displayId) { /* Do nothing */ }

        @Override
        public void onDisplayChanged(int displayId) {
            if (getDisplay().getDisplayId() != displayId) return;
            mDisplayDensity = getResources().getDisplayMetrics().density;
            if (mScreenOn) getHandler().post(NetworkTraffic.this::updateSettings);
        }
    };

    private final SettingsObserver mSettingsObserver = new SettingsObserver(getHandler());
    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void start() {
            final ContentResolver resolver = getContext().getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_STATE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_TYPE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_ARROW), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_FONT_SIZE), false,
                    this, UserHandle.USER_ALL);
        }

        void stop() {
            getContext().getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            setMode();
            getHandler().post(NetworkTraffic.this::updateSettings);
        }
    }

    public NetworkTraffic(Context context) {
        this(context, null);
    }

    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        mResourceFontSize = getResources().getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            mHandlerThread.start();
            mLooper = mHandlerThread.getLooper();
            mTrafficHandler = new Handler(mLooper, mTrafficHandlerCallback);
            mDisplayDensity = getResources().getDisplayMetrics().density;
            mIsConnected = mConnectivityManager.getActiveNetwork() != null;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
            mDisplayManager.registerDisplayListener(mDisplayListener, getHandler());
            mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
            mSettingsObserver.start();
        }
        setMode();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mSettingsObserver.stop();
            mHandlerThread.quit();
            mAttached = false;
        }
    }

    private void updateSettings() {
        setText("");
        setVisibility(View.GONE);
        updateTextSize();
        updateTrafficDrawable();
        mTrafficHandler.removeCallbacksAndMessages(null);
        if (mIsEnabled && mAttached && !isDisabled()) {
            mLastUpdateTime = SystemClock.elapsedRealtime();
            mTrafficHandler.sendEmptyMessage(MSG_UPDATE);
        }
    }

    private void setMode() {
        ContentResolver resolver = getContext().getContentResolver();
        mIsEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 0,
                UserHandle.USER_CURRENT) == 1;
        mTrafficType = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_TYPE, 0,
                UserHandle.USER_CURRENT);
        mAutoHideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 1,
                UserHandle.USER_CURRENT);
        mShowArrow = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_ARROW, 1,
	        UserHandle.USER_CURRENT) == 1;
        mLocation = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_VIEW_LOCATION, 0,
                UserHandle.USER_CURRENT);
        mFontSize = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_FONT_SIZE, 10,
                UserHandle.USER_CURRENT);
    }

    private void updateTrafficDrawable() {
        int intTrafficDrawable = 0;
        if (mIsEnabled && mShowArrow && !isDisabled()) {
            switch (mTrafficType) {
                case UP:
                    if (oBytes) intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                    else intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                    break;
                case DOWN:
                    if (iBytes) intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                    else intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                    break;
                case DYNAMIC: case COMBINED:
                    if (iBytes && !oBytes) intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                    else if (!iBytes && oBytes) intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                    else intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                    break;
                default: // BOTH
                    if (!iBytes && !oBytes) intTrafficDrawable = R.drawable.stat_sys_network_traffic_updown;
                    else if (!oBytes) intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
                    else if (!iBytes) intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
                    else intTrafficDrawable = R.drawable.stat_sys_network_traffic;
                    break;
            }
        }
        if (intTrafficDrawable != 0) {
            Drawable d = getContext().getDrawable(intTrafficDrawable);
            if (d != null) d.setColorFilter(new PorterDuffColorFilter(getCurrentTextColor(), Mode.MULTIPLY));
            setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.net_traffic_txt_img_padding));
            setCompoundDrawablesWithIntrinsicBounds(null, null, d, null);
            return;
        }
        setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
    }

    public void setTintColor(int color) {
        setTextColor(color);
        updateTrafficDrawable();
    }

    boolean isDisabled() {
        return !mIsEnabled || mLocation == LOCATION_STATUSBAR;
    }

    /**
     * updates the text size and properties according to user settings
     * @return array of int. index 0 contains the size and index 1 the unit. null if disabled
     */
    int[] updateTextSize() {
        if (isDisabled()) return null;
        int size = mResourceFontSize;
        int unit = TypedValue.COMPLEX_UNIT_PX;
        if (mTrafficType == BOTH) {
            setTextSize(unit, (float)size);
            setMaxLines(2);
            return new int[] { size, unit };
        }
        size = mFontSize;
        unit = TypedValue.COMPLEX_UNIT_DIP;
        setTextSize(unit, (float)size);
        setMaxLines(1);
        return new int[] { size, unit };
    }
}
