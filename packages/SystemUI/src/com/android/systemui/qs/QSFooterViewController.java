/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.qs;

import static android.provider.Settings.Secure.QS_SHOW_DATA_USAGE;
import static android.provider.Settings.Secure.QS_TILES;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkScoreManager;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.dagger.QSScope;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.ViewController;

import com.android.settingslib.wifi.WifiStatusTracker;

import java.util.Arrays;
import javax.inject.Inject;

/**
 * Controller for {@link QSFooterView}.
 */
@QSScope
public class QSFooterViewController extends ViewController<QSFooterView>
        implements QSFooter, TunerService.Tunable {

    private final UserTracker mUserTracker;
    private final QSPanelController mQsPanelController;
    private final PageIndicator mPageIndicator;
    private final View mEditButton;
    private final FalsingManager mFalsingManager;
    private final ActivityStarter mActivityStarter;
    private final WifiStatusTracker mWifiTracker;
    private final NetworkController mNetworkController;
    private final Context mContext;
    private final TunerService mTunerService;

    private static final String INTERNET_TILE = "internet";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mWifiTracker.handleBroadcast(intent);
            onWifiStatusUpdated();
        }
    };

    private final SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setNoSims(boolean show, boolean simDetected) {
            mView.setNoSims(show);
         }
    };

    @Inject
    QSFooterViewController(QSFooterView view,
            UserTracker userTracker,
            FalsingManager falsingManager,
            ActivityStarter activityStarter,
            QSPanelController qsPanelController,
            NetworkController networkController,
            Context context,
            TunerService tunerService) {
        super(view);
        mUserTracker = userTracker;
        mQsPanelController = qsPanelController;
        mFalsingManager = falsingManager;
        mActivityStarter = activityStarter;
        mNetworkController = networkController;
        mContext = context;
        mTunerService = tunerService;
        mPageIndicator = mView.findViewById(R.id.footer_page_indicator);
        mEditButton = mView.findViewById(android.R.id.edit);
        mWifiTracker = new WifiStatusTracker(context, context.getSystemService(WifiManager.class),
                context.getSystemService(NetworkScoreManager.class),
                context.getSystemService(ConnectivityManager.class),
                        this::onWifiStatusUpdated);
    }

    @Override
    protected void onViewAttached() {
        mEditButton.setOnClickListener(view -> {
            if (mFalsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
                return;
            }
            mActivityStarter
                    .postQSRunnableDismissingKeyguard(() -> mQsPanelController.showEdit(view));
        });
        mQsPanelController.setFooterPageIndicator(mPageIndicator);
        final IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, filter);
        mWifiTracker.fetchInitialState();
        mWifiTracker.setListening(true);
        onWifiStatusUpdated();
        mNetworkController.addCallback(mSignalCallback);
        mTunerService.addTunable(this, QS_TILES, QS_SHOW_DATA_USAGE);
    }

    @Override
    protected void onViewDetached() {
        mContext.unregisterReceiver(mReceiver);
        mNetworkController.removeCallback(mSignalCallback);
        mTunerService.removeTunable(this);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (key.equals(QS_TILES)) {
            if (TextUtils.isEmpty(newValue)) {
                newValue = mContext.getString(R.string.quick_settings_tiles_default);
            }
            int rows = mContext.getResources().getInteger(R.integer.quick_settings_max_rows);
            int cols = mContext.getResources().getInteger(R.integer.quick_settings_num_columns);
            // Don't show the suffix if we have internet tile in the first page.
            mView.setShowSuffix(!Arrays.stream(newValue.split(","))
                                       .limit(rows * cols)
                                       .anyMatch(INTERNET_TILE::equals));
         } else if (key.equals(QS_SHOW_DATA_USAGE)) {
            mView.setHideDataUsage(!TunerService.parseIntegerSwitch(newValue, true));
         }
    }

    @Override
    public void setVisibility(int visibility) {
        mView.setVisibility(visibility);
        mEditButton.setClickable(visibility == View.VISIBLE);
    }

    @Override
    public void setExpanded(boolean expanded) {
        mView.setExpanded(expanded);
    }

    @Override
    public void setExpansion(float expansion) {
        mView.setExpansion(expansion);
    }

    @Override
    public void setKeyguardShowing(boolean keyguardShowing) {
        mView.setKeyguardShowing();
    }

    @Override
    public void disable(int state1, int state2, boolean animate) {
        mView.disable(state2);
    }

    private void onWifiStatusUpdated() {
        mView.setIsWifiConnected(mWifiTracker.connected);
        mView.setWifiSsid(mWifiTracker.ssid);
    }
}
