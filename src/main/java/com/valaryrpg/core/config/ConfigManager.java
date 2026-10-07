package com.valaryrpg.core.config;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Carica i YAML: config/storage in root, testi in lang/, suoni in sounds/, moduli in modules/.
 */
public final class ConfigManager {

    private static final int CONFIG_VERSION = 2;
    private final ValaryCorePlugin plugin;
    private YamlFile root;
    private YamlFile messages;
    private YamlFile vanilla;
    private YamlFile storage;
    private YamlFile sounds;
    private YamlFile worlds;
    private YamlFile homes;
    private YamlFile warps;
    private YamlFile players;
    private YamlFile moderation;
    private YamlFile mechanics;
    private YamlFile holograms;
    private YamlFile kits;
    private YamlFile prices;
    private YamlFile chat;
    private YamlFile webhooks;
    private YamlFile tomb;

    public ConfigManager(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadInitial() {
        activate(loadSnapshot(false));
        int version = this.root.config().getInt("plugin.config-version", 0);
        if (version < 1) {
            migrateLegacySounds();
            migrateGroundItemHologram();
            migrateDisconnectScreens();
            migrateHoloCommand();
            migrateTriggerblockCommand();
        }
        if (version < 2) {
            migrateSpawnOnJoin();
        }
        if (version < CONFIG_VERSION) {
            this.root.config().set("plugin.config-version", CONFIG_VERSION);
            this.root.save();
        }
        this.plugin.getLogger().info("Configurazioni caricate.");
    }

    /**
     * Prepara tutti i YAML in memoria senza modificare i file o la snapshot attiva.
     */
    public ReloadCandidate prepareReload() throws Exception {
        Snapshot snapshot = loadSnapshot(true);
        int hologramFiles = validateHologramFiles(snapshot.holograms().config());
        return new ReloadCandidate(snapshot, 17, hologramFiles);
    }

    public Snapshot current() {
        return new Snapshot(
                this.root, this.storage, this.messages, this.vanilla, this.sounds,
                this.worlds, this.homes, this.warps, this.players, this.moderation,
                this.mechanics, this.holograms, this.kits, this.prices, this.chat, this.webhooks, this.tomb
        );
    }

    public void activate(Snapshot snapshot) {
        this.root = snapshot.root();
        this.storage = snapshot.storage();
        this.messages = snapshot.messages();
        this.vanilla = snapshot.vanilla();
        this.sounds = snapshot.sounds();
        this.worlds = snapshot.worlds();
        this.homes = snapshot.homes();
        this.warps = snapshot.warps();
        this.players = snapshot.players();
        this.moderation = snapshot.moderation();
        this.mechanics = snapshot.mechanics();
        this.holograms = snapshot.holograms();
        this.kits = snapshot.kits();
        this.prices = snapshot.prices();
        this.chat = snapshot.chat();
        this.webhooks = snapshot.webhooks();
        this.tomb = snapshot.tomb();
    }

    public List<String> loadedPaths(Snapshot snapshot) {
        return List.of(
                snapshot.root().file().getAbsolutePath(),
                snapshot.storage().file().getAbsolutePath(),
                snapshot.messages().file().getAbsolutePath(),
                snapshot.vanilla().file().getAbsolutePath(),
                snapshot.sounds().file().getAbsolutePath(),
                snapshot.worlds().file().getAbsolutePath(),
                snapshot.homes().file().getAbsolutePath(),
                snapshot.warps().file().getAbsolutePath(),
                snapshot.players().file().getAbsolutePath(),
                snapshot.moderation().file().getAbsolutePath(),
                snapshot.mechanics().file().getAbsolutePath(),
                snapshot.holograms().file().getAbsolutePath(),
                snapshot.kits().file().getAbsolutePath(),
                snapshot.prices().file().getAbsolutePath(),
                snapshot.chat().file().getAbsolutePath(),
                snapshot.webhooks().file().getAbsolutePath(),
                snapshot.tomb().file().getAbsolutePath()
        );
    }

    public FileConfiguration root() {
        return this.root.config();
    }

    public FileConfiguration messages() {
        return this.messages.config();
    }

    public FileConfiguration vanilla() {
        return this.vanilla.config();
    }

    public FileConfiguration storage() {
        return this.storage.config();
    }

    public FileConfiguration sounds() {
        return this.sounds.config();
    }

    public FileConfiguration worlds() {
        return this.worlds.config();
    }

    public FileConfiguration homes() {
        return this.homes.config();
    }

    public FileConfiguration warps() {
        return this.warps.config();
    }

    public FileConfiguration players() {
        return this.players.config();
    }

    public FileConfiguration moderation() {
        return this.moderation.config();
    }

    public FileConfiguration mechanics() {
        return this.mechanics.config();
    }

    public FileConfiguration holograms() {
        return this.holograms.config();
    }

    public FileConfiguration kits() {
        return this.kits.config();
    }

    public FileConfiguration prices() {
        return this.prices.config();
    }

    public FileConfiguration chat() {
        return this.chat.config();
    }

    public FileConfiguration webhooks() {
        return this.webhooks.config();
    }

    public FileConfiguration tomb() {
        return this.tomb.config();
    }

    public void saveWorlds() {
        this.worlds.save();
    }

    public void saveWarps() {
        this.warps.save();
    }

    public void saveMechanics() {
        this.mechanics.save();
    }

    private void migrateSpawnOnJoin() {
        if (this.worlds.config().getBoolean("settings.spawn-on-join", false)) {
            return;
        }
        this.worlds.config().set("settings.spawn-on-join", true);
        this.worlds.save();
        this.plugin.getLogger().info("Login impostato sempre allo spawn globale.");
    }

    private void migrateLegacySounds() {
        ConfigurationSection old = this.root.config().getConfigurationSection("sounds");
        if (old == null) {
            return;
        }
        boolean changed = false;
        for (String key : old.getKeys(false)) {
            if (this.sounds.config().contains(key)) {
                continue;
            }
            this.sounds.config().set(key + ".sound", old.getString(key));
            this.sounds.config().set(key + ".volume", 1.0);
            this.sounds.config().set(key + ".pitch", 1.0);
            changed = true;
        }
        if (changed) {
            this.sounds.save();
            this.plugin.getLogger().info("Suoni migrati da config.yml a sounds/sounds.yml");
        }
    }

    /**
     * Il primo hologram usava font uniform + box nero nametag: se i valori sono ancora quelli, passa allo stile senza riquadro.
     */
    private void migrateGroundItemHologram() {
        FileConfiguration yaml = this.mechanics.config();
        boolean changed = false;
        if (yaml.getInt("ground-items.background") == 1073741824) {
            yaml.set("ground-items.background", 0);
            changed = true;
        }
        if ("minecraft:uniform".equals(yaml.getString("ground-items.font"))) {
            yaml.set("ground-items.font", "");
            changed = true;
        }
        if (yaml.getBoolean("ground-items.see-through") && yaml.getInt("ground-items.background") == 0) {
            yaml.set("ground-items.see-through", false);
            changed = true;
        }
        if (yaml.getDouble("ground-items.scale") == 0.45D) {
            yaml.set("ground-items.scale", 0.8D);
            changed = true;
        }
        if ("<white>{name} <gray>x{amount}".equals(yaml.getString("ground-items.name-format"))) {
            yaml.set("ground-items.name-format", "{name} <dark_gray>×<white>{amount}");
            changed = true;
        }
        if (changed) {
            this.mechanics.save();
            this.plugin.getLogger().info("Hologram item a terra aggiornato (niente box nametag).");
        }
    }

    /**
     * Le schermate vecchie erano stringhe: il merge non le converte in liste.
     */
    private void migrateDisconnectScreens() {
        FileConfiguration yaml = this.messages.config();
        YamlConfiguration defaults = jarMessages();
        if (defaults == null) {
            return;
        }
        boolean changed = false;
        for (String key : new String[]{
                "moderation.kick-screen",
                "moderation.ban-screen",
                "moderation.ipban-screen"
        }) {
            if (yaml.isList(key)) {
                continue;
            }
            yaml.set(key, defaults.get(key));
            changed = true;
        }
        if ("{hours}h {minutes}m".equals(yaml.getString("duration.hours"))) {
            yaml.set("duration.hours", defaults.getString("duration.hours"));
            changed = true;
        }
        if ("{days}d {hours}h".equals(yaml.getString("duration.days"))) {
            yaml.set("duration.days", defaults.getString("duration.days"));
            changed = true;
        }
        if (changed) {
            this.messages.save();
            this.plugin.getLogger().info("Schermate kick/ban migrate in lang/messages.yml");
        }
    }

    /**
     * /holo collide con DecentHolograms e simili: passa a /vholo senza toccare i valori custom.
     */
    private void migrateHoloCommand() {
        FileConfiguration root = this.root.config();
        boolean rootChanged = false;
        if ("holo".equalsIgnoreCase(root.getString("commands.holo", ""))) {
            root.set("commands.holo", "vholo");
            rootChanged = true;
        }
        List<String> aliases = new ArrayList<>(root.getStringList("commands.holo-aliases"));
        List<String> cleaned = new ArrayList<>();
        boolean aliasesChanged = false;
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String token = alias.toLowerCase(java.util.Locale.ROOT);
            if (token.equals("holo") || token.equals("holos") || token.equals("hologram") || token.equals("holograms")) {
                aliasesChanged = true;
                continue;
            }
            cleaned.add(alias);
        }
        if (rootChanged && !containsIgnoreCase(cleaned, "vholos")) {
            cleaned.add("vholos");
            aliasesChanged = true;
        }
        if (aliasesChanged) {
            root.set("commands.holo-aliases", cleaned);
            rootChanged = true;
        }
        if (rootChanged) {
            this.root.save();
        }

        boolean textsChanged = rewriteSlashHolo(this.messages.config());
        if (textsChanged) {
            this.messages.save();
        }
        boolean mechanicsChanged = rewriteSlashHolo(this.mechanics.config());
        if (mechanicsChanged) {
            this.mechanics.save();
        }
        if (rootChanged || textsChanged || mechanicsChanged) {
            this.plugin.getLogger().info("Comando hologram migrato da /holo a /vholo.");
        }
    }

    /**
     * I trigger su blocco non usano più /vholo: quello resta per gli hologram in holo/.
     */
    private void migrateTriggerblockCommand() {
        FileConfiguration root = this.root.config();
        boolean changed = false;
        if (!root.contains("commands.triggerblock")) {
            root.set("commands.triggerblock", "triggerblock");
            root.set("commands.triggerblock-aliases", List.of("tblock", "triggerblocks"));
            changed = true;
        }
        if (!root.contains("permissions.triggerblock")) {
            root.set("permissions.triggerblock", "valary.triggerblock");
            changed = true;
        }
        if (!root.contains("modules.holograms")) {
            root.set("modules.holograms", true);
            changed = true;
        }
        if (changed) {
            this.root.save();
        }
        boolean texts = rewriteTriggerLabel(this.messages.config(), "holos.");
        if (texts) {
            this.messages.save();
        }
        boolean mechanics = rewriteTriggerLabel(this.mechanics.config(), "holo-blocks.");
        if (mechanics) {
            this.mechanics.save();
        }
        if (changed || texts || mechanics) {
            this.plugin.getLogger().info("Trigger su blocco migrati da /vholo a /triggerblock.");
        }
    }

    private static boolean rewriteTriggerLabel(FileConfiguration yaml, String prefix) {
        boolean changed = false;
        for (String key : yaml.getKeys(true)) {
            if (!key.startsWith(prefix) || yaml.isConfigurationSection(key)) {
                continue;
            }
            if (yaml.isList(key)) {
                List<String> lines = yaml.getStringList(key);
                List<String> next = new ArrayList<>();
                boolean listChanged = false;
                for (String line : lines) {
                    String updated = rewriteVholoToTrigger(line);
                    next.add(updated);
                    if (!updated.equals(line)) {
                        listChanged = true;
                    }
                }
                if (listChanged) {
                    yaml.set(key, next);
                    changed = true;
                }
                continue;
            }
            String value = yaml.getString(key);
            String updated = rewriteVholoToTrigger(value);
            if (value != null && !updated.equals(value)) {
                yaml.set(key, updated);
                changed = true;
            }
        }
        return changed;
    }

    private static String rewriteVholoToTrigger(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("/vholos", "/triggerblocks").replace("/vholo", "/triggerblock");
    }

    private static boolean rewriteSlashHolo(FileConfiguration yaml) {
        boolean changed = false;
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) {
                continue;
            }
            if (yaml.isList(key)) {
                List<String> lines = yaml.getStringList(key);
                List<String> next = new ArrayList<>();
                boolean listChanged = false;
                for (String line : lines) {
                    String updated = rewriteSlashHolo(line);
                    next.add(updated);
                    if (!updated.equals(line)) {
                        listChanged = true;
                    }
                }
                if (listChanged) {
                    yaml.set(key, next);
                    changed = true;
                }
                continue;
            }
            String value = yaml.getString(key);
            String updated = rewriteSlashHolo(value);
            if (value != null && !updated.equals(value)) {
                yaml.set(key, updated);
                changed = true;
            }
        }
        return changed;
    }

    private static String rewriteSlashHolo(String value) {
        if (value == null || !value.contains("/holo")) {
            return value;
        }
        return value.replaceAll("/holo(?![A-Za-z])", "/vholo");
    }

    private static boolean containsIgnoreCase(List<String> values, String expected) {
        for (String value : values) {
            if (expected.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private YamlConfiguration jarMessages() {
        try (InputStream input = this.plugin.getResource("lang/messages.yml")) {
            if (input == null) {
                return null;
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return defaults;
        } catch (Exception exception) {
            return null;
        }
    }

    private Snapshot loadSnapshot(boolean strict) throws RuntimeException {
        try {
            return new Snapshot(
                    load("config.yml", strict),
                    load("storage.yml", strict),
                    load("lang/messages.yml", strict),
                    load("lang/vanilla.yml", strict),
                    load("sounds/sounds.yml", strict),
                    load("modules/worlds.yml", strict),
                    load("modules/homes.yml", strict),
                    load("modules/warps.yml", strict),
                    load("modules/players.yml", strict),
                    load("modules/moderation.yml", strict),
                    load("modules/mechanics.yml", strict),
                    load("modules/holograms.yml", strict),
                    load("modules/kits.yml", strict),
                    load("modules/prices.yml", strict),
                    load("modules/chat.yml", strict),
                    load("modules/webhooks.yml", strict),
                    load("modules/tomb.yml", strict)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("YAML non valido: " + exception.getMessage(), exception);
        }
    }

    private YamlFile load(String fileName, boolean strict) throws Exception {
        YamlFile yaml = new YamlFile(this.plugin, fileName);
        if (strict) {
            yaml.loadStrict();
        } else {
            yaml.load();
        }
        return yaml;
    }

    private int validateHologramFiles(FileConfiguration settings) throws Exception {
        File folder = new File(this.plugin.getDataFolder(), settings.getString("folder", "holo"));
        if (!folder.exists()) {
            return 0;
        }
        if (!folder.isDirectory()) {
            throw new IllegalStateException("La cartella hologram non è una directory: " + folder.getAbsolutePath());
        }
        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml")
                        && !name.equalsIgnoreCase("holograms.yml"));
        if (files == null) {
            throw new IllegalStateException("Impossibile leggere la cartella hologram: " + folder.getAbsolutePath());
        }
        for (File file : files) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
            } catch (Exception exception) {
                throw new IllegalStateException("Hologram YAML non valido: " + file.getAbsolutePath(), exception);
            }
        }
        return files.length;
    }

    public record ReloadCandidate(Snapshot snapshot, int managedFiles, int hologramFiles) {
    }

    public record Snapshot(
            YamlFile root,
            YamlFile storage,
            YamlFile messages,
            YamlFile vanilla,
            YamlFile sounds,
            YamlFile worlds,
            YamlFile homes,
            YamlFile warps,
            YamlFile players,
            YamlFile moderation,
            YamlFile mechanics,
            YamlFile holograms,
            YamlFile kits,
            YamlFile prices,
            YamlFile chat,
            YamlFile webhooks,
            YamlFile tomb
    ) {
    }
}
