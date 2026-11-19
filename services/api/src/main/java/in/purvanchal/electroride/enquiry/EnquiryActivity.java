package in.purvanchal.electroride.enquiry;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "enquiry_activities")
public class EnquiryActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "enquiry_id", nullable = false)
    public Long enquiryId;

    @Column(nullable = false, columnDefinition = "text")
    public String message;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime createdAt;
}
