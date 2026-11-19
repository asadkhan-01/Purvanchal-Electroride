package in.purvanchal.electroride.security;

import in.purvanchal.electroride.enquiry.Enquiry;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EnquiryAccess {
    private final AppUserRepository users;

    public EnquiryAccess(AppUserRepository users) { this.users = users; }

    public AppUser current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByUsername(authentication.getName())
            .filter(user -> user.enabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public boolean canSee(AppUser user, Enquiry enquiry) {
        return switch (user.role) {
            case "ADMIN" -> true;
            case "BRANCH_MANAGER" -> user.branch != null && user.branch.equals(enquiry.branch);
            case "SALESPERSON" -> user.branch != null
                && user.branch.equals(enquiry.branch)
                && user.displayName.equals(enquiry.salesperson);
            default -> false;
        };
    }

    public void requireAccess(AppUser user, Enquiry enquiry) {
        if (!canSee(user, enquiry)) {
            // Avoid exposing the existence of inaccessible customer records.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found");
        }
    }

    public void validateNew(AppUser user, Enquiry enquiry) {
        if ("ADMIN".equals(user.role)) return;
        if (!enquiry.branch.equals(user.branch)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Outside assigned branch");
        }
        if ("SALESPERSON".equals(user.role) && !enquiry.salesperson.equals(user.displayName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign another salesperson");
        }
        if (!"SALESPERSON".equals(user.role) && !"BRANCH_MANAGER".equals(user.role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
