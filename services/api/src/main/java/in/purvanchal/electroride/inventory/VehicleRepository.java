package in.purvanchal.electroride.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.chassis = :chassis")
    Optional<Vehicle> lockByChassis(@Param("chassis") String chassis);
    Optional<Vehicle> findByChassis(String chassis);
    List<Vehicle> findByBranch(String branch);
    List<Vehicle> findByBranchOrTransferDestination(String branch, String destination);
}
