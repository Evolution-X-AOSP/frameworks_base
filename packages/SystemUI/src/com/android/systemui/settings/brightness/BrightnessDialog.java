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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.util.settings.SystemSettings;

import javax.inject.Inject;

/** A dialog that provides controls for adjusting the screen brightness. */
public class BrightnessDialog extends Activity {

    private BrightnessController mBrightnessController;
    private final BrightnessSlider.Factory mToggleSliderFactory;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final SystemSettings mSystemSettings;

    @Inject
    public BrightnessDialog(
            BroadcastDispatcher broadcastDispatcher,
            BrightnessSlider.Factory factory,
            SystemSettings systemSettings) {
        mBroadcastDispatcher = broadcastDispatcher;
        mToggleSliderFactory = factory;
        mSystemSettings = systemSettings;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window window = getWindow();

        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);

        // Calling this creates the decor View, so setLayout takes proper effect
        // (see Dialog#onWindowAttributesChanged)
        window.getDecorView();
        window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        setContentView(R.layout.brightness_mirror_container);
        FrameLayout frame = findViewById(R.id.brightness_mirror_container);
        // The brightness mirror container is INVISIBLE by default.
        frame.setVisibility(View.VISIBLE);

        BrightnessSlider controller = mToggleSliderFactory.create(this, frame);
        controller.init();
        frame.addView(controller.getRootView(), MATCH_PARENT, WRAP_CONTENT);
        final ImageView icon = controller.getIconView();
        final boolean configEnabled = getResources().getBoolean(
            com.android.internal.R.bool.config_automatic_brightness_available);
        final boolean showAutoBrightnessButton = mSystemSettings.getIntForUser(
            Settings.System.QS_SHOW_AUTO_BRIGHTNESS_BUTTON,
            0, UserHandle.USER_CURRENT) == 1;
        icon.setVisibility((configEnabled && showAutoBrightnessButton)
            ? View.VISIBLE : View.GONE);
        mBrightnessController = new BrightnessController(this, icon,
                controller, mBroadcastDispatcher);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBrightnessController.registerCallbacks();
        MetricsLogger.visible(this, MetricsEvent.BRIGHTNESS_DIALOG);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MetricsLogger.hidden(this, MetricsEvent.BRIGHTNESS_DIALOG);
        mBrightnessController.unregisterCallbacks();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            finish();
        }

        return super.onKeyDown(keyCode, event);
    }
}
