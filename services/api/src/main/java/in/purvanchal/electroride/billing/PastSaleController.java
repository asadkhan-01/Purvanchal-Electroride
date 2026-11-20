package in.purvanchal.electroride.billing;

import in.purvanchal.electroride.security.EnquiryAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/past-sales")
public class PastSaleController {
    private final PastSaleService service;
    private final EnquiryAccess access;

    public PastSaleController(PastSaleService service, EnquiryAccess access) { this.service = service; this.access = access; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PastSaleService.Result record(@RequestBody PastSaleService.Request request, Authentication auth) {
        return service.record(request, access.current(auth));
    }
}
