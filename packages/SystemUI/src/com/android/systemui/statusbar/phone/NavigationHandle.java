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

package com.android.systemui.statusbar.phone;

import android.animation.ArgbEvaluator;
import android.annotation.ColorInt;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.R;

public class NavigationHandle extends View implements ButtonInterface {

    private final Paint mPaint = new Paint();
    private @ColorInt final int mLightColor;
    private @ColorInt final int mDarkColor;
    private final int mRadius;
    private final int mBottom;
    private boolean mIsDreaming = false;
    private boolean mIsKeyguard = false;
    private boolean mRequiresInvalidate;

    private AudioManager mAudioManager;

    private KeyguardUpdateMonitor mUpdateMonitor;
    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            mIsDreaming = dreaming;
            if (dreaming) {
                setVisibility(View.GONE);
            } else if (!mIsKeyguard) {
                getHandler().post(() -> maybeMakeVisible());
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            mIsKeyguard = showing;
            if (showing) {
                setVisibility(View.GONE);
            } else if (!mIsDreaming) {
                getHandler().post(() -> maybeMakeVisible());
            }
        }
    };

    public NavigationHandle(Context context) {
        this(context, null);
    }

    public NavigationHandle(Context context, AttributeSet attr) {
        super(context, attr);
        final Resources res = context.getResources();
        mRadius = res.getDimensionPixelSize(R.dimen.navigation_handle_radius);
        mBottom = res.getDimensionPixelSize(R.dimen.navigation_handle_bottom);

        final int dualToneDarkTheme = Utils.getThemeAttr(context, R.attr.darkIconTheme);
        final int dualToneLightTheme = Utils.getThemeAttr(context, R.attr.lightIconTheme);
        Context lightContext = new ContextThemeWrapper(context, dualToneLightTheme);
        Context darkContext = new ContextThemeWrapper(context, dualToneDarkTheme);
        mLightColor = Utils.getColorAttrDefaultColor(lightContext, R.attr.homeHandleColor);
        mDarkColor = Utils.getColorAttrDefaultColor(darkContext, R.attr.homeHandleColor);
        mPaint.setAntiAlias(true);
        setFocusable(false);

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mUpdateMonitor = Dependency.get(KeyguardUpdateMonitor.class);
    }

    private void maybeMakeVisible() {
        boolean pulseEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.NAVBAR_PULSE_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;
        if (pulseEnabled && mAudioManager.isMusicActive()) return;
        setVisibility(View.VISIBLE);
        if (mRequiresInvalidate) invalidate();
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (alpha > 0f && mRequiresInvalidate) {
            mRequiresInvalidate = false;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw that bar
        int navHeight = getHeight();
        int height = mRadius * 2;
        int width = getWidth();
        int y = (navHeight - mBottom - height);
        canvas.drawRoundRect(0, y, width, y + height, mRadius, mRadius, mPaint);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
    }

    @Override
    public void abortCurrentGesture() {
    }

    @Override
    public void setVertical(boolean vertical) {
    }

    @Override
    public void setDarkIntensity(float intensity) {
        int color = (int) ArgbEvaluator.getInstance().evaluate(intensity, mLightColor, mDarkColor);
        if (mPaint.getColor() != color) {
            mPaint.setColor(color);
            if (getVisibility() == VISIBLE && getAlpha() > 0) {
                invalidate();
            } else {
                // If we are currently invisible, then invalidate when we are next made visible
                mRequiresInvalidate = true;
            }
        }
    }

    @Override
    public void setDelayTouchFeedback(boolean shouldDelay) {
    }

    @Override
    public void onAttachedToWindow() {
        mUpdateMonitor.registerCallback(mMonitorCallback);
        super.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        mUpdateMonitor.removeCallback(mMonitorCallback);
        super.onDetachedFromWindow();
    }
}
