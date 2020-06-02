/*
 * Copyright (C) 2014-2015 The MoKee OpenSource Project
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

package com.android.systemui.evolution.carrierlabel;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.internal.util.evolution.EvolutionUtils;
import com.android.internal.telephony.TelephonyIntents;

import com.android.systemui.Dependency;
import com.android.systemui.evolution.carrierlabel.SpnOverride;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.systemui.R;

public class CarrierLabel extends TextView implements DarkReceiver {

    private Context mContext;
    private boolean mAttached;
    private static boolean isCN;
    private int mCarrierFontSize = 11;
    private int mCarrierColor = 0xffffffff;
    private int mTintColor = Color.WHITE;

    private int mCarrierLabelFontStyle = GOOGLESANS;
    public static final int FONT_NORMAL = 0;
    public static final int FONT_ITALIC = 1;
    public static final int FONT_BOLD = 2;
    public static final int FONT_BOLD_ITALIC = 3;
    public static final int FONT_LIGHT = 4;
    public static final int FONT_LIGHT_ITALIC = 5;
    public static final int FONT_THIN = 6;
    public static final int FONT_THIN_ITALIC = 7;
    public static final int FONT_CONDENSED = 8;
    public static final int FONT_CONDENSED_ITALIC = 9;
    public static final int FONT_CONDENSED_BOLD = 10;
    public static final int FONT_CONDENSED_BOLD_ITALIC = 11;
    public static final int FONT_CONDENSED_LIGHT = 12;
    public static final int FONT_CONDENSED_LIGHT_ITALIC = 13;
    public static final int FONT_MEDIUM = 14;
    public static final int FONT_MEDIUM_ITALIC = 15;
    public static final int FONT_BLACK = 16;
    public static final int FONT_BLACK_ITALIC = 17;
    public static final int FONT_DANCINGSCRIPT = 18;
    public static final int FONT_DANCINGSCRIPT_BOLD = 19;
    public static final int FONT_COMINGSOON = 20;
    public static final int FONT_NOTOSERIF = 21;
    public static final int FONT_NOTOSERIF_ITALIC = 22;
    public static final int FONT_NOTOSERIF_BOLD = 23;
    public static final int FONT_NOTOSERIF_BOLD_ITALIC = 24;
    public static final int GOBOLD_LIGHT = 25;
    public static final int ROADRAGE = 26;
    public static final int SNOWSTORM = 27;
    public static final int GOOGLESANS = 28;
    public static final int NEONEON = 29;
    public static final int THEMEABLE = 30;
    public static final int SAMSUNG = 31;
    public static final int MEXCELLENT = 32;
    public static final int BURNSTOWN = 33;
    public static final int DUMBLEDOR = 34;
    public static final int PHANTOMBOLD = 35;
    public static final int SOURCESANSPRO = 36;
    public static final int CIRCULARSTD = 37;
    public static final int ONEPLUSSLATE = 38;
    public static final int ACLONICA = 39;
    public static final int AMARANTE = 40;
    public static final int BARIOL = 41;
    public static final int CAGLIOSTRO = 42;
    public static final int COOLSTORY = 43;
    public static final int LGSMARTGOTHIC = 44;
    public static final int ROSEMARY = 45;
    public static final int SONYSKETCH = 46;
    public static final int SURFER = 47;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_COLOR), false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_FONT_SIZE), false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_FONT_STYLE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
             updateColor();
             updateSize();
             updateStyle();
        }
    }

    public CarrierLabel(Context context) {
        this(context, null);
    }

    public CarrierLabel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        updateNetworkName(true, null, false, null);
        /* Force carrier label to the lockscreen. This helps us avoid
        the carrier label on the statusbar if for whatever reason
        the user changes notch overlays */
        if (EvolutionUtils.hasNotch(mContext)) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_SHOW_CARRIER, 1);
        }
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        updateColor();
        updateSize();
        updateStyle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
            filter.addAction(Intent.ACTION_CUSTOM_CARRIER_LABEL_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mCarrierColor == 0xFFFFFFFF) {
            setTextColor(mTintColor);
        } else {
            setTextColor(mCarrierColor);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)
                    || Intent.ACTION_CUSTOM_CARRIER_LABEL_CHANGED.equals(action)) {
                        updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, true),
                        intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                        intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
                isCN = EvolutionUtils.isChineseLanguage();
            }
        }
    };

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        final String str;
        final boolean plmnValid = showPlmn && !TextUtils.isEmpty(plmn);
        final boolean spnValid = showSpn && !TextUtils.isEmpty(spn);
        if (spnValid) {
            str = spn;
        } else if (plmnValid) {
            str = plmn;
        } else {
            str = "";
        }
        String customCarrierLabel = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.CUSTOM_CARRIER_LABEL, UserHandle.USER_CURRENT);
        if (!TextUtils.isEmpty(customCarrierLabel)) {
            setText(customCarrierLabel);
        } else {
            setText(TextUtils.isEmpty(str) ? getOperatorName() : str);
        }
    }

    private String getOperatorName() {
        String operatorName = getContext().getString(R.string.quick_settings_wifi_no_network);
        TelephonyManager telephonyManager = (TelephonyManager) getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        if (isCN) {
            String operator = telephonyManager.getNetworkOperator();
            if (TextUtils.isEmpty(operator)) {
                operator = telephonyManager.getSimOperator();
            }
            SpnOverride mSpnOverride = new SpnOverride();
            operatorName = mSpnOverride.getSpn(operator);
        } else {
            operatorName = telephonyManager.getNetworkOperatorName();
        }
        if (TextUtils.isEmpty(operatorName)) {
            operatorName = telephonyManager.getSimOperatorName();
        }
        return operatorName;
    }

    public void getFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.ITALIC));
                break;
            case FONT_BOLD:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                setTypeface(Typeface.create("sans-serif-light",
                    Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-light",
                    Typeface.ITALIC));
                break;
            case FONT_THIN:
                setTypeface(Typeface.create("sans-serif-thin",
                    Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                setTypeface(Typeface.create("sans-serif-thin",
                    Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                setTypeface(Typeface.create("sans-serif-condensed-light",
                    Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed-light",
                    Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                setTypeface(Typeface.create("sans-serif-medium",
                    Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                setTypeface(Typeface.create("sans-serif-medium",
                    Typeface.ITALIC));
                break;
            case FONT_BLACK:
                setTypeface(Typeface.create("sans-serif-black",
                    Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                setTypeface(Typeface.create("sans-serif-black",
                    Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                setTypeface(Typeface.create("cursive",
                    Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                setTypeface(Typeface.create("cursive",
                    Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                setTypeface(Typeface.create("casual",
                    Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                setTypeface(Typeface.create("serif",
                    Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                setTypeface(Typeface.create("serif",
                    Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                setTypeface(Typeface.create("serif",
                    Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                setTypeface(Typeface.create("serif",
                    Typeface.BOLD_ITALIC));
                break;
            case GOBOLD_LIGHT:
                setTypeface(Typeface.create("gobold-light-sys",
                    Typeface.NORMAL));
                break;
            case ROADRAGE:
                setTypeface(Typeface.create("roadrage-sys",
		   Typeface.NORMAL));
                break;
            case SNOWSTORM:
                setTypeface(Typeface.create("snowstorm-sys",
                    Typeface.NORMAL));
                break;
            case GOOGLESANS:
            default:
                setTypeface(Typeface.create("googlesans-sys",
                    Typeface.NORMAL));
                break;
            case NEONEON:
                setTypeface(Typeface.create("neoneon-sys",
                    Typeface.NORMAL));
                break;
            case THEMEABLE:
                setTypeface(Typeface.create("themeable-sys",
                    Typeface.NORMAL));
                break;
            case SAMSUNG:
                setTypeface(Typeface.create("samsung-sys",
                    Typeface.NORMAL));
                break;
            case MEXCELLENT:
                setTypeface(Typeface.create("mexcellent-sys",
                    Typeface.NORMAL));
                break;
            case BURNSTOWN:
                setTypeface(Typeface.create("burnstown-sys",
                    Typeface.NORMAL));
                break;
            case DUMBLEDOR:
                setTypeface(Typeface.create("dumbledor-sys",
                    Typeface.NORMAL));
                break;
            case PHANTOMBOLD:
                setTypeface(Typeface.create("phantombold-sys",
                    Typeface.NORMAL));
                break;
            case SOURCESANSPRO:
                setTypeface(Typeface.create("sourcesanspro-sys",
                    Typeface.NORMAL));
                break;
            case CIRCULARSTD:
                setTypeface(Typeface.create("circularstd-sys",
                    Typeface.NORMAL));
                break;
            case ONEPLUSSLATE:
                setTypeface(Typeface.create("oneplusslate-sys",
                    Typeface.NORMAL));
                break;
            case ACLONICA:
                setTypeface(Typeface.create("aclonica-sys",
                    Typeface.NORMAL));
                break;
            case AMARANTE:
                setTypeface(Typeface.create("amarante-sys",
                    Typeface.NORMAL));
                break;
            case BARIOL:
                setTypeface(Typeface.create("bariol-sys",
                    Typeface.NORMAL));
                break;
            case CAGLIOSTRO:
                setTypeface(Typeface.create("cagliostro-sys",
                    Typeface.NORMAL));
                break;
            case COOLSTORY:
                setTypeface(Typeface.create("coolstory-sys",
                    Typeface.NORMAL));
                break;
            case LGSMARTGOTHIC:
                setTypeface(Typeface.create("lgsmartgothic-sys",
                    Typeface.NORMAL));
                break;
            case ROSEMARY:
                setTypeface(Typeface.create("rosemary-sys",
                    Typeface.NORMAL));
                break;
            case SONYSKETCH:
                setTypeface(Typeface.create("sonysketch-sys",
                    Typeface.NORMAL));
                break;
            case SURFER:
                setTypeface(Typeface.create("surfer-sys",
                    Typeface.NORMAL));
                break;
        }
    }

    private void updateColor() {
        mCarrierColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_COLOR, 0xffffffff);
        if (mCarrierColor == 0xFFFFFFFF) {
            setTextColor(mTintColor);
        } else {
            setTextColor(mCarrierColor);
        }
    }

    private void updateSize() {
        mCarrierFontSize = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_SIZE, 11);
        setTextSize(mCarrierFontSize);
    }

    private void updateStyle() {
        mCarrierLabelFontStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_STYLE, GOOGLESANS);
        getFontStyle(mCarrierLabelFontStyle);
    }
}
