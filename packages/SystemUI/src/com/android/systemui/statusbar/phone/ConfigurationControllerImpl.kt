/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.statusbar.phone

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.LocaleList
import com.android.systemui.statusbar.policy.ConfigurationController

import java.util.ArrayList

class ConfigurationControllerImpl(context: Context) : ConfigurationController {

    private val listeners: MutableList<ConfigurationController.ConfigurationListener> = ArrayList()
    private val lastConfig = Configuration()
    private var density: Int = 0
    private var fontScale: Float = 0.toFloat()
    private val inCarMode: Boolean
    private var uiMode: Int = 0
    private var localeList: LocaleList? = null
    private val context: Context

    init {
        val currentConfig = context.resources.configuration
        this.context = context
        fontScale = currentConfig.fontScale
        density = currentConfig.densityDpi
        inCarMode = currentConfig.uiMode and Configuration.UI_MODE_TYPE_MASK ==
                Configuration.UI_MODE_TYPE_CAR
        uiMode = currentConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        localeList = currentConfig.locales
    }

    override fun notifyThemeChanged() {
        val listeners = ArrayList(listeners)

        listeners.filterForEach({ this.listeners.contains(it) }) {
            it.onThemeChanged()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Avoid concurrent modification exception
        val listeners = ArrayList(listeners)

        listeners.filterForEach({ this.listeners.contains(it) }) {
            it.onConfigChanged(newConfig)
        }
        val fontScale = newConfig.fontScale
        val density = newConfig.densityDpi
        val uiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val uiModeChanged = uiMode != this.uiMode
        if (density != this.density || fontScale != this.fontScale ||
                inCarMode && uiModeChanged) {
            listeners.filterForEach({ this.listeners.contains(it) }) {
                it.onDensityOrFontScaleChanged()
            }
            this.density = density
            this.fontScale = fontScale
        }

        val localeList = newConfig.locales
        if (localeList != this.localeList) {
            this.localeList = localeList
            listeners.filterForEach({ this.listeners.contains(it) }) {
                it.onLocaleListChanged()
            }
        }

        if (uiModeChanged) {
            // We need to force the style re-evaluation to make sure that it's up to date
            // and attrs were reloaded.
            context.theme.applyStyle(context.themeResId, true)

            this.uiMode = uiMode
            listeners.filterForEach({ this.listeners.contains(it) }) {
                it.onUiModeChanged()
            }
        }

        if (lastConfig.updateFrom(newConfig) and ActivityInfo.CONFIG_ASSETS_PATHS != 0) {
            listeners.filterForEach({ this.listeners.contains(it) }) {
                it.onOverlayChanged()
            }
        }
    }

    override fun addCallback(listener: ConfigurationController.ConfigurationListener) {
        listeners.add(listener)
        listener.onDensityOrFontScaleChanged()
    }

    override fun removeCallback(listener: ConfigurationController.ConfigurationListener) {
        listeners.remove(listener)
    }
}

// This could be done with a Collection.filter and Collection.forEach, but Collection.filter
// creates a new array to store them in and we really don't need that here, so this provides
// a little more optimized inline version.
inline fun <T> Collection<T>.filterForEach(f: (T) -> Boolean, execute: (T) -> Unit) {
    forEach {
        if (f.invoke(it)) {
            execute.invoke(it)
        }
    }
}
