# Video development samples

These short MP4 files are intended for local UI and playback testing:

- `sample_plane_tracking.mp4`: short camera-motion footage
- `sample_vertical_tracking.mp4`: a second tracking clip for list testing
- `sample_navigation.mp4`: a longer navigation clip for playback and multi-select testing

Run the import script from the project root while an emulator or Android device is connected:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import-sample-videos.ps1
```

The script copies every MP4 in this directory to
`/sdcard/Movies/AlbumSamples/`, imports the app-folder image samples, and asks
Android's media scanner to index them.
The files then appear in the app's Videos tab after media permission is granted
and the library is refreshed.

The samples are copied from the Android Emulator resources installed with the
local Android SDK and are for development use only.
