# modernCore

Core modulare per Paper 26.2 (`paper-api` 26.2.build.134, Java 25, versione 1.1.3). Copre i comandi base del server: spawn, home, warp, chat, moderazione, kit, sell, portali, hologram. I comandi usano i nomi vanilla (`/kick`, `/ban`, `/vanish`), senza un prefisso tipo `/cmi`.

Hook: MMOItems, MythicLib, Vault, CoinsEngine, ExcellentEconomy, Multiverse-Core, PlaceholderAPI, TAB, VoidGen. Tutti soft-depend.

Build con Gradle. Repository: https://github.com/Nyxon-com/modernCore

I permessi nel codice usano il namespace `valary`, perché il plugin gira sul server ValaryRPG.

Guide: [GUIDE.md](GUIDE.md), [Holograms](wiki/Holograms.md), [Triggerblocks](wiki/Triggerblocks.md), [Chat](wiki/Chat.md), [Webhooks](wiki/Webhooks.md).

## Cosa fa

- Spawn per mondo, lock meteo e lock ora (`/timelock`)
- Home e warp. Da console: `/warp <nome> <player>`
- Join/quit, nick MiniMessage (chat e tab), msg/tell/r, socialspy, fly, god, heal, feed, back, gamemode che resta tra i mondi
- Moderazione: kick, ban con countdown, ipban, mute, warn, `/history`, dupeip, vanish, invsee
- `/unban`, `/unbanip`, `/pardon-ip`
- Kit in GUI, kit al primo join, kit MMOItems
- Sell in GUI, `prices.yml`, reward money/xp/comandi/item
- LimitY, hologram sui drop, morti configurabili, `/checkdeath` con inventario, `/copycoord`
- Long jump e `/pressureplayerjump`
- Portali con wand, click sui blocchi e comandi all'ingresso
- Chat: `/clearchat`, annunci ciclici, filtro parole/link/IP
- Webhook Discord in async per il log staff e gli errori SEVERE in console. L'URL sta solo in `plugins/modernCore/` sul server, non nel repository
- Reload dei YAML con merge delle chiavi nuove e rigenerazione dei file rotti

Spedizioni, bonus XP VIP, SMP, roll degli eco, piccone adventure, multihit, la barra sopra la testa e i fix di MMOItems/MMOCore non fanno parte di questo repository.

## Moduli

Registrati da `ModernCorePlugin`: `worlds`, `homes`, `warps`, `moderation`, `players`, `mechanics`, `holograms`, `kits`, `sell`, `chat`, `features`.

Si spengono da `config.yml` con `modules.<id>: false`. Le tombe (morte, inventario, menu) stanno dentro `mechanics`.

## Build

```bash
./gradlew.bat build
```

JAR: `build/libs/modernCore-1.1.3.jar`
