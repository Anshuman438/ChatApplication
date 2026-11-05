package client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class ClientGUI {

    public static final String DEFAULT_PRODUCTION_SERVER = "wss://chat.itsanshuman.dev/ws";
    public static final String DEFAULT_LOCAL_SERVER = "ws://localhost:8081";

    private WebSocketClient wsClient;
    private JFrame frame;
    private JList<String> chatList;
    private DefaultListModel<String> chatListModel;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JLabel chatTitle;
    private JLabel typingLabel;
    private JLabel statusLabel;
    private Timer typingTimer;
    private boolean isTyping = false;

    private final String username;
    private final String serverUri;
    private String currentChat = "GROUP";

    private final HashMap<String, StringBuilder> chatHistory = new HashMap<>();
    private HashMap<String, Integer> unreadCounts = new HashMap<>();

    public ClientGUI(String username, String serverUri) {
        this.username = username;
        this.serverUri = serverUri;

        chatHistory.put("GROUP", new StringBuilder());
        createGUI();
        connectWebSocket();
    }

    private void createGUI() {
        frame = new JFrame("Chat App - " + username + " (" + serverUri + ")");
        frame.setLayout(new BorderLayout());

        // Top Status Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(240, 242, 245));
        topBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusLabel = new JLabel("Connecting to " + serverUri + "...");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(100, 100, 100));
        topBar.add(statusLabel, BorderLayout.WEST);

        JLabel userBadge = new JLabel("👤 " + username);
        userBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topBar.add(userBadge, BorderLayout.EAST);
        frame.add(topBar, BorderLayout.NORTH);

        // Left Sidebar: Users / Rooms
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(220, 224, 230)));

        JLabel chatsLabel = new JLabel("  Conversations");
        chatsLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatsLabel.setPreferredSize(new Dimension(200, 36));

        chatListModel = new DefaultListModel<>();
        chatListModel.addElement("# Group Chat");

        chatList = new JList<>(chatListModel);
        chatList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatList.setSelectedIndex(0);

        JScrollPane chatListScroll = new JScrollPane(chatList);
        leftPanel.add(chatsLabel, BorderLayout.NORTH);
        leftPanel.add(chatListScroll, BorderLayout.CENTER);

        // Right Main Chat Panel
        JPanel rightPanel = new JPanel(new BorderLayout());

        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        chatTitle = new JLabel("🌐 Group Chat");
        chatTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));

        typingLabel = new JLabel(" ");
        typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        typingLabel.setForeground(new Color(120, 120, 120));

        headerPanel.add(chatTitle);
        headerPanel.add(typingLabel);
        rightPanel.add(headerPanel, BorderLayout.NORTH);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setMargin(new Insets(8, 12, 8, 12));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        rightPanel.add(chatScroll, BorderLayout.CENTER);

        // Message Input Panel
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(leftPanel, BorderLayout.WEST);
        frame.add(rightPanel, BorderLayout.CENTER);

        // Events
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        typingTimer = new Timer(1500, e -> isTyping = false);
        typingTimer.setRepeats(false);

        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (!isTyping && wsClient != null && wsClient.isOpen()) {
                    wsClient.send("TYPING");
                    isTyping = true;
                }
                typingTimer.restart();
            }
        });

        chatList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            String selected = chatList.getSelectedValue();
            if (selected == null) return;

            if (selected.equals("# Group Chat")) {
                openChat("GROUP");
                return;
            }

            String actualUser = selected;
            if (actualUser.startsWith("🟢 ")) {
                actualUser = actualUser.substring(3).trim();
            }

            int unreadIndex = actualUser.indexOf(" [");
            if (unreadIndex != -1) {
                actualUser = actualUser.substring(0, unreadIndex);
            }
            openChat(actualUser.trim());
        });

        frame.setSize(950, 600);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void connectWebSocket() {
        try {
            URI uri = new URI(serverUri);
            wsClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("🟢 Connected to " + serverUri);
                        statusLabel.setForeground(new Color(34, 139, 34));
                    });
                    // Initial username handshake
                    send(username);
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("🔴 Disconnected from server (" + reason + ")");
                        statusLabel.setForeground(Color.RED);
                    });
                }

                @Override
                public void onError(Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("⚠️ Connection error: " + (ex != null ? ex.getMessage() : "Unknown"));
                        statusLabel.setForeground(Color.RED);
                    });
                }
            };

            wsClient.connect();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Invalid server URL: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleIncomingMessage(String message) {
        if (message == null) return;

        // Typing indicator
        if (message.startsWith("TYPING:")) {
            String user = message.substring(7);
            SwingUtilities.invokeLater(() -> {
                if (currentChat.equals(user)) {
                    typingLabel.setText(user + " is typing...");
                }
            });
            return;
        }

        // Online users update
        if (message.startsWith("USERLIST:")) {
            String users = message.substring(9);
            SwingUtilities.invokeLater(() -> updateUserPanel(users));
            return;
        }

        // Private message
        if (message.startsWith("PRIVATE:")) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String sender = parts[1];
                String privateMessage = parts[2];
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

                SwingUtilities.invokeLater(() -> {
                    typingLabel.setText(" ");
                    appendToChat(sender, "[" + time + "] " + sender + ": " + privateMessage);
                    if (!currentChat.equals(sender)) {
                        unreadCounts.put(sender, unreadCounts.getOrDefault(sender, 0) + 1);
                        refreshChatList();
                    }
                });
            }
            return;
        }

        // Normal group broadcast
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        SwingUtilities.invokeLater(() -> {
            typingLabel.setText(" ");
            appendToChat("GROUP", "[" + time + "] " + message);
        });
    }

    private void openChat(String chatName) {
        currentChat = chatName;
        if (chatName.equals("GROUP")) {
            chatTitle.setText("🌐 Group Chat");
        } else {
            chatTitle.setText("💬 Chat with " + chatName);
        }

        unreadCounts.put(chatName, 0);
        refreshChatList();

        StringBuilder history = chatHistory.computeIfAbsent(chatName, k -> new StringBuilder());
        chatArea.setText(history.toString());
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty() || wsClient == null || !wsClient.isOpen()) {
            return;
        }

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        if (currentChat.equals("GROUP")) {
            wsClient.send(msg);
        } else {
            wsClient.send("PRIVATE:" + currentChat + ":" + msg);
            appendToChat(currentChat, "[" + time + "] Me: " + msg);
        }

        messageField.setText("");
    }

    private void appendToChat(String chat, String message) {
        chatHistory.computeIfAbsent(chat, k -> new StringBuilder()).append(message).append("\n");

        if (currentChat.equals(chat)) {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    private void updateUserPanel(String usersData) {
        HashMap<String, Integer> newUnread = new HashMap<>();
        String[] users = usersData.split(",");

        for (String user : users) {
            user = user.trim();
            if (user.isEmpty()) continue;

            chatHistory.putIfAbsent(user, new StringBuilder());
            newUnread.put(user, unreadCounts.getOrDefault(user, 0));
        }

        unreadCounts = newUnread;
        refreshChatList();
    }

    private void refreshChatList() {
        chatListModel.clear();
        chatListModel.addElement("# Group Chat");

        for (String user : unreadCounts.keySet()) {
            int unread = unreadCounts.get(user);
            String displayName = unread > 0 ? "🟢 " + user + " [" + unread + "]" : "🟢 " + user;
            chatListModel.addElement(displayName);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String initialUser = (args != null && args.length > 0 && !args[0].trim().isEmpty())
                    ? args[0].trim() : null;
            String initialServer = (args != null && args.length > 1 && !args[1].trim().isEmpty())
                    ? args[1].trim() : null;

            if (initialUser != null) {
                String server = initialServer != null ? initialServer : DEFAULT_LOCAL_SERVER;
                new ClientGUI(initialUser, server);
                return;
            }

            // Connection Dialog
            JPanel panel = new JPanel(new GridLayout(4, 1, 5, 5));
            JTextField userField = new JTextField();
            JTextField serverField = new JTextField(DEFAULT_LOCAL_SERVER);

            panel.add(new JLabel("Enter your Username:"));
            panel.add(userField);
            panel.add(new JLabel("Server URL (e.g., wss://chat.itsanshuman.dev/ws or ws://localhost:8080):"));
            panel.add(serverField);

            int result = JOptionPane.showConfirmDialog(null, panel, "Chat Application Login",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String user = userField.getText().trim();
                String server = serverField.getText().trim();

                if (user.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Username cannot be empty!");
                    return;
                }
                if (server.isEmpty()) {
                    server = DEFAULT_LOCAL_SERVER;
                }

                new ClientGUI(user, server);
            }
        });
    }
}
