package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.booking.Booking;
import in.purvanchal.electroride.booking.BookingRepository;
import in.purvanchal.electroride.inventory.InventoryPolicy;
import in.purvanchal.electroride.inventory.Vehicle;
import in.purvanchal.electroride.inventory.VehicleMovement;
import in.purvanchal.electroride.inventory.VehicleMovementRepository;
import in.purvanchal.electroride.inventory.VehicleRepository;
import in.purvanchal.electroride.quotation.BookingQuotation;
import in.purvanchal.electroride.quotation.BookingQuotationRepository;
import in.purvanchal.electroride.quotation.QuotationMath;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Records a sale that already happened on paper: vehicle, original paper invoice, payments and
 * delivery with their real dates. Branch managers (own branch) and admins only.
 */
@Service
public class PastSaleService {
    private final BillingService billing;
    private final BookingRepository bookings;
    private final BookingQuotationRepository quotations;
    private final InvoiceRepository invoices;
    private final PaymentReceiptRepository receipts;
    private final VehicleRepository vehicles;
    private final VehicleMovementRepository movements;
    private final AppUserRepository users;
    private final InventoryPolicy policy;
    private final JdbcTemplate jdbc;
    private final in.purvanchal.electroride.branch.Branches branchCheck;

    public PastSaleService(BillingService billing, BookingRepository bookings, BookingQuotationRepository quotations,
                           InvoiceRepository invoices, PaymentReceiptRepository receipts, VehicleRepository vehicles,
                           VehicleMovementRepository movements, AppUserRepository users, InventoryPolicy policy,
                           JdbcTemplate jdbc, in.purvanchal.electroride.branch.Branches branchCheck) {
        this.branchCheck = branchCheck;
        this.billing = billing; this.bookings = bookings; this.quotations = quotations; this.invoices = invoices;
        this.receipts = receipts; this.vehicles = vehicles; this.movements = movements; this.users = users;
        this.policy = policy; this.jdbc = jdbc;
    }

    public record NewVehicle(String model, String variant, String colour, String motorSerial,
                             List<String> batterySerials, String chargerSerial, String controllerSerial) {}
    public record PastPayment(BigDecimal amount, String mode, String reference, String financeCompany, LocalDate receivedOn) {}
    public record Request(String branch, String salesperson,
                          String customerName, String customerPhone, BillingService.InvoiceRequest customer,
                          String chassis, NewVehicle newVehicle,
                          String paperBookNumber, String paperInvoiceNumber, LocalDate invoiceDate, LocalDate deliveredOn,
                          BigDecimal vehiclePrice, BigDecimal discount, BigDecimal accessories, BigDecimal otherCharges,
                          List<PastPayment> payments) {}
    public record Result(Booking booking, Invoice invoice, List<PaymentReceipt> receipts) {}

    @Transactional
    public Result record(Request r, AppUser actor) {
        String branch = trim(r.branch());
        if (branch == null) throw bad("Branch is required");
        policy.requireManager(actor, branch);
        branchCheck.requireActive(branch);
        LocalDate today = BillingService.today();

        if (r.customer() == null) throw bad("Customer details (address etc.) are required");
        String name = trim(r.customerName());
        if (name == null || name.length() > 160) throw bad("Customer name is required");
        if (r.customerPhone() == null || !r.customerPhone().trim().matches("[0-9]{10}")) throw bad("10-digit customer mobile is required");
        String paperNo = trim(r.paperInvoiceNumber());
        if (paperNo == null || paperNo.length() > 40) throw bad("Paper invoice number is required");
        String book = trim(r.paperBookNumber());
        if (book != null && book.length() > 20) throw bad("Book number is too long");
        if (r.vehiclePrice() == null) throw bad("Vehicle price is required");
        if (r.invoiceDate() == null || r.invoiceDate().isAfter(today)) throw bad("Invoice date is required and cannot be in the future");
        if (r.invoiceDate().isBefore(today.minusYears(8))) throw bad("Invoice date is too old");
        if (r.deliveredOn() == null || r.deliveredOn().isAfter(today) || r.deliveredOn().isBefore(r.invoiceDate()))
            throw bad("Delivery date is required, on or after the invoice date, and not in the future");

        String salesperson = trim(r.salesperson());
        if (salesperson == null) salesperson = actor.displayName;
        final String sp = salesperson;
        boolean known = sp.equals(actor.displayName) || users.findAll().stream().anyMatch(u -> u.enabled
            && sp.equals(u.displayName) && branch.equals(u.branch) && List.of("SALESPERSON", "BRANCH_MANAGER").contains(u.role));
        if (!known) throw bad("Salesperson must be an account in " + branch);

        // Vehicle: existing unsold stock in this branch, or new details recorded straight as sold.
        String chassis = trim(r.chassis());
        if (chassis == null) throw bad("Chassis / frame number is required");
        chassis = chassis.toUpperCase(Locale.ROOT);
        Vehicle vehicle = vehicles.lockByChassis(chassis).orElse(null);
        boolean newStock = vehicle == null;
        if (newStock) {
            NewVehicle nv = r.newVehicle();
            if (nv == null || trim(nv.model()) == null || trim(nv.variant()) == null || trim(nv.motorSerial()) == null
                    || trim(nv.chargerSerial()) == null || trim(nv.controllerSerial()) == null
                    || nv.batterySerials() == null || nv.batterySerials().stream().allMatch(b -> trim(b) == null))
                throw bad("Vehicle " + chassis + " is not in stock: give model, variant, motor, battery, charger and controller numbers");
            List<String> batteries = nv.batterySerials().stream().map(PastSaleService::trim)
                .filter(b -> b != null).map(b -> b.toUpperCase(Locale.ROOT)).toList();
            if (batteries.size() > 6 || batteries.stream().distinct().count() != batteries.size())
                throw bad("Up to 6 different battery serials");
            vehicle = new Vehicle();
            vehicle.chassis = chassis; vehicle.model = trim(nv.model()); vehicle.variant = trim(nv.variant());
            vehicle.colour = trim(nv.colour()); vehicle.branch = branch; vehicle.status = "SOLD";
            vehicle.motorSerial = nv.motorSerial().trim().toUpperCase(Locale.ROOT);
            vehicle.batterySerials = new ArrayList<>(batteries);
            vehicle.chargerSerial = nv.chargerSerial().trim().toUpperCase(Locale.ROOT);
            vehicle.controllerSerial = nv.controllerSerial().trim().toUpperCase(Locale.ROOT);
            try { vehicle = vehicles.saveAndFlush(vehicle); }
            catch (DataIntegrityViolationException e) { throw conflict("A component serial is already registered on another vehicle"); }
        } else {
            if (!branch.equals(vehicle.branch)) throw conflict("Vehicle " + chassis + " belongs to " + vehicle.branch);
            if (!"AVAILABLE".equals(vehicle.status)) throw conflict("Vehicle " + chassis + " is " + vehicle.status + ", not available stock");
            vehicle.status = "SOLD";
            vehicle = vehicles.save(vehicle);
        }

        OffsetDateTime deliveredAt = r.deliveredOn().atTime(LocalTime.NOON).atZone(BillingService.INDIA).toOffsetDateTime();
        Booking booking = new Booking();
        booking.vehicleId = vehicle.id; booking.branch = branch;
        booking.customerName = name; booking.customerPhone = r.customerPhone().trim();
        booking.salesperson = sp; booking.createdBy = actor.username;
        booking.status = "DELIVERED"; booking.confirmedBy = actor.username;
        booking.deliveredBy = actor.username; booking.deliveredAt = deliveredAt;
        booking.recordedFromPaper = true;
        booking = bookings.saveAndFlush(booking);

        BigDecimal subtotal = QuotationMath.subtotal(r.vehiclePrice(), zero(r.discount()), zero(r.accessories()), zero(r.otherCharges()));
        BookingQuotation quote = new BookingQuotation();
        quote.bookingId = booking.id;
        quote.vehiclePrice = r.vehiclePrice().setScale(2); quote.discount = zero(r.discount()).setScale(2);
        quote.accessories = zero(r.accessories()).setScale(2); quote.otherCharges = zero(r.otherCharges()).setScale(2);
        quote.subtotalExcludingTax = subtotal; quote.status = "APPROVED";
        quote.createdBy = actor.username; quote.approvedBy = actor.username; quote.approvedAt = deliveredAt;
        quotations.save(quote);

        // Payments (validated before the invoice so totals can be checked against it).
        List<PastPayment> pays = r.payments() == null ? List.of() : r.payments();
        BigDecimal received = BigDecimal.ZERO.setScale(2), cash = BigDecimal.ZERO.setScale(2);
        for (PastPayment p : pays) {
            BigDecimal amount = BillingMath.amount(p.amount());
            String mode = p.mode() == null ? "" : p.mode().trim().toUpperCase(Locale.ROOT);
            if (!BillingService.MODES.contains(mode)) throw bad("Payment mode must be one of " + BillingService.MODES);
            if (!"CASH".equals(mode) && trim(p.reference()) == null) throw bad("Reference is required for " + mode + " payments");
            if ("FINANCE".equals(mode) && trim(p.financeCompany()) == null) throw bad("Finance company is required for finance payments");
            if (p.receivedOn() == null || p.receivedOn().isAfter(today) || p.receivedOn().isBefore(r.invoiceDate().minusYears(1)))
                throw bad("Each payment needs a date that is not in the future");
            received = received.add(amount);
            if ("CASH".equals(mode)) cash = cash.add(amount);
        }
        if (cash.compareTo(BillingMath.CASH_LIMIT) >= 0)
            throw bad("Cash for one sale must be below Rs 2,00,000 (Income Tax Act s.269ST)");

        String code = DocumentNumbers.branchCode(branch);
        String number = "PAPER/" + code + "/" + (book == null ? "-" : book) + "/" + paperNo;
        if (number.length() > 36) throw bad("Book and invoice numbers are too long");
        Invoice inv = billing.buildInvoice(booking, vehicle, quote, r.customer(), r.invoiceDate(), () -> number, received, true, actor);
        inv.source = "PAPER"; inv.paperBookNumber = book; inv.paperInvoiceNumber = paperNo;
        try { inv = invoices.saveAndFlush(inv); }
        catch (DataIntegrityViolationException e) {
            throw conflict("Paper invoice " + (book == null ? "" : "book " + book + " ") + "no. " + paperNo + " is already recorded for " + branch);
        }

        List<PaymentReceipt> saved = new ArrayList<>();
        int n = 0;
        for (PastPayment p : pays) {
            PaymentReceipt rc = new PaymentReceipt();
            rc.bookingId = booking.id; rc.branch = branch;
            rc.receiptNumber = number + "/P" + (++n);
            rc.amount = BillingMath.amount(p.amount()); rc.mode = p.mode().trim().toUpperCase(Locale.ROOT);
            rc.reference = trim(p.reference()); rc.financeCompany = "FINANCE".equals(rc.mode) ? trim(p.financeCompany()) : null;
            rc.receivedOn = p.receivedOn(); rc.notes = "Recorded from paper invoice"; rc.recordedBy = actor.username;
            saved.add(receipts.save(rc));
        }

        // Back-date the booking to the earliest real event so lists and reports read correctly.
        LocalDate first = pays.stream().map(PastPayment::receivedOn).filter(d -> d.isBefore(r.invoiceDate()))
            .min(LocalDate::compareTo).orElse(r.invoiceDate());
        jdbc.update("UPDATE bookings SET created_at = ? WHERE id = ?",
            first.atTime(LocalTime.of(10, 0)).atZone(BillingService.INDIA).toOffsetDateTime(), booking.id);
        if (newStock) {
            VehicleMovement in = new VehicleMovement();
            in.vehicleId = vehicle.id; in.event = "RECORDED_PAST_SALE"; in.fromBranch = branch; in.toBranch = branch;
            in.actorUsername = actor.username; movements.save(in);
        }
        VehicleMovement out = new VehicleMovement();
        out.vehicleId = vehicle.id; out.event = "DELIVERED_SOLD"; out.fromBranch = branch; out.toBranch = branch;
        out.actorUsername = actor.username; movements.save(out);
        billing.log(booking.id, "RECORDED_PAST_SALE", actor, inv.invoiceNumber + " · dated " + r.invoiceDate()
            + " · delivered " + r.deliveredOn() + " · Rs " + inv.grandTotal.toPlainString());
        return new Result(bookings.findById(booking.id).orElse(booking), inv, saved);
    }

    private static BigDecimal zero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
