# BANKAI — Personal Anime Library

BANKAI is an offline-first Android app for organising your own anime collection and personal video files.

## Features
- Add, edit and delete anime entries.
- Import poster artwork from the device or use a poster URL.
- Create custom season names and generate episode lists by count.
- Add and remove individual episodes.
- Mark episodes watched or unwatched and view progress.
- Add or remove anime from the Weekly list.
- Import video files into BANKAI's private app storage. Removing the original Gallery copy does not remove the imported copy.
- Play imported episodes inside BANKAI with AndroidX Media3.
- Open an imported episode with an installed external player such as VLC or MX Player.
- Local library data and imported media work offline. Poster URLs need an internet connection.

## Build an APK
1. Open the repository Actions tab.
2. Run the Build BANKAI APK workflow (or push a commit to main).
3. Download the BANKAI-debug-apk artifact from the completed workflow run.
4. Install app-debug.apk on your Android device.

The debug APK is intended for personal testing and sideloading. A release build should be signed with a private keystore before any wider distribution.

## Storage note
Imported posters and episode videos are copied into BANKAI's app-private storage. Android removes this data when BANKAI is uninstalled or its app data is cleared. Export/backup is not included in this build.

## Copyright
BANKAI does not bundle anime episodes or copyrighted posters. Add media that you own or are authorised to use.
