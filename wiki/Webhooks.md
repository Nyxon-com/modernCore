# Wiki webhook Discord

File sul server: `plugins/ValaryRPG-Core/modules/webhooks.yml`.

Due canali, entrambi **async** (un thread `valary-webhook`, niente HTTP sul main):

| Canale | Cosa arriva | Cosa non arriva |
|---|---|---|
| `logs` | azioni staff, filtro chat, clearchat, reload, chat se abilitata | warning, info di routine |
| `errors` | solo log **SEVERE** della console | warning, OOM / heap / GC / memory |

`url` è il fallback. Puoi mettere URL diversi in `logs.url` e `errors.url`. URL vuoto = canale spento.

`logs.categories.chat: false` di default: la chat intera su Discord è rumorosa.

`errors.same-message-cooldown-millis` evita di ripostare lo stesso SEVERE in loop.

Disattiva tutto con `config.yml` → `modules.webhooks: false` oppure `enabled: false` nel YAML.
