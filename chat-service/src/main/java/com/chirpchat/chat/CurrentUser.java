package com.chirpchat.chat;

import java.security.Principal;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller as asserted by a verified access token. */
public record CurrentUser(UUID id, String username) implements Principal {

    public static CurrentUser of(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("preferred_username"));
    }

    @Override
    public String getName() {
        return username;
    }
}
