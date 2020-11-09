/*
* Copyright (C) 2019 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.evolution;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.android.settingslib.Utils;
import com.android.systemui.R;

public class NotificationLightsView extends RelativeLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";
    private static final String CANCEL_NOTIFICATION_PULSE_ACTION = "cancel_notification_pulse";
    private ValueAnimator mLightAnimator;

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void stopAnimateNotification() {
        if (mLightAnimator != null) {
            mLightAnimator.end();
            mLightAnimator = null;
        }
    }

    public void animateNotification() {
        animateNotificationWithColor(getNotificationLightsColor());
    }

    public int getNotificationLightsColor() {
        int color = getDefaultNotificationLightsColor();
        boolean useAccent = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_PULSE_ACCENT,
                0, UserHandle.USER_CURRENT) != 0;
        if (useAccent) {
            color = Utils.getColorAccentDefaultColor(getContext());
        }
        return color;
    }

    public int getDefaultNotificationLightsColor() {
        int defaultColor = getResources().getInteger(
                com.android.internal.R.integer.config_ambientNotificationDefaultColor);
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NOTIFICATION_PULSE_COLOR, defaultColor,
                    UserHandle.USER_CURRENT);
    }

    public void animateNotificationWithColor(int color) {
        ContentResolver resolver = mContext.getContentResolver();
        int duration = Settings.System.getIntForUser(resolver,
                Settings.System.AMBIENT_LIGHT_DURATION, 2,
                UserHandle.USER_CURRENT) * 1000;
        int repeats = Settings.System.getIntForUser(resolver,
                Settings.System.AMBIENT_LIGHT_REPEAT_COUNT, 0,
                UserHandle.USER_CURRENT);
        ImageView leftView = (ImageView) findViewById(R.id.notification_animation_left);
        ImageView rightView = (ImageView) findViewById(R.id.notification_animation_right);
        leftView.setColorFilter(color);
        rightView.setColorFilter(color);
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimator.setDuration(duration);
        mLightAnimator.setRepeatCount(repeats == 0 ? ValueAnimator.INFINITE : repeats);
        mLightAnimator.setRepeatMode(ValueAnimator.RESTART);
        if (repeats != 0) {
            mLightAnimator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationCancel(Animator animation) { /* do nothing */ }
                @Override
                public void onAnimationRepeat(Animator animation) { /* do nothing */ }
                @Override
                public void onAnimationStart(Animator animation) { /* do nothing */ }
                @Override
                public void onAnimationEnd(Animator animation) {
                    Settings.System.putIntForUser(resolver,
                            Settings.System.AOD_NOTIFICATION_PULSE_ACTIVATED, 0,
                            UserHandle.USER_CURRENT);
                    Settings.System.putIntForUser(resolver,
                            Settings.System.AOD_NOTIFICATION_PULSE_TRIGGER, 0,
                            UserHandle.USER_CURRENT);
                }
            });
        }
        mLightAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (DEBUG) Log.d(TAG, "onAnimationUpdate");
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                leftView.setScaleY(progress);
                rightView.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                leftView.setAlpha(alpha);
                rightView.setAlpha(alpha);
            }
        });
        if (DEBUG) Log.d(TAG, "start");
        mLightAnimator.start();
    }
}
