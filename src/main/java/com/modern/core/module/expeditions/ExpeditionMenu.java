package com.modern.core.module.expeditions;

import com.modern.core.gui.MenuHolder;
import com.modern.core.util.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ExpeditionMenu {

  static final String MENU = "expeditions";

  private final ExpeditionService service;

  ExpeditionMenu(ExpeditionService service) {
    this.service = service;
  }

  void open(Player player) {
    ConfigurationSection gui = gui();
    int rows = Math.max(1, Math.min(6, gui.getInt("rows", 3)));
    MenuHolder holder = new MenuHolder(MENU);
    Inventory inventory = Bukkit.createInventory(holder, rows * 9,
        this.service.plugin.messages().deserialize(gui.getString("title", "Spedizioni")));
    holder.inventory(inventory);
    render(player, inventory);
    player.openInventory(inventory);
    if (!this.service.loaded(player.getUniqueId())) {
      this.service.load(player, false);
    }
  }

  boolean isMenu(Inventory inventory) {
    return inventory != null && inventory.getHolder() instanceof MenuHolder holder && MENU.equals(holder.menu());
  }

  void render(Player player, Inventory inventory) {
    ConfigurationSection gui = gui();
    ItemStack filler = item(gui.getString("filler", "BLACK_STAINED_GLASS_PANE"), "<black>", List.of(), false);
    for (int i = 0; i < inventory.getSize(); i++) {
      inventory.setItem(i, filler);
    }
    long hours = this.service.hours(player);
    Optional<ExpeditionRepository.Run> state = this.service.state(player.getUniqueId());
    ExpeditionRepository.Run run = state == null ? null : state.orElse(null);
    long now = System.currentTimeMillis();

    for (String id : this.service.expeditionIds()) {
      ConfigurationSection expedition = this.service.expedition(id);
      if (expedition == null) {
        continue;
      }
      int slot = expedition.getInt("slot", -1);
      if (slot < 0 || slot >= inventory.getSize()) {
        continue;
      }
      List<String> lore = new ArrayList<>(expedition.getStringList("description"));
      Map<String, String> values = new HashMap<>();
      values.put("duration", hours > 0 ? hours + "h" : "-");
      values.put("hint", expedition.getString("locked-hint", ""));
      String material = expedition.getString("material", "CHEST");
      boolean glow = false;
      String stateKey;
      if (run != null && run.expedition().equalsIgnoreCase(id)) {
        if (run.endsAt() <= now) {
          stateKey = "ready";
          glow = true;
        } else {
          stateKey = "running";
          timer(values, run, now);
        }
      } else if (run != null) {
        stateKey = "busy";
      } else if (hours <= 0) {
        stateKey = "no-rank";
        material = expedition.getString("locked-material", material);
      } else if (!this.service.unlocked(player, expedition)) {
        stateKey = "locked";
        material = expedition.getString("locked-material", material);
      } else {
        stateKey = "available";
      }
      lore.addAll(gui.getStringList("states." + stateKey));
      inventory.setItem(slot, item(material, expedition.getString("name", id), fill(lore, values), glow));
    }

    int statusSlot = gui.getInt("status-slot", -1);
    if (statusSlot >= 0 && statusSlot < inventory.getSize()) {
      String key;
      Map<String, String> values = new HashMap<>();
      values.put("duration", hours > 0 ? hours + "h" : "-");
      if (run != null) {
        values.put("name", this.service.displayName(run.expedition()));
        if (run.endsAt() <= now) {
          key = "ready";
        } else {
          key = "running";
          timer(values, run, now);
        }
      } else {
        key = hours > 0 ? "idle" : "no-rank";
      }
      ConfigurationSection status = gui.getConfigurationSection("status." + key);
      if (status != null) {
        inventory.setItem(statusSlot, item(
            status.getString("material", "CLOCK"),
            fill(status.getString("name", ""), values),
            fill(status.getStringList("lore"), values),
            "ready".equals(key)
        ));
      }
    }

    int closeSlot = gui.getInt("close-slot", -1);
    if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
      inventory.setItem(closeSlot, item(
          gui.getString("close.material", "BARRIER"),
          gui.getString("close.name", "<red>Chiudi"),
          List.of(),
          false
      ));
    }
  }

  void click(Player player, int slot) {
    ConfigurationSection gui = gui();
    if (slot == gui.getInt("close-slot", -1)) {
      player.closeInventory();
      return;
    }
    Optional<ExpeditionRepository.Run> state = this.service.state(player.getUniqueId());
    Runnable refresh = () -> {
      Inventory top = player.getOpenInventory().getTopInventory();
      if (isMenu(top)) {
        render(player, top);
      }
    };
    if (slot == gui.getInt("status-slot", -1)) {
      if (state != null && state.isPresent() && state.get().endsAt() <= System.currentTimeMillis()) {
        this.service.openClaim(player);
      }
      return;
    }
    for (String id : this.service.expeditionIds()) {
      ConfigurationSection expedition = this.service.expedition(id);
      if (expedition == null || expedition.getInt("slot", -1) != slot) {
        continue;
      }
      if (state != null && state.isPresent()) {
        ExpeditionRepository.Run run = state.get();
        if (run.expedition().equalsIgnoreCase(id) && run.endsAt() <= System.currentTimeMillis()) {
          this.service.openClaim(player);
        } else if (run.expedition().equalsIgnoreCase(id)) {
          this.service.send(player, "not-ready", Map.of(
              "remaining", Durations.formatRemaining(this.service.plugin, run.endsAt())));
        } else {
          this.service.send(player, "already-running", Map.of());
        }
        return;
      }
      this.service.startExpedition(player, id, refresh);
      return;
    }
  }

  private void timer(Map<String, String> values, ExpeditionRepository.Run run, long now) {
    int percent = ExpeditionRules.percent(run.startedAt(), run.endsAt(), now);
    values.put("remaining", Durations.formatRemaining(this.service.plugin, run.endsAt()));
    values.put("percent", String.valueOf(percent));
    values.put("bar", ExpeditionRules.bar(percent));
  }

  private ConfigurationSection gui() {
    ConfigurationSection gui = this.service.config().getConfigurationSection("gui");
    return gui != null ? gui : this.service.config().createSection("gui");
  }

  private static String fill(String raw, Map<String, String> values) {
    String out = raw == null ? "" : raw;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
    }
    return out;
  }

  private static List<String> fill(List<String> lines, Map<String, String> values) {
    List<String> out = new ArrayList<>(lines.size());
    for (String line : lines) {
      out.add(fill(line, values));
    }
    return out;
  }

  private ItemStack item(String materialName, String name, List<String> lore, boolean glow) {
    Material material = materialName == null ? null : Material.matchMaterial(materialName.trim());
    if (material == null || material.isAir() || !material.isItem()) {
      material = Material.PAPER;
    }
    ItemStack stack = new ItemStack(material);
    stack.editMeta(meta -> {
      meta.displayName(component(name));
      List<Component> lines = new ArrayList<>(lore.size());
      for (String line : lore) {
        lines.add(component(line));
      }
      meta.lore(lines);
      meta.addItemFlags(ItemFlag.values());
      if (glow) {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
      }
    });
    return stack;
  }

  private Component component(String raw) {
    return this.service.plugin.messages().deserialize(raw == null ? "" : raw)
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
  }
}
