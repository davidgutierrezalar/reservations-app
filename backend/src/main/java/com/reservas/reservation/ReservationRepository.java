package com.reservas.reservation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    // Fetch resource and user in the same query to avoid N+1 when mapping to DTOs
    @EntityGraph(attributePaths = {"resource", "user"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    // Two ranges overlap when each one starts before the other ends
    boolean existsByResourceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
            Long resourceId, ReservationStatus status, Instant end, Instant start);
}
