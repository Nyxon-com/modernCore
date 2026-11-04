# Hologram wiki (`/vholo`)

**Text-only** holograms (TextDisplay). One YAML file per hologram, FancyHolograms style.

These are not the clickable triggers. Those live in `modules/mechanics.yml` and are managed with `/triggerblock`. See [Triggerblocks](Triggerblocks.md).

---

## Where the files live

Always edit the server folder, not `src/main/resources/` in the Git repository.

| Path on the server | Role |
|---|---|
| `plugins/modernCore/modules/holograms.yml` | Module settings |
| `plugins/modernCore/holo/<name>.yml` | One hologram |
| `plugins/modernCore/config.yml` | `modules.holograms`, command, permission |
| `plugins/modernCore/lang/messages.yml` | `holograms.*` text |

The file name is the id: `holo/spawn.yml` → hologram `spawn`. Only `a-z`, `0-9`, `_`, `-`. Capitals and odd characters are stripped.

`holo/holograms.yml` is not a hologram. It is the FancyHolograms dump to import.

On first start, if `holo/` is empty, `example.yml` is copied with `enabled: false`.

---

## Reload

| What you changed | Command |
|---|---|
| Files in `holo/*.yml` | `/vholo reload` **or** `/valary reload` |
| `modules/holograms.yml`, messages, `config.yml` | `/valary reload` |
| The `/vholo` command name or its permission | **Restart** the server |

`/vholo reload` rereads `holo/` and respawns the entities. It does not touch triggerblocks.

Editing the YAML inside the Cursor project and reloading in game shows nothing. The plugin only reads `plugins/modernCore/`.

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

| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | `false` turns the module off (nothing spawns) |
| `folder` | `holo` | Subfolder under `plugins/modernCore/` |
| `max` | `200` | Cap on holograms created with `/vholo create` |
| `tick-ticks` | `10` | How often personal text / `update_text_interval` refreshes |
| `near-range` | `16` | Radius of `/vholo near` |
| `default-visibility-distance` | `64` | Used when a hologram file has no `visibility_distance` |
| `unlimited-view-blocks` | `128` | Real distance when `visibility_distance: -1` |
| `max-personal-displays` | `200` | Cap on per-player copies (text that contains `%placeholder%`) |
| `line-width` | `200` | Default `line_width` for new holograms (pixels, line wrap) |

The module can also be toggled in `config.yml` → `modules.holograms`. Default permission: `permissions.holo` → `valary.holo`. Command: `commands.holo` → `vholo` (alias `vholos`).

---

## File `holo/<name>.yml`

Minimum example:

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
  - "&7Welcome"
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

### Keys

| Key | Type | Default | Notes |
|---|---|---|---|
| `enabled` | bool | `true` | `false` keeps the file and spawns nothing |
| `type` | string | `TEXT` | Fancy compatibility. Valary spawns **only** TextDisplay |
| `location.world` | string | — | Paper world name. An unloaded world does not spawn |
| `location.x/y/z` | double | — | Display center (usually `.5` on the block) |
| `location.yaw` | float | `0` | Horizontal rotation. Useful with `billboard: fixed` |
| `location.pitch` | float | `0` | Vertical rotation |
| `visibility_distance` | double | from the module (`64`) | View distance in blocks. Effective minimum **8**. `-1` uses `unlimited-view-blocks` |
| `visibility` | string | `ALL` | Fancy compatibility. **Not** filtered (no permission or team check) |
| `scale_x/y/z` | double | `1` | Scale. `/vholo scale name 7` sets all three axes to 7 |
| `translation_x/y/z` | double | `0` | Local text offset (does not move `location`) |
| `shadow_radius` | float | `0` | Ground shadow of the display (`0` = off) |
| `shadow_strength` | float | `1` | Ground-shadow strength |
| `text` | list | `[]` | Lines. MiniMessage **or** `&7` / `&#RRGGBB` / `&x&R&R&G&G&B&B` |
| `text_shadow` | bool | `true` | Letter shadow |
| `see_through` | bool | `false` | Text visible through blocks |
| `text_alignment` | string | `center` | `left` / `center` / `right` |
| `update_text_interval` | int | `-1` | Ticks between PlaceholderAPI refreshes. `-1` = do not refresh. Counted in `tick-ticks` |
| `background` | string | `transparent` | `transparent`, `#RRGGBB`, `#AARRGGBB` |
| `billboard` | string | `center` | `center` (faces the player), `fixed`, `horizontal`, `vertical` |
| `block_brightness` | int | `-1` | Block light 0–15. `-1` = world light |
| `sky_brightness` | int | `-1` | Sky light 0–15. `-1` = world light |
| `line_width` | int | from the module (`200`) | Wrap width in pixels |

### Text

Each `text` entry is one line. Lines are joined with newlines.

Formats accepted on the same hologram:

```yaml
text:
  - "<gradient:#A78BFA:#EC4899>Shop</gradient>"
  - "&7Right click"
  - "&#FFAA00Gold"
  - "<white>%player_name%"
```

If **any** line contains `%` (PlaceholderAPI / `%player_name%`), the hologram becomes **personal**: one copy per player in range, up to `max-personal-displays`. Without `%` it is a single shared entity.

PlaceholderAPI is optional. `%player_name%` works without it.

---

## `/vholo` commands

Permission: `valary.holo` (node in `config.yml` → `permissions.holo`).

| Command | Effect | Writes |
|---|---|---|
| `/vholo create <name>` | Creates it here, new file | `holo/<name>.yml` |
| `/vholo delete <name>` | Removes file and entity | deletes the YAML |
| `/vholo list` | List (marks disabled ones) | — |
| `/vholo info <name>` | World, coords, scale, billboard, lines | — |
| `/vholo near` | Holograms within `near-range` | — |
| `/vholo movehere <name>` | Moves it to where you stand | `location` |
| `/vholo tp <name>` | Teleports you to the hologram | — |
| `/vholo enable \| disable <name>` | Turns it on or off | `enabled` |
| `/vholo copy <name> <new>` | Duplicates the YAML | new file |
| `/vholo reload` | Rereads `holo/` and respawns | — |
| `/vholo import [file]` | Splits a Fancy dump | see below |
| `/vholo line add <name> <text>` | Appends a line | `text` |
| `/vholo line set <name> <i> <text>` | Replaces line `i` (**0**-based) | `text` |
| `/vholo line insert <name> <i> <text>` | Inserts at index `i` | `text` |
| `/vholo line remove <name> <i>` | Removes the line | `text` |
| `/vholo line clear <name>` | Clears the text | `text: []` |
| `/vholo scale <name> <n>` | Uniform scale on X Y Z | `scale_*` |
| `/vholo scale <name> <x> <y> [z]` | Per-axis scale | `scale_*` |
| `/vholo billboard <name> <value>` | `fixed` `center` `horizontal` `vertical` | `billboard` |
| `/vholo shadow <name> <radius> [strength]` | Ground shadow | `shadow_*` |
| `/vholo background <name> <value>` | `transparent` or hex | `background` |
| `/vholo seethrough <name> on\|off` | Sees through blocks | `see_through` |
| `/vholo textshadow <name> on\|off` | Letter shadow | `text_shadow` |
| `/vholo alignment <name> left\|center\|right` | Alignment | `text_alignment` |
| `/vholo brightness <name> <block> [sky]` | Light 0–15 | `*_brightness` |
| `/vholo distance <name> <blocks>` | View distance (`-1` = module unlimited) | `visibility_distance` |
| `/vholo updateinterval <name> <ticks>` | PlaceholderAPI refresh (`-1` = off) | `update_text_interval` |
| `/vholo yaw <name> <degrees>` | Yaw | `location.yaw` |

Commands that change the YAML **save the file and respawn immediately**. A hand edit of the file still needs `/vholo reload`.

---

## FancyHolograms import

1. Put the dump in `plugins/modernCore/holo/holograms.yml` (shape `holograms:<id>: ...`).
2. `/vholo import` — or `/vholo import relative/path.yml`.
3. Each id becomes `holo/<id>.yml`. Existing files are **not** overwritten.
4. The dump is renamed to `holograms.yml.imported`.

Fancy keys (`scale_x`, `billboard`, `background`, `&7`, …) stay as they are. Types other than text are still spawned as TextDisplay.

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

Changing the command name or the permission needs a **restart**. Reload is not enough.

---

## Checklist

- Files live in `plugins/modernCore/holo/`, not in the repository.
- `enabled: true` and the world is loaded.
- `/vholo delete` does not delete triggerblocks.
- Text with `%...%` means personal copies. Watch the cap.
- After a hand edit: `/vholo reload`.
