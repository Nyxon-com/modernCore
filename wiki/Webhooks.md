# Discord webhook wiki

Server file: `plugins/modernCore/modules/webhooks.yml`.

Two channels, both **async** (one `valary-webhook` thread, no HTTP on the main thread):

| Channel | What is sent | What is not sent |
|---|---|---|
| `logs` | staff actions, chat filter, clearchat, reload, chat if enabled | warnings, routine info |
| `errors` | console **SEVERE** logs only | warnings, OOM / heap / GC / memory |

`url` is the fallback. `logs.url` and `errors.url` can point at different endpoints. An empty URL turns that channel off.

`logs.categories.chat` defaults to `false`: the full chat log is noisy on Discord.

`errors.same-message-cooldown-millis` stops the same SEVERE line from being posted in a loop.

Turn everything off with `config.yml` → `modules.webhooks: false`, or `enabled: false` in the YAML.
