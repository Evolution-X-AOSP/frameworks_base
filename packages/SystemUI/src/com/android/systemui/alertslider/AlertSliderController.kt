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

package com.android.systemui.alertslider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler

import com.android.systemui.R
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.ScreenLifecycle

import javax.inject.Inject

@SysUISingleton
class AlertSliderController @Inject constructor(
    private val context: Context,
    @Main private val handler: Handler,
    private val dialog: AlertSliderDialog,
    private val screenLifecycle: ScreenLifecycle
) {

    // Supported modes for AlertSlider positions.
    private val MODE_NORMAL: String
    private val MODE_PRIORITY: String
    private val MODE_VIBRATE: String
    private val MODE_SILENT: String
    private val MODE_DND: String

    private val dismissDialogRunnable = Runnable { dialog.dismiss() }

    init {
        MODE_NORMAL = context.getString(com.android.internal.R.string.alert_slider_mode_normal)
        MODE_PRIORITY = context.getString(com.android.internal.R.string.alert_slider_mode_priority)
        MODE_VIBRATE = context.getString(com.android.internal.R.string.alert_slider_mode_vibrate)
        MODE_SILENT = context.getString(com.android.internal.R.string.alert_slider_mode_silent)
        MODE_DND = context.getString(com.android.internal.R.string.alert_slider_mode_dnd)
    }

    fun start() {
        context.registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateDialog(intent.getStringExtra(Intent.EXTRA_SLIDER_MODE))
                showDialog(intent.getIntExtra(Intent.EXTRA_SLIDER_POSITION, 0))
            }
        }, IntentFilter(Intent.ACTION_SLIDER_POSITION_CHANGED))
    }

    fun updateConfiguration(newConfig: Configuration) {
        removeHandlerCalbacks()
        dialog.updateConfiguration(newConfig)
    }

    private fun updateDialog(mode: String) {
        when (mode) {
            MODE_NORMAL -> dialog.setIconAndLabel(R.drawable.ic_volume_ringer,
                R.string.volume_ringer_status_normal)
            MODE_PRIORITY -> dialog.setIconAndLabel(com.android.internal.R.drawable.ic_qs_dnd,
                R.string.alert_slider_mode_priority_text)
            MODE_VIBRATE -> dialog.setIconAndLabel(R.drawable.ic_volume_ringer_vibrate,
                R.string.volume_ringer_status_vibrate)
            MODE_SILENT -> dialog.setIconAndLabel(R.drawable.ic_volume_ringer_mute,
                R.string.volume_ringer_status_silent)
            MODE_DND -> dialog.setIconAndLabel(com.android.internal.R.drawable.ic_qs_dnd,
                R.string.alert_slider_mode_dnd_text)
        }
    }

    private fun showDialog(position: Int) {
        removeHandlerCalbacks()
        if (screenLifecycle.screenState == ScreenLifecycle.SCREEN_ON) {
            dialog.show(position)
            handler.postDelayed(dismissDialogRunnable, TIMEOUT)
        }
    }

    private fun removeHandlerCalbacks() {
        if (handler.hasCallbacks(dismissDialogRunnable)) {
            handler.removeCallbacks(dismissDialogRunnable)
        }
    }

    companion object {
        private const val TIMEOUT = 1000L
    }
}
