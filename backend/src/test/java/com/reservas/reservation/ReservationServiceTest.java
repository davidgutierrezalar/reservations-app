package com.reservas.reservation;

import com.reservas.audit.AuditService;
import com.reservas.auth.CurrentUser;
import com.reservas.common.ApiException;
import com.reservas.reservation.ReservationDtos.Frequency;
import com.reservas.reservation.ReservationDtos.ReservationRequest;
import com.reservas.reservation.ReservationDtos.SeriesRequest;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservations;
    @Mock ResourceRepository resources;
    @Mock UserRepository users;
    @Mock AuditService audit;
    @InjectMocks ReservationService service;

    private final Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    private final CurrentUser ana = new CurrentUser(7L, false);
    private final CurrentUser admin = new CurrentUser(1L, true);

    @Test
    void rejectsEndBeforeStart() {
        assertStatus(() -> service.create(ana, new ReservationRequest(1L, start, start.minusSeconds(60))),
                HttpStatus.BAD_REQUEST);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReservationsLongerThanMax() {
        var req = new ReservationRequest(1L, start, start.plus(ReservationService.MAX_DURATION).plusSeconds(1));
        assertStatus(() -> service.create(ana, req), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsInactiveResource() {
        Resource resource = resource(1L, false);
        resource.deactivate();
        when(resources.findById(1L)).thenReturn(Optional.of(resource));

        assertStatus(() -> service.create(ana, oneHour(1L)), HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsOverlappingReservation() {
        when(resources.findById(1L)).thenReturn(Optional.of(resource(1L, false)));
        overlap(true);

        assertStatus(() -> service.create(ana, oneHour(1L)), HttpStatus.CONFLICT);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void confirmsImmediatelyWhenNoApprovalIsNeeded() {
        givenBookable(resource(1L, false));

        var response = service.create(ana, oneHour(1L));

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(audit).record(any(), any(), eq(com.reservas.audit.AuditEntry.Action.CREATED), eq(7L), any());
    }

    @Test
    void userBookingOnApprovalResourceStartsPending() {
        givenBookable(resource(1L, true));

        assertThat(service.create(ana, oneHour(1L)).status()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void adminBookingOnApprovalResourceIsConfirmed() {
        givenBookable(resource(1L, true));
        when(users.getReferenceById(1L)).thenReturn(user(1L));

        assertThat(service.create(admin, oneHour(1L)).status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void seriesIsAllOrNothing() {
        when(resources.findById(1L)).thenReturn(Optional.of(resource(1L, false)));
        Instant thirdWeek = start.plus(Duration.ofDays(14));
        when(reservations.existsByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfterAndIdNot(
                anyLong(), any(), any(), any(), anyLong()))
                .thenAnswer(inv -> inv.getArgument(3, Instant.class).equals(thirdWeek));

        var req = new SeriesRequest(1L, start, start.plusSeconds(3600), Frequency.WEEKLY, 4);

        assertThatThrownBy(() -> service.createSeries(ana, req)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getProperties().get("conflicts")).isEqualTo(List.of(thirdWeek));
        });
        verify(reservations, never()).saveAllAndFlush(anyList());
    }

    @Test
    void createsWeeklySeriesSharingOneId() {
        givenBookable(resource(1L, false));
        when(reservations.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var created = service.createSeries(ana, new SeriesRequest(1L, start, start.plusSeconds(3600), Frequency.WEEKLY, 3));

        assertThat(created).hasSize(3);
        assertThat(created).extracting(ReservationDtos.ReservationResponse::seriesId).doesNotContainNull().containsOnly(created.getFirst().seriesId());
        assertThat(created.get(2).startTime()).isEqualTo(start.plus(Duration.ofDays(14)));
    }

    @Test
    void updateIgnoresItselfWhenCheckingOverlap() {
        var existing = withId(new Reservation(resource(1L, false), user(7L), start, start.plusSeconds(3600)), 5L);
        when(reservations.findById(5L)).thenReturn(Optional.of(existing));
        when(resources.findById(1L)).thenReturn(Optional.of(existing.getResource()));
        when(reservations.existsByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfterAndIdNot(
                anyLong(), any(), any(), any(), eq(5L))).thenReturn(false);

        var moved = service.update(5L, ana, new ReservationRequest(1L, start.plusSeconds(1800), start.plusSeconds(5400)));

        assertThat(moved.startTime()).isEqualTo(start.plusSeconds(1800));
    }

    @Test
    void cannotEditStartedOrCancelledReservations() {
        var started = withId(new Reservation(resource(1L, false), user(7L), Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)), 5L);
        when(reservations.findById(5L)).thenReturn(Optional.of(started));
        assertStatus(() -> service.update(5L, ana, oneHour(1L)), HttpStatus.CONFLICT);

        var cancelled = withId(new Reservation(resource(1L, false), user(7L), start, start.plusSeconds(3600)), 6L);
        cancelled.cancel();
        when(reservations.findById(6L)).thenReturn(Optional.of(cancelled));
        assertStatus(() -> service.update(6L, ana, oneHour(1L)), HttpStatus.CONFLICT);
    }

    @Test
    void otherUsersCannotCancel() {
        var reservation = new Reservation(resource(1L, false), user(7L), start, start.plusSeconds(3600));
        when(reservations.findById(5L)).thenReturn(Optional.of(reservation));

        assertStatus(() -> service.cancel(5L, new CurrentUser(8L, false)), HttpStatus.NOT_FOUND);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void adminCanCancelAnyReservationButOnlyOnce() {
        var reservation = new Reservation(resource(1L, false), user(7L), start, start.plusSeconds(3600));
        when(reservations.findById(5L)).thenReturn(Optional.of(reservation));

        assertThat(service.cancel(5L, admin).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertStatus(() -> service.cancel(5L, admin), HttpStatus.CONFLICT);
    }

    @Test
    void onlyPendingReservationsCanBeApproved() {
        var pending = new Reservation(resource(1L, true), user(7L), start, start.plusSeconds(3600),
                ReservationStatus.PENDING, null);
        when(reservations.findById(5L)).thenReturn(Optional.of(pending));

        assertThat(service.approve(5L, admin).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertStatus(() -> service.reject(5L, admin), HttpStatus.CONFLICT);
    }

    @Test
    void availabilityRangeIsBounded() {
        assertStatus(() -> service.availability(1L, start, start), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.availability(1L, start, start.plus(Duration.ofDays(32))), HttpStatus.BAD_REQUEST);
    }

    private void givenBookable(Resource resource) {
        when(resources.findById(resource.getId())).thenReturn(Optional.of(resource));
        lenientUser();
        overlap(false);
    }

    private void lenientUser() {
        org.mockito.Mockito.lenient().when(users.getReferenceById(7L)).thenReturn(user(7L));
    }

    private void overlap(boolean result) {
        when(reservations.existsByResourceIdAndStatusInAndStartTimeBeforeAndEndTimeAfterAndIdNot(
                anyLong(), any(), any(), any(), anyLong())).thenReturn(result);
    }

    private ReservationRequest oneHour(Long resourceId) {
        return new ReservationRequest(resourceId, start, start.plusSeconds(3600));
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(expected));
    }

    private static Resource resource(Long id, boolean requiresApproval) {
        var resource = new Resource("Room A", null, ResourceType.MEETING_ROOM, 6, requiresApproval);
        ReflectionTestUtils.setField(resource, "id", id);
        return resource;
    }

    private static User user(Long id) {
        var user = new User("Ana", "ana@test.dev", "hash", id == 1L ? Role.ADMIN : Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Reservation withId(Reservation r, Long id) {
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }
}
