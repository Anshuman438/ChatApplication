package server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

/**
 * WebSocket Server listening on port 8081.
 * Bridges browser and desktop WebSocket clients into the central Server.
 */
public class WsServer extends WebSocketServer {

    public WsServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        WsClientSession session = new WsClientSession(conn);
        conn.setAttachment(session);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        WsClientSession session = conn.getAttachment();
        if (session != null) {
            Server.removeClient(session);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        WsClientSession session = conn.getAttachment();
        if (session == null) return;

        if (session.getUsername() == null) {
            String msg = message != null ? message.trim() : "";
            if (msg.startsWith("IDENTIFY:")) {
                String[] parts = msg.split(":", 3);
                if (parts.length >= 3) {
                    String userId = parts[1].trim();
                    String username = parts[2].trim();
                    if (Server.isValidUsername(username)) {
                        Server.handleClientConnected(session, userId, username);
                        return;
                    }
                }
            } else if (!msg.isEmpty() && Server.isValidUsername(msg)) {
                Server.handleClientConnected(session, msg, msg);
                return;
            }
            conn.close();
        } else {
            Server.handleIncomingMessage(session, message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket Error: " + (ex != null ? ex.getMessage() : "Unknown error"));
    }

    @Override
    public void onStart() {
        System.out.println("   WEBSOCKET PORT : " + getPort());
    }
}
