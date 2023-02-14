/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.qs

import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.content.Context
import android.os.Handler
import android.os.UserManager
import android.os.UserHandle
import android.provider.Settings
import android.provider.Settings.Global.USER_SWITCHER_ENABLED
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.UiEventLogger
import com.android.internal.logging.nano.MetricsProto
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.systemui.R
import com.android.systemui.animation.ActivityLaunchAnimator
import com.android.systemui.globalactions.GlobalActionsDialogLite
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.dagger.QSFlagsModule.PM_LITE_ENABLED
import com.android.systemui.qs.dagger.QSScope
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.phone.MultiUserSwitchController
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.statusbar.policy.UserInfoController
import com.android.systemui.statusbar.policy.UserInfoController.OnUserInfoChangedListener
import com.android.systemui.tuner.TunerService
import com.android.systemui.util.LargeScreenUtils
import com.android.systemui.util.ViewController
import com.android.systemui.util.settings.GlobalSettings
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

/**
 * Manages [FooterActionsView] behaviour, both when it's placed in QS or QQS (split shade).
 * Main difference between QS and QQS behaviour is condition when buttons should be visible,
 * determined by [buttonsVisibleState]
 */
@QSScope
// TODO(b/242040009): Remove this file.
internal class FooterActionsController @Inject constructor(
    view: FooterActionsView,
    multiUserSwitchControllerFactory: MultiUserSwitchController.Factory,
    private val activityStarter: ActivityStarter,
    private val userManager: UserManager,
    private val userTracker: UserTracker,
    private val userInfoController: UserInfoController,
    private val deviceProvisionedController: DeviceProvisionedController,
    private val securityFooterController: QSSecurityFooter,
    private val fgsManagerFooterController: QSFgsManagerFooter,
    private val falsingManager: FalsingManager,
    private val metricsLogger: MetricsLogger,
    private val tunerService: TunerService,
    private val globalActionsDialogProvider: Provider<GlobalActionsDialogLite>,
    private val uiEventLogger: UiEventLogger,
    @Named(PM_LITE_ENABLED) private val showPMLiteButton: Boolean,
    private val globalSetting: GlobalSettings,
    private val handler: Handler,
    private val configurationController: ConfigurationController,
) : ViewController<FooterActionsView>(view) {

    private var globalActionsDialog: GlobalActionsDialogLite? = null

    private var lastExpansion = -1f
    private var listening: Boolean = false
    private var inSplitShade = false
    private var mShowPMLiteButton: Boolean = true

    private val singleShadeAnimator by lazy {
        // In single shade, the actions footer should only appear at the end of the expansion,
        // so that it doesn't overlap with the notifications panel.
        TouchAnimator.Builder().addFloat(mView, "alpha", 0f, 1f).setStartDelay(0.9f).build()
    }

    private val splitShadeAnimator by lazy {
        // The Actions footer view has its own background which is the same color as the qs panel's
        // background.
        // We don't want it to fade in at the same time as the rest of the panel, otherwise it is
        // more opaque than the rest of the panel's background. Only applies to split shade.
        val alphaAnimator = TouchAnimator.Builder().addFloat(mView, "alpha", 0f, 1f).build()
        val bgAlphaAnimator =
            TouchAnimator.Builder()
                .addFloat(mView, "backgroundAlpha", 0f, 1f)
                .setStartDelay(0.9f)
                .build()
        // In split shade, we want the actions footer to fade in exactly at the same time as the
        // rest of the shade, as there is no overlap.
        TouchAnimator.Builder()
            .addFloat(alphaAnimator, "position", 0f, 1f)
            .addFloat(bgAlphaAnimator, "position", 0f, 1f)
            .build()
    }

    private val animators: TouchAnimator
        get() = if (inSplitShade) splitShadeAnimator else singleShadeAnimator

    var visible = true
        set(value) {
            field = value
            updateVisibility()
        }

    private val servicesButtonContainer: View = view.findViewById(R.id.services_button_container)
    private val settingsButtonContainer: View = view.findViewById(R.id.settings_button_container)
    private val securityFootersContainer: ViewGroup? =
        view.findViewById(R.id.security_footers_container)
    private val powerMenuLite: View = view.findViewById(R.id.pm_lite)
    private val multiUserSwitchController = multiUserSwitchControllerFactory.create(view)

    private val QS_FOOTER_SHOW_SETTINGS =
            "system:" + Settings.System.QS_FOOTER_SHOW_SETTINGS
    private val  QS_FOOTER_SHOW_USER =
            "system:" + Settings.System.QS_FOOTER_SHOW_USER
    private val  QS_FOOTER_SHOW_POWER_MENU =
            "system:" + Settings.System.QS_FOOTER_SHOW_POWER_MENU
    private val QS_FOOTER_SHOW_SERVICES =
            "system:" + Settings.System.QS_FOOTER_SHOW_SERVICES

    @VisibleForTesting
    internal val securityFootersSeparator = View(context).apply { visibility = View.GONE }

    private val onUserInfoChangedListener = OnUserInfoChangedListener { _, picture, _ ->
        val isGuestUser: Boolean = userManager.isGuestUser(KeyguardUpdateMonitor.getCurrentUser())
        mView.onUserInfoChanged(picture, isGuestUser)
    }

    private val multiUserSetting =
            object : SettingObserver(
                    globalSetting, handler, USER_SWITCHER_ENABLED, userTracker.userId) {
                override fun handleValueChanged(value: Int, observedChange: Boolean) {
                    if (observedChange) {
                        updateView()
                    }
                }
            }

    private val onClickListener = View.OnClickListener { v ->
        // Don't do anything if the tap looks suspicious.
        if (!visible || falsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
            return@OnClickListener
        }
        if (v === settingsButtonContainer) {
            if (!deviceProvisionedController.isCurrentUserSetup) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                activityStarter.postQSRunnableDismissingKeyguard {}
                return@OnClickListener
            }
            metricsLogger.action(MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH)
            startSettingsActivity()
        } else if (v === servicesButtonContainer) {
            if (!deviceProvisionedController.isCurrentUserSetup) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                activityStarter.postQSRunnableDismissingKeyguard {}
                return@OnClickListener
            }
            metricsLogger.action(MetricsProto.MetricsEvent.ACTION_QS_EXPANDED_SETTINGS_LAUNCH)
            startServicesActivity()
        } else if (v === powerMenuLite) {
            uiEventLogger.log(GlobalActionsDialogLite.GlobalActionsEvent.GA_OPEN_QS)
            val isHidePowerMenuEnabled = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.LOCK_POWER_MENU_DISABLED, 1,
                    UserHandle.USER_CURRENT) != 0;
            val keyguardManager = getContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val isLocked = keyguardManager.isDeviceLocked()
            if ((isLocked) && (isHidePowerMenuEnabled)) {
                // no nothing
            } else {
                globalActionsDialog?.showOrHideDialog(false, true, v)
            }
        }
    }

    private val onLongClickListener = View.OnLongClickListener { v ->
        // Don't do anything if the tap looks suspicious.
        if (!visible || falsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
            return@OnLongClickListener false
        }
        if (v === settingsButtonContainer) {
            if (!deviceProvisionedController.isCurrentUserSetup) {
                // If user isn't setup just unlock the device and dump them back at SUW.
                activityStarter.postQSRunnableDismissingKeyguard {}
                return@OnLongClickListener false
            }
            startEvolverActivity()
            return@OnLongClickListener true
        }
        return@OnLongClickListener false
    }

    private val configurationListener =
        object : ConfigurationController.ConfigurationListener {
            override fun onConfigChanged(newConfig: Configuration?) {
                updateResources()
            }
        }

    private fun updateResources() {
        inSplitShade = LargeScreenUtils.shouldUseSplitNotificationShade(resources)
    }

    override fun onInit() {
        multiUserSwitchController.init()
        securityFooterController.init()
        fgsManagerFooterController.init()
    }

    private fun updateVisibility() {
        val previousVisibility = mView.visibility
        mView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (previousVisibility != mView.visibility) updateView()
    }

    private fun startSettingsActivity() {
        val animationController = settingsButtonContainer?.let {
            ActivityLaunchAnimator.Controller.fromView(
                    it,
                    InteractionJankMonitor.CUJ_SHADE_APP_LAUNCH_FROM_SETTINGS_BUTTON)
            }
        activityStarter.startActivity(Intent(Settings.ACTION_SETTINGS),
                true /* dismissShade */, animationController)
    }

    private fun startEvolverActivity() {
        val intent = Intent()
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings\$EvolutionSettingsActivity")
        val animationController = settingsButtonContainer?.let {
            ActivityLaunchAnimator.Controller.fromView(
                    it,
                    InteractionJankMonitor.CUJ_SHADE_APP_LAUNCH_FROM_SETTINGS_BUTTON)
            }
        activityStarter.startActivity(intent,   
                true /* dismissShade */, animationController)
    }

    private fun startServicesActivity() {
        val intent = Intent()
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings\$DevRunningServicesActivity")
        val animationController = servicesButtonContainer?.let {
            ActivityLaunchAnimator.Controller.fromView(
                    it,
                    InteractionJankMonitor.CUJ_SHADE_APP_LAUNCH_FROM_SETTINGS_BUTTON)
            }
        activityStarter.startActivity(intent,
                true /* dismissShade */, animationController)
    }

    @VisibleForTesting
    public override fun onViewAttached() {
        globalActionsDialog = globalActionsDialogProvider.get()
        servicesButtonContainer.setOnClickListener(onClickListener)
        settingsButtonContainer.setOnClickListener(onClickListener)
        settingsButtonContainer.setOnLongClickListener(onLongClickListener)
        multiUserSetting.isListening = true

        val securityFooter = securityFooterController.view
        securityFootersContainer?.addView(securityFooter)
        val separatorWidth = resources.getDimensionPixelSize(R.dimen.qs_footer_action_inset)
        securityFootersContainer?.addView(securityFootersSeparator, separatorWidth, 1)

        val fgsFooter = fgsManagerFooterController.view
        securityFootersContainer?.addView(fgsFooter)

        val visibilityListener =
            VisibilityChangedDispatcher.OnVisibilityChangedListener { visibility ->
                if (securityFooter.visibility == View.VISIBLE &&
                    fgsFooter.visibility == View.VISIBLE) {
                    securityFootersSeparator.visibility = View.VISIBLE
                } else {
                    securityFootersSeparator.visibility = View.GONE
                }
                fgsManagerFooterController
                    .setCollapsed(securityFooter.visibility == View.VISIBLE)
            }
        securityFooterController.setOnVisibilityChangedListener(visibilityListener)
        fgsManagerFooterController.setOnVisibilityChangedListener(visibilityListener)

        configurationController.addCallback(configurationListener)

        tunerService.addTunable(object : TunerService.Tunable {
            override fun onTuningChanged(key: String?, newValue: String?) {
                mView.updateServicesIconVisibility(tunerService.getValue(key, 0) != 0)
            }
        }, QS_FOOTER_SHOW_SERVICES)

        tunerService.addTunable(object : TunerService.Tunable {
            override fun onTuningChanged(key: String?, newValue: String?) {
                mView.updateSettingsIconVisibility(tunerService.getValue(key, 1) != 0)
            }
        }, QS_FOOTER_SHOW_SETTINGS)

        tunerService.addTunable(object : TunerService.Tunable {
            override fun onTuningChanged(key: String?, newValue: String?) {
                mView.updateUserIconVisibility(tunerService.getValue(key, 1) != 0)
            }
        }, QS_FOOTER_SHOW_USER)

        tunerService.addTunable(object : TunerService.Tunable {
            override fun onTuningChanged(key: String?, newValue: String?) {
                mShowPMLiteButton = tunerService.getValue(key, 1) != 0
                updatePMLiteIconVisibility()
            }
        }, QS_FOOTER_SHOW_POWER_MENU)

        updateResources()
        updateView()
    }

    private fun updatePMLiteIconVisibility() {
        if (mShowPMLiteButton) {
            powerMenuLite.visibility = View.VISIBLE
            powerMenuLite.setOnClickListener(onClickListener)
        } else {
            powerMenuLite.visibility = View.GONE
            powerMenuLite.setOnClickListener(null)
        }
    }

    private fun updateView() {
        mView.updateEverything(multiUserSwitchController.isMultiUserEnabled)
        updatePMLiteIconVisibility()
    }

    override fun onViewDetached() {
        globalActionsDialog?.destroy()
        globalActionsDialog = null
        setListening(false)
        multiUserSetting.isListening = false
        configurationController.removeCallback(configurationListener)
    }

    fun setListening(listening: Boolean) {
        if (this.listening == listening) {
            return
        }
        this.listening = listening
        if (this.listening) {
            userInfoController.addCallback(onUserInfoChangedListener)
            updateView()
        } else {
            userInfoController.removeCallback(onUserInfoChangedListener)
        }

        fgsManagerFooterController.setListening(listening)
        securityFooterController.setListening(listening)
    }

    fun disable(state2: Int) {
        mView.disable(state2, multiUserSwitchController.isMultiUserEnabled)
    }

    fun setExpansion(headerExpansionFraction: Float) {
        animators.setPosition(headerExpansionFraction)
    }

    fun setKeyguardShowing(showing: Boolean) {
        setExpansion(lastExpansion)
    }
}
