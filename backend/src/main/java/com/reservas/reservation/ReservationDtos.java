package com.reservas.reservation;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    /** Used to create a booking and to change one (resource and/or time). */
    public record ReservationRequest(@NotNull Long resourceId, @NotNull @Future Instant startTime,
                                     @NotNull Instant endTime) {
    }

    public enum Frequency {
        DAILY(Duration.ofDays(1)), WEEKLY(Duration.ofDays(7));

        // shortcut: fixed 24h/7d steps keep the UTC time; in zones with DST the local hour shifts once. Use ZonedDateTime with the user's zone if that matters.
        final Duration step;

        Frequency(Duration step) {
            this.step = step;
        }
    }

    public record SeriesRequest(@NotNull Long resourceId, @NotNull @Future Instant startTime, @NotNull Instant endTime,
                                @NotNull Frequency frequency,
                                @Min(2) @Max(ReservationService.MAX_OCCURRENCES) int occurrences) {
    }

    public record ReservationResponse(Long id, Long resourceId, String resourceName, Long userId, String userName,
                                      Instant startTime, Instant endTime, ReservationStatus status,
                                      UUID seriesId, Instant createdAt) {
        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.getId(), r.getResource().getId(), r.getResource().getName(),
                    r.getUser().getId(), r.getUser().getName(), r.getStartTime(), r.getEndTime(), r.getStatus(),
                    r.getSeriesId(), r.getCreatedAt());
        }
    }

    /** A taken slot; deliberately without the user, since any signed-in user can see availability. */
    public record BusySlot(Instant startTime, Instant endTime, ReservationStatus status) {
    }

    public record SeriesCancelResponse(UUID seriesId, int cancelled) {
    }
}
