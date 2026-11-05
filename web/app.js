// Real-Time Chat Web Client
(function () {
  let ws = null;
  let username = "";
  let currentChat = "GROUP"; // "GROUP" or username for direct message

  const chatHistory = {
    GROUP: []
  };
  const unreadCounts = {};
  let typingTimer = null;
  let isTyping = false;

  // DOM Elements
  const loginModal = document.getElementById("login-modal");
  const loginForm = document.getElementById("login-form");
  const usernameInput = document.getElementById("username-input");
  const connectionStatus = document.getElementById("connection-status");
  const myUsernameEl = document.getElementById("my-username");
  const myAvatarEl = document.getElementById("my-avatar");
  const groupChannelBtn = document.getElementById("group-channel-btn");
  const onlineUsersList = document.getElementById("online-users-list");
  const currentChatTitle = document.getElementById("current-chat-title");
  const currentChatSubtitle = document.getElementById("current-chat-subtitle");
  const messagesContainer = document.getElementById("messages-container");
  const messageForm = document.getElementById("message-form");
  const messageInput = document.getElementById("message-input");
  const typingIndicator = document.getElementById("typing-indicator");

  // Determine WebSocket Endpoint
  function getWebSocketUrl() {
    const isLocal = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
    if (isLocal) {
      return "ws://localhost:8081";
    }
    // Production reverse-proxied over Nginx with SSL: wss://chat.itsanshuman.dev/ws
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/ws`;
  }

  // Handle Login
  loginForm.addEventListener("submit", function (e) {
    e.preventDefault();
    const name = usernameInput.value.trim();
    if (!name) return;

    username = name;
    myUsernameEl.textContent = username;
    myAvatarEl.textContent = username.charAt(0).toUpperCase();

    loginModal.classList.add("hidden");
    connectWebSocket();
  });

  // Connect WebSocket
  function connectWebSocket() {
    const url = getWebSocketUrl();
    updateStatus("Connecting...", "connecting");

    try {
      ws = new WebSocket(url);

      ws.onopen = function () {
        updateStatus("Connected", "connected");
        // Send initial username handshake
        ws.send(username);
      };

      ws.onmessage = function (event) {
        handleServerMessage(event.data);
      };

      ws.onclose = function () {
        updateStatus("Disconnected (reconnecting...)", "disconnected");
        // Auto-reconnect after 3 seconds
        setTimeout(() => {
          if (username) connectWebSocket();
        }, 3000);
      };

      ws.onerror = function () {
        updateStatus("Error", "disconnected");
      };
    } catch (err) {
      console.error("WebSocket init error:", err);
      updateStatus("Connection failed", "disconnected");
    }
  }

  function updateStatus(text, stateClass) {
    connectionStatus.textContent = text;
    connectionStatus.className = "badge " + stateClass;
  }

  // Handle Incoming Messages from Server
  function handleServerMessage(raw) {
    if (!raw) return;

    // 1. TYPING Notification: "TYPING:username"
    if (raw.startsWith("TYPING:")) {
      const sender = raw.substring(7);
      if (currentChat === sender || (currentChat === "GROUP" && sender !== username)) {
        showTyping(sender);
      }
      return;
    }

    // 2. USERLIST Update: "USERLIST:user1,user2,..."
    if (raw.startsWith("USERLIST:")) {
      const usersStr = raw.substring(9);
      updateUsersList(usersStr);
      return;
    }

    // 3. PRIVATE Message: "PRIVATE:sender:message"
    if (raw.startsWith("PRIVATE:")) {
      const parts = raw.split(":");
      if (parts.length >= 3) {
        const sender = parts[1];
        const text = parts.slice(2).join(":");
        const time = formatTime(new Date());

        saveMessage(sender, {
          sender: sender,
          text: text,
          time: time,
          type: "other"
        });

        if (currentChat !== sender) {
          unreadCounts[sender] = (unreadCounts[sender] || 0) + 1;
          renderUsersListUI();
        } else {
          renderMessages();
        }
      }
      return;
    }

    // 4. System Join/Leave Broadcasts: "🟢 user joined" or "🔴 user left"
    if (raw.startsWith("🟢 ") || raw.startsWith("🔴 ")) {
      const time = formatTime(new Date());
      saveMessage("GROUP", {
        sender: "SYSTEM",
        text: raw,
        time: time,
        type: "system"
      });
      renderMessages();
      return;
    }

    // 5. Normal Group Message: "sender: message"
    const colonIndex = raw.indexOf(":");
    if (colonIndex !== -1) {
      const sender = raw.substring(0, colonIndex).trim();
      const text = raw.substring(colonIndex + 1).trim();
      const time = formatTime(new Date());

      saveMessage("GROUP", {
        sender: sender,
        text: text,
        time: time,
        type: sender.toLowerCase() === username.toLowerCase() ? "me" : "other"
      });

      if (currentChat !== "GROUP") {
        unreadCounts["GROUP"] = (unreadCounts["GROUP"] || 0) + 1;
        updateGroupBadge();
      } else {
        renderMessages();
      }
    }
  }

  function saveMessage(chatKey, msgObj) {
    if (!chatHistory[chatKey]) {
      chatHistory[chatKey] = [];
    }
    chatHistory[chatKey].push(msgObj);
  }

  function renderMessages() {
    messagesContainer.innerHTML = "";
    const list = chatHistory[currentChat] || [];

    list.forEach(msg => {
      const row = document.createElement("div");
      row.className = `message-row ${msg.type}`;

      if (msg.type === "system") {
        row.innerHTML = `<div class="msg-bubble">${escapeHtml(msg.text)} <span class="msg-time">${msg.time}</span></div>`;
      } else {
        const senderName = msg.type === "me" ? "Me" : msg.sender;
        row.innerHTML = `
          <div class="msg-sender">${escapeHtml(senderName)} <span class="msg-time">${msg.time}</span></div>
          <div class="msg-bubble">${escapeHtml(msg.text)}</div>
        `;
      }

      messagesContainer.appendChild(row);
    });

    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  function showTyping(user) {
    typingIndicator.textContent = `${user} is typing...`;
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => {
      typingIndicator.textContent = "";
    }, 2000);
  }

  // Handle User List Updates
  let activeUsers = [];
  function updateUsersList(usersStr) {
    activeUsers = usersStr
      .split(",")
      .map(u => u.trim())
      .filter(u => u.length > 0 && u.toLowerCase() !== username.toLowerCase());

    renderUsersListUI();
  }

  function renderUsersListUI() {
    onlineUsersList.innerHTML = "";

    if (activeUsers.length === 0) {
      onlineUsersList.innerHTML = `<div class="empty-users">No other users online yet</div>`;
      return;
    }

    activeUsers.forEach(user => {
      const item = document.createElement("div");
      item.className = `channel-item ${currentChat === user ? "active" : ""}`;
      const unread = unreadCounts[user] || 0;

      item.innerHTML = `
        <span class="channel-icon">🟢</span>
        <span class="channel-name">${escapeHtml(user)}</span>
        ${unread > 0 ? `<span class="unread-badge">${unread}</span>` : ""}
      `;

      item.addEventListener("click", () => switchChat(user));
      onlineUsersList.appendChild(item);
    });
  }

  function updateGroupBadge() {
    const badge = document.getElementById("unread-group");
    const count = unreadCounts["GROUP"] || 0;
    if (count > 0) {
      badge.textContent = count;
      badge.style.display = "inline-block";
    } else {
      badge.style.display = "none";
    }
  }

  // Switch Active Chat Window
  function switchChat(chatKey) {
    currentChat = chatKey;
    unreadCounts[chatKey] = 0;

    if (chatKey === "GROUP") {
      groupChannelBtn.classList.add("active");
      currentChatTitle.textContent = "# Group Chat";
      currentChatSubtitle.textContent = "Public broadcast room for all connected web & desktop users";
      messageInput.placeholder = "Message # Group Chat...";
      updateGroupBadge();
    } else {
      groupChannelBtn.classList.remove("active");
      currentChatTitle.textContent = `💬 Direct Message with ${chatKey}`;
      currentChatSubtitle.textContent = `Private end-to-end conversation with ${chatKey}`;
      messageInput.placeholder = `Message ${chatKey}...`;
    }

    renderUsersListUI();
    renderMessages();
    messageInput.focus();
  }

  groupChannelBtn.addEventListener("click", () => switchChat("GROUP"));

  // Send Message
  messageForm.addEventListener("submit", function (e) {
    e.preventDefault();
    const text = messageInput.value.trim();
    if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;

    const time = formatTime(new Date());

    if (currentChat === "GROUP") {
      ws.send(text);
    } else {
      ws.send(`PRIVATE:${currentChat}:${text}`);
      saveMessage(currentChat, {
        sender: username,
        text: text,
        time: time,
        type: "me"
      });
      renderMessages();
    }

    messageInput.value = "";
    isTyping = false;
  });

  // Typing Trigger
  messageInput.addEventListener("input", function () {
    if (!isTyping && ws && ws.readyState === WebSocket.OPEN) {
      ws.send("TYPING");
      isTyping = true;
      setTimeout(() => {
        isTyping = false;
      }, 2000);
    }
  });

  function formatTime(date) {
    const h = String(date.getHours()).padStart(2, "0");
    const m = String(date.getMinutes()).padStart(2, "0");
    return `${h}:${m}`;
  }

  function escapeHtml(str) {
    return str
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }
})();
