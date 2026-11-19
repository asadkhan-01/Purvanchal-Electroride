package in.purvanchal.electroride.inventory;

import in.purvanchal.electroride.security.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class InventoryPolicyTest {
    private final InventoryPolicy policy = new InventoryPolicy();
    private AppUser actor(String role, String branch) {
        AppUser user = new AppUser();
        user.role = role; user.branch = branch;
        return user;
    }
    private Vehicle vehicle(String branch, String status, String destination) {
        Vehicle v = new Vehicle();
        v.branch = branch; v.status = status; v.transferDestination = destination;
        return v;
    }

    @Test void adminCanSeeAllStockAndRegisterAnywhere() {
        var admin = actor("ADMIN", null);
        assertThat(policy.canView(admin, vehicle("Ghazipur", "RESERVED", null))).isTrue();
        assertThatCode(() -> policy.requireManager(admin, "Varanasi")).doesNotThrowAnyException();
    }
    @Test void staffCanSeeOnlyTheirBranchOrIncomingTransfers() {
        var staff = actor("SALESPERSON", "Varanasi");
        assertThat(policy.canView(staff, vehicle("Varanasi", "AVAILABLE", null))).isTrue();
        assertThat(policy.canView(staff, vehicle("Ghazipur", "IN_TRANSIT", "Varanasi"))).isTrue();
        assertThat(policy.canView(staff, vehicle("Ghazipur", "AVAILABLE", null))).isFalse();
        assertThat(policy.canView(staff, vehicle("Ghazipur", "IN_TRANSIT", "Mau"))).isFalse();
    }
    @Test void salespersonCannotModifyStock() {
        assertThatThrownBy(() -> policy.requireManager(actor("SALESPERSON", "Varanasi"), "Varanasi"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
    @Test void branchManagerCannotModifyAnotherBranch() {
        var manager = actor("BRANCH_MANAGER", "Varanasi");
        assertThatCode(() -> policy.requireManager(manager, "Varanasi")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireManager(manager, "Mau"))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test void destinationManagerCanReceiveIncomingTransfer() {
        var incoming = vehicle("Ghazipur", "IN_TRANSIT", "Varanasi");
        assertThatCode(() -> policy.requireReceiver(actor("BRANCH_MANAGER", "Varanasi"), incoming))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireReceiver(actor("BRANCH_MANAGER", "Ghazipur"), incoming))
            .isInstanceOf(ResponseStatusException.class);
    }
}
