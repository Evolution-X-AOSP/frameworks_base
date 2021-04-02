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

import static com.android.systemui.statusbar.phone.KeyguardClockPositionAlgorithm.CLOCK_USE_DEFAULT_Y;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Paint.Style;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.util.TimeZone;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class StickerTagClockController implements ClockPlugin {

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    private final Context mContext;

    /**
     * Helper to extract colors from wallpaper palette for clock face.
     */
    private final ClockPalette mPalette = new ClockPalette();

    /**
     * Root view of preview.
     */
    private View mView;

    /**
     * Background of the tag view
     */
    private LinearLayout mTagContainer;

    /**
     * Custom text
     */
    private TextView mBuildTag;

    /**
     * Create a StickerTagClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public StickerTagClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        this(res, inflater, colorExtractor, null);
    }

    /**
     * Create a StickerTagClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     * @param context A context.
     */
    public StickerTagClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor, Context context) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
        mContext = context;
    }

    private void createViews() {
        mView = mLayoutInflater.inflate(R.layout.clock_tag, null);
        mTagContainer = mView.findViewById(R.id.tagContainer);
        mBuildTag = mView.findViewById(R.id.tagBuild);
    }

    @Override
    public void onDestroyView() {
        mView = null;
        mTagContainer = null;
        mBuildTag = null;
    }

    @Override
    public String getName() {
        return "sticker_tag";
    }

    @Override
    public String getTitle() {
        return mResources.getString(R.string.sticker_tag_clock_title);
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.sticker_tag_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        // Use the big clock view for the preview
        View view = getView();

        // Initialize state of plugin before generating preview.
        setDarkAmount(1f);
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        onTimeTick();

        return mRenderer.createPreview(view, width, height);
    }

    @Override
    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    @Override
    public View getBigClockView() {
        return null;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return CLOCK_USE_DEFAULT_Y;
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        mPalette.setColorPalette(supportsDarkText, colorPalette);
        updateColor();
    }

    private void updateColor() {
        final int primary = mPalette.getPrimaryColor();

        int[] gradColors = {primary, generateColorDesat(primary)};
        GradientDrawable bgTinted = (GradientDrawable) mResources.getDrawable(R.drawable.tag_base_background);
        bgTinted.setColors(gradColors);
        bgTinted.setOrientation(GradientDrawable.Orientation.TL_BR);
        bgTinted.setGradientType(GradientDrawable.LINEAR_GRADIENT);

        Drawable overlay = mResources.getDrawable(R.drawable.tag_extra_overlay);
        Drawable[] layers = {bgTinted, overlay};
        LayerDrawable tagBg = new LayerDrawable(layers);
        mTagContainer.setBackground(tagBg);

    }

    @Override
    public void onTimeTick() {
        String buildType = SystemProperties.get("org.evolution.build_codename");
        mBuildTag.setText(buildType);
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mPalette.setDarkAmount(darkAmount);
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        if (mContext == null) return true;
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.CLOCK_SHOW_STATUS_AREA, 1) == 1;
    }

    private int generateColorDesat(int color) {
        float[] hslParams = new float[3];
        ColorUtils.colorToHSL(color, hslParams);
        // Conversion to desature the color?
        hslParams[1] = hslParams[1]*0.55f;
        return ColorUtils.HSLToColor(hslParams);
    }
}
