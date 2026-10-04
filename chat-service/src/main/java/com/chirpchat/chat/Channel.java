package com.chirpchat.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channels")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String name;

    private String topic;

    @Column(name = "is_private", nullable = false, updatable = false)
    private boolean privateChannel;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private long lastSeq;

    private Instant lastMessageAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Channel() {
    }

    public Channel(String name, String topic, boolean privateChannel, UUID createdBy, Instant createdAt) {
        this.name = name;
        this.topic = topic;
        this.privateChannel = privateChannel;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    long nextSeq(Instant now) {
        lastMessageAt = now;
        return ++lastSeq;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public boolean isPrivateChannel() { return privateChannel; }
    public long getLastSeq() { return lastSeq; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getCreatedAt() { return createdAt; }
}
