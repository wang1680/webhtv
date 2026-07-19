package com.fongmi.android.tv.player.danmaku;

import androidx.annotation.Nullable;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class LiveDanmakuWebSocketSession {

    private static final int NORMAL_CLOSE_CODE = 1000;

    private final LiveDanmakuWebSocketClient client;
    private final Listener listener;
    private WebSocket webSocket;
    private String targetUrl;
    private State state = State.IDLE;
    private long generation;
    private boolean released;

    public LiveDanmakuWebSocketSession(Listener listener) {
        this.client = new LiveDanmakuWebSocketClient();
        this.listener = listener;
    }

    public long connect(String url) {
        if (url == null || url.isBlank()) return generation();
        String sourceUrl = url.trim();
        WebSocket previous;
        long currentGeneration;
        synchronized (this) {
            if (released) return generation;
            if (sourceUrl.equals(targetUrl) && (state == State.CONNECTING || state == State.OPEN)) return generation;
            previous = webSocket;
            webSocket = null;
            generation++;
            currentGeneration = generation;
            targetUrl = sourceUrl;
            state = State.CONNECTING;
        }
        closeSocket(previous, "replace");
        notifyState(State.CONNECTING, currentGeneration, sourceUrl, -1, "connect");
        WebSocket created;
        try {
            created = client.newWebSocket(sourceUrl, new SessionListener(currentGeneration, sourceUrl));
        } catch (Throwable error) {
            failBeforeSocket(currentGeneration, sourceUrl, error);
            return currentGeneration;
        }
        synchronized (this) {
            if (released || generation != currentGeneration || state != State.CONNECTING) {
                created.cancel();
            } else if (webSocket == null) {
                webSocket = created;
            } else if (webSocket != created) {
                created.cancel();
            }
        }
        return currentGeneration;
    }

    public long stop(String reason) {
        WebSocket socket;
        long stoppedGeneration;
        String stoppedUrl;
        synchronized (this) {
            if (released) return generation;
            if (webSocket == null && targetUrl == null && (state == State.IDLE || state == State.STOPPED)) return generation;
            socket = webSocket;
            webSocket = null;
            stoppedUrl = targetUrl;
            targetUrl = null;
            generation++;
            stoppedGeneration = generation;
            state = State.STOPPED;
        }
        closeSocket(socket, reason);
        notifyState(State.STOPPED, stoppedGeneration, stoppedUrl, NORMAL_CLOSE_CODE, safeReason(reason));
        return stoppedGeneration;
    }

    public void release() {
        WebSocket socket;
        long releasedGeneration;
        String releasedUrl;
        synchronized (this) {
            if (released) return;
            socket = webSocket;
            webSocket = null;
            releasedUrl = targetUrl;
            targetUrl = null;
            generation++;
            releasedGeneration = generation;
            released = true;
            state = State.RELEASED;
        }
        closeSocket(socket, "release");
        client.release();
        notifyState(State.RELEASED, releasedGeneration, releasedUrl, NORMAL_CLOSE_CODE, "release");
    }

    public synchronized State state() {
        return state;
    }

    public synchronized long generation() {
        return generation;
    }

    private void failBeforeSocket(long callbackGeneration, String sourceUrl, Throwable error) {
        synchronized (this) {
            if (released || generation != callbackGeneration || state != State.CONNECTING) return;
            state = State.STOPPED;
        }
        notifyState(State.STOPPED, callbackGeneration, sourceUrl, -1, errorName(error));
    }

    private boolean markOpen(WebSocket socket, long callbackGeneration) {
        synchronized (this) {
            if (released || generation != callbackGeneration || state != State.CONNECTING) return false;
            if (webSocket != null && webSocket != socket) return false;
            webSocket = socket;
            state = State.OPEN;
            return true;
        }
    }

    private boolean isCurrent(WebSocket socket, long callbackGeneration) {
        synchronized (this) {
            return !released && generation == callbackGeneration && socket == webSocket;
        }
    }

    private boolean markStopped(WebSocket socket, long callbackGeneration) {
        synchronized (this) {
            if (released || generation != callbackGeneration) return false;
            if (webSocket != null && socket != webSocket) return false;
            webSocket = null;
            state = State.STOPPED;
            return true;
        }
    }

    private static void closeSocket(WebSocket socket, String reason) {
        if (socket != null && !socket.close(NORMAL_CLOSE_CODE, safeReason(reason))) socket.cancel();
    }

    private void notifyState(State newState, long callbackGeneration, String sourceUrl, int code, String detail) {
        listener.onStateChanged(newState, callbackGeneration, sourceUrl, code, detail);
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "stop";
        return reason.length() <= 48 ? reason : reason.substring(0, 48);
    }

    private static String errorName(Throwable error) {
        return error == null ? "unknown" : error.getClass().getSimpleName();
    }

    private final class SessionListener extends WebSocketListener {

        private final long callbackGeneration;
        private final String sourceUrl;

        private SessionListener(long callbackGeneration, String sourceUrl) {
            this.callbackGeneration = callbackGeneration;
            this.sourceUrl = sourceUrl;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (markOpen(webSocket, callbackGeneration)) notifyState(State.OPEN, callbackGeneration, sourceUrl, response.code(), "open");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (isCurrent(webSocket, callbackGeneration)) listener.onMessage(callbackGeneration, text);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            if (isCurrent(webSocket, callbackGeneration)) webSocket.close(code, safeReason(reason));
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (markStopped(webSocket, callbackGeneration)) notifyState(State.STOPPED, callbackGeneration, sourceUrl, code, safeReason(reason));
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable throwable, @Nullable Response response) {
            if (markStopped(webSocket, callbackGeneration)) notifyState(State.STOPPED, callbackGeneration, sourceUrl, response == null ? -1 : response.code(), errorName(throwable));
        }
    }

    public enum State {
        IDLE,
        CONNECTING,
        OPEN,
        RETRY_WAIT,
        STOPPED,
        RELEASED
    }

    public interface Listener {

        void onStateChanged(State state, long generation, String url, int code, String detail);

        void onMessage(long generation, String text);
    }
}
