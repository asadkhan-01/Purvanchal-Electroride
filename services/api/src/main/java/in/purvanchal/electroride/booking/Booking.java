package in.purvanchal.electroride.booking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "enquiry_id", nullable = false) public Long enquiryId;
    @Column(name = "vehicle_id", nullable = false) public Long vehicleId;
    @Column(nullable = false) public String branch;
    @Column(nullable = false) public String status = "PENDING";
    @Column(name = "created_by", nullable = false) public String createdBy;
    @Column(name = "confirmed_by") public String confirmedBy;
    @Column(name = "cancelled_by") public String cancelledBy;
    @Column(name = "cancellation_reason") public String cancellationReason;
    @Column(name = "created_at", insertable = false, updatable = false) public OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public OffsetDateTime updatedAt;

    @PrePersist @PreUpdate
    void timestamp() { updatedAt = OffsetDateTime.now(); }
}
