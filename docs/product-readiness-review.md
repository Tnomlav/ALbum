# Album 产品成熟度全盘复盘（基线 v1.2.1）

> 评估时间：2026-09-16
> 评估对象：本仓库工作区（`version.properties` 已更新到 1.2.1）
> 评估方式：静态代码审计 + 构建/lint/单元测试实测 + 发布产物与流程核对

## 0. 结论

**功能已经"够用"，产品化还"不够稳"。**

Album 现在的功能面（浏览、编辑、播放、壁纸、Pixiv 归档、清理）已经超过大多数个人相册 App，日常自用是成立的。真正阻碍它成为"成熟可用产品"的，不是缺功能，而是六件事：

1. **发布与分发链路不可信**：更新清单陈旧、APK 入库、GitHub Release 落后本地 40 多个版本。
2. **质量证据链不足**：3 万行主代码只有 66 个单测（含 2 个模板测试），没有覆盖率门槛、没有 UI 测试、没有崩溃上报。
3. **国际化是"手写字典"架构**：无 `strings.xml` 资源，第三种语言基本无法加入。
4. **无障碍与大屏适配没有验证**：锁竖屏 + 固定 412×900 视口，TalkBack、平板、横屏体验未知。
5. **第三方合规缺失**：LibVLC（LGPLv2.1）与 10 款 OFL 字体随包分发，但仓库没有任何许可声明文件。
6. **用户数据不可迁移**：收藏以 URI 为键、回收站放在应用私有目录、备份全关，换机/卸载即丢。

### 成熟度总览

| 维度 | 评级 | 现状 | 主要缺口 |
| --- | --- | --- | --- |
| 功能完整度 | B | 浏览/编辑/播放/壁纸/Pixiv/清理均已落地 | 数据导出、接收系统分享、批量重命名 |
| 工程与架构 | C | 有 `data`/`ui`/`playback`/`wallpaper` 分层 | 单文件近 4000 行、无 ViewModel/DI、6 个 SharedPreferences、无数据迁移 |
| 质量与测试 | D | 66 单测 + 2 仪器测试，CI 只跑 debug | 无覆盖率门槛、无 Compose UI 测试、无真机矩阵、无崩溃上报 |
| 国际化 | D | 397 + 101 条手写映射 + 215 处内联三目 | 无字符串资源、无法扩展第三语言、无法交给译者 |
| 无障碍与适配 | D | 34 处 `contentDescription`，锁竖屏 | TalkBack/大屏/横屏未验证；Android 16 起固定方向被忽略 |
| 隐私与合规 | C | HTTPS 更新校验、默认关闭云备份、无埋点 | 无第三方许可声明、无仓库级隐私政策、`MANAGE_EXTERNAL_STORAGE` 上架风险 |
| 数据可靠性 | C | 回收站、库快照、壁纸队列备份 | 回收站在私有目录、收藏键为 URI、无导入导出 |
| 发布与分发 | D | 版本号/CHANGELOG/CI 构建齐全 | 更新清单陈旧、APK 入库、无发布自动化 |
| 性能与体积 | C | R8、按 ABI 分包、缩略图缓存与内存回收 | 字体占 60 MB、单架构包 63 MB、无启动/帧率实测 |
| 文档与协作 | B | README/CHANGELOG/SECURITY/Issue 模板齐全 | 无用户手册、无路线图与已知问题清单 |

## 1. 本次实测证据

在本机工作区执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

结果：`BUILD SUCCESSFUL in 1m 22s`（35 个任务，20 个执行）。

lint 报告（`app/build/reports/lint-results-debug.txt`）：**0 error / 133 warning / 1 hint**。其中 `UseKtx` 105 条属于风格建议，其余 28 条包含真实风险信号：

| 规则 | 数量 | 代表位置 | 含义 |
| --- | --- | --- | --- |
| `GradleDependency` / `NewerVersionAvailable` | 5 | `gradle/libs.versions.toml` | 依赖版本明显滞后（`core-ktx 1.10.1` → 1.16.0、`lifecycle 2.6.1` → 2.9.4、Kotlin 插件 2.2.10 → 2.4.20） |
| `DiscouragedApi` / `SourceLockedOrientationActivity` / `LockedOrientationActivity` | 3 | `MainActivity.kt:88`、`MediaViewer.kt:356`、`AndroidManifest.xml:105` | 固定竖屏，Android 16 起系统基本会忽略，大屏体验受损 |
| `ClickableViewAccessibility` | 2 | `OverlayMiniWindow.kt:106,112` | 悬浮小窗自定义触摸未调用 `performClick`，无障碍服务无法操作 |
| `FrequentlyChangingValue` | 3 | `CleanupScreen.kt:871-875` | 在组合中读取高频变化值，会造成多余重组 |
| `UnusedResources` | 2 | `res/layout/view_html_video_player.xml`、`res/mipmap-*/ic_launcher_round.webp` | 迁移残留的死资源 |
| `PictureInPictureIssue` | 1 | `AndroidManifest.xml:51` | 声明了画中画但未同时提供 `setSourceRectHint`，过渡体验不佳 |
| `LaunchActivityFromNotification` | 1 | `MediaPlaybackService.kt:122` | 通知直接拉起 Activity，后台/前台服务边界不规范 |
| `SetJavaScriptEnabled` | 1 | `PixivArchiveRepository.kt:981` | WebView 开启 JS（Pixiv 登录所需，但需明确防护边界） |

代码与产物规模：

| 项目 | 数值 |
| --- | --- |
| 主代码 | 70 个 Kotlin 文件 / 30,130 行 |
| 单元测试 | 18 个文件 / 624 行 / 66 个 `@Test`（含 2 个 Android Studio 模板测试） |
| 仪器测试 | 2 个文件 / 151 行（其中 1 个是模板测试） |
| Release APK（arm64-v8a） | 63,503,609 字节（约 60.6 MiB） |
| Debug APK（arm64-v8a） | 86,754,953 字节 |
| `res/font` 字体总量 | 60.42 MB（10 个 TTF，最大 `noto_serif_sc.ttf` 14.10 MB） |
| Git 仓库 | `size-pack` 244.70 MiB，另有 1.12 GiB 松散对象 |

## 2. 分维度复盘

### 2.1 发布与分发（最短板）

**现状与证据**

- `version.properties` 长期只做"构建后 +1"的机械累加，当前已到 `1.1.92`，而 README 仍写"当前构建版本：1.1.91"，`app/release/output-metadata.json` 还停留在 `versionCode=169 / 1.1.91`。同一个仓库里三个版本数字对不上。
- 应用内的更新检查地址是 `gradle.properties` 里的 `ALBUM_UPDATE_URL`，指向 **main 分支上的 `app/release/output-metadata.json`**。用户点"检查更新"读到的是构建产物式的清单，一旦它不是最新构建写出的，就会永远返回"已是最新版本"。
- README 记录的最新 GitHub Release 是 **v1.1.50**，本地已经迭代到 1.1.91+。也就是说已经积累了 40 多个未对外发布的版本，Release 通道事实上是断的。
- 仓库直接跟踪了 63 MB 的 `app/release/app-release.apk` 和 4 个 `.dm` 基线产物，其中 `app/release/baselineProfiles/baselineProfiles/{0,1}/` 是重复的嵌套目录。每次发版提交一次 APK，是仓库膨胀的主因。
- CI（`.github/workflows/android.yml`）只做 `lintDebug testDebugUnitTest assembleDebug assembleRelease` 并上传产物，没有 tag/Release 自动化，也没有依赖更新机器人。

**成熟产品要求**

单一版本事实源；发布由 CI 从 tag 生成 Release、上传 APK 与 SHA-256、写入更新清单；仓库不存二进制产物；更新清单与 Release 资产同源。

**影响**

用户无法可靠获知"有没有新版本"，维护者发版还要手工核对三个版本号——这是最容易在真实用户侧暴露"不成熟"的地方。

### 2.2 质量与测试证据

**现状与证据**

- 30,130 行主代码对 624 行测试（约 2% 行数比），66 个单测里有 2 个是模板测试（`ExampleUnitTest.addition_isCorrect`、`ExampleInstrumentedTest.useAppContext`）。
- 没有覆盖率插件（无 Kover/JaCoCo 配置），没有覆盖率门槛。
- Compose 层 **0 个 `@Preview`、0 个 `testTag`**，现有仪器测试只覆盖播放器容器，UI 行为没有自动化覆盖。
- 仪器测试不进 CI（无模拟器 job），只能靠人工接到真机后运行。
- 没有崩溃上报/错误聚合（无 Crashlytics、无自建上报），线上问题只能靠用户口述；release 构建的堆栈还会被 R8 混淆，且没有 mapping 上传通道。
- CHANGELOG 里反复出现"需要真机确认""只跑了编译与 review"（1.1.91、1.1.69、1.1.67 等条目），说明验证依赖一次性人工操作，没有可复现的回归清单。

**成熟产品要求**

关键路径（删除/移动/回收站/编辑保存/播放进度/壁纸应用）有自动化回归；有覆盖率下限；每次 PR 出 lint + 单测报告；有可复现的手工回归清单；有线上错误聚合与 mapping 上传。

### 2.3 国际化（架构级问题）

**现状与证据**

- `app/src/main/res/values/strings.xml` **只有 2 条字符串**（`app_name`、`live_wallpaper_description`），全部界面文案以中文硬编码在 Kotlin 里。
- 英文靠四套并存的手工机制：
  - `ui/AppLanguage.kt` 的 `APP_ENGLISH`：397 条映射；
  - `ui/screens/SettingsScreen.kt` 的 `settingsEnglish`：101 条映射；
  - 215 处 `if (isEnglish) "..." else "..."` 内联三目；
  - `WallpaperSettingsSheet` / `SlideshowSettingsSheet` 里的局部 `text(zh, en)` 辅助函数。
- `app_name` 是固定中文"相册"，英文系统上桌面图标名仍是中文。
- 文案通过字符串拼接生成（如 `appSeekText`、时长文本），没有复数形式与占位符机制。

**成熟产品要求**

所有文案走资源文件（含 `values-en`），用占位符/复数资源拼装；新增语言只需加一份资源；文案改动可被 lint 校验（`MissingTranslation`、`HardcodedText`）。

**影响**

当前状态下"加第三种语言"的成本等于重写 UI 层；英文也会因为漏改三目/映射而与中文不一致（历史上已经出现过漏项）。

### 2.4 无障碍与设备适配

**现状与证据**

- 图标语义的实际覆盖比初次统计更好：175 处 `Icon(` 调用里有 83 处用 `appText(...)` 传了说明、37 处用了具名 `contentDescription`，其余多为与文字标签并排的装饰图标（传 `null` 是正确的）。真正的空缺是 `stateDescription`/`heading` 等状态语义，以及从未做过 TalkBack 实测。
- lint 已报 `ClickableViewAccessibility`（悬浮小窗触摸不响应无障碍点击动作），本轮已修复。
- `MainActivity` 在 `onCreate` 里硬锁竖屏，`MediaViewer` 也锁方向；Android 16 起系统会忽略固定方向（lint `DiscouragedApi` 已提示），届时行为由系统决定，而不是由 App 设计决定。
- `PrototypeViewport` 把内容固定在 412×900 的设计坐标系里按比例缩放，平板、折叠屏、横屏只是"放大一个手机界面"，没有自适应布局（双栏、侧栏导航）。
- `supportsRtl="true"` 已声明，但没有 RTL 语言，也没有验证镜像布局。
- 字体缩放走 `LocalDensity` 的 `fontScale`，这一项做对了；但没有大字号下的布局溢出测试。

**成熟产品要求**

关键控件可被 TalkBack 完整操作；横竖屏/大屏有明确布局策略；无固定方向依赖；有无障碍扫描（Accessibility Scanner）与多种字体缩放的实机记录。

### 2.5 隐私、安全与合规

**做得好的部分（应保留）**

- 更新检查只接受 HTTPS、禁止 URL 携带 userInfo/fragment、限制响应体 256 KB。
- 无任何统计/广告 SDK，媒体不出设备。
- Auto Backup 与设备迁移都显式排除应用数据，避免把 Pixiv Cookie 与回收站内容上传云端。
- 仓库内没有硬编码密钥/Token；签名材料在 `.gitignore` 的 `key/` 下，未入库（但要提醒：密码明文存放在用户级 `~/.gradle/gradle.properties`，属于本机运维风险）。
- 危险操作有二次确认与回收站兜底。

**缺口**

1. **第三方许可声明缺失**。LibVLC（`libvlc-all`）是 LGPLv2.1 授权，10 款字体是 OFL 授权，Media3/AndroidX 是 Apache-2.0。仓库里只有 MIT 的 `LICENSE`，没有 `THIRD_PARTY_NOTICES`，应用内也没有"开源许可"页面。LGPL 分发要求随包提供许可证文本并说明可替换性，当前不满足。
2. **隐私政策只存在于设置页弹窗**，仓库里没有 `PRIVACY.md`，也没有可分享的链接页；分发页面需要它。
3. **`MANAGE_EXTERNAL_STORAGE` 与 `MANAGE_MEDIA`**：两者都是受控权限。Google Play 对 `MANAGE_EXTERNAL_STORAGE` 有严格适用范围审查，相册类应用通常不被接受；`MANAGE_MEDIA` 还要求应用是系统默认媒体管理应用。如果未来上架，需要能证明"没有更窄的替代方案"（SAF/MediaStore `createWriteRequest`）或改为可选引导。
4. **WebView 登录**：Pixiv 登录依赖 JS 与 Cookie，`PixivWebActivity` 已用独立进程隔离，但目前没有任何"登录态存放位置/清除入口/超时清理"的可见说明（设置页只有一段文字描述）。
5. **回收站位于应用私有目录**（`filesDir/recycle_bin`）：卸载或"清除数据"会直接删除其中所有文件，界面没有提示；同时 `allowBackup="true"` 与"全部排除"的备份规则组合在语义上是自相矛盾的（要么关备份，要么明确可备份范围）。
6. **收藏以 MediaStore URI 为键**（`album_preferences` 的 `favorites` StringSet）：文件被移动/重命名/重新索引后 URI 变化，收藏会静默丢失。

### 2.6 数据可靠性与用户资产

**现状与证据**

- 用户侧状态分散在 **6 个 SharedPreferences 文件**：`album_preferences`、`album_settings`、`cleanup_preferences`、`transfer_preferences`、`pixiv_archive`、`pixiv_metadata_cache`。
- 使用 `SharedPreferences` 而非 DataStore，没有版本号与迁移框架；`searchable_folder_index.json`、`MediaSnapshotStore`、`WallpaperQueueStore` 各自实现了一套 JSON 落盘机制。
- 没有导入/导出/备份入口。换机、重装、清除数据都会丢：收藏、排序与列数偏好、壁纸队列、幻灯片队列、回收站内容、排除文件夹列表。
- 回收站恢复同时依据 `storedPath` 与原始 URI 判断，并考虑了失败回滚（这是加分项），但容量与生命周期完全绑定应用私有存储。

**成熟产品要求**

一个可导出/导入的用户数据包（收藏、偏好、队列、排除列表）；回收站与卸载语义对用户可见；状态存储有明确 schema 与迁移。

### 2.7 架构与可维护性

**现状与证据**

- 单文件规模：`AlbumApp.kt` 3,997 行（仅 6 个 `@Composable`）、`ImageEditorDialog.kt` 3,634 行、`CleanupScreen.kt` 2,492 行、`Media3VideoPlayer.kt` 1,605 行、`PixivArchiveRepository.kt` 1,425 行。
- 没有 ViewModel 层，界面状态直接写在 `@Composable` 与 Activity 里（`rememberCoroutineScope`/`LaunchedEffect` 出现 180 处），配置变更与进程重建后的恢复能力依赖 Compose 的 `rememberSaveable` 覆盖度，未做验证。
- 没有依赖注入，仓库类在 composable 中直接构造（如 `MediaLibraryState(context)`），测试替身难以注入。
- 死代码与残留仍在：`MediaLibraryState.kt` 有整段被注释掉的 `retentionDays` 与 `init` 块；`res/layout/view_html_video_player.xml` 与整套 `mipmap-*/ic_launcher_round.webp` 已无人引用；而 `AndroidManifest` 的 `android:icon`/`roundIcon` 指向 `@drawable/ic_launcher_legacy`，导致 `mipmap-anydpi-v26/ic_launcher.xml` 这套**自适应图标（含 monochrome 主题图标）从未生效**——桌面图标既不是自适应图标，也没有 Android 13+ 主题图标。
- 依赖版本与 Compose BOM（2026.02.01）严重错位：`core-ktx 1.10.1`、`activity-compose 1.8.0`、`documentfile 1.0.1`（2020 年）、`lifecycle 2.6.1` 均为三四年前的版本。

**成熟产品要求**

单文件有上限（例如 800 行）；UI 状态由 ViewModel 承载并有明确状态模型；关键仓库可注入以便测试；依赖按季度升级并有回归；无死资源。

### 2.8 功能完整度（对标成熟相册）

已有：文件夹/相册/时间轴浏览、收藏、搜索（含文件夹名索引）、排序与列数、多选批处理、复制/移动/重命名、回收站与保留期、重复图片检测、排除文件夹、图片编辑器（裁剪/滤镜/调整/涂鸦/文字/十款字体）、视频播放（Media3 + LibVLC 双引擎、后台播放、画中画、小窗、手势、进度记忆）、静态/动态壁纸队列与轮播、幻灯片队列、Pixiv 登录与归档、中英双语、主题色与深浅色、更新检查。

仍缺（按对"成熟度"的影响排序）：

| 缺口 | 说明 | 影响 |
| --- | --- | --- |
| 用户数据备份/导出 | 无导入导出，换机即丢 | 高 |
| 接收系统分享（`ACTION_SEND`） | 清单里只有 `VIEW`/`ATTACH_DATA`，其他 App"分享到 Album"不可用 | 高 |
| 应用内更新下载 | 目前只是打开浏览器地址，无包完整性校验 | 中 |
| 批量重命名/EXIF 编辑 | 编辑器只处理像素，不写元数据（Pixiv 归档除外） | 中 |
| 视频剪辑/转码 | 已依赖 `media3-transformer` 但未见剪辑入口 | 中 |
| 大屏/横屏布局 | 只有缩放式适配 | 中 |
| 搜索历史/拼音搜索 | 无 | 低 |
| 桌面小组件 | 无 | 低 |

### 2.9 性能与体积

- Release APK 63.5 MB（arm64-v8a），其中 `res/font` 60.42 MB，LibVLC 原生库另需数十 MB——**编辑器的 10 款中文字体几乎单独决定了安装包体积**。
- 这些字体只在编辑器"文字"工具里用到，却全量随包分发，没有做字形子集化（subsetting）或按需下载。
- 加分项：R8 生效（`app/build/outputs/mapping/release/mapping.txt` 76 MB）、按 ABI 分包、缩略图磁盘缓存与 `onTrimMemory` 回收。
- 没有启动耗时、滚动帧率（jank）、大库扫描耗时的测量手段（无 Macrobenchmark 模块，无性能基线记录），也没有 ANR/卡顿上报。

### 2.10 文档与协作流程

- 已有：README（功能/构建/发布/权限说明）、CHANGELOG（每版含 APK 与 SHA-256）、SECURITY.md、Issue/PR 模板、CI 构建。
- 缺：用户向使用手册或 FAQ、路线图与已知问题清单、仓库级隐私政策、贡献指南细节、依赖更新策略；CHANGELOG 尾部条目顺序也有乱序（1.1.44 之后出现 1.1.21/1.1.22/1.1.23/1.1.31）。
- `docs/conversation-history/` 保留了 11 份开发对话归档，对维护者有用，但对外部贡献者没有信息价值。

## 3. 行动清单（含验收标准）

### P0 — 发布前必须解决（决定"能不能放心给用户用"）

1. **统一版本事实源**：`version.properties` 作为唯一来源，README 与 `output-metadata.json` 不再手写；README 只描述"下一个发布版本"。
   验收：仓库里不存在与 `version.properties` 冲突的版本号。
2. **修复更新通道**：把 `ALBUM_UPDATE_URL` 指向发布产物（Release 资产或 Pages），发布时由 CI 生成；检查更新失败时给出明确提示而不是"已是最新"。
   验收：发布 1.2.1 后，1.1.x 用户在设置页点"检查更新"能看到 1.2.1。
3. **补齐第三方许可**：新增 `THIRD_PARTY_NOTICES.md` 与应用内"开源许可"页，覆盖 LibVLC（LGPLv2.1）、OFL 字体、Media3/AndroidX（Apache-2.0）。
   验收：应用内可查看全部第三方组件与许可证全文；LGPL 组件说明可替换性。
4. **明确用户数据处理**：提示"卸载/清除数据会删除回收站内容"；为收藏改用更稳定的键（或对移动后的条目做迁移）；明确"有备份"还是"无备份"。
   验收：删除前、卸载说明、设置页三处对同一语义表述一致；移动文件后收藏仍保留。
5. **CI 硬化**：lint 配置化（`lint.xml` + `abortOnError`，现存告警分类处理或建立 baseline），并加入仪器测试 job（至少覆盖 API 34/35/36）。
   验收：CI 能在 PR 上给出 lint 报告与真机/模拟器测试结果。

### P1 — 影响可用性与口碑（建议 1.2.x–1.3.x 完成）

6. **国际化重构**：把 397 + 101 条映射与 215 处内联三目迁到 `values/strings.xml` + `values-en/strings.xml`，开启 `MissingTranslation`/`HardcodedText` 校验。
7. **无障碍专项**：为所有图标按钮补 `contentDescription`，修复 `ClickableViewAccessibility`，为手势区域提供替代操作，跑一次 Accessibility Scanner。
8. **自适应与横屏**：去掉 Activity 级竖屏锁定（或改为"内容页竖屏、播放器自适应"的显式策略），为大屏提供双栏布局。
9. **测试补强**：让 ViewModel/仓库层可注入，覆盖"删除→回收站→恢复""移动/复制冲突策略""播放进度记忆""壁纸队列应用"等关键路径；为 Compose 页面加 `testTag` 与 UI 测试。
10. **体积治理**：字体子集化或改为按需下载（可省掉接近一半安装包体积），评估把 LibVLC 变成可选能力或仅随支持的设备安装。
11. **接收系统分享**：为 `MainActivity` 增加 `ACTION_SEND`/`ACTION_SEND_MULTIPLE` 入口。

### P2 — 打磨（2.0 之前）

12. ViewModel/DataStore 迁移与统一 schema，把 6 个 SharedPreferences 收敛为带迁移的存储层。
13. 拆分 4000 行级大文件；删除死资源（`view_html_video_player.xml`、未引用的 mipmap）；启动图标切回自适应图标并启用 monochrome。
14. 依赖升级与回归（`core-ktx`、`lifecycle`、`activity-compose`、`documentfile`）。
15. 崩溃收集与 mapping 上传（可自建、可匿名、默认关闭）。
16. 性能基线：启动耗时、首屏滚动帧率、大库（万张图）扫描耗时的可复现测量。
17. 清理仓库：APK 与 `.dm` 产物移出版本控制（当前 `size-pack` 244 MB）；补 `PRIVACY.md`、路线图、已知问题清单。

## 4. 建议节奏

| 版本 | 主题 | 目标 |
| --- | --- | --- |
| 1.2.1 | 版本基线 + 复盘 | 本次：统一版本号，固化复盘结论（本文件） |
| 1.2.x | 发布链路与合规 | P0 全部关闭：更新通道、许可声明、数据语义、CI 硬化 |
| 1.3.0 | 国际化与无障碍 | P1 的 6–9 项：资源化文案、无障碍、自适应布局、测试补强 |
| 2.0.0 | 架构与体积 | 状态层重构、依赖升级、包体减半、用户数据导入导出 |

## 5. 本轮（1.2.1）完成情况

本轮的改动目标是"把复盘里能安全落地的项一次做完，并把结论留在仓库里"。

### 已完成

| 复盘项 | 完成情况 | 证据 |
| --- | --- | --- |
| 版本号单一来源 | `version.properties` 更新到 `1.2.1` / `VERSION_CODE 171`；README 只声明"下一个发布版本"；新增 [RELEASING.md](../RELEASING.md) | `BuildConfig.VERSION_NAME = "1.2.1"`、`VERSION_CODE = 171`（实际构建产物核对） |
| 修复更新通道 | `ALBUM_UPDATE_URL` 改为 `releases/latest/download/album-update.json`；新增 `scripts/write-update-manifest.ps1` 从 `version.properties` 生成清单；检查器支持 GitHub Release 响应与语义化版本比较，并新增"更新源落后"状态 | `AppUpdateCheckerTest` 9 个用例；生成脚本已跑通并产出合法 JSON |
| 第三方许可 | 新增 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)、`assets/licenses/{lgpl-2.1,apache-2.0,ofl-1.1,fonts}.txt`（LGPL 全文 26 KB、OFL 全文 4 KB）、应用内**设置 → 关于 → 开源许可**页 | 字体版权声明直接从包内 TTF 的 name 表提取 |
| 用户数据语义 | `allowBackup` 改为 `false`（与既有的"全部排除"规则一致）；回收站页面与设置项写明"卸载/清除数据会删除回收站内容"；隐私政策同步更新 | `AndroidManifest.xml`、`CleanupScreen.kt`、`SettingsScreen.kt` |
| 收藏不再静默丢失 | 新增稳定身份键与 `repairFavoriteUris()`，每次媒体库扫描后自动把收藏重新指向改名/移动/重新索引后的文件；歧义匹配不动 | `FavoriteIdentityTest` 8 个用例 |
| 用户数据可迁移 | 新增导出/导入（设置 → 文件操作）：收藏、队列、排除文件夹与偏好设置，不含媒体与账号信息 | `UserDataBackup.kt`，导入后自动重载界面 |
| CI 硬化 | lint 配置化（baseline + `warningsAsErrors` + `abortOnError`）；新增 API 30/35 模拟器仪器测试 job；新增 Dependabot 配置；上传 lint/测试报告 | `.github/workflows/android.yml`、`app/lint-baseline.xml` |
| 死代码与死资源 | 删除 92 行注释掉的编辑器组件、`MediaLibraryState` 中被注释的旧实现、`view_html_video_player.xml`、10 个模板图标 webp、两个 Android Studio 模板测试 | `git status` 中的删除项 |
| 启动图标 | 清单改为引用 `@mipmap/ic_launcher`，自适应图标（含 Android 13+ 主题图标）真正生效；新增单色图层 `ic_launcher_monochrome.xml` | `AndroidManifest.xml`、`mipmap-anydpi-v26/*` |
| 接收系统分享 | 主界面新增 `ACTION_SEND` / `ACTION_SEND_MULTIPLE` 入口，分享进来的照片和视频走现有目标文件夹选择流程导入 | `AndroidManifest.xml`、`MainActivity.kt`、`AlbumApp.kt` |
| 依赖与编译目标 | `core-ktx 1.19.0`、`lifecycle 2.11.0`、`activity-compose 1.13.0`、`documentfile 1.1.0`、`exifinterface 1.4.2`、`espresso 3.7.0`；`compileSdk` 升到 37（`targetSdk` 仍为 36）；`resourceConfigurations` 迁移到 `androidResources.localeFilters` | 构建通过 |
| 国际化架构 | `SettingsScreen` 里的第二份字典（109 行）已删除，全部文案走 `AppLanguage.kt` 的单一字典；新增 `TranslationCoverageTest`，自动校验每个 `appText("…")` 调用点都有英文 | 该测试上线时抓出了编辑器裁切比例弹窗里三段乱码文案（已随死代码一并删除） |
| 无障碍 | 修复 `OverlayMiniWindow` 触摸不触发 `performClick` 的问题（lint `ClickableViewAccessibility`） | lint 报告 |

### 仍未完成（附原因）

| 复盘项 | 状态 | 原因 |
| --- | --- | --- |
| 文案迁移到 Android 资源 | 未完成 | 现有 394 处 `appText(...)` 调用点中，有相当一部分在非 Composable 上下文（如 `Context` 工具函数），迁移到 `stringResource` 需要重写这些调用点；本轮先统一到单一字典并加了覆盖率测试，作为过渡 |
| 字体体积治理 | 未完成 | 字体子集化必须配 `Typeface.CustomFallbackBuilder` 才能在缺字时回退系统字体，而该 API 仅 Android 10+ 可用；minSdk 24 需要另一条路径，属于必须真机验证的独立工作 |
| ViewModel / DataStore 迁移 | 未完成 | 30k 行界面的状态模型重构，风险与工作量都很大，应当单独立项 |
| 大屏与横屏布局 | 未完成 | 需要设计层面的双栏布局方案，不是代码清理 |
| TalkBack / 大屏实机验收 | 未完成 | 需要设备与无障碍扫描工具；本轮只修了 lint 能发现的问题 |
| 崩溃上报 | 未完成 | 涉及后端与隐私取舍，需要产品决策 |
| 性能基线（启动耗时/帧率） | 未完成 | 需要 Macrobenchmark 模块与真机 |

### 说明

- 本轮构建了签名 release 包用于验证，`app/release/app-release.apk` 已更新为 1.2.1（arm64-v8a 分包，SHA-256 `90B8F82C…1ED8E`）。注意 `packageRelease` 成功后会自己把 patch 号加一，本次构建后 `version.properties` 一度变成 1.2.2，已改回 **1.2.1**，因为这一版还没有对外发布。
- 仓库内的 `app/release/` 已不再纳入版本控制：历史上的每次发版都在往仓库里塞一个 63 MB 的 APK，是仓库膨胀到 244 MB 的主要原因。文件仍留在本地磁盘上，只是不再提交。
- 本轮**没有**创建 Git tag 或 GitHub Release，也没有推送任何东西；更新通道要等第一次把 `album-update.json` 作为 Release 资产发布后才会返回有效结果。

## 2026-09-22 全盘优化清单（进行中）

已完成：P 页归档文件夹过滤改为预计算小写集合＋哈希查找（v1.2.50）；小窗四角"固定哪两条边"抽成 miniWindowPinnedEdges() 并由单元测试钉死四种情形（v1.2.51）；小窗期间隐藏多任务卡片尝试 setExcludeFromRecents 并调整调用时机（v1.2.52），真机结论是 OriginOS 不采纳该标志（dumpsys 里仍是 Recent #1）。

待做：1 裁剪页黑边区域走同一套命中判定（把角/边/移动三态抽成接收图片归一化坐标的共用函数）；2 裁剪页记录被旋转挤小前的尺寸并在角度回正后恢复；3 P 页 walk 增量（目录指纹＋复用快照，真机计时）；4 小窗多任务隐藏改用 FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS 重建任务；5 缩略图并发与缓存命中率审计；6 三个列表页 remember/derivedStateOf 键审计；7 启动路径 IO 移出主线程；8 lint baseline 失效条目与死代码清理；9 APK 体积（LibVLC 架构/localeFilters/资源）审计；10 CI 增补真机脚本化检查。
