/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.settings.brightness;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settingslib.RestrictedLockUtils;
import com.android.systemui.Gefingerpoken;
import com.android.systemui.res.R;

import java.util.Collections;

/**
 * {@code FrameLayout} used to show and manipulate a {@link ToggleSeekBar}.
 *
 */
public class BrightnessSliderView extends LinearLayout {

    @NonNull
    private TextView mTextPersen;
    private ToggleSeekBar mSlider;
    private DispatchTouchEventListener mListener;
    private Gefingerpoken mOnInterceptListener;
    @Nullable
    private Drawable mProgressDrawable;
    private float mScale = 1f;
    private final Rect mSystemGestureExclusionRect = new Rect();

    public BrightnessSliderView(Context context) {
        this(context, null);
    }

    public BrightnessSliderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // Inflated from quick_settings_brightness_dialog
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setLayerType(LAYER_TYPE_HARDWARE, null);

        mSlider = requireViewById(R.id.slider);
        mSlider.setAccessibilityLabel(getContentDescription().toString());
        mTextPersen = requireViewById(R.id.percentbrightness);    
	    Handler h = new Handler();
        TextBrightness text = new TextBrightness(h);
        text.BTObserver();
	    ShowingTextBrightness();
	    GetValueBrightness(mSlider.getProgress());

        // Finds the progress drawable. Assumes brightness_progress_drawable.xml
        try {
            LayerDrawable progress = (LayerDrawable) mSlider.getProgressDrawable();
            DrawableWrapper progressSlider = (DrawableWrapper) progress
                    .findDrawableByLayerId(android.R.id.progress);
            LayerDrawable actualProgressSlider = (LayerDrawable) progressSlider.getDrawable();
            mProgressDrawable = actualProgressSlider.findDrawableByLayerId(R.id.slider_foreground);
        } catch (Exception e) {
            // Nothing to do, mProgressDrawable will be null.
        }
    }

    public void GetValueBrightness(int value) {
            int make100 = value * 100 / mSlider.getMax();
            mTextPersen.setText(String.valueOf(make100) + "%");
    }

    private void ShowingTextBrightness() {
            int showHide = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.QS_BRIGHTNESS_TEXTVIEW, 0);
	    if (showHide == 1) {
	    LinearLayout.LayoutParams bright = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
            bright.setMargins(20, 0, 0, 0);
	    mTextPersen.setLayoutParams(bright);
            mTextPersen.setVisibility(View.VISIBLE);
            } else {
            LinearLayout.LayoutParams bright = new LinearLayout.LayoutParams(0, 0);
            bright.setMargins(0, 0, 0, 0);
	    mTextPersen.setLayoutParams(bright);
            mTextPersen.setVisibility(View.GONE);
            }

    }

    public class TextBrightness extends ContentObserver {
            public TextBrightness(Handler h) {
            super(h);
            BTObserver();
            }

            @Override
            public void onChange(boolean selfChange) {
                   super.onChange(selfChange);
                   ShowingTextBrightness();
            }

            public void BTObserver()
            {
                   ContentResolver cr = getContext().getContentResolver();
                   cr.registerContentObserver(Settings.System.getUriFor(
                           Settings.System.QS_BRIGHTNESS_TEXTVIEW), false, this);
            }
    }

    /**
     * Attaches a listener to relay touch events.
     * @param listener use {@code null} to remove listener
     */
    public void setOnDispatchTouchEventListener(
            DispatchTouchEventListener listener) {
        mListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mListener != null) {
            mListener.onDispatchTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // We prevent disallowing on this view, but bubble it up to our parents.
        // We need interception to handle falsing.
        if (mParent != null) {
            mParent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    /**
     * Attaches a listener to the {@link ToggleSeekBar} in the view so changes can be observed
     * @param seekListener use {@code null} to remove listener
     */
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener seekListener) {
        mSlider.setOnSeekBarChangeListener(seekListener);
    }

    /**
     * Enforces admin rules for toggling auto-brightness and changing value of brightness
     * @param admin
     * @see ToggleSeekBar#setEnforcedAdmin
     */
    public void setEnforcedAdmin(RestrictedLockUtils.EnforcedAdmin admin) {
        mSlider.setEnabled(admin == null);
        mSlider.setEnforcedAdmin(admin);
    }

    /**
     * Enables or disables the slider
     * @param enable
     */
    public void enableSlider(boolean enable) {
        mSlider.setEnabled(enable);
    }

    /**
     * @return the maximum value of the {@link ToggleSeekBar}.
     */
    public int getMax() {
        return mSlider.getMax();
    }

    /**
     * Sets the maximum value of the {@link ToggleSeekBar}.
     * @param max
     */
    public void setMax(int max) {
        mSlider.setMax(max);
    }

    /**
     * Sets the current value of the {@link ToggleSeekBar}.
     * @param value
     */
    public void setValue(int value) {
        mSlider.setProgress(value);
    }

    /**
     * @return the current value of the {@link ToggleSeekBar}
     */
    public int getValue() {
        return mSlider.getProgress();
    }

    public void setOnInterceptListener(Gefingerpoken onInterceptListener) {
        mOnInterceptListener = onInterceptListener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mOnInterceptListener != null) {
            return mOnInterceptListener.onInterceptTouchEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        applySliderScale();
        int horizontalMargin =
                getResources().getDimensionPixelSize(R.dimen.notification_side_paddings);
        mSystemGestureExclusionRect.set(-horizontalMargin, 0, right - left + horizontalMargin,
                bottom - top);
        setSystemGestureExclusionRects(Collections.singletonList(mSystemGestureExclusionRect));
    }

    /**
     * Sets the scale for the progress bar (for brightness_progress_drawable.xml)
     *
     * This will only scale the thick progress bar and not the icon inside
     *
     * Used in {@link com.android.systemui.qs.QSAnimator}.
     */
    @Keep
    public void setSliderScaleY(float scale) {
        if (scale != mScale) {
            mScale = scale;
            applySliderScale();
        }
    }

    private void applySliderScale() {
        if (mProgressDrawable != null) {
            final Rect r = mProgressDrawable.getBounds();
            int height = (int) (mProgressDrawable.getIntrinsicHeight() * mScale);
            int inset = (mProgressDrawable.getIntrinsicHeight() - height) / 2;
            mProgressDrawable.setBounds(r.left, inset, r.right, inset + height);
        }
    }

    @Keep
    public float getSliderScaleY() {
        return mScale;
    }

    /**
     * Interface to attach a listener for {@link View#dispatchTouchEvent}.
     */
    @FunctionalInterface
    interface DispatchTouchEventListener {
        boolean onDispatchTouchEvent(MotionEvent ev);
    }
}

