# 🚀 Deployment Guide: Oracle Cloud Always Free VM & chat.itsanshuman.dev

This guide walks you through deploying your **Real-Time Cross-Platform Chat Application** to an **Oracle Cloud Always Free VM**, attaching your domain **`chat.itsanshuman.dev`**, configuring free SSL (HTTPS/WSS), and serving both the **Web Client** and **Desktop App Download**.

---

## 🏗️ Architecture Overview

```
                                chat.itsanshuman.dev
                                          │
                                          ▼
                      ┌───────────────────────────────────────┐
                      │    Oracle Cloud Always Free VM (Ubuntu)│
                      │                                       │
                      │    Nginx (Reverse Proxy + SSL)        │
                      │       │                    │          │
                      │       ▼                    ▼          │
                      │   /var/www/web         /ws (Proxy)    │
                      │  (Static Web App)          │          │
                      │  + .jar Download           ▼          │
                      │                    Java Chat Server   │
                      │                    (Port 8081 & 5000) │
                      └───────────────────────────────────────┘
```

---

## Step 1: Set Up Your Free Oracle Cloud VM

1. Log in to your **[Oracle Cloud Console](https://cloud.oracle.com/)**.
2. Navigate to **Compute** > **Instances** > **Create Instance**.
3. Choose:
   - **Image**: Ubuntu 22.04 or 24.04 LTS (Canonical Ubuntu).
   - **Shape**: 
     - *VM.Standard.A1.Flex* (Ampere ARM, up to 4 OCPUs & 24GB RAM free), or
     - *VM.Standard.E2.1.Micro* (AMD 1GB RAM free).
   - **SSH Keys**: Download your private key (`.key` / `.pem`) and save it.
4. Click **Create**. Once active, copy the **Public IP Address** (e.g. `129.153.xx.xx`).

---

## Step 2: Open Ingress Ports in Oracle Cloud (Crucial!)

Oracle Cloud has strict security firewalls by default. You must allow HTTP (80) and HTTPS (443):

1. On your instance page, click your **Virtual Cloud Network (VCN)**.
2. Under **Security Lists**, click **Default Security List**.
3. Click **Add Ingress Rules**:
   - **Source CIDR**: `0.0.0.0/0`
   - **IP Protocol**: `TCP`
   - **Destination Port Range**: `80,443`
   - **Description**: Allow HTTP and HTTPS
4. Click **Add Ingress Rules**.

---

## Step 3: Configure DNS for `chat.itsanshuman.dev`

Go to your DNS provider (Cloudflare, Namecheap, GoDaddy, or wherever `itsanshuman.dev` is managed):

| Type | Name | Content / Value | TTL | Proxy Status |
| :--- | :--- | :--- | :--- | :--- |
| **A** | `chat` | `YOUR_ORACLE_PUBLIC_IP` | Auto / 1 min | DNS Only (Gray cloud if Cloudflare)* |

> *\*Note on Cloudflare:* If using Cloudflare, make sure **WebSockets** is toggled ON under *Network Settings*, or keep proxy disabled ("DNS Only") while generating Let's Encrypt certificates.

---

## Step 4: Clone & Deploy on the Oracle VM

SSH into your Oracle VM:
```bash
ssh -i /path/to/your-key.pem ubuntu@YOUR_ORACLE_PUBLIC_IP
```

Clone your repository and run the automated deployment script:
```bash
# Clone the repository
git clone https://github.com/Anshuman438/ChatApplication.git
cd ChatApplication

# Make deploy script executable and run it
chmod +x deploy.sh
./deploy.sh
```

The script automatically:
1. Installs OpenJDK 21, Nginx, Certbot, and UFW.
2. Deploys the static Web client to `/var/www/chat.itsanshuman.dev/web`.
3. Deploys the `ChatApp-Desktop.jar` to `/var/www/chat.itsanshuman.dev/downloads/`.
4. Creates and activates the `systemd` service to run the Java Server 24/7.
5. Configures Nginx with WebSocket reverse proxying.
6. Opens Ubuntu firewall ports 80 and 443.

---

## Step 5: Activate Free SSL Certificate (HTTPS & WSS)

Once your DNS `chat.itsanshuman.dev` has propagated (usually takes 1–5 minutes), run:

```bash
sudo certbot --nginx -d chat.itsanshuman.dev
```

- Enter your email address.
- Agree to the terms.
- Certbot will automatically configure HTTPS and update Nginx for secure WebSockets (`wss://`).

Now visit **`https://chat.itsanshuman.dev`** in your browser!

---

## Step 6: Add to Your Portfolio (`itsanshuman.dev`)

In your portfolio website repository, update your Projects section:

```json
{
  "title": "Real-Time Cross-Platform Chat System",
  "description": "Multi-threaded real-time chat platform with group messaging, private direct chats, and typing indicators. Built with Java and WebSockets, enabling real-time cross-communication between web browsers and native desktop clients.",
  "techStack": ["Java 21", "WebSockets", "Java Swing", "HTML5/CSS3", "Nginx", "Oracle Cloud"],
  "links": {
    "live": "https://chat.itsanshuman.dev",
    "download": "https://chat.itsanshuman.dev/download/ChatApp-Desktop.jar",
    "github": "https://github.com/Anshuman438/ChatApplication"
  }
}
```

---

## 🛠️ Management & Useful Commands on the VM

- **View Server Logs**:
  ```bash
  sudo journalctl -u chat-server -f
  ```
- **Restart Chat Server**:
  ```bash
  sudo systemctl restart chat-server
  ```
- **Check Server Status**:
  ```bash
  sudo systemctl status chat-server
  ```
- **Rebuild New Changes**:
  ```bash
  git pull
  mvn package -DskipTests
  ./deploy.sh
  ```
