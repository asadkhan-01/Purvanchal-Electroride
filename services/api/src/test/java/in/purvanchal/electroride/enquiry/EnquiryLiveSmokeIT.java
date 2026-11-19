package in.purvanchal.electroride.enquiry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-only smoke test against an already-running backend.
 * Opt in with RUN_LIVE_SMOKE=true and optionally API_BASE_URL.
 */
class EnquiryLiveSmokeIT {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_SMOKE", matches = "true")
    void runningBackendHealthAndListRespond() throws Exception {
        String base = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> health = client.send(
            HttpRequest.newBuilder(URI.create(base + "/actuator/health")).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        HttpResponse<String> list = client.send(
            HttpRequest.newBuilder(URI.create(base + "/api/v1/enquiries")).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).startsWith("[");
    }
}
