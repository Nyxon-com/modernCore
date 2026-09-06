package com.valaryrpg.core;

import com.valaryrpg.core.command.CommandService;
import com.valaryrpg.core.config.ConfigManager;
import com.valaryrpg.core.economy.EconomyService;
import com.valaryrpg.core.message.MessageService;
import com.valaryrpg.core.module.ModuleManager;
import com.valaryrpg.core.module.homes.HomesModule;
import com.valaryrpg.core.module.players.PlayersModule;
import com.valaryrpg.core.module.warps.WarpsModule;
import com.valaryrpg.core.module.worlds.WorldsModule;
import com.valaryrpg.core.storage.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point del Core. Carica config, storage, economy hook e i moduli attivi.
 */
public final class ValaryCorePlugin extends JavaPlugin {

    private ConfigManager configs;
    private MessageService messages;
    private StorageManager storage;
    private EconomyService economy;
    private ModuleManager modules;
    private CommandService commands;

    @Override
    public void onLoad() {
        this.configs = new ConfigManager(this);
        this.configs.loadAll();
        this.messages = new MessageService(this);
    }

    @Override
    public void onEnable() {
        this.storage = new StorageManager(this);
        try {
            this.storage.connect();
        } catch (Exception exception) {
            getLogger().severe("Impossibile avviare lo storage: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.economy = new EconomyService(this);
        this.economy.hook();

        this.modules = new ModuleManager(this);
        this.modules.register(new WorldsModule(this));
        this.modules.register(new HomesModule(this));
        this.modules.register(new WarpsModule(this));
        this.modules.register(new PlayersModule(this));
        this.modules.enableAll();

        this.commands = new CommandService(this);
        this.commands.register();

        getLogger().info("ValaryRPG-Core " + getPluginMeta().getVersion() + " abilitato.");
    }

    @Override
    public void onDisable() {
        if (this.modules != null) {
            this.modules.disableAll();
        }
        if (this.storage != null) {
            this.storage.close();
        }
        getLogger().info("ValaryRPG-Core disabilitato.");
    }

    /**
     * Ricarica YAML e moduli. Lo storage non viene riconnesso (richiede restart).
     */
    public boolean reloadAll() {
        try {
            this.configs.loadAll();
            this.messages.reload();
            if (this.modules != null) {
                this.modules.reloadAll();
            }
            return true;
        } catch (Exception exception) {
            getLogger().severe("Reload fallito: " + exception.getMessage());
            exception.printStackTrace();
            return false;
        }
    }

    public ConfigManager configs() {
        return this.configs;
    }

    public MessageService messages() {
        return this.messages;
    }

    public StorageManager storage() {
        return this.storage;
    }

    public EconomyService economy() {
        return this.economy;
    }

    public ModuleManager modules() {
        return this.modules;
    }
}
