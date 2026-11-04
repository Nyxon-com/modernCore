package com.modern.core.module.kits;

import com.modern.core.gui.GuiLayouts;
import com.modern.core.gui.MenuHolder;
import com.modern.core.gui.PanelClicks;
import com.modern.core.gui.PanelFrames;
import com.modern.core.gui.PanelItems;
import com.modern.core.gui.Panels;
import com.modern.core.gui.SlotSpec;
import com.modern.core.util.Durations;
import com.modern.core.util.Perms;
import com.modern.core.util.Sounds;
import com.modern.core.util.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class KitMenus {

  private final KitService kits;

  KitMenus(KitService kits) {
    this.kits = kits;
  }

  void open(Player player) {
    open(player, 1);
  }

  void open(Player player, int page) {
    render(player, page, this.kits.claims.snapshot(player.getUniqueId()));
    if (this.kits.claims.loaded(player.getUniqueId())) {
      return;
    }
    UUID uuid = player.getUniqueId();
    Tasks.async(this.kits.plugin, () -> {
      try {
        this.kits.claims.lastClaims(uuid);
      } catch (Exception exception) {
        this.kits.plugin.getLogger().warning("Kit claims SQL fallito per " + player.getName() + ": " + exception.getMessage());
      }
      Tasks.sync(this.kits.plugin, () -> refreshOpenMenu(player, page));
    });
  }

  void click(Player player, int slot, int page, ClickType click) {
    ConfigurationSection layout = pageSection(this.kits.plugin.configs().kits().getConfigurationSection("gui"), page);
    ConfigurationSection button = GuiLayouts.button(layout, slot);
    if (button == null) {
      return;
    }
    String action = GuiLayouts.action(button);
    if (action.equals("kit")) {
      String kitId = button.getString("kit", "");
      if (kitId == null || kitId.isBlank()) {
        return;
      }
      if (previewClick(click)) {
        Sounds.playRaw(this.kits.plugin, player, button.getString("click-sound", ""), "gui.click-sound");
        preview(player, kitId, page);
        return;
      }
      if (claimClick(click)) {
        PanelClicks.run(this.kits.plugin, player, button);
        this.kits.claim(player, kitId, false);
      }
      return;
    }
    PanelClicks.run(this.kits.plugin, player, button);
    if (action.equals("preview")) {
      String kitId = button.getString("kit", "");
      if (kitId != null && !kitId.isBlank()) {
        preview(player, kitId, page);
      }
      return;
    }
    if (action.equals("page")) {
      open(player, button.getInt("page", 1));
      return;
    }
    if (action.equals("close")) {
      player.closeInventory();
    }
  }

  void previewClick(Player player, int slot, MenuHolder holder) {
    if (closePreviewSlot(slot)) {
      open(player, holder.page());
      return;
    }
    if (claimPreviewSlot(slot) && !holder.extra().isBlank()) {
      this.kits.claim(player, holder.extra(), false);
    }
  }

  void preview(Player player, String kitId) {
    preview(player, kitId, 1);
  }

  void preview(Player player, String kitId, int fromPage) {
    ConfigurationSection kit = this.kits.plugin.configs().kits().getConfigurationSection("kits." + kitId.toLowerCase(Locale.ROOT));
    if (kit == null) {
      this.kits.fail(player, "kits.unknown", Map.of("kit", kitId));
      return;
    }
    ConfigurationSection preview = this.kits.plugin.configs().kits().getConfigurationSection("preview");
    int rows = GuiLayouts.rows(preview, 6);
    String titleRaw = preview == null ? "" : preview.getString("title", "");
    Component title;
    if (titleRaw == null || titleRaw.isBlank()) {
      title = this.kits.plugin.messages().component("kits.preview-title", Map.of("kit", kitId));
    } else {
      title = this.kits.plugin.messages().deserialize(titleRaw.replace("{kit}", kitId).replace("{player}", player.getName()));
    }
    MenuHolder holder = new MenuHolder(KitService.PREVIEW, fromPage, kitId);
    Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
    holder.inventory(inventory);
    PanelFrames.apply(this.kits.plugin, inventory, preview);
    int closeSlot = previewSlot(preview, "close-slot", inventory.getSize() - 1);
    int claimSlot = previewSlot(preview, "claim-slot", -1);
    List<Integer> content = SlotSpec.expand(preview == null ? "" : preview.getString("content-slots", ""));
    int cursor = 0;
    for (Map<?, ?> raw : kit.getMapList("items")) {
      ItemStack stack = this.kits.itemFrom(raw, player);
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      int targetSlot;
      if (content.isEmpty()) {
        targetSlot = cursor;
        cursor++;
      } else {
        if (cursor >= content.size()) {
          break;
        }
        targetSlot = content.get(cursor);
        cursor++;
      }
      if (targetSlot < 0 || targetSlot >= inventory.getSize() || targetSlot == closeSlot || targetSlot == claimSlot) {
        continue;
      }
      inventory.setItem(targetSlot, stack);
    }
    placePreviewButton(inventory, player, preview, closeSlot, "close", Material.ARROW, "kits.preview-close-name");
    placePreviewButton(inventory, player, preview, claimSlot, "claim", Material.LIME_DYE, "kits.preview-claim-name");
    player.openInventory(inventory);
    PanelClicks.openSound(this.kits.plugin, player, preview);
    Sounds.play(this.kits.plugin, player, "kits-preview");
  }

  boolean closePreviewSlot(int slot) {
    return slot == previewSlot(this.kits.plugin.configs().kits().getConfigurationSection("preview"), "close-slot", 53);
  }

  boolean claimPreviewSlot(int slot) {
    int claim = previewSlot(this.kits.plugin.configs().kits().getConfigurationSection("preview"), "claim-slot", -1);
    return claim >= 0 && slot == claim;
  }

  private void refreshOpenMenu(Player player, int page) {
    if (!player.isOnline()) {
      return;
    }
    if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)) {
      return;
    }
    if (!KitService.MENU.equals(holder.menu()) || holder.page() != page) {
      return;
    }
    ConfigurationSection layout = pageSection(this.kits.plugin.configs().kits().getConfigurationSection("gui"), page);
    decorateKitIcons(player, holder.getInventory(), layout, this.kits.claims.snapshot(player.getUniqueId()));
  }

  private void render(Player player, int page, Map<String, Long> last) {
    ConfigurationSection root = this.kits.plugin.configs().kits().getConfigurationSection("gui");
    ConfigurationSection layout = pageSection(root, page);
    if (layout == null) {
      this.kits.plugin.messages().send(player, "kits.no-page", Map.of("page", String.valueOf(page)));
      return;
    }
    Inventory inventory = Panels.open(
        this.kits.plugin,
        player,
        new MenuHolder(KitService.MENU, page, ""),
        layout,
        root,
        Map.of("page", String.valueOf(page))
    );
    decorateKitIcons(player, inventory, layout, last);
  }

  private void decorateKitIcons(Player player, Inventory inventory, ConfigurationSection layout, Map<String, Long> last) {
    ConfigurationSection slots = layout == null ? null : layout.getConfigurationSection("slots");
    if (slots == null) {
      return;
    }
    for (String key : slots.getKeys(false)) {
      ConfigurationSection button = slots.getConfigurationSection(key);
      if (button == null || !GuiLayouts.action(button).equals("kit")) {
        continue;
      }
      String kitId = button.getString("kit", "");
      if (kitId == null || kitId.isBlank()) {
        continue;
      }
      for (int slot : SlotSpec.expand(key)) {
        ItemStack icon = inventory.getItem(slot);
        if (icon == null || icon.getType().isAir()) {
          continue;
        }
        inventory.setItem(slot, withStatus(player, icon, button, kitId.toLowerCase(Locale.ROOT), last));
      }
    }
  }

  private ItemStack withStatus(Player player, ItemStack icon, ConfigurationSection button, String kitId, Map<String, Long> last) {
    ItemStack copy = icon.clone();
    KitStatus status = statusOf(player, kitId, last);
    if (status == KitStatus.LOCKED) {
      String locked = button.getString("locked-material", "");
      Material material = locked == null || locked.isBlank() ? null : Material.matchMaterial(locked);
      if (material != null && !material.isAir()) {
        copy.setType(material);
      }
    }
    List<Component> lore = copy.lore() == null ? new ArrayList<>() : new ArrayList<>(copy.lore());
    lore.add(Component.empty());
    lore.add(this.kits.plugin.messages().component("kits.hint-claim", Map.of()));
    lore.add(this.kits.plugin.messages().component("kits.hint-preview", Map.of()));
    lore.add(status.line(this, kitId, last));
    if (this.kits.plugin.configs().kits().getBoolean("settings.show-contents", true)) {
      appendContents(lore, kitId);
    }
    copy.editMeta(meta -> {
      meta.lore(lore);
      if (status == KitStatus.READY && this.kits.plugin.configs().kits().getBoolean("settings.glow-when-ready", true)) {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
      }
    });
    return copy;
  }

  private void appendContents(List<Component> lore, String kitId) {
    ConfigurationSection kit = this.kits.plugin.configs().kits().getConfigurationSection("kits." + kitId);
    if (kit == null) {
      return;
    }
    int shown = 0;
    for (Map<?, ?> raw : kit.getMapList("items")) {
      if (shown >= 6) {
        lore.add(this.kits.plugin.messages().component("kits.contents-more", Map.of()));
        break;
      }
      String item = KitService.asString(raw.get("mmoitem-id"));
      if (item.isBlank()) {
        item = KitService.asString(raw.get("material"));
      }
      if (item.isBlank()) {
        continue;
      }
      lore.add(this.kits.plugin.messages().component("kits.contents-line", Map.of(
          "item", item,
          "amount", String.valueOf(Math.max(1, KitService.asInt(raw.get("amount"), 1)))
      )));
      shown++;
    }
  }

  private KitStatus statusOf(Player player, String kitId, Map<String, Long> last) {
    ConfigurationSection kit = this.kits.plugin.configs().kits().getConfigurationSection("kits." + kitId);
    if (kit == null) {
      return KitStatus.READY;
    }
    String permission = kit.getString("permission", "");
    if (permission != null && !permission.isBlank()
        && !player.hasPermission(permission)
        && !Perms.has(this.kits.plugin, player, "kit-bypass")) {
      return KitStatus.LOCKED;
    }
    Long claimed = last.get(kitId);
    if (kit.getBoolean("one-time", false) && claimed != null && !Perms.has(this.kits.plugin, player, "kit-bypass")) {
      return KitStatus.USED;
    }
    long cooldownMs = kit.getLong("cooldown-seconds", 0L) * 1000L;
    if (claimed != null && cooldownMs > 0 && !Perms.has(this.kits.plugin, player, "kit-bypass")) {
      if (System.currentTimeMillis() < claimed + cooldownMs) {
        return KitStatus.COOLDOWN;
      }
    }
    return KitStatus.READY;
  }

  private boolean previewClick(ClickType click) {
    if (!this.kits.plugin.configs().kits().getBoolean("settings.right-click-preview", true)) {
      return false;
    }
    return matchesClick(click, "settings.preview-clicks", ClickType.RIGHT, ClickType.SHIFT_RIGHT);
  }

  private boolean claimClick(ClickType click) {
    return matchesClick(click, "settings.claim-clicks", ClickType.LEFT, ClickType.SHIFT_LEFT);
  }

  private boolean matchesClick(ClickType click, String path, ClickType... defaults) {
    List<String> names = this.kits.plugin.configs().kits().getStringList(path);
    if (names.isEmpty()) {
      for (ClickType type : defaults) {
        if (type == click) {
          return true;
        }
      }
      return false;
    }
    for (String name : names) {
      try {
        if (ClickType.valueOf(name.trim().toUpperCase(Locale.ROOT)) == click) {
          return true;
        }
      } catch (IllegalArgumentException ignored) {

      }
    }
    return false;
  }

  private ConfigurationSection pageSection(ConfigurationSection root, int page) {
    if (root == null) {
      return null;
    }
    if (page <= 1) {
      return root;
    }
    return root.getConfigurationSection("pages." + page);
  }

  private void placePreviewButton(
      Inventory inventory,
      Player player,
      ConfigurationSection preview,
      int slot,
      String section,
      Material fallback,
      String nameKey
  ) {
    if (slot < 0 || slot >= inventory.getSize()) {
      return;
    }
    ConfigurationSection button = preview == null ? null : preview.getConfigurationSection(section);
    inventory.setItem(slot, button == null
        ? named(fallback, this.kits.plugin.messages().text(nameKey))
        : PanelItems.from(this.kits.plugin, button, player));
  }

  private static int previewSlot(ConfigurationSection preview, String key, int fallback) {
    return preview == null ? fallback : preview.getInt(key, fallback);
  }

  private ItemStack named(Material material, String name) {
    ItemStack item = new ItemStack(material);
    item.editMeta(meta -> meta.displayName(this.kits.plugin.messages().deserialize(name)));
    return item;
  }

  private enum KitStatus {
    READY,
    COOLDOWN,
    LOCKED,
    USED;

    private Component line(KitMenus menus, String kitId, Map<String, Long> last) {
      return switch (this) {
        case LOCKED -> menus.kits.plugin.messages().component("kits.status-locked", Map.of());
        case USED -> menus.kits.plugin.messages().component("kits.status-one-time-used", Map.of());
        case COOLDOWN -> {
          ConfigurationSection kit = menus.kits.plugin.configs().kits().getConfigurationSection("kits." + kitId);
          long cooldownMs = kit == null ? 0L : kit.getLong("cooldown-seconds", 0L) * 1000L;
          Long claimed = last.get(kitId);
          long readyAt = claimed == null ? System.currentTimeMillis() : claimed + cooldownMs;
          yield menus.kits.plugin.messages().component("kits.status-cooldown", Map.of(
              "duration", Durations.formatRemaining(menus.kits.plugin, readyAt),
              "seconds", String.valueOf(Math.max(1, (readyAt - System.currentTimeMillis() + 999) / 1000))
          ));
        }
        case READY -> menus.kits.plugin.messages().component("kits.status-ready", Map.of());
      };
    }
  }
}
