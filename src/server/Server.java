package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central Chat Server managing both TCP and WebSocket clients.
 * Supports persistent user profiles and seamless page-refresh reconnection.
 */
public class Server {

    public static final int TCP_PORT = 5000;
    public static final int DEFAULT_WS_PORT = 8081;

    public static int getWsPort() {
        String envPort = System.getenv("WS_PORT");
        if (envPort == null || envPort.isEmpty()) {
            envPort = System.getenv("PORT");
        }
        if (envPort != null && !envPort.isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_WS_PORT;
    }

    // Thread-safe list holding all active client sessions (TCP and WS)
    public static final List<ChatSession> clients = new CopyOnWriteArrayList<>();

    /**
     * Broadcast a message to all connected clients.
     */
    public static void broadcast(String message) {
        for (ChatSession client : clients) {
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                System.err.println("Broadcast delivery error: " + e.getMessage());
            }
        }
    }

    /**
     * Update online users list for every connected client.
     */
    public static void updateUserLists() {
        for (ChatSession receiver : clients) {
            if (receiver.getUsername() == null) continue;

            StringBuilder userList = new StringBuilder("USERLIST:");
            for (ChatSession client : clients) {
                if (client.getUsername() != null && !client.getUsername().equalsIgnoreCase(receiver.getUsername())) {
                    userList.append(client.getUsername()).append(",");
                }
            }
            receiver.sendMessage(userList.toString());
        }
    }

    /**
     * Validate that a username is a real nickname and not an HTTP request or scanner.
     */
    public static boolean isValidUsername(String name) {
        if (name == null) return false;
        name = name.trim();
        if (name.length() < 1 || name.length() > 25) return false;
        if (name.contains("HTTP/") || name.startsWith("GET ") || name.startsWith("HEAD ") || name.startsWith("POST ")) {
            return false;
        }
        return name.matches("^[\\p{L}0-9_ -]+$");
    }

    /**
     * Called when a client identifies with a username (backward compatible).
     */
    public static void handleClientConnected(ChatSession session, String username) {
        handleClientConnected(session, username, username);
    }

    /**
     * Called when a client identifies with both a persistent unique userId and a display username.
     */
    public static void handleClientConnected(ChatSession session, String userId, String username) {
        if (!isValidUsername(username)) {
            session.close();
            return;
        }

        // Check if an existing session is already active for this userId or username
        ChatSession existing = findClientByUserId(userId);
        if (existing == null) {
            existing = findClient(username);
        }

        boolean isReconnect = false;
        if (existing != null && existing != session) {
            System.out.println("🔄 " + username + " (" + userId + ") reconnected. Replacing session.");
            clients.remove(existing);
            existing.close();
            isReconnect = true;
        }

        session.setUserId(userId != null ? userId : username);
        session.setUsername(username);
        clients.add(session);

        if (!isReconnect) {
            System.out.println("🟢 " + username + " joined the chat. (Total: " + clients.size() + ")");
            broadcast("🟢 " + username + " joined the chat");
        }

        session.sendMessage("WELCOME:" + session.getUserId() + ":" + username);
        updateUserLists();
    }

    /**
     * Handle incoming messages from any client (TCP or WebSocket).
     */
    public static void handleIncomingMessage(ChatSession sender, String message) {
        if (message == null || sender.getUsername() == null) return;
        message = message.trim();
        String username = sender.getUsername();

        // Typing notification
        if (message.equals("TYPING")) {
            for (ChatSession client : clients) {
                if (client != sender) {
                    client.sendMessage("TYPING:" + username);
                }
            }
            return;
        }

        // Exit notification
        if (message.equalsIgnoreCase("exit")) {
            removeClient(sender);
            sender.close();
            return;
        }

        // Private message: PRIVATE:receiver:content
        if (message.startsWith("PRIVATE:")) {
            handlePrivateMessage(sender, message);
            return;
        }

        // Online users command
        if (message.equalsIgnoreCase("/users")) {
            StringBuilder users = new StringBuilder("ONLINE USERS:\n");
            for (ChatSession client : clients) {
                if (client.getUsername() != null) {
                    users.append("• ").append(client.getUsername()).append("\n");
                }
            }
            sender.sendMessage(users.toString());
            return;
        }

        // Normal group broadcast
        broadcast(username + ": " + message);
    }

    /**
     * Handle private direct messaging between two users.
     */
    private static void handlePrivateMessage(ChatSession sender, String message) {
        try {
            String[] parts = message.split(":", 3);
            if (parts.length != 3) return;

            String receiverName = parts[1].trim();
            String privateMessage = parts[2];

            ChatSession receiver = findClient(receiverName);
            if (receiver != null) {
                receiver.sendMessage("PRIVATE:" + sender.getUsername() + ":" + privateMessage);
            } else {
                sender.sendMessage("SYSTEM: User " + receiverName + " is offline.");
            }
        } catch (Exception e) {
            sender.sendMessage("SYSTEM: Failed to send private message.");
        }
    }

    /**
     * Remove disconnected client and broadcast leave notification unless already reconnected.
     */
    public static void removeClient(ChatSession client) {
        if (clients.remove(client)) {
            String username = client.getUsername();
            String userId = client.getUserId();
            if (username != null) {
                // Check if user is still active on another session (e.g. fast page refresh)
                ChatSession active = findClientByUserId(userId);
                if (active == null) {
                    active = findClient(username);
                }
                if (active == null) {
                    System.out.println("🔴 " + username + " disconnected. (Total: " + clients.size() + ")");
                    broadcast("🔴 " + username + " left the chat");
                    updateUserLists();
                }
            }
        }
    }

    /**
     * Find a connected user by persistent userId.
     */
    public static ChatSession findClientByUserId(String userId) {
        if (userId == null) return null;
        for (ChatSession client : clients) {
            if (userId.equals(client.getUserId())) {
                return client;
            }
        }
        return null;
    }

    /**
     * Find a connected user by username.
     */
    public static ChatSession findClient(String username) {
        if (username == null) return null;
        for (ChatSession client : clients) {
            if (client.getUsername() != null && client.getUsername().equalsIgnoreCase(username)) {
                return client;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println("====================================");
        System.out.println("   CHAT SERVER STARTING...");
        System.out.println("====================================");

        // 1. Start WebSocket Server (port 8081 by default, or configurable)
        WsServer wsServer = new WsServer(getWsPort());
        wsServer.start();

        // 2. Start TCP Server on port 5000 (Native TCP desktop clients)
        Thread tcpThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                System.out.println("   TCP SOCKET PORT: " + TCP_PORT);
                System.out.println("====================================");
                System.out.println("Ready for connections!");

                while (true) {
                    Socket socket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(socket);
                    clientHandler.start();
                }
            } catch (IOException e) {
                System.err.println("TCP Server Error: " + e.getMessage());
            }
        });
        tcpThread.setDaemon(false);
        tcpThread.start();
    }
}
