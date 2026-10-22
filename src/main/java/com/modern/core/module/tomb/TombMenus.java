//made with love by nyxon

package com.modern.core.module.tomb;

import com.modern.core.ModernCorePlugin;
import com.modern.core.gui.GuiLayouts;
import com.modern.core.gui.MenuHolder;
import com.modern.core.gui.PanelClicks;
import com.modern.core.gui.PanelFrames;
import com.modern.core.gui.PanelItems;
import com.modern.core.gui.SlotSpec;
import com.modern.core.module.players.PlayerProfile;
import com.modern.core.module.players.PlayersModule;
import com.modern.core.util.Durations;
import com.modern.core.util.ItemStacks;
import com.modern.core.util.Sounds;
import com.modern.core.util.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TombMenus {

  public static final String LIST = "tomb-list";
  public static final String VIEW = "tomb-view";

  private final ModernCorePlugin plugin;
  private final TombService tombs;

  public TombMenus(ModernCorePlugin plugin, TombService tombs) {
    this.plugin = plugin;
    this.tombs = tombs;
  }

  public void openList(Player player, int page) {
    int safePage = Math.max(1, page);
    ConfigurationSection gui = listGui();
    List<Integer> slots = SlotSpec.expand(gui == null ? "10-16,19-25,28-34" : gui.getString("tomb-slots", "10-16,19-25,28-34"));
    int pageSize = Math.max(1, slots.size());
    Tasks.async(this.plugin, () -> {
      int total = this.tombs.repository().count(player.getUniqueId());
      int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
      int current = Math.min(safePage, pages);
      List<Tomb> entries = this.tombs.repository().list(player.getUniqueId(), pageSize, (current - 1) * pageSize);
      Tasks.sync(this.plugin, () -> renderList(player, current, pages, entries, slots));
    });
  }

  public void openView(Player player, Tomb tomb, int fromPage) {
    ConfigurationSection gui = viewGui();
    int rows = GuiLayouts.rows(gui, 6);
    MenuHolder holder = new MenuHolder(VIEW, Math.max(1, fromPage), String.valueOf(tomb.id()));
    String titleRaw = gui == null ? "<gray>Tomba #{id}" : gui.getString("title", "<gray>Tomba #{id}");
    Component title = this.plugin.messages().deserialize(apply(titleRaw, placeholders(player, tomb)));
    Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
    holder.inventory(inventory);
    fillBase(inventory, gui, player, tomb);
    fillContents(inventory, gui, ItemStacks.decode(tomb.inventory()));
    fillButtons(inventory, gui, player, tomb);
    player.openInventory(inventory);
    PanelClicks.openSound(this.plugin, player, gui);
  }

  public void clickList(Player player, int slot, int page) {
    ConfigurationSection gui = listGui();
    ConfigurationSection button = GuiLayouts.button(gui, slot);
    if (button != null) {
      String action = GuiLayouts.action(button);
      PanelClicks.run(this.plugin, player, button);
      switch (action) {
        case "close" -> player.closeInventory();
        case "page" -> {
          String dir = button.getString("page", "");
          if ("prev".equalsIgnoreCase(dir)) {
            openList(player, page - 1);
          } else if ("next".equalsIgnoreCase(dir)) {
            openList(player, page + 1);
          } else {
            openList(player, button.getInt("page", page));
          }
        }
        default -> {
        }
      }
      return;
    }
    List<Integer> slots = SlotSpec.expand(gui == null ? "10-16,19-25,28-34" : gui.getString("tomb-slots", "10-16,19-25,28-34"));
    int index = slots.indexOf(slot);
    if (index < 0) {
      return;
    }
    int pageSize = Math.max(1, slots.size());
    Tasks.async(this.plugin, () -> {
      List<Tomb> entries = this.tombs.repository().list(player.getUniqueId(), pageSize, (Math.max(1, page) - 1) * pageSize);
      if (index >= entries.size()) {
        return;
      }
      Tomb tomb = entries.get(index);
      Tasks.sync(this.plugin, () -> this.tombs.openView(player, tomb.id(), page));
    });
  }

  public void clickView(Player player, int slot, MenuHolder holder) {
    ConfigurationSection gui = viewGui();
    ConfigurationSection button = GuiLayouts.button(gui, slot);
    if (button == null) {
      return;
    }
    String action = GuiLayouts.action(button);
    PanelClicks.run(this.plugin, player, button);
    long tombId = parseLong(holder.extra());
    switch (action) {
      case "close" -> player.closeInventory();
      case "back" -> openList(player, holder.page());
      case "claim" -> this.tombs.claim(player, tombId);
      default -> {
      }
    }
  }

  private void renderList(Player player, int page, int pages, List<Tomb> entries, List<Integer> slots) {
    ConfigurationSection gui = listGui();
    int rows = GuiLayouts.rows(gui, 6);
    MenuHolder holder = new MenuHolder(LIST, page, pages + ":" + entries.size());
    Map<String, String> base = this.tombs.economyPlaceholders(player);
    base.put("page", String.valueOf(page));
    base.put("pages", String.valueOf(pages));
    putPlaytime(player, base);
    String titleRaw = gui == null ? "<gray>Tombe" : gui.getString("title", "<gray>Tombe");
    Inventory inventory = Bukkit.createInventory(holder, rows * 9, this.plugin.messages().deserialize(apply(titleRaw, base)));
    holder.inventory(inventory);
    fillBase(inventory, gui, player, null);
    fillButtons(inventory, gui, player, null);
    if (entries.isEmpty()) {
      ConfigurationSection empty = gui == null ? null : gui.getConfigurationSection("empty-icon");
      if (empty != null && !slots.isEmpty()) {
        inventory.setItem(slots.get(0), icon(empty, base));
      }
    } else {
      ConfigurationSection tombIcon = gui == null ? null : gui.getConfigurationSection("tomb-icon");
      for (int i = 0; i < entries.size() && i < slots.size(); i++) {
        Tomb tomb = entries.get(i);
        inventory.setItem(slots.get(i), icon(tombIcon, placeholders(player, tomb)));
      }
    }
    player.openInventory(inventory);
    PanelClicks.openSound(this.plugin, player, gui);
    Sounds.playRaw(this.plugin, player, this.tombs.yaml().getString("sounds.open", ""), "tomb.open");
    this.plugin.messages().send(player, "playtime.self", Map.of(
        "player", this.plugin.messages().escape(player.getName()),
        "playtime", base.getOrDefault("playtime", "-")
    ));
  }

  private void putPlaytime(Player player, Map<String, String> base) {
    base.put("playtime", "-");
    if (this.plugin.modules() == null || !this.plugin.modules().isEnabled("players")) {
      return;
    }
    PlayersModule players = this.plugin.modules().get("players");
    if (players == null || players.playtime() == null || players.repository() == null) {
      return;
    }
    PlayerProfile profile = players.repository().find(player.getUniqueId()).orElse(null);
    long stored = profile == null ? 0L : profile.playtimeMs();
    base.put("playtime", players.playtime().format(players.playtime().liveMillis(player.getUniqueId(), stored)));
  }

  private void fillBase(Inventory inventory, ConfigurationSection gui, Player player, Tomb tomb) {
    if (gui != null && gui.getConfigurationSection("fill") != null && gui.getConfigurationSection("fill").getBoolean("enabled", true)) {
      ItemStack fill = PanelItems.filler(this.plugin, gui);
      for (int i = 0; i < inventory.getSize(); i++) {
        inventory.setItem(i, fill.clone());
      }
    }
    PanelFrames.apply(this.plugin, inventory, gui);
  }

  private void fillButtons(Inventory inventory, ConfigurationSection gui, Player player, Tomb tomb) {
    if (gui == null) {
      return;
    }
    ConfigurationSection slots = gui.getConfigurationSection("slots");
    if (slots == null) {
      return;
    }
    Map<String, String> placeholders = tomb == null ? this.tombs.economyPlaceholders(player) : placeholders(player, tomb);
    for (String key : slots.getKeys(false)) {
      ConfigurationSection button = slots.getConfigurationSection(key);
      if (button == null) {
        continue;
      }
      ItemStack item = icon(button, placeholders);
      for (int slot : SlotSpec.expand(key)) {
        if (slot >= 0 && slot < inventory.getSize()) {
          inventory.setItem(slot, item.clone());
        }
      }
    }
  }

  private void fillContents(Inventory inventory, ConfigurationSection gui, ItemStack[] packed) {
    ItemStack[] items = packed == null ? new ItemStack[0] : packed;
    ConfigurationSection armor = gui == null ? null : gui.getConfigurationSection("armor-slots");
    int helmet = armor == null ? 0 : armor.getInt("helmet", 0);
    int chest = armor == null ? 1 : armor.getInt("chestplate", 1);
    int legs = armor == null ? 2 : armor.getInt("leggings", 2);
    int boots = armor == null ? 3 : armor.getInt("boots", 3);
    int off = armor == null ? 4 : armor.getInt("offhand", 4);
    set(inventory, helmet, at(items, 36));
    set(inventory, chest, at(items, 37));
    set(inventory, legs, at(items, 38));
    set(inventory, boots, at(items, 39));
    set(inventory, off, at(items, 40));
    ConfigurationSection filler = gui == null ? null : gui.getConfigurationSection("filler");
    ItemStack pane = PanelItems.from(this.plugin, filler, null);
    if (pane.getType().isAir()) {
      pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      pane.editMeta(meta -> meta.displayName(Component.empty()));
    }
    for (int slot : SlotSpec.expand(gui == null ? "5-8" : gui.getString("filler-slots", "5-8"))) {
      set(inventory, slot, pane.clone());
    }
    int start = gui == null ? 9 : gui.getInt("contents-start", 9);
    for (int i = 0; i < 36; i++) {
      set(inventory, start + i, at(items, i));
    }
  }

  private Map<String, String> placeholders(Player player, Tomb tomb) {
    Map<String, String> map = this.tombs.economyPlaceholders(player);
    map.put("id", String.valueOf(tomb.id()));
    map.put("when", Durations.formatWhen(this.plugin, tomb.createdAt()));
    map.put("world", tomb.world() == null || tomb.world().isBlank() ? "-" : tomb.world());
    map.put("coords", String.format(Locale.ROOT, "%.0f %.0f %.0f", tomb.x(), tomb.y(), tomb.z()));
    map.put("cause", tomb.cause().isBlank() ? "-" : tomb.cause());
    map.put("killer", tomb.killer().isBlank() ? "-" : tomb.killer());
    map.put("xp", String.valueOf(tomb.xp()));
    map.put("items", String.valueOf(countItems(ItemStacks.decode(tomb.inventory()))));
    return map;
  }

  private ItemStack icon(ConfigurationSection section, Map<String, String> placeholders) {
    if (section == null) {
      return new ItemStack(Material.STONE);
    }
    MemoryConfiguration copy = new MemoryConfiguration();
    for (String key : section.getKeys(false)) {
      Object value = section.get(key);
      if (value instanceof String text) {
        copy.set(key, apply(text, placeholders));
      } else if (value instanceof List<?> list) {
        copy.set(key, list.stream().map(entry -> entry instanceof String line ? apply(line, placeholders) : entry).toList());
      } else {
        copy.set(key, value);
      }
    }
    return PanelItems.from(this.plugin, copy, null);
  }

  private static String apply(String raw, Map<String, String> placeholders) {
    if (raw == null) {
      return "";
    }
    String out = raw;
    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
      out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
    }
    return out;
  }

  private ConfigurationSection listGui() {
    return this.tombs.yaml().getConfigurationSection("gui-list");
  }

  private ConfigurationSection viewGui() {
    return this.tombs.yaml().getConfigurationSection("gui-view");
  }

  private static void set(Inventory inventory, int slot, ItemStack item) {
    if (slot >= 0 && slot < inventory.getSize()) {
      inventory.setItem(slot, item == null ? new ItemStack(Material.AIR) : item);
    }
  }

  private static ItemStack at(ItemStack[] items, int index) {
    if (items == null || index < 0 || index >= items.length || items[index] == null) {
      return new ItemStack(Material.AIR);
    }
    return items[index].clone();
  }

  private static long parseLong(String raw) {
    try {
      return Long.parseLong(raw);
    } catch (Exception exception) {
      return -1L;
    }
  }

  private static int countItems(ItemStack[] items) {
    int count = 0;
    if (items == null) {
      return 0;
    }
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir()) {
        count += item.getAmount();
      }
    }
    return count;
  }
}
