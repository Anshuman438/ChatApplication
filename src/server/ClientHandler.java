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
            String initialName = input.readLine();
            if (initialName == null || initialName.trim().isEmpty()) {
                return;
            }

            Server.handleClientConnected(this, initialName.trim());

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
