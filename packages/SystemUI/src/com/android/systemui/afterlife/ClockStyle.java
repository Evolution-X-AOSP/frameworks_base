package com.android.systemui.afterlife;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.res.R;

import com.android.internal.util.evolution.ThemeUtils;

public class ClockStyle extends RelativeLayout implements TunerService.Tunable {

    private static final int[] CLOCK_VIEW_IDS = {
            0,
            R.id.keyguard_clock_style_oos,
            R.id.keyguard_clock_style_ios,
            R.id.keyguard_clock_style_cos,
            R.id.keyguard_clock_style_custom,
            R.id.keyguard_clock_style_custom1,
            R.id.keyguard_clock_style_custom2,
            R.id.keyguard_clock_style_custom3,
            R.id.keyguard_clock_style_miui,
            R.id.keyguard_clock_style_ide,
            R.id.keyguard_clock_style_lottie,
            R.id.keyguard_clock_style_lottie2,
            R.id.keyguard_clock_style_fluid,
            R.id.keyguard_clock_style_hyper,
            R.id.keyguard_clock_style_dual,
            R.id.keyguard_clock_style_stylish,
            R.id.keyguard_clock_style_sidebar,
            R.id.keyguard_clock_style_minimal,
            R.id.keyguard_clock_style_minimal2,
            R.id.keyguard_clock_style_minimal3
    };

    private static final int DEFAULT_STYLE = 0; //Disabled
    private static final String CLOCK_STYLE_KEY = "clock_style";

    private static final String CLOCK_STYLE = "system:" + CLOCK_STYLE_KEY;

    private ThemeUtils mThemeUtils;

    private Context mContext;
    private View[] clockViews;
    private int mClockStyle;

    private static final long UPDATE_INTERVAL_MILLIS = 15 * 1000;
    private final Handler mHandler;
    private long lastUpdateTimeMillis = 0;

    public ClockStyle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(context.getMainLooper());
        mThemeUtils = new ThemeUtils(context);
        Dependency.get(TunerService.class).addTunable(this, CLOCK_STYLE);
    }

    private void enableClockOverlays(boolean enable) {
        boolean isCenterClock = mClockStyle == 2 || mClockStyle == 4 || mClockStyle == 5 || mClockStyle == 9 || mClockStyle == 10 || mClockStyle == 11 || mClockStyle == 12 || mClockStyle == 15 || mClockStyle == 17 || mClockStyle == 18 || mClockStyle == 19;
        mThemeUtils.setOverlayEnabled("android.theme.customization.smartspace", enable ? "com.android.systemui.hide.smartspace" : "com.android.systemui", "com.android.systemui");
        mThemeUtils.setOverlayEnabled("android.theme.customization.smartspace_offset", enable && isCenterClock ? "com.android.systemui.smartspace_offset.smartspace" : "com.android.systemui", "com.android.systemui");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHandler.post(() -> {
            clockViews = new View[CLOCK_VIEW_IDS.length];
            for (int i = 0; i < CLOCK_VIEW_IDS.length; i++) {
                if (CLOCK_VIEW_IDS[i] != 0) {
                    clockViews[i] = findViewById(CLOCK_VIEW_IDS[i]);
                } else {
                    clockViews[i] = null;
                }
            }
            updateClockView();
        });
    }

    private void updateTextClockViews(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            mHandler.post(() -> {
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View childView = viewGroup.getChildAt(i);
                    updateTextClockViews(childView);
                    if (childView instanceof TextClock) {
                        ((TextClock) childView).refreshTime();
                    }
                }
            });
        }
    }

    public void onTimeChanged() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastUpdateTimeMillis >= UPDATE_INTERVAL_MILLIS) {
            if (clockViews != null) {
                mHandler.post(() -> {
                    for (View clockView : clockViews) {
                        updateTextClockViews(clockView);
                        lastUpdateTimeMillis = currentTimeMillis;
                    }
                });
            }
        }
    }

    private void updateClockView() {
        if (clockViews != null) {
            mHandler.post(() -> {
                for (int i = 0; i < clockViews.length; i++) {
                    if (clockViews[i] != null) {
                        int visibility = (i == mClockStyle) ? View.VISIBLE : View.GONE;
                        if (clockViews[i].getVisibility() != visibility) {
                            clockViews[i].setVisibility(visibility);
                        }
                    }
                }
            });
        }
    }
    
    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case CLOCK_STYLE:
                mClockStyle = TunerService.parseInteger(newValue, 0);
                updateClockView();
                enableClockOverlays(mClockStyle != 0);
                break;
            default:
                break;
        }
    }
}
