package in.purvanchal.electroride.enquiry;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "enquiries")
public class Enquiry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "customer_name", nullable = false) public String customerName;
    @Column(nullable = false) public String phone;
    @Column(nullable = false) public String branch;
    @Column(nullable = false) public String salesperson;
    @Column(nullable = false) public String source;
    @Column(name = "interested_model", nullable = false) public String interestedModel;
    @Column(nullable = false) public String stage = "New";
    @Column(name = "follow_up_date") public LocalDate followUpDate;
    @Column(columnDefinition = "text") public String notes;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public OffsetDateTime updatedAt;

    @PrePersist @PreUpdate
    void updateTimestamp() { updatedAt = OffsetDateTime.now(); }
}
