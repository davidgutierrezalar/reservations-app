package com.reservas.auth;

import com.reservas.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** One refresh token of a sign-in family; see V3__refresh_tokens.sql. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    public enum RevokedReason { ROTATED, LOGOUT, REUSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", updatable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, updatable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    private RevokedReason revokedReason;

    protected RefreshToken() {
    }

    RefreshToken(User user, UUID familyId, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    void revoke(RevokedReason reason, Instant at) {
        this.revokedAt = at;
        this.revokedReason = reason;
    }

    public User getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public RevokedReason getRevokedReason() { return revokedReason; }
}
