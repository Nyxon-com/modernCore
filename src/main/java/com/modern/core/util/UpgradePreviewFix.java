package com.modern.core.util;

import com.modern.core.ModernCorePlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public final class UpgradePreviewFix implements Listener {

  private static final Map<Character, Character> SMALL_CAPS = Map.ofEntries(
      Map.entry('ᴀ', 'a'), Map.entry('ʙ', 'b'), Map.entry('ᴄ', 'c'), Map.entry('ᴅ', 'd'),
      Map.entry('ᴇ', 'e'), Map.entry('ꜰ', 'f'), Map.entry('ɢ', 'g'), Map.entry('ʜ', 'h'),
      Map.entry('ɪ', 'i'), Map.entry('ᴊ', 'j'), Map.entry('ᴋ', 'k'), Map.entry('ʟ', 'l'),
      Map.entry('ᴍ', 'm'), Map.entry('ɴ', 'n'), Map.entry('ᴏ', 'o'), Map.entry('ᴘ', 'p'),
      Map.entry('ǫ', 'q'), Map.entry('ʀ', 'r'), Map.entry('ꜱ', 's'), Map.entry('ᴛ', 't'),
      Map.entry('ᴜ', 'u'), Map.entry('ᴠ', 'v'), Map.entry('ᴡ', 'w'), Map.entry('ʏ', 'y'),
      Map.entry('ᴢ', 'z')
  );

  private final ModernCorePlugin plugin;

  public UpgradePreviewFix(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPreviewOpen(InventoryOpenEvent event) {
    if (!(event.getPlayer() instanceof Player)) {
      return;
    }
    if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
      return;
    }
    String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
    if (!isUpgradePreviewTitle(title)) {
      return;
    }
    Inventory inventory = event.getInventory();
    Bukkit.getScheduler().runTask(this.plugin, () -> apply(inventory));
  }

  static boolean isUpgradePreviewTitle(String title) {
    if (title == null || title.isBlank()) {
      return false;
    }
    String plain = toLatin(title).toLowerCase();

    return plain.contains("preview") && (plain.contains("upgrade") || plain.contains("+1"));
  }

  private void apply(Inventory inventory) {
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack stack = inventory.getItem(slot);
      ItemStack fixed = upgradePreview(stack);
      if (fixed != null) {
        inventory.setItem(slot, fixed);
      }
    }
  }

  private static ItemStack upgradePreview(ItemStack stack) {
    if (stack == null || stack.getType().isAir()) {
      return null;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null || !meta.hasDisplayName()) {
      return null;
    }
    String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

    if (!name.contains("+1")) {
      return null;
    }
    try {
      Class<?> nbtClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
      Object nbt = nbtClass.getMethod("get", ItemStack.class).invoke(null, stack);
      boolean hasType = (boolean) nbtClass.getMethod("hasType").invoke(nbt);
      boolean hasId = (boolean) nbtClass.getMethod("hasTag", String.class).invoke(nbt, "MMOITEMS_ITEM_ID");
      if (!hasType && !hasId) {
        return null;
      }

      Class<?> liveClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem");
      Object live = liveClass.getConstructor(nbtClass).newInstance(nbt);

      Class<?> mmoItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
      Class<?> itemStats = Class.forName("net.Indyuce.mmoitems.ItemStats");
      Object upgradeStat = itemStats.getField("UPGRADE").get(null);
      Class<?> itemStatClass = Class.forName("net.Indyuce.mmoitems.stat.type.ItemStat");
      if (!(boolean) mmoItemClass.getMethod("hasData", itemStatClass).invoke(live, upgradeStat)) {
        return null;
      }

      mmoItemClass.getMethod("upgrade").invoke(live);

      Object builder = mmoItemClass.getMethod("newBuilder").invoke(live);
      Object built = builder.getClass().getMethod("build").invoke(builder);
      if (!(built instanceof ItemStack upgraded) || upgraded.getType().isAir()) {
        return null;
      }
      return upgraded;
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private static String toLatin(String input) {
    StringBuilder out = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); ) {
      int cp = input.codePointAt(i);
      i += Character.charCount(cp);
      if (cp <= 0xFFFF) {
        Character mapped = SMALL_CAPS.get((char) cp);
        if (mapped != null) {
          out.append(mapped);
          continue;
        }
      }
      out.appendCodePoint(cp);
    }
    return out.toString();
  }
}
