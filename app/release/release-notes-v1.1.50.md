# Album v1.1.50

## Changes

- Fixed nested transfer destinations by preserving the complete shared-storage path, including folders at the phone storage root such as `AI生成/AI生成1`.
- Removed the implicit `Pictures` destination fallback and made move, copy, folder creation, conflict checks, and SAF destinations use the same path.
- Made search and transfer folder navigation return one directory at a time, restoring the initial search page only after reaching the storage root.
- Kept image preview previous/next navigation aligned with the current folder and search sort order.
- Preserved the original modified date when an image is moved to the app recycle bin and restored.
- Included the Pixiv archive, login, tag, editor, player, wallpaper, and selection-flow updates accumulated since v1.1.44.

## Verification

- Version: `1.1.50 (128)`
- APK SHA-256: `C21E7DDD7B5308B185D590EB542FF630FA11B333927F931825564227676C30C3`
- APK Signature Scheme v2: verified, 1 signer.
- `:app:testDebugUnitTest`: passed.
- `:app:packageRelease`: passed.
