package client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ClientGUI {

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private JFrame frame;

    private JList<String> chatList;
    private DefaultListModel<String> chatListModel;

    private JTextArea chatArea;

    private JTextField messageField;

    private JButton sendButton;

    private JLabel chatTitle;

    private String username;

    private JLabel typingLabel;

    private Timer typingTimer;
    
    private boolean typing = false;

    // GROUP = Broadcast Room
    private String currentChat = "GROUP";

    // Chat history for each conversation
    private HashMap<String, StringBuilder> chatHistory =
            new HashMap<>();

    // Unread counts
    private HashMap<String, Integer> unreadCounts =
            new HashMap<>();

    public ClientGUI(String username) {

        this.username = username;

        try {

            socket =new Socket("localhost", 5000);

            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            output =new PrintWriter(socket.getOutputStream(),true);

            output.println(username);

           chatHistory.put("GROUP",new StringBuilder());

createGUI();

startReceiver();

        } catch (IOException e) {JOptionPane.showMessageDialog(null,"Unable to connect to server");}
    }

    private void createGUI() {

        frame =new JFrame("Chat App - " + username);

        frame.setLayout(new BorderLayout());

        /*
         * LEFT PANEL
         */

        JPanel leftPanel =new JPanel(new BorderLayout());

        JLabel chatsLabel =new JLabel(" Chats");

        chatsLabel.setFont(new Font("Segoe UI",Font.BOLD,16));

        chatListModel =new DefaultListModel<>();

        chatListModel.addElement("# Group Chat");

        chatList =new JList<>(chatListModel);

        chatList.setFont(new Font("Segoe UI",Font.PLAIN,14));

        JScrollPane chatListScroll =new JScrollPane(chatList);

        leftPanel.add(chatsLabel,BorderLayout.NORTH);

        leftPanel.add(chatListScroll,BorderLayout.CENTER);

        leftPanel.setPreferredSize(new Dimension(220,0));

      /*
         * RIGHT PANEL
        */

        JPanel rightPanel =new JPanel(new BorderLayout());

        JPanel headerPanel =new JPanel(new GridLayout(2,1));

        chatTitle =new JLabel("🌐 Group Chat");

        chatTitle.setFont(new Font("Segoe UI",Font.BOLD,18));

        typingLabel =new JLabel("");

        typingLabel.setFont(new Font("Segoe UI",Font.ITALIC,12));

        headerPanel.add(chatTitle);

        headerPanel.add(typingLabel);

        rightPanel.add(headerPanel,BorderLayout.NORTH);

        chatArea =new JTextArea();

        chatArea.setEditable(false);

        chatArea.setLineWrap(true);

        chatArea.setWrapStyleWord(true);

        chatArea.setFont(new Font("Segoe UI",Font.PLAIN,14));

        JScrollPane chatScroll =new JScrollPane(chatArea);

        rightPanel.add(chatScroll,BorderLayout.CENTER);
        /*
         * MESSAGE PANEL
         */

        JPanel bottomPanel =new JPanel(new BorderLayout());

        messageField =new JTextField();

        sendButton =new JButton("Send");

        bottomPanel.add(messageField,BorderLayout.CENTER);

        bottomPanel.add(sendButton,BorderLayout.EAST);

        rightPanel.add(bottomPanel,BorderLayout.SOUTH);

        /*
         * ADD PANELS
         */

        frame.add(leftPanel,BorderLayout.WEST);

        frame.add(rightPanel,BorderLayout.CENTER);

        /*
         * EVENTS
         */

        sendButton.addActionListener(e -> sendMessage());

        messageField.addActionListener(e -> sendMessage());

       typingTimer = new Timer(1000,e -> {

        typing = false;
        });

        typingTimer.setRepeats(false);

        messageField.addKeyListener(new KeyAdapter() {

            @Override
            public void keyTyped(KeyEvent e) {

                if (!typing) {

                    output.println("TYPING");

                    typing = true;
                }

                typingTimer.restart();
            }
        });

        chatList.addListSelectionListener(e -> {

                    if (e.getValueIsAdjusting()) {
                        return;
                    }

                    String selected =chatList.getSelectedValue();

                    if (selected == null) {
                        return;
                    }

                    if (selected.equals("# Group Chat")) {

                        openChat("GROUP");
                        return;
                    }

                    String actualUser =selected;

                    if (actualUser.startsWith("🟢")) {

                        actualUser =actualUser.substring(2).trim();
                    }

                    int unreadIndex = actualUser.indexOf(" [");

                    if (unreadIndex != -1) {

                        actualUser = actualUser.substring(0,unreadIndex);
                    }

                    openChat(actualUser.trim());
                });

        frame.setSize(1000,600);

        frame.setLocationRelativeTo(
                null);

        frame.setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);
    }


    private void openChat(String chatName) {

        currentChat =chatName;

        if (chatName.equals("GROUP")) {

            chatTitle.setText("🌐 Group Chat");

        } else {

            chatTitle.setText("💬 Chat with "+ chatName);
        }

        unreadCounts.put(chatName,0);

        StringBuilder history =chatHistory.get(chatName);

        if (history == null) {

            history = new StringBuilder();

            chatHistory.put(chatName, history);
        }

        chatArea.setText(history.toString());

        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }


    private void sendMessage() {

        String msg =messageField.getText().trim();

        if (msg.isEmpty()) {

            return;
        }

        String time =LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        if (currentChat.equals("GROUP")) {

            output.println(msg);

        } else {

            output.println("PRIVATE:"+ currentChat + ":"+ msg);

            appendToChat(currentChat,"[" + time + "] Me: "+ msg);
        }

        messageField.setText("");
    }

    private void appendToChat(String chat,String message) {

        chatHistory.computeIfAbsent(chat,k -> new StringBuilder()).append(message).append("\n");

        if (currentChat.equals(chat)) {

            chatArea.append(message + "\n");

            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }



    private void startReceiver() {

    Thread receiver = new Thread(() -> {

        try {

            String message;

            while ((message = input.readLine()) != null) {

                final String msg = message;

                System.out.println("SERVER -> " + msg);

                // TYPING INDICATOR
                if (msg.startsWith("TYPING:")) {

                    String user = msg.substring(7);

                    SwingUtilities.invokeLater(() -> {

                        if (currentChat.equals(user)) {

                            typingLabel.setText(user + " is typing...");
                        }
                    });
                }

                // USER LIST UPDATE
                else if (msg.startsWith("USERLIST:")) {

                    SwingUtilities.invokeLater(() -> {

                        String users = msg.substring(9);

                        updateUserPanel(users);
                    });
                }

                // PRIVATE MESSAGE
                else if (msg.startsWith("PRIVATE:")) {

                    String[] parts = msg.split(":", 3);

                    if (parts.length == 3) {

                        String sender = parts[1];

                        String privateMessage = parts[2];

                        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

                        SwingUtilities.invokeLater(() -> {

                            typingLabel.setText("");
                        });

                        appendToChat(sender,"[" + time + "] " + sender + ": " + privateMessage);

                        if (!currentChat.equals(sender)) {

                            unreadCounts.put(sender,unreadCounts.getOrDefault(sender,0) + 1);

                            refreshChatList();
                        }
                    }
                }

                // GROUP MESSAGE
                else {

                    String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

                    SwingUtilities.invokeLater(() -> {

                        typingLabel.setText("");
                    });

                    appendToChat("GROUP","[" + time + "] " + msg);
                }
            }

        } catch (IOException e) {

            SwingUtilities.invokeLater(() -> {

                JOptionPane.showMessageDialog(frame,"Disconnected from server");
            });
        }
    });

    receiver.start();
}

private void updateUserPanel(String usersData) {

    HashMap<String, Integer> 
    newUnread =new HashMap<>();

    String[] users =usersData.split(",");

    for (String user : users) {

        user = user.trim();

        if (user.isEmpty()) {

            continue;
        }

        chatHistory.putIfAbsent(user,new StringBuilder());

        newUnread.put(user,unreadCounts.getOrDefault(user,0));
    }

    unreadCounts = newUnread;

    refreshChatList();
}

private void refreshChatList() {

    SwingUtilities.invokeLater(() -> {chatListModel.clear();

        chatListModel.addElement("# Group Chat");

        for (String user :unreadCounts.keySet()) {

            String displayName;

            int unread =unreadCounts.get(user);

            if (unread > 0) {

                displayName ="🟢 "+ user+ " ["+ unread+ "]";

            } else {

                displayName ="🟢 "+ user;
            }

            chatListModel.addElement(displayName);
        }
    });
}

public static void main(
        String[] args) {

    SwingUtilities.invokeLater(() -> {

        String username =JOptionPane.showInputDialog(null,"Enter Username");

        if (username == null|| username.trim().isEmpty()) {

            return;
        }

        new ClientGUI( username.trim());
    });
}
}