package com.reservas.audit;

import com.reservas.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/** Append-only record of who changed what and when. */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    public enum EntityType { RESERVATION, RESOURCE }

    public enum Action { CREATED, UPDATED, CANCELLED, APPROVED, REJECTED, DEACTIVATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntityType entityType;

    @Column(nullable = false, updatable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Action action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(updatable = false)
    private User actor;

    @Column(updatable = false)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditEntry() {
    }

    AuditEntry(EntityType entityType, Long entityId, Action action, User actor, String details) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.details = details;
    }

    public Long getId() { return id; }
    public EntityType getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public Action getAction() { return action; }
    public User getActor() { return actor; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
