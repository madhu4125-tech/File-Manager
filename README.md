# File Manager (Kotlin + Jetpack Compose)

Fully native Android file manager. No WebView, no Capacitor, no Node.

## Features
- Browse internal storage and SD card, breadcrumbs, list or grid view, sort, hidden files, Quick access (plus starred folders)
- Long-press to select; copy, move, rename, delete, new folder, new text file
- Zip and unzip (zip-slip protected)
- Search by name (this folder or all storage), cancelable
- Categories: images, videos, audio, documents, archives, APKs, downloads
- Preview: image gallery with pinch-zoom, double-tap zoom and swipe; video and audio player; text/code editor
- Recycle bin: deletes are restorable for 30 days (toggle in the menu)
- Duplicate finder: groups identical files (size, then SHA-256), "select extras", delete to free space
- Storage analyzer: usage by type, biggest folders (drill down), largest files
- Open with / Share through any Android app
- Dark mode

## Build with GitHub (no Android Studio)
1. Create a repo and upload everything in this folder (keep `.github/workflows/build-apk.yml`).
2. Actions tab > wait for the run, then download the artifact `file-manager-kotlin-apk` at the bottom of the run page.
3. Unzip and install `app-debug.apk`. On first launch tap Grant access and switch on "All files access".

Note: debug builds are signed with a throwaway key, so each new build must be installed after
uninstalling the previous one (your files are not affected).

## Layout
- `FileOps.kt` file engine (list, copy, move, zip, search, analyze, recycle bin, duplicate finder, open, share)
- `FmViewModel.kt` state and actions · `Models.kt` data classes · `Screens.kt` UI · `Overlays.kt` dialogs and viewers
- Versions: AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00, Gradle 8.7, JDK 17, minSdk 26, targetSdk 34
