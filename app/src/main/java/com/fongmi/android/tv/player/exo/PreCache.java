package com.fongmi.android.tv.player.exo;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.source.preload.PreCacheHelper;

import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PreCache implements Player.Listener {

    private static final long TICK_MS = 5000;
    private static final long MIN_STEP_MS = 5000;
    private static final long MAX_STEP_MS = 30000;
    private static final long BUFFER_GAP_MS = 1250;
    private static final int STEP_DIV = 4;

    private ThreadPoolExecutor executor;
    private PreCacheHelper helper;
    private Handler handler;
    private HandlerThread worker;
    private Player player;
    private PlaybackRoute route;
    private Runnable scheduledTask;
    private int threads;
    private long generation;
    private long lastStartMs;
    private long seekStartMs;
    private boolean playable;
    private BufferGate bufferGate;
    private AutoPreloadPolicy autoPolicy;
    private DiagnosticState diagnosticState = DiagnosticState.STOPPED;

    public void start(Player player, MediaItem mediaItem) {
        stop();
        PriorityTaskDataSource.resetDiagnostics();
        if (!PreloadSetting.isPreload(PlayerSetting.EXO) || !canPreCache(mediaItem)) return;
        this.player = player;
        this.handler = new Handler(player.getApplicationLooper());
        this.route = PlaybackRoute.classify(mediaItem.localConfiguration.uri.toString());
        this.autoPolicy = PlaybackPerformanceSetting.isAuto(PlayerSetting.EXO) ? new AutoPreloadPolicy() : null;
        this.helper = createHelper(mediaItem);
        clearSeek();
        lastStartMs = C.TIME_UNSET;
        playable = false;
        bufferGate = BufferGate.FIRST_FRAME;
        this.player.addListener(this);
        diagnostic(DiagnosticState.WAIT_FIRST_FRAME, "start generation=%d route=%s configuredThreads=%d effectiveThreads=%d durationTargetMs=%d cacheCapacityBytes=%d", generation, route, PreloadSetting.getPreloadThreads(PlayerSetting.EXO), threads, PreloadSetting.getPreloadDurationMs(PlayerSetting.EXO), MediaSourceFactory.getCacheCapacityBytes());
        check();
    }

    public void stop() {
        boolean active = helper != null || player != null;
        if (active) {
            PriorityTaskDataSource.DiagnosticSnapshot priority = PriorityTaskDataSource.getDiagnosticSnapshot();
            diagnostic(DiagnosticState.STOPPED, "stop generation=%d waitCount=%d waitTotalMs=%d", generation, priority.waitCount(), priority.waitTotalMs());
        }
        stopCurrentTask();
        if (player != null) player.removeListener(this);
        if (helper != null) helper.release(false);
        handler = null;
        helper = null;
        player = null;
        route = null;
        autoPolicy = null;
        clearSeek();
        lastStartMs = C.TIME_UNSET;
        playable = false;
        bufferGate = BufferGate.FIRST_FRAME;
        diagnosticState = DiagnosticState.STOPPED;
    }

    public void release() {
        stop();
        ThreadPoolExecutor retiringExecutor = executor;
        HandlerThread retiringWorker = worker;
        executor = null;
        worker = null;
        threads = 0;
        if (retiringWorker == null) {
            shutdownExecutor(retiringExecutor);
            return;
        }
        // PreCacheHelper.release() posts cancellation to this same looper.
        // Queue resource teardown behind it so SegmentDownloader cannot submit
        // work to an executor which has already entered SHUTTING_DOWN.
        new Handler(retiringWorker.getLooper()).post(() -> {
            shutdownExecutor(retiringExecutor);
            retiringWorker.quitSafely();
        });
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_BUFFERING) {
            if (autoPolicy != null) autoPolicy.disrupt(SystemClock.elapsedRealtime());
            if (playable) bufferGate = BufferGate.RECOVERY;
            diagnostic(DiagnosticState.CANCELLED_BUFFERING, "cancel reason=buffering generation=%d position=%d buffered=%d loading=%s", generation, player.getCurrentPosition(), player.getTotalBufferedDuration(), player.isLoading());
            stopCurrentTask();
        } else if (state == Player.STATE_READY && playable) {
            check();
        } else if (isStopped(state)) {
            cancel();
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        markPlayable();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!isPlaying || playable || player == null) return;
        if (!player.getCurrentTracks().containsType(C.TRACK_TYPE_VIDEO) && player.getCurrentTracks().containsType(C.TRACK_TYPE_AUDIO)) {
            markPlayable();
        }
    }

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
        if (playable && bufferGate != BufferGate.OPEN) check();
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
        if (!isSeek(reason) || helper == null) return;
        diagnostic(DiagnosticState.CANCELLED_SEEK, "cancel reason=seek generation=%d oldPosition=%d newPosition=%d", generation, oldPosition.positionMs, newPosition.positionMs);
        if (autoPolicy != null) autoPolicy.disrupt(SystemClock.elapsedRealtime());
        stopCurrentTask();
        markSeek(newPosition.positionMs);
        if (playable) bufferGate = BufferGate.RECOVERY;
        check();
    }

    private void check() {
        check(generation);
    }

    private void check(long expectedGeneration) {
        if (expectedGeneration != generation) return;
        cancel();
        if (update()) schedule(expectedGeneration);
    }

    private boolean update() {
        if (helper == null || player == null) return false;
        if (!PreloadSetting.isPreload(PlayerSetting.EXO)) {
            stop();
            return false;
        }
        int state = player.getPlaybackState();
        if (isStopped(state)) return false;
        if (state != Player.STATE_READY) return true;
        if (!playable) {
            diagnostic(DiagnosticState.WAIT_FIRST_FRAME, "wait firstFrame generation=%d position=%d buffered=%d loading=%s", generation, player.getCurrentPosition(), player.getTotalBufferedDuration(), player.isLoading());
            return true;
        }
        if (bufferGate != BufferGate.OPEN) {
            SafeBufferStatus status = getSafeBufferStatus();
            if (!status.safe()) {
                DiagnosticState waitState = status.recovery() ? DiagnosticState.WAIT_RECOVERY_BUFFER : DiagnosticState.WAIT_INITIAL_BUFFER;
                diagnostic(waitState, "wait buffer generation=%d recovery=%s requiredMs=%d bufferedMs=%d loading=%s bitrate=%d effectiveCapacityBytes=%d capacityDurationMs=%d", generation, status.recovery(), status.requiredMs(), status.bufferedMs(), status.loading(), status.bitrate(), status.effectiveCapacityBytes(), status.capacityDurationMs());
                return true;
            }
        }
        bufferGate = BufferGate.OPEN;
        if (player.isCurrentMediaItemLive()) {
            diagnostic(DiagnosticState.SKIPPED, "skip reason=live generation=%d", generation);
            stop();
            return false;
        }
        AutoPreloadPolicy.Decision autoDecision = getAutoDecision();
        if (autoDecision != null && !autoDecision.enabled()) {
            diagnostic(DiagnosticState.PAUSED_AUTO, "pause reason=auto generation=%d route=%s mode=%s position=%d buffered=%d bandwidth=%d bitrate=%d", generation, route, autoDecision.mode(), player.getCurrentPosition(), player.getTotalBufferedDuration(), PlaybackAnalyticsListener.getSnapshot().bandwidthEstimate(), getSelectedBitrate());
            return true;
        }
        if (autoDecision != null) setEffectiveThreads(autoDecision.threads());
        long startMs = getStart();
        long lengthMs = getLength(startMs, autoDecision == null ? PreloadSetting.getPreloadDurationMs(PlayerSetting.EXO) : autoDecision.durationMs());
        if (lengthMs <= 0) {
            diagnostic(DiagnosticState.NO_RANGE, "skip reason=noRange generation=%d startMs=%d durationMs=%d", generation, startMs, player.getDuration());
            clearSeek();
            return true;
        }
        if (!shouldPreCache(startMs)) return true;
        long bitrate = getSelectedBitrate();
        long estimatedBytes = ExoPlaybackDiagnostics.estimateBytes(bitrate, lengthMs);
        PriorityTaskDataSource.DiagnosticSnapshot priority = PriorityTaskDataSource.getDiagnosticSnapshot();
        ExoPlaybackDiagnostics.logPreload("start generation=%d route=%s threads=%d startMs=%d lengthMs=%d estimatedBytes=%d bitrate=%d position=%d buffered=%d loading=%s waitCount=%d waitTotalMs=%d", generation, route, threads, startMs, lengthMs, estimatedBytes, bitrate, player.getCurrentPosition(), player.getTotalBufferedDuration(), player.isLoading(), priority.waitCount(), priority.waitTotalMs());
        diagnosticState = DiagnosticState.PRELOADING;
        helper.preCache(startMs, lengthMs);
        lastStartMs = startMs;
        clearSeek();
        return true;
    }

    private void schedule(long expectedGeneration) {
        if (handler == null || expectedGeneration != generation) return;
        scheduledTask = () -> check(expectedGeneration);
        handler.postDelayed(scheduledTask, TICK_MS);
    }

    private void cancel() {
        if (handler != null && scheduledTask != null) handler.removeCallbacks(scheduledTask);
        scheduledTask = null;
    }

    private void stopCurrentTask() {
        generation++;
        cancel();
        if (helper != null) helper.stop();
        lastStartMs = C.TIME_UNSET;
    }

    private SafeBufferStatus getSafeBufferStatus() {
        long durationMs = player.getDuration();
        long positionMs = player.getCurrentPosition();
        long remainingMs = durationMs > 0 && positionMs >= 0 ? Math.max(0, durationMs - positionMs) : C.TIME_UNSET;
        boolean recovery = bufferGate == BufferGate.RECOVERY;
        long bitrate = getSelectedBitrate();
        int effectiveCapacityBytes = ExoUtil.getBufferBudget().effectiveTargetBytes();
        long requiredMs = PreCachePolicy.safeBufferTargetMs(recovery, remainingMs, bitrate, effectiveCapacityBytes);
        long bufferedMs = player.getTotalBufferedDuration();
        boolean loading = player.isLoading();
        boolean safe = PreCachePolicy.hasSafeBuffer(bufferedMs, loading, requiredMs, recovery);
        return new SafeBufferStatus(safe, recovery, requiredMs, bufferedMs, loading, bitrate, effectiveCapacityBytes, ExoPlaybackDiagnostics.capacityDurationMs(effectiveCapacityBytes, bitrate));
    }

    private long getSelectedBitrate() {
        Format video = TrackUtil.selectedFormat(player.getCurrentTracks(), C.TRACK_TYPE_VIDEO);
        Format audio = TrackUtil.selectedFormat(player.getCurrentTracks(), C.TRACK_TYPE_AUDIO);
        return ExoPlaybackDiagnostics.combinedBitrate(video, audio);
    }

    private void markPlayable() {
        if (!playable) {
            playable = true;
            bufferGate = BufferGate.INITIAL;
        }
        check();
    }

    private PreCacheHelper createHelper(MediaItem mediaItem) {
        DataSource.Factory upstreamFactory = MediaSourceFactory.createUpstreamDataSourceFactory(ExoUtil.extractHeaders(mediaItem));
        return new PreCacheHelper.Factory(MediaSourceFactory.getCache(), upstreamFactory, ExoUtil.buildRenderersFactory(), getWorker().getLooper()).setDownloadExecutor(getExecutor()).create(mediaItem);
    }

    private boolean canPreCache(MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.localConfiguration == null) return false;
        MediaItem.LocalConfiguration local = mediaItem.localConfiguration;
        String scheme = local.uri.getScheme();
        String url = local.uri.toString();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && !MediaSourceFactory.isConcatenatingUrl(url);
    }

    private long getStart() {
        if (hasSeek()) return Math.max(0, seekStartMs);
        long bufferedPositionMs = player.getBufferedPosition();
        if (bufferedPositionMs < 0) return Math.max(0, player.getCurrentPosition());
        return bufferedPositionMs > Long.MAX_VALUE - BUFFER_GAP_MS ? bufferedPositionMs : bufferedPositionMs + BUFFER_GAP_MS;
    }

    private boolean shouldPreCache(long startMs) {
        if (hasSeek()) return true;
        if (lastStartMs == C.TIME_UNSET) return true;
        return Math.abs(startMs - lastStartMs) >= getStep();
    }

    private boolean isStopped(int state) {
        return state == Player.STATE_ENDED || state == Player.STATE_IDLE;
    }

    private long getLength(long startMs, long durationTargetMs) {
        long durationMs = player.getDuration();
        if (durationMs <= 0) return 0;
        long remainingMs = Math.max(0, durationMs - startMs);
        return PreCachePolicy.preloadLengthMs(durationTargetMs, remainingMs, getSelectedBitrate(), MediaSourceFactory.getCacheCapacityBytes());
    }

    private long getStep() {
        return Math.clamp(PreloadSetting.getPreloadDurationMs(PlayerSetting.EXO) / STEP_DIV, MIN_STEP_MS, MAX_STEP_MS);
    }

    private void markSeek(long startMs) {
        seekStartMs = startMs;
    }

    private void clearSeek() {
        seekStartMs = C.TIME_UNSET;
    }

    private boolean hasSeek() {
        return seekStartMs != C.TIME_UNSET;
    }

    private Executor getExecutor() {
        int requested = PreloadSetting.getPreloadThreads(PlayerSetting.EXO);
        int count = route == null ? requested : route.effectivePreloadThreads(requested);
        if (autoPolicy != null) count = route == null ? AutoPreloadPolicy.NORMAL_THREADS : route.effectivePreloadThreads(AutoPreloadPolicy.NORMAL_THREADS);
        if (executor != null) {
            setEffectiveThreads(count);
            return executor;
        }
        retireExecutor();
        threads = count;
        return executor = new ThreadPoolExecutor(count, count, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private AutoPreloadPolicy.Decision getAutoDecision() {
        if (autoPolicy == null) return null;
        PlaybackAnalyticsListener.Snapshot snapshot = PlaybackAnalyticsListener.getSnapshot();
        return autoPolicy.evaluate(SystemClock.elapsedRealtime(), route, player.getTotalBufferedDuration(), getSelectedBitrate(), snapshot.bandwidthEstimate(), snapshot.rebufferCount(), player.isLoading());
    }

    private void setEffectiveThreads(int requested) {
        if (executor == null) return;
        int count = route == null ? requested : route.effectivePreloadThreads(requested);
        if (count == threads) return;
        if (count > threads) {
            executor.setMaximumPoolSize(count);
            executor.setCorePoolSize(count);
        } else {
            executor.setCorePoolSize(count);
            executor.setMaximumPoolSize(count);
        }
        threads = count;
        ExoPlaybackDiagnostics.logPreload("auto mode threads=%d route=%s generation=%d", threads, route, generation);
    }

    private void retireExecutor() {
        if (executor == null) return;
        ThreadPoolExecutor retiringExecutor = executor;
        executor = null;
        if (worker == null) shutdownExecutor(retiringExecutor);
        else new Handler(worker.getLooper()).post(() -> shutdownExecutor(retiringExecutor));
    }

    private void shutdownExecutor(ThreadPoolExecutor target) {
        if (target == null) return;
        target.shutdownNow();
    }

    private HandlerThread getWorker() {
        if (worker != null) return worker;
        worker = new HandlerThread("CurrentMediaPreCache");
        worker.start();
        return worker;
    }

    private boolean isSeek(int reason) {
        return reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
    }

    private void diagnostic(DiagnosticState state, String format, Object... args) {
        if (diagnosticState == state) return;
        diagnosticState = state;
        ExoPlaybackDiagnostics.logPreload(format, args);
    }

    private record SafeBufferStatus(boolean safe, boolean recovery, long requiredMs, long bufferedMs, boolean loading, long bitrate, int effectiveCapacityBytes, long capacityDurationMs) {
    }

    private enum BufferGate {
        FIRST_FRAME,
        INITIAL,
        RECOVERY,
        OPEN
    }

    private enum DiagnosticState {
        STOPPED,
        WAIT_FIRST_FRAME,
        WAIT_INITIAL_BUFFER,
        WAIT_RECOVERY_BUFFER,
        PRELOADING,
        CANCELLED_BUFFERING,
        CANCELLED_SEEK,
        NO_RANGE,
        PAUSED_AUTO,
        SKIPPED
    }
}
