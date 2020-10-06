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
import android.util.Log;
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
    private TextPaint mPaint;
    private ImageSwitcher mIconSwitcher;
    private TextSwitcher mTextSwitcher;
    private float mIconScale;
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
        final Resources res = context.getResources();
        final int outerBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_size);
        final int imageBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_drawing_size);
        mIconScale = (float)imageBounds / (float)outerBounds;

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
                tickerDone();
            }
            return;
        }

        final CharSequence text = notification.tickerText;
        if (text == null) {
            tickerDone();
            return;
        }
        mCurrentText = text;

        mTextSwitcher.setText(mCurrentText);
        mTextSwitcher.setTextColor(mTextColor);

        if (!isNotificationEquals(mCurrentNotification, n) || notification.extras.getBoolean("ticker_icon_switch", false)) {
            mCurrentNotification = n;
            int iconId = notification.extras.getInt("ticker_icon", notification.icon);
            icon = StatusBarIconView.getIcon(mContext, 
            new StatusBarIcon(n.getPackageName(), n.getUser(), iconId, notification.iconLevel, 0,
                notification.tickerText));

            mIconSwitcher.setAnimateFirstView(false);
            mIconSwitcher.reset();
            setAppIconColor(icon);
            tickerStarting();
        }

    }

    public void removeEntry(StatusBarNotification n) {
        if (isNotificationEquals(n, mCurrentNotification)) {
            mCurrentNotification = null;
            tickerDone();
        }
    }

    public void halt() {
        tickerHalting();
        mIconSwitcher.reset();
        mTextSwitcher.reset();
        mCurrentNotification = null;
    }

    public void setViews(TextSwitcher ts, ImageSwitcher is) {
        mTextSwitcher = ts;
        // Copy the paint style of one of the TextSwitchers children to use later for measuring
        TextView text = (TextView) mTextSwitcher.getChildAt(0);
        mPaint = text.getPaint();

        mIconSwitcher = is;
        mIconSwitcher.setScaleX(mIconScale);
        mIconSwitcher.setScaleY(mIconScale);

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
