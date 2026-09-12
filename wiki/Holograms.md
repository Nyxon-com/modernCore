# Wiki hologram (`/vholo`)

Hologram **solo testo** (TextDisplay). Un file YAML per hologram, stile FancyHolograms.

Non sono i trigger cliccabili. Quelli stanno in `modules/mechanics.yml` e si gestiscono con `/triggerblock`. Vedi [Triggerblocks](Triggerblocks.md).

---

## Dove stanno i file

Edita **sempre** la cartella del server, non `src/main/resources/` del repo Git.

| Path sul server | Ruolo |
|---|---|
| `plugins/ValaryRPG-Core/modules/holograms.yml` | Impostazioni globali del modulo |
| `plugins/ValaryRPG-Core/holo/<nome>.yml` | Un hologram |
| `plugins/ValaryRPG-Core/config.yml` | `modules.holograms`, comando, permesso |
| `plugins/ValaryRPG-Core/lang/messages.yml` | Testi `holograms.*` |

Il nome file è l’id: `holo/spawn.yml` → hologram `spawn`. Solo `a-z`, `0-9`, `_`, `-`. Maiuscole e caratteri strani vengono normalizzati (tolti).

`holo/holograms.yml` non è un hologram: è il dump FancyHolograms da importare.

Al primo avvio, se `holo/` è vuota, viene copiato `example.yml` con `enabled: false`.

---

## Reload

| Cosa hai cambiato | Comando |
|---|---|
| File in `holo/*.yml` | `/vholo reload` **oppure** `/valary reload` |
| `modules/holograms.yml`, messaggi, `config.yml` | `/valary reload` |
| Nome comando `/vholo` o permesso | **Riavvio** del server |

`/vholo reload` rilegge `holo/` e respawna le entity. Non tocca i triggerblock.

Se modifichi i YAML nel progetto Cursor e reloaddi in game, **non** vedi nulla: il plugin legge solo `plugins/ValaryRPG-Core/`.

---

## `modules/holograms.yml`

```yaml
enabled: true
folder: holo
max: 200
tick-ticks: 10
near-range: 16
default-visibility-distance: 64
unlimited-view-blocks: 128
max-personal-displays: 200
line-width: 200
```

| Chiave | Default | Effetto |
|---|---|---|
| `enabled` | `true` | `false` = modulo spento (nessuno spawn) |
| `folder` | `holo` | Sottocartella sotto `plugins/ValaryRPG-Core/` |
| `max` | `200` | Cap hologram creati con `/vholo create` |
| `tick-ticks` | `10` | Ogni quanti tick aggiorna testo personale / `update_text_interval` |
| `near-range` | `16` | Raggio di `/vholo near` |
| `default-visibility-distance` | `64` | Usato se il file hologram non ha `visibility_distance` |
| `unlimited-view-blocks` | `128` | Distanza reale se `visibility_distance: -1` |
| `max-personal-displays` | `200` | Cap copie per-player (testo con `%placeholder%`) |
| `line-width` | `200` | Default `line_width` dei nuovi hologram (pixel, wrap riga) |

Modulo on/off anche in `config.yml` → `modules.holograms`. Permesso default: `permissions.holo` → `valary.holo`. Comando: `commands.holo` → `vholo` (alias `vholos`).

---

## File `holo/<nome>.yml`

Esempio minimo:

```yaml
enabled: true
type: TEXT
location:
  world: world
  x: 100.5
  y: 65.0
  z: -20.5
  yaw: 0.0
  pitch: 0.0
visibility_distance: 64
visibility: ALL
scale_x: 1.0
scale_y: 1.0
scale_z: 1.0
translation_x: 0.0
translation_y: 0.0
translation_z: 0.0
shadow_radius: 0.0
shadow_strength: 1.0
text:
  - "<gradient:#A78BFA:#EC4899><bold>Spawn</bold></gradient>"
  - "&7Benvenuto"
text_shadow: true
see_through: false
text_alignment: center
update_text_interval: -1
background: transparent
billboard: center
block_brightness: -1
sky_brightness: -1
line_width: 200
```

### Chiavi

| Chiave | Tipo | Default | Note |
|---|---|---|---|
| `enabled` | bool | `true` | `false` = file tenuto, niente entity |
| `type` | string | `TEXT` | Compat Fancy. Valary spawna **solo** TextDisplay |
| `location.world` | string | — | Nome mondo Paper. Mondo scaricato = niente spawn |
| `location.x/y/z` | double | — | Centro del display (di solito `.5` sul blocco) |
| `location.yaw` | float | `0` | Rotazione orizzontale. Utile con `billboard: fixed` |
| `location.pitch` | float | `0` | Rotazione verticale |
| `visibility_distance` | double | da modulo (`64`) | Blocchi di vista. Minimo effettivo **8**. `-1` usa `unlimited-view-blocks` |
| `visibility` | string | `ALL` | Compat Fancy. **Non** filtrato (niente permission/team) |
| `scale_x/y/z` | double | `1` | Scala. `/vholo scale nome 7` imposta i tre assi a 7 |
| `translation_x/y/z` | double | `0` | Offset locale del testo (non sposta `location`) |
| `shadow_radius` | float | `0` | Ombra a terra del display (`0` = spenta) |
| `shadow_strength` | float | `1` | Intensità ombra a terra |
| `text` | list | `[]` | Righe. MiniMessage **o** `&7` / `&#RRGGBB` / `&x&R&R&G&G&B&B` |
| `text_shadow` | bool | `true` | Ombra delle lettere |
| `see_through` | bool | `false` | Testo visibile dietro i blocchi |
| `text_alignment` | string | `center` | `left` / `center` / `right` |
| `update_text_interval` | int | `-1` | Tick tra refresh PAPI. `-1` = non rinfrescare. Contato su `tick-ticks` |
| `background` | string | `transparent` | `transparent`, `#RRGGBB`, `#AARRGGBB` |
| `billboard` | string | `center` | `center` (guarda il player), `fixed`, `horizontal`, `vertical` |
| `block_brightness` | int | `-1` | Luce blocco 0–15. `-1` = luce del mondo |
| `sky_brightness` | int | `-1` | Luce cielo 0–15. `-1` = luce del mondo |
| `line_width` | int | da modulo (`200`) | Larghezza wrap in pixel |

### Testo

Ogni elemento di `text` è una riga. Si uniscono con newline.

Formati accettati nella stessa riga:

```yaml
text:
  - "<gradient:#A78BFA:#EC4899>Shop</gradient>"
  - "&7Click destro"
  - "&#FFAA00Oro"
  - "<white>%player_name%"
```

Se **qualsiasi** riga contiene `%` (PlaceholderAPI / `%player_name%`), l’hologram diventa **personale**: una copia per ogni player nel raggio, fino a `max-personal-displays`. Senza `%` è un’unica entity condivisa.

PlaceholderAPI è opzionale. `%player_name%` funziona anche senza.

---

## Comandi `/vholo`

Permesso: `valary.holo` (nodo in `config.yml` → `permissions.holo`).

| Comando | Effetto | Scrive |
|---|---|---|
| `/vholo create <nome>` | Crea qui, file nuovo | `holo/<nome>.yml` |
| `/vholo delete <nome>` | Toglie file + entity | elimina il YAML |
| `/vholo list` | Elenco (off se disabilitato) | — |
| `/vholo info <nome>` | Mondo, coords, scale, billboard, righe | — |
| `/vholo near` | Hologram entro `near-range` | — |
| `/vholo movehere <nome>` | Sposta dove sei | `location` |
| `/vholo tp <nome>` | Ti porta sull’hologram | — |
| `/vholo enable \| disable <nome>` | Accende / spegne | `enabled` |
| `/vholo copy <nome> <nuovo>` | Duplica il YAML | nuovo file |
| `/vholo reload` | Rilegge `holo/` e respawna | — |
| `/vholo import [file]` | Spezza un dump Fancy | vedi sotto |
| `/vholo line add <nome> <testo>` | Aggiunge riga in fondo | `text` |
| `/vholo line set <nome> <i> <testo>` | Sostituisce riga (`i` parte da **0**) | `text` |
| `/vholo line insert <nome> <i> <testo>` | Inserisce alla posizione `i` | `text` |
| `/vholo line remove <nome> <i>` | Toglie la riga | `text` |
| `/vholo line clear <nome>` | Svuota il testo | `text: []` |
| `/vholo scale <nome> <n>` | Scala uguale su X Y Z | `scale_*` |
| `/vholo scale <nome> <x> <y> [z]` | Scala per asse | `scale_*` |
| `/vholo billboard <nome> <valore>` | `fixed` `center` `horizontal` `vertical` | `billboard` |
| `/vholo shadow <nome> <raggio> [forza]` | Ombra a terra | `shadow_*` |
| `/vholo background <nome> <valore>` | `transparent` o hex | `background` |
| `/vholo seethrough <nome> on\|off` | Attraversa i blocchi | `see_through` |
| `/vholo textshadow <nome> on\|off` | Ombra lettere | `text_shadow` |
| `/vholo alignment <nome> left\|center\|right` | Allineamento | `text_alignment` |
| `/vholo brightness <nome> <block> [sky]` | Luce 0–15 | `*_brightness` |
| `/vholo distance <nome> <blocchi>` | Vista (`-1` = unlimited del modulo) | `visibility_distance` |
| `/vholo updateinterval <nome> <tick>` | Refresh PAPI (`-1` = no) | `update_text_interval` |
| `/vholo yaw <nome> <gradi>` | Yaw | `location.yaw` |

I comandi che cambiano il YAML **salvano il file e respawnano** subito. Per edit a mano del file serve `/vholo reload`.

---

## Import FancyHolograms

1. Metti il dump in `plugins/ValaryRPG-Core/holo/holograms.yml` (struttura `holograms:<id>: ...`).
2. `/vholo import` — oppure `/vholo import percorso/relativo.yml`.
3. Ogni id diventa `holo/<id>.yml`. File già esistenti **non** vengono sovrascritti.
4. Il dump viene rinominato in `holograms.yml.imported`.

Le chiavi Fancy (`scale_x`, `billboard`, `background`, `&7`, …) restano così come sono. `type` diversi da testo vengono comunque spawnati come TextDisplay.

---

## `config.yml`

```yaml
modules:
  holograms: true

commands:
  holo: vholo
  holo-aliases:
    - vholos

permissions:
  holo: valary.holo
```

Cambio nome comando o permesso: **restart**, non basta il reload.

---

## Checklist

- File in `plugins/ValaryRPG-Core/holo/`, non nel repo.
- `enabled: true` e mondo caricato.
- `/vholo delete` non cancella i triggerblock.
- Testo con `%...%` = copie personali, attenzione al cap.
- Dopo edit a mano: `/vholo reload`.
