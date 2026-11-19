package in.purvanchal.electroride.inventory;

import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.booking.BookingRepository;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VehicleRepository vehicles;
    @MockitoBean VehicleMovementRepository movements;
    @MockitoBean EnquiryAccess access;
    @MockitoBean InventoryPolicy policy;
    @MockitoBean BookingRepository bookings;

    @Test void registrationRejectsMissingSerialNumbers() throws Exception {
        mvc.perform(post("/api/v1/inventory")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"chassis":"CH-1","model":"EV","variant":"Standard","branch":"Varanasi"}
                """))
            .andExpect(status().isBadRequest());
        verify(vehicles, never()).saveAndFlush(any());
    }

    @Test void stockNotFoundReturns404() throws Exception {
        when(vehicles.findByChassis("UNKNOWN")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/inventory/unknown")).andExpect(status().isNotFound());
    }

    @Test void cannotTransferToSameBranch() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.chassis = "CH-1";
        vehicle.branch = "Varanasi";
        vehicle.status = "AVAILABLE";
        when(vehicles.lockByChassis("CH-1")).thenReturn(Optional.of(vehicle));
        AppUser user = new AppUser();
        user.username = "manager";
        when(access.current(any())).thenReturn(user);
        mvc.perform(post("/api/v1/inventory/CH-1/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"destination\":\"Varanasi\"}"))
            .andExpect(status().isBadRequest());
        verify(vehicles, never()).save(any());
        verifyNoInteractions(movements);
    }

    @Test void cannotReserveInTransitVehicle() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.chassis = "CH-1";
        vehicle.branch = "Varanasi";
        vehicle.status = "IN_TRANSIT";
        when(vehicles.lockByChassis("CH-1")).thenReturn(Optional.of(vehicle));
        when(access.current(any())).thenReturn(new AppUser());
        mvc.perform(post("/api/v1/inventory/CH-1/reserve")).andExpect(status().isConflict());
        verify(vehicles, never()).save(any());
    }
    @Test void bookingAllocatedVehicleCannotBeReleasedDirectly() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.id = 41L;
        vehicle.chassis = "CH-BOOKED";
        vehicle.branch = "Varanasi";
        vehicle.status = "RESERVED";
        when(vehicles.lockByChassis("CH-BOOKED")).thenReturn(Optional.of(vehicle));
        AppUser manager = new AppUser();
        manager.username = "manager";
        when(access.current(any())).thenReturn(manager);
        when(bookings.existsByVehicleIdAndStatusIn(eq(41L), anyList())).thenReturn(true);
        mvc.perform(post("/api/v1/inventory/CH-BOOKED/release"))
            .andExpect(status().isConflict());
        verify(vehicles, never()).save(any());
        verifyNoInteractions(movements);
    }

}
