package com.modern.core.module;

import com.modern.core.ModernCorePlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registro moduli: abilita solo quelli con flag true in config.yml.
 */
public final class ModuleManager {

    private final ModernCorePlugin plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Set<String> active = new LinkedHashSet<>();

    public ModuleManager(ModernCorePlugin plugin) {
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
            this.active.add(module.id());
            this.plugin.getLogger().info("Modulo '" + module.id() + "' abilitato.");
        }
    }

    public void disableAll() {
        for (String id : Set.copyOf(this.active)) {
            Module module = this.modules.get(id);
            if (module == null) {
                continue;
            }
            try {
                module.disable();
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Errore spegnendo il modulo " + module.id() + ": " + exception.getMessage());
            } finally {
                this.active.remove(id);
            }
        }
    }

    public int reloadAll() {
        int reloaded = 0;
        for (Module module : this.modules.values()) {
            boolean shouldEnable = isEnabled(module.id());
            boolean isActive = this.active.contains(module.id());
            if (!shouldEnable) {
                if (isActive) {
                    module.disable();
                    this.active.remove(module.id());
                    reloaded++;
                }
                continue;
            }
            if (isActive) {
                module.reload();
            } else {
                module.enable();
                this.active.add(module.id());
            }
            reloaded++;
        }
        return reloaded;
    }

    public Set<String> activeIds() {
        return Set.copyOf(this.active);
    }

    public void restore(Set<String> expectedActive) {
        for (Module module : this.modules.values()) {
            boolean shouldEnable = expectedActive.contains(module.id());
            boolean isActive = this.active.contains(module.id());
            if (isActive && !shouldEnable) {
                module.disable();
                this.active.remove(module.id());
            } else if (!isActive && shouldEnable) {
                module.enable();
                this.active.add(module.id());
            } else if (isActive) {
                module.reload();
            }
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
