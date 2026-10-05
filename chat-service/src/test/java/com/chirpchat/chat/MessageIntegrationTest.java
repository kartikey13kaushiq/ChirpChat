package com.chirpchat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageIntegrationTest extends IntegrationTest {

    String channel(User owner, User... joiners) throws Exception {
        String id = body(call(owner, post("/api/channels"), Map.of("name", "general", "isPrivate", false), 201)).at("/id").asText();
        for (User u : joiners) {
            call(u, post("/api/channels/" + id + "/join"), null, 200);
        }
        return id;
    }

    @Test
    void postingRequiresMembership() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        String id = channel(ada);
        JsonNode denied = body(call(bob, post("/api/channels/" + id + "/messages"), Map.of("body", "hi"), 403));
        assertThat(denied.at("/detail").asText()).contains("Join");
        call(bob, get("/api/channels/" + id + "/messages"), null, 403);
        call(ada, post("/api/channels/" + id + "/messages"), Map.of("body", " "), 400);
    }

    @Test
    void historyPagesOldestFirst() throws Exception {
        User ada = user("ada");
        String id = channel(ada);
        for (int i = 1; i <= 5; i++) {
            call(ada, post("/api/channels/" + id + "/messages"), Map.of("body", "m" + i), 201);
        }
        JsonNode latest = body(call(ada, get("/api/channels/" + id + "/messages").param("limit", "2"), null, 200));
        assertThat(latest.at("/messages").findValuesAsText("body")).containsExactly("m4", "m5");
        JsonNode older = body(call(ada, get("/api/channels/" + id + "/messages").param("limit", "2")
                .param("before", latest.at("/nextBefore").asText()), null, 200));
        assertThat(older.at("/messages").findValuesAsText("body")).containsExactly("m2", "m3");
        JsonNode all = body(call(ada, get("/api/channels/" + id + "/messages"), null, 200));
        assertThat(all.at("/messages").size()).isEqualTo(5);
        assertThat(all.at("/nextBefore").isNull()).isTrue();
        assertThat(all.at("/messages/0/authorUsername").asText()).isEqualTo("ada");
    }

    @Test
    void mentionsResolveToMembersOnly() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        User cat = user("Cat");
        String id = channel(ada, bob, cat);
        JsonNode msg = body(call(ada, post("/api/channels/" + id + "/messages"),
                Map.of("body", "@bob and @cat please review; cc @ada @nobody, mail me at ada@example.com"), 201));
        assertThat(msg.at("/mentions").toString()).isEqualTo("[\"bob\",\"Cat\"]");
        assertThat(MessageService.mentions("hey @Bob, @bob! email x@y.com @ab")).containsExactly("bob");
    }

    @Test
    void tokensAreCheckedForSignatureIssuerAudienceAndExpiry() throws Exception {
        UUID id = UUID.randomUUID();
        call(null, get("/api/channels"), null, 401);
        for (String bad : new String[] {
                token(OTHER_KEY, id, "eve", "http://auth.test", "chirpchat", Duration.ofMinutes(5)),
                token(KEY, id, "eve", "http://evil.test", "chirpchat", Duration.ofMinutes(5)),
                token(KEY, id, "eve", "http://auth.test", "someone-else", Duration.ofMinutes(5)),
                token(KEY, id, "eve", "http://auth.test", "chirpchat", Duration.ofMinutes(-5)) }) {
            call(new User(id, "eve", bad), get("/api/channels"), null, 401);
        }
        call(user("ok"), get("/api/channels"), null, 200);
        call(null, get("/actuator/health"), null, 200);
    }
}
