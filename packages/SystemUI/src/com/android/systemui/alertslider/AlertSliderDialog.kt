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
import android.annotation.IdRes
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
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
import androidx.core.view.doOnLayout

import com.android.systemui.R

import javax.inject.Inject

class AlertSliderDialog @Inject constructor(
    private val context: Context,
    private val windowManager: WindowManager,
) {

    private lateinit var view: View
    private lateinit var background: GradientDrawable
    private lateinit var icon: ImageView
    private lateinit var label: TextView

    private var appearAnimator: ValueAnimator? = null
    private var transitionAnimator: ValueAnimator? = null
    private var radiusAnimator: ValueAnimator? = null

    private var alertSliderTopY = 0
    private var stepSize = 0
    private val positionGravity: Int

    private var currPosition = 0
    private var prevPosition = 0

    private var isPortrait = context.resources.configuration.orientation ==
        Configuration.ORIENTATION_PORTRAIT

    private var layoutParams = LayoutParams(
        LayoutParams.WRAP_CONTENT /** width */,
        LayoutParams.WRAP_CONTENT /** height */,
        LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY /** type */,
        LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_NOT_TOUCHABLE or
            LayoutParams.FLAG_HARDWARE_ACCELERATED /** flags */,
        PixelFormat.TRANSLUCENT /** format */
    )

    init {
        context.resources.let {
            alertSliderTopY = it.getInteger(R.integer.alert_slider_top_y)
            stepSize = it.getInteger(R.integer.alertslider_width) / 2
            positionGravity = if (it.getBoolean(R.bool.config_alertSliderOnLeft)) Gravity.LEFT
                else Gravity.RIGHT
        }
        inflateLayout()
    }

    fun updateConfiguration(newConfig: Configuration) {
        if (view.parent != null) {
            radiusAnimator?.cancel()
            transitionAnimator?.cancel()
            appearAnimator?.cancel()
            windowManager.removeViewImmediate(view)
        }
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        context.resources.let {
            alertSliderTopY = it.getInteger(R.integer.alert_slider_top_y)
            stepSize = it.getInteger(R.integer.alertslider_width) / 2
        }
        inflateLayout()
    }

    fun setIconAndLabel(@IdRes iconResId: Int, @IdRes labelResId: Int) {
        icon.setImageResource(iconResId)
        label.setText(labelResId)
    }

    fun show(position: Int) {
        prevPosition = currPosition
        currPosition = position
        appearAnimator?.cancel()
        if (view.parent == null) {
            view.alpha = 0f
            windowManager.addView(view, layoutParams)
            // Only start the animations after view has been drawn
            // Need to get measured height to position the view correctly
            view.doOnLayout {
                layoutParams = updateLayoutParams()
                updateCornerRadii(false)
                windowManager.updateViewLayout(view, layoutParams)
                animateAppear(true)
            }
        } else {
            radiusAnimator?.end()
            transitionAnimator?.end()
            view.alpha = 1f // Make sure view is visible for transitions
            updateCornerRadii(true)
            animateTransition()
        }
    }

    fun dismiss() {
        prevPosition = currPosition
        if (view.parent != null) {
            animateAppear(false)
        }
    }

    private fun inflateLayout() {
        view = LayoutInflater.from(context).inflate(R.layout.alertslider_dialog, null, false)
        background = view.background as GradientDrawable
        icon = view.findViewById(R.id.icon)
        label = view.findViewById(R.id.label)
    }

    private fun updateLayoutParams(): LayoutParams {
        val lp = LayoutParams().apply {
            copyFrom(layoutParams)
            x = 0
            y = 0
            if (isPortrait) {
                gravity = positionGravity
                horizontalMargin = 0.025f
                verticalMargin = 0f
            } else {
                gravity = Gravity.TOP
                horizontalMargin = 0f
                verticalMargin = 0.025f
            }
        }
        val bounds = windowManager.currentWindowMetrics.bounds
        if (isPortrait) {
            lp.y = alertSliderTopY - (bounds.height() / 2) +
                ((2 - currPosition) * stepSize) + getOffsetForPosition()
        } else {
            lp.x = alertSliderTopY - (bounds.width() / 2) + ((2 - currPosition) * stepSize)
            if (context.display.rotation == Surface.ROTATION_270) {
                lp.x = -lp.x
            }
        }
        return lp
    }

    private fun getOffsetForPosition() =
        when (currPosition) {
            0 -> view.measuredHeight / 2
            2 -> -view.measuredHeight / 2
            else -> 0
        }

    private fun updateCornerRadii(animate: Boolean) {
        var radius = view.measuredHeight / 2f
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
            0, 2 -> startRadiusAnimator(radius, currPosition, radius, 0f)
            1 -> startRadiusAnimator(radius, prevPosition, 0f, radius)
        }
    }

    private fun startRadiusAnimator(radius: Float, position: Int, vararg values: Float) {
        radiusAnimator = ValueAnimator.ofFloat(*values).apply {
            setDuration(TRANSITION_ANIM_DURATION)
            addUpdateListener {
                val topRightRadius = if (position == 0) it.animatedValue as Float else radius
                val bottomRightRadius = if (position == 2) it.animatedValue as Float else radius
                background.cornerRadii = floatArrayOf(
                    radius, radius,
                    topRightRadius, topRightRadius,
                    bottomRightRadius, bottomRightRadius,
                    radius, radius,
                )
            }
            addListener(
                onCancel = { radiusAnimator = null },
                onEnd = { radiusAnimator = null },
            )
            start()
        }
    }

    private fun animateAppear(appearing: Boolean) {
        appearAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(APPEAR_ANIM_DURATION)
            addUpdateListener {
                view.alpha = it.animatedValue as Float
            }
            addListener(
                onEnd = {
                    appearAnimator = null
                    if (!appearing) windowManager.removeViewImmediate(view)
                },
                onCancel = { appearAnimator = null },
            )
            if (appearing) start()
            else reverse()
        }
    }

    private fun animateTransition() {
        val lp = updateLayoutParams()
        transitionAnimator = if (isPortrait) ValueAnimator.ofInt(layoutParams.y, lp.y)
            else ValueAnimator.ofInt(layoutParams.x, lp.x)
        transitionAnimator!!.let {
            it.setDuration(TRANSITION_ANIM_DURATION)
            it.addUpdateListener { animator ->
                if (isPortrait) {
                    lp.y = animator.animatedValue as Int
                } else {
                    lp.x = animator.animatedValue as Int
                }
                windowManager.updateViewLayout(view, lp)
            }
            it.addListener(
                onEnd = {
                    transitionAnimator = null
                    layoutParams = lp
                },
                onCancel = { transitionAnimator = null },
            )
            it.start()
        }
    }

    companion object {
        private const val APPEAR_ANIM_DURATION = 300L
        private const val TRANSITION_ANIM_DURATION = 300L
    }
}
