package com.reservas.reservation;

import com.reservas.auth.CurrentUser;
import com.reservas.common.ApiException;
import com.reservas.reservation.ReservationDtos.ReservationRequest;
import com.reservas.reservation.ReservationDtos.ReservationResponse;
import com.reservas.resource.Resource;
import com.reservas.resource.ResourceRepository;
import com.reservas.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;

@Service
@Transactional
public class ReservationService {

    static final Duration MAX_DURATION = Duration.ofHours(12);

    private final ReservationRepository reservations;
    private final ResourceRepository resources;
    private final UserRepository users;

    public ReservationService(ReservationRepository reservations, ResourceRepository resources, UserRepository users) {
        this.reservations = reservations;
        this.resources = resources;
        this.users = users;
    }

    public ReservationResponse create(Long userId, ReservationRequest req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw ApiException.badRequest("endTime must be after startTime");
        }
        if (Duration.between(req.startTime(), req.endTime()).compareTo(MAX_DURATION) > 0) {
            throw ApiException.badRequest("A reservation cannot last more than " + MAX_DURATION.toHours() + " hours");
        }
        Resource resource = resources.findById(req.resourceId())
                .filter(Resource::isActive)
                .orElseThrow(() -> ApiException.notFound("Resource not found"));
        if (reservations.existsByResourceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
                resource.getId(), ReservationStatus.CONFIRMED, req.endTime(), req.startTime())) {
            throw ApiException.conflict("The resource is already booked in that time range");
        }
        var reservation = new Reservation(resource, users.getReferenceById(userId), req.startTime(), req.endTime());
        // Flush now so a concurrent double booking fails on the DB constraint here, inside the request
        return ReservationResponse.from(reservations.saveAndFlush(reservation));
    }

    /** userId null means every user (admin view). */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> search(Long userId, Long resourceId, ReservationStatus status, Pageable pageable) {
        Specification<Reservation> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resource").get("id"), resourceId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return reservations.findAll(spec, pageable).map(ReservationResponse::from);
    }

    public ReservationResponse cancel(Long id, CurrentUser caller) {
        Reservation reservation = reservations.findById(id)
                // 404 rather than 403 so users cannot probe other people's reservation ids
                .filter(r -> caller.admin() || r.getUser().getId().equals(caller.id()))
                .orElseThrow(() -> ApiException.notFound("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw ApiException.conflict("Only confirmed reservations can be cancelled");
        }
        reservation.cancel();
        return ReservationResponse.from(reservation);
    }
}
