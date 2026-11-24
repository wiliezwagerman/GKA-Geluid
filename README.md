# GKA Geluid

Android-app waarmee vrijwilligers de Biamp TesiraFORTÉ AVB van Gereformeerde Kerk Andijk bedienen:
presets, faders en mutes, zonder Biamp Canvas.

De app praat rechtstreeks met de Tesira via het **Tesira Text Protocol (TTP)**, over Telnet (poort 23) of SSH (poort 22).
Standen worden live bijgewerkt (abonnementen), dus wijzigingen via Canvas of wandpanelen zie je direct terug.

## Gebruik

- Bij de eerste start verschijnt het instellingenscherm: vul het IP-adres van de Tesira in en kies Telnet of SSH.
- Vrijwilligers zien de pagina's **Kerkzaal** en **Torenzaal**.
- Via het slotje (pincode, standaard `1234`) worden de beheerpagina's en de instellingen zichtbaar. Wijzig de pincode meteen.
- Bij wegleggen van de app vergrendelt hij automatisch weer.
- Faders kunnen alleen binnen een veilig bereik bewegen (standaard −60 tot +6 dB, en nooit buiten wat de Tesira toestaat).

### Tesira voorbereiden
- **Telnet:** staat bij nieuwere firmware soms uit. Aanzetten in Tesira-software → *Device Maintenance*.
- **SSH:** gebruiker `default`, wachtwoord leeg als er geen beveiliging in het ontwerp staat.

## Schermindeling aanpassen

Alles staat in [`app/src/main/assets/layout.json`](app/src/main/assets/layout.json). Per pagina:

```json
{
  "title": "Kerkzaal",
  "advanced": false,
  "presets": [ { "label": "Normale dienst", "preset": 1001, "confirm": false } ],
  "faders":  [ { "label": "Kansel", "tag": "Level1", "ch": 1, "min": -60, "max": 6,
                 "fader": true, "mute": true, "muteTag": "Mute1", "muteCh": 1 } ]
}
```

- `tag` = instance tag uit het Tesira-ontwerp, `ch` = kanaalnummer (vanaf 1).
- `muteTag`/`muteCh` alleen als de mute in een ander blok zit.
- `advanced: true` = alleen zichtbaar na ontgrendelen.

Na een push naar `main` bouwt GitHub automatisch een nieuwe versie.

`tools/bcv_dump.py` leest een Canvas-bestand (`.bcv`) uit, handig om tags en preset-nummers op te zoeken:

```
python3 tools/bcv_dump.py GK_Andijk_TorenZaal_V6.4.bcv
```

## Bouwen en verspreiden (Obtainium)

GitHub Actions bouwt bij elke push naar `main` een APK en publiceert die als **Release**.
Zonder keystore-secrets wordt alleen een test-build gemaakt (te downloaden onder *Actions → Artifacts*), zonder release.

### Eenmalig: ondertekensleutel aanmaken

Alle updates moeten met dezelfde sleutel ondertekend zijn. Maak hem één keer aan en bewaar hem goed (bijv. in een wachtwoordkluis):

```bash
keytool -genkeypair -v -keystore gka-geluid.jks -alias gka -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 gka-geluid.jks > gka-geluid.jks.b64
```

Zet onder *Settings → Secrets and variables → Actions* deze secrets:

| Secret | Waarde |
|---|---|
| `KEYSTORE_BASE64` | inhoud van `gka-geluid.jks.b64` |
| `KEYSTORE_PASSWORD` | wachtwoord van de keystore |
| `KEY_ALIAS` | `gka` |
| `KEY_PASSWORD` | wachtwoord van de sleutel |

### Installeren op een telefoon of tablet

1. Installeer **Obtainium** (via [obtainium.imranr.dev](https://obtainium.imranr.dev) of F-Droid).
2. In Obtainium: *Add app* → plak de URL van deze repository → *Add*.
3. Installeer; Android vraagt eenmalig toestemming om apps uit Obtainium te installeren.
4. Obtainium controleert daarna zelf op updates.

Is de repository privé, dan heeft Obtainium een GitHub-token nodig. Voor vrijwilligers is een **publieke** repository het eenvoudigst;
er staan geen wachtwoorden in de code (IP-adres en pincode worden alleen op het toestel opgeslagen).

### Vaste tablet als bedienpaneel
Zet in Android *Scherm vastzetten* (App pinning) aan en pin de app. Het scherm blijft aan zolang de app open staat.
