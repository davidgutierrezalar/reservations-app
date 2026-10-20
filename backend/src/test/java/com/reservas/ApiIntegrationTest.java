package com.reservas;

import com.jayway.jsonpath.JsonPath;
import com.reservas.reservation.Reservation;
import com.reservas.reservation.ReservationRepository;
import com.reservas.reservation.ReservationStatus;
import com.reservas.resource.Resource;
import com.reservas.resource.ResourceRepository;
import com.reservas.resource.ResourceType;
import com.reservas.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against a real PostgreSQL in Docker: HTTP, security, validation, JPA and Flyway migrations. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-123",
        "app.admin.email=admin@test.dev",
        "app.admin.password=admin-password",
        // Every test signs in from the same address; AuthRateLimitFilterTest covers the limit itself
        "app.auth.rate-limit.max-attempts=100000"})
class ApiIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired ResourceRepository resources;
    @Autowired ReservationRepository reservations;
    @Autowired UserRepository users;

    MockMvc mvc;
    String adminToken;
    String userToken;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = login("admin@test.dev", "admin-password");
        userToken = register("user-" + UUID.randomUUID() + "@test.dev");
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/resources")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsInvalidInputAndDuplicates() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        String email = "dup-" + UUID.randomUUID() + "@test.dev";
        register(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedJsonGetsAProblemDetail() throws Exception {
        mvc.perform(auth(post("/api/reservations"), userToken).content("{\"resourceId\": 1, \"startTime\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is missing or is not valid JSON"));
    }

    @Test
    void loginSetsHttpOnlyCookiesAndKeepsTokensOutOfTheBody() throws Exception {
        MockHttpServletResponse res = loginResponse("admin@test.dev", "admin-password");

        assertThat(res.getContentAsString()).contains("admin@test.dev").doesNotContain(res.getCookie("access_token").getValue());
        assertThat(res.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(c -> assertThat(c).startsWith("access_token=")
                        .contains("Path=/api;", "HttpOnly", "Secure", "SameSite=Strict"))
                .anySatisfy(c -> assertThat(c).startsWith("refresh_token=")
                        .contains("Path=/api/auth/refresh;", "HttpOnly", "Secure", "SameSite=Strict"));
    }

    @Test
    void cookieSessionsNeedTheCsrfHeaderToChangeData() throws Exception {
        Cookie access = loginResponse("admin@test.dev", "admin-password").getCookie("access_token");
        String body = "{\"name\":\"Room " + UUID.randomUUID() + "\",\"type\":\"DESK\",\"capacity\":1}";

        // Reads work with the cookie alone, and hand out the CSRF token cookie
        Cookie xsrf = mvc.perform(get("/api/resources").cookie(access))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        mvc.perform(post("/api/resources").cookie(access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/resources").cookie(access, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void refreshTokenRenewsTheSessionButCannotCallTheApi() throws Exception {
        Cookie refresh = loginResponse("admin@test.dev", "admin-password").getCookie("refresh_token");

        mvc.perform(get("/api/resources").header("Authorization", "Bearer " + refresh.getValue()))
                .andExpect(status().isUnauthorized());

        String renewed = mvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.dev"))
                .andReturn().getResponse().getCookie("access_token").getValue();
        mvc.perform(get("/api/resources").cookie(new Cookie("access_token", renewed))).andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Not signed in"));
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", userToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsBothCookies() throws Exception {
        MockHttpServletResponse res = mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(res.getCookie("access_token").getMaxAge()).isZero();
        assertThat(res.getCookie("refresh_token").getMaxAge()).isZero();
    }

    @Test
    void passwordsLongerThanBcryptAllowsAreRejectedCleanly() throws Exception {
        // 40 characters, 80 bytes in UTF-8: passes a character limit but BCrypt only takes 72 bytes
        String multibyte = "ñ".repeat(40);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"long-" + UUID.randomUUID() + "@test.dev\",\"password\":\"" + multibyte + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.dev\",\"password\":\"" + "x".repeat(100) + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validationMessagesAreInEnglishWhateverTheBrowserLanguage() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "es-CO,es;q=0.9")
                        .content("{\"name\":\"\",\"email\":\"x-" + UUID.randomUUID() + "@test.dev\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.dev\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void onlyAdminsManageResources() throws Exception {
        String body = "{\"name\":\"Room X\",\"type\":\"MEETING_ROOM\",\"capacity\":4}";
        mvc.perform(auth(post("/api/resources"), userToken).content(body)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/resources"), adminToken).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(auth(get("/api/reservations"), userToken)).andExpect(status().isForbidden());
    }

    @Test
    void searchesResourcesWithFiltersAndPagination() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            resources.save(new Resource("Desk " + tag + " " + i, null, ResourceType.DESK, 1));
        }
        resources.save(new Resource("Van " + tag, null, ResourceType.VEHICLE, 8));

        mvc.perform(auth(get("/api/resources"), userToken)
                        .param("name", tag).param("type", "DESK").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mvc.perform(auth(get("/api/resources"), userToken).param("sort", "notAField"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingFlowPreventsDoubleBooking() throws Exception {
        Long resourceId = resources.save(new Resource("Room B", null, ResourceType.MEETING_ROOM, 6)).getId();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        String created = mvc.perform(auth(post("/api/reservations"), userToken)
                        .content(reservationBody(resourceId, start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        Integer reservationId = JsonPath.read(created, "$.id");

        // Overlaps the second half of the first reservation
        String overlapping = reservationBody(resourceId, start.plus(30, ChronoUnit.MINUTES), start.plus(2, ChronoUnit.HOURS));
        mvc.perform(auth(post("/api/reservations"), adminToken).content(overlapping))
                .andExpect(status().isConflict());

        // Back-to-back is allowed: ranges are [start, end)
        mvc.perform(auth(post("/api/reservations"), adminToken)
                        .content(reservationBody(resourceId, start.plus(1, ChronoUnit.HOURS), start.plus(2, ChronoUnit.HOURS))))
                .andExpect(status().isCreated());

        mvc.perform(auth(get("/api/reservations/me"), userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].resourceName").value("Room B"));

        // Another user cannot see or cancel it
        String otherToken = register("other-" + UUID.randomUUID() + "@test.dev");
        mvc.perform(auth(patch("/api/reservations/" + reservationId + "/cancel"), otherToken))
                .andExpect(status().isNotFound());

        mvc.perform(auth(patch("/api/reservations/" + reservationId + "/cancel"), userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rejectsReservationsInThePast() throws Exception {
        Long resourceId = resources.save(new Resource("Room C", null, ResourceType.MEETING_ROOM, 2)).getId();
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);

        mvc.perform(auth(post("/api/reservations"), userToken)
                        .content(reservationBody(resourceId, past, past.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startTime").exists());
    }

    @Test
    void databaseConstraintBlocksOverlapEvenWhenServiceIsBypassed() {
        Resource resource = resources.save(new Resource("Room D", null, ResourceType.MEETING_ROOM, 2));
        var user = users.findByEmail("admin@test.dev").orElseThrow();
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        reservations.saveAndFlush(new Reservation(resource, user, start, start.plus(1, ChronoUnit.HOURS)));

        // Simulates two concurrent requests that both passed the existence check
        assertThatThrownBy(() -> reservations.saveAndFlush(
                new Reservation(resource, user, start.plus(10, ChronoUnit.MINUTES), start.plus(2, ChronoUnit.HOURS))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvalFlowHoldsTheSlotAndIsAudited() throws Exception {
        Long vanId = resources.save(new Resource("Van " + UUID.randomUUID(), null, ResourceType.VEHICLE, 8, true)).getId();
        Instant start = futureHour(5);

        String created = mvc.perform(auth(post("/api/reservations"), userToken)
                        .content(reservationBody(vanId, start, start.plus(2, ChronoUnit.HOURS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        // A pending request already blocks the slot for everyone else
        String other = register("other-" + UUID.randomUUID() + "@test.dev");
        mvc.perform(auth(post("/api/reservations"), other)
                        .content(reservationBody(vanId, start.plus(1, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS))))
                .andExpect(status().isConflict());

        mvc.perform(auth(patch("/api/reservations/" + id + "/approve"), userToken)).andExpect(status().isForbidden());
        mvc.perform(auth(patch("/api/reservations/" + id + "/approve"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(auth(patch("/api/reservations/" + id + "/reject"), adminToken)).andExpect(status().isConflict());

        mvc.perform(auth(get("/api/audit"), userToken)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/audit"), adminToken)
                        .param("entityType", "RESERVATION").param("entityId", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.content[0].actorName").value("Admin"))
                .andExpect(jsonPath("$.content[1].action").value("CREATED"));
    }

    @Test
    void weeklySeriesIsAllOrNothingAndCanBeCancelledAsAWhole() throws Exception {
        Long roomId = resources.save(new Resource("Room S", null, ResourceType.MEETING_ROOM, 4)).getId();
        Instant start = futureHour(10);

        String created = mvc.perform(auth(post("/api/reservations/series"), userToken)
                        .content(seriesBody(roomId, start, start.plus(1, ChronoUnit.HOURS), 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String seriesId = JsonPath.read(created, "$[0].seriesId");
        Integer firstId = JsonPath.read(created, "$[0].id");

        // Weeks 3 and 4: only week 3 collides, yet nothing from this series may be created
        String other = register("other-" + UUID.randomUUID() + "@test.dev");
        Instant thirdWeek = start.plus(14, ChronoUnit.DAYS);
        mvc.perform(auth(post("/api/reservations/series"), other)
                        .content(seriesBody(roomId, thirdWeek.minus(30, ChronoUnit.MINUTES),
                                thirdWeek.plus(30, ChronoUnit.MINUTES), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts.length()").value(1));
        mvc.perform(auth(get("/api/reservations/me"), other))
                .andExpect(jsonPath("$.content.length()").value(0));

        // One occurrence, then the rest of the series
        mvc.perform(auth(patch("/api/reservations/" + firstId + "/cancel"), userToken)).andExpect(status().isOk());
        mvc.perform(auth(patch("/api/reservations/series/" + seriesId + "/cancel"), other)).andExpect(status().isNotFound());
        mvc.perform(auth(patch("/api/reservations/series/" + seriesId + "/cancel"), userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(2));
    }

    @Test
    void editingKeepsTheSameRules() throws Exception {
        Long roomId = resources.save(new Resource("Room E", null, ResourceType.MEETING_ROOM, 4)).getId();
        Long otherRoomId = resources.save(new Resource("Room F", null, ResourceType.MEETING_ROOM, 4)).getId();
        Instant start = futureHour(15);
        createReservation(roomId, start, start.plus(1, ChronoUnit.HOURS));
        Integer second = createReservation(roomId, start.plus(2, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS));

        mvc.perform(auth(put("/api/reservations/" + second), userToken)
                        .content(reservationBody(roomId, start.plus(30, ChronoUnit.MINUTES), start.plus(90, ChronoUnit.MINUTES))))
                .andExpect(status().isConflict());
        mvc.perform(auth(put("/api/reservations/" + second), userToken)
                        .content(reservationBody(roomId, start.plus(2, ChronoUnit.HOURS), start.plus(15, ChronoUnit.HOURS))))
                .andExpect(status().isBadRequest());
        // Moving within its own current slot does not collide with itself
        mvc.perform(auth(put("/api/reservations/" + second), userToken)
                        .content(reservationBody(roomId, start.plus(150, ChronoUnit.MINUTES), start.plus(4, ChronoUnit.HOURS))))
                .andExpect(status().isOk());
        mvc.perform(auth(put("/api/reservations/" + second), userToken)
                        .content(reservationBody(otherRoomId, start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceName").value("Room F"));
    }

    @Test
    void databaseConstraintAlsoBlocksOverlappingUpdates() {
        Resource resource = resources.save(new Resource("Room U", null, ResourceType.MEETING_ROOM, 2));
        var user = users.findByEmail("admin@test.dev").orElseThrow();
        Instant start = futureHour(20);
        reservations.saveAndFlush(new Reservation(resource, user, start, start.plus(1, ChronoUnit.HOURS)));
        Reservation later = reservations.saveAndFlush(
                new Reservation(resource, user, start.plus(2, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS)));

        later.reschedule(resource, start.plus(30, ChronoUnit.MINUTES), start.plus(90, ChronoUnit.MINUTES),
                ReservationStatus.CONFIRMED);
        assertThatThrownBy(() -> reservations.saveAndFlush(later)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void availabilityListsOnlySlotsThatAreTaken() throws Exception {
        Long roomId = resources.save(new Resource("Room V", null, ResourceType.MEETING_ROOM, 4)).getId();
        Instant start = futureHour(25);
        createReservation(roomId, start, start.plus(1, ChronoUnit.HOURS));
        Integer cancelled = createReservation(roomId, start.plus(2, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS));
        mvc.perform(auth(patch("/api/reservations/" + cancelled + "/cancel"), userToken)).andExpect(status().isOk());

        mvc.perform(auth(get("/api/resources/" + roomId + "/availability"), userToken)
                        .param("from", start.minus(1, ChronoUnit.DAYS).toString())
                        .param("to", start.plus(1, ChronoUnit.DAYS).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].userName").doesNotExist());

        mvc.perform(auth(get("/api/resources/" + roomId + "/availability"), userToken)
                        .param("from", start.toString()).param("to", start.plus(40, ChronoUnit.DAYS).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersResourcesByMinimumCapacity() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        resources.save(new Resource("Cap " + tag + " small", null, ResourceType.MEETING_ROOM, 4));
        resources.save(new Resource("Cap " + tag + " big", null, ResourceType.MEETING_ROOM, 12));

        mvc.perform(auth(get("/api/resources"), userToken).param("name", tag).param("minCapacity", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].capacity").value(12));
    }

    private Integer createReservation(Long resourceId, Instant start, Instant end) throws Exception {
        String body = mvc.perform(auth(post("/api/reservations"), userToken).content(reservationBody(resourceId, start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Each test books on its own day offset so tests never collide on shared resources. */
    private static Instant futureHour(int daysAhead) {
        return Instant.now().plus(daysAhead, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private static String seriesBody(Long resourceId, Instant start, Instant end, int occurrences) {
        return "{\"resourceId\":" + resourceId + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
                + "\",\"frequency\":\"WEEKLY\",\"occurrences\":" + occurrences + "}";
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String login(String email, String password) throws Exception {
        return loginResponse(email, password).getCookie("access_token").getValue();
    }

    private MockHttpServletResponse loginResponse(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private String register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("access_token").getValue();
    }

    private static String registerBody(String email) {
        return "{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"password123\"}";
    }

    private static String reservationBody(Long resourceId, Instant start, Instant end) {
        return "{\"resourceId\":" + resourceId + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }
}
