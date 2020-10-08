/*
 * Copyright (C) 2008 The Android Open Source Project
 *               2020 The exTHmUI Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.os.Handler;
import android.service.notification.StatusBarNotification;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.View;
import android.widget.ImageSwitcher;
import android.widget.TextSwitcher;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.ContrastColorUtil;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.StatusBarIconView;

import java.util.ArrayList;

public abstract class LyricTicker implements DarkReceiver {

    private Context mContext;
    private ImageSwitcher mIconSwitcher;
    private TextSwitcher mTextSwitcher;
    private int mIconTint =  0xffffffff;
    private int mTextColor = 0xffffffff;

    private CharSequence mCurrentText;
    private StatusBarNotification mCurrentNotification;

    private Drawable icon;

    private ContrastColorUtil mNotificationColorUtil;

    private Animation mAnimationIn;
    private Animation mAnimationOut;

    public LyricTicker(Context context, View tickerLayout) {
        mContext = context;

        updateAnimation();

        mNotificationColorUtil = ContrastColorUtil.getInstance(mContext);

        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
    }

    public void updateAnimation() {
        mAnimationIn = AnimationUtils.loadAnimation(mContext,
                com.android.internal.R.anim.push_up_in);
        mAnimationOut = AnimationUtils.loadAnimation(mContext,
                com.android.internal.R.anim.push_up_out);
        
        if (mTextSwitcher != null && mIconSwitcher != null) {
            setViewAnimations();
        }
    }

    private boolean isNotificationEquals(StatusBarNotification a, StatusBarNotification b) {
        return a != null && b != null && TextUtils.equals(a.getPackageName(), b.getPackageName()) && a.getId() == b.getId();
    }

    public void updateNotification(StatusBarNotification n) {

        Notification notification = n.getNotification();

        boolean isLyric = ((notification.flags & Notification.FLAG_ALWAYS_SHOW_TICKER) != 0)
                        && ((notification.flags & Notification.FLAG_ONLY_UPDATE_TICKER) != 0);

        if (!isLyric) {
            if (isNotificationEquals(n, mCurrentNotification)) {
                mCurrentNotification = null;
                stopTicker();
            }
            return;
        }

        mCurrentText = notification.tickerText;

        if (!isNotificationEquals(mCurrentNotification, n) || notification.extras.getBoolean("ticker_icon_switch", false)) {
            mCurrentNotification = n;
            int iconId = notification.extras.getInt("ticker_icon", notification.icon);
            icon = StatusBarIconView.getIcon(mContext, 
                new StatusBarIcon(n.getPackageName(), n.getUser(), iconId, notification.iconLevel, 0,
                    notification.tickerText));

            mIconSwitcher.setAnimateFirstView(false);
            mIconSwitcher.reset();
            setAppIconColor(icon);

            mTextSwitcher.setAnimateFirstView(false);
            mTextSwitcher.reset();
            mTextSwitcher.setText(mCurrentText);
            mTextSwitcher.setTextColor(mTextColor);

            tickerStarting();
        } else {
            mTextSwitcher.setText(mCurrentText);
            mTextSwitcher.setTextColor(mTextColor);
        }

    }

    public void removeEntry(StatusBarNotification n) {
        if (isNotificationEquals(n, mCurrentNotification)) {
            stopTicker();
        }
    }

    public void halt() {
        tickerHalting();
        mCurrentNotification = null;
    }

    private void stopTicker() {
        tickerDone();
        mCurrentNotification = null;
    }

    public void setViews(TextSwitcher ts, ImageSwitcher is) {
        mTextSwitcher = ts;
        mIconSwitcher = is;

        setViewAnimations();
    }

    private void setViewAnimations() {
        if (mIconSwitcher == null || mTextSwitcher == null) return;
        mTextSwitcher.setInAnimation(mAnimationIn);
        mTextSwitcher.setOutAnimation(mAnimationOut);
        mIconSwitcher.setInAnimation(mAnimationIn);
        mIconSwitcher.setOutAnimation(mAnimationOut);
    }

    public void reflowText() {
        mTextSwitcher.setCurrentText(mCurrentText);
        mTextSwitcher.setTextColor(mTextColor);
    }

    public abstract void tickerStarting();
    public abstract void tickerDone();
    public abstract void tickerHalting();

    public void setTextColor(int color) {
        mTextColor = color;
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {}

    public void applyDarkIntensity(Rect area, View v, int tint) {
        mTextColor = DarkIconDispatcher.getTint(area, v, tint);
        mIconTint = mTextColor;
        if (mCurrentNotification == null) return;
        if (mTextSwitcher != null) mTextSwitcher.setTextColor(mTextColor);
        if (mIconSwitcher != null) {
            mIconSwitcher.reset();
            setAppIconColor(icon);
        }
    }

    private void setAppIconColor(Drawable icon) {
        boolean isGrayscale = mNotificationColorUtil.isGrayscaleIcon(icon);
        mIconSwitcher.setImageDrawableTint(icon, mIconTint, isGrayscale);
    }
}
