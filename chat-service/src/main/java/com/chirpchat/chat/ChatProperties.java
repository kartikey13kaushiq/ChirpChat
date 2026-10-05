package com.chirpchat.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chat.auth")
public record ChatProperties(String issuer, String audience, String jwkSetUri, String baseUrl) {
}
