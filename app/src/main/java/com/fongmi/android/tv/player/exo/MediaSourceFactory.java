package com.fongmi.android.tv.player.exo;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.util.Locale;

public class MediaSourceFactory implements MediaSource.Factory {

    private final DefaultMediaSourceFactory defaultMediaSourceFactory;
    private DefaultMediaSourceFactory directMediaSourceFactory;
    private HttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory dataSourceFactory;
    private ExtractorsFactory extractorsFactory;
    private static SimpleCache cache;
    private static long cacheSize;

    public MediaSourceFactory() {
        defaultMediaSourceFactory = new DefaultMediaSourceFactory(getDataSourceFactory(), getExtractorsFactory());
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return defaultMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        getHttpDataSourceFactory().setDefaultRequestProperties(ExoUtil.extractHeaders(mediaItem));
        String url = mediaItem.requestMetadata.mediaUri != null ? mediaItem.requestMetadata.mediaUri.toString() : "";
        if (url.contains("***") && url.contains("|||")) return createConcatenatingMediaSource(mediaItem, url);
        if (isHls(mediaItem, url)) return getDirectMediaSourceFactory().createMediaSource(mediaItem);
        if (isLocalProxy(url)) return getDirectMediaSourceFactory().createMediaSource(mediaItem);
        else return defaultMediaSourceFactory.createMediaSource(mediaItem);
    }

    private MediaSource createConcatenatingMediaSource(MediaItem mediaItem, String url) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        for (String split : url.split("\\*\\*\\*")) {
            String[] info = split.split("\\|\\|\\|");
            if (info.length >= 2) builder.add(defaultMediaSourceFactory.createMediaSource(mediaItem.buildUpon().setUri(Uri.parse(info[0])).build()), Long.parseLong(info[1]));
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 10);
        return extractorsFactory;
    }

    private DataSource.Factory getDataSourceFactory() {
        if (dataSourceFactory == null) {
            DataSource.Factory upstreamFactory = new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory());
            dataSourceFactory = PlayerSetting.getPlayCacheSize() > 0 ? getCacheDataSource(upstreamFactory) : upstreamFactory;
        }
        return dataSourceFactory;
    }

    private DefaultMediaSourceFactory getDirectMediaSourceFactory() {
        if (directMediaSourceFactory == null) directMediaSourceFactory = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory()), getExtractorsFactory());
        return directMediaSourceFactory;
    }

    private DataSource.Factory getCacheDataSource(DataSource.Factory upstreamFactory) {
        SimpleCache cache = getCache();
        if (cache == null) return upstreamFactory;
        return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstreamFactory).setCacheWriteDataSinkFactory(new CacheDataSink.Factory().setCache(cache)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private HttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.player());
        return httpDataSourceFactory;
    }

    private static boolean isLocalProxy(String url) {
        return url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost");
    }

    private static boolean isHls(MediaItem mediaItem, String url) {
        String mimeType = mediaItem.localConfiguration == null ? null : mediaItem.localConfiguration.mimeType;
        if (MimeTypes.APPLICATION_M3U8.equals(mimeType)) return true;
        return url.toLowerCase(Locale.ROOT).contains("m3u8");
    }

    private static SimpleCache getCache() {
        long size = PlayerSetting.getPlayCacheSize();
        if (size <= 0) return null;
        if (cache != null && cacheSize != size) {
            cache.release();
            cache = null;
        }
        if (cache == null) {
            try {
                cache = new SimpleCache(Path.exo(), new LeastRecentlyUsedCacheEvictor(size), new StandaloneDatabaseProvider(App.get()));
                cacheSize = size;
            } catch (Throwable ignored) {
                cache = null;
                cacheSize = 0;
            }
        }
        return cache;
    }
}
