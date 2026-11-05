# 💬 Real-Time Cross-Platform Chat Application

A high-performance real-time chat application featuring a **Web Client**, **Native Desktop Client (Java Swing)**, and a **Dual-Engine WebSocket + TCP Server**. 

Built for seamless cross-device communication — users in a web browser can chat with users running the native desktop client in real time.

---

## ✨ Features

- 🌐 **Web Client**: Modern dark-slate UI built with vanilla HTML/CSS/JS, zero frameworks needed.
- 💻 **Desktop Client**: Native Java Swing GUI packaged into a standalone runnable `.jar`.
- ⚡ **Real-Time Cross Communication**: Messages broadcast seamlessly between web browsers and desktop apps.
- 💬 **Direct Messaging (DM)**: End-to-end 1-on-1 private chat tabs with unread notification badges.
- ⌨️ **Typing Indicators**: Real-time typing status broadcast across channels.
- 👥 **Live Online User List**: Automatically updates when users join or leave.
- 📥 **One-Click Desktop Download**: Visitors on the web site can download the native client directly.

---

## 🚀 One-Click Deploy to Render (Free)

This repository includes a `Dockerfile` and `render.yaml` ready for deployment on **[Render](https://render.com/)**.

### Step 1: Push Code to GitHub
```bash
git add .
git commit -m "Configure Docker and Render deployment"
git push origin main
```

### Step 2: Create a Web Service on Render
1. Go to **[dashboard.render.com](https://dashboard.render.com/)**.
2. Click **New +** > **Web Service**.
3. Connect your GitHub repository `ChatApplication`.
4. Render will auto-detect the **Docker** runtime.
5. Select the **Free** instance plan and click **Create Web Service**.

Render will automatically build the multi-stage Docker image, start the server, and give you a free live URL (e.g. `https://chatapplication-xxxx.onrender.com`).

---

## 🌐 Connecting `chat.itsanshuman.dev` (Custom Domain & Free SSL)

1. In your Render Dashboard, open your chat service and go to **Settings** > **Custom Domains**.
2. Click **Add Custom Domain** and enter:
   ```
   chat.itsanshuman.dev
   ```
3. Render will display the DNS target (e.g., `chatapplication-xxxx.onrender.com`).
4. Log into your DNS provider (Cloudflare, GoDaddy, Namecheap, etc.) for `itsanshuman.dev` and add:
   - **Type**: `CNAME`
   - **Name / Host**: `chat`
   - **Target / Value**: `chatapplication-xxxx.onrender.com`
   - **TTL**: Auto (or 1 min)
5. Click **Verify** in Render. Render will automatically issue and manage a free SSL certificate (HTTPS & WSS)!

Once verified, your chat application will be live at:
👉 **`https://chat.itsanshuman.dev`**

---

## 💻 Running Locally

### Prerequisites
- Java 21 JDK (or higher)
- Maven 3.9+

### 1. Build the Application
```bash
mvn clean package -DskipTests
```
This produces:
- `target/chat-server.jar` (Unified WebSocket + TCP server)
- `target/ChatApp-Desktop.jar` (Standalone desktop client)

### 2. Start the Local Server
```bash
java -jar target/chat-server.jar
```
- WebSocket Server listens on `ws://localhost:8081`
- TCP Socket Server listens on port `5000`

### 3. Open the Web Client
Open `web/index.html` in your web browser. It automatically connects to `ws://localhost:8081`.

### 4. Launch the Desktop Client
Double-click `target/ChatApp-Desktop.jar` or run:
```bash
java -jar target/ChatApp-Desktop.jar
```
Or launch with predefined username:
```bash
java -jar target/ChatApp-Desktop.jar Alice ws://localhost:8081
```

---

## 📁 Project Architecture

```
├── Dockerfile                  # Multi-stage production container for Render
├── render.yaml                 # Infrastructure-as-code for Render Blueprint
├── pom.xml                     # Maven build configuration
├── docker/
│   ├── nginx.conf.template     # Nginx config routing HTTP + WebSocket on $PORT
│   └── entrypoint.sh           # Container startup script
├── src/
│   ├── client/
│   │   └── ClientGUI.java      # Java Swing Desktop Client (WebSocket-enabled)
│   └── server/
│       ├── Server.java         # Central Dual-Engine Server (WebSocket + TCP)
│       ├── WsServer.java       # WebSocket server (org.java-websocket)
│       ├── ClientHandler.java  # Legacy raw TCP handler
│       └── ChatSession.java    # Unified session abstraction
└── web/                        # Static Web Client (HTML, CSS, JS)
    ├── index.html
    ├── style.css
    └── app.js
```
