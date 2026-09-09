# Piano di test ValaryRPG-Core

Esegui in ordine su un Paper 1.21.11 di prova (OP). Segna ogni riga. Nessun comando `/cmi`.

## 0. Avvio
- [x] Il plugin si abilita, storage SQLITE ok
- [x] Cartella `plugins/ValaryRPG-Core/` con tutti i YAML
- [x] Cancella una chiave da `lang/messages.yml`, `/valary reload` → la chiave torna, i tuoi testi restano
- [x] Rinomina `config.yml` in modo da corromperlo, reload/restart → backup `*.broken.*.yml` + file nuovo

## 1. Mondi
- [x] `/setspawn` nel overworld, vai in un altro mondo, `/spawn` ti riporta al punto del `/setspawn` (non allo spawn del mondo in cui sei)
- [x] `/spawn <altro>` con permesso
- [x] `/weatherlock rain` → resta pioggia; `/weatherlock unlock`
- [x] `/timelock noon` → ora ferma; `/time add 1000` non deve avanzare; `/timelock unlock`
- [ ] `/falldamage false spawn` → in spawn niente danno da caduta; `/falldamage true spawn` lo riaccende
- [x] Con Multiverse: un mondo extra compare in `worlds.yml` senza sovrascrivere alias già scritti

## 2. Home / warp
- [x] `/sethome`, `/home`, `/delhome`, `/homes`
- [x] Secondo home senza VIP → limite
- [x] Permesso `valary.home.limit.vip` → 3 home
- [x] `/setwarp prova`, `/warp prova`, `/warp prova <player>` da console
- [x] Nome warp invalido (`prova!`) rifiutato

## 3. Player
- [x] Join/quit MiniMessage; primo join usa `first-join`
- [x] `/nick <gradient:#ff0:#f0f>Test` senza `valary.nick.color` → niente colori; con permesso → ok
- [x] `/nick off` reset
- [x] Nick in **chat**
- [x] Nick in **tab** (TAB plugin: deve comparire il nick, non lo username)
- [x] `/msg`, `/r`, `/socialspy` (due account): `[SS]` anche sui tuoi msg se `spy-self: true`
- [x] Vanilla `/tell` è quello Valary
- [x] `/fly` persiste al relog se `players.yml` `fly.persist: true`
- [x] `/fly` + `/kill` (o morte) → dopo respawn voli ancora
- [x] `/gamemode creative` nello spawn, `/mvtp` (o warp) in un altro mondo → resti creative
- [x] `/gamemode creative` + `/kill` → dopo respawn resti creative
- [x] `/god` ignora danno e fame; `/heal` `/feed` `/back`

## 4. Moderazione
- [x] `/kick <player> test` schermata custom
- [ ] `/ban <player> 10s motivo` → kick istantaneo (niente freeze in-game); schermata + ticket Discord
- [x] `/banlist`, `/pardon <player> <motivo>` (motivo obbligatorio); `/pardon list`
- [x] `/ipban <player>` → altri account stesso IP kickati
- [x] `/unbanip <player>` o `/unban <player>` toglie anche l'IP ban
- [x] `/history <player>` mostra kick/ban/mute/warn; REVOCATO ≠ SCADUTO; count = record veri
- [x] `/mute 5m` blocca chat e `/msg`; `/unmute`
- [x] `/warn` + `/warnings` (i warn NON scadono subito); `/warn remove <player> [id|all]`
- [x] `/dupeip <player>`: online/offline, BAN, MUTE, warn (layout HISTORY/DUPEIP)
- [x] `/vanish` invisibile, tab, no pickup se flag; `/vanishedit pickup`
- [x] `/invsee <player>` modifica zaino/armatura

## 5. Kit
- [x] Account nuovo: kit `starter` in mano entro ~1s
- [x] `/kit` GUI: **sinistro** ritira, **destro** anteprima (niente bottone libro), Indietro torna alla pagina, Ritira in anteprima, chiudi, pagine
- [x] In GUI: glow se disponibile, cooldown leggibile, daily grigio senza permesso, elenco item
- [x] `/kit daily` senza permesso → no; con `valary.kit.daily` → sì, cooldown
- [x] `/kit starter <player>` da console
- [ ] Se MMOItems è presente, decommenta un item in `kits.yml` e ritira

## 6. Sell
- [x] Senza Vault: messaggio no-economy
- [ ] Con Vault: metti pane in GUI, conferma, soldi ok
- [x] Item senza prezzo torna in inventario
- [x] `/sellall`
- [x] Sezione `custom` in `prices.yml`: vendi `GOLD_NUGGET` → money + comando + xp

## 7. LimitY / drop / morti / coords
- [x] `/limity -5 20` nel mondo in cui sei → vale **solo** quel mondo (overworld/nether senza sezione = nessun limite)
- [x] Vai in un altro mondo: niente LimitY se non l'hai impostato lì
- [x] `/limity remove` (mondo in cui sei) o `/limity remove <mondo>` → niente più limite (anche se il mondo non è caricato)
- [x] `/limity command spawn {player}` → comando sul mondo corrente (`spawn` non è il nome mondo)
- [x] `/limity notify off` silenzia il messaggio "sei fuori dal limite"
- [x] `/limity command [player] spawn` → vola fuori range esegue spawn (come il player)
- [x] Caduta + LimitY `/spawn` → tp allo spawn **senza** danno/morte da fall
- [x] Bypass `valary.limity.bypass` ignora
- [x] Droppa un diamond: hologram piccolo con nome e countdown ~300s; `show-lore: true` mostra lore
- [x] Dopo il tempo l'item sparisce
- [x] `/checkdeath` coords cliccabili, causa, GUI inventario
- [x] `/checkdeath restore` ripristina l'inv; `/checkdeath restore <player>` da staff
- [x] `/copycoord` click = clipboard `world x y z`

## 8. Long jump
- [x] Calpesta gold plate → salto in direzione sguardo
- [x] Mira una gold plate **senza** calpestarla → **nessun** salto
- [x] `/pressureplayerjump` mentre miri → info blocco
- [x] `/pressureplayerjump set iron` su una stone plate? solo se miri una plate; su gold plate associa quel blocco
- [x] `/pressureplayerjump remove`

## 9. Portali
- [x] `/portal wand` → item in mano
- [x] Destro blocco A, Shift+destro blocco B, Shift+sinistro (dest), `/portal create test`
- [x] `/portal command test [message] <green>ok`
- [x] Entra nel cubo → messaggio (+ tp se dest era settata)
- [x] `/portal item test` con ender pearl, consuma all'ingresso
- [x] `/portal disable test` non teletrasporta; `/portal delete test`

## 10. Reload e permessi
- [x] `/valary reload` con GUI aperte non crasha
- [ ] Senza OP, LuckPerms: da `valary.kit` ma non `valary.ban`
- [x] Modulo `kits: false` → `/kit` assente dopo restart (i comandi Brigadier si registrano all'enable)

## Non regressioni
- [ ] 2–3 player insieme: mute, vanish, kit GUI, drop hologram (cap 80)
- [x] Nessun comando `/cmi` esiste
