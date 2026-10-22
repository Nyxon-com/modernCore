# modernCore

Plugin per Paper 1.21.11. Copre i comandi base del server: spawn, home, warp, chat, moderazione, kit, sell, portali, hologram. I comandi usano i nomi vanilla (`/kick`, `/ban`, `/vanish`), senza un prefisso tipo `/cmi`.

Hook: MMOItems, Vault, CoinsEngine, Multiverse-Core, PlaceholderAPI.

Build con Gradle, JDK 21. Repository: https://github.com/Nyxon-com/modernCore

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
- Webhook Discord in async per il log staff e gli errori SEVERE in console
- Reload dei YAML con merge delle chiavi nuove e rigenerazione dei file rotti

## Build

```bash
./gradlew.bat build
```

JAR: `build/libs/modernCore-1.0.1.jar`
