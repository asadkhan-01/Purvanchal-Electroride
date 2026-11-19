package in.purvanchal.electroride.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final EnquiryAccess access;
    public AuthController(EnquiryAccess access) { this.access = access; }

    public record CurrentUser(String username, String displayName, String role, String branch) {}

    @GetMapping("/me")
    public CurrentUser me(Authentication authentication) {
        AppUser user = access.current(authentication);
        return new CurrentUser(user.username, user.displayName, user.role, user.branch);
    }
}
