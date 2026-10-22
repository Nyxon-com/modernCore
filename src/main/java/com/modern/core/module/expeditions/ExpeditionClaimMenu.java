//made with love by nyxon

package com.modern.core.module.expeditions;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.modern.core.gui.MenuHolder;
import com.modern.core.util.MmoItems;
import com.modern.core.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExpeditionClaimMenu {

  static final String MENU = "expeditions-claim";
  private static final int SIZE = 45;

  private final ExpeditionService service;

  ExpeditionClaimMenu(ExpeditionService service) {
    this.service = service;
  }

  boolean isMenu(Inventory inventory) {
    return inventory != null && inventory.getHolder() instanceof MenuHolder holder && MENU.equals(holder.menu());
  }

  int buttonSlot() {
    return gui().getInt("button-slot", 40);
  }

  ClaimSession.Reward reward(List<String> lines, String poolName, String poolMaterial, boolean rare, boolean bonus) {
    ConfigurationSection gui = gui();
    for (String line : lines) {
      String[] give = ExpeditionRules.parseMmoGive(line);
      if (give == null) {
        continue;
      }
      int amount = parseInt(give[2], 1);
      ItemStack stack = MmoItems.stack(give[0], give[1], amount);
      if (stack == null) {
        continue;
      }
      String itemName = MiniMessage.miniMessage().serialize(stack.effectiveName());
      if (rare) {
        stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
      }
      String label = (amount > 1 ? "<white>" + amount + "x</white> " : "") + itemName;
      return new ClaimSession.Reward(stack, lines, label, rare, bonus);
    }
    String material = poolMaterial;
    String name = poolName;
    int amount = 1;
    for (String line : lines) {
      String command = ExpeditionRules.stripPrefix(line);
      for (Map<?, ?> rule : gui.getMapList("icons")) {
        Object match = rule.get("match");
        if (match == null) {
          continue;
        }
        Matcher matcher;
        try {
          matcher = Pattern.compile(String.valueOf(match), Pattern.CASE_INSENSITIVE).matcher(command);
        } catch (Exception exception) {
          continue;
        }
        if (!matcher.find()) {
          continue;
        }
        if (material == null || material.isBlank()) {
          material = rule.get("material") == null ? null : String.valueOf(rule.get("material"));
        }
        if (name == null || name.isBlank()) {
          name = groups(rule.get("name") == null ? "" : String.valueOf(rule.get("name")), matcher);
        }
        Object amountGroup = rule.get("amount-group");
        if (amountGroup != null) {
          int group = parseInt(String.valueOf(amountGroup), 0);
          if (group > 0 && group <= matcher.groupCount()) {
            amount = parseInt(matcher.group(group), 1);
          }
        }
        break;
      }
      if (material != null && name != null) {
        break;
      }
    }
    if (material == null || material.isBlank()) {
      material = gui.getString("default-icon", "CHEST_MINECART");
    }
    if (name == null || name.isBlank()) {
      name = gui.getString("default-name", "<light_purple>Ricompensa");
    }
    ItemStack icon = item(material, name, gui.getStringList(bonus ? "bonus-lore" : "reward-lore"), rare, null);
    icon.setAmount(Math.max(1, Math.min(64, amount)));
    return new ClaimSession.Reward(icon, lines, name, rare, bonus);
  }

  void open(Player player, ClaimSession session) {
    ConfigurationSection gui = gui();
    MenuHolder holder = new MenuHolder(MENU, 1, session.run.expedition());
    Inventory inventory = Bukkit.createInventory(holder, SIZE,
        component(gui.getString("title", "Spedizione")));
    holder.inventory(inventory);
    session.inventory = inventory;

    ItemStack inner = item(gui.getString("inner", "AIR"), " ", List.of(), false, null);
    for (int slot = 0; slot < SIZE; slot++) {
      inventory.setItem(slot, inner.getType().isAir() ? null : inner.clone());
    }
    frame(session);
    ConfigurationSection expedition = this.service.expedition(session.run.expedition());
    int headerSlot = gui.getInt("header-slot", 4);
    if (headerSlot >= 0 && headerSlot < SIZE) {
      String material = expedition == null ? "CHEST" : expedition.getString("material", "CHEST");
      inventory.setItem(headerSlot, item(material, this.service.displayName(session.run.expedition()),
          gui.getStringList("header-lore"), true, null));
    }
    ItemStack hidden = section("hidden", "ENDER_CHEST", "<dark_purple><b>???</b>", List.of(), false);
    for (int slot : ExpeditionRules.rewardSlots(session.rewards.size())) {
      inventory.setItem(slot, hidden.clone());
    }
    button(session, "revealing");
    player.openInventory(inventory);
    sound(player, "open", 1.0f);

    long delay = Math.max(1L, gui.getLong("start-delay-ticks", 20L));
    long interval = Math.max(2L, gui.getLong("reveal-interval-ticks", 12L));
    session.task = this.service.plugin.getServer().getScheduler().runTaskTimer(this.service.plugin,
        () -> tick(player, session), delay, interval);
  }

  private void tick(Player player, ClaimSession session) {
    if (!player.isOnline() || session.finished
        || player.getOpenInventory().getTopInventory() != session.inventory) {
      session.cancelTask();
      return;
    }
    List<Integer> slots = ExpeditionRules.rewardSlots(session.rewards.size());
    if (session.revealed < slots.size()) {
      int index = session.revealed++;
      ClaimSession.Reward reward = session.rewards.get(index);
      session.inventory.setItem(slots.get(index), reward.icon().clone());
      session.phase++;
      frame(session);
      float pitch = (float) Math.min(2.0, 0.8 + 0.12 * index);
      sound(player, reward.rare() ? "rare" : "reveal", reward.rare() ? 1.0f : pitch);
      return;
    }
    finish(player, session, true);
  }

  void revealAll(Player player, ClaimSession session) {
    if (session.finished) {
      return;
    }
    List<Integer> slots = ExpeditionRules.rewardSlots(session.rewards.size());
    for (int i = session.revealed; i < slots.size(); i++) {
      session.inventory.setItem(slots.get(i), session.rewards.get(i).icon().clone());
    }
    session.revealed = slots.size();
    finish(player, session, true);
  }

  private void finish(Player player, ClaimSession session, boolean fanfare) {
    session.cancelTask();
    if (session.finished) {
      return;
    }
    session.finished = true;
    if (!session.committing) {
      button(session, "ready");
    }
    if (!fanfare || !player.isOnline()) {
      return;
    }
    ConfigurationSection gui = gui();
    sound(player, "fanfare", 1.0f);
    String title = gui.getString("fanfare.title", "");
    String subtitle = gui.getString("fanfare.subtitle", "");
    if ((title != null && !title.isBlank()) || (subtitle != null && !subtitle.isBlank())) {
      String name = this.service.displayName(session.run.expedition());
      player.showTitle(Title.title(
          component(title == null ? "" : title.replace("{name}", name)),
          component(subtitle == null ? "" : subtitle.replace("{name}", name)),
          Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(600))));
    }
    String particleName = gui.getString("fanfare.particle", "TOTEM_OF_UNDYING");
    if (particleName != null && !particleName.isBlank()) {
      try {
        Particle particle = Particle.valueOf(particleName.trim().toUpperCase(Locale.ROOT));
        Location location = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(particle, location, Math.max(1, gui.getInt("fanfare.particle-count", 60)),
            0.6, 0.9, 0.6, 0.3);
      } catch (Exception exception) {
        this.service.plugin.getLogger().warning("Spedizioni: particella non valida '" + particleName + "'");
      }
    }
  }

  void markClaimed(Player player, ClaimSession session) {
    session.cancelTask();
    session.finished = true;
    if (session.inventory == null) {
      return;
    }
    List<Integer> slots = ExpeditionRules.rewardSlots(session.rewards.size());
    for (int i = session.revealed; i < slots.size(); i++) {
      session.inventory.setItem(slots.get(i), session.rewards.get(i).icon().clone());
    }
    session.revealed = slots.size();
    button(session, "claimed");
    long delay = gui().getLong("close-delay-ticks", 40L);
    if (!this.service.plugin.isEnabled()) {
      return;
    }
    if (delay >= 0) {
      this.service.plugin.getServer().getScheduler().runTaskLater(this.service.plugin, () -> {
        if (player.isOnline() && player.getOpenInventory().getTopInventory() == session.inventory) {
          player.closeInventory();
        }
      }, delay);
    }
  }

  void button(ClaimSession session, String state) {
    int slot = buttonSlot();
    if (session.inventory == null || slot < 0 || slot >= SIZE) {
      return;
    }
    session.inventory.setItem(slot, section("button." + state, "NETHER_STAR",
        "<b><gradient:#E5A2C1:#9711EA>ʀɪꜱᴄᴀᴛᴛᴀ ᴛᴜᴛᴛᴏ</gradient></b>", List.of(), !"revealing".equals(state)));
  }

  private void frame(ClaimSession session) {
    List<String> materials = gui().getStringList("frame");
    if (materials.isEmpty()) {
      materials = List.of("PURPLE_STAINED_GLASS_PANE", "MAGENTA_STAINED_GLASS_PANE");
    }
    int header = gui().getInt("header-slot", 4);
    int button = buttonSlot();
    List<ItemStack> panes = new ArrayList<>(materials.size());
    for (String material : materials) {
      panes.add(item(material, " ", List.of(), false, null));
    }
    for (int slot = 0; slot < SIZE; slot++) {
      int row = slot / 9;
      int column = slot % 9;
      boolean border = row == 0 || row == 4 || column == 0 || column == 8;
      if (!border || slot == header || slot == button) {
        continue;
      }
      session.inventory.setItem(slot, panes.get(Math.floorMod(row + column + session.phase, panes.size())).clone());
    }
  }

  void sound(Player player, String key, float pitch) {
    String raw = gui().getString("sounds." + key, "");
    if (raw != null && !raw.isBlank()) {
      Sounds.playRaw(this.service.plugin, player, raw, 1.0f, pitch, "expeditions.claim-gui.sounds." + key);
    }
  }

  private ItemStack section(String path, String material, String name, List<String> lore, boolean glow) {
    ConfigurationSection section = gui().getConfigurationSection(path);
    if (section == null) {
      return item(material, name, lore, glow, null);
    }
    return item(section.getString("material", material), section.getString("name", name),
        section.isList("lore") ? section.getStringList("lore") : lore,
        section.getBoolean("glow", glow), section.getString("texture", ""));
  }

  private ItemStack item(String materialName, String name, List<String> lore, boolean glow, String texture) {
    Material material = materialName == null ? null : Material.matchMaterial(materialName.trim());
    if (material == null) {
      material = Material.PAPER;
    }
    if (material.isAir()) {
      return new ItemStack(Material.AIR);
    }
    if (!material.isItem()) {
      material = Material.PAPER;
    }
    boolean head = texture != null && !texture.isBlank();
    ItemStack stack = new ItemStack(head ? Material.PLAYER_HEAD : material);
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
      if (head && meta instanceof SkullMeta skull) {
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), "valary_head");
        profile.setProperty(new ProfileProperty("textures", texture.trim()));
        skull.setPlayerProfile(profile);
      }
    });
    return stack;
  }

  private Component component(String raw) {
    return this.service.plugin.messages().deserialize(raw == null ? "" : raw)
        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
  }

  private ConfigurationSection gui() {
    ConfigurationSection gui = this.service.config().getConfigurationSection("claim-gui");
    return gui != null ? gui : this.service.config().createSection("claim-gui");
  }

  private static String groups(String template, Matcher matcher) {
    String out = template;
    for (int group = matcher.groupCount(); group >= 1; group--) {
      String value = matcher.group(group);
      out = out.replace("$" + group, value == null ? "" : value);
    }
    return out;
  }

  private static int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }
}
