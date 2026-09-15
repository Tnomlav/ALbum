# Album Changelog

## v1.1.83 - 2026-09-16 (local signed build)

- The picture a static wallpaper queue is showing is now also handed to the system as an ordinary wallpaper (before the live wallpaper is bound), so a package replacement that drops the live wallpaper leaves the user's image on screen instead of the stock wallpaper. The still image is only written when no Album live wallpaper is bound, because setting one replaces the other. Video queues get the first frame of the clip as the same fallback.
- The applied queue is now backed up on launch when the backup file is missing, so a queue applied before the backup existed can still be restored after an update.
- A video's length is shown again in the player for MPEG program streams: the MPEG-1 extractor reads the clock reference of the first and the last pack header, reports the duration and provides a constant bit rate seek map so the progress bar works too. Verified against an independent SCR calculation (5.365 s) and on the user's own 64 minute `.mpg`.
- The page action button sits in the same place on every page: the play button of the slideshow queue and the apply button of the wallpaper page now share one trailing group with a fixed gap and button size.
- Deleting inside a search page keeps the page in place: the scroll restore anchors on the item (folder name or media URI) that was on screen instead of a stored index, so a shorter list no longer clamps it to the bottom. Closing a folder opened from a search anchors the same way.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `E7DB728F990723BF466C8B8BC36BBA7BAF5AF90C26977A81A77CD5BE050BFCE3`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease` and the `MainPlayerContainerTest` instrumentation test passed. On the test phone the user's `.mpg` now shows `64:34` and a DivX `.avi` shows `30:33`, both in the main player.

## v1.1.79 - 2026-09-16 (local signed build)

- MPG/AVI now really reach the main player. The player no longer probes the platform extractor before starting (that probe sent AVI/MPEG files straight to the compatible player whenever the platform reported a codec the codec list did not offer back), and the "no playable video" guard was fixed: it used `Tracks.Group.isTrackSupported`, which only turns true once the renderer has already handled the track, so a perfectly playable AVI was judged undecodable at the first track report.
- MPEG-4 Part 2 video (DivX/Xvid/FMP4 in AVI) now prefers the software decoder, because AVI carries no codec specific data and several hardware decoders refuse to start without it.
- Decoders hidden behind the vendor "special-codec" feature are now offered to the player when the regular list is empty. On the test phone the MPEG-2 decoder is marked that way, so MPEG-1/2 `.mpg` files used to be reported as "no decoder" and fell back; they now play in the main player.
- The slideshow queue has its play button back. The top bar only rendered its action capsule on pages with a search field, and the slideshow queue hides the search field, so the button never appeared.
- Tapping the bottom-bar icon of the page you are already on scrolls to the top again: the folder grid never received the scroll token, and the Timeline did not forward it to either of its grids.
- The search page starts at the top instead of somewhere in the middle of the clamped list, and leaving it clears the field (the entered query is no longer remembered) and returns to the position the page had before searching. Deleting inside the search page re-anchors the page instead of dropping to the bottom.
- Tools drag follows the finger: the drag state is bound to the entry (`key`) instead of the slot, and the row height is measured instead of the previous hard-coded 72 px, which was roughly a third of a real row.
- The P page loads on first entry (the reload effect now also keys on the selected tab) and pull-to-refresh only re-reads MediaStore, while the full storage walk stays on the "扫描刷新" menu entry, so pulling no longer waits for a complete DCIM/Pictures/Movies/Downloads scan.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `B434613D95B9AF8792027A4E511CAD8AD6FB7A7F4EDC3915618D79C728803E92`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease` and the `MainPlayerContainerTest` instrumentation test passed. On the test phone an MPEG-1 `.mpg` from the user's library now opens in the main player, while DivX AVIs are still handed over because the device's only MPEG-4 decoder fails on them at decode time.

## v1.1.69 - 2026-09-15 (local signed build)

- AVI and MPEG program streams are back in the main player. Media3's own AVI extractor and MPEG-2 program stream extractor handle `.avi`, `.divx`, `.xvid` and MPEG-2 `.mpg`/`.mpeg`/`.vob` files, and a new extractor parses MPEG-1 program streams, whose pack and PES headers (0xFF stuffing, buffer scale and size, `0010` clock reference and PTS/DTS) Media3 cannot read at all.
- Because of that, `.mpg`/`.avi` files no longer fall through to "audio only on a black screen", and the special-case routing that sent them straight to the compatible player is gone. Files whose codec the device really cannot decode are detected from the reported tracks and hand over to the compatible player by themselves.
- The P page no longer waits for the whole SAF tree before showing anything: the walk streams partial snapshots to the grid every 250 ms and archived artist folders are read four at a time, so folders appear as they are found.
- The tag index is only rebuilt when a full walk finishes, so the partial snapshots do not restart the (expensive) tag index build while the page is still filling in.
- New instrumentation test `MainPlayerContainerTest` opens an AVI and an MPEG program stream with the exact player configuration the app builds and asserts the expected tracks are demuxed. It skips itself when the sample files are not pushed to the device (the commands are in the test's KDoc).
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `E52ABB141D307240D6D23F6ADE874B33A8EE4CB1EE5F508883F3CBA43C8D1CED`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. On the emulator `MainPlayerContainerTest` demuxes the AVI (Xvid, `video/mp4v-es`) and both an MPEG-2 and an MPEG-1 program stream (`video/mpeg2` + `audio/mpeg-L2`); the emulator has no MPEG-2 decoder, so MPEG playback itself still needs a real device to confirm. The P page loading change only ran through compilation and review - confirming it needs a device with a Pixiv archive.

## v1.1.67 - 2026-09-15 (local signed build)

- The photo/video switch is narrower, and the Albums page now reads 图片 / 视频.
- Tapping the bottom-bar icon of the page you are already on scrolls that page back to the top.
- The Tools reorder drag follows the finger (the offset is carried across swaps instead of being reset).
- The slideshow queue uses the wallpaper page's capsule action button, labelled 播放.
- Preview to full screen now fades the background and the controls together.
- Closing a folder keeps the search results at the scroll position they had before the folder was opened, and a search the user already left is no longer restored when a folder closes.
- The cleanup entry was removed from Settings (it stays in the Tools page).
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `D6784974C7314B647138251C571CBD016943BF3870051D0B86C02ED88BC298A5`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. The Media3 AVI/MPEG-PS demuxers (item 9) and the remaining Pixiv folder-load and search scroll refinements are still outstanding.

## v1.1.66 - 2026-09-14 (local signed build)

- The main media library now shows the previous snapshot immediately (same approach as the Pixiv page) and replaces it with the fresh MediaStore scan in the background.
- The photo/video switch on the Albums and Timeline pages is now a single prominent control: a switch mark plus the current state in the theme colour, placed at the top-left where the title used to be.
- The Tools page opens the Pixiv page without adding a Pixiv tab to the bottom bar.
- Menus no longer start a slideshow directly: the Slideshow entry adds the selection to the slideshow queue.
- The slideshow queue page gained the wallpaper-manager style menu (columns, layout, sort) and its action plays the queue through the normal image viewer: swiping, preview controls and editing all work, previewing pauses the slideshow and returning to full screen resumes it.
- Adding a new setting, "long-press to reorder Tools components", which enables drag reordering of the Tools page entries.
- Images: full screen now paints the background black (white stays for the preview state), and swiping to another photo no longer leaves full screen - only a single tap toggles the preview.
- Swiping in the viewer keeps the page underneath on the photo/video being viewed, so closing returns to that item instead of the one first opened.
- Restoring a search after leaving a folder now applies the query in the same frame, removing the flash of the unfiltered list.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `CFB1E8FB70843F6E4DC65F4D49972DF38519DAF2010DAB99E07B384E61AD4B21`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. Items 2 (Media3 AVI/MPEG-PS demuxers), 8 (search deletion scroll) and the multi-select/viewer items still need on-device verification.

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
