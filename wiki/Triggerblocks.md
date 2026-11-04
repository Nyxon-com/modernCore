# Triggerblock wiki (`/triggerblock`)

A hologram **on a block**: right-click (or a click on the text) runs ActionCommands. Data lives in `modules/mechanics.yml`, not in `holo/`.

`/vholo delete` does **not** remove these triggers. Remove them with `/triggerblock delete <name>`.

Text-only holograms (Fancy style): [Holograms](Holograms.md).

---

## Where the files live

Always edit `plugins/modernCore/`, not the Git repository.

| Path | Role |
|---|---|
| `plugins/modernCore/modules/mechanics.yml` | `holo-blocks.*` (globals and each trigger) |
| `plugins/modernCore/config.yml` | `/triggerblock` command, permission |
| `plugins/modernCore/lang/messages.yml` | `holos.*` text |
| `plugins/modernCore/sounds/sounds.yml` | click sound (`holo-click` by default) |

After a hand edit of `mechanics.yml`: **`/valary reload`**. `/vholo reload` does not reread triggers.

---

## Global `holo-blocks` settings

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
    - "<gray>Click to use"
```

| Key | Effect |
|---|---|
| `enabled` | `false` = no spawn and no clicks |
| `max` | Cap on triggers created in game |
| `tick-ticks` | Animation tick (bob/spin/pulse/wave) |
| `particle-every-ticks` | How often idle particles play |
| `click-cooldown-millis` | Double-click guard |
| `view-range` | Display visibility distance |
| `select-range` | Blocks for `/triggerblock set` (look radius) |
| `hitbox-width` / `hitbox-height` | Clickable interaction on the hologram |
| `default-*` | Values copied onto a new trigger |
| `wand` | Wand item (material, name, lore, glow) |
| `animations` / `particles` / `click-particles` | Named presets (add your own) |
| `holos.<id>` | One trigger |

`{name}` in `default-lines` becomes the trigger id at create time.

---

## One trigger `holo-blocks.holos.<id>`

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
          - "<gray>Click to open"
      icon:
        material: CHEST
        scale: 0.55
        offset-y: -0.45
      animation: pulse
      particle: happy_villager   # only if it exists in holo-blocks.particles
      click-particle: spark
      glow: true
      permission: ""
      sound: holo-click
      commands:
        - "[console] cp crates_boss {player}"
```

| Key | Notes |
|---|---|
| `enabled` | `false` = no entity, clicks do nothing |
| `world` `x` `y` `z` | Whole block (not `.5`) |
| `hologram.offset-y` | Text height above the block |
| `hologram.scale` | Text scale |
| `hologram.billboard` | `CENTER` `FIXED` `HORIZONTAL` `VERTICAL` |
| `hologram.see-through` | Text through blocks |
| `hologram.line-width` | Wrap in pixels |
| `hologram.background` | ARGB integer. `0` = transparent |
| `hologram.shadowed` | Letter shadow |
| `hologram.lines` | MiniMessage |
| `icon.material` | ItemDisplay. Missing section or AIR = no icon |
| `icon.scale` / `icon.offset-y` | Icon scale and offset relative to the text |
| `animation` | Name in `holo-blocks.animations`, or `none` |
| `particle` | Name in `holo-blocks.particles`, or `none` |
| `click-particle` | Name in `holo-blocks.click-particles`, or `none` |
| `glow` | Glow on the display |
| `permission` | Empty = everyone. Otherwise that node is required to click |
| `sound` | Key in `sounds/sounds.yml` |
| `commands` | ActionCommands on click |

Particles: the value must be a **YAML key** (`enchant`, `flame`, …), not the Bukkit enum (`HAPPY_VILLAGER`). For `happy_villager`, add a preset:

```yaml
holo-blocks:
  particles:
    happy_villager:
      type: HAPPY_VILLAGER
      count: 6
      offset: 0.35
      extra: 0.0
```

`type` is the Paper `Particle` enum.

### Built-in animations

| Preset | YAML |
|---|---|
| `bob` | `speed`, `height` |
| `spin` | `speed` |
| `pulse` | `speed`, `min-scale`, `max-scale` |
| `wave` | `speed`, `width`, `height` |
| `none` | still |

---

## Click commands

No tag means the command runs **as the player**.

```yaml
commands:
  - "warp pvp"
  - "[player] spawn"
  - "[console] cp crates_boss {player}"
  - "[message] <green>Opened"
  - "[sound] UI_BUTTON_CLICK"
```

Placeholders: `{player}`, `{player_name}`, `%player_name%`, `%player%`, then PlaceholderAPI. A console command for CratePlus usually wants `{player}` (the nick), not a selector.

Shift-click on the block **ignores** the trigger, so you can still place blocks or open a chest.

---

## `/triggerblock` commands

Aliases: `/tblock`, `/triggerblocks`. Permission: `valary.triggerblock`.

| Command | Effect |
|---|---|
| `/triggerblock wand` | Gives the wand |
| `/triggerblock set` | Marks the block you are looking at |
| `/triggerblock create <name>` | Creates on the marked block |
| `/triggerblock delete <name>` | Removes YAML and entities |
| `/triggerblock list` / `info` / `enable` / `disable` | Management |
| `/triggerblock command <name> ...` | Appends an ActionCommand |
| `/triggerblock clearcommands <name>` | Clears the commands |
| `/triggerblock line add <name> <text>` | Hologram line |
| `/triggerblock line clear <name>` | Clears the text |
| `/triggerblock animation <name> bob\|spin\|pulse\|wave\|none` | |
| `/triggerblock particle <name> <preset>` | Idle |
| `/triggerblock clickparticle <name> <preset>` | On click |
| `/triggerblock icon <name>` | Item in hand (empty hand removes it) |
| `/triggerblock glow <name> on\|off` | |
| `/triggerblock sound <name> <key>` | Key in `sounds.yml` |
| `/triggerblock permission <name> [node]` | Empty = everyone |
| `/triggerblock scale <name> <n>` | |
| `/triggerblock offset <name> <n>` | Y offset |

Wand: right-click a block to select it, then `create`.

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

Module: `modules.mechanics` (triggers live in mechanics, not in `modules.holograms`).
