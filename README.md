# P2P Lending Platform

Nền tảng cho vay ngang hàng (P2P Lending) dạng microservices — Java 21 + Spring Boot 3.3.5.

## Structure

```text
p2p-lending/
  apps/
    api/                  # Spring Boot microservices
      auth-service/         # Đăng ký/đăng nhập, OTP, JWT RS256, eKYC
      loan-service/         # Tạo khoản vay, offer, vòng đời khoản vay
      matching-service/     # Thuật toán ghép vay
      cms-service/          # Customer Manager Service — admin portal
      notification-service/ # Gửi email/SMS qua Kafka
    cms/                  # Web CMS/admin app
    mobile/               # Reserved

  infra/                  # SQL init scripts
  nginx/                  # Reverse proxy config
```

## Yêu cầu

- Docker & Docker Compose
- Java 21 + Maven 3.9 (chỉ cần nếu build thủ công)

## Chạy Local

### 1. Clone và chuẩn bị env

```bash
git clone <repo-url>
cd p2p-lending
cp .env.example .env
```

File `.env` mặc định đã đủ để chạy local — không cần sửa gì thêm.

### 2. Khởi động toàn bộ hệ thống

```bash
docker compose up -d
```

Lần đầu sẽ build image và pull dependencies, mất vài phút. Flyway tự động chạy migration và **seed data** khi service khởi động (profile `dev`).

### 3. Kiểm tra service đã sẵn sàng

```bash
docker compose ps
docker compose logs -f auth-service   # Xem log auth-service
```

Chờ đến khi thấy `Started AuthServiceApplication` là đã sẵn sàng.

### 4. Test API qua Nginx (port 7080)

```bash
# Check phone
curl -s -X POST http://localhost:7080/api/auth/check-phone \
  -H "Content-Type: application/json" \
  -d '{"phone":"0901111111"}'

# Login với tài khoản seed
curl -s -X POST http://localhost:7080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phone":"0901111111","password":"Test@1234"}' | jq .
```

---

## Tài khoản seed (dev / test server)

Tất cả tài khoản có mật khẩu: **`Test@1234`**

| SĐT | KYC | Ghi chú |
|-----|-----|---------|
| `0901111111` | APPROVED | Borrower 1 |
| `0902222222` | APPROVED | Borrower 2 |
| `0903333333` | PENDING | Investor 1 |
| `0904444444` | NONE | Investor 2 |
| `0905555555` | APPROVED | Admin (role ADMIN) |

Seed data còn bao gồm: 3 loan requests, 3 loan offers, 2 investor preferences, các notification templates.

---

## OTP Mock

Ở môi trường `dev` và `test` server, OTP cố định là **`000000`** cho mọi luồng:
- Đăng ký (`/api/auth/register/verify`)
- eKYC (`/api/auth/kyc/verify`)
- Quên mật khẩu (`/api/auth/forgot-password/verify-otp`)
- Đổi mật khẩu (`/api/auth/change-password/verify`)

---

## Ports

| Service | Host Port | Ghi chú |
|---------|-----------|---------|
| Nginx (API gateway) | **7080** | Dùng cổng này để gọi API |
| auth-service | 8084 | Direct (debug) |
| loan-service | 8082 | Direct (debug) |
| matching-service | 8083 | Direct (debug) |
| cms-service | 8090 | Direct (debug) |
| notification-service | 8085 | Direct (debug) |
| MySQL | 3306 | — |
| Redis | 6379 | — |
| Kafka | 9092 | — |
| Kafka UI | 8989 | http://localhost:8989 |

---

## Spring Profiles

| Profile | Dùng ở đâu | Seed data | OTP mock |
|---------|-----------|-----------|----------|
| `dev` | Local (mặc định) | ✅ | ✅ |
| `test` | Test server | ✅ | ✅ (qua `APP_OTP_MOCK=true`) |
| `prod` | Live server | ❌ | ❌ |

---

## Lệnh thường dùng

```bash
# Rebuild một service sau khi thay đổi code
docker compose build auth-service && docker compose up -d auth-service

# Xem log
docker compose logs -f auth-service

# Restart một service
docker compose restart auth-service

# Dừng tất cả
docker compose down

# Dừng và xóa data
docker compose down -v
```

---

## CI/CD

Push lên branch `main` → GitHub Actions tự động phát hiện service nào thay đổi và deploy lên **test server** (`42.113.122.119`, port 7080).

Deploy thủ công toàn bộ: **Actions → CI/CD → Test Server → Run workflow → force_all ✅**
