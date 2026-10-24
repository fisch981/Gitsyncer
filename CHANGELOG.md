# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden in dieser Datei dokumentiert.
Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.0.0/),
und dieses Projekt hält sich an [Semantic Versioning](https://semver.org/lang/de/).

---

## [1.1.2] - 2026-10-24

### Geändert / Hinzugefügt
- Branch-Zuordnung korrigiert (expliziter targetBranch Pull/Push ohne Master-Fallback), Selbstreparatur bei verwaisten Klon-Resten und Reset-Funktion

## [1.1.1] - 2026-10-24

### Geändert / Hinzugefügt
- JGit Android-Speicheroptimierung (LargeHeap, WindowCacheConfig, mmap=false) und Shallow-Clone zur Vermeidung von Haengern bei grossen Repositories wie FolderGallery

## [1.1.0] - 2026-10-24

### Geändert / Hinzugefügt
- Zentrale GitHub-Zugangsdatenverwaltung, automatisches Auslesen aller Repositories und konfigurierbares Standard-Basisverzeichnis mit Unterordnern

## [1.0.1] - 2026-10-24

### Geändert / Hinzugefügt
- Direkte 1-Klick GitHub Token-Generierung im Browser und Clipboard-Einfuegen hinzugefuegt

## [1.0.0] - 2026-10-24

### Hinzugefügt
- **Vollwertige Git-Synchronisation für Android:** Nahtlose Synchronisation lokaler Verzeichnisse (z. B. Obsidian Vaults, Notizen oder Quellcode) mit entfernten Git-Repositories (GitHub, GitLab, Gitea).
- **100% Kostenlos & Unbeschränkt:** Unbegrenzte Repositories, unbegrenzte Sync-Paare, freie Hintergrund-Intervalle – komplett ohne In-App-Käufe oder Paywall.
- **Git Engine (Eclipse JGit):** Unterstützung für Klonen, Pull, Fetch, Commit, Push, Status-Erkennung und Diff-Anzeige.
- **Authentifizierung:** Unterstützung für HTTPS (Personal Access Token, Benutzername/Passwort) sowie SSH-Schlüssel (RSA & Ed25519).
- **Integrierter SSH Key Manager:** Schlüssel direkt in der App generieren, öffentliche Schlüssel mit einem Fingertipp für GitHub kopieren und bestehende private Schlüssel importieren.
- **Automatischer Hintergrund-Sync:** Zuverlässige Synchronisation per WorkManager mit wählbaren Intervallen (15m, 30m, 1h, 6h, etc.).
- **Quick Settings Kachel:** "Git Sync"-Kachel für die Android-Statusleiste für sofortige Synchronisation mit nur einem Fingertipp.
- **Konflikt-Erkennung & Diff Viewer:** Sichere Erkennung von Merge-Konflikten mit integriertem Diff-Betrachter und Auswahl für "Lokal behalten" oder "Remote behalten".
- **Modernes Jetpack Compose UI:** Material 3 Design mit dynamischer Farbunterstützung und Dark/Light Mode.
- **Vollständiger Gerätespeicherzugriff:** Zugriff auf alle internen Ordner und Obsidian-Tresore über Android Manage External Storage.
