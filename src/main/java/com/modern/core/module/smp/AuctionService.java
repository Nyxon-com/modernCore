//made with love by nyxon

package com.modern.core.module.smp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class AuctionService {
  public record Listing(UUID id, UUID seller, String sellerName, ItemStack item, double price, long expires, double taxPercent) {
  }

  private final SmpContext plugin;
  private final File file;
  private final Map<UUID, Listing> listings = new LinkedHashMap<>();
  private final Map<UUID, List<ItemStack>> returns = new LinkedHashMap<>();

  public AuctionService(SmpContext plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "auctions.yml");
    load();
  }

  public void open(Player player) {
    open(player, 0, false);
  }

  public void open(Player player, int page, boolean mine) {
    tickExpired();
    MenuFrames frames = new MenuFrames(plugin);
    List<Listing> shown = visible(player, mine);
    List<Integer> slots = frames.slots("menus.auction.item-slots", "10-16,19-25,28-34,37-43");
    int pages = Math.max(1, (shown.size() + slots.size() - 1) / Math.max(1, slots.size()));
    int safePage = Math.max(0, Math.min(page, pages - 1));
    Inventory inventory = Bukkit.createInventory(new AuctionHolder(safePage, mine), frames.rows("menus.auction.rows", 6) * 9,
        frames.title("menus.auction.title", "<b><gradient:#1A6EFF:#6EEBFF>Asta</gradient></b>",
            Map.of("page", String.valueOf(safePage + 1), "pages", String.valueOf(pages))));
    frames.fill(inventory, "menus.auction.filler");
    int start = safePage * slots.size();
    for (int i = 0; i < slots.size() && start + i < shown.size(); i++) {
      inventory.setItem(slots.get(i), icon(frames, player, shown.get(start + i)));
    }
    button(frames, inventory, "menus.auction.prev", safePage > 0, "&bPagina precedente");
    button(frames, inventory, "menus.auction.next", start + slots.size() < shown.size(), "&bPagina successiva");
    String filterName = mine
        ? plugin.getConfig().getString("menus.auction.filter.name-mine", "&bTutte le aste")
        : plugin.getConfig().getString("menus.auction.filter.name-all", "&bLe mie aste");
    List<String> filterLore = plugin.getConfig().getStringList(mine
        ? "menus.auction.filter.lore-mine" : "menus.auction.filter.lore-all");
    int filterSlot = plugin.getConfig().getInt("menus.auction.filter.slot", 49);
    org.bukkit.Material filterMat = org.bukkit.Material.matchMaterial(
        plugin.getConfig().getString("menus.auction.filter.material", "CHEST"));
    if (filterSlot >= 0 && filterSlot < inventory.getSize()) {
      inventory.setItem(filterSlot, frames.stack(filterMat == null ? org.bukkit.Material.CHEST : filterMat,
          filterName, filterLore.isEmpty() ? List.of("&7Clicca per cambiare") : filterLore));
    }
    player.openInventory(inventory);
  }

  private List<Listing> visible(Player player, boolean mine) {
    List<Listing> shown = new ArrayList<>();
    for (Listing listing : listings.values()) {
      if (!mine || listing.seller.equals(player.getUniqueId())) {
        shown.add(listing);
      }
    }
    return shown;
  }

  private void button(MenuFrames frames, Inventory inventory, String path, boolean show, String fallbackName) {
    int slot = plugin.getConfig().getInt(path + ".slot", -1);
    if (!show || slot < 0 || slot >= inventory.getSize()) {
      return;
    }
    org.bukkit.Material material = org.bukkit.Material.matchMaterial(plugin.getConfig().getString(path + ".material", "ARROW"));
    inventory.setItem(slot, frames.stack(material == null ? org.bukkit.Material.ARROW : material,
        plugin.getConfig().getString(path + ".name", fallbackName),
        plugin.getConfig().getStringList(path + ".lore")));
  }

  public void sell(Player player, double price) {
    if (plugin.combat().isTagged(player)) {
      SmpText.send(player, "&cNon puoi vendere in combattimento.");
      return;
    }
    if (!plugin.economy().available()) {
      SmpText.send(player, "&cL'economia non e pronta.");
      return;
    }
    double min = plugin.getConfig().getDouble("auction.min-price", 10);
    double max = plugin.getConfig().getDouble("auction.max-price", 1_000_000);
    if (price < min || price > max) {
      SmpText.send(player, "&cIl prezzo deve stare tra &e" + SmpText.money(min) + " &ce &e" + SmpText.money(max) + "&c.");
      return;
    }
    int cap = slots(player);
    if (count(player.getUniqueId()) >= cap) {
      SmpText.send(player, "&cHai gia &f" + cap + " &coggetti in asta.");
      return;
    }
    ItemStack hand = player.getInventory().getItemInMainHand();
    if (hand.getType().isAir()) {
      SmpText.send(player, "&cTieni in mano l'oggetto da vendere.");
      return;
    }
    ItemStack listed = hand.clone();
    player.getInventory().setItemInMainHand(null);
    long hours = hours(player);
    Listing listing = new Listing(UUID.randomUUID(), player.getUniqueId(), player.getName(), listed, price,
        System.currentTimeMillis() + hours * 3_600_000L, taxOf(player));
    listings.put(listing.id, listing);
    save();
    SmpText.send(player, "&aIn asta: &f" + SmpText.pretty(listed.getType()) + " x" + listed.getAmount()
        + " &aa &e" + SmpText.money(price) + "&a.");
  }

  public void click(Player player, Inventory inventory, int slot) {
    if (!(inventory.getHolder() instanceof AuctionHolder holder)) {
      return;
    }
    MenuFrames frames = new MenuFrames(plugin);
    if (slot == plugin.getConfig().getInt("menus.auction.prev.slot", 45) && holder.page() > 0) {
      open(player, holder.page() - 1, holder.mine());
      return;
    }
    List<Integer> slots = frames.slots("menus.auction.item-slots", "10-16,19-25,28-34,37-43");
    List<Listing> shown = visible(player, holder.mine());
    int start = holder.page() * slots.size();
    if (slot == plugin.getConfig().getInt("menus.auction.next.slot", 53) && start + slots.size() < shown.size()) {
      open(player, holder.page() + 1, holder.mine());
      return;
    }
    if (slot == plugin.getConfig().getInt("menus.auction.filter.slot", 49)) {
      open(player, 0, !holder.mine());
      return;
    }
    int index = slots.indexOf(slot);
    if (index < 0 || start + index >= shown.size()) {
      return;
    }
    Listing listing = shown.get(start + index);
    if (listing.seller.equals(player.getUniqueId())) {
      cancel(player, listing.id);
      open(player, holder.page(), holder.mine());
      return;
    }
    buy(player, listing.id);
    open(player, holder.page(), holder.mine());
  }

  public void givePending(Player player) {
    List<ItemStack> pending = returns.remove(player.getUniqueId());
    if (pending == null) {
      return;
    }
    for (ItemStack stack : pending) {
      give(player, stack);
    }
    save();
    SmpText.send(player, "&eOggetti scaduti o ritirati rimessi nell'inventario.");
  }

  public void tickExpired() {
    long now = System.currentTimeMillis();
    List<UUID> gone = new ArrayList<>();
    for (Listing listing : listings.values()) {
      if (listing.expires <= now) {
        gone.add(listing.id);
      }
    }
    if (gone.isEmpty()) {
      return;
    }
    for (UUID id : gone) {
      Listing listing = listings.remove(id);
      if (listing != null) {
        returns.computeIfAbsent(listing.seller, key -> new ArrayList<>()).add(listing.item);
        Player seller = Bukkit.getPlayer(listing.seller);
        if (seller != null) {
          givePending(seller);
        }
      }
    }
    save();
  }

  private void buy(Player buyer, UUID id) {
    if (plugin.combat().isTagged(buyer)) {
      SmpText.send(buyer, "&cNon puoi comprare in combattimento.");
      return;
    }
    Listing listing = listings.remove(id);
    if (listing == null) {
      SmpText.send(buyer, "&cQuell'asta non c'e piu.");
      return;
    }
    if (!plugin.economy().withdraw(buyer, listing.price)) {
      listings.put(id, listing);
      SmpText.send(buyer, "&cTi servono &e" + SmpText.money(listing.price) + "&c.");
      save();
      return;
    }
    double tax = listing.taxPercent();
    double payout = listing.price * (100.0 - tax) / 100.0;
    OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.seller);
    if (!plugin.economy().deposit(seller, payout)) {
      plugin.economy().deposit(buyer, listing.price);
      listings.put(id, listing);
      SmpText.send(buyer, "&cPagamento al venditore fallito. Soldi restituiti.");
      save();
      return;
    }
    give(buyer, listing.item.clone());
    save();
    SmpText.send(buyer, "&aComprato per &e" + SmpText.money(listing.price) + "&a.");
    Player online = Bukkit.getPlayer(listing.seller);
    if (online != null) {
      SmpText.send(online, "&a" + buyer.getName() + " ha comprato il tuo oggetto. &e+" + SmpText.money(payout));
    }
  }

  private void cancel(Player player, UUID id) {
    Listing listing = listings.remove(id);
    if (listing == null || !listing.seller.equals(player.getUniqueId())) {
      if (listing != null) {
        listings.put(id, listing);
      }
      return;
    }
    give(player, listing.item.clone());
    save();
    SmpText.send(player, "&eAsta ritirata.");
  }

  private void give(Player player, ItemStack stack) {
    for (ItemStack left : player.getInventory().addItem(stack).values()) {
      player.getWorld().dropItemNaturally(player.getLocation(), left);
    }
  }

  private int slots(Player player) {
    int[] steps = {20, 16, 14, 12, 10, 9};
    for (int step : steps) {
      if (player.hasPermission("modernsmp.auction.slots." + step)) {
        return step;
      }
    }
    return plugin.getConfig().getInt("auction.max-listings", 8);
  }

  private double taxOf(Player player) {
    for (int step : new int[] {0, 1, 2, 3, 4}) {
      if (player.hasPermission("modernsmp.auction.tax." + step)) {
        return step;
      }
    }
    return plugin.getConfig().getDouble("auction.tax-percent", 5);
  }

  private long hours(Player player) {
    if (player.hasPermission("modernsmp.auction.hours.96")) {
      return 96;
    }
    return Math.max(1, plugin.getConfig().getLong("auction.hours", 48));
  }

  private int count(UUID seller) {
    int total = 0;
    for (Listing listing : listings.values()) {
      if (listing.seller.equals(seller)) {
        total++;
      }
    }
    return total;
  }

  private ItemStack icon(MenuFrames frames, Player viewer, Listing listing) {
    boolean own = listing.seller.equals(viewer.getUniqueId());
    Map<String, String> values = Map.of(
        "seller", listing.sellerName,
        "price", SmpText.money(listing.price),
        "item", SmpText.pretty(listing.item.getType()),
        "amount", String.valueOf(listing.item.getAmount()),
        "time", SmpText.timeLeft(listing.expires),
        "action", frames.text(own ? "menus.auction.own-line" : "menus.auction.buy-line",
            own ? "&cClicca per ritirare" : "&bClicca per comprare", Map.of()));
    ItemStack icon = listing.item.clone();
    ItemMeta meta = icon.getItemMeta();
    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
    for (String line : frames.lines("menus.auction.lore",
        List.of("&7Venditore: &f{seller}", "&7Prezzo: &b{price}", "&7Scade: &f{time}", "{action}"), values)) {
      lore.add(SmpText.parse(line));
    }
    meta.lore(lore);
    icon.setItemMeta(meta);
    return icon;
  }

  public boolean isAuction(Inventory inventory) {
    return inventory.getHolder() instanceof AuctionHolder;
  }

  private void load() {
    listings.clear();
    returns.clear();
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection root = yaml.getConfigurationSection("listings");
    if (root != null) {
      for (String key : root.getKeys(false)) {
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section == null) {
          continue;
        }
        ItemStack item = section.getItemStack("item");
        if (item == null || item.getType().isAir()) {
          continue;
        }
        try {
          listings.put(UUID.fromString(key), new Listing(
              UUID.fromString(key),
              UUID.fromString(section.getString("seller")),
              section.getString("name", "?"),
              item,
              section.getDouble("price"),
              section.getLong("expires"),
              section.contains("tax")
                  ? section.getDouble("tax")
                  : plugin.getConfig().getDouble("auction.tax-percent", 5)));
        } catch (IllegalArgumentException ignored) {
          plugin.getLogger().warning("Asta ignorata: " + key);
        }
      }
    }
    ConfigurationSection pending = yaml.getConfigurationSection("returns");
    if (pending == null) {
      return;
    }
    for (String key : pending.getKeys(false)) {
        try {
          List<ItemStack> items = new ArrayList<>();
          List<?> raw = pending.getList(key);
          if (raw != null) {
            for (Object value : raw) {
              if (value instanceof ItemStack stack && !stack.getType().isAir()) {
                items.add(stack);
              }
            }
          }
        if (!items.isEmpty()) {
          returns.put(UUID.fromString(key), items);
        }
      } catch (RuntimeException ignored) {
        plugin.getLogger().warning("Reso asta ignorato: " + key);
      }
    }
  }

  private void save() {
    YamlConfiguration yaml = new YamlConfiguration();
    for (Listing listing : listings.values()) {
      String path = "listings." + listing.id;
      yaml.set(path + ".seller", listing.seller.toString());
      yaml.set(path + ".name", listing.sellerName);
      yaml.set(path + ".price", listing.price);
      yaml.set(path + ".expires", listing.expires);
      yaml.set(path + ".tax", listing.taxPercent());
      yaml.set(path + ".item", listing.item);
    }
    for (Map.Entry<UUID, List<ItemStack>> entry : returns.entrySet()) {
      yaml.set("returns." + entry.getKey(), entry.getValue());
    }
    try {
      yaml.save(file);
    } catch (IOException ex) {
      plugin.getLogger().log(Level.WARNING, "Impossibile salvare le aste", ex);
    }
  }
}
