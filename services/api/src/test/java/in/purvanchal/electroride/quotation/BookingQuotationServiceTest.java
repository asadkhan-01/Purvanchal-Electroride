package in.purvanchal.electroride.quotation;

import in.purvanchal.electroride.booking.Booking;
import in.purvanchal.electroride.booking.BookingEventRepository;
import in.purvanchal.electroride.booking.BookingRepository;
import in.purvanchal.electroride.booking.BookingService;
import in.purvanchal.electroride.inventory.InventoryPolicy;
import in.purvanchal.electroride.security.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingQuotationServiceTest {
    @Mock BookingQuotationRepository quotations;
    @Mock BookingRepository bookings;
    @Mock BookingService bookingService;
    @Mock BookingEventRepository events;
    @Mock InventoryPolicy inventoryPolicy;
    @InjectMocks BookingQuotationService service;

    Booking booking;
    AppUser actor;

    @BeforeEach void setup() {
        booking = new Booking();
        booking.id = 9L; booking.status = "PENDING"; booking.branch = "Varanasi";
        actor = new AppUser();
        actor.username = "manager"; actor.role = "BRANCH_MANAGER"; actor.branch = "Varanasi";
    }

    @Test void cannotQuoteConfirmedBooking() {
        booking.status = "CONFIRMED";
        when(bookings.lockById(9L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.create(9L, new BigDecimal("100000"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(quotations, never()).saveAndFlush(any());
    }

    @Test void cannotOverwriteExistingQuotation() {
        when(bookings.lockById(9L)).thenReturn(Optional.of(booking));
        when(quotations.findByBookingId(9L)).thenReturn(Optional.of(new BookingQuotation()));
        assertThatThrownBy(() -> service.create(9L, new BigDecimal("100000"),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(quotations, never()).saveAndFlush(any());
    }

    @Test void cannotApproveQuotationTwice() {
        BookingQuotation quotation = new BookingQuotation();
        quotation.status = "APPROVED";
        when(bookings.lockById(9L)).thenReturn(Optional.of(booking));
        when(quotations.lockByBookingId(9L)).thenReturn(Optional.of(quotation));
        assertThatThrownBy(() -> service.approve(9L, actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(quotations, never()).save(any());
    }
}
