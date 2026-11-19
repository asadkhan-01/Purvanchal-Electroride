package in.purvanchal.electroride.quotation;

import in.purvanchal.electroride.security.EnquiryAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/quotation")
public class BookingQuotationController {
    private final BookingQuotationService service;
    private final EnquiryAccess access;

    public BookingQuotationController(BookingQuotationService service, EnquiryAccess access) {
        this.service = service; this.access = access;
    }

    public record QuotationRequest(@NotNull BigDecimal vehiclePrice,
                                   @NotNull BigDecimal discount,
                                   @NotNull BigDecimal accessories,
                                   @NotNull BigDecimal otherCharges) {}

    @GetMapping
    public BookingQuotation get(@PathVariable Long bookingId, Authentication authentication) {
        return service.get(bookingId, access.current(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingQuotation create(@PathVariable Long bookingId,
                                   @Valid @RequestBody QuotationRequest body,
                                   Authentication authentication) {
        return service.create(bookingId, body.vehiclePrice(), body.discount(),
            body.accessories(), body.otherCharges(), access.current(authentication));
    }

    @PostMapping("/approve")
    public BookingQuotation approve(@PathVariable Long bookingId, Authentication authentication) {
        return service.approve(bookingId, access.current(authentication));
    }
}
