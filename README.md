# GitSyncer

**GitSyncer** (`de.fisch981.gitsyncer`) ist eine leistungsstarke, vollständig kostenlose Android-App zur nahtlosen Zwei-Wege-Synchronisation beliebiger lokaler Ordner (z. B. **Obsidian Vaults**, Markdown-Notizen oder Quellcode-Repositories) mit Remote-Git-Diensten wie **GitHub**, **GitLab** und **Gitea**.

Im Gegensatz zu herkömmlichen Apps aus dem Store sind in GitSyncer **alle Funktionen dauerhaft kostenlos und unbegrenzt freigeschaltet**: keine Paywalls für Multi-Repository-Support, keine Limits bei Hintergrund-Synchronisationen oder Sync-Intervallen.

---

## Features

- **Unbegrenzte Repositories:** Verwalte und synchronisiere beliebig viele Git-Repositories parallel auf deinem Smartphone.
- **Volle Git-Funktionalität (Eclipse JGit):**
  - Klonen, Pull, Fetch, Push, Commit und Status-Prüfung.
  - Erkennung ungespeicherter oder geänderter Dateien.
  - Integrierter Diff-Viewer mit farblicher Hervorhebung hinzugefügter und entfernter Zeilen.
  - Sichere Merge-Konflikt-Lösung ("Lokal behalten" vs. "Remote behalten").
- **Authentifizierung:**
  - **HTTPS:** Personal Access Tokens (PAT) oder Benutzername/Passwort.
  - **SSH:** Integrierter SSH-Schlüssel-Generator (RSA 2048/4096 und Ed25519) mit Ein-Klick-Kopieren des öffentlichen Schlüssels für GitHub/GitLab. Import bestehender Schlüssel möglich.
- **Hintergrund-Synchronisation:**
  - Zuverlässige Hintergrund-Synchronisation via Android `WorkManager` (15m, 30m, 1h, 6h, 12h, 24h).
  - Akkuschonend & netzwerküberwacht.
- **Quick Settings Kachel (Android Statusleiste):**
  - Schnelle Synchronisation aller Repositories mit einem Klick direkt aus den Android-Schnelleinstellungen.
- **Obsidian & Gerätespeicher-Kompatibilität:**
  - Vollständiger Dateizugriff auf interne Verzeichnisse (`/sdcard/Documents/...`) für Obsidian-Tresore.
- **Modernes Material 3 UI:**
  - Modernes, klares Interface mit Jetpack Compose, Dynamic Colors und Dark Mode.

---

## Build & Release

Das Projekt nutzt den standardisierten Gradle-Build-Workflow aus `AGENTS.md`:

```powershell
# Automatische Versionierung, Release-APK Erstellung und Git-Push:
.\push.ps1 -ChangeSummary "Feature-Beschreibung"
```

Die fertige, signierte Release-APK wird automatisch im Projekt-Hauptverzeichnis als `Gitsyncer-release.apk` abgelegt.
