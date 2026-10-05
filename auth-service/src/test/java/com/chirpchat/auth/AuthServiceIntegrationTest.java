package com.chirpchat.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthServiceIntegrationTest.TestBeans.class)
class AuthServiceIntegrationTest {

    static final String EXTERNAL_DB = System.getenv("TEST_DATABASE_URL");
    static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_DB == null ? new PostgreSQLContainer<>("postgres:16-alpine") : null;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES != null ? POSTGRES.getJdbcUrl() : EXTERNAL_DB);
        registry.add("spring.datasource.username", () -> POSTGRES != null ? POSTGRES.getUsername()
                : System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> POSTGRES != null ? POSTGRES.getPassword()
                : System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate refresh_tokens, users cascade");
        clock.reset();
    }

    MvcResult perform(MockHttpServletRequestBuilder request, Object body, int status) throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        return result;
    }

    JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    JsonNode registerAndLogin(String username) throws Exception {
        perform(post("/auth/register"), Map.of("username", username, "displayName", "User " + username,
                "password", "correct-horse-battery"), 201);
        return body(perform(post("/auth/token"), Map.of("username", username, "password", "correct-horse-battery"), 200));
    }

    @Test
    void issuedTokensVerifyAgainstThePublishedJwks() throws Exception {
        JsonNode tokens = registerAndLogin("ada");
        assertThat(tokens.at("/token_type").asText()).isEqualTo("Bearer");
        assertThat(tokens.at("/expires_in").asLong()).isEqualTo(900);

        MvcResult jwksResult = perform(get("/.well-known/jwks.json"), null, 200);
        JWKSet jwks = JWKSet.parse(jwksResult.getResponse().getContentAsString());
        RSAKey key = jwks.getKeys().getFirst().toRSAKey();
        assertThat(key.isPrivate()).as("only the public key is published").isFalse();

        Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(tokens.at("/access_token").asText());
        assertThat(jwt.getIssuer().toString()).isEqualTo("http://auth.test");
        assertThat(jwt.getAudience()).containsExactly("chirpchat");
        assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo("ada");
        assertThat(jwt.getHeaders()).containsEntry("kid", key.getKeyID());
    }

    @Test
    void meAndUserLookupNeedAToken() throws Exception {
        String token = registerAndLogin("ada").at("/access_token").asText();
        registerAndLogin("bob");
        perform(get("/auth/me"), null, 401);
        JsonNode me = body(perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null, 200));
        assertThat(me.at("/username").asText()).isEqualTo("ada");
        JsonNode bob = body(perform(get("/auth/users/BOB").header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null, 200));
        assertThat(bob.at("/username").asText()).isEqualTo("bob");
        perform(get("/auth/users/nobody").header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null, 404);

        clock.advance(Duration.ofMinutes(16));
        perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token), null, 401);
    }

    @Test
    void registrationRulesAndUniformLoginFailures() throws Exception {
        registerAndLogin("grace");
        assertThat(body(perform(post("/auth/register"), Map.of("username", "Grace", "displayName", "G",
                "password", "another-password"), 409)).at("/code").asText()).isEqualTo("USERNAME_TAKEN");
        perform(post("/auth/register"), Map.of("username", "x", "displayName", "", "password", "short"), 400);
        JsonNode wrong = body(perform(post("/auth/token"), Map.of("username", "grace", "password", "wrong-password"), 401));
        JsonNode unknown = body(perform(post("/auth/token"), Map.of("username", "nobody", "password", "wrong-password"), 401));
        assertThat(wrong.at("/detail")).isEqualTo(unknown.at("/detail"));
        assertThat(jdbc.queryForObject("select password_hash from users", String.class)).startsWith("$2a$");
    }

    @Test
    void refreshTokensRotateAndReuseRevokesTheFamily() throws Exception {
        String first = registerAndLogin("ada").at("/refresh_token").asText();
        String second = body(perform(post("/auth/refresh"), Map.of("refreshToken", first), 200)).at("/refresh_token").asText();
        assertThat(second).isNotEqualTo(first);

        // Replaying the rotated token is treated as theft: it fails and kills the newer token too.
        assertThat(body(perform(post("/auth/refresh"), Map.of("refreshToken", first), 401)).at("/code").asText())
                .isEqualTo("INVALID_REFRESH_TOKEN");
        perform(post("/auth/refresh"), Map.of("refreshToken", second), 401);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?",
                Integer.class, AuthService.hash(first))).isEqualTo(1);
    }

    @Test
    void logoutAndExpiryEndRefresh() throws Exception {
        String token = registerAndLogin("ada").at("/refresh_token").asText();
        perform(post("/auth/logout"), Map.of("refreshToken", token), 204);
        perform(post("/auth/refresh"), Map.of("refreshToken", token), 401);
        perform(post("/auth/logout"), Map.of("refreshToken", "unknown"), 204);

        String other = registerAndLogin("bob").at("/refresh_token").asText();
        clock.advance(Duration.ofDays(31));
        perform(post("/auth/refresh"), Map.of("refreshToken", other), 401);
    }

    @Test
    void healthAndDocsArePublic() throws Exception {
        perform(get("/actuator/health"), null, 200);
        assertThat(body(perform(get("/v3/api-docs"), null, 200)).at("/paths").has("/auth/token")).isTrue();
    }

    @Test
    void configuredSigningKeysMustBePkcs8Rsa() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        var keys = new SigningKeys(new AuthProperties("i", "a", Duration.ofMinutes(1), Duration.ofDays(1), pem));
        assertThat(keys.signingKey().toRSAPublicKey()).isEqualTo(pair.getPublic());
        assertThatThrownBy(() -> SigningKeys.load("not a key")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NimbusJwtDecoder.withPublicKey(keys.signingKey().toRSAPublicKey()).build().decode("x.y.z"))
                .isInstanceOf(JwtException.class);
    }
}
