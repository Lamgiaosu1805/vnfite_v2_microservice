package com.p2plending.auth.service;

import com.p2plending.auth.config.JwtProperties;
import com.p2plending.auth.domain.entity.KycDocument;
import com.p2plending.auth.domain.entity.User;
import com.p2plending.auth.domain.enums.KycStatus;
import com.p2plending.auth.domain.repository.KycDocumentRepository;
import com.p2plending.auth.domain.repository.UserRepository;
import com.p2plending.auth.dto.request.KycSubmitRequest;
import com.p2plending.auth.dto.request.LoginRequest;
import com.p2plending.auth.dto.request.RefreshTokenRequest;
import com.p2plending.auth.dto.request.RegisterRequest;
import com.p2plending.auth.dto.response.AuthResponse;
import com.p2plending.auth.dto.response.KycDocumentResponse;
import com.p2plending.auth.exception.InvalidCredentialsException;
import com.p2plending.auth.exception.InvalidTokenException;
import com.p2plending.auth.exception.ResourceNotFoundException;
import com.p2plending.auth.exception.UserAlreadyExistsException;
import com.p2plending.auth.kafka.KafkaProducerService;
import com.p2plending.auth.mapper.KycDocumentMapper;
import com.p2plending.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    private final UserRepository          userRepository;
    private final KycDocumentRepository   kycDocumentRepository;
    private final JwtService              jwtService;
    private final PasswordEncoder         passwordEncoder;
    private final UserMapper              userMapper;
    private final KycDocumentMapper       kycDocumentMapper;
    private final KafkaProducerService    kafkaProducerService;
    private final StringRedisTemplate     redisTemplate;
    private final JwtProperties           jwtProperties;

    // ── Register ──────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered: " + request.getEmail());
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new UserAlreadyExistsException("Phone number already registered: " + request.getPhone());
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setKycStatus(KycStatus.NONE);
        User saved = userRepository.save(user);

        log.info("User registered: id={} email={} role={}", saved.getId(), saved.getEmail(), saved.getRole());
        kafkaProducerService.publishUserRegistered(saved.getId(), saved.getEmail(), saved.getFullName(), saved.getRole().name());
        return issueTokenPair(saved);
    }

    // ── Login ─────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("User authenticated: id={} email={}", user.getId(), user.getEmail());
        return issueTokenPair(user);
    }

    // ── Refresh token ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String incomingToken = request.getRefreshToken();

        if (!jwtService.isRefreshTokenValid(incomingToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        String email  = jwtService.extractSubject(incomingToken);
        String stored = redisTemplate.opsForValue().get(refreshTokenKey(email));

        if (!incomingToken.equals(stored)) {
            // Token reuse detected — revoke all sessions for this user
            redisTemplate.delete(refreshTokenKey(email));
            log.warn("Refresh token reuse detected for email={} — all sessions revoked", email);
            throw new InvalidTokenException("Refresh token reuse detected. Please log in again.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        log.info("Tokens rotated for user id={}", user.getId());
        return issueTokenPair(user);
    }

    // ── KYC submit ────────────────────────────────────────────────

    @Transactional
    public KycDocumentResponse submitKyc(Long userId, KycSubmitRequest request) {
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

        kafkaProducerService.publishKycSubmitted(userId, saved.getId(), request.getDocType());

        log.info("KYC submitted: userId={} documentId={} docType={}", userId, saved.getId(), request.getDocType());
        return kycDocumentMapper.toResponse(saved);
    }

    // ── Helper ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private AuthResponse issueTokenPair(User user) {
        UserDetails userDetails = buildUserDetails(user);
        String accessToken  = jwtService.generateAccessToken(userDetails, user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        // Overwrite previous refresh token (single active session per user)
        redisTemplate.opsForValue().set(
                refreshTokenKey(user.getEmail()),
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
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    private String refreshTokenKey(String email) {
        return REFRESH_TOKEN_PREFIX + email;
    }
}
