package in.pragati.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import in.pragati.security.AuthUser;
import in.pragati.service.CandidateAnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Candidate decision support: fit analytics, top-5 recommendations, skill gap
 * and the grounded skill-gap assistant. All numbers come from the same
 * suitability/eligibility engine used by the explorer and the allocation.
 */
@RestController
@RequestMapping("/api/candidates")
@Tag(name = "Candidate analytics", description = "Fit analytics, recommendations, skill gap and grounded guidance (self-scoped).")
public class CandidateAnalyticsController {

    private final CandidateAnalyticsService analytics;

    public CandidateAnalyticsController(CandidateAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/me/analytics/opportunity/{id}")
    @Operation(summary = "Fit analysis for one opportunity: score /100, per-factor fit, weight, contribution and lost points.")
    public CandidateAnalyticsService.FitAnalysis fit(@AuthenticationPrincipal AuthUser me,
                                                     @PathVariable Long id) {
        return analytics.fit(me, id);
    }

    @GetMapping("/me/recommendations")
    @Operation(summary = "Top-5 recommended opportunities (active + eligible only). Recommendation, not allocation.")
    public CandidateAnalyticsService.Recommendations recommendations(@AuthenticationPrincipal AuthUser me) {
        return analytics.recommendations(me);
    }

    @GetMapping("/me/skill-gap/{id}")
    @Operation(summary = "Skill readiness for one opportunity: covered, mandatory gaps, preferred gaps.")
    public CandidateAnalyticsService.SkillGap skillGap(@AuthenticationPrincipal AuthUser me,
                                                       @PathVariable Long id) {
        return analytics.skillGap(me, id);
    }

    public record GuidanceInput(String question, String skill) { }

    @PostMapping("/me/skill-gap/{id}/guidance")
    @Operation(summary = "Grounded skill-gap assistant. Only structured, fact-grounded answers; never invented.")
    public CandidateAnalyticsService.Guidance guidance(@AuthenticationPrincipal AuthUser me,
                                                       @PathVariable Long id,
                                                       @RequestBody GuidanceInput input) {
        return analytics.guidance(me, id, input == null ? null : input.question(),
                input == null ? null : input.skill());
    }
}
