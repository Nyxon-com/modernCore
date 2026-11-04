# modernCore guide

Paper plugin **26.2** (Java 25, version 1.1.3). Native commands: `/kick`, `/ban`, `/vanish`, `/invsee` — **never** `/cmi`.

Repository: https://github.com/Nyxon-com/modernCore

---

## Install

1. JDK 25, Paper 26.2
2. `./gradlew.bat build` → `build/libs/modernCore-1.1.3.jar`
3. Put the JAR in `plugins/`
4. Optional soft-depends: Vault, CoinsEngine, MMOItems, MythicLib, Multiverse-Core, PlaceholderAPI
5. Start the server, then edit the YAML in `plugins/modernCore/`
6. `/valary reload` (or `/vcore reload`) after edits. **SQL storage does not reconnect**: change `storage.yml` and restart.

Files are created on first start. A broken YAML is renamed `*.broken.<timestamp>.yml` and regenerated. New keys are added on their own and do not touch your values.

Reload reads and validates every YAML first. It does not run migrations and it does not rewrite files. If a single YAML is invalid, the previous configuration stays and the bad path is printed to the console. Hologram and triggerblock coordinates, text, and style are respawned from the new values. Command names, aliases, and the `storage.yml` connection still need a restart.

Turn a module off in `config.yml` → `modules.<id>: false`.

---

## Files

YAML is grouped in folders, not dumped in the root. On first start (or on reload) old root files are moved on their own.

| Path | Contents |
|---|---|
| `config.yml` | modules, command names, aliases, permissions, teleport HUD |
| `storage.yml` | SQLITE / MYSQL / MARIADB |
| `lang/messages.yml` | every MiniMessage string |
| `sounds/sounds.yml` | sounds (namespaced id, volume, pitch) |
| `modules/worlds.yml` | spawn, weather lock, time lock, world catalog |
| `modules/homes.yml` | home limits, warmup/cooldown |
| `modules/warps.yml` | warp list, warmup/cooldown |
| `modules/players.yml` | join/quit, nick, msg, fly, heal/feed |
| `modules/moderation.yml` | default reasons, mute, vanish, invsee layout |
| `modules/mechanics.yml` | LimitY, drop holograms, deaths, long-jump, portals, triggerblocks |
| `modules/holograms.yml` | `/vholo` settings |
| `holo/<name>.yml` | One text hologram (Fancy style) |
| `modules/kits.yml` | kit GUI and kit contents |
| `modules/prices.yml` | sell GUI, vanilla/MMOItems prices, custom rewards |
| `modules/chat.yml` | `/clearchat`, rotating announcements, chat filter |
| `modules/webhooks.yml` | Discord webhooks (staff log and SEVERE errors) |

Permissions: change the node in `config.yml` → `permissions.*`, not in Java. Defaults live in `plugin.yml`.

MiniMessage: `<red>`, `<gradient:#8B5CF6:#EC4899>`, `&#FF00AA`, `<lang:item.minecraft.diamond>`.

---

## Worlds and spawn

- `/setspawn` — sets **the** spawn (one point, in the world you are standing in)
- `/spawn` — takes you there from any world. `/spawn <player>` with `valary.spawn.others`
- On every login the player is sent to the global spawn (`modules/worlds.yml` → `settings.spawn-on-join`).
- `/weatherlock <clear|rain|thunder|unlock> [world]` — world and client weather (also needed in void spawns)
- `/timelock <day|noon|night|midnight|unlock> [world]` — stops the cycle; `/time add` is rolled back
- `/falldamage <true|false> [world]` — fall damage per world (`/falldamage false spawn`). Stored in `worlds.yml` (`fall-damage`)

If **Multiverse-Core** is present, pvp, difficulty, gamemode, hunger, and flight stay on Multiverse. Do not duplicate them in `worlds.yml`. Valary still owns `/setspawn`, weatherlock, timelock, and `/falldamage`. Without Multiverse, the catalog in `worlds.yml` is applied as before.

PlaceholderAPI: `%valary_world%` `%valary_world_alias%` `%valary_nick%` `%valary_nick_mm%` `%valary_god%` `%valary_vanished%` `%valary_fly%`

---

## Homes and warps

- `/sethome [name]` `/home [name]` `/delhome [name]` `/homes`
- Limit: permission `valary.home.limit.<group>` (see `modules/homes.yml`). `valary.home.unlimited` = no cap
- Warmup and cooldown in YAML; the countdown is an action bar (`config.yml` → `teleport.hud`). Bypass cooldown: `valary.teleport.cooldown.bypass`
- Sounds in `sounds/sounds.yml` (volume, pitch, pack id)
- No quotes in names: message `general.quotes` in `lang/messages.yml`
- `/setwarp <name>` `/warp <name>` `/warp <name> <player>` `/delwarp` `/warps`
- From console: `/warp <name> <player>` (a nick, not `@p`)

---

## Players

- Join and quit in `lang/messages.yml` → `join-quit.*`. An empty string means no message
- `/nick <text|off>` MiniMessage. Colors: `valary.nick.color`. Other players: `valary.nick.others`
  - Chat: `modules/players.yml` → `chat.format` (`{displayname}` `{message}` `%luckperms_prefix%`)
  - Vanilla tab: `playerListName`. If you use **TAB** (NEZNAMY), Valary sets the name through the API; the TAB config can also use `%valary_nick%` / `%valary_nick_mm%`
- `/clearchat` (`/cc`) — admin only, clears chat for **every** online player
- Automatic announcements every N seconds in `modules/chat.yml` → `announcements`
- Chat filter (words, regex, IP, links) also covers `/msg` when `filter.private-messages: true`. Bypass: `valary.chat.filter.bypass`
- `/msg` `/tell` `/w` `/whisper` `/m` `/r` — text in `lang/messages.yml` → `msg.*` (`to-sender`, `to-target`, `spy`)
- `/socialspy` (`/ss`): private messages show as `[SS]`. `msg.spy-self: true` also shows your own (a two-account test). `false` = other people only
- `/fly` persists across relog and death. `/gamemode` persists **across worlds** and after respawn (including Multiverse), unless `worlds.<world>.force-gamemode: true`
- `/god` `/heal` `/feed` `/back`

---

## Moderation (vanilla overrides)

`/kick` `/ban [duration] [reason]` `/banlist` `/pardon` `/pardon-ip` `/unban` `/unbanip` `/ipban` `/mute` `/unmute` `/warn` `/warnings` `/history` `/dupeip` `/vanish` `/vanishedit` `/invsee`

Durations: `10s` `5m` `2h` `7d` `2w` `1mo` `1y` `perm`. Empty means permanent.

**Remove an IP ban:** `/unbanip <player|ip>`, or `/unban <player> <reason>` (also clears the IP ban), or `/pardon-ip <ip> <reason>`.

`/history <player>` — kick, ban, ipban, mute, warn. **REVOKED** (pardon) is not **EXPIRED**. `/pardon <player> <reason>` requires a reason. `/pardon list` lists pardons.

`/dupeip <player|ip>` — accounts on the same IP, online, banned, muted, or warned. Text in `moderation.dupeip*`.

A timed ban screen is a native dialog with a countdown (`screen.live-countdown`). Layout: `lang/messages.yml` → `moderation.ban-dialog-*`.

`/vanishedit nightvision|pickup`

---

## Kits

`/kit` `/kits` opens the GUI. **Left click** claims, **right click** previews the contents (there is no separate book button). From the preview: **Back** returns to the page, **Claim** takes the kit. `/kit <id> [player]` from command or console.

- Cooldown and one-time claims live in SQL (`kit_claims`), cached in RAM (no SQL on chat or click)
- In the GUI: available (glow) / readable cooldown / locked / already claimed, plus the item list
- `settings.first-join-kit: starter` — given on the **first join** (`hasPlayedBefore() == false`)
- Clicks in `kits.yml` (`claim-clicks` / `preview-clicks`). Sounds in `sounds.yml` (`kits-claim`, `kits-preview`, `kits-deny`)
- MMOItems: `mmoitem-type` + `mmoitem-id`
- Staff `/kit <id> <player>` ignores the target's permission and cooldown
- Kit commands on claim: `[console]` `[player]` `[sound]` `[message]`

---

## Sell

`/sell` opens the GUI. `/sellall` sells the inventory.

Prices in `prices.yml` (a file outside `config.yml`):

- `worth.DIAMOND: 25.0`
- `mmoitems.SWORD.STEEL_SWORD: 40.0`
- `custom.<id>` — money, xp, commands, extra items

Without Vault the command reports `sell.no-economy`.

---

## Mechanics

### LimitY

`/limity` shows the range and the command of the world you are in.
`/limity <min> <max>` sets the range **only** in that world (in game, a third argument is ignored). From console: `/limity <min> <max> <world>`.
`/limity command <command>` sets the command for the world you are in. Examples:

- `/limity command [player] spawn`
- `/limity command [console] warp netherhub {player}`

`/limity clearcommand [world]` removes the command. Worlds with no section in `mechanics.yml` have no limit.

`teleport-spawn` turns off when you set a command, so it does not fight the command.

### Ground items

A small hologram (`font` + `scale`). `show-lore: false` hides the item lore. Despawn countdown in `despawn-format` (`despawn-seconds: 300` = 5 minutes).

### Deaths

Messages in `mechanics.yml` → `deaths.messages.<CAUSE>`.
`/checkdeath [player]` — coords (click to copy), cause, killer, and a GUI of the inventory at death.
`/checkdeath restore [player]` — restores that inventory onto the player (they must be online). Permission `valary.checkdeath.restore`.

### Coordinates

`/copycoord [player]` — clicking the message copies the **targeted block** (`world x y z`). Range in `mechanics.yml` → `copycoord.range`.

### Pressure-plate long jump

Plates per material in `long-jump.plates`.

- Step on the plate → jump
- Looking at it does **not** jump. `/pressureplayerjump` only inspects or binds the block you are looking at
- `/pressureplayerjump set <gold|iron>` binds **that** block to the preset
- `/pressureplayerjump remove` clears the binding

### Portals

1. `/portal wand` — take the wand
2. Right-click a block = pos1, Shift+right-click = pos2 (the **blocks** are the cuboid, including walking on top of them)
3. Shift+left-click = destination (your position and look), or `/portal dest`
4. `/portal create <name>`
5. `/portal command <name> [player] spawn` — or `[message] <green>ok</green>`, `[console] say {player}`
6. `/portal item <name>` with the item in hand (empty hand = no item). MMOItems is recognized
7. `/portal enable|disable|delete|list` and `/portal clearcommands <name>`

Without a destination the portal only runs commands. Commands run **after** the movement tick, otherwise Paper drops them. `portal-settings.y-padding` in `mechanics.yml` (default 1) keeps the portal active when you stand on the selected floor.

### Holograms (`/vholo`) and triggerblocks

Two different systems:

| | Hologram | Triggerblock |
|---|---|---|
| Command | `/vholo` | `/triggerblock` (`/tblock`) |
| File | `holo/<name>.yml` | `modules/mechanics.yml` → `holo-blocks.holos` |
| Reload | `/vholo reload` or `/valary reload` | `/valary reload` only |
| Click | no, text only | yes, runs ActionCommands |

Full YAML keys: [wiki/Holograms.md](wiki/Holograms.md) and [wiki/Triggerblocks.md](wiki/Triggerblocks.md).

Always edit `plugins/modernCore/`, not the YAML in the repository. `/vholo delete` does not delete triggerblocks.

---

## ActionCommands (GUI, kits, sell, LimitY, portals, triggerblocks)

```
- "[close]"
- "[console] say {player} ..."
- "[player] spawn"
- "[sound] UI_BUTTON_CLICK"
- "[message] <green>Done"
- "[msg] <gray>alias of message"
- "eco give {player} 10"   # no tag = console (on triggerblocks / portals, no tag = player)
```

Placeholders: `{player}` `{world}` `{x}` `{y}` `{z}` and the extras for that context (`{portal}` `{holo}` `{amount}` `{min}` `{max}`). `{player}` / `%player_name%` on triggers.

---

## Permissions, short list

Default group: `valary.spawn`, `valary.home*`, `valary.warp`, `valary.msg`, `valary.nick`, `valary.kit`, `valary.sell`, `valary.checkdeath`, `valary.copycoord`, `valary.back`

Staff: `valary.admin` (every child) or the individual nodes `valary.kick`, `valary.ban`, `valary.vanish`, `valary.invsee`, `valary.portal`, `valary.holo`, `valary.triggerblock`, `valary.limity`, `valary.time.lock`, `valary.pressureplayerjump`, `valary.clearchat`, ...

---

## Discord webhooks

URL in `modules/webhooks.yml`. Async HTTP, one `valary-webhook` thread.

- `logs`: kick/ban/mute/warn, filter, clearchat, reload. The full chat log only if `logs.categories.chat: true`
- `errors`: console **SEVERE** only. No warnings, no OOM/heap/GC/memory

Wiki: [wiki/Chat.md](wiki/Chat.md) and [wiki/Webhooks.md](wiki/Webhooks.md).

---

## Storage

Default SQLite at `plugins/modernCore/data.db`. For MySQL or MariaDB set `storage.type` and the credentials, then **restart**.

Tables (prefix `valary_`): `homes`, `players` (nick, fly, vanish, god, last death, IP), `punishments`, `kit_claims`.
