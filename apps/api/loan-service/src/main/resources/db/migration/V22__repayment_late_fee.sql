-- Phí phạt trả chậm cho từng kỳ. Job DPD tính lại hằng ngày dựa trên số ngày quá hạn (dpd)
-- và lãi suất phạt = lãi suất khoản × lateFeeRate% (mặc định 150%).
-- late_fee      = tổng phí phạt đã tính tới hiện tại của kỳ.
-- late_fee_paid = phần phí phạt người gọi vốn đã trả (để hỗ trợ trả từng phần + idempotent).
-- Không đụng dữ liệu hiện có; mặc định 0 cho mọi kỳ đang có.
ALTER TABLE repayment_schedule
  ADD COLUMN late_fee      DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER paid_amount,
  ADD COLUMN late_fee_paid DECIMAL(15,2) NOT NULL DEFAULT 0 AFTER late_fee;
