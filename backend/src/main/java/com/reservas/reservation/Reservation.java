package com.reservas.reservation;

import com.reservas.resource.Resource;
import com.reservas.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Shared by every occurrence of a recurring booking; null for one-off bookings. */
    @Column(updatable = false)
    private UUID seriesId;

    protected Reservation() {
    }

    public Reservation(Resource resource, User user, Instant startTime, Instant endTime) {
        this(resource, user, startTime, endTime, ReservationStatus.CONFIRMED, null);
    }

    public Reservation(Resource resource, User user, Instant startTime, Instant endTime, ReservationStatus status,
                       UUID seriesId) {
        this.resource = resource;
        this.user = user;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.seriesId = seriesId;
    }

    public void reschedule(Resource resource, Instant startTime, Instant endTime, ReservationStatus status) {
        this.resource = resource;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public void cancel() { this.status = ReservationStatus.CANCELLED; }
    public void approve() { this.status = ReservationStatus.CONFIRMED; }
    public void reject() { this.status = ReservationStatus.REJECTED; }

    public Long getId() { return id; }
    public Resource getResource() { return resource; }
    public User getUser() { return user; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getSeriesId() { return seriesId; }
}
