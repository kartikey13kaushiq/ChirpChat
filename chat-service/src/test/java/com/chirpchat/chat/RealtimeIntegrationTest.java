package com.chirpchat.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

class RealtimeIntegrationTest extends IntegrationTest {

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void close() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        StompSession s = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
        sessions.add(s);
        return s;
    }

    BlockingQueue<JsonNode> events(User u) throws Exception {
        BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
        connect(u.token()).subscribe("/user/queue/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        });
        Thread.sleep(300);
        return queue;
    }

    JsonNode next(BlockingQueue<JsonNode> queue, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode e = queue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (e != null && e.at("/type").asText().equals(type)) {
                return e;
            }
        }
        throw new AssertionError("no " + type + " event");
    }

    @Test
    void membersGetMessagesAndMentionedMembersGetAMention() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        String id = body(call(ada, post("/api/channels"), Map.of("name", "general", "isPrivate", false), 201)).at("/id").asText();
        call(bob, post("/api/channels/" + id + "/join"), null, 200);
        BlockingQueue<JsonNode> bobEvents = events(bob);

        call(ada, post("/api/channels/" + id + "/messages"), Map.of("body", "hello @bob"), 201);
        assertThat(next(bobEvents, "message.created").at("/data/body").asText()).isEqualTo("hello @bob");
        assertThat(next(bobEvents, "mention").at("/channelId").asText()).isEqualTo(id);
    }

    @Test
    void removedMembersStopReceivingImmediately() throws Exception {
        User ada = user("ada");
        User bob = user("bob");
        String id = body(call(ada, post("/api/channels"), Map.of("name", "ops", "isPrivate", false), 201)).at("/id").asText();
        call(bob, post("/api/channels/" + id + "/join"), null, 200);
        BlockingQueue<JsonNode> bobEvents = events(bob);

        call(ada, delete("/api/channels/" + id + "/members/" + bob.id()), null, 204);
        assertThat(next(bobEvents, "member.left").at("/data/username").asText()).isEqualTo("bob");
        call(ada, post("/api/channels/" + id + "/messages"), Map.of("body", "after bob left"), 201);
        assertThat(bobEvents.poll(700, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void unauthenticatedOrWrongDestinationIsRefused() throws Exception {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> connect("garbage")).isInstanceOf(Exception.class);
        StompSession session = connect(user("ada").token());
        session.subscribe("/queue/everyone", new StompSessionHandlerAdapter() { });
        long deadline = System.currentTimeMillis() + 3000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected()).isFalse();
    }
}
