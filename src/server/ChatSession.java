package server;

/**
 * Common interface representing a chat client connection (TCP or WebSocket).
 */
public interface ChatSession {
    String getUserId();
    void setUserId(String userId);

    String getUsername();
    void setUsername(String username);

    void sendMessage(String message);
    void close();
    boolean isOpen();
}
