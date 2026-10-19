package com.reservas.audit;

import com.reservas.audit.AuditEntry.Action;
import com.reservas.audit.AuditEntry.EntityType;
import com.reservas.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;

@Service
public class AuditService {

    public record AuditResponse(Long id, EntityType entityType, Long entityId, Action action, Long actorId,
                                String actorName, String details, Instant createdAt) {
        static AuditResponse from(AuditEntry e) {
            return new AuditResponse(e.getId(), e.getEntityType(), e.getEntityId(), e.getAction(),
                    e.getActor().getId(), e.getActor().getName(), e.getDetails(), e.getCreatedAt());
        }
    }

    private final AuditRepository entries;
    private final UserRepository users;

    public AuditService(AuditRepository entries, UserRepository users) {
        this.entries = entries;
        this.users = users;
    }

    /** Runs inside the caller's transaction, so a change and its audit entry commit or roll back together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(EntityType type, Long entityId, Action action, Long actorId, String details) {
        entries.save(new AuditEntry(type, entityId, action, users.getReferenceById(actorId), details));
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> search(EntityType type, Long entityId, Pageable pageable) {
        Specification<AuditEntry> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (type != null) {
                predicates.add(cb.equal(root.get("entityType"), type));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return entries.findAll(spec, pageable).map(AuditResponse::from);
    }
}
