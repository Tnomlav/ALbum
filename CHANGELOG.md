# Album Changelog

This file records release-level changes. Each exported release should have:

1. A version entry in this file.
2. A Git commit whose message starts with the version, for example `v1.1.20:`.
3. An annotated Git tag with the same version, for example `v1.1.20`.
4. The APK SHA-256 and verification status recorded in the entry when an APK is exported.

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
