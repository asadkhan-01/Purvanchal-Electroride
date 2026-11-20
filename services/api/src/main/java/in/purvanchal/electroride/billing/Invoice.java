package in.purvanchal.electroride.billing;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Immutable GST tax invoice for one booking. */
@Entity
@Table(name = "invoices")
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "booking_id", nullable = false, unique = true, updatable = false) public Long bookingId;
    @Column(name = "invoice_number", nullable = false, unique = true, updatable = false) public String invoiceNumber;
    @Column(nullable = false, updatable = false) public String branch;
    @Column(name = "invoice_date", nullable = false, updatable = false) public LocalDate invoiceDate;
    @Column(name = "customer_name", nullable = false, updatable = false) public String customerName;
    @Column(name = "customer_phone", nullable = false, updatable = false) public String customerPhone;
    @Column(name = "customer_address", nullable = false, updatable = false) public String customerAddress;
    @Column(name = "customer_gstin", updatable = false) public String customerGstin;
    @Column(nullable = false, updatable = false) public String chassis;
    @Column(nullable = false, updatable = false) public String model;
    @Column(nullable = false, updatable = false) public String variant;
    @Column(updatable = false) public String colour;
    @Column(name = "motor_serial", nullable = false, updatable = false) public String motorSerial;
    @Column(name = "battery_serial", nullable = false, updatable = false) public String batterySerial;
    @Column(name = "vehicle_price", nullable = false, updatable = false) public BigDecimal vehiclePrice;
    @Column(nullable = false, updatable = false) public BigDecimal discount;
    @Column(nullable = false, updatable = false) public BigDecimal accessories;
    @Column(name = "other_charges", nullable = false, updatable = false) public BigDecimal otherCharges;
    @Column(name = "taxable_value", nullable = false, updatable = false) public BigDecimal taxableValue;
    @Column(name = "gst_rate", nullable = false, updatable = false) public BigDecimal gstRate;
    @Column(name = "tax_type", nullable = false, updatable = false) public String taxType;
    @Column(nullable = false, updatable = false) public BigDecimal cgst;
    @Column(nullable = false, updatable = false) public BigDecimal sgst;
    @Column(nullable = false, updatable = false) public BigDecimal igst;
    @Column(name = "round_off", nullable = false, updatable = false) public BigDecimal roundOff;
    @Column(name = "grand_total", nullable = false, updatable = false) public BigDecimal grandTotal;
    @Column(name = "issued_by", nullable = false, updatable = false) public String issuedBy;
    @Column(name = "created_at", insertable = false, updatable = false) public OffsetDateTime createdAt;
}
