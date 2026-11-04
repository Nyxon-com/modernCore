package com.modern.core.command;

import com.mojang.brigadier.Command;
import com.modern.core.ModernCorePlugin;
import com.modern.core.util.MmoItems;
import com.modern.core.util.Perms;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ReciclatoreCommands implements Listener {

  private static final Set<String> WEAPON_TYPES = Set.of(
      "SWORD", "AXE", "BOW", "CROSSBOW", "DAGGER", "GAUNTLET", "GREATSWORD",
      "HAMMER", "KATANA", "LANCE", "MUSKET", "SPEAR", "STAFF", "WAND", "WHIP", "TOOL"
  );
  private static final Set<String> ARMOR_TYPES = Set.of("ARMOR", "SHIELD");
  private static final Set<String> GEM_TYPES = Set.of("GEM_STONE", "GEMSTONE");
  private static final String[] MARS_ID_MARKERS = {
      "MARZIANO", "MARZIANA", "MARZIANI", "LARYCAN", "PUTRAN", "VULCAN", "NEKRATH"
  };

  private final ModernCorePlugin plugin;
  private final CommandSettings settings;

  ReciclatoreCommands(ModernCorePlugin plugin) {
    this.plugin = plugin;
    this.settings = new CommandSettings(plugin);
  }

  void register(Commands registrar) {
    this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    String name = this.settings.name("commands.reciclatore", "reciclatore");
    registrar.register(Commands.literal(name)
            .requires(stack -> Perms.has(this.plugin, stack.getSender(), "reciclatore"))
            .executes(ctx -> open(ctx.getSource().getSender()))
            .build(),
        "Ricicla armi, armature e gemme in rottami",
        this.settings.aliases("commands.reciclatore-aliases"));
  }

  private int open(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      this.plugin.messages().send(sender, "general.player-only");
      return 0;
    }
    Component title = this.plugin.messages().component("reciclatore.title", Map.of());
    player.openInventory(new ReciclatoreHolder(title).getInventory());
    this.plugin.messages().send(player, "reciclatore.hint");
    return Command.SINGLE_SUCCESS;
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof ReciclatoreHolder) || !(event.getPlayer() instanceof Player player)) {
      return;
    }
    Inventory inventory = event.getInventory();
    int weapons = 0;
    int armors = 0;
    int gems = 0;
    int weaponsT2 = 0;
    int armorsT2 = 0;
    int gemsT2 = 0;

    for (ItemStack stack : inventory.getContents()) {
      if (stack == null || stack.getType().isAir()) {
        continue;
      }
      String[] typeAndId = MmoItems.typeAndId(stack);
      if (typeAndId == null) {
        giveBack(player, stack);
        continue;
      }
      String type = typeAndId[0] == null ? "" : typeAndId[0].toUpperCase(Locale.ROOT);
      String id = typeAndId[1] == null ? "" : typeAndId[1].toUpperCase(Locale.ROOT);
      int amount = Math.max(1, stack.getAmount());
      if (WEAPON_TYPES.contains(type)) {
        if (isMarsGear(id)) {
          weaponsT2 += amount;
        } else {
          weapons += amount;
        }
      } else if (ARMOR_TYPES.contains(type)) {
        if (isMarsGear(id)) {
          armorsT2 += amount;
        } else {
          armors += amount;
        }
      } else if (GEM_TYPES.contains(type)) {
        if (isGemTier2Plus(id)) {
          gemsT2 += amount;
        } else {
          gems += amount;
        }
      } else {
        giveBack(player, stack);
      }
    }
    inventory.clear();

    ItemStack cursor = player.getItemOnCursor();
    if (cursor != null && !cursor.getType().isAir()) {
      String[] typeAndId = MmoItems.typeAndId(cursor);
      if (typeAndId != null) {
        String type = typeAndId[0] == null ? "" : typeAndId[0].toUpperCase(Locale.ROOT);
        String id = typeAndId[1] == null ? "" : typeAndId[1].toUpperCase(Locale.ROOT);
        int amount = Math.max(1, cursor.getAmount());
        boolean consumed = false;
        if (WEAPON_TYPES.contains(type)) {
          if (isMarsGear(id)) {
            weaponsT2 += amount;
          } else {
            weapons += amount;
          }
          consumed = true;
        } else if (ARMOR_TYPES.contains(type)) {
          if (isMarsGear(id)) {
            armorsT2 += amount;
          } else {
            armors += amount;
          }
          consumed = true;
        } else if (GEM_TYPES.contains(type)) {
          if (isGemTier2Plus(id)) {
            gemsT2 += amount;
          } else {
            gems += amount;
          }
          consumed = true;
        }
        if (consumed) {
          player.setItemOnCursor(null);
        }
      }
    }

    if (weapons <= 0 && armors <= 0 && gems <= 0 && weaponsT2 <= 0 && armorsT2 <= 0 && gemsT2 <= 0) {
      return;
    }
    if (weapons > 0) {
      MmoItems.give(player, "MATERIAL", "ROTTAME_ARMA", weapons, null);
    }
    if (armors > 0) {
      MmoItems.give(player, "MATERIAL", "ROTTAME_ARMATURA", armors, null);
    }
    if (gems > 0) {
      MmoItems.give(player, "MATERIAL", "ROTTAME_GEMMA", gems, null);
    }
    if (weaponsT2 > 0) {
      MmoItems.give(player, "MATERIAL", "ROTTAME_ARMA_2", weaponsT2, null);
    }
    if (armorsT2 > 0) {
      MmoItems.give(player, "MATERIAL", "ROTTAME_ARMATURA_2", armorsT2, null);
    }
    if (gemsT2 > 0) {
      MmoItems.give(player, "MATERIAL", "ROTTAME_GEMMA_2", gemsT2, null);
    }
    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
    this.plugin.messages().send(player, "reciclatore.done", Map.of(
        "weapons", Integer.toString(weapons + weaponsT2),
        "armors", Integer.toString(armors + armorsT2),
        "gems", Integer.toString(gems + gemsT2)
    ));
  }

  private static boolean isMarsGear(String id) {
    for (String marker : MARS_ID_MARKERS) {
      if (id.contains(marker)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isGemTier2Plus(String id) {
    return id.endsWith("2") || id.endsWith("3")
        || id.contains("_2") || id.contains("_3");
  }

  private static void giveBack(Player player, ItemStack stack) {
    Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
    leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
  }

  private static final class ReciclatoreHolder implements InventoryHolder {
    private final Inventory inventory;

    private ReciclatoreHolder(Component title) {
      this.inventory = Bukkit.createInventory(this, 27, title);
    }

    @Override
    public Inventory getInventory() {
      return this.inventory;
    }
  }
}
