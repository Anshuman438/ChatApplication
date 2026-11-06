package client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

public class ClientGUI {

    public static final String DEFAULT_PRODUCTION_SERVER = "wss://chat.itsanshuman.dev/ws";
    public static final String DEFAULT_LOCAL_SERVER = "ws://localhost:8081";

    // Palette Colors matching ChatApp_Redesign.html
    public static final Color BG_APP = new Color(0x0B, 0x0D, 0x12);          // #0B0D12
    public static final Color BG_TOPBAR = new Color(0x0F, 0x12, 0x18);       // #0F1218
    public static final Color BG_SIDEBAR = new Color(0x0F, 0x12, 0x18);      // #0F1218
    public static final Color BG_CARD = new Color(0x15, 0x19, 0x22);         // #151922
    public static final Color BG_ROW_HOVER = new Color(0x15, 0x19, 0x22);    // #151922
    public static final Color BG_ROW_ACTIVE = new Color(0x1A, 0x1F, 0x2B);   // #1A1F2B
    public static final Color BG_COMPOSER = new Color(0x12, 0x15, 0x1C);     // #12151C
    public static final Color BG_BUBBLE_OTHER = new Color(0x17, 0x1B, 0x24); // #171B24
    public static final Color BG_BUBBLE_ME = new Color(0x4F, 0x5B, 0xD5);    // #4F5BD5

    public static final Color BORDER_SUBTLE = new Color(0x1C, 0x21, 0x2B);   // #1C212B
    public static final Color BORDER_CARD = new Color(0x23, 0x29, 0x38);     // #232938
    public static final Color BORDER_INPUT = new Color(0x1F, 0x25, 0x32);    // #1F2532

    public static final Color ACCENT = new Color(0x4F, 0x5B, 0xD5);          // #4F5BD5
    public static final Color ACCENT_HOVER = new Color(0x5D, 0x69, 0xE4);    // #5D69E4
    public static final Color COLOR_ONLINE = new Color(0x4A, 0xDE, 0x9C);    // #4ADE9C
    public static final Color STATUS_CONN_BG = new Color(0x10, 0x26, 0x1D);  // #10261D

    public static final Color TEXT_PRIMARY = new Color(0xE8, 0xEC, 0xF4);    // #E8ECF4
    public static final Color TEXT_SECONDARY = new Color(0xA3, 0xAC, 0xBF);  // #A3ACBF
    public static final Color TEXT_MUTED = new Color(0x8A, 0x93, 0xA6);      // #8A93A6
    public static final Color TEXT_PLACEHOLDER = new Color(0x7D, 0x86, 0x9A);// #7D869A

    // Avatar color palette
    public static final Color[] AVATAR_COLORS = new Color[]{
            new Color(0xE8, 0xB0, 0x4F), // Amber #E8B04F
            new Color(0x3F, 0xB6, 0x8B), // Emerald #3FB68B
            new Color(0x4F, 0x5B, 0xD5), // Indigo #4F5BD5
            new Color(0x8B, 0x5C, 0xF6), // Violet #8B5CF6
            new Color(0xEC, 0x48, 0x99), // Pink #EC4899
            new Color(0x3B, 0x82, 0xF6), // Blue #3B82F6
            new Color(0xF9, 0x73, 0x16), // Orange #F97316
            new Color(0x14, 0xB8, 0xA6)  // Teal #14B8A6
    };

    public static Color getAvatarColor(String name) {
        if (name == null || name.isEmpty() || name.equals("GROUP")) return ACCENT;
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = name.charAt(i) + ((hash << 5) - hash);
        }
        return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
    }

    private WebSocketClient wsClient;
    private JFrame frame;
    private JList<ChatListItem> chatList;
    private DefaultListModel<ChatListItem> chatListModel;
    private JTextField searchField;
    private JTextPane chatPane;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private JTextField messageField;
    private JButton sendButton;
    private JLabel chatTitle;
    private JLabel chatSubtitle;
    private JLabel typingLabel;
    private JLabel statusPill;
    private JLabel userBadge;
    private JLabel myNameLabel;
    private JPanel activeAvatarPanel;
    private Timer typingTimer;
    private boolean isTyping = false;

    private final String userId;
    private String username;
    private final String serverUri;
    private String currentChat = "Group Chat";
    private boolean isUpdatingList = false;

    private final List<String> activeGroups = new ArrayList<>();
    private final HashMap<String, StringBuilder> chatHistoryHtml = new HashMap<>();
    private final HashMap<String, Integer> unreadCounts = new HashMap<>();
    private final List<String> onlineUsers = new ArrayList<>();

    // Model object for the conversations list
    public static class ChatListItem {
        public final String id;       // room name or username
        public final String name;     // Display name
        public final boolean isGroup;
        public int unread;

        public ChatListItem(String id, String name, boolean isGroup, int unread) {
            this.id = id;
            this.name = name;
            this.isGroup = isGroup;
            this.unread = unread;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public ClientGUI(String userId, String username, String serverUri) {
        this.userId = userId;
        this.username = username;
        this.serverUri = serverUri;

        activeGroups.add("Group Chat");
        chatHistoryHtml.put("Group Chat", new StringBuilder());
        createGUI();
        connectWebSocket();
    }

    private void createGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("ChatApp — " + username);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(BG_APP);

        // ================= Top Bar =================
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_TOPBAR);
        topBar.setPreferredSize(new Dimension(0, 60));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE));

        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 12));
        topLeft.setOpaque(false);

        // App Icon Badge
        JPanel iconBadge = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
                FontMetrics fm = g2.getFontMetrics();
                String icon = "💬";
                g2.drawString(icon, (getWidth() - fm.stringWidth(icon)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        iconBadge.setPreferredSize(new Dimension(32, 32));
        iconBadge.setOpaque(false);
        topLeft.add(iconBadge);

        JLabel appTitle = new JLabel("ChatApp");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        appTitle.setForeground(TEXT_PRIMARY);
        topLeft.add(appTitle);

        // Status Pill
        statusPill = new JLabel("● Connecting...") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(STATUS_CONN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        statusPill.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusPill.setForeground(COLOR_ONLINE);
        statusPill.setBorder(new EmptyBorder(4, 10, 4, 10));
        statusPill.setOpaque(false);
        topLeft.add(statusPill);

        topBar.add(topLeft, BorderLayout.WEST);

        // Top Right Info
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 15));
        topRight.setOpaque(false);

        userBadge = new JLabel("👤 " + username + " (" + userId + ") [Edit]");
        userBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        userBadge.setForeground(TEXT_PRIMARY);
        userBadge.setCursor(new Cursor(Cursor.HAND_CURSOR));
        userBadge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showProfileDialog();
            }
        });
        topRight.add(userBadge);

        topBar.add(topRight, BorderLayout.EAST);
        frame.add(topBar, BorderLayout.NORTH);

        // ================= Main Layout: Sidebar & Chat =================
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(BG_APP);

        // Sidebar
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(280, 0));
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_SUBTLE));

        // Search in Sidebar
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);
        searchPanel.setBorder(new EmptyBorder(14, 14, 8, 14));

        searchField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(BORDER_INPUT);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    g2.setColor(TEXT_PLACEHOLDER);
                    g2.setFont(getFont());
                    g2.drawString("🔍  Search conversations", 12, (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2);
                }
                g2.dispose();
            }
        };
        searchField.setPreferredSize(new Dimension(0, 38));
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.setForeground(TEXT_PRIMARY);
        searchField.setCaretColor(TEXT_PRIMARY);
        searchField.setOpaque(false);
        searchField.setBorder(new EmptyBorder(4, 12, 4, 12));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshChatList(); }
            public void removeUpdate(DocumentEvent e) { refreshChatList(); }
            public void changedUpdate(DocumentEvent e) { refreshChatList(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);

        JButton newGroupBtn = new JButton("+ New") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? ACCENT_HOVER : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("+ New", (getWidth() - fm.stringWidth("+ New")) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        newGroupBtn.setPreferredSize(new Dimension(58, 38));
        newGroupBtn.setOpaque(false);
        newGroupBtn.setBorder(null);
        newGroupBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        newGroupBtn.setToolTipText("Create a new group");
        newGroupBtn.addActionListener(e -> promptCreateGroup());
        searchPanel.add(newGroupBtn, BorderLayout.EAST);

        sidebar.add(searchPanel, BorderLayout.NORTH);

        // Conversations List
        chatListModel = new DefaultListModel<>();
        chatList = new JList<>(chatListModel);
        chatList.setBackground(BG_SIDEBAR);
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatList.setCellRenderer(new ModernChatListRenderer());

        JScrollPane sidebarScroll = new JScrollPane(chatList);
        sidebarScroll.setBorder(null);
        sidebarScroll.getViewport().setBackground(BG_SIDEBAR);
        sidebarScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sidebar.add(sidebarScroll, BorderLayout.CENTER);

        // Current User Profile Card at Sidebar Bottom
        JPanel userFooter = new JPanel(new BorderLayout(12, 0));
        userFooter.setBackground(BG_SIDEBAR);
        userFooter.setCursor(new Cursor(Cursor.HAND_CURSOR));
        userFooter.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_SUBTLE),
                new EmptyBorder(12, 16, 12, 16)
        ));
        userFooter.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showProfileDialog();
            }
        });

        JPanel myAvatar = new RoundedAvatarPanel(username.substring(0, 1).toUpperCase(), getAvatarColor(username), 40, true);
        userFooter.add(myAvatar, BorderLayout.WEST);

        JPanel myMeta = new JPanel(new GridLayout(2, 1, 0, 2));
        myMeta.setOpaque(false);
        myNameLabel = new JLabel(username);
        myNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        myNameLabel.setForeground(TEXT_PRIMARY);
        JLabel myStatusLabel = new JLabel("Online · Click to edit profile");
        myStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        myStatusLabel.setForeground(COLOR_ONLINE);
        myMeta.add(myNameLabel);
        myMeta.add(myStatusLabel);
        userFooter.add(myMeta, BorderLayout.CENTER);

        sidebar.add(userFooter, BorderLayout.SOUTH);
        workspace.add(sidebar, BorderLayout.WEST);

        // ================= Right Main Chat Panel =================
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(BG_APP);

        // Active Chat Header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_TOPBAR);
        chatHeader.setPreferredSize(new Dimension(0, 72));
        chatHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE));

        JPanel chatHeaderLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        chatHeaderLeft.setOpaque(false);

        activeAvatarPanel = new RoundedAvatarPanel("#", new Color(0x1A, 0x20, 0x30), 42, false);
        chatHeaderLeft.add(activeAvatarPanel);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setOpaque(false);
        chatTitle = new JLabel("Group Chat");
        chatTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        chatTitle.setForeground(TEXT_PRIMARY);

        chatSubtitle = new JLabel("Public broadcast room for all connected web & desktop users");
        chatSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chatSubtitle.setForeground(COLOR_ONLINE);

        titleBlock.add(chatTitle);
        titleBlock.add(chatSubtitle);
        chatHeaderLeft.add(titleBlock);
        chatHeader.add(chatHeaderLeft, BorderLayout.WEST);

        typingLabel = new JLabel(" ");
        typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        typingLabel.setForeground(TEXT_MUTED);
        typingLabel.setBorder(new EmptyBorder(0, 0, 0, 20));
        chatHeader.add(typingLabel, BorderLayout.EAST);

        chatPanel.add(chatHeader, BorderLayout.NORTH);

        // Chat Message Feed (HTML Pane)
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(BG_APP);
        chatPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        htmlKit = new HTMLEditorKit();
        chatPane.setEditorKit(htmlKit);
        htmlDoc = (HTMLDocument) htmlKit.createDefaultDocument();
        chatPane.setDocument(htmlDoc);

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(BG_APP);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // ================= Bottom Floating Composer =================
        JPanel composerOuter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        composerOuter.setBackground(BG_APP);

        JPanel composerBar = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_COMPOSER);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.setColor(BORDER_CARD);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        composerBar.setPreferredSize(new Dimension(720, 54));
        composerBar.setOpaque(false);
        composerBar.setBorder(new EmptyBorder(6, 14, 6, 8));

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        messageField.setForeground(TEXT_PRIMARY);
        messageField.setCaretColor(TEXT_PRIMARY);
        messageField.setBackground(BG_COMPOSER);
        messageField.setBorder(null);
        messageField.setOpaque(false);

        sendButton = new JButton("✈") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? ACCENT_HOVER : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI Symbol", Font.BOLD, 17));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        sendButton.setPreferredSize(new Dimension(42, 42));
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setContentAreaFilled(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        composerBar.add(messageField, BorderLayout.CENTER);
        composerBar.add(sendButton, BorderLayout.EAST);
        composerOuter.add(composerBar);
        chatPanel.add(composerOuter, BorderLayout.SOUTH);

        workspace.add(chatPanel, BorderLayout.CENTER);
        frame.add(workspace, BorderLayout.CENTER);

        // ================= Event Listeners =================
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
            if (e.getValueIsAdjusting() || isUpdatingList) return;
            ChatListItem item = chatList.getSelectedValue();
            if (item != null && !item.id.equals(currentChat)) {
                openChat(item.id);
            }
        });

        refreshChatList();
        renderChatMessages();

        frame.setSize(1040, 680);
        frame.setMinimumSize(new Dimension(800, 500));
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
                        statusPill.setText("● Connected");
                        statusPill.setForeground(COLOR_ONLINE);
                    });
                    // Handshake with permanent userId & username
                    send("IDENTIFY:" + userId + ":" + username);
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    SwingUtilities.invokeLater(() -> {
                        statusPill.setText("● Disconnected");
                        statusPill.setForeground(new Color(0xEF, 0x44, 0x44));
                    });
                }

                @Override
                public void onError(Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusPill.setText("● Disconnected");
                        statusPill.setForeground(new Color(0xEF, 0x44, 0x44));
                    });
                }
            };

            if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("wss")) {
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getDefault();
                wsClient.setSocketFactory(sslContext.getSocketFactory());
            }

            wsClient.connect();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Failed to connect to " + serverUri + ": " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleIncomingMessage(String message) {
        if (message == null) return;

        // Filter health checks / bot headers
        if (message.contains("HTTP/") || message.contains("Go-http-client") || message.contains("Host:") || message.contains("User-Agent:")) {
            return;
        }

        if (message.startsWith("WELCOME:")) {
            return;
        }

        // Groups List update: "GROUPLIST:group1,group2,..."
        if (message.startsWith("GROUPLIST:")) {
            String listStr = message.substring(10);
            SwingUtilities.invokeLater(() -> {
                for (String g : listStr.split(",")) {
                    g = g.trim();
                    if (!g.isEmpty() && !activeGroups.contains(g)) {
                        activeGroups.add(g);
                    }
                }
                refreshChatList();
            });
            return;
        }

        // Group Message: "GROUP_MSG:groupName:sender:message"
        if (message.startsWith("GROUP_MSG:")) {
            String[] parts = message.split(":", 4);
            if (parts.length >= 4) {
                String groupName = parts[1].trim();
                String sender = parts[2].trim();
                String text = parts[3].trim();
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                boolean isMe = sender.equalsIgnoreCase(username);

                SwingUtilities.invokeLater(() -> {
                    if (!activeGroups.contains(groupName)) {
                        activeGroups.add(groupName);
                    }
                    appendHtmlBubble(groupName, sender, text, time, isMe);
                    if (!currentChat.equalsIgnoreCase(groupName)) {
                        unreadCounts.put(groupName, unreadCounts.getOrDefault(groupName, 0) + 1);
                        refreshChatList();
                    }
                });
            }
            return;
        }

        // Typing indicator
        if (message.startsWith("TYPING:")) {
            String rest = message.substring(7).trim();
            String user = rest.contains(":") ? rest.substring(rest.indexOf(":") + 1) : rest;
            if (!user.equalsIgnoreCase(username)) {
                SwingUtilities.invokeLater(() -> {
                    typingLabel.setText(user + " is typing...");
                    Timer t = new Timer(2000, e -> typingLabel.setText(" "));
                    t.setRepeats(false);
                    t.start();
                });
            }
            return;
        }

        // Online users update
        if (message.startsWith("USERLIST:")) {
            String users = message.substring(9);
            SwingUtilities.invokeLater(() -> updateUserList(users));
            return;
        }

        // Private message
        if (message.startsWith("PRIVATE:")) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                String sender = parts[1].trim();
                String text = parts[2].trim();
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

                SwingUtilities.invokeLater(() -> {
                    appendHtmlBubble(sender, sender, text, time, false);
                    if (!currentChat.equals(sender)) {
                        unreadCounts.put(sender, unreadCounts.getOrDefault(sender, 0) + 1);
                        refreshChatList();
                    }
                });
            }
            return;
        }

        // System join/leave/broadcast announcements
        if (message.startsWith("🟢 ") || message.startsWith("🔴 ") || message.startsWith("📢")) {
            SwingUtilities.invokeLater(() -> appendHtmlSystem(message));
            return;
        }

        // System notices
        if (message.startsWith("SYSTEM:")) {
            SwingUtilities.invokeLater(() -> appendHtmlSystem(message.substring(7).trim()));
            return;
        }

        // Normal group broadcast fallback
        int colon = message.indexOf(":");
        if (colon != -1) {
            String sender = message.substring(0, colon).trim();
            String text = message.substring(colon + 1).trim();
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            boolean isMe = sender.equalsIgnoreCase(username);

            SwingUtilities.invokeLater(() -> {
                String targetRoom = isGroupChat(currentChat) ? currentChat : "Group Chat";
                appendHtmlBubble(targetRoom, sender, text, time, isMe);
                if (!currentChat.equals(targetRoom)) {
                    unreadCounts.put(targetRoom, unreadCounts.getOrDefault(targetRoom, 0) + 1);
                    refreshChatList();
                }
            });
        }
    }

    private boolean isGroupChat(String chatId) {
        return chatId != null && (chatId.equals("GROUP") || activeGroups.contains(chatId));
    }

    private void promptCreateGroup() {
        String groupName = JOptionPane.showInputDialog(
                frame,
                "Enter group name (2-30 characters):",
                "Create New Group",
                JOptionPane.PLAIN_MESSAGE
        );
        if (groupName != null) {
            groupName = groupName.trim();
            if (groupName.length() >= 2 && groupName.length() <= 30) {
                if (wsClient != null && wsClient.isOpen()) {
                    wsClient.send("CREATE_GROUP:" + groupName);
                }
                if (!activeGroups.contains(groupName)) {
                    activeGroups.add(groupName);
                }
                refreshChatList();
                openChat(groupName);
            } else if (!groupName.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Group name must be between 2 and 30 characters.", "Invalid Name", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void openChat(String chatId) {
        currentChat = chatId;
        unreadCounts.put(chatId, 0);

        if (isGroupChat(chatId)) {
            chatTitle.setText("# " + chatId);
            chatSubtitle.setText("Public group room for connected users");
            messageField.setToolTipText("Message #" + chatId + "...");
            updateActiveAvatar("#", new Color(0x1A, 0x20, 0x30), false);
        } else {
            chatTitle.setText(chatId);
            chatSubtitle.setText("Online · Private conversation");
            messageField.setToolTipText("Message " + chatId + "...");
            updateActiveAvatar(chatId.substring(0, 1).toUpperCase(), getAvatarColor(chatId), true);
        }

        refreshChatList();
        renderChatMessages();
        messageField.requestFocus();
    }

    private void updateActiveAvatar(String letter, Color color, boolean isCircle) {
        activeAvatarPanel.removeAll();
        RoundedAvatarPanel p = new RoundedAvatarPanel(letter, color, 42, isCircle);
        activeAvatarPanel.add(p);
        activeAvatarPanel.revalidate();
        activeAvatarPanel.repaint();
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty() || wsClient == null || !wsClient.isOpen()) {
            return;
        }

        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        if (isGroupChat(currentChat)) {
            String roomName = currentChat.equals("GROUP") ? "Group Chat" : currentChat;
            wsClient.send("GROUP_MSG:" + roomName + ":" + msg);
        } else {
            wsClient.send("PRIVATE:" + currentChat + ":" + msg);
            appendHtmlBubble(currentChat, "Me", msg, time, true);
        }

        messageField.setText("");
        messageField.requestFocus();
    }

    private void appendHtmlBubble(String chatKey, String sender, String text, String time, boolean isMe) {
        StringBuilder sb = chatHistoryHtml.computeIfAbsent(chatKey, k -> new StringBuilder());
        String escaped = escapeHtml(text);

        if (isMe) {
            sb.append("<div style='text-align: right; margin-bottom: 12px; margin-right: 20px;'>")
              .append("<div style='display: inline-block; padding: 10px 16px; background-color: #4F5BD5; color: #FFFFFF; font-family: sans-serif; font-size: 13px; line-height: 1.4; border-radius: 16px 16px 4px 16px; max-width: 65%; text-align: left; word-wrap: break-word;'>")
              .append(escaped)
              .append("<div style='text-align: right; font-size: 10px; color: #C2C8FC; margin-top: 4px;'>")
              .append(time).append("  ✓✓")
              .append("</div></div></div>");
        } else {
            String colorHex = toHex(getAvatarColor(sender));
            sb.append("<div style='text-align: left; margin-bottom: 12px; margin-left: 20px;'>")
              .append("<div style='display: inline-block; padding: 10px 16px; background-color: #171B24; border: 1px solid #232938; color: #E8ECF4; font-family: sans-serif; font-size: 13px; line-height: 1.4; border-radius: 16px 16px 16px 4px; max-width: 65%; word-wrap: break-word;'>")
              .append("<div style='font-size: 11px; font-weight: bold; color: ").append(colorHex).append("; margin-bottom: 2px;'>")
              .append(escapeHtml(sender)).append("</div>")
              .append(escaped)
              .append("<div style='font-size: 10px; color: #8A93A6; margin-top: 4px;'>")
              .append(time)
              .append("</div></div></div>");
        }

        if (currentChat.equals(chatKey)) {
            renderChatMessages();
        }
    }

    private void appendHtmlSystem(String text) {
        StringBuilder sb = chatHistoryHtml.computeIfAbsent("GROUP", k -> new StringBuilder());
        sb.append("<div style='text-align: center; margin: 10px 0;'>")
          .append("<span style='display: inline-block; padding: 4px 14px; background-color: #151922; color: #8A93A6; font-family: sans-serif; font-size: 11px; border-radius: 999px;'>")
          .append(escapeHtml(text))
          .append("</span></div>");

        if (currentChat.equals("GROUP")) {
            renderChatMessages();
        }
    }

    private void renderChatMessages() {
        StringBuilder history = chatHistoryHtml.get(currentChat);
        String body = history != null ? history.toString() : "";

        String html = "<html><head><style>" +
                "body { background-color: #0B0D12; color: #E8ECF4; font-family: sans-serif; padding: 16px; }" +
                ".badge { text-align: center; margin-bottom: 14px; }" +
                ".badge span { display: inline-block; padding: 4px 14px; background: #151922; color: #A3ACBF; border-radius: 999px; font-size: 11px; }" +
                "</style></head><body>" +
                "<div class='badge'><span>Today</span></div>" +
                body +
                "</body></html>";

        chatPane.setText(html);
        SwingUtilities.invokeLater(() -> chatPane.setCaretPosition(chatPane.getDocument().getLength()));
    }

    private void updateUserList(String usersData) {
        onlineUsers.clear();
        String[] users = usersData.split(",");

        for (String u : users) {
            u = u.trim();
            if (u.isEmpty() || u.equalsIgnoreCase(username) || u.contains("HTTP/") || u.contains("Go-http-client")) {
                continue;
            }
            onlineUsers.add(u);
            chatHistoryHtml.putIfAbsent(u, new StringBuilder());
        }

        refreshChatList();
    }

    private void refreshChatList() {
        if (isUpdatingList) return;
        isUpdatingList = true;
        try {
            String filter = (searchField != null ? searchField.getText() : "").trim().toLowerCase();
            ChatListItem selected = chatList != null ? chatList.getSelectedValue() : null;
            String selectedId = selected != null ? selected.id : currentChat;

            chatListModel.clear();

            // Render all active group rooms
            for (String group : activeGroups) {
                if (filter.isEmpty() || group.toLowerCase().contains(filter)) {
                    int unread = unreadCounts.getOrDefault(group, 0);
                    chatListModel.addElement(new ChatListItem(group, "# " + group, true, unread));
                }
            }

            // Direct messages
            for (String user : onlineUsers) {
                if (filter.isEmpty() || user.toLowerCase().contains(filter)) {
                    int unread = unreadCounts.getOrDefault(user, 0);
                    chatListModel.addElement(new ChatListItem(user, user, false, unread));
                }
            }

            // Restore selection
            for (int i = 0; i < chatListModel.size(); i++) {
                if (chatListModel.get(i).id.equals(selectedId)) {
                    chatList.setSelectedIndex(i);
                    break;
                }
            }
        } finally {
            isUpdatingList = false;
        }
    }

    private void showProfileDialog() {
        JPanel panel = new JPanel(new GridLayout(6, 1, 6, 8));
        panel.setBackground(BG_APP);
        panel.setPreferredSize(new Dimension(380, 220));

        JLabel idLabel = new JLabel("Your Permanent User ID: " + userId);
        idLabel.setForeground(COLOR_ONLINE);
        idLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton copyBtn = new JButton("Copy User ID to Clipboard");
        copyBtn.setBackground(BG_CARD);
        copyBtn.setForeground(TEXT_PRIMARY);
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(userId), null);
            JOptionPane.showMessageDialog(frame, "User ID copied: " + userId);
        });

        JLabel nameLabel = new JLabel("Change Nickname:");
        nameLabel.setForeground(TEXT_PRIMARY);
        JTextField editField = new JTextField(username);
        editField.setBackground(BG_CARD);
        editField.setForeground(TEXT_PRIMARY);

        JButton resetBtn = new JButton("Switch Account / Create New ID");
        resetBtn.setBackground(new Color(0x3B, 0x15, 0x1B));
        resetBtn.setForeground(new Color(0xEF, 0x44, 0x44));
        resetBtn.addActionListener(e -> {
            Preferences prefs = Preferences.userNodeForPackage(ClientGUI.class);
            prefs.remove("user_id");
            prefs.remove("user_name");
            JOptionPane.showMessageDialog(frame, "Identity reset. Please restart application to create a new profile.");
            System.exit(0);
        });

        panel.add(idLabel);
        panel.add(copyBtn);
        panel.add(nameLabel);
        panel.add(editField);
        panel.add(new JLabel(" "));
        panel.add(resetBtn);

        int res = JOptionPane.showConfirmDialog(frame, panel, "User Profile & Identity", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String newName = editField.getText().trim();
            if (!newName.isEmpty() && !newName.equalsIgnoreCase(username)) {
                username = newName;
                Preferences prefs = Preferences.userNodeForPackage(ClientGUI.class);
                prefs.put("user_name", username);

                frame.setTitle("ChatApp — " + username);
                userBadge.setText("👤 " + username + " (" + userId + ") [Edit]");
                if (myNameLabel != null) myNameLabel.setText(username);

                if (wsClient != null && wsClient.isOpen()) {
                    wsClient.send("IDENTIFY:" + userId + ":" + username);
                }
            }
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // Avatar custom component
    static class RoundedAvatarPanel extends JPanel {
        private String letter;
        private Color bgColor;
        private final int size;
        private boolean isCircle;

        public RoundedAvatarPanel(String letter, Color bgColor, int size, boolean isCircle) {
            this.letter = letter != null ? letter : "?";
            this.bgColor = bgColor;
            this.size = size;
            this.isCircle = isCircle;
            setPreferredSize(new Dimension(size, size));
            setOpaque(false);
        }

        public void update(String letter, Color bgColor, boolean isCircle) {
            this.letter = letter != null ? letter : "?";
            this.bgColor = bgColor;
            this.isCircle = isCircle;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            if (isCircle) {
                g2.fillOval(0, 0, size, size);
            } else {
                g2.fillRoundRect(0, 0, size, size, 12, 12);
            }

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, (int) (size * 0.42)));
            FontMetrics fm = g2.getFontMetrics();
            int x = (size - fm.stringWidth(letter)) / 2;
            int y = (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(letter, x, y);

            // Online green indicator dot
            if (isCircle) {
                g2.setColor(COLOR_ONLINE);
                g2.fillOval(size - 11, size - 11, 10, 10);
                g2.setColor(BG_SIDEBAR);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(size - 11, size - 11, 10, 10);
            }

            g2.dispose();
        }
    }

    // Modern Sidebar Row Renderer
    static class ModernChatListRenderer extends JPanel implements ListCellRenderer<ChatListItem> {
        private final RoundedAvatarPanel avatarPanel;
        private final JLabel nameLabel;
        private final JLabel subtitleLabel;
        private final JLabel badgeLabel;

        public ModernChatListRenderer() {
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setOpaque(true);

            avatarPanel = new RoundedAvatarPanel("?", ACCENT, 38, true);
            add(avatarPanel, BorderLayout.WEST);

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            nameLabel.setForeground(TEXT_PRIMARY);

            subtitleLabel = new JLabel();
            subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            subtitleLabel.setForeground(TEXT_SECONDARY);

            textPanel.add(nameLabel);
            textPanel.add(subtitleLabel);
            add(textPanel, BorderLayout.CENTER);

            badgeLabel = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    if (getText().isEmpty()) return;
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
                    super.paintComponent(g);
                    g2.dispose();
                }
            };
            badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            badgeLabel.setForeground(Color.WHITE);
            badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
            badgeLabel.setBorder(new EmptyBorder(2, 7, 2, 7));
            badgeLabel.setOpaque(false);
            add(badgeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ChatListItem> list, ChatListItem item, int index, boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? BG_ROW_ACTIVE : BG_SIDEBAR);

            if (item != null) {
                nameLabel.setText(item.name);
                if (item.isGroup) {
                    subtitleLabel.setText("Public room");
                    avatarPanel.update("#", new Color(0x1A, 0x20, 0x30), false);
                } else {
                    subtitleLabel.setText("Online");
                    String letter = (item.name != null && !item.name.isEmpty()) ? item.name.substring(0, 1).toUpperCase() : "?";
                    avatarPanel.update(letter, getAvatarColor(item.name), true);
                }

                if (item.unread > 0) {
                    badgeLabel.setText(String.valueOf(item.unread));
                    badgeLabel.setVisible(true);
                } else {
                    badgeLabel.setText("");
                    badgeLabel.setVisible(false);
                }
            }

            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Preferences prefs = Preferences.userNodeForPackage(ClientGUI.class);
            String savedUserId = prefs.get("user_id", null);
            String savedUsername = prefs.get("user_name", null);
            String savedServer = prefs.get("server_uri", DEFAULT_PRODUCTION_SERVER);

            String initialUser = (args != null && args.length > 0 && !args[0].trim().isEmpty())
                    ? args[0].trim() : null;
            String initialServer = (args != null && args.length > 1 && !args[1].trim().isEmpty())
                    ? args[1].trim() : null;

            if (initialUser != null) {
                String server = initialServer != null ? initialServer : DEFAULT_PRODUCTION_SERVER;
                String uid = savedUserId != null ? savedUserId : "usr_" + Long.toHexString(System.currentTimeMillis());
                prefs.put("user_id", uid);
                prefs.put("user_name", initialUser);
                new ClientGUI(uid, initialUser, server);
                return;
            }

            // Auto-login with persistent profile if available
            if (savedUserId != null && savedUsername != null && !savedUsername.isEmpty()) {
                new ClientGUI(savedUserId, savedUsername, savedServer);
                return;
            }

            // First-Time Login Dialog
            JPanel panel = new JPanel(new GridLayout(4, 1, 6, 8));
            panel.setBackground(BG_APP);
            panel.setPreferredSize(new Dimension(360, 160));

            JLabel userLabel = new JLabel("Choose your Nickname:");
            userLabel.setForeground(TEXT_PRIMARY);
            userLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

            JTextField userField = new JTextField();
            userField.setBackground(BG_CARD);
            userField.setForeground(TEXT_PRIMARY);
            userField.setCaretColor(TEXT_PRIMARY);
            userField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_CARD),
                    new EmptyBorder(6, 10, 6, 10)
            ));

            JLabel serverHeader = new JLabel("Server URL (Live Cloud Server):");
            serverHeader.setForeground(TEXT_PRIMARY);
            serverHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));

            JTextField serverField = new JTextField(DEFAULT_PRODUCTION_SERVER);
            serverField.setBackground(BG_CARD);
            serverField.setForeground(TEXT_PRIMARY);
            serverField.setCaretColor(TEXT_PRIMARY);
            serverField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_CARD),
                    new EmptyBorder(6, 10, 6, 10)
            ));

            panel.add(userLabel);
            panel.add(userField);
            panel.add(serverHeader);
            panel.add(serverField);

            UIManager.put("OptionPane.background", BG_APP);
            UIManager.put("Panel.background", BG_APP);
            UIManager.put("Button.background", ACCENT);
            UIManager.put("Button.foreground", Color.WHITE);

            int result = JOptionPane.showConfirmDialog(null, panel, "ChatApp Login",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String user = userField.getText().trim();
                String server = serverField.getText().trim();

                if (user.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Username cannot be empty!", "Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (server.isEmpty()) {
                    server = DEFAULT_PRODUCTION_SERVER;
                }

                String newUid = "usr_" + Long.toHexString(System.currentTimeMillis());
                prefs.put("user_id", newUid);
                prefs.put("user_name", user);
                prefs.put("server_uri", server);

                new ClientGUI(newUid, user, server);
            }
        });
    }
}
