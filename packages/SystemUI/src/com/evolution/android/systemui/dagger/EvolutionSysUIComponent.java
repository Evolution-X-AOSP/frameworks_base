package com.evolution.android.systemui.dagger;

import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIBinder;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SystemUIModule;

import com.evolution.android.systemui.keyguard.EvolutionKeyguardSliceProvider;
import com.evolution.android.systemui.smartspace.KeyguardSmartspaceController;

import dagger.Subcomponent;

@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        EvolutionSystemUIBinder.class,
        EvolutionSystemUIModule.class,
        SystemUIModule.class})
public interface EvolutionSysUIComponent extends SysUIComponent {
    @SysUISingleton
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        EvolutionSysUIComponent build();
    }

    /**
     * Member injection into the supplied argument.
     */
    void inject(EvolutionKeyguardSliceProvider keyguardSliceProvider);

    @SysUISingleton
    KeyguardSmartspaceController createKeyguardSmartspaceController();
}
