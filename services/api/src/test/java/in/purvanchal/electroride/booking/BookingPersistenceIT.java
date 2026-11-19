package in.purvanchal.electroride.booking;

import in.purvanchal.electroride.enquiry.Enquiry;
import in.purvanchal.electroride.enquiry.EnquiryRepository;
import in.purvanchal.electroride.inventory.Vehicle;
import in.purvanchal.electroride.inventory.VehicleRepository;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingPersistenceIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("booking_it").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired AppUserRepository users;
    @Autowired EnquiryRepository enquiries;
    @Autowired VehicleRepository vehicles;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void users() {
        createUser("booking-admin", "ADMIN", null, "Administrator");
        createUser("booking-manager", "BRANCH_MANAGER", "Varanasi", "Manager");
        createUser("booking-other", "BRANCH_MANAGER", "Ghazipur", "Other manager");
        createUser("booking-sales", "SALESPERSON", "Varanasi", "Aman");
    }

    private void createUser(String username, String role, String branch, String name) {
        if (users.findByUsername(username).isPresent()) return;
        AppUser user = new AppUser();
        user.username = username;
        user.passwordHash = encoder.encode("booking-test-password-123");
        user.displayName = name; user.role = role; user.branch = branch;
        users.save(user);
    }

    private TestRestTemplate as(String name) {
        return rest.withBasicAuth(name, "booking-test-password-123");
    }

    private Enquiry enquiry() {
        Enquiry e = new Enquiry();
        e.customerName = "Booking Customer";
        e.phone = "9876500000";
        e.branch = "Varanasi";
        e.salesperson = "Aman";
        e.source = "Walk-in";
        e.interestedModel = "Scooter";
        e.stage = "Interested";
        return enquiries.save(e);
    }

    private Vehicle vehicle() {
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Vehicle v = new Vehicle();
        v.chassis = "BOOK-" + token;
        v.model = "Scooter";
        v.variant = "Standard";
        v.branch = "Varanasi";
        v.motorSerial = "M-" + token;
        v.batterySerial = "B-" + token;
        v.chargerSerial = "C-" + token;
        v.controllerSerial = "CTL-" + token;
        return vehicles.save(v);
    }

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void bookingPreventsDoubleAllocationAndCancellationRestoresStockAndStage() {
        Enquiry customer = enquiry();
        Vehicle scooter = vehicle();
        String body = "{\"enquiryId\":" + customer.id + ",\"chassis\":\"" + scooter.chassis + "\"}";

        ResponseEntity<Booking> created = as("booking-sales").postForEntity(
            "/api/v1/bookings", json(body), Booking.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long bookingId = created.getBody().id;
        assertThat(created.getBody().status).isEqualTo("PENDING");
        assertThat(vehicles.findById(scooter.id).orElseThrow().status).isEqualTo("RESERVED");
        assertThat(as("booking-manager").postForEntity(
            "/api/v1/inventory/" + scooter.chassis + "/release", HttpEntity.EMPTY, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Vehicle otherScooter = vehicle();
        String sameCustomerDifferentVehicle = "{\"enquiryId\":" + customer.id + ",\"chassis\":\"" + otherScooter.chassis + "\"}";
        assertThat(as("booking-admin").postForEntity(
            "/api/v1/bookings", json(sameCustomerDifferentVehicle), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(as("booking-admin").postForEntity(
            "/api/v1/bookings", json(body), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(as("booking-other").getForEntity(
            "/api/v1/bookings/" + bookingId, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(as("booking-sales").postForEntity(
            "/api/v1/bookings/" + bookingId + "/confirm", HttpEntity.EMPTY, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Booking> confirmed = as("booking-manager").postForEntity(
            "/api/v1/bookings/" + bookingId + "/confirm", HttpEntity.EMPTY, Booking.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().status).isEqualTo("CONFIRMED");
        assertThat(enquiries.findById(customer.id).orElseThrow().stage).isEqualTo("Booked");

        assertThat(as("booking-manager").postForEntity(
            "/api/v1/bookings/" + bookingId + "/confirm", HttpEntity.EMPTY, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Booking> cancelled = as("booking-manager").postForEntity(
            "/api/v1/bookings/" + bookingId + "/cancel",
            json("{\"reason\":\"Customer withdrew\"}"), Booking.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status).isEqualTo("CANCELLED");
        assertThat(vehicles.findById(scooter.id).orElseThrow().status).isEqualTo("AVAILABLE");
        assertThat(enquiries.findById(customer.id).orElseThrow().stage).isEqualTo("Interested");

        BookingEvent[] events = as("booking-manager").getForObject(
            "/api/v1/bookings/" + bookingId + "/events", BookingEvent[].class);
        assertThat(events).extracting(e -> e.event).contains("CREATED", "CONFIRMED", "CANCELLED");
        assertThat(as("booking-manager").postForEntity(
            "/api/v1/bookings/" + bookingId + "/cancel",
            json("{\"reason\":\"Duplicate\"}"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsBranchMismatchAndMissingAuthentication() {
        Enquiry customer = enquiry();
        Vehicle scooter = vehicle();
        scooter.branch = "Ghazipur";
        vehicles.save(scooter);
        String body = "{\"enquiryId\":" + customer.id + ",\"chassis\":\"" + scooter.chassis + "\"}";

        assertThat(as("booking-admin").postForEntity(
            "/api/v1/bookings", json(body), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(vehicles.findById(scooter.id).orElseThrow().status).isEqualTo("AVAILABLE");
    }
}
