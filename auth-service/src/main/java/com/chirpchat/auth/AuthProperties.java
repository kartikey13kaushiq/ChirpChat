package com.chirpchat.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth")
public record AuthProperties(String issuer, String audience, Duration accessTokenTtl, Duration refreshTokenTtl,
        String signingKey) {
}
