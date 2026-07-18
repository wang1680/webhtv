package com.fongmi.android.tv.player.diagnostic;

public final class PanDiagnosticVerdict {

    private static final double SUFFICIENT_RATIO = 1.25;
    private static final double MATERIAL_LOSS_RATIO = 0.75;

    private PanDiagnosticVerdict() {
    }

    public static Result resolve(Input input) {
        if (input == null || input.requiredBitsPerSecond <= 0) return result(Cause.INCONCLUSIVE, Confidence.LOW, "缺少资源持续码率");
        long required = input.requiredBitsPerSecond;
        long safe = (long) (required * SUFFICIENT_RATIO);
        if (known(input.baselineBitsPerSecond) && input.baselineBitsPerSecond < required) {
            return result(Cause.DEVICE_NETWORK, Confidence.HIGH, "公共网络基准低于资源最低需求");
        }
        if (known(input.upstreamBitsPerSecond) && input.upstreamBitsPerSecond < required) {
            Confidence confidence = known(input.baselineBitsPerSecond) && input.baselineBitsPerSecond >= safe ? Confidence.HIGH : Confidence.MEDIUM;
            return result(Cause.UPSTREAM_PROVIDER, confidence, "网盘真实上游低于资源最低需求");
        }
        if (known(input.upstreamBitsPerSecond) && known(input.proxyBitsPerSecond)
                && input.upstreamBitsPerSecond >= safe
                && input.proxyBitsPerSecond < safe
                && (input.proxyBitsPerSecond < required || input.proxyBitsPerSecond < input.upstreamBitsPerSecond * MATERIAL_LOSS_RATIO)) {
            return result(Cause.EXTERNAL_PROXY, Confidence.HIGH, "本地JAR/Go代理相对上游存在显著损耗");
        }
        if (known(input.proxyBitsPerSecond) && known(input.dataSourceBitsPerSecond)
                && input.proxyBitsPerSecond >= safe
                && input.dataSourceBitsPerSecond < safe
                && (input.dataSourceBitsPerSecond < required || input.dataSourceBitsPerSecond < input.proxyBitsPerSecond * MATERIAL_LOSS_RATIO)) {
            return result(Cause.APP_DATA_SOURCE, Confidence.HIGH, "App DataSource相对本地代理存在显著损耗");
        }
        if (input.rebufferCount > 0 && input.dataSourceBitsPerSecond >= safe) {
            return result(Cause.PLAYER_BUFFERING, Confidence.MEDIUM, "供数充足但播放器仍发生重缓冲");
        }
        if (input.droppedFrames >= 60 && input.dataSourceBitsPerSecond >= safe) {
            return result(Cause.DECODE_RENDER, Confidence.MEDIUM, "供数充足但解码或渲染掉帧较多");
        }
        if (input.dataSourceBitsPerSecond >= safe && input.rebufferCount == 0 && input.droppedFrames < 60) {
            return result(Cause.SUFFICIENT, Confidence.HIGH, "完整链路满足资源安全吞吐");
        }
        return result(Cause.INCONCLUSIVE, Confidence.LOW, "证据层级不完整，不能可靠归因");
    }

    private static boolean known(long value) {
        return value > 0;
    }

    private static Result result(Cause cause, Confidence confidence, String reason) {
        return new Result(cause, confidence, reason);
    }

    public record Input(long requiredBitsPerSecond, long baselineBitsPerSecond, long upstreamBitsPerSecond,
                        long proxyBitsPerSecond, long dataSourceBitsPerSecond, int rebufferCount, int droppedFrames) {
    }

    public record Result(Cause cause, Confidence confidence, String reason) {
    }

    public enum Cause {
        DEVICE_NETWORK,
        UPSTREAM_PROVIDER,
        EXTERNAL_PROXY,
        APP_DATA_SOURCE,
        PLAYER_BUFFERING,
        DECODE_RENDER,
        SUFFICIENT,
        INCONCLUSIVE
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }
}
