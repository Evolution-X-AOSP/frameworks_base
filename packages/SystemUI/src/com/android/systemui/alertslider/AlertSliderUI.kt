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

import android.content.Context
import android.content.res.Configuration

import com.android.internal.R
import com.android.systemui.SystemUI
import com.android.systemui.dagger.SysUISingleton

import dagger.Lazy

import java.io.FileDescriptor
import java.io.PrintWriter

import javax.inject.Inject

@SysUISingleton
class AlertSliderUI @Inject constructor(
    context: Context,
    private val alertSliderController: Lazy<AlertSliderController>,
): SystemUI(context) {

    private var enabled = false

    override fun start() {
        enabled = mContext.resources.getBoolean(R.bool.config_hasAlertSlider)
        if (!enabled) return
        alertSliderController.get().start()
    }

    override protected fun onConfigurationChanged(newConfig: Configuration) {
        if (enabled) {
            alertSliderController.get().updateConfiguration(newConfig)
        }
    }

    override fun dump(fd: FileDescriptor, pw: PrintWriter, args: Array<String>) {
        pw.print("enabled=$enabled")
    }
}
