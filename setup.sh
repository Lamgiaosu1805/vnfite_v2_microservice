#!/usr/bin/env bash
# setup.sh — Khởi tạo môi trường local cho P2P Lending
# Chạy một lần sau khi clone: bash setup.sh

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${GREEN}[✓]${NC} $*"; }
warn()  { echo -e "${YELLOW}[!]${NC} $*"; }
error() { echo -e "${RED}[✗]${NC} $*"; exit 1; }

# ── 1. Kiểm tra công cụ cần thiết ──────────────────────────────
command -v openssl &>/dev/null || error "openssl chưa được cài. Cài bằng: brew install openssl (macOS) hoặc apt install openssl (Linux)"
command -v docker  &>/dev/null || error "Docker chưa được cài. Tải tại: https://docs.docker.com/get-docker/"

# ── 2. Tạo .env nếu chưa có ────────────────────────────────────
if [ -f .env ]; then
    warn ".env đã tồn tại — bỏ qua tạo mới. Xóa .env rồi chạy lại nếu muốn reset."
else
    cp .env.example .env
    info "Đã tạo .env từ .env.example"
fi

# ── 3. Generate RSA key pair ────────────────────────────────────
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

openssl genrsa -out "$TMP_DIR/private.pem" 2048 2>/dev/null
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
    -in "$TMP_DIR/private.pem" \
    -out "$TMP_DIR/private_pkcs8.pem" 2>/dev/null
openssl rsa -in "$TMP_DIR/private.pem" -pubout \
    -out "$TMP_DIR/public.pem" 2>/dev/null

PRIVATE_KEY=$(awk 'NF {sub(/\r/, ""); printf "%s\\n", $0}' "$TMP_DIR/private_pkcs8.pem")
PUBLIC_KEY=$(awk  'NF {sub(/\r/, ""); printf "%s\\n", $0}' "$TMP_DIR/public.pem")

# Thay placeholder trong .env (tương thích cả macOS sed lẫn Linux sed)
if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' "s|RSA_PRIVATE_KEY=REPLACE_WITH_BASE64_PKCS8_PRIVATE_KEY|RSA_PRIVATE_KEY=${PRIVATE_KEY}|" .env
    sed -i '' "s|RSA_PUBLIC_KEY=REPLACE_WITH_BASE64_X509_PUBLIC_KEY|RSA_PUBLIC_KEY=${PUBLIC_KEY}|"   .env
else
    sed -i  "s|RSA_PRIVATE_KEY=REPLACE_WITH_BASE64_PKCS8_PRIVATE_KEY|RSA_PRIVATE_KEY=${PRIVATE_KEY}|" .env
    sed -i  "s|RSA_PUBLIC_KEY=REPLACE_WITH_BASE64_X509_PUBLIC_KEY|RSA_PUBLIC_KEY=${PUBLIC_KEY}|"   .env
fi

info "Đã generate RSA key pair và ghi vào .env"

# ── 4. Kiểm tra .env hoàn chỉnh ────────────────────────────────
if grep -q "REPLACE_WITH" .env; then
    warn "Vẫn còn placeholder chưa được điền trong .env — kiểm tra lại trước khi chạy."
else
    info ".env đã đầy đủ"
fi

# ── 5. Hướng dẫn bước tiếp theo ────────────────────────────────
echo ""
echo "═══════════════════════════════════════════"
echo "  Bước tiếp theo:"
echo ""
echo "  1. (Tuỳ chọn) Mở .env, điền SMTP nếu"
echo "     cần notification-service gửi email."
echo ""
echo "  2. Build & chạy toàn bộ hệ thống:"
echo "     docker-compose up -d --build"
echo ""
echo "  3. Xem logs:"
echo "     docker-compose logs -f auth-service"
echo "═══════════════════════════════════════════"
