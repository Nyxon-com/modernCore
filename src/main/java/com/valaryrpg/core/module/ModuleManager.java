package com.valaryrpg.core.module;

import com.valaryrpg.core.ValaryCorePlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro moduli: abilita solo quelli con flag true in config.yml.
 */
public final class ModuleManager {

    private final ValaryCorePlugin plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void register(Module module) {
        this.modules.put(module.id(), module);
    }

    public void enableAll() {
        for (Module module : this.modules.values()) {
            if (!isEnabled(module.id())) {
                this.plugin.getLogger().info("Modulo '" + module.id() + "' disabilitato da config.");
                continue;
            }
            module.enable();
            this.plugin.getLogger().info("Modulo '" + module.id() + "' abilitato.");
        }
    }

    public void disableAll() {
        for (Module module : this.modules.values()) {
            try {
                module.disable();
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Errore spegnendo il modulo " + module.id() + ": " + exception.getMessage());
            }
        }
    }

    public void reloadAll() {
        for (Module module : this.modules.values()) {
            if (!isEnabled(module.id())) {
                module.disable();
                continue;
            }
            module.reload();
        }
    }

    public boolean isEnabled(String id) {
        return this.plugin.configs().root().getBoolean("modules." + id, true);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(String id) {
        return (T) this.modules.get(id);
    }

    public Collection<Module> all() {
        return this.modules.values();
    }
}
