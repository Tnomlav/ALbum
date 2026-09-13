# Album Changelog

## v1.1.65 - 2026-09-13 (local signed build)

- Pixiv page opens instantly from a cached snapshot (items, folders, settings) and refreshes in the background, so the slow SAF tree walk no longer blocks the page.
- The mini window is now the app's own floating window, drawn through the "display over other apps" permission: our buttons, drag to move, corner drag to resize, and no system picture-in-picture control layer. Picture-in-picture and the in-app window remain as fallbacks when the permission is not granted.
- The photo and video libraries are merged into one page with a large top-left Albums/Videos switch that replaces the title; the bottom-bar icon and label follow the selected library. The separate Videos tab is gone.
- The wallpaper manager entry was removed from the album/video page menus (it stays in the new Tools page).
- The Tools page no longer shows the favourite star and gained a Pixiv entry.
- The Tools slideshow entry opens a slideshow queue page (persisted like the wallpaper queue); images are added from the multi-select menu and the queue can be played or cleared.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `D7EDFBC16AA9FC2ECA7BB86C0523DBA0185E0A8821A43F814714205193387F65`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed; the release build was installed and launched on the emulator. The phone was disconnected, so on-device checks are still pending.

## v1.1.64 - 2026-09-13 (local signed build)

- Every multi-select now happens inside the list that is already on screen (media and folders), so positions never change and the current sort order is respected.
- Image and video paging follows the exact list the page shows (current sort, search and favourite filters) instead of a separately derived list.
- The picture-in-picture window keeps playing when it opens or closes, and its controls sit in one row along the bottom so the system's own PiP buttons and gesture layer cannot cover them. Leaving the window restores the normal "pause in background" behaviour.
- The player seek bar shows the archive slider's thumb/track separation (dark ring between white thumb and white track).
- LibVLC-based formats (MPG, AVI, …) use the same player layout as the main player (controls on the left, lock on the right, title bar, seek bar and orientation row).
- Pixiv tag search uses a precomputed tag index, so typing no longer rebuilds uri strings and tag lists for the whole archive.
- The album index is built off the main thread, so toggling the favourite filter no longer freezes the UI.
- New bottom-bar "Tools" page with entries for the Pixiv archive, wallpaper queue, file cleanup and slideshow playback.
- Setting a wallpaper from inside a folder returns to that folder instead of the home page.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `2C04E6A00EE7D93A84EB8FE7B67D3034953F712E8FC7DB57AF4F00347B90AD34`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. The phone was disconnected during this round, so on-device verification is still pending.

## v1.1.63 - 2026-09-13 (local signed build)

- Folder multi-select now happens inside the list that is already on screen (like media multi-select), so the Pixiv page keeps its scroll position and no second grid is built.
- The floating window no longer pauses playback: entering picture-in-picture counts as "keep playing" instead of a background pause, and the window controls sit below the system's own PiP buttons so both stay usable.
- The mini-window button uses the previous picture-in-picture icon again, and the orientation buttons use clear icons (follow gravity / stay landscape / stay portrait / match video ratio).
- The player seek bar draws the same separation band between thumb and track as the archive page slider (widened so it stays visible in white on black).
- MPG, AVI and other LibVLC-routed videos use the same player interface as the main player: title bar with speed control, favourite and menu (share / set as wallpaper / info), seek bar with time labels, orientation button and mini window, plus tap, double-tap seek zones, long-press 2x and the brightness / volume / seek drag gestures with their HUD.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `16BFCF3547D58F7459E50A38B7EC4E2231188FDD28B85D2D6BD6AAAD56450466`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. The phone was disconnected before this build could be installed, so the on-device pass for these six items is still pending.

## v1.1.62 - 2026-09-13 (local signed build)

- Selection mode no longer builds a second grid: the page the user came from stays mounted, so the layout never switches to adaptive, the scroll position is kept, and leaving selection no longer flashes. Folder multi-select still uses the folder grid.
- The layout setting is shared by the timeline and the folder pages, so choosing grid once means grid everywhere.
- The floating window is the system picture-in-picture again, but it now renders the app's own window controls (restore, close, rewind, pause, fast-forward) instead of the full player UI, and it keeps floating above other apps. The separate picture-in-picture button is gone; the single mini-window button and auto-mini both use it, with the in-app window only as a fallback for devices without PiP.
- Player orientation "follow gravity" now lets the platform handle the sensor instead of mapping raw angles to explicit landscape/portrait sides, which inverted the direction in landscape.
- The player seek bar uses the archive-page slider geometry without the vertical squash that distorted the thumb.
- Empty and non-media folders appear while the storage walk is still running instead of only after it finishes, so searching for them is no longer much slower than for folders already known from the media scan.
- The applied wallpaper queue is copied outside the app's private storage and restored (with a single system confirmation) after an update or reinstall cleared the live wallpaper.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `EF2AD641D893E663CA2AAB12FE680FD8C5E6D0E22E85A77581810A7C65BA8431`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed; the signed arm64 build was installed on the connected phone, where grid layout keeps square tiles in selection mode and the mini-window button enters picture-in-picture.

## v1.1.59 - 2026-09-12 (local signed build)

- The mini window replaces the system picture-in-picture entry: the player now has a single "mini window" button, auto-mini keeps playing in the app's own floating window, and that window floats over the album pages with corner drag, centre drag, restore, close, rewind, pause and fast-forward.
- Player screen orientation now has four modes: follow the sensor, always landscape, always portrait, and match the video ratio.
- Both the player seek bar and the wallpaper volume slider use the Pixiv archive page's rounded line slider (thick rounded track with a white-ringed thumb); the player keeps white instead of the theme accent.
- AVI/MPG/other LibVLC-routed videos use the same player chrome as the main player (title bar, centre controls, seek bar, orientation button and mini window) instead of a reduced layout.
- The wallpaper manager folder view shows cover thumbnails with the folder name and item count underneath, like the album page, and the layout sheet remembers that the current view is the folder view.
- Leaving selection mode no longer flashes: the grids are created directly at the stored scroll position, and the timeline keeps the shared position when returning.
- Fixing the decoder probe so the Media3 player is not torn down and recreated keeps the user's brightness/volume adjustments when moving to the next video.
- The timeline player playlist now follows the timeline's own display order for "next video".
- Back navigation: one system back returns from any non-home page to the home tab, the first back on the home page shows the exit prompt and the next one exits.
- Pixiv login retries the "pixiv ID / email" switch for up to 14 seconds so the direct ID/password form is opened instead of the third-party provider list.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `A8713D5986165149CCF27C94EE3E876A6B31D635B818019A30019BE54C5B27D1`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed; the signed arm64 build was installed on the connected phone (vivo V2254A) and the one-press back-to-home behaviour was confirmed there. Items that need a real media library (multi-select layout, wallpaper folder view, AVI/MPG playback, Pixiv login) still need a device pass with the user's own files.

## v1.1.58 - 2026-09-10 (local signed build)

- Fixed selection mode changing the thumbnail size and jumping the list: the selection grids now use the same padding, spacing, adaptive layout and starting scroll position as the page the user came from.
- The wallpaper manager layout sheet now really switches between the media view and the folder view, and the folder view lists the queued folders with counts and opens them.
- Static and live wallpapers only advance when "switch on return to home" is enabled, and re-applying an unchanged queue keeps the current image instead of restarting at the first one.
- The static wallpaper option previously named "自动适配图片占用" is now "低功耗模式".
- Video settings rename: "亮度：空白：音量 触控占比" and "快退：暂停：快进 触控占比".
- The mini window keeps playing: it now resizes the existing player surface instead of creating a second one, so playback is never interrupted; corner drag, centre drag, restore, close, rewind, pause and fast-forward stay in place.
- MPEG-PS (`.mpg`, `.mpeg`, `.m1v`, `.m2v`, `.vob`, …) and other containers the platform cannot demux are routed to LibVLC, and a new decoder probe plus ExoPlayer error fallback move unsupported codecs to LibVLC automatically instead of showing a black screen.
- Pixiv login now selects the pixiv ID / email + password tab and focuses the form, instead of leaving the user on the third-party provider list.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `245461876C484FB7387EFCD7CF127F1D9C849D3FA82175E7F91FF1E14806630C`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed; the signed x86_64 build was installed on an API 36 emulator and the AVI played through LibVLC (`00:01/00:02`). The emulator's own H.264 decoder is broken (`c2.goldfish.h264.decoder` fails to configure), so MP4 playback and the Pixiv web flow could not be verified here.

This file records release-level changes. Each exported release should have:

1. A version entry in this file.
2. A Git commit whose message starts with the version, for example `v1.1.20:`.
3. An annotated Git tag with the same version, for example `v1.1.20`.
4. The APK SHA-256 and verification status recorded in the entry when an APK is exported.

## v1.1.57 - 2026-09-10 (local signed build, pending release)

- Wallpaper settings now re-apply immediately: the running static/live wallpaper services listen for a settings change broadcast and repaint or reload instead of waiting for the next rotation.
- Static wallpapers fill the whole screen with the original aspect ratio (cropped, never letterboxed) and follow the launcher offset when "across screens" is selected.
- The wallpaper manager shows "Re-apply" when the displayed queue is already the active wallpaper.
- The wallpaper manager layout sheet now has two wheels (media ≈ timeline / folder ≈ album page × grid / adaptive) and adaptive layout uses the timeline's staggered presentation.
- Long pressing a media or album tile enters multi-select immediately, and dragging without releasing keeps batch selecting; the selection bar appears with the first long press.
- The in-player video settings mirror the Settings video section, and add the brightness/volume touch split (1:1, 1:1:1, 1:2:1) plus the seek/pause touch split (1:1:1, 1:2:1, 1:0:1).
- Added an "auto mini window" video option (off by default): backgrounding the app during playback keeps playing in the system picture-in-picture window.
- Rebuilt the in-app mini window: drag corners to resize, drag the middle to move, top-left restores full screen, top-right closes, and the centre row holds rewind / pause / fast-forward.
- AVI and other containers ExoPlayer cannot demux now play through a bundled LibVLC player (`org.videolan.android:libvlc-all`). Builds are split per ABI and native libraries are compressed, so a Release APK is 57–62 MB per ABI instead of 230 MB+ universal; the Gradle heap limit was raised to 4 GB to package the compressed libraries.
- Added R8 keep rules for `org.videolan.**` (release builds crashed in `JNI_OnLoad` without them) and route `content://` media to LibVLC through a file descriptor, because LibVLC cannot open MediaStore URIs as an MRL.
- App text keeps following the system font size (font scale is read from the system configuration and `fontScale` no longer restarts the activity).
- Added defensive handling for low-memory image conversion, thumbnail decoding, and editor loading paths.
- Moved rename, delete, cache-size, duplicate-scan, and transfer file work off the main thread.
- Added direct provider/file moves with permanent-delete fallback, conflict-safe naming, and stale Pixiv scan-state protection.
- Hardened API compatibility for navigation bar, media metadata, WebView renderer, and Media3 integrations.
- Added Android 11 package-visibility queries and expanded CI coverage to include `lintDebug`.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `4F19B4E28D09D9EBABEC2C889397C6FAB5FC0A8517209EBDB3300662EC26CD5C`
- Signature: APK Signature Scheme v2, 1 signer, certificate SHA-256 `062E93393B7BF2759E1D2B5D48FA0D1DA15F2BE0E6370F7DBAFF6DA50F36842F`. This keystore was created on 2026-09-10 and differs from the 1.1.56 certificate, so 1.1.57 cannot be installed over an existing 1.1.56 installation.
- Verification: `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleRelease` passed from a clean tree. The signed x86_64 build was installed on an API 36 emulator: the app launches, ExoPlayer plays MP4, and LibVLC plays AVI through the file-descriptor path without crashes. Real-device/API 24/28/29 and Pixiv login verification remain outstanding.

## v1.1.56 - 2026-09-04 (local signed build, pending release)

- APK: `app/release/app-release.apk`
- SHA-256: `92346A37696359C3B281D047594D2D381646CA65D5AB4763872DA7D31F2AD2D6`
- Signature: verified with APK Signature Scheme v2; 1 signer.

## v1.1.50 - 2026-09-01

- Fixed nested transfer destinations and unified shared-storage, MediaStore, and SAF path handling.
- Made folder navigation return one directory at a time and preserved image preview ordering.
- Preserved original modified dates during recycle-bin move and restore operations.
- APK: `app/release/app-release.apk`
- SHA-256: `C21E7DDD7B5308B185D590EB542FF630FA11B333927F931825564227676C30C3`
- Signature: verified with APK Signature Scheme v2; 1 signer.

## v1.1.44 - 2026-09-01

- Reworked Pixiv login loading with an isolated WebView process/data directory, automatic fallback login entry, renderer recovery, blank-page timeout recovery, and Cookie handoff back to the archive process.
- Fixed rapid video next/previous navigation in both player implementations by preserving the requested media index and avoiding repeated `prepare()` calls on an already prepared player.
- Prevented competing Pixiv reload jobs and stale refresh results from overwriting newer page data.
- Preserved the current folder/timeline order when entering multi-select, including timeline date headers and spacing; cancelled superseded editor carousel scroll jobs.
- Updated dynamic wallpaper video scaling to preserve the source aspect ratio while cropping to fill the screen without black bars.
- Updated the custom mini-player to autoplay independently of background playback, use the video's aspect ratio, and expose dedicated fullscreen, close, rewind, play/pause, and fast-forward controls.
- APK: `app/release/app-release.apk`
- SHA-256: `2AC6E9AA1FA39AFA4E8989149779615C0F0F196B6CBE85152535176B23B39B1B`
- Signature: verified with APK Signature Scheme v2; 1 signer.
- Verification: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleRelease` passed. Pixiv login still requires real-device verification.

## v1.1.21 - 2026-08-27

- Changed Move to use native MediaStore/SAF move operations where supported, avoiding duplicate copies and unnecessary source-delete confirmation.
- Made the Move/Copy destination page close immediately after confirmation while transfer work continues in the background.
- Improved non-media folder search with a persistent local index, background refresh, cache freshness window, and stale-task protection.
- Continued the recent archive, Pixiv tag, P-page reload, web access, editor control, player gesture, thumbnail, and selection-flow fixes.
- APK: `app/release/app-release.apk`
- SHA-256: `DB5B77CC0F7BD5B1A71745A5055F2C79D980A23429C674A6171CD25065007909`
- Signature: verified with APK Signature Scheme v2; 1 signer.

## v1.1.22 - 2026-08-27

- Removed archive-page scan results after a Pixiv archive move succeeds; copy operations keep their scan results.
- Removed archive records after manual Move completes, including SAF/MediaStore deletion confirmation paths.
- Reset the archive page to its initial state when all scan results have been cleared.
- Fixed batch archive cleanup so previously archived records are not removed accidentally.
- Included the recent duration alignment, editor carousel snapping, video dialog transparency, and decoded Chinese path display fixes.
- APK: `app/release/app-release.apk`
- SHA-256: `4E14E6828B5021CB81522CB944D69FD11C3D2E4CE22C08AED2012B6875677674`
- Signature: verified with APK Signature Scheme v2; 1 signer.

## v1.1.23 - 2026-08-27

- Made multi-select folder names use the same constrained ellipsis layout as the normal thumbnail view.
- Fixed Back in the Pixiv archive page to leave multi-select mode before closing the page.
- Improved global Move to discover persisted SAF source trees and prefer provider-native document moves, avoiding unintended copy behavior.
- APK: `app/release/app-release.apk`
- SHA-256: `4ED4C7A99A0F7A232D4564B1B9E41A69CD36DF9D7DB617F336F3686B6DCBFA5C`
- Signature: verified with APK Signature Scheme v2; 1 signer.

## v1.1.31 - 2026-08-28

- Fixed archive multi-select so the long-pressed image and drag start image are selected reliably.
- Made long-press selection idempotent when list and thumbnail gesture handlers receive the same pointer sequence.
- Improved direct global Move path resolution for Pictures, Movies, DCIM, and Downloads instead of unnecessarily falling back to copy behavior.
- Restored the wallpaper manager top-bar inset and preserved its selection ordering during multi-select.
- APK: `app/release/app-release.apk`
- SHA-256: `50FFFFB7E032D1EEC9D532B7356538BC3CA887DC01360BC86DA4E5C96ECC3F77`
- Signature: verified with APK Signature Scheme v2; 1 signer.
