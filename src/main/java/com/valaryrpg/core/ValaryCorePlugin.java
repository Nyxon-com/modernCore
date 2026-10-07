package com.valaryrpg.core;

import com.valaryrpg.core.command.CommandService;
import com.valaryrpg.core.config.ConfigManager;
import com.valaryrpg.core.economy.EconomyService;
import com.valaryrpg.core.message.MessageService;
import com.valaryrpg.core.message.VanillaTranslator;
import com.valaryrpg.core.module.ModuleManager;
import com.valaryrpg.core.module.chat.ChatModule;
import com.valaryrpg.core.module.expeditions.ExpeditionsModule;
import com.valaryrpg.core.module.holograms.HologramsModule;
import com.valaryrpg.core.module.homes.HomesModule;
import com.valaryrpg.core.module.kits.KitsModule;
import com.valaryrpg.core.module.mechanics.MechanicsModule;
import com.valaryrpg.core.module.moderation.ModerationModule;
import com.valaryrpg.core.module.players.PlayersModule;
import com.valaryrpg.core.module.sell.SellModule;
import com.valaryrpg.core.module.vip.VipModule;
import com.valaryrpg.core.module.warps.WarpsModule;
import com.valaryrpg.core.module.worlds.WorldsModule;
import com.valaryrpg.core.papi.PlaceholderHook;
import com.valaryrpg.core.security.OpFullAccessService;
import com.valaryrpg.core.security.OwnerPermsBootstrap;
import com.valaryrpg.core.storage.StorageManager;
import com.valaryrpg.core.util.LastLocations;
import com.valaryrpg.core.util.Teleports;
import com.valaryrpg.core.webhook.WebhookService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point del Core. Carica config, storage, economy hook e i moduli attivi.
 */
public final class ValaryCorePlugin extends JavaPlugin {

    private ConfigManager configs;
    private MessageService messages;
    private VanillaTranslator vanillaTranslator;
    private StorageManager storage;
    private EconomyService economy;
    private ModuleManager modules;
    private CommandService commands;
    private LastLocations lastLocations;
    private Teleports teleports;
    private WebhookService webhooks;
    private OpFullAccessService opFullAccess;

    @Override
    public void onLoad() {
        this.configs = new ConfigManager(this);
        this.configs.loadInitial();
        this.messages = new MessageService(this);
        this.vanillaTranslator = new VanillaTranslator(this);
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
        this.lastLocations = new LastLocations();
        this.teleports = new Teleports(this);
        getServer().getPluginManager().registerEvents(this.teleports, this);

        this.webhooks = new WebhookService(this);
        this.webhooks.enable();

        this.modules = new ModuleManager(this);
        this.modules.register(new WorldsModule(this));
        this.modules.register(new HomesModule(this));
        this.modules.register(new WarpsModule(this));
        this.modules.register(new ModerationModule(this));
        this.modules.register(new PlayersModule(this));
        this.modules.register(new MechanicsModule(this));
        this.modules.register(new HologramsModule(this));
        this.modules.register(new KitsModule(this));
        this.modules.register(new SellModule(this));
        this.modules.register(new ChatModule(this));
        this.modules.register(new ExpeditionsModule(this));
        this.modules.register(new VipModule(this));
        this.modules.enableAll();

        this.commands = new CommandService(this);
        this.commands.register();
        getServer().getPluginManager().registerEvents(new com.valaryrpg.core.util.UpgradePreviewFix(this), this);
        new com.valaryrpg.core.util.UpgradeStationFix(this);
        new com.valaryrpg.core.util.GemForgeChanceFix(this);
        new com.valaryrpg.core.util.MiningInvDropFix(this);
        PlaceholderHook.register(this);
        if (this.vanillaTranslator != null) {
            this.vanillaTranslator.sync();
        }
        this.opFullAccess = new OpFullAccessService(this);
        this.opFullAccess.start();
        new OwnerPermsBootstrap(this).schedule();
        new com.valaryrpg.core.security.DefaultRankBootstrap(this).schedule();

        getLogger().info("ValaryRPG-Core " + getPluginMeta().getVersion() + " abilitato.");
    }

    @Override
    public void onDisable() {
        if (this.opFullAccess != null) {
            this.opFullAccess.stop();
        }
        if (this.vanillaTranslator != null) {
            this.vanillaTranslator.unregister();
        }
        if (this.modules != null) {
            this.modules.disableAll();
        }
        if (this.webhooks != null) {
            this.webhooks.disable();
        }
        if (this.storage != null) {
            this.storage.close();
        }
        getLogger().info("ValaryRPG-Core disabilitato.");
    }

    public OpFullAccessService opFullAccess() {
        return this.opFullAccess;
    }

    /**
     * Ricarica YAML e moduli. Lo storage non viene riconnesso (richiede restart).
     */
    public ReloadResult reloadAll() {
        ConfigManager.Snapshot previous = this.configs.current();
        java.util.Set<String> previousModules = this.modules == null
                ? java.util.Set.of()
                : this.modules.activeIds();
        ConfigManager.ReloadCandidate candidate;
        try {
            candidate = this.configs.prepareReload();
        } catch (Exception exception) {
            getLogger().severe("Reload rifiutato, configurazione attiva invariata: " + exception.getMessage());
            return ReloadResult.failed(exception.getMessage());
        }

        boolean storageRestart = changed(previous.storage().config(), candidate.snapshot().storage().config());
        boolean commandsRestart = changedSection(
                previous.root().config(),
                candidate.snapshot().root().config(),
                "commands"
        );
        try {
            this.configs.activate(candidate.snapshot());
            if (this.vanillaTranslator != null) {
                this.vanillaTranslator.sync();
            }
            if (this.webhooks != null) {
                this.webhooks.reload();
            }
            int moduleCount = 0;
            if (this.modules != null) {
                moduleCount = this.modules.reloadAll();
            }
            if (this.commands != null) {
                this.commands.reloadFilter();
            }
            int triggerCount = activeCount("mechanics")
                    ? ((MechanicsModule) this.modules.get("mechanics")).holos().names().size()
                    : 0;
            int hologramCount = activeCount("holograms")
                    ? ((HologramsModule) this.modules.get("holograms")).holograms().names().size()
                    : 0;
            for (String path : this.configs.loadedPaths(candidate.snapshot())) {
                getLogger().info("Reload YAML: " + path);
            }
            java.io.File holoFolder = new java.io.File(
                    getDataFolder(),
                    candidate.snapshot().holograms().config().getString("folder", "holo")
            );
            getLogger().info("Reload cartella hologram: " + holoFolder.getAbsolutePath());
            getLogger().info("Reload completato: " + candidate.managedFiles() + " YAML, "
                    + hologramCount + " hologram, " + triggerCount + " triggerblock, "
                    + moduleCount + " moduli.");
            if (storageRestart) {
                getLogger().warning("storage.yml è cambiato: la connessione DB verrà aggiornata al riavvio.");
            }
            if (commandsRestart) {
                getLogger().warning("Nomi/alias comandi cambiati: Paper li aggiornerà al riavvio.");
            }
            if (this.webhooks != null) {
                this.webhooks.log("reload", "Reload", "YAML e moduli ricaricati.");
            }
            return ReloadResult.ok(
                    candidate.managedFiles(),
                    hologramCount,
                    triggerCount,
                    moduleCount,
                    storageRestart,
                    commandsRestart
            );
        } catch (Exception exception) {
            this.configs.activate(previous);
            try {
                if (this.webhooks != null) {
                    this.webhooks.reload();
                }
                if (this.modules != null) {
                    this.modules.restore(previousModules);
                }
                if (this.vanillaTranslator != null) {
                    this.vanillaTranslator.sync();
                }
            } catch (Exception rollback) {
                exception.addSuppressed(rollback);
            }
            getLogger().severe("Reload fallito, ripristinata la configurazione precedente: " + exception.getMessage());
            exception.printStackTrace();
            return ReloadResult.failed(exception.getMessage());
        }
    }

    private static boolean changed(
            org.bukkit.configuration.file.FileConfiguration before,
            org.bukkit.configuration.file.FileConfiguration after
    ) {
        return !before.saveToString().equals(after.saveToString());
    }

    private boolean activeCount(String id) {
        return this.modules != null && this.modules.activeIds().contains(id);
    }

    private static boolean changedSection(
            org.bukkit.configuration.file.FileConfiguration before,
            org.bukkit.configuration.file.FileConfiguration after,
            String path
    ) {
        org.bukkit.configuration.ConfigurationSection oldSection = before.getConfigurationSection(path);
        org.bukkit.configuration.ConfigurationSection newSection = after.getConfigurationSection(path);
        java.util.Map<String, Object> oldValue = oldSection == null ? java.util.Map.of() : oldSection.getValues(true);
        java.util.Map<String, Object> newValue = newSection == null ? java.util.Map.of() : newSection.getValues(true);
        return !oldValue.equals(newValue);
    }

    public record ReloadResult(
            boolean success,
            int files,
            int holograms,
            int triggers,
            int modules,
            boolean storageRestart,
            boolean commandsRestart,
            String error
    ) {
        private static ReloadResult ok(
                int files,
                int holograms,
                int triggers,
                int modules,
                boolean storageRestart,
                boolean commandsRestart
        ) {
            return new ReloadResult(true, files, holograms, triggers, modules, storageRestart, commandsRestart, "");
        }

        private static ReloadResult failed(String error) {
            return new ReloadResult(false, 0, 0, 0, 0, false, false, error == null ? "errore sconosciuto" : error);
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

    public WebhookService webhooks() {
        return this.webhooks;
    }

    public LastLocations lastLocations() {
        if (this.lastLocations == null) {
            this.lastLocations = new LastLocations();
        }
        return this.lastLocations;
    }

    public Teleports teleports() {
        if (this.teleports == null) {
            this.teleports = new Teleports(this);
            if (isEnabled()) {
                getServer().getPluginManager().registerEvents(this.teleports, this);
            }
        }
        return this.teleports;
    }
}
