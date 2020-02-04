/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.systemui.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class CustomTextClock extends TextView {

    private String mDescFormat;
    private final String[] mHours;
    private final String[] mMinutes;
    private final Resources mResources;
    private final Calendar mTime = Calendar.getInstance(TimeZone.getDefault());
    private TimeZone mTimeZone;

    private int mAccentColor;
    private int mClockSize = 54;
    private SettingsObserver mSettingsObserver;

    private final BroadcastReceiver mTimeZoneChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                onTimeZoneChanged(TimeZone.getTimeZone(tz));
                onTimeChanged();
            }
        }
    };

    public CustomTextClock(Context context) {
        this(context, null);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);
        mDescFormat = ((SimpleDateFormat) DateFormat.getTimeFormat(context)).toLocalizedPattern();
        mResources = context.getResources();
        mHours = mResources.getStringArray(R.array.type_clock_hours);
        mMinutes = mResources.getStringArray(R.array.type_clock_minutes);
        mAccentColor = mResources.getColor(R.color.accent_device_default_light);
    }

    public void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setContentDescription(DateFormat.format(mDescFormat, mTime));
        int hours = mTime.get(Calendar.HOUR) % 12;
        int minutes = mTime.get(Calendar.MINUTE) % 60;
        SpannedString rawFormat = (SpannedString) mResources.getQuantityText(R.plurals.type_clock_header, hours);
        Annotation[] annotationArr = (Annotation[]) rawFormat.getSpans(0, rawFormat.length(), Annotation.class);
        SpannableString colored = new SpannableString(rawFormat);
        for (Annotation annotation : annotationArr) {
            if ("color".equals(annotation.getValue())) {
                colored.setSpan(new ForegroundColorSpan(mAccentColor),
                        colored.getSpanStart(annotation),
                        colored.getSpanEnd(annotation),
                        Spanned.SPAN_POINT_POINT);
            }
        }
        setText(TextUtils.expandTemplate(colored, new CharSequence[]{mHours[hours], mMinutes[minutes]}));
    }

    public void onTimeZoneChanged(TimeZone timeZone) {
        mTimeZone = timeZone;
        mTime.setTimeZone(timeZone);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Calendar calendar = mTime;
        TimeZone timeZone = mTimeZone;
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        calendar.setTimeZone(timeZone);
        onTimeChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(mTimeZoneChangedReceiver, filter);

        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
        }
        mSettingsObserver.observe();
        updateClockSize();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mTimeZoneChangedReceiver);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        refreshLockFont();
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 28);
    }

    private void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() :28;
        switch (lockClockFont) {
            case 0:
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case 1:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case 2:
                setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case 3:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case 4:
                setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case 5:
                setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case 6:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case 7:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case 8:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case 9:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case 10:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case 11:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case 12:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case 13:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case 14:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case 15:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case 16:
                setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case 17:
                setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case 18:
                setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case 19:
                setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case 20:
                setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case 21:
                setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case 22:
                setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case 23:
                setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case 24:
                setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
            case 25:
                setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                break;
            case 26:
                setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
                break;
            case 27:
                setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                break;
            case 28:
                setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                break;
            case 29:
                setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                break;
            case 30:
                setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
                break;
            case 31:
                setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                break;
            case 32:
                setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                break;
            case 33:
                setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                break;
            case 34:
                setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                break;
            case 35:
                setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                break;
            case 36:
                setTypeface(Typeface.create("sourcesanspro-sys", Typeface.NORMAL));
                break;
            case 37:
                setTypeface(Typeface.create("circularstd-sys", Typeface.NORMAL));
                break;
            case 38:
                setTypeface(Typeface.create("oneplusslate-sys", Typeface.NORMAL));
                break;
            case 39:
                setTypeface(Typeface.create("aclonica-sys", Typeface.NORMAL));
                break;
            case 40:
                setTypeface(Typeface.create("amarante-sys", Typeface.NORMAL));
                break;
            case 41:
                setTypeface(Typeface.create("bariol-sys", Typeface.NORMAL));
                break;
            case 42:
                setTypeface(Typeface.create("cagliostro-sys", Typeface.NORMAL));
                break;
            case 43:
                setTypeface(Typeface.create("coolstory-sys", Typeface.NORMAL));
                break;
            case 44:
                setTypeface(Typeface.create("lgsmartgothic-sys", Typeface.NORMAL));
                break;
            case 45:
                setTypeface(Typeface.create("rosemary-sys", Typeface.NORMAL));
                break;
            case 46:
                setTypeface(Typeface.create("sonysketch-sys", Typeface.NORMAL));
                break;
            case 47:
                setTypeface(Typeface.create("surfer-sys", Typeface.NORMAL));
                break;
        }
    }

    private void updateTextSize(int lockClockSize) {
        if (lockClockSize == 10) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9));
        } else if (lockClockSize == 11) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_2));
        } else if (lockClockSize == 12) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_4));
        } else if (lockClockSize == 13) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_6));
        } else if (lockClockSize == 14) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_9_8));
        } else if (lockClockSize == 15) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
        } else if (lockClockSize == 16) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_2));
        } else if (lockClockSize == 17) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_4));
        } else if (lockClockSize == 18) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_6));
        } else if (lockClockSize == 19) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10_8));
        } else if (lockClockSize == 20) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
        } else if (lockClockSize == 21) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_2));
        } else if (lockClockSize == 22) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_4));
        } else if (lockClockSize == 23) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_6));
        } else if (lockClockSize == 24) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11_8));
        } else if (lockClockSize == 25) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
        } else if (lockClockSize == 26) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_2));
        } else if (lockClockSize == 27) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_4));
        } else if (lockClockSize == 28) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_6));
        } else if (lockClockSize == 29) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12_8));
        } else if (lockClockSize == 30) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
        } else if (lockClockSize == 31) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_2));
        } else if (lockClockSize == 32) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_4));
        } else if (lockClockSize == 33) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_6));
        } else if (lockClockSize == 34) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13_8));
        } else if (lockClockSize == 35) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
        } else if (lockClockSize == 36) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_2));
        } else if (lockClockSize == 37) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_4));
        } else if (lockClockSize == 38) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_6));
        } else if (lockClockSize == 39) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14_8));
        } else if (lockClockSize == 40) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
        } else if (lockClockSize == 41) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_2));
        } else if (lockClockSize == 42) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_4));
        } else if (lockClockSize == 43) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_6));
        } else if (lockClockSize == 44) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15_8));
        } else if (lockClockSize == 45) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
        } else if (lockClockSize == 46) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_2));
        } else if (lockClockSize == 47) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_4));
        } else if (lockClockSize == 48) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_6));
        } else if (lockClockSize == 49) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16_8));
        } else if (lockClockSize == 50) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
        } else if (lockClockSize == 51) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17_2));
        } else if (lockClockSize == 52) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17_4));
        } else if (lockClockSize == 53) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17_8));
        } else if (lockClockSize == 54) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
        } else if (lockClockSize == 55) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18_3));
        } else if (lockClockSize == 56) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18_6));
        } else if (lockClockSize == 57) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
        } else if (lockClockSize == 58) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19_3));
        } else if (lockClockSize == 59) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19_6));
        } else if (lockClockSize == 60) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
        } else if (lockClockSize == 61) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20_3));
        } else if (lockClockSize == 62) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20_6));
        } else if (lockClockSize == 63) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
        } else if (lockClockSize == 64) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21_3));
        } else if (lockClockSize == 65) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21_6));
        } else if (lockClockSize == 66) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
        } else if (lockClockSize == 67) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22_3));
        } else if (lockClockSize == 68) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22_6));
        } else if (lockClockSize == 69) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
        } else if (lockClockSize == 70) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23_3));
        } else if (lockClockSize == 71) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23_6));
        } else if (lockClockSize == 72) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
        } else if (lockClockSize == 73) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24_3));
        } else if (lockClockSize == 74) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24_6));
        } else if (lockClockSize == 75) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
        } else if (lockClockSize == 76) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25_3));
        } else if (lockClockSize == 77) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25_6));
        } else if (lockClockSize == 78) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26));
        } else if (lockClockSize == 79) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26_3));
        } else if (lockClockSize == 80) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26_6));
        } else if (lockClockSize == 81) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27));
        } else if (lockClockSize == 82) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27_3));
        } else if (lockClockSize == 83) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27_6));
        } else if (lockClockSize == 84) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28));
        } else if (lockClockSize == 85) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28_3));
        } else if (lockClockSize == 86) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28_6));
        } else if (lockClockSize == 87) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29));
        } else if (lockClockSize == 88) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29_3));
        } else if (lockClockSize == 89) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29_6));
        } else if (lockClockSize == 90) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30));
        } else if (lockClockSize == 91) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30_3));
        } else if (lockClockSize == 92) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30_6));
        } else if (lockClockSize == 93) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31));
        } else if (lockClockSize == 94) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31_3));
        } else if (lockClockSize == 95) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31_6));
        } else if (lockClockSize == 96) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32));
        } else if (lockClockSize == 97) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32_3));
        } else if (lockClockSize == 98) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32_6));
        } else if (lockClockSize == 99) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33));
        } else if (lockClockSize == 100) {
            setTextSize(getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33_3));
        }
    }

    public void updateClockSize() {
        mClockSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 54,
                UserHandle.USER_CURRENT);
        updateTextSize(mClockSize);
        onTimeChanged();
    }

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKCLOCK_FONT_SIZE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateClockSize();
        }
    }
}
