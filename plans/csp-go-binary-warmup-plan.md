# CSP Go 二进制自动预热方案

日期：2026-06-16

## 背景

部分接口线路依赖 Go 二进制服务。当前 App 在 WebHome 作为默认主页时，只加载 WebView 主页，不会自动执行原生 CSP 首页链路；因此某些 Go 二进制不会自动启动。用户期望即使默认进入 WebHome 主页，也能自动拉起这些 Go 二进制依赖。

## 当前链路判断

移动端 WebHome 首页加载逻辑：

- `VodFragment.loadHome()` 优先尝试 `mWeb.load(home)`。
- 如果当前首页站点配置了 `homePage` 且 WebHome 加载成功，就隐藏原生内容，不调用 `homeContent()`。
- 只有 WebHome 加载失败或站点没有 `homePage` 时，才走原生 `homeContent()`。

原生 CSP 首页链路：

- `SiteViewModel.homeContent()` 调用 `SiteApi.homeContent(VodConfig.get().getHome())`。
- 对 `type=3` CSP 站点，`SiteApi.homeContent()` 会调用 `site.recent().spider().homeContent(true)` 和 `spider.homeVideoContent()`。
- `site.spider()` 最终进入 `BaseLoader.getSpider(...)`。
- Jar CSP 的 `JarLoader.getSpider()` 会懒加载 jar、实例化 `com.github.catvod.spider.Xxx`，并执行 `spider.init(App.get(), ext)`。

因此：WebHome 默认主页不会天然触发 CSP spider 初始化；原生 CSP 作为主页时会触发。

## init 与 home 的区别

### init 预热

只执行 CSP Spider 初始化：

```java
site.recent().spider();
```

触发内容：

- 加载 jar/js/py。
- 实例化对应 Spider。
- 执行 `spider.init(context, ext)`。
- 设置 recent，让本地代理等 recent 相关逻辑能命中对应 jar。

优点：

- 成本低。
- 不请求首页接口。
- 不解析分类。
- 不影响 UI。

适用：

- Go 二进制在 `Init.init()`、Spider 构造、`spider.init()` 或 jar 全局单例里启动。
- 已确认“任意原生 CSP 被触发即可启动 Go”的情况。

### home 预热

模拟一次原生 CSP 首页调用：

```java
SiteApi.homeContent(site);
```

触发内容：

- 包含 init 预热。
- 继续执行 `spider.homeContent(true)`。
- 继续执行 `spider.homeVideoContent()`。

优点：

- 兼容性更强。
- 适合 Go 二进制在首次首页请求、分类初始化、服务探测中启动的接口。

风险：

- 成本更高。
- 可能发起网络请求。
- 可能较慢。
- 可能触发站源日志、缓存或风控。

## 推荐产品设计

新增能力名称建议：

- `CSP 启动预热`
- 或 `接口服务预热`

入口建议放在：

- 设置 -> 增强功能。
- 与 WebHome、站点注入、Proxy 壳代理附近。

用户可见模式：

- `关闭`
- `轻量启动`
- `完整唤醒`

对应技术模式：

- `off`
- `init`
- `home`

文案建议：

- 轻量启动：只初始化一个原生 CSP 接口，推荐用于自动拉起 Go 二进制服务。
- 完整唤醒：模拟打开一次原生接口首页，兼容性更强但更慢。

## 默认策略

第一版建议默认关闭，由用户手动开启。

用户开启后默认：

- 模式：`轻量启动`
- 站点选择：`自动选择`
- 执行时机：点播接口首次加载后，如果默认首页走 WebHome，则在 WebHome 首屏加载成功后延迟后台执行，避免影响首屏。
- 去重策略：同一 App 进程、同一接口配置只尝试一次；刷新 WebHome 页面不再次触发。

自动选择站点规则：

1. 从当前点播配置里找 `type=3` 的 CSP 站点。
2. 排除空站点。
3. 优先选择没有 `homePage` 的原生 CSP 站点。
4. 优先选择当前配置里的第一个可用原生 CSP。
5. 如果用户手动指定预热站点，则优先使用指定站点。

原因：

- 用户反馈“任意原生 CSP 都可以促使 Go 二进制启动成功”，因此第一版不必强制绑定到具体 Go 线路。
- 轻量启动一个 CSP 即可覆盖多数场景，成本最低。

## 配置设计

建议新增设置项：

```text
csp_warmup_enabled = false
csp_warmup_mode = init
csp_warmup_site_key = ""
csp_warmup_delay_ms = 1500
```

含义：

- `csp_warmup_enabled`：是否开启。
- `csp_warmup_mode`：`init` 或 `home`。
- `csp_warmup_site_key`：空表示自动选择。
- `csp_warmup_delay_ms`：WebHome 首页加载后延迟执行，避免抢首屏资源。

后续可扩展：

```json
{
  "cspWarmup": {
    "enabled": true,
    "mode": "init",
    "siteKey": "",
    "delayMs": 1500
  }
}
```

## 实现建议

新增工具类：

```text
app/src/main/java/com/fongmi/android/tv/api/CspWarmup.java
```

职责：

- 判断是否需要预热。
- 选择预热站点。
- 防重复执行。
- 后台执行 `init` 或 `home`。
- 输出调试日志。

核心接口：

```java
public final class CspWarmup {
    public static void schedule(String reason);
    public static void run(String reason);
    public static Site pickSite();
}
```

建议逻辑：

```java
if (!Setting.isCspWarmup()) return;
if (alreadyWarmedForCurrentConfig()) return;
Site site = pickSite();
if (site.isEmpty()) return;
Task.execute(() -> {
    if ("home".equals(Setting.getCspWarmupMode())) {
        SiteApi.homeContent(site);
    } else {
        site.recent().spider();
    }
});
```

防重复建议：

- 以当前 `VodConfig.get().getConfig().getUrl()` 或配置 hash 为 key。
- 同一个 App 进程、同一个配置只预热一次。
- 刷新 WebHome 页面不重新预热。
- 用户切换到新的接口配置后，按新配置首次加载重新预热。

## 触发位置

建议在点播配置加载完成后触发。

移动端：

- `VodFragment.loadHome()` 中，WebHome load 成功后可以 `CspWarmup.schedule("webhome")`。
- `RefreshEvent.HOME` 只刷新 WebHome 页面，不调用预热。
- 原生首页分支不用额外触发，因为原生 `homeContent()` 已经会触发 CSP。

TV 端：

- `HomeActivity` 中 WebHome overlay 加载成功后同样调用 `CspWarmup.schedule("webhome")`。

更通用的方式：

- 在 `VodConfig.load(...)` 成功回调后触发一次。
- 但需要避免原生首页场景重复预热。

推荐第一版：

- WebHome load 成功后触发。
- 因为问题只发生在 WebHome 默认主页场景。

## 日志与诊断

调试日志建议：

```text
csp-warmup schedule reason=webhome mode=init delay=1500
csp-warmup start key=xxx name=xxx mode=init
csp-warmup success key=xxx cost=120ms
csp-warmup failed key=xxx mode=home error=...
csp-warmup skipped reason=no-site
csp-warmup skipped reason=disabled
csp-warmup skipped reason=already-warmed
```

管理页或调试日志可展示：

- 是否开启。
- 当前模式。
- 自动选择的站点。
- 最近一次执行结果。

## 风险控制

- 不要默认执行所有 CSP 站点。
- 不要默认执行 `home` 模式。
- 不要阻塞 WebHome 首屏。
- 不要影响用户当前首页 UI。
- 不要静默频繁重试。
- 如果预热失败，只记录日志，不弹打扰用户的错误。

## 验收标准

轻量启动：

- 默认进入 WebHome 首页时，后台能触发一个原生 CSP 的 `spider.init()`。
- Go 二进制能自动启动。
- WebHome 首屏不被阻塞。
- 重复返回首页不会反复预热。

完整唤醒：

- 默认进入 WebHome 首页时，后台能执行一次选定 CSP 的 `homeContent()`。
- 对必须首页调用才启动 Go 的接口有效。
- 失败时不影响 WebHome 页面使用。

配置：

- 用户可以关闭预热。
- 用户可以切换轻量启动/完整唤醒。
- 用户可以选择自动站点或指定站点。

## 结论

当前问题成立：WebHome 默认主页不会自动执行原生 CSP 链路，因此不会稳定触发某些 Go 二进制启动。

建议第一版实现“启动时自动轻量预热一个 CSP”：

- 默认关闭，用户开启。
- 默认 `init` 模式。
- 自动选择第一个可用原生 CSP。
- WebHome 首页加载成功后延迟后台执行。
- 提供 `home` 模式作为兼容兜底。

这样可以满足用户自动启动 Go 二进制的需求，同时避免全量执行 CSP 带来的启动慢、网络请求和副作用。
