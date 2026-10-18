package com.reservas.reservation;

import com.reservas.auth.CurrentUser;
import com.reservas.reservation.ReservationDtos.ReservationRequest;
import com.reservas.reservation.ReservationDtos.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationRequest req, JwtAuthenticationToken auth) {
        return service.create(CurrentUser.from(auth).id(), req);
    }

    @GetMapping("/me")
    public Page<ReservationResponse> mine(@RequestParam(required = false) ReservationStatus status,
                                         @PageableDefault(sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable,
                                         JwtAuthenticationToken auth) {
        return service.search(CurrentUser.from(auth).id(), null, status, pageable);
    }

    /** Admin only (see SecurityConfig). */
    @GetMapping
    public Page<ReservationResponse> all(@RequestParam(required = false) Long resourceId,
                                        @RequestParam(required = false) ReservationStatus status,
                                        @PageableDefault(sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(null, resourceId, status, pageable);
    }

    @PatchMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, JwtAuthenticationToken auth) {
        return service.cancel(id, CurrentUser.from(auth));
    }
}
