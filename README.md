# modernCore

A modular core for Paper 26.2 (`paper-api` 26.2.build.134, Java 25, version 1.1.3). It covers the everyday server commands: spawn, homes, warps, chat, moderation, kits, sell, portals, and holograms. Commands keep the vanilla names (`/kick`, `/ban`, `/vanish`). There is no `/cmi`-style prefix.

Hooks, all soft-depends: MMOItems, MythicLib, Vault, CoinsEngine, ExcellentEconomy, Multiverse-Core, PlaceholderAPI, TAB, VoidGen.

Built with Gradle. Repository: https://github.com/Nyxon-com/modernCore

Permission nodes use the `valary` namespace, because the plugin runs on ValaryRPG.

Guides: [GUIDE.md](GUIDE.md), [Holograms](wiki/Holograms.md), [Triggerblocks](wiki/Triggerblocks.md), [Chat](wiki/Chat.md), [Webhooks](wiki/Webhooks.md).

## What it does

- Per-world spawn, weather lock, and time lock (`/timelock`)
- Homes and warps. From console: `/warp <name> <player>`
- Join and quit messages, MiniMessage nick (chat and tab), msg/tell/r, social spy, fly, god, heal, feed, back, and a gamemode that survives world changes
- Moderation: kick, ban with a countdown, ipban, mute, warn, `/history`, dupeip, vanish, invsee
- `/unban`, `/unbanip`, `/pardon-ip`
- Kits in a GUI, a first-join kit, and MMOItems kits
- Sell GUI, `prices.yml`, rewards as money, xp, commands, or items
- LimitY, holograms on dropped items, configurable death messages, `/checkdeath` with the inventory, `/copycoord`
- Long jump and `/pressureplayerjump`
- Portals with a wand, block clicks, and commands on entry
- Chat: `/clearchat`, rotating announcements, word/link/IP filter
- Async Discord webhooks for staff logs and SEVERE console errors. The URL lives only in `plugins/modernCore/` on the server, not in the repository
- YAML reload that merges new keys and regenerates broken files

Expeditions, the VIP XP bonus, SMP, eco rolls, the adventure pickaxe, multihit, the overhead bar, and the MMOItems/MMOCore fixes are not part of this repository.

## Modules

Registered by `ModernCorePlugin`: `worlds`, `homes`, `warps`, `moderation`, `players`, `mechanics`, `holograms`, `kits`, `sell`, `chat`, `features`.

Turn one off with `modules.<id>: false` in `config.yml`. Graves (death, inventory, menu) live inside `mechanics`.

## Build

```bash
./gradlew.bat build
```

JAR: `build/libs/modernCore-1.1.3.jar`
