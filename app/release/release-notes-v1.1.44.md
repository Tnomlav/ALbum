# Album v1.1.44

## Changes

- Reworked Pixiv login loading with an isolated WebView process/data directory, automatic fallback login entry, renderer recovery, blank-page timeout recovery, and Cookie handoff back to the archive process.
- Fixed rapid video next/previous navigation by preserving the requested media index and avoiding repeated player preparation.
- Prevented competing Pixiv reload jobs and stale refresh results from overwriting newer page data.
- Preserved folder and timeline order when entering multi-select, including timeline date headers and spacing.
- Updated dynamic wallpaper video scaling to preserve the source aspect ratio while cropping to fill the screen without black bars.
- Updated the custom mini-player to autoplay independently of background playback, use the video's aspect ratio, and provide dedicated playback controls.

## Verification

- Version: `1.1.44 (122)`
- APK SHA-256: `2AC6E9AA1FA39AFA4E8989149779615C0F0F196B6CBE85152535176B23B39B1B`
- APK Signature Scheme v2: verified, 1 signer.
- `:app:compileDebugKotlin`: passed.
- `:app:testDebugUnitTest`: passed.
- `:app:assembleRelease`: passed.
- Pixiv login requires real-device verification; no device was connected during export.
