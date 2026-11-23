# GitSyncer

[![Release](https://img.shields.io/github/v/release/fisch981/Gitsyncer)](https://github.com/fisch981/Gitsyncer/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://android.com)

[🇬🇧 English](#english) | [🇩🇪 Deutsch](#deutsch)

---

<a name="english"></a>
## 🇬🇧 English

**GitSyncer** (`de.fisch981.gitsyncer`) is a modern, native Android app for seamless two-way synchronization of arbitrary local folders (such as **Obsidian Vaults**, Markdown notes, or source code repositories) with Git hosting services including **GitHub**, **GitLab**, and **Gitea**. In addition, GitSyncer provides a dedicated Release-APK mode to directly download, track, and update Android apps from GitHub Releases.

### Key Features

#### 📦 Release APK Mode
- **Targeted APK Downloads:** Repositories can optionally be configured to download only compiled release `.apk` files from GitHub Releases rather than cloning the entire Git repository.
- **Integrated Release Browser:** Inspect all published releases including version tags, changelog/notes, release dates, and file sizes.
- **One-Tap Direct Installation:** Launch APK installation directly from the app or from notifications via Android's PackageInstaller (`FileProvider`).
- **Update Checking:** Automatic background and manual detection of new release versions.

#### 🔄 Complete Git Functionality (Eclipse JGit)
- **Two-Way Synchronization:** Supports clone, pull, fetch, push, commit, and status checks.
- **Shallow Clone (`depth = 1`):** Clones only the latest commit by default to save storage and mobile bandwidth.
- **Diff Viewer:** Integrated visual diff viewer highlighting added, modified, and removed lines.
- **Merge Conflict Resolution:** Clear selection between "Keep Local" vs. "Keep Remote".
- **Hard Reset & Self-Healing:** One-tap restore of modified or deleted local files, plus clean re-clone capabilities.

#### ⚡ Stability, Reliability & Instant Abort
- **Instant Abort:** Cancel stalled operations within milliseconds through direct socket closing (`transport.close()`) and thread interruption.
- **Mobile Network (5G/LTE) Optimization:** Robust handling of large Git packfiles with optimized buffer sizes and connection settings.
- **Automatic Lock Cleanup:** Stale lock files (`index.lock`, `HEAD.lock`, `refs/...lock`) and unfinished merges are cleaned automatically before each operation.
- **Zlib / Cache Resilience:** Tuned JGit `WindowCache` with strong references (`isPackedGitUseStrongRefs = true`) avoids compression stream issues (`Inflater has been closed`).

#### 📂 Repository & Folder Management
- **Smart Sorting:** Active repositories are always displayed at the top in alphabetical order (A–Z), followed by paused repositories below.
- **Pause vs. Delete:** Temporarily pause repositories to exclude them from batch syncs without losing settings.
- **Default Storage Directory:** Set a base storage path (e.g., `/storage/emulated/0/Documents/Notes`); new repositories automatically set up matching subfolders.

#### 🌐 GitHub Integration & Explorer
- **Central Credentials:** Store your GitHub Personal Access Token (PAT) once in Settings; new repositories can inherit it automatically.
- **1-Tap Repository Import:** Browse your private and public GitHub repositories directly in the app.

#### 🔐 Authentication
- **HTTPS:** Personal Access Tokens (PAT) or Username/Password with in-app browser shortcut to create tokens.
- **SSH:** Built-in key generator (RSA 2048/4096 and Ed25519), key import, and one-tap public key copy.

#### ⏱️ Background Sync & Quick Settings Tile
- **Android WorkManager:** Battery-friendly background synchronization in customizable intervals (15 min to 24 h).
- **Quick Settings Tile:** Sync all active repositories with one tap right from the Android notification bar.

#### 🎨 Interface & Multilingual Support
- **Material 3 & Jetpack Compose:** Clean UI with Dark Mode support.
- **Bilingual (English / German):** Follows the system language by default; switch anytime between System, English, and German in Settings.
- **Obsidian Compatibility:** Works seamlessly with local storage paths (`/storage/emulated/0/...`).

### Build & Release

```powershell
# Build signed release APK and publish to GitHub Releases
powershell -ExecutionPolicy Bypass -File .\push.ps1 -ChangeSummary "Description of changes"
```

---

<a name="deutsch"></a>
## 🇩🇪 Deutsch

**GitSyncer** (`de.fisch981.gitsyncer`) ist eine moderne, native Android-App zur nahtlosen Zwei-Wege-Synchronisation beliebiger lokaler Verzeichnisse (z. B. **Obsidian Vaults**, Markdown-Notizen oder Quellcode-Repositories) mit Git-Hostern wie **GitHub**, **GitLab** und **Gitea**. Zusätzlich bietet GitSyncer einen spezialisierten Release-APK-Modus zum direkten Beziehen und Aktualisieren von Android-Apps aus GitHub Releases.

### Highlights & Features

#### 📦 Release-APK-Download-Modus
- **Gezielter APK-Download:** Repositories können optional so konfiguriert werden, dass statt des gesamten Git-Repositorys (Quellcode, Assets, Commit-Historie) ausschließlich die kompilierten Release-APKs aus den GitHub Releases heruntergeladen werden.
- **Integrierter Release-Browser:** Übersicht über alle veröffentlichten Releases inklusive Versionsnummern, Release-Notes, Veröffentlichungsdatum und Dateigrößen.
- **Direkt-Installation:** Heruntergeladene APK-Dateien können direkt aus der App oder aus Benachrichtigungen heraus über den systemweiten Android PackageInstaller installiert werden (`FileProvider`).
- **Update-Prüfung:** Schnelle Erkennung neuer Releases sowohl bei manuellem Sync als auch im Hintergrund.

#### 🔄 Volle Git-Funktionalität (Eclipse JGit)
- **Zwei-Wege-Synchronisation:** Unterstützt Klonen, Pull, Fetch, Push, Commit und detaillierte Status-Prüfungen.
- **Shallow-Clone (`depth = 1`):** Lädt standardmäßig nur den aktuellen Stand herunter und spart Bandbreite sowie Speicherplatz auf dem Mobilgerät.
- **Diff-Viewer:** Integrierter Diff-Viewer mit farblicher Hervorhebung modifizierter, hinzugefügter und entfernter Zeilen.
- **Merge-Konflikt-Behandlung:** Klare Auswahlmöglichkeit bei Konflikten („Lokal behalten“ vs. „Remote behalten“).
- **Hard-Reset & Wiederherstellung:** Schnelle Wiederherstellung versehentlich gelöschter lokaler Dateien aus dem Git-Tree sowie Ein-Klick-Reset („Repository sauber neu klonen“).

#### ⚡ Stabilität, Ausfallsicherheit & Sofort-Abbruch
- **Sofortiger Abbruch:** Laufende oder hängende Synchronisationen können jederzeit sofort abgebrochen werden. Über Socket-Schließung (`transport.close()`) und Thread-Interruption werden blockierte Netzwerkverbindungen innerhalb von Millisekunden freigegeben.
- **Mobilfunk-Optimierung (5G/LTE):** Robuste Handhabung großer Git-Packfiles und Binärdateien mit optimierten Timeouts und Buffer-Größen.
- **Automatische Lock-Bereinigung:** Verwaiste Lock-Dateien (`index.lock`, `HEAD.lock`, `refs/...lock`) und unvollständige Merges werden vor jeder Operation automatisch bereinigt.
- **Selbstheilung bei Zlib-/Cache-Fehlern:** Optimierter JGit `WindowCache` mit Strong References (`isPackedGitUseStrongRefs = true`) verhindert vorzeitiges Bereinigen aktiver Dekomprimierungs-Instanzen (`Inflater has been closed`).

#### 📂 Intelligente Repository-Verwaltung
- **Strukturierte Sortierung:** Aktive Repositories stehen automatisch immer oben und sind alphabetisch geordnet (A–Z). Pausierte Repositories folgen getrennt darunter (ebenfalls alphabetisch).
- **Pausieren statt Löschen:** Repositories können temporär deaktiviert (`isEnabled = false`) werden, um sie vom automatischen und manuellen Sammel-Sync auszuschließen, ohne die lokale Konfiguration zu verlieren.
- **Automatische Verzeichnisstruktur:** Definiere ein Standard-Basisverzeichnis (z. B. `/storage/emulated/0/Documents/Notes`); neue Repositories werden dort automatisch in passenden Unterordnern eingerichtet.

#### 🌐 GitHub Integration & Explorer
- **Zentrale Zugangsdaten:** Hinterlege dein GitHub Personal Access Token (PAT) einmalig global in den Einstellungen; Repositories können darauf standardmäßig zugreifen.
- **1-Klick Repository-Import:** Durchsuche deine privaten und öffentlichen GitHub-Repositories direkt in der App und importiere sie mit einem Fingertipp.

#### 🔐 Authentifizierung
- **HTTPS:** Personal Access Tokens (PAT) oder Benutzername/Passwort mit integrierter Schaltfläche zur Token-Erstellung im Browser.
- **SSH:** Integrierter Schlüsselgenerator (RSA 2048/4096 und Ed25519) inklusive Schlüsselimport und 1-Klick-Kopieren des Public Keys.

#### ⏱️ Hintergrund-Sync & Schnellzugriff
- **Android WorkManager:** Zuverlässige, akkuschonende Hintergrund-Synchronisation in konfigurierbaren Intervallen (15 min bis 24 h).
- **Quick Settings Kachel (Statusleiste):** Schnelles Synchronisieren aller aktiven Repositories mit einem Klick direkt aus den Android-Schnelleinstellungen.

#### 🎨 Benutzeroberfläche & Zweisprachigkeit
- **Material 3 & Jetpack Compose:** Moderne Oberfläche mit Dark Mode.
- **Zweisprachig (Deutsch / Englisch):** Standardmäßig an die Systemsprache angepasst; jederzeit in den Einstellungen zwischen System, Deutsch und Englisch umschaltbar.
- **Obsidian-Kompatibilität:** Reibungsloser Zugriff auf Gerätespeicherpfade (`/storage/emulated/0/...`).

### Build & Release

```powershell
# Signierte Release-APK kompilieren und auf GitHub Releases veröffentlichen
powershell -ExecutionPolicy Bypass -File .\push.ps1 -ChangeSummary "Beschreibung der Änderungen"
```

---

## License / Lizenz

Distributed under the [MIT License](LICENSE).
