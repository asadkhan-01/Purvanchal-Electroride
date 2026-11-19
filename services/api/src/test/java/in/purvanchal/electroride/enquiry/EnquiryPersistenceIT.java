package in.purvanchal.electroride.enquiry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.http.*;
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
    @LocalServerPort int port;

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
}
