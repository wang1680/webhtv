package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.ui.adapter.TmdbCastAdapter;
import com.fongmi.android.tv.ui.helper.TmdbUIAdapter;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TMDB 风格详情页头部面板控制器。
 *
 * 职责：
 * 1. 将 view_tmdb_header.xml 注入到 VideoActivity 滚动区顶部
 * 2. 从 TmdbUIAdapter 读取数据并填充视图
 * 3. 加载背景图、海报、评分、演员等
 *
 * 使用方式：
 *   TmdbHeaderView headerView = new TmdbHeaderView(activity, scrollContainer);
 *   headerView.bind(tmdbUIAdapter);
 */
public class TmdbHeaderView {

    private static final int OMDB_FULL_RATING_TEXT_MAX_LENGTH = 14;

    /**
     * 内容渲染完成回调接口
     */
    public interface OnImagesLoadedListener {
        void onImagesLoaded();
    }

    public interface ActionListener {
        void onChangeSource();

        void onRematch();

        void onKeep();
    }

    private final Activity activity;
    private final ViewGroup scrollContainer;
    private View headerRoot;
    private TmdbCastAdapter castAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter photoAdapter;
    private TmdbCastAdapter crewAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter personalTmdbRecommendationAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter personalDoubanRecommendationAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter recommendationAdapter;
    private TmdbUIAdapter boundAdapter;
    private boolean loadingRecommendations;
    private boolean loadingPersonalTmdbRecommendations;
    private boolean loadingPersonalDoubanRecommendations;
    private final java.util.Map<String, java.util.List<String[]>> omdbRatingCache = new java.util.HashMap<>();
    private final java.util.Map<String, PersonalRecommendationService.DoubanRating> doubanRatingCache = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.List<String[]>> ratingDisplayChips = new java.util.HashMap<>();

    private OnImagesLoadedListener imagesLoadedListener;
    private ActionListener actionListener;

    // 幻灯片相关
    private ImageView backdropView;
    private java.util.List<String> backdropPhotos = new java.util.ArrayList<>();
    private int currentBackdropIndex = 0;
    private android.os.Handler backdropHandler;
    private Runnable backdropRunnable;

    public TmdbHeaderView(Activity activity, ViewGroup scrollContainer) {
        this.activity = activity;
        this.scrollContainer = scrollContainer;
    }

    /**
     * 设置内容渲染完成监听器
     */
    public void setOnImagesLoadedListener(OnImagesLoadedListener listener) {
        this.imagesLoadedListener = listener;
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * 获取头部根视图
     */
    public View getHeaderRoot() {
        return headerRoot;
    }

    /**
     * 注入头部面板到滚动区顶部（仅调用一次）。
     */
    public void inflate() {
        if (headerRoot != null) return;
        headerRoot = LayoutInflater.from(activity).inflate(R.layout.view_tmdb_header, scrollContainer, false);
        scrollContainer.addView(headerRoot, 0);
        // 初始隐藏，等 bind 完成后再显示
        headerRoot.setVisibility(View.GONE);
        setupRecyclerViews();
        setupActions();
    }

    public void setKeepSelected(boolean selected) {
        if (headerRoot == null) return;
        MaterialButton keep = headerRoot.findViewById(R.id.tmdbKeep);
        if (keep == null) return;
        keep.setSelected(selected);
        keep.setText(selected ? R.string.keep_add : R.string.keep);
    }

    /**
     * 绑定 TMDB 数据并填充视图。
     */
    public void bind(TmdbUIAdapter adapter) {
        if (headerRoot == null || adapter == null || !adapter.isLoaded()) {
            android.util.Log.w("TmdbHeaderView", "bind() 跳过：headerRoot=" + (headerRoot != null) + " adapter=" + (adapter != null) + " isLoaded=" + (adapter != null && adapter.isLoaded()));
            return;
        }

        TmdbItem item = adapter.getTmdbItem();
        JsonObject detail = adapter.getTmdbDetail();
        if (item == null || detail == null) {
            android.util.Log.w("TmdbHeaderView", "bind() 跳过：item=" + (item != null) + " detail=" + (detail != null));
            return;
        }

        android.util.Log.d("TmdbHeaderView", "bind() 开始，标题=" + item.getTitle());
        boundAdapter = adapter;

        // 背景图（backdrop）- 改为幻灯片模式
        backdropView = headerRoot.findViewById(R.id.tmdbBackdrop);
        setupBackdropSlideshow(adapter);

        // 海报
        ImageView poster = headerRoot.findViewById(R.id.tmdbPoster);
        String posterUrl = item.getPosterUrl();
        if (!TextUtils.isEmpty(posterUrl)) {
            Glide.with(activity).load(posterUrl).into(poster);
        }

        // 标题
        TextView title = headerRoot.findViewById(R.id.tmdbTitle);
        title.setText(item.getTitle());

        // 评分徽章（隐藏，已在简介上方显示详细评分）
        TextView rating = headerRoot.findViewById(R.id.tmdbRating);
        rating.setVisibility(View.GONE);

        // 元数据（类型 · 年份），移除左边距因为评分已隐藏
        TextView meta = headerRoot.findViewById(R.id.tmdbMeta);
        String mediaType = "tv".equals(item.getMediaType()) ? "剧集" : "电影";
        String year = extractYear(detail);
        meta.setText(TextUtils.isEmpty(year) ? mediaType : mediaType + " · " + year);
        // 评分徽章已隐藏，移除元数据左边距使其左对齐
        ViewGroup.MarginLayoutParams metaParams = (ViewGroup.MarginLayoutParams) meta.getLayoutParams();
        metaParams.setMarginStart(0);
        meta.setLayoutParams(metaParams);

        // 类型标签
        TextView genres = headerRoot.findViewById(R.id.tmdbGenres);
        String genresText = adapter.getGenresText();
        if (!TextUtils.isEmpty(genresText)) {
            genres.setText(genresText);
            genres.setVisibility(View.VISIBLE);
        } else {
            genres.setVisibility(View.GONE);
        }

        // 简介
        TextView overview = headerRoot.findViewById(R.id.tmdbOverview);
        String overviewText = detail.has("overview") && !detail.get("overview").isJsonNull()
                ? detail.get("overview").getAsString() : "";
        android.util.Log.d("TmdbHeaderView", "简介长度=" + overviewText.length() + " 内容=" + (overviewText.length() > 20 ? overviewText.substring(0, 20) : overviewText));
        if (!TextUtils.isEmpty(overviewText)) {
            overview.setText(overviewText);
            overview.setVisibility(View.VISIBLE);
            // 点击展开/收起
            overview.setOnClickListener(v -> {
                int currentMaxLines = overview.getMaxLines();
                overview.setMaxLines(currentMaxLines == 4 ? Integer.MAX_VALUE : 4);
            });
        } else {
            overview.setVisibility(View.GONE);
        }

        // 演员
        if (!adapter.getCast().isEmpty()) {
            headerRoot.findViewById(R.id.tmdbCastLabel).setVisibility(View.VISIBLE);
            RecyclerView castRv = headerRoot.findViewById(R.id.tmdbCast);
            castRv.setVisibility(View.VISIBLE);
            castAdapter.setItems(adapter.getCast());
        } else {
            headerRoot.findViewById(R.id.tmdbCastLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbCast).setVisibility(View.GONE);
        }

        // 剧照
        if (!adapter.getPhotos().isEmpty()) {
            headerRoot.findViewById(R.id.tmdbPhotosLabel).setVisibility(View.VISIBLE);
            RecyclerView photosRv = headerRoot.findViewById(R.id.tmdbPhotos);
            photosRv.setVisibility(View.VISIBLE);
            photoAdapter.setItems(adapter.getPhotos());
        } else {
            headerRoot.findViewById(R.id.tmdbPhotosLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbPhotos).setVisibility(View.GONE);
        }

        // 主创团队
        if (!adapter.getCreators().isEmpty()) {
            headerRoot.findViewById(R.id.tmdbCrewLabel).setVisibility(View.VISIBLE);
            RecyclerView crewRv = headerRoot.findViewById(R.id.tmdbCrew);
            crewRv.setVisibility(View.VISIBLE);
            crewAdapter.setItems(adapter.getCreators());
        } else {
            headerRoot.findViewById(R.id.tmdbCrewLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbCrew).setVisibility(View.GONE);
        }

        // 外部链接
        setupExternalLinks(adapter);

        // 猜你喜欢
        if (!adapter.getRecommendations().isEmpty()) {
            headerRoot.findViewById(R.id.tmdbRecommendationsLabel).setVisibility(View.VISIBLE);
            RecyclerView recommendationsRv = headerRoot.findViewById(R.id.tmdbRecommendations);
            recommendationsRv.setVisibility(View.VISIBLE);
            recommendationAdapter.setItems(adapter.getRecommendations());
        } else {
            headerRoot.findViewById(R.id.tmdbRecommendationsLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbRecommendations).setVisibility(View.GONE);
        }

        // 个性推荐
        bindRecommendationRow(R.id.tmdbPersonalTmdbRecommendationsLabel, R.id.tmdbPersonalTmdbRecommendations, personalTmdbRecommendationAdapter, adapter.getPersonalTmdbRecommendations());
        bindRecommendationRow(R.id.tmdbPersonalDoubanRecommendationsLabel, R.id.tmdbPersonalDoubanRecommendations, personalDoubanRecommendationAdapter, adapter.getPersonalDoubanRecommendations());

        // 内容填充完成，显示头部容器
        headerRoot.setVisibility(View.VISIBLE);

        // bind 完成，通知监听器
        android.util.Log.d("TmdbHeaderView", "bind 完成，显示容器并通知监听器");
        if (imagesLoadedListener != null) {
            imagesLoadedListener.onImagesLoaded();
        }
    }

    /**
     * 移除头部面板（切换回普通模式时）。
     */
    public void remove() {
        if (headerRoot != null && headerRoot.getParent() == scrollContainer) {
            scrollContainer.removeView(headerRoot);
            headerRoot = null;
        }
    }

    /**
     * 强制显示头部容器（用于 TMDB 加载失败时显示播放控件）。
     */
    public void show() {
        if (headerRoot != null) headerRoot.setVisibility(View.VISIBLE);
    }

    public void refreshPersonalRecommendations() {
        if (boundAdapter == null || headerRoot == null) return;
        boundAdapter.refreshPersonalRecommendations(changed -> {
            if (!changed || headerRoot == null) return;
            bindRecommendationRow(R.id.tmdbPersonalTmdbRecommendationsLabel, R.id.tmdbPersonalTmdbRecommendations, personalTmdbRecommendationAdapter, boundAdapter.getPersonalTmdbRecommendations());
            bindRecommendationRow(R.id.tmdbPersonalDoubanRecommendationsLabel, R.id.tmdbPersonalDoubanRecommendations, personalDoubanRecommendationAdapter, boundAdapter.getPersonalDoubanRecommendations());
        });
    }

    private void setupRecyclerViews() {
        RecyclerView castRv = headerRoot.findViewById(R.id.tmdbCast);
        castAdapter = new TmdbCastAdapter();
        castAdapter.setOnItemClickListener(this::onPersonClick);
        castRv.setAdapter(castAdapter);

        RecyclerView photosRv = headerRoot.findViewById(R.id.tmdbPhotos);
        photoAdapter = new com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter();
        photoAdapter.setOnItemClickListener(this::onPhotoClick);
        photosRv.setAdapter(photoAdapter);

        RecyclerView crewRv = headerRoot.findViewById(R.id.tmdbCrew);
        crewAdapter = new TmdbCastAdapter();
        crewAdapter.setOnItemClickListener(this::onPersonClick);
        crewRv.setAdapter(crewAdapter);

        RecyclerView personalTmdbRecommendationsRv = headerRoot.findViewById(R.id.tmdbPersonalTmdbRecommendations);
        personalTmdbRecommendationAdapter = new com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter();
        personalTmdbRecommendationAdapter.setOnItemClickListener(this::onRecommendationClick);
        personalTmdbRecommendationsRv.setAdapter(personalTmdbRecommendationAdapter);
        attachLazyLoader(personalTmdbRecommendationsRv, RecommendationRow.PERSONAL_TMDB);

        RecyclerView personalDoubanRecommendationsRv = headerRoot.findViewById(R.id.tmdbPersonalDoubanRecommendations);
        personalDoubanRecommendationAdapter = new com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter();
        personalDoubanRecommendationAdapter.setOnItemClickListener(this::onRecommendationClick);
        personalDoubanRecommendationsRv.setAdapter(personalDoubanRecommendationAdapter);
        attachLazyLoader(personalDoubanRecommendationsRv, RecommendationRow.PERSONAL_DOUBAN);

        RecyclerView recommendationsRv = headerRoot.findViewById(R.id.tmdbRecommendations);
        recommendationAdapter = new com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter();
        recommendationAdapter.setOnItemClickListener(this::onRecommendationClick);
        recommendationsRv.setAdapter(recommendationAdapter);
        attachLazyLoader(recommendationsRv, RecommendationRow.RECOMMENDATIONS);
    }

    private void bindRecommendationRow(int labelId, int recyclerId, com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter adapter, List<TmdbItem> items) {
        if (items != null && !items.isEmpty()) {
            headerRoot.findViewById(labelId).setVisibility(View.VISIBLE);
            RecyclerView recyclerView = headerRoot.findViewById(recyclerId);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setItems(items);
        } else {
            adapter.setItems(new ArrayList<>());
            headerRoot.findViewById(labelId).setVisibility(View.GONE);
            headerRoot.findViewById(recyclerId).setVisibility(View.GONE);
        }
    }

    private void attachLazyLoader(RecyclerView recyclerView, RecommendationRow row) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dx <= 0 || !isNearRowEnd(recyclerView)) return;
                loadMore(row);
            }
        });
    }

    private boolean isNearRowEnd(RecyclerView recyclerView) {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (adapter == null || !(manager instanceof LinearLayoutManager)) return false;
        int lastVisible = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        return lastVisible >= 0 && adapter.getItemCount() - lastVisible <= 4;
    }

    private void loadMore(RecommendationRow row) {
        if (boundAdapter == null) return;
        if (row == RecommendationRow.RECOMMENDATIONS) {
            if (loadingRecommendations || !boundAdapter.hasMoreRecommendations()) return;
            loadingRecommendations = true;
            boundAdapter.loadMoreRecommendations(changed -> {
                loadingRecommendations = false;
                if (changed) recommendationAdapter.setItems(boundAdapter.getRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_TMDB) {
            if (loadingPersonalTmdbRecommendations || !boundAdapter.hasMorePersonalTmdbRecommendations()) return;
            loadingPersonalTmdbRecommendations = true;
            boundAdapter.loadMorePersonalTmdbRecommendations(changed -> {
                loadingPersonalTmdbRecommendations = false;
                if (changed) personalTmdbRecommendationAdapter.setItems(boundAdapter.getPersonalTmdbRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_DOUBAN) {
            if (loadingPersonalDoubanRecommendations || !boundAdapter.hasMorePersonalDoubanRecommendations()) return;
            loadingPersonalDoubanRecommendations = true;
            boundAdapter.loadMorePersonalDoubanRecommendations(changed -> {
                loadingPersonalDoubanRecommendations = false;
                if (changed) personalDoubanRecommendationAdapter.setItems(boundAdapter.getPersonalDoubanRecommendations());
            });
        }
    }

    private enum RecommendationRow {
        RECOMMENDATIONS, PERSONAL_TMDB, PERSONAL_DOUBAN
    }

    private void setupActions() {
        headerRoot.findViewById(R.id.tmdbChangeSource).setOnClickListener(view -> {
            if (actionListener != null) actionListener.onChangeSource();
        });
        headerRoot.findViewById(R.id.tmdbRematch).setOnClickListener(view -> {
            if (actionListener != null) actionListener.onRematch();
        });
        headerRoot.findViewById(R.id.tmdbKeep).setOnClickListener(view -> {
            if (actionListener != null) actionListener.onKeep();
        });
    }

    /**
     * 点击剧照：使用 PhotoViewerDialog 查看。
     */
    private void onPhotoClick(String url, int position) {
        if (TextUtils.isEmpty(url)) return;
        java.util.List<String> photos = photoAdapter.getItems();
        com.fongmi.android.tv.ui.dialog.PhotoViewerDialog.show(activity, photos, position, null);
    }

    /**
     * 点击演员/主创：显示简介弹窗。
     */
    private void onPersonClick(com.fongmi.android.tv.bean.TmdbPerson person) {
        if (person == null) return;
        com.fongmi.android.tv.ui.dialog.TmdbPersonDialog.show(activity, person, currentSite());
    }

    /**
     * 点击"猜你喜欢"卡片：以新的 TMDB 条目打开详情页。
     */
    private void onRecommendationClick(TmdbItem item) {
        TmdbNavigation.open(activity, item, currentSite());
    }

    private com.fongmi.android.tv.bean.Site currentSite() {
        String key = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("key");
        return com.fongmi.android.tv.api.config.VodConfig.get().getSite(key);
    }

    private String extractYear(JsonObject detail) {
        if (detail == null) return "";
        // 电影用 release_date，剧集用 first_air_date
        String dateField = detail.has("release_date") ? "release_date" : "first_air_date";
        if (!detail.has(dateField) || detail.get(dateField).isJsonNull()) return "";
        String date = detail.get(dateField).getAsString();
        if (date.length() >= 4) return date.substring(0, 4);
        return "";
    }

    private int parseYear(String year) {
        if (TextUtils.isEmpty(year)) return 0;
        try {
            return Integer.parseInt(year);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * 设置背景图幻灯片模式
     */
    private void setupBackdropSlideshow(TmdbUIAdapter adapter) {
        TmdbItem item = adapter.getTmdbItem();
        if (item == null) return;

        // 收集所有可用的背景图
        backdropPhotos.clear();
        String mainBackdrop = item.getBackdropUrl();
        if (!TextUtils.isEmpty(mainBackdrop)) {
            backdropPhotos.add(mainBackdrop);
        }

        // 添加所有剧照
        java.util.List<String> photos = adapter.getPhotos();
        if (photos != null) {
            for (String photo : photos) {
                if (!TextUtils.isEmpty(photo) && !backdropPhotos.contains(photo)) {
                    backdropPhotos.add(photo);
                }
            }
        }

        // 如果只有一张图，直接加载
        if (backdropPhotos.isEmpty()) {
            return;
        }

        if (backdropPhotos.size() == 1) {
            Glide.with(activity).load(backdropPhotos.get(0)).into(backdropView);
            return;
        }

        // 多张图片，启动幻灯片
        startBackdropSlideshow();
    }

    /**
     * 启动背景图幻灯片自动切换
     */
    private void startBackdropSlideshow() {
        stopBackdropSlideshow();

        backdropHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        backdropRunnable = new Runnable() {
            @Override
            public void run() {
                if (backdropPhotos == null || backdropPhotos.isEmpty()) return;

                // 预加载下一张图片
                int nextIndex = (currentBackdropIndex + 1) % backdropPhotos.size();
                String nextUrl = backdropPhotos.get(nextIndex);

                // 使用 Glide 预加载，加载完成后切换
                Glide.with(activity)
                        .load(nextUrl)
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                // 加载失败，跳过这张，继续下一张
                                currentBackdropIndex = nextIndex;
                                if (backdropHandler != null && backdropRunnable != null) {
                                    backdropHandler.postDelayed(backdropRunnable, 500);
                                }
                                return true;
                            }

                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                // 图片加载完成，切换到这张图
                                currentBackdropIndex = nextIndex;
                                backdropView.setImageDrawable(resource);

                                // 5秒后切换下一张
                                if (backdropHandler != null && backdropRunnable != null) {
                                    backdropHandler.postDelayed(backdropRunnable, 5000);
                                }
                                return true;
                            }
                        })
                        .preload();
            }
        };

        // 加载第一张
        if (!backdropPhotos.isEmpty()) {
            Glide.with(activity).load(backdropPhotos.get(0)).into(backdropView);
            currentBackdropIndex = 0;
            // 5秒后开始切换
            backdropHandler.postDelayed(backdropRunnable, 5000);
        }
    }

    /**
     * 停止背景图幻灯片
     */
    private void stopBackdropSlideshow() {
        if (backdropHandler != null && backdropRunnable != null) {
            backdropHandler.removeCallbacks(backdropRunnable);
        }
    }

    /**
     * 添加评分展示区域（在简介上方）
     */
    private void addRatingsDisplay(ViewGroup container, String tmdbRating, com.google.gson.JsonObject externalIds, int tmdbId, String mediaType, String title, int year, com.google.android.material.textview.MaterialTextView doubanRatingView) {
        container.removeAllViews();
        String displayKey = ratingDisplayKey(title, mediaType, year, tmdbId);
        container.setTag(displayKey);

        List<String[]> baseChips = new ArrayList<>();
        if (!TextUtils.isEmpty(tmdbRating)) {
            baseChips.add(new String[]{"TMDB", tmdbRating + "/10", "#21D07A"});
        }
        setRatingDisplayChips(displayKey, baseChips);
        renderRatingChips(container, getRatingDisplayChips(displayKey));
        fetchDoubanRatingForDisplay(title, mediaType, year, displayKey, container, doubanRatingView);

        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) return;
        String imdbId = externalIds.get("imdb_id").getAsString();
        if (TextUtils.isEmpty(imdbId)) return;

        com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(com.fongmi.android.tv.setting.Setting.getTmdbConfig());
        String omdbApiKey = tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(omdbApiKey)) return;

        String cacheKey = omdbRatingCacheKey(imdbId, omdbApiKey);
        java.util.List<String[]> cachedChips = getCachedOmdbRatingChips(cacheKey);
        if (cachedChips != null) {
            putRatingDisplayChips(displayKey, cachedChips);
            renderRatingChips(container, getRatingDisplayChips(displayKey));
            return;
        }

        fetchRatingChipsForDisplay(imdbId, omdbApiKey, cacheKey, displayKey, container);
    }

    /**
     * 创建评分 Chip
     */
    private com.google.android.material.textview.MaterialTextView createRatingChip(String platform, String value, String color) {
        com.google.android.material.textview.MaterialTextView chip = new com.google.android.material.textview.MaterialTextView(activity);
        chip.setText(platform + " ★ " + value);
        chip.setTextColor(android.graphics.Color.parseColor(color));
        chip.setTextSize(15);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setSingleLine(true);
        chip.setIncludeFontPadding(false);
        chip.setMinWidth(ResUtil.dp2px(84));
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(8), ResUtil.dp2px(10), ResUtil.dp2px(8));

        // 设置圆角背景
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0x30FFFFFF);  // 更明显的半透明白色
        background.setCornerRadius(ResUtil.dp2px(6));  // 圆角
        chip.setBackground(background);

        com.google.android.flexbox.FlexboxLayout.LayoutParams params =
                new com.google.android.flexbox.FlexboxLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(ResUtil.dp2px(8));
        params.setMargins(0, 0, ResUtil.dp2px(8), ResUtil.dp2px(8));
        chip.setLayoutParams(params);
        return chip;
    }

    private void fetchRatingChipsForDisplay(String imdbId, String omdbApiKey, String cacheKey, String displayKey, ViewGroup container) {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String url = "https://www.omdbapi.com/?i=" + imdbId + "&apikey=" + omdbApiKey;
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                okhttp3.Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.code() != 200) return;
                if (response.body() == null) return;

                String json = response.body().string();
                com.google.gson.JsonObject jsonObj = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                if (jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) return;

                List<String[]> omdbChips = buildRatingChips(jsonObj);
                putCachedOmdbRatingChips(cacheKey, omdbChips);
                if (omdbChips.isEmpty()) return;

                activity.runOnUiThread(() -> {
                    if (headerRoot == null) return;
                    if (!(container.getTag() instanceof String) || !displayKey.equals(container.getTag())) return;
                    putRatingDisplayChips(displayKey, omdbChips);
                    renderRatingChips(container, getRatingDisplayChips(displayKey));
                });
            } catch (Exception e) {
                android.util.Log.w("TmdbHeaderView", "获取顶部评分失败: " + e.getMessage());
            }
        });
    }

    private void fetchDoubanRatingForDisplay(String title, String mediaType, int year, String displayKey, ViewGroup container, com.google.android.material.textview.MaterialTextView externalRatingView) {
        if (TextUtils.isEmpty(title)) return;
        String cacheKey = doubanRatingCacheKey(title, mediaType, year);
        PersonalRecommendationService.DoubanRating cached = getCachedDoubanRating(cacheKey);
        if (cached != null) {
            applyDoubanRating(displayKey, container, externalRatingView, cached);
            return;
        }
        com.fongmi.android.tv.utils.Task.execute(() -> {
            PersonalRecommendationService.DoubanRating rating = PersonalRecommendationService.DoubanRating.empty();
            try {
                rating = new PersonalRecommendationService().loadDoubanRating(title, mediaType, year);
            } catch (Throwable e) {
                android.util.Log.w("TmdbHeaderView", "获取豆瓣评分失败: " + e.getMessage());
            }
            putCachedDoubanRating(cacheKey, rating);
            PersonalRecommendationService.DoubanRating finalRating = rating;
            activity.runOnUiThread(() -> {
                if (headerRoot == null) return;
                if (!(container.getTag() instanceof String) || !displayKey.equals(container.getTag())) return;
                applyDoubanRating(displayKey, container, externalRatingView, finalRating);
            });
        });
    }

    private void applyDoubanRating(String displayKey, ViewGroup container, com.google.android.material.textview.MaterialTextView externalRatingView, PersonalRecommendationService.DoubanRating rating) {
        if (rating == null || rating.isEmpty()) return;
        String value = formatRating(rating.getRating());
        if (TextUtils.isEmpty(value)) return;
        putRatingDisplayChips(displayKey, java.util.Collections.singletonList(new String[]{"豆瓣", value + "/10", "#00B51D"}));
        renderRatingChips(container, getRatingDisplayChips(displayKey));
        setExternalRating(externalRatingView, value);
    }

    private void renderRatingChips(ViewGroup container, List<String[]> chips) {
        if (container == null) return;
        container.removeAllViews();
        if (chips == null || chips.isEmpty()) {
            setRatingContainerVisible(container, false);
            return;
        }
        for (String[] chip : chips) {
            container.addView(createRatingChip(chip[0], chip[1], chip[2]));
        }
        setRatingContainerVisible(container, true);
    }

    private void setRatingContainerVisible(ViewGroup container, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        container.setVisibility(visibility);
    }

    private void setRatingDisplayChips(String displayKey, List<String[]> chips) {
        synchronized (ratingDisplayChips) {
            ratingDisplayChips.put(displayKey, chips == null ? new ArrayList<>() : new ArrayList<>(chips));
        }
    }

    private void putRatingDisplayChips(String displayKey, List<String[]> chips) {
        if (TextUtils.isEmpty(displayKey) || chips == null || chips.isEmpty()) return;
        synchronized (ratingDisplayChips) {
            List<String[]> current = ratingDisplayChips.get(displayKey);
            if (current == null) current = new ArrayList<>();
            for (String[] chip : chips) replaceRatingChip(current, chip);
            current.sort(java.util.Comparator.comparingInt(chip -> ratingChipOrder(chip[0])));
            ratingDisplayChips.put(displayKey, current);
        }
    }

    private List<String[]> getRatingDisplayChips(String displayKey) {
        synchronized (ratingDisplayChips) {
            List<String[]> chips = ratingDisplayChips.get(displayKey);
            return chips == null ? new ArrayList<>() : new ArrayList<>(chips);
        }
    }

    private void replaceRatingChip(List<String[]> chips, String[] chip) {
        if (chips == null || chip == null || chip.length < 3 || TextUtils.isEmpty(chip[0]) || TextUtils.isEmpty(chip[1])) return;
        for (int i = 0; i < chips.size(); i++) {
            if (chip[0].equals(chips.get(i)[0])) {
                chips.set(i, chip);
                return;
            }
        }
        chips.add(chip);
    }

    private int ratingChipOrder(String platform) {
        if ("TMDB".equals(platform)) return 0;
        if ("豆瓣".equals(platform)) return 1;
        if ("IMDB".equals(platform)) return 2;
        if ("烂番茄".equals(platform)) return 3;
        if ("Metacritic".equals(platform) || "Metascore".equals(platform)) return 4;
        return 5;
    }

    private String ratingDisplayKey(String title, String mediaType, int year, int tmdbId) {
        return tmdbId + "|" + nullToEmpty(mediaType) + "|" + nullToEmpty(title) + "|" + year;
    }

    private String doubanRatingCacheKey(String title, String mediaType, int year) {
        return nullToEmpty(mediaType) + "|" + nullToEmpty(title).toLowerCase(Locale.ROOT) + "|" + year;
    }

    private PersonalRecommendationService.DoubanRating getCachedDoubanRating(String key) {
        synchronized (doubanRatingCache) {
            return doubanRatingCache.get(key);
        }
    }

    private void putCachedDoubanRating(String key, PersonalRecommendationService.DoubanRating rating) {
        if (TextUtils.isEmpty(key) || rating == null) return;
        synchronized (doubanRatingCache) {
            doubanRatingCache.put(key, rating);
        }
    }

    private String formatRating(double rating) {
        return rating <= 0 ? "" : String.format(Locale.US, "%.1f", rating);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 设置 OMDB 多来源评分区域。
     *
     * 仅当配置了 OMDB API Key、能拿到 IMDb ID 且 OMDB 返回有效数据时显示，
     * 否则隐藏整个区域。展示 IMDb 评分（含投票数）、烂番茄、Metacritic 等多来源评分。
     */
    private void setupOmdbRatings(TmdbUIAdapter adapter) {
        com.google.android.material.textview.MaterialTextView label = headerRoot.findViewById(R.id.tmdbOmdbRatingsLabel);
        View scroll = headerRoot.findViewById(R.id.tmdbOmdbRatingsScroll);
        ViewGroup container = headerRoot.findViewById(R.id.tmdbOmdbRatings);
        label.setVisibility(View.GONE);
        scroll.setVisibility(View.GONE);
        container.removeAllViews();
        container.setTag(null);

        JsonObject detail = adapter.getTmdbDetail();
        if (detail == null) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：detail 为空");
            return;
        }

        // 取 IMDb ID
        JsonObject externalIds = detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：无 external_ids/imdb_id，detail keys=" + detail.keySet());
            return;
        }
        String imdbId = externalIds.get("imdb_id").getAsString();
        if (TextUtils.isEmpty(imdbId)) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：imdb_id 为空");
            return;
        }

        // 必须配置 OMDB API Key
        com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(com.fongmi.android.tv.setting.Setting.getTmdbConfig());
        String omdbApiKey = tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(omdbApiKey)) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：未配置 OMDB API Key");
            return;
        }

        String cacheKey = omdbRatingCacheKey(imdbId, omdbApiKey);
        java.util.List<String[]> cachedChips = getCachedOmdbRatingChips(cacheKey);
        if (cachedChips != null) {
            renderSourceRatingChips(label, scroll, container, cachedChips);
            return;
        }

        container.setTag(cacheKey);
        android.util.Log.d("TmdbHeaderView", "OMDB 评分开始请求，imdbId=" + imdbId);
        fetchOmdbRatings(imdbId, omdbApiKey, cacheKey, label, scroll, container);
    }

    /**
     * 异步请求 OMDB 并渲染多来源评分。匹配不到数据时保持隐藏。
     */
    private void fetchOmdbRatings(String imdbId, String omdbApiKey, String cacheKey, com.google.android.material.textview.MaterialTextView label, View scroll, ViewGroup container) {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String url = "https://www.omdbapi.com/?i=" + imdbId + "&apikey=" + omdbApiKey;
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                okhttp3.Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.code() != 200 || response.body() == null) {
                    android.util.Log.w("TmdbHeaderView", "OMDB 请求失败，code=" + response.code());
                    return;
                }

                String json = response.body().string();
                android.util.Log.d("TmdbHeaderView", "OMDB 响应: " + json.substring(0, Math.min(300, json.length())));
                JsonObject jsonObj = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                if (jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) {
                    android.util.Log.w("TmdbHeaderView", "OMDB 返回 Response=False");
                    return;
                }

                final java.util.List<String[]> chips = buildRatingChips(jsonObj);
                putCachedOmdbRatingChips(cacheKey, chips);
                android.util.Log.d("TmdbHeaderView", "OMDB 评分卡片数=" + chips.size());
                if (chips.isEmpty()) return;

                activity.runOnUiThread(() -> {
                    if (headerRoot == null) return;
                    if (!(container.getTag() instanceof String) || !cacheKey.equals(container.getTag())) return;
                    renderSourceRatingChips(label, scroll, container, chips);
                });
            } catch (Exception e) {
                android.util.Log.w("TmdbHeaderView", "获取 OMDB 评分失败: " + e.getMessage());
            }
        });
    }

    private void renderSourceRatingChips(com.google.android.material.textview.MaterialTextView label, View scroll, ViewGroup container, java.util.List<String[]> chips) {
        container.removeAllViews();
        if (chips == null || chips.isEmpty()) {
            label.setVisibility(View.GONE);
            scroll.setVisibility(View.GONE);
            return;
        }
        for (String[] chip : chips) {
            container.addView(createSourceRatingChip(chip[0], chip[1], chip[2]));
        }
        label.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.VISIBLE);
    }

    private String omdbRatingCacheKey(String imdbId, String omdbApiKey) {
        return imdbId + "|" + omdbApiKey;
    }

    private java.util.List<String[]> getCachedOmdbRatingChips(String key) {
        synchronized (omdbRatingCache) {
            java.util.List<String[]> chips = omdbRatingCache.get(key);
            return chips == null ? null : new ArrayList<>(chips);
        }
    }

    private void putCachedOmdbRatingChips(String key, java.util.List<String[]> chips) {
        if (TextUtils.isEmpty(key) || chips == null || chips.isEmpty()) return;
        synchronized (omdbRatingCache) {
            omdbRatingCache.put(key, new ArrayList<>(chips));
        }
    }

    /**
     * 从 OMDB 响应组装评分卡片数据：每项为 {平台名, 评分文本, 颜色}。
     */
    private java.util.List<String[]> buildRatingChips(JsonObject jsonObj) {
        java.util.List<String[]> chips = new java.util.ArrayList<>();

        // IMDb 评分（附投票数）
        String imdbRating = optString(jsonObj, "imdbRating");
        if (!TextUtils.isEmpty(imdbRating)) {
            String votes = optString(jsonObj, "imdbVotes");
            String text = buildImdbRatingText(imdbRating, votes);
            chips.add(new String[]{"IMDB", text, "#F5C518"});
        }

        // Ratings 数组中的烂番茄、Metacritic 等来源
        if (jsonObj.has("Ratings") && jsonObj.get("Ratings").isJsonArray()) {
            for (com.google.gson.JsonElement el : jsonObj.getAsJsonArray("Ratings")) {
                if (!el.isJsonObject()) continue;
                JsonObject rating = el.getAsJsonObject();
                String source = optString(rating, "Source");
                String value = optString(rating, "Value");
                if (TextUtils.isEmpty(source) || TextUtils.isEmpty(value)) continue;
                if ("Internet Movie Database".equals(source)) continue; // 已由 imdbRating 展示
                if ("Rotten Tomatoes".equals(source)) {
                    chips.add(new String[]{"烂番茄", value, "#FA320A"});
                } else if ("Metacritic".equals(source)) {
                    chips.add(new String[]{"Metacritic", value, "#FFCC33"});
                } else {
                    chips.add(new String[]{source, value, "#21D07A"});
                }
            }
        }

        // Metascore（若 Ratings 中没有 Metacritic 则补充）
        String metascore = optString(jsonObj, "Metascore");
        if (!TextUtils.isEmpty(metascore) && !hasChip(chips, "Metacritic")) {
            chips.add(new String[]{"Metascore", metascore + "/100", "#FFCC33"});
        }

        return chips;
    }

    private boolean hasChip(java.util.List<String[]> chips, String platform) {
        for (String[] chip : chips) if (platform.equals(chip[0])) return true;
        return false;
    }

    /**
     * 读取 OMDB 字段，过滤空值与 "N/A"。
     */
    private String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        String value = obj.get(key).getAsString();
        return (TextUtils.isEmpty(value) || "N/A".equals(value)) ? "" : value.trim();
    }

    private String buildImdbRatingText(String rating, String votes) {
        if (TextUtils.isEmpty(votes)) return rating;
        String fullText = rating + " (" + votes + ")";
        if (fullText.length() <= OMDB_FULL_RATING_TEXT_MAX_LENGTH) return fullText;
        String compactVotes = compactOmdbVoteCount(votes);
        return rating + " (" + (TextUtils.isEmpty(compactVotes) ? votes : compactVotes) + ")";
    }

    private String compactOmdbVoteCount(String votes) {
        if (TextUtils.isEmpty(votes)) return "";
        String digits = votes.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(digits)) return "";
        try {
            long count = Long.parseLong(digits);
            if (count >= 1_000_000_000L) return formatOmdbCompactCount(count / 1_000_000_000d, "B");
            if (count >= 1_000_000L) return formatOmdbCompactCount(count / 1_000_000d, "M");
            if (count >= 1_000L) return formatOmdbCompactCount(count / 1_000d, "K");
        } catch (NumberFormatException ignored) {
            return "";
        }
        return votes;
    }

    private String formatOmdbCompactCount(double value, String suffix) {
        String text = String.format(Locale.US, "%.1f", value);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + suffix;
    }

    /**
     * 创建多来源评分卡片：平台名在上，评分在下。
     */
    private View createSourceRatingChip(String platform, String value, String color) {
        androidx.appcompat.widget.LinearLayoutCompat chip = new androidx.appcompat.widget.LinearLayoutCompat(activity);
        chip.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(28, 14, 28, 14);

        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0x26FFFFFF);
        background.setCornerRadius(10);
        chip.setBackground(background);

        com.google.android.material.textview.MaterialTextView platformView = new com.google.android.material.textview.MaterialTextView(activity);
        platformView.setText(platform);
        platformView.setTextColor(0xFF9AA7B4);
        platformView.setTextSize(11);
        chip.addView(platformView);

        com.google.android.material.textview.MaterialTextView valueView = new com.google.android.material.textview.MaterialTextView(activity);
        valueView.setText(value);
        valueView.setTextColor(android.graphics.Color.parseColor(color));
        valueView.setTextSize(15);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams valueParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = 4;
        valueView.setLayoutParams(valueParams);
        chip.addView(valueView);

        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams params =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(12);
        chip.setLayoutParams(params);
        return chip;
    }

    /**
     * 设置外部链接（TMDB、IMDb、豆瓣、烂番茄等）
     */
    private void setupExternalLinks(TmdbUIAdapter adapter) {
        com.google.android.material.textview.MaterialTextView label = headerRoot.findViewById(R.id.tmdbExternalLinksLabel);
        ViewGroup container = headerRoot.findViewById(R.id.tmdbExternalLinks);

        JsonObject detail = adapter.getTmdbDetail();
        if (detail == null) {
            label.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
            return;
        }

        container.removeAllViews();
        container.setTag(null);
        int linkCount = 0;

        TmdbItem item = adapter.getTmdbItem();
        int tmdbId = item != null ? item.getTmdbId() : 0;
        String mediaType = detail.has("first_air_date") ? "tv" : "movie";

        // 获取 external_ids
        JsonObject externalIds = detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        String imdbId = externalIds != null && externalIds.has("imdb_id") && !externalIds.get("imdb_id").isJsonNull()
                ? externalIds.get("imdb_id").getAsString() : "";
        String westernSearchQuery = buildWesternSearchQuery(detail, item, mediaType);
        com.google.android.material.textview.MaterialTextView imdbRatingView = null;
        com.google.android.material.textview.MaterialTextView doubanRatingView = null;
        com.google.android.material.textview.MaterialTextView rottenRatingView = null;
        com.google.android.material.textview.MaterialTextView metacriticRatingView = null;

        // TMDB 链接（始终显示）
        if (tmdbId > 0) {
            String tmdbUrl = "https://www.themoviedb.org/" + mediaType + "/" + tmdbId;
            addExternalLink(container, "TMDB", tmdbUrl, adapter.getRatingText());
            linkCount++;
        }

        // IMDB 链接
        if (!TextUtils.isEmpty(imdbId)) {
            String imdbUrl = "https://www.imdb.com/title/" + imdbId;
            android.util.Log.d("TmdbHeaderView", "Adding IMDB link: " + imdbId);
            imdbRatingView = addExternalLink(container, "IMDB", imdbUrl, null);
            linkCount++;
        }

        // 豆瓣链接（通过标题搜索）
        if (item != null && !TextUtils.isEmpty(item.getTitle())) {
            String doubanUrl = "https://search.douban.com/movie/subject_search?search_text=" +
                    android.net.Uri.encode(item.getTitle());
            doubanRatingView = addExternalLink(container, "豆瓣", doubanUrl, null);
            linkCount++;
        }

        // 烂番茄（Rotten Tomatoes）- 优先使用英文标题搜索
        if (!TextUtils.isEmpty(westernSearchQuery)) {
            String rtUrl = "https://www.rottentomatoes.com/search?search=" +
                    android.net.Uri.encode(westernSearchQuery);
            rottenRatingView = addExternalLink(container, "烂番茄", rtUrl, null);
            linkCount++;
        }

        // Metacritic（优先使用英文标题搜索）
        if (!TextUtils.isEmpty(westernSearchQuery)) {
            String metacriticUrl = "https://www.metacritic.com/search/" +
                    android.net.Uri.encode(westernSearchQuery) + "/";
            metacriticRatingView = addExternalLink(container, "Metacritic", metacriticUrl, null);
            linkCount++;
        }

        if (!TextUtils.isEmpty(imdbId)) {
            com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(com.fongmi.android.tv.setting.Setting.getTmdbConfig());
            String omdbApiKey = tmdbConfig.getOmdbApiKey();
            if (!TextUtils.isEmpty(omdbApiKey)) {
                container.setTag(imdbId);
                fetchExternalLinkRatings(imdbId, omdbApiKey, container, imdbRatingView, rottenRatingView, metacriticRatingView);
            }
        }

        // 评分展示区域（在简介区域）
        ViewGroup ratingsContainer = headerRoot.findViewById(R.id.tmdbRatingsContainer);
        String doubanTitle = item == null ? detailTitle(detail, mediaType, false) : item.getTitle();
        addRatingsDisplay(ratingsContainer, adapter.getRatingText(), externalIds, tmdbId, mediaType, doubanTitle, parseYear(extractYear(detail)), doubanRatingView);

        if (linkCount > 0) {
            label.setVisibility(View.VISIBLE);
            container.setVisibility(View.VISIBLE);
        } else {
            label.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
        }
    }

    private String buildWesternSearchQuery(JsonObject detail, TmdbItem item, String mediaType) {
        String title = preferredWesternSearchTitle(detail, item, mediaType);
        if (TextUtils.isEmpty(title)) return "";
        String year = extractYear(detail);
        return TextUtils.isEmpty(year) || title.contains(year) ? title : title + " " + year;
    }

    private String preferredWesternSearchTitle(JsonObject detail, TmdbItem item, String mediaType) {
        String english = englishTranslationTitle(detail, mediaType);
        if (!TextUtils.isEmpty(english)) return english;

        String original = detailTitle(detail, mediaType, true);
        if (hasLatinLetter(original)) return original;

        String localized = detailTitle(detail, mediaType, false);
        if (hasLatinLetter(localized)) return localized;

        String itemTitle = item == null ? "" : item.getTitle();
        if (hasLatinLetter(itemTitle)) return itemTitle;

        if (!TextUtils.isEmpty(original)) return original;
        if (!TextUtils.isEmpty(localized)) return localized;
        return itemTitle;
    }

    private String englishTranslationTitle(JsonObject detail, String mediaType) {
        JsonArray translations = jsonArray(detail, "translations", "translations");
        String fallback = "";
        for (JsonElement element : translations) {
            if (!element.isJsonObject()) continue;
            JsonObject translation = element.getAsJsonObject();
            if (!"en".equalsIgnoreCase(jsonString(translation, "iso_639_1"))) continue;
            JsonObject data = jsonObject(translation, "data");
            String title = "tv".equals(mediaType) ? jsonString(data, "name", "title") : jsonString(data, "title", "name");
            if (TextUtils.isEmpty(title)) continue;
            if ("US".equalsIgnoreCase(jsonString(translation, "iso_3166_1"))) return title;
            if (TextUtils.isEmpty(fallback)) fallback = title;
        }
        return fallback;
    }

    private String detailTitle(JsonObject detail, String mediaType, boolean original) {
        if (original) {
            return "tv".equals(mediaType)
                    ? jsonString(detail, "original_name", "original_title")
                    : jsonString(detail, "original_title", "original_name");
        }
        return "tv".equals(mediaType)
                ? jsonString(detail, "name", "title")
                : jsonString(detail, "title", "name");
    }

    private boolean hasLatinLetter(String text) {
        if (TextUtils.isEmpty(text)) return false;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')) return true;
        }
        return false;
    }

    private String jsonString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) continue;
            String value = object.get(key).getAsString();
            if (!TextUtils.isEmpty(value)) return value.trim();
        }
        return "";
    }

    private JsonArray jsonArray(JsonObject object, String... keys) {
        com.google.gson.JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return new JsonArray();
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
            current = currentObject.get(key);
        }
        return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
    }

    private JsonObject jsonObject(JsonObject object, String... keys) {
        com.google.gson.JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return null;
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return null;
            current = currentObject.get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
    }

    private void fetchExternalLinkRatings(String imdbId, String omdbApiKey, ViewGroup container,
                                          com.google.android.material.textview.MaterialTextView imdbRatingView,
                                          com.google.android.material.textview.MaterialTextView rottenRatingView,
                                          com.google.android.material.textview.MaterialTextView metacriticRatingView) {
        com.fongmi.android.tv.utils.Task.execute(() -> {
            try {
                String url = "https://www.omdbapi.com/?i=" + imdbId + "&apikey=" + omdbApiKey;
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                okhttp3.Response response = client.newCall(request).execute();
                if (!response.isSuccessful() || response.code() != 200 || response.body() == null) return;

                String json = response.body().string();
                JsonObject jsonObj = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                if (jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) return;

                String imdbRating = "";
                String rottenRating = "";
                String metacriticRating = "";
                for (String[] chip : buildRatingChips(jsonObj)) {
                    if ("IMDB".equals(chip[0])) imdbRating = chip[1];
                    else if ("烂番茄".equals(chip[0])) rottenRating = chip[1];
                    else if ("Metacritic".equals(chip[0]) || "Metascore".equals(chip[0])) metacriticRating = chip[1];
                }

                final String finalImdbRating = imdbRating;
                final String finalRottenRating = rottenRating;
                final String finalMetacriticRating = metacriticRating;
                activity.runOnUiThread(() -> {
                    if (headerRoot == null) return;
                    if (!(container.getTag() instanceof String) || !imdbId.equals(container.getTag())) return;
                    setExternalRating(imdbRatingView, finalImdbRating);
                    setExternalRating(rottenRatingView, finalRottenRating);
                    setExternalRating(metacriticRatingView, finalMetacriticRating);
                });
            } catch (Exception e) {
                android.util.Log.w("TmdbHeaderView", "获取外部链接评分失败: " + e.getMessage());
            }
        });
    }

    private void setExternalRating(com.google.android.material.textview.MaterialTextView view, String rating) {
        if (view == null) return;
        if (TextUtils.isEmpty(rating)) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText("★ " + rating);
        view.setVisibility(View.VISIBLE);
    }

    /**
     * 添加一个外部链接按钮，返回评分 TextView 以便后续更新
     */
    private com.google.android.material.textview.MaterialTextView addExternalLink(ViewGroup container, String name, String url, String rating) {
        // 创建链接项布局
        androidx.appcompat.widget.LinearLayoutCompat linkItem = new androidx.appcompat.widget.LinearLayoutCompat(activity);
        linkItem.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL);
        linkItem.setGravity(android.view.Gravity.CENTER_VERTICAL);
        linkItem.setPadding(0, 12, 0, 12);
        linkItem.setClickable(true);
        linkItem.setFocusable(true);
        android.graphics.drawable.Drawable background = new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                null,
                null
        );
        linkItem.setBackground(background);

        // 平台名称
        com.google.android.material.textview.MaterialTextView nameView = new com.google.android.material.textview.MaterialTextView(activity);
        nameView.setText(name);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(14);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams nameParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        linkItem.addView(nameView, nameParams);

        // 评分视图（始终创建，初始可能为空）
        com.google.android.material.textview.MaterialTextView ratingView = new com.google.android.material.textview.MaterialTextView(activity);
        ratingView.setTextColor(0xFFFFC107);
        ratingView.setTextSize(13);
        ratingView.setGravity(android.view.Gravity.END);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams ratingParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingParams.setMarginEnd(12);
        linkItem.addView(ratingView, ratingParams);

        // 如果已有评分，立即显示
        if (!TextUtils.isEmpty(rating)) {
            ratingView.setText("★ " + rating);
            ratingView.setVisibility(View.VISIBLE);
        } else {
            ratingView.setVisibility(View.GONE);
        }

        // 打开图标
        ImageView iconView = new ImageView(activity);
        iconView.setImageResource(R.drawable.ic_open);
        iconView.setColorFilter(0xFF9E9E9E);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams iconParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(24, 24);
        linkItem.addView(iconView, iconParams);

        // 点击事件
        linkItem.setOnClickListener(v -> {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(url));
                activity.startActivity(intent);
            } catch (Exception e) {
                android.widget.Toast.makeText(activity, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(linkItem);
        return ratingView;  // 返回评分视图，以便后续更新
    }

    /**
     * 清理资源（Activity 销毁时调用）
     */
    public void onDestroy() {
        stopBackdropSlideshow();
        backdropHandler = null;
        backdropRunnable = null;
    }
}
