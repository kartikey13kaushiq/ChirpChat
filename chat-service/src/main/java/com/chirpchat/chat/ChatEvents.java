package com.chirpchat.chat;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes events to each recipient's {@code /user/queue/events}, after the producing transaction commits.
 * Fan-out is per user rather than a shared {@code /topic} per channel: membership is checked when the event
 * is produced, so someone removed from a private channel stops receiving it immediately, with no stale
 * subscription to revoke.
 */
@Component
public class ChatEvents {

    public record Event(String type, UUID channelId, Object data) {
    }

    record Outbound(List<String> usernames, Event event) {
    }

    private final ApplicationEventPublisher publisher;
    private final SimpMessagingTemplate template;

    public ChatEvents(ApplicationEventPublisher publisher, SimpMessagingTemplate template) {
        this.publisher = publisher;
        this.template = template;
    }

    public void publish(Collection<String> usernames, String type, UUID channelId, Object data) {
        if (!usernames.isEmpty()) {
            publisher.publishEvent(new Outbound(List.copyOf(usernames), new Event(type, channelId, data)));
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    void deliver(Outbound outbound) {
        outbound.usernames().forEach(u -> template.convertAndSendToUser(u, "/queue/events", outbound.event()));
    }
}
