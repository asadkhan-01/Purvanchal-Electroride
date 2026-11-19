package in.purvanchal.electroride.enquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/enquiries")
public class EnquiryController {
    private final EnquiryRepository repository;
    public EnquiryController(EnquiryRepository repository) { this.repository = repository; }

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

    @PatchMapping("/{id}/stage")
    public Enquiry changeStage(@PathVariable Long id, @Valid @RequestBody UpdateStage input) {
        if (!List.of("New", "Contacted", "Interested", "Booked", "Lost").contains(input.stage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stage");
        }
        Enquiry e = get(id);
        e.stage = input.stage();
        return repository.save(e);
    }
}
