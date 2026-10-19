package com.reservas.reservation;

import java.util.Set;

public enum ReservationStatus {
    PENDING, CONFIRMED, REJECTED, CANCELLED;

    /** Statuses that hold the time slot (mirrors the WHERE of the DB exclusion constraint). */
    public static final Set<ReservationStatus> ACTIVE = Set.of(PENDING, CONFIRMED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
