package in.purvanchal.electroride.booking;

import in.purvanchal.electroride.enquiry.Enquiry;
import in.purvanchal.electroride.enquiry.EnquiryRepository;
import in.purvanchal.electroride.inventory.InventoryPolicy;
import in.purvanchal.electroride.inventory.Vehicle;
import in.purvanchal.electroride.inventory.VehicleMovementRepository;
import in.purvanchal.electroride.inventory.VehicleRepository;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository bookings;
    @Mock BookingEventRepository events;
    @Mock EnquiryRepository enquiries;
    @Mock VehicleRepository vehicles;
    @Mock VehicleMovementRepository movements;
    @Mock EnquiryAccess enquiryAccess;
    @Mock InventoryPolicy inventoryPolicy;
    @Mock in.purvanchal.electroride.billing.InvoiceRepository invoices;
    @Mock in.purvanchal.electroride.billing.PaymentReceiptRepository receipts;
    @InjectMocks BookingService service;

    private AppUser actor;
    private Enquiry enquiry;
    private Vehicle vehicle;

    @BeforeEach
    void setup() {
        actor = new AppUser();
        actor.role = "ADMIN";
        actor.username = "admin";
        enquiry = new Enquiry();
        enquiry.id = 1L;
        enquiry.branch = "Varanasi";
        enquiry.stage = "Interested";
        vehicle = new Vehicle();
        vehicle.id = 2L;
        vehicle.chassis = "CH-100";
        vehicle.branch = "Varanasi";
        vehicle.status = "AVAILABLE";
    }

    @Test
    void refusesReservationWhenVehicleIsNotAvailable() {
        vehicle.status = "RESERVED";
        when(vehicles.lockByChassis("CH-100")).thenReturn(Optional.of(vehicle));
        when(enquiries.findById(1L)).thenReturn(Optional.of(enquiry));
        assertThatThrownBy(() -> service.create(1L, "ch-100", actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void refusesReservationAcrossBranches() {
        vehicle.branch = "Ghazipur";
        when(vehicles.lockByChassis("CH-100")).thenReturn(Optional.of(vehicle));
        when(enquiries.findById(1L)).thenReturn(Optional.of(enquiry));
        assertThatThrownBy(() -> service.create(1L, "CH-100", actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void createsPendingBookingAndReservesStock() {
        when(vehicles.lockByChassis("CH-100")).thenReturn(Optional.of(vehicle));
        when(enquiries.findById(1L)).thenReturn(Optional.of(enquiry));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.id = 15L;
            return b;
        });
        Booking result = service.create(1L, "ch-100", actor);
        assertThat(result.status).isEqualTo("PENDING");
        assertThat(result.priorEnquiryStage).isEqualTo("Interested");
        assertThat(vehicle.status).isEqualTo("RESERVED");
        verify(vehicles).save(vehicle);
        verify(events).save(any(BookingEvent.class));
        verify(movements).save(any());
    }

    @Test
    void confirmedBookingCannotBeConfirmedAgain() {
        Booking booking = new Booking();
        booking.id = 15L;
        booking.enquiryId = 1L;
        booking.branch = "Varanasi";
        booking.status = "CONFIRMED";
        when(bookings.lockById(15L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.confirm(15L, actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(bookings, never()).save(any());
    }

    @Test
    void cancelledBookingCannotBeCancelledAgain() {
        Booking booking = new Booking();
        booking.id = 15L;
        booking.enquiryId = 1L;
        booking.branch = "Varanasi";
        booking.status = "CANCELLED";
        when(bookings.lockById(15L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.cancel(15L, "Duplicated", actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(vehicles, never()).save(any());
    }
    @Test
    void directBookingReservesStockWithoutCreatingEnquiry() {
        actor.role = "SALESPERSON";
        actor.branch = "Varanasi";
        actor.displayName = "Aman";
        when(vehicles.lockByChassis("CH-100")).thenReturn(Optional.of(vehicle));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking saved = inv.getArgument(0);
            saved.id = 20L;
            return saved;
        });
        Booking booking = service.createDirect("ch-100", "Walk-in customer", "9876543210", actor);
        assertThat(booking.enquiryId).isNull();
        assertThat(booking.priorEnquiryStage).isNull();
        assertThat(booking.salesperson).isEqualTo("Aman");
        assertThat(booking.status).isEqualTo("PENDING");
        assertThat(vehicle.status).isEqualTo("RESERVED");
        verifyNoInteractions(enquiries);
        verify(vehicles).save(vehicle);
        verify(events).save(any(BookingEvent.class));
    }

    @Test
    void directBookingRejectsInvalidCustomerData() {
        assertThatThrownBy(() -> service.createDirect("CH-100", "", "123", actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(vehicles);
    }

    @Test
    void directBookingRejectsOtherBranchForSalesperson() {
        actor.role = "SALESPERSON";
        actor.branch = "Ghazipur";
        actor.displayName = "Sana";
        when(vehicles.lockByChassis("CH-100")).thenReturn(Optional.of(vehicle));
        assertThatThrownBy(() -> service.createDirect("CH-100", "Walk-in", "9876543210", actor))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(bookings, never()).saveAndFlush(any());
    }

}
