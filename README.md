# NOVA Launcher

Minecraft-Launcher auf Basis von Tauri 2, React und TypeScript.

## Entwicklung

```bash
npm install
NOVA_MS_CLIENT_ID=<azure-client-id> npm run tauri dev
```

### Ohne Client-ID testen

Ohne `NOVA_MS_CLIENT_ID` bietet der Login-Dialog im Entwicklungsmodus (`npm run tauri dev`)
ein **Offline-Testkonto** an: nur Name, keine Anmeldung. Damit laufen Einzelspieler und Server
im Offline-Modus. Skin- und Namensänderung brauchen ein Microsoft-Konto.

In Release-Builds (`npm run tauri build`) sind Offline-Konten gesperrt. Dort startet Minecraft
nur mit einem Microsoft-Konto, das das Spiel besitzt.

## Microsoft-Anmeldung einrichten

Der Launcher meldet sich per Gerätecode an: Microsoft → Xbox Live → XSTS → Minecraft Services.

1. Im [Azure-Portal](https://portal.azure.com) unter **App-Registrierungen** eine neue App anlegen.
   Kontotyp: **Nur persönliche Microsoft-Konten**.
2. Unter **Authentifizierung** die Option **Öffentliche Clientflows zulassen** aktivieren.
3. Die **Anwendungs-ID (Client-ID)** als `NOVA_MS_CLIENT_ID` setzen. Der Wert wird beim Kompilieren eingebettet.
4. Neue Azure-Apps müssen von Mojang für die Minecraft-API freigeschaltet werden
   ([Antrag](https://aka.ms/mce-reviewappid)). Bis dahin lehnt Minecraft die Anmeldung mit HTTP 403 ab.

## Was das Backend macht (`src-tauri/src`)

| Modul | Aufgabe |
| --- | --- |
| `auth.rs` | Anmeldung, Token-Refresh, mehrere Konten, Skin-Upload/-Reset, Namensänderung |
| `profiles.rs` | Spielprofile (Version, Vanilla/Fabric, Symbol), Spielzeit, Profilordner |
| `minecraft.rs` | Versions-Manifest, Fabric-Merge, Regeln, Bibliotheken, Natives, Assets, Java-Runtime |
| `download.rs` | Parallele Downloads mit SHA1-Prüfung und Fortschritt (`launch-progress`) |
| `launch.rs` | Argumente bauen, Java starten, Logs (`game-log`), Ende (`game-exit`), Stoppen |
| `bridge.rs` | Lokale Verbindung zum laufenden Spiel (Chat, Status, Cosmetics-Wechsel), Event `game-bridge` |
| `social.rs` | Anmeldung am Nova-Server über den Minecraft-Account (Mojang-Beitritt wie bei Servern) |
| `screenshots.rs` | Screenshots aller Profile auflisten, öffnen, löschen |
| `discord.rs` | Discord Rich Presence (eigener Thread, verbindet sich neu, wenn Discord startet) |

Alle Daten liegen im App-Datenordner (Einstellungen → Daten → Launcher-Daten):
`accounts.json` (Tokens, nur für den Nutzer lesbar), `profiles.json`, `versions/`, `libraries/`,
`assets/`, `runtime/` (Java von Mojang) und `instances/<profil-id>/` (Welten, Mods, Screenshots).

## Logo und Farben

Quelle ist das Wappen `tools/branding/emblem.png` (flache weiße Variante: `emblem-white.png`).
`java tools/branding/Branding.java` erzeugt daraus Favicon, Mod-Icon und das Logo für Launcher-Kopfzeile,
Willkommensbildschirm, Ladebildschirm und Hauptmenü der Mod (`NovaDraw.LOGO_WIDTH/LOGO_HEIGHT` passen zu dessen
Größe). Danach `npx tauri icon src-tauri/app-icon.png` für die App-Icons und `mod/build-all.sh` für die Mod.

Die Farben folgen dem Wappen: kühles Graphit und Silber. `node tools/branding/recolor.mjs` hat das frühere
Frost-Blau einmalig umgestellt (Helligkeit bleibt, Status-, Seltenheits- und Markenfarben bleiben) – nicht erneut
auf schon umgestellte Dateien anwenden.

## Nova-Server (`server/`)

Freunde, Online-Status, Chat und die News der Startseite. Node.js (≥ 20) mit WebSocket, Daten in
`server/data/store.json`, News und Patchnotes in `server/data/news.json` (ohne Neustart änderbar).

```bash
cd server
npm install
npm start                       # Port 8787
NOVA_ALLOW_OFFLINE=1 npm start  # zusätzlich Offline-Testkonten (nur lokal!)
npm test
```

| Variable | Bedeutung |
| --- | --- |
| `PORT` | Port (Standard 8787) |
| `NOVA_DATA` / `NOVA_NEWS` | Pfade der Daten- und News-Datei |
| `NOVA_ALLOW_OFFLINE=1` | Offline-Konten zulassen – auf einem öffentlichen Server **nie** setzen |

**Anmeldung:** Der Launcher holt eine `serverId`, meldet bei Mojang einen Beitritt mit dem Minecraft-Token
und der Server fragt Mojang (`hasJoined`), ob das stimmt – genau wie ein Minecraft-Server. Das Minecraft-Token
verlässt den Launcher nie; der Server gibt ein eigenes Token aus (30 Tage, gespeichert nur als Hash).

**Launcher-Adresse:** `NOVA_SERVER_URL=https://…` beim Bauen setzen (Standard `http://localhost:8787`).
Online stellen z. B. auf einem VPS hinter einem Reverse-Proxy mit HTTPS (Caddy, nginx), der auch WebSockets
unter `/ws` durchreicht, oder bei Railway/Fly.io. Freunde findet man über den Minecraft-Namen, sobald diese
Person sich einmal mit dem Nova Launcher angemeldet hat.

## Freunde, Chat und Nova-Menü

- **Launcher:** Seite FREUNDE (Liste, Anfragen, Chat), Freunde-Karte auf der Startseite, Benachrichtigung
  bei neuen Nachrichten, Zähler in der Navigation. Logik in `src/hooks/useSocial.ts`.
- **Spiel:** Rechts-Shift öffnet das Nova-Menü (`core/.../menu/NovaModMenu.java`) mit zwei Tabs:
  **Cosmetics** (an-/ablegen ohne Neustart, gleiche Regeln wie die Garderobe, bis zu 3 am Kopf) und
  **Freunde** (Liste, Anfragen, Chat). Neue Nachrichten und Freunde, die online kommen, erscheinen im Spiel-Chat.
  Der Cosmetics-Button im Nova-Hauptmenü öffnet dasselbe Menü.
- **Weg der Nachrichten:** Spiel ⇄ Launcher über einen lokalen Socket (`-Dnova.bridge`, `bridge.rs`,
  `core/.../social/NovaBridge.java`) ⇄ Nova-Server über WebSocket. Die Mod spricht nie direkt mit dem Server.
- **Status:** Die Mod meldet Hauptmenü, Einzelspieler oder Serveradresse; der Launcher reicht das an Freunde
  und Discord weiter. „Server anzeigen“ (Discord-Seite) aus: Freunde sehen nur „Mehrspieler“.
- **Cosmetics im Spiel geändert:** Die Mod speichert sie in `config/nova-cosmetics.txt` und meldet sie dem
  Launcher, der sein Loadout übernimmt. Ohne Nova Launcher (z. B. Lunar) gilt beim nächsten Start die Datei.

Ohne Nova Launcher (Lunar) gibt es im Menü nur Cosmetics, Freunde und Chat brauchen die Launcher-Verbindung.

## Mehrspielermenü der Mod

Ersetzt das Vanilla-Mehrspielermenü in allen Versionen (`MinecraftClientMixin`): Tabs **Gespeicherte Server**,
**Nova Server** und **LAN-Welten**, Partner-Karten mit Pixel-Banner und Badge (VERIFIED/PARTNER, `NovaContent`),
darunter die gespeicherten Server mit Icon, MOTD, Spielerzahl und Ping. Unten Beitreten, Direkt verbinden,
Hinzufügen, Bearbeiten, Löschen, Aktualisieren (F5), Zurück – die Dialoge sind die von Minecraft.
Layout und Zeichnen: `core/.../menu/NovaMultiplayer.java`, Anbindung je Version: `NovaMultiplayerScreen`.
1.8.9 zeigt Ersatz-Icons statt Server-Icons.

## Screenshots

Seite SCREENSHOTS: alle Bilder aus `instances/<profil>/screenshots/`, Filter nach Profil, Großansicht mit
Pfeiltasten, Öffnen im Standardprogramm, Löschen, Ordner öffnen. Angezeigt über Tauris Asset-Protokoll
(nur dieser Ordner ist freigegeben, `tauri.conf.json` → `assetProtocol`).

## Discord Rich Presence

Zeigt „Im Launcher“ oder „Spielt Minecraft 1.21.4 · Auf play.example.net“ mit Spielzeit.

1. Auf [discord.com/developers](https://discord.com/developers/applications) eine Anwendung „Nova Client“ anlegen.
2. Unter **Rich Presence → Art Assets** das Logo mit dem Namen `nova` hochladen.
3. Die **Application ID** beim Bauen als `NOVA_DISCORD_APP_ID` setzen. Ohne ID bleibt Rich Presence aus.

Abschalten lässt es sich auf der Discord-Seite des Launchers.

## Updates (GitHub Releases)

Der Launcher sucht beim Start und alle 6 Stunden nach einer neuen Version
(`https://github.com/<owner>/nova-launcher/releases/latest/download/latest.json`, `tauri.conf.json` → `plugins.updater`)
und zeigt oben rechts „UPDATE x.y.z“. Ein Klick lädt, installiert und startet neu. Die Mod-JARs stecken im
Launcher (`include_bytes!` in `nova_mod.rs`), Mod-Updates kommen also mit.

Updates sind signiert. Der private Schlüssel liegt in `~/.tauri/nova-launcher.key` (nicht im Repo, sicher
aufbewahren – ohne ihn kann niemand mehr Updates ausliefern), der öffentliche steht in `tauri.conf.json`.

**Neue Version veröffentlichen:**
1. Version in `package.json`, `src-tauri/Cargo.toml` und `src-tauri/tauri.conf.json` erhöhen,
   Patchnotes in `server/data/news.json` ergänzen.
2. `cd mod && ./build-all.sh` (falls sich die Mod geändert hat), alles committen.
3. Tag pushen: `git tag v0.2.0 && git push origin v0.2.0`. Der Workflow `.github/workflows/release.yml`
   baut den Windows-Installer, signiert ihn und legt einen Release-Entwurf mit `latest.json` an.
4. Den Entwurf auf GitHub veröffentlichen – ab dann bekommen alle Launcher das Update.

GitHub-Secrets dafür: `TAURI_SIGNING_PRIVATE_KEY` (Inhalt der Schlüsseldatei), `NOVA_MS_CLIENT_ID`,
`NOVA_SERVER_URL`, `NOVA_DISCORD_APP_ID`.

## Nova-Mod (`mod/`)

Ladebildschirm und Hauptmenü im Nova-Design für **1.8.9, 1.20 – 1.21.11 und 26.1 – 26.3**.

```
mod/
  core/                 Nova-Design in reinem Java 8 (Layout, Buttons, Icons, Pixelschrift,
                        Landschaft, Ladebildschirm) – zeichnet nur über NovaCanvas
  versions/<mc>/        dünne Anpassungsschicht je Minecraft-Version (Canvas, Screens, Mixins)
  common.gradle         gemeinsame Build-Logik
  build-all.sh          baut alle Versionen → src-tauri/resources/nova-mods/
```

| Build | Minecraft | Loader / Mappings | Java |
| --- | --- | --- | --- |
| 1.8.9 | 1.8.9 | Legacy Fabric, Legacy Yarn | 8 |
| 1.20.1 · 1.20.4 · 1.20.6 | 1.20 – 1.20.6 | Fabric, Yarn | 17 / 21 |
| 1.21.1 · 1.21.3 · 1.21.4 · 1.21.5 · 1.21.8 · 1.21.10 · 1.21.11 | 1.21 – 1.21.11 | Fabric, Yarn | 21 |
| 26.1 · 26.2 · 26.3 | 26.1 – 26.3 | Fabric, Mojang-Namen (unverschleiert) | 25 |

Welche Minecraft-Version welchen Build bekommt, steht in `src-tauri/src/nova_mod.rs`. Der Launcher
installiert die passende JAR automatisch beim Anlegen eines Fabric-Profils und vor jedem Start
(fremde Dateien im Mods-Ordner werden nie überschrieben). Für 1.8.9 nutzt er Legacy Fabric.

```bash
cd mod
./build-all.sh                          # alle Versionen bauen und in den Launcher kopieren
./gradlew -p versions/1.21.4 build      # eine Version bauen
```

Gradle braucht Java 25+ (Loom 1.19); `build-all.sh` nutzt das Homebrew-OpenJDK, falls `JAVA_HOME` nicht gesetzt ist.
Neue Version anlegen: `python3 new-version.py <mc> <yarn> <fabric-api> "<range>" <java> <run-java> <basis>`,
dann die Compiler-Fehler mit dem per `genSources` entpackten Minecraft-Code abgleichen.

Partner-Server sind Platzhalter (`core/.../NovaContent.java`), Texte stehen in `core/src/main/resources/assets/nova/lang/`.

## 3D-Cosmetics (`public/cosmetics/`)

Jedes 3D-Cosmetic ist ein Ordner im Bedrock-Format (lässt sich in Blockbench als **Bedrock Entity** öffnen):

```
public/cosmetics/frost-wings/
  cosmetic.json      Name, Typ, Seltenheit; Leucht-Ebene ("glow") und Partikel ("sparkles")
  model.geo.json     Geometrie: Box- oder Flächen-UV, gedrehte Würfel, dünne Flächen (Größe 0)
  texture.png        Grundtextur (Cutout)
  glow.png           Leucht-Textur: Farbe + Deckkraft = Lichtstärke, ohne Schatten gezeichnet
  animation.json     "idle" läuft in Schleife
  thumbnail.png      Vorschaubild für Store und Garderobe
```

Die vorhandenen Cosmetics entstehen per Code statt in Blockbench: `tools/cosmetics/FrostWings.java`
(Frostblitz-Schwingen, HD-Texturen mit Java2D) und `tools/cosmetics/RavenPet.java` (Pet „Rabe", blockig mit Pixeltextur
128 × 128) und `tools/cosmetics/RavenWings.java` (Rabenschwingen: die Flügel des Raben doppelt so groß) bauen Textur, Geometrie, Animation und Manifest. `tools/cosmetics/render.sh` rendert mit dem
Mod-Kern das Vorschaubild (Ansicht aus `"thumbnail"` im Manifest) und Ansichten von hinten und vorne.

```bash
java tools/cosmetics/FrostWings.java public/cosmetics/frost-wings
java tools/cosmetics/RavenPet.java public/cosmetics/raven
java tools/cosmetics/RavenWings.java public/cosmetics/raven-wings
java tools/cosmetics/GuardianExtras.java public/cosmetics
java tools/cosmetics/RavenPet.java public/cosmetics/purpur-rabe purpur
tools/cosmetics/render.sh public/cosmetics/raven
```

Capes mit "cape" in cosmetic.json (Mod: `core/.../cosmetic/CapeBehavior.java`) bestehen aus Streifen untereinander,
heben sich mit dem Tempo nach hinten und flattern stärker ("idle" ↔ "run"). Bringt ein Cosmetic eine
Elytra-Textur mit ("elytra", Vanilla-Layout), trägt die Elytra des Spielers diese Textur
(`ElytraTextureMixin` je Version, außer 1.8.9) und das Cape wird darunter ausgeblendet – wie bei
Minecraft-Capes. Beispiel: Purpur-Wächter, `tools/cosmetics/GuardianCape.java`.
Kopf-Cosmetics ("attach": "head") drehen mit dem Kopf und werden unter einem Helm ausgeblendet. Beispiel:
Purpur-Krone, `tools/cosmetics/GuardianCrown.java`.
Weitere Teile des Sets aus `tools/cosmetics/GuardianExtras.java`: Purpur-Halo und Purpur-Hörner (Kopf) sowie
Glutspuren – Abdrücke beim Laufen ("trail", `core/.../cosmetic/TrailBehavior.java`): Sie liegen mit Weltkoordinaten
und werden pro Bild in den Modellraum des Spielers umgerechnet, dafür meldet jede Version Position, Körperdrehung
und Ducken (`Cosmetics.trackPlayer`). Der Purpur-Rabe ist die Variante `purpur` von `RavenPet.java`.

Flügel mit "wings" in cosmetic.json (Mod: `core/.../cosmetic/WingBehavior.java`) sind angelegt ("closed")
und breiten sich aus ("open"), sobald der Spieler schneller als 5 Blöcke pro Sekunde über Grund ist –
Sprinten, Fliegen, Elytra. In der 3D-Vorschau des Launchers wechseln sie alle 4 Sekunden.

Pets hängen wie alle Cosmetics am Körper des Spielers. Ihr Verhalten steht unter "pet" in cosmetic.json
(Mod: `core/.../cosmetic/PetBehavior.java`, Launcher: `cosmetics/petBehavior.ts`): Der Rabe schwebt rechts über
dem Kopf ("idle"), fliegt alle 18–35 Sekunden eine Runde um den Spieler und lässt dabei Herzen über ihm
aufsteigen ("fly"), landet nach 10 Sekunden Stillstand neben dem rechten Fuß ("sit") und fliegt wieder
auf, sobald der Spieler sich bewegt. Beim Elytra-Gleitflug fliegt er mit ausgebreiteten Flügeln rechts
neben dem Spieler mit ("glide", Platz unter "pet.glide"). Die Versionsschicht meldet dafür Spielerposition und Gleiten
(`Cosmetics.trackPlayer`). `tools/cosmetics/render.sh` spielt das Verhalten für einen stillstehenden Spieler
durch und rendert einzelne Momente.

- **Launcher:** Im Store zeigt **3D-VORSCHAU** das Cosmetic an deinem Skin (`cosmetics/bedrockModel.ts`).
  Dafür braucht der Eintrag in `src/data/launcherData.ts` die Angabe `model: true`.
- **Spiel:** Beim Bauen kopiert Gradle den Ordner nach `assets/nova/cosmetics/` in die Mod. Der Launcher
  übergibt das gespeicherte Loadout als `-Dnova.cosmetics=<id>,…`, und die Mod zeichnet es am eigenen Spieler
  (F5, Inventar) in allen Versionen. Andere Spieler sehen es noch nicht, dafür fehlt ein Server.
  Pro Typ passt ein Item, am Kopf bis zu drei (`slotLimits` in `CosmeticsView.tsx`), etwa Krone, Halo und Hörner.
- Animationen dürfen Keyframes oder Molang enthalten (`query.anim_time`, `math.sin/cos/abs`, `+ - * /`).
  Positives Y dreht ein Teil auf der +X-Seite nach vorne. Launcher (`bedrockModel.ts`) und Mod
  (`core/.../cosmetic/`) rechnen gleich, Blockbench kann die Drehrichtung gespiegelt anzeigen.

```bash
cd mod
./gradlew -p versions/1.21.4 runClient -Pnova.cosmetics=frost-wings -Pnova.capture=world
# legt eine Testwelt an, speichert run/screenshots/nova-world.png (Spieler von hinten)
# und nach 25 Sekunden Stillstand nova-world-still.png; mit -Pnova.elytra trägt die Testfigur eine Elytra
# (-Pnova.helmet zusätzlich einen Helm), mit -Pnova.walk läuft sie einen Steinweg entlang und duckt sich,
# mit -Pnova.glide startet sie mit Elytra hoch in der Luft und gleitet (Pets fliegen dann mit);
# -Pnova.menu=cosmetics|friends öffnet das Nova-Menü, -Pnova.bridge=127.0.0.1:<port> -Pnova.bridge.token=<t>
# verbindet mit einem (Test-)Launcher; -Pnova.capture=multiplayer fotografiert das Mehrspielermenü (1.21.4),
# -Pnova.open=multiplayer öffnet es in 26.3 und speichert einen Screenshot
```

## Tests

```bash
cd src-tauri
cargo test --lib                                   # Regeln, Maven-Pfade
NOVA_TEST_DIR=/tmp/nova-test cargo test --lib -- --ignored --nocapture
# installiert 1.21.4 (Fabric) und 1.8.9 echt – ohne Assets
```
