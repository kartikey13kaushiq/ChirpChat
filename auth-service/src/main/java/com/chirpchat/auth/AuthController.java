package com.chirpchat.auth;

import com.chirpchat.auth.AuthDtos.RefreshRequest;
import com.chirpchat.auth.AuthDtos.RegisterRequest;
import com.chirpchat.auth.AuthDtos.TokenRequest;
import com.chirpchat.auth.AuthDtos.TokenResponse;
import com.chirpchat.auth.AuthDtos.UserView;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    private final AuthService auth;
    private final SigningKeys keys;

    AuthController(AuthService auth, SigningKeys keys) {
        this.auth = auth;
        this.keys = keys;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserView register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/auth/token")
    ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        return noStore(auth.login(request.username(), request.password()));
    }

    @PostMapping("/auth/refresh")
    ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return noStore(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }

    @GetMapping("/auth/me")
    UserView me(@AuthenticationPrincipal Jwt jwt) {
        return auth.user(UUID.fromString(jwt.getSubject()));
    }

    /** User directory lookup for other services (called with the end user's token). */
    @GetMapping("/auth/users/{username}")
    UserView user(@PathVariable String username) {
        return auth.userByName(username);
    }

    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)))
                .body(keys.publicKeys().toJSONObject());
    }

    private static ResponseEntity<TokenResponse> noStore(TokenResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
