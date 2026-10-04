package com.chirpchat.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record CreateChannelRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,39}", message = "use 2-40 lowercase letters, digits or '-'")
            String name,
            @Size(max = 250) String topic,
            boolean isPrivate) {
    }

    public record UpdateTopicRequest(@Size(max = 250) String topic) {
    }

    public record AddMemberRequest(@NotBlank String username) {
    }

    public record PostMessageRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record ReadRequest(@PositiveOrZero long seq) {
    }

    /** {@code unreadCount} and {@code role} are null when the caller is not a member. */
    public record ChannelView(UUID id, String name, String topic, boolean isPrivate, long memberCount, long lastSeq,
            Long unreadCount, ChannelMember.Role role, Instant lastMessageAt, Instant createdAt) {
    }

    public record MemberView(UUID userId, String username, ChannelMember.Role role) {
    }

    public record MessageView(UUID id, UUID channelId, long seq, UUID authorId, String authorUsername, String body,
            List<String> mentions, Instant createdAt) {

        static MessageView of(Message m) {
            return new MessageView(m.getId(), m.getChannel().getId(), m.getSeq(), m.getAuthorId(), m.getAuthorUsername(),
                    m.getBody(), m.getMentions(), m.getCreatedAt());
        }
    }

    public record MessagePage(List<MessageView> messages, Long nextBefore) {
    }

    public record ReadState(UUID channelId, long lastReadSeq, long unreadCount) {
    }
}
