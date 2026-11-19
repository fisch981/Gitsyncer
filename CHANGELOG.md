# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden in dieser Datei dokumentiert.
Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.0.0/),
und dieses Projekt hält sich an [Semantic Versioning](https://semver.org/lang/de/).

---

## [1.3.2] - 2026-11-19

### Geändert / Hinzugefügt
- Langer Druck auf das Schnellzugriffsleisten-Symbol oeffnet nun direkt die App statt der Android-Systemeinstellungen

## [1.3.1] - 2026-11-19

### Geändert / Hinzugefügt
- build: Release-APK ausschliesslich im Build-Verzeichnis erzeugen und per GitHub Releases ausliefern (Root-APK entfernt)

## [1.3.0] - 2026-11-19

### Geändert / Hinzugefügt
- Optionaler Modus 'Nur Release-APKs herunterladen': Lädt gezielt APKs aus GitHub Releases statt ganzer Repositories inkl. Release-Browser und Direkt-Installation

## [1.2.6] - 2026-11-03

### Geändert / Hinzugefügt
- Schnellzugriffsleisten-Sync synchronisiert nun alle aktiven Repositories (auch wenn automatischer Hintergrund-Sync deaktiviert ist)

## [1.2.5] - 2026-11-03

### Geändert / Hinzugefügt
- Sortierung der Repository-Liste: Aktive Repositories stehen oben und sind alphabetisch sortiert (pausierte Repositories folgen darunter alphabetisch)

## [1.2.4] - 2026-11-03

### Geändert / Hinzugefügt
- Sofortiger Abbruch haengender Socket-Verbindungen (Transport close & Thread Interruption), Deaktivierung fehlerhafter Connection:close-Header fuer stabile Git-Packfile-Uebertragungen grosser Repositories und Timeout-Optimierung

## [1.2.3] - 2026-11-03

### Geändert / Hinzugefügt
- Abbrechen-Funktion fuer laufende/haengende Synchronisationen hinzugefuegt und JGit-CanceledException Support implementiert

## [1.2.2] - 2026-11-03

### Geändert / Hinzugefügt
- Behebung von 'Software caused connection abort' durch Deaktivierung von Keep-Alive & automatische Retries bei Netzwerkunterbrechungen, sowie Absicherung gegen Ueberschreiben des Basis-Verzeichnisses

## [1.2.1] - 2026-10-27

### Geändert / Hinzugefügt
- Behebung von Checkout-Abbruechen bei grossen Dateien (streamFileThreshold 100MB), Schutz vor ungewollter Loeschung fehlender Dateien und sauberer Hard-Reset

## [1.2.0] - 2026-10-27

### Hinzugefügt
- **Repositories pausieren / vom Sync ausschließen:**
  - Neue Option `isEnabled` pro Repository: Erlaubt das temporäre Deaktivieren/Pausieren von Repositories, die gerade nicht aktiv genutzt werden, ohne sie löschen zu müssen.
  - Ein-/Ausschaltbar direkt über ein Pause/Play-Icon auf den Repository-Karten im Dashboard, in der Detailansicht ("Übersicht") sowie im Bearbeiten-Dialog.
  - Pausierte Repositories werden automatisch von automatischen periodischen Hintergrund-Syncs (WorkManager), Quick Settings Kacheln und manuellen Sammel-Syncs ("Alle synchronisieren") ausgeschlossen.
  - Visuelle Kennzeichnung mit dezentem „Pausiert“-Badge und angepasstem Status.

### Geändert
- **Bereinigung von Menü- und Oberflächemtexten:**
  - Alle verkaufs- oder marketingartigen Hinweise ("100% Kostenlos", "umsonst", "Vollversion freigeschaltet") in der Benutzeroberfläche wurden entfernt und durch einheitliche, neutrale und professionelle Formulierungen ersetzt.

---

## [1.1.3] - 2026-10-25

### Behoben
- **Behebung von `Inflater has been closed`:** 
  - JGit `WindowCache` nutzt nun `isPackedGitUseStrongRefs = true`, um zu verhindern, dass die Android Garbage Collection Dekomprimierungs-Instanzen während des Einpflegens neuer Commits vorzeitig zerstört.
  - Thread-sichere Synchronisierung aller Git-Operationen pro Repository, um gleichzeitige Zugriffe durch Hintergrund-Syncs und UI-Aktualisierungen auszuschließen.
  - Sauberes Ressourcen-Management: `Repository` und `Git` Instanzen werden garantiert in `finally`-Blöcken freigegeben.
- **Automatische Selbstreparatur & Lock-Bereinigung:**
  - Verwaiste `.lock`-Dateien (`index.lock`, `HEAD.lock`, `refs/...lock`) und abgebrochene Merge-Zustände werden vor jedem Vorgang automatisch aufgeräumt.
  - Im Falle von Zlib-Stream-Fehlern wird der Cache automatisch zurückgesetzt und der Sync selbstständig wiederholt, ohne dass manuelle Eingriffe nötig sind.

---

## [1.1.2] - 2026-10-24

### Behoben
- **Branch-Mapping ohne Fallback:**
  - Pull und Push forcieren nun fest den konfigurierten Zielbranch (`main`), wodurch Fehlermeldungen bezüglich fehlender `master`-Branches auf GitHub dauerhaft eliminiert wurden.
  - Erkennung unvollständiger/abgebrochener Klon-Ordner mit automatischer Bereinigung.
- **Reset-Funktion:**
  - Neuer Button *„Repository sauber neu klonen (Reset)“* in der Detailansicht für manuelle Zurücksetzungen.

---

## [1.1.1] - 2026-10-24

### Hinzugefügt
- **Shallow-Clone (Tiefe 1):**
  - Standardmäßig aktiv beim Anlegen von Repositories: Lädt nur den aktuellen Stand herunter und spart hunderte Megabyte an altem Versionsverlauf.
- **`largeHeap` & Memory-Streaming:**
  - `android:largeHeap="true"` im Manifest für bis zu 512 MB Heap.
  - JGit `streamFileThreshold = 4 MB` und `isPackedGitMMAP = false` zur Vermeidung von OutOfMemory-Fehlern bei großen Binärdateien/APKs (wie in `FolderGallery`).
  - Timeout für Klon- und Push/Pull-Vorgänge auf 300 Sekunden erhöht.

---

## [1.1.0] - 2026-10-24

### Hinzugefügt
- **Zentrale GitHub-Zugangsdatenverwaltung:**
  - GitHub Personal Access Token (PAT) kann einmalig in den Einstellungen hinterlegt, getestet und für alle Repositories wiederverwendet werden.
- **Automatischer GitHub Repository-Explorer:**
  - Neues Dialog-Fenster zum direkten Auslesen aller eigenen GitHub-Repositories (öffentlich & privat) inklusive Suchfilter und 1-Klick-Übernahme aller Daten (Name, Clone-URL, Branch).
- **Konfigurierbares Standard-Basisverzeichnis:**
  - In den Einstellungen wählbarer Basisordner. Neue Repositories legen automatisch einen Unterordner `<Basisverzeichnis>/<RepoName>` an.

---

## [1.0.1] - 2026-10-24

### Hinzugefügt
- **1-Klick Token-Erstellung im Browser:**
  - Direkter Link zur GitHub-Tokenseite mit vorausgewähltem `repo`-Scope.
  - Einfügen-Button für die Zwischenablage im Token-Eingabefeld.

---

## [1.0.0] - 2026-10-24

### Hinzugefügt
- **Vollwertige Git-Synchronisation für Android:** Nahtlose Zwei-Wege-Synchronisation lokaler Verzeichnisse (z. B. Obsidian Vaults, Notizen oder Quellcode) mit entfernten Git-Repositories (GitHub, GitLab, Gitea).
- **100% Kostenlos & Unbeschränkt:** Unbegrenzte Repositories, unbegrenzte Sync-Paare, freie Hintergrund-Intervalle – komplett ohne In-App-Käufe oder Paywall.
- **Git Engine (Eclipse JGit):** Unterstützung für Klonen, Pull, Fetch, Commit, Push, Status-Erkennung und Diff-Anzeige.
- **Authentifizierung:** Unterstützung für HTTPS (Personal Access Token, Benutzername/Passwort) sowie SSH-Schlüssel (RSA & Ed25519).
- **Integrierter SSH Key Manager:** Schlüssel direkt in der App generieren, öffentliche Schlüssel mit einem Fingertipp für GitHub kopieren und bestehende private Schlüssel importieren.
- **Automatischer Hintergrund-Sync:** Zuverlässige Synchronisation per WorkManager mit wählbaren Intervallen (15m, 30m, 1h, 6h, etc.).
- **Quick Settings Kachel:** "Git Sync"-Kachel für die Android-Statusleiste für sofortige Synchronisation mit nur einem Fingertipp.
- **Konflikt-Erkennung & Diff Viewer:** Sichere Erkennung von Merge-Konflikten mit integriertem Diff-Betrachter und Auswahl für "Lokal behalten" oder "Remote behalten".
- **Modernes Jetpack Compose UI:** Material 3 Design mit dynamischer Farbunterstützung und Dark/Light Mode.
- **Vollständiger Gerätespeicherzugriff:** Zugriff auf alle internen Ordner und Obsidian-Tresore über Android Manage External Storage.
