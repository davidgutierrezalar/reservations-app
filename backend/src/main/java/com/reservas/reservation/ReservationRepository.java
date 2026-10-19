package com.reservas.reservation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    // Fetch resource and user in the same query to avoid N+1 when mapping to DTOs
    @EntityGraph(attributePaths = {"resource", "user"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    /**
     * Two ranges overlap when each one starts before the other ends.
     * excludeId skips the reservation being edited; pass 0 when creating (ids start at 1).
     */
    boolean existsByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfterAndIdNot(
            Long resourceId, Collection<ReservationStatus> statuses, Instant end, Instant start, Long excludeId);

    List<Reservation> findByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfterOrderByStartTime(
            Long resourceId, Collection<ReservationStatus> statuses, Instant to, Instant from);

    @EntityGraph(attributePaths = {"resource", "user"})
    List<Reservation> findBySeriesIdAndStatusInAndStartTimeAfter(
            UUID seriesId, Collection<ReservationStatus> statuses, Instant after);
}
