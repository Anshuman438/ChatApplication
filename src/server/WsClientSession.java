package server;

import org.java_websocket.WebSocket;

/**
 * WebSocket implementation of ChatSession.
 */
public class WsClientSession implements ChatSession {
    private final WebSocket webSocket;
    private String username;

    public WsClientSession(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void sendMessage(String message) {
        if (isOpen()) {
            webSocket.send(message);
        }
    }

    @Override
    public void close() {
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.close();
        }
    }

    @Override
    public boolean isOpen() {
        return webSocket != null && webSocket.isOpen();
    }
}
