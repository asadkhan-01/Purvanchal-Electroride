package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.booking.Booking;
import in.purvanchal.electroride.booking.BookingService;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Money figures scoped to the bookings the caller may see (admin: all, manager: branch, salesperson: own). */
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
    private final BookingService bookings;
    private final BillingService billing;
    private final InvoiceRepository invoices;
    private final PaymentReceiptRepository receipts;
    private final EnquiryAccess access;

    public FinanceController(BookingService bookings, BillingService billing, InvoiceRepository invoices,
                             PaymentReceiptRepository receipts, EnquiryAccess access) {
        this.bookings = bookings; this.billing = billing; this.invoices = invoices;
        this.receipts = receipts; this.access = access;
    }

    public record Due(Long bookingId, String customerName, String branch, String salesperson, String bookingStatus,
                      String invoiceNumber, boolean estimated, BigDecimal payable, BigDecimal received, BigDecimal balance) {}
    public record ReceiptRow(Long id, Long bookingId, String receiptNumber, String customerName, String branch,
                             BigDecimal amount, String mode, String reference, String receivedOn, String status) {}
    public record Summary(String month, BigDecimal invoicedThisMonth, int invoicesThisMonth,
                          BigDecimal collectedThisMonth, int receiptsThisMonth, Map<String, BigDecimal> collectedByMode,
                          BigDecimal outstandingDues, int bookingsWithDues, BigDecimal advancesHeld,
                          int deliveredThisMonth, List<Due> dues, List<ReceiptRow> recentReceipts) {}

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public Summary summary(@RequestParam(required = false) String month, Authentication auth) {
        AppUser actor = access.current(auth);
        YearMonth ym;
        try { ym = month == null ? YearMonth.from(BillingService.today()) : YearMonth.parse(month); }
        catch (DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be YYYY-MM"); }

        Map<Long, Booking> visible = bookings.list(actor).stream()
            .collect(Collectors.toMap(b -> b.id, Function.identity()));
        Set<Long> ids = visible.keySet();
        List<Invoice> invs = ids.isEmpty() ? List.of() : invoices.findByBookingIdIn(ids);
        List<PaymentReceipt> rcpts = ids.isEmpty() ? List.of() : receipts.findByBookingIdIn(ids);
        Map<Long, Invoice> invoiceByBooking = invs.stream().collect(Collectors.toMap(i -> i.bookingId, Function.identity()));
        BigDecimal zero = BigDecimal.ZERO.setScale(2);

        List<Invoice> monthInvoices = invs.stream().filter(i -> YearMonth.from(i.invoiceDate).equals(ym)).toList();
        List<PaymentReceipt> live = rcpts.stream().filter(r -> "RECEIVED".equals(r.status)).toList();
        List<PaymentReceipt> monthReceipts = live.stream().filter(r -> YearMonth.from(r.receivedOn).equals(ym)).toList();
        Map<String, BigDecimal> byMode = new TreeMap<>();
        monthReceipts.forEach(r -> byMode.merge(r.mode, r.amount, BigDecimal::add));
        Map<Long, BigDecimal> receivedByBooking = new HashMap<>();
        live.forEach(r -> receivedByBooking.merge(r.bookingId, r.amount, BigDecimal::add));

        List<Due> dues = new ArrayList<>();
        BigDecimal outstanding = zero, advances = zero;
        for (Booking b : visible.values()) {
            if ("CANCELLED".equals(b.status)) continue;
            BigDecimal received = receivedByBooking.getOrDefault(b.id, zero);
            Invoice inv = invoiceByBooking.get(b.id);
            if (inv == null) advances = advances.add(received);
            BillingService.Account acc = inv == null ? billing.accountOf(b) : null;
            BigDecimal payable = inv != null ? inv.grandTotal : acc.payable();
            if (payable == null) continue; // no approved quotation yet
            BigDecimal balance = payable.subtract(received);
            if (balance.signum() > 0) {
                if (inv != null) outstanding = outstanding.add(balance);
                dues.add(new Due(b.id, b.customerName, b.branch, b.salesperson, b.status,
                    inv == null ? null : inv.invoiceNumber, inv == null, payable, received, balance));
            }
        }
        dues.sort(Comparator.comparing(Due::estimated).thenComparing(Due::balance, Comparator.reverseOrder()));
        int dueCount = (int) dues.stream().filter(d -> !d.estimated()).count();
        int delivered = (int) visible.values().stream().filter(b -> "DELIVERED".equals(b.status) && b.deliveredAt != null
            && YearMonth.from(b.deliveredAt.atZoneSameInstant(BillingService.INDIA)).equals(ym)).count();
        List<ReceiptRow> recent = rcpts.stream().sorted(Comparator.comparing((PaymentReceipt r) -> r.id).reversed()).limit(15)
            .map(r -> new ReceiptRow(r.id, r.bookingId, r.receiptNumber, visible.get(r.bookingId).customerName, r.branch,
                r.amount, r.mode, r.reference, r.receivedOn.toString(), r.status)).toList();

        return new Summary(ym.toString(),
            monthInvoices.stream().map(i -> i.grandTotal).reduce(zero, BigDecimal::add), monthInvoices.size(),
            monthReceipts.stream().map(r -> r.amount).reduce(zero, BigDecimal::add), monthReceipts.size(), byMode,
            outstanding, dueCount, advances, delivered, dues, recent);
    }
}
