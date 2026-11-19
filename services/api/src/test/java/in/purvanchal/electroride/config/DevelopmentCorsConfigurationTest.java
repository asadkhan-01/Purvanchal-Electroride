package in.purvanchal.electroride.config;

import in.purvanchal.electroride.enquiry.EnquiryActivityRepository;
import in.purvanchal.electroride.enquiry.EnquiryController;
import in.purvanchal.electroride.enquiry.EnquiryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnquiryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DevelopmentCorsConfiguration.class)
@TestPropertySource(properties = "app.cors.allowed-origin=https://example-8081.app.github.dev")
class DevelopmentCorsConfigurationTest {
    @Autowired MockMvc mvc;
    @MockitoBean EnquiryRepository enquiries;
    @MockitoBean EnquiryActivityRepository activities;
    @MockitoBean EnquiryAccess access;

    @Test
    void permitsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/enquiries")
            .header("Origin", "https://example-8081.app.github.dev")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://example-8081.app.github.dev"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/v1/enquiries")
            .header("Origin", "https://untrusted.example.com")
            .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}
