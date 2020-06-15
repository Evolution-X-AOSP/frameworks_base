package com.android.systemui.statusbar.info;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.widget.TextView;
import android.provider.Settings;
import android.view.View;

import com.android.internal.util.evolution.EvolutionUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;
import com.android.settingslib.net.DataUsageController;

public class DataUsageView extends TextView {

    private Context mContext;
    private NetworkController mNetworkController;
    private static boolean shouldUpdateData;
    private String formatedinfo;
    private Handler mHandler;
    private static long mTime;

    public DataUsageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mNetworkController = Dependency.get(NetworkController.class);
        mHandler = new Handler();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if ((isDataUsageEnabled() == 0) && this.getText().toString() != "") {
            setText("");
        }
        if (isDataUsageEnabled() != 0) {
            if(shouldUpdateData) {
                shouldUpdateData = false;
                updateDataUsage();
            } else {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateDataUsage();
                    }
                }, 2000);
            }
        }
    }

    private void updateDataUsage() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                updateUsageData();
            }
        });
        setText(formatedinfo);
    }

    private void updateUsageData() {
        DataUsageController mobileDataController = new DataUsageController(mContext);
        mobileDataController.setSubscriptionId(
            SubscriptionManager.getDefaultDataSubscriptionId());
        final DataUsageController.DataUsageInfo info = isDataUsageEnabled() == 1 ?
                (EvolutionUtils.isWiFiConnected(mContext) ?
                        mobileDataController.getDailyWifiDataUsageInfo()
                        : mobileDataController.getDailyDataUsageInfo())
                : (EvolutionUtils.isWiFiConnected(mContext) ?
                        mobileDataController.getWifiDataUsageInfo()
                        : mobileDataController.getDataUsageInfo());
        formatedinfo = formatDataUsage(info.usageLevel) + " ";
    }

    public int isDataUsageEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_DATAUSAGE, 0);
    }

    public static void updateUsage() {
        // limit to one update per second
        long time = System.currentTimeMillis();
        if (time - mTime > 1000) {
            shouldUpdateData = true;
        }
        mTime = time;
    }

    private CharSequence formatDataUsage(long byteValue) {
        final BytesResult res = Formatter.formatBytes(mContext.getResources(), byteValue,
                Formatter.FLAG_IEC_UNITS);
        return BidiFormatter.getInstance().unicodeWrap(mContext.getString(
                com.android.internal.R.string.fileSizeSuffix, res.value, res.units));
    }
}
