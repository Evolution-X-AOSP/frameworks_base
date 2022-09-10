/*
 * Copyright (C) 2022 FlamingoOS Project
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

package com.android.systemui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView

import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope

import com.android.systemui.R
import com.android.systemui.keyguard.WakefulnessLifecycle

import java.io.RandomAccessFile

import javax.inject.Inject

import kotlin.math.roundToInt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FPSInfoService @Inject constructor(
    private val wakefulnessLifecycle: WakefulnessLifecycle
) : LifecycleService() {

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private val wakefulnessObserver = object : WakefulnessLifecycle.Observer {
        override fun onStartedGoingToSleep() {
            logD {
                "onStartedGoingToSleep"
            }
            stopReadingInternal()
        }

        override fun onFinishedWakingUp() {
            logD {
                "onFinishedWakingUp"
            }
            startReadingInternal()
        }
    }

    private val topInset: Int
        get() = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top

    private lateinit var binder: ServiceBinder
    private lateinit var windowManager: WindowManager
    private lateinit var fpsInfoView: TextView
    private lateinit var fpsInfoNode: RandomAccessFile

    private var fpsReadJob: Job? = null

    private var registeredWakefulnessLifecycleObserver = false

    val isReading: Boolean
        get() = fpsReadJob?.isActive == true

    override fun onCreate() {
        super.onCreate()
        logD {
            "onCreate"
        }
        binder = ServiceBinder()

        windowManager = getSystemService(WindowManager::class.java)
        layoutParams.y = topInset

        fpsInfoView = TextView(this).apply {
            text = getString(R.string.fps_text_placeholder, 0)
            setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, BACKGROUND_ALPHA))
            setTextColor(Color.WHITE)
            val padding = resources.getDimensionPixelSize(R.dimen.fps_info_text_padding)
            setPadding(padding, padding, padding, padding)
        }

        val nodePath = getString(R.string.config_fpsInfoSysNode)
        runCatching {
            RandomAccessFile(nodePath, "r")
        }.onFailure {
            Log.e(TAG, "Unable to open $nodePath", it)
            stopSelf()
        }.onSuccess {
            fpsInfoNode = it
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        logD {
            "onConfigurationChanged"
        }
        layoutParams.y = topInset
        if (fpsInfoView.parent != null) {
            windowManager.updateViewLayout(fpsInfoView, layoutParams)
        }
    }

    fun startReading() {
        if (!registeredWakefulnessLifecycleObserver) {
            wakefulnessLifecycle.addObserver(wakefulnessObserver)
            registeredWakefulnessLifecycleObserver = true
        }
        startReadingInternal()
    }

    private fun startReadingInternal() {
        logD {
            "startReadingInternal, isReading = $isReading"
        }
        if (isReading) return
        fpsReadJob = lifecycleScope.launch {
            if (fpsInfoView.parent == null) {
                windowManager.addView(fpsInfoView, layoutParams)
            }
            do {
                fpsInfoView.text = getString(R.string.fps_text_placeholder, measureFps())
                delay(FPS_MEASURE_INTERVAL)
            } while (isActive)
        }
    }

    fun stopReading() {
        stopReadingInternal()
        if (registeredWakefulnessLifecycleObserver) {
            wakefulnessLifecycle.removeObserver(wakefulnessObserver)
            registeredWakefulnessLifecycleObserver = false
        }
    }

    private fun stopReadingInternal() {
        logD {
            "stopReadingInternal, isReading = $isReading"
        }
        if (!isReading) return
        fpsReadJob?.cancel()
        fpsReadJob = null
        lifecycleScope.launch {
            if (fpsInfoView.parent != null) {
                windowManager.removeViewImmediate(fpsInfoView)
            }
        }
    }

    private suspend fun measureFps(): Int = withContext(Dispatchers.IO) {
        runCatching {
            fpsInfoNode.seek(0L)
            FpsRegex.find(fpsInfoNode.readLine())?.value?.toFloat()?.roundToInt() ?: 0
        }.getOrElse {
            Log.e(TAG, "Failed to parse fps, ${it.message}")
            0
        }
    }

    override fun onDestroy() {
        logD {
            "onDestroy"
        }
        stopReading()
        super.onDestroy()
    }

    inner class ServiceBinder : Binder() {
        val service: FPSInfoService
            get() = this@FPSInfoService
    }

    private companion object {
        private const val FPS_MEASURE_INTERVAL = 1000L

        private const val BACKGROUND_ALPHA = 120

        private val FpsRegex = "[0-9]+".toRegex()

        private val TAG = FPSInfoService::class.simpleName
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        private inline fun logD(crossinline msg: () -> String) {
            if (DEBUG) Log.d(TAG, msg())
        }
    }
}
