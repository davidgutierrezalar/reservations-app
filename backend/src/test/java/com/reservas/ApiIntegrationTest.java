package com.reservas;

import com.jayway.jsonpath.JsonPath;
import com.reservas.reservation.Reservation;
import com.reservas.reservation.ReservationRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
        "app.admin.password=admin-password"})
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

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String registerBody(String email) {
        return "{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"password123\"}";
    }

    private static String reservationBody(Long resourceId, Instant start, Instant end) {
        return "{\"resourceId\":" + resourceId + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }
}
