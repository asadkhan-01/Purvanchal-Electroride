package in.purvanchal.electroride.enquiry;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnquiryActivityRepository extends JpaRepository<EnquiryActivity, Long> {
    List<EnquiryActivity> findByEnquiryIdOrderByCreatedAtDescIdDesc(Long enquiryId);
}
