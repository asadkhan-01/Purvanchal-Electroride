package in.purvanchal.electroride.inventory;

import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.booking.BookingRepository;
import in.purvanchal.electroride.security.EnquiryAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final VehicleRepository vehicles;
    private final VehicleMovementRepository movements;
    private final EnquiryAccess users;
    private final InventoryPolicy policy;
    private final BookingRepository bookings;

    public InventoryController(VehicleRepository vehicles, VehicleMovementRepository movements,
                               EnquiryAccess users, InventoryPolicy policy, BookingRepository bookings) {
        this.vehicles = vehicles;
        this.movements = movements;
        this.users = users;
        this.policy = policy;
        this.bookings = bookings;
    }

    public record RegisterVehicle(
        @NotBlank @Size(max=100) String chassis,
        @NotBlank @Size(max=160) String model,
        @NotBlank @Size(max=120) String variant,
        @Size(max=80) String colour,
        @NotBlank @Size(max=100) String branch,
        @NotBlank @Size(max=120) String motorSerial,
        @Size(max=6) List<@NotBlank @Size(max=120) String> batterySerials,
        @Size(max=120) String batterySerial, // single-battery shorthand, kept for older clients
        @NotBlank @Size(max=120) String chargerSerial,
        @NotBlank @Size(max=120) String controllerSerial
    ) {}
    public record TransferRequest(@NotBlank String destination) {}

    @GetMapping
    public List<Vehicle> list(Authentication auth) {
        AppUser actor = users.current(auth);
        if ("ADMIN".equals(actor.role)) return vehicles.findAll();
        if (actor.branch == null) return List.of();
        return vehicles.findByBranchOrTransferDestination(actor.branch, actor.branch)
            .stream().filter(v -> policy.canView(actor, v)).toList();
    }

    @GetMapping("/{chassis}")
    public Vehicle get(@PathVariable String chassis, Authentication auth) {
        Vehicle vehicle = vehicles.findByChassis(normalize(chassis))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        policy.requireView(users.current(auth), vehicle);
        return vehicle;
    }

    @GetMapping("/{chassis}/movements")
    public List<VehicleMovement> history(@PathVariable String chassis, Authentication auth) {
        Vehicle vehicle = get(chassis, auth);
        return movements.findByVehicleIdOrderByIdDesc(vehicle.id);
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Vehicle register(@Valid @RequestBody RegisterVehicle body, Authentication auth) {
        AppUser actor = users.current(auth);
        policy.requireManager(actor, body.branch().trim());
        Vehicle vehicle = new Vehicle();
        vehicle.chassis = normalize(body.chassis());
        vehicle.model = body.model().trim();
        vehicle.variant = body.variant().trim();
        vehicle.colour = body.colour() == null ? null : body.colour().trim();
        vehicle.branch = body.branch().trim();
        vehicle.motorSerial = normalize(body.motorSerial());
        vehicle.batterySerials = batteries(body);
        vehicle.chargerSerial = normalize(body.chargerSerial());
        vehicle.controllerSerial = normalize(body.controllerSerial());
        try {
            Vehicle saved = vehicles.saveAndFlush(vehicle);
            log(saved, "RECEIVED", null, saved.branch, actor.username);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chassis or component serial already registered");
        }
    }

    private static List<String> batteries(RegisterVehicle body) {
        List<String> raw = body.batterySerials() != null && !body.batterySerials().isEmpty() ? body.batterySerials()
            : body.batterySerial() != null && !body.batterySerial().isBlank() ? List.of(body.batterySerial()) : List.of();
        if (raw.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one battery serial is required");
        List<String> serials = raw.stream().map(InventoryController::normalize).toList();
        if (serials.stream().distinct().count() != serials.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Battery serials must be different");
        return new java.util.ArrayList<>(serials);
    }

    @PostMapping("/{chassis}/reserve")
    @Transactional
    public Vehicle reserve(@PathVariable String chassis, Authentication auth) {
        Vehicle vehicle = locked(chassis);
        AppUser actor = users.current(auth);
        policy.requireView(actor, vehicle);
        policy.requireManager(actor, vehicle.branch);
        requireStatus(vehicle, "AVAILABLE");
        vehicle.status = "RESERVED";
        log(vehicle, "RESERVED", vehicle.branch, vehicle.branch, actor.username);
        return vehicles.save(vehicle);
    }

    @PostMapping("/{chassis}/release")
    @Transactional
    public Vehicle release(@PathVariable String chassis, Authentication auth) {
        Vehicle vehicle = locked(chassis);
        AppUser actor = users.current(auth);
        policy.requireView(actor, vehicle);
        policy.requireManager(actor, vehicle.branch);
        requireStatus(vehicle, "RESERVED");
        if (bookings.existsByVehicleIdAndStatusIn(vehicle.id, List.of("PENDING", "CONFIRMED")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancel the booking before releasing this vehicle");
        vehicle.status = "AVAILABLE";
        log(vehicle, "RELEASED", vehicle.branch, vehicle.branch, actor.username);
        return vehicles.save(vehicle);
    }

    @PostMapping("/{chassis}/transfer")
    @Transactional
    public Vehicle transfer(@PathVariable String chassis, @Valid @RequestBody TransferRequest body, Authentication auth) {
        Vehicle vehicle = locked(chassis);
        AppUser actor = users.current(auth);
        policy.requireView(actor, vehicle);
        policy.requireManager(actor, vehicle.branch);
        requireStatus(vehicle, "AVAILABLE");
        String destination = body.destination().trim();
        if (destination.equals(vehicle.branch)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination must differ");
        vehicle.status = "IN_TRANSIT";
        vehicle.transferDestination = destination;
        log(vehicle, "TRANSFER_STARTED", vehicle.branch, destination, actor.username);
        return vehicles.save(vehicle);
    }

    @PostMapping("/{chassis}/receive")
    @Transactional
    public Vehicle receive(@PathVariable String chassis, Authentication auth) {
        Vehicle vehicle = locked(chassis);
        AppUser actor = users.current(auth);
        policy.requireView(actor, vehicle);
        requireStatus(vehicle, "IN_TRANSIT");
        policy.requireReceiver(actor, vehicle);
        String from = vehicle.branch;
        String to = vehicle.transferDestination;
        vehicle.branch = to;
        vehicle.transferDestination = null;
        vehicle.status = "AVAILABLE";
        log(vehicle, "TRANSFER_RECEIVED", from, to, actor.username);
        return vehicles.save(vehicle);
    }

    private Vehicle locked(String chassis) {
        return vehicles.lockByChassis(normalize(chassis))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private static void requireStatus(Vehicle vehicle, String required) {
        if (!required.equals(vehicle.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle must be " + required);
    }

    private void log(Vehicle vehicle, String event, String from, String to, String actor) {
        VehicleMovement movement = new VehicleMovement();
        movement.vehicleId = vehicle.id;
        movement.event = event;
        movement.fromBranch = from;
        movement.toBranch = to;
        movement.actorUsername = actor;
        movements.save(movement);
    }

    private static String normalize(String serial) {
        return serial.trim().toUpperCase(Locale.ROOT);
    }
}
