# P2P Lending Platform — CLAUDE.md

## Project Overview

Nền tảng cho vay ngang hàng (P2P Lending) dạng microservices, xây dựng bằng Java 21 + Spring Boot 3.3.5. Gồm 5 service độc lập giao tiếp qua Kafka, dùng JWT RS256 cho xác thực, MySQL cho persistence, Redis cho caching.

**Tên thương hiệu:** Luôn viết là **VNFITE** (toàn bộ chữ hoa) — không dùng "VnFite", "Vnfite", hay bất kỳ cách viết khác. Áp dụng cho mọi nơi: UI, tài liệu, commit message, comment code.

**Quy tắc commit & push:** Không tự ý chạy `git commit` hay `git push`. Sau khi hoàn thành thay đổi, chỉ được **gen câu lệnh** để người dùng tự chạy. Commit message ngắn gọn, 1 dòng, tiếng Anh hoặc tiếng Việt đều được.

**Viết tắt trong dự án:** CMS = **Customer Manager Service** (không phải Content Management System). Khi đọc hoặc viết bất kỳ chỗ nào có chữ "CMS" trong project này đều hiểu là Customer Manager Service.

**CMS account model:** User đăng nhập CMS là `cms_admin_users` riêng trong `cms_db`, tách biệt hoàn toàn với customer/user trong `auth_db.users`. Không dùng tài khoản customer để đăng nhập CMS.

**Thuật ngữ UI:** Dùng **"người gọi vốn"** và **"nhà đầu tư"** — không dùng "người vay" hay "cho vay". Áp dụng cho mọi label, tiêu đề, nội dung trên cả CMS web và mobile app.

**CMS Dark Mode:** `VnFiteCMS` đã có dark mode. Toggle bằng nút mặt trăng/mặt trời ở header, lưu vào `localStorage` key `cms_theme`. Cơ chế: toggle class `dark` trên `document.documentElement` → Tailwind `dark:` variant. Khi thiết kế hoặc sửa bất kỳ component CMS nào, **bắt buộc thêm `dark:` classes** cho mọi màu nền, chữ, border, input, modal.

**Không mirror user/loan vào CMS:** `cms-service` không còn đồng bộ `auth_db.users` hoặc `loan_db.loan_requests` sang DB riêng. `cms_db` chỉ lưu dữ liệu nội bộ CMS như admin users, quyền, cấu hình, audit/ops data. Khi CMS cần dữ liệu khách hàng hoặc khoản gọi vốn, hãy gọi API từ service nguồn (`auth-service`, `loan-service`) thay vì copy bảng.

**Mobile OTP UI:** Tất cả màn nhập OTP trên app mobile VNFITE phải dùng cùng một kiểu giao diện: mỗi số OTP là một ô riêng. Không dùng một ô input dài nhập liền toàn bộ OTP. Ưu tiên component/pattern OTP dùng chung nếu có. Áp dụng cho đăng ký, quên mật khẩu, eKYC, bật/tắt sinh trắc học, reset thiết bị và mọi luồng OTP mới. Input OTP phải numeric-only, fixed length, đồng nhất về khoảng cách/kích thước/trạng thái lỗi. OTP phải được scope theo tính năng/purpose, không dùng OTP của tính năng này để xác thực tính năng khác.

**Error handling:** Không che lỗi nghiệp vụ/validation/service nguồn/schema DB bằng câu chung `Internal server error`. Khi service gọi service khác, phải giữ status code của service nguồn và bóc message theo thứ tự `details[]` → `message` → `detail` → `error`. Frontend phải đọc được cả single-message và `details[]`, rồi hiển thị lỗi cụ thể cho người dùng/admin. Backend vẫn phải log exception đầy đủ bằng `@Slf4j` để trace server.

**Seed/data safety:** Không bao giờ `TRUNCATE`, `DELETE`, drop, recreate, hoặc reset dữ liệu nghiệp vụ/người dùng trong seed, migration, deploy script, local script, hoặc SQL thủ công trừ khi người dùng yêu cầu reset phá dữ liệu rõ ràng và xác nhận đã có backup. Các bảng được bảo vệ gồm `loan_requests`, `loan_offers`, `users`, KYC, payment, transaction, audit, notification, customer/admin operational records. Seed chỉ được insert/update dữ liệu cấu hình, phải idempotent và không phá dữ liệu, ưu tiên `INSERT ... ON DUPLICATE KEY UPDATE`.

**Redis namespace:** Test/UAT và live đang dùng chung một Redis server, nên mọi Redis key bắt buộc phải có namespace theo môi trường. Ưu tiên dùng `APP_REDIS_NAMESPACE` làm root namespace; nếu chưa set thì fallback theo `SPRING_PROFILES_ACTIVE`, rồi ghép thêm service name trong code, ví dụ `uat:auth-service:*`, `uat:loan-service:*`, `prod:cms-service:*`. Áp dụng cho cả Spring cache lẫn các key Redis viết tay như OTP, refresh token, session thiết bị, rate limit, pending KYC, pending loan. Script reset cache chỉ được xóa theo namespaced pattern, tuyệt đối không scan/xóa pattern trần dễ đụng môi trường khác.

**Luồng duyệt khoản gọi vốn:** Ban lãnh đạo phê duyệt xong **không được đưa khoản gọi vốn lên sàn ngay**. Backend phải chuyển khoản sang `AWAITING_BORROWER_APPROVAL` và thông báo cho người gọi vốn số tiền được phê duyệt, lãi suất, kỳ hạn. Chỉ khi người gọi vốn xác nhận điều kiện qua `POST /api/loans/{id}/confirm`, khoản mới chuyển `ACTIVE` và hiển thị trên sàn cho nhà đầu tư. Không đổi CMS approval thành active trực tiếp trừ khi người dùng yêu cầu đổi nghiệp vụ rõ ràng.

## Architecture

```
Client → Nginx (port 80) → [auth-service | loan-service | matching-service | notification-service]
                                          ↑                       ↑
                                     Kafka (9092)            Redis (6379)
                                          ↑
                                     MySQL (3306)
Admin → cms.p2plending.local → cms-service (8090)
```

### Services

| Service | Host Port | Container Port | Database | Mô tả |
|---------|-----------|----------------|----------|-------|
| auth-service | 8084      | 8081 | auth_db | Đăng ký/đăng nhập bằng SĐT, OTP, JWT RS256, KYC |
| loan-service | 8082      | 8082 | loan_db | Tạo khoản vay, quản lý offer, vòng đời khoản vay |
| matching-service | 8083      | 8083 | matching_db | Thuật toán ghép vay, lịch chạy 30 phút/lần |
| cms-service | 8090      | 8090 | cms_db | CMS admin/auth service |
| notification-service | 8085      | 8084 | notification_db | Gửi email/SMS qua Kafka consumer + push notification qua service.vnfite.com.vn |

### Infrastructure

| Component | Port | Ghi chú |
|-----------|------|---------|
| MySQL 8.0 | 3306 | 6 database riêng biệt |
| Redis 7 | 6379 | Cache + session |
| Kafka | 9092 | Confluent 7.5.0 |
| Zookeeper | 2181 | Kafka coordination |
| Kafka UI | 8989 | Web monitoring |
| Nginx | 7080 | Reverse proxy (host port 7080 → container 80) |

## Build & Run

```bash
# Khởi động toàn bộ hệ thống (lần đầu build + seed data tự động)
docker compose up -d

# Rebuild một service sau khi thay đổi code
docker compose build auth-service && docker compose up -d auth-service

# Build JAR thủ công (bỏ qua tests)
cd apps/api/auth-service && mvn clean package -DskipTests

# Xem logs
docker compose logs -f auth-service

# Dừng tất cả
docker compose down

# Dừng và xóa toàn bộ data (reset DB)
docker compose down -v
```

## Spring Profiles

| Profile | Dùng ở đâu | Seed data | OTP mock | Ghi chú |
|---------|-----------|-----------|----------|---------|
| `dev` | Local (mặc định) | ✅ | ✅ `000000` | Flyway load `db/migration` + `db/seed` |
| `test` | Test server | ✅ | ✅ `000000` (qua `APP_OTP_MOCK=true` env) | Flyway load `db/migration` + `db/seed` |
| `prod` | Live server | ❌ | ❌ | Flyway chỉ load `db/migration` |

Profile được set qua `SPRING_PROFILES_ACTIVE` env var. Docker Compose mặc định dùng `dev`.

### Seed Data (dev / test)

Flyway Repeatable migration (`R__dev_seed_data.sql`) có thể chạy tự động ở dev/test. Seed phải là data-preserving: chỉ insert/update dữ liệu cấu hình, tuyệt đối không truncate/reset dữ liệu nghiệp vụ hoặc user data.

**Quy tắc bắt buộc cho seed data:**
- **ID phải là UUID v4 random thật** — ví dụ `23657e3f-4271-46d7-b920-923d101f0519`. **Tuyệt đối không dùng pattern ID** kiểu `d1000001-0000-0000-0000-000000000001`.
- Dùng `python3 -c "import uuid; print(uuid.uuid4())"` để generate UUID khi cần thêm seed.
- Seed chỉ chứa **data cấu hình** (loan_products, notification_templates, cms_admin_users). Không seed user data — user tự đăng ký qua app.
- Không dùng `TRUNCATE`, `DELETE`, drop/recreate bảng, hoặc reset sequence trên bảng nghiệp vụ/người dùng trong seed.

**Tài khoản CMS admin** (password: `Admin@1234`):

| Username | Role | Ghi chú |
|----------|------|---------|
| `admin` | ADMIN | Toàn quyền |
| `ops` | OPS | Vận hành |

Không có tài khoản user mẫu — đăng ký qua app.

## CI/CD

GitHub Actions (`.github/workflows/deploy-test.yml`):
- Trigger: push lên `main` → phát hiện service nào thay đổi qua `git diff`, chỉ deploy service đó
- Deploy thủ công toàn bộ: **Actions → Run workflow → force_all ✅**
- Mỗi deploy: `git pull` → `docker compose build <svc>` → `docker compose up -d <svc>` → `docker image prune`
- Nginx config thay đổi: `docker compose restart nginx`
- `.env` luôn được sync từ GitHub Secret `ENV_FILE_TEST` trước mỗi deploy

**Test server:** `42.113.122.119`, API qua `http://42.113.122.119:7080`

### ⚠️ Kiến trúc MySQL trên Test Server

MySQL **KHÔNG chạy trong Docker** — chạy **native trên host**. Docker MySQL container trong `docker-compose.yml` chỉ dùng cho local dev, **không expose port ra host** (xem comment trong compose file).

- Các service kết nối qua `host.docker.internal:3306` → native MySQL trên server
- `docker compose exec mysql` → sai, đây là Docker MySQL không có data
- Để thao tác DB trực tiếp trên test server, dùng lệnh sau (SSH vào `42.113.122.119`):

```bash
source /root/p2p-lending/.env && mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -h 127.0.0.1 <database> -e "<SQL>"
```

Ví dụ xóa CMS admin để setup lại:
```bash
source /root/p2p-lending/.env && mysql -u"${DB_USERNAME}" -p"${DB_PASSWORD}" -h 127.0.0.1 cms_db -e "TRUNCATE TABLE cms_admin_users;"
```

## Registration Flow

Luồng đăng ký bằng số điện thoại, 3 bước:

```
1. POST /api/auth/check-phone   → kiểm tra SĐT chưa tồn tại
2. POST /api/auth/register      → hash password, lưu PendingRegistration vào Redis, gửi OTP
3. POST /api/auth/register/verify → xác thực OTP, tạo User, phát Kafka event, trả JWT
```

### Dữ liệu tại thời điểm đăng ký

| Field | Giá trị | Ghi chú |
|-------|---------|---------|
| `phone` | từ request | NOT NULL, dùng để đăng nhập |
| `password` | bcrypt hash | NOT NULL |
| `role` | `USER` | mặc định — user có thể vừa vay vừa đầu tư |
| `kyc_status` | `NONE` | chưa nộp KYC |
| `referred_by` | SĐT người giới thiệu hoặc null | optional |
| `email` | **null** | user tự cập nhật sau |
| `full_name` | **null** | user tự cập nhật sau |

### OTP & Redis

- Key trong Redis: `pending_reg:{phone}`
- Giá trị: JSON của `PendingRegistration` (phone, hashedPassword, referrerPhone, otp)
- TTL: 5 phút
- Mock mode (`app.otp.mock=true`): OTP cố định là `000000`
- Sau khi verify thành công → xóa key khỏi Redis (consume một lần)

### Kafka event sau đăng ký

Topic `user.registered` payload:
```json
{ "userId": "uuid", "phone": "0912345678", "fullName": null, "role": "USER", "registeredAt": "..." }
```
CMS service không mirror event này. Thông tin user là source of truth ở `auth-service`.

### Token

- Access token: JWT RS256, TTL 15 phút
- Refresh token: lưu DB (`refresh_tokens`), TTL 24 giờ

---

## eKYC Flow

Luồng định danh điện tử 2 bước, yêu cầu đăng nhập (JWT):

```
1. POST /api/auth/kyc/init
   Body: cccdNumber, fullName, dateOfBirth, permanentAddress, hometown,
         issueDate, issuingAuthority, expiryDate (nullable),
         frontImage (base64), backImage (base64), portraitImage (base64)
   → Kiểm tra cccdNumber chưa tồn tại (409 nếu trùng)
   → Upload 3 ảnh qua ImageStorageService → nhận về 3 imageId (string)
   → Tạo OTP 6 chữ số, lưu PendingKycData vào Redis key `kyc_pending:{userId}`, TTL 10 phút
   → Trả về { "message": "OTP đã được gửi..." } (mock mode: cũng trả "otp" field)

2. POST /api/auth/kyc/verify
   Body: otp (6 chữ số)
   → Đọc PendingKycData từ Redis, xác thực OTP
   → Xóa key Redis
   → Insert `kyc_submissions` với status = PENDING
   → Cập nhật `users.kyc_status` = PENDING
   → Publish Kafka event `kyc.submitted` { userId, documentId (submission UUID), submittedAt }
   → Trả về KycSubmissionResponse
```

### ImageStorageService

- Interface `ImageStorageService.upload(imageData: base64, filename)` → trả về imageId string
- Mock (`@Profile("!prod")`): trả về `mock_<UUID>` không có gạch ngang
- Production: implement để gọi service lưu ảnh thật, trả về ID từ hệ thống đó

### Redis keys cho eKYC

- Key: `kyc_pending:{userId}`
- Value: JSON của `PendingKycData` (tất cả field CCCD + 3 imageId + otp)
- TTL: 10 phút
- Mock OTP: `000000` khi `app.otp.mock=true`

---

## Domain Model

### User & Auth

- `users`: phone (login), password (bcrypt), role, kyc_status, referral_code, referred_by, email (nullable), full_name (nullable)
- `kyc_documents`: user_id, doc_type, doc_url, status
- `refresh_tokens`: user_id, token (unique), expires_at
- Access token TTL: 15 phút | Refresh token TTL: 24 giờ

### Loan Lifecycle

```
PENDING → ACTIVE → FUNDED → REPAYING → COMPLETED
                                     → DEFAULTED
```

- `loan_requests`: borrower_id, amount, interest_rate, term_months, purpose, status, funded_amount
- `loan_offers`: loan_request_id, investor_id, amount, status (PENDING/ACCEPTED/REJECTED/CANCELLED)

### Matching

- Score từ 0.000 đến 1.000 dựa trên amount, interest_rate, term_months
- Ngưỡng tối thiểu: `matching.min-score-threshold: 0.60` (configurable)
- Tối đa 10 matches/khoản vay
- `investor_preferences`: min/max_investment_amount, min/max_interest_rate, min/max_term_months, active
- `match_records`: loan_id, investor_id, score, status

---

## Database Schema Notes

### auth_db

#### users
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | VARCHAR(36) | NO | PK UUID, `@GeneratedValue(strategy = GenerationType.UUID)` |
| `phone` | VARCHAR(20) | NO | SĐT đăng nhập, unique, format Việt Nam |
| `password` | VARCHAR(255) | NO | Bcrypt hash |
| `full_name` | VARCHAR(100) | YES | Null khi mới đăng ký, điền sau khi KYC |
| `email` | VARCHAR(150) | YES | Null khi mới đăng ký, user cập nhật sau |
| `role` | ENUM | NO | `USER` (mặc định, vừa vay vừa đầu tư được) \| `ADMIN` |
| `kyc_status` | ENUM | NO | `NONE` \| `PENDING` \| `APPROVED` \| `REJECTED` |
| `referred_by` | VARCHAR(20) | YES | SĐT người giới thiệu (SĐT chính là mã giới thiệu) |
| `is_deleted` | TINYINT(1) | NO | Soft delete, default 0 |
| `created_at` | DATETIME | YES | Thời điểm tạo tài khoản |
| `updated_at` | DATETIME | YES | |

#### kyc_submissions
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | VARCHAR(36) | NO | PK UUID |
| `user_id` | VARCHAR(36) | NO | FK → users.id |
| `cccd_number` | VARCHAR(20) | NO | Số CCCD, unique |
| `full_name` | VARCHAR(100) | NO | Họ tên trên CCCD |
| `date_of_birth` | DATE | NO | Ngày sinh |
| `permanent_address` | VARCHAR(500) | NO | Địa chỉ thường trú |
| `hometown` | VARCHAR(255) | NO | Quê quán |
| `issue_date` | DATE | NO | Ngày cấp |
| `issuing_authority` | VARCHAR(255) | NO | Nơi cấp |
| `expiry_date` | DATE | YES | Ngày hết hạn — null = không thời hạn |
| `front_image_id` | VARCHAR(255) | NO | ID ảnh mặt trước từ hệ thống lưu ảnh ngoài |
| `back_image_id` | VARCHAR(255) | NO | ID ảnh mặt sau |
| `portrait_image_id` | VARCHAR(255) | NO | ID ảnh chân dung |
| `status` | ENUM | NO | `PENDING` \| `APPROVED` \| `REJECTED`, default PENDING |
| `is_deleted` | TINYINT(1) | NO | Soft delete, default 0 |
| `created_at` | DATETIME | YES | |
| `updated_at` | DATETIME | YES | |

#### user_fcm_tokens

Lưu FCM token của thiết bị đang đăng nhập — 1 user tối đa 1 thiết bị active tại một thời điểm.

| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `user_id` | VARCHAR(36) | NO | PK = users.id, 1 user 1 dòng |
| `fcm_token` | TEXT | NO | Firebase Cloud Messaging token |
| `device_key` | VARCHAR(100) | YES | Unique device identifier từ mobile |
| `updated_at` | DATETIME | NO | Tự cập nhật khi upsert |

**Quy tắc:**
- Khi user mới đăng nhập trên thiết bị, nếu `fcm_token` đó đã thuộc user khác → xóa record cũ trước (account switching on same device)
- Khi logout → xóa record của user đó
- `notification-service` gọi `GET /internal/users/{userId}/fcm-token` để lấy token trước khi push

#### refresh_tokens
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | VARCHAR(36) | NO | PK UUID |
| `user_id` | VARCHAR(36) | NO | FK → users.id |
| `token` | VARCHAR(500) | NO | JWT refresh token, unique |
| `expires_at` | DATETIME | NO | Hết hạn sau 24h từ lúc login |
| `created_at` | DATETIME | YES | |

---

### loan_db

#### loan_requests
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | BIGINT | NO | PK tự tăng |
| `borrower_id` | BIGINT | NO | FK → auth_db.users.id (người vay) |
| `amount` | DECIMAL(15,2) | NO | Số tiền cần vay (VND) |
| `interest_rate` | DECIMAL(5,2) | NO | Lãi suất năm (%), vd: 12.50 = 12.5%/năm |
| `term_months` | INT | NO | Thời hạn vay (số tháng) |
| `purpose` | VARCHAR(500) | NO | Mục đích vay vốn |
| `status` | ENUM | NO | `PENDING` chờ duyệt \| `ACTIVE` nhận offer \| `FUNDED` đủ vốn \| `REPAYING` đang trả \| `COMPLETED` xong \| `DEFAULTED` mất khả năng trả |
| `funded_amount` | DECIMAL(15,2) | NO | Tổng tiền đã được offer ACCEPTED, tăng dần đến khi = amount |
| `created_at` | DATETIME | YES | |
| `updated_at` | DATETIME | YES | |

#### loan_offers
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | BIGINT | NO | PK tự tăng |
| `loan_request_id` | BIGINT | NO | FK → loan_requests.id |
| `investor_id` | BIGINT | NO | FK → auth_db.users.id (nhà đầu tư) |
| `amount` | DECIMAL(15,2) | NO | Số tiền nhà đầu tư muốn đặt |
| `status` | ENUM | NO | `PENDING` \| `ACCEPTED` (default khi tạo) \| `REJECTED` \| `CANCELLED` |
| `created_at` | DATETIME | YES | |
| `updated_at` | DATETIME | YES | |

---

### cms_db

`cms_db` chỉ lưu dữ liệu nội bộ CMS. Không mirror user/loan từ service khác.

#### cms_admin_users
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | VARCHAR(36) | NO | PK UUID |
| `username` | VARCHAR(60) | NO | Tên đăng nhập CMS, unique |
| `email` | VARCHAR(150) | NO | Email admin, unique |
| `full_name` | VARCHAR(100) | NO | Họ tên admin |
| `password` | VARCHAR(255) | NO | Bcrypt hash |
| `must_change_password` | TINYINT(1) | NO | Bắt đổi mật khẩu khi đăng nhập lần đầu |
| `created_by` | VARCHAR(36) | YES | Admin tạo tài khoản |
| `totp_secret` | VARCHAR(64) | YES | Secret 2FA |
| `totp_enabled` | TINYINT(1) | NO | Bật/tắt 2FA |
| `role` | VARCHAR(20) | NO | `SUPER_ADMIN` \| `ADMIN` \| `OPS` |
| `active` | TINYINT(1) | NO | Trạng thái tài khoản |
| `is_deleted` | TINYINT(1) | NO | Soft delete |
| `created_at` | DATETIME | YES | |
| `updated_at` | DATETIME | YES | |

---

### matching_db

#### match_records
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | BIGINT | NO | PK tự tăng |
| `loan_id` | BIGINT | NO | FK → loan_db.loan_requests.id |
| `investor_id` | BIGINT | NO | FK → auth_db.users.id |
| `score` | DECIMAL(4,3) | NO | Điểm phù hợp 0.000–1.000, ngưỡng tối thiểu 0.60 |
| `status` | ENUM | NO | `PENDING` tìm thấy \| `NOTIFIED` đã gửi event \| `ACCEPTED` \| `REJECTED` \| `EXPIRED` vay đã fund trước khi investor xử lý |
| `created_at` | DATETIME | YES | |
| `updated_at` | DATETIME | YES | |

Unique constraint: `(loan_id, investor_id)` — mỗi cặp vay-investor chỉ có 1 match record.

#### investor_preferences
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `id` | BIGINT | NO | PK tự tăng |
| `investor_id` | BIGINT | NO | FK → auth_db.users.id |
| `min_investment_amount` | DECIMAL(15,2) | NO | Số tiền tối thiểu sẵn sàng đầu tư (VND) |
| `max_investment_amount` | DECIMAL(15,2) | NO | Số tiền tối đa sẵn sàng đầu tư (VND) |
| `min_interest_rate` | DECIMAL(5,2) | NO | Lãi suất tối thiểu kỳ vọng (%) |
| `max_interest_rate` | DECIMAL(5,2) | YES | Lãi suất tối đa — null = không giới hạn trên |
| `min_term_months` | INT | NO | Thời hạn tối thiểu chấp nhận (tháng) |
| `max_term_months` | INT | NO | Thời hạn tối đa chấp nhận (tháng) |
| `active` | TINYINT(1) | NO | 1 = đang áp dụng, 0 = đã vô hiệu hóa |
| `created_at` | DATETIME | YES | |
| `updated_at` | DATETIME | YES | |

#### pending_loans
| Column | Type | Nullable | Mô tả |
|--------|------|----------|-------|
| `loan_id` | BIGINT | NO | PK = loan_requests.id, không auto-increment |
| `borrower_id` | BIGINT | NO | Snapshot từ event `loan.created` |
| `amount` | DECIMAL(15,2) | NO | Snapshot từ event `loan.created` |
| `interest_rate` | DECIMAL(5,2) | NO | |
| `term_months` | INT | NO | |
| `purpose` | VARCHAR(500) | YES | |
| `fully_funded` | TINYINT(1) | NO | true khi nhận event `loan.funded` → scheduler bỏ qua |
| `received_at` | DATETIME | YES | Thời điểm nhận event `loan.created` |
| `last_matched_at` | DATETIME | YES | Lần chạy matching gần nhất — scheduler dùng để quyết định re-match |

## API Endpoints

### Auth Service (`/api/auth`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| POST | `/api/auth/check-phone` | No | Kiểm tra SĐT còn trống |
| POST | `/api/auth/register` | No | Bắt đầu đăng ký, gửi OTP |
| POST | `/api/auth/register/verify` | No | Xác thực OTP, tạo tài khoản |
| POST | `/api/auth/login` | No | Đăng nhập SĐT + mật khẩu |
| POST | `/api/auth/refresh` | No | Làm mới access token |
| POST | `/api/auth/kyc/init` | JWT | Bước 1 eKYC: upload ảnh, gửi OTP |
| POST | `/api/auth/kyc/verify` | JWT | Bước 2 eKYC: xác thực OTP, lưu submission |
| POST | `/api/auth/forgot-password/check` | No | Bước 0 quên MK: kiểm tra phone → `{ requiresCccd: boolean }` để frontend render đúng form |
| POST | `/api/auth/forgot-password` | No | Bước 1 quên MK: xác minh danh tính, gửi OTP (thêm cccdNumber nếu đã eKYC) |
| POST | `/api/auth/forgot-password/verify-otp` | No | Bước 2 quên MK: xác thực OTP → trả `{ resetToken }` (TTL 10 phút), chuyển sang màn nhập MK mới |
| POST | `/api/auth/forgot-password/reset` | No | Bước 3 quên MK: dùng resetToken + newPassword → đặt lại MK, vô hiệu hoá phiên cũ |
| POST | `/api/auth/change-password/init` | JWT | Bước 1 đổi MK: xác minh mật khẩu hiện tại → gửi OTP |
| POST | `/api/auth/change-password/verify` | JWT | Bước 2 đổi MK: xác thực OTP → đặt MK mới, vô hiệu hoá phiên cũ |
| POST | `/api/auth/devices/fcm-token` | JWT | Đăng ký FCM token của thiết bị để nhận push notification |

### Loan Service (`/api/loans`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| POST | `/api/loans/request` | JWT (BORROWER) | Tạo khoản vay mới |
| GET | `/api/loans` | JWT | Danh sách khoản vay (cache 5 phút) |
| GET | `/api/loans/{id}` | JWT | Chi tiết khoản vay + offers (cache 10 phút) |
| POST | `/api/loans/{id}/offer` | JWT (INVESTOR) | Đặt offer đầu tư |
| PUT | `/api/loans/{id}/status` | JWT (ADMIN) | Cập nhật trạng thái |

### Matching Service (`/api/matches`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| GET | `/api/matches/{loanId}` | JWT | Danh sách matches theo score |
| POST | `/api/matches/preferences` | Header: X-Investor-Id | Cập nhật preferences nhà đầu tư |

### CMS Service (`/cms`)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| GET | `/cms/dashboard/stats` | JWT (ADMIN/OPS) | Thống kê tổng quan |
| GET | `/cms/dashboard/chart` | JWT (ADMIN/OPS) | Dữ liệu biểu đồ |

### Notification Service

Không có REST endpoint — chỉ nhận event từ Kafka.

## Kafka Topics & Event Flow

| Topic | Producer | Consumer | Mô tả |
|-------|----------|----------|-------|
| `user.registered` | auth-service | (future analytics/notification consumers) | Không consume vào cms-service để mirror user |
| `kyc.submitted` | auth-service | (future analytics/notification consumers) | Không consume vào cms-service để mirror KYC |
| `loan.created` | loan-service | matching-service | Trigger matching |
| `loan.funded` | loan-service | matching-service | Đánh dấu fully_funded trong matching-service |
| `match.found` | matching-service | notification-service | Thông báo cho investor |
| `payment.completed` | (future) | loan-service | Cập nhật trạng thái trả nợ |

**Push Notification flow (ngoài Kafka):**
`notification-service` gọi thẳng HTTP đến `service.vnfite.com.vn` để push FCM:
1. Lấy FCM token: `GET /internal/users/{userId}/fcm-token` → auth-service (kèm header `X-Internal-Secret`)
2. Push: `POST https://service.vnfite.com.vn/push-notification/v2/notification/pushNotification` với `{ alias, token, title, body, data }`

Consumer Group IDs: `{service-name}-group` (vd: `loan-service-group`)

## Security

### JWT RS256

- auth-service giữ **private key** để ký token
- Tất cả service khác dùng **public key** để validate
- Keys truyền qua env vars dạng **base64 DER content** (không có PEM headers, không có newline):
  - `RSA_PRIVATE_KEY`: PKCS8 format (auth-service only)
  - `RSA_PUBLIC_KEY`: X509 format (tất cả services)
- Nếu không set key → auth-service tự sinh **ephemeral key** khi startup (token không survive restart, chỉ dùng cho local dev nhanh)
- Generate key pair:
  ```bash
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
  openssl pkey -in private.pem -pubout -out public.pem
  # Lấy giá trị để paste vào .env:
  grep -v "^-----" private.pem | tr -d '\n'   # RSA_PRIVATE_KEY
  grep -v "^-----" public.pem  | tr -d '\n'   # RSA_PUBLIC_KEY
  ```

### Authorization

- Role-based: USER (người dùng thường, có thể vừa vay vừa đầu tư), ADMIN (quản trị)
- Service-to-service: không có mTLS, dùng JWT forward
- matching-service dùng header `X-Investor-Id` thay vì JWT cho preferences endpoint

## Environment Variables

Xem file `.env.example`. Các biến quan trọng:

```
# Database
MYSQL_HOST, MYSQL_PORT, DB_USERNAME, DB_PASSWORD
AUTH_DB, LOAN_DB, CMS_DB, MATCHING_DB, PAYMENT_DB, NOTIFICATION_DB

# Cache
REDIS_HOST, REDIS_PORT, REDIS_PASSWORD

# Messaging
KAFKA_BOOTSTRAP_SERVERS

# Security
RSA_PRIVATE_KEY   # Base64 PKCS8 PEM — chỉ auth-service
RSA_PUBLIC_KEY    # Base64 X509 PEM — tất cả services

# Email
MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD

# Push Notification (notification-service → service.vnfite.com.vn)
PUSH_NOTIFICATION_URL   # https://service.vnfite.com.vn/push-notification/v2/notification
PUSH_NOTIFICATION_ALIAS # vnfite
```

## Tech Stack

- **Java**: 21 (Eclipse Temurin)
- **Framework**: Spring Boot 3.3.5
- **Build**: Maven 3.9.6 (parent POM ở root)
- **ORM**: Spring Data JPA + Hibernate
- **Security**: Spring Security + JJWT 0.12.6
- **Cache**: Spring Cache + Redis (Spring Data Redis)
- **Messaging**: Spring Kafka
- **DTO Mapping**: MapStruct 1.5.5
- **Boilerplate**: Lombok 1.18.38
- **Monitoring**: Spring Boot Actuator

## Nginx Routing

```
/api/auth/      → auth-service:8081
/api/loans/     → loan-service:8082
/api/matching/  → matching-service:8083
/api/notification/ → notification-service:8084
cms.p2plending.local → cms-service:8090
```

## Code Conventions

- **HTTP Client (frontend):** Luôn dùng **axios** để call API — không dùng `fetch` hay `XMLHttpRequest` trực tiếp. Cấu hình interceptor (auth header, error handling) tập trung ở một file duy nhất (`axiosClient` / `axiosInstance`).

- **DTO pattern**: Request/Response DTOs tách biệt entity, mapping qua MapStruct
- **Controller → Service → Repository** layering nghiêm ngặt

### ⚠️ Response format khác nhau giữa auth-service và loan-service

`auth-service` có `ApiResponseAdvice` (`@RestControllerAdvice`) bọc **tất cả** response công khai vào envelope:
```json
{ "requestId": "...", "timestamp": "...", "status": 200, "message": "Success", "data": <payload> }
```

`loan-service` **không** có wrapper — trả raw JSON trực tiếp.

**Quy tắc đã áp dụng:**
- Các endpoint `/internal/**` được **exclude** khỏi `ApiResponseAdvice` trong auth-service (kiểm tra `request.getURI().getPath().startsWith("/internal")` trong `beforeBodyWrite`) → trả raw JSON giống loan-service.
- `cms-service` dùng `SourceServiceClient` gọi cả hai service qua `/internal/**` → parse cùng cách, không cần unwrap.
- Khi thêm service mới hoặc endpoint mới, cần kiểm tra service đó có wrapper hay không trước khi parse response.
- **Package structure**: `com.p2plending.{service-name}.{controller|service|repository|entity|dto|config|event}`
- **Entity IDs**: UUID (`VARCHAR(36)`), dùng `@GeneratedValue(strategy = GenerationType.UUID)` — không dùng BIGINT auto-increment
- **Enums**: dùng `@Enumerated(EnumType.STRING)` cho tất cả status fields
- **Logging**: `@Slf4j` từ Lombok, không dùng `System.out.println`

### ⚠️ Quy tắc bắt buộc: `@Column(name = ...)` cho field có số liền kề chữ hoa

`SpringPhysicalNamingStrategy` của Hibernate **KHÔNG** chèn `_` ở ranh giới số-chữhoa:

| Java field | Hibernate tự suy ra | Thực tế cần |
|---|---|---|
| `ref1Address` | `ref1address` ❌ | `ref1_address` ✅ |
| `ref1FullName` | `ref1fullname` ❌ | `ref1_full_name` ✅ |
| `step2Result` | `step2result` ❌ | `step2_result` ✅ |

**Quy tắc:** Bất kỳ field nào có pattern `{chữ}{số}{ChữHoa}` đều **bắt buộc** khai báo `@Column(name = "tên_cột_đúng")` tường minh. Không để Hibernate tự suy ra — sẽ gây `Schema-validation: missing column` và service crash khi startup.

```java
// ✅ Đúng
@Column(name = "ref1_full_name", length = 100)
private String ref1FullName;

// ❌ Sai — Hibernate sẽ tìm cột "ref1fullname" thay vì "ref1_full_name"
@Column(length = 100)
private String ref1FullName;
```

### Quy tắc bắt buộc cho mọi Entity/Table

Mọi bảng đều phải có đủ 3 trường sau, không ngoại lệ:

| Field | Java | SQL | Ghi chú |
|-------|------|-----|---------|
| `isDeleted` | `boolean`, `@Builder.Default = false`, `@Column(nullable = false)` | `TINYINT(1) NOT NULL DEFAULT 0` | Soft delete — không xóa cứng |
| `createdAt` | `LocalDateTime`, `@CreationTimestamp`, `@Column(updatable = false)` | `DATETIME DEFAULT CURRENT_TIMESTAMP` | Tự set khi insert |
| `updatedAt` | `LocalDateTime`, `@UpdateTimestamp` | `DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Tự cập nhật khi update |

**Timezone**: Toàn hệ thống dùng `Asia/Ho_Chi_Minh` (UTC+7).
- Datasource URL: `serverTimezone=Asia/Ho_Chi_Minh`
- Hibernate: `spring.jpa.properties.hibernate.jdbc.time_zone: Asia/Ho_Chi_Minh`

**Soft delete**: Khi cần xóa record, set `isDeleted = true`, không dùng `DELETE`. Query phải filter `WHERE is_deleted = 0`.

### Transaction Management

Mọi method trong `@Service` thực hiện truy cập database **bắt buộc** phải có `@Transactional`. Không có ngoại lệ.

Khi xây dựng API mới, phải chủ động dùng transaction ở service layer khi cần thiết, đặc biệt với các flow thay đổi trạng thái nghiệp vụ như KYC decision, loan approval/rejection, account lock/suspend, event consumer cập nhật read-model, hoặc seed dữ liệu hệ thống.

| Trường hợp | Annotation | Ghi chú |
|------------|------------|---------|
| Ghi DB (INSERT / UPDATE) | `@Transactional` | Rollback tự động khi có RuntimeException |
| Đọc DB đơn thuần | `@Transactional(readOnly = true)` | Hibernate tắt dirty-checking, connection pool tối ưu hơn |
| Đọc + ghi trong cùng một method | `@Transactional` | readOnly = false (mặc định) |
| Kafka consumer gọi service method đã có `@Transactional` | Không cần thêm vào consumer | Transaction bắt đầu từ service method |

**Quy tắc cụ thể:**

- Method chỉ gọi `repository.findBy*` / `repository.existsBy*` / `repository.count*` → `@Transactional(readOnly = true)`
- Method thực hiện `repository.save()` hoặc `repository.delete()` → `@Transactional`
- Method vừa đọc vừa ghi (ví dụ: find rồi save) → `@Transactional`
- Method validate đầu vào bằng cách query DB (ví dụ: kiểm tra phone trùng) → `@Transactional(readOnly = true)`
- `@KafkaListener` method không trực tiếp gọi repository thì **không** cần `@Transactional` trên consumer — transaction được quản lý ở service layer

**Lưu ý quan trọng:**

- `@Transactional` chỉ hoạt động khi method được gọi từ **bên ngoài** bean (Spring proxy). Không gọi `@Transactional` method từ chính class đó (self-invocation).
- Không dùng `@Transactional` trên `private` method.
- Kafka publish (`kafkaProducerService.publish*`) nằm **trong** `@Transactional` block là có chủ ý — nếu DB write fail thì message vẫn có thể đã được gửi (at-least-once). Cần lưu ý khi xử lý idempotency ở consumer.

## Testing

- Dependencies đã có (spring-boot-starter-test, spring-security-test) nhưng chưa có test nào
- Docker builds dùng `-DskipTests`
- Khi viết tests, cần Testcontainers cho MySQL/Kafka/Redis

## Known Gaps / Future Work

- `payment_db` và bảng payment chưa được implement
- Không có CI/CD pipeline
- notification-service chưa implement gửi SMS (chỉ email)
- Không có service-to-service authentication (mTLS)
- Kafka event classes bị duplicate giữa loan-service và matching-service (nên extract shared module)
- Chưa có distributed tracing (OpenTelemetry/Zipkin)
