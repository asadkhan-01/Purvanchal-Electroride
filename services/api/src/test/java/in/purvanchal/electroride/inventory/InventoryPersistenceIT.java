package in.purvanchal.electroride.inventory;

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

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryPersistenceIT {

    @Autowired org.springframework.jdbc.core.JdbcTemplate testBranchesJdbc;

    /** The seeded branches are the real ones; tests use their own test branches. */
    @BeforeEach
    void testBranches() {
        for (String b : java.util.List.of("Varanasi", "Ghazipur"))
            testBranchesJdbc.update("INSERT INTO branches (name, invoice_address) VALUES (?, ?) ON CONFLICT DO NOTHING", b, "Test address, " + b);
    }
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("inventory_it").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired TestRestTemplate http;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;

    private void user(String username, String role, String branch) {
        if (users.findByUsername(username).isPresent()) return;
        AppUser user = new AppUser();
        user.username = username;
        user.passwordHash = passwords.encode("integration-test-secret-123");
        user.displayName = username;
        user.role = role;
        user.branch = branch;
        users.save(user);
    }

    private TestRestTemplate as(String username) {
        return http.withBasicAuth(username, "integration-test-secret-123");
    }

    @BeforeEach
    void setup() {
        user("inventory-admin", "ADMIN", null);
        user("inventory-varanasi", "BRANCH_MANAGER", "Varanasi");
        user("inventory-ghazipur", "BRANCH_MANAGER", "Ghazipur");
        user("inventory-sales", "SALESPERSON", "Varanasi");
    }

    @Test
    void registersVehicleWithSeveralBatteries() {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {"chassis":"it-multi-1","model":"ZL Test","variant":"Dual","branch":"Varanasi",
             "motorSerial":"it-m-multi-1","batterySerials":["it-bat-a","it-bat-b"],
             "chargerSerial":"it-c-multi-1","controllerSerial":"it-k-multi-1"}
            """;
        ResponseEntity<Vehicle> created = as("inventory-varanasi").postForEntity(
            "/api/v1/inventory", new HttpEntity<>(body, json), Vehicle.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().batterySerials).containsExactly("IT-BAT-A", "IT-BAT-B");
        Vehicle read = as("inventory-varanasi").getForObject("/api/v1/inventory/IT-MULTI-1", Vehicle.class);
        assertThat(read.batterySerials).containsExactly("IT-BAT-A", "IT-BAT-B");

        String reusedBattery = body.replace("it-multi-1", "it-multi-2").replace("it-m-multi-1", "it-m-multi-2")
            .replace("it-c-multi-1", "it-c-multi-2").replace("it-k-multi-1", "it-k-multi-2").replace("it-bat-a", "it-bat-z");
        assertThat(as("inventory-varanasi").postForEntity("/api/v1/inventory", new HttpEntity<>(reusedBattery, json), String.class)
            .getStatusCode()).as("battery it-bat-b already fitted elsewhere").isEqualTo(HttpStatus.CONFLICT);

        String sameTwice = reusedBattery.replace("\"it-bat-z\",\"it-bat-b\"", "\"it-bat-y\",\"IT-BAT-Y\"");
        assertThat(as("inventory-varanasi").postForEntity("/api/v1/inventory", new HttpEntity<>(sameTwice, json), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String none = reusedBattery.replace("[\"it-bat-z\",\"it-bat-b\"]", "[]");
        assertThat(as("inventory-varanasi").postForEntity("/api/v1/inventory", new HttpEntity<>(none, json), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registersAndTransfersWithSeparateSourceAndDestination() {
        String chassis = "IT-CHASSIS-100";
        String payload = """
            {"chassis":"it-chassis-100","model":"Electric Scooter","variant":"Standard",
             "colour":"White","branch":"Varanasi",
             "motorSerial":"it-motor-100","batterySerial":"it-battery-100",
             "chargerSerial":"it-charger-100","controllerSerial":"it-controller-100"}
            """;
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Vehicle> registered = as("inventory-varanasi").postForEntity(
            "/api/v1/inventory", new HttpEntity<>(payload, json), Vehicle.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody().chassis).isEqualTo(chassis);
        assertThat(registered.getBody().status).isEqualTo("AVAILABLE");

        ResponseEntity<String> duplicate = as("inventory-admin").postForEntity(
            "/api/v1/inventory", new HttpEntity<>(payload, json), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> salespersonCannotReserve = as("inventory-sales").postForEntity(
            "/api/v1/inventory/" + chassis + "/reserve", HttpEntity.EMPTY, String.class);
        assertThat(salespersonCannotReserve.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Vehicle> reserved = as("inventory-varanasi").postForEntity(
            "/api/v1/inventory/" + chassis + "/reserve", HttpEntity.EMPTY, Vehicle.class);
        assertThat(reserved.getBody().status).isEqualTo("RESERVED");

        ResponseEntity<String> reservedTransfer = as("inventory-varanasi").postForEntity(
            "/api/v1/inventory/" + chassis + "/transfer",
            new HttpEntity<>("{\"destination\":\"Ghazipur\"}", json), String.class);
        assertThat(reservedTransfer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Vehicle> released = as("inventory-varanasi").postForEntity(
            "/api/v1/inventory/" + chassis + "/release", HttpEntity.EMPTY, Vehicle.class);
        assertThat(released.getBody().status).isEqualTo("AVAILABLE");

        ResponseEntity<Vehicle> transfer = as("inventory-varanasi").postForEntity(
            "/api/v1/inventory/" + chassis + "/transfer",
            new HttpEntity<>("{\"destination\":\"Ghazipur\"}", json), Vehicle.class);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transfer.getBody().branch).isEqualTo("Varanasi");
        assertThat(transfer.getBody().transferDestination).isEqualTo("Ghazipur");
        assertThat(transfer.getBody().status).isEqualTo("IN_TRANSIT");

        ResponseEntity<String> wrongReceiver = as("inventory-varanasi").postForEntity(
            "/api/v1/inventory/" + chassis + "/receive", HttpEntity.EMPTY, String.class);
        assertThat(wrongReceiver.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Vehicle> received = as("inventory-ghazipur").postForEntity(
            "/api/v1/inventory/" + chassis + "/receive", HttpEntity.EMPTY, Vehicle.class);
        assertThat(received.getBody().branch).isEqualTo("Ghazipur");
        assertThat(received.getBody().transferDestination).isNull();
        assertThat(received.getBody().status).isEqualTo("AVAILABLE");

        ResponseEntity<String> sourceNoLongerSees = as("inventory-varanasi").getForEntity(
            "/api/v1/inventory/" + chassis, String.class);
        assertThat(sourceNoLongerSees.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        VehicleMovement[] history = as("inventory-ghazipur").getForObject(
            "/api/v1/inventory/" + chassis + "/movements", VehicleMovement[].class);
        assertThat(history).extracting(m -> m.event).contains(
            "RECEIVED", "RESERVED", "RELEASED", "TRANSFER_STARTED", "TRANSFER_RECEIVED");
    }

    @Test
    void authenticationIsRequired() {
        ResponseEntity<String> response = new TestRestTemplate().getForEntity(
            "http://localhost:" + port() + "/api/v1/inventory", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    int serverPort;
    private int port() { return serverPort; }
}
