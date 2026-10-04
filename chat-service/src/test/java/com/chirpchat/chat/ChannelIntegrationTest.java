package com.chirpchat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChannelIntegrationTest extends IntegrationTest {

    String create(User u, String name, boolean isPrivate) throws Exception {
        return body(call(u, post("/api/channels"), Map.of("name", name, "topic", "about " + name, "isPrivate", isPrivate), 201))
                .at("/id").asText();
    }

    @Test
    void createDiscoverAndJoinPublicChannels() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        String general = create(ada, "general", false);
        create(ada, "secret-plans", true);
        call(bob, post("/api/channels"), Map.of("name", "general", "isPrivate", false), 409);
        JsonNode invalid = body(call(ada, post("/api/channels"), Map.of("name", "Bad Name!", "isPrivate", false), 400));
        assertThat(invalid.at("/fields/name").asText()).contains("lowercase");

        JsonNode discover = body(call(bob, get("/api/channels/discover"), null, 200));
        assertThat(discover.findValuesAsText("name")).containsExactly("general");
        assertThat(discover.at("/0/unreadCount").isNull()).isTrue();
        assertThat(body(call(bob, get("/api/channels/discover").param("q", "gen"), null, 200)).size()).isEqualTo(1);
        assertThat(body(call(bob, get("/api/channels/discover").param("q", "%"), null, 200)).size()).isEqualTo(1);

        JsonNode joined = body(call(bob, post("/api/channels/" + general + "/join"), null, 200));
        assertThat(joined.at("/role").asText()).isEqualTo("MEMBER");
        assertThat(joined.at("/memberCount").asLong()).isEqualTo(2);
        call(bob, post("/api/channels/" + general + "/join"), null, 200); // idempotent
        assertThat(body(call(bob, get("/api/channels/discover"), null, 200)).size()).isZero();
        assertThat(body(call(bob, get("/api/channels"), null, 200)).findValuesAsText("name")).containsExactly("general");
    }

    @Test
    void privateChannelsAreInvisibleToOutsiders() throws Exception {
        User ada = user("ada");
        User eve = user("eve");
        String secret = create(ada, "secret", true);
        call(eve, get("/api/channels/" + secret), null, 404);
        call(eve, post("/api/channels/" + secret + "/join"), null, 404);
        call(eve, get("/api/channels/" + secret + "/members"), null, 404);
        call(eve, get("/api/channels/" + secret + "/messages"), null, 404);
        call(eve, post("/api/channels/" + secret + "/messages"), Map.of("body", "hi"), 404);
        call(ada, get("/api/channels/" + java.util.UUID.randomUUID()), null, 404);
    }

    @Test
    void ownersAddMembersResolvedThroughTheAuthService() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        User cat = user("cat");
        String secret = create(ada, "secret", true);
        when(directory.find(eq("bob"), anyString()))
                .thenReturn(Optional.of(new UserDirectory.DirectoryUser(bob.id(), "bob", "Bob")));
        when(directory.find(eq("cat"), anyString()))
                .thenReturn(Optional.of(new UserDirectory.DirectoryUser(cat.id(), "cat", "Cat")));
        when(directory.find(eq("ghost"), anyString())).thenReturn(Optional.empty());
        when(directory.find(eq("down"), anyString())).thenThrow(ApiException.badGateway("User directory is unavailable"));

        JsonNode added = body(call(ada, post("/api/channels/" + secret + "/members"), Map.of("username", "bob"), 201));
        assertThat(added.at("/username").asText()).isEqualTo("bob");
        call(ada, post("/api/channels/" + secret + "/members"), Map.of("username", "bob"), 409);
        call(ada, post("/api/channels/" + secret + "/members"), Map.of("username", "ghost"), 404);
        call(ada, post("/api/channels/" + secret + "/members"), Map.of("username", "down"), 502);
        call(bob, post("/api/channels/" + secret + "/members"), Map.of("username", "cat"), 403);
        assertThat(body(call(bob, get("/api/channels/" + secret), null, 200)).at("/name").asText()).isEqualTo("secret");

        call(bob, patch("/api/channels/" + secret), Map.of("topic", "nope"), 403);
        assertThat(body(call(ada, patch("/api/channels/" + secret), Map.of("topic", "launch"), 200)).at("/topic").asText())
                .isEqualTo("launch");
    }

    @Test
    void leavingRemovingAndOwnerHandOver() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        User cat = user("cat");
        String general = create(ada, "general", false);
        call(bob, post("/api/channels/" + general + "/join"), null, 200);
        call(cat, post("/api/channels/" + general + "/join"), null, 200);

        call(bob, delete("/api/channels/" + general + "/members/" + cat.id()), null, 403);
        call(ada, delete("/api/channels/" + general + "/members/" + cat.id()), null, 204);
        call(ada, delete("/api/channels/" + general + "/members/" + cat.id()), null, 404);
        call(cat, post("/api/channels/" + general + "/messages"), Map.of("body", "still here?"), 403);

        call(ada, delete("/api/channels/" + general + "/members/" + ada.id()), null, 204);
        JsonNode members = body(call(bob, get("/api/channels/" + general + "/members"), null, 200));
        assertThat(members.findValuesAsText("username")).containsExactly("bob");
        assertThat(members.at("/0/role").asText()).isEqualTo("OWNER");
    }

    @Test
    void readMarkersAndUnreadCounts() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        String general = create(ada, "general", false);
        call(bob, post("/api/channels/" + general + "/join"), null, 200);
        for (int i = 0; i < 3; i++) {
            call(ada, post("/api/channels/" + general + "/messages"), Map.of("body", "update " + i), 201);
        }
        assertThat(body(call(bob, get("/api/channels"), null, 200)).at("/0/unreadCount").asLong()).isEqualTo(3);
        assertThat(body(call(ada, get("/api/channels"), null, 200)).at("/0/unreadCount").asLong()).isZero();
        JsonNode read = body(call(bob, post("/api/channels/" + general + "/read"), Map.of("seq", 2), 200));
        assertThat(read.at("/unreadCount").asLong()).isEqualTo(1);
        call(bob, post("/api/channels/" + general + "/read"), Map.of("seq", 50), 200);
        assertThat(body(call(bob, get("/api/channels/" + general), null, 200)).at("/unreadCount").asLong()).isZero();
    }
}
