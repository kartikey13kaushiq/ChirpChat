package com.chirpchat.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant rotatedAt;

    private Instant revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(User user, UUID familyId, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isUsable(Instant now) {
        return rotatedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public void rotate(Instant now) { rotatedAt = now; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public User getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public Instant getRotatedAt() { return rotatedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
