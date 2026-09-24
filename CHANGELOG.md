# Album Changelog

## v1.2.66 - 2026-09-25（加载圈只跟手动下拉；查重能找出"肉眼相同"的图）

- **加载圈只在手动下拉时出现**：`requestPixivReload()` 增加 `showIndicator`，只有 P 页的下拉手势传 `true`；切标签、改设置、归档完成等 App 自己触发的加载不再点亮指示器（P 页的 `loading` 也从"媒体库 loading"改为只跟手动下拉的 `pixivPageRefreshing`）。
- **查重找不出"肉眼相同"的图，原因有两个**：一是"重复图片"只比较 SHA-256，只有字节完全一致才算重复——同一张图重新编码、缩放、换质量保存的副本永远查不出来；二是本来能兜住这种情况的感知哈希（dHash）函数 `findSimilarImages` 是死代码，**从未被任何界面调用**。
- 现在"重复图片"同时找两类：字节完全相同的（同尺寸才做 SHA-256，保持原来的快），以及"就是同一张图"的（64 位 dHash 距离 ≤ 6、长宽比差 ≤ 3%、平均色差 ≤ 30）。两者的结果用并查集合并，所以「3 个完全相同的文件 + 1 个重编码副本」会归到同一组。阈值故意收紧，因为这一屏是拿去删的。
- 为控制耗时：只解码"长宽比可能匹配"的图（不同长宽比不可能命中），解码并发 4 路，并沿用原来的指纹磁盘缓存（第二次扫描直接读缓存）。新增单测覆盖阈值判定与长宽比裁剪。
- 说明：本轮测试机在扫描过程中被切到别的 App（有人在使用），端到端结果没能在真机确认；首次全盘扫描在大库上仍需解码全部候选（数分钟量级），第二次起走缓存会快得多，下一轮打算改用已有的缩略图管线再提速。

## v1.2.64 - 2026-09-22（P 页下拉的加载圈）

- **修好"P 页下拉时加载圈不转"**：P 页的下拉刷新跑的是 Pixiv 图库遍历（状态是 `pixivPageRefreshing`），但页面把加载指示器绑在媒体库的 `loading` 上——两者从来不是同一个状态，所以即使目录真的在遍历，圈也不出现、不转。现在 P 页的指示器接 `pixivPageRefreshing || library.loading`。
- 顺带两处让"拉到的东西看得见"：一是请求发出时**立刻**点亮指示器（以前要先等 350ms 的合并触发延迟）；二是**最短显示 700ms**——目录指纹命中后一次遍历只要几百毫秒，圈可能在一帧内出现又消失，读起来像"什么都没发生"。另外给"切到别的页面导致请求直接返回"的路径补上清标志，避免圈一直转。
- 真机（1.2.64 已装）：下拉后顶部刷新区域出现指示器、内容被顶下并持续动画，遍历结束后恢复静止；更直观的一次确认还是直接在 P 页拉一下最快。

## v1.2.62 - 2026-09-22（历史归档补扫 R-18、弹窗按钮统一）

- **补上"更新前已归档的 R-18 图片"**：1.2.61 只在归档那一刻记录 R-18，所以那次更新之前完成的归档不会进过滤表，开关对它们是空的。现在新增 `PixivArchiveRepository.backfillAdultTags()`：按目录遍历归档目录一次，逐张读取归档器当初写下的 tag（先读文件内嵌 EXIF/PNG，再回退到 `.pixiv.json` 旁车），命中 R-18 / R-18G 的补记进过滤表；每种归档配置只跑一次（`adult_backfill_key` 记住 source/target/显示隐藏 的组合，换目录会重扫）。打开开关时先补扫、再刷新图库与 P 页，因此历史归档同样会被屏蔽。
- **弹窗按钮统一**：新增 `VaultDialogLabels`（取消 / 完成 / 知道了 / 应用 / 关闭 五个标签只在这里定义一次）与一组共用组件：`VaultSheetPrimaryButton`（居中胶囊主按钮，破坏性操作自动改用错误色）、`VaultSheetDismissButton`（"知道了"式关闭）、`VaultSheetCancelButton`（次要按钮全宽、纯文字、不使用强调色）、`VaultSheetConfirmButtons`（主按钮 + 全宽取消）。信息类、确认类、输入类三种弹窗（底部 sheet 与居中 dialog 两种形态）全部改用它们——同类弹窗的确认/关闭键位置与样式一致，破坏性确认用红色区分，次要动作不再挤占主按钮的位置。
- 顺带把 `VaultDialogs.kt` 里散落的 "应用"/"关闭" 字面量收敛到标签对象，并新增 `DialogButtonConsistencyTest`：断言五个标签在组件文件里各只出现一次（只能在标签对象里定义）、五个共用弹窗必须使用共用按钮组件——以后谁再在弹窗里手写一套按钮，测试直接失败。
- 真机（1.2.62 已装）：设置 → 显示 出现"屏蔽 R-18 图片"（副标题"隐藏归档时写入 R-18 tag 的图片"）；设置 → 视频 显示改名后的"手势提示浮层"（副标题"手势操作时显示中间提示"）。共 115 个单测通过、lint 0 error / 0 warning。

## v1.2.61 - 2026-09-22（R-18 屏蔽、视频设置同步、播放器菜单横屏居中）

- **设置 → 显示 新增"屏蔽 R-18 图片"**：归档时若作品 tags 里含 R-18 / R-18G，归档器在它本来就要写 tag 的那一步顺手把这张图记到 `content_filter`（URI 与文件名两份身份）；打开开关后，图库与 P 页在过滤阶段直接排除这些图，不需要逐张回读文件里的 tag。开关沿用"显示点号开头的图片"那套流程（改设置即重新过滤），URI 与文件名任一命中都会屏蔽，所以归档后改名也不会漏。
- **视频设置两侧同步**：播放器内的"视频设置"弹窗漏了 `video_center_popup`（设置页有、弹窗没有），现已补上；并新增 `VideoSettingsSyncTest`，扫描设置页"视频"分区与播放器弹窗里所有偏好键调用，断言两边键集合完全一致——以后任一侧新增或删除设置而忘了同步，测试直接失败。
- **改名并缩短副标题**：`video_center_popup` 的标题由"视频弹窗"改为"手势提示浮层"（英文 Gesture hint overlay），副标题由"暂停、快进快退、调节倍速时显示中间的黑色提示"缩短为"手势操作时显示中间提示"。
- **播放器菜单横屏居中**：该菜单所在 Dialog 的窗口覆盖整屏（含导航栏与挖孔区），横屏时内容是按错误矩形居中的。现在内容加 `safeDrawingPadding()`，并把菜单宽度上限设为 360dp，横屏下既不偏在一侧、也不会被拉伸成整屏宽。
- 单元测试：新增 `AdultTagStoreTest`（4 项：大小写/【】归一化、R-18G、URI 与文件名两种身份、空过滤器）与 `VideoSettingsSyncTest`（1 项）；共 113 个单测通过、lint 0 error / 0 warning（顺手把两处旧的 `SharedPreferences.edit()` 换成 KTX 写法）。
- **说明**：本轮结束时测试机与电脑断连（adb 无设备），1.2.61 没能装到手机做真机确认；上述改动由单测与 lint 保证，R-18 过滤的端到端效果需要一条真实带 R-18 tag 的归档记录才能真机复核。

## v1.2.59 - 2026-09-22（P 页遍历增量化：一次查询读目录、目录指纹复用）

- **P 页构建图库的遍历改成"每个目录只查一次"**。以前是 `DocumentFile` 递归：列一个目录 1 次 IPC，然后每个子项再分别读 name / type / size / lastModified（每项 3-4 次 IPC），所以一个几千张图的归档要走成千上万次 provider 往返。现在新增 `listPixivDirectory()`，用一次 `DocumentsContract` 查询把 displayName / mimeType / size / lastModified 全部取回（`file://` 目录走本地实现），遍历顺序与过滤规则（隐藏项、`.trashed-*`、只收图片）保持不变。
- **同时做目录指纹复用**：每个目录的列表会算一个 SHA-256 指纹（uri + 名称 + 类型 + 大小 + 时间 + mime），并随图库缓存写进 `pixiv_walk_index.json`。下一次遍历时，只要某个目录的列表与指纹一致，就整棵子树直接回放、**完全不再访问 provider**；只要目录里增删改任何一项，指纹变化即失效重读，所以不会把旧内容当新内容。缓存键沿用 source/target/显示隐藏媒体三者（改动其中任一即整表失效）。
- 遍历结束后在归档页标题旁显示本次代价（例如"扫描 1.2 秒 · 读取 96 个目录，复用 412 个"），复用数就是没有再次访问 provider 的目录数。
- 单测 `PixivWalkIndexTest` 覆盖：指纹对顺序不敏感但能识别改名/改大小/改时间/增删；第二次遍历只列根目录（`root` 一次查询）、未变子树全部复用；目录变化时重新列该目录；隐藏项与 `.trashed-*` 被跳过。共 108 个单测通过、lint 0 error / 0 warning。
- **说明**：这一轮改动本身（查询次数从"1+3~4×文件数"降到"每目录 1 次"、未变子树 0 次）由单测断言；归档页上的耗时长行因为本机 uiautomator 在该页多次返回同一份旧 dump，未能在真机读到数字，下一轮换截图/日志方式再确认一次。

## v1.2.58 - 2026-09-22（裁剪框按旋转后的真实边界内接，并可自由移动）

- **重新做了裁剪框的几何**：以前判定"框有没有跑出画面"用的是旋转后图片的**外接矩形**，比真实边界更宽松，所以框能被挪到画面被转出的黑角上；而且绘制和手势各自又套了一层缩放，同一个手指位置在两层里换算不一样，于是"点住角拖动却没反应"。现在统一在"图片高度单位"里算：把图片看成 `宽=图片宽高比、高=1` 的矩形绕自身中心旋转，用**四个角**判定是否在旋转后的图片内。
- **旋转时按边界自动控制大小**（既不超出也不内缩）：框先按自身中心缩到刚好能放进旋转后的图片，再沿"到图片中心"的方向滑回边界内。大小只会变小、绝不会变大；缩放量正好到边界为止（单元测试断言"再放大 1% 就越界"）。图片转回 0° 时，用户原定的框原样回来（角度只影响绘制，不写入框本身）。
- **旋转后移动受边界限制**：拖动时同样走这套判定，框最多滑到刚好贴住旋转后的图片边界，不会再压到黑角上；如果框本身就是当前角度下的最大内接尺寸，则它本来也无法平移（数学上只有居中那一个位置），拖动会表现为"到边界为止"。
- **拖动缩放不再"没反应"**：绘制与手势共用同一套坐标（去掉重复缩放），角/边的命中带把内侧从 24dp 放宽到 32dp（外侧仍 48dp），从框内瞄准角或边更容易抓住。
- 真机复核（均匀测试图，便于按亮度判定"框是否压在黑角上"）：0° 时框 (184,360)-(1256,2727)，倾斜后四个角内侧亮度仍是画面亮度（101，而不是黑角的 0），硬拖到左上角后框完全没越界；把角往内拖 180px 后框确实变小、再往外拖 320px 后回到与倾斜时一模一样的内接尺寸；角度回 0° 后尺寸恢复。`scripts/device-smoke.py` 四项检查全绿（含"倾斜往返逐像素一致"和"黑边拖拽把边带到手指位置、对边不动"）。

## v1.2.57 - 2026-09-22（列表滚动：第一个可见项改为哈希查表）

- 相册文件夹网格、文件夹内网格、时间轴三处的"当前第一个可见项"判定，原来都写在滚动采样里（每 80ms 一次），对每个可见项做嵌套线性扫描；时间轴那一版是"每个可见项 × 每个日期分组 × 分组内每张图"，而且每次比较都调用一次 `uri.toString()` 新建字符串。一万张图时每次采样就是十万级比较加同等数量的字符串分配。现在每个页面用 `remember` 预计算一份 key 集合（文件夹名 / `uri.toString()`），采样里只做一次 `contains` 查询，复杂度从 O(可见项 × 全库) 降到 O(可见项)，判定结果不变。
- 真机验证：1.2.57 装机后冷启动 235 ms；时间轴来回滚动后正常渲染、首个可见项跟踪未回归，logcat 无崩溃。

## v1.2.56 - 2026-09-22（冷启动去掉主线程 IO、lint 基线清理、体积审计）

- **冷启动不再在主线程解析媒体快照**。`MediaLibraryState` 以前在构造时直接读并解析 `media_snapshot.json`（大库是几 MB 的 JSON），把第一帧卡在解析上。现在构造只取"本进程已经解析过的快照"（`MediaSnapshotStore.cached()`），冷启动的磁盘读取留在 `refresh()` 里、本来就在 `Dispatchers.IO` 上。同一台设备、同一份数据、`force-stop` 后 `am start -W` 实测：改动前 682 / 736 / 700 ms，改动后 226 / 240 / 232 ms（约 3 倍），首屏相册内容照常显示。
- **缩略图缓存审计**：新增命中率计数（memory / disk / decoded / coalesced / hit%）与日志输出（首次加载必出一行，之后每 100 次一行，清缓存与媒体扫描结束时各一行汇总）。并发路径逐条核对：同一 key 只有一个解码在跑（per-key `Mutex`，等待方计入 coalesced）；解码许可为可见 5 / 预取 1 / 原图 1；内存 LRU 为堆上限的 1/8；磁盘缓存按 `last-modified` 做 LRU 并在超限时调度裁剪。**说明**：本机（OriginOS）的 release 包日志没有出现在 logcat 里，所以命中率数字没能在该机上采集，计数代码在有日志的构建与设备上可用。
- **lint baseline 清理**：删掉 2 条已经不再命中的历史条目（`ClickableViewAccessibility`、`UseKtx`），并把 baseline 与当前代码对齐（其余为行号更新）。lint 仍为 0 error / 0 warning / 1 hint——留下来的那 1 条是"libvlc 3.7.6 可升级"的提示，故意不写进 baseline，免得静默。顺带扫了一遍 main 源集：没有被引用不到的 private/internal 函数。
- **APK 体积审计**：新增 [docs/release-size.md](docs/release-size.md)，实测 63.5 MB 的构成（LibVLC 原生库约 23 MB、编辑器字体约 31.5 MB、dex 2.55 MB，其余合计不到 6 MB），并给出三条可选优化路径（分流精简版 / 字按需下载 / 字体子集化）及各自风险。当前取舍是"装上即用、离线可用"，1.2.x 不动体积。

## v1.2.53 - 2026-09-22（裁剪页：命中判定统一、倾斜回正恢复尺寸）

- 裁剪页原本有两份"这一下抓住的是角、边还是整框"的判定：一份在裁剪框自己身上，一份在盖住上下黑边的手势层里。两份代码各写各的，已经漂移过——同一个手指位置，黑边层与框体层可以得出不同的把手。现在抽成共用的 `cropHandleAt()`（角 0-3、边 4-7、整框移动 8、没碰到 -1，内侧带宽仍是外侧的一半），两处都调用同一份实现，并补单元测试钉住角/边、内外侧带宽、黑边贴边抓边、远处不响应、最近角优先等情形。
- 裁剪框不会再被倾斜预览永久压小：以前手势会把经过角度约束的结果写回裁剪框本身，于是在倾斜状态下拖动一次，裁剪尺寸就变小且回不来。现在用户设定的框原样保留（`constrainWallpaperFrameToImage()` 只保证它在图片内），角度只影响绘制与导出时的内缩（`rotatedWallpaperPreviewScale()` / `wallpaperPreviewFrame()`），角度回到 0 时尺寸自动恢复。顺带修好导出：之前倾斜状态下传给导出的框已经被内缩过一次，导出函数内部还会再内缩一次，等于压小两遍。
- 新增 `WallpaperCropGestureTest`（10 个用例）覆盖"倾斜只改变绘制而不改裁剪框""倾斜时拖动不会缩小裁剪""角度回正后尺寸恢复"三点，以及黑边与框体共用命中的全部边界情形。

## v1.2.52 - 2026-09-22（小窗多任务隐藏：设置时机调整，仍未生效）

- 把 task 排除多任务（setExcludeFromRecents）的调用提前到 moveTaskToBack 之前（此前在之后调用）。真机复测：本应用仍以 Recent #1 出现在 dumpsys activity recents 里 —— 说明这台 OriginOS 机器不采纳该标志。结论记在 CHANGELOG，下一轮改用 FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS 重建任务的方式验证。

## v1.2.51 - 2026-09-22（小窗四角回归测试）

- 把"拖动某个角时固定哪两条边"抽成 `miniWindowPinnedEdges()` 并用单元测试钉死四种情形（右下固定顶+左、左下固定顶+右、左上固定底+右、右上固定底+左）。这段语义前后改过好几次，以后改动一旦破坏它测试会直接失败。
- 至此"全盘优化"里体验一致性一项有了第一块可回归的护栏；性能与工程卫生两块见下方说明，按计划继续。

## v1.2.50 - 2026-09-22（P 页文件夹过滤优化）

- P 页判断某张图是否属于归档文件夹，从"逐项遍历文件夹名 + ignoreCase 比较"改为预计算小写集合 + 哈希查找。归档有几千张图时，这是每次重组都要重算的热点。

## v1.2.49 - 2026-09-21（视频弹窗开关）

- 视频大类新增"视频弹窗"开关（`video_center_popup`，默认开），控制暂停、快进快退、调节倍速时画面中间那个黑色提示条是否出现。关掉后手势仍然生效，只是不再显示中间的黑色弹窗。

## v1.2.48 - 2026-09-21（小窗期间隐藏多任务卡片）

- 进入小窗后应用本体已不可见，任务卡片留在多任务里点开会得到一个小窗飘在自己头上的怪状态。现在进入小窗时把本应用的 task 设为"不显示在最近任务"（`AppTask.setExcludeFromRecents(true)`），点"回到全屏"、关闭小窗、或直接切回应用（activity 回到前台）时都会恢复显示。

## v1.2.47 - 2026-09-21（小窗四角恢复"固定对角"）

- 按你的说明把小窗四角语义改回来：拖右下角＝顶边和左边定死、拖左下角＝顶边和右边定死、拖左上角＝底边和右边定死、拖右上角＝底边和左边定死，只改长度。上一版我误理解成"四个角都固定左上角"（窗口位置完全不动），这次恢复为固定对角，同时保留前两轮的修正：手指位移按窗口对角线做正交投影（不再横向减半、纵向放大），以及只把贴边 16dp 窄带算作缩放、点内侧一律是移动。

## v1.2.42 - 2026-09-20（裁剪判定符号、黑边手势层）

- 裁剪框判定里"框内/框外"的符号写反了：框内的点算出来是正数，于是永远走外侧 48dp 分支，内侧限制形同虚设——这就是连续两轮"内侧判定没变过"的原因。现在四个角和四条边的符号统一为"正数=框外"，内侧只剩 1/4（约 12dp）。
- 黑边手势层的 `pointerInput` 之前在 key 里带了每帧都变的缩放值，拖动一开始重算就重启并取消手势，表现为"黑边拖动没反应"。现在改为稳定 key，黑边拖动可正常移动裁剪框。
- 小窗缩放判定区的内侧同步收窄：只有贴边/贴角才算缩放，点内侧一律判为移动。

## v1.2.41 - 2026-09-20（小窗缩放统一为左上角锚定）

- 小窗四个角的缩放现在完全一致：一律固定左上角，窗口本身在缩放过程中不再移动（这是连续两次要求的"把右下角的逻辑复制到全部角"）。角判定区从 40% 收到 25%（最小 44dp），手指落在内侧想移动时不会再被误判成缩放。

## v1.2.40 - 2026-09-20（裁剪页手势层、小窗角判定）

- 壁纸裁剪页手势层原来只覆盖原图（CropFrame 画在图片盒子里），手指落在图片上下的黑框上完全没有反应。现在整个预览区都有手势层：碰到黑框拖动＝移动裁剪框（缩放把手仍在框上）。
- 裁剪框"改大小"判定区**在框内的部分再减半**（框外仍 48dp 宽容，框内改为 12dp 量级），避免缩放区吃掉框内、想挪框却总在缩放。
- 小窗角判定改用**视图内坐标**：getLocationInWindow 对 FLAG_LAYOUT_NO_LIMITS 的悬浮窗会被窗口自身偏移污染，于是拖左下角被算成右下角（表现为窗口位置不动）。两个上角带按钮，改为直接按按钮归属判定角，不再依赖坐标。


## v1.2.39 - 2026-09-19（裁剪框控制区、小窗间距、归档删除、P 页加载）

- 壁纸裁剪页：裁剪框"改大小"的判定区**在框内的部分减半**（框外仍是 48dp 的宽容区），之前框内大半区域都算缩放区，想把框整体挪动很难。
- 小窗里快退/播放/快进的间距改成随窗口缩放（原来固定 6dp，窗口放大后按钮挤在中间、缩小后间距又显得过大）。
- 小窗拖动：抬手时补上手指的最后位置（之前只处理 MOVE，快速拖动会停在上一个采样点，所以"超慢速拖动勉强对、快一点就偏"）。
- 归档页删除：扫描出来的条目都是 SAF 文档，删除不需要系统弹窗。现在**留在归档页就地删除**（不再先跳回 P 页闪一下），成功会从列表消失，失败会有 Toast 说明，不会再"看起来什么都没发生"。
- P 页下拉加载圈卡住：`pixivPageRefreshing` 之前只在"本次真的走了 SAF 遍历"时清除；如果一次新的下拉只命中缓存提前返回，旧任务的 finally 又因为代号变了不清，圈就永远转下去。现在**最新一次请求总是负责清掉这个标志**。
- P 页/归档页"重复更新"：归档成功后会清掉该 PID 的元数据缓存，导致下一次扫描又去 Pixiv 查一遍、把没变化的文件当成新文件重做。现在保留缓存，重复扫描会把这些文件视为老文件直接跳过。

## v1.2.38 - 2026-09-19（小窗角部拖动数学、播放器底栏图标）

- 小窗四个角拖动的"乱飘"找到真正的数学原因：角部拖动时我用的是 `(横向位移 + 纵向位移 × 16/9) / 2`，这个权重把纵向运动放大了近 2 倍（正确做法是把手指位移投影到窗口自己的对角方向：`(横向 + 纵向 × 9/16) / (1 + (9/16)²)`）。所以横向拖只走一半、手指一抖却放大成跳动——右下角感觉正常只是因为它的窗口位置本来就不动，抖动没那么明显。现在四个角都用同一套投影，沿窗口对角线拖动时角点与手指 1:1 跟随，斜对角锁定（拖动角固定对角）保持不变。新增单元测试锁住这三点：对角线 1:1、横向不再减半、纵向抖动不再被放大。
- 播放器底栏图标尺寸统一：之前"播放"是 42dp（开长快进时 34dp）、其余 25dp，现在整行统一 26dp。

## v1.2.36 - 2026-09-19（进度条左端、小窗角区、去掉多任务缩小）

- 进度条最左端不再是"半圆变圆角矩形"：原因是我用"从左边到滑块的圆角矩形"画播过条，圆角半径会被宽度挤掉一半——滑块靠近 0% 时那条就窄到只剩圆角矩形。现在先按整条进度条画圆角，再裁到滑块中心、再挖掉滑块圆，所以左端永远是半径 6dp 的半圆。真机实测白条高度在 24px 内从 0 涨到 48px（1px 处 14px ≈ 2√(24²−23²)），正是半圆。
- **取消多任务缩小功能**：OriginOS 从桌面/其它 app 唤起多任务时系统完全不给信号（已用 dumpsys 逐项验证），保留它只会带来误触发，所以按你的要求把这个功能和对应的探测代码（后台任务探针、系统广播接收器、缩小/还原状态）整体删掉了。悬浮窗现在只有你在做的大小和位置，不会自己变。
- 左下角拖动真正修好了：两个原因叠加——① 角区大小原来有 96dp 上限，窗口一旦被放大，左下角区只剩 29%，你手指落在 30% 处就被判成"移动"；② 角区判定的纵坐标用"屏幕坐标 − 窗口参数"，而这个悬浮窗带 `LAYOUT_NO_LIMITS`，两者偏移并不等于状态栏高度。现在角区改为纯按比例（40%，无上限），坐标改用 `getLocationInWindow + event.x/y`。真机实测三种拖法（贴角外拖、30% 处外拖、贴角内缩）都是右边 1376 与顶边 800 不动、只有宽高变化。

## v1.2.33 - 2026-09-19（小窗圆角与 P 标镂空）

- 小窗"有时候直角有时候圆角"的原因找到了：窗口的黑色底是圆角的，但里面的视频默认用 SurfaceView——它由 SurfaceFlinger 单独合成，不吃父容器的圆角裁切，所以一旦视频出画面，四角就变直角；还没出画面（黑色垫底）时看着又是圆角。现在小窗的视频改用 `surface_type="texture_view"` 的 PlayerView，并在 PlayerView 上也加了圆角裁切。真机取色：窗口角落里是背后的壁纸（212,98,102）而不是视频（152,146,138），圆角生效。
- Pixiv 角标的 P 改回镂空：字母本体是接近纯白的白（90%），字母中间那个圈不再填充，露出的就是主题色底色（等于背景），和你要的一致。真机放大逐像素确认白色字母中间是主题色内圈。

## v1.2.32 - 2026-09-19（P 标提纯到白色）

- Pixiv 角标里的 P 提到 90% 不透明度：70% 时叠在主题色底上仍显得偏青（"P 里还有主题色"），现在真机取色是接近纯白的 (229,249,248)，只剩一点透明感，角标底色仍是主题色。

## v1.2.31 - 2026-09-19（进度条几何、小窗按键与回前台）

- 播放器进度条按你的描述重做几何：bar 直接画到控件两端（不再有 6dp 内缩的空隙），滑块圆心按"控件宽度 × 进度"计算，所以 0% 时滑块左缘贴住左端、100% 时右缘贴住右端。
- 播过条的右端改成**向左凹的半圆**：用"圆角矩形减去滑块圆"的路径裁出来，凹进去的缺口直径与滑块完全一致；滑块本体是 6dp 白点，和凹口之间由 3dp 的未播放色隔断分开（真机逐像素确认：播过条 → 3dp 隔断 → 6dp 白点 → 未播放条）。
- 小窗按键不再抢拖动：从右上角（关闭键）按下拖动，以前会判定成"点击关闭"，现在会正常缩放（真机实测：拖右上角内缩后窗口 x 不变、底边 1302 不变、宽高从 893×502 变到 767×431，窗口没有被关掉）。左下角拖动同样验证通过：右边缘 1250、顶边 871 都不动。
- 小窗缩小状态也允许拖角缩放（之前只允许移动，这会让"拖左下角"看起来像在乱动）；单击仍然是还原到进入小窗时的默认大小。
- 小窗模式点进本 app 直接恢复全屏：activity 回到前台时会自动关掉悬浮窗并重建视频画面，而不是让窗口飘在本 app 上面。
- 关于多任务缩小的进一步排查结论：本机（OriginOS）从桌面或其它 app 唤起多任务时，系统没有给出任何可观测差异——任务列表里只有本 app 与桌面的 task，桌面 task 的 top activity 仍是 `.Launcher`、可见性/活动数不变，我们进程的 importance 与全部 Settings 值也都不变（都已用 dumpsys 逐项对比）。也就是说"桌面状态下的多任务页"目前无法被第三方应用识别；本 app 自己从前台退到后台的分支（多任务动画会短暂出现 recents 组件）仍然可以触发缩小。

## v1.2.30 - 2026-09-19（P 标实心、进度条本体、多任务判定）

- Pixiv 角标：P 字母的空腔原来是透出主题色，现在用实心白色半透明填满——整块 P 是均匀的白色半透明形状，角标仍是主题色底。
- 播放器进度条本体不再被切开：播过的部分恢复成正常的圆头（不再是被裁的平口），只停在滑块隔断的外缘；隔断只属于滑块，进度条自己不再有缺口。
- 多任务缩小的判定放宽并且不再依赖厂商命名：现在看"桌面可见但没停在主屏"就算多任务页（之前只认类名里带 recents/overview/taskmanager 的界面，厂商换个名字就失效）。真机实测（1.2.30）：刚进小窗在桌面保持 893×502 不动 → 进多任务缩到 605×340 → 在多任务页里点一下放大回 893×502 并保持 → 回到桌面仍是 893×502 → 再进多任务又缩，可重复。

## v1.2.29 - 2026-09-19（滑块隔断可见性）

- 播放器滑块的隔断按 1:1 像素复核后做了关键修正：整条进度条先用"未播放"颜色铺满，播过的部分裁切到隔断外缘（左侧保持圆角），所以隔断真的看得见——真机量到的结构是【播过的白条 → 12px（3dp）未播放色隔断 → 24px（6dp）白色滑块本体 → 未播放条】，隔断+滑块正好等于原来的 12dp，隔断与右侧进度条颜色/透明度完全一致。

## v1.2.28 - 2026-09-19（切换标、滑块隔断、小窗角与多任务）

- 相册/时间轴的切换标加高 20%（内边距 5dp → 8dp），不再比顶栏其他元素扁。
- 播放器滑块向内加了一圈隔断：总直径仍是原来的 12dp，外圈 3dp 用"未播放进度条"本身的颜色填充，中间 6dp 才是滑块本体——隔断+滑块正好等于原滑块大小，隔断与右侧进度条完全接得上。
- 小窗拖角修复：命中判定改用窗口内坐标（之前拿屏幕坐标和窗口参数比，纵向差了一个状态栏高度），并且四个角区改成窗口尺寸的 40%（44–96dp 之间）。竖屏视频左右有黑边时，你手指落在"画面左下角"也能算作左下角；从那里拖现在是顶边和右边都不动，窗口只往左下长。
- 进多任务缩小重新做稳：改成"只有**可见的**多任务任务才算多任务页 + 上升沿触发"。之前桌面会把多任务页留在任务历史里，导致新建小窗时它自己缩一下、之后又永远不再缩小。现在实测：刚进小窗大小不变 → 进多任务缩到 605×340 → 在多任务页里点一下放大到 893×502 且保持 → 再进多任务又缩。
- 冷启动的加载圈终于找到真凶：本地缓存让 `initialLoadComplete` 一开始就是 true，于是启动时那次自动读取会点亮下拉刷新圈。现在启动自读期间强制不显示该圈（相册/视频/时间轴/P 页都改了），只有你主动下拉才出现。
- "主页"选项改成真正的"冷启动进入的页面 + 连续返回的最后一页"：选项为 相册/时间轴/Pixiv/工具箱/设置（视频已并入相册，去掉；新增工具箱、设置）。返回链的落点也跟着这个设置走，不再固定是底栏第一项。
- Pixiv 角标配色反转：改成主题色底 + 白色半透明镂空 P；同一套图标也用到了工具箱的"Pixiv 文件归档"入口和 P 页的归档入口。

## v1.2.25 - 2026-09-18（播放器滑块、小窗缩放与设置整理）

- 播放器进度条的滑块不再和进度条错开：滑块改成和进度条画在同一层，圆心按构造就落在进度条的上下平分线上（之前由 Material3 单独摆放，会高出几像素）。
- 小窗缩放改为"拖哪条边就固定对角"：拖左下角固定右上角、拖右下角固定左上角、拖左上角固定右下角、拖右上角固定左下角。锚点现在直接由抓到的角决定并在屏幕上保持绝对位置，不再看手指相对窗口中心的位置；窗口很小时四个角也按最近角判定，不会判不出角。
- 进入多任务页的瞬间就缩小：缩小的检测间隔从 800ms 缩到 120ms，配合系统广播，在切换动画开始时窗口就已经变小。
- 被多任务缩小的小窗，单击后放大回"刚进入小窗时的默认大小"，而不是你手动拖过的大小。
- 设置一级页面（8 个大类）文字放大一档。
- 冷启动时相册页不再出现加载圈：下拉刷新的转圈现在只在你主动下拉刷新时出现，首次扫描不点亮它。
- 设置里选中的选项文字改用主题色（例如 主题 → 语言 后面的"简体中文"）。
- 设置整理：删掉"使用提示"；"所有文件访问权限/媒体管理权限"提到文件操作大类顶部；导入/导出应用数据移到大类底部；"默认界面"改名"主页"并移到主题大类；删掉"默认排序方式"；打开"长快进"后视频大类里才出现"长快进长度"。
- P 页的置顶文件夹固定为本地 `Pictures/Pixiv`（不再跟着归档页的"来源目录"改动而变），并在该文件夹封面左上角加了 Pixiv 标记；文件夹名比较改为忽略大小写。
- P 页不再无谓地重新扫描：只有真正动过文件的页面（归档、回收站/重复清理、删除、移动、复制）退出时才重新走一遍 SAF；只是进归档页看一眼再返回不会触发扫描。

## v1.2.24 - 2026-09-18（小窗缩小只在多任务页）

- 删掉"小窗在后台缩小"开关和它那条"退到后台就缩小"的行为：进入小窗时、切到桌面或别的应用时，小窗保持你设定的大小，不会再自己缩一下。
- 缩小改成针对系统多任务页：手机显示任务管理页时小窗自动缩到屏幕短边的 42%（上限 220dp），离开多任务页后保持缩小；**单击缩小后的小窗就恢复到你之前的大小**，控件也重新显示。
- 缩小状态下只拖得动、改不了大小（角点挨得太近），也不会误触按钮；单击是"还原"，不会顺手把控件切走。
- 说明：系统没有给后台应用"多任务页已打开"的公开回调（AOSP 里多任务页不走 homekey/assist 那条广播），所以这一版是按平台仍暴露的任务列表识别多任务页，并配合系统广播。如果你的机器上它没有缩小，把手机连上，我用日志实测这台机器给什么信号再改。

## v1.2.23 - 2026-09-18（设置二级返回）

- 设置二级页面的返回手势又跑偏：二级页的"当前打开了哪一组"状态原本只存在设置页内部，应用层的返回处理看不到它，于是手势先被"回到主页"的规则吃掉了（顺带还会清掉相册页的文件夹、搜索和筛选状态）。现在这个状态提升到应用层，返回处理第一步就是关掉二级页面，任何入口进来都只会回到设置列表；另外离开设置标签再回来时会重新回到设置列表首页。

## v1.2.22 - 2026-09-18（设置二级返回）

- 设置二级页面的系统返回手势修好：上一轮我重建设置页时把 `BackHandler` 一起删掉了，返回因此落到应用层，会退出设置甚至退出应用。现在二级页面重新接管返回：先回到设置主页。

## v1.2.21 - 2026-09-18（启动加载圈）

- 打开应用时的加载圈已隐藏：相册页与时间轴在"库还没读完"时不再显示居中进度圈（改为空白，用户主动下拉刷新时仍会出现下拉指示）。P 页也不再因为后台遍历而显示加载圈（它本来就是边扫描边把文件夹流式显示出来），但仍然保留"库刷新"时的下拉指示。

## v1.2.19 - 2026-09-18（小窗缩放锚点）

- 悬浮小窗缩放恢复为"按所拖的角固定对角"：拖右下角固定左上角、拖左下角固定右上角、拖右上角固定左下角、拖左上角固定右下角。上一版把它统一成"永远固定左上角"是我理解错了；现在配合"未固定一侧的可用空间"限制，被固定的角不会再被推出屏幕。

## v1.2.17 - 2026-09-18（壁纸在更新后丢失）

- 应用更新（覆盖安装）后壁纸丢失：系统在包被替换时会解绑我们的动态壁纸服务，之前 1.2.12 又移除了"启动自动恢复"，所以壁纸回落到系统静态壁纸。现在应用会在启动时**自动重新绑定**已应用的壁纸（静态或动态、单个文件或队列都适用）。
- 恢复的安全前提：只有当**当前静态壁纸仍然是我们当时留下的那张**（比较系统 wallpaper id）才会恢复；如果你自己换过壁纸，id 会变，我们就绝不触碰。这样既修好了"更新后壁纸丢失"，也不会再覆盖你手动设置的壁纸。
- 说明：从零重新安装（先卸载）会清空应用数据，壁纸队列文件与记录一并消失，这种情况无法恢复；覆盖更新（数据保留）已可自动恢复。

## v1.2.16 - 2026-09-18（1.2.15 反馈修复）

- 归档页多选下的"移动/复制/删除"失效：归档页是以"整页替换 + 提前 return"的方式渲染的，应用级的**目标文件夹选择页**和**删除确认弹层**在它打开时根本没有被组合出来。现在这三个动作会先退出归档页，再执行（归档扫描结果保留在会话里，返回即可继续）。
- 播放器滑块中心对齐：轨道按 Material3 的滑块内缩（两侧各半个滑块）计算进度分界，之前按整条宽度计算，所以分界与滑块中心差半个滑块。
- 悬浮小窗改变大小不再移动窗口：取消"固定对面角"的反向位移，统一改为**左上角固定**，只改变尺寸。
- 小窗后台缩小改为可选项（设置 → 视频 → "小窗在后台缩小"，默认关闭）：之前进入小窗后应用退到后台就立刻缩小，属于误伤；现在由你决定是否在后台/多任务时缩小。

## v1.2.15 - 2026-09-18

- 工具箱主页去掉顶栏：该页没有搜索框、页面切换和菜单，顶栏只剩标题；现在整条去掉，页面自己处理状态栏内边距。
- 设置页重构完成（这次用"整块搬运"的方式重建，不再手改括号）：主页只列 8 个大类行，每个大类的设置项只出现在它自己的二级页面；二级页面的内容全部保留（主题 8 项、文件操作 11 项、显示 6 项、视频 13 项、滚动条/幻灯片/缓存/关于 各 3 项）。

## v1.2.14 - 2026-09-18（1.2.13 反馈修复）

- 冷启动的加载圈：P 页加载本地缓存快照时不再点亮 `pixivPageRefreshing`（那个标志只用于"真正走 SAF 遍历"），所以打开应用不会再出现加载圈。
- 播放器滑块：滑块直径改为与进度条宽度一致（12dp，描边 3dp），隔断仍为 3dp。
- 小窗手势彻底分区：只有**四个角 44dp 的方块**触发缩放，其余所有位置（含四条边）都是移动，不再有重叠判定。
- 小窗在后台/多任务时缩小：应用离开前台时把窗口缩到屏幕短边的 42%（上限 220dp），回到应用时恢复原尺寸。
- 进出文件夹：旧页面在 1ms 内透明（之前 `ExitTransition.None` 会让旧页面在整个 260ms 共享元素动画期间保持可见），共享元素的飞入动画保留。
- 设置页恢复并修正：主页只有 8 个大类行；主题等每个大类的项目只在各自的二级页面（上一版由于分组括号错误导致二级页面为空），系统返回手势先退回设置主页。

## v1.2.13 - 2026-09-18（1.2.12 反馈修复）

- 播放器滑块：只保留"进度条宽度 12dp"这一项改动，滑块与隔断的全部几何回退到上一版（16dp 滑块 + 4dp 白色描边、3dp 隔断）。
- 设置页重新生成分组结构：主页只列 8 个大类行，每个大类的项目只出现在它自己的二级页面里（修掉"显示/视频 被收进别的二级页面"的问题）。
- 设置二级页面支持系统返回手势：返回先退出二级页面回到设置主页，而不是直接离开设置。
- 悬浮小窗改变大小时，除固定对面角/边之外，还按"未被固定一侧的可用空间"限制尺寸，避免窗口被推到屏幕外看起来乱动。

## v1.2.12 - 2026-09-18（1.2.11 反馈修复）

- 共享元素过渡回来了：进出文件夹时封面图仍然会缩放平移飞到新位置（260ms），但**旧页面在新页面出现的同一帧就消失**（淡出改为瞬时），所以既保留了过渡效果，也不再看到上一个页面的残留。
- 小窗基础尺寸按屏幕**短边**计算，横屏与竖屏进入时大小一致（之前用当前宽度，横屏会明显更大）。
- 小窗不再因为进入后台管理/多任务界面而缩到最小：屏幕尺寸改用 `WindowManager.currentWindowMetrics` 的显示边界，而不是会随多任务界面变小的配置尺寸。
- 播放器滑块按你的规格重做：外圆 12dp（等于进度条宽度）**透明**、内圆 6dp 白色（同心圆）；已播放进度条在与滑块相接处提前 6dp 结束，**滑块下方不再绘制任何东西**（连透明层也没有），因此左右两侧都是干净的隔断。
- 不再擅自替换壁纸：启动时的壁纸自动还原已移除（备份仍保留，可在壁纸管理页手动应用）。之前它会把用户自己设的静态壁纸悄悄换成队列里的动态壁纸，动不了时还会留下一个死壁纸。
- 设置页彻底折叠：主页只显示 8 个大类行（主题、文件操作、显示、视频、滚动条、幻灯片、缓存、关于），每个大类点进去是独立页面，且**子页面只包含该大类的设置**（主题不再出现在其他子页面里）。

## v1.2.11 - 2026-09-18（1.2.10 反馈修复）

- 文件夹切换的"残留"定位到共享元素动画：相册列表与文件夹网格里的同一张图共用 `media:<uri>` 这个共享元素键，切换时框架会把旧页面保留到 360ms 动画结束（这就是"上一个页面的内容没在下一个页面出现前消失"，且不限于搜索页）。现在共享元素只对"正在打开/关闭查看器的那一张"生效，页面之间的切换不再有共享动画。
- 横屏进入小窗变慢：后台化之前先锁定当前方向（横屏锁横屏、竖屏锁竖屏），避免系统在把任务切到后台时再转一次屏。
- 英语补齐（归档相关）：仓库与会话的状态文案改为按语言输出（读取来源、查询作品信息、准备画师目录、目标目录不可用、归档失败、正在准备扫描等），会话与仓库通过所有者传入当前语言。

## v1.2.10 - 2026-09-18（1.2.9 反馈修复）

- 播放器进度条改为直接照搬 Pixiv 文件归档页"单次扫描上限"那条拖动条：12dp 圆角轨道、两端各留 3dp 隔断、滑块 16dp 圆形加 4dp 描边（颜色按播放器黑白配色），不再经过公共组件，避免样式漂移。
- 悬浮小窗：按钮上的拖动现在会转交给窗口的移动/缩放逻辑（模式在按下时判定一次，缩放区与移动区不再重叠），轻点仍然触发按钮。
- 设置页二级页面在 1.2.9 已完成（主页只留主题 + 各分类一行，点进去只显示该分类）。

## v1.2.9 - 2026-09-18（1.2.8 反馈修复）

- 打开应用不再出现加载态：`MediaLibraryState` 在构造时就同步读取本地快照并立即发布，相册页第一帧就有内容；只有从未扫描过（没有快照）才会显示进度圈。
- 播放器滑块按归档页 1:1 还原：白色轨道、白色滑块（默认 16dp 隔断 + 10dp 滑块心）、3dp 隔断，与 VLC 播放器和归档页使用同一套参数，不再自己调尺寸——先照搬，之后再按需要调整。
- 小窗：悬浮窗（系统级小窗）的快进/快退/暂停整排下移（顶边距 28dp），这一次改的才是你实际在用的那个窗口；边角拖动沿用"固定对面角"的数学（左下角/底边不再带动右上角）。
- 页面切换动画提速：标签页横向过渡 380→240ms、淡入 260ms（含 18ms 延迟）→190ms、淡出 220→170ms，底栏回归 240→170ms。
- 设置页改为二级页面：主页只保留"主题"，其余分组（文件操作、显示、视频、滚动条、幻灯片、缓存、关于）各占一行并显示箭标，点进去是独立页面（带返回），不再原地展开。

## v1.2.8 - 2026-09-18（1.2.7 反馈修复）

- P 页：改为"缓存优先"——打开应用或从其他页面进入只读本地缓存快照，不再走 SAF 遍历；只有第一次（没有缓存）和显式刷新（菜单"重新扫描"、下拉刷新）才会真正扫描。
- 打开应用不再出现加载态：`MediaLibraryState.loading` 初值改回 false，有缓存快照时内容直接出现，只有库为空且尚未完成首次扫描时才显示进度圈。
- 播放器滑块：修正滑块中心与进度条的对应关系——Material3 会让进度条两端各让出半个滑块的距离，之前按整宽计算，所以隔断总是偏在滑块一侧；现在按同样的内缩计算，隔断在滑块两侧对称。
- 小窗：改变大小时不再对"被固定的一侧"做位置夹取（那正是左下角/底边拖动把右上角推走的原因），改为限制可用空间（horizontalRoom/verticalRoom），被固定的一角保持不动。
- 小窗快进/快退/暂停整体下移 14dp。
- 预览页放大后的可拖动范围改为按"实际显示尺寸"计算：比视口矮的图片不能上下拖动，上下边界不会被拉进可视区；边缘判定与缩放判定共用同一套边界。
- 仍未完成：设置页的二级页面改造（主题以外的设置改为进入独立页面而不是原地展开）。这是设置页结构级改动（约 900 行、所有分组都内联在同一个列表里），需要单独一轮重构与验证，本轮时间预算不足，已记录待办。

## v1.2.7 - 2026-09-18（1.2.6 反馈修复）

- 翻页阈值 10%。
- 预览页放大后的平移随缩放倍数放大（原来 1:1，放大到 2.5× 后手指移动同样的距离只能挪动很小一段）。
- 搜索页进出文件夹：底栏的滑出动画改为立即消失（新页面已经出现时它还在动，就是"残留"）；离开搜索页会立即回到搜索前的位置（进入搜索时记录位置，退出时下发滚动请求）。
- P 页：切换 tag/画师不再加载任何东西（标签索引只在"确实按 tag 搜索且有查询词"时才读）；进入 P 页不再触发 SAF 遍历（改为用"图片数量 + 最新修改时间"指纹判断，只有 Pixiv 目录内容真的变了才重新扫描）。
- 播放器滑块：隔断直径等于进度条宽度（12dp），滑块本体 9dp，隔断颜色与右侧未播放进度条完全一致，滑块左侧同样留出隔断。
- 自制小窗：五个按键统一为同一套白色扁平图标（悬浮窗不再用系统图标——系统图标有的是描边、有的是实心，正是"不一致"的来源）；边角判定按各自半区计算，左下角拖动不再带动右上角。
- 设置页副标题精简（10 条最长的说明改为短句），减少换行。

## v1.2.6 - 2026-09-18（1.2.5 反馈修复）

- 翻页阈值下调到 12%。
- 搜索页进出文件夹不再有交叉淡入淡出（相册列表与文件夹网格同时绘制正是"闪一下"的来源），改为直接切换。
- 搜索页删除文件夹后不再跳到列表底部：锚点文件夹被删除且原索引超出新列表时保持当前位置，不再强制滚到末尾。
- 播放器滑块：滑块周围的隔断改为**与右侧未播放进度条完全同色同透明度**的填充（不再是描边感的白圈）。
- 小窗：内置小窗（含播放器内的迷你窗口）的按键去掉黑色圆底，与悬浮窗统一为扁平白色按键；边缘拖动也能改变大小（原来只有四角能缩放），并把未拖动的一侧/一角固定。
- 回到前台不再触发全库重扫：只有检测到权限变化时才刷新（之前每次从后台回来都会重新加载）。
- 设置页可读性：行高 58→66dp，行内上下留白与说明文字字号提高，标签与说明之间间距加大。
- 清理页"清理所选"补上英文。
- 仍未完成：英语覆盖的最后一批字符串（Pixiv 归档的状态/进度文案共约 17 条），它们位于非 Composable 的仓库与会话对象里，需要把语言标志传进去，留待下一轮集中处理。

## v1.2.5 - 2026-09-18（1.2.4 反馈修复）

- 翻页阈值下调到 15%。
- 放大后终于能既平移又切图：问题出在手势识别器——`detectHorizontalDragGestures` 一开始就吞掉手势，导致放大时 transformable 拿不到事件、原图无法平移（上一版为了切图把它彻底挡死了）。现在改为手写手势：越过滑动阈值后才判定"这次是平移还是翻页"，判定为平移时**不消费任何事件**，判定为翻页时才接管，过 15% 松手即切换。
- 预览页预加载前后各两张（原来只有 +1/-1/+2），并且拖动时露出的相邻图片用缩略图尺寸渲染，避免出现空白帧。
- Tag 搜索切不回画师：切换标的可点区域原来只有约 27dp 高，很容易点不中。现在可点区域固定 48dp（外观不变）；同时 P 页菜单新增"按画师搜索 / 按 Tag 搜索"作为不变路径。
- 搜索页进出文件夹的上下晃动：让文件夹 `AnimatedContent` 始终以页面尺寸测量（`fillMaxSize`），容器不再跟随两种内容的高度变化。
- 播放器进度条按归档页样式重做：条、滑块、隔断都与归档页一致（白色圆点滑块、3dp 隔断、隔断显示未播放轨道色）。
- 壁纸裁剪页边框拖动的判定带放大到原来的 1.8 倍，边框外侧也能抓住。
- 小窗按键：内置小窗的按键去掉了黑色圆底（这是"按键都没改"的原因，改动只在悬浮窗上），现在两个小窗都是同一套扁平白色按键；角点拖动改为两轴位移的平均投影，缩放更均匀，并固定所拖角对面的角。

## v1.2.4 - 2026-09-18（1.2.3 反馈修复）

- 翻页阈值改为 20%（0.2 × 视口宽度）。
- 放大后的切图终于能用了：以前图片在边缘停住、页面也不再动，所以永远切不了下一张。现在放大状态下继续往边缘方向拖，页面会接管这次拖动并带出相邻图片，松手过 20% 即切换，切换后自动回到 1×。
- 幻灯片手动滑动不再闪一下：滑动过程中暂停自动播放计时（避免两处同时换页），幻灯片换页也不再走共享元素动画。
- Tag 搜索可以切回画师：切换搜索模式时会退出当前文件夹（在文件夹里顶栏显示文件夹名，切换标随之消失，正是"切不回去"的原因）；P 页在搜索状态下仍保留切换标。
- 下拉刷新变成一次性信号：新增判定，同一个请求只会被一个网格消费，进入/退出页面不再重复触发下拉加载；点击其他底栏图标在到达后也不会再触发。
- 库页面（相册/时间轴）在搜索状态下不再显示图片/视频切换标。
- 搜索页进出文件夹的闪烁：撤销上一版"重建列表状态"的做法（重建会导致整页重绘），改用 `requestScrollToItem` 在下一帧测量时直接落位，配合已去掉的尺寸动画。
- 播放器滑块：滑块周围的隔断不再是纯白，改为与右侧未播放进度条完全同色同透明度（深色圆环 + 白色芯）。
- 壁纸裁剪页：底部为角度尺预留 76dp，角度调整不再遮住原图。
- 首次进入显示加载圈：`MediaLibraryState.loading` 初值改为 true，第一帧就是加载态。
- 小窗：监听配置变化并重新夹取位置（横屏后窗口曾落到可视区域之外，看起来像"不显示"）；所有按键统一为 46dp、无背景、无阴影、同样的白色；改变大小改为"离心变大、向心变小"，不再出现两个方向都变小。

## v1.2.3 - 2026-09-18（1.2.2 反馈修复）

针对 1.2.2 在真机上暴露的 12 项问题做了修正。

- 启动时不再自行打开系统壁纸界面：还原壁纸队列改为静默应用（`openSettings = false`），只有用户主动应用时才跳转系统壁纸预览。
- 翻页手感：换页阈值从 50% 降到 25%；吸附动画结束后不再多走一次滑动（交接帧用 `LaunchedEffect(currentIndex)` 复位，滑动手势自己的动画就是最终动画）。
- 幻灯片：从队列进入预览/全屏后保持队列本身作为播放列表，手动滑动不再把它换成整个图库（这正是"滑不动"和"一滑就终止播放"的原因）。
- 底栏图标二次点击改为模拟下拉刷新：新增 `pullRequestToken`，相册/时间轴重新播放下拉动画后再加载，不再后台静默刷新。
- Pixiv 页在输入 Tag 查询（顶栏出现返回箭头）时仍显示画师/Tag 切换标，可以切回画师模式。
- 搜索页进出文件夹的过渡不再上下位移：文件夹容器的尺寸变化不再参与动画（`SizeTransform` 用 snap）。
- 进度条滑块周围不再有黑块：整条轨道先按未播放颜色铺满，滑块圆环也用未播放色。
- 双击放大后可以拖动越界切换上一张/下一张：改为按"越界推动的距离"累计判定（超过视口 12% 生效），不再依赖被阻尼后的偏移量。
- 首次进入不再闪空状态或权限提示：第一次扫描完成前一律显示加载态（相册页与时间轴都把加载判断提到权限判断之前）。
- 小窗：回到全屏键与关闭键贴到角落（2dp 边距），暂停/快进/快退键放大到 52dp，所有按键统一为白色无底色；点击空白或 3 秒无操作会隐藏控件；回到全屏会重新把应用任务带回前台，因此能正常回到播放器；开启"进入后台自动暂停"时关闭小窗会暂停播放；窗口继续使用应用级 Context，并且在后台 Activity 被系统回收时不再跟着消失。
- 续播更顺：`setMediaItems` 已经带上了保存的进度，首次媒体项切换不再重复 seek（那次多出来的定位会让画面卡一下）。

## v1.2.2 - 2026-09-17（浏览与播放体验修复）

本轮处理了 13 项来自实际使用的反馈，集中在看图、播放和小窗。

看图：

- 预览页和全屏页横向滑动时图片跟随手指移动，相邻图片从边缘一起被拖进来；松手时过中线才切换，没过中线回弹到原位（以前是图片不动、只在松手后判断一个固定距离就切换）。
- 双击放大后，把图片拖过边缘再松手会切到上一张/下一张；切换后自动回到未缩放状态。
- 进入文件夹不再继承上一次的滚动位置，文件夹总是从顶部开始。
- 从搜索页进出文件夹的滚动恢复改为在列表创建时应用，不再先显示原位置再跳一下。

界面：

- 图片/视频切换标只在相册与时间轴的主页显示，进入文件夹后由文件夹名取代。
- Pixiv 页的画师/Tag 切换标移到标题位置（原来在右侧动作区）。
- 首次进入应用时，在第一次扫描完成前显示加载状态，不再先闪一下"还没有图片"。
- 播放器进度条滑块的左右空缺改为未播放部分的颜色，不再露出黑底。

小窗与播放：

- 小窗拖动边框改变大小（保持 16:9），拖动中间移动位置；按键去掉黑色底背，回到全屏移到左上角、关闭移到右上角、暂停/快进/快退居中。
- 进入小窗时应用退到后台，只保留小窗。
- 小窗使用应用级 Context，切到后台后不再随 Activity 一起消失。
- 从小窗回到全屏时重建视频输出面，画面不再只剩声音。
- 修复播放进度记忆：写入用的是 `Uri.hashCode()`，读取用的是 `uri.toString().hashCode()`，两边永远对不上；现在统一到同一个键，并在切换视频时也会续播。

## v1.2.1 - 2026-09-17（工程与产品化收口，未重新构建 APK）

版本号从 `1.1.92` 提升到 `1.2.1`（`VERSION_CODE` 171）。本轮按 [产品成熟度全盘复盘](docs/product-readiness-review.md) 的清单做了一次集中收口：功能行为基本不变，改动集中在发布链路、合规、数据安全、CI 与清理。仓库内 `app/release/` 已不再纳入版本控制；`app/release/app-release.apk` 已更新为本轮签名构建的 1.2.1（arm64-v8a 分包）。

- APK: `app/release/app-release.apk`（arm64-v8a 分包，1.2.1）
- SHA-256: `90B8F82C78884C76575190E70C7D17FF1FEBB45867745A6F0AD6BA32F361ED8E`

发布与更新：

- `ALBUM_UPDATE_URL` 从 `main` 分支上的 `output-metadata.json` 改为 `releases/latest/download/album-update.json`，不再读取会过期的分支文件。
- 新增 `scripts/write-update-manifest.ps1`：从 `version.properties` 生成更新清单（含 `versionCode`/`versionName`/`downloadUrl`/`notes`），发布时作为 Release 资产上传；新增 `RELEASING.md` 记录完整发布流程。
- 更新检查支持 GitHub Release 响应（`tag_name` + assets）与语义化版本比较，并新增"更新源落后于已安装版本"的明确提示，不再把过期清单显示成"已是最新版本"。
- 版本号统一到 `version.properties`，README 只声明"下一个发布版本"。

合规与隐私：

- 新增 `THIRD_PARTY_NOTICES.md` 与应用内"设置 → 关于 → 开源许可"页，随包提供 LGPL-2.1、Apache-2.0、OFL-1.1 全文；内置 10 款字体的版权声明直接从包内字体文件提取。
- `allowBackup` 改为 `false`，与既有的"排除全部数据"规则保持一致；隐私政策补充"无云备份""回收站在应用私有目录、卸载即删除"。
- 回收站页面与设置项明确提示卸载/清除数据会删除回收站内容。

用户数据：

- 收藏改为带稳定身份键：文件改名、移动或被 MediaStore 重新索引后，收藏会自动重新指向新 URI；歧义匹配保持原样，不会张冠李戴。
- 新增应用数据导出/导入（设置 → 文件操作），覆盖收藏、壁纸与幻灯片队列、排除文件夹和偏好设置，不包含媒体、缩略图与 Pixiv 登录信息。

新能力与体验：

- 支持系统分享入口：其他应用可以把照片/视频"分享到 Album"，随后在目标文件夹页面选择导入位置。
- 启动图标改为自适应图标（清单此前指向旧 drawable，导致自适应图标与 Android 13+ 主题图标从未生效），并新增单色主题图层。

工程与质量：

- lint 配置化：新增 `app/lint-baseline.xml`，开启 `warningsAsErrors` 与 `abortOnError`，新告警会让构建失败。
- CI 增加 API 30/35 模拟器仪器测试 job、报告上传与 Dependabot 配置。
- 依赖升级：`core-ktx 1.19.0`、`lifecycle 2.11.0`、`activity-compose 1.13.0`、`documentfile 1.1.0`、`exifinterface 1.4.2`、`espresso 3.7.0`；`compileSdk` 升到 37，`targetSdk` 保持 36；`resourceConfigurations` 迁移到 `androidResources.localeFilters`。
- 国际化收敛为单一字典（删除 `SettingsScreen` 里 109 行的第二份映射），新增 `TranslationCoverageTest` 校验每个 `appText` 字面量都有英文；该测试上线时抓出并清除了编辑器裁切比例弹窗中三段乱码文案。
- 清理：删除 92 行注释掉的编辑器组件、`MediaLibraryState` 中被注释的旧实现、`view_html_video_player.xml`、10 个模板启动图标和两个 Android Studio 模板测试；修复悬浮小窗的无障碍 `performClick` 问题。
- 单元测试从 66 个增加到 83 个：新增更新检查 8 个、收藏身份 8 个、翻译覆盖 2 个，删除 1 个 Android Studio 模板测试。

仍未完成、需要单独排期的部分（字体子集化、文案迁移到资源、ViewModel/DataStore、大屏布局、TalkBack 实机验收、崩溃上报、性能基线）与原因见复盘文档第 5 节。

验证：`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleRelease` 通过；仪器测试在 CI 的模拟器 job 中运行。

## v1.1.91 - 2026-09-16 (local signed build)

Structural cleanup: dead code removed, duplicated code shared. Net -2355 lines with no behaviour change.

- Deleted `HtmlVideoPlayer.kt` (661 lines) and `SelectionScreen.kt` (461 lines): neither was referenced anywhere since the viewer and the in-place selection replaced them.
- Deleted the unreferenced `NativeVideoPlayer` (896 lines) and `SlideshowOverlay` (121 lines) from `MediaViewer.kt`, plus `SimilarContent`, `VaultRatioInputSheet`, `renderDoodleComposite`, `EditorStageColor`, `htmlOrientationIcon`, `HtmlShareIcon`, `HtmlMiniWindowIcon` and the unused `VaultGreenDark` colour.
- Removed a commented-out editor popover that had been left behind with mangled text.
- The five copies of the pull-to-refresh scaffolding (three grids in the album screen, two in the timeline) are now one `rememberPullToRefresh()` helper: the distances, the animation and the "loading alone must not move the content" rule live in one place.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `43A97129BF16C445B7DE710DADFE7C4F6E3A8D813EA6F11E2F46EC43F52258DD`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. The phone was in use during this round, so the pull-to-refresh refactor is compile/lint verified but not re-tested by hand.

## v1.1.89 - 2026-09-16 (local signed build)

Consistency pass over the whole UI, following the review of what feels intuitive and visually coherent.

- One modal language: the wallpaper settings and slideshow settings are bottom sheets now, with the same frame, the same rows and the same apply pill as every other sheet (they were centred Material dialogs, the only ones in the app).
- One vocabulary for the top-bar action: "清除" (which also meant "clear the search") is now "移出队列" for the queue action, and the empty states say "清除搜索"; the menu entry that walks storage is "重新扫描" while pull-to-refresh and tapping the current tab stay "刷新".
- Feedback: a refresh started from the UI now shows the same pull-to-refresh indicator the gesture uses, instead of being invisible on a full library; the extra toasts were removed.
- The bottom-bar label follows the content in Chinese too (相册 ↔ 视频), matching its icon and the English behaviour.
- Back buttons are named after the page they return to (返回工具箱, 返回队列, 返回上级文件夹, 退出搜索) instead of always "返回相册".
- Empty states speak with one voice: 还没有图片 / 还没有视频, 文件夹为空, 没有匹配的内容, plus the queue messages.
- Semantic text styles (`VaultText`) replace the scattered hard-coded font sizes in the top bar, the sheets and the settings rows.
- New one-time "使用提示" sheet listing the gestures that are otherwise invisible (long press to select, long-press to reorder, viewer and player gestures, tab-tap to top/refresh); it is also reachable from Settings.
- English coverage checked properly this time: 362 strings are mapped and only 12 were missing (the review's "one third" figure came from a bad parse of the mapping file); those are added now.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `3AFCBF9584032F80C20B4FB4732DF1BD998A644F5DF20530D862D7B7E721E708`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. On the test phone the hints sheet renders and opens from Settings, and the wallpaper settings sheet now matches the shared sheet shape.

## v1.1.87 - 2026-09-16 (local signed build)

- The P page's artist/Tag switch moved to the right side of the bar, next to the favourite and overflow buttons, where the search it controls lives; the page keeps its "Pixiv" title on the left.
- The slideshow's play button now opens the viewer straight into full screen, so playback starts immediately. Opening a picture from the queue still goes to the preview page first, and tapping it enters full screen and starts the slideshow there.
- The slideshow settings dialog is now the same shape as the wallpaper settings sheet: an AlertDialog with 幻灯片设置 as its title, value rows that open the wheel sheet (interval, animation), a switch for shuffle, and 应用 / 取消 buttons.
- Tapping the bottom bar icon of the current page refreshes when the page is already at the top; "at the top" is now tracked per visible grid (the folder list and the grid inside a folder), which is why the refresh never fired before, and a short toast makes the refresh visible.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `ADBC62CF65E3395EEAB152FB9018B63DC0239082E2779D304EE98D28EA3B70E6`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. On the emulator the P page switch now sits at x=936..1034 (was x=169..267) with the title on the left, and the slideshow settings dialog renders with the shared dialog components.

## v1.1.86 - 2026-09-16 (local signed build)

- The P page no longer starts a SAF walk every time it is opened: entering it again keeps the snapshot that is already loaded, and a walk only starts on the first visit, on an explicit refresh, or when the local library changes. Reload requests are debounced so a library refresh and an explicit request no longer cancel and restart a walk that just began.
- Finishing an archive releases the archive page straight away and re-reads the library in the background, then reloads the P page once with the refreshed index in place.
- The P page's artist/Tag switch is now the same mark-and-current-state control as the photo/video pages, in the same place (top left, theme colour); it is only replaced by the folder name inside a folder.
- Tools has one Pixiv entry again: the separate "文件归档" entry is gone and the P page entry uses the archive's icon and wording. Entries are looked up by id, so a stored order that still lists the removed entry no longer shifts every label.
- Slideshow: the system back gesture returns to the queue page (starting playback no longer closes it), tapping a picture in the queue opens it in slideshow mode, the queue menu gained 设置 with every slideshow option (interval, animation, shuffle), 自适应 now really is the masonry layout used by the timeline, 排布方式 gained the media/folder scope choice, playback follows the chosen sort (it used to play the unsorted queue, which made every sort look broken), and the empty message is just "幻灯片队列为空".
- The wallpaper page lost its search field: media is added from the multi-select menu, and static/live moved to the same title switch as the other pages (it also shows on pages that have a back arrow now).
- Tapping the bottom bar icon of the current page jumps to the top in one step instead of animating up in two, and tapping it again while already at the top refreshes that page.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `008465E1CF3C00E424AAB4787EAE95C1D6AB53151BB3F381ED8116588BA589DB`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug` and `assembleRelease` passed. On the test phone the Tools page, the P page switch, the wallpaper page without a search field, the slideshow settings sheet and the shortened empty message were all checked.

## v1.1.84 - 2026-09-16 (local signed build)

- The picture a static wallpaper queue is showing is now also handed to the system as an ordinary wallpaper (before the live wallpaper is bound), so a package replacement that drops the live wallpaper leaves the user's image on screen instead of the stock wallpaper. The still image is only written when no Album live wallpaper is bound, because setting one replaces the other. Video queues get the first frame of the clip as the same fallback.
- The applied queue is now backed up on launch when the backup file is missing, so a queue applied before the backup existed can still be restored after an update.
- A video's length is shown again in the player for MPEG program streams: the MPEG-1 extractor reads the clock reference of the first and the last pack header, reports the duration and provides a constant bit rate seek map so the progress bar works too. Verified against an independent SCR calculation (5.365 s) and on the user's own 64 minute `.mpg`.
- The page action button sits in the same place on every page: the play button of the slideshow queue and the apply button of the wallpaper page now share one trailing group with a fixed gap and button size.
- Deleting inside a search page keeps the page in place: the scroll restore anchors on the item (folder name or media URI) that was on screen instead of a stored index, so a shorter list no longer clamps it to the bottom. Closing a folder opened from a search anchors the same way.
- APK: `app/release/app-release.apk` (arm64-v8a split)
- SHA-256: `E99BF0225079A80F291CF9365B818D5F6F7DF72F4E7A33925CE068186171B7B4`
- Verification: `lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease` and the `MainPlayerContainerTest` instrumentation test passed. On the test phone the user's `.mpg` shows `64:34` and a DivX `.avi` shows `30:33`, both in the main player, and the slideshow play button now sits at exactly the same position as the wallpaper page's apply button (both at x=1088..1184 with the same overflow button at x=1288..1384).

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
