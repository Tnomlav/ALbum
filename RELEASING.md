# 发布流程

`version.properties` 是版本号的唯一来源。不要在 `README.md`、`app/release/` 或
`CHANGELOG.md` 里单独维护版本号。

1. **准备版本**：编辑 `version.properties`
   （`VERSION_CODE`、`VERSION_MAJOR`、`VERSION_MINOR`、`VERSION_PATCH`）。
   一次发布只改这里。

2. **写发布说明**：在 `CHANGELOG.md` 顶部新增一节，内容对应用户能看到的变化。

3. **验证**：

   ```powershell
   .\gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleRelease
   ```

   `app/build/reports/lint-results-debug.html` 与
   `app/build/reports/tests/testDebugUnitTest/index.html` 是这次的证据。
   有真机时再跑一次 `connectedDebugAndroidTest`。

4. **生成更新清单**（必须和 APK 来自同一次构建）：

   ```powershell
   .\scripts\write-update-manifest.ps1 -Notes "这一版的变化"
   ```

   产物是 `app/release/album-update.json`，字段为
   `versionCode` / `versionName` / `downloadUrl` / `notes`。

5. **取签名包**：`app/build/outputs/apk/release/` 下按 ABI 拆分的
   `app-<abi>-release.apk` 是发布物。仓库根目录的 `app/release/`
   只是本地验证产物目录，不纳入版本控制。

6. **打标签并创建 Release**：标签名 `v<versionName>`（例如 `v1.2.1`），
   附上各 ABI 的 APK、`album-update.json` 和每个文件的 SHA-256。
   更新检查读取的是
   `https://github.com/Tnomlav/ALbum/releases/latest/download/album-update.json`，
   因此 Release 资产名必须是 `album-update.json`。

7. **发布后自检**：装上一个旧版本，在设置页点"应用版本"→"检查更新"，
   确认能看到新版本号和下载入口。

## 版本号只在前进一步之后更新

`app/build.gradle.kts` 里的 `packageRelease` 钩子会在签名 release 构建成功后
自动把 `VERSION_PATCH` 和 `VERSION_CODE` 加一。也就是说：

- 打出来的包用的是构建开始时的版本号；
- 构建成功后文件里保存的是"下一个"版本号。

如果中途失败，版本号不会被消耗。
