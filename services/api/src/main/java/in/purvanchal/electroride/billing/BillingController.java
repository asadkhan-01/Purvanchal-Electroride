package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.booking.Booking;
import in.purvanchal.electroride.security.EnquiryAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
public class BillingController {
    private final BillingService service;
    private final EnquiryAccess access;

    public BillingController(BillingService service, EnquiryAccess access) {
        this.service = service; this.access = access;
    }

    public record PaymentBody(@NotNull BigDecimal amount, @NotBlank String mode,
                              @Size(max = 100) String reference, @Size(max = 120) String financeCompany,
                              LocalDate receivedOn, @Size(max = 300) String notes) {}
    public record VoidBody(@NotBlank @Size(max = 300) String reason) {}
    public record InvoiceBody(@NotBlank @Size(max = 300) String customerAddress, @Size(max = 15) String customerGstin,
                              BigDecimal gstRate, boolean interState) {}

    @GetMapping("/account")
    public BillingService.Account account(@PathVariable Long bookingId, Authentication auth) {
        return service.account(bookingId, access.current(auth));
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentReceipt pay(@PathVariable Long bookingId, @Valid @RequestBody PaymentBody b, Authentication auth) {
        return service.recordPayment(bookingId, new BillingService.PaymentRequest(
            b.amount(), b.mode(), b.reference(), b.financeCompany(), b.receivedOn(), b.notes()), access.current(auth));
    }

    @PostMapping("/payments/{receiptId}/void")
    public PaymentReceipt voidPayment(@PathVariable Long bookingId, @PathVariable Long receiptId,
                                      @Valid @RequestBody VoidBody b, Authentication auth) {
        return service.voidPayment(bookingId, receiptId, b.reason(), access.current(auth));
    }

    @GetMapping("/invoice")
    public Invoice invoice(@PathVariable Long bookingId, Authentication auth) {
        return service.invoice(bookingId, access.current(auth));
    }

    @PostMapping("/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    public Invoice issue(@PathVariable Long bookingId, @Valid @RequestBody InvoiceBody b, Authentication auth) {
        return service.issueInvoice(bookingId, new BillingService.InvoiceRequest(
            b.customerAddress(), b.customerGstin(), b.gstRate(), b.interState()), access.current(auth));
    }

    @PostMapping("/deliver")
    public Booking deliver(@PathVariable Long bookingId, Authentication auth) {
        return service.deliver(bookingId, access.current(auth));
    }
}
