package in.purvanchal.electroride.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return name -> {
            AppUser account = users.findByUsername(name)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(account.username)
                .password(account.passwordHash)
                .roles(account.role)
                .disabled(!account.enabled)
                .build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // Basic auth is DEVELOPMENT ONLY; requests carry explicit Authorization headers.
            // Replace it with a production-grade session/token design and CSRF controls before release.
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Bundled Expo web build (only present in the single-service Docker image).
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/metadata.json",
                    "/_expo/**", "/assets/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .anyRequest().authenticated())
            // Plain 401 without a WWW-Authenticate challenge, so browsers (notably iOS Safari) do not
            // show a native login dialog over the app's own sign-in screen.
            .httpBasic(basic -> basic.authenticationEntryPoint(
                (request, response, ex) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
            .formLogin(form -> form.disable())
            .build();
    }
}
