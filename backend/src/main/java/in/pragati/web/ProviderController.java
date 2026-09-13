package in.pragati.web;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import in.pragati.domain.enums.OppStatus;
import in.pragati.security.AuthUser;
import in.pragati.service.ProviderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/providers")
@Tag(name = "Providers", description = "Organisation, owned opportunities, capacity & demand, allocation impact.")
public class ProviderController {

    public record StatusInput(String status) { }

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping("/me")
    @Operation(summary = "My organisation and headline numbers.")
    public ProviderService.OrgView me(@AuthenticationPrincipal AuthUser me) {
        return providerService.org(me);
    }

    @GetMapping("/me/opportunities")
    @Operation(summary = "My opportunities with live demand and allocation counts.")
    public Page<ProviderService.OppCard> opportunities(@AuthenticationPrincipal AuthUser me,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return providerService.myOpportunities(me, page, size);
    }

    @GetMapping("/me/opportunities/{id}")
    @Operation(summary = "One of my opportunities (ownership enforced).")
    public ProviderService.OppCard opportunity(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return providerService.myOpportunity(me, id);
    }

    @PostMapping("/me/opportunities")
    @Operation(summary = "Create an opportunity.")
    public ProviderService.OppCard create(@AuthenticationPrincipal AuthUser me,
                                          @Valid @RequestBody ProviderService.OppInput input) {
        return providerService.createOpp(me, input);
    }

    @PutMapping("/me/opportunities/{id}")
    @Operation(summary = "Update one of my opportunities.")
    public ProviderService.OppCard update(@AuthenticationPrincipal AuthUser me,
                                          @PathVariable Long id,
                                          @Valid @RequestBody ProviderService.OppInput input) {
        return providerService.updateOpp(me, id, input);
    }

    @PatchMapping("/me/opportunities/{id}/status")
    @Operation(summary = "Open, pause or close one of my opportunities.")
    public ProviderService.OppCard status(@AuthenticationPrincipal AuthUser me,
                                          @PathVariable Long id,
                                          @RequestBody StatusInput input) {
        return providerService.changeStatus(me, id, input.status());
    }

    @GetMapping("/me/capacity")
    @Operation(summary = "Capacity vs demand per opportunity.")
    public List<ProviderService.CapacityEntry> capacity(@AuthenticationPrincipal AuthUser me) {
        return providerService.capacity(me);
    }

    @GetMapping("/me/impact")
    @Operation(summary = "Allocation impact of my opportunities in the latest run.")
    public List<ProviderService.ImpactEntry> impact(@AuthenticationPrincipal AuthUser me) {
        return providerService.impact(me);
    }
}
