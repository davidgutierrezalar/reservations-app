package com.reservas.reservation;

import com.reservas.resource.Resource;
import com.reservas.user.User;
import jakarta.persistence.*;

import java.time.Instant;

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

    protected Reservation() {
    }

    public Reservation(Resource resource, User user, Instant startTime, Instant endTime) {
        this.resource = resource;
        this.user = user;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void cancel() { this.status = ReservationStatus.CANCELLED; }

    public Long getId() { return id; }
    public Resource getResource() { return resource; }
    public User getUser() { return user; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
