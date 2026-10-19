package com.modern.core;

import com.modern.core.command.VanillaCommandOverrides;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

/**
 * Bootstrap Paper: registra i comandi con gli stessi nomi vanilla così vincono su /minecraft:msg, /kick, /ban.
 */
public final class ModernBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                VanillaCommandOverrides.register(event.registrar()));
    }
}
