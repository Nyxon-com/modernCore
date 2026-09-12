# Wiki triggerblock (`/triggerblock`)

Hologram **su un blocco**: click destro (o click sul testo) esegue ActionCommands. Dati in `modules/mechanics.yml`, non in `holo/`.

`/vholo delete` **non** cancella questi trigger. Per toglierli: `/triggerblock delete <nome>`.

Hologram solo testo (Fancy-style): [Holograms](Holograms.md).

---

## Dove stanno i file

Edita **sempre** `plugins/ValaryRPG-Core/`, non il repo Git.

| Path | Ruolo |
|---|---|
| `plugins/ValaryRPG-Core/modules/mechanics.yml` | `holo-blocks.*` (globali + ogni trigger) |
| `plugins/ValaryRPG-Core/config.yml` | comando `/triggerblock`, permesso |
| `plugins/ValaryRPG-Core/lang/messages.yml` | testi `holos.*` |
| `plugins/ValaryRPG-Core/sounds/sounds.yml` | suono click (`holo-click` di default) |

Dopo edit a mano di `mechanics.yml`: **`/valary reload`**. `/vholo reload` non rilegge i trigger.

---

## Impostazioni globali `holo-blocks`

```yaml
holo-blocks:
  enabled: true
  max: 80
  tick-ticks: 2
  particle-every-ticks: 8
  click-cooldown-millis: 400
  view-range: 24
  select-range: 8
  hitbox-width: 1.05
  hitbox-height: 1.8
  default-offset-y: 1.35
  default-scale: 1.0
  default-icon-scale: 0.55
  default-icon-offset-y: -0.45
  default-animation: bob
  default-particle: enchant
  default-click-particle: spark
  default-glow: false
  default-sound: holo-click
  default-lines:
    - "<gradient:#A78BFA:#EC4899><bold>{name}</bold></gradient>"
    - "<gray>Click per usare"
```

| Chiave | Effetto |
|---|---|
| `enabled` | `false` = nessuno spawn / click |
| `max` | Cap trigger creati in game |
| `tick-ticks` | Tick animazione (bob/spin/pulse/wave) |
| `particle-every-ticks` | Ogni quanti tick le particelle idle |
| `click-cooldown-millis` | Anti-doppio click |
| `view-range` | Distanza visibilità display |
| `select-range` | Blocchi per `/triggerblock set` (raggio sguardo) |
| `hitbox-width` / `hitbox-height` | Interaction cliccabile sull’hologram |
| `default-*` | Valori copiati in un trigger nuovo |
| `wand` | Item della wand (material, name, lore, glow) |
| `animations` / `particles` / `click-particles` | Preset nominati (aggiungi i tuoi) |
| `holos.<id>` | Un trigger |

`{name}` in `default-lines` diventa l’id del trigger alla create.

---

## Un trigger `holo-blocks.holos.<id>`

```yaml
holo-blocks:
  holos:
    terrestri:
      enabled: true
      world: world
      x: 10
      y: 64
      z: -4
      hologram:
        offset-y: 1.35
        scale: 1.0
        billboard: CENTER
        see-through: false
        line-width: 200
        background: 0
        shadowed: true
        lines:
          - "<gradient:#A78BFA:#EC4899><bold>Crate</bold></gradient>"
          - "<gray>Click per aprire"
      icon:
        material: CHEST
        scale: 0.55
        offset-y: -0.45
      animation: pulse
      particle: happy_villager   # solo se esiste in holo-blocks.particles
      click-particle: spark
      glow: true
      permission: ""
      sound: holo-click
      commands:
        - "[console] cp crates_boss {player}"
```

| Chiave | Note |
|---|---|
| `enabled` | `false` = niente entity, click morto |
| `world` `x` `y` `z` | Blocco intero (non `.5`) |
| `hologram.offset-y` | Altezza testo sul blocco |
| `hologram.scale` | Scala testo |
| `hologram.billboard` | `CENTER` `FIXED` `HORIZONTAL` `VERTICAL` |
| `hologram.see-through` | Testo dietro i blocchi |
| `hologram.line-width` | Wrap in pixel |
| `hologram.background` | ARGB intero. `0` = trasparente |
| `hologram.shadowed` | Ombra lettere |
| `hologram.lines` | MiniMessage |
| `icon.material` | ItemDisplay. Sezione assente / AIR = niente icona |
| `icon.scale` / `icon.offset-y` | Scala e offset icona rispetto al testo |
| `animation` | Nome in `holo-blocks.animations`, o `none` |
| `particle` | Nome in `holo-blocks.particles`, o `none` |
| `click-particle` | Nome in `holo-blocks.click-particles`, o `none` |
| `glow` | Glow sul display |
| `permission` | Vuoto = tutti. Altrimenti serve quel nodo per cliccare |
| `sound` | Chiave in `sounds/sounds.yml` |
| `commands` | ActionCommands al click |

Particelle: il valore deve essere una **chiave YAML** (`enchant`, `flame`, …), non l’enum Bukkit (`HAPPY_VILLAGER`). Per `happy_villager` aggiungi un preset:

```yaml
holo-blocks:
  particles:
    happy_villager:
      type: HAPPY_VILLAGER
      count: 6
      offset: 0.35
      extra: 0.0
```

`type` = enum Paper `Particle`.

### Animazioni built-in

| Preset | YAML |
|---|---|
| `bob` | `speed`, `height` |
| `spin` | `speed` |
| `pulse` | `speed`, `min-scale`, `max-scale` |
| `wave` | `speed`, `width`, `height` |
| `none` | fermo |

---

## Comandi al click

Senza tag = eseguito **come il player**.

```yaml
commands:
  - "warp pvp"
  - "[player] spawn"
  - "[console] cp crates_boss {player}"
  - "[message] <green>Aperto"
  - "[sound] UI_BUTTON_CLICK"
```

Placeholder: `{player}`, `{player_name}`, `%player_name%`, `%player%`, poi PlaceholderAPI. Nel comando console per CratePlus di solito `{player}` (nick), non un selettore.

Shift+click sul blocco **ignora** il trigger (puoi piazzare / aprire chest).

---

## Comandi `/triggerblock`

Alias: `/tblock`, `/triggerblocks`. Permesso: `valary.triggerblock`.

| Comando | Effetto |
|---|---|
| `/triggerblock wand` | Da la wand |
| `/triggerblock set` | Marca il blocco mirato |
| `/triggerblock create <nome>` | Crea sul blocco marcato |
| `/triggerblock delete <nome>` | Toglie YAML + entity |
| `/triggerblock list` / `info` / `enable` / `disable` | Gestione |
| `/triggerblock command <nome> ...` | Aggiunge un ActionCommand |
| `/triggerblock clearcommands <nome>` | Azzera i comandi |
| `/triggerblock line add <nome> <testo>` | Riga hologram |
| `/triggerblock line clear <nome>` | Azzera il testo |
| `/triggerblock animation <nome> bob\|spin\|pulse\|wave\|none` | |
| `/triggerblock particle <nome> <preset>` | Idle |
| `/triggerblock clickparticle <nome> <preset>` | Al click |
| `/triggerblock icon <nome>` | Item in mano (mano vuota = togli) |
| `/triggerblock glow <nome> on\|off` | |
| `/triggerblock sound <nome> <chiave>` | Chiave `sounds.yml` |
| `/triggerblock permission <nome> [nodo]` | Vuoto = tutti |
| `/triggerblock scale <nome> <n>` | |
| `/triggerblock offset <nome> <n>` | Offset Y |

Wand: destro sul blocco = seleziona, poi `create`.

---

## `config.yml`

```yaml
commands:
  triggerblock: triggerblock
  triggerblock-aliases:
    - tblock
    - triggerblocks

permissions:
  triggerblock: valary.triggerblock
```

Modulo: `modules.mechanics` (i trigger stanno nel modulo mechanics, non in `modules.holograms`).
