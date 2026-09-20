# Wiki chat (`/clearchat`, annunci, filtro)

Modulo `chat`. File sul server: `plugins/ValaryRPG-Core/modules/chat.yml`.

| Funzione | YAML | Permesso |
|---|---|---|
| Pulisce la chat di **tutti** i player | `clearchat.blank-lines` | `valary.clearchat` (admin) |
| Annunci che ciclano | `announcements.*` | nessuno |
| Filtro parole / regex / IP / link | `filter.*` | bypass: `valary.chat.filter.bypass` |

Testi in `lang/messages.yml` → `chat.*`. Disattiva il modulo con `config.yml` → `modules.chat: false`.

---

## Clear chat

`/clearchat` (alias `/cc`) invia N righe vuote a ogni player online e un messaggio di avviso. Solo staff.

## Annunci

`interval-seconds` è il ritardo tra un annuncio e il successivo. `random: false` li gira in ordine. MiniMessage, `{prefix}` dal `config.yml`.

Il primo annuncio parte dopo il primo intervallo, non all'avvio.

## Filtro

Dopo il mute. `private-messages: true` applica le stesse regole a `/msg` e `/r`.

`notify-staff: true` avvisa chi ha `valary.chat.notify`.

Aggiungi parole in `filter.words` e regex Java in `filter.regex`. Link e IPv4 si accendono con `block-links` / `block-ips`.
