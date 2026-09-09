# Piano di test — cose nuove

Paper 1.21.11, account OP. Niente `/cmi`. Segna ogni riga. Il piano vecchio resta in `TESTING.md`.

JAR: `build/libs/ValaryRPG-Core-1.0.0.jar` dopo `builda`. Se hai YAML vecchi in `plugins/ValaryRPG-Core/`, `/valary reload` aggiunge le chiavi nuove ma **non** toglie lo slot libro Anteprima in `kits.yml`: cancellalo a mano se c’è ancora.

---

## 1. Kit (GUI nuova)

Due account: uno **nuovo** (mai entrato) e il tuo OP.

- [ ] Account **nuovo**: entro ~1s in mano pane + spada starter (`first-join-delay-ticks: 20`)
- [ ] `/kit` apre la GUI. **Niente** bottone libro “Anteprima”
- [ ] **Sinistro** su Starter → ritira, GUI si chiude, suono claim
- [ ] Subito dopo: icona Starter **senza** glow, lore cooldown tipo `23h 12m` (non solo secondi)
- [ ] **Destro** su Starter → chest anteprima col contenuto (pane/spada nominati)
- [ ] In anteprima: **Indietro** torna alla **stessa** pagina GUI; **Ritira** dà il kit (o cooldown)
- [ ] Daily **senza** `valary.kit.daily`: icona grigia (`GRAY_DYE`), sinistro rifiuta, destro mostra comunque l’anteprima
- [ ] LuckPerms / OP: dai `valary.kit.daily` → Daily glow, ritiro ok, poi cooldown
- [ ] Pagine: Avanti / Indietro / Chiudi
- [ ] Console: `/kit starter <nick>` mentre è online → gli arriva il kit (ignora cooldown/permesso)
- [ ] Zaino pieno → ritiro: avviso e item a terra (`drop-overflow`)
- [ ] Se **MMOItems** è sul server: decommenta `mmoitem-type` / `mmoitem-id` in `kits.yml` (icona o item starter) e ritira

---

## 2. Portali (cubo + comandi)

Cammina **sopra o dentro** i blocchi cliccati, non serve il centro esatto del blocco.

- [ ] `/portal wand` → blaze rod in mano
- [ ] Destro blocco A = pos1, Shift+destro blocco B = pos2, Shift+sinistro = dest (dove sei **tu**)
- [ ] `/portal create test`
- [ ] `/portal command test [message] <green>ok`
- [ ] Esci dal cubo e rientra a piedi → messaggio verde (niente “comando incompleto”)
- [ ] `/portal command test [player] spawn` → rientrando esegue spawn **come te**
- [ ] `/portal command test [console] say {player} portale` → say in console/chat
- [ ] Con dest settata: ingresso → tp lì, niente danno da caduta
- [ ] `/portal item test` con ender pearl in mano → senza perla non entra; con perla la consuma
- [ ] Mano vuota `/portal item test` → toglie il requisito item
- [ ] `/portal disable test` → cubo morto; `/portal enable test` lo riaccende
- [ ] `/portal list` mostra `test`; `/portal delete test` lo toglie
- [ ] Se in `mechanics.yml` c’è ancora `portals.example` con perla: è `enabled: false`, non deve attivarsi

---

## 3. Sell

- [ ] **Senza** Vault: `/sell` → `sell.no-economy`
- [ ] **Con** Vault + CoinsEngine (o altro provider): `/sell`, metti **pane**, Conferma → soldi (`worth.BREAD: 0.5`)
- [ ] In GUI metti un item **senza** prezzo (es. dirt) + pane → Conferma: dirt torna in inventario (o a terra se zaino pieno), pane si vende
- [ ] `/sellall` vende solo lo zaino con prezzo, lascia il resto
- [ ] Metti **pepite d’oro** in `/sell` → money 1.0 + 2 xp + messaggio `Venduti {amount} pepite` (`custom.gold_nugget`)

---

## 4. Rimasugli dal piano vecchio

- [ ] `/falldamage false spawn` → in mondo `spawn` niente danno da caduta; `/falldamage true spawn` lo riaccende
- [ ] `/ban <player> 10s motivo` → kick **subito** (niente freeze), schermata custom, ticket Discord se configurato
- [ ] `/msg <nick>` senza testo → uso custom, **niente** `Unknown or incomplete command` / `<--[HERE]`
- [ ] `/pardon <player>` senza motivo → usage; `/pardon <player> prova` + `/pardon list`
- [ ] `/history <player>`: **REVOCATO** (pardon) ≠ **SCADUTO**; il count = record veri (no doppio per IP)
- [ ] Senza OP, LuckPerms: `valary.kit` sì, `valary.ban` no
- [ ] `modules.kits: false` in `config.yml` → **restart** → `/kit` assente
- [ ] 2–3 player insieme: mute, vanish, kit GUI, drop hologram (cap 80)

---

## Note rapide

| Cosa | Dove |
|---|---|
| Click kit | `modules/kits.yml` → `claim-clicks` / `preview-clicks` |
| Suoni kit/portale | `sounds.yml` → `kits-claim`, `kits-preview`, `kits-deny`, `portals-enter` |
| Cubo portale | `mechanics.yml` → `portal-settings.y-padding: 1` |
| Pepita sell | `modules/prices.yml` → `custom.gold_nugget` |
| Comandi portale/kit | `[player]` `[console]` `[message]` `[msg]` `[sound]` |
