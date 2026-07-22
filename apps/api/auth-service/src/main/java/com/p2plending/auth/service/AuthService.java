package com.p2plending.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p2plending.auth.common.Constant;
import com.p2plending.auth.config.RedisNamespaceProperties;
import com.p2plending.auth.config.JwtProperties;
import com.p2plending.auth.domain.entity.KycDocument;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.model.DeviceSessionData;
import com.p2plending.auth.domain.model.PendingRegistration;
import com.p2plending.auth.domain.entity.DeviceLoginHistory;
import com.p2plending.auth.domain.repository.DeviceLoginHistoryRepository;
import com.p2plending.auth.domain.repository.KycDocumentRepository;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.BiometricEnableRequest;
import com.p2plending.auth.dto.request.KycSubmitRequest;
import com.p2plending.auth.dto.request.KycVerifyRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.OtpVerifyRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.DeviceSessionResponse;
import com.p2plending.auth.dto.response.KycDocumentResponse;
import com.p2plending.auth.dto.response.RegisterInitResponse;
import com.p2plending.auth.dto.response.UserProfileResponse;
import com.p2plending.auth.dto.response.vwork.ReferralResponse;
import com.p2plending.auth.exception.DeviceConflictException;
import com.p2plending.auth.exception.InvalidCredentialsException;
import com.p2plending.auth.exception.InvalidOtpException;
import com.p2plending.auth.exception.InvalidReferrerException;
import com.p2plending.auth.exception.InvalidTokenException;
import com.p2plending.auth.exception.OtpRateLimitException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import com.p2plending.auth.exception.UserAlreadyExistsException;
import com.p2plending.auth.feign.VWorkFeignService;
import com.p2plending.auth.kafka.KafkaProducerService;
import com.p2plending.auth.mapper.KycDocumentMapper;
import com.p2plending.auth.mapper.UserMapper;
import com.p2plending.auth.service.vwork.CustomerSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BIOMETRIC_ENABLE_OTP_PREFIX  = "biometric_enable:";
    private static final String BIOMETRIC_DISABLE_OTP_PREFIX = "biometric_disable:";
    private static final String BIOMETRIC_CHALLENGE_PREFIX   = "biometric_challenge:";
    private static final String DEVICE_SESSION_PREFIX  = "device_session:";
    private static final String DEVICE_RESET_OTP_PREFIX = "device_reset:";
    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final String LOGIN_LOCK_PREFIX = "login_lock:";
    private static final Duration BIOMETRIC_OTP_TTL = Duration.ofMinutes(5);
    private static final Duration BIOMETRIC_CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final Duration DEVICE_RESET_OTP_TTL = Duration.ofMinutes(10);
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final String MOCK_OTP = "000000";

    @Value("${app.otp.mock:false}")
    private boolean mockMode;

    @Value("${app.demo.excluded-user-ids:}")
    private Set<String> demoExcludedUserIds;

    private final UserRepository           userRepository;
    private final KycDocumentRepository    kycDocumentRepository;
    private final KycSubmissionRepository  kycSubmissionRepository;
    private final com.p2plending.auth.domain.repository.BusinessProfileRepository businessProfileRepository;
    private final JwtService            jwtService;
    private final PasswordEncoder       passwordEncoder;
    private final UserMapper            userMapper;
    private final KycDocumentMapper     kycDocumentMapper;
    private final KafkaProducerService  kafkaProducerService;
    private final StringRedisTemplate   redisTemplate;
    private final JwtProperties         jwtProperties;
    private final OtpService                    otpService;
    private final OtpRateLimitService           otpRateLimitService;
    private final ObjectMapper                  objectMapper;
    private final DeviceLoginHistoryRepository  deviceLoginHistoryRepository;
    private final RedisNamespaceProperties      redisNamespaceProperties;
    private final FcmTokenService               fcmTokenService;
    private final VnfOtpSenderService           vnfOtpSenderService;
    private final CustomerSyncService           customerSyncService;
    private final VWorkFeignService             vWorkFeignService;

    @Value("${spring.vwork.api-key}")
    private String apiKey;

    // ── Check phone ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isPhoneAvailable(String phone) {
        return !userRepository.existsByPhone(phone);
    }

    // ── Register step 1: validate + send OTP ─────────────────────

    @Transactional(readOnly = true)
    public RegisterInitResponse registerInit(RegisterRequest request, String clientIp) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new UserAlreadyExistsException("Số điện thoại đã được đăng ký");
        }

        if (StringUtils.hasText(request.getReferrerPhone())) {
            if (!userRepository.existsByPhone(request.getReferrerPhone())) {
                // Nếu chưa có → gọi kiểm tra CRM
                try {
                    ReferralResponse response = vWorkFeignService.checkReferral(apiKey, request.getReferrerPhone(), Constant.APP_CODE);
                    if (response == null || !Boolean.TRUE.equals(response.getExists()) || response.getInfo() == null) {
                        throw new InvalidReferrerException("Mã giới thiệu không hợp lệ");
                    }
                } catch (Exception ex) {
                    log.error("Lỗi call kiểm tra mã giới thiệu bên CRM", ex);
                }

            }
            if (request.getReferrerPhone().equals(request.getPhone())) {
                throw new InvalidReferrerException("Không thể tự giới thiệu chính mình");
            }
        }

        PendingRegistration pending = new PendingRegistration(
                request.getPhone(),
                passwordEncoder.encode(request.getPassword()),
                StringUtils.hasText(request.getReferrerPhone()) ? request.getReferrerPhone() : null,
                request.getType(),
                null
        );

        String otp = otpService.generateAndStore(pending, clientIp);

        return RegisterInitResponse.builder()
                .message("OTP đã được gửi đến số điện thoại của bạn")
                .build();
    }

    // ── Register step 2: verify OTP + create account ──────────────

    @Transactional
    public AuthResponse registerVerify(OtpVerifyRequest request) {
        PendingRegistration pending = otpService.verifyAndConsume(request.getPhone(), request.getOtp());

        if (userRepository.existsByPhone(pending.getPhone())) {
            throw new UserAlreadyExistsException("Số điện thoại đã được đăng ký");
        }

        User user = User.builder()
                .phone(pending.getPhone())
                .password(pending.getHashedPassword())
                .kycStatus(KycStatus.NONE)
                .referredBy(pending.getReferrerPhone())
                .build();

        User saved = userRepository.save(user);

        log.info("User registered: id={} phone={}", saved.getId(), saved.getPhone());
        kafkaProducerService.publishUserRegistered(saved.getId(), saved.getPhone());

        // Call VWork
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        customerSyncService.syncRegister(apiKey, saved.getId(), pending.getReferrerPhone(), pending.getType(), saved.getPhone());
                    }
                }
        );
        return issueTokenPair(saved);
    }

    // ── Login ─────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        assertLoginNotLocked(request.getPhone());

        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> {
                    recordFailedLogin(request.getPhone());
                    return new InvalidCredentialsException("Số điện thoại hoặc mật khẩu không đúng");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailedLogin(request.getPhone());
            throw new InvalidCredentialsException("Số điện thoại hoặc mật khẩu không đúng");
        }

        clearFailedLogin(request.getPhone());
        checkAndBindDevice(user.getId(), user.getPhone(), request.getDeviceKey(), request.getDeviceName(), request.getPlatform());

        log.info("User authenticated: id={} phone={}", user.getId(), user.getPhone());
        return issueTokenPair(user);
    }

    // ── Refresh token ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String incomingToken = request.getRefreshToken();

        if (!jwtService.isRefreshTokenValid(incomingToken)) {
            throw new InvalidTokenException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        String phone  = jwtService.extractSubject(incomingToken);
        String stored = redisTemplate.opsForValue().get(refreshTokenKey(phone));

        if (!incomingToken.equals(stored)) {
            redisTemplate.delete(refreshTokenKey(phone));
            log.warn("Refresh token reuse detected for phone={} — all sessions revoked", phone);
            throw new InvalidTokenException("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
        }

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        log.info("Tokens rotated for user id={}", user.getId());
        return issueTokenPair(user);
    }

    // ── Biometric enable OTP ─────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, String> initBiometric(String userId, String phone) {
        otpRateLimitService.assertCanRequest(phone);

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        String otp;
        if (mockMode) {
            otp = MOCK_OTP;
            log.info("[MOCK] Biometric enable OTP for userId={}: {}", userId, otp);
        } else {
            String sentOtp = vnfOtpSenderService.sendOtp(phone, VnfOtpSenderService.FN_BIOMETRIC);
            otp = (sentOtp != null) ? sentOtp : String.format("%06d", new SecureRandom().nextInt(1_000_000));
        }
        redisTemplate.opsForValue().set(biometricEnableOtpKey(userId), otp, BIOMETRIC_OTP_TTL);

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP đã được gửi đến số điện thoại của bạn");
        return response;
    }

    @Transactional
    public Map<String, String> verifyBiometricEnable(String userId, BiometricEnableRequest request) {
        otpRateLimitService.assertCanVerify(userId + ":biometric_enable");
        String otpKey = biometricEnableOtpKey(userId);
        String stored = redisTemplate.opsForValue().get(otpKey);
        if (stored == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện yêu cầu bật sinh trắc học");
        }
        if (!stored.equals(request.getOtp())) {
            otpRateLimitService.recordFailedVerify(userId + ":biometric_enable", otpKey);
            throw new InvalidOtpException("OTP không chính xác");
        }

        // Public key phải parse được — chặn dữ liệu rác trước khi lưu vào DB
        if (!isValidPublicKey(request.getPublicKey())) {
            throw new InvalidTokenException("Public key sinh trắc học không hợp lệ");
        }

        otpRateLimitService.clearVerifyFailures(userId + ":biometric_enable");
        redisTemplate.delete(otpKey);

        // Lưu PUBLIC key vào DB. Private key nằm trong Secure Enclave / Keystore của thiết bị,
        // không bao giờ rời máy. Đây là credential lâu dài → DB, không phải Redis.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setBiometricPublicKey(request.getPublicKey());
        userRepository.save(user);

        log.info("Biometric enabled for userId={}", userId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đăng nhập sinh trắc học đã được bật");
        return response;
    }

    // ── Biometric login: challenge–response bằng chữ ký bất đối xứng ─────────────

    /**
     * Bước 1: cấp một challenge (nonce ngẫu nhiên, one-time, TTL ngắn) để thiết bị ký.
     * Challenge nằm Redis là đúng mục đích — ephemeral; mất chỉ cần xin lại, không "thu hồi" gì.
     */
    @Transactional(readOnly = true)
    public Map<String, String> createBiometricChallenge(String phone) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getBiometricPublicKey() == null) {
            throw new InvalidTokenException(
                    "Sinh trắc học chưa được kích hoạt hoặc đã bị thu hồi. Vui lòng đăng nhập bằng mật khẩu.");
        }

        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);

        redisTemplate.opsForValue().set(biometricChallengeKey(phone), challenge, BIOMETRIC_CHALLENGE_TTL);

        log.info("Biometric challenge issued for userId={}", user.getId());
        return Map.of("challenge", challenge);
    }

    /**
     * Bước 2: verify chữ ký của challenge bằng public key đã lưu.
     * Không pass biometric ở thiết bị thì không ký được → server từ chối.
     */
    @Transactional
    public AuthResponse biometricLogin(String phone, String signature, String deviceKey,
                                       String deviceName, String platform) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getBiometricPublicKey() == null) {
            throw new InvalidTokenException(
                    "Sinh trắc học chưa được kích hoạt hoặc đã bị thu hồi. Vui lòng đăng nhập bằng mật khẩu.");
        }

        String challenge = redisTemplate.opsForValue().get(biometricChallengeKey(phone));
        if (challenge == null) {
            throw new InvalidTokenException("Phiên sinh trắc học đã hết hạn. Vui lòng thử lại.");
        }

        if (!verifySignature(user.getBiometricPublicKey(), challenge, signature)) {
            throw new InvalidTokenException("Xác thực sinh trắc học thất bại. Vui lòng đăng nhập bằng mật khẩu.");
        }

        // Challenge dùng một lần — xóa ngay để chống replay
        redisTemplate.delete(biometricChallengeKey(phone));

        checkAndBindDevice(user.getId(), phone, deviceKey, deviceName, platform);

        log.info("Biometric login success for userId={}", user.getId());
        return issueTokenPair(user);
    }

    // ── Server-side logout ────────────────────────────────────────────────────

    @Transactional
    public void serverLogout(String phone) {
        redisTemplate.delete(deviceSessionKey(phone));
        redisTemplate.delete(refreshTokenKey(phone));

        // Xóa FCM token để thiết bị không nhận push của tài khoản vừa logout
        userRepository.findByPhone(phone).ifPresent(user ->
                fcmTokenService.removeToken(user.getId()));

        log.info("Server logout completed for phone={}", phone);
    }

    @Transactional(readOnly = true)
    public Map<String, String> initBiometricDisable(String userId, String phone) {
        otpRateLimitService.assertCanRequest(phone);

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        String otp;
        if (mockMode) {
            otp = MOCK_OTP;
            log.info("[MOCK] Biometric disable OTP for userId={}: {}", userId, otp);
        } else {
            String sentOtp = vnfOtpSenderService.sendOtp(phone, VnfOtpSenderService.FN_BIOMETRIC);
            otp = (sentOtp != null) ? sentOtp : String.format("%06d", new SecureRandom().nextInt(1_000_000));
        }
        redisTemplate.opsForValue().set(biometricDisableOtpKey(userId), otp, BIOMETRIC_OTP_TTL);

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP đã được gửi đến số điện thoại của bạn");
        return response;
    }

    @Transactional
    public Map<String, String> verifyBiometricDisable(String userId, KycVerifyRequest request) {
        otpRateLimitService.assertCanVerify(userId + ":biometric_disable");
        String otpKey = biometricDisableOtpKey(userId);
        String stored = redisTemplate.opsForValue().get(otpKey);
        if (stored == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện yêu cầu tắt sinh trắc học");
        }
        if (!stored.equals(request.getOtp())) {
            otpRateLimitService.recordFailedVerify(userId + ":biometric_disable", otpKey);
            throw new InvalidOtpException("OTP không chính xác");
        }

        otpRateLimitService.clearVerifyFailures(userId + ":biometric_disable");
        redisTemplate.delete(otpKey);

        // Xóa public key khỏi DB → vô hiệu hóa biometric login
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setBiometricPublicKey(null);
        userRepository.save(user);

        log.info("Biometric disabled for userId={}", userId);
        return Map.of("message", "Đăng nhập sinh trắc học đã được tắt");
    }

    // ── KYC submit ────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse submitKyc(String userId, KycSubmitRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        KycDocument document = KycDocument.builder()
                .userId(userId)
                .docType(request.getDocType())
                .docUrl(request.getDocUrl())
                .status(KycStatus.PENDING)
                .build();

        KycDocument saved = kycDocumentRepository.save(document);

        user.setKycStatus(KycStatus.PENDING);
        userRepository.save(user);

        kafkaProducerService.publishKycSubmitted(userId, saved.getId());

        log.info("KYC submitted: userId={} documentId={} docType={}", userId, saved.getId(), request.getDocType());
        return kycDocumentMapper.toResponse(saved);
    }

    // ── Profile ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        var builder = UserProfileResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .kycStatus(user.getKycStatus())
                .accountType(user.getAccountType())
                .createdAt(user.getCreatedAt());

        businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .ifPresent(bp -> builder
                        .businessProfileStatus(bp.getStatus())
                        .businessType(bp.getBusinessType())
                        .businessName(bp.getBusinessName())
                        .businessRejectReason(bp.getRejectReason())
                );

        kycSubmissionRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, user.getKycStatus())
                .ifPresent(kyc -> builder
                        .fullName(kyc.getFullName())
                        .gender(kyc.getGender())
                        .cccdNumber(kyc.getCccdNumber())
                        .dateOfBirth(kyc.getDateOfBirth())
                        .permanentAddress(kyc.getPermanentAddress())
                        .hometown(kyc.getHometown())
                        .issueDate(kyc.getIssueDate())
                        .issuingAuthority(kyc.getIssuingAuthority())
                        .expiryDate(kyc.getExpiryDate())
                        .kycSubmissionStatus(kyc.getStatus())
                        .kycSubmittedAt(kyc.getCreatedAt())
                );

        return builder.build();
    }

    // ── Helper ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String getUserIdByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + phone));
    }

    private AuthResponse issueTokenPair(User user) {
        UserDetails userDetails = buildUserDetails(user);
        String accessToken  = jwtService.generateAccessToken(userDetails, user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getPhone());

        redisTemplate.opsForValue().set(
                refreshTokenKey(user.getPhone()),
                refreshToken,
                Duration.ofSeconds(jwtProperties.getRefreshTokenExpiry())
        );

        // fullName lấy từ KYC submission APPROVED mới nhất (source of truth)
        var userResponse = userMapper.toResponse(user);
        kycSubmissionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), KycStatus.APPROVED)
                .ifPresent(kyc -> userResponse.setFullName(kyc.getFullName()));

        // Hồ sơ doanh nghiệp — cần có ngay ở login/refresh để app hiện được toggle ví doanh nghiệp
        businessProfileRepository.findTopByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId())
                .ifPresent(bp -> {
                    userResponse.setBusinessProfileStatus(bp.getStatus());
                    userResponse.setBusinessType(bp.getBusinessType());
                    userResponse.setBusinessName(bp.getBusinessName());
                    userResponse.setBusinessRejectReason(bp.getRejectReason());
                });

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiry())
                .user(userResponse)
                .build();
    }

    private UserDetails buildUserDetails(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getPhone(),
                user.getPassword(),
                List.of()
        );
    }

    private String refreshTokenKey(String phone) {
        return redisNamespaceProperties.qualify(REFRESH_TOKEN_PREFIX + phone);
    }

    private String biometricEnableOtpKey(String userId) {
        return redisNamespaceProperties.qualify(BIOMETRIC_ENABLE_OTP_PREFIX + userId);
    }

    private String biometricDisableOtpKey(String userId) {
        return redisNamespaceProperties.qualify(BIOMETRIC_DISABLE_OTP_PREFIX + userId);
    }

    private String biometricChallengeKey(String phone) {
        return redisNamespaceProperties.qualify(BIOMETRIC_CHALLENGE_PREFIX + phone);
    }

    /** Kiểm tra public key base64 (X.509 SPKI) có parse được thành RSA key không. */
    private boolean isValidPublicKey(String publicKeyBase64) {
        try {
            parsePublicKey(publicKeyBase64);
            return true;
        } catch (Exception e) {
            log.warn("Invalid biometric public key submitted: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verify chữ ký SHA256withRSA: payload (challenge) được thiết bị ký bằng private key,
     * server verify bằng public key đã lưu.
     */
    private boolean verifySignature(String publicKeyBase64, String payload, String signatureBase64) {
        try {
            PublicKey publicKey = parsePublicKey(publicKeyBase64);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            log.warn("Biometric signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private PublicKey parsePublicKey(String publicKeyBase64) throws Exception {
        byte[] der = Base64.getDecoder().decode(publicKeyBase64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private String deviceSessionKey(String phone) {
        return redisNamespaceProperties.qualify(DEVICE_SESSION_PREFIX + phone);
    }

    private String deviceResetOtpKey(String phone) {
        return redisNamespaceProperties.qualify(DEVICE_RESET_OTP_PREFIX + phone);
    }

    private void assertLoginNotLocked(String phone) {
        if (!StringUtils.hasText(phone)) return;
        Long ttl = redisTemplate.getExpire(loginLockKey(phone), java.util.concurrent.TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            throw new OtpRateLimitException(
                    "Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau " + ttl + " giây.",
                    ttl);
        }
    }

    private void recordFailedLogin(String phone) {
        if (!StringUtils.hasText(phone)) return;

        String failKey = loginFailKey(phone);
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count == null) count = 1L;
        if (count == 1L) {
            redisTemplate.expire(failKey, LOGIN_FAIL_WINDOW);
        }
        if (count >= MAX_LOGIN_FAILURES) {
            redisTemplate.delete(failKey);
            redisTemplate.opsForValue().set(loginLockKey(phone), "1", LOGIN_LOCK_TTL);
            throw new OtpRateLimitException(
                    "Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau "
                            + LOGIN_LOCK_TTL.toSeconds() + " giây.",
                    LOGIN_LOCK_TTL.toSeconds());
        }
    }

    private void clearFailedLogin(String phone) {
        if (StringUtils.hasText(phone)) {
            redisTemplate.delete(loginFailKey(phone));
        }
    }

    private String loginFailKey(String phone) {
        return redisNamespaceProperties.qualify(LOGIN_FAIL_PREFIX + phone.trim());
    }

    private String loginLockKey(String phone) {
        return redisNamespaceProperties.qualify(LOGIN_LOCK_PREFIX + phone.trim());
    }

    /**
     * Kiểm tra single-device, gắn device session vào Redis và ghi lịch sử vào DB.
     * - Nếu chưa có session → lưu DeviceSessionData (không TTL) và cho qua
     * - Nếu session tồn tại và deviceKey khớp → thiết bị này đang login lại, cập nhật loginAt
     * - Nếu session tồn tại và deviceKey KHÔNG khớp → DEVICE_CONFLICT
     */
    private void checkAndBindDevice(String userId, String phone, String deviceKey, String deviceName, String platform) {
        // Demo account: bỏ qua device conflict, cho phép đăng nhập song song nhiều thiết bị
        if (demoExcludedUserIds.contains(userId)) {
            log.info("Demo account {} — skipping device binding", userId);
            return;
        }

        String stored = redisTemplate.opsForValue().get(deviceSessionKey(phone));
        String resolvedDeviceKey = StringUtils.hasText(deviceKey) ? deviceKey : UUID.randomUUID().toString();

        if (stored != null) {
            String existingDeviceKey = parseDeviceKey(stored);
            boolean sameDevice = existingDeviceKey != null && existingDeviceKey.equals(resolvedDeviceKey);
            if (!sameDevice) {
                throw new DeviceConflictException(
                        "Tài khoản đang đăng nhập trên một thiết bị khác. Vui lòng đăng xuất thiết bị đó, hoặc thực hiện đặt lại thiết bị bằng CCCD.");
            }
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        // Ghi/cập nhật session data vào Redis (deviceKey khớp hoặc session mới)
        DeviceSessionData data = DeviceSessionData.builder()
                .deviceKey(resolvedDeviceKey)
                .deviceName(StringUtils.hasText(deviceName) ? deviceName : "Thiết bị không xác định")
                .platform(StringUtils.hasText(platform) ? platform : "unknown")
                .loginAt(now.toString())
                .build();

        try {
            redisTemplate.opsForValue().set(deviceSessionKey(phone), objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            redisTemplate.opsForValue().set(deviceSessionKey(phone), resolvedDeviceKey);
            log.warn("Failed to serialize device session data for phone={}", phone, e);
        }

        // Ghi lịch sử vào DB (mỗi lần login tạo 1 record)
        DeviceLoginHistory history = DeviceLoginHistory.builder()
                .userId(userId)
                .deviceKey(resolvedDeviceKey)
                .deviceName(StringUtils.hasText(deviceName) ? deviceName : "Thiết bị không xác định")
                .platform(StringUtils.hasText(platform) ? platform : "unknown")
                .loginAt(now)
                .build();
        deviceLoginHistoryRepository.save(history);
    }

    /**
     * Trả về danh sách thiết bị đã từng đăng nhập, mỗi deviceKey chỉ xuất hiện 1 lần
     * (lấy lần login gần nhất), sắp xếp theo thời gian login mới nhất.
     * Thiết bị đang giữ phiên hiện tại được đánh dấu current=true.
     */
    @Transactional(readOnly = true)
    public List<DeviceSessionResponse> getDeviceHistory(String userId, String phone) {
        // 1. Lấy deviceKey đang active từ Redis
        String activeDeviceKey = null;
        String stored = redisTemplate.opsForValue().get(deviceSessionKey(phone));
        if (stored != null) {
            activeDeviceKey = parseDeviceKey(stored);
        }

        // 2. Lấy 100 bản ghi gần nhất rồi deduplicate theo deviceKey
        List<DeviceLoginHistory> records =
                deviceLoginHistoryRepository.findTop100ByUserIdAndIsDeletedFalseOrderByLoginAtDesc(userId);

        LinkedHashMap<String, DeviceLoginHistory> latestPerDevice = new LinkedHashMap<>();
        for (DeviceLoginHistory r : records) {
            latestPerDevice.putIfAbsent(r.getDeviceKey(), r); // giữ bản ghi đầu tiên (mới nhất)
        }

        String finalActiveDeviceKey = activeDeviceKey;
        return new ArrayList<>(latestPerDevice.values()).stream()
                .limit(15) // tối đa 15 thiết bị khác nhau
                .map(r -> DeviceSessionResponse.builder()
                        .deviceName(r.getDeviceName())
                        .platform(r.getPlatform())
                        .loginAt(r.getLoginAt())
                        .current(r.getDeviceKey().equals(finalActiveDeviceKey))
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    /** Trả về thông tin thiết bị đang đăng nhập của user, hoặc empty nếu chưa có session. */
    @Transactional(readOnly = true)
    public Optional<DeviceSessionData> getActiveDevice(String phone) {
        String stored = redisTemplate.opsForValue().get(deviceSessionKey(phone));
        if (stored == null) return Optional.empty();

        try {
            return Optional.of(objectMapper.readValue(stored, DeviceSessionData.class));
        } catch (Exception e) {
            // Backward compat: stored value là plain deviceKey (format cũ)
            DeviceSessionData legacy = DeviceSessionData.builder()
                    .deviceKey(stored)
                    .deviceName("Thiết bị đã đăng nhập")
                    .platform("unknown")
                    .loginAt(null)
                    .build();
            return Optional.of(legacy);
        }
    }

    /** Trích xuất deviceKey từ stored value (có thể là JSON hoặc plain string). */
    private String parseDeviceKey(String stored) {
        try {
            DeviceSessionData data = objectMapper.readValue(stored, DeviceSessionData.class);
            return data.getDeviceKey();
        } catch (Exception e) {
            // Plain string format (cũ)
            return stored;
        }
    }

    // ── Device Reset (xác minh qua CCCD để đặt lại session) ──────────────────

    @Transactional(readOnly = true)
    public Map<String, String> initDeviceReset(String phone, String cccdNumber, String issueDateStr, String clientIp) {
        otpRateLimitService.assertCanRegisterRequest(phone, clientIp);

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new InvalidCredentialsException("Số điện thoại không tồn tại trong hệ thống"));

        LocalDate issueDate;
        try {
            issueDate = LocalDate.parse(issueDateStr);
        } catch (Exception e) {
            throw new InvalidCredentialsException("Ngày cấp không hợp lệ (định dạng yyyy-MM-dd)");
        }

        // Xác minh thông tin CCCD từ KYC submission đã duyệt
        var kycOpt = kycSubmissionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), KycStatus.APPROVED);
        if (kycOpt.isEmpty()
                || !kycOpt.get().getCccdNumber().equals(cccdNumber)
                || !kycOpt.get().getIssueDate().equals(issueDate)) {
            throw new InvalidCredentialsException("Thông tin CCCD không khớp hoặc tài khoản chưa xác minh danh tính");
        }

        String otp;
        if (mockMode) {
            otp = MOCK_OTP;
            log.info("[MOCK] Device reset OTP for phone={}: {}", phone, otp);
        } else {
            String sentOtp = vnfOtpSenderService.sendOtp(phone, VnfOtpSenderService.FN_DEVICE_RESET);
            if (!StringUtils.hasText(sentOtp)) {
                throw new InvalidOtpException("Không gửi được OTP đặt lại thiết bị. Vui lòng thử lại sau.");
            }
            otp = sentOtp;
        }
        redisTemplate.opsForValue().set(deviceResetOtpKey(phone), otp, DEVICE_RESET_OTP_TTL);

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP đã được gửi đến số điện thoại đăng ký");
        return response;
    }

    @Transactional
    public Map<String, String> verifyDeviceReset(String phone, String otp) {
        otpRateLimitService.assertCanVerify(phone + ":device_reset");
        String otpKey = deviceResetOtpKey(phone);
        String stored = redisTemplate.opsForValue().get(otpKey);
        if (stored == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện yêu cầu đặt lại thiết bị");
        }
        if (!stored.equals(otp)) {
            otpRateLimitService.recordFailedVerify(phone + ":device_reset", otpKey);
            throw new InvalidOtpException("OTP không chính xác");
        }

        otpRateLimitService.clearVerifyFailures(phone + ":device_reset");
        redisTemplate.delete(otpKey);
        // Xóa toàn bộ session và device binding trong Redis
        redisTemplate.delete(refreshTokenKey(phone));
        redisTemplate.delete(deviceSessionKey(phone));
        redisTemplate.delete(biometricChallengeKey(phone));
        // Xóa public key sinh trắc học trong DB → buộc đăng nhập lại từ đầu (reset từ thiết bị khác)
        userRepository.findByPhone(phone).ifPresent(u -> {
            u.setBiometricPublicKey(null);
            userRepository.save(u);
        });

        log.info("Device reset verified for phone={} — all sessions revoked", phone);
        return Map.of("message", "Đặt lại thiết bị thành công. Vui lòng đăng nhập lại bằng mật khẩu.");
    }

}
