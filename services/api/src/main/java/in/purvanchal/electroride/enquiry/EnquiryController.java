package in.purvanchal.electroride.enquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import in.purvanchal.electroride.security.AppUser;
import in.purvanchal.electroride.security.EnquiryAccess;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/enquiries")
public class EnquiryController {
    private final EnquiryRepository repository;
    private final EnquiryActivityRepository activities;
    private final EnquiryAccess access;

    public EnquiryController(EnquiryRepository repository, EnquiryActivityRepository activities, EnquiryAccess access) {
        this.repository = repository;
        this.activities = activities;
        this.access = access;
    }

    public record CreateEnquiry(
        @NotBlank String customerName,
        @NotBlank @Pattern(regexp = "[0-9]{10}") String phone,
        @NotBlank String branch,
        @NotBlank String salesperson,
        @NotBlank String source,
        @NotBlank String interestedModel,
        LocalDate followUpDate,
        String notes
    ) {}

    public record UpdateStage(@NotBlank String stage) {}
    public record FollowUpRequest(LocalDate followUpDate) {}
    public record CreateActivity(@NotBlank @Size(max = 2000) String message) {}

    @GetMapping
    public List<Enquiry> list(Authentication authentication) {
        AppUser user = access.current(authentication);
        return repository.findAll().stream().filter(e -> access.canSee(user, e)).toList();
    }

    @GetMapping("/{id}")
    public Enquiry get(@PathVariable Long id, Authentication authentication) {
        Enquiry enquiry = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found"));
        access.requireAccess(access.current(authentication), enquiry);
        return enquiry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Enquiry create(@Valid @RequestBody CreateEnquiry input, Authentication authentication) {
        Enquiry e = new Enquiry();
        e.customerName = input.customerName().trim();
        e.phone = input.phone();
        e.branch = input.branch().trim();
        e.salesperson = input.salesperson().trim();
        e.source = input.source().trim();
        e.interestedModel = input.interestedModel().trim();
        e.followUpDate = input.followUpDate();
        e.notes = input.notes();
        access.validateNew(access.current(authentication), e);
        return repository.save(e);
    }

    @GetMapping("/{id}/activities")
    public List<EnquiryActivity> listActivities(@PathVariable Long id, Authentication authentication) {
        get(id, authentication);
        return activities.findByEnquiryIdOrderByCreatedAtDescIdDesc(id);
    }

    @PostMapping("/{id}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public EnquiryActivity addActivity(@PathVariable Long id, @Valid @RequestBody CreateActivity input, Authentication authentication) {
        get(id, authentication);
        EnquiryActivity activity = new EnquiryActivity();
        activity.enquiryId = id;
        activity.message = input.message().trim();
        return activities.save(activity);
    }

    @Transactional
    @PatchMapping("/{id}/follow-up")
    public Enquiry scheduleFollowUp(@PathVariable Long id, @Valid @RequestBody FollowUpRequest input, Authentication authentication) {
        if (input.followUpDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Follow-up date is required");
        }
        Enquiry enquiry = get(id, authentication);
        enquiry.followUpDate = input.followUpDate();
        Enquiry saved = repository.save(enquiry);
        EnquiryActivity activity = new EnquiryActivity();
        activity.enquiryId = id;
        activity.message = "Follow-up scheduled for " + input.followUpDate();
        activities.save(activity);
        return saved;
    }

    @Transactional
    @PatchMapping("/{id}/stage")
    public Enquiry changeStage(@PathVariable Long id, @Valid @RequestBody UpdateStage input, Authentication authentication) {
        if (!List.of("New", "Contacted", "Interested", "Booked", "Lost").contains(input.stage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stage");
        }
        Enquiry e = get(id, authentication);
        e.stage = input.stage();
        Enquiry saved = repository.save(e);
        EnquiryActivity event = new EnquiryActivity();
        event.enquiryId = id;
        event.message = "Stage changed to " + input.stage();
        activities.save(event);
        return saved;
    }
}
