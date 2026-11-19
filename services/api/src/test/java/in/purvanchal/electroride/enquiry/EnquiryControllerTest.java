package in.purvanchal.electroride.enquiry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnquiryController.class)
class EnquiryControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean EnquiryRepository repository;

    @BeforeEach
    void setup() {
        reset(repository);
    }

    private Enquiry sample(long id) {
        Enquiry e = new Enquiry();
        e.id = id;
        e.customerName = "Test Customer";
        e.phone = "9876500000";
        e.branch = "Varanasi";
        e.salesperson = "Aman";
        e.source = "Walk-in";
        e.interestedModel = "Electric Scooter";
        e.stage = "New";
        return e;
    }

    private String createJson(String phone) {
        return """
            {"customerName":"  Test Customer  ","phone":"%s","branch":"Varanasi",
             "salesperson":"Aman","source":"Walk-in","interestedModel":"Electric Scooter"}
            """.formatted(phone);
    }

    @Test
    void listReturnsEnquiries() throws Exception {
        when(repository.findAll()).thenReturn(List.of(sample(7)));
        mvc.perform(get("/api/v1/enquiries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].customerName").value("Test Customer"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/enquiries/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201AndTrimsCustomerName() throws Exception {
        when(repository.save(any(Enquiry.class))).thenAnswer(invocation -> {
            Enquiry e = invocation.getArgument(0);
            assertThat(e.customerName).isEqualTo("Test Customer");
            e.id = 12L;
            return e;
        });
        mvc.perform(post("/api/v1/enquiries").contentType(MediaType.APPLICATION_JSON)
                .content(createJson("9876500000")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.stage").value("New"));
        verify(repository).save(any(Enquiry.class));
    }

    @Test
    void createRejectsInvalidPhone() throws Exception {
        mvc.perform(post("/api/v1/enquiries").contentType(MediaType.APPLICATION_JSON)
                .content(createJson("123")))
            .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsBlankRequiredField() throws Exception {
        String body = createJson("9876500000").replace("Electric Scooter", "");
        mvc.perform(post("/api/v1/enquiries").contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void changeStageUpdatesExistingEnquiry() throws Exception {
        Enquiry e = sample(7);
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        when(repository.save(any(Enquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(patch("/api/v1/enquiries/7/stage")
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"Interested\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stage").value("Interested"));
        verify(repository).save(eq(e));
    }

    @Test
    void changeStageRejectsUnknownValue() throws Exception {
        mvc.perform(patch("/api/v1/enquiries/7/stage")
                .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"Invalid\"}"))
            .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }
}
