package in.purvanchal.electroride.enquiry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.http.*;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.enquiry.EnquiryRepository;
import in.purvanchal.electroride.security.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnquiryPersistenceIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("electroride_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @Autowired AppUserRepository users;
    @Autowired EnquiryRepository enquiries;
    @Autowired PasswordEncoder encoder;
    @LocalServerPort int port;

    @BeforeEach
    void authenticate() {
        if (users.findByUsername("integration-admin").isEmpty()) {
            AppUser user = new AppUser();
            user.username = "integration-admin";
            user.passwordHash = encoder.encode("integration-secret-123");
            user.role = "ADMIN";
            user.displayName = "Integration Administrator";
            users.save(user);
        }
        rest = rest.withBasicAuth("integration-admin", "integration-secret-123");
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString(
            "integration-admin:integration-secret-123".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void createReadUpdateAndListArePersisted() throws Exception {
        String payload = """
            {"customerName":"Integration Test","phone":"9876500000",
             "branch":"Varanasi","salesperson":"Aman","source":"Walk-in",
             "interestedModel":"Electric Scooter"}
            """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Enquiry> created = rest.postForEntity(
            "/api/v1/enquiries", new HttpEntity<>(payload, headers), Enquiry.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Long id = created.getBody().id;
        assertThat(id).isNotNull();

        ResponseEntity<Enquiry> fetched = rest.getForEntity("/api/v1/enquiries/" + id, Enquiry.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().customerName).isEqualTo("Integration Test");

        HttpResponse<String> changed = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/enquiries/" + id + "/stage"))
                .header("Content-Type", "application/json")
                .header("Authorization", basicAuth())
                .method("PATCH", HttpRequest.BodyPublishers.ofString("""
                    {"stage":"Interested"}
                    """))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(changed.statusCode()).isEqualTo(200);
        HttpResponse<String> activityResponse = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/enquiries/" + id + "/activities"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {"message":"Called to confirm interest"}
                    """))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(activityResponse.statusCode()).isEqualTo(201);
        assertThat(activityResponse.body()).contains("Called to confirm interest");

        HttpResponse<String> followUpResponse = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/enquiries/" + id + "/follow-up"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("""
                    {"followUpDate":"2026-12-05"}
                    """))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(followUpResponse.statusCode()).isEqualTo(200);
        assertThat(followUpResponse.body()).contains("2026-12-05");

        ResponseEntity<String> history = rest.getForEntity("/api/v1/enquiries/" + id + "/activities", String.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).contains("Called to confirm interest", "Stage changed to Interested",
            "Follow-up scheduled for 2026-12-05");
        Enquiry updated = rest.getForObject("/api/v1/enquiries/" + id, Enquiry.class);
        assertThat(updated.stage).isEqualTo("Interested");

        Enquiry[] list = rest.getForObject("/api/v1/enquiries", Enquiry[].class);
        assertThat(list).extracting(e -> e.id).contains(id);
    }

    @Test
    void flywayCreatesEnquiriesTable() {
        Enquiry[] list = rest.getForObject("/api/v1/enquiries", Enquiry[].class);
        assertThat(list).isNotNull();
    }
    @Test
    void rejectsUnauthenticatedRequestsAndProtectsCrossBranchData() {
        TestRestTemplate anonymous = new TestRestTemplate();
        assertThat(anonymous.getForEntity("http://localhost:" + port + "/api/v1/enquiries",
            String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Enquiry owned = new Enquiry();
        owned.customerName = "Owner Sample";
        owned.phone = "9876500001";
        owned.branch = "Varanasi";
        owned.salesperson = "Aman";
        owned.source = "Walk-in";
        owned.interestedModel = "Demo EV";
        owned = enquiries.save(owned);

        Enquiry outside = new Enquiry();
        outside.customerName = "Other Branch Sample";
        outside.phone = "9876500002";
        outside.branch = "Ghazipur";
        outside.salesperson = "Sana";
        outside.source = "Phone";
        outside.interestedModel = "Demo EV";
        outside = enquiries.save(outside);

        AppUser sales = new AppUser();
        sales.username = "sales-aman";
        sales.passwordHash = encoder.encode("sales-password-123");
        sales.displayName = "Aman";
        sales.role = "SALESPERSON";
        sales.branch = "Varanasi";
        users.save(sales);

        TestRestTemplate salesperson = rest.withBasicAuth("sales-aman", "sales-password-123");
        ResponseEntity<Enquiry[]> list = salesperson.getForEntity("/api/v1/enquiries", Enquiry[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).extracting(e -> e.id).contains(owned.id).doesNotContain(outside.id);
        assertThat(salesperson.getForEntity("/api/v1/enquiries/" + outside.id, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String unauthorizedCreation = """
            {"customerName":"Wrong Branch","phone":"9876500003","branch":"Ghazipur",
             "salesperson":"Aman","source":"Walk-in","interestedModel":"Demo EV"}
            """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> forbidden = salesperson.postForEntity("/api/v1/enquiries",
            new HttpEntity<>(unauthorizedCreation, headers), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void onlyAdministratorCanProvisionUsers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = """
            {"username":"branch-manager-test","password":"strong-manager-pass-123",
             "displayName":"Demo Manager","role":"BRANCH_MANAGER","branch":"Varanasi"}
            """;
        ResponseEntity<String> created = rest.postForEntity("/api/v1/users",
            new HttpEntity<>(payload, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("branch-manager-test").doesNotContain("strong-manager-pass-123");

        TestRestTemplate manager = rest.withBasicAuth("branch-manager-test", "strong-manager-pass-123");
        assertThat(manager.getForEntity("/api/v1/auth/me", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manager.getForEntity("/api/v1/users", String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

}
