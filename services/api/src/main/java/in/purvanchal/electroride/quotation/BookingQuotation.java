package in.purvanchal.electroride.quotation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "booking_quotations")
public class BookingQuotation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "booking_id", nullable = false, unique = true) public Long bookingId;
    @Column(name = "vehicle_price", nullable = false, precision = 14, scale = 2) public BigDecimal vehiclePrice;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal discount;
    @Column(nullable = false, precision = 14, scale = 2) public BigDecimal accessories;
    @Column(name = "other_charges", nullable = false, precision = 14, scale = 2) public BigDecimal otherCharges;
    @Column(name = "subtotal_excluding_tax", nullable = false, precision = 14, scale = 2)
    public BigDecimal subtotalExcludingTax;
    @Column(nullable = false) public String status = "DRAFT";
    @Column(name = "created_by", nullable = false) public String createdBy;
    @Column(name = "approved_by") public String approvedBy;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime createdAt;
    @Column(name = "approved_at") public OffsetDateTime approvedAt;
}
