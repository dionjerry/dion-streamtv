# DiON streamTV

Personal Android/Google TV browser and streaming library built with React,
Vite, Capacitor, and native Android TV integrations.

## Features

- TV remote pointer and D-pad focus modes
- Per-site desktop/mobile view and zoom
- App-scoped ad and popup controls
- Fullscreen TV media controls and playback resume
- Movie, series, anime, and recently visited libraries
- StreamIMDB, AnimePahe, Dulo, Google, and custom web launchers
- Android TV Watch Next/Continue Watching publishing
- Visual tabs, browsing history, diagnostics, and crash recovery

The app includes an app-scoped request blocker. Use the pointer to click the
`AD BLOCK ON/OFF` pill in the top-right corner, or press the remote's Menu/Settings
key. The preference is remembered and changing it reloads the current page.

Movie and TV episode URLs are saved to Explore as soon as they are visited.
Playback progress is then attached to that canonical page URL, allowing the app
to reopen the exact movie or episode and seek when its HTML5 player becomes ready.
Continue Watching keeps the complete local viewing history without an item limit.
Explore groups films inside a Movies folder and creates a separate folder for
each TV series, with that show's saved episodes and progress inside it.

The Explore library includes poster cards, automatic watched state at 90%,
manual watched/unwatched controls, detected Next Episode links, item/folder/all
history removal with confirmation for bulk actions, plus Home and Refresh controls.

Top-level external links are blocked by default so advertising redirects cannot
silently open Chrome. The `LINKS BLOCKED / LINKS ASK` control lets the TV owner
enable per-site confirmation when an external link is genuinely wanted.

## Build

```sh
npm install
npm run android:sync
cd android && ./gradlew assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## GitHub builds

Every push to `main` builds a debug APK. Open the repository's **Actions** tab,
select the latest successful build, and download the `DiON-streamTV-debug` artifact.

Only use the app to access content you are legally authorized to view. Site availability and playback remain controlled by the target website and its media providers.
