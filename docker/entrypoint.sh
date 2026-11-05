#!/bin/bash
set -e

# Substitute $PORT into Nginx config
envsubst '${PORT}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf
rm -f /etc/nginx/sites-enabled/default

echo "=== Starting Java Chat Server on internal port 8081 ==="
java -jar /app/chat-server.jar &

echo "=== Starting Nginx on port $PORT ==="
exec nginx -g "daemon off;"
