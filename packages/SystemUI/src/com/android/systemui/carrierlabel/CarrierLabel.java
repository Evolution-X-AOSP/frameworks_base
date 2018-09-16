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

package com.android.systemui.carrierlabel;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
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
import com.android.systemui.carrierlabel.SpnOverride;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.DarkIconDispatcher.DarkReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.systemui.R;

public class CarrierLabel extends TextView implements DarkReceiver {

    private Context mContext;
    private boolean mAttached;
    private static boolean isCN;
    private int mCarrierFontSize = 14;

    private int mCarrierLabelFontStyle = FONT_NORMAL;
    public static final int FONT_NORMAL = 0;
    public static final int FONT_BOLD = 1;
    public static final int FONT_ITALIC = 2;
    public static final int FONT_BOLD_ITALIC = 3;
    public static final int FONT_LIGHT = 4;
    public static final int FONT_LIGHT_ITALIC = 5;
    public static final int FONT_CONDENSED = 6;
    public static final int FONT_CONDENSED_ITALIC = 7;
    public static final int FONT_CONDENSED_BOLD = 8;
    public static final int FONT_CONDENSED_BOLD_ITALIC = 9;
    public static final int FONT_MEDIUM = 10;
    public static final int FONT_MEDIUM_ITALIC = 11;
    public static final int FONT_ABELREG = 12;
    public static final int FONT_ADAMCG = 13;
    public static final int FONT_ADVENTPRO = 14;
    public static final int FONT_ALIEN = 15;
    public static final int FONT_ARCHIVONAR = 16;
    public static final int FONT_AUTOURONE = 17;
    public static final int FONT_BADSCRIPT = 18;
    public static final int FONT_BIGNOODLE = 19;
    public static final int FONT_BIKO = 20;
    public static final int FONT_CHERRYSWASH = 21;
    public static final int FONT_GINORA = 22;
    public static final int FONT_GOOGLESANS = 23;
    public static final int FONT_IBMPLEX = 24;
    public static final int FONT_INKFERNO = 25;
    public static final int FONT_INSTRUCTION = 26;
    public static final int FONT_JACK = 27;
    public static final int FONT_KELLYSLAB = 28;
    public static final int FONT_MONAD = 29;
    public static final int FONT_NOIR = 30;
    public static final int FONT_OUTRUN = 31;
    public static final int FONT_POMPIERE = 32;
    public static final int FONT_REEMKUFI = 33;
    public static final int FONT_RIVIERA = 34;
    public static final int FONT_SOURCESANSPRO = 35;
    public static final int FONT_OUTBOX = 36;
    public static final int FONT_THEMEABLE = 37;
    public static final int FONT_VIBUR = 38;
    public static final int FONT_VOLTAIRE = 39;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_FONT_SIZE), false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_CARRIER_FONT_STYLE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
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
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
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
        setTextColor(DarkIconDispatcher.getTint(area, this, tint));
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
            default:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.NORMAL));
                break;
            case FONT_BOLD:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.BOLD));
                break;
            case FONT_ITALIC:
                setTypeface(Typeface.create("sans-serif",
                    Typeface.ITALIC));
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
            case FONT_CONDENSED:
                setTypeface(Typeface.create("sans-serif-condensed",
                    Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed",
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
            case FONT_ABELREG:
                setTypeface(Typeface.create("abelreg",
                    Typeface.NORMAL));
                break;
            case FONT_ADAMCG:
                setTypeface(Typeface.create("adamcg-pro",
                    Typeface.NORMAL));
                break;
            case FONT_ADVENTPRO:
                setTypeface(Typeface.create("adventpro",
                    Typeface.NORMAL));
                break;
            case FONT_ALIEN:
                setTypeface(Typeface.create("alien-league",
                    Typeface.NORMAL));
                break;
            case FONT_ARCHIVONAR:
                setTypeface(Typeface.create("archivonar",
                    Typeface.NORMAL));
                break;
            case FONT_AUTOURONE:
                setTypeface(Typeface.create("autourone",
                    Typeface.NORMAL));
                break;
            case FONT_BADSCRIPT:
                setTypeface(Typeface.create("badscript",
                    Typeface.NORMAL));
                break;
            case FONT_BIGNOODLE:
                setTypeface(Typeface.create("bignoodle-regular",
                    Typeface.NORMAL));
                break;
            case FONT_BIKO:
                setTypeface(Typeface.create("biko",
                    Typeface.NORMAL));
                break;
            case FONT_CHERRYSWASH:
                setTypeface(Typeface.create("cherryswash",
                    Typeface.NORMAL));
                break;
            case FONT_GINORA:
                setTypeface(Typeface.create("ginora-sans",
                    Typeface.NORMAL));
                break;
            case FONT_GOOGLESANS:
                setTypeface(Typeface.create("google-sans-medium",
                    Typeface.NORMAL));
                break;
            case FONT_IBMPLEX:
                setTypeface(Typeface.create("ibmplex-mono",
                    Typeface.NORMAL));
                break;
            case FONT_INKFERNO:
                setTypeface(Typeface.create("inkferno",
                    Typeface.NORMAL));
                break;
            case FONT_INSTRUCTION:
                setTypeface(Typeface.create("instruction",
                    Typeface.NORMAL));
                break;
            case FONT_JACK:
                setTypeface(Typeface.create("jack-lane",
                    Typeface.NORMAL));
                break;
            case FONT_KELLYSLAB:
                setTypeface(Typeface.create("kellyslab",
                    Typeface.NORMAL));
                break;
            case FONT_MONAD:
                setTypeface(Typeface.create("monad",
                    Typeface.NORMAL));
                break;
            case FONT_NOIR:
                setTypeface(Typeface.create("noir",
                    Typeface.NORMAL));
                break;
            case FONT_OUTRUN:
                setTypeface(Typeface.create("outrun-future",
                    Typeface.NORMAL));
                break;
            case FONT_POMPIERE:
                setTypeface(Typeface.create("pompiere",
                    Typeface.NORMAL));
                break;
            case FONT_REEMKUFI:
                setTypeface(Typeface.create("reemkufi",
                    Typeface.NORMAL));
                break;
            case FONT_RIVIERA:
                setTypeface(Typeface.create("riviera",
                    Typeface.NORMAL));
                break;
            case FONT_SOURCESANSPRO:
                setTypeface(Typeface.create("source-sans-pro",
                    Typeface.NORMAL));
                break;
            case FONT_OUTBOX:
                setTypeface(Typeface.create("the-outbox",
                    Typeface.NORMAL));
                break;
            case FONT_THEMEABLE:
                setTypeface(Typeface.create("themeable-date",
                    Typeface.NORMAL));
                break;
            case FONT_VIBUR:
                setTypeface(Typeface.create("vibur",
                    Typeface.NORMAL));
                break;
            case FONT_VOLTAIRE:
                setTypeface(Typeface.create("voltaire",
                    Typeface.NORMAL));
                break;
        }
    }

    private void updateSize() {
        mCarrierFontSize = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_SIZE, 14);
        setTextSize(mCarrierFontSize);
    }

    private void updateStyle() {
        mCarrierLabelFontStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CARRIER_FONT_STYLE, FONT_NORMAL);
        getFontStyle(mCarrierLabelFontStyle);
    }
}
