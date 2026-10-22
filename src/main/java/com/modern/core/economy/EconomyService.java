//made with love by nyxon

package com.modern.core.economy;

import com.modern.core.ModernCorePlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyService {

  private final ModernCorePlugin plugin;
  private Economy vault;

  public EconomyService(ModernCorePlugin plugin) {
    this.plugin = plugin;
  }

  public void hook() {
    if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
      this.plugin.getLogger().warning("Vault non trovato: le funzioni economy resteranno inattive.");
      return;
    }

    RegisteredServiceProvider<Economy> registration = this.plugin.getServer()
        .getServicesManager()
        .getRegistration(Economy.class);
    if (registration == null) {
      this.plugin.getLogger().warning("Vault è presente ma non c'è un provider economy (CoinsEngine/ExcellentEconomy).");
      return;
    }

    this.vault = registration.getProvider();
    this.plugin.getLogger().info("Economy agganciata via Vault: " + this.vault.getName());

    logIfPresent("CoinsEngine");
    logIfPresent("ExcellentEconomy");
  }

  public boolean available() {
    return this.vault != null;
  }

  public double balance(OfflinePlayer player) {
    return available() ? this.vault.getBalance(player) : 0.0D;
  }

  public boolean withdraw(OfflinePlayer player, double amount) {
    return available() && this.vault.withdrawPlayer(player, amount).transactionSuccess();
  }

  public boolean deposit(OfflinePlayer player, double amount) {
    return available() && this.vault.depositPlayer(player, amount).transactionSuccess();
  }

  public boolean has(OfflinePlayer player, double amount) {
    return available() && this.vault.has(player, amount);
  }

  private void logIfPresent(String pluginName) {
    if (this.plugin.getServer().getPluginManager().getPlugin(pluginName) != null) {
      this.plugin.getLogger().info(pluginName + " rilevato.");
    }
  }
}
