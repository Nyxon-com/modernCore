# ValaryRPG Core

Plugin Core modulare per **Paper 1.21.11**. Sostituisce CMI/Essentials con comandi nativi (`/kick`, `/ban`, `/vanish` — **non** `/cmi`), YAML data-driven e hook **MMOItems**, **Vault**, **CoinsEngine**, **Multiverse-Core**, **PlaceholderAPI**.

Build: **Gradle** (JDK 21). Repository: https://github.com/Nyxon-com/ValaryRPGcore

**Guida completa:** [GUIDE.md](GUIDE.md)  
**Cosa testare in game:** [TESTING.md](TESTING.md)

---

## Stato

Feature complete rispetto alla spec, con perfezionamento (step 2):

- Spawn per mondo, lock meteo **e** lock ora (`/timelock`)
- Home / warp (console: `/warp <nome> <player>`)
- Join/quit, nick MiniMessage, msg/r, fly, god, heal, feed, back
- Moderazione nativa: kick, ban, ipban, mute, warn, dupeip (stato ban/mute), vanish, invsee
- Kit GUI + first-join + MMOItems
- Sell GUI + `prices.yml` + reward custom (money/xp/comandi/item)
- LimitY con comandi YAML, hologram drop (font piccolo + despawn 5 min), morti configurabili, `/checkdeath` con inventario, `/copycoord`
- Long-jump con checker dello sguardo + `/pressureplayerjump`
- Portali: wand, click sui blocchi, comandi all'ingresso
- Reload YAML con merge chiavi e rigenerazione file corrotti
- Zero prefisso `/cmi`

---

## Build

```bash
./gradlew.bat build
```

JAR: `build/libs/ValaryRPG-Core-1.0.0.jar`
