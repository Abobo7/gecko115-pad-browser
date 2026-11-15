# GeckoPad 115 浏览器 (`com.mipad.geckobrowser`)

专为 **小米平板 1 (`mocha`, NVIDIA Tegra K1 ARMv7, Android 4.4.4 KitKat API 19)** 从零定制编译的纯净中文多标签平板浏览器，底层基于官方 Mozilla **GeckoView `115.0.20230726201356` (Firefox 115 ESR)**。

## 核心特性与 Android 4.4 适配要点

- **真 Firefox 115 ESR 内核 + 独立证书库**：完整支持 ES2023 JavaScript 语法、现代 CSS Grid / Container Queries；自带独立 `libnss3.so` (TLS 1.3) 与 `libnssckbi.so` 现代根证书链，不依赖 Android 4.4 过期系统根证书，原生打通 Cloudflare 验证、Pixiv、GitHub 等现代站点。
- **Tegra K1 硬件 WebRender + WebGL 2 加速**：启动前通过反射向 `GeckoRuntimeSettings` 注入底层首选项：
  - 关闭独立 `:gpu` 子进程（`layers.gpu-process.enabled = false`），规避 Android 4.4 跨进程 `Surface` 合成黑屏并节省约 55 MB 内存；
  - 注入 `gfx.webrender.all = true` 与 `gfx.webrender.software = false` 绕过 Android < 5.0 版本号黑名单，在 Tegra K1 (`OpenGL ES 3.1 334.00`) 上原生激活硬件 `WebRender` 合成及硬件 `WebGL 1 / WebGL 2`（同时支持在菜单中一键自动重启切换为 `SWGL` NEON 软件光栅）。
- **Dalvik 单 DEX 与 JNI 类加载器修复**：
  - 使用 `d8 --min-api 19` 将 GeckoView、ExoPlayer2、Kotlin 标准库、AndroidX 与业务代码收敛进单一 `classes.dex`（`6.6 MB`），避免 `:tab0..39` 内容进程 MultiDex 崩溃；
  - `assets/omni.ja` 保持 `Stored` (`0%` 不压缩) 配合 4 字节 `zipalign` 直接 `mmap`；
  - 独立引导类 `BootstrapApp` 仅引用系统引导类库，在 `attachBaseContext` 阶段注入 `SlashCompatLoader` 将 JNI `'/'` 类名规范化为 `'.'`，消除 Dalvik `dvmFindClassByName rejecting` 且不触发 `CLASS_ISPREVERIFIED` 冲突。
- **零广告、零后台常驻、4:3 平板交互**：
  - 顶部桌面级可滚动多标签栏 + 无痕模式，横竖屏切换零重载（起始页横屏 4 列 / 竖屏 3 列）；
  - 一键切换桌面版 / 移动版 UA、书签与历史记录、页内查找、全屏视频沉浸模式、第三方 Scheme（如 `ehviewer://`、`pixiv://`、`bilibili://`）跳回支持，以及继承当前登录态 Cookie 直存 `/sdcard/Download/` 的文件下载器。

## 目录结构

- `AndroidManifest.xml`：应用清单与 46 个 `GeckoChildProcessServices` 多进程服务声明
- `src/com/mipad/geckobrowser/`
  - `BootstrapApp.java`：Dalvik `SlashCompatLoader` 早期类加载器注入
  - `BrowserApp.java`：`GeckoRuntime` 单例与 KitKat / Tegra K1 WebRender 首选项注入
  - `MainActivity.java`：多标签平板顶栏 UI、横竖屏自适应与进程热重启切换
  - `SessionDelegates.java`：导航、进度、弹窗、外部协议跳转、文件选择与下载回调
  - `HomePageBuilder.java`：原生零延迟快捷导航起始页
  - `BrowserStore.java` / `BrowserTab.java`：标签状态持久化、书签、历史与配置存储
  - `DownloadHelper.java`：带登录态流式下载器
  - `UiKit.java`：纯 `Canvas` 抗锯齿矢量图标绘制（不依赖高版本字体符号）
- `tools/GenIcon.java`：构建期多分辨率启动图标生成器
- `build.sh`：一键拉取官方 AAR、编译资源、D8 脱糖、4 字节对齐与 V1+V2 签名脚本

## 编译与安装

```bash
bash build.sh
adb -s 192.168.1.185:5555 install -r dist/GeckoPad_115.0_MiPad_KitKat_arm.apk
```
