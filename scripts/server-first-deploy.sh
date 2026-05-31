#!/bin/bash
# ──────────────────────────────────────────────────────────────────
# Chạy script này MỘT LẦN trên server test để thiết lập môi trường.
# SSH vào server rồi chạy: bash server-first-deploy.sh
# ──────────────────────────────────────────────────────────────────
set -e

GITHUB_USER="Lamgiaosu1805"
REPO_URL="https://github.com/Lamgiaosu1805/vnfite_v2_microservice.git"
APP_DIR="$HOME/p2p-lending"

echo "=== [1/6] Cài Docker nếu chưa có ==="
if ! command -v docker &>/dev/null; then
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$USER"
  echo "Docker đã cài. Cần logout/login lại hoặc chạy: newgrp docker"
else
  echo "Docker đã có: $(docker --version)"
fi

echo "=== [2/6] Clone repo ==="
if [ -d "$APP_DIR" ]; then
  echo "Thư mục $APP_DIR đã tồn tại, bỏ qua clone."
else
  git clone "$REPO_URL" "$APP_DIR"
fi
cd "$APP_DIR"

echo "=== [3/6] Tạo file .env ==="
if [ ! -f .env ]; then
  cp .env.example .env
  echo ""
  echo "  ⚠️  Hãy chỉnh sửa .env trước khi tiếp tục:"
  echo "       nano $APP_DIR/.env"
  echo "  Đặc biệt: RSA_PRIVATE_KEY, RSA_PUBLIC_KEY, SMTP_PASSWORD"
  echo ""
  read -rp "Nhấn Enter sau khi đã chỉnh .env để tiếp tục..."
else
  echo ".env đã có."
fi

echo "=== [4/6] Tạo SSH key để GitHub Actions deploy ==="
KEY_PATH="$HOME/.ssh/github_actions_deploy"
if [ ! -f "$KEY_PATH" ]; then
  ssh-keygen -t ed25519 -C "github-actions-deploy" -f "$KEY_PATH" -N ""
  cat "$KEY_PATH.pub" >> "$HOME/.ssh/authorized_keys"
  chmod 600 "$HOME/.ssh/authorized_keys"
  echo ""
  echo "  ✅ SSH key đã tạo. Copy PRIVATE KEY dưới đây vào GitHub Secret SSH_PRIVATE_KEY:"
  echo "────────────────────────────────────────────────────────"
  cat "$KEY_PATH"
  echo "────────────────────────────────────────────────────────"
else
  echo "SSH key đã có tại $KEY_PATH."
fi

echo "=== [5/6] Login GitHub Container Registry ==="
echo ""
echo "  Tạo GitHub PAT tại: https://github.com/settings/tokens/new"
echo "  Scope cần chọn: read:packages"
echo ""
read -rp "Nhập GitHub PAT (read:packages): " GHCR_PAT
echo "$GHCR_PAT" | docker login ghcr.io -u "$GITHUB_USER" --password-stdin
echo "Login ghcr.io thành công."

echo "=== [6/6] Khởi động infrastructure lần đầu ==="
docker compose up -d mysql redis zookeeper kafka kafka-ui
echo "Đợi infrastructure sẵn sàng (30s)..."
sleep 30

echo ""
echo "══════════════════════════════════════════════════════════"
echo " Server setup HOÀN TẤT!"
echo ""
echo " Các bước tiếp theo:"
echo " 1. Cài Portainer (GUI):"
echo "    docker volume create portainer_data"
echo "    docker run -d -p 9000:9000 --name portainer --restart=always \\"
echo "      -v /var/run/docker.sock:/var/run/docker.sock \\"
echo "      -v portainer_data:/data portainer/portainer-ce:latest"
echo ""
echo " 2. Thêm Secrets vào GitHub repo:"
echo "    SSH_HOST     = $(curl -s ifconfig.me 2>/dev/null || echo 'IP server')"
echo "    SSH_USER     = $USER"
echo "    SSH_PRIVATE_KEY = (nội dung key đã hiện ở bước 4)"
echo ""
echo " 3. Push code lên GitHub → pipeline tự chạy!"
echo "══════════════════════════════════════════════════════════"
