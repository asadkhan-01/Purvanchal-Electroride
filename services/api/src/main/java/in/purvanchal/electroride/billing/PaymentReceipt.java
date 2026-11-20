package in.purvanchal.electroride.billing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Money received against a booking; voided (never deleted) when reversed. */
@Entity
@Table(name = "payment_receipts")
public class PaymentReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "booking_id", nullable = false, updatable = false) public Long bookingId;
    @Column(name = "receipt_number", nullable = false, unique = true, updatable = false) public String receiptNumber;
    @Column(nullable = false, updatable = false) public String branch;
    @Column(nullable = false, updatable = false) public BigDecimal amount;
    @Column(nullable = false, updatable = false) public String mode;
    @Column(updatable = false) public String reference;
    @Column(name = "finance_company", updatable = false) public String financeCompany;
    @Column(name = "received_on", nullable = false, updatable = false) public LocalDate receivedOn;
    @Column(updatable = false) public String notes;
    @Column(nullable = false) public String status = "RECEIVED";
    @Column(name = "recorded_by", nullable = false, updatable = false) public String recordedBy;
    @Column(name = "voided_by") public String voidedBy;
    @Column(name = "void_reason") public String voidReason;
    @Column(name = "voided_at") public OffsetDateTime voidedAt;
    @Column(name = "created_at", insertable = false, updatable = false) public OffsetDateTime createdAt;
}
