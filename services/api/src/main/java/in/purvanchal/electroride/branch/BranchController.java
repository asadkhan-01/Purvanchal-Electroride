package in.purvanchal.electroride.branch;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {
    private final BranchRepository repo;

    public BranchController(BranchRepository repo) { this.repo = repo; }

    public record BranchView(String name, String invoiceAddress) {}

    /** Active branches for any signed-in user (pickers, invoices). */
    @GetMapping
    public List<BranchView> list() {
        return repo.findByActiveTrueOrderByName().stream().map(b -> new BranchView(b.name, b.invoiceAddress)).toList();
    }
}
