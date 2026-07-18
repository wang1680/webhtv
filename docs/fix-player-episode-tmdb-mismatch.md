# 修复播放页选集卡片TMDB海报匹配错误

## 问题描述

在炫彩详情模式下，播放页的选集卡片显示的TMDB海报与实际集号不匹配：

- **详情页**：显示正确的TMDB海报（如第17集显示第17集的海报）✓
- **播放页**：部分集数显示错误的海报（如第18集按顺序显示了错误的海报）✗

## 问题截图

详情页正确显示：
- 第13-17集显示对应的TMDB海报

播放页错误显示：
- 第17集正确
- 第18集显示的是错误的海报（按顺序匹配导致）

## 根本原因

### 详情页的实现（正确）

```java
// TmdbDetailActivity.java
private final Map<Integer, TmdbEpisode> tmdbEpisodes = new HashMap<>();

// 在 renderEpisodes() 中
Map<Episode, Integer> episodeNumbers = episodeNumbers(pagedDisplayEpisodes, episodes);
episodeAdapter.setItems(items, tmdbEpisodes, episodeNumbers, selectedEpisode);
```

```java
// TmdbEpisodeAdapter.java - onBindViewHolder()
int episodeNumber = episodeNumber(episode, position);
TmdbEpisode tmdbEpisode = tmdbItems.get(episodeNumber);
if (!TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, episodeNumber)) {
    tmdbEpisode = null;
}
```

**详情页使用 Map 存储 TMDB 数据，通过集号精确匹配。**

### 播放页的实现（有问题）

```java
// VideoActivity - EpisodeAdapter.java (修复前)
TmdbEpisode tmdbEpisode = item.getTmdbEpisode();  // 直接获取，没有验证
if (tmdbEpisode == null) return;
```

**播放页直接使用 `episode.getTmdbEpisode()`，这个值是在 `TmdbUIAdapter.applyEpisodeTitles()` 中按顺序设置的，当源文件集号与TMDB顺序不一致时会导致错位。**

## 修复方案

在播放页的适配器中添加与详情页相同的验证逻辑，使用 `TmdbEpisodeMatcher.shouldApply()` 验证集号是否匹配。

### 修改的文件

#### 1. Leanback 版本

**文件**: `app/src/leanback/java/com/fongmi/android/tv/ui/adapter/EpisodeAdapter.java`

```java
// 添加 import
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;

// 修改 bindCardView() 方法
private void bindCardView(@NonNull ViewHolder holder, Episode item, int position) {
    AdapterEpisodeCardBinding binding = holder.cardBinding;
    if (binding == null) return;

    // 使用集号匹配 TMDB 数据，而不是直接使用 item.getTmdbEpisode()
    int episodeNumber = item.getNumber() > 0 ? item.getNumber() : position + 1;
    TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
    if (!TmdbEpisodeMatcher.shouldApply(item, tmdbEpisode, episodeNumber)) {
        tmdbEpisode = null;
    }
    if (tmdbEpisode == null) return;
    
    // ... 继续原有逻辑
}
```

#### 2. Mobile 版本 - Horizontal Holder

**文件**: `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeHoriHolder.java`

```java
// 添加 import
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;

// 修改 initView() 方法
@Override
public void initView(Episode item) {
    // 使用集号匹配 TMDB 数据，而不是直接使用 item.getTmdbEpisode()
    int position = getBindingAdapterPosition();
    int episodeNumber = item.getNumber() > 0 ? item.getNumber() : position + 1;
    TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
    if (!TmdbEpisodeMatcher.shouldApply(item, tmdbEpisode, episodeNumber)) {
        tmdbEpisode = null;
    }
    if (EpisodeCardPolicy.shouldShowCard(useTmdbCard, tmdbEpisode != null, !TextUtils.isEmpty(fallbackStillUrl))) 
        bindCard(item, tmdbEpisode);
    else 
        bindText(item);
}
```

#### 3. Mobile 版本 - Grid Holder

**文件**: `app/src/mobile/java/com/fongmi/android/tv/ui/holder/EpisodeGridHolder.java`

```java
// 添加 import
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;

// 修改 initView() 方法
@Override
public void initView(Episode item) {
    updateLayout();
    // 使用集号匹配 TMDB 数据，而不是直接使用 item.getTmdbEpisode()
    int position = getBindingAdapterPosition();
    int episodeNumber = item.getNumber() > 0 ? item.getNumber() : position + 1;
    TmdbEpisode episode = item.getTmdbEpisode();
    if (!TmdbEpisodeMatcher.shouldApply(item, episode, episodeNumber)) {
        episode = null;
    }
    if (EpisodeCardPolicy.shouldShowCard(useTmdbCard, episode != null, !TextUtils.isEmpty(fallbackStillUrl))) 
        bindCard(item, episode);
    else 
        bindText(item);
}
```

## 修复逻辑说明

### TmdbEpisodeMatcher.shouldApply() 方法

```java
public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode, int mappedNumber) {
    if (tmdbEpisode == null) return false;
    // 如果没有有效的映射编号，放行（原有逻辑已经决定了这个 tmdbEpisode）
    if (mappedNumber <= 0) return true;
    // 如果源文件有编号，检查它是否与 TMDB 编号一致（这是真正的匹配检查）
    if (episode != null && episode.getNumber() > 0) {
        return episode.getNumber() == tmdbEpisode.getNumber();
    }
    // 源文件没编号，依赖映射编号
    return mappedNumber == tmdbEpisode.getNumber();
}
```

**核心逻辑**：
1. 获取 Episode 的实际集号（`episode.getNumber()`）
2. 如果没有集号，使用位置 + 1 作为集号
3. 验证 TMDB 数据的集号是否与 Episode 的集号匹配
4. 不匹配则将 `tmdbEpisode` 设为 `null`，避免显示错误信息

## 测试方法

1. 编译并安装修复后的应用
2. 打开一个有 TMDB 数据的剧集（如《花间令》）
3. 从详情页进入播放页
4. 查看选集卡片列表
5. 验证每一集显示的海报是否与该集号匹配

### 预期结果

- ✅ 第17集显示第17集的TMDB海报
- ✅ 第18集显示第18集的TMDB海报（而不是按顺序错位的海报）
- ✅ 所有集数的TMDB海报都与集号正确匹配

## 相关代码

### TmdbUIAdapter.applyEpisodeTitles()

这个方法负责将 TMDB 数据设置到 Episode 对象：

```java
private boolean applyEpisodeTitles(Vod vod, TmdbItem item) {
    // ... 获取 TMDB 季度数据
    List<TmdbEpisode> episodes = tmdbService.episodes(season, tmdbConfig, item.getTmdbId(), seasonNumber);
    
    for (Flag flag : vod.getFlags()) {
        List<Episode> sourceEpisodes = flag.getEpisodes();
        boolean usePosition = shouldUseEpisodePosition(sourceEpisodes, episodes);
        for (int index = 0; index < sourceEpisodes.size(); index++) {
            Episode episode = sourceEpisodes.get(index);
            int resolvedNumber = resolveEpisodeNumber(episode, index, usePosition);
            TmdbEpisode tmdbEp = findEpisodeByNumber(episodes, resolvedNumber);
            if (tmdbEp == null) continue;
            episode.setTmdbEpisode(tmdbEp);  // 按顺序或集号设置
        }
    }
}
```

## Git 提交信息

```
commit 9f18a37510
Author: Silent1566
Date: 2026-07-18

fix(player): 修复播放页选集卡片TMDB海报匹配错误

问题：
- 播放页选集卡片显示的TMDB海报与实际集号不匹配
- 详情页显示正确，但进入播放页后按顺序显示错误的海报

原因：
- 详情页使用 Map<Integer, TmdbEpisode> 和集号映射正确匹配
- 播放页直接使用 episode.getTmdbEpisode()，按顺序设置导致错位

修复：
- 在播放页适配器中添加 TmdbEpisodeMatcher.shouldApply() 验证
- 验证 TMDB 数据的集号是否与 Episode 实际集号匹配
- 不匹配的 TMDB 数据设为 null，避免显示错误海报

修改文件：
- EpisodeAdapter (leanback): 添加集号匹配验证
- EpisodeHoriHolder (mobile): 添加集号匹配验证  
- EpisodeGridHolder (mobile): 添加集号匹配验证
```

## 总结

此修复确保了播放页和详情页使用相同的 TMDB 匹配逻辑，解决了炫彩详情模式下播放页选集卡片海报显示错误的问题。通过在适配器层面添加集号验证，确保只有真正匹配的 TMDB 数据才会被显示，避免了因顺序错位导致的海报不匹配问题。
