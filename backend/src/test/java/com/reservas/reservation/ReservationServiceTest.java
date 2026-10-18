package com.reservas.reservation;

import com.reservas.auth.CurrentUser;
import com.reservas.common.ApiException;
import com.reservas.reservation.ReservationDtos.ReservationRequest;
import com.reservas.resource.Resource;
import com.reservas.resource.ResourceRepository;
import com.reservas.resource.ResourceType;
import com.reservas.user.Role;
import com.reservas.user.User;
import com.reservas.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservations;
    @Mock ResourceRepository resources;
    @Mock UserRepository users;
    @InjectMocks ReservationService service;

    private final Instant start = Instant.now().plus(1, ChronoUnit.DAYS);

    @Test
    void rejectsEndBeforeStart() {
        var req = new ReservationRequest(1L, start, start.minusSeconds(60));

        assertStatus(() -> service.create(1L, req), HttpStatus.BAD_REQUEST);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReservationsLongerThanMax() {
        var req = new ReservationRequest(1L, start, start.plus(ReservationService.MAX_DURATION).plusSeconds(1));

        assertStatus(() -> service.create(1L, req), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsInactiveResource() {
        Resource resource = resource(1L);
        resource.deactivate();
        when(resources.findById(1L)).thenReturn(Optional.of(resource));

        assertStatus(() -> service.create(1L, new ReservationRequest(1L, start, start.plusSeconds(3600))),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsOverlappingReservation() {
        when(resources.findById(1L)).thenReturn(Optional.of(resource(1L)));
        when(reservations.existsByResourceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
                anyLong(), any(), any(), any())).thenReturn(true);

        assertStatus(() -> service.create(1L, new ReservationRequest(1L, start, start.plusSeconds(3600))),
                HttpStatus.CONFLICT);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void createsReservation() {
        Resource resource = resource(1L);
        User user = user(7L);
        when(resources.findById(1L)).thenReturn(Optional.of(resource));
        when(users.getReferenceById(7L)).thenReturn(user);
        when(reservations.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(7L, new ReservationRequest(1L, start, start.plusSeconds(3600)));

        assertThat(response.resourceId()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void otherUsersCannotCancel() {
        var reservation = new Reservation(resource(1L), user(7L), start, start.plusSeconds(3600));
        when(reservations.findById(5L)).thenReturn(Optional.of(reservation));

        assertStatus(() -> service.cancel(5L, new CurrentUser(8L, false)), HttpStatus.NOT_FOUND);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void adminCanCancelAnyReservationButOnlyOnce() {
        var reservation = new Reservation(resource(1L), user(7L), start, start.plusSeconds(3600));
        when(reservations.findById(5L)).thenReturn(Optional.of(reservation));
        var admin = new CurrentUser(1L, true);

        assertThat(service.cancel(5L, admin).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertStatus(() -> service.cancel(5L, admin), HttpStatus.CONFLICT);
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    private static Resource resource(Long id) {
        var resource = new Resource("Room A", null, ResourceType.MEETING_ROOM, 6);
        ReflectionTestUtils.setField(resource, "id", id);
        return resource;
    }

    private static User user(Long id) {
        var user = new User("Ana", "ana@test.dev", "hash", Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
