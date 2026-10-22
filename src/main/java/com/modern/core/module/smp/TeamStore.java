//made with love by nyxon

package com.modern.core.module.smp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class TeamStore {
  public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
  private static final String[] COLORS = {"&9", "&b", "&3", "&1"};

  public static final class Team {
    public String id;
    public String name;
    public UUID leader;
    public final List<UUID> members = new ArrayList<>();
    public final Set<UUID> invites = new HashSet<>();
    public String world;
    public Double x;
    public Double y;
    public Double z;
    public Float yaw;
    public Float pitch;
  }

  private final SmpContext plugin;
  private final File file;
  private final Map<String, Team> teams = new ConcurrentHashMap<>();
  private final Map<UUID, String> byPlayer = new ConcurrentHashMap<>();
  private final Set<UUID> teamChat = new HashSet<>();

  public TeamStore(SmpContext plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "teams.yml");
    load();
  }

  public Team teamOf(Player player) {
    String id = byPlayer.get(player.getUniqueId());
    return id == null ? null : teams.get(id);
  }

  public Team byName(String name) {
    return teams.get(name.toLowerCase(Locale.ROOT));
  }

  public boolean allowPvp(Player attacker, Player victim) {
    if (plugin.getConfig().getBoolean("team-pvp", false)) {
      return true;
    }
    Team left = teamOf(attacker);
    Team right = teamOf(victim);
    return left == null || right == null || !left.id.equals(right.id);
  }

  public String create(Player leader, String name) {
    if (!NAME.matcher(name).matches()) {
      return "Il nome deve essere 3-16 lettere, numeri o _.";
    }
    if (teamOf(leader) != null) {
      return "Sei gia in un team.";
    }
    String id = name.toLowerCase(Locale.ROOT);
    if (teams.containsKey(id)) {
      return "Questo team esiste gia.";
    }
    Team team = new Team();
    team.id = id;
    team.name = name;
    team.leader = leader.getUniqueId();
    team.members.add(leader.getUniqueId());
    teams.put(id, team);
    byPlayer.put(leader.getUniqueId(), id);
    save();
    return null;
  }

  public String invite(Player leader, Player target) {
    Team team = teamOf(leader);
    if (team == null || !team.leader.equals(leader.getUniqueId())) {
      return "Solo il capo puo invitare.";
    }
    if (teamOf(target) != null) {
      return target.getName() + " e gia in un team.";
    }
    if (team.members.size() >= maxMembers()) {
      return "Il team e pieno.";
    }
    team.invites.add(target.getUniqueId());
    save();
    return null;
  }

  public String join(Player player, String name) {
    if (teamOf(player) != null) {
      return "Sei gia in un team.";
    }
    Team team = byName(name);
    if (team == null || !team.invites.contains(player.getUniqueId())) {
      return "Non hai un invito per questo team.";
    }
    if (team.members.size() >= maxMembers()) {
      return "Il team e pieno.";
    }
    team.invites.remove(player.getUniqueId());
    team.members.add(player.getUniqueId());
    byPlayer.put(player.getUniqueId(), team.id);
    save();
    return null;
  }

  public String leave(Player player) {
    Team team = teamOf(player);
    if (team == null) {
      return "Non sei in un team.";
    }
    removeMember(team, player.getUniqueId());
    save();
    return null;
  }

  public String kick(Player leader, String targetName) {
    Team team = teamOf(leader);
    if (team == null || !team.leader.equals(leader.getUniqueId())) {
      return "Solo il capo puo cacciare.";
    }
    UUID target = memberByName(team, targetName);
    if (target == null || target.equals(leader.getUniqueId())) {
      return "Quel giocatore non e nel team.";
    }
    removeMember(team, target);
    save();
    return null;
  }

  public String disband(Player leader) {
    Team team = teamOf(leader);
    if (team == null || !team.leader.equals(leader.getUniqueId())) {
      return "Solo il capo puo sciogliere il team.";
    }
    for (UUID member : new ArrayList<>(team.members)) {
      byPlayer.remove(member);
      teamChat.remove(member);
    }
    teams.remove(team.id);
    save();
    return null;
  }

  public boolean toggleChat(Player player) {
    if (teamOf(player) == null) {
      return false;
    }
    if (!teamChat.add(player.getUniqueId())) {
      teamChat.remove(player.getUniqueId());
      return false;
    }
    return true;
  }

  public boolean teamChat(Player player) {
    return teamChat.contains(player.getUniqueId()) && teamOf(player) != null;
  }

  public String setHome(Player player) {
    Team team = teamOf(player);
    if (team == null || !team.leader.equals(player.getUniqueId())) {
      return "Solo il capo puo mettere la home.";
    }
    Location location = player.getLocation();
    team.world = location.getWorld().getName();
    team.x = location.getX();
    team.y = location.getY();
    team.z = location.getZ();
    team.yaw = location.getYaw();
    team.pitch = location.getPitch();
    save();
    return null;
  }

  public String home(Player player) {
    Team team = teamOf(player);
    if (team == null) {
      return "Non sei in un team.";
    }
    if (team.world == null) {
      return "Il team non ha una home.";
    }
    World world = Bukkit.getWorld(team.world);
    if (world == null) {
      return "Il mondo della home non e caricato.";
    }
    player.teleport(new Location(world, team.x, team.y, team.z, team.yaw, team.pitch));
    return null;
  }

  public void sendTeamChat(Player sender, String text) {
    Team team = teamOf(sender);
    if (team == null) {
      return;
    }
    String line = color(team) + "[" + team.name + "] &f" + sender.getName() + "&7: &f" + text;
    var component = LegacyComponentSerializer.legacyAmpersand().deserialize(line);
    for (UUID uuid : team.members) {
      Player member = Bukkit.getPlayer(uuid);
      if (member != null) {
        member.sendMessage(component);
      }
    }
  }

  public List<Team> all() {
    return new ArrayList<>(teams.values());
  }

  public String color(Team team) {
    return COLORS[Math.floorMod(team.name.hashCode(), COLORS.length)];
  }

  private void removeMember(Team team, UUID uuid) {
    team.members.remove(uuid);
    team.invites.remove(uuid);
    byPlayer.remove(uuid);
    teamChat.remove(uuid);
    if (team.members.isEmpty()) {
      teams.remove(team.id);
      return;
    }
    if (team.leader.equals(uuid)) {
      team.leader = team.members.get(0);
    }
  }

  private UUID memberByName(Team team, String name) {
    for (UUID uuid : team.members) {
      String known = Bukkit.getOfflinePlayer(uuid).getName();
      if (known != null && known.equalsIgnoreCase(name)) {
        return uuid;
      }
    }
    return null;
  }

  private int maxMembers() {
    return Math.max(1, plugin.getConfig().getInt("team-max-members", 8));
  }

  private void load() {
    teams.clear();
    byPlayer.clear();
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection root = yaml.getConfigurationSection("teams");
    if (root == null) {
      return;
    }
    for (String id : root.getKeys(false)) {
      ConfigurationSection section = root.getConfigurationSection(id);
      if (section == null) {
        continue;
      }
      Team team = new Team();
      team.id = id;
      team.name = section.getString("name", id);
      team.leader = UUID.fromString(section.getString("leader"));
      for (String raw : section.getStringList("members")) {
        UUID uuid = UUID.fromString(raw);
        team.members.add(uuid);
        byPlayer.put(uuid, id);
      }
      for (String raw : section.getStringList("invites")) {
        team.invites.add(UUID.fromString(raw));
      }
      if (section.contains("home.world")) {
        team.world = section.getString("home.world");
        team.x = section.getDouble("home.x");
        team.y = section.getDouble("home.y");
        team.z = section.getDouble("home.z");
        team.yaw = (float) section.getDouble("home.yaw");
        team.pitch = (float) section.getDouble("home.pitch");
      }
      teams.put(id, team);
    }
  }

  private void save() {
    YamlConfiguration yaml = new YamlConfiguration();
    for (Team team : teams.values()) {
      String path = "teams." + team.id;
      yaml.set(path + ".name", team.name);
      yaml.set(path + ".leader", team.leader.toString());
      yaml.set(path + ".members", team.members.stream().map(UUID::toString).toList());
      yaml.set(path + ".invites", team.invites.stream().map(UUID::toString).toList());
      if (team.world != null) {
        yaml.set(path + ".home.world", team.world);
        yaml.set(path + ".home.x", team.x);
        yaml.set(path + ".home.y", team.y);
        yaml.set(path + ".home.z", team.z);
        yaml.set(path + ".home.yaw", team.yaw);
        yaml.set(path + ".home.pitch", team.pitch);
      }
    }
    try {
      yaml.save(file);
    } catch (IOException ex) {
      plugin.getLogger().log(Level.WARNING, "Impossibile salvare i team", ex);
    }
  }
}
