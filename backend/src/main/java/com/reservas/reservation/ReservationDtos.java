package com.reservas.reservation;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record ReservationRequest(@NotNull Long resourceId, @NotNull @Future Instant startTime,
                                     @NotNull Instant endTime) {
    }

    public record ReservationResponse(Long id, Long resourceId, String resourceName, Long userId, String userName,
                                      Instant startTime, Instant endTime, ReservationStatus status,
                                      Instant createdAt) {
        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.getId(), r.getResource().getId(), r.getResource().getName(),
                    r.getUser().getId(), r.getUser().getName(), r.getStartTime(), r.getEndTime(), r.getStatus(),
                    r.getCreatedAt());
        }
    }
}
