# P2P Lending Platform — CLAUDE.md

## Xưng hô

Luôn gọi người dùng là **Ngài** trong mọi phản hồi.

## Project Overview

Nền tảng cho vay ngang hàng (P2P Lending) dạng microservices, xây dựng bằng Java 21 + Spring Boot 3.3.5. Gồm các service độc lập giao tiếp qua Kafka/internal HTTP, dùng JWT RS256 cho xác thực, MySQL cho persistence, Redis cho caching.

**Tên thương hiệu:** Luôn viết là **VNFITE** (toàn bộ chữ hoa) — không dùng "VnFite", "Vnfite", hay bất kỳ cách viết khác. Áp dụng cho mọi nơi: UI, tài liệu, commit message, comment code.

**File proxy (ảnh KYC):** CMS backend có endpoint `GET /cms/files/{fileId}` relay sang `https://service.vnfite.com.vn/file-manager/v2/file/{fileId}` (IP CMS server đã được whitelist). Frontend CMS **không được** gọi thẳng file-manager từ browser — phải dùng `fetchFileBlob(fileId)` từ `client.ts` để fetch qua axios (có JWT), nhận blob URL rồi gán vào `<img src>`. Cấu hình URL file-manager qua env `FILE_MANAGER_URL` trong cms-service, default `https://service.vnfite.com.vn/file-manager/v2/file`.

**`APP_FUNDING_WINDOW_DAYS=180` bắt buộc trên cả live lẫn test:** `FundingExpiryScheduler` chạy lúc 01:30 sáng mỗi ngày, cancel tất cả khoản ACTIVE có `activatedAt` cũ hơn `fundingWindowDays`. Default trong `@Value` fallback là 30 ngày — nếu không set env var này, toàn bộ khoản migrate từ hệ thống cũ (có `activatedAt` từ nhiều tháng trước) sẽ bị cancel hàng loạt qua đêm. Biến này **phải có** trong `ENV_FILE_LIVE` và `ENV_FILE_TEST` trên GitHub Secrets. Khi restore data bị cancel nhầm bởi scheduler: `UPDATE loan_requests SET status='ACTIVE', borrower_cancelled_reason=NULL, activated_at=NOW(), updated_at=NOW() WHERE status='CANCELLED' AND borrower_cancelled_reason='Hết hạn gọi vốn — đã hoàn tiền cho nhà đầu tư' AND is_deleted=0;` — lệnh này an toàn với data migrate vì các khoản đó chưa có offers/tiền thật trong hệ thống mới.

**Migration hệ thống cũ → mới:** Script migration đầy đủ tại `infra/scripts/migrate-all-users.sh`. Chạy trực tiếp trên server 118: `bash /root/p2p-lending/infra/scripts/migrate-all-users.sh`. Script idempotent, kéo dữ liệu từ `APP_V2` (118) + `VNF_ACCOUNT_MANAGEMENT` (155) sang `auth_db` + `payment_db`. Hiện tại chỉ mới migrate thủ công 1 user thử nghiệm (`0343316951`). Chưa chạy full migration. Khi chạy full cần backup trước. Mapping: `tbl_user` → `users` (không có cột `full_name`/`role` trong schema thực); `tbl_identification_info` → `kyc_submissions`; `tbl_associate_bank_information` → `linked_banks`; `tbl_transaction` → `wallet_transactions` (STATUS: 0→PENDING, 1→SUCCESS, 2→FAILED; CATEGORY: 0→DEPOSIT, 1→WITHDRAWAL); wallet link qua CCCD→`IDENTITY_NUMBER`→`acc_no LIKE 'VNF%'`. Password hệ thống cũ: app MD5 rồi mới gửi lên, server bcrypt(md5). App mới đã MD5 trước khi gửi — tương thích. `TransactionStatus` enum chỉ có `PENDING`, `SUCCESS`, `FAILED` — không có `COMPLETED`.

**Ranh giới VNFITE cũ và TIKLUY production:** Backend VNFITE cũ tại `42.113.122.155:6666` sẽ ngừng hoạt động và phải được thay thế bởi các microservice VNFITE mới; không tạo thêm dependency runtime, API call hoặc luồng nghiệp vụ mới trỏ về port `6666`. TIKLUY tại `42.113.122.155:8888` là gateway tích hợp MB Bank production đang chạy và phải giữ nguyên contract/luồng hiện hữu nếu chưa có kế hoạch cutover riêng. VNFITE mới sở hữu source of truth, ledger, withdrawal state machine, idempotency và vận hành; `payment-service` chỉ gọi TIKLUY `8888` ở biên ngân hàng để tạo VA/xác minh/chuyển tiền và nhận callback. Không được hiểu yêu cầu “không sửa TIKLUY 8888” thành “giữ luồng VNFITE cũ 6666”.

**Source rút tiền qua TIKLUY 8888:** VNFITE mới gọi `POST /api/v1/transfer-money` trên TIKLUY `8888`; endpoint đọc `source` trong JSON body, không đọc source từ header. Mọi lệnh rút của VNFITE mới phải gửi `source: "VNFITE"` để TIKLUY chọn tài khoản chi `6966638888`, channel `YFCH` và remark `YF CHUYEN TIEN ... VNFITE`. Nhánh `VNFFITE_CAPITAL`/mặc định dùng `6RCH` và nội dung `TIKLUY`, không được dùng cho lệnh rút VNFITE. Thiếu source phải fail-closed, không được gửi lệnh để tránh chọn sai nguồn tiền.

**Cutover rút tiền VNFITE cũ → mới (chạy song song):** MB chỉ whitelist IP server `42.113.122.155`, vì vậy `payment-service` mới trên server `118` không gọi MB trực tiếp. Nó gọi account-service `8888` trên server `155`; `8888` là gateway MB và phải giữ nguyên nhánh `6RCH` của TIKLUY. VNFITE cũ `6666` vẫn chạy độc lập trong giai đoạn UAT. Chỉ process `8888` được set `VNFITE_NEW_PAYMENT_URL=http://42.113.122.118:7080` và `VNFITE_NEW_CALLBACK_SECRET`; process `6666` phải để hai biến này trống. Lệnh mới gửi `clientReference`; lệnh legacy `6666` không có trường này. MB callback rút vào `8888`, sau đó `8888` chỉ relay bất đồng bộ callback terminal `YFCH` có `clientReference` sang `/internal/payment/withdrawal/transfer-callback`, nên giao dịch cũ không bị relay. Relay lỗi không được làm lỗi response trả MB; scheduler payment-service query lại `/api/v1/get-transaction` qua `8888` để đối soát. Mã nội bộ `transfer_ref` và mã thật `provider_transfer_ref` (`YFCH...`) phải lưu riêng. Số dư VA chỉ quyết toán sau MB success qua endpoint idempotent `/api/v2/account/{accNo}/balance-adjustment`; callback/retry lặp dùng cùng settlement request ID và không được trừ hai lần.

**Quy tắc commit & push:** Không tự ý chạy `git commit` hay `git push`. Sau khi hoàn thành thay đổi, chỉ được **gen câu lệnh** để người dùng tự chạy. Luôn gen đủ cả 4 lệnh: `cd /Users/lamgs/Desktop/p2p-lending`, `git add`, `git commit`, và `git push` trong cùng một khối lệnh (bắt đầu bằng `cd` vào thư mục project). Commit message ngắn gọn, 1 dòng, tiếng Anh hoặc tiếng Việt đều được.

**Viết tắt trong dự án:** CMS = **Customer Manager Service** (không phải Content Management System). Khi đọc hoặc viết bất kỳ chỗ nào có chữ "CMS" trong project này đều hiểu là Customer Manager Service.

**CMS account model:** User đăng nhập CMS là `cms_admin_users` riêng trong `cms_db`, tách biệt hoàn toàn với customer/user trong `auth_db.users`. Không dùng tài khoản customer để đăng nhập CMS.

**Thuật ngữ UI:** Dùng **"người gọi vốn"** và **"nhà đầu tư"** — không dùng "người vay" hay "cho vay". Áp dụng cho mọi label, tiêu đề, nội dung trên cả CMS web và mobile app.

**Số tiền đã nhận đầu tư:** Luôn dùng **"đã được đầu tư"** — không dùng "đã huy động", "đã gọi được", hay bất kỳ cách diễn đạt khác. Áp dụng cho mọi label hiển thị `raisedAmount` trên mobile app và CMS web.

**CMS Dark Mode:** `VnFiteCMS` đã có dark mode. Toggle bằng nút mặt trăng/mặt trời ở header, lưu vào `localStorage` key `cms_theme`. Cơ chế: toggle class `dark` trên `document.documentElement` → Tailwind `dark:` variant. Khi thiết kế hoặc sửa bất kỳ component CMS nào, **bắt buộc thêm `dark:` classes** cho mọi màu nền, chữ, border, input, modal.

**Không mirror user/loan vào CMS:** `cms-service` không còn đồng bộ `auth_db.users` hoặc `loan_db.loan_requests` sang DB riêng. `cms_db` chỉ lưu dữ liệu nội bộ CMS như admin users, quyền, cấu hình, audit/ops data. Khi CMS cần dữ liệu khách hàng hoặc khoản gọi vốn, hãy gọi API từ service nguồn (`auth-service`, `loan-service`) thay vì copy bảng.

**CMS giao dịch nạp/rút:** Màn `Giao dịch nạp/rút` lấy dữ liệu trực tiếp từ `payment-service` qua `GET /internal/payment/transactions` rồi proxy tại `GET /cms/transactions`; `cms_db` không mirror giao dịch. `payment_db.wallet_transactions` là source of truth. Dữ liệu VNFITE cũ chỉ được migrate một lần bằng script migration đã kiểm soát vào `payment_db`, không tạo job đồng bộ runtime giữa hai database vì dễ trùng giao dịch và sai số dư.

**Mobile OTP UI:** Tất cả màn nhập OTP trên app mobile VNFITE phải dùng cùng một kiểu giao diện: mỗi số OTP là một ô riêng. Không dùng một ô input dài nhập liền toàn bộ OTP. Ưu tiên component/pattern OTP dùng chung nếu có. Áp dụng cho đăng ký, quên mật khẩu, eKYC, bật/tắt sinh trắc học, reset thiết bị và mọi luồng OTP mới. Input OTP phải numeric-only, fixed length, đồng nhất về khoảng cách/kích thước/trạng thái lỗi. OTP phải được scope theo tính năng/purpose, không dùng OTP của tính năng này để xác thực tính năng khác.

**Mobile file upload:** Mobile app không được gọi trực tiếp `service.vnfite.com.vn/file-manager`. Mọi upload chứng từ/file từ app phải đi qua API proxy của VNFITE, hiện tại là `POST /api/loans/documents/upload`, để app/network whitelist chỉ cần domain API VNFITE. Backend proxy mới là nơi gọi file-manager và trả `fileId` cho mobile.

**Error handling:** Không che lỗi nghiệp vụ/validation/service nguồn/schema DB bằng câu chung `Internal server error`. Khi service gọi service khác, phải giữ status code của service nguồn và bóc message theo thứ tự `details[]` → `message` → `detail` → `error`. Frontend phải đọc được cả single-message và `details[]`, rồi hiển thị lỗi cụ thể cho người dùng/admin. Tuy nhiên với lỗi network/kết nối, UI **không được** hiển thị raw API URL, hostname nội bộ, chi tiết stack HTTP, hay câu kiểu `Không thể kết nối API ...`; phải đổi sang câu chung thân thiện như `Không thể kết nối với máy chủ. Vui lòng thử lại.`. Trên mobile app, đặc biệt ở các màn auth/OTP, lỗi kết nối nên ưu tiên hiển thị bằng modal chung `VnfiteAlert` thay vì để raw text chạy inline trong form. Backend vẫn phải log exception đầy đủ bằng `@Slf4j` để trace server.

**Seed/data safety:** Không bao giờ `TRUNCATE`, `DELETE`, drop, recreate, hoặc reset dữ liệu nghiệp vụ/người dùng trong seed, migration, deploy script, local script, hoặc SQL thủ công trừ khi người dùng yêu cầu reset phá dữ liệu rõ ràng và xác nhận đã có backup. Các bảng được bảo vệ gồm `loan_requests`, `loan_offers`, `users`, KYC, payment, transaction, audit, notification, customer/admin operational records. Seed chỉ được insert/update dữ liệu cấu hình, phải idempotent và không phá dữ liệu, ưu tiên `INSERT ... ON DUPLICATE KEY UPDATE`.

**Redis namespace:** Test/UAT và live đang dùng chung một Redis server, nên mọi Redis key bắt buộc phải có namespace theo môi trường. Ưu tiên dùng `APP_REDIS_NAMESPACE` làm root namespace; nếu chưa set thì fallback theo `SPRING_PROFILES_ACTIVE`, rồi ghép thêm service name trong code, ví dụ `uat:auth-service:*`, `uat:loan-service:*`, `prod:cms-service:*`. Áp dụng cho cả Spring cache lẫn các key Redis viết tay như OTP, refresh token, session thiết bị, rate limit, pending KYC, pending loan. Script reset cache chỉ được xóa theo namespaced pattern, tuyệt đối không scan/xóa pattern trần dễ đụng môi trường khác.

**Timezone và định dạng ngày giờ:** Toàn bộ backend, mobile và CMS bắt buộc dùng múi giờ Việt Nam `Asia/Ho_Chi_Minh` (`UTC+7`). Java service phải giữ JVM default timezone, Jackson, Hibernate/JDBC và Docker runtime đồng nhất ở `Asia/Ho_Chi_Minh`; không dùng UTC hoặc timezone mặc định của host cho `LocalDateTime`, scheduler, timestamp nghiệp vụ hay JSON response. `Instant`/epoch vẫn được dùng cho JWT, TTL và chữ ký vì đây là mốc thời gian tuyệt đối, nhưng khi hiển thị phải quy đổi sang giờ Việt Nam. Frontend phải dùng formatter dùng chung có `timeZone: 'Asia/Ho_Chi_Minh'`, không gọi `toLocaleDateString('vi-VN')` trần. Ngày hiển thị theo `dd/MM/yyyy` và luôn đủ hai chữ số ngày/tháng, ví dụ `22/01/2021`; ngày giờ hiển thị theo `HH:mm:ss dd/MM/yyyy` khi cần giây. Chuỗi `LocalDate` dạng `yyyy-MM-dd` phải được format trực tiếp, không được chuyển timezone làm lệch ngày; chuỗi `LocalDateTime` backend không có offset phải được hiểu là giờ Việt Nam `+07:00`.

**LocalDateTime.now() bắt buộc truyền ZoneId tường minh:** Không bao giờ gọi `LocalDateTime.now()` không tham số trong bất kỳ file Java nào. Luôn phải viết `LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))`. Không được phụ thuộc vào JVM default timezone hay `JAVA_TOOL_OPTIONS` để "tự đúng" vì nếu service chạy ngoài docker-compose (Kubernetes, test standalone, CI) sẽ trả về UTC gây lệch timestamp. Áp dụng cho mọi loại timestamp: sentAt, createdAt gán tay, reviewedAt, disbursedAt, audit log, error/API response, Kafka event, scheduler log.

**Dockerfile timezone bắt buộc:** Mọi Dockerfile runtime stage đều phải có `ENV TZ=Asia/Ho_Chi_Minh` và JVM arg `-Duser.timezone=Asia/Ho_Chi_Minh` trong ENTRYPOINT — không phụ thuộc docker-compose inject. Cấu trúc chuẩn:
```dockerfile
ENV TZ=Asia/Ho_Chi_Minh
EXPOSE <port>
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Duser.timezone=Asia/Ho_Chi_Minh", \
  "-jar", "app.jar"]
```

**Luồng duyệt khoản gọi vốn:** Ban lãnh đạo phê duyệt xong **không được đưa khoản gọi vốn lên sàn ngay**. Backend phải chuyển khoản sang `AWAITING_BORROWER_APPROVAL` và thông báo cho người gọi vốn số tiền được phê duyệt, lãi suất, kỳ hạn. Chỉ khi người gọi vốn xác nhận điều kiện qua `POST /api/loans/{id}/confirm`, khoản mới chuyển `ACTIVE` và hiển thị trên sàn cho nhà đầu tư. Không đổi CMS approval thành active trực tiếp trừ khi người dùng yêu cầu đổi nghiệp vụ rõ ràng.

**Credit Score 360:** Chuẩn chấm điểm duy nhất của VNFITE là **Credit Score 360**. Grade gồm `A+`, `A`, `B`, `C`, `D`, `E`, quy đổi về thang 300-850. Engine QĐ-LSGV/rate-card chỉ dùng cho **pricing/tra lãi suất**, không tự chấm điểm hoặc tự quyết định phê duyệt. Mapping pricing: `A+ -> A1`, `A -> A2`, `B -> B1`, `C -> B3`, `D -> C1`, `E -> C3`, sau đó tra `FundingRateCard`.

**CIC và AI thẩm định:** CIC do thẩm định viên nhập thủ công trên CMS, feed vào Group B scoring và exclusion gate. Debt group `>= 3` phải ra `HARD_REJECT`; thiếu CIC hoặc chứng từ AI verdict `HIGH_RISK` thì `MANUAL_REVIEW`. AI thẩm định chứng từ chỉ là tham khảo, không được auto-approve hoặc auto-reject khoản gọi vốn. AI analyzer hiện dùng `GeminiAiDocumentAnalyzer` khi `APP_AI_MODE=gemini`, hoặc `ClaudeAiDocumentAnalyzer` khi `APP_AI_MODE=claude`; verdict chuẩn gồm `CONSISTENT`, `SUSPICIOUS`, `HIGH_RISK`, `UNREADABLE`.

**Fraud signals:** `FraudSignalService` nằm trong `loan-service`, trả về `appraisal-suggestion.fraudChecks`. Các signal hiện có: `VELOCITY_OPEN_LOANS` (người gọi vốn có từ 2 khoản mở trở lên → `HIGH`), `SHARED_REFERENCE` (một số tham chiếu dùng bởi từ 3 người gọi vốn trở lên → `HIGH`), `SAME_REF_PHONE` (`ref1 == ref2` → `MEDIUM`).

**UAT Gemini PDF analysis:** Bản fix gần nhất đã bỏ `response_mime_type: application/json` khỏi Gemini generation config vì JSON mode + inline PDF có thể làm Gemini trả empty candidates cho PDF sao kê. Đồng thời thêm `maxOutputTokens: 2048` và log `blockReason`. Sau deploy cần verify trên CMS bằng cách mở khoản `PENDING_REVIEW` và bấm `Phân tích AI tất cả`. Nếu còn lỗi, kiểm tra:
```bash
docker compose logs credit-service --tail=200 | grep -E "Gemini|blockReason|finishReason|ERROR"
```

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
| loan-service | 8082      | 8082 | loan_db | Tạo khoản gọi vốn, quản lý offer, vòng đời khoản gọi vốn, fraud signals |
| matching-service | 8083      | 8083 | matching_db | Thuật toán ghép nhà đầu tư, lịch chạy 30 phút/lần |
| cms-service | 8090      | 8090 | cms_db | CMS admin/auth service, gọi source APIs để thẩm định |
| notification-service | 8085      | 8084 | notification_db | Gửi email/SMS qua Kafka consumer + push notification qua service.vnfite.com.vn |
| credit-service | 8087      | 8087 | credit_db/configured DB | Credit Score 360, CIC/manual appraisal inputs, AI document analysis |

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

### Deploy lên Test server (42.113.122.119)

**Tự động:** push lên `main` → GitHub Actions (`.github/workflows/deploy-test.yml`) tự phát hiện service thay đổi qua `git diff` và deploy đúng service đó.

**Deploy thủ công 1 hoặc nhiều service:**
> GitHub → Actions → `CI/CD → Test Server` → Run workflow → nhập tên service (vd: `auth-service,payment-service`) hoặc `all`

**Deploy thủ công toàn bộ:**
> GitHub → Actions → `CI/CD → Test Server` → Run workflow → tick **force_all ✅**

**Cập nhật biến môi trường test:** sửa GitHub Secret `ENV_FILE_TEST` (xem hướng dẫn bên dưới).

---

### Deploy CMS Web (VnFiteCMS — tại `/Users/lamgs/Desktop/APP/VnFiteCMS`)

CMS là frontend riêng, deploy tự động lên **server 155** (SSH port 2222), **không liên quan** đến microservices p2p-lending.

| Nhánh | Môi trường | URL | Thư mục trên server |
|-------|-----------|-----|---------------------|
| `main` | Test | `cms-test.vnfite.com.vn` | `/var/www/cms-web-test` |
| `release` | Live | `cms.vnfite.com.vn` | `/var/www/cms-web-prod` |

**Deploy test:** push lên `main` → GitHub Actions tự build + rsync lên server 155.

**Deploy live:** push lên `release` → GitHub Actions tự build + rsync lên server 155.

**Quy trình bắt buộc:**
```bash
cd /Users/lamgs/Desktop/APP/VnFiteCMS
# LUÔN làm việc trên nhánh main
git checkout main
# ... sửa code ...
git add ...
git commit -m "..."
git push origin main   # → tự deploy test

# Khi cần deploy live → tạo PR trên GitHub: main → release
# KHÔNG push thẳng lên release
```

⚠️ **KHÔNG BAO GIỜ** commit hay push thẳng lên nhánh `release`. Nhánh `release` có branch protection — chỉ được merge qua Pull Request từ `main`. Mọi thay đổi phải lên `main` trước, test ổn rồi tạo PR `main → release`.

---

### Deploy lên Live server (42.113.122.118)

**Chỉ deploy thủ công** — không có auto-deploy khi push. Workflow: `.github/workflows/deploy-live.yml`.

> GitHub → Actions → `CI/CD → Live Server (Manual)` → Run workflow → nhập tên service (vd: `auth-service`) hoặc `all`

**Cập nhật biến môi trường live:** sửa GitHub Secret `ENV_FILE_LIVE` (cùng quy trình với `ENV_FILE_TEST` nhưng SSH vào `42.113.122.118`).

**SSH key cho live server:** đã có `SSH_PRIVATE_KEY_LIVE` trong GitHub Secrets. Không xem lại được sau khi lưu.

---

GitHub Actions (`.github/workflows/deploy-test.yml`):
- Trigger: push lên `main` → phát hiện service nào thay đổi qua `git diff`, chỉ deploy service đó
- Deploy thủ công toàn bộ: **Actions → Run workflow → force_all ✅**
- `credit-service` đã nằm trong pipeline deploy test; thay đổi trong `apps/api/credit-service/` phải trigger build/deploy credit-service.
- Mỗi deploy: `git pull` → `docker compose build <svc>` → `docker compose up -d <svc>` → `docker image prune`
- Nginx config thay đổi: `docker compose restart nginx`
- `.env` luôn được sync từ GitHub Secret `ENV_FILE_TEST` trước mỗi deploy
- **⚠️ GitHub không cho sửa từng dòng trong Secret** — muốn cập nhật `ENV_FILE_TEST` phải copy toàn bộ nội dung `.env` hiện tại, sửa rồi paste lại toàn bộ vào ô Secret (GitHub thay thế hết, không append). Không có cách sửa một biến đơn lẻ qua UI.
- **Quy trình cập nhật ENV_FILE_TEST:** Khi cần sửa biến môi trường, luôn gen đủ 3 bước: (1) lệnh SSH lấy `.env` hiện tại từ server, (2) lệnh `sed` sửa biến, (3) lệnh in ra nội dung mới để Ngài copy paste vào ô Secret trên GitHub. Mẫu:
  ```bash
  # 1. Lấy .env hiện tại từ test server
  ssh root@42.113.122.119 'cat /root/p2p-lending/.env'

  # 2. Sửa cục bộ (ví dụ đổi APP_PAYMENT_MOCK và thêm dòng mới)
  ssh root@42.113.122.119 'cat /root/p2p-lending/.env' \
    | sed 's/^APP_PAYMENT_MOCK=.*/APP_PAYMENT_MOCK=false/' \
    > /tmp/env_updated.txt
  # Thêm dòng nếu chưa có:
  grep -q 'TIKLUY_BASE_URL' /tmp/env_updated.txt || echo 'TIKLUY_BASE_URL=http://42.113.122.119:9999' >> /tmp/env_updated.txt

  # 3. In ra để copy paste vào GitHub Secret ENV_FILE_TEST
  cat /tmp/env_updated.txt
  ```

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
| `password` | bcrypt(md5(password)) | NOT NULL — app MD5 trước khi gửi |
| `kyc_status` | `NONE` | chưa nộp KYC |
| `referred_by` | SĐT người giới thiệu hoặc null | optional |
| `email` | **null** | user tự cập nhật sau |

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

- `users`: phone (login), password (bcrypt(md5)), kyc_status, referred_by, email (nullable), biometric_public_key (nullable)
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
| `password` | VARCHAR(255) | NO | Bcrypt hash của md5(password) — app MD5 trước khi gửi |
| `email` | VARCHAR(150) | YES | Null khi mới đăng ký, user cập nhật sau |
| `kyc_status` | ENUM | NO | `NONE` \| `PENDING` \| `APPROVED` \| `REJECTED`, default NONE |
| `referred_by` | VARCHAR(20) | YES | SĐT người giới thiệu (SĐT chính là mã giới thiệu) |
| `is_deleted` | TINYINT(1) | NO | Soft delete, default 0 |
| `created_at` | DATETIME | YES | Thời điểm tạo tài khoản |
| `updated_at` | DATETIME | YES | |
| `biometric_public_key` | TEXT | YES | Public key sinh trắc học của thiết bị |

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

## Credit Scoring And AI Appraisal

### Credit Score 360

Credit Score 360 là chuẩn đánh giá tín dụng duy nhất của VNFITE. Không dùng engine nào khác để tự tính quyết định phê duyệt.

| Grade | Normalized score | Pricing band |
|-------|------------------|--------------|
| A+ | 300-850 scale | A1 |
| A | 300-850 scale | A2 |
| B | 300-850 scale | B1 |
| C | 300-850 scale | B3 |
| D | 300-850 scale | C1 |
| E | 300-850 scale | C3 |

QĐ-LSGV/rate-card chỉ được dùng sau khi đã có Credit Score 360 grade để tra lãi suất/kỳ hạn theo `FundingRateCard`.

### CIC, exclusion gate, and appraisal suggestion

- Thẩm định viên nhập CIC thủ công trên CMS.
- CIC feed vào Group B scoring và điều kiện loại trừ.
- Debt group `>= 3` → `reviewDirective = HARD_REJECT`.
- Thiếu CIC hoặc chứng từ có verdict `HIGH_RISK` → `reviewDirective = MANUAL_REVIEW`.
- `loan-service` nhận `creditGrade` từ CMS khi tạo appraisal suggestion, map Credit360 grade sang pricing band, rồi tra rate.
- AI và fraud signal chỉ là thông tin tham khảo cho thẩm định, không được tự động đưa khoản gọi vốn lên sàn.

### AI document analysis

- `APP_AI_MODE=gemini` → dùng `GeminiAiDocumentAnalyzer`.
- `APP_AI_MODE=claude` → dùng `ClaudeAiDocumentAnalyzer`.
- Gemini default trên test có thể set bằng `GEMINI_MODEL`; hiện ưu tiên model flash/lite theo `.env`/CI.
- AI đọc PDF/image chứng từ như sao kê lương, sao kê doanh thu, hóa đơn, hợp đồng, chứng từ kinh doanh.
- Verdict chuẩn: `CONSISTENT`, `SUSPICIOUS`, `HIGH_RISK`, `UNREADABLE`.
- Với người gọi vốn buôn bán/không có lương cố định, UI và backend phải cho upload nhiều loại chứng từ chứng minh dòng tiền, không hard-code chỉ "sao kê lương".

### Fraud signals

`FraudSignalService` trong `loan-service` trả về `fraudChecks[]` trong appraisal suggestion:

| Signal | Điều kiện | Severity |
|--------|-----------|----------|
| `VELOCITY_OPEN_LOANS` | Người gọi vốn có từ 2 khoản đang mở trở lên | HIGH |
| `SHARED_REFERENCE` | Cùng một số điện thoại tham chiếu xuất hiện ở từ 3 người gọi vốn trở lên | HIGH |
| `SAME_REF_PHONE` | SĐT tham chiếu 1 trùng SĐT tham chiếu 2 | MEDIUM |

### Recent UAT verification

Gemini PDF analysis đã được sửa bằng cách bỏ `response_mime_type: application/json` khỏi generation config, thêm `maxOutputTokens: 2048`, và log `blockReason`. Nếu CMS vẫn báo chưa phân tích được chứng từ sau deploy, kiểm tra log:

```bash
docker compose logs credit-service --tail=200 | grep -E "Gemini|blockReason|finishReason|ERROR"
```

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
