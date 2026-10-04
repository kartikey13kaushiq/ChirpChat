package com.chirpchat.auth;

import com.chirpchat.auth.AuthDtos.RegisterRequest;
import com.chirpchat.auth.AuthDtos.TokenResponse;
import com.chirpchat.auth.AuthDtos.UserView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SigningKeys keys;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyHash;

    AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder, SigningKeys keys, AuthProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    UserView register(RegisterRequest request) {
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new AuthException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is taken");
        }
        try {
            return UserView.of(users.saveAndFlush(new User(username, request.displayName().trim(),
                    passwordEncoder.encode(request.password()), clock.instant())));
        } catch (DataIntegrityViolationException raced) {
            throw new AuthException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is taken");
        }
    }

    @Transactional
    TokenResponse login(String username, String password) {
        var user = users.findByUsernameIgnoreCase(username.trim());
        // Hash something either way so unknown usernames and wrong passwords take the same time.
        boolean ok = passwordEncoder.matches(password, user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !ok) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }
        return issue(user.get(), UUID.randomUUID());
    }

    /**
     * Rotates a refresh token. Presenting one that was already rotated means it leaked (or a client
     * replayed it), so every token in its family is revoked and the caller must log in again.
     */
    @Transactional(noRollbackFor = AuthException.class)
    TokenResponse refresh(String presented) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByHash(hash(presented)).orElseThrow(AuthService::invalidRefresh);
        if (token.getRotatedAt() != null && token.getRevokedAt() == null) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw invalidRefresh();
        }
        if (!token.isUsable(now)) {
            throw invalidRefresh();
        }
        token.rotate(now);
        return issue(token.getUser(), token.getFamilyId());
    }

    @Transactional
    void logout(String presented) {
        refreshTokens.findByHash(hash(presented))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    UserView user(UUID id) {
        return users.findById(id).map(UserView::of)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
    }

    @Transactional(readOnly = true)
    UserView userByName(String username) {
        return users.findByUsernameIgnoreCase(username.trim()).map(UserView::of)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
    }

    private TokenResponse issue(User user, UUID familyId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("preferred_username", user.getUsername())
                .claim("name", user.getDisplayName())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.signingKey().getKeyID()).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        refreshTokens.save(new RefreshToken(user, familyId, hash(refreshToken),
                now.plus(properties.refreshTokenTtl()), now));
        return new TokenResponse(accessToken, "Bearer", properties.accessTokenTtl().toSeconds(), refreshToken);
    }

    private static AuthException invalidRefresh() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
