package com.chirpchat.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channel_members")
public class ChannelMember {

    public enum Role { OWNER, MEMBER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", updatable = false)
    private Channel channel;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private long lastReadSeq;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    protected ChannelMember() {
    }

    public ChannelMember(Channel channel, UUID userId, String username, Role role, long lastReadSeq, Instant joinedAt) {
        this.channel = channel;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.lastReadSeq = lastReadSeq;
        this.joinedAt = joinedAt;
    }

    boolean markReadUpTo(long seq) {
        if (seq <= lastReadSeq) {
            return false;
        }
        lastReadSeq = seq;
        return true;
    }

    public Channel getChannel() { return channel; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    void setRole(Role role) { this.role = role; }
    public long getLastReadSeq() { return lastReadSeq; }
    public Instant getJoinedAt() { return joinedAt; }
}
