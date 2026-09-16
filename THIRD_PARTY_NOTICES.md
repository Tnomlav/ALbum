# 第三方组件与许可

Album 自身的代码使用 [MIT License](LICENSE)。下面列出随 APK 一起分发的第三方组件
及其许可，以及分发时需要满足的义务。

应用内可离线查看这些声明：**设置 → 关于 → 开源许可**。许可证全文存放在
`app/src/main/assets/licenses/`，同时分发在安装包内。

## 随包分发的组件

| 组件 | 版本 | 许可 | 说明 |
| --- | --- | --- | --- |
| LibVLC（`org.videolan.android:libvlc-all`） | 3.7.5 | LGPL-2.1 | AVI/MPEG 等容器与编解码的播放引擎 |
| AndroidX Media3（ExoPlayer、UI、Transformer、Effect） | 1.6.1 | Apache-2.0 | 主播放器与图片/视频处理 |
| AndroidX Compose（UI、Material 3、Material Icons） | BOM 2026.02.01 | Apache-2.0 | 全部界面 |
| AndroidX Core / Lifecycle / Activity / DocumentFile / ExifInterface | 1.10.1 / 2.6.1 / 1.8.0 / 1.0.1 / 1.3.6 | Apache-2.0 | 基础能力、SAF 文档访问、EXIF |
| Kotlin 标准库与协程 | 2.2.10 | Apache-2.0 | 语言运行时 |
| 内置编辑器字体（10 款，见下） | — | OFL-1.1 | 图片编辑器"文字"工具 |

### LibVLC（LGPL-2.1）分发义务

LibVLC 以动态链接方式使用，应用本身不修改其代码。按 LGPL-2.1 的要求：

- 随安装包提供完整的许可证文本（`app/src/main/assets/licenses/lgpl-2.1.txt`）；
- 在应用内提供可查看的声明入口（设置 → 关于 → 开源许可）；
- 允许用户替换库版本：本项目公开全部源码，任何人对 LibVLC 的替换/重新链接都可
  通过重新构建本应用完成，构建步骤见 [README](README.md#构建)。

LibVLC 的源码与许可信息：<https://code.videolan.org/videolan/vlc-android>。

## 内置字体（SIL Open Font License 1.1）

图片编辑器的"文字"工具内置以下字体，全部以 OFL-1.1 授权分发。版权声明从安装包
内的字体文件中提取，完整列表见 `app/src/main/assets/licenses/fonts.txt`。

| 字体 | 文件 | 版权 |
| --- | --- | --- |
| DotGothic16 | `dotgothic16.ttf` | Copyright 2020 The DotGothic16 Project Authors |
| Liu Jian Mao Cao | `liu_jian_mao_cao.ttf` | Copyright 2018 The Liu Jian Mao Cao Project Authors |
| Long Cang | `long_cang.ttf` | Copyright 2018 The LongCang Project Authors |
| Ma Shan Zheng | `ma_shan_zheng.ttf` | Copyright 2018 The MaShanZheng Project Authors |
| Noto Sans SC | `noto_sans_sc.ttf` | (c) 2014-2021 Adobe, with Reserved Font Name 'Source' |
| Noto Serif SC | `noto_serif_sc.ttf` | (c) 2017-2024 Adobe |
| ZCOOL KuaiLe | `zcool_kuaile.ttf` | Copyright 2018 The ZCOOL KuaiLe Project Authors |
| ZCOOL QingKe HuangYou | `zcool_qingke_huangyou.ttf` | Copyright 2018 The ZCOOL QingKe HuangYou Project Authors |
| ZCOOL XiaoWei | `zcool_xiaowei.ttf` | Copyright 2018 The ZCOOL XiaoWei Project Authors |
| Zhi Mang Xing | `zhi_mang_xing.ttf` | Copyright 2018 The ZhiMangXing Project Authors |

字体文件未做改动（未重命名、未修改字形），仅作为字体软件嵌入应用分发。

## 仅用于开发与测试（不随包分发）

JUnit、AndroidX Test、Espresso：用于单元测试与仪器测试，不会进入发布 APK。
