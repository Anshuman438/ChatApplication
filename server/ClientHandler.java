package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler extends Thread {

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private String username;

    public ClientHandler(Socket socket) {

        this.socket = socket;

        try {

            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            output = new PrintWriter(socket.getOutputStream(),true);

        } catch (IOException e) {

            System.out.println("ClientHandler Error : "+ e.getMessage());
        }
    }

    public String getUsername() {

        return username;
    }

    public void sendMessage(String message) {

        output.println(message);
    }

    @Override
    public void run() {

        try {

            /*
             * First message received from client
             * is always the username
             */
            username = input.readLine();

            if (username == null|| username.trim().isEmpty()) {

                return;
            }

            /*
             * Add client AFTER username is received
             * This fixes online-user-list issues
             */
            Server.clients.add(this);

            System.out.println(username + " joined.");

            Server.broadcast("🟢 " + username+ " joined the chat");

            Server.updateUserLists();

            String message;

            while ((message =input.readLine()) != null) {


                if (message.equals("TYPING")) {

                sendTyping();

                continue;
                        }

                /*
                 * Exit command
                 */
                if (message.equalsIgnoreCase("exit")) {

                    Server.broadcast( "🔴 " + username+ " left the chat");

                    break;
                }

                /*
                 * Private Message Format:
                 *
                 * PRIVATE:receiver:message
                 */
                if (message.startsWith("PRIVATE:")) {

                    handlePrivateMessage(message);
                }

                /*
                 * Online Users Command
                 */
                else if (message.equalsIgnoreCase("/users")) {
                    sendOnlineUsers();
                }

                /*
                 * Normal Group Message
                 */
                else {

                    Server.broadcast(username+ ": "+ message);
                }
            }

        } catch (IOException e) {

            System.out.println(username+ " disconnected unexpectedly.");
        }

        finally {

            disconnectClient();
        }
    }

    /**
     * Handle private messages
     */
    private void handlePrivateMessage(String message) {

        try {

            String[] parts =message.split(":",3);

            if (parts.length != 3) {

                return;
            }

            String receiver =parts[1];

            String privateMessage =parts[2];

            ClientHandler receiverClient =Server.findClient(receiver);

            if (receiverClient != null) {

                receiverClient.sendMessage("PRIVATE:"+ username+ ":"+ privateMessage);

            } else {

                sendMessage("SYSTEM: User "+ receiver + " is offline.");
            }

        } catch (Exception e) {

            sendMessage("SYSTEM: Failed to send private message.");
        }
    }

    /**
     * Send online users to requester
     */
    private void sendOnlineUsers() {

        StringBuilder users =new StringBuilder("ONLINE USERS:\n");

        for (ClientHandler client :Server.clients) {

            users.append("• ").append(client.getUsername()).append("\n");
        }

        sendMessage(users.toString());
    }

    /**
     * Cleanup when user disconnects
     */
    private void disconnectClient() {

        try {

            if (username != null) {

                Server.removeClient(this);

                Server.broadcast("🔴 " + username + " left the chat");
            }

            if (input != null) {

                input.close();
            }

            if (output != null) {

                output.close();
            }

            if (socket != null
                    && !socket.isClosed()) {

                socket.close();
            }

        } catch (IOException e) {

            System.out.println("Disconnect Error : " + e.getMessage());
        }
    }

    private void sendTyping() {

    for (ClientHandler client :
            Server.clients) {

        if (client != this) {

            client.sendMessage(
                    "TYPING:" + username);
        }
    }
}
}