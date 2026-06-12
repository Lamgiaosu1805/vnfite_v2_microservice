# VNFITE Credit Score 360 — Triển khai theo dữ liệu hiện có & Lộ trình tích hợp

> Tài liệu kỹ thuật đi kèm bản thuyết minh **VNFITE Credit Score 360 (V1.0)**.
> Bản thuyết minh mô tả mô hình mục tiêu **1.000 điểm, 8 nhóm A–H**. Tài liệu này mô tả
> **phần đã code được ngay** dựa trên dữ liệu hệ thống đang có + file chứng từ khách upload,
> và **lộ trình bổ sung dữ liệu** để tiến dần tới mô hình đầy đủ.

## 1. Nguyên tắc triển khai

- **Chỉ chấm tiêu chí có dữ liệu thật.** Nhóm chưa có nguồn (CIC đầy đủ, dòng tiền ngân hàng,
  device analytics, AML/graph) để ở "roadmap", **không seed**, nên **không kéo tụt điểm** hồ sơ
  (tránh đúng lỗi thin-file: điểm thấp vì thiếu dữ liệu chứ không phải vì rủi ro).
- **Không chấm biến nhân khẩu học nhạy cảm** (tuổi, hôn nhân, người phụ thuộc, học vấn) — theo
  nguyên tắc chống phân biệt đối xử của mô hình (thuyết minh mục 4 & 6.7). Các trường này nếu cần
  vẫn hiển thị cho thẩm định viên tham khảo, nhưng **không vào điểm**.
- **Chuẩn hóa về thang 300–850** như cũ để giao tiếp với nhà đầu tư. Điểm thô tối đa khả dụng hiện
  tại ~320; engine tự chuẩn hóa theo tổng band active, nên khi bật thêm nhóm (roadmap) chỉ cần
  thêm cấu hình trong bảng `scoring_criteria`, **không sửa code**.
- **AI chỉ hỗ trợ thẩm định** — không phán quyết thật/giả, không tự duyệt/từ chối.

## 2. Đang chấm điểm (Giai đoạn 0 — đã triển khai)

Cấu hình tại `credit-service`: `db/migration/V6__creditscore360_realignment.sql`.
Engine đọc bảng `scoring_criteria` (đổi trọng số không cần deploy lại code).

| Nhóm (docx) | Tiêu chí | Mã | Điểm tối đa | Nguồn dữ liệu hiện có |
|---|---|---|---|---|
| **A** KYC & định danh | Định danh eKYC | `KYC_STATUS` | 20 | `auth_db.users.kyc_status` |
| **B** Lịch sử tín dụng | Lịch sử trả nợ nội bộ VNFITE *(proxy cho CIC)* | `COMPLETED_LOANS` | 50 | `loan_db` — số khoản COMPLETED |
| **C** Khả năng trả nợ | Mức xác minh thu nhập | `INCOME_VERIFICATION` | 45 | Thu nhập khai báo + **AI đọc chứng từ thu nhập** |
| **C** | PTI — trả nợ kỳ/thu nhập | `PTI_RATIO` | 45 | EMI (amount, rate, term) / thu nhập |
| **C** | DTI — tổng nợ/thu nhập | `DTI_RATIO` | 35 | `borrower_profiles.existing_monthly_debt` / thu nhập |
| **E** Nghề nghiệp & thu nhập | Thâm niên công tác/kinh doanh | `EMPLOYMENT_YEARS` | 25 | `borrower_profiles.employment_years` |
| **E** | Ổn định nghề/ngành | `OCCUPATION_TYPE` | 20 | Nghề nghiệp khai báo (đơn vay/profile) |
| **E** | Chứng từ nghề nghiệp/kinh doanh | `OCCUPATION_DOC` | 20 | **AI xác nhận** HĐLĐ / ĐKKD |
| **F** Đặc điểm khoản vay | Tiền vay/thu nhập năm | `LOAN_TO_ANNUAL_INCOME` | 20 | amount / (thu nhập × 12) |
| **F** | Mục đích vay rõ ràng | `PURPOSE_CLARITY` | 10 | `loan_requests.purpose` |
| **F** | Quan hệ với VNFITE | `ACCOUNT_AGE_MONTHS` | 15 | Tuổi tài khoản |
| **H** Gian lận & bất thường | Toàn vẹn chứng từ | `DOCUMENT_INTEGRITY` | 15 | **AI verdict** trên toàn bộ file đính kèm |

**Tổng tối đa khả dụng ≈ 320 điểm thô → chuẩn hóa 300–850.**
Hạng: **A+** ≥ 800 · A ≥ 750 · B ≥ 680 · C ≥ 620 · D ≥ 550 · E < 550.

### File chứng từ AI nuôi 3 tín hiệu

Đây là điểm cốt lõi "dựa trên file chứng từ khách gửi": kết quả AI phân tích từng chứng từ được
quy đổi thẳng vào điểm, không chỉ để hiển thị:

1. **C1 `INCOME_VERIFICATION`** — `VERIFIED` (45đ) khi có chứng từ thu nhập AI đánh giá *CONSISTENT*
   và số tiền trích xuất khớp khai báo (±25%); `SUPPORTED` (30đ) khi có chứng từ bổ trợ;
   `DECLARED_ONLY` (15đ) khi chỉ tự khai.
2. **E3 `OCCUPATION_DOC`** — `CONFIRMED` (20đ) khi AI xác nhận HĐLĐ/ĐKKD nhất quán.
3. **H2 `DOCUMENT_INTEGRITY`** — `FLAGGED` (0đ) nếu có file *HIGH_RISK*, `REVIEW` (8đ) nếu *SUSPICIOUS*,
   `CLEAN` (15đ) nếu tất cả nhất quán. File *HIGH_RISK* đồng thời được nêu trong cảnh báo để
   thẩm định viên kiểm tra thủ công (cơ chế override mềm theo thuyết minh mục 7).

Loại chứng từ chấp nhận (đa nguồn thu, không bó cứng sao kê lương): sao kê lương/ngân hàng, bảng
lương, HĐLĐ, giấy phép kinh doanh, hóa đơn, sổ bán hàng, sao kê POS/ví/nền tảng, chứng từ thuế,
ảnh cửa hàng, chứng từ thu nhập khác.

## 3. Lộ trình tích hợp dữ liệu (theo độ khả thi)

Mỗi nhóm dưới đây khi có dữ liệu chỉ cần **thêm band vào `scoring_criteria`** + bổ sung feature
tương ứng trong `CreditScoringService.buildFeatures()`. Trọng số docx ghi trong ngoặc.

### Tier 1 — Khả thi ngay / ngắn hạn (0–1 tháng), nội bộ, không phụ thuộc bên thứ ba

| Tiêu chí docx | Cần thêm gì | Ghi chú |
|---|---|---|
| **C4** Thu nhập khả dụng sau chi phí (30) | Thêm 1 trường "chi phí sinh hoạt/tháng" ở form gọi vốn app | Tính buffer = (thu nhập − nợ − EMI − chi phí) / EMI |
| **C6** Stress test thu nhập (20) | Thuần tính toán: PTI khi giảm 20% thu nhập | Không cần dữ liệu mới, chỉ thêm feature |
| **F2** Kỳ hạn khớp dòng tiền (15) | Rule kỳ hạn vs mục đích/loại nguồn thu | Dữ liệu đã có (term, purpose) |
| **F5** Phương thức trả nợ (10) | Cờ auto-debit/ví khi tạo khoản | Phụ thuộc tính năng thu nợ tự động |
| **E6** Ổn định địa điểm (10) | So `permanent_address` vs `current_address` + lịch sử đổi | Dữ liệu địa chỉ đã có một phần |

### Tier 2 — Trung hạn (1–3 tháng), nội bộ + AI nâng cao

| Nhóm/Tiêu chí docx | Cần thêm gì | Giá trị |
|---|---|---|
| **D** Dòng tiền & hành vi tài chính (**150**) | Mở rộng AI đọc **sao kê ngân hàng** → trích cấu trúc: inflow bình quân (D1), biến động (D2), số dư & số ngày số dư thấp (D3), chi tiêu định kỳ (D4), dòng tiền bất thường (D6) | **Rất cao** cho thin-file/hộ KD — đã có sẵn file sao kê, chỉ cần nâng prompt + schema trích xuất |
| **E4** Quy mô/biên lợi nhuận hộ KD (20) | AI trích doanh thu/lợi nhuận từ chứng từ kinh doanh | Bổ sung cho nhóm E |
| **G** Device/digital/app (**70**) | Tận dụng `user_fcm_tokens.device_key` + login/app analytics: nhất quán thiết bị (G1), địa lý (G2), hành vi hoàn thiện hồ sơ (G3) | Cần pipeline thu tín hiệu từ app, kiểm định bias |
| **H1/H4** Graph & velocity (25) | Cross-user dup trên SĐT/thiết bị/STK (H1) + tốc độ nộp/sửa hồ sơ (H4) | Dữ liệu nội bộ, dựng graph link |
| **A4/A6** Trùng định danh / AML cơ bản (25) | Check trùng CCCD cross-user + đối chiếu watchlist tĩnh | A4 gần như đã chặn ở eKYC; cần lưu vết để chấm |

### Tier 3 — Dài hạn (3–6 tháng), phụ thuộc bên thứ ba / pháp lý

| Nhóm docx | Cần thêm gì | Ghi chú |
|---|---|---|
| **B** CIC & lịch sử tín dụng (**250**) | Kết nối **CIC/PCB** theo Thông tư 15/2023/TT-NHNN, có consent + lưu vết tra cứu | Uplift đơn lẻ lớn nhất; cần hợp đồng/pháp lý. Khi có, thay `COMPLETED_LOANS` proxy bằng B1–B8 đầy đủ |
| **C1/C5** Thu nhập xác minh & ổn định 3–6 tháng | Open Banking / payroll API (chuỗi thời gian thu nhập) | Nâng C1 từ "chứng từ tĩnh" lên "dòng thu thực" |
| **A2/A6** Face match·liveness / AML đầy đủ (30) | Vendor eKYC + sàng lọc PEP/sanctions | Đã lưu ảnh chân dung/CCCD, cần điểm liveness từ vendor |

### Tier 4 — Hiệu chuẩn thống kê (sau pilot, ≥ 1.000–3.000 hồ sơ có outcome)

- Hệ thống **đã ghi nền dữ liệu**: `feature_snapshots` lưu toàn bộ feature mỗi lần chấm + nhãn
  `loan_outcome` khi khoản kết thúc (`recordOutcome`). Đây chính là tập train cho bước này.
- Theo thuyết minh §9: chuyển từ **policy score → statistical scorecard** (logistic/GBM có giải
  thích), tính WoE/IV, kiểm định AUC/Gini/KS, hiệu chuẩn PD theo observed default rate từng hạng,
  giám sát PSI/CSI. Áp dụng champion–challenger trước khi thay mô hình.

## 4. Quản trị mô hình (đã có một phần, theo thuyết minh §7, §9, §12)

- **Reason code (RC01–RC12)**: hiện `ScoreExplainer` đã sinh negative/positive drivers + missing
  data theo từng tiêu chí. Bước tiếp: map driver → mã RC chuẩn để xuất ra Decision API/audit.
- **Rule override fraud**: file *HIGH_RISK* → `DOCUMENT_INTEGRITY=FLAGGED` (0đ) + cảnh báo manual
  review. Cần bổ sung hard-reject rule chạy trước/sau Score Engine cho gian lận nghiêm trọng.
- **Audit/explainability**: mỗi lần chấm đã lưu `credit_scores` + `credit_score_details` +
  `feature_snapshots` (input/output/model_version). Đủ để back-test và giải trình sandbox.
- **Truyền thông**: theo §15, trong pilot gọi là *policy/hybrid score có giám sát*, không quảng bá
  "AI tự động hoàn toàn".

## 5. Tổng kết quy đổi nhóm hiện tại vs mục tiêu

| Nhóm | Điểm mục tiêu (docx) | Khả dụng hiện tại | % phủ |
|---|---|---|---|
| A · KYC | 90 | 20 | ~22% |
| B · CIC/lịch sử | 250 | 50 (proxy nội bộ) | ~20% |
| C · Affordability | 200 | 125 | ~63% |
| D · Dòng tiền | 150 | 0 (roadmap) | 0% |
| E · Nghề nghiệp | 110 | 65 | ~59% |
| F · Khoản vay | 70 | 45 | ~64% |
| G · Device | 70 | 0 (roadmap) | 0% |
| H · Fraud | 60 | 15 | ~25% |
| **Tổng** | **1.000** | **~320** | **~32%** |

Ưu tiên uplift cao nhất theo thứ tự khả thi: **D (sao kê ngân hàng AI)** → **B (CIC)** →
**G (device/app)**.
