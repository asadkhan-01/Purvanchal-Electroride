package in.purvanchal.electroride.enquiry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.EnquiryAccess;
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
@AutoConfigureMockMvc(addFilters = false)
class EnquiryControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean EnquiryRepository repository;
    @MockitoBean EnquiryActivityRepository activities;
    @MockitoBean EnquiryAccess access;

    @BeforeEach
    void setup() {
        reset(repository, activities, access);
        AppUser admin = new AppUser();
        admin.role = "ADMIN";
        when(access.current(any())).thenReturn(admin);
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
    @Test
    void listActivitiesRequiresExistingEnquiry() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/enquiries/999/activities"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(activities);
    }

    @Test
    void listActivitiesReturnsHistory() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(sample(7)));
        EnquiryActivity event = new EnquiryActivity();
        event.id = 3L; event.enquiryId = 7L; event.message = "Customer called";
        when(activities.findByEnquiryIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(event));
        mvc.perform(get("/api/v1/enquiries/7/activities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].message").value("Customer called"));
    }

    @Test
    void createActivityPersistsTrimmedMessage() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(sample(7)));
        when(activities.save(any(EnquiryActivity.class))).thenAnswer(invocation -> {
            EnquiryActivity event = invocation.getArgument(0);
            assertThat(event.message).isEqualTo("Customer called");
            assertThat(event.enquiryId).isEqualTo(7L);
            event.id = 8L;
            return event;
        });
        mvc.perform(post("/api/v1/enquiries/7/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  Customer called  \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void createActivityRejectsBlankMessage() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(sample(7)));
        mvc.perform(post("/api/v1/enquiries/7/activities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
            .andExpect(status().isBadRequest());
        verify(activities, never()).save(any());
    }

    @Test
    void followUpIsSavedAndLogged() throws Exception {
        Enquiry e = sample(7);
        when(repository.findById(7L)).thenReturn(Optional.of(e));
        when(repository.save(any(Enquiry.class))).thenAnswer(inv -> inv.getArgument(0));
        when(activities.save(any(EnquiryActivity.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(patch("/api/v1/enquiries/7/follow-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"followUpDate\":\"2026-12-04\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.followUpDate").value("2026-12-04"));
        verify(repository).save(eq(e));
        verify(activities).save(any(EnquiryActivity.class));
    }

    @Test
    void followUpRequiresDate() throws Exception {
        when(repository.findById(7L)).thenReturn(Optional.of(sample(7)));
        mvc.perform(patch("/api/v1/enquiries/7/follow-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

}
