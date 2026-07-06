# VNFITE x FEC UAT - Exposed API & Lead Integration

## 1. Scope

Giai đoạn UAT triển khai service riêng `fec-integration-service` để tách tích hợp FEC/FEOL khỏi các service nghiệp vụ hiện tại.

Luồng tích hợp:

1. VNFITE gửi lead sang FEC UAT qua API `Receive Lead Information`.
2. FEC trả `LeadStatus`, `LeadGenID`, onboarding link nếu lead eligible.
3. FEC gọi callback public của VNFITE để cập nhật trạng thái lead/application trong hành trình FEOL.

Nguồn tài liệu đối tác:

- `Receive Lead Information - UBIT`
- `API Spec Structure - Update Lead Status Callback - UBIT`
- `General Lead Onboarding Flow (Partner Reference)`

## 2. Service

Service: `fec-integration-service`

Port nội bộ Docker: `8091`

Gateway route:

- Public callback: `POST /api/fec/lead-status-callback`
- Internal submit lead: `POST /internal/fec/leads`

UAT callback URL:

```text
https://api-uat.vnfite.com.vn/api/fec/lead-status-callback
```

Domain UAT này trỏ về API gateway test. Không gửi URL IP:port cho đối tác khi chạy UAT.

## 3. FEC Receive Lead API - VNFITE gọi FEC

Endpoint FEC UAT:

```text
POST https://uat-whitelist-gw.ubank.vn/wl/fe2/api/v2/feol/receive-leads
```

Headers gửi sang FEC:

```http
Content-Type: application/json
Authorization: Bearer <APP_FEC_BEARER_TOKEN>
partnerCode: <APP_FEC_PARTNER_CODE>
signature: <base64 RSA-SHA256 signature of request body>
```

Crypto:

- PII fields mã hóa RSA/PKCS#1 v1.5 bằng public key FEC:
  - `fullName`
  - `phoneNumber`
  - `nid`
  - `dob`
  - `email`
- Request body được ký bằng private key của VNFITE partner:
  - algorithm: `SHA256withRSA`
  - output: Base64

Body gửi FEC:

```json
{
  "transId": "uuid",
  "fullName": "<RSA encrypted base64>",
  "phoneNumber": "<RSA encrypted base64>",
  "nid": "<RSA encrypted base64>",
  "dob": "<RSA encrypted base64>",
  "email": "<RSA encrypted base64>",
  "loanAmount": 10000000,
  "tenor": 12,
  "leadSource": "VNFITE",
  "agentCode": "VNFITE",
  "consentType": "Tickbox",
  "consentTickbox": "YES",
  "consentContent": "Full consent text"
}
```

## 4. Internal API - VNFITE submit lead

Endpoint này chỉ cho service nội bộ/CMS/backend VNFITE gọi, bảo vệ bằng `X-Internal-Secret`.

```http
POST /internal/fec/leads
X-Internal-Secret: <INTERNAL_API_SECRET>
Content-Type: application/json
```

Request:

```json
{
  "transId": "optional-uuid",
  "fullName": "NGUYEN VAN A",
  "phoneNumber": "0912345678",
  "nid": "001099015069",
  "dob": "22-06-1999",
  "email": "customer@example.com",
  "loanAmount": 10000000,
  "tenor": 12,
  "leadSource": "VNFITE",
  "agentCode": "VNFITE",
  "consentType": "Tickbox",
  "consentTickbox": "YES",
  "consentContent": "Khách hàng đồng ý cho VNFITE chia sẻ dữ liệu với VPB SMBC FC để đăng ký và xét duyệt dịch vụ tài chính cá nhân."
}
```

Response:

```json
{
  "transId": "1dd6b977-fcd0-4097-9920-495fa4afdac8",
  "code": 1,
  "description": "SUCCESS",
  "leadStatus": "Eligible",
  "leadGenId": "VNFITE3929425667",
  "onboardingLink": "https://uat-ingress.ubank.vn/fe2/api/v2/service-qr/onboarding?...",
  "responseSignatureValid": true
}
```

## 5. Public Callback API - FEC gọi VNFITE

Endpoint VNFITE expose cho FEC:

```http
POST /api/fec/lead-status-callback
Content-Type: application/json
x-api-key: <APP_FEC_CALLBACK_API_KEY>
signature: algo=SHA256&signature=<base64_signature>
```

Signature verification:

- Payload ký: raw request body.
- Algorithm: RSA SHA256.
- VNFITE verify bằng public key do FEC cung cấp (`APP_FEC_PUBLIC_KEY`).

Request body FEC gửi:

```json
{
  "leadgen_id": "LG123456",
  "request_time": "2026-02-06 10:30:00",
  "status": "Pending Offer",
  "remark": "",
  "offer_amt": 10000000,
  "cash_amt": 8000000,
  "insurance_amt": 200000,
  "topup_amt": 0,
  "referral_code": "REF001",
  "app_id": "APP987654",
  "app_type": "PLNTB"
}
```

Success response:

```json
{
  "code": 200,
  "data": {
    "leadgen_id": "LG123456"
  },
  "message": "Success"
}
```

## 6. Status events expected from FEC

Theo tài liệu callback, `status` có thể là:

- `App download`
- `Start registration`
- `App login`
- `Pre-screening failure`
- `Start loan onboarding`
- `Referral code`
- `Pending eSign`
- `Pending Offer`
- `Pending Disbursement`
- `PL Disbursed`
- `Hard Reject`
- `Soft Reject`
- `Cancellation`
- `Drop-off`

Các trạng thái kết thúc hành trình:

- `PL Disbursed`
- `Hard Reject`
- `Soft Reject`
- `Cancellation`

## 7. Required ENV for UAT

```env
APP_FEC_ENABLED=true
APP_FEC_RECEIVE_LEAD_URL=https://uat-whitelist-gw.ubank.vn/wl/fe2/api/v2/feol/receive-leads
APP_FEC_PARTNER_CODE=VNFITE
APP_FEC_LEAD_SOURCE=VNFITE
APP_FEC_BEARER_TOKEN=<Bearer token FEC cấp>

# Private key VNFITE dùng để ký request gửi FEC, dạng PEM 1 dòng với \n
APP_FEC_PARTNER_PRIVATE_KEY=<VNFITE PKCS8 private key>

# Public key VNFITE gửi cho FEC để họ verify request của VNFITE
APP_FEC_PARTNER_PUBLIC_KEY=<VNFITE public key>

# Public key FEC dùng để encrypt PII khi gửi lead và verify callback từ FEC
APP_FEC_PUBLIC_KEY=<FEC public key>

# API key FEC sẽ gửi trong callback header x-api-key
APP_FEC_CALLBACK_API_KEY=<shared callback api key>
```

## 8. Information to send FEC for UAT

VNFITE cần gửi FEC:

```text
Callback URL:
https://api-uat.vnfite.com.vn/api/fec/lead-status-callback

Header:
x-api-key: <APP_FEC_CALLBACK_API_KEY>

Partner code:
VNFITE

VNFITE public key:
<APP_FEC_PARTNER_PUBLIC_KEY>
```

FEC cần gửi VNFITE:

```text
UAT bearer token
FEC public key
Callback x-api-key agreement
Whitelist/IP/domain requirement nếu có
```

Credential UAT:

```text
Base URL:
https://api-uat.vnfite.com.vn

Callback URL:
https://api-uat.vnfite.com.vn/api/fec/lead-status-callback

Authentication:
x-api-key + RSA SHA256 signature

Signature header:
signature: algo=SHA256&signature=<base64_signature>
```

## 9. Current implementation note

Giai đoạn UAT hiện tại callback đã verify chữ ký và log sự kiện. Bước tiếp theo khi UAT contract ổn định:

- Lưu lead submit/response/callback vào DB riêng.
- Thêm retry/outbox cho submit lead nếu FEC timeout.
- Mapping callback status vào hồ sơ gọi vốn/CRM/CMS nếu Ngài chốt luồng nghiệp vụ.
