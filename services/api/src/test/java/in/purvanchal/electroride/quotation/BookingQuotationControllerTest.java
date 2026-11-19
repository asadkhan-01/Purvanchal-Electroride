package in.purvanchal.electroride.quotation;

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
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingQuotationController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingQuotationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingQuotationService service;
    @MockitoBean EnquiryAccess access;

    @BeforeEach void actor() {
        AppUser manager = new AppUser();
        manager.role = "BRANCH_MANAGER"; manager.branch = "Varanasi";
        when(access.current(nullable(org.springframework.security.core.Authentication.class)))
            .thenReturn(manager);
    }

    @Test void rejectsOmittedAmounts() throws Exception {
        mvc.perform(post("/api/v1/bookings/9/quotation")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehiclePrice\":100000}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).create(anyLong(), any(), any(), any(), any(), any());
    }

    @Test void createsTaxExclusiveQuote() throws Exception {
        BookingQuotation quote = new BookingQuotation();
        quote.id = 4L; quote.bookingId = 9L; quote.status = "DRAFT";
        quote.subtotalExcludingTax = new BigDecimal("97500.00");
        when(service.create(eq(9L), any(), any(), any(), any(), any())).thenReturn(quote);
        mvc.perform(post("/api/v1/bookings/9/quotation")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"vehiclePrice":100000,"discount":5000,"accessories":2000,"otherCharges":500}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.subtotalExcludingTax").value(97500.0))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test void approvesQuote() throws Exception {
        BookingQuotation quote = new BookingQuotation();
        quote.bookingId = 9L; quote.status = "APPROVED";
        when(service.approve(eq(9L), any())).thenReturn(quote);
        mvc.perform(post("/api/v1/bookings/9/quotation/approve"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
