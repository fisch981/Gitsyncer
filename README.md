# GitSyncer

**GitSyncer** (`de.fisch981.gitsyncer`) ist eine moderne, native Android-App zur nahtlosen Zwei-Wege-Synchronisation beliebiger lokaler Verzeichnisse (z. B. **Obsidian Vaults**, Markdown-Notizen oder Quellcode-Repositories) mit Git-Hostern wie **GitHub**, **GitLab** und **Gitea**. Zusätzlich bietet GitSyncer einen spezialisierten Release-APK-Modus zum direkten Beziehen und Aktualisieren von Android-Apps aus GitHub Releases.

---

## Highlights & Features

### 📦 Neu: Release-APK-Download-Modus (ab v1.3.0)
- **Gezielter APK-Download:** Repositories können optional so konfiguriert werden, dass statt des gesamten Git-Repositorys (Quellcode, Assets, Commit-Historie) ausschließlich die kompilierten Release-APKs aus den GitHub Releases heruntergeladen werden.
- **Integrierter Release-Browser:** Übersicht über alle veröffentlichten Releases inklusive Versionsnummern, Release-Notes, Veröffentlichungsdatum und Dateigrößen.
- **Direkt-Installation:** Heruntergeladene APK-Dateien können direkt aus der App heraus über den systemweiten Android PackageInstaller installiert werden (`FileProvider`).
- **Update-Prüfung:** Schnelle Erkennung neuer Releases sowohl bei manuellem Sync als auch im Hintergrund.

### 🔄 Volle Git-Funktionalität (Eclipse JGit)
- **Zwei-Wege-Synchronisation:** Unterstützt Klonen, Pull, Fetch, Push, Commit und detaillierte Status-Prüfungen.
- **Shallow-Clone (`depth = 1`):** Lädt standardmäßig nur den aktuellen Stand herunter und spart Bandbreite sowie Speicherplatz auf dem Mobilgerät.
- **Diff-Viewer:** Integrierter Diff-Viewer mit farblicher Hervorhebung modifizierter, hinzugefügter und entfernter Zeilen.
- **Merge-Konflikt-Behandlung:** Klare Auswahlmöglichkeit bei Konflikten („Lokal behalten“ vs. „Remote behalten“).
- **Hard-Reset & Wiederherstellung:** Schnelle Wiederherstellung versehentlich gelöschter lokaler Dateien aus dem Git-Tree sowie Ein-Klick-Reset („Repository sauber neu klonen“).

### ⚡ Stabilität, Ausfallsicherheit & Sofort-Abbruch
- **Sofortiger Abbruch:** Laufende oder hängende Synchronisationen können jederzeit sofort abgebrochen werden. Über Socket-Schließung (`transport.close()`) und Thread-Interruption werden blockierte Netzwerkverbindungen innerhalb von Millisekunden freigegeben.
- **Mobilfunk-Optimierung (5G/LTE):** Robuste Handhabung großer Git-Packfiles und Binärdateien mit optimierten Timeouts und Buffer-Größen.
- **Automatische Lock-Bereinigung:** Verwaiste Lock-Dateien (`index.lock`, `HEAD.lock`, `refs/...lock`) und unvollständige Merges werden vor jeder Operation automatisch bereinigt.
- **Selbstheilung bei Zlib-/Cache-Fehlern:** Optimierter JGit `WindowCache` mit Strong References (`isPackedGitUseStrongRefs = true`) verhindert vorzeitiges Bereinigen aktiver Dekomprimierungs-Instanzen (`Inflater has been closed`).

### 📂 Intelligente Repository-Verwaltung
- **Strukturierte Sortierung:** Aktive Repositories stehen automatisch immer oben und sind alphabetisch geordnet (A–Z). Pausierte Repositories folgen getrennt darunter (ebenfalls alphabetisch).
- **Pausieren statt Löschen:** Repositories können temporär deaktiviert (`isEnabled = false`) werden, um sie vom automatischen und manuellen Sammel-Sync auszuschließen, ohne die lokale Konfiguration zu verlieren.
- **Automatische Verzeichnisstruktur:** Definiere ein Standard-Basisverzeichnis (z. B. `/storage/emulated/0/Documents/Notes`); neue Repositories werden dort automatisch in passenden Unterordnern eingerichtet.

### 🌐 GitHub Integration & Explorer
- **Zentrale Zugangsdaten:** Hinterlege dein GitHub Personal Access Token (PAT) einmalig global in den Einstellungen; Repositories können darauf standardmäßig zugreifen.
- **1-Klick Repository-Import:** Durchsuche deine privaten und öffentlichen GitHub-Repositories direkt in der App und importiere sie mit einem Fingertipp.

### 🔐 Authentifizierung
- **HTTPS:** Personal Access Tokens (PAT) oder Benutzername/Passwort mit integrierter Schaltfläche zur Token-Erstellung im Browser.
- **SSH:** Integrierter Schlüsselgenerator (RSA 2048/4096 und Ed25519) inklusive Schlüsselimport und 1-Klick-Kopieren des Public Keys.

### ⏱️ Hintergrund-Sync & Schnellzugriff
- **Android WorkManager:** Zuverlässige, akkuschonende Hintergrund-Synchronisation in konfigurierbaren Intervallen (15 min bis 24 h).
- **Quick Settings Kachel (Statusleiste):** Schnelles Synchronisieren aller aktiven Repositories mit einem Klick direkt aus den Android-Schnelleinstellungen.

### 🎨 Benutzeroberfläche & Speicherzugriff
- **Material 3 & Jetpack Compose:** Moderne Oberfläche mit Dynamic Color Support und Dark Mode.
- **Obsidian-Kompatibilität:** Reibungsloser Zugriff auf Gerätespeicherpfade (`/storage/emulated/0/...`) für nahtlose Zusammenarbeit mit Obsidian und anderen Markdown-Editoren.

---

## Build & Release

Das Projekt verwendet das automatisierte Build- und Release-Skript `push.ps1`:

```powershell
# Inkrementiert die Version, aktualisiert das Changelog, baut die signierte Release-APK,
# committet die Änderungen und veröffentlicht das Release auf GitHub:
powershell -ExecutionPolicy Bypass -File .\push.ps1 -ChangeSummary "Beschreibung der Änderungen"
```

Release-Builds werden automatisch als Release-Assets auf GitHub Releases bereitgestellt (`gh release create / upload`).
