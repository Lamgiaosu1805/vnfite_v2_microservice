# TIKLUY → VNFITE mới: Hướng dẫn cutover từng phase

## Bối cảnh

TIKLUY gọi 2 downstream sau mỗi giao dịch nạp tiền thành công từ MB:
- `CmsFeignService` → `{cms.url}/transaction-management/add-transaction-by-ms-account`
- `AppFeignService`  → `{app.url}/notification/save-by-ms-account`

VNFITE mới (payment-service) đã implement cả 2 endpoint này. Chỉ cần đổi URL trong config TIKLUY.

---

## Phase 1 — Test environment (42.113.122.119)

### Bước 1: Sửa config TIKLUY test trên server 119

```bash
ssh root@42.113.122.119

# Tìm file config TIKLUY test (thường là application.yml hoặc application-test.yml)
find /root/01.TIKLUY -name "application*.yml" | head -10

# Sửa 2 dòng sau trong file config TIKLUY test (port 9999):
#   spring.vnf-ms.cms.url = http://127.0.0.1:7080
#   spring.vnf-ms.app.url = http://127.0.0.1:7080   ← thêm mới nếu chưa có

# Restart TIKLUY test (port 9999)
# (tuỳ theo cách deploy — jar hoặc docker)
```

### Bước 2: Deploy VNFITE mới với nginx IP allowlist đã sửa

Commit lần này đã sửa nginx.conf: cho phép `127.0.0.1` và `42.113.122.119` vào 2 callback routes.

```bash
# Sau khi CI/CD deploy xong, verify nginx OK:
ssh root@42.113.122.119 'docker compose exec nginx nginx -t'
```

### Bước 3: Test nạp tiền trên môi trường test

Dùng flow nạp tiền test (chuyển khoản đến VA sandbox MB hoặc dùng TIKLUY test API trực tiếp).

```bash
# Xem log payment-service để xác nhận nhận được callback:
ssh root@42.113.122.119 'docker compose logs payment-service --tail=50 | grep -E "Deposit|save-by-ms|accNo"'
```

Kết quả kỳ vọng:
- Log `Deposit callback: accNo=VNF... amount=... category=IN`
- Log `Deposit processed: accNo=VNF... amount=...`
- Kafka event `payment.deposit_completed` → notification-service gửi FCM push

---

## Phase 2 — Live environment (chưa làm ngay, sau khi test ổn định)

### Điều kiện go-live:
- [ ] Test environment chạy ổn tối thiểu 1 tuần
- [ ] Kiểm tra log: không có ERROR từ payment-service cho VNF accounts
- [ ] FCM push notification nhận được trên app mới
- [ ] Withdrawal flow test xong (rút tiền từ app mới, nhận tiền về tài khoản ngân hàng)

### Bước 1: Sửa nginx.conf cho live (42.113.122.118)

Đổi IP allowlist trong nginx.conf:
```
# Thay 42.113.122.119 thành 42.113.122.155
allow 127.0.0.1;
allow 42.113.122.155;   ← IP TIKLUY live
deny all;
```

### Bước 2: Sửa config TIKLUY live trên server 155

```bash
ssh root@42.113.122.155

# File config TIKLUY live: /root/01.TIKLUY/02.Ms-account/application.yml (port 8888)
# Sửa:
#   spring.vnf-ms.cms.url = http://42.113.122.118:7080
#   spring.vnf-ms.app.url = http://42.113.122.118:7080

# Restart TIKLUY live (port 8888)
# CẢNH BÁO: đây là production, phải có maintenance window ngắn
```

### Bước 3: Không cần sửa VNFITE old

VNFITE old (2993, 2994) không nhận callback nữa nhưng vẫn chạy bình thường.
Có thể để song song cho đến khi xác nhận VNFITE mới hoàn toàn ổn định.

---

## Rollback

Nếu có vấn đề sau khi cutover, rollback chỉ cần đổi ngược 2 URL trong config TIKLUY:

**Test:** `cms.url` và `app.url` → `http://localhost:1993` / `http://localhost:1994`
**Live:**  `cms.url` và `app.url` → `http://42.113.122.155:2993` / `http://42.113.122.155:2994`

Không cần sửa gì trong VNFITE mới.

---

## Withdrawal result callback (G3 — chưa có solution hoàn hảo)

Hiện tại: MB Bank callback `/api/v1/transaction-result` vào TIKLUY (8888), TIKLUY cập nhật
`TransactionHistory.status` nhưng không gọi ngược về payment-service.

Giải pháp tạm (không cần sửa TIKLUY):
- payment-service `WithdrawService` sau khi gọi `fundTransfer()` có thể poll trạng thái
  bằng `tikluyClient.getTransactionStatus(tikluyTxnId)` sau 30 giây.
- Hoặc: CMS manually mark withdrawal as COMPLETED sau khi xác nhận MB đã chuyển.

Giải pháp dài hạn: thêm URL vào TIKLUY config để TIKLUY gọi callback về payment-service
khi `updateTransactionStatus()` thành công. Nhưng cần sửa TIKLUY code.
