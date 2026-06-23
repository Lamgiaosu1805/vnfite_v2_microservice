# VNFITE withdrawal cutover (6666 -> payment-service)

## Boundary

- `42.113.122.155:6666`: VNFITE legacy, continues running during parallel UAT.
- `42.113.122.155:8888`: MB-whitelisted account-service gateway.
- Server `118`: new VNFITE `payment-service`; it never calls MB directly.
- `source=VNFITE` selects debit account `6966638888`, channel `YFCH`, and VNFITE remarks.
- `source=VNFFITE_CAPITAL`/`6RCH` remains unchanged and is never relayed to the new system.

## New withdrawal flow

1. Mobile calls `POST /api/payment/wallet/withdrawal/initiate`.
2. Mobile confirms OTP at `POST /api/payment/wallet/withdrawal/{id}/confirm-otp`.
3. `payment-service` locks the local wallet and writes an internal `transfer_ref` before HTTP.
4. `payment-service` calls `POST http://42.113.122.155:8888/api/v1/transfer-money` with:
   - `source=VNFITE`
   - `accNo=VNF...`
   - `clientReference=<internal transfer_ref>`
5. `8888` calls MB from the whitelisted IP and returns the generated `YFCH...` reference.
6. MB callback reaches `8888`. Only a transaction carrying `clientReference` is relayed to the new system, so legacy `6666` transactions are not relayed.
7. On MB success, `payment-service` calls the idempotent balance-adjustment endpoint on `8888`, then completes the local withdrawal and releases the local lock.
8. If callback relay fails, the payment scheduler queries `/api/v1/get-transaction` through `8888`. Unknown/timeout status remains `PROCESSING`; it never blindly sends another transfer.

## Required runtime variables

Set only on the `8888` account-service process:

```bash
VNFITE_NEW_PAYMENT_URL=http://42.113.122.118:7080
VNFITE_NEW_CALLBACK_SECRET=<shared-random-secret>
```

Keep both variables empty on the `6666` process.

Set on the new `payment-service`:

```bash
APP_PAYMENT_MOCK=false
TIKLUY_BASE_URL=http://42.113.122.155:8888
TIKLUY_SOURCE=VNFITE
TIKLUY_CALLBACK_SECRET=<same-shared-random-secret>
TIKLUY_CALLBACK_ALLOWED_IPS=42.113.122.155,127.0.0.1
```

## Deployment order

1. Back up the account database and `payment_db`.
2. Deploy the additive account-service build to `8888` only. Do not restart or reconfigure `6666`.
3. Confirm `8888` health and confirm `6666` is still healthy.
4. Deploy new `payment-service` and nginx route on server `118`; let Flyway apply payment migration `V7`.
5. Configure the shared callback secret on both sides.
6. Use one dedicated low-balance UAT account and one minimum-value withdrawal.
7. Verify `withdrawal_requests.provider_transfer_ref`, wallet transaction status, TIKLUY `CLIENT_REFERENCE`, and MB FT number.

## Parallel-run restriction

During UAT, a single VNF account must be operated from only one app generation at a time. The old and new systems have separate local lock ledgers; `payment-service` protects migrated locks by using the greater of provider and local locked balances, but simultaneous new locks from both apps are not a supported test scenario.

## Rollback

- Set `APP_PAYMENT_MOCK=true` or disable the withdrawal UI in the new app.
- Clear `VNFITE_NEW_PAYMENT_URL` on `8888` to stop relay.
- Keep `6666` running; no API contract used by it is removed or changed.
- Do not delete withdrawal rows. Resolve `PROCESSING` transactions only after checking the `YFCH` status/FT at MB.
