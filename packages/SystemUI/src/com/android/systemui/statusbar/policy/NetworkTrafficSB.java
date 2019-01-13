/*
 * Copyright (C) 2021 Yet Another AOSP Project
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

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import android.view.ViewGroup;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;

import java.util.ArrayList;

public class NetworkTrafficSB extends NetworkTraffic implements DarkReceiver {

    public NetworkTrafficSB(Context context) {
        this(context, null);
    }

    public NetworkTrafficSB(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTrafficSB(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    public void onDarkChanged(ArrayList<Rect> area, float darkIntensity, int tint) {
        setTintColor(DarkIconDispatcher.getTint(area, this, tint));
    }

    @Override
    int[] updateTextSize() {
        final int[] arr = super.updateTextSize();
        if (arr == null) return null;
        final int size = arr[0];
        final int unit = arr[1];
        setAutoSizeTextTypeUniformWithConfiguration(1, size, 1, unit);
        return arr;
    }

    @Override
    boolean isDisabled() {
        return !mIsEnabled || mLocation == LOCATION_QS_HEADER;
    }
}
