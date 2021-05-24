/**
 * Copyright (C) 2021 ExtendedUI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.biometrics;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.drawable.AnimationDrawable;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.ImageView;
import com.android.systemui.R;

public class FODIconView extends ImageView {
    private AnimationDrawable iconAnim;
    private boolean mIsFODIconAnimated;
    private boolean mIsKeyguard;
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private int mPositionX;
    private int mPositionY;
    private int mSize;
    private final WindowManager mWindowManager;

    private int mSelectedAnim;
    private final int[] ANIMATION_STYLES_NAMES = {
        R.drawable.fod_icon_aod_anim,
        R.drawable.zaid_oneui_fod,
        R.drawable.oneui2_fod
    };

    private int mSelectedIcon;
    private final int[] ICON_STYLES = {
        R.drawable.fod_icon_default,
        R.drawable.fod_icon_default_0,
        R.drawable.fod_icon_default_1,
        R.drawable.fod_icon_default_2,
        R.drawable.fod_icon_default_3,
        R.drawable.fod_icon_default_4,
        R.drawable.fod_icon_default_5,
        R.drawable.fod_icon_arc_reactor,
        R.drawable.fod_icon_cpt_america_flat,
        R.drawable.fod_icon_cpt_america_flat_gray,
        R.drawable.fod_icon_dragon_black_flat,
        R.drawable.fod_icon_evo1,
        R.drawable.fod_icon_glow_circle,
        R.drawable.fod_icon_neon_arc,
        R.drawable.fod_icon_neon_arc_gray,
        R.drawable.fod_icon_neon_circle_pink,
        R.drawable.fod_icon_neon_triangle,
        R.drawable.fod_icon_paint_splash_circle,
        R.drawable.fod_icon_rainbow_horn,
        R.drawable.fod_icon_shooky,
        R.drawable.fod_icon_spiral_blue,
        R.drawable.fod_icon_sun_metro,
        R.drawable.fod_icon_scratch_pink_blue,
        R.drawable.fod_icon_spiral,
        R.drawable.fod_icon_fire_ice_ouroboros,
        R.drawable.fod_icon_transparent
    };

    public FODIconView(Context context, int i, int i2, int i3) {
        super(context);
        this.mPositionX = i2;
        this.mPositionY = i3;
        this.mSize = i;
        this.mWindowManager = (WindowManager) context.getSystemService(WindowManager.class);
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        context.getResources();
        WindowManager.LayoutParams layoutParams = this.mParams;
        int i4 = this.mSize;
        layoutParams.height = i4;
        layoutParams.width = i4;
        layoutParams.format = -3;
        layoutParams.packageName = "android";
        layoutParams.type = 2020;
        layoutParams.flags = 264;
        layoutParams.gravity = 51;
        layoutParams.setTitle("Fingerprint on display icon");
        this.mWindowManager.addView(this, this.mParams);
        boolean z = Settings.System.getInt(getContext().getContentResolver(), "fod_icon_animation", 0) != 0;
        this.mIsFODIconAnimated = z;
        if (z) {
            update(z);
            setBackgroundResource(ANIMATION_STYLES_NAMES[mSelectedAnim]);
            this.iconAnim = (AnimationDrawable) getBackground();
        } else {
            setImageResource(ICON_STYLES[mSelectedIcon]);
        }
        hide();

        update(z);
    }

    public void update(boolean isEnabled) {
        mSelectedIcon = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ICON, 0);
        mSelectedAnim = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ICON_ANIM_TYPE, 0);
    }

    public void hide() {
        setVisibility(8);
        if (this.iconAnim != null && this.mIsFODIconAnimated) {
            clearAnimation();
            this.iconAnim.stop();
            this.iconAnim.selectDrawable(0);
        }
    }

    public void show() {
        setIsAnimationEnabled(Settings.System.getInt(getContext().getContentResolver(), "fod_icon_animation", 0) != 0);
        setVisibility(0);
        AnimationDrawable animationDrawable = this.iconAnim;
        if (animationDrawable != null && this.mIsFODIconAnimated && this.mIsKeyguard) {
            animationDrawable.start();
        }
    }

    public void updatePosition(int i, int i2) {
        this.mPositionX = i;
        this.mPositionY = i2;
        WindowManager.LayoutParams layoutParams = this.mParams;
        layoutParams.x = i;
        layoutParams.y = i2;
        this.mWindowManager.updateViewLayout(this, layoutParams);
    }

    public void setIsAnimationEnabled(boolean z) {
        this.mIsFODIconAnimated = z;
        if (z) {
            setImageResource(0);
            update(z);
            setBackgroundResource(ANIMATION_STYLES_NAMES[mSelectedAnim]);
            this.iconAnim = (AnimationDrawable) getBackground();
            return;
        }
        setBackgroundResource(0);
        setImageResource(ICON_STYLES[mSelectedIcon]);
    }

    public void setIsKeyguard(boolean z) {
        this.mIsKeyguard = z;
        if (z && !this.mIsFODIconAnimated) {
            setColorFilter(-1);
        } else if (this.mIsKeyguard || !this.mIsFODIconAnimated) {
            setBackgroundTintList(null);
            setColorFilter((ColorFilter) null);
        } else {
            setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#807B7E")));
        }
    }
}
