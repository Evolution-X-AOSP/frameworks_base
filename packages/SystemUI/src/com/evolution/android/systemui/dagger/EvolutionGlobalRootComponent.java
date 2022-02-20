package com.evolution.android.systemui.dagger;

import android.content.Context;

import com.android.systemui.dagger.GlobalModule;
import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.dagger.WMModule;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;

@Singleton
@Component(modules = {
        EvolutionSysUISubcomponentModule.class,
        GlobalModule.class,
        WMModule.class})
public interface EvolutionGlobalRootComponent extends GlobalRootComponent {

    @Component.Builder
    interface Builder extends GlobalRootComponent.Builder {
        EvolutionGlobalRootComponent build();
    }

    @Override
    EvolutionSysUIComponent.Builder getSysUIComponent();
}
