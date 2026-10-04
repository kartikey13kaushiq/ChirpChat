package com.chirpchat.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32)
            @Pattern(regexp = "[A-Za-z0-9_.]+", message = "may contain only letters, digits, '_' and '.'")
            String username,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record UserView(UUID id, String username, String displayName) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName());
        }
    }

    /** OAuth2-style token response (RFC 6749 section 5.1 field names). */
    public record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken) {
    }
}
