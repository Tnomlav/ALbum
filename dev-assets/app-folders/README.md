# App-folder development samples

These images create realistic source albums for development and UI testing:

- `WeChat` imports to `Pictures/WeiXin` and appears as the `WeiXin` album.
- `QQ` imports to `Pictures/QQ` and appears as the `QQ` album.
- `Pixiv` imports to `Pictures/Pixiv` and appears as the `Pixiv` album.

Run the shared importer from the project root while a device or emulator is
connected:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-sample-videos.ps1
```

Android does not expose empty directories through `MediaStore`, so each sample
folder contains an image that the media scanner can index.

The Pixiv sample is copied from the local development machine's
`Downloads/pixiv` folder. WeChat and QQ currently use project screenshots
because no matching images were available under `Downloads`.
