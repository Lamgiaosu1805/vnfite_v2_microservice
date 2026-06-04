package com.p2plending.cms.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final String ISSUER = "VNFITE CMS";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32);
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier codeVerifier;

    public TotpService() {
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setTimePeriod(30);
        verifier.setAllowedTimePeriodDiscrepancy(1); // ±1 chu kỳ dung sai lệch đồng hồ
        this.codeVerifier = verifier;
    }

    /** Sinh secret base32 ngẫu nhiên */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /** Tạo URI otpauth:// để render QR code ở frontend */
    public String getOtpAuthUrl(String secret, String username) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    /** Xác thực mã TOTP 6 chữ số */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) return false;
        return codeVerifier.isValidCode(secret, code.trim());
    }
}
