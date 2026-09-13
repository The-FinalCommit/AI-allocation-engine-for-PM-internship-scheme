package in.pragati.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import in.pragati.domain.enums.Sector;
import in.pragati.security.AuthUser;
import in.pragati.service.CandidateService;
import in.pragati.service.ReadinessService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/candidates")
@Tag(name = "Candidates", description = "Profile, skills, preferences, resume, readiness and allocation view (self-scoped).")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/me/profile")
    @Operation(summary = "My profile with skills and interests.")
    public Map<String, Object> profile(@AuthenticationPrincipal AuthUser me) {
        in.pragati.domain.CandidateProfile p = candidateService.requireSelfProfile(me);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", p.id);
        out.put("fullName", p.fullName == null ? "" : p.fullName);
        out.put("phone", p.phone == null ? "" : p.phone);
        out.put("dob", p.dob == null ? null : p.dob.toString());
        out.put("qualification", p.qualification == null ? null : p.qualification.name());
        out.put("candidateStatus", p.candidateStatus == null ? null : p.candidateStatus.name());
        out.put("state", p.state == null ? "" : p.state);
        out.put("district", p.district == null ? "" : p.district);
        out.put("locationType", p.locationType == null ? null : p.locationType.name());
        out.put("bio", p.bio == null ? "" : p.bio);
        out.put("experienceYears", p.experienceYears == null ? 0 : p.experienceYears);
        out.put("skills", candidateService.mySkills(me));
        out.put("interests", candidateService.myInterests(me));
        return out;
    }

    @PutMapping("/me/profile")
    @Operation(summary = "Update my profile (validated, human-readable errors).")
    public Map<String, Object> updateProfile(@AuthenticationPrincipal AuthUser me,
                                             @RequestBody CandidateService.ProfileInput input) {
        candidateService.updateProfile(me, input);
        return profile(me);
    }

    @PutMapping("/me/skills")
    @Operation(summary = "Replace my skills (labels are normalized to the validated taxonomy).")
    public List<CandidateService.SkillItem> setSkills(@AuthenticationPrincipal AuthUser me,
                                                      @RequestBody List<String> skills) {
        return candidateService.setSkills(me, skills);
    }

    @PutMapping("/me/interests")
    @Operation(summary = "Replace my sector interests.")
    public List<Sector> setInterests(@AuthenticationPrincipal AuthUser me,
                                     @RequestBody List<Sector> sectors) {
        return candidateService.setInterests(me, sectors);
    }

    @GetMapping("/me/preferences")
    @Operation(summary = "My ranked opportunity preferences.")
    public List<CandidateService.PreferenceInput> preferences(@AuthenticationPrincipal AuthUser me) {
        return candidateService.myPreferences(me);
    }

    @PutMapping("/me/preferences")
    @Operation(summary = "Replace my ranked preferences (1 = top choice).")
    public List<CandidateService.PreferenceInput> setPreferences(@AuthenticationPrincipal AuthUser me,
                                                                 @RequestBody List<CandidateService.PreferenceInput> prefs) {
        return candidateService.setPreferences(me, prefs);
    }

    @GetMapping("/me/readiness")
    @Operation(summary = "Allocation readiness: a calculated completeness score (never a guarantee).")
    public ReadinessService.Readiness readiness(@AuthenticationPrincipal AuthUser me) {
        return candidateService.myReadiness(me);
    }

    @PostMapping("/me/resume")
    @Operation(summary = "Upload a resume (PDF/DOCX, 5 MB max). Skills are extracted and await your review.")
    public CandidateService.ResumeInfo uploadResume(@AuthenticationPrincipal AuthUser me,
                                                    @RequestParam("file") MultipartFile file) throws IOException {
        return candidateService.uploadResume(me, file);
    }

    @GetMapping("/me/resume")
    @Operation(summary = "Latest resume and its extracted skills.")
    public CandidateService.ResumeInfo resume(@AuthenticationPrincipal AuthUser me) {
        CandidateService.ResumeInfo r = candidateService.latestResume(me);
        return r == null ? new CandidateService.ResumeInfo(null, null, 0, "NONE", 0, List.of(), List.of(), null, null) : r;
    }

    @GetMapping("/me/resume/suggestions")
    @Operation(summary = "The current resume's structured suggestions (profile fields with confidence + evidence, skills, interests).")
    public CandidateService.ResumeSuggestionsView suggestions(@AuthenticationPrincipal AuthUser me) {
        return candidateService.mySuggestions(me);
    }

    @PostMapping("/me/resume/apply-suggestions")
    @Operation(summary = "Confirm resume suggestions. Only explicitly confirmed values are saved; "
            + "nothing is ever auto-overwritten. Marks the resume REVIEWED.")
    public CandidateService.ResumeInfo applySuggestions(@AuthenticationPrincipal AuthUser me,
                                                        @RequestBody CandidateService.ApplyInput input) {
        return candidateService.applySuggestions(me, input);
    }

    @GetMapping("/me/allocation")
    @Operation(summary = "My allocation in the latest run, with a clear state (allocated / not allocated / no run yet / stale).")
    public CandidateService.AllocationView allocation(@AuthenticationPrincipal AuthUser me) {
        return candidateService.myAllocation(me);
    }

    @GetMapping("/me/allocation/history")
    @Operation(summary = "My allocation history across runs.")
    public List<CandidateService.HistoryEntry> history(@AuthenticationPrincipal AuthUser me) {
        return candidateService.myHistory(me);
    }

    @GetMapping("/me/eligibility")
    @Operation(summary = "My eligibility per active opportunity, with explicit human-readable reasons.")
    public Page<CandidateService.EligibilityEntry> eligibility(@AuthenticationPrincipal AuthUser me,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "10") int size) {
        return candidateService.myEligibility(me, page, size);
    }

    @GetMapping("/me/dashboard")
    @Operation(summary = "Candidate dashboard aggregate: allocation state, readiness, next actions.")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthUser me) {
        CandidateService.AllocationView alloc = candidateService.myAllocation(me);
        ReadinessService.Readiness ready = candidateService.myReadiness(me);
        List<String> actions = new java.util.ArrayList<>();
        String resumeState = candidateService.latestResume(me) == null ? null : candidateService.latestResume(me).status();
        if ("PENDING_REVIEW".equals(resumeState)) {
            actions.add("Review the suggestions found in your resume");
        }
        if (ready.items().stream().anyMatch(i -> !i.status().equals("COMPLETED") && i.key().equals("basic"))) {
            actions.add("Complete your basic information");
        }
        if (ready.items().stream().anyMatch(i -> !i.status().equals("COMPLETED") && i.key().equals("skills"))) {
            actions.add("Add at least 3 skills");
        }
        if (ready.items().stream().anyMatch(i -> !i.status().equals("COMPLETED") && i.key().equals("preferences"))) {
            actions.add("Rank at least one opportunity");
        }
        if (!"REVIEWED".equals(resumeState) && !"PENDING_REVIEW".equals(resumeState)) {
            actions.add("Upload your resume");
        }
        if (actions.isEmpty()) {
            actions.add("You are ready — watch for the next allocation run");
        }
        return Map.of("allocation", alloc, "readiness", ready, "nextActions", actions);
    }
}
