package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackRoute;

public final class MpvNetworkRecoveryPolicy {

    private MpvNetworkRecoveryPolicy() {
    }

    public static Decision resolve(String playableUri) {
        PlaybackRoute route = PlaybackRoute.classify(playableUri);
        return switch (route) {
            case DIRECT_REMOTE_HTTP -> new Decision(route, "mpv-native-curl", true, false, false);
            case APP_LOCAL_SERVICE -> new Decision(route, "app-local-service", false, true, false);
            case EXTERNAL_LOOPBACK_PROXY -> new Decision(route, "external-loopback-proxy", false, true, false);
            default -> new Decision(route, "not-applicable", false, false, false);
        };
    }

    public record Decision(PlaybackRoute route, String recoveryOwner, boolean nativeRemoteRecovery, boolean proxyOwnsUpstreamRecovery, boolean appReconnectOverlay) {
    }
}
