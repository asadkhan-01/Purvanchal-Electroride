package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.booking.Booking;
import in.purvanchal.electroride.booking.BookingRepository;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sales register: every invoiced sale (app or paper) in a date range, filterable by branch, model
 * and salesperson. Admins see all branches; branch managers only their own; salespeople have no access.
 */
@RestController
@RequestMapping("/api/v1/reports/sales")
public class SalesReportController {
    private final InvoiceRepository invoices;
    private final BookingRepository bookings;
    private final PaymentReceiptRepository receipts;
    private final EnquiryAccess access;

    public SalesReportController(InvoiceRepository invoices, BookingRepository bookings,
                                 PaymentReceiptRepository receipts, EnquiryAccess access) {
        this.invoices = invoices; this.bookings = bookings; this.receipts = receipts; this.access = access;
    }

    public record Row(Long bookingId, String invoiceNumber, String source, LocalDate invoiceDate, String branch,
                      String salesperson, String customerName, String customerPhone, String model, String variant,
                      String colour, String chassis, String batterySerials, BigDecimal taxableValue, BigDecimal gst,
                      BigDecimal subsidy, BigDecimal grandTotal, BigDecimal received, BigDecimal balance,
                      String paymentModes, String financier, String bookingStatus, LocalDate deliveredOn) {}
    public record Group(String name, int count, BigDecimal grandTotal, BigDecimal received, BigDecimal balance) {}
    public record Totals(int count, BigDecimal taxableValue, BigDecimal gst, BigDecimal subsidy, BigDecimal grandTotal,
                         BigDecimal received, BigDecimal balance) {}
    public record Options(List<String> branches, List<String> models, List<String> salespeople) {}
    public record Report(LocalDate from, LocalDate to, String branch, String model, String salesperson, Totals totals,
                         List<Group> byModel, List<Group> byBranch, List<Group> bySalesperson, List<Row> rows, Options options) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Report sales(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                        @RequestParam(required = false) String branch, @RequestParam(required = false) String model,
                        @RequestParam(required = false) String salesperson, Authentication auth) {
        AppUser actor = access.current(auth);
        if (!"ADMIN".equals(actor.role) && !"BRANCH_MANAGER".equals(actor.role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers and administrators can view sales reports");
        LocalDate today = BillingService.today();
        LocalDate start = parse(from, today.withDayOfMonth(1)), end = parse(to, today);
        if (end.isBefore(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end date is before the start date");
        if (start.plusYears(5).isBefore(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a range of 5 years or less");
        String scopeBranch = "ADMIN".equals(actor.role) ? blank(branch) : actor.branch;
        if (!"ADMIN".equals(actor.role) && blank(branch) != null && !blank(branch).equals(actor.branch))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only report on " + actor.branch);
        String modelFilter = blank(model), spFilter = blank(salesperson);

        List<Invoice> inScope = invoices.findByInvoiceDateBetweenOrderByInvoiceDateAscIdAsc(start, end).stream()
            .filter(i -> scopeBranch == null || scopeBranch.equals(i.branch)).toList();
        Map<Long, Booking> bookingById = bookings.findAllById(inScope.stream().map(i -> i.bookingId).toList()).stream()
            .collect(Collectors.toMap(b -> b.id, Function.identity()));
        Map<Long, List<PaymentReceipt>> paid = inScope.isEmpty() ? Map.of()
            : receipts.findByBookingIdIn(bookingById.keySet()).stream().filter(r -> "RECEIVED".equals(r.status))
                .collect(Collectors.groupingBy(r -> r.bookingId));

        Options options = new Options(
            inScope.stream().map(i -> i.branch).distinct().sorted().toList(),
            inScope.stream().map(i -> i.model).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
            inScope.stream().map(i -> bookingById.get(i.bookingId)).filter(Objects::nonNull).map(b -> b.salesperson)
                .distinct().sorted().toList());

        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        List<Row> rows = new ArrayList<>();
        for (Invoice i : inScope) {
            Booking b = bookingById.get(i.bookingId);
            if (b == null || "CANCELLED".equals(b.status)) continue;
            if (modelFilter != null && !i.model.equalsIgnoreCase(modelFilter)) continue;
            if (spFilter != null && !spFilter.equals(b.salesperson)) continue;
            List<PaymentReceipt> rs = paid.getOrDefault(b.id, List.of());
            BigDecimal received = rs.stream().map(r -> r.amount).reduce(zero, BigDecimal::add);
            String modes = rs.stream().map(r -> r.mode).distinct().sorted().collect(Collectors.joining(", "));
            String number = "PAPER".equals(i.source)
                ? (i.paperBookNumber == null ? "" : i.paperBookNumber + "/") + i.paperInvoiceNumber : i.invoiceNumber;
            rows.add(new Row(b.id, number, i.source, i.invoiceDate, i.branch, b.salesperson, i.customerName, i.customerPhone,
                i.model, i.variant, i.colour, i.chassis, i.batterySerials, i.taxableValue, i.cgst.add(i.sgst).add(i.igst),
                i.subsidy, i.grandTotal, received, i.grandTotal.subtract(received), modes, i.financier, b.status,
                b.deliveredAt == null ? null : b.deliveredAt.atZoneSameInstant(BillingService.INDIA).toLocalDate()));
        }
        Totals totals = new Totals(rows.size(), sum(rows, Row::taxableValue), sum(rows, Row::gst), sum(rows, Row::subsidy),
            sum(rows, Row::grandTotal), sum(rows, Row::received), sum(rows, Row::balance));
        return new Report(start, end, scopeBranch, modelFilter, spFilter, totals,
            group(rows, Row::model), group(rows, Row::branch), group(rows, Row::salesperson), rows, options);
    }

    private static List<Group> group(List<Row> rows, Function<Row, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList())).entrySet().stream()
            .map(e -> new Group(e.getKey(), e.getValue().size(), sum(e.getValue(), Row::grandTotal),
                sum(e.getValue(), Row::received), sum(e.getValue(), Row::balance)))
            .sorted(Comparator.comparing(Group::grandTotal).reversed()).toList();
    }

    private static BigDecimal sum(List<Row> rows, Function<Row, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static LocalDate parse(String v, LocalDate fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return LocalDate.parse(v.trim()); }
        catch (DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must be real dates"); }
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
