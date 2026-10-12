# Gallery Pro (Android, Kotlin + Jetpack Compose)

## APK GitHub se banane ke steps
1. github.com par naya repository banao (e.g. `GalleryPro`).
2. Is folder ke andar ki saari files upload karo. `.github` folder zaroor upload karna
   (phone se hidden folder na dikhe to computer se "Upload files" use karo ya zip extract karke drag-drop karo).
3. Commit karo (branch `main`).
4. Repository ke **Actions** tab me "Build APK" chalega (5-8 min). Green tick ka wait karo.
   Agar khud start karna ho: Actions -> Build APK -> Run workflow.
5. Us run ko kholo -> neeche **Artifacts** -> `GalleryPro-debug-apk` download karo -> zip extract karo -> `app-debug.apk` install karo.

## Features
- Photos + videos grid (date wise), Albums, Favorites, Search, filters, sorting, grid size, Light/Dark theme
- Full-screen viewer: swipe, pinch zoom, slideshow, video player, details (EXIF, GPS)
- Multi-select: share, favorite, delete, hide in vault
- Private Vault: PIN + fingerprint, AES-256 encrypted files (Android Keystore), screenshots blocked while open,
  auto-lock on leaving the app, 5 wrong PINs = temporary lock, restore to gallery anytime
- Photo Editor: rotate, flip, crop (1:1, 4:3, 3:4, 16:9, 9:16), 8 filters, brightness/contrast/saturation, saves as a copy
- Trash (Android 11+): restore or delete forever
