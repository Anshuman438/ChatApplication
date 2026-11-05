#!/bin/bash
# ==============================================================================
# Automated Deployment Script for Oracle Cloud Always Free VM (Ubuntu)
# Domain: chat.itsanshuman.dev
# ==============================================================================

set -e

echo "=== 1. Updating System Packages ==="
sudo apt-get update && sudo apt-get install -y openjdk-21-jre-headless nginx certbot python3-certbot-nginx git ufw

echo "=== 2. Creating Directories ==="
sudo mkdir -p /var/www/chat.itsanshuman.dev/web
sudo mkdir -p /var/www/chat.itsanshuman.dev/downloads
sudo mkdir -p /opt/chat_application

echo "=== 3. Copying Web Files & Desktop Download ==="
sudo cp -r web/* /var/www/chat.itsanshuman.dev/web/
if [ -f target/ChatApp-Desktop.jar ]; then
    sudo cp target/ChatApp-Desktop.jar /var/www/chat.itsanshuman.dev/downloads/
fi
sudo chown -R www-data:www-data /var/www/chat.itsanshuman.dev

echo "=== 4. Setting up Chat Server Binary ==="
sudo cp target/chat-server.jar /opt/chat_application/
sudo chown -R ubuntu:ubuntu /opt/chat_application

echo "=== 5. Setting up Systemd Service ==="
sudo cp systemd/chat-server.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable chat-server
sudo systemctl restart chat-server

echo "=== 6. Configuring Nginx ==="
sudo cp nginx/chat.itsanshuman.dev.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/chat.itsanshuman.dev.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx

echo "=== 7. Configuring Firewall (iptables / ufw) ==="
# Oracle Ubuntu images have default iptables rules that block 80 & 443
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save || true

echo "=============================================================================="
echo "Deployment Complete!"
echo "Next step: Run SSL certificate generator (once DNS chat.itsanshuman.dev is set):"
echo "  sudo certbot --nginx -d chat.itsanshuman.dev"
echo "=============================================================================="
