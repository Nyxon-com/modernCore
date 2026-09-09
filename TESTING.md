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
- [ ] `/msg`, `/r`, `/socialspy` (due account): `[SS]` anche sui tuoi msg se `spy-self: true`
- [x] Vanilla `/tell` è quello Valary
- [x] `/fly` persiste al relog se `players.yml` `fly.persist: true`
- [x] `/gamemode creative` nello spawn, `/mvtp` (o warp) in un altro mondo → resti creative
- [x] `/god` ignora danno e fame; `/heal` `/feed` `/back`

## 4. Moderazione
- [x] `/kick <player> test` schermata custom
- [ ] `/ban <player> 10s motivo` → kick istantaneo (niente freeze in-game); schermata + ticket Discord
- [ ] `/banlist`, `/pardon <player>`
- [x] `/ipban <player>` → altri account stesso IP kickati
- [x] `/unbanip <player>` o `/unban <player>` toglie anche l'IP ban
- [x] `/history <player>` mostra kick/ban/mute/warn
- [x] `/mute 5m` blocca chat e `/msg`; `/unmute`
- [ ] `/warn` + `/warnings` (i warn NON scadono subito); `/warn remove <player> [id|all]`
- [x] `/dupeip <player>`: online/offline, BAN, MUTE, warn (layout HISTORY/DUPEIP)
- [x] `/vanish` invisibile, tab, no pickup se flag; `/vanishedit pickup`
- [x] `/invsee <player>` modifica zaino/armatura

## 5. Kit
- [ ] Account nuovo: kit `starter` in mano entro ~1s
- [ ] `/kit` GUI, suoni, pagine, anteprima, chiudi
- [ ] `/kit daily` senza permesso → no; con `valary.kit.daily` → sì, cooldown
- [ ] `/kit starter <player>` da console
- [ ] Se MMOItems è presente, decommenta un item in `kits.yml` e ritira

## 6. Sell
- [ ] Senza Vault: messaggio no-economy
- [ ] Con Vault: metti pane in GUI, conferma, soldi ok
- [ ] Item senza prezzo torna in inventario
- [ ] `/sellall`
- [ ] Sezione `custom` in `prices.yml`: vendi quell'item → money + comando + xp

## 7. LimitY / drop / morti / coords
- [ ] `/limity -5 20` nel mondo in cui sei → vale **solo** quel mondo (overworld/nether senza sezione = nessun limite)
- [ ] Vai in un altro mondo: niente LimitY se non l'hai impostato lì
- [ ] `/limity remove` (mondo in cui sei) o `/limity remove <mondo>` → niente più limite (anche se il mondo non è caricato)
- [ ] `/limity command spawn {player}` → comando sul mondo corrente (`spawn` non è il nome mondo)
- [ ] `/limity notify off` silenzia il messaggio "sei fuori dal limite"
- [ ] `/limity command [player] spawn` → vola fuori range esegue spawn (come il player)
- [ ] Bypass `valary.limity.bypass` ignora
- [ ] Droppa un diamond: hologram piccolo con nome e countdown ~300s; `show-lore: true` mostra lore
- [ ] Dopo il tempo l'item sparisce
- [ ] `/checkdeath` coords cliccabili, causa, GUI inventario
- [ ] `/checkdeath restore` ripristina l'inv; `/checkdeath restore <player>` da staff
- [ ] `/copycoord` click = clipboard `world x y z`

## 8. Long jump
- [ ] Calpesta gold plate → salto in direzione sguardo
- [ ] Mira una gold plate **senza** calpestarla → **nessun** salto
- [ ] `/pressureplayerjump` mentre miri → info blocco
- [ ] `/pressureplayerjump set iron` su una stone plate? solo se miri una plate; su gold plate associa quel blocco
- [ ] `/pressureplayerjump remove`

## 9. Portali
- [ ] `/portal wand` → item in mano
- [ ] Destro blocco A, Shift+destro blocco B, Shift+sinistro (dest), `/portal create test`
- [ ] `/portal command test [message] <green>ok`
- [ ] Entra nel cubo → messaggio (+ tp se dest era settata)
- [ ] `/portal item test` con ender pearl, consuma all'ingresso
- [ ] `/portal disable test` non teletrasporta; `/portal delete test`

## 10. Reload e permessi
- [ ] `/valary reload` con GUI aperte non crasha
- [ ] Senza OP, LuckPerms: da `valary.kit` ma non `valary.ban`
- [ ] Modulo `kits: false` → `/kit` assente dopo restart (i comandi Brigadier si registrano all'enable)

## Non regressioni
- [ ] 2–3 player insieme: mute, vanish, kit GUI, drop hologram (cap 80)
- [ ] Nessun comando `/cmi` esiste
