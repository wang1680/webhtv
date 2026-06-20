package com.fongmi.android.tv.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MediaTitle;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.danmaku.DanmakuConfig;
import androidx.media3.ui.danmaku.DanmakuController;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.IjkPlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.SystemPlayerEngine;
import com.fongmi.android.tv.player.exo.TrackUtil;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.LocalProxyDebug;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    private static final long LOCAL_PROXY_READY_TIMEOUT_MS = 5000;
    private static final long LOCAL_PROXY_RETRY_DELAY_MS = 1000;
    private static final int LOCAL_PROXY_MAX_RETRY = 2;
    private static final int[] PLAYER_FALLBACK_ORDER = new int[]{PlayerSetting.EXO, PlayerSetting.IJK, PlayerSetting.SYSTEM};
    private static final float[] SPEED_PRESETS = new float[]{0.5f, 0.75f, 1f, 1.2f, 1.5f, 2f, 3f, 5f};
    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("0.##x");

    private final Runnable runnable;
    private final Callback callback;
    private DanmakuController danmakuController;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;

    private boolean initTrack;
    private int playerType;
    private int retry;
    private int localProxyRetry;
    private int prepareSeq;
    private boolean[] playerFallbackTried;

    public PlayerManager(Callback callback) {
        this.runnable = () -> callback.onError(ResUtil.getString(R.string.error_play_timeout));
        this.playerType = PlayerSetting.getPlayer();
        this.playerFallbackTried = new boolean[PLAYER_FALLBACK_ORDER.length];
        this.engine = buildEngine(playerType, PlayerEngine.HARD);
        this.player = engine.getPlayer();
        this.callback = callback;
    }

    public void release() {
        prepareSeq++;
        player.removeListener(listener);
        App.removeCallbacks(runnable);
        if (engine == null) return;
        engine.release();
        engine = null;
        player = null;
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return engine.getCurrentTracks();
    }

    public List<MediaTitle> getCurrentMediaTitles() {
        return engine.getCurrentMediaTitles();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return engine.haveTrack(type);
    }

    public boolean haveTitle() {
        return engine.haveTitle();
    }

    public boolean haveDanmaku() {
        return getDanmakus() != null && getDanmakus().stream().anyMatch(Danmaku::isSelected);
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getVideoParamsText() {
        StringBuilder builder = new StringBuilder();
        append(builder, "分辨率", getSizeText());
        Format video = getSelectedFormat(C.TRACK_TYPE_VIDEO);
        Format audio = getSelectedFormat(C.TRACK_TYPE_AUDIO);
        if (video != null) {
            if (video.frameRate > 0) append(builder, "帧率", String.format(Locale.getDefault(), "%.2f fps", video.frameRate));
            append(builder, "视频编码", firstText(video.codecs, video.sampleMimeType, video.containerMimeType));
            append(builder, "视频码率", formatBitrate(video.averageBitrate > 0 ? video.averageBitrate : video.peakBitrate));
        }
        if (audio != null) {
            append(builder, "音频编码", firstText(audio.codecs, audio.sampleMimeType, audio.containerMimeType));
            append(builder, "采样率", audio.sampleRate > 0 ? audio.sampleRate + " Hz" : "");
            append(builder, "声道", audio.channelCount > 0 ? String.valueOf(audio.channelCount) : "");
            append(builder, "音频码率", formatBitrate(audio.averageBitrate > 0 ? audio.averageBitrate : audio.peakBitrate));
        }
        append(builder, "解码", getDecodeText());
        append(builder, "倍速", getSpeedText());
        append(builder, "时长", getDurationTime());
        return builder.toString();
    }

    public String getSpeedText() {
        return SPEED_FORMAT.format(getSpeed());
    }

    public String getDecodeText() {
        return engine.getDecodeText();
    }

    public String getPlayerText() {
        return getPlayerText(playerType);
    }

    public int getPlayerType() {
        return playerType;
    }

    public boolean isIjk() {
        return playerType == PlayerSetting.IJK;
    }

    private String getPlayerText(int type) {
        String[] items = ResUtil.getStringArray(R.array.select_player_kernel);
        return type >= 0 && type < items.length ? items[type] : items[PlayerSetting.EXO];
    }

    private Format getSelectedFormat(int type) {
        Tracks tracks = getCurrentTracks();
        if (tracks == null || tracks.isEmpty()) return null;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (group.isTrackSelected(i)) return group.getTrackFormat(i);
            }
        }
        return null;
    }

    private static void append(StringBuilder builder, String name, String value) {
        if (TextUtils.isEmpty(value)) return;
        builder.append(name).append(" : ").append(value).append("\n");
    }

    private static String firstText(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private static String formatBitrate(int bitrate) {
        if (bitrate <= 0) return "";
        return bitrate >= 1_000_000 ? String.format(Locale.getDefault(), "%.2f Mbps", bitrate / 1_000_000f) : bitrate / 1000 + " Kbps";
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        setMediaItem();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        setMediaItem();
    }

    public void setTitle(MediaTitle title) {
        if (spec != null) spec.setUrl(spec.getUri().buildUpon().fragment("title=" + title.index).build().toString());
        setMediaItem();
        seekTo(0);
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        engine.setMetadata(data);
    }

    public void setDanmakuController(DanmakuController controller) {
        danmakuController = controller;
        danmakuController.setOkHttpClient(OkHttp.player());
        danmakuController.setConfig(DanmakuSetting.getConfig());
    }

    public void setDanmakuConfig(DanmakuConfig config) {
        danmakuController.setConfig(config);
    }

    public void setDanmakuEnabled(boolean enabled) {
        danmakuController.setEnabled(enabled);
    }

    public void sendDanmaku(String text) {
        danmakuController.sendNow(text);
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        return setSpeed(nextPresetSpeed());
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    private float nextPresetSpeed() {
        float speed = getSpeed();
        for (float preset : SPEED_PRESETS) if (speed < preset - 0.01f) return preset;
        return SPEED_PRESETS[0];
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) engine.setTrack(tracks);
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        player.stop();
        stopParse();
    }

    public void clearMediaItems() {
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return engine.isRepeatOne();
    }

    public void setRepeatOne(boolean repeat) {
        engine.setRepeatOne(repeat);
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_TEXT_OFFSET)) return player.getTextOffsetMs();
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_TEXT_OFFSET)) player.setTextOffsetMs(offsetMs);
    }

    public long getAudioOffsetMs() {
        if (player.isCommandAvailable(Player.COMMAND_GET_AUDIO_OFFSET)) return player.getAudioOffsetMs();
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
        if (player.isCommandAvailable(Player.COMMAND_SET_AUDIO_OFFSET)) player.setAudioOffsetMs(offsetMs);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
        localProxyRetry = 0;
        resetPlayerFallback();
    }

    public void clear() {
        prepareSeq++;
        spec = null;
    }

    public void resetTrack() {
        engine.resetTrack();
    }

    public void toggleDecode() {
        engine.setDecode(engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
        rebuildPlayer();
        setMediaItem();
    }

    public void togglePlayer() {
        switchPlayer(nextPlayer(playerType));
    }

    public void switchPlayer(int type) {
        if (engine == null || player == null) return;
        type = PlayerSetting.sanitizePlayer(type);
        if (type == playerType) return;
        resetPlayerFallback();
        switchEngine(type, true, true, true);
    }

    private void switchEngine(int type, boolean persist, boolean preserveState, boolean notifyPrepare) {
        long position = preserveState ? getPosition() : 0;
        float speed = preserveState ? getSpeed() : 1f;
        boolean repeat = preserveState && isRepeatOne();
        int decode = engine.getDecode();
        engine.release();
        playerType = type;
        if (persist) PlayerSetting.putPlayer(type);
        engine = buildEngine(playerType, decode);
        player = engine.getPlayer();
        callback.onPlayerRebuild(player);
        if (spec == null || spec.getUrl() == null) return;
        if (persist) setMediaItem();
        else setMediaItemNow(Constant.TIMEOUT_PLAY, notifyPrepare);
        if (position > 0) seekTo(position);
        if (speed != 1f) setSpeed(speed);
        setRepeatOne(repeat);
    }

    private void rebuildPlayer() {
        player = engine.rebuild(listener);
        callback.onPlayerRebuild(player);
    }

    private PlayerEngine buildEngine(int type, int decode) {
        return switch (type) {
            case PlayerSetting.IJK -> new IjkPlayerEngine(decode, listener);
            case PlayerSetting.SYSTEM -> new SystemPlayerEngine(decode, listener);
            default -> new ExoPlayerEngine(decode, listener);
        };
    }

    public void browse(PlaySpec spec) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY);
    }

    public void start(PlaySpec spec, long timeout) {
        this.spec = spec;
        localProxyRetry = 0;
        resetPlayerFallback();
        setMediaItem(timeout);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        stopParse();
        spec = PlaySpec.fromParse(result, key, metadata);
        localProxyRetry = 0;
        resetPlayerFallback();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaItem() {
        setMediaItem(Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(long timeout) {
        if (spec == null || spec.getUrl() == null) return;
        int seq = ++prepareSeq;
        if (LocalProxyDebug.shouldAwaitReady(spec.getUrl())) {
            awaitLocalProxyAndSetMediaItem(seq, timeout);
            return;
        }
        setMediaItemNow(timeout, true);
    }

    private void awaitLocalProxyAndSetMediaItem(int seq, long timeout) {
        PlaySpec target = spec;
        String url = target.getUrl();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await start seq=%d timeout=%d spec=%s", seq, timeout, debugSpec());
        Task.execute(() -> {
            boolean ready = LocalProxyDebug.awaitReady(url, LOCAL_PROXY_READY_TIMEOUT_MS);
            App.post(() -> {
                if (seq != prepareSeq || spec != target || engine == null) {
                    SpiderDebug.log("player", "local proxy await skip seq=%d current=%d ready=%s", seq, prepareSeq, ready);
                    return;
                }
                if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy await done seq=%d ready=%s spec=%s", seq, ready, debugSpec());
                setMediaItemNow(timeout, true);
            });
        });
    }

    private void setMediaItemNow(long timeout, boolean notifyPrepare) {
        if (spec == null || spec.getUrl() == null || engine == null) return;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "setMediaItem timeout=%d notify=%s spec=%s", timeout, notifyPrepare, debugSpec());
        setDanmakus(spec.getDanmakus());
        engine.start(spec.checkUa());
        App.post(runnable, timeout);
        if (notifyPrepare) callback.onPrepare();
        initTrack = false;
    }

    private void setDanmakus(List<Danmaku> items) {
        setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        if (danmakuController == null) return;
        if (spec != null) spec.setDanmaku(item);
        if (item.isEmpty()) danmakuController.clearItems();
        else danmakuController.setDataSource(Uri.parse(item.getRealUrl()));
    }

    public void addDanmaku(Danmaku item) {
        if (danmakuController == null || item.isEmpty()) return;
        if (spec != null) spec.addDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "parseSuccess from=%s url=%s headers=%s", from, summarizeUrl(url), headers == null ? 0 : headers.size());
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        setMediaItem();
    }

    @Override
    public void onParseError() {
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    private String debugSpec() {
        if (spec == null) return "null";
        return "key=" + spec.getKey() +
                ", url=" + summarizeUrl(spec.getUrl()) +
                ", format=" + spec.getFormat() +
                ", headers=" + (spec.getHeaders() == null ? 0 : spec.getHeaders().size()) +
                ", subs=" + (spec.getSubs() == null ? 0 : spec.getSubs().size()) +
                ", danmakus=" + (spec.getDanmakus() == null ? 0 : spec.getDanmakus().size());
    }

    private static String summarizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://");
        builder.append(TextUtils.isEmpty(host) ? "unknown" : host);
        if (port > 0) builder.append(':').append(port);
        if (!TextUtils.isEmpty(path)) builder.append(path.length() > 48 ? path.substring(0, 48) + "..." : path);
        builder.append(" len=").append(url.length());
        return builder.toString();
    }

    private static String stateName(int state) {
        return switch (state) {
            case Player.STATE_IDLE -> "IDLE";
            case Player.STATE_BUFFERING -> "BUFFERING";
            case Player.STATE_READY -> "READY";
            case Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private static String causeChain(Throwable error) {
        if (error == null) return "null";
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (builder.length() > 0) builder.append(" <- ");
            builder.append(current.getClass().getName());
            if (!TextUtils.isEmpty(current.getMessage())) builder.append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return builder.toString();
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "state=%s spec=%s", stateName(state), debugSpec());
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            List<Track> savedTracks = Track.find(getKey());
            setTrack(savedTracks);
            if (PlayerSetting.isPreferAAC() && !TrackUtil.hasTrack(player, savedTracks, C.TRACK_TYPE_AUDIO)) TrackUtil.preferAAC(player);
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onMediaTitlesChanged(@NonNull List<MediaTitle> titles) {
            callback.onTitlesChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            PlayerEngine.ErrorAction action = engine.handleError(e);
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "error code=%d message=%s action=%s retry=%d spec=%s cause=%s", e.errorCode, e.getMessage(), action, retry, debugSpec(), causeChain(e));
            LocalProxyDebug.dumpIfLocalFailure(spec == null ? null : spec.getUrl(), e);
            if (action == PlayerEngine.ErrorAction.FATAL && retryLocalProxy(e)) return;
            if (action == PlayerEngine.ErrorAction.RECOVERED) {
                if (spec != null) setDanmakus(spec.getDanmakus());
                return;
            }
            if (action == PlayerEngine.ErrorAction.FATAL) {
                if (fallbackPlayer(e)) return;
                callback.onError(engine.getErrorMessage(e));
            } else if (++retry > 1) {
                if (fallbackPlayer(e)) return;
                callback.onError(engine.getErrorMessage(e));
            } else {
                toggleDecode();
            }
        }
    };

    private boolean retryLocalProxy(PlaybackException e) {
        if (spec == null || !LocalProxyDebug.isLocalProxyUrl(spec.getUrl())) return false;
        if (!LocalProxyDebug.isConnectionRefused(e)) return false;
        if (++localProxyRetry > LOCAL_PROXY_MAX_RETRY) return false;
        int attempt = localProxyRetry;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry schedule attempt=%d delay=%d spec=%s", attempt, LOCAL_PROXY_RETRY_DELAY_MS, debugSpec());
        App.removeCallbacks(runnable);
        App.post(() -> {
            if (spec == null || attempt != localProxyRetry) return;
            if (SpiderDebug.isEnabled()) SpiderDebug.log("player", "local proxy retry start attempt=%d spec=%s", attempt, debugSpec());
            setMediaItem();
        }, LOCAL_PROXY_RETRY_DELAY_MS);
        return true;
    }

    private boolean fallbackPlayer(PlaybackException e) {
        if (spec == null || spec.getUrl() == null || engine == null) return false;
        int next = nextFallbackPlayer();
        if (next == PlayerSetting.NONE) return false;
        SpiderDebug.log("player", "fallback player from=%s to=%s spec=%s cause=%s", getPlayerText(playerType), getPlayerText(next), debugSpec(), causeChain(e));
        App.removeCallbacks(runnable);
        retry = 0;
        localProxyRetry = 0;
        switchEngine(next, false, true, true);
        return true;
    }

    private int nextFallbackPlayer() {
        markPlayerFallbackTried(playerType);
        for (int type : PLAYER_FALLBACK_ORDER) {
            if (type == playerType || isPlayerFallbackTried(type)) continue;
            markPlayerFallbackTried(type);
            return type;
        }
        return PlayerSetting.NONE;
    }

    private int nextPlayer(int type) {
        for (int i = 0; i < PLAYER_FALLBACK_ORDER.length; i++) {
            if (PLAYER_FALLBACK_ORDER[i] != type) continue;
            return PLAYER_FALLBACK_ORDER[(i + 1) % PLAYER_FALLBACK_ORDER.length];
        }
        return PlayerSetting.EXO;
    }

    private void resetPlayerFallback() {
        playerFallbackTried = new boolean[PLAYER_FALLBACK_ORDER.length];
    }

    private void markPlayerFallbackTried(int type) {
        if (type >= 0 && type < playerFallbackTried.length) playerFallbackTried[type] = true;
    }

    private boolean isPlayerFallbackTried(int type) {
        return type >= 0 && type < playerFallbackTried.length && playerFallbackTried[type];
    }
}
