#!/bin/bash
# EC2 "User data" script: runs once as root on the instance's first boot.
# Prepares Amazon Linux 2023 for CampusHub (backend + nginx/frontend). Deploys are done later by GitHub Actions.
# Log: /var/log/cloud-init-output.log
set -euxo pipefail

# --- 1 GB swap (t3.micro has only 1 GB RAM; keeps the JVM from being OOM-killed) ---
dd if=/dev/zero of=/swapfile bs=1M count=1024
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile swap swap defaults 0 0' >> /etc/fstab

# --- Packages ---
dnf install -y java-21-amazon-corretto-headless nginx

# --- Backend (systemd service; app.jar is uploaded by the backend deploy workflow) ---
useradd --system --home /opt/campushub --shell /sbin/nologin campushub
mkdir -p /opt/campushub
chown campushub:campushub /opt/campushub

# Read by systemd as root, so it can stay root-only. Put DB passwords etc. here later.
cat > /opt/campushub/app.env <<'EOF'
SERVER_ADDRESS=127.0.0.1
# Small footprint for a 1 GB instance: capped heap, restart (via systemd) instead of limping on OOM.
JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError"
# Default is 200 threads; plenty fewer are needed at this traffic level and each costs memory.
SERVER_TOMCAT_THREADS_MAX=50
EOF
chmod 600 /opt/campushub/app.env

cat > /etc/systemd/system/campushub.service <<'EOF'
[Unit]
Description=CampusHub Backend
After=network-online.target
Wants=network-online.target
ConditionPathExists=/opt/campushub/app.jar

[Service]
User=campushub
Group=campushub
WorkingDirectory=/opt/campushub
EnvironmentFile=-/opt/campushub/app.env
ExecStart=/usr/bin/java -jar /opt/campushub/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable campushub

# --- Frontend + reverse proxy ---
mkdir -p /var/www/campushub/releases

cat > /etc/nginx/conf.d/campushub.conf <<'EOF'
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    root /var/www/campushub/current;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /assets/ {
        add_header Cache-Control "public, max-age=31536000, immutable";
        try_files $uri =404;
    }

    location = /index.html {
        add_header Cache-Control "no-cache";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF
nginx -t
systemctl enable --now nginx
