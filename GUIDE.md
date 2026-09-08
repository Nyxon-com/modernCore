# Guida ValaryRPG-Core

Plugin Paper **1.21.11** (Java 21). Comandi nativi: `/kick`, `/ban`, `/vanish`, `/invsee` — **mai** `/cmi`.

Repository: https://github.com/Nyxon-com/ValaryRPGcore

---

## Installazione

1. JDK 21+, Paper 1.21.11
2. `./gradlew.bat build` → `build/libs/ValaryRPG-Core-1.0.0.jar`
3. Metti il JAR in `plugins/`
4. Soft-depend opzionali: Vault, CoinsEngine, MMOItems, MythicLib, Multiverse-Core, PlaceholderAPI
5. Avvia, poi edita i YAML in `plugins/ValaryRPG-Core/`
6. `/valary reload` (o `/vcore reload`) dopo le modifiche. Lo **storage SQL non si riconnette**: cambia `storage.yml` e riavvia.

Al primo avvio i file vengono creati. Se un YAML è corrotto viene rinominato `*.broken.<timestamp>.yml` e rigenerato. Chiavi nuove si aggiungono da sole senza toccare i tuoi valori.

Disattiva un modulo in `config.yml` → `modules.<id>: false`.

---

## File

| File | Contenuto |
|---|---|
| `config.yml` | moduli, nomi comandi, alias, nodi permesso, suoni |
| `messages.yml` | tutti i testi MiniMessage |
| `storage.yml` | SQLITE / MYSQL / MARIADB |
| `worlds.yml` | spawn, meteo lock, ora lock, catalogo mondi |
| `homes.yml` | limiti home per gruppo |
| `warps.yml` | elenco warp |
| `players.yml` | join/quit, nick, msg, fly, heal/feed |
| `moderation.yml` | motivi default, mute blocked-commands, vanish, layout invsee |
| `mechanics.yml` | LimitY, hologram drop, morti, long-jump, portali |
| `kits.yml` | GUI kit + contenuto kit |
| `prices.yml` | GUI sell, prezzi vanilla/MMOItems, reward custom |

Permessi: cambia il nodo in `config.yml` → `permissions.*`, non nel Java. Default in `plugin.yml`.

MiniMessage: `<red>`, `<gradient:#8B5CF6:#EC4899>`, `&#FF00AA`, `<lang:item.minecraft.diamond>`.

---

## Mondi e spawn

- `/setspawn` — spawn del mondo in cui sei
- `/spawn` — tp al tuo mondo (o default). `/spawn <player>` con `valary.spawn.others`
- `/weatherlock <clear|rain|thunder|unlock> [mondo]`
- `/timelock <day|noon|night|midnight|unlock> [mondo]` — blocca anche il ciclo giorno/notte (`GameRule.DO_DAYLIGHT_CYCLE`)

I mondi Bukkit/Multiverse vengono importati in `worlds.yml` solo se la chiave manca. Poi puoi settare pvp, difficulty, gamemode, hunger, flight.

PlaceholderAPI: `%valary_world%` `%valary_world_alias%` `%valary_nick%` `%valary_god%` `%valary_vanished%` `%valary_fly%`

---

## Home e warp

- `/sethome [nome]` `/home [nome]` `/delhome [nome]` `/homes`
- Limite: permesso `valary.home.limit.<gruppo>` (vedi `homes.yml`). `valary.home.unlimited` = infinito
- `/setwarp <nome>` `/warp <nome>` `/warp <nome> <player>` `/delwarp` `/warps`
- Da console: `/warp <nome> <player>` (nick, non `@p`)

---

## Player

- Join/quit in `messages.yml` → `join-quit.*`. Stringa vuota = nessun messaggio
- `/nick <testo|off>` MiniMessage. Colori: `valary.nick.color`. Altri: `valary.nick.others`
- `/msg` `/tell` `/w` `/whisper` `/m` (sostituiscono i vanilla) `/r` `/socialspy`
- `/fly` `/god` `/heal` `/feed` `/back`

---

## Moderazione (override vanilla)

`/kick` `/ban [durata] [motivo]` `/banlist` `/pardon` `/pardon-ip` `/ipban` `/mute` `/unmute` `/warn` `/warnings` `/dupeip` `/vanish` `/vanishedit` `/invsee`

Durate: `10s` `5m` `2h` `7d` `2w` `1mo` `1y` `perm`. Vuoto = permanente.

`/dupeip <player|ip>` mostra gli account sullo stesso IP **e** se sono online, bannati, ipban, mutati, quanti warn.

`/vanishedit nightvision|pickup`

---

## Kit

`/kit` `/kits` apre la GUI. `/kit <id> [player]` da comando/console.

- Cooldown e one-time su SQL (`kit_claims`)
- `settings.first-join-kit: starter` — dato al **primo join** (`hasPlayedBefore() == false`)
- Item MMOItems: `mmoitem-type` + `mmoitem-id`
- GUI: ogni slot ha `action`, `click-sound`, `commands` (`[close]` `[console]` `[player]` `[sound]` `[message]`)

---

## Sell

`/sell` GUI. `/sellall` vende lo zaino.

Prezzi in `prices.yml` (file esterno a `config.yml`):

- `worth.DIAMOND: 25.0`
- `mmoitems.SWORD.STEEL_SWORD: 40.0`
- `custom.<id>` — money, xp, commands, items extra

Senza Vault il comando avvisa `sell.no-economy`.

---

## Meccaniche

### LimitY
`/limity` mostra. `/limity <min> <max> [mondo]` salva.
Arrivati fuori range: esegue `commands` (ActionCommands) e opzionalmente tp spawn (`teleport-spawn`).

### Item a terra
Hologram piccolo (`font` + `scale`). `show-lore: false` nasconde la lore dell'item. Countdown despawn in `despawn-format` (`despawn-seconds: 300` = 5 minuti).

### Morti
Messaggi in `mechanics.yml` → `deaths.messages.<CAUSE>`.
`/checkdeath [player]` — coords (click = copia), causa, killer, GUI inventario al momento della morte.

### Coordinate
`/copycoord [player]` — click sul messaggio = Ctrl+C (`world x y z`).

### Pressure plate long-jump
Piastre per materiale in `long-jump.plates`.
- Step sulla plate → jump
- `look-trigger: true` → jump anche **mirando** la plate
- `/pressureplayerjump` info sul blocco mirato
- `/pressureplayerjump set <gold|iron>` associa **quel** blocco al preset
- `/pressureplayerjump remove` toglie l'associazione

### Portali
1. `/portal wand` — prendi la wand
2. Destro su un blocco = pos1, Shift+destro = pos2, Shift+sinistro = dest (posizione tua)
3. `/portal create <nome>`
4. `/portal command <nome> [console] warp spawn` (o `[player] spawn`, `[message] ...`)
5. `/portal item <nome>` con l'oggetto in mano (MMOItems riconosciuto)
6. `/portal enable|disable|delete|list`

Se non imposti dest, il portale esegue solo i comandi.

---

## ActionCommands (GUI, kit, sell, LimitY, portali)

```
- "[close]"
- "[console] say {player} ..."
- "[player] spawn"
- "[sound] UI_BUTTON_CLICK"
- "[message] <green>Fatto"
- "eco give {player} 10"   # senza tag = console
```

Placeholder: `{player}` `{world}` `{x}` `{y}` `{z}` e quelli extra del contesto (`{portal}` `{amount}` `{min}` `{max}`).

---

## Permessi rapidi

Gruppo default: `valary.spawn`, `valary.home*`, `valary.warp`, `valary.msg`, `valary.nick`, `valary.kit`, `valary.sell`, `valary.checkdeath`, `valary.copycoord`, `valary.back`

Staff: `valary.admin` (tutti i figli) oppure i singoli `valary.kick`, `valary.ban`, `valary.vanish`, `valary.invsee`, `valary.portal`, `valary.limity`, `valary.time.lock`, `valary.pressureplayerjump`, ...

---

## Storage

Default SQLite `plugins/ValaryRPG-Core/data.db`. Per MySQL/MariaDB imposta `storage.type` e le credenziali, poi **riavvia**.

Tabelle (prefisso `valary_`): `homes`, `players` (nick, fly, vanish, god, ultima morte, IP), `punishments`, `kit_claims`.
