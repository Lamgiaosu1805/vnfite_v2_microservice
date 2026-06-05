package com.p2plending.auth.service;

import com.p2plending.auth.config.JwtProperties;
import com.p2plending.auth.domain.entity.KycDocument;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.model.PendingRegistration;
import com.p2plending.auth.domain.repository.KycDocumentRepository;
import com.p2plending.auth.domain.repository.KycSubmissionRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.KycSubmitRequest;
import com.p2plending.auth.dto.request.KycVerifyRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.OtpVerifyRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.KycDocumentResponse;
import com.p2plending.auth.dto.response.RegisterInitResponse;
import com.p2plending.auth.exception.InvalidCredentialsException;
import com.p2plending.auth.exception.InvalidOtpException;
import com.p2plending.auth.exception.InvalidReferrerException;
import com.p2plending.auth.exception.InvalidTokenException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import com.p2plending.auth.exception.UserAlreadyExistsException;
import com.p2plending.auth.kafka.KafkaProducerService;
import com.p2plending.auth.mapper.KycDocumentMapper;
import com.p2plending.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BIOMETRIC_ENABLE_OTP_PREFIX = "biometric_enable:";
    private static final String BIOMETRIC_DISABLE_OTP_PREFIX = "biometric_disable:";
    private static final Duration BIOMETRIC_OTP_TTL = Duration.ofMinutes(5);
    private static final String MOCK_OTP = "000000";

    @Value("${app.otp.mock:false}")
    private boolean mockMode;

    private final UserRepository           userRepository;
    private final KycDocumentRepository    kycDocumentRepository;
    private final KycSubmissionRepository  kycSubmissionRepository;
    private final JwtService            jwtService;
    private final PasswordEncoder       passwordEncoder;
    private final UserMapper            userMapper;
    private final KycDocumentMapper     kycDocumentMapper;
    private final KafkaProducerService  kafkaProducerService;
    private final StringRedisTemplate   redisTemplate;
    private final JwtProperties         jwtProperties;
    private final OtpService            otpService;
    private final OtpRateLimitService   otpRateLimitService;

    // ── Check phone ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isPhoneAvailable(String phone) {
        return !userRepository.existsByPhone(phone);
    }

    // ── Register step 1: validate + send OTP ─────────────────────

    @Transactional(readOnly = true)
    public RegisterInitResponse registerInit(RegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new UserAlreadyExistsException("Số điện thoại đã được đăng ký");
        }

        if (StringUtils.hasText(request.getReferrerPhone())) {
            if (!userRepository.existsByPhone(request.getReferrerPhone())) {
                throw new InvalidReferrerException("Mã giới thiệu không hợp lệ");
            }
            if (request.getReferrerPhone().equals(request.getPhone())) {
                throw new InvalidReferrerException("Không thể tự giới thiệu chính mình");
            }
        }

        PendingRegistration pending = new PendingRegistration(
                request.getPhone(),
                passwordEncoder.encode(request.getPassword()),
                StringUtils.hasText(request.getReferrerPhone()) ? request.getReferrerPhone() : null,
                null
        );

        String otp = otpService.generateAndStore(pending);

        return RegisterInitResponse.builder()
                .message("OTP đã được gửi đến số điện thoại của bạn")
                .otp(mockMode ? otp : null)
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
        return issueTokenPair(saved);
    }

    // ── Login ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new InvalidCredentialsException("Số điện thoại hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Số điện thoại hoặc mật khẩu không đúng");
        }

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

        String otp = mockMode ? MOCK_OTP
                : String.format("%06d", new SecureRandom().nextInt(1_000_000));

        redisTemplate.opsForValue().set(biometricEnableOtpKey(userId), otp, BIOMETRIC_OTP_TTL);

        if (mockMode) {
            log.info("[MOCK] Biometric enable OTP for userId={}: {}", userId, otp);
        } else {
            // TODO: gửi OTP qua SMS / Zalo ZNS
            log.info("Biometric enable OTP sent for userId={} phone={}", userId, phone);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP đã được gửi đến số điện thoại của bạn");
        if (mockMode) response.put("otp", otp);
        return response;
    }

    @Transactional
    public Map<String, String> verifyBiometricEnable(String userId, KycVerifyRequest request) {
        String stored = redisTemplate.opsForValue().get(biometricEnableOtpKey(userId));
        if (stored == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện yêu cầu bật sinh trắc học");
        }
        if (!stored.equals(request.getOtp())) {
            throw new InvalidOtpException("OTP không chính xác");
        }

        redisTemplate.delete(biometricEnableOtpKey(userId));
        log.info("Biometric enable OTP verified for userId={}", userId);
        return Map.of("message", "Xác thực OTP thành công");
    }

    @Transactional(readOnly = true)
    public Map<String, String> initBiometricDisable(String userId, String phone) {
        otpRateLimitService.assertCanRequest(phone);

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        String otp = mockMode ? MOCK_OTP
                : String.format("%06d", new SecureRandom().nextInt(1_000_000));

        redisTemplate.opsForValue().set(biometricDisableOtpKey(userId), otp, BIOMETRIC_OTP_TTL);

        if (mockMode) {
            log.info("[MOCK] Biometric disable OTP for userId={}: {}", userId, otp);
        } else {
            // TODO: gửi OTP qua SMS / Zalo ZNS
            log.info("Biometric disable OTP sent for userId={} phone={}", userId, phone);
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "OTP đã được gửi đến số điện thoại của bạn");
        if (mockMode) response.put("otp", otp);
        return response;
    }

    @Transactional
    public Map<String, String> verifyBiometricDisable(String userId, KycVerifyRequest request) {
        String stored = redisTemplate.opsForValue().get(biometricDisableOtpKey(userId));
        if (stored == null) {
            throw new InvalidOtpException("OTP đã hết hạn hoặc chưa thực hiện yêu cầu tắt sinh trắc học");
        }
        if (!stored.equals(request.getOtp())) {
            throw new InvalidOtpException("OTP không chính xác");
        }

        redisTemplate.delete(biometricDisableOtpKey(userId));
        log.info("Biometric disable OTP verified for userId={}", userId);
        return Map.of("message", "Xác thực OTP thành công");
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
        return REFRESH_TOKEN_PREFIX + phone;
    }

    private String biometricEnableOtpKey(String userId) {
        return BIOMETRIC_ENABLE_OTP_PREFIX + userId;
    }

    private String biometricDisableOtpKey(String userId) {
        return BIOMETRIC_DISABLE_OTP_PREFIX + userId;
    }

}
