package in.purvanchal.electroride.enquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

    public EnquiryController(EnquiryRepository repository, EnquiryActivityRepository activities) {
        this.repository = repository;
        this.activities = activities;
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
    public List<Enquiry> list() { return repository.findAll(); }

    @GetMapping("/{id}")
    public Enquiry get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Enquiry create(@Valid @RequestBody CreateEnquiry input) {
        Enquiry e = new Enquiry();
        e.customerName = input.customerName().trim();
        e.phone = input.phone();
        e.branch = input.branch().trim();
        e.salesperson = input.salesperson().trim();
        e.source = input.source().trim();
        e.interestedModel = input.interestedModel().trim();
        e.followUpDate = input.followUpDate();
        e.notes = input.notes();
        return repository.save(e);
    }

    @GetMapping("/{id}/activities")
    public List<EnquiryActivity> listActivities(@PathVariable Long id) {
        get(id);
        return activities.findByEnquiryIdOrderByCreatedAtDescIdDesc(id);
    }

    @PostMapping("/{id}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public EnquiryActivity addActivity(@PathVariable Long id, @Valid @RequestBody CreateActivity input) {
        get(id);
        EnquiryActivity activity = new EnquiryActivity();
        activity.enquiryId = id;
        activity.message = input.message().trim();
        return activities.save(activity);
    }

    @Transactional
    @PatchMapping("/{id}/follow-up")
    public Enquiry scheduleFollowUp(@PathVariable Long id, @Valid @RequestBody FollowUpRequest input) {
        if (input.followUpDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Follow-up date is required");
        }
        Enquiry enquiry = get(id);
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
    public Enquiry changeStage(@PathVariable Long id, @Valid @RequestBody UpdateStage input) {
        if (!List.of("New", "Contacted", "Interested", "Booked", "Lost").contains(input.stage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stage");
        }
        Enquiry e = get(id);
        e.stage = input.stage();
        Enquiry saved = repository.save(e);
        EnquiryActivity event = new EnquiryActivity();
        event.enquiryId = id;
        event.message = "Stage changed to " + input.stage();
        activities.save(event);
        return saved;
    }
}
