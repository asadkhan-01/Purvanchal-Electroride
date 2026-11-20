package in.purvanchal.electroride.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final EnquiryAccess access;

    public UserController(AppUserRepository users, PasswordEncoder encoder, EnquiryAccess access) {
        this.users = users;
        this.encoder = encoder;
        this.access = access;
    }

    public record CreateUser(
        @NotBlank String username,
        @NotBlank @Size(min = 12) String password,
        @NotBlank String displayName,
        @NotBlank String role,
        String branch
    ) {}

    public record UserView(Long id, String username, String displayName, String role, String branch, boolean enabled) {
        static UserView from(AppUser user) {
            return new UserView(user.id, user.username, user.displayName, user.role, user.branch, user.enabled);
        }
    }

    private void requireAdmin(Authentication authentication) {
        if (!"ADMIN".equals(access.current(authentication).role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator required");
        }
    }

    @GetMapping
    public List<UserView> list(Authentication authentication) {
        requireAdmin(authentication);
        return users.findAll().stream().map(UserView::from).toList();
    }

    /** Name and branch only, for assigning enquiries: admins see all, managers their branch, salespeople themselves. */
    public record SalespersonView(String displayName, String branch) {}

    @GetMapping("/salespeople")
    public List<SalespersonView> salespeople(Authentication authentication) {
        AppUser me = access.current(authentication);
        return users.findAll().stream()
            .filter(u -> u.enabled && "SALESPERSON".equals(u.role))
            .filter(u -> switch (me.role) {
                case "ADMIN" -> true;
                case "BRANCH_MANAGER" -> me.branch != null && me.branch.equals(u.branch);
                default -> u.username.equals(me.username);
            })
            .map(u -> new SalespersonView(u.displayName, u.branch))
            .sorted(java.util.Comparator.comparing(SalespersonView::branch).thenComparing(SalespersonView::displayName))
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUser input, Authentication authentication) {
        requireAdmin(authentication);
        if (!List.of("ADMIN", "BRANCH_MANAGER", "SALESPERSON").contains(input.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        }
        String branch = input.branch() == null ? null : input.branch().trim();
        if (!"ADMIN".equals(input.role()) && (branch == null || branch.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch required");
        }
        if (users.findByUsername(input.username().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        AppUser user = new AppUser();
        user.username = input.username().trim();
        user.passwordHash = encoder.encode(input.password());
        user.displayName = input.displayName().trim();
        user.role = input.role();
        user.branch = "ADMIN".equals(input.role()) ? null : branch;
        return UserView.from(users.save(user));
    }
}
