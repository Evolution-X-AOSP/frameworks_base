/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui;

import static com.android.systemui.Dependency.ALLOW_NOTIFICATION_LONG_PRESS_NAME;

import com.android.systemui.fragments.FragmentService;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.InjectionInflationController;
import com.android.systemui.util.leak.GarbageMonitor;

import com.google.android.systemui.SystemUIGoogleModule;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;

/**
 * Root component for Dagger injection.
 */
@Singleton
@Component(modules = {
        DependencyProvider.class,
        DependencyBinder.class,
        ServiceBinder.class,
        SystemUIFactory.ContextHolder.class,
        SystemUIModule.class,
        SystemUIDefaultModule.class,
        SystemUIGoogleModule.class})
public interface SystemUIRootComponent {

    /**
     * Main dependency providing module.
     */
    @Singleton
    Dependency.DependencyInjector createDependency();


    /**
     * Creates a ConfigurationController.
     */
    @Singleton
    ConfigurationController getConfigurationController();

    /**
     * Injects the StatusBar.
     */
    @Singleton
    StatusBar.StatusBarInjector getStatusBarInjector();

    /**
     * FragmentCreator generates all Fragments that need injection.
     */
    @Singleton
    FragmentService.FragmentCreator createFragmentCreator();

    /**
     * ViewCreator generates all Views that need injection.
     */
    InjectionInflationController.ViewCreator createViewCreator();

    /**
     * Creatse a GarbageMonitor.
     */
    @Singleton
    GarbageMonitor createGarbageMonitor();

    /**
     * Whether notification long press is allowed.
     */
    @Named(ALLOW_NOTIFICATION_LONG_PRESS_NAME)
    boolean allowNotificationLongPressName();

    /**
     * Injects into the supplied argument.
     */
    void inject(SystemUIAppComponentFactory factory);
}
