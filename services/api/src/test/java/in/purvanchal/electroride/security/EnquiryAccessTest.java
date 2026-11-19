package in.purvanchal.electroride.security;

import in.purvanchal.electroride.enquiry.Enquiry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnquiryAccessTest {
    private final EnquiryAccess access = new EnquiryAccess(mock(AppUserRepository.class));

    private AppUser user(String role, String branch, String name) {
        AppUser u = new AppUser();
        u.role = role;
        u.branch = branch;
        u.displayName = name;
        return u;
    }

    private Enquiry enquiry(String branch, String salesperson) {
        Enquiry e = new Enquiry();
        e.branch = branch;
        e.salesperson = salesperson;
        return e;
    }

    @Test
    void adminCanReadAcrossAllBranches() {
        assertThat(access.canSee(user("ADMIN", null, "Administrator"),
            enquiry("Ghazipur", "Sana"))).isTrue();
    }

    @Test
    void branchManagerCanReadOnlyTheirBranch() {
        AppUser manager = user("BRANCH_MANAGER", "Varanasi", "Manager");
        assertThat(access.canSee(manager, enquiry("Varanasi", "Aman"))).isTrue();
        assertThat(access.canSee(manager, enquiry("Ghazipur", "Aman"))).isFalse();
    }

    @Test
    void salespersonCanReadOnlyTheirAssignedEnquiriesWithinBranch() {
        AppUser aman = user("SALESPERSON", "Varanasi", "Aman");
        assertThat(access.canSee(aman, enquiry("Varanasi", "Aman"))).isTrue();
        assertThat(access.canSee(aman, enquiry("Varanasi", "Neha"))).isFalse();
        assertThat(access.canSee(aman, enquiry("Ghazipur", "Aman"))).isFalse();
    }

    @Test
    void inaccessibleRecordsReturn404() {
        assertThatThrownBy(() -> access.requireAccess(
            user("BRANCH_MANAGER", "Varanasi", "Manager"), enquiry("Ghazipur", "Sana")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void salespersonCannotAssignSomeoneElseOrAnotherBranch() {
        AppUser aman = user("SALESPERSON", "Varanasi", "Aman");
        assertThatThrownBy(() -> access.validateNew(aman, enquiry("Varanasi", "Neha")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> access.validateNew(aman, enquiry("Ghazipur", "Aman")))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void branchManagerCanCreateInTheirOwnBranch() {
        AppUser manager = user("BRANCH_MANAGER", "Varanasi", "Manager");
        assertThatCode(() -> access.validateNew(manager, enquiry("Varanasi", "Neha")))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> access.validateNew(manager, enquiry("Ghazipur", "Neha")))
            .isInstanceOf(ResponseStatusException.class);
    }
}
