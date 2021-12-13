/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.android.systemui.alertslider;

import android.content.Context;
import android.content.res.Configuration;

import com.android.systemui.R;
import com.android.systemui.SystemUI;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class AlertSliderUI extends SystemUI {
    private AlertSliderController mController;
    private boolean mEnabled;

    @Inject
    public AlertSliderUI(Context context, AlertSliderController alertSliderController) {
        super(context);
        mController = alertSliderController;
    }

    @Override
    public void start() {
        mEnabled = mContext.getResources().getBoolean(R.bool.config_hasAlertSlider);
        if (!mEnabled) return;
        mController.register();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        if (mEnabled) {
            mController.updateConfiguration();
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("mEnabled="); pw.println(mEnabled);
    }
}
