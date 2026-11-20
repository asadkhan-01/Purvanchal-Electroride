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
    @Autowired in.purvanchal.electroride.quotation.BookingQuotationRepository quotations;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

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
        v.batterySerials = new java.util.ArrayList<>(java.util.List.of("B-" + token));
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
        assertThat(new TestRestTemplate().getForEntity(
            "http://localhost:" + port + "/api/v1/bookings", String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Enquiry customer = enquiry();
        Vehicle scooter = vehicle();
        scooter.branch = "Ghazipur";
        vehicles.save(scooter);
        String body = "{\"enquiryId\":" + customer.id + ",\"chassis\":\"" + scooter.chassis + "\"}";

        assertThat(as("booking-admin").postForEntity(
            "/api/v1/bookings", json(body), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(vehicles.findById(scooter.id).orElseThrow().status).isEqualTo("AVAILABLE");
    }
    @Test
    void directBookingBySalespersonCanBeConfirmedAndCancelledByManager() {
        Vehicle scooter = vehicle();
        String payload = "{\"chassis\":\"" + scooter.chassis
            + "\",\"customerName\":\"Walk-in Customer\",\"customerPhone\":\"9876543210\"}";
        ResponseEntity<Booking> created = as("booking-sales").postForEntity(
            "/api/v1/bookings", json(payload), Booking.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Booking booking = created.getBody();
        assertThat(booking).isNotNull();
        assertThat(booking.enquiryId).isNull();
        assertThat(booking.priorEnquiryStage).isNull();
        assertThat(booking.customerName).isEqualTo("Walk-in Customer");
        assertThat(booking.salesperson).isEqualTo("Aman");
        assertThat(vehicles.findById(scooter.id).orElseThrow().status).isEqualTo("RESERVED");

        assertThat(as("booking-other").getForEntity("/api/v1/bookings/" + booking.id, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(as("booking-sales").getForEntity("/api/v1/bookings/" + booking.id, Booking.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(as("booking-sales").postForEntity(
            "/api/v1/bookings/" + booking.id + "/confirm", HttpEntity.EMPTY, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Enquiry unrelatedEnquiry = enquiry();
        String otherBooking = "{\"enquiryId\":" + unrelatedEnquiry.id
            + ",\"chassis\":\"" + scooter.chassis + "\"}";
        assertThat(as("booking-admin").postForEntity(
            "/api/v1/bookings", json(otherBooking), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Booking> confirmed = as("booking-manager").postForEntity(
            "/api/v1/bookings/" + booking.id + "/confirm", HttpEntity.EMPTY, Booking.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().enquiryId).isNull();
        assertThat(confirmed.getBody().status).isEqualTo("CONFIRMED");

        ResponseEntity<Booking> cancelled = as("booking-manager").postForEntity(
            "/api/v1/bookings/" + booking.id + "/cancel",
            json("{\"reason\":\"Walk-in customer withdrew\"}"), Booking.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status).isEqualTo("CANCELLED");
        assertThat(vehicles.findById(scooter.id).orElseThrow().status).isEqualTo("AVAILABLE");

        BookingEvent[] events = as("booking-sales").getForObject(
            "/api/v1/bookings/" + booking.id + "/events", BookingEvent[].class);
        assertThat(events).extracting(e -> e.event).contains(
            "CREATED_DIRECT", "CONFIRMED", "CANCELLED");
    }

    @Test
    void directBookingRejectsMissingCustomerAndWrongBranch() {
        Vehicle scooter = vehicle();
        String missingCustomer = "{\"chassis\":\"" + scooter.chassis + "\"}";
        assertThat(as("booking-sales").postForEntity(
            "/api/v1/bookings", json(missingCustomer), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String validPayload = "{\"chassis\":\"" + scooter.chassis
            + "\",\"customerName\":\"Walk-in\",\"customerPhone\":\"9876543210\"}";
        assertThat(as("booking-other").postForEntity(
            "/api/v1/bookings", json(validPayload), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(vehicles.findById(scooter.id).orElseThrow().status).isEqualTo("AVAILABLE");
    }

    @Test
    void quotationPersistsExactAmountsAndRequiresManagerApproval() {
        Vehicle scooter = vehicle();
        String bookingPayload = "{\"chassis\":\"" + scooter.chassis
            + "\",\"customerName\":\"Quote Customer\",\"customerPhone\":\"9876543210\"}";
        ResponseEntity<Booking> booked = as("booking-sales").postForEntity(
            "/api/v1/bookings", json(bookingPayload), Booking.class);
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = booked.getBody().id;

        String quotationBody = """
            {"vehiclePrice":105000.25,"discount":5000.10,
             "accessories":2250.25,"otherCharges":750.00}
            """;
        ResponseEntity<in.purvanchal.electroride.quotation.BookingQuotation> created =
            as("booking-sales").postForEntity("/api/v1/bookings/" + id + "/quotation",
                json(quotationBody), in.purvanchal.electroride.quotation.BookingQuotation.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().status).isEqualTo("DRAFT");
        assertThat(created.getBody().subtotalExcludingTax)
            .isEqualByComparingTo("103000.40");
        assertThat(quotations.findByBookingId(id)).isPresent();

        assertThat(as("booking-other").getForEntity(
            "/api/v1/bookings/" + id + "/quotation", String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(as("booking-sales").postForEntity(
            "/api/v1/bookings/" + id + "/quotation/approve", HttpEntity.EMPTY, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(as("booking-manager").postForEntity(
            "/api/v1/bookings/" + id + "/quotation", json(quotationBody), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<in.purvanchal.electroride.quotation.BookingQuotation> approved =
            as("booking-manager").postForEntity(
                "/api/v1/bookings/" + id + "/quotation/approve", HttpEntity.EMPTY,
                in.purvanchal.electroride.quotation.BookingQuotation.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status).isEqualTo("APPROVED");
        assertThat(approved.getBody().approvedBy).isEqualTo("booking-manager");
        assertThat(as("booking-manager").postForEntity(
            "/api/v1/bookings/" + id + "/quotation/approve", HttpEntity.EMPTY, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        BookingEvent[] audit = as("booking-manager").getForObject(
            "/api/v1/bookings/" + id + "/events", BookingEvent[].class);
        assertThat(audit).extracting(event -> event.event)
            .contains("QUOTATION_CREATED", "QUOTATION_APPROVED");
    }

}
