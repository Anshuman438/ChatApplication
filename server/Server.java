package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {

    // Thread-safe client collection
    public static final List<ClientHandler> clients =new CopyOnWriteArrayList<>();

    /**
     * Send message to all connected clients
     */
    public static void broadcast(String message) {

        for (ClientHandler client : clients) {

            client.sendMessage(message);
        }
    }

    /**
     * Update online users list for every client
     */
    public static void updateUserLists() {

        for (ClientHandler receiver : clients) {

            StringBuilder userList =new StringBuilder("USERLIST:");

            for (ClientHandler client : clients) {

                // Don't show current user in his own list
                if (!client.getUsername().equals(receiver.getUsername())) {

                    userList.append(client.getUsername()).append(",");
                }
            }

            receiver.sendMessage(userList.toString());
        }
    }

    /**
     * Remove disconnected client
     */
    public static void removeClient(ClientHandler client) {

        clients.remove(client);

        System.out.println(client.getUsername()+ " disconnected.");

        updateUserLists();
    }

    /**
     * Find user by username
     */
    public static ClientHandler findClient(
            String username) {

        for (ClientHandler client : clients) {

            if (client.getUsername().equalsIgnoreCase(username)) {

                return client;
            }
        }

        return null;
    }

    public static void main(String[] args) {

        try {

            ServerSocket serverSocket =new ServerSocket(5000);

            System.out.println("====================================");

            System.out.println("   CHAT SERVER STARTED");

            System.out.println("   PORT : 5000");

            System.out.println("====================================");

            while (true) {

                Socket socket =serverSocket.accept();

                System.out.println("New Connection : "+ socket.getInetAddress());

                ClientHandler clientHandler =new ClientHandler(socket);

                clientHandler.start();
            }

        } catch (IOException e) {

            System.out.println("Server Error : "+ e.getMessage());
        }
    }
}