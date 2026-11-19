package in.purvanchal.electroride.enquiry;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    List<Enquiry> findByBranch(String branch);
    List<Enquiry> findByBranchAndSalesperson(String branch, String salesperson);
}
