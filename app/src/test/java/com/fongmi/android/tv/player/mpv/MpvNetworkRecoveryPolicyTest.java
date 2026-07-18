package com.fongmi.android.tv.player.mpv;

import com.fongmi.android.tv.player.PlaybackRoute;
import com.github.catvod.Proxy;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvNetworkRecoveryPolicyTest {

    @Before
    public void setUp() {
        Proxy.set(9988);
    }

    @Test
    public void directRemoteHttpReliesOnNativeCurlRecoveryOnly() {
        MpvNetworkRecoveryPolicy.Decision decision = MpvNetworkRecoveryPolicy.resolve("https://cdn.example.com/movie.mkv");
        assertEquals(PlaybackRoute.DIRECT_REMOTE_HTTP, decision.route());
        assertEquals("mpv-native-curl", decision.recoveryOwner());
        assertTrue(decision.nativeRemoteRecovery());
        assertFalse(decision.proxyOwnsUpstreamRecovery());
        assertFalse(decision.appReconnectOverlay());
    }

    @Test
    public void appLocalServiceOwnsItsUpstreamRecovery() {
        MpvNetworkRecoveryPolicy.Decision decision = MpvNetworkRecoveryPolicy.resolve("http://127.0.0.1:9988/proxy/video");
        assertEquals(PlaybackRoute.APP_LOCAL_SERVICE, decision.route());
        assertEquals("app-local-service", decision.recoveryOwner());
        assertFalse(decision.nativeRemoteRecovery());
        assertTrue(decision.proxyOwnsUpstreamRecovery());
        assertFalse(decision.appReconnectOverlay());
    }

    @Test
    public void externalLoopbackProxyDoesNotReceiveRemoteReconnectOverlay() {
        MpvNetworkRecoveryPolicy.Decision decision = MpvNetworkRecoveryPolicy.resolve("http://127.0.0.1:7777/video");
        assertEquals(PlaybackRoute.EXTERNAL_LOOPBACK_PROXY, decision.route());
        assertEquals("external-loopback-proxy", decision.recoveryOwner());
        assertFalse(decision.nativeRemoteRecovery());
        assertTrue(decision.proxyOwnsUpstreamRecovery());
        assertFalse(decision.appReconnectOverlay());
    }

    @Test
    public void nonHttpSourcesDoNotReceiveReconnectOptions() {
        MpvNetworkRecoveryPolicy.Decision decision = MpvNetworkRecoveryPolicy.resolve("file:///storage/emulated/0/movie.mkv");
        assertEquals(PlaybackRoute.OTHER, decision.route());
        assertEquals("not-applicable", decision.recoveryOwner());
        assertFalse(decision.nativeRemoteRecovery());
        assertFalse(decision.proxyOwnsUpstreamRecovery());
        assertFalse(decision.appReconnectOverlay());
    }
}
