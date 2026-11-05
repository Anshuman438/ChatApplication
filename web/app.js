// Real-Time Chat Web Client (Persistent User Profile & Reconnection)
(function () {
  let ws = null;
  let currentChat = "GROUP"; // "GROUP" or username for direct message

  const STORAGE_KEY_PROFILE = "chatapp_user_profile";
  const STORAGE_KEY_HISTORY = "chatapp_history";

  // Persistent User Profile State
  let userProfile = {
    userId: null,
    username: "",
    tag: "",
    avatarColor: "#4F5BD5",
    avatarTextColor: "#FFFFFF"
  };

  let chatHistory = {
    GROUP: []
  };
  const unreadCounts = {};
  let typingTimer = null;
  let isTyping = false;
  let activeUsers = [];

  // Deterministic avatar color palette
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
  const appWorkspace = document.getElementById("app-workspace");
  const loginModal = document.getElementById("login-modal");
  const loginForm = document.getElementById("login-form");
  const usernameInput = document.getElementById("username-input");
  const connectionStatus = document.getElementById("connection-status");
  const myUsernameEl = document.getElementById("my-username");
  const myUserTagEl = document.getElementById("my-user-tag");
  const myAvatarEl = document.getElementById("my-avatar");
  const desktopUserCard = document.getElementById("desktop-user-card");
  const groupChannelBtn = document.getElementById("group-channel-btn");
  const groupLastSnippet = document.getElementById("group-last-snippet");
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
  const btnChatOptions = document.getElementById("btn-chat-options");

  // Profile Modal Elements
  const profileModal = document.getElementById("profile-modal");
  const btnCloseProfile = document.getElementById("btn-close-profile");
  const modalAvatar = document.getElementById("modal-avatar");
  const modalDisplayName = document.getElementById("modal-display-name");
  const modalTagPill = document.getElementById("modal-tag-pill");
  const modalUserId = document.getElementById("modal-user-id");
  const btnCopyId = document.getElementById("btn-copy-id");
  const editProfileForm = document.getElementById("edit-profile-form");
  const editNicknameInput = document.getElementById("edit-nickname-input");
  const btnResetProfile = document.getElementById("btn-reset-profile");

  // Mobile Navigation Elements
  const mobileBackBtn = document.getElementById("mobile-back-btn");
  const navRoomsTab = document.getElementById("nav-rooms-tab");
  const navChatsTab = document.getElementById("nav-chats-tab");
  const navProfileTab = document.getElementById("nav-profile-tab");
  const navProfileUsername = document.getElementById("nav-profile-username");
  const navUnreadRooms = document.getElementById("nav-unread-rooms");

  // Load Saved Chat History from localStorage
  try {
    const savedHistory = localStorage.getItem(STORAGE_KEY_HISTORY);
    if (savedHistory) {
      chatHistory = JSON.parse(savedHistory);
      if (!chatHistory.GROUP) chatHistory.GROUP = [];
    }
  } catch (e) {
    console.warn("Failed to load saved history:", e);
  }

  // Load or Initialize Persistent User Profile
  function loadOrCreateProfile() {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_PROFILE);
      if (saved) {
        userProfile = JSON.parse(saved);
        if (userProfile && userProfile.userId && userProfile.username) {
          applyProfileToUI();
          loginModal.classList.add("hidden");
          connectWebSocket();
          return true;
        }
      }
    } catch (e) {
      console.warn("Error reading profile:", e);
    }
    // Show first-time login modal
    loginModal.classList.remove("hidden");
    return false;
  }

  function createNewProfile(chosenName) {
    const randomHex = Math.random().toString(36).substring(2, 9);
    const userId = "usr_" + randomHex;
    const tag = "#" + Math.floor(1000 + Math.random() * 9000);
    const colors = getAvatarColors(chosenName);

    userProfile = {
      userId: userId,
      username: chosenName,
      tag: tag,
      avatarColor: colors.bg,
      avatarTextColor: colors.text
    };

    saveProfile();
    applyProfileToUI();
    loginModal.classList.add("hidden");
    connectWebSocket();
  }

  function saveProfile() {
    try {
      localStorage.setItem(STORAGE_KEY_PROFILE, JSON.stringify(userProfile));
    } catch (e) {
      console.warn("Error saving profile to localStorage:", e);
    }
  }

  function applyProfileToUI() {
    const name = userProfile.username || "Guest";
    const tag = userProfile.tag || "";
    const colors = getAvatarColors(name);

    if (myUsernameEl) myUsernameEl.textContent = name;
    if (myUserTagEl) myUserTagEl.textContent = `${tag} · Online`;
    if (navProfileUsername) navProfileUsername.textContent = name;

    if (myAvatarEl) {
      myAvatarEl.textContent = name.charAt(0).toUpperCase();
      myAvatarEl.style.backgroundColor = colors.bg;
      myAvatarEl.style.color = colors.text;
    }

    // Update Profile Modal fields
    if (modalAvatar) {
      modalAvatar.textContent = name.charAt(0).toUpperCase();
      modalAvatar.style.backgroundColor = colors.bg;
      modalAvatar.style.color = colors.text;
    }
    if (modalDisplayName) modalDisplayName.textContent = name;
    if (modalTagPill) modalTagPill.textContent = tag;
    if (modalUserId) modalUserId.textContent = userProfile.userId;
    if (editNicknameInput) editNicknameInput.value = name;
  }

  function persistHistory() {
    try {
      localStorage.setItem(STORAGE_KEY_HISTORY, JSON.stringify(chatHistory));
    } catch (e) {}
  }

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

  // Handle Initial Nickname Form Submit
  loginForm.addEventListener("submit", function (e) {
    e.preventDefault();
    const name = usernameInput.value.trim();
    if (!name) return;
    createNewProfile(name);
  });

  // Connect WebSocket with Persistent User Identity
  function connectWebSocket() {
    if (!userProfile.userId || !userProfile.username) return;

    const url = getWebSocketUrl();
    updateStatus("Connecting...", "connecting");

    try {
      ws = new WebSocket(url);

      ws.onopen = function () {
        updateStatus("Connected", "connected");
        // Handshake with permanent userId & username: "IDENTIFY:userId:username"
        ws.send(`IDENTIFY:${userProfile.userId}:${userProfile.username}`);
      };

      ws.onmessage = function (event) {
        handleServerMessage(event.data);
      };

      ws.onclose = function () {
        updateStatus("Disconnected", "disconnected");
        // Auto-reconnect after 2.5 seconds
        setTimeout(() => {
          if (userProfile.username) connectWebSocket();
        }, 2500);
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
    if (connectionStatus) {
      connectionStatus.className = `status-pill ${stateClass}`;
      connectionStatus.innerHTML = `<span class="status-dot"></span>${text}`;
    }
  }

  // Handle Incoming Messages from Server
  function handleServerMessage(raw) {
    if (!raw) return;

    // Filter out HTTP health checks, bot scans, or probe headers
    if (raw.includes("HTTP/") || raw.includes("Go-http-client") || raw.includes("Host:") || raw.includes("User-Agent:")) {
      return;
    }

    // 0. Server WELCOME Handshake
    if (raw.startsWith("WELCOME:")) {
      console.log("Server session authenticated:", raw);
      return;
    }

    // 1. TYPING Notification: "TYPING:username"
    if (raw.startsWith("TYPING:")) {
      const sender = raw.substring(7).trim();
      if (sender.toLowerCase() !== userProfile.username.toLowerCase()) {
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
        const sender = parts[1].trim();
        const text = parts.slice(2).join(":").trim();
        const time = formatTime(new Date());

        saveMessage(sender, {
          sender: sender,
          text: text,
          time: time,
          type: "other"
        });

        if (currentChat !== sender) {
          unreadCounts[sender] = (unreadCounts[sender] || 0) + 1;
        } else {
          renderMessages();
        }
        renderUsersListUI();
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
        type: sender.toLowerCase() === userProfile.username.toLowerCase() ? "me" : "other"
      });

      if (currentChat !== "GROUP") {
        unreadCounts["GROUP"] = (unreadCounts["GROUP"] || 0) + 1;
        updateGroupBadge();
      } else {
        renderMessages();
      }

      if (groupLastSnippet) {
        const unread = unreadCounts["GROUP"] || 0;
        groupLastSnippet.textContent = unread > 0 ? `${unread} new message${unread > 1 ? "s" : ""}` : `${sender}: ${text}`;
      }
    }
  }

  function saveMessage(chatKey, msgObj) {
    if (!chatHistory[chatKey]) {
      chatHistory[chatKey] = [];
    }
    chatHistory[chatKey].push(msgObj);
    persistHistory();
  }

  function renderMessages() {
    messagesContainer.innerHTML = "";
    
    // Centered Day badge
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
                   u.toLowerCase() !== userProfile.username.toLowerCase() && 
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
      if (navUnreadRooms) {
        navUnreadRooms.textContent = count;
        navUnreadRooms.style.display = "flex";
      }
    } else {
      badge.style.display = "none";
      if (navUnreadRooms) {
        navUnreadRooms.style.display = "none";
      }
    }
  }

  // Switch Active Chat Window
  function switchChat(chatKey) {
    currentChat = chatKey;
    unreadCounts[chatKey] = 0;

    // On mobile, switch to conversation view
    if (window.innerWidth <= 768 && appWorkspace) {
      appWorkspace.classList.remove("view-inbox");
      appWorkspace.classList.add("view-chat");
    }

    if (chatKey === "GROUP") {
      groupChannelBtn.classList.add("active");
      currentChatTitle.textContent = "Group Chat";
      currentChatSubtitle.innerHTML = `<span class="active-status-bullet">●</span> Online · Connected`;
      messageInput.placeholder = "Message # Group Chat...";
      activeChatAvatar.textContent = "#";
      activeChatAvatar.style.backgroundColor = "#1A2030";
      activeChatAvatar.style.color = "#A3B1FF";
      activeChatAvatar.style.borderRadius = "12px";
      activeChatIndicator.style.display = "none";
      updateGroupBadge();
      setActiveNavTab(navRoomsTab);
    } else {
      groupChannelBtn.classList.remove("active");
      currentChatTitle.textContent = chatKey;
      currentChatSubtitle.innerHTML = `<span class="active-status-bullet">●</span> Online · Connected`;
      messageInput.placeholder = `Message ${chatKey}...`;
      const colors = getAvatarColors(chatKey);
      activeChatAvatar.textContent = chatKey.charAt(0).toUpperCase();
      activeChatAvatar.style.backgroundColor = colors.bg;
      activeChatAvatar.style.color = colors.text;
      activeChatAvatar.style.borderRadius = "50%";
      activeChatIndicator.style.display = "block";
      setActiveNavTab(navChatsTab);
    }

    renderUsersListUI();
    renderMessages();
    messageInput.focus();
  }

  groupChannelBtn.addEventListener("click", () => switchChat("GROUP"));

  // Mobile Back Button: Returns to Inbox View
  if (mobileBackBtn) {
    mobileBackBtn.addEventListener("click", function () {
      if (appWorkspace) {
        appWorkspace.classList.remove("view-chat");
        appWorkspace.classList.add("view-inbox");
      }
      setActiveNavTab(navChatsTab);
    });
  }

  // Bottom Navigation Tabs
  function setActiveNavTab(activeTab) {
    [navRoomsTab, navChatsTab, navProfileTab].forEach(tab => {
      if (tab) tab.classList.remove("active");
    });
    if (activeTab) activeTab.classList.add("active");
  }

  if (navRoomsTab) {
    navRoomsTab.addEventListener("click", function () {
      switchChat("GROUP");
      setActiveNavTab(navRoomsTab);
    });
  }

  if (navChatsTab) {
    navChatsTab.addEventListener("click", function () {
      if (appWorkspace) {
        appWorkspace.classList.remove("view-chat");
        appWorkspace.classList.add("view-inbox");
      }
      setActiveNavTab(navChatsTab);
    });
  }

  // Profile Modal Event Listeners
  function openProfileModal() {
    applyProfileToUI();
    profileModal.classList.remove("hidden");
  }

  function closeProfileModal() {
    profileModal.classList.add("hidden");
  }

  if (navProfileTab) {
    navProfileTab.addEventListener("click", function () {
      setActiveNavTab(navProfileTab);
      openProfileModal();
    });
  }

  if (btnSettings) {
    btnSettings.addEventListener("click", function (e) {
      e.stopPropagation();
      openProfileModal();
    });
  }

  if (desktopUserCard) {
    desktopUserCard.addEventListener("click", function () {
      openProfileModal();
    });
  }

  if (btnChatOptions) {
    btnChatOptions.addEventListener("click", function () {
      openProfileModal();
    });
  }

  if (btnCloseProfile) {
    btnCloseProfile.addEventListener("click", closeProfileModal);
  }

  if (profileModal) {
    profileModal.addEventListener("click", function (e) {
      if (e.target === profileModal) closeProfileModal();
    });
  }

  // Copy User ID
  if (btnCopyId) {
    btnCopyId.addEventListener("click", function () {
      if (userProfile.userId) {
        navigator.clipboard.writeText(userProfile.userId).then(() => {
          btnCopyId.textContent = "Copied!";
          setTimeout(() => { btnCopyId.textContent = "Copy"; }, 2000);
        }).catch(() => {
          btnCopyId.textContent = "Copied!";
          setTimeout(() => { btnCopyId.textContent = "Copy"; }, 2000);
        });
      }
    });
  }

  // Edit Nickname
  if (editProfileForm) {
    editProfileForm.addEventListener("submit", function (e) {
      e.preventDefault();
      const newName = editNicknameInput.value.trim();
      if (!newName) return;

      userProfile.username = newName;
      saveProfile();
      applyProfileToUI();

      // Re-identify with the server
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(`IDENTIFY:${userProfile.userId}:${userProfile.username}`);
      }

      closeProfileModal();
    });
  }

  // Switch Account / Create New Profile
  if (btnResetProfile) {
    btnResetProfile.addEventListener("click", function () {
      if (confirm("Are you sure you want to switch accounts? This will generate a new user identity.")) {
        localStorage.removeItem(STORAGE_KEY_PROFILE);
        if (ws) ws.close();
        closeProfileModal();
        loginModal.classList.remove("hidden");
        usernameInput.value = "";
        usernameInput.focus();
      }
    });
  }

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

  // Initialize: Load existing profile from localStorage or prompt for first time
  loadOrCreateProfile();
  renderMessages();

})();
