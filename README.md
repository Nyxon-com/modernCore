# ValaryRPG Core

Plugin Core modulare per server **Paper 1.21.11**, pensato per sostituire plugin monolitici pesanti (CMI/Essentials) con un sistema leggero, data-driven e integrato con **MMOItems**, **Vault**, **CoinsEngine** e **Multiverse-Core**.

Build: **Gradle**. Ogni blocco funzionale viene committato su GitHub.

Repository: https://github.com/Nyxon-com/ValaryRPGcore

---

## Stato attuale

- Scaffold Gradle (Paper 1.21.11, Java 21, Shadow JAR)
- Config YAML con validazione, merge chiavi mancanti e reload (`/valary reload`)
- Storage configurabile: **SQLite / MySQL / MariaDB** (HikariCP)
- Hook economy **Vault** (CoinsEngine / ExcellentEconomy come provider)
- Modulo mondi: spawn per mondo (`/spawn`, `/setspawn`) e lock meteo (`/weatherlock`)
- Home: `/sethome`, `/home`, `/delhome`, `/homes` (SQL, limiti per gruppo)
- Warp: `/setwarp`, `/warp <nomewarp>`, `/warp <nomewarp> <player>`, `/delwarp`, `/warps`
- Player (stile CMI): join/quit MiniMessage, `/nick`, `/msg` `/tell` `/w` (override vanilla), `/r`, `/socialspy`, `/fly`, `/back`, `/god`, `/heal`, `/feed`
- Moderazione (override vanilla): `/kick`, `/ban [durata]`, `/banlist`, `/pardon`, `/ipban`, `/mute`, `/warn`, `/dupeip`, `/vanish`, `/vanishedit`, `/invsee`
- Utility: `/limity`, `/checkdeath`, `/copycoord`, lore sugli item a terra, long-jump su pressure plate, portali (`/portal` + item/MMOItems)
- Kit GUI (`/kit`, `/kits`) con pagine, anteprima, one-time e kit first-join + MMOItems
- Sell GUI (`/sell`) e `/sellall` con prezzi in `prices.yml` (Vault)
- Catalogo mondi in `worlds.yml` all'avvio (opzioni Multiverse-Core se presente, altrimenti default Bukkit)
- PlaceholderAPI: `%valary_nick%` `%valary_god%` `%valary_vanished%` `%valary_world%` `%valary_world_alias%` `%valary_fly%`

---

## Specifica delle features

### 1. Gestione dei Mondi e Spawn
- Spawn indipendenti per ciascun mondo
- Blocco meteo (pioggia, sole, tuoni) per mondo
- Import automatico dei mondi caricati (Bukkit / Multiverse-Core) in `worlds.yml` con pvp, difficulty, gamemode, spawn flags, hunger, flight, keep-spawn

### 2. Sistemi di Spostamento (`/home`, `/warp`, `/spawn`)
- `/sethome`, `/home`, `/delhome` con permessi granulari e limiti per gruppo
- `/warp <nomewarp>` e `/warp <nomewarp> <player>` da console o con permesso

### 3. Gestione Giocatori e Comunicazione
- Join/quit MiniMessage
- `/nick` MiniMessage + RGB
- `/msg`, `/r`, `/back`, `/god`, `/heal`, `/feed`
- Moderazione nativa: `/kick`, `/ban`, `/banlist`, `/mute`, `/warn`, `/warnings`, `/fly`, `/vanish`, `/vanishedit`, `/invsee`
- `/ipban`, `/dupeip`

### 4. Economia, Kit e Oggetti
- Kit GUI con pagine, anteprima, one-time + MMOItems + kit di benvenuto al primo join
- Sell GUI e `/sellall`, prezzi in `prices.yml`, item custom — economy via Vault/CoinsEngine

### 5. Meccaniche e utility
- `/limity`, lore dinamica item a terra, `/checkdeath`, `/copycoord`, pressure plate long-jump, portali (`/portal`) item-based

### 6. Reload e configurazione
- Reload con validazione YAML e rigenerazione sicura. Zero hardcoding.

---

## Build

Requisiti: **JDK 21+**.

```bash
./gradlew.bat build
```

Il JAR viene generato in `build/libs/ValaryRPG-Core-1.0.0.jar`.
