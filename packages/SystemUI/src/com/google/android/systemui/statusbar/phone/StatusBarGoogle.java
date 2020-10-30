package com.google.android.systemui.statusbar.phone;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.phone.StatusBar;
import com.google.android.systemui.NotificationLockscreenUserManagerGoogle;
import com.google.android.systemui.smartspace.SmartSpaceController;
import java.io.FileDescriptor;
import java.io.PrintWriter;

import android.content.Context;
import com.android.systemui.statusbar.notification.init.NotificationsController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.PulseExpansionHandler;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.phone.HeadsUpManagerPhone;
import com.android.systemui.statusbar.notification.DynamicPrivacyController;
import com.android.systemui.statusbar.notification.interruption.BypassHeadsUpNotifier;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler;
import com.android.systemui.statusbar.notification.row.NotificationGutsManager;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.notification.logging.NotificationLogger;
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider;
import com.android.systemui.statusbar.NotificationViewHierarchyManager;
import com.android.systemui.keyguard.KeyguardViewMediator;
import android.util.DisplayMetrics;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.bubbles.BubbleController;
import com.android.systemui.statusbar.phone.NotificationGroupManager;
import com.android.systemui.statusbar.notification.VisualStabilityManager;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.NavigationBarController;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.phone.NotificationShadeWindowController;
import com.android.systemui.statusbar.phone.LockscreenLockIconController;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.KeyguardLiftController;
import com.android.systemui.statusbar.phone.LockscreenWallpaper;
import com.android.systemui.statusbar.phone.DozeServiceHost;
import android.os.PowerManager;
import com.android.systemui.recents.ScreenPinningRequest;
import com.android.systemui.statusbar.phone.DozeScrimController;
import com.android.systemui.volume.VolumeComponent;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.recents.Recents;
import com.android.systemui.statusbar.phone.dagger.StatusBarComponent;
import com.android.systemui.shared.plugins.PluginManager;
import com.android.systemui.stackdivider.Divider;
import com.android.systemui.statusbar.phone.LightsOutNotifController;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter;
import com.android.systemui.statusbar.phone.ShadeController;
import com.android.systemui.statusbar.SuperStatusBarViewFactory;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.InitController;
import com.android.systemui.plugins.DarkIconDispatcher;
import static com.android.systemui.Dependency.TIME_TICK_HANDLER_NAME;
import android.os.Handler;
import com.android.systemui.plugins.PluginDependencyProvider;
import com.android.systemui.statusbar.phone.KeyguardDismissUtil;
import com.android.systemui.statusbar.policy.ExtensionController;
import com.android.systemui.statusbar.policy.UserInfoControllerImpl;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.KeyguardIndicationController;
import com.android.systemui.keyguard.DismissCallbackRegistry;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import com.android.systemui.dagger.qualifiers.UiBackground;

import javax.inject.Named;
import javax.inject.Provider;
import dagger.Lazy;
import javax.inject.Inject;
import java.util.concurrent.Executor;
import android.annotation.Nullable;

public class StatusBarGoogle extends StatusBar {

    public StatusBarGoogle(
            Context context,
            NotificationsController notificationsController,
            LightBarController lightBarController,
            AutoHideController autoHideController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            StatusBarIconController statusBarIconController,
            PulseExpansionHandler pulseExpansionHandler,
            NotificationWakeUpCoordinator notificationWakeUpCoordinator,
            KeyguardBypassController keyguardBypassController,
            KeyguardStateController keyguardStateController,
            HeadsUpManagerPhone headsUpManagerPhone,
            DynamicPrivacyController dynamicPrivacyController,
            BypassHeadsUpNotifier bypassHeadsUpNotifier,
            FalsingManager falsingManager,
            BroadcastDispatcher broadcastDispatcher,
            RemoteInputQuickSettingsDisabler remoteInputQuickSettingsDisabler,
            NotificationGutsManager notificationGutsManager,
            NotificationLogger notificationLogger,
            NotificationInterruptStateProvider notificationInterruptStateProvider,
            NotificationViewHierarchyManager notificationViewHierarchyManager,
            KeyguardViewMediator keyguardViewMediator,
            DisplayMetrics displayMetrics,
            MetricsLogger metricsLogger,
            @UiBackground Executor uiBgExecutor,
            NotificationMediaManager notificationMediaManager,
            NotificationLockscreenUserManager lockScreenUserManager,
            NotificationRemoteInputManager remoteInputManager,
            UserSwitcherController userSwitcherController,
            NetworkController networkController,
            BatteryController batteryController,
            SysuiColorExtractor colorExtractor,
            ScreenLifecycle screenLifecycle,
            WakefulnessLifecycle wakefulnessLifecycle,
            SysuiStatusBarStateController statusBarStateController,
            VibratorHelper vibratorHelper,
            BubbleController bubbleController,
            NotificationGroupManager groupManager,
            VisualStabilityManager visualStabilityManager,
            DeviceProvisionedController deviceProvisionedController,
            NavigationBarController navigationBarController,
            Lazy<AssistManager> assistManagerLazy,
            ConfigurationController configurationController,
            NotificationShadeWindowController notificationShadeWindowController,
            LockscreenLockIconController lockscreenLockIconController,
            DozeParameters dozeParameters,
            ScrimController scrimController,
            @Nullable KeyguardLiftController keyguardLiftController,
            Lazy<LockscreenWallpaper> lockscreenWallpaperLazy,
            Lazy<BiometricUnlockController> biometricUnlockControllerLazy,
            DozeServiceHost dozeServiceHost,
            PowerManager powerManager,
            ScreenPinningRequest screenPinningRequest,
            DozeScrimController dozeScrimController,
            VolumeComponent volumeComponent,
            CommandQueue commandQueue,
            Optional<Recents> recentsOptional,
            Provider<StatusBarComponent.Builder> statusBarComponentBuilder,
            PluginManager pluginManager,
            Optional<Divider> dividerOptional,
            LightsOutNotifController lightsOutNotifController,
            FlashlightController flashlightController,
            StatusBarNotificationActivityStarter.Builder
                    statusBarNotificationActivityStarterBuilder,
            ShadeController shadeController,
            SuperStatusBarViewFactory superStatusBarViewFactory,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            ViewMediatorCallback viewMediatorCallback,
            InitController initController,
            DarkIconDispatcher darkIconDispatcher,
            @Named(TIME_TICK_HANDLER_NAME) Handler timeTickHandler,
            PluginDependencyProvider pluginDependencyProvider,
            KeyguardDismissUtil keyguardDismissUtil,
            ExtensionController extensionController,
            UserInfoControllerImpl userInfoControllerImpl,
            PhoneStatusBarPolicy phoneStatusBarPolicy,
            KeyguardIndicationController keyguardIndicationController,
            DismissCallbackRegistry dismissCallbackRegistry,
            Lazy<NotificationShadeDepthController> notificationShadeDepthControllerLazy,
            StatusBarTouchableRegionManager statusBarTouchableRegionManager) {

        super(context, 
            notificationsController,
            lightBarController,
            autoHideController,
            keyguardUpdateMonitor,
            statusBarIconController,
            pulseExpansionHandler, 
            notificationWakeUpCoordinator,
            keyguardBypassController,
            keyguardStateController,
            headsUpManagerPhone,
            dynamicPrivacyController,
            bypassHeadsUpNotifier,
            falsingManager,
            broadcastDispatcher,
            remoteInputQuickSettingsDisabler,
            notificationGutsManager,
            notificationLogger,
            notificationInterruptStateProvider,
            notificationViewHierarchyManager,
            keyguardViewMediator,
            displayMetrics,
            metricsLogger,
            uiBgExecutor,
            notificationMediaManager,
            lockScreenUserManager,
            remoteInputManager,
            userSwitcherController,
            networkController,
            batteryController,
            colorExtractor,
            screenLifecycle,
            wakefulnessLifecycle,
            statusBarStateController,
            vibratorHelper,
            bubbleController,
            groupManager,
            visualStabilityManager,
            deviceProvisionedController,
            navigationBarController,
            assistManagerLazy,
            configurationController,
            notificationShadeWindowController,
            lockscreenLockIconController,
            dozeParameters,
            scrimController,
            keyguardLiftController,
            lockscreenWallpaperLazy,
            biometricUnlockControllerLazy,
            dozeServiceHost,
            powerManager,
            screenPinningRequest,
            dozeScrimController,
            volumeComponent,
            commandQueue,
            recentsOptional,
            statusBarComponentBuilder,
            pluginManager,
            dividerOptional,
            lightsOutNotifController,
            flashlightController,
            statusBarNotificationActivityStarterBuilder,
            shadeController,
            superStatusBarViewFactory,
            statusBarKeyguardViewManager,
            viewMediatorCallback,
            initController,
            darkIconDispatcher,
            timeTickHandler,
            pluginDependencyProvider,
            keyguardDismissUtil,
            extensionController,
            userInfoControllerImpl,
            phoneStatusBarPolicy,
            keyguardIndicationController,
            dismissCallbackRegistry,
            notificationShadeDepthControllerLazy,
            statusBarTouchableRegionManager);
        }

    @Override
    public void start() {
        super.start();
        ((NotificationLockscreenUserManagerGoogle) Dependency.get(NotificationLockscreenUserManager.class)).updateAodVisibilitySettings();
    }

    @Override
    public void setLockscreenUser(int i) {
        super.setLockscreenUser(i);
        SmartSpaceController.get(this.mContext).reloadData();
    }

    @Override
    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        super.dump(fileDescriptor, printWriter, strArr);
        SmartSpaceController.get(this.mContext).dump(fileDescriptor, printWriter, strArr);
    }

}
