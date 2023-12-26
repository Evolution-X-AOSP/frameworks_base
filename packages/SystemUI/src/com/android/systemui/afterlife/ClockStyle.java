package com.android.systemui.afterlife;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
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
            R.id.keyguard_clock_style_custom3
    };

    private static final int DEFAULT_STYLE = 0; //Disabled
    private static final String CLOCK_STYLE_KEY = "clock_style";
    
    private static final String CLOCK_STYLE =
            "system:" + CLOCK_STYLE_KEY;

    private ThemeUtils mThemeUtils;

    private Context mContext;
    private View[] clockViews;
    private int mClockStyle;

    public ClockStyle(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mThemeUtils = new ThemeUtils(context);
        Dependency.get(TunerService.class).addTunable(this, CLOCK_STYLE);
    }

    private void enableClockOverlays(boolean enable) {
        boolean isCenterClock = mClockStyle == 2 || mClockStyle == 4 || mClockStyle == 5 || mClockStyle == 6;
        mThemeUtils.setOverlayEnabled("android.theme.customization.smartspace", enable ? "com.android.systemui.hide.smartspace" : "com.android.systemui", "com.android.systemui");
        mThemeUtils.setOverlayEnabled("android.theme.customization.smartspace_offset", enable && isCenterClock ? "com.android.systemui.smartspace_offset.smartspace" : "com.android.systemui", "com.android.systemui");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        clockViews = new View[CLOCK_VIEW_IDS.length];
        for (int i = 0; i < CLOCK_VIEW_IDS.length; i++) {
            if (CLOCK_VIEW_IDS[i] != 0) {
                clockViews[i] = findViewById(CLOCK_VIEW_IDS[i]);
            } else {
                clockViews[i] = null;
            }
        }
        updateClockView();
    }

    private void updateClockView() {
        if (clockViews != null) {
            for (int i = 0; i < clockViews.length; i++) {
                if (clockViews[i] != null) {
                    clockViews[i].setVisibility(i == mClockStyle ? View.VISIBLE : View.GONE);
                }
            }
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
