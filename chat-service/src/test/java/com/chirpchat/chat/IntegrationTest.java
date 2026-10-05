package com.chirpchat.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs the service against PostgreSQL (Testcontainers, or TEST_DATABASE_URL). Tokens are minted with a
 * test RSA key standing in for the auth service, and the user directory is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestKeys.class)
abstract class IntegrationTest {

    static final String EXTERNAL_DB = System.getenv("TEST_DATABASE_URL");
    static final PostgreSQLContainer<?> POSTGRES = EXTERNAL_DB == null ? new PostgreSQLContainer<>("postgres:16-alpine") : null;
    static final RSAKey KEY;
    static final RSAKey OTHER_KEY;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test").generate();
            OTHER_KEY = new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
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
    static class TestKeys {
        /** Same validation rules as production, with the test key instead of a remote JWKS. */
        @Bean
        JwtDecoder jwtDecoder(ChatProperties properties, Clock clock) throws Exception {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(SecurityConfig.validator(properties, clock));
            return decoder;
        }
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @MockitoBean protected UserDirectory directory;
    @LocalServerPort protected int port;

    protected record User(UUID id, String username, String token) {
    }

    @BeforeEach
    void clean() {
        jdbc.execute("truncate messages, channel_members, channels cascade");
    }

    protected User user(String username) {
        UUID id = UUID.randomUUID();
        return new User(id, username, token(KEY, id, username, "http://auth.test", "chirpchat", Duration.ofMinutes(15)));
    }

    protected static String token(RSAKey key, UUID id, String username, String issuer, String audience, Duration ttl) {
        Instant now = Instant.now();
        Instant expires = now.plus(ttl);
        Instant issued = expires.isAfter(now) ? now.minusSeconds(5) : expires.minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(id.toString()).audience(List.of(audience))
                .issuedAt(issued).expiresAt(expires).claim("preferred_username", username).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(), claims))
                .getTokenValue();
    }

    protected MvcResult call(User u, MockHttpServletRequestBuilder request, Object body, int status) throws Exception {
        if (u != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + u.token());
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        MvcResult result = mvc.perform(request).andReturn();
        if (result.getResponse().getStatus() != status) {
            throw new AssertionError("Expected HTTP " + status + " but got " + result.getResponse().getStatus() + ": "
                    + result.getResponse().getContentAsString());
        }
        return result;
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
