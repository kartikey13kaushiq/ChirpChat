package com.chirpchat.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", updatable = false)
    private Channel channel;

    @Column(nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, updatable = false)
    private String authorUsername;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(nullable = false, updatable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, updatable = false)
    private List<String> mentions;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(Channel channel, CurrentUser author, long seq, String body, List<String> mentions, Instant createdAt) {
        this.channel = channel;
        this.authorId = author.id();
        this.authorUsername = author.username();
        this.seq = seq;
        this.body = body;
        this.mentions = mentions;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Channel getChannel() { return channel; }
    public UUID getAuthorId() { return authorId; }
    public String getAuthorUsername() { return authorUsername; }
    public long getSeq() { return seq; }
    public String getBody() { return body; }
    public List<String> getMentions() { return mentions; }
    public Instant getCreatedAt() { return createdAt; }
}
