/*
 * Copyright (C) 2021 Project Radiant
 * Copyright (C) 2023 The risingOS Android Project
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

package com.android.internal.util.evolution;

import android.app.WallpaperManager;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

public class WallpaperViewUtils extends ImageView {

    private Context contextM;
    private BroadcastReceiver wallpaperChangeReceiver;

    public WallpaperViewUtils(@Nullable Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        contextM = context;
    }

    public WallpaperViewUtils(@Nullable Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        contextM = context;
    }

    public WallpaperViewUtils(@Nullable Context context) {
        super(context);
        contextM = context;
        setWallpaper();
    }

    public void setWallpaper() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(contextM);
        setImageDrawable(wallpaperManager.getDrawable());
        registerWallpaperChangeReceiver();
    }

    public void registerWallpaperChangeReceiver() {
        wallpaperChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(contextM);
                setImageDrawable(wallpaperManager.getDrawable());
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
        contextM.registerReceiver(wallpaperChangeReceiver, filter);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (wallpaperChangeReceiver != null) {
            contextM.unregisterReceiver(wallpaperChangeReceiver);
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }
}

