package in.purvanchal.electroride.enquiry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-only smoke test against a running backend.
 * Always checks health and anonymous rejection.
 * Optional SMOKE_USERNAME/SMOKE_PASSWORD validate authorized list access.
 */
class EnquiryLiveSmokeIT {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_SMOKE", matches = "true")
    void runningBackendRequiresAuthentication() throws Exception {
        String base = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> health = client.send(
            HttpRequest.newBuilder(URI.create(base + "/actuator/health"))
                .timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        HttpResponse<String> anonymous = client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/enquiries"))
                .timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);

        String username = System.getenv("SMOKE_USERNAME");
        String password = System.getenv("SMOKE_PASSWORD");
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            String token = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> authorized = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/enquiries"))
                    .header("Authorization", "Basic " + token)
                    .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(authorized.statusCode()).isEqualTo(200);
            assertThat(authorized.body()).startsWith("[");
        }
    }
}
