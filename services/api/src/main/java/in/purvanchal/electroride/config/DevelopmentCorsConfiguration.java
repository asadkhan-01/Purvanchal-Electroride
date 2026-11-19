package in.purvanchal.electroride.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Development-only CORS allowance for a single, explicitly configured web preview origin.
 * Never use '*' for this API, particularly before authentication exists.
 */
@Configuration
@ConditionalOnProperty(name = "app.cors.allowed-origin")
public class DevelopmentCorsConfiguration implements WebMvcConfigurer {
    private final String origin;

    public DevelopmentCorsConfiguration(@Value("${app.cors.allowed-origin}") String origin) {
        this.origin = origin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(origin)
            .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization")
            .maxAge(1800);
    }
}
