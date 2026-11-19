package in.purvanchal.electroride.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    List<Booking> findByBranch(String branch);
    List<Booking> findByBranchAndCreatedBy(String branch, String createdBy);
    boolean existsByEnquiryIdAndStatusIn(Long enquiryId, List<String> statuses);
    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<String> statuses);
}
