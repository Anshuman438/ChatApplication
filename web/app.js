// Real-Time Chat Web Client (Persistent User Profile, Reconnection & Multi-Group Support)
(function () {
  let ws = null;
  let currentChat = "Group Chat"; // Active room name or username for direct message

  const STORAGE_KEY_PROFILE = "chatapp_user_profile";
  const STORAGE_KEY_HISTORY = "chatapp_history";
  const STORAGE_KEY_GROUPS = "chatapp_groups";
  const STORAGE_KEY_REMOVED_USERS = "chatapp_removed_users";

  // Pre-configured engaging demo users
  const DUMMY_PERSONAS = [
    {
      name: "Alex",
      status: "UI/UX Designer",
      welcomeMsg: "Hey! The dark theme interface looks super crisp ✨",
      replies: [
        "Love the typography and layout here!",
        "Working on the next component iteration, what do you think?",
        "That looks great, really responsive on mobile too!",
        "Awesome! Let me know if you need any icon or color tweaks."
      ]
    },
    {
      name: "Sarah",
      status: "Full-Stack Dev",
      welcomeMsg: "Hey! Just pushed the latest updates to the server 🚀",
      replies: [
        "Everything compiles cleanly on Maven and Docker!",
        "WebSockets latency is virtually instantaneous right now.",
        "Checked the logs, zero packet drops or connection issues.",
        "Sounds good! Let's ship it."
      ]
    },
    {
      name: "Liam",
      status: "Mobile Engineer",
      welcomeMsg: "Hi! Tested the offline .jar desktop app and mobile web view.",
      replies: [
        "Touch gestures and room navigation feel so native now.",
        "The jar executable runs completely smooth with no memory leaks.",
        "Nice! The group chat and DM routing work seamlessly.",
        "Catch you in the next build!"
      ]
    },
    {
      name: "Nova AI",
      status: "AI Assistant",
      welcomeMsg: "Hello! I'm Nova, your real-time chat assistant. How can I help today?",
      replies: [
        "I'm here to test multi-user messaging, latency, and group synchronization!",
        "Antigravity chat handles concurrent WebSocket connections smoothly.",
        "Your message was received and processed in less than 5 milliseconds.",
        "Everything looks operational and ready for prime time!"
      ]
    }
  ];

  // Set of usernames removed by the user
  let removedUsers = new Set();
  try {
    const savedRemoved = localStorage.getItem(STORAGE_KEY_REMOVED_USERS);
    if (savedRemoved) {
      const parsed = JSON.parse(savedRemoved);
      if (Array.isArray(parsed)) {
        removedUsers = new Set(parsed.map(u => u.toLowerCase()));
      }
    }
  } catch (e) {
    console.warn("Failed to load removed users:", e);
  }

  function saveRemovedUsers() {
    try {
      localStorage.setItem(STORAGE_KEY_REMOVED_USERS, JSON.stringify(Array.from(removedUsers)));
    } catch (e) {}
  }

  function isDummyUser(name) {
    if (!name) return false;
    return DUMMY_PERSONAS.some(p => p.name.toLowerCase() === name.toLowerCase());
  }

  // Persistent User Profile State
  let userProfile = {
    userId: null,
    username: "",
    tag: "",
    avatarColor: "#4F5BD5",
    avatarTextColor: "#FFFFFF"
  };

  // Group Rooms State
  let groupRooms = ["Group Chat"];
  try {
    const savedGroups = localStorage.getItem(STORAGE_KEY_GROUPS);
    if (savedGroups) {
      const parsed = JSON.parse(savedGroups);
      if (Array.isArray(parsed) && parsed.length > 0) {
        groupRooms = parsed;
      }
    }
  } catch (e) {
    console.warn("Failed to load saved groups:", e);
  }

  function saveGroups() {
    try {
      localStorage.setItem(STORAGE_KEY_GROUPS, JSON.stringify(groupRooms));
    } catch (e) {}
  }

  let chatHistory = {
    "Group Chat": []
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
    if (!name || groupRooms.includes(name) || name === "GROUP") return { bg: "#4F5BD5", text: "#FFFFFF" };
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

  // Rooms and Direct Messages Lists
  const roomsList = document.getElementById("rooms-list");
  const onlineUsersList = document.getElementById("online-users-list");
  const userSearch = document.getElementById("user-search");
  const currentChatTitle = document.getElementById("current-chat-title");
  const currentChatSubtitle = document.getElementById("current-chat-subtitle");
  const activeChatAvatar = document.getElementById("active-chat-avatar");
  const activeChatAvatarText = document.getElementById("active-chat-avatar-text");
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
  const chatOptionsMenu = document.getElementById("chat-options-menu");
  const menuBtnProfile = document.getElementById("menu-btn-profile");
  const menuBtnClearChat = document.getElementById("menu-btn-clear-chat");
  const menuBtnRemoveActiveUser = document.getElementById("menu-btn-remove-active-user");
  const btnAddDummyUsers = document.getElementById("btn-add-dummy-users");
  const btnClearUsers = document.getElementById("btn-clear-users");

  // Create Group Modal Elements
  const btnOpenCreateGroup = document.getElementById("btn-open-create-group");
  const createGroupModal = document.getElementById("create-group-modal");
  const btnCloseGroupModal = document.getElementById("btn-close-group-modal");
  const btnCancelGroupModal = document.getElementById("btn-cancel-group-modal");
  const createGroupForm = document.getElementById("create-group-form");
  const groupNameInput = document.getElementById("group-name-input");
  const previewGroupName = document.getElementById("preview-group-name");
  const previewGroupHash = document.getElementById("preview-group-hash");
  const mobileComposeBtn = document.getElementById("mobile-compose-btn");

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
      if (!chatHistory["Group Chat"]) chatHistory["Group Chat"] = [];
    }
  } catch (e) {
    console.warn("Failed to load saved history:", e);
  }

  // Pre-seed initial greeting messages for demo personas
  function initDummyUsersHistory() {
    DUMMY_PERSONAS.forEach(p => {
      if (!chatHistory[p.name] || chatHistory[p.name].length === 0) {
        chatHistory[p.name] = [
          {
            sender: p.name,
            text: p.welcomeMsg,
            time: formatTime(new Date()),
            type: "other"
          }
        ];
      }
    });
    persistHistory();
  }

  // Auto-init dummy users on first load
  if (localStorage.getItem("chatapp_dummy_initialized") !== "true") {
    initDummyUsersHistory();
    try {
      localStorage.setItem("chatapp_dummy_initialized", "true");
    } catch (e) {}
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
    if (myUserTagEl) myUserTagEl.textContent = `${tag} \u00B7 Online`;
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

    // 1. GROUPLIST Update: "GROUPLIST:group1,group2,..."
    if (raw.startsWith("GROUPLIST:")) {
      const listStr = raw.substring(10);
      const incoming = listStr.split(",").map(g => g.trim()).filter(Boolean);
      incoming.forEach(g => {
        if (!groupRooms.includes(g)) {
          groupRooms.push(g);
        }
      });
      saveGroups();
      renderRoomsListUI();
      return;
    }

    // 2. TYPING Notification: "TYPING:target:username" or "TYPING:username"
    if (raw.startsWith("TYPING:")) {
      const rest = raw.substring(7).trim();
      let target = "";
      let sender = rest;
      if (rest.includes(":")) {
        const parts = rest.split(":", 2);
        target = parts[0].trim();
        sender = parts[1].trim();
      }
      if (sender.toLowerCase() !== userProfile.username.toLowerCase()) {
        if (!target || target === currentChat || (groupRooms.includes(target) && target === currentChat)) {
          showTyping(sender);
        }
      }
      return;
    }

    // 3. USERLIST Update: "USERLIST:user1,user2,..."
    if (raw.startsWith("USERLIST:")) {
      const usersStr = raw.substring(9);
      updateUsersList(usersStr);
      return;
    }

    // 4. GROUP_MSG Message: "GROUP_MSG:groupName:sender:message"
    if (raw.startsWith("GROUP_MSG:")) {
      const parts = raw.split(":");
      if (parts.length >= 4) {
        const groupName = parts[1].trim();
        const sender = parts[2].trim();
        const text = parts.slice(3).join(":").trim();
        const time = formatTime(new Date());

        if (!groupRooms.includes(groupName)) {
          groupRooms.push(groupName);
          saveGroups();
        }

        saveMessage(groupName, {
          sender: sender,
          text: text,
          time: time,
          type: sender.toLowerCase() === userProfile.username.toLowerCase() ? "me" : "other"
        });

        if (currentChat !== groupName) {
          unreadCounts[groupName] = (unreadCounts[groupName] || 0) + 1;
        } else {
          renderMessages();
        }
        renderRoomsListUI();
      }
      return;
    }

    // 5. PRIVATE Message: "PRIVATE:sender:message"
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

    // 6. System Broadcasts: "🟢 user joined" or "🔴 user left" or "📢 ..."
    if (raw.startsWith("🟢 ") || raw.startsWith("🔴 ") || raw.startsWith("📢")) {
      const time = formatTime(new Date());
      saveMessage(currentChat, {
        sender: "SYSTEM",
        text: raw,
        time: time,
        type: "system"
      });
      renderMessages();
      return;
    }

    // 7. System Warning/Error: "SYSTEM: ..."
    if (raw.startsWith("SYSTEM:")) {
      const time = formatTime(new Date());
      saveMessage(currentChat, {
        sender: "SYSTEM",
        text: raw.substring(7).trim(),
        time: time,
        type: "system"
      });
      renderMessages();
      return;
    }

    // 8. Legacy Group Message: "sender: message"
    const colonIndex = raw.indexOf(":");
    if (colonIndex !== -1) {
      const sender = raw.substring(0, colonIndex).trim();
      const text = raw.substring(colonIndex + 1).trim();
      const time = formatTime(new Date());

      const targetRoom = groupRooms.includes(currentChat) ? currentChat : "Group Chat";

      saveMessage(targetRoom, {
        sender: sender,
        text: text,
        time: time,
        type: sender.toLowerCase() === userProfile.username.toLowerCase() ? "me" : "other"
      });

      if (currentChat !== targetRoom) {
        unreadCounts[targetRoom] = (unreadCounts[targetRoom] || 0) + 1;
      } else {
        renderMessages();
      }
      renderRoomsListUI();
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
                   u.toLowerCase() !== (userProfile.username || "").toLowerCase() && 
                   !u.includes("HTTP/") && 
                   !u.includes("Go-http-client"));

    renderUsersListUI();
  }

  // Get active combined users (Real online clients + Dummy demo users, excluding any removed)
  function getCombinedUsers() {
    const combined = [];
    const seen = new Set();
    const myName = (userProfile.username || "").toLowerCase();

    // 1. Add real active users from server
    activeUsers.forEach(u => {
      const lower = u.toLowerCase();
      if (!removedUsers.has(lower) && !seen.has(lower) && lower !== myName) {
        seen.add(lower);
        combined.push(u);
      }
    });

    // 2. Add dummy personas that haven't been removed
    DUMMY_PERSONAS.forEach(p => {
      const lower = p.name.toLowerCase();
      if (!removedUsers.has(lower) && !seen.has(lower) && lower !== myName) {
        seen.add(lower);
        combined.push(p.name);
      }
    });

    return combined;
  }

  function renderUsersListUI() {
    onlineUsersList.innerHTML = "";

    const combined = getCombinedUsers();
    const query = (userSearch ? userSearch.value : "").trim().toLowerCase();
    const filtered = query 
      ? combined.filter(u => u.toLowerCase().includes(query)) 
      : combined;

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
      const isDemo = isDummyUser(user);
      const previewText = lastMsg 
        ? (lastMsg.type === "me" ? "You: " : "") + lastMsg.text 
        : (isDemo ? "Online · Demo user" : "Online");
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
        <button type="button" class="btn-remove-user" title="Remove ${escapeHtml(user)}" aria-label="Remove user">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      `;

      // Inline Remove Button
      const btnRemove = row.querySelector(".btn-remove-user");
      if (btnRemove) {
        btnRemove.addEventListener("click", function (e) {
          e.stopPropagation();
          removeUser(user);
        });
      }

      row.addEventListener("click", () => switchChat(user));
      onlineUsersList.appendChild(row);
    });
  }

  // Remove individual user
  function removeUser(user) {
    if (!user) return;
    const lower = user.toLowerCase();
    removedUsers.add(lower);
    saveRemovedUsers();

    // Notify backend WebSocket server to prune this user session
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(`REMOVE_USER:${user}`);
    }

    // If currently chatting with this user, switch back to Group Chat
    if (currentChat === user) {
      switchChat("Group Chat");
    }

    renderUsersListUI();
  }

  // Clear all other users from list
  function clearAllUsers() {
    const combined = getCombinedUsers();
    combined.forEach(u => {
      removedUsers.add(u.toLowerCase());
    });
    saveRemovedUsers();

    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send("CLEAR_USERS");
    }

    if (!groupRooms.includes(currentChat)) {
      switchChat("Group Chat");
    }

    renderUsersListUI();
  }

  // Restore pre-configured demo users
  function restoreDemoUsers() {
    DUMMY_PERSONAS.forEach(p => {
      removedUsers.delete(p.name.toLowerCase());
    });
    saveRemovedUsers();
    initDummyUsersHistory();
    renderUsersListUI();
  }

  if (userSearch) {
    userSearch.addEventListener("input", function () {
      renderRoomsListUI();
      renderUsersListUI();
    });
  }

  // Render Rooms / Groups List in Sidebar
  function renderRoomsListUI() {
    if (!roomsList) return;
    roomsList.innerHTML = "";

    const query = (userSearch ? userSearch.value : "").trim().toLowerCase();

    groupRooms.forEach(room => {
      if (query && !room.toLowerCase().includes(query)) return;

      const isActive = currentChat === room;
      const unread = unreadCounts[room] || 0;

      const history = chatHistory[room] || [];
      const lastMsg = history.length > 0 ? history[history.length - 1] : null;
      let previewText = "Public room &middot; Tap to chat";
      if (lastMsg) {
        if (lastMsg.type === "system") {
          previewText = lastMsg.text;
        } else {
          previewText = (lastMsg.type === "me" ? "You: " : `${lastMsg.sender}: `) + lastMsg.text;
        }
      }

      const row = document.createElement("div");
      row.className = `channel-row ${isActive ? "active" : ""}`;
      row.innerHTML = `
        <div class="channel-hash">#</div>
        <div class="channel-info-col">
          <div class="channel-name">${escapeHtml(room)}</div>
          <div class="channel-snippet">${previewText}</div>
        </div>
        ${unread > 0 ? `<span class="unread-bubble">${unread}</span>` : ""}
      `;

      row.addEventListener("click", () => switchChat(room, true));
      roomsList.appendChild(row);
    });

    updateGroupBadge();
  }

  function updateGroupBadge() {
    let totalUnread = 0;
    groupRooms.forEach(r => {
      totalUnread += (unreadCounts[r] || 0);
    });
    if (navUnreadRooms) {
      if (totalUnread > 0) {
        navUnreadRooms.textContent = totalUnread;
        navUnreadRooms.style.display = "flex";
      } else {
        navUnreadRooms.style.display = "none";
      }
    }
  }

  // Switch Active Chat Window
  function switchChat(chatKey, isGroup) {
    const isRoom = isGroup !== undefined ? isGroup : groupRooms.includes(chatKey);
    currentChat = chatKey;
    unreadCounts[chatKey] = 0;

    // Hide chat options dropdown when switching
    if (chatOptionsMenu) chatOptionsMenu.classList.add("hidden");

    // On mobile, switch to conversation view
    if (window.innerWidth <= 768 && appWorkspace) {
      appWorkspace.classList.remove("view-inbox");
      appWorkspace.classList.add("view-chat");
    }

    if (isRoom) {
      currentChatTitle.textContent = chatKey;
      currentChatSubtitle.innerHTML = `<span class="active-status-bullet">&bull;</span> Public Room &middot; Connected`;
      messageInput.placeholder = `Message #${chatKey}...`;
      if (activeChatAvatarText) {
        activeChatAvatarText.textContent = "#";
      } else {
        activeChatAvatar.textContent = "#";
      }
      activeChatAvatar.style.backgroundColor = "#1A2030";
      activeChatAvatar.style.color = "#A3B1FF";
      activeChatAvatar.style.borderRadius = "12px";
      if (activeChatIndicator) activeChatIndicator.style.display = "none";
      if (menuBtnRemoveActiveUser) menuBtnRemoveActiveUser.style.display = "none";
      setActiveNavTab(navRoomsTab);
    } else {
      currentChatTitle.textContent = chatKey;
      currentChatSubtitle.innerHTML = `<span class="active-status-bullet">&bull;</span> Online &middot; Connected`;
      messageInput.placeholder = `Message ${chatKey}...`;
      const colors = getAvatarColors(chatKey);
      if (activeChatAvatarText) {
        activeChatAvatarText.textContent = chatKey.charAt(0).toUpperCase();
      } else {
        activeChatAvatar.textContent = chatKey.charAt(0).toUpperCase();
      }
      activeChatAvatar.style.backgroundColor = colors.bg;
      activeChatAvatar.style.color = colors.text;
      activeChatAvatar.style.borderRadius = "50%";
      if (activeChatIndicator) activeChatIndicator.style.display = "block";
      if (menuBtnRemoveActiveUser) {
        menuBtnRemoveActiveUser.style.display = "flex";
        const labelEl = menuBtnRemoveActiveUser.querySelector("span");
        if (labelEl) labelEl.textContent = `Remove ${chatKey}`;
      }
      setActiveNavTab(navChatsTab);
    }

    renderRoomsListUI();
    renderUsersListUI();
    renderMessages();
    messageInput.focus();
  }

  // Create Group Modal Listeners
  function openCreateGroupModal() {
    if (createGroupModal) {
      createGroupModal.classList.remove("hidden");
      if (groupNameInput) {
        groupNameInput.value = "";
        if (previewGroupName) previewGroupName.textContent = "New Group";
        setTimeout(() => groupNameInput.focus(), 60);
      }
    }
  }

  function closeCreateGroupModal() {
    if (createGroupModal) {
      createGroupModal.classList.add("hidden");
    }
  }

  if (btnOpenCreateGroup) {
    btnOpenCreateGroup.addEventListener("click", openCreateGroupModal);
  }

  if (mobileComposeBtn) {
    mobileComposeBtn.addEventListener("click", openCreateGroupModal);
  }

  if (btnCloseGroupModal) {
    btnCloseGroupModal.addEventListener("click", closeCreateGroupModal);
  }

  if (btnCancelGroupModal) {
    btnCancelGroupModal.addEventListener("click", closeCreateGroupModal);
  }

  if (createGroupModal) {
    createGroupModal.addEventListener("click", function (e) {
      if (e.target === createGroupModal) closeCreateGroupModal();
    });
  }

  if (groupNameInput) {
    groupNameInput.addEventListener("input", function () {
      const val = this.value.trim();
      if (previewGroupName) {
        previewGroupName.textContent = val ? val : "New Group";
      }
    });
  }

  if (createGroupForm) {
    createGroupForm.addEventListener("submit", function (e) {
      e.preventDefault();
      const name = groupNameInput.value.trim();
      if (!name) return;

      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(`CREATE_GROUP:${name}`);
      }

      if (!groupRooms.includes(name)) {
        groupRooms.push(name);
        saveGroups();
      }

      closeCreateGroupModal();
      switchChat(name, true);
    });
  }

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
      const room = groupRooms.includes(currentChat) ? currentChat : (groupRooms[0] || "Group Chat");
      switchChat(room, true);
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

  // Direct Messages Section Header Actions
  if (btnAddDummyUsers) {
    btnAddDummyUsers.addEventListener("click", function (e) {
      e.stopPropagation();
      restoreDemoUsers();
    });
  }

  if (btnClearUsers) {
    btnClearUsers.addEventListener("click", function (e) {
      e.stopPropagation();
      clearAllUsers();
    });
  }

  // Chat Options Dropdown Toggle & Actions
  if (btnChatOptions && chatOptionsMenu) {
    btnChatOptions.addEventListener("click", function (e) {
      e.stopPropagation();
      chatOptionsMenu.classList.toggle("hidden");
    });

    document.addEventListener("click", function (e) {
      if (!chatOptionsMenu.contains(e.target) && e.target !== btnChatOptions) {
        chatOptionsMenu.classList.add("hidden");
      }
    });
  }

  if (menuBtnProfile) {
    menuBtnProfile.addEventListener("click", function () {
      if (chatOptionsMenu) chatOptionsMenu.classList.add("hidden");
      openProfileModal();
    });
  }

  if (menuBtnClearChat) {
    menuBtnClearChat.addEventListener("click", function () {
      if (chatOptionsMenu) chatOptionsMenu.classList.add("hidden");
      chatHistory[currentChat] = [];
      persistHistory();
      renderMessages();
      renderRoomsListUI();
      renderUsersListUI();
    });
  }

  if (menuBtnRemoveActiveUser) {
    menuBtnRemoveActiveUser.addEventListener("click", function () {
      if (chatOptionsMenu) chatOptionsMenu.classList.add("hidden");
      removeUser(currentChat);
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

  // Simulated smart response from demo persona
  function handleDummyUserReply(dummyName, userText) {
    const persona = DUMMY_PERSONAS.find(p => p.name.toLowerCase() === dummyName.toLowerCase());
    if (!persona) return;

    // Show typing bubble after 500ms
    setTimeout(() => {
      if (currentChat === dummyName) {
        showTyping(dummyName);
      }
    }, 500);

    // Send realistic response after delay
    const delay = 1300 + Math.random() * 800;
    setTimeout(() => {
      if (typingRow) typingRow.style.display = "none";

      const replyText = persona.replies[Math.floor(Math.random() * persona.replies.length)];
      const time = formatTime(new Date());

      saveMessage(dummyName, {
        sender: dummyName,
        text: replyText,
        time: time,
        type: "other"
      });

      if (currentChat !== dummyName) {
        unreadCounts[dummyName] = (unreadCounts[dummyName] || 0) + 1;
      } else {
        renderMessages();
      }
      renderUsersListUI();
    }, delay);
  }

  // Send Message
  messageForm.addEventListener("submit", function (e) {
    e.preventDefault();
    const text = messageInput.value.trim();
    if (!text) return;

    const time = formatTime(new Date());

    if (groupRooms.includes(currentChat) || currentChat === "GROUP") {
      if (!ws || ws.readyState !== WebSocket.OPEN) return;
      const room = currentChat === "GROUP" ? "Group Chat" : currentChat;
      ws.send(`GROUP_MSG:${room}:${text}`);
    } else {
      // Check if chatting with a dummy persona
      if (isDummyUser(currentChat)) {
        handleDummyUserReply(currentChat, text);
      } else if (ws && ws.readyState === WebSocket.OPEN) {
        // Private message protocol: "PRIVATE:recipient:message"
        ws.send(`PRIVATE:${currentChat}:${text}`);
      }

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
      ws.send(`TYPING:${currentChat}`);
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
  renderRoomsListUI();
  renderUsersListUI();
  renderMessages();

})();
