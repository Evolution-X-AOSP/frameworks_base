package com.evolution.android.systemui;

import android.content.Context;

import com.evolution.android.systemui.dagger.DaggerGlobalRootComponentEvolution;
import com.evolution.android.systemui.dagger.GlobalRootComponentEvolution;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;

public class SystemUIEvolutionFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerGlobalRootComponentEvolution.builder()
                .context(context)
                .build();
    }
}
