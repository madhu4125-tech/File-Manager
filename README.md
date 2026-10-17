# File Manager (Kotlin + Jetpack Compose)

Fully native Android file manager. No WebView, no Capacitor, no Node.

## Features
- Browse internal storage and SD card, breadcrumbs, list or grid view, sort, hidden files, Quick access (plus starred folders)
- Long-press to select; copy, move, rename, delete, new folder, new text file
- Zip and unzip (zip-slip protected)
- Search by name (this folder or all storage), cancelable
- Categories: images, videos, audio, documents, archives, APKs, downloads
- Preview: swipeable image gallery, video and audio player, text/code viewer and editor
- Open with / Share through any Android app
- Storage analyzer: usage by type, biggest folders (tap to drill down), largest files with delete
- Dark mode

## Build the APK with GitHub (no Android Studio needed)
1. Create a new repo, e.g. `File-Manager-Kotlin`.
2. Upload everything in this folder. Keep `.github/workflows/build-apk.yml` at that exact path
   (if drag and drop skips the dot-folder, use Add file > Create new file and type the path).
3. Open the Actions tab and wait for "Build File Manager APK (Kotlin)" (about 5 to 8 minutes the first time).
4. Download the artifact `file-manager-kotlin-apk`, unzip, install `app-debug.apk`.
5. On first launch tap Grant access and switch on "All files access".

If the build fails, open the failed step in Actions and copy the red error lines.

## Layout
- `app/src/main/java/com/madhu/filemanager/`
  - `FileOps.kt`      file engine: list, copy, move, zip, unzip, search, analyze, open, share
  - `FmViewModel.kt`  all state and actions
  - `Models.kt`       data classes, categories, formatting helpers
  - `Screens.kt`      top/bottom bars, home, file list and grid, categories, storage analyzer
  - `Overlays.kt`     dialogs and the image, media and text viewers
  - `Theme.kt`        light and dark palettes
  - `MainActivity.kt` permission flow and back handling
- Versions: AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00, Gradle 8.7, JDK 17, minSdk 26, targetSdk 34
