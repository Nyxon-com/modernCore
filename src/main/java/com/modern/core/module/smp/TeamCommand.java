//made with love by nyxon

package com.modern.core.module.smp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TeamCommand implements CommandExecutor, TabCompleter {
  private static final List<String> SUBS = List.of(
      "create", "invite", "join", "leave", "kick", "disband", "chat", "sethome", "home", "info", "list");

  private final SmpContext plugin;

  public TeamCommand(SmpContext plugin) {
    this.plugin = plugin;
  }

  public boolean run(CommandSender sender, String[] args) {
    return onCommand(sender, null, "team", args);
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!(sender instanceof Player player)) {
      SmpText.send(sender, "&cSolo in gioco.");
      return true;
    }
    if (!player.hasPermission("modernsmp.team")) {
      SmpText.send(player, "&cNon hai il permesso.");
      return true;
    }
    if (args.length == 0) {
      help(player);
      return true;
    }
    String sub = args[0].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "create" -> {
        if (args.length < 2) {
          SmpText.send(player, "&7/team create <nome>");
          return true;
        }
        String error = plugin.teams().create(player, args[1]);
        SmpText.send(player, error == null ? "&aTeam &f" + args[1] + " &acreato." : "&c" + error);
      }
      case "invite" -> invite(player, args);
      case "join" -> {
        if (args.length < 2) {
          SmpText.send(player, "&7/team join <nome>");
          return true;
        }
        String error = plugin.teams().join(player, args[1]);
        SmpText.send(player, error == null ? "&aSei entrato nel team." : "&c" + error);
      }
      case "leave" -> {
        String error = plugin.teams().leave(player);
        SmpText.send(player, error == null ? "&aHai lasciato il team." : "&c" + error);
      }
      case "kick" -> {
        if (args.length < 2) {
          SmpText.send(player, "&7/team kick <player>");
          return true;
        }
        String error = plugin.teams().kick(player, args[1]);
        SmpText.send(player, error == null ? "&aGiocatore cacciato." : "&c" + error);
      }
      case "disband" -> {
        String error = plugin.teams().disband(player);
        SmpText.send(player, error == null ? "&aTeam sciolto." : "&c" + error);
      }
      case "chat" -> {
        if (plugin.teams().teamOf(player) == null) {
          SmpText.send(player, "&cNon sei in un team.");
          return true;
        }
        SmpText.send(player, plugin.teams().toggleChat(player) ? "&aChat team attiva." : "&7Chat team spenta.");
      }
      case "sethome" -> {
        String error = plugin.teams().setHome(player);
        SmpText.send(player, error == null ? "&aHome del team impostata." : "&c" + error);
      }
      case "home" -> {
        if (plugin.combat().isTagged(player)) {
          SmpText.send(player, "&cNon puoi farlo mentre sei in combattimento.");
          return true;
        }
        String error = plugin.teams().home(player);
        SmpText.send(player, error == null ? "&aTeletrasportato alla home del team." : "&c" + error);
      }
      case "info" -> info(player);
      case "list" -> list(player);
      default -> help(player);
    }
    return true;
  }

  private void invite(Player player, String[] args) {
    if (args.length < 2) {
      SmpText.send(player, "&7/team invite <player>");
      return;
    }
    Player target = plugin.getServer().getPlayerExact(args[1]);
    if (target == null) {
      SmpText.send(player, "&cGiocatore non online.");
      return;
    }
    String error = plugin.teams().invite(player, target);
    if (error != null) {
      SmpText.send(player, "&c" + error);
      return;
    }
    SmpText.send(player, "&aInvito inviato a &f" + target.getName() + "&a.");
    SmpText.send(target, "&a" + player.getName() + " ti ha invitato nel team. &f/team join " + plugin.teams().teamOf(player).name);
  }

  private void info(Player player) {
    TeamStore.Team team = plugin.teams().teamOf(player);
    if (team == null) {
      SmpText.send(player, "&cNon sei in un team.");
      return;
    }
    SmpText.send(player, plugin.teams().color(team) + team.name + " &7(" + team.members.size() + "/" + plugin.getConfig().getInt("team-max-members", 8) + ")");
    StringBuilder names = new StringBuilder();
    for (var uuid : team.members) {
      var offline = plugin.getServer().getOfflinePlayer(uuid);
      if (!names.isEmpty()) {
        names.append("&7, ");
      }
      names.append(uuid.equals(team.leader) ? "&e" : "&f").append(offline.getName() == null ? "?" : offline.getName());
    }
    SmpText.send(player, names.toString());
  }

  private void list(Player player) {
    var all = plugin.teams().all();
    if (all.isEmpty()) {
      SmpText.send(player, "&7Nessun team.");
      return;
    }
    for (TeamStore.Team team : all) {
      SmpText.send(player, plugin.teams().color(team) + team.name + " &7- &f" + team.members.size());
    }
  }

  private void help(Player player) {
    SmpText.send(player, "&f/team create <nome>");
    SmpText.send(player, "&f/team invite <player> &7| &f/team join <nome>");
    SmpText.send(player, "&f/team leave &7| &f/team kick <player> &7| &f/team disband");
    SmpText.send(player, "&f/team chat &7| &f/team sethome &7| &f/team home");
    SmpText.send(player, "&f/team info &7| &f/team list");
  }

  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    if (args.length == 1) {
      String needle = args[0].toLowerCase(Locale.ROOT);
      List<String> out = new ArrayList<>();
      for (String sub : SUBS) {
        if (sub.startsWith(needle)) {
          out.add(sub);
        }
      }
      return out;
    }
    return List.of();
  }
}
