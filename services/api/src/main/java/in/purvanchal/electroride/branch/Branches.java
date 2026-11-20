package in.purvanchal.electroride.branch;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Checks that a branch name refers to an active branch. */
@Component
public class Branches {
    private final BranchRepository repo;

    public Branches(BranchRepository repo) { this.repo = repo; }

    public String requireActive(String name) {
        String n = name == null ? "" : name.trim();
        return repo.findById(n).filter(b -> b.active).map(b -> b.name)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown branch: " + n));
    }

    public String invoiceAddress(String name) {
        return name == null ? null : repo.findById(name).map(b -> b.invoiceAddress).orElse(null);
    }
}
