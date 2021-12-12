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

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import androidx.core.animation.addListener

import com.android.systemui.R
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main

import javax.inject.Inject

@SysUISingleton
class AlertSliderController @Inject constructor(
    private val context: Context,
    @Main private val handler: Handler,
    private val windowManager: WindowManager,
) {

    // Supported modes for AlertSlider positions.
    private val MODE_NORMAL: String
    private val MODE_PRIORITY: String
    private val MODE_VIBRATE: String
    private val MODE_SILENT: String
    private val MODE_DND: String

    private lateinit var dialogView: View
    private lateinit var layoutParams: LayoutParams
    private lateinit var icon: ImageView
    private lateinit var label: TextView

    private var appearAnimator: ValueAnimator? = null
    private var disappearAnimator: ValueAnimator? = null
    private var transitionAnimator: ValueAnimator? = null
    private var radiusAnimator: ValueAnimator? = null

    private val dismissDialogRunnable = Runnable {
        prevPosition = currPosition
        if (dialogView.parent != null) {
            animateDisappear()
        }
    }

    private var alertSliderTopY: Int = 0
    private var stepSize: Int = 0

    private var currPosition = 0
    private var prevPosition = 0

    private var isPortrait: Boolean

    init {
        MODE_NORMAL = context.getString(com.android.internal.R.string.alert_slider_mode_normal)
        MODE_PRIORITY = context.getString(com.android.internal.R.string.alert_slider_mode_priority)
        MODE_VIBRATE = context.getString(com.android.internal.R.string.alert_slider_mode_vibrate)
        MODE_SILENT = context.getString(com.android.internal.R.string.alert_slider_mode_silent)
        MODE_DND = context.getString(com.android.internal.R.string.alert_slider_mode_dnd)
        isPortrait = context.display.rotation == Surface.ROTATION_0
    }

    fun start() {
        context.resources.let {
            alertSliderTopY = it.getInteger(R.integer.alert_slider_top_y)
            stepSize = it.getInteger(R.integer.alertslider_width) / 2
        }

        context.registerReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateDialog(intent.getStringExtra(Intent.EXTRA_SLIDER_MODE))
                showDialog(intent.getIntExtra(Intent.EXTRA_SLIDER_POSITION, 0))
            }
        }, IntentFilter(Intent.ACTION_SLIDER_POSITION_CHANGED))

        initDialog()
    }

    fun updateConfiguration(newConfig: Configuration) {
        if (dialogView.parent != null) {
            radiusAnimator?.cancel()
            transitionAnimator?.cancel()
            appearAnimator?.cancel()
            windowManager.removeViewImmediate(dialogView)
        }
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun updateDialog(mode: String) {
        when (mode) {
            MODE_NORMAL -> {
                icon.setImageResource(R.drawable.ic_volume_ringer)
                label.setText(R.string.volume_ringer_status_normal)
            }
            MODE_PRIORITY -> {
                icon.setImageResource(com.android.internal.R.drawable.ic_qs_dnd)
                label.setText(R.string.alert_slider_mode_priority_text)
            }
            MODE_VIBRATE -> {
                icon.setImageResource(R.drawable.ic_volume_ringer_vibrate)
                label.setText(R.string.volume_ringer_status_vibrate)
            }
            MODE_SILENT -> {
                icon.setImageResource(R.drawable.ic_volume_ringer_mute)
                label.setText(R.string.volume_ringer_status_silent)
            }
            MODE_DND -> {
                icon.setImageResource(com.android.internal.R.drawable.ic_qs_dnd)
                label.setText(R.string.alert_slider_mode_dnd_text)
            }
        }
    }

    private fun showDialog(position: Int) {
        if (handler.hasCallbacks(dismissDialogRunnable)) {
            handler.removeCallbacks(dismissDialogRunnable)
            prevPosition = currPosition
        }
        currPosition = position
        disappearAnimator?.cancel()
        if (dialogView.parent == null) {
            updateCornerRadii(false)
            layoutParams = updateLayoutParams()
            dialogView.alpha = 0f
            windowManager.addView(dialogView, layoutParams)
            animateAppear()
        } else {
            appearAnimator?.end()
            transitionAnimator?.end()
            dialogView.alpha = 1f // Make sure alpha is 1 for transitions
            updateCornerRadii(true)
            animateTransition()
        }
        handler.postDelayed(dismissDialogRunnable, TIMEOUT)
    }

    private fun initDialog() {
        dialogView = LayoutInflater.from(context).inflate(
            R.layout.alertslider_dialog, null, false)
        icon = dialogView.findViewById(R.id.icon)
        label = dialogView.findViewById(R.id.label)

        layoutParams = LayoutParams().apply {
            width = LayoutParams.WRAP_CONTENT
            height = LayoutParams.WRAP_CONTENT
            flags = flags or LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                LayoutParams.FLAG_HARDWARE_ACCELERATED
            type = LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY
            format = PixelFormat.TRANSLUCENT
        }
    }

    private fun updateLayoutParams(): LayoutParams {
        val lp = LayoutParams().apply {
            copyFrom(layoutParams)
            x = 0
            y = 0
            if (isPortrait) {
                gravity = Gravity.RIGHT
                horizontalMargin = 0.025f
                verticalMargin = 0f
            } else {
                gravity = Gravity.TOP
                horizontalMargin = 0f
                verticalMargin = 0.025f
            }
        }
        val bounds = windowManager.currentWindowMetrics.bounds
        when (context.display.rotation) {
            Surface.ROTATION_0 -> lp.y = alertSliderTopY - (bounds.height() / 2) +
                ((2 - currPosition) * stepSize) + getOffsetForPosition()
            Surface.ROTATION_90 -> lp.x = alertSliderTopY - (bounds.width() / 2) + ((2 - currPosition) * stepSize)
            Surface.ROTATION_270 -> lp.x = (bounds.width() / 2) - alertSliderTopY - ((2 - currPosition) * stepSize)
        }
        return lp
    }

    private fun getOffsetForPosition() =
        when (currPosition) {
            0 -> dialogView.getMeasuredHeight() / 2
            2 -> -dialogView.getMeasuredHeight() / 2
            else -> 0
        }
    
    private fun updateCornerRadii(animate: Boolean) {
        val background = dialogView.background as GradientDrawable
        var radius = dialogView.getMeasuredHeight() / 2f
        if (radius == 0f) {
            // Use the default radius value since view hasn't been drawn yet,
            // which will be pretty close to the actual value.
            radius = context.resources.getDimension(R.dimen.alertslider_dialog_minradius)
        }
        if (!isPortrait) {
            background.cornerRadius = radius
            return
        }
        if (!animate) {
            if (currPosition == 1) {
                background.cornerRadius = radius
                return
            }
            background.cornerRadii = floatArrayOf(
                radius, radius, // T-L
                if (currPosition == 0) 0f else radius, if (currPosition == 0) 0f else radius, // T-R
                if (currPosition == 2) 0f else radius, if (currPosition == 2) 0f else radius, // B-R
                radius, radius, // B-L
            )
        }
        when (currPosition) {
            0 -> {
                radiusAnimator = ValueAnimator.ofFloat(radius, 0f).also {
                    it.setDuration(TRANSITION_ANIM_DURATION)
                    it.addUpdateListener {
                        val topRightRadius = it.animatedValue as Float
                        background.cornerRadii = floatArrayOf(
                            radius, radius,
                            topRightRadius, topRightRadius,
                            radius, radius,
                            radius, radius,
                        )
                    }
                    it.addListener(onEnd = {
                        radiusAnimator = null
                    })
                    it.start()
                }
            }
            1 -> {
                radiusAnimator = ValueAnimator.ofFloat(0f, radius).also {
                    it.setDuration(TRANSITION_ANIM_DURATION)
                    it.addUpdateListener {
                        val topRightRadius = if (prevPosition == 0) it.animatedValue as Float else radius
                        val bottomRightRadius = if (prevPosition == 2) it.animatedValue as Float else radius
                        background.cornerRadii = floatArrayOf(
                            radius, radius,
                            topRightRadius, topRightRadius,
                            bottomRightRadius, bottomRightRadius,
                            radius, radius,
                        )
                    }
                    it.addListener(onEnd = {
                        radiusAnimator = null
                    })
                    it.start()
                }
            }
            2 -> {
                radiusAnimator = ValueAnimator.ofFloat(radius, 0f).also {
                    it.setDuration(TRANSITION_ANIM_DURATION)
                    it.addUpdateListener {
                        val bottomRightRadius = it.animatedValue as Float
                        background.cornerRadii = floatArrayOf(
                            radius, radius,
                            radius, radius,
                            bottomRightRadius, bottomRightRadius,
                            radius, radius,
                        )
                    }
                    it.addListener(onEnd = {
                        radiusAnimator = null
                    })
                    it.start()
                }
            }
        }
    }

    private fun animateAppear() {
        appearAnimator = ValueAnimator.ofFloat(0f, 1f).also {
            it.setDuration(APPEAR_ANIM_DURATION)
            it.addUpdateListener {
                dialogView.alpha = it.animatedValue as Float
            }
            it.addListener(onEnd = {
                appearAnimator = null
            })
            it.start()
        }
    }

    private fun animateDisappear() {
        disappearAnimator = ValueAnimator.ofFloat(1f, 0f).also {
            it.setDuration(APPEAR_ANIM_DURATION)
            it.addUpdateListener {
                dialogView.alpha = it.animatedValue as Float
            }
            it.addListener(onEnd = {
                disappearAnimator = null
                windowManager.removeViewImmediate(dialogView)
            })
            it.start()
        }
    }

    private fun animateTransition() {
        val lp = updateLayoutParams()
        transitionAnimator = if (isPortrait)
            ValueAnimator.ofInt(layoutParams.y, lp.y)
        else
            ValueAnimator.ofInt(layoutParams.x, lp.x)
        transitionAnimator!!.let {
            it.setDuration(TRANSITION_ANIM_DURATION)
            it.addUpdateListener { animator ->
                if (isPortrait) {
                    lp.y = animator.animatedValue as Int
                } else {
                    lp.x = animator.animatedValue as Int
                }
                if (dialogView.parent != null) {
                    windowManager.updateViewLayout(dialogView, lp)
                }
            }
            it.addListener(onEnd = {
                transitionAnimator = null
                layoutParams = lp
            })
            it.start()
        }
        
    }

    companion object {
        private const val TIMEOUT = 1000L
        private const val APPEAR_ANIM_DURATION = 300L
        private const val TRANSITION_ANIM_DURATION = 300L
    }
}
