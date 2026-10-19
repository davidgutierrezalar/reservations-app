package com.reservas.reservation;

import com.reservas.auth.CurrentUser;
import com.reservas.reservation.ReservationDtos.BusySlot;
import com.reservas.reservation.ReservationDtos.ReservationRequest;
import com.reservas.reservation.ReservationDtos.ReservationResponse;
import com.reservas.reservation.ReservationDtos.SeriesCancelResponse;
import com.reservas.reservation.ReservationDtos.SeriesRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/api/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationRequest req, JwtAuthenticationToken auth) {
        return service.create(CurrentUser.from(auth), req);
    }

    @PostMapping("/api/reservations/series")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReservationResponse> createSeries(@Valid @RequestBody SeriesRequest req, JwtAuthenticationToken auth) {
        return service.createSeries(CurrentUser.from(auth), req);
    }

    @GetMapping("/api/reservations/me")
    public Page<ReservationResponse> mine(@RequestParam(required = false) ReservationStatus status,
                                         @PageableDefault(sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable,
                                         JwtAuthenticationToken auth) {
        return service.search(CurrentUser.from(auth).id(), null, status, pageable);
    }

    /** Admin only (see SecurityConfig). */
    @GetMapping("/api/reservations")
    public Page<ReservationResponse> all(@RequestParam(required = false) Long resourceId,
                                        @RequestParam(required = false) ReservationStatus status,
                                        @PageableDefault(sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(null, resourceId, status, pageable);
    }

    @PutMapping("/api/reservations/{id}")
    public ReservationResponse update(@PathVariable Long id, @Valid @RequestBody ReservationRequest req,
                                      JwtAuthenticationToken auth) {
        return service.update(id, CurrentUser.from(auth), req);
    }

    @PatchMapping("/api/reservations/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, JwtAuthenticationToken auth) {
        return service.cancel(id, CurrentUser.from(auth));
    }

    @PatchMapping("/api/reservations/series/{seriesId}/cancel")
    public SeriesCancelResponse cancelSeries(@PathVariable UUID seriesId, JwtAuthenticationToken auth) {
        return service.cancelSeries(seriesId, CurrentUser.from(auth));
    }

    /** Admin only (see SecurityConfig). */
    @PatchMapping("/api/reservations/{id}/approve")
    public ReservationResponse approve(@PathVariable Long id, JwtAuthenticationToken auth) {
        return service.approve(id, CurrentUser.from(auth));
    }

    /** Admin only (see SecurityConfig). */
    @PatchMapping("/api/reservations/{id}/reject")
    public ReservationResponse reject(@PathVariable Long id, JwtAuthenticationToken auth) {
        return service.reject(id, CurrentUser.from(auth));
    }

    /** Taken slots of a resource in [from, to), so clients can show free gaps before booking. */
    @GetMapping("/api/resources/{id}/availability")
    public List<BusySlot> availability(@PathVariable Long id, @RequestParam Instant from, @RequestParam Instant to) {
        return service.availability(id, from, to);
    }
}
