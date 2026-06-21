package com.fongmi.android.tv.ui.helper;

import android.app.Activity;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TMDB 数据适配器
 *
 * 负责在 VideoActivity 的 TMDB 模式下：
 * 1. 根据视频名称自动搜索匹配 TMDB 条目
 * 2. 加载详情、演员、简介等元数据
 * 3. 把元数据写回 {@link Vod} 并通过 {@link RefreshEvent#vod(Vod)} 推送到 UI
 *
 * 该类位于 src/main，被 mobile / leanback 两个 flavor 共享，因此只依赖
 * 两端都存在的事件机制，不直接操作各自布局生成的 binding 字段。
 */
public class TmdbUIAdapter {

    private final Activity activity;
    private final TmdbService tmdbService;
    private final TmdbMatcher tmdbMatcher;
    private final TmdbConfig tmdbConfig;

    private TmdbItem tmdbItem;
    private JsonObject tmdbDetail;
    private List<TmdbPerson> tmdbCast;
    private List<TmdbItem> personalTmdbRecommendations;
    private List<TmdbItem> personalDoubanRecommendations;
    private boolean loaded;
    private volatile int loadGeneration;

    public TmdbUIAdapter(Activity activity) {
        this.activity = activity;
        this.tmdbService = new TmdbService();
        this.tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        this.tmdbMatcher = new TmdbMatcher(tmdbService, tmdbConfig);
    }

    public boolean isReady() {
        return tmdbConfig.isReady();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public TmdbItem getTmdbItem() {
        return tmdbItem;
    }

    public JsonObject getTmdbDetail() {
        return tmdbDetail;
    }

    public List<TmdbPerson> getCast() {
        return tmdbCast == null ? new ArrayList<>() : tmdbCast;
    }

    public List<TmdbItem> getPersonalTmdbRecommendations() {
        return getPersonalRecommendations(personalTmdbRecommendations, new ArrayList<>());
    }

    public List<TmdbItem> getPersonalDoubanRecommendations() {
        return getPersonalRecommendations(personalDoubanRecommendations, getPersonalTmdbRecommendations());
    }

    private List<TmdbItem> getPersonalRecommendations(List<TmdbItem> personalRecommendations, List<TmdbItem> sourceRecommendations) {
        if (personalRecommendations == null || personalRecommendations.isEmpty()) return new ArrayList<>();
        List<TmdbItem> currentRecommendations = getRecommendations();
        List<TmdbItem> items = new ArrayList<>();
        for (TmdbItem item : personalRecommendations) {
            if (containsRecommendation(currentRecommendations, item) || containsRecommendation(items, item)) continue;
            if (containsRecommendation(sourceRecommendations, item)) continue;
            items.add(item);
        }
        return items;
    }

    /**
     * 直接指定 TMDB 条目并加载详情。
     */
    public void load(TmdbItem item, Vod vod) {
        if (item == null) return;
        int generation = resetLoadState();
        this.tmdbItem = item;
        saveMatch(vod, item);
        loadDetail(vod, item, generation);
    }

    /**
     * 根据视频名称自动搜索匹配并加载详情。
     *
     * @param videoName 视频标题（通常取详情页解析出的名称）
     * @param vod       待增强的 Vod；增强后通过事件推回 UI
     */
    public void autoMatch(String videoName, Vod vod) {
        int generation = resetLoadState();
        if (!isReady()) {
            SpiderDebug.log("tmdb", "skip auto match: config not ready");
            notifyLoadComplete(vod, generation);
            return;
        }
        if (TextUtils.isEmpty(videoName)) {
            notifyLoadComplete(vod, generation);
            return;
        }
        Task.execute(() -> {
            if (!isCurrentGeneration(generation)) return;
            TmdbItem matched = getCachedMatch(vod);
            if (matched != null) SpiderDebug.log("tmdb", "use cached match title=%s", matched.getTitle());
            if (matched == null) matched = tmdbMatcher.searchAndMatch(videoName, vod);
            if (!isCurrentGeneration(generation)) return;
            if (matched == null) {
                SpiderDebug.log("tmdb", "auto match miss name=%s", videoName);
                tmdbItem = null;
                notifyLoadComplete(vod, generation);
                return;
            }
            saveMatch(vod, matched);
            tmdbItem = matched;
            loadDetailSync(vod, matched, generation);
        });
    }

    public List<TmdbItem> search(String keyword) throws Exception {
        return search(keyword, null);
    }

    public List<TmdbItem> search(String keyword, Vod vod) throws Exception {
        return tmdbMatcher.search(keyword, vod);
    }

    public String cleanSearchQuery(String keyword) {
        return tmdbMatcher.cleanVideoName(keyword);
    }

    private void loadDetail(Vod vod, TmdbItem item, int generation) {
        if (item == null || !isReady()) {
            notifyLoadComplete(vod, generation);
            return;
        }
        Task.execute(() -> loadDetailSync(vod, item, generation));
    }

    private int resetLoadState() {
        int generation = ++loadGeneration;
        tmdbItem = null;
        tmdbDetail = null;
        tmdbCast = null;
        personalTmdbRecommendations = null;
        personalDoubanRecommendations = null;
        loaded = false;
        return generation;
    }

    private boolean isCurrentGeneration(int generation) {
        return generation == loadGeneration;
    }

    private void loadDetailSync(Vod vod, TmdbItem item, int generation) {
        try {
            JsonObject detail = tmdbService.detail(item, tmdbConfig);
            List<TmdbPerson> cast = tmdbService.cast(detail, tmdbConfig);
            PersonalRecommendationService.Recommendations recommendations = new PersonalRecommendationService(tmdbService, tmdbConfig).load(vod, item, detail);
            if (!isCurrentGeneration(generation)) return;
            tmdbDetail = detail;
            tmdbCast = cast;
            personalTmdbRecommendations = recommendations.getTmdb();
            personalDoubanRecommendations = recommendations.getDouban();
            loaded = true;
            if (vod != null) {
                enrichVod(vod, item, detail);
                // 如果是电视剧，自动获取并应用集数信息
                if (item.isTv()) {
                    applyEpisodeTitles(vod, item);
                }
                if (!isCurrentGeneration(generation)) return;
                activity.runOnUiThread(() -> RefreshEvent.vod(vod));
            }
            SpiderDebug.log("tmdb", "detail loaded title=%s cast=%d", item.getTitle(), getCast().size());
        } catch (Exception e) {
            SpiderDebug.log("tmdb", "detail load failed: %s", e.getMessage());
            notifyLoadComplete(vod, generation);
        }
    }

    private void notifyLoadComplete(Vod vod, int generation) {
        // TMDB 加载失败或跳过时，仍然发送 RefreshEvent 让 UI 继续
        if (vod != null && isCurrentGeneration(generation)) {
            activity.runOnUiThread(() -> RefreshEvent.vod(vod));
        }
    }

    private TmdbItem getCachedMatch(Vod vod) {
        if (vod == null) return null;
        return Setting.getTmdbMatchCache().find(cacheSiteKey(vod), cacheVodId(vod));
    }

    private void saveMatch(Vod vod, TmdbItem item) {
        if (vod == null || item == null || item.getTmdbId() <= 0) return;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        cache.put(cacheSiteKey(vod), cacheVodId(vod), item);
        Setting.putTmdbMatchCache(cache);
    }

    private String cacheSiteKey(Vod vod) {
        String siteKey = vod == null ? "" : vod.getSiteKey();
        if (!TextUtils.isEmpty(siteKey)) return siteKey;
        String fallback = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("key");
        return TextUtils.isEmpty(fallback) ? "" : fallback;
    }

    private String cacheVodId(Vod vod) {
        String vodId = vod == null ? "" : vod.getId();
        if (!TextUtils.isEmpty(vodId)) return vodId;
        String fallback = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("id");
        return TextUtils.isEmpty(fallback) ? "" : fallback;
    }

    /**
     * 把 TMDB 详情写回 Vod。仅在源数据缺失时补充，避免覆盖站点已有信息。
     */
    public void enrichVod(Vod vod) {
        enrichVod(vod, tmdbItem, tmdbDetail);
    }

    private void enrichVod(Vod vod, TmdbItem item, JsonObject detail) {
        if (vod == null || item == null || detail == null) return;

        // 简介：优先使用 TMDB 翻译后的简介
        String overview = tmdbService.translatedOverview(detail, tmdbConfig);
        if (!TextUtils.isEmpty(overview) && overview.length() > vod.getContent().length()) {
            vod.setContent(overview);
        }

        // 海报：源站缺失时使用 TMDB 海报
        if (TextUtils.isEmpty(vod.getPic()) && !TextUtils.isEmpty(item.getPosterUrl())) {
            vod.setPic(item.getPosterUrl());
        }

        // 演员：源站缺失时使用 TMDB 演员表（无法直接设置，Vod 无 setter）
        // 可通过扩展 Vod 添加 setActor() 或在 VideoActivity 显示时从 adapter 获取

        // 导演 / 主创：源站缺失时使用 TMDB 主创
        if (TextUtils.isEmpty(vod.getDirector())) {
            List<TmdbPerson> creators = tmdbService.creators(detail, tmdbConfig);
            List<String> names = new ArrayList<>();
            for (TmdbPerson person : creators) {
                if (!TextUtils.isEmpty(person.getName())) names.add(person.getName());
                if (names.size() >= 5) break;
            }
            if (!names.isEmpty()) vod.setDirector(TextUtils.join(" / ", names));
        }
    }

    /**
     * 获取并应用 TMDB 集数标题到 Vod（仅针对电视剧）。
     */
    private void applyEpisodeTitles(Vod vod, TmdbItem item) {
        if (vod == null || item == null || vod.getFlags() == null) return;
        try {
            // 尝试获取第1季
            JsonObject season = null;
            int seasonNumber = 1;
            try {
                season = tmdbService.season(item, 1, tmdbConfig);
            } catch (Exception ignored) {
            }

            // 第1季失败，尝试第0季（特别篇）
            if (season == null) {
                try {
                    seasonNumber = 0;
                    season = tmdbService.season(item, 0, tmdbConfig);
                } catch (Exception ignored) {
                }
            }

            if (season == null) return;

            List<TmdbEpisode> episodes = tmdbService.episodes(season, tmdbConfig, item.getTmdbId(), seasonNumber);
            if (episodes.isEmpty()) return;

            // 先排序集数
            com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(vod);

            // 应用标题到每个 Episode
            for (Flag flag : vod.getFlags()) {
                for (Episode episode : flag.getEpisodes()) {
                    TmdbEpisode tmdbEp = findEpisodeByNumber(episodes, episode.getNumber());
                    if (tmdbEp != null) {
                        episode.setTmdbEpisode(tmdbEp);
                        if (!tmdbEp.getTitle().isEmpty() && !episode.getDisplayName().contains(tmdbEp.getTitle())) {
                            episode.setDisplayName(episode.getNumber() + ". " + tmdbEp.getTitle());
                        }
                    }
                }
            }
            SpiderDebug.log("tmdb", "应用集数标题: %d 集", episodes.size());
        } catch (Exception e) {
            SpiderDebug.log("tmdb", "获取集数信息失败: %s", e.getMessage());
        }
    }

    private TmdbEpisode findEpisodeByNumber(List<TmdbEpisode> episodes, int number) {
        for (TmdbEpisode ep : episodes) {
            if (ep.getNumber() == number) return ep;
        }
        return null;
    }

    /**
     * 评分文本，形如 "8.6"，无评分返回空串。
     */
    public String getRatingText() {
        if (tmdbDetail == null) return "";
        if (!tmdbDetail.has("vote_average") || tmdbDetail.get("vote_average").isJsonNull()) return "";
        double vote = tmdbDetail.get("vote_average").getAsDouble();
        return vote <= 0 ? "" : String.format(Locale.US, "%.1f", vote);
    }

    /**
     * 类型文本，形如 "剧情 / 动作"，无类型返回空串。
     */
    public String getGenresText() {
        if (tmdbDetail == null || !tmdbDetail.has("genres")) return "";
        JsonElement element = tmdbDetail.get("genres");
        if (!element.isJsonArray()) return "";
        JsonArray genres = element.getAsJsonArray();
        List<String> names = new ArrayList<>();
        for (JsonElement g : genres) {
            if (!g.isJsonObject()) continue;
            JsonObject obj = g.getAsJsonObject();
            if (obj.has("name") && !obj.get("name").isJsonNull()) names.add(obj.get("name").getAsString());
        }
        return TextUtils.join(" / ", names);
    }

    /**
     * 年份文本，取首播/上映日期的年份，无则返回空串。
     */
    public String getYear() {
        if (tmdbDetail == null) return "";
        String date = readString(tmdbDetail, "first_air_date");
        if (TextUtils.isEmpty(date)) date = readString(tmdbDetail, "release_date");
        if (TextUtils.isEmpty(date) || date.length() < 4) return "";
        return date.substring(0, 4);
    }

    /**
     * 地区文本，优先取制片国家名称，其次原产国代码，无则返回空串。
     */
    public String getArea() {
        if (tmdbDetail == null) return "";
        if (tmdbDetail.has("production_countries") && tmdbDetail.get("production_countries").isJsonArray()) {
            List<String> names = new ArrayList<>();
            for (JsonElement e : tmdbDetail.getAsJsonArray("production_countries")) {
                if (!e.isJsonObject()) continue;
                JsonObject obj = e.getAsJsonObject();
                String name = readString(obj, "name");
                if (!TextUtils.isEmpty(name)) names.add(name);
                if (names.size() >= 2) break;
            }
            if (!names.isEmpty()) return TextUtils.join(" / ", names);
        }
        if (tmdbDetail.has("origin_country") && tmdbDetail.get("origin_country").isJsonArray()) {
            List<String> codes = new ArrayList<>();
            for (JsonElement e : tmdbDetail.getAsJsonArray("origin_country")) {
                if (e.isJsonNull()) continue;
                String code = e.getAsString();
                if (!TextUtils.isEmpty(code)) codes.add(code);
                if (codes.size() >= 2) break;
            }
            if (!codes.isEmpty()) return TextUtils.join(" / ", codes);
        }
        return "";
    }

    private String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString();
    }

    /**
     * 获取剧照列表（backdrops）。
     */
    public List<String> getPhotos() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.photos(tmdbDetail, tmdbConfig);
    }

    /**
     * 获取主创团队（导演、编剧、制片）。
     */
    public List<TmdbPerson> getCreators() {
        if (tmdbDetail == null) return new ArrayList<>();
        return tmdbService.creators(tmdbDetail, tmdbConfig);
    }

    /**
     * 获取推荐影片（recommendations + similar 合并去重）。
     */
    public List<TmdbItem> getRecommendations() {
        if (tmdbDetail == null) return new ArrayList<>();
        List<TmdbItem> items = new ArrayList<>(tmdbService.recommendations(tmdbDetail, tmdbConfig));
        List<TmdbItem> similar = tmdbService.similar(tmdbDetail, tmdbConfig);
        // 去重合并
        for (TmdbItem item : similar) {
            boolean exists = false;
            for (TmdbItem existing : items) {
                if (existing.getTmdbId() == item.getTmdbId()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) items.add(item);
            if (items.size() >= 12) break;
        }
        return items;
    }

    private boolean containsRecommendation(List<TmdbItem> items, TmdbItem target) {
        if (items == null || target == null) return false;
        for (TmdbItem item : items) if (sameRecommendation(item, target)) return true;
        return false;
    }

    private boolean sameRecommendation(TmdbItem first, TmdbItem second) {
        if (first == null || second == null) return false;
        if (first.getTmdbId() > 0 && second.getTmdbId() > 0) {
            return first.getTmdbId() == second.getTmdbId() && TextUtils.equals(first.getMediaType(), second.getMediaType());
        }
        String firstTitle = normalizeRecommendationTitle(first.getTitle());
        String secondTitle = normalizeRecommendationTitle(second.getTitle());
        return !TextUtils.isEmpty(firstTitle) && firstTitle.equals(secondTitle);
    }

    private String normalizeRecommendationTitle(String text) {
        return TextUtils.isEmpty(text) ? "" : text.replaceAll("[\\s·•・._\\-/\\\\|()（）\\[\\]【】《》<>:：,，.。]+", "").trim().toLowerCase(Locale.ROOT);
    }
}
