# Chat wiki (`/clearchat`, announcements, filter)

Module `chat`. Server file: `plugins/modernCore/modules/chat.yml`.

| Feature | YAML | Permission |
|---|---|---|
| Clears chat for **every** online player | `clearchat.blank-lines` | `valary.clearchat` (admin) |
| Rotating announcements | `announcements.*` | none |
| Word / regex / IP / link filter | `filter.*` | bypass: `valary.chat.filter.bypass` |

Text lives in `lang/messages.yml` → `chat.*`. Turn the module off with `config.yml` → `modules.chat: false`.

---

## Clear chat

`/clearchat` (alias `/cc`) sends N blank lines to every online player, then a notice. Staff only.

## Announcements

`interval-seconds` is the delay between one announcement and the next. `random: false` plays them in order. MiniMessage, `{prefix}` from `config.yml`.

The first announcement waits for the first interval. It does not fire on startup.

## Filter

Runs after mute. `private-messages: true` applies the same rules to `/msg` and `/r`.

`notify-staff: true` tells players who have `valary.chat.notify`.

Add words under `filter.words` and Java regex under `filter.regex`. Links and IPv4 turn on with `block-links` / `block-ips`.
