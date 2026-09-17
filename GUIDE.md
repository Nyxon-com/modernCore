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

Il reload legge e valida prima tutti i YAML: non esegue migrazioni né riscrive i file. Se anche un solo YAML è invalido, mantiene la configurazione precedente e mostra il path errato in console. Coordinate, testo e stile di hologram/triggerblock vengono respawnati dai nuovi valori. Nomi/alias dei comandi e connessione `storage.yml` richiedono ancora il riavvio.

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
| `modules/mechanics.yml` | LimitY, hologram drop, morti, long-jump, portali, triggerblock |
| `modules/holograms.yml` | Impostazioni `/vholo` |
| `holo/<nome>.yml` | Un hologram testo (Fancy-style) |
| `modules/kits.yml` | GUI kit + contenuto kit |
| `modules/prices.yml` | GUI sell, prezzi vanilla/MMOItems, reward custom |

Permessi: cambia il nodo in `config.yml` → `permissions.*`, non nel Java. Default in `plugin.yml`.

MiniMessage: `<red>`, `<gradient:#8B5CF6:#EC4899>`, `&#FF00AA`, `<lang:item.minecraft.diamond>`.

---

## Mondi e spawn

- `/setspawn` — imposta **lo** spawn (un solo punto, nel mondo in cui sei)
- `/spawn` — ti porta lì da qualsiasi mondo. `/spawn <player>` con `valary.spawn.others`
- A ogni login il player viene portato allo spawn globale (`modules/worlds.yml` → `settings.spawn-on-join`).
- `/weatherlock <clear|rain|thunder|unlock> [mondo]` — meteo mondo + client (serve anche negli spawn void)
- `/timelock <day|noon|night|midnight|unlock> [mondo]` — ferma il ciclo; `/time add` viene riportato indietro
- `/falldamage <true|false> [mondo]` — danno da caduta per mondo (`/falldamage false spawn`). Resta in `worlds.yml` (`fall-damage`)

Se **Multiverse-Core** è presente, pvp, difficulty, gamemode, hunger e flight restano quelli di MV: non serve rifarli in `worlds.yml`. Valary usa `/setspawn`, weatherlock, timelock e `/falldamage`. Senza MV, il catalogo in `worlds.yml` viene applicato come prima.

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
- `/fly` persiste al relog e dopo la morte. `/gamemode` persiste **tra i mondi** e dopo respawn (anche Multiverse), tranne se `worlds.<mondo>.force-gamemode: true`
- `/god` `/heal` `/feed` `/back`

---

## Moderazione (override vanilla)

`/kick` `/ban [durata] [motivo]` `/banlist` `/pardon` `/pardon-ip` `/unban` `/unbanip` `/ipban` `/mute` `/unmute` `/warn` `/warnings` `/history` `/dupeip` `/vanish` `/vanishedit` `/invsee`

Durate: `10s` `5m` `2h` `7d` `2w` `1mo` `1y` `perm`. Vuoto = permanente.

**Togliere un IP ban:** `/unbanip <player|ip>` oppure `/unban <player> <motivo>` (toglie anche l’IP ban) o `/pardon-ip <ip> <motivo>`.

`/history <player>` — kick, ban, ipban, mute, warn. **REVOCATO** (pardon) ≠ **SCADUTO**. `/pardon <player> <motivo>` obbligatorio. `/pardon list` elenco pardon.

`/dupeip <player|ip>` — account sullo stesso IP, online/ban/mute/warn. Testi in `moderation.dupeip*`.

Schermata ban a tempo: dialog nativo col countdown (`screen.live-countdown`). Layout: `lang/messages.yml` → `moderation.ban-dialog-*`.

`/vanishedit nightvision|pickup`

---

## Kit

`/kit` `/kits` apre la GUI. **Sinistro** ritira, **destro** anteprima del contenuto (niente bottone libro a parte). Dall’anteprima: **Indietro** torna alla pagina, **Ritira** prende il kit. `/kit <id> [player]` da comando/console.

- Cooldown e one-time su SQL (`kit_claims`), cache in RAM (niente SQL in chat/click)
- In GUI: disponibile (glow) / cooldown leggibile / bloccato / già ritirato + elenco item
- `settings.first-join-kit: starter` — dato al **primo join** (`hasPlayedBefore() == false`)
- Click in `kits.yml` (`claim-clicks` / `preview-clicks`). Suoni in `sounds.yml` (`kits-claim`, `kits-preview`, `kits-deny`)
- Item MMOItems: `mmoitem-type` + `mmoitem-id`
- Staff `/kit <id> <player>` ignora permesso e cooldown del target
- Comandi del kit al claim: `[console]` `[player]` `[sound]` `[message]`

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
`/limity <min> <max>` imposta il range **solo** in quel mondo (in game ignora un eventuale terzo argomento). Da console: `/limity <min> <max> <mondo>`.
`/limity command <comando>` imposta il comando sul mondo in cui sei. Esempi:
- `/limity command [player] spawn`
- `/limity command [console] warp netherhub {player}`
`/limity clearcommand [mondo]` toglie il comando. Mondi senza sezione in `mechanics.yml` = nessun limite.

`teleport-spawn` viene spento quando setti un comando, così non conflitto col comando.

### Item a terra
Hologram piccolo (`font` + `scale`). `show-lore: false` nasconde la lore dell'item. Countdown despawn in `despawn-format` (`despawn-seconds: 300` = 5 minuti).

### Morti
Messaggi in `mechanics.yml` → `deaths.messages.<CAUSE>`.
`/checkdeath [player]` — coords (click = copia), causa, killer, GUI inventario al momento della morte.
`/checkdeath restore [player]` — ripristina quell'inventario sul player (online). Permesso `valary.checkdeath.restore`.

### Coordinate
`/copycoord [player]` — click sul messaggio = Ctrl+C del **blocco mirato** (`world x y z`). Raggio in `mechanics.yml` → `copycoord.range`.

### Pressure plate long-jump
Piastre per materiale in `long-jump.plates`.
- Step sulla plate → jump
- **Non** salta mirando. `/pressureplayerjump` serve solo a vedere/associare il blocco che miri
- `/pressureplayerjump set <gold|iron>` associa **quel** blocco al preset
- `/pressureplayerjump remove` toglie l'associazione

### Portali
1. `/portal wand` — prendi la wand
2. Destro su un blocco = pos1, Shift+destro = pos2 (i **blocchi** sono il cubo, anche camminandoci sopra)
3. Shift+sinistro = dest (la tua posizione + sguardo), oppure `/portal dest`
4. `/portal create <nome>`
5. `/portal command <nome> [player] spawn` — oppure `[message] <green>ok</green>`, `[console] say {player}`
6. `/portal item <nome>` con l'oggetto in mano (mano vuota = niente item). MMOItems riconosciuto
7. `/portal enable|disable|delete|list`  `/portal clearcommands <nome>`

Senza dest il portale esegue solo i comandi. I comandi partono **dopo** il tick di movimento, altrimenti Paper li ignora. `portal-settings.y-padding` in `mechanics.yml` (default 1) fa sì che resti attivo anche in piedi sul pavimento selezionato.

### Hologram (`/vholo`) e triggerblock

Due sistemi diversi:

| | Hologram | Triggerblock |
|---|---|---|
| Comando | `/vholo` | `/triggerblock` (`/tblock`) |
| File | `holo/<nome>.yml` | `modules/mechanics.yml` → `holo-blocks.holos` |
| Reload | `/vholo reload` o `/valary reload` | solo `/valary reload` |
| Click | no, solo testo | sì, esegue ActionCommands |

Wiki completa delle chiavi YAML: [wiki/Holograms.md](wiki/Holograms.md) e [wiki/Triggerblocks.md](wiki/Triggerblocks.md).

Edita sempre `plugins/ValaryRPG-Core/`, non i YAML del repo. `/vholo delete` non cancella i triggerblock.

---

## ActionCommands (GUI, kit, sell, LimitY, portali, triggerblock)

```
- "[close]"
- "[console] say {player} ..."
- "[player] spawn"
- "[sound] UI_BUTTON_CLICK"
- "[message] <green>Fatto"
- "[msg] <gray>alias di message"
- "eco give {player} 10"   # senza tag = console (nei triggerblock / portali senza tag = player)
```

Placeholder: `{player}` `{world}` `{x}` `{y}` `{z}` e quelli extra del contesto (`{portal}` `{holo}` `{amount}` `{min}` `{max}`). `{player}` / `%player_name%` nei trigger.

---

## Permessi rapidi

Gruppo default: `valary.spawn`, `valary.home*`, `valary.warp`, `valary.msg`, `valary.nick`, `valary.kit`, `valary.sell`, `valary.checkdeath`, `valary.copycoord`, `valary.back`

Staff: `valary.admin` (tutti i figli) oppure i singoli `valary.kick`, `valary.ban`, `valary.vanish`, `valary.invsee`, `valary.portal`, `valary.holo`, `valary.triggerblock`, `valary.limity`, `valary.time.lock`, `valary.pressureplayerjump`, ...

---

## Storage

Default SQLite `plugins/ValaryRPG-Core/data.db`. Per MySQL/MariaDB imposta `storage.type` e le credenziali, poi **riavvia**.

Tabelle (prefisso `valary_`): `homes`, `players` (nick, fly, vanish, god, ultima morte, IP), `punishments`, `kit_claims`.
