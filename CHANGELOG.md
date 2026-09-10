# Album Changelog

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
