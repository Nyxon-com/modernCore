package com.valaryrpg.core.module.mechanics;

import com.valaryrpg.core.ValaryCorePlugin;
import com.valaryrpg.core.gui.PanelItems;
import com.valaryrpg.core.util.ActionCommands;
import com.valaryrpg.core.util.MmoItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalService {

    private final ValaryCorePlugin plugin;
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> dest = new ConcurrentHashMap<>();

    public PortalService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void tryEnter(Player player, Location from, Location to) {
        if (to.getWorld() == null) {
            return;
        }
        String entered = nameAt(to);
        if (entered == null || entered.equals(nameAt(from))) {
            return;
        }
        ConfigurationSection portal = this.plugin.configs().mechanics().getConfigurationSection("portals." + entered);
        if (portal == null || !portal.getBoolean("enabled", true)) {
            return;
        }
        String permission = portal.getString("permission", "");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            this.plugin.messages().send(player, "portals.no-permission", Map.of("portal", entered));
            return;
        }
        ConfigurationSection item = portal.getConfigurationSection("item");
        if (item != null && !consumeOrMatch(player, item)) {
            this.plugin.messages().send(player, "portals.need-item", Map.of("portal", entered));
            return;
        }
        this.plugin.lastLocations().remember(player);
        List<String> commands = portal.getStringList("commands");
        if (!commands.isEmpty()) {
            ActionCommands.run(this.plugin, player, commands, Map.of("portal", entered));
        }
        Location destination = destinationOf(portal);
        if (destination == null) {
            if (commands.isEmpty()) {
                this.plugin.messages().send(player, "portals.invalid-destination", Map.of("portal", entered));
            } else {
                this.plugin.messages().send(player, "portals.used", Map.of("portal", entered));
            }
            return;
        }
        int warmup = portal.getInt("warmup-seconds", 0);
        if (warmup > 0) {
            this.plugin.messages().send(player, "portals.warmup", Map.of(
                    "portal", entered,
                    "seconds", String.valueOf(warmup)
            ));
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (player.isOnline()) {
                    player.teleport(destination);
                }
            }, warmup * 20L);
        } else {
            player.teleport(destination);
        }
        this.plugin.messages().send(player, "portals.used", Map.of("portal", entered));
    }

    public void markPos1(Player player) {
        markPos1(player, player.getLocation());
    }

    public void markPos1(Player player, Location location) {
        this.pos1.put(player.getUniqueId(), location.clone());
        this.plugin.messages().send(player, "portals.pos1");
    }

    public void markPos2(Player player) {
        markPos2(player, player.getLocation());
    }

    public void markPos2(Player player, Location location) {
        this.pos2.put(player.getUniqueId(), location.clone());
        this.plugin.messages().send(player, "portals.pos2");
    }

    public void markDest(Player player) {
        this.dest.put(player.getUniqueId(), player.getLocation().clone());
        this.plugin.messages().send(player, "portals.dest");
    }

    public void create(Player player, String name) {
        String id = normalize(name);
        if (id.isBlank()) {
            this.plugin.messages().send(player, "portals.invalid-name");
            return;
        }
        Location first = this.pos1.get(player.getUniqueId());
        Location second = this.pos2.get(player.getUniqueId());
        Location destination = this.dest.get(player.getUniqueId());
        if (first == null || second == null
                || first.getWorld() == null || second.getWorld() == null) {
            this.plugin.messages().send(player, "portals.incomplete");
            return;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            this.plugin.messages().send(player, "portals.world-mismatch");
            return;
        }
        FileConfiguration yaml = this.plugin.configs().mechanics();
        String base = "portals." + id;
        yaml.set(base + ".enabled", true);
        yaml.set(base + ".world", first.getWorld().getName());
        writeLocation(yaml, base + ".pos1", first, false);
        writeLocation(yaml, base + ".pos2", second, false);
        if (destination != null && destination.getWorld() != null) {
            writeLocation(yaml, base + ".destination", destination, true);
        }
        yaml.set(base + ".commands", List.of());
        yaml.set(base + ".item.material", "AIR");
        yaml.set(base + ".item.mmoitem-type", "");
        yaml.set(base + ".item.mmoitem-id", "");
        yaml.set(base + ".item.consume", false);
        yaml.set(base + ".item.amount", 1);
        yaml.set(base + ".permission", "");
        yaml.set(base + ".warmup-seconds", 0);
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(player, "portals.created", Map.of("portal", id));
    }

    public void setItem(Player player, String name) {
        String id = requirePortal(player, name);
        if (id == null) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        FileConfiguration yaml = this.plugin.configs().mechanics();
        String base = "portals." + id + ".item";
        if (hand == null || hand.getType().isAir()) {
            yaml.set(base + ".material", "AIR");
            yaml.set(base + ".mmoitem-type", "");
            yaml.set(base + ".mmoitem-id", "");
            yaml.set(base + ".consume", false);
            this.plugin.configs().saveMechanics();
            this.plugin.messages().send(player, "portals.item-cleared", Map.of("portal", id));
            return;
        }
        yaml.set(base + ".material", hand.getType().name());
        yaml.set(base + ".amount", Math.max(1, hand.getAmount()));
        yaml.set(base + ".consume", true);
        String[] nbt = MmoItems.typeAndId(hand);
        if (nbt != null && isMmoId(nbt[0]) && isMmoId(nbt[1])) {
            yaml.set(base + ".mmoitem-type", nbt[0]);
            yaml.set(base + ".mmoitem-id", nbt[1]);
        } else {
            yaml.set(base + ".mmoitem-type", "");
            yaml.set(base + ".mmoitem-id", "");
        }
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(player, "portals.item-set", Map.of("portal", id));
    }

    public void enable(CommandSender sender, String name, boolean enabled) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        this.plugin.configs().mechanics().set("portals." + id + ".enabled", enabled);
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(sender, enabled ? "portals.enabled" : "portals.disabled", Map.of("portal", id));
    }

    public void delete(CommandSender sender, String name) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        this.plugin.configs().mechanics().set("portals." + id, null);
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(sender, "portals.deleted", Map.of("portal", id));
    }

    public void list(CommandSender sender) {
        Set<String> names = names();
        if (names.isEmpty()) {
            this.plugin.messages().send(sender, "portals.list-empty");
            return;
        }
        this.plugin.messages().send(sender, "portals.list", Map.of(
                "count", String.valueOf(names.size()),
                "portals", String.join(", ", names)
        ));
    }

    public Set<String> names() {
        ConfigurationSection portals = this.plugin.configs().mechanics().getConfigurationSection("portals");
        return portals == null ? Set.of() : portals.getKeys(false);
    }

    public void giveWand(Player player) {
        ConfigurationSection wand = this.plugin.configs().mechanics().getConfigurationSection("portal-wand");
        ItemStack item = wand == null
                ? PanelItems.named("BLAZE_ROD", Material.BLAZE_ROD)
                : PanelItems.from(this.plugin, wand, player);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(wandKey(), PersistentDataType.BYTE, (byte) 1));
        player.getInventory().addItem(item);
        this.plugin.messages().send(player, "portals.wand-given");
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey(), PersistentDataType.BYTE);
    }

    public void handleWand(Player player, Block block, boolean sneak, boolean leftClick) {
        Location location = block.getLocation().add(0.5D, 0.0D, 0.5D);
        if (leftClick && sneak) {
            this.dest.put(player.getUniqueId(), player.getLocation().clone());
            this.plugin.messages().send(player, "portals.dest");
            return;
        }
        if (sneak) {
            markPos2(player, location);
            return;
        }
        markPos1(player, location);
    }

    public void setCommand(CommandSender sender, String name, String command) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        List<String> commands = this.plugin.configs().mechanics().getStringList("portals." + id + ".commands");
        commands = new java.util.ArrayList<>(commands);
        commands.add(command);
        this.plugin.configs().mechanics().set("portals." + id + ".commands", commands);
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(sender, "portals.command-set", Map.of(
                "portal", id,
                "command", command
        ));
    }

    public void clearCommands(CommandSender sender, String name) {
        String id = requirePortal(sender, name);
        if (id == null) {
            return;
        }
        this.plugin.configs().mechanics().set("portals." + id + ".commands", List.of());
        this.plugin.configs().saveMechanics();
        this.plugin.messages().send(sender, "portals.commands-cleared", Map.of("portal", id));
    }

    private NamespacedKey wandKey() {
        return new NamespacedKey(this.plugin, "portal_wand");
    }

    private String requirePortal(CommandSender sender, String name) {
        String id = normalize(name);
        if (this.plugin.configs().mechanics().getConfigurationSection("portals." + id) == null) {
            this.plugin.messages().send(sender, "portals.unknown", Map.of("portal", name));
            return null;
        }
        return id;
    }

    private static void writeLocation(FileConfiguration yaml, String path, Location location, boolean withWorld) {
        if (withWorld && location.getWorld() != null) {
            yaml.set(path + ".world", location.getWorld().getName());
        }
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        if (withWorld) {
            yaml.set(path + ".yaw", location.getYaw());
            yaml.set(path + ".pitch", location.getPitch());
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    private static boolean isMmoId(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    private String nameAt(Location location) {
        ConfigurationSection portals = this.plugin.configs().mechanics().getConfigurationSection("portals");
        if (portals == null || location.getWorld() == null) {
            return null;
        }
        for (String name : portals.getKeys(false)) {
            ConfigurationSection portal = portals.getConfigurationSection(name);
            if (portal != null && portal.getBoolean("enabled", true) && inside(portal, location)) {
                return name;
            }
        }
        return null;
    }

    private boolean inside(ConfigurationSection portal, Location location) {
        String worldName = portal.getString("world", "");
        World world = location.getWorld();
        if (world == null || !world.getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        ConfigurationSection pos1 = portal.getConfigurationSection("pos1");
        ConfigurationSection pos2 = portal.getConfigurationSection("pos2");
        if (pos1 == null || pos2 == null) {
            return false;
        }
        double minX = Math.min(pos1.getDouble("x"), pos2.getDouble("x"));
        double maxX = Math.max(pos1.getDouble("x"), pos2.getDouble("x"));
        double minY = Math.min(pos1.getDouble("y"), pos2.getDouble("y"));
        double maxY = Math.max(pos1.getDouble("y"), pos2.getDouble("y"));
        double minZ = Math.min(pos1.getDouble("z"), pos2.getDouble("z"));
        double maxZ = Math.max(pos1.getDouble("z"), pos2.getDouble("z"));
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= minX && x <= maxX + 1.0D && y >= minY && y <= maxY + 1.0D && z >= minZ && z <= maxZ + 1.0D;
    }

    private Location destinationOf(ConfigurationSection portal) {
        ConfigurationSection dest = portal.getConfigurationSection("destination");
        if (dest == null) {
            return null;
        }
        World world = Bukkit.getWorld(dest.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                dest.getDouble("x"),
                dest.getDouble("y"),
                dest.getDouble("z"),
                (float) dest.getDouble("yaw"),
                (float) dest.getDouble("pitch")
        );
    }

    private boolean consumeOrMatch(Player player, ConfigurationSection item) {
        Material material = Material.matchMaterial(item.getString("material", "AIR"));
        String type = item.getString("mmoitem-type", "");
        String id = item.getString("mmoitem-id", "");
        int amount = Math.max(1, item.getInt("amount", 1));
        if ((material == null || material.isAir()) && (type == null || type.isBlank()) && (id == null || id.isBlank())) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        int slot = findSlot(inventory, material, type, id, amount);
        if (slot < 0) {
            return false;
        }
        if (!item.getBoolean("consume", true)) {
            return true;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack == null) {
            return false;
        }
        int left = stack.getAmount() - amount;
        if (left <= 0) {
            inventory.setItem(slot, null);
        } else {
            stack.setAmount(left);
        }
        return true;
    }

    private int findSlot(PlayerInventory inventory, Material material, String type, String id, int amount) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir() || stack.getAmount() < amount) {
                continue;
            }
            if (material != null && material != Material.AIR && stack.getType() != material) {
                continue;
            }
            if (!MmoItems.matches(stack, type, id)) {
                continue;
            }
            return i;
        }
        return -1;
    }
}
