package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Handles raw TCP socket connections and implements ChatSession.
 */
public class ClientHandler extends Thread implements ChatSession {

    private final Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private String userId;
    private String username;
    private volatile boolean running = true;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.out.println("TCP ClientHandler Error : " + e.getMessage());
        }
    }

    @Override
    public String getUserId() {
        return userId != null ? userId : username;
    }

    @Override
    public void setUserId(String userId) {
        this.userId = userId;
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
        if (output != null) {
            output.println(message);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    @Override
    public boolean isOpen() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    @Override
    public void run() {
        try {
            String initialMessage = input.readLine();
            if (initialMessage == null || initialMessage.trim().isEmpty()) {
                return;
            }
            initialMessage = initialMessage.trim();

            // Detect HTTP probe / health check
            if (initialMessage.startsWith("GET ") ||
                initialMessage.startsWith("HEAD ") ||
                initialMessage.startsWith("POST ") ||
                initialMessage.startsWith("OPTIONS ") ||
                initialMessage.contains("HTTP/")) {
                if (output != null) {
                    output.print("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK");
                    output.flush();
                }
                return;
            }

            // Check if identity handshake
            if (initialMessage.startsWith("IDENTIFY:")) {
                String[] parts = initialMessage.split(":", 3);
                if (parts.length >= 3) {
                    String uid = parts[1].trim();
                    String uname = parts[2].trim();
                    if (Server.isValidUsername(uname)) {
                        Server.handleClientConnected(this, uid, uname);
                    } else {
                        return;
                    }
                }
            } else if (Server.isValidUsername(initialMessage)) {
                Server.handleClientConnected(this, initialMessage, initialMessage);
            } else {
                return;
            }

            String message;
            while (running && (message = input.readLine()) != null) {
                Server.handleIncomingMessage(this, message);
            }
        } catch (IOException e) {
            // Client disconnected unexpectedly
        } finally {
            Server.removeClient(this);
            close();
        }
    }
}
