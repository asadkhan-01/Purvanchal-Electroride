package in.purvanchal.electroride.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerSalespeopleTest {
    private final AppUserRepository repo = mock(AppUserRepository.class);
    private final UserController controller =
        new UserController(repo, mock(PasswordEncoder.class), new EnquiryAccess(repo),
            mock(in.purvanchal.electroride.branch.Branches.class));

    private static AppUser user(String username, String name, String role, String branch, boolean enabled) {
        AppUser u = new AppUser();
        u.username = username; u.displayName = name; u.role = role; u.branch = branch; u.enabled = enabled;
        return u;
    }

    private final AppUser admin = user("admin", "Admin", "ADMIN", null, true);
    private final AppUser manager = user("mgr", "Manager", "BRANCH_MANAGER", "Varanasi", true);
    private final AppUser salesA = user("a", "Asha", "SALESPERSON", "Varanasi", true);
    private final AppUser salesB = user("b", "Bilal", "SALESPERSON", "Mau", true);
    private final AppUser disabled = user("c", "Chetan", "SALESPERSON", "Varanasi", false);

    private Authentication as(AppUser me) {
        when(repo.findByUsername(me.username)).thenReturn(Optional.of(me));
        when(repo.findAll()).thenReturn(List.of(admin, manager, salesA, salesB, disabled));
        return UsernamePasswordAuthenticationToken.authenticated(me.username, null, List.of());
    }

    private List<String> names(AppUser me) {
        return controller.salespeople(as(me)).stream().map(UserController.SalespersonView::displayName).toList();
    }

    @Test
    void adminSeesAllEnabledSalespeople() {
        assertThat(names(admin)).containsExactly("Bilal", "Asha");
    }

    @Test
    void managerSeesOwnBranchOnly() {
        assertThat(names(manager)).containsExactly("Asha");
    }

    @Test
    void salespersonSeesOnlyThemselves() {
        assertThat(names(salesB)).containsExactly("Bilal");
    }
}
