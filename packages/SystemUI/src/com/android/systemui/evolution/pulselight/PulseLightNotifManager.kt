/*
 * Copyright (C) 2023-2024 The LibreMobileOS Foundation
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
package com.android.systemui.evolution.pulselight

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings

import com.android.systemui.R
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider
import com.android.systemui.plugins.statusbar.StatusBarStateController

import javax.inject.Inject

@SysUISingleton
class PulseLightNotifManager @Inject constructor(
    private val context: Context,
    @Main private val handler: Handler,
    private val statusBarStateController: StatusBarStateController,
    private val notificationInterruptStateProvider: NotificationInterruptStateProvider
) {

    private var notifListener: PulseLightNotifListener? = null

    private var enabled = false
    private var duration = 2  // in seconds
    private var lightCount = 1
    private val pulsingTime: Long
        get() = lightCount * duration * 1000L // in ms

    private val onlyWhenFaceDown by lazy {
        context.resources.getBoolean(R.bool.config_showEdgeLightOnlyWhenFaceDown)
    }

    private var faceDownDetector: FaceDownDetector? = null
    private var isFaceDown = false

    private var pulseStopRunnable: Runnable? = null

    private val statusBarStateListener: StatusBarStateController.StateListener =
        object : StatusBarStateController.StateListener {
            override fun onDozingChanged(isDozing: Boolean) {
                if (!enabled || faceDownDetector == null) return

                if (isDozing) {
                    faceDownDetector?.enable()
                } else {
                    faceDownDetector?.disable()
                }
            }
        }

    init {
        setupContentObserver()
        setupFaceDownDetector()
    }

    private fun setupContentObserver() {
        val pulseAmbientLight = Settings.Secure.getUriFor(PULSE_AMBIENT_LIGHT)
        val pulseAmbientLightDuration = Settings.Secure.getUriFor(PULSE_AMBIENT_LIGHT_DURATION)
        val pulseAmbientLightRepeatCount =
                Settings.Secure.getUriFor(PULSE_AMBIENT_LIGHT_REPEAT_COUNT)
        val contentObserver = object: ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                when (uri) {
                    pulseAmbientLight -> {
                        enabled = getSettingsInt(PULSE_AMBIENT_LIGHT, 0) == 1
                    }
                    pulseAmbientLightDuration -> {
                        duration = getSettingsInt(PULSE_AMBIENT_LIGHT_DURATION, 2)
                    }
                    pulseAmbientLightRepeatCount -> {
                        // repeat = 0 means light will show up only one time.
                        // so to get actual lights animation count, repeat + 1.
                        lightCount = 1 + getSettingsInt(PULSE_AMBIENT_LIGHT_REPEAT_COUNT, 0)
                    }
                    else -> { /* Nothing */}
                }
            }
        }
        context.contentResolver.registerContentObserver(
                pulseAmbientLight, false, contentObserver, UserHandle.USER_CURRENT)
        context.contentResolver.registerContentObserver(
                pulseAmbientLightDuration, false, contentObserver, UserHandle.USER_CURRENT)
        context.contentResolver.registerContentObserver(
                pulseAmbientLightRepeatCount, false, contentObserver, UserHandle.USER_CURRENT)
        contentObserver.onChange(true, pulseAmbientLight)
        contentObserver.onChange(true, pulseAmbientLightDuration)
        contentObserver.onChange(true, pulseAmbientLightRepeatCount)
    }

    private fun setupFaceDownDetector() {
        if (onlyWhenFaceDown) {
            faceDownDetector = FaceDownDetector(context, this::onFlip)
            statusBarStateController.addCallback(statusBarStateListener)
        }
    }

    private fun onFlip(faceDown: Boolean) {
        isFaceDown = faceDown
        notifListener?.onFaceDownChanged(faceDown)
        // Stop pulsing on lifting the device
        val stopRunnable = pulseStopRunnable
        if (!faceDown && stopRunnable != null) {
            handler.removeCallbacks(stopRunnable)
            handler.post(stopRunnable)
        }
    }

    fun addListener(listener: PulseLightNotifListener) {
        notifListener = listener
    }

    fun onNotificationPosted(entry: NotificationEntry) {
        if (!canPulse(entry)) return
        // Update notification pulse state for the entry
        // so we can get the package to extract app icon color.
        entry.setPulseLightState(true)
        notifListener?.onNotification(entry, true)
        // Stop the pulsing once pulse light animaton done.
        pulseStopRunnable = Runnable {
            entry.setPulseLightState(false)
            notifListener?.onNotification(entry, false)
            pulseStopRunnable = null
        }.also {
            handler.postDelayed(it, pulsingTime)
        }
    }

    private fun canPulse(entry: NotificationEntry): Boolean {
        // Ignore if pulse light disabled
        if (!enabled) return false

        if (onlyWhenFaceDown) {
            // Ignore pulse if it's face up
            if (!isFaceDown) return false
        }

        // We want to show pulse on headsup like notifications
        // Do check like that.
        return notificationInterruptStateProvider
                .shouldShowPulseLight(entry, onlyWhenFaceDown)
    }

    private fun getSettingsInt(key: String, default: Int): Int {
        return Settings.Secure.getIntForUser(context.contentResolver,
                key, default, UserHandle.USER_CURRENT)
    }

    interface PulseLightNotifListener {
        fun onNotification(entry: NotificationEntry, pulse: Boolean)
        fun onFaceDownChanged(faceDown: Boolean)
    }

    companion object {
        private const val PULSE_AMBIENT_LIGHT = Settings.Secure.PULSE_AMBIENT_LIGHT
        private const val PULSE_AMBIENT_LIGHT_DURATION =
                Settings.Secure.PULSE_AMBIENT_LIGHT_DURATION
        private const val PULSE_AMBIENT_LIGHT_REPEAT_COUNT =
                Settings.Secure.PULSE_AMBIENT_LIGHT_REPEAT_COUNT
    }

}
