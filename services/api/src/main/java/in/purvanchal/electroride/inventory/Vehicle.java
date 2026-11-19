package in.purvanchal.electroride.inventory;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, unique = true) public String chassis;
    @Column(nullable = false) public String model;
    @Column(nullable = false) public String variant;
    public String colour;
    @Column(nullable = false) public String branch;
    @Column(nullable = false) public String status = "AVAILABLE";
    @Column(name = "motor_serial", nullable = false, unique = true) public String motorSerial;
    @Column(name = "battery_serial", nullable = false, unique = true) public String batterySerial;
    @Column(name = "charger_serial", nullable = false, unique = true) public String chargerSerial;
    @Column(name = "controller_serial", nullable = false, unique = true) public String controllerSerial;
    @Column(name = "transfer_destination") public String transferDestination;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public OffsetDateTime updatedAt;

    @PrePersist @PreUpdate
    void timestamp() { updatedAt = OffsetDateTime.now(); }
}
