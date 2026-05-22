# WebHomeTV

WebHomeTV 是基于 FongMi / CatVod 生态二次开发的 Android 影音应用，保留原有点播、直播、Spider、解析、投屏、本地 HTTP 服务等能力，并重点增强了 **WebHome 自定义首页**、**App Native SDK**、**网盘链接检测** 和 **Nostr/TMDB 推荐首页**。

这个项目的核心目标不是替换 CSP/Spider 体系，而是让 CSP 站点首页可以变成一个真正可开发的网页应用：开发者可以用 HTML/CSS/JavaScript 定制首页，再通过 App 暴露的 Native 能力完成搜索、播放、跨域请求、资源代理、最近观看、网盘检测和状态同步。

## 二开重点

### 1. CSP 站点支持自定义 WebHome 首页

https://github.com/user-attachments/assets/7249b787-a720-406c-8365-acaa0995cb6a

站点配置新增首页字段，切换到该 CSP 站点时可以直接显示自定义网页：

```json
{
  "key": "webhome",
  "name": "WebHome",
  "type": 3,
  "api": "csp_Xxx",
  "homePage": "./nostr.html"
}
```

兼容字段：

- `homePage`
- `home_page`
- `webHome`
- `web_home`

如果配置文件来自在线地址，`./nostr.html` 会按配置文件 URL 做相对路径解析，方便把配置和首页 HTML 放在同一目录。

### 2. WebHome Native SDK

WebHome 页面会注入 `window.fongmi` 和简写 `window.fm`，网页可以直接调用 App 能力。

常用能力包括：

| 能力 | 说明 |
| --- | --- |
| `fm.req(url, options)` | 使用 App 内置 OkHttp 请求接口，绕过浏览器 CORS 限制 |
| `fm.res(url, options)` | 生成本地资源网关地址，给图片、视频、字幕等 DOM 资源使用 |
| `fm.play(url, title, options)` | 播放直链或 `push://` 地址 |
| `fm.vod(siteKey, vodId, title, pic)` | 打开 App 原生 CSP 详情/播放链路 |
| `fm.search(keyword, { direct })` | 调用 App 搜索，支持直接进入搜索结果 |
| `fm.history()` | 读取最近观看记录 |
| `fm.stat()` | 获取当前播放状态、进度、时长等信息 |
| `fm.ctrl(action)` | 控制播放、暂停、停止、上一集、下一集等 |
| `fm.check(items)` | 调用内置网盘链接有效性检测 |
| `fm.config()` | 获取当前配置和网盘检测开关状态 |
| `fm.site()` | 获取当前站点信息 |
| `fm.device()` | 获取设备信息 |
| `fm.cache` | 提供 WebHome 可用的本地缓存能力 |
| `fm.back()` / `fm.reload()` | 处理网页返回和刷新 |

这套 SDK 的设计目标是让 WebHome 开发者少依赖浏览器私有行为，尽量通过 App 的 Native 能力完成网络、播放和状态管理。

### 3. CORS 和资源加载增强

普通网页 `fetch()` 会受浏览器 CORS 限制。WebHomeTV 提供两种内置能力：

- `fm.req()`：用于接口请求，返回 JSON、文本、二进制等数据。
- `fm.res()` / `/webResource`：用于图片、视频、字幕、CSS 背景等资源加载。

这可以处理常见跨域、Header、Cookie、资源防盗链等问题。WebHome 页面不需要要求用户安装浏览器插件或关闭系统 WebView 的安全策略。

### 4. 透明背景 WebHome

App WebView 已支持透明背景，WebHome 页面可以让 App 壁纸透出，适合做沉浸式影视首页。

开发时建议：

- `html`、`body` 和主容器保持透明。
- 卡片、按钮、输入框、Tab、弹层使用半透明中性背景。
- 详情页、剧情页等全屏浮层打开时隐藏底层页面，避免多层内容叠在一起。
- 电脑浏览器调试可以保留兜底背景，App 内使用透明背景。

### 5. WebHome 路由、返回、刷新和恢复

WebHome 支持多层网页状态：

- 使用 History API 管理详情页、搜索页、弹层等路由。
- App 返回键会优先让网页内部回退，再退出 WebHome。
- `fm.reload()` 可以刷新当前 WebHome，而不要求用户重启 App。
- App 从后台或锁屏恢复时会派发 `fmresume` 事件，网页可以保留当前页面状态并补偿刷新数据。

### 6. 内置网盘链接检测

设置页新增“网盘检测”开关。开启后，WebHome 或自定义工具可以调用 App 内置检测能力。

WebHome SDK：

```js
const config = await fm.config();
if (config.driveCheck) {
  const result = await fm.check([
    { url: "https://www.aliyundrive.com/s/xxx", type: "aliyun" },
    { url: "https://pan.quark.cn/s/xxx", type: "quark" }
  ]);
}
```

本地 HTTP API：

```http
POST http://127.0.0.1:{port}/check/links
Content-Type: application/json

{
  "items": [
    { "url": "https://pan.quark.cn/s/xxx", "type": "quark" }
  ]
}
```

同时兼容：

```text
/api/check/links
```

检测接口支持批量提交，内部会按合理并发处理。WebHome 开发时建议只检测用户当前可见范围内的资源，并且只检测 App 支持的网盘类型，避免无意义请求和界面跳动。

### 7. PanSou 网盘搜索集成示例

`demo/nostr.html` 的详情页集成了 PanSou 类搜索能力，支持：

- 自定义盘搜服务地址。
- 账号密码认证。
- 自定义 TG 频道。
- 按网盘类型分 Tab 展示。
- 对支持的网盘类型调用 App 内置检测。
- 只检测可见范围内的结果。
- 检测结果用状态圆点表达。
- 点击资源后生成 `push://` 播放地址交给 App 播放。

PanSou 搜索结果可能是异步补充的，示例页会轮询合并新增结果。

### 8. Nostr + TMDB 推荐首页示例

`demo/nostr.html` 是一个完整的 WebHome 首页示例，不只是 SDK demo。它包含：

- TMDB 今日趋势、电影、剧集、动画等榜单。
- 中国大陆内容优先的推荐分区。
- 瀑布流卡片布局，移动端一行 3 个，宽屏自动显示更多列。
- Nostr 去中心化偏好同步。
- 用户搜索、点击、播放时长等行为可参与推荐计算。
- 每个用户对同一影视条目的热度去重，避免重复点击无限累加。
- 状态面板展示 SDK、TMDB、Nostr、PanSou、发布状态和身份信息。
- 支持清理本机测试数据和发布 Nostr 删除事件。

示例页使用 TMDB API，请自行替换或管理 API Key，并遵守对应服务条款。

### 9. App 行为调整

- 启动 App 不再自动弹出版本更新窗口。
- 用户仍可在设置页手动点击版本检查。
- 手机端和电视端都保留原有 FongMi/CatVod 能力。
- WebHome 能力优先面向手机端体验，同时兼顾电视遥控器焦点和返回操作。

## Demo

仓库内置两个 WebHome 相关示例：

| 文件 | 说明 |
| --- | --- |
| `demo/nostr.html` | 正式推荐首页示例，集成 TMDB、Nostr、PanSou、网盘检测、透明背景 |
| `demo/check.html` | 网盘检测能力测试页 |

配置示例：

```json
{
  "sites": [
    {
      "key": "webhome_demo",
      "name": "WebHome 推荐",
      "type": 3,
      "api": "csp_Demo",
      "homePage": "./nostr.html"
    }
  ]
}
```

如果你的配置文件和 `nostr.html` 放在同一个服务器目录，`homePage` 可以直接写相对路径。

## 构建

环境要求：

- JDK 17
- Android Gradle Plugin 对应的 Android SDK
- Gradle Wrapper 使用仓库内置 `gradlew`

常用构建命令：

```bash
bash gradlew assembleMobileArm64_v8aRelease
```

其它常见变体：

```bash
bash gradlew assembleMobileArmeabi_v7aRelease
bash gradlew assembleLeanbackArm64_v8aRelease
bash gradlew assembleLeanbackArmeabi_v7aRelease
```

Release 签名读取根目录 `local.properties`：

```properties
storeFile=/path/to/keystore.jks
keyAlias=your_alias
storePassword=your_password
```

APK 输出路径以 Gradle 实际输出为准，常见路径：

```text
app/build/outputs/apk/mobileArm64_v8a/release/mobile-arm64_v8a.apk
app/build/outputs/apk/leanbackArm64_v8a/release/leanback-arm64_v8a.apk
```

## 文档

完整开发说明见：

```text
docs/应用完整开发文档.md
```

这份文档包含：

- App 配置字段
- Spider 开发
- JS/Python Spider 运行时
- 本地 HTTP 服务
- WebHome SDK 参数和返回值
- 透明背景实现建议
- 网盘检测 API
- PanSou 集成建议
- Nostr 首页实现要点
- 隐藏功能和使用技巧
- Android Intent、DLNA、MediaSession
- CORS、Cookie 和网络策略

## 目录结构

```text
app/       Android 主应用
catvod/    CatVod 抽象层、Spider 接口、网络和代理工具
quickjs/   JavaScript Spider 运行时
chaquo/    Python Spider 运行时
demo/      WebHome 示例页面
docs/      完整开发文档
other/     其它构建或依赖模块
```

## 开源说明

本仓库只提供技术实现和开发示例，不内置、不维护、不分发任何影视内容、播放源、资源站接口或网盘资源。项目中的搜索、播放、网盘检测、TMDB、Nostr、PanSou 等能力都需要用户自行配置合法服务和数据来源。

请遵守所在地法律法规、第三方 API 服务条款和内容版权要求。
