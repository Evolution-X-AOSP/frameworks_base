/*
 * Copyright 2017-2018 Paranoid Android
 *           2021 AOSP-Krypton Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.R
import com.android.systemui.statusbar.phone.PhoneStatusBarView
import com.android.systemui.statusbar.phone.StatusBar
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener

import javax.inject.Inject

@SysUISingleton
class BurnInProtectionController @Inject constructor(
    private val context: Context,
    private val configurationController: ConfigurationController,
): Handler.Callback {

    private val handler: Handler
    private val shiftEnabled: Boolean
    private val shiftInterval: Long

    private val configurationListener = object: ConfigurationListener {
        override fun onDensityOrFontScaleChanged() {
            logD("onDensityOrFontScaleChanged")
            loadResources(context.resources)
        }
    }

    private var statusBar: StatusBar? = null
    private var phoneStatusBarView: PhoneStatusBarView? = null

    private var shiftTimerScheduled = false

    // Shift amount in pixels
    private var horizontalShift = 0
    private var horizontalMaxShift = 0
    private var verticalShift = 0
    private var verticalMaxShift = 0

    // Increment / Decrement (based on sign) for each tick
    private var horizontalShiftStep = 0
    private var verticalShiftStep = 0

    init {
        handler = Handler(Looper.getMainLooper(), this)
        val res = context.resources
        shiftEnabled = res.getBoolean(R.bool.config_statusBarBurnInProtection)
        shiftInterval = res.getInteger(R.integer.config_shift_interval) * 1000L
        logD("shiftEnabled = $shiftEnabled, shiftInterval = $shiftInterval")
        loadResources(res)
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            MSG_START -> startShiftInternal()
            MSG_STOP -> stopShiftInternal()
            MSG_SHIFT -> {
                shiftItems()
                handler.sendMessageDelayed(handler.obtainMessage(MSG_SHIFT), shiftInterval)
            }
        }
        return true
    }

    fun setStatusBar(statusBar: StatusBar?) {
        this.statusBar = statusBar
    }

    fun setPhoneStatusBarView(phoneStatusBarView: PhoneStatusBarView?) {
        this.phoneStatusBarView = phoneStatusBarView
    }

    fun startShiftTimer() {
        handler.sendMessage(handler.obtainMessage(MSG_START))
    }

    private fun startShiftInternal() {
        if (!shiftEnabled || shiftTimerScheduled) return
        configurationController.addCallback(configurationListener)
        handler.sendMessageDelayed(handler.obtainMessage(MSG_SHIFT), shiftInterval)
        shiftTimerScheduled = true
        logD("Started shift timer")
    }

    fun stopShiftTimer() {
        handler.sendMessage(handler.obtainMessage(MSG_STOP))
    }

    private fun stopShiftInternal() {
        if (!shiftEnabled || !shiftTimerScheduled) return
        handler.removeMessages(MSG_SHIFT)
        configurationController.removeCallback(configurationListener)
        horizontalShift = 0
        verticalShift = 0
        shiftTimerScheduled = false
        logD("Cancelled shift timer")
    }

    private fun loadResources(res: Resources)  {
        horizontalShift = 0
        verticalShift = 0
        horizontalMaxShift = res.getDimensionPixelSize(R.dimen.horizontal_max_shift)
        horizontalShiftStep = horizontalMaxShift / TOTAL_SHIFTS_IN_ONE_DIRECTION
        verticalMaxShift = res.getDimensionPixelSize(R.dimen.vertical_max_shift)
        verticalShiftStep = verticalMaxShift / TOTAL_SHIFTS_IN_ONE_DIRECTION
        logD("horizontalMaxShift = $horizontalMaxShift" +
            ", horizontalShiftStep = $horizontalShiftStep" +
            ", verticalMaxShift = $verticalMaxShift"  +
            ", verticalShiftStep = $verticalShiftStep")
    }

    private fun shiftItems() {
        horizontalShift += horizontalShiftStep
        if (horizontalShift >= horizontalMaxShift ||
                horizontalShift <= -horizontalMaxShift) {
            logD("Switching horizontal direction")
            horizontalShiftStep *= -1
        }

        verticalShift += verticalShiftStep
        if (verticalShift >= verticalMaxShift ||
                verticalShift <= -verticalMaxShift) {
            logD("Switching vertical direction")
            verticalShiftStep *= -1
        }

        logD("Shifting items, horizontalShift = $horizontalShift" +
                ", verticalShift = $verticalShift")

        phoneStatusBarView?.shiftStatusBarItems(horizontalShift, verticalShift)
        statusBar?.let {
            it.navigationBarView?.shiftNavigationBarItems(horizontalShift, verticalShift)
        }
    }

    companion object {
        private const val TAG = "BurnInProtectionController"
        private const val DEBUG = false
        private const val TOTAL_SHIFTS_IN_ONE_DIRECTION = 3

        // Handler message constants
        private const val MSG_START = 1
        private const val MSG_STOP = 2
        private const val MSG_SHIFT = 3

        private fun logD(msg: String?) {
            if (DEBUG) Log.d(TAG, msg)
        }
    }
}
