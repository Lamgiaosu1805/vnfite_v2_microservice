package com.p2plending.auth.service;

import com.p2plending.auth.config.JwtProperties;
import com.p2plending.auth.domain.entity.KycDocument;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.enums.Role;
import com.p2plending.auth.domain.model.PendingRegistration;
import com.p2plending.auth.domain.repository.KycDocumentRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.KycSubmitRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.OtpVerifyRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.KycDocumentResponse;
import com.p2plending.auth.dto.response.RegisterInitResponse;
import com.p2plending.auth.exception.InvalidCredentialsException;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    @Value("${app.otp.mock:false}")
    private boolean mockMode;

    private final UserRepository        userRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final JwtService            jwtService;
    private final PasswordEncoder       passwordEncoder;
    private final UserMapper            userMapper;
    private final KycDocumentMapper     kycDocumentMapper;
    private final KafkaProducerService  kafkaProducerService;
    private final StringRedisTemplate   redisTemplate;
    private final JwtProperties         jwtProperties;
    private final OtpService            otpService;

    // ── Check phone ───────────────────────────────────────────────

    public boolean isPhoneAvailable(String phone) {
        return !userRepository.existsByPhone(phone);
    }

    // ── Register step 1: validate + send OTP ─────────────────────

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
                .role(Role.USER)
                .kycStatus(KycStatus.NONE)
                .referredBy(pending.getReferrerPhone())
                .build();

        User saved = userRepository.save(user);

        log.info("User registered: id={} phone={}", saved.getId(), saved.getPhone());
        kafkaProducerService.publishUserRegistered(saved.getId(), saved.getPhone(), saved.getFullName(), saved.getRole().name());
        return issueTokenPair(saved);
    }

    // ── Login ─────────────────────────────────────────────────────

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

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiry())
                .user(userMapper.toResponse(user))
                .build();
    }

    private UserDetails buildUserDetails(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getPhone(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    private String refreshTokenKey(String phone) {
        return REFRESH_TOKEN_PREFIX + phone;
    }

}
