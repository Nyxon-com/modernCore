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

I YAML stanno in cartelle, non tutti in root. Al primo avvio (o al reload) i file vecchi in root vengono spostati da soli.

| Path | Contenuto |
|---|---|
| `config.yml` | moduli, nomi comandi, alias, permessi, HUD teleport |
| `storage.yml` | SQLITE / MYSQL / MARIADB |
| `lang/messages.yml` | tutti i testi MiniMessage |
| `sounds/sounds.yml` | suoni (ID namespaced, volume, pitch) |
| `modules/worlds.yml` | spawn, meteo lock, ora lock, catalogo mondi |
| `modules/homes.yml` | limiti home, warmup/cooldown |
| `modules/warps.yml` | elenco warp, warmup/cooldown |
| `modules/players.yml` | join/quit, nick, msg, fly, heal/feed |
| `modules/moderation.yml` | motivi default, mute, vanish, layout invsee |
| `modules/mechanics.yml` | LimitY, hologram drop, morti, long-jump, portali |
| `modules/kits.yml` | GUI kit + contenuto kit |
| `modules/prices.yml` | GUI sell, prezzi vanilla/MMOItems, reward custom |

Permessi: cambia il nodo in `config.yml` → `permissions.*`, non nel Java. Default in `plugin.yml`.

MiniMessage: `<red>`, `<gradient:#8B5CF6:#EC4899>`, `&#FF00AA`, `<lang:item.minecraft.diamond>`.

---

## Mondi e spawn

- `/setspawn` — imposta **lo** spawn (un solo punto, nel mondo in cui sei)
- `/spawn` — ti porta lì da qualsiasi mondo. `/spawn <player>` con `valary.spawn.others`
- `/weatherlock <clear|rain|thunder|unlock> [mondo]` — meteo mondo + client (serve anche negli spawn void)
- `/timelock <day|noon|night|midnight|unlock> [mondo]` — ferma il ciclo; `/time add` viene riportato indietro

Se **Multiverse-Core** è presente, pvp, difficulty, gamemode, hunger e flight restano quelli di MV: non serve rifarli in `worlds.yml`. Valary usa `/setspawn`, weatherlock e timelock. Senza MV, il catalogo in `worlds.yml` viene applicato come prima.

PlaceholderAPI: `%valary_world%` `%valary_world_alias%` `%valary_nick%` `%valary_nick_mm%` `%valary_god%` `%valary_vanished%` `%valary_fly%`

---

## Home e warp

- `/sethome [nome]` `/home [nome]` `/delhome [nome]` `/homes`
- Limite: permesso `valary.home.limit.<gruppo>` (vedi `modules/homes.yml`). `valary.home.unlimited` = infinito
- Warmup/cooldown in YAML; countdown in action bar (`config.yml` → `teleport.hud`). Bypass cooldown: `valary.teleport.cooldown.bypass`
- Suoni in `sounds/sounds.yml` (volume/pitch/ID pack)
- Niente virgolette nei nomi: messaggio `general.quotes` in `lang/messages.yml`
- `/setwarp <nome>` `/warp <nome>` `/warp <nome> <player>` `/delwarp` `/warps`
- Da console: `/warp <nome> <player>` (nick, non `@p`)

---

## Player

- Join/quit in `lang/messages.yml` → `join-quit.*`. Stringa vuota = nessun messaggio
- `/nick <testo|off>` MiniMessage. Colori: `valary.nick.color`. Altri: `valary.nick.others`
  - Chat: `modules/players.yml` → `chat.format` (`{displayname}` `{message}` `%luckperms_prefix%`)
  - Tab vanilla: `playerListName`. Se usi **TAB** (NEZNAMY), Valary imposta il nome via API; in config TAB puoi anche usare `%valary_nick%` / `%valary_nick_mm%`
- `/msg` `/tell` `/w` `/whisper` `/m` `/r` — testi in `lang/messages.yml` → `msg.*` (`to-sender`, `to-target`, `spy`)
- `/socialspy` (`/ss`): vedi i privati in `[SS]`. `msg.spy-self: true` mostra anche i tuoi (test a 2 account). `false` = solo gli altri
- `/fly` persiste al relog. `/gamemode` persiste **tra i mondi** (anche Multiverse), tranne se `worlds.<mondo>.force-gamemode: true`
- `/god` `/heal` `/feed` `/back`

---

## Moderazione (override vanilla)

`/kick` `/ban [durata] [motivo]` `/banlist` `/pardon` `/pardon-ip` `/unban` `/unbanip` `/ipban` `/mute` `/unmute` `/warn` `/warnings` `/history` `/dupeip` `/vanish` `/vanishedit` `/invsee`

Durate: `10s` `5m` `2h` `7d` `2w` `1mo` `1y` `perm`. Vuoto = permanente.

**Togliere un IP ban:** `/unbanip <player|ip>` oppure `/unban <player>` (toglie anche l’IP ban di quell’account) o `/pardon-ip <ip>`.

`/history <player>` — kick, ban, ipban, mute, warn (attivi e chiusi). Limite: `modules/moderation.yml` → `history.max-listed`.

`/dupeip <player|ip>` — account sullo stesso IP, online/ban/mute/warn. Testi in `moderation.dupeip*`.

Schermata ban a tempo: dialog nativo col countdown (`screen.live-countdown`). Layout: `lang/messages.yml` → `moderation.ban-dialog-*`.

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
`/limity` mostra range e comando del mondo in cui sei.
`/limity <min> <max> [mondo]` imposta il range.
`/limity command [mondo] <comando>` imposta **il comando** eseguito appena si esce dal range (uno per mondo). Esempi:
- `/limity command [player] spawn`
- `/limity command world_nether [console] warp netherhub {player}`
`/limity clearcommand [mondo]` toglie il comando.

`teleport-spawn` viene spento quando setti un comando, così non conflitto col comando.

### Item a terra
Hologram piccolo (`font` + `scale`). `show-lore: false` nasconde la lore dell'item. Countdown despawn in `despawn-format` (`despawn-seconds: 300` = 5 minuti).

### Morti
Messaggi in `mechanics.yml` → `deaths.messages.<CAUSE>`.
`/checkdeath [player]` — coords (click = copia), causa, killer, GUI inventario al momento della morte.
`/checkdeath restore [player]` — ripristina quell'inventario sul player (online). Permesso `valary.checkdeath.restore`.

### Coordinate
`/copycoord [player]` — click sul messaggio = Ctrl+C (`world x y z`).

### Pressure plate long-jump
Piastre per materiale in `long-jump.plates`.
- Step sulla plate → jump
- **Non** salta mirando. `/pressureplayerjump` serve solo a vedere/associare il blocco che miri
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
