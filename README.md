# Album

一个面向 Android 的本地图片、视频与壁纸管理器，使用 Kotlin 和 Jetpack Compose 构建。

Album 主要服务于希望直接管理本机媒体文件的用户：不上传媒体、不依赖云端相册，并提供比系统相册更完整的整理、编辑和播放入口。

## 功能

- 图片和视频按文件夹、相册、时间轴浏览
- 收藏、搜索、排序、批量选择与批量操作
- 使用 MediaStore 和 Storage Access Framework 移动、复制和删除媒体
- 回收站、重复图片检测和文件夹排除
- 图片裁剪、旋转、调整和保存副本
- 视频播放、播放进度记忆、后台播放和画中画
- 静态/动态壁纸队列、裁剪、轮播和低功耗模式
- Pixiv 登录后按作品、画师和 Tag 归档下载的图片
- 中文和英文界面

## 截图

| 相册 | 编辑器 | 视频 |
| --- | --- | --- |
| ![相册首页](final-home.png) | ![图片编辑器](album-editor.png) | ![视频列表](video-list.png) |

## 下载

- [GitHub Releases](https://github.com/Tnomlav/ALbum/releases)
- 最新已发布版本：[v1.1.50 APK](https://github.com/Tnomlav/ALbum/releases/download/v1.1.50/Album-v1.1.50.apk)
- 当前构建版本：`1.1.62`

仓库内本地签名构建 `app/release/app-release.apk` 为 `1.1.62`（arm64-v8a 分包，其余架构见构建输出目录），仅用于交付前验证，不代表已创建 GitHub Release。其 SHA-256 为：

```text
EF2AD641D893E663CA2AAB12FE680FD8C5E6D0E22E85A77581810A7C65BA8431
```

安装前请确认文件来自本仓库，并通过 SHA-256 校验下载完整性。正式发布版本和发布说明会优先放在 GitHub Releases。

## 要求

- Android 7.0（API 24）或更高版本
- Android Studio，或 JDK 17 与项目自带 Gradle Wrapper
- 图片/视频访问权限；移动、复制和 Pixiv 归档功能可能需要额外的文件夹访问授权

应用只在执行对应功能时请求权限。媒体库默认读取设备上的图片和视频；Pixiv 归档功能会访问 Pixiv 网络服务并使用应用内登录状态。

## 构建

```bash
git clone https://github.com/Tnomlav/ALbum.git
cd ALbum
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

生成的 Debug APK 位于 `app/build/outputs/apk/debug/`。Release 签名配置通过 Gradle 属性提供，不应把 keystore 或密码提交到仓库：

```text
ALBUM_STORE_FILE
ALBUM_STORE_PASSWORD
ALBUM_KEY_ALIAS
ALBUM_KEY_PASSWORD
```

构建默认按 ABI 拆分，因为 AVI 播放使用的 LibVLC 每个架构约 60 MB 原生库。每个设备只需要安装自己架构的包：

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk      # 现代手机
app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk    # 32 位 ARM 设备
app/build/outputs/apk/debug/app-x86_64-debug.apk         # 模拟器
```

用 Android Studio 直接运行时会自动选择匹配的架构。手动 `adb install` 时请选择与设备 CPU 对应的 APK。需要单个全架构包时加上 `-PalbumUniversalApk=true`（体积约为单架构包的 3–4 倍）。压缩原生库会增加 Gradle 打包内存占用，`gradle.properties` 已把堆上限调到 4 GB。

## 发布

版本号保存在 `version.properties`。发布前请更新 `CHANGELOG.md`，构建签名 APK，并为同名版本创建 Git tag 和 GitHub Release。应用的更新检查地址由 `ALBUM_UPDATE_URL` 配置，生产环境建议指向 Release 资产，而不是直接依赖 `main` 分支文件。

## 开发素材

`dev-assets/` 中的图片和视频仅用于本地 UI、媒体扫描和播放测试。导入模拟器的命令见：

```powershell
.\scripts\import-sample-videos.ps1
```

## 隐私与第三方服务

Album 不提供云端媒体同步。Pixiv 归档是可选功能，会连接 Pixiv 以读取公开作品信息；请遵守 Pixiv 的服务条款、版权要求和所在地法律。项目不应提交真实账号 Cookie、个人媒体或私有密钥。

## 贡献

欢迎提交 Issue 和 Pull Request。提交前请先运行 `./gradlew lintDebug testDebugUnitTest assembleDebug`，并在行为或界面变化时附上复现步骤、设备版本和截图。

## License

本项目使用 [MIT License](LICENSE)。
