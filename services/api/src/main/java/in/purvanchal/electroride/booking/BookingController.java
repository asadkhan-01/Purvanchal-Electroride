package in.purvanchal.electroride.booking;

import in.purvanchal.electroride.security.EnquiryAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService service;
    private final EnquiryAccess access;

    public BookingController(BookingService service, EnquiryAccess access) {
        this.service = service;
        this.access = access;
    }

    public record CreateBooking(@NotNull Long enquiryId, @NotBlank String chassis) {}
    public record CancelBooking(@NotBlank @Size(max = 500) String reason) {}

    @GetMapping
    public List<Booking> list(Authentication authentication) {
        return service.list(access.current(authentication));
    }

    @GetMapping("/{id}")
    public Booking get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, access.current(authentication));
    }

    @GetMapping("/{id}/events")
    public List<BookingEvent> events(@PathVariable Long id, Authentication authentication) {
        return service.history(id, access.current(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(@Valid @RequestBody CreateBooking input, Authentication authentication) {
        return service.create(input.enquiryId(), input.chassis(), access.current(authentication));
    }

    @PostMapping("/{id}/confirm")
    public Booking confirm(@PathVariable Long id, Authentication authentication) {
        return service.confirm(id, access.current(authentication));
    }

    @PostMapping("/{id}/cancel")
    public Booking cancel(@PathVariable Long id, @Valid @RequestBody CancelBooking input,
                          Authentication authentication) {
        return service.cancel(id, input.reason(), access.current(authentication));
    }
}
