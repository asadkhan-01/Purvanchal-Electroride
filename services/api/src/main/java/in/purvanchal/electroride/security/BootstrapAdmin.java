package in.purvanchal.electroride.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap.username:}")
    private String username;

    @Value("${app.bootstrap.password:}")
    private String password;

    public BootstrapAdmin(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) return;
        if (password.length() < 12) {
            throw new IllegalStateException("Bootstrap password must be at least 12 characters");
        }
        if (users.count() != 0) return;
        AppUser admin = new AppUser();
        admin.username = username.trim();
        admin.displayName = "Administrator";
        admin.passwordHash = encoder.encode(password);
        admin.role = "ADMIN";
        admin.branch = null;
        users.save(admin);
    }
}
