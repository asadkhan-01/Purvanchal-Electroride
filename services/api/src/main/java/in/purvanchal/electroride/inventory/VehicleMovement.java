package in.purvanchal.electroride.inventory;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vehicle_movements")
public class VehicleMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "vehicle_id", nullable = false) public Long vehicleId;
    @Column(nullable = false) public String event;
    @Column(name = "from_branch") public String fromBranch;
    @Column(name = "to_branch") public String toBranch;
    @Column(name = "actor_username", nullable = false) public String actorUsername;
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime occurredAt;
}
