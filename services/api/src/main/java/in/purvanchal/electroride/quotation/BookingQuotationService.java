package in.purvanchal.electroride.quotation;

import in.purvanchal.electroride.booking.Booking;
import in.purvanchal.electroride.booking.BookingEvent;
import in.purvanchal.electroride.booking.BookingEventRepository;
import in.purvanchal.electroride.booking.BookingRepository;
import in.purvanchal.electroride.booking.BookingService;
import in.purvanchal.electroride.inventory.InventoryPolicy;
import in.purvanchal.electroride.security.AppUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class BookingQuotationService {
    private final BookingQuotationRepository quotations;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final BookingEventRepository events;
    private final InventoryPolicy inventoryPolicy;

    public BookingQuotationService(BookingQuotationRepository quotations, BookingRepository bookings,
                                   BookingService bookingService, BookingEventRepository events,
                                   InventoryPolicy inventoryPolicy) {
        this.quotations = quotations;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.events = events;
        this.inventoryPolicy = inventoryPolicy;
    }

    @Transactional(readOnly = true)
    public BookingQuotation get(Long bookingId, AppUser actor) {
        bookingService.get(bookingId, actor);
        return quotations.findByBookingId(bookingId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
    }

    @Transactional
    public BookingQuotation create(Long bookingId, BigDecimal vehiclePrice, BigDecimal discount,
                                   BigDecimal accessories, BigDecimal otherCharges, AppUser actor) {
        // Bookings are locked to serialize quotation creation with approval/cancellation.
        Booking booking = bookings.lockById(bookingId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        bookingService.get(bookingId, actor);
        if (!"PENDING".equals(booking.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending bookings may be quoted");
        if (quotations.findByBookingId(bookingId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking already has a quotation");

        BigDecimal subtotal = QuotationMath.subtotal(vehiclePrice, discount, accessories, otherCharges);
        if (subtotal.precision() > 14)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subtotal exceeds supported amount");

        BookingQuotation quote = new BookingQuotation();
        quote.bookingId = bookingId;
        quote.vehiclePrice = vehiclePrice.setScale(2);
        quote.discount = discount.setScale(2);
        quote.accessories = accessories.setScale(2);
        quote.otherCharges = otherCharges.setScale(2);
        quote.subtotalExcludingTax = subtotal;
        quote.createdBy = actor.username;
        try {
            BookingQuotation saved = quotations.saveAndFlush(quote);
            record(bookingId, "QUOTATION_CREATED", actor.username, "Tax-exclusive estimate");
            return saved;
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking already has a quotation");
        }
    }

    @Transactional
    public BookingQuotation approve(Long bookingId, AppUser actor) {
        Booking booking = bookings.lockById(bookingId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        bookingService.get(bookingId, actor);
        inventoryPolicy.requireManager(actor, booking.branch);
        if (!"PENDING".equals(booking.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation cannot be approved after booking progresses");
        BookingQuotation quote = quotations.lockByBookingId(bookingId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        if (!"DRAFT".equals(quote.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation already approved");
        quote.status = "APPROVED";
        quote.approvedBy = actor.username;
        quote.approvedAt = OffsetDateTime.now();
        record(bookingId, "QUOTATION_APPROVED", actor.username, null);
        return quotations.save(quote);
    }

    private void record(Long bookingId, String event, String username, String details) {
        BookingEvent audit = new BookingEvent();
        audit.bookingId = bookingId;
        audit.event = event;
        audit.actorUsername = username;
        audit.details = details;
        events.save(audit);
    }
}
