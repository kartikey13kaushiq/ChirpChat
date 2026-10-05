package com.chirpchat.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Stub upstreams that echo which service answered, the path, and the forwarded request id. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingTest {

    static final HttpServer AUTH = stub("auth");
    static final HttpServer CHAT = stub("chat");

    static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = (name + " " + exchange.getRequestURI() + " "
                        + exchange.getRequestHeaders().getFirst("X-Request-Id")).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() {
        AUTH.stop(0);
        CHAT.stop(0);
    }

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> "http://127.0.0.1:" + AUTH.getAddress().getPort());
        registry.add("CHAT_SERVICE_URL", () -> "http://127.0.0.1:" + CHAT.getAddress().getPort());
    }

    @Autowired WebTestClient client;

    String get(String path, String requestId) {
        var spec = client.get().uri(path);
        if (requestId != null) {
            spec = spec.header("X-Request-Id", requestId);
        }
        return spec.exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody(String.class).returnResult().getResponseBody();
    }

    @Test
    void routesByPathPrefix() {
        assertThat(get("/auth/me", null)).startsWith("auth /auth/me ");
        assertThat(get("/.well-known/jwks.json", null)).startsWith("auth /.well-known/jwks.json ");
        assertThat(get("/api/channels?x=1", null)).startsWith("chat /api/channels?x=1 ");
        client.get().uri("/elsewhere").exchange().expectStatus().isNotFound();
    }

    @Test
    void requestIdsArePropagatedAndEchoed() {
        String body = client.get().uri("/api/channels").exchange()
                .expectHeader().exists("X-Request-Id")
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body.split(" ")[2]).hasSizeGreaterThanOrEqualTo(36);

        assertThat(get("/api/channels", "trace-12345678")).endsWith(" trace-12345678");
        assertThat(get("/api/channels", "bad id <script>")).doesNotContain("script");
    }

    @Test
    void exposesHealth() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
