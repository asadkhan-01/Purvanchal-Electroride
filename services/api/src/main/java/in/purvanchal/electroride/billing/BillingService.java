package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.booking.Booking;
import in.purvanchal.electroride.booking.BookingEvent;
import in.purvanchal.electroride.booking.BookingEventRepository;
import in.purvanchal.electroride.booking.BookingRepository;
import in.purvanchal.electroride.booking.BookingService;
import in.purvanchal.electroride.inventory.InventoryPolicy;
import in.purvanchal.electroride.inventory.Vehicle;
import in.purvanchal.electroride.inventory.VehicleMovement;
import in.purvanchal.electroride.inventory.VehicleMovementRepository;
import in.purvanchal.electroride.inventory.VehicleRepository;
import in.purvanchal.electroride.quotation.BookingQuotation;
import in.purvanchal.electroride.quotation.BookingQuotationRepository;
import in.purvanchal.electroride.security.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BillingService {
    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    static final Set<String> MODES = Set.of("CASH", "UPI", "CARD", "BANK_TRANSFER", "CHEQUE", "FINANCE");

    private final BookingService bookingService;
    private final BookingRepository bookings;
    private final BookingEventRepository events;
    private final BookingQuotationRepository quotations;
    private final InvoiceRepository invoices;
    private final PaymentReceiptRepository receipts;
    private final VehicleRepository vehicles;
    private final VehicleMovementRepository movements;
    private final InventoryPolicy policy;
    private final DocumentNumbers numbers;

    public BillingService(BookingService bookingService, BookingRepository bookings, BookingEventRepository events,
                          BookingQuotationRepository quotations, InvoiceRepository invoices,
                          PaymentReceiptRepository receipts, VehicleRepository vehicles,
                          VehicleMovementRepository movements, InventoryPolicy policy, DocumentNumbers numbers) {
        this.bookingService = bookingService; this.bookings = bookings; this.events = events;
        this.quotations = quotations; this.invoices = invoices; this.receipts = receipts;
        this.vehicles = vehicles; this.movements = movements; this.policy = policy; this.numbers = numbers;
    }

    /** What the customer owes on one booking. Before invoicing, payable is an estimate (quote + default GST). */
    public record Account(Long bookingId, String bookingStatus, boolean quotationApproved, boolean estimated,
                          BigDecimal payable, BigDecimal received, BigDecimal balance,
                          Invoice invoice, List<PaymentReceipt> receipts) {}

    public record PaymentRequest(BigDecimal amount, String mode, String reference, String financeCompany,
                                 LocalDate receivedOn, String notes) {}

    public record InvoiceRequest(String customerAddress, String customerGstin, BigDecimal gstRate, boolean interState) {}

    public static LocalDate today() { return LocalDate.now(INDIA); }

    @Transactional(readOnly = true)
    public Account account(Long bookingId, AppUser actor) {
        Booking booking = bookingService.get(bookingId, actor);
        return accountOf(booking);
    }

    Account accountOf(Booking booking) {
        BookingQuotation quote = quotations.findByBookingId(booking.id).orElse(null);
        Invoice invoice = invoices.findByBookingId(booking.id).orElse(null);
        List<PaymentReceipt> list = receipts.findByBookingIdOrderByIdDesc(booking.id);
        BigDecimal received = list.stream().filter(r -> "RECEIVED".equals(r.status))
            .map(r -> r.amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        boolean approved = quote != null && "APPROVED".equals(quote.status);
        BigDecimal payable = invoice != null ? invoice.grandTotal
            : approved ? BillingMath.estimatedTotal(quote.subtotalExcludingTax) : null;
        BigDecimal balance = payable == null ? null : payable.subtract(received);
        return new Account(booking.id, booking.status, approved, invoice == null, payable, received, balance, invoice, list);
    }

    @Transactional
    public PaymentReceipt recordPayment(Long bookingId, PaymentRequest req, AppUser actor) {
        Booking booking = lockVisible(bookingId, actor);
        if (!List.of("PENDING", "CONFIRMED").contains(booking.status))
            throw conflict("Payments can only be recorded on pending or confirmed bookings");
        // Validate the request itself (400) before business rules (409).
        BigDecimal amount = BillingMath.amount(req.amount());
        String mode = req.mode() == null ? "" : req.mode().trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) throw bad("Mode must be one of " + MODES);
        String reference = blankToNull(req.reference());
        String financeCompany = blankToNull(req.financeCompany());
        if (!"CASH".equals(mode) && reference == null)
            throw bad("Reference (UTR, transaction, cheque or loan number) is required for " + mode);
        if ("FINANCE".equals(mode) && financeCompany == null)
            throw bad("Finance company is required for finance disbursements");
        LocalDate on = req.receivedOn() == null ? today() : req.receivedOn();
        if (on.isAfter(today())) throw bad("Received date cannot be in the future");
        if (booking.createdAt != null && on.isBefore(booking.createdAt.atZoneSameInstant(INDIA).toLocalDate()))
            throw bad("Received date cannot be before the booking was created");

        Account account = accountOf(booking);
        if (!account.quotationApproved())
            throw conflict("Approve the quotation before recording payments");
        if (amount.compareTo(account.balance()) > 0)
            throw conflict("Amount exceeds balance due of Rs " + account.balance().toPlainString());
        if ("CASH".equals(mode)) {
            BigDecimal cash = account.receipts().stream()
                .filter(r -> "RECEIVED".equals(r.status) && "CASH".equals(r.mode))
                .map(r -> r.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (cash.add(amount).compareTo(BillingMath.CASH_LIMIT) >= 0)
                throw conflict("Cash for one booking must stay below Rs 2,00,000 (Income Tax Act s.269ST)");
        }
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.bookingId = booking.id;
        receipt.branch = booking.branch;
        receipt.receiptNumber = numbers.next("RCT", booking.branch, on);
        receipt.amount = amount;
        receipt.mode = mode;
        receipt.reference = reference;
        receipt.financeCompany = "FINANCE".equals(mode) ? financeCompany : null;
        receipt.receivedOn = on;
        receipt.notes = blankToNull(req.notes());
        receipt.recordedBy = actor.username;
        PaymentReceipt saved = receipts.save(receipt);
        log(booking.id, "PAYMENT_RECEIVED", actor, saved.receiptNumber + " · Rs " + amount.toPlainString() + " · " + mode);
        return saved;
    }

    @Transactional
    public PaymentReceipt voidPayment(Long bookingId, Long receiptId, String reason, AppUser actor) {
        Booking booking = lockVisible(bookingId, actor);
        policy.requireManager(actor, booking.branch);
        if ("DELIVERED".equals(booking.status)) throw conflict("Receipts of a delivered booking cannot be voided");
        if (reason == null || reason.isBlank()) throw bad("Void reason is required");
        PaymentReceipt receipt = receipts.lockById(receiptId)
            .filter(r -> r.bookingId.equals(bookingId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found"));
        if (!"RECEIVED".equals(receipt.status)) throw conflict("Receipt is already voided");
        receipt.status = "VOIDED";
        receipt.voidedBy = actor.username;
        receipt.voidReason = reason.trim();
        receipt.voidedAt = OffsetDateTime.now();
        log(booking.id, "PAYMENT_VOIDED", actor, receipt.receiptNumber + " · " + receipt.voidReason);
        return receipts.save(receipt);
    }

    @Transactional(readOnly = true)
    public Invoice invoice(Long bookingId, AppUser actor) {
        bookingService.get(bookingId, actor);
        return invoices.findByBookingId(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    @Transactional
    public Invoice issueInvoice(Long bookingId, InvoiceRequest req, AppUser actor) {
        Booking booking = lockVisible(bookingId, actor);
        policy.requireManager(actor, booking.branch);
        if (!"CONFIRMED".equals(booking.status)) throw conflict("Only confirmed bookings can be invoiced");
        if (invoices.existsByBookingId(bookingId)) throw conflict("Booking is already invoiced");
        BookingQuotation quote = quotations.findByBookingId(bookingId)
            .filter(q -> "APPROVED".equals(q.status))
            .orElseThrow(() -> conflict("An approved quotation is required to invoice"));
        String address = blankToNull(req.customerAddress());
        if (address == null || address.length() > 300) throw bad("Customer address is required (max 300 characters)");
        String gstin = blankToNull(req.customerGstin());
        if (gstin != null) {
            gstin = gstin.toUpperCase(Locale.ROOT);
            if (!gstin.matches("[0-9]{2}[A-Z0-9]{13}")) throw bad("Customer GSTIN must be 15 characters");
        }
        Vehicle vehicle = vehicles.findById(booking.vehicleId).orElseThrow(() -> conflict("Vehicle not found"));
        BigDecimal rate = BillingMath.rate(req.gstRate());
        BillingMath.Tax tax = BillingMath.tax(quote.subtotalExcludingTax, rate, req.interState());
        BigDecimal received = accountOf(booking).received();
        if (received.compareTo(tax.grandTotal()) > 0)
            throw conflict("Receipts exceed the invoice total; void the excess receipt first");

        LocalDate date = today();
        Invoice inv = new Invoice();
        inv.bookingId = booking.id;
        inv.branch = booking.branch;
        inv.invoiceNumber = numbers.next("INV", booking.branch, date);
        inv.invoiceDate = date;
        inv.customerName = booking.customerName;
        inv.customerPhone = booking.customerPhone;
        inv.customerAddress = address;
        inv.customerGstin = gstin;
        inv.chassis = vehicle.chassis;
        inv.model = vehicle.model;
        inv.variant = vehicle.variant;
        inv.colour = vehicle.colour;
        inv.motorSerial = vehicle.motorSerial;
        inv.batterySerial = vehicle.batterySerial;
        inv.vehiclePrice = quote.vehiclePrice;
        inv.discount = quote.discount;
        inv.accessories = quote.accessories;
        inv.otherCharges = quote.otherCharges;
        inv.taxableValue = quote.subtotalExcludingTax;
        inv.gstRate = rate;
        inv.taxType = req.interState() ? "IGST" : "CGST_SGST";
        inv.cgst = tax.cgst();
        inv.sgst = tax.sgst();
        inv.igst = tax.igst();
        inv.roundOff = tax.roundOff();
        inv.grandTotal = tax.grandTotal();
        inv.issuedBy = actor.username;
        Invoice saved = invoices.save(inv);
        log(booking.id, "INVOICED", actor, saved.invoiceNumber + " · Rs " + saved.grandTotal.toPlainString());
        return saved;
    }

    @Transactional
    public Booking deliver(Long bookingId, AppUser actor) {
        Booking booking = lockVisible(bookingId, actor);
        policy.requireManager(actor, booking.branch);
        if (!"CONFIRMED".equals(booking.status)) throw conflict("Only confirmed bookings can be delivered");
        Account account = accountOf(booking);
        if (account.invoice() == null) throw conflict("Issue the invoice before delivery");
        if (account.balance().signum() != 0)
            throw conflict("Balance due of Rs " + account.balance().toPlainString() + " must be collected before delivery");
        Vehicle current = vehicles.findById(booking.vehicleId).orElseThrow(() -> conflict("Vehicle not found"));
        Vehicle vehicle = vehicles.lockByChassis(current.chassis).orElseThrow(() -> conflict("Vehicle not found"));
        if (!"RESERVED".equals(vehicle.status)) throw conflict("Vehicle is not reserved for this booking");
        vehicle.status = "SOLD";
        vehicles.save(vehicle);
        VehicleMovement m = new VehicleMovement();
        m.vehicleId = vehicle.id; m.event = "DELIVERED_SOLD";
        m.fromBranch = vehicle.branch; m.toBranch = vehicle.branch; m.actorUsername = actor.username;
        movements.save(m);
        booking.status = "DELIVERED";
        booking.deliveredBy = actor.username;
        booking.deliveredAt = OffsetDateTime.now();
        log(booking.id, "DELIVERED", actor, account.invoice().invoiceNumber);
        return bookings.save(booking);
    }

    private Booking lockVisible(Long bookingId, AppUser actor) {
        bookingService.get(bookingId, actor); // 404 when outside the actor's scope
        return bookings.lockById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private void log(Long bookingId, String event, AppUser actor, String details) {
        BookingEvent e = new BookingEvent();
        e.bookingId = bookingId; e.event = event; e.actorUsername = actor.username;
        e.details = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        events.save(e);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
