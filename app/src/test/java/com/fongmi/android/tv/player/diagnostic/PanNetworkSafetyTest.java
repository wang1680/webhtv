package com.fongmi.android.tv.player.diagnostic;

import org.junit.Test;

import java.io.IOException;

import okhttp3.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class PanNetworkSafetyTest {

    @Test
    public void stripsSensitiveHeadersWhenRedirectingAcrossHosts() {
        Request request = new Request.Builder()
                .url("https://cdn.example.com/file")
                .header("Cookie", "session=secret")
                .header("Authorization", "Bearer secret")
                .header("X-Api-Key", "secret")
                .header("X-Playback-Token", "secret")
                .header("User-Agent", "UA")
                .build();

        Request sanitized = PanNetworkSafety.stripSensitiveHeadersOnRedirect(request, "origin.example.com");

        assertNull(sanitized.header("Cookie"));
        assertNull(sanitized.header("Authorization"));
        assertNull(sanitized.header("X-Api-Key"));
        assertNull(sanitized.header("X-Playback-Token"));
        assertEquals("UA", sanitized.header("User-Agent"));
    }

    @Test
    public void keepsHeadersWhenHostDoesNotChange() {
        Request request = new Request.Builder()
                .url("https://cdn.example.com/file")
                .header("Cookie", "session=secret")
                .header("Authorization", "Bearer secret")
                .build();

        Request sanitized = PanNetworkSafety.stripSensitiveHeadersOnRedirect(request, "cdn.example.com");

        assertEquals("session=secret", sanitized.header("Cookie"));
        assertEquals("Bearer secret", sanitized.header("Authorization"));
    }

    @Test
    public void rejectsUnsafeRemoteTargetsBeforeConnection() {
        try {
            PanNetworkSafety.requireSafeRemoteHost("10.0.0.1");
            fail("Expected IOException");
        } catch (IOException expected) {
            // expected
        }
    }
}
