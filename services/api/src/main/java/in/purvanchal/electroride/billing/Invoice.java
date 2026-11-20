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
    @Column(name = "customer_relation", updatable = false) public String customerRelation;
    @Column(name = "customer_guardian_name", updatable = false) public String customerGuardianName;
    @Column(name = "id_type", updatable = false) public String idType;
    @Column(name = "id_number", updatable = false) public String idNumber;
    @Column(name = "aadhaar_last4", updatable = false) public String aadhaarLast4;
    @Column(updatable = false) public String financier;
    @Column(name = "dealer_gstin", updatable = false) public String dealerGstin;
    @Column(name = "dealer_address", updatable = false) public String dealerAddress;
    @Column(name = "dealer_phone", updatable = false) public String dealerPhone;
    @Column(nullable = false, updatable = false) public String chassis;
    @Column(nullable = false, updatable = false) public String model;
    @Column(nullable = false, updatable = false) public String variant;
    @Column(updatable = false) public String colour;
    @Column(name = "motor_serial", nullable = false, updatable = false) public String motorSerial;
    /** Comma-separated battery pack serials. */
    @Column(name = "battery_serials", nullable = false, updatable = false) public String batterySerials;
    @Column(name = "charger_serial", nullable = false, updatable = false) public String chargerSerial;
    @Column(name = "controller_serial", nullable = false, updatable = false) public String controllerSerial;
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
    @Column(nullable = false, updatable = false) public BigDecimal subsidy;
    @Column(name = "grand_total", nullable = false, updatable = false) public BigDecimal grandTotal;
    @Column(name = "issued_by", nullable = false, updatable = false) public String issuedBy;
    /** APP = issued in this system; PAPER = recorded later from the paper invoice book. */
    @Column(nullable = false, updatable = false) public String source = "APP";
    @Column(name = "paper_book_number", updatable = false) public String paperBookNumber;
    @Column(name = "paper_invoice_number", updatable = false) public String paperInvoiceNumber;
    @Column(name = "created_at", insertable = false, updatable = false) public OffsetDateTime createdAt;
}
