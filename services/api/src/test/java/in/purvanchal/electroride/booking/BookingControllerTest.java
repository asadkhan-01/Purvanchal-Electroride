package in.purvanchal.electroride.booking;

import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingService service;
    @MockitoBean EnquiryAccess access;

    @BeforeEach
    void setup() {
        AppUser manager = new AppUser();
        manager.role = "BRANCH_MANAGER";
        manager.branch = "Varanasi";
        when(access.current(nullable(org.springframework.security.core.Authentication.class)))
            .thenReturn(manager);
    }

    @Test
    void rejectsCreateWithoutChassis() throws Exception {
        mvc.perform(post("/api/v1/bookings")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enquiryId\":1}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).create(anyLong(), anyString(), any());
    }

    @Test
    void createsBookingAndReturns201() throws Exception {
        Booking booking = new Booking();
        booking.id = 7L; booking.enquiryId = 1L; booking.vehicleId = 2L;
        booking.status = "PENDING";
        when(service.create(eq(1L), eq("CHS-100"), any())).thenReturn(booking);
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
            .content("{\"enquiryId\":1,\"chassis\":\"CHS-100\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void confirmsBooking() throws Exception {
        Booking booking = new Booking();
        booking.id = 7L; booking.status = "CONFIRMED";
        when(service.confirm(eq(7L), any())).thenReturn(booking);
        mvc.perform(post("/api/v1/bookings/7/confirm"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectsBlankCancellationReason() throws Exception {
        mvc.perform(post("/api/v1/bookings/7/cancel")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\" \"}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).cancel(anyLong(), anyString(), any());
    }

    @Test
    void cancelsBookingWithReason() throws Exception {
        Booking booking = new Booking();
        booking.id = 7L; booking.status = "CANCELLED";
        when(service.cancel(eq(7L), eq("Customer withdrew"), any())).thenReturn(booking);
        mvc.perform(post("/api/v1/bookings/7/cancel")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Customer withdrew\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
