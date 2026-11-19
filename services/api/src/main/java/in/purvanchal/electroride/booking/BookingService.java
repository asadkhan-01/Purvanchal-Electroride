package in.purvanchal.electroride.booking;

import in.purvanchal.electroride.enquiry.Enquiry;
import in.purvanchal.electroride.enquiry.EnquiryRepository;
import in.purvanchal.electroride.inventory.InventoryPolicy;
import in.purvanchal.electroride.inventory.Vehicle;
import in.purvanchal.electroride.inventory.VehicleMovement;
import in.purvanchal.electroride.inventory.VehicleMovementRepository;
import in.purvanchal.electroride.inventory.VehicleRepository;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;

@Service
public class BookingService {
    private static final List<String> LIVE = List.of("PENDING", "CONFIRMED");
    private final BookingRepository bookings;
    private final BookingEventRepository events;
    private final EnquiryRepository enquiries;
    private final VehicleRepository vehicles;
    private final VehicleMovementRepository movements;
    private final EnquiryAccess enquiryAccess;
    private final InventoryPolicy inventoryPolicy;

    public BookingService(BookingRepository bookings, BookingEventRepository events,
                          EnquiryRepository enquiries, VehicleRepository vehicles,
                          VehicleMovementRepository movements, EnquiryAccess enquiryAccess,
                          InventoryPolicy inventoryPolicy) {
        this.bookings = bookings; this.events = events;
        this.enquiries = enquiries; this.vehicles = vehicles;
        this.movements = movements; this.enquiryAccess = enquiryAccess;
        this.inventoryPolicy = inventoryPolicy;
    }

    public List<Booking> list(AppUser actor) {
        if ("ADMIN".equals(actor.role)) return bookings.findAll();
        if (actor.branch == null) return List.of();
        // Use enquiry ownership, not booking creator, for salesperson visibility.
        return bookings.findByBranch(actor.branch).stream().filter(booking ->
            enquiries.findById(booking.enquiryId)
                .map(enquiry -> enquiryAccess.canSee(actor, enquiry)).orElse(false)).toList();
    }

    public Booking get(Long id, AppUser actor) {
        Booking booking = bookings.findById(id).orElseThrow(() -> missing());
        requireAccess(actor, booking);
        return booking;
    }

    public List<BookingEvent> history(Long id, AppUser actor) {
        get(id, actor);
        return events.findByBookingIdOrderByIdDesc(id);
    }

    @Transactional
    public Booking create(Long enquiryId, String chassis, AppUser actor) {
        String normalized = chassis.trim().toUpperCase(Locale.ROOT);
        Vehicle vehicle = vehicles.lockByChassis(normalized).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        Enquiry enquiry = enquiries.findById(enquiryId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found"));
        enquiryAccess.requireAccess(actor, enquiry);
        inventoryPolicy.requireView(actor, vehicle);
        if (!vehicle.branch.equals(enquiry.branch))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle and enquiry must be in the same branch");
        if (!"AVAILABLE".equals(vehicle.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle is not available for booking");
        if ("Booked".equals(enquiry.stage) || "Lost".equals(enquiry.stage))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enquiry cannot be booked from its current stage");
        if (bookings.existsByEnquiryIdAndStatusIn(enquiryId, LIVE)
                || bookings.existsByVehicleIdAndStatusIn(vehicle.id, LIVE))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enquiry or vehicle already has a live booking");
        Booking booking = new Booking();
        booking.enquiryId = enquiry.id;
        booking.vehicleId = vehicle.id;
        booking.branch = vehicle.branch;
        booking.status = "PENDING";
        booking.priorEnquiryStage = enquiry.stage;
        booking.createdBy = actor.username;
        Booking saved = bookings.saveAndFlush(booking);
        vehicle.status = "RESERVED";
        vehicles.save(vehicle);
        logMovement(vehicle, "RESERVED_FOR_BOOKING", actor.username);
        logEvent(saved, "CREATED", actor.username, normalized);
        return saved;
    }

    @Transactional
    public Booking confirm(Long id, AppUser actor) {
        Booking booking = bookings.lockById(id).orElseThrow(() -> missing());
        requireAccess(actor, booking);
        inventoryPolicy.requireManager(actor, booking.branch);
        if (!"PENDING".equals(booking.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending bookings can be confirmed");
        Vehicle vehicle = vehicles.findById(booking.vehicleId).orElseThrow(() -> missing());
        if (!"RESERVED".equals(vehicle.status) || !booking.branch.equals(vehicle.branch))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved vehicle is no longer available");
        booking.status = "CONFIRMED";
        booking.confirmedBy = actor.username;
        Enquiry enquiry = enquiries.findById(booking.enquiryId).orElseThrow(() -> missing());
        enquiry.stage = "Booked";
        enquiries.save(enquiry);
        logEvent(booking, "CONFIRMED", actor.username, null);
        return bookings.save(booking);
    }

    @Transactional
    public Booking cancel(Long id, String reason, AppUser actor) {
        Booking booking = bookings.lockById(id).orElseThrow(() -> missing());
        requireAccess(actor, booking);
        inventoryPolicy.requireManager(actor, booking.branch);
        if (!LIVE.contains(booking.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is already cancelled");
        Vehicle vehicle = vehicles.lockByChassis(
            vehicles.findById(booking.vehicleId).orElseThrow(() -> missing()).chassis
        ).orElseThrow(() -> missing());
        if (!"RESERVED".equals(vehicle.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle is no longer reserved");
        booking.status = "CANCELLED";
        booking.cancelledBy = actor.username;
        booking.cancellationReason = reason.trim();
        vehicle.status = "AVAILABLE";
        vehicles.save(vehicle);
        logMovement(vehicle, "BOOKING_CANCELLED_RELEASED", actor.username);
        Enquiry enquiry = enquiries.findById(booking.enquiryId).orElseThrow(() -> missing());
        if ("Booked".equals(enquiry.stage)) {
            enquiry.stage = booking.priorEnquiryStage;
            enquiries.save(enquiry);
        }
        logEvent(booking, "CANCELLED", actor.username, booking.cancellationReason);
        return bookings.save(booking);
    }

    private void requireAccess(AppUser actor, Booking booking) {
        Enquiry enquiry = enquiries.findById(booking.enquiryId).orElseThrow(() -> missing());
        enquiryAccess.requireAccess(actor, enquiry);
    }

    private void logEvent(Booking booking, String event, String actor, String detail) {
        BookingEvent entry = new BookingEvent();
        entry.bookingId = booking.id; entry.event = event;
        entry.actorUsername = actor; entry.details = detail;
        events.save(entry);
    }

    private void logMovement(Vehicle vehicle, String event, String actor) {
        VehicleMovement movement = new VehicleMovement();
        movement.vehicleId = vehicle.id; movement.event = event;
        movement.fromBranch = vehicle.branch; movement.toBranch = vehicle.branch;
        movement.actorUsername = actor; movements.save(movement);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
    }
}
