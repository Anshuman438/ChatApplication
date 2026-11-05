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
  let activeUsers = [];

  // Deterministic avatar color palette matching redesign
  const AVATAR_PALETTE = [
    { bg: "#E8B04F", text: "#241803" }, // Amber
    { bg: "#3FB68B", text: "#04170F" }, // Emerald
    { bg: "#4F5BD5", text: "#FFFFFF" }, // Indigo
    { bg: "#8B5CF6", text: "#FFFFFF" }, // Violet
    { bg: "#EC4899", text: "#FFFFFF" }, // Pink
    { bg: "#3B82F6", text: "#FFFFFF" }, // Blue
    { bg: "#F97316", text: "#FFFFFF" }, // Orange
    { bg: "#14B8A6", text: "#04170F" }  // Teal
  ];

  function getAvatarColors(name) {
    if (!name || name === "GROUP") return { bg: "#4F5BD5", text: "#FFFFFF" };
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
  }

  // DOM Elements
  const loginModal = document.getElementById("login-modal");
  const loginForm = document.getElementById("login-form");
  const usernameInput = document.getElementById("username-input");
  const connectionStatus = document.getElementById("connection-status");
  const myUsernameEl = document.getElementById("my-username");
  const myAvatarEl = document.getElementById("my-avatar");
  const groupChannelBtn = document.getElementById("group-channel-btn");
  const onlineUsersList = document.getElementById("online-users-list");
  const userSearch = document.getElementById("user-search");
  const currentChatTitle = document.getElementById("current-chat-title");
  const currentChatSubtitle = document.getElementById("current-chat-subtitle");
  const activeChatAvatar = document.getElementById("active-chat-avatar");
  const activeChatIndicator = document.getElementById("active-chat-indicator");
  const messagesContainer = document.getElementById("messages-container");
  const messageForm = document.getElementById("message-form");
  const messageInput = document.getElementById("message-input");
  const typingRow = document.getElementById("typing-indicator-row");
  const typingAvatar = document.getElementById("typing-avatar");
  const btnEmoji = document.getElementById("btn-emoji");
  const emojiPopover = document.getElementById("emoji-popover");
  const btnSettings = document.getElementById("btn-settings");

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
    const colors = getAvatarColors(username);
    myAvatarEl.textContent = username.charAt(0).toUpperCase();
    myAvatarEl.style.backgroundColor = colors.bg;
    myAvatarEl.style.color = colors.text;

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
        updateStatus("Disconnected", "disconnected");
        // Auto-reconnect after 3 seconds
        setTimeout(() => {
          if (username) connectWebSocket();
        }, 3000);
      };

      ws.onerror = function () {
        updateStatus("Disconnected", "disconnected");
      };
    } catch (err) {
      console.error("WebSocket init error:", err);
      updateStatus("Disconnected", "disconnected");
    }
  }

  function updateStatus(text, stateClass) {
    connectionStatus.className = `status-pill ${stateClass}`;
    connectionStatus.innerHTML = `<span class="status-dot"></span>${text}`;
  }

  // Handle Incoming Messages from Server
  function handleServerMessage(raw) {
    if (!raw) return;

    // Filter out HTTP health checks, bot scans, or probe headers
    if (raw.includes("HTTP/") || raw.includes("Go-http-client") || raw.includes("Host:") || raw.includes("User-Agent:")) {
      return;
    }

    // 1. TYPING Notification: "TYPING:username"
    if (raw.startsWith("TYPING:")) {
      const sender = raw.substring(7).trim();
      if (sender.toLowerCase() !== username.toLowerCase()) {
        if (currentChat === sender || (currentChat === "GROUP")) {
          showTyping(sender);
        }
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
    
    // Day badge
    const dateBadge = document.createElement("div");
    dateBadge.className = "date-badge";
    dateBadge.textContent = "Today";
    messagesContainer.appendChild(dateBadge);

    const innerWrap = document.createElement("div");
    innerWrap.className = "messages-inner-wrap";

    const list = chatHistory[currentChat] || [];

    list.forEach(msg => {
      if (msg.type === "system") {
        const sysDiv = document.createElement("div");
        sysDiv.className = "msg-system";
        sysDiv.textContent = msg.text;
        innerWrap.appendChild(sysDiv);
      } else if (msg.type === "me") {
        const meWrap = document.createElement("div");
        meWrap.className = "msg-bubble-wrap me";
        meWrap.innerHTML = `
          <div class="msg-bubble me">${escapeHtml(msg.text)}</div>
          <div class="msg-meta me">
            <span>${msg.time}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#4ADE9C" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-label="Delivered"><path d="M2 12l5 5L18 6M12 16l1 1L22 6"></path></svg>
          </div>
        `;
        innerWrap.appendChild(meWrap);
      } else {
        const colors = getAvatarColors(msg.sender);
        const otherWrap = document.createElement("div");
        otherWrap.className = "msg-bubble-wrap other";
        otherWrap.innerHTML = `
          <div class="avatar-sm" style="background:${colors.bg}; color:${colors.text};">${msg.sender.charAt(0).toUpperCase()}</div>
          <div class="msg-content">
            ${currentChat === "GROUP" ? `<div class="msg-sender-name">${escapeHtml(msg.sender)}</div>` : ""}
            <div class="msg-bubble other">${escapeHtml(msg.text)}</div>
            <div class="msg-meta other">
              <span>${msg.time}</span>
            </div>
          </div>
        `;
        innerWrap.appendChild(otherWrap);
      }
    });

    messagesContainer.appendChild(innerWrap);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  function showTyping(user) {
    if (!typingRow) return;
    const colors = getAvatarColors(user);
    typingAvatar.textContent = user.charAt(0).toUpperCase();
    typingAvatar.style.backgroundColor = colors.bg;
    typingAvatar.style.color = colors.text;

    typingRow.style.display = "flex";
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => {
      typingRow.style.display = "none";
    }, 2000);
  }

  // Handle User List Updates
  function updateUsersList(usersStr) {
    activeUsers = usersStr
      .split(",")
      .map(u => u.trim())
      .filter(u => u.length > 0 && 
                   u.toLowerCase() !== username.toLowerCase() && 
                   !u.includes("HTTP/") && 
                   !u.includes("Go-http-client"));

    renderUsersListUI();
  }

  function renderUsersListUI() {
    onlineUsersList.innerHTML = "";

    const query = (userSearch ? userSearch.value : "").trim().toLowerCase();
    const filtered = query 
      ? activeUsers.filter(u => u.toLowerCase().includes(query)) 
      : activeUsers;

    if (filtered.length === 0) {
      onlineUsersList.innerHTML = `<div class="empty-notice">${query ? "No users matching search" : "No other users online yet"}</div>`;
      return;
    }

    filtered.forEach(user => {
      const colors = getAvatarColors(user);
      const row = document.createElement("div");
      row.className = `user-row ${currentChat === user ? "active" : ""}`;
      const unread = unreadCounts[user] || 0;

      // Last message preview if any
      const history = chatHistory[user] || [];
      const lastMsg = history.length > 0 ? history[history.length - 1] : null;
      const previewText = lastMsg 
        ? (lastMsg.type === "me" ? "You: " : "") + lastMsg.text 
        : "Online";
      const previewTime = lastMsg ? lastMsg.time : "";

      row.innerHTML = `
        <div class="avatar" style="background:${colors.bg}; color:${colors.text};">
          ${user.charAt(0).toUpperCase()}
          <span class="online-indicator"></span>
        </div>
        <div class="user-meta-col">
          <div class="user-name-line">
            <span class="user-name">${escapeHtml(user)}</span>
            <span class="user-time">${previewTime}</span>
          </div>
          <div class="user-snippet">${escapeHtml(previewText)}</div>
        </div>
        ${unread > 0 ? `<span class="unread-bubble">${unread}</span>` : ""}
      `;

      row.addEventListener("click", () => switchChat(user));
      onlineUsersList.appendChild(row);
    });
  }

  if (userSearch) {
    userSearch.addEventListener("input", renderUsersListUI);
  }

  function updateGroupBadge() {
    const badge = document.getElementById("unread-group");
    const count = unreadCounts["GROUP"] || 0;
    if (count > 0) {
      badge.textContent = count;
      badge.style.display = "flex";
    } else {
      badge.style.display = "none";
    }
  }

  // Switch Active Chat Window
  function switchChat(chatKey) {
    currentChat = chatKey;
    unreadCounts[chatKey] = 0;

    // Update active class in sidebar
    if (chatKey === "GROUP") {
      groupChannelBtn.classList.add("active");
      currentChatTitle.textContent = "Group Chat";
      currentChatSubtitle.textContent = "Public broadcast room for all connected web & desktop users";
      messageInput.placeholder = "Message # Group Chat...";
      activeChatAvatar.textContent = "#";
      activeChatAvatar.style.backgroundColor = "#1A2030";
      activeChatAvatar.style.color = "#A3B1FF";
      activeChatAvatar.style.borderRadius = "12px";
      activeChatIndicator.style.display = "none";
      updateGroupBadge();
    } else {
      groupChannelBtn.classList.remove("active");
      currentChatTitle.textContent = chatKey;
      currentChatSubtitle.textContent = "Online · Private conversation";
      messageInput.placeholder = `Message ${chatKey}...`;
      const colors = getAvatarColors(chatKey);
      activeChatAvatar.textContent = chatKey.charAt(0).toUpperCase();
      activeChatAvatar.style.backgroundColor = colors.bg;
      activeChatAvatar.style.color = colors.text;
      activeChatAvatar.style.borderRadius = "50%";
      activeChatIndicator.style.display = "block";
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
      // Normal broadcast
      ws.send(text);
    } else {
      // Private message protocol: "PRIVATE:recipient:message"
      ws.send(`PRIVATE:${currentChat}:${text}`);

      saveMessage(currentChat, {
        sender: "Me",
        text: text,
        time: time,
        type: "me"
      });
      renderMessages();
      renderUsersListUI();
    }

    messageInput.value = "";
    messageInput.focus();
  });

  // Typing Notification
  messageInput.addEventListener("input", function () {
    if (!isTyping && ws && ws.readyState === WebSocket.OPEN) {
      ws.send("TYPING");
      isTyping = true;
    }
    clearTimeout(typingTimer);
    typingTimer = setTimeout(() => {
      isTyping = false;
    }, 1500);
  });

  // Emoji Popover
  if (btnEmoji && emojiPopover) {
    btnEmoji.addEventListener("click", function (e) {
      e.stopPropagation();
      const isVisible = emojiPopover.style.display === "grid";
      emojiPopover.style.display = isVisible ? "none" : "grid";
    });

    emojiPopover.querySelectorAll(".emoji-opt").forEach(opt => {
      opt.addEventListener("click", function () {
        messageInput.value += this.textContent;
        emojiPopover.style.display = "none";
        messageInput.focus();
      });
    });

    document.addEventListener("click", function (e) {
      if (!emojiPopover.contains(e.target) && e.target !== btnEmoji) {
        emojiPopover.style.display = "none";
      }
    });
  }

  // Settings / Reconnect modal
  if (btnSettings) {
    btnSettings.addEventListener("click", function () {
      if (confirm(`Currently signed in as "${username}". Would you like to change your nickname?`)) {
        if (ws) ws.close();
        loginModal.classList.remove("hidden");
        usernameInput.focus();
      }
    });
  }

  // Utilities
  function formatTime(date) {
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false });
  }

  function escapeHtml(str) {
    if (!str) return "";
    return str
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

})();
