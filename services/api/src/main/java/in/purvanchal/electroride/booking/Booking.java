package in.purvanchal.electroride.booking;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "enquiry_id") public Long enquiryId;
    @Column(name = "vehicle_id", nullable = false) public Long vehicleId;
    @Column(nullable = false) public String branch;
    @Column(name = "customer_name", nullable = false) public String customerName;
    @Column(name = "customer_phone", nullable = false) public String customerPhone;
    @Column(nullable = false) public String salesperson;
    @Column(nullable = false) public String status = "PENDING";
    @Column(name = "prior_enquiry_stage") public String priorEnquiryStage;
    @Column(name = "created_by", nullable = false) public String createdBy;
    @Column(name = "confirmed_by") public String confirmedBy;
    @Column(name = "cancelled_by") public String cancelledBy;
    @Column(name = "cancellation_reason") public String cancellationReason;
    @Column(name = "delivered_by") public String deliveredBy;
    @Column(name = "delivered_at") public OffsetDateTime deliveredAt;
    @Column(name = "recorded_from_paper", nullable = false) public boolean recordedFromPaper;
    @Column(name = "created_at", insertable = false, updatable = false) public OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public OffsetDateTime updatedAt;

    @PrePersist @PreUpdate
    void timestamp() { updatedAt = OffsetDateTime.now(); }
}
