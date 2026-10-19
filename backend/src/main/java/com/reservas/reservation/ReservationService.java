package com.reservas.reservation;

import com.reservas.audit.AuditEntry.Action;
import com.reservas.audit.AuditEntry.EntityType;
import com.reservas.audit.AuditService;
import com.reservas.auth.CurrentUser;
import com.reservas.common.ApiException;
import com.reservas.reservation.ReservationDtos.BusySlot;
import com.reservas.reservation.ReservationDtos.ReservationRequest;
import com.reservas.reservation.ReservationDtos.ReservationResponse;
import com.reservas.reservation.ReservationDtos.SeriesCancelResponse;
import com.reservas.reservation.ReservationDtos.SeriesRequest;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReservationService {

    static final Duration MAX_DURATION = Duration.ofHours(12);
    static final int MAX_OCCURRENCES = 12;
    static final Duration MAX_AVAILABILITY_RANGE = Duration.ofDays(31);
    private static final long NO_EXCLUSION = 0L;

    private final ReservationRepository reservations;
    private final ResourceRepository resources;
    private final UserRepository users;
    private final AuditService audit;

    public ReservationService(ReservationRepository reservations, ResourceRepository resources, UserRepository users,
                              AuditService audit) {
        this.reservations = reservations;
        this.resources = resources;
        this.users = users;
        this.audit = audit;
    }

    public ReservationResponse create(CurrentUser caller, ReservationRequest req) {
        validateTimes(req.startTime(), req.endTime());
        Resource resource = bookableResource(req.resourceId());
        if (overlaps(resource.getId(), req.startTime(), req.endTime(), NO_EXCLUSION)) {
            throw ApiException.conflict("The resource is already booked in that time range");
        }
        var reservation = new Reservation(resource, users.getReferenceById(caller.id()), req.startTime(),
                req.endTime(), initialStatus(resource, caller), null);
        // Flush now so a concurrent double booking fails on the DB constraint here, inside the request
        reservations.saveAndFlush(reservation);
        record(reservation, Action.CREATED, caller, describe(reservation));
        return ReservationResponse.from(reservation);
    }

    /** All or nothing: if any occurrence collides, nothing is created and the colliding dates are returned. */
    public List<ReservationResponse> createSeries(CurrentUser caller, SeriesRequest req) {
        validateTimes(req.startTime(), req.endTime());
        Resource resource = bookableResource(req.resourceId());
        Duration length = Duration.between(req.startTime(), req.endTime());

        var starts = new ArrayList<Instant>();
        for (int i = 0; i < req.occurrences(); i++) {
            starts.add(req.startTime().plus(req.frequency().step.multipliedBy(i)));
        }
        List<Instant> conflicts = starts.stream()
                .filter(s -> overlaps(resource.getId(), s, s.plus(length), NO_EXCLUSION))
                .toList();
        if (!conflicts.isEmpty()) {
            throw ApiException.conflict(conflicts.size() + " of the " + starts.size() + " dates are already booked")
                    .with("conflicts", conflicts);
        }

        UUID seriesId = UUID.randomUUID();
        var user = users.getReferenceById(caller.id());
        ReservationStatus status = initialStatus(resource, caller);
        List<Reservation> created = reservations.saveAllAndFlush(starts.stream()
                .map(s -> new Reservation(resource, user, s, s.plus(length), status, seriesId))
                .toList());
        created.forEach(r -> record(r, Action.CREATED, caller,
                describe(r) + " (" + req.frequency().name().toLowerCase() + " series " + seriesId + ")"));
        return created.stream().map(ReservationResponse::from).toList();
    }

    /** Change resource and/or time with the same rules as a new booking. */
    public ReservationResponse update(Long id, CurrentUser caller, ReservationRequest req) {
        Reservation reservation = findVisible(id, caller);
        if (!reservation.getStatus().isActive()) {
            throw ApiException.conflict("Only pending or confirmed reservations can be changed");
        }
        if (!reservation.getStartTime().isAfter(Instant.now())) {
            throw ApiException.conflict("Reservations that have already started cannot be changed");
        }
        validateTimes(req.startTime(), req.endTime());
        Resource resource = bookableResource(req.resourceId());
        if (overlaps(resource.getId(), req.startTime(), req.endTime(), reservation.getId())) {
            throw ApiException.conflict("The resource is already booked in that time range");
        }
        String before = describe(reservation);
        // An admin's edit keeps the current status; a user's edit is re-checked against the approval rule
        ReservationStatus status = caller.admin() ? reservation.getStatus() : initialStatus(resource, caller);
        reservation.reschedule(resource, req.startTime(), req.endTime(), status);
        reservations.flush();
        record(reservation, Action.UPDATED, caller, before + " -> " + describe(reservation));
        return ReservationResponse.from(reservation);
    }

    public ReservationResponse cancel(Long id, CurrentUser caller) {
        Reservation reservation = findVisible(id, caller);
        if (!reservation.getStatus().isActive()) {
            throw ApiException.conflict("Only pending or confirmed reservations can be cancelled");
        }
        reservation.cancel();
        record(reservation, Action.CANCELLED, caller, describe(reservation));
        return ReservationResponse.from(reservation);
    }

    /** Cancels every upcoming occurrence; past ones stay as history. */
    public SeriesCancelResponse cancelSeries(UUID seriesId, CurrentUser caller) {
        List<Reservation> upcoming = reservations
                .findBySeriesIdAndStatusInAndStartTimeAfter(seriesId, ReservationStatus.ACTIVE, Instant.now()).stream()
                .filter(r -> caller.admin() || r.getUser().getId().equals(caller.id()))
                .toList();
        if (upcoming.isEmpty()) {
            throw ApiException.notFound("No upcoming reservations in that series");
        }
        upcoming.forEach(r -> {
            r.cancel();
            record(r, Action.CANCELLED, caller, describe(r) + " (whole series)");
        });
        return new SeriesCancelResponse(seriesId, upcoming.size());
    }

    public ReservationResponse approve(Long id, CurrentUser admin) {
        Reservation reservation = pending(id);
        reservation.approve();
        record(reservation, Action.APPROVED, admin, describe(reservation));
        return ReservationResponse.from(reservation);
    }

    public ReservationResponse reject(Long id, CurrentUser admin) {
        Reservation reservation = pending(id);
        reservation.reject();
        record(reservation, Action.REJECTED, admin, describe(reservation));
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<BusySlot> availability(Long resourceId, Instant from, Instant to) {
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_AVAILABILITY_RANGE) > 0) {
            throw ApiException.badRequest("'to' must be after 'from' and at most "
                    + MAX_AVAILABILITY_RANGE.toDays() + " days later");
        }
        if (!resources.existsById(resourceId)) {
            throw ApiException.notFound("Resource not found");
        }
        return reservations.findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfterOrderByStartTime(
                        resourceId, ReservationStatus.ACTIVE, to, from).stream()
                .map(r -> new BusySlot(r.getStartTime(), r.getEndTime(), r.getStatus()))
                .toList();
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

    private static void validateTimes(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("endTime must be after startTime");
        }
        if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            throw ApiException.badRequest("A reservation cannot last more than " + MAX_DURATION.toHours() + " hours");
        }
    }

    private Resource bookableResource(Long id) {
        return resources.findById(id)
                .filter(Resource::isActive)
                .orElseThrow(() -> ApiException.notFound("Resource not found"));
    }

    private boolean overlaps(Long resourceId, Instant start, Instant end, long excludeId) {
        return reservations.existsByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfterAndIdNot(
                resourceId, ReservationStatus.ACTIVE, end, start, excludeId);
    }

    private static ReservationStatus initialStatus(Resource resource, CurrentUser caller) {
        return resource.isRequiresApproval() && !caller.admin() ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED;
    }

    private Reservation findVisible(Long id, CurrentUser caller) {
        return reservations.findById(id)
                // 404 rather than 403 so users cannot probe other people's reservation ids
                .filter(r -> caller.admin() || r.getUser().getId().equals(caller.id()))
                .orElseThrow(() -> ApiException.notFound("Reservation not found"));
    }

    private Reservation pending(Long id) {
        Reservation reservation = reservations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw ApiException.conflict("Only pending reservations can be approved or rejected");
        }
        return reservation;
    }

    private void record(Reservation r, Action action, CurrentUser caller, String details) {
        audit.record(EntityType.RESERVATION, r.getId(), action, caller.id(), details);
    }

    private static String describe(Reservation r) {
        return r.getResource().getName() + " " + r.getStartTime() + " to " + r.getEndTime() + " [" + r.getStatus() + "]";
    }
}
