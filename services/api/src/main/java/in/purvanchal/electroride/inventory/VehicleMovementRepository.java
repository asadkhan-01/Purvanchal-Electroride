package in.purvanchal.electroride.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehicleMovementRepository extends JpaRepository<VehicleMovement, Long> {
    List<VehicleMovement> findByVehicleIdOrderByIdDesc(Long vehicleId);
}
