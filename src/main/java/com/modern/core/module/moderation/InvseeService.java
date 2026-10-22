//made with love by nyxon

package com.modern.core.module.moderation;

import com.modern.core.ModernCorePlugin;
import com.modern.core.gui.PanelItems;
import com.modern.core.gui.SlotSpec;
import com.modern.core.module.players.PlayerDataRepository;
import com.modern.core.module.players.PlayersModule;
import com.modern.core.util.ItemStacks;
import com.modern.core.util.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;

public final class InvseeService {

  private final ModernCorePlugin plugin;

  public InvseeService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void open(Player viewer, Player target) {
    Holder holder = new Holder(target.getUniqueId(), target.getName(), false);
    openGui(viewer, holder, target.getName());
    fill(holder.inventory, target);
  }

  public void openOffline(Player viewer, UUID uuid, String name, ItemStack[] packed) {
    Holder holder = new Holder(uuid, name, true);
    openGui(viewer, holder, name);
    fillPacked(holder.inventory, packed);
    this.plugin.messages().send(viewer, "invsee.offline", Map.of("player", name));
  }

  private void openGui(Player viewer, Holder holder, String name) {
    ConfigurationSection gui = gui();
    int rows = gui == null ? 6 : Math.min(6, Math.max(1, gui.getInt("rows", 6)));
    String titleRaw = gui == null ? "" : gui.getString("title", "");
    Component title = titleRaw == null || titleRaw.isBlank()
        ? this.plugin.messages().component("invsee.title", Map.of("player", name))
        : this.plugin.messages().deserialize(titleRaw.replace("{player}", name));
    Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
    holder.inventory = inventory;
    viewer.openInventory(inventory);
  }

  public void fill(Inventory inventory, Player target) {
    PlayerInventory playerInventory = target.getInventory();
    ItemStack[] packed = new ItemStack[41];
    for (int i = 0; i < 36; i++) {
      packed[i] = playerInventory.getItem(i);
    }
    packed[36] = playerInventory.getHelmet();
    packed[37] = playerInventory.getChestplate();
    packed[38] = playerInventory.getLeggings();
    packed[39] = playerInventory.getBoots();
    packed[40] = playerInventory.getItemInOffHand();
    fillPacked(inventory, packed);
  }

  public void fillPacked(Inventory inventory, ItemStack[] packed) {
    ItemStack[] items = packed == null ? new ItemStack[0] : packed;
    inventory.setItem(slot("helmet", 0), copyAt(items, 36));
    inventory.setItem(slot("chestplate", 1), copyAt(items, 37));
    inventory.setItem(slot("leggings", 2), copyAt(items, 38));
    inventory.setItem(slot("boots", 3), copyAt(items, 39));
    inventory.setItem(slot("offhand", 4), copyAt(items, 40));
    ItemStack filler = PanelItems.from(this.plugin, fillerSection(), null);
    if (filler.getType().isAir()) {
      filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      filler.editMeta(meta -> meta.displayName(Component.empty()));
    }
    for (int slot : SlotSpec.expand(fillerSlots())) {
      if (slot >= 0 && slot < inventory.getSize()) {
        inventory.setItem(slot, filler.clone());
      }
    }
    int start = slot("contents-start", 9);
    for (int index = 0; index < 36; index++) {
      int guiSlot = start + index;
      if (guiSlot >= 0 && guiSlot < inventory.getSize()) {
        inventory.setItem(guiSlot, copyAt(items, index));
      }
    }
  }

  public void syncFromGui(Holder holder) {
    if (holder.inventory == null) {
      return;
    }
    ItemStack[] packed = packFromGui(holder.inventory);
    if (holder.offline) {
      String encoded = ItemStacks.encode(packed);
      PlayerDataRepository repository = repository();
      if (repository != null) {
        Tasks.async(this.plugin, () -> repository.saveInventory(holder.target, encoded, true));
      }
      return;
    }
    Player target = Bukkit.getPlayer(holder.target);
    if (target == null) {
      String encoded = ItemStacks.encode(packed);
      PlayerDataRepository repository = repository();
      if (repository != null) {
        Tasks.async(this.plugin, () -> repository.saveInventory(holder.target, encoded, true));
      }
      return;
    }
    ItemStacks.restorePlayer(target.getInventory(), packed);
    String encoded = ItemStacks.encode(packed);
    PlayerDataRepository repository = repository();
    if (repository != null) {
      Tasks.async(this.plugin, () -> repository.saveInventory(holder.target, encoded, false));
    }
  }

  private ItemStack[] packFromGui(Inventory gui) {
    ItemStack[] packed = new ItemStack[41];
    int start = slot("contents-start", 9);
    for (int index = 0; index < 36; index++) {
      packed[index] = airToNull(gui.getItem(start + index));
    }
    packed[36] = airToNull(gui.getItem(slot("helmet", 0)));
    packed[37] = airToNull(gui.getItem(slot("chestplate", 1)));
    packed[38] = airToNull(gui.getItem(slot("leggings", 2)));
    packed[39] = airToNull(gui.getItem(slot("boots", 3)));
    packed[40] = airToNull(gui.getItem(slot("offhand", 4)));
    return packed;
  }

  public boolean filler(int slot) {
    return SlotSpec.contains(fillerSlots(), slot);
  }

  private PlayerDataRepository repository() {
    if (this.plugin.modules() == null || !this.plugin.modules().isEnabled("players")) {
      return null;
    }
    PlayersModule module = this.plugin.modules().get("players");
    return module == null ? null : module.repository();
  }

  private int slot(String name, int fallback) {
    ConfigurationSection slots = gui() == null ? null : gui().getConfigurationSection("slots");
    return slots == null ? fallback : slots.getInt(name, fallback);
  }

  private String fillerSlots() {
    ConfigurationSection filler = fillerSection();
    return filler == null ? "5-8" : filler.getString("slots", "5-8");
  }

  private ConfigurationSection fillerSection() {
    return gui() == null ? null : gui().getConfigurationSection("filler");
  }

  private ConfigurationSection gui() {
    return this.plugin.configs().moderation().getConfigurationSection("invsee.gui");
  }

  private static ItemStack copyAt(ItemStack[] items, int index) {
    if (items == null || index < 0 || index >= items.length || items[index] == null || items[index].getType().isAir()) {
      return new ItemStack(Material.AIR);
    }
    return items[index].clone();
  }

  private static ItemStack airToNull(ItemStack item) {
    return item == null || item.getType().isAir() ? null : item;
  }

  public static final class Holder implements InventoryHolder {
    private final UUID target;
    private final String name;
    private final boolean offline;
    private Inventory inventory;

    Holder(UUID target, String name, boolean offline) {
      this.target = target;
      this.name = name;
      this.offline = offline;
    }

    public UUID target() {
      return this.target;
    }

    public String name() {
      return this.name;
    }

    public boolean offline() {
      return this.offline;
    }

    @Override
    public Inventory getInventory() {
      return this.inventory;
    }
  }
}
