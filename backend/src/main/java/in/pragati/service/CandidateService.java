package in.pragati.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.ai.AiServiceClient;
import in.pragati.common.ApiException;
import in.pragati.common.Labels;
import in.pragati.common.Weights;
import in.pragati.config.AppProperties;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;

/**
 * Candidate-facing services: profile, skills, interests, preferences,
 * resume intelligence, readiness, allocation view and eligibility.
 * Every method is scoped to the authenticated candidate (self-scope).
 */
@Service
public class CandidateService {

    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidateInterestRepository interests;
    private final CandidatePreferenceRepository preferences;
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final AssignmentRepository assignments;
    private final AllocationRunRepository runs;
    private final DatasetSnapshotRepository snapshots;
    private final ResumeFileRepository resumes;
    private final SuitabilityService suitability;
    private final ReadinessService readiness;
    private final ResumeIntelligenceService resumeIntelligence;
    private final AuditService audit;
    private final AiServiceClient ai;
    private final AppProperties props;
    private final ObjectMapper json;

    public CandidateService(UserRepository users, CandidateProfileRepository profiles,
                            CandidateSkillRepository skills, CandidateInterestRepository interests,
                            CandidatePreferenceRepository preferences, OpportunityRepository opportunities,
                            OpportunitySkillRepository oppSkills, AssignmentRepository assignments,
                            AllocationRunRepository runs, DatasetSnapshotRepository snapshots,
                            ResumeFileRepository resumes, SuitabilityService suitability,
                            ReadinessService readiness, ResumeIntelligenceService resumeIntelligence,
                            AuditService audit, AiServiceClient ai,
                            AppProperties props, ObjectMapper json) {
        this.users = users; this.profiles = profiles; this.skills = skills;
        this.interests = interests; this.preferences = preferences;
        this.opportunities = opportunities; this.oppSkills = oppSkills;
        this.assignments = assignments; this.runs = runs; this.snapshots = snapshots;
        this.resumes = resumes; this.suitability = suitability; this.readiness = readiness;
        this.resumeIntelligence = resumeIntelligence;
        this.audit = audit; this.ai = ai; this.props = props; this.json = json;
    }

    // ------------------------------------------------------------------
    // Self-scoped profile
    // ------------------------------------------------------------------

    public CandidateProfile requireSelfProfile(AuthUser me) {
        return profiles.findByUserId(me.id()).orElseThrow(() ->
                ApiException.notFound("Your profile has not been initialized yet."));
    }

    public record ProfileInput(String fullName, String phone, java.time.LocalDate dob,
                               String qualification, String candidateStatus,
                               String state, String district, String locationType,
                               String bio, Double experienceYears) { }

    @Transactional
    public CandidateProfile updateProfile(AuthUser me, ProfileInput in) {
        CandidateProfile p = requireSelfProfile(me);
        if (in.fullName != null) {
            if (in.fullName.isBlank()) throw ApiException.badRequest("Full name cannot be empty.");
            p.fullName = in.fullName;
        }
        if (in.phone != null) {
            if (!in.phone.isBlank() && !in.phone.matches("\\+?[0-9]{8,15}")) {
                throw ApiException.badRequest("Phone number is not valid (8-15 digits).");
            }
            p.phone = in.phone;
        }
        if (in.dob != null) p.dob = in.dob;
        if (in.qualification != null) {
            p.qualification = parseEnum(Qualification.class, in.qualification, "qualification");
        }
        if (in.candidateStatus != null) {
            p.candidateStatus = parseEnum(CandidateStatus.class, in.candidateStatus, "current status");
        }
        if (in.state != null) p.state = in.state;
        if (in.district != null) p.district = in.district;
        if (in.locationType != null) {
            p.locationType = parseEnum(LocationType.class, in.locationType, "location type");
        }
        if (in.bio != null) {
            if (in.bio.length() > 600) throw ApiException.badRequest("Keep the introduction under 600 characters.");
            p.bio = in.bio;
        }
        if (in.experienceYears != null) {
            if (in.experienceYears < 0 || in.experienceYears > 50) {
                throw ApiException.badRequest("Experience must be between 0 and 50 years.");
            }
            p.experienceYears = in.experienceYears;
        }
        p.touch();
        profiles.save(p);
        audit.log(me, AuditAction.PROFILE_UPDATED, "CandidateProfile", String.valueOf(p.id),
                me.name() + " updated their profile.", null);
        return p;
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid value for " + field + ".");
        }
    }

    public record SkillItem(Long id, String raw, String canonical, String source, boolean validated) { }

    public List<SkillItem> mySkills(AuthUser me) {
        CandidateProfile p = requireSelfProfile(me);
        return skills.findByCandidateId(p.id).stream()
                .map(s -> new SkillItem(s.id, s.rawLabel, s.canonical, s.source.name(), s.validated))
                .toList();
    }

    @Transactional
    public List<SkillItem> setSkills(AuthUser me, List<String> labels) {
        CandidateProfile p = requireSelfProfile(me);
        Set<String> canonical = new HashSet<>();
        for (String raw : labels) {
            String c = SkillTaxonomy.normalize(raw);
            if (c == null) throw ApiException.badRequest("Unrecognized skill: \"" + raw + "\". Please use a skill from the suggested list.");
            canonical.add(c);
        }
        if (canonical.size() > 20) throw ApiException.badRequest("Please keep the list to at most 20 skills.");
        skills.deleteByCandidateId(p.id);
        List<SkillItem> out = new ArrayList<>();
        for (String c : new java.util.TreeSet<>(canonical)) {
            CandidateSkill s = new CandidateSkill();
            s.candidateId = p.id;
            s.rawLabel = c;
            s.canonical = c;
            s.source = SkillSource.PROFILE;
            s.validated = true;
            out.add(new SkillItem(s.id, c, c, "PROFILE", true));
        }
        skills.saveAll(out.stream().map(i -> {
            CandidateSkill s = new CandidateSkill();
            s.candidateId = p.id;
            s.rawLabel = i.canonical();
            s.canonical = i.canonical();
            s.source = SkillSource.PROFILE;
            s.validated = true;
            return s;
        }).toList());
        p.touch();
        profiles.save(p);
        return mySkills(me);
    }

    public List<Sector> myInterests(AuthUser me) {
        CandidateProfile p = requireSelfProfile(me);
        return interests.findByCandidateId(p.id).stream().map(i -> i.sector).toList();
    }

    @Transactional
    public List<Sector> setInterests(AuthUser me, List<Sector> sectors) {
        CandidateProfile p = requireSelfProfile(me);
        interests.deleteByCandidateId(p.id);
        for (Sector s : new java.util.TreeSet<>(sectors)) {
            CandidateInterest it = new CandidateInterest();
            it.candidateId = p.id;
            it.sector = s;
            interests.save(it);
        }
        p.touch();
        profiles.save(p);
        return myInterests(me);
    }

    public record PreferenceInput(Long opportunityId, int rank) { }

    public List<PreferenceInput> myPreferences(AuthUser me) {
        CandidateProfile p = requireSelfProfile(me);
        return preferences.findByCandidateId(p.id).stream()
                .sorted(java.util.Comparator.comparing(x -> x.rank))
                .map(x -> new PreferenceInput(x.opportunityId, x.rank))
                .toList();
    }

    @Transactional
    public List<PreferenceInput> setPreferences(AuthUser me, List<PreferenceInput> inputs) {
        CandidateProfile p = requireSelfProfile(me);
        preferences.deleteByCandidateId(p.id);
        Set<Integer> ranks = new HashSet<>();
        Set<Long> oppIds = new HashSet<>();
        for (PreferenceInput in : inputs) {
            if (in.rank() < 1 || in.rank() > 10) throw ApiException.badRequest("Preference rank must be between 1 and 10.");
            if (!ranks.add(in.rank())) throw ApiException.badRequest("Each preference needs a different rank.");
            if (!oppIds.add(in.opportunityId())) throw ApiException.badRequest("The same opportunity cannot be listed twice.");
            opportunities.findById(in.opportunityId())
                    .orElseThrow(() -> ApiException.badRequest("Unknown opportunity in preferences."));
            CandidatePreference cp = new CandidatePreference();
            cp.candidateId = p.id;
            cp.opportunityId = in.opportunityId();
            cp.rank = in.rank();
            preferences.save(cp);
        }
        p.touch();
        profiles.save(p);
        return myPreferences(me);
    }

    // ------------------------------------------------------------------
    // Resume intelligence
    // ------------------------------------------------------------------

    public record ResumeInfo(Long id, String originalName, long sizeBytes, String status,
                             int textLength, List<String> pendingSkills, List<String> pendingInterests,
                             String statusMessage, java.time.Instant createdAt) { }

    public record ResumeSuggestionsView(Long resumeId, String status, String statusMessage,
                                        List<ResumeIntelligenceService.FieldSuggestion> fields,
                                        List<String> skills, List<String> interests) { }

    public ResumeInfo latestResume(AuthUser me) {
        return resumes.findTopByUserIdOrderByIdDesc(me.id()).map(r -> {
            List<String> pending = new ArrayList<>();
            List<String> interestsPending = new ArrayList<>();
            if (r.skillsJson != null) {
                try {
                    for (var node : json.readTree(r.skillsJson)) pending.add(node.asText());
                } catch (Exception ignored) { }
            }
            if (r.suggestionsJson != null) {
                try {
                    var root = json.readTree(r.suggestionsJson);
                    if (root.has("interests")) for (var n : root.get("interests")) interestsPending.add(n.asText());
                } catch (Exception ignored) { }
            }
            return new ResumeInfo(r.id, r.originalName, r.sizeBytes, r.status, r.textLength,
                    pending, interestsPending, r.statusMessage, r.createdAt);
        }).orElse(null);
    }

    /** The current resume's suggestions. The UI compares each value with the
     *  current profile (fetched from /me/profile) to distinguish "fill a blank"
     *  from "proposed update of data you already entered". */
    public ResumeSuggestionsView mySuggestions(AuthUser me) {
        ResumeFile r = resumes.findTopByUserIdOrderByIdDesc(me.id())
                .orElseThrow(() -> ApiException.notFound("Upload a resume first."));
        CandidateProfile p = requireSelfProfile(me);
        List<ResumeIntelligenceService.FieldSuggestion> fields = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        List<String> interests = new ArrayList<>();
        if (r.suggestionsJson != null) {
            try {
                var root = json.readTree(r.suggestionsJson);
                for (var n : root.path("fields")) fields.add(new ResumeIntelligenceService.FieldSuggestion(
                        n.get("field").asText(), n.get("value").asText(),
                        n.get("confidence").asText(), n.get("evidence").asText()));
                for (var n : root.path("skills")) skills.add(n.asText());
                for (var n : root.path("interests")) interests.add(n.asText());
            } catch (Exception ignored) { }
        }
        return new ResumeSuggestionsView(r.id, r.status, r.statusMessage, fields, skills, interests);
    }

    @Transactional
    public ResumeInfo uploadResume(AuthUser me, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Please choose a file to upload.");
        if (file.getSize() > 5L * 1024 * 1024) throw ApiException.badRequest("The resume must be at most 5 MB.");
        String name = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String lower = name.toLowerCase();
        boolean pdf = lower.endsWith(".pdf");
        boolean docx = lower.endsWith(".docx");
        if (!pdf && !docx) throw ApiException.badRequest("Upload a PDF or Word (.docx) resume.");
        byte[] head = new byte[4];
        try (var in = file.getInputStream()) {
            int read = in.read(head);
            if (read < 4) throw ApiException.badRequest("The file could not be read.");
        }
        boolean magicPdf = head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46;
        boolean magicZip = head[0] == 0x50 && head[1] == 0x4B;
        if (pdf && !magicPdf) throw ApiException.badRequest("The file does not look like a valid PDF.");
        if (docx && !magicZip) throw ApiException.badRequest("The file does not look like a valid Word document.");

        Path dir = Path.of(props.getUploads().getDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String stored = UUID.randomUUID().toString() + (pdf ? ".pdf" : ".docx");
        Path target = dir.resolve(stored);
        if (!target.normalize().startsWith(dir)) throw ApiException.badRequest("Invalid file name.");
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String text = extractText(target, pdf);
        ResumeFile r = new ResumeFile();
        r.userId = me.id();
        r.originalName = name.substring(0, Math.min(255, name.length()));
        r.storedName = stored;
        r.contentType = file.getContentType();
        r.sizeBytes = file.getSize();
        r.textLength = text.length();

        // A scanned / image-only file yields no readable text: fail honestly
        // instead of pretending the extraction succeeded.
        if (text == null || text.strip().length() < 40) {
            r.status = "FAILED";
            r.statusMessage = "We could not reliably read this PDF. Please upload a text-based PDF or a Word (.docx) resume.";
            resumes.save(r);
            audit.log(me, AuditAction.RESUME_UPLOADED, "ResumeFile", String.valueOf(r.id),
                    me.name() + " uploaded a resume that could not be read (" + name + ").", null);
            return latestResume(me);
        }

        r.status = "PROCESSING";
        List<String> extracted = extractSkills(text);
        // Normalized, taxonomy-validated skills only; user confirms before they count.
        List<String> validated = extracted.stream().distinct().limit(15).toList();
        try { r.skillsJson = json.writeValueAsString(validated); } catch (Exception ignored) { }

        // Structured profile suggestions (deterministic, evidence-backed, never auto-applied).
        try {
            ResumeIntelligenceService.ResumeSuggestions sugg = resumeIntelligence.extract(text);
            var node = json.createObjectNode();
            var fields = node.putArray("fields");
            for (var f : sugg.fields()) {
                var fn = fields.addObject();
                fn.put("field", f.field());
                fn.put("value", f.value());
                fn.put("confidence", f.confidence());
                fn.put("evidence", f.evidence());
            }
            var skillsArr = node.putArray("skills");
            for (String s : validated) skillsArr.add(s);
            var interestsArr = node.putArray("interests");
            for (String s : sugg.interests()) interestsArr.add(s);
            r.suggestionsJson = json.writeValueAsString(node);
        } catch (Exception e) {
            r.suggestionsJson = null;
        }

        boolean anythingToReview = !validated.isEmpty()
                || (r.suggestionsJson != null && r.suggestionsJson.contains("\"fields\":[{\""));
        r.status = anythingToReview ? "PENDING_REVIEW" : "REVIEWED";
        if (!anythingToReview) r.statusMessage = "No profile suggestions found — nothing to review.";
        resumes.save(r);
        audit.log(me, AuditAction.RESUME_UPLOADED, "ResumeFile", String.valueOf(r.id),
                me.name() + " uploaded a resume (" + name + ").", Map.of("skillsFound", validated.size()));
        return latestResume(me);
    }

    // ------------------------------------------------------------------
    // Resume suggestion review & safe auto-fill
    // ------------------------------------------------------------------

    public record ApplyInput(Long resumeId, Map<String, String> profile, List<String> skills, List<String> interests) { }

    /** Applies only what the candidate explicitly confirms.
     *  - only the latest resume of this user can be reviewed;
     *  - profile values are validated with the same rules as manual entry;
     *  - skills must be among the skills this resume actually suggested;
     *  - interests must be among the sectors this resume actually suggested;
     *  - after confirmation the resume becomes REVIEWED (readiness counts it).
     */
    @Transactional
    public ResumeInfo applySuggestions(AuthUser me, ApplyInput in) {
        if (in == null || in.resumeId() == null) throw ApiException.badRequest("resumeId is required.");
        CandidateProfile p = requireSelfProfile(me);
        ResumeFile latest = resumes.findTopByUserIdOrderByIdDesc(me.id())
                .orElseThrow(() -> ApiException.notFound("Upload a resume first."));
        if (!latest.id.equals(in.resumeId())) {
            throw ApiException.conflict("A newer resume was uploaded after these suggestions were generated. Review the latest resume instead.");
        }
        if (!"PENDING_REVIEW".equals(latest.status) && !"PROCESSING".equals(latest.status)) {
            throw ApiException.badRequest("This resume has already been reviewed.");
        }
        Set<String> suggestedSkills = new HashSet<>();
        Set<String> suggestedInterests = new HashSet<>();
        Set<String> suggestedFields = new HashSet<>();
        if (latest.suggestionsJson != null) {
            try {
                var root = json.readTree(latest.suggestionsJson);
                for (var n : root.path("skills")) suggestedSkills.add(n.asText());
                for (var n : root.path("interests")) suggestedInterests.add(n.asText());
                for (var n : root.path("fields")) suggestedFields.add(n.get("field").asText());
            } catch (Exception ignored) { }
        }

        Map<String, String> profile = in.profile() == null ? Map.of() : in.profile();
        for (var e : profile.entrySet()) {
            String field = e.getKey();
            if (!ResumeIntelligenceService.PROFILE_FIELDS.contains(field)) {
                throw ApiException.badRequest("Unknown profile field: " + field);
            }
            if (!suggestedFields.contains(field)) {
                throw ApiException.badRequest("The resume did not suggest a value for " + field + ".");
            }
        }
        for (String s : in.skills() == null ? List.<String>of() : in.skills()) {
            if (!suggestedSkills.contains(s)) throw ApiException.badRequest("Skill not suggested by this resume: " + s);
        }
        for (String it : in.interests() == null ? List.<String>of() : in.interests()) {
            if (!suggestedInterests.contains(it)) throw ApiException.badRequest("Interest not suggested by this resume: " + it);
        }

        // Apply confirmed profile values with the same validation as manual entry.
        CandidateProfile updated = updateProfile(me, new ProfileInput(
                profile.get("fullName"), profile.get("phone"), null,
                profile.get("qualification"), profile.get("candidateStatus"),
                profile.get("state"), profile.get("district"), null,
                profile.get("bio"),
                profile.get("experienceYears") == null ? null : Double.valueOf(profile.get("experienceYears"))));

        // Merge confirmed skills (source RESUME) and interests.
        if (in.skills() != null && !in.skills().isEmpty()) {
            Set<String> existing = skills.findByCandidateId(p.id).stream().map(s -> s.canonical).collect(java.util.stream.Collectors.toSet());
            for (String raw : in.skills()) {
                String canonical = SkillTaxonomy.normalize(raw);
                if (canonical == null || existing.contains(canonical)) continue;
                CandidateSkill s = new CandidateSkill();
                s.candidateId = p.id;
                s.rawLabel = raw;
                s.canonical = canonical;
                s.source = SkillSource.RESUME;
                s.validated = true;
                skills.save(s);
            }
        }
        if (in.interests() != null && !in.interests().isEmpty()) {
            Set<Sector> current = new java.util.TreeSet<>(myInterests(me));
            for (String it : in.interests()) current.add(Sector.valueOf(it));
            setInterests(me, new ArrayList<>(current));
        }

        updated.touch();
        profiles.save(updated);
        latest.status = "REVIEWED";
        latest.statusMessage = null;
        resumes.save(latest);
        audit.log(me, AuditAction.RESUME_REVIEWED, "ResumeFile", String.valueOf(latest.id),
                me.name() + " reviewed resume suggestions ("
                        + profile.size() + " profile fields, "
                        + (in.skills() == null ? 0 : in.skills().size()) + " skills, "
                        + (in.interests() == null ? 0 : in.interests().size()) + " interests confirmed).", null);
        return latestResume(me);
    }

    private String extractText(Path file, boolean pdf) {
        try {
            if (pdf) {
                org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(file.toFile());
                try {
                    return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
                } finally {
                    doc.close();
                }
            } else {
                // DOCX: body paragraphs, tables (row by row) and headers/footers,
                // so contact details in a table or header are not lost.
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file.toFile());
                     org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                             new org.apache.poi.xwpf.usermodel.XWPFDocument(fis)) {
                    StringBuilder sb = new StringBuilder();
                    for (var para : doc.getParagraphs()) sb.append(para.getText()).append('\n');
                    for (var table : doc.getTables()) {
                        for (var row : table.getRows()) {
                            List<String> cells = new ArrayList<>();
                            for (var cell : row.getTableCells()) cells.add(cell.getText().trim());
                            sb.append(String.join("  |  ", cells)).append('\n');
                        }
                    }
                    for (var header : doc.getHeaderList()) for (var p : header.getParagraphs()) sb.append(p.getText()).append('\n');
                    for (var footer : doc.getFooterList()) for (var p : footer.getParagraphs()) sb.append(p.getText()).append('\n');
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            throw ApiException.badRequest("We could not read this file. Please try a different resume.");
        }
    }

    private List<String> extractSkills(String text) {
        // AI-assisted when the AI service is available; deterministic local fallback otherwise.
        if (text == null || text.isBlank()) return List.of();
        try {
            AiServiceClient.SkillsResult res = ai.extractSkills(text);
            return res.skills().stream().map(AiServiceClient.ExtractedSkill::canonical).toList();
        } catch (Exception e) {
            List<String> found = new ArrayList<>();
            String lower = text.toLowerCase();
            for (String canonical : SkillTaxonomy.CANONICAL) {
                if (lower.contains(canonical.toLowerCase())) found.add(canonical);
            }
            return found;
        }
    }

    // ------------------------------------------------------------------
    // Readiness & allocation view
    // ------------------------------------------------------------------

    public ReadinessService.Readiness myReadiness(AuthUser me) {
        CandidateProfile p = requireSelfProfile(me);
        int skillCount = (int) skills.findByCandidateId(p.id).stream().filter(s -> s.validated).count();
        int interestCount = interests.findByCandidateId(p.id).size();
        int preferenceCount = preferences.findByCandidateId(p.id).size();
        String resumeState = resumes.findTopByUserIdOrderByIdDesc(me.id())
                .map(r -> r.status).orElse(null);
        return readiness.compute(p, skillCount, interestCount, preferenceCount, resumeState);
    }

    public record AllocationView(String state, String message, Long runId, int runNumber,
                                 String opportunityTitle, String sector, String opportunityState,
                                 double suitability, Integer preferenceRank, java.time.Instant runAt) { }

    public AllocationView myAllocation(AuthUser me) {
        CandidateProfile p = requireSelfProfile(me);
        DatasetSnapshot current = snapshots.findTopByOrderByIdDesc().orElse(null);
        AllocationRun latest = runs.findTopByOrderByIdDesc().orElse(null);
        if (latest == null) {
            return new AllocationView("NO_RUN", "No allocation has been completed yet.",
                    null, 0, null, null, null, 0, null, null);
        }
        if (latest.status != RunStatus.COMPLETED) {
            return new AllocationView("IN_PROGRESS",
                    "An allocation is " + (latest.status == RunStatus.RUNNING ? "running" : "queued") + " right now.",
                    latest.id, latest.number, null, null, null, 0, null, latest.createdAt);
        }
        if (current != null && !current.id.equals(latest.snapshotId)) {
            return new AllocationView("STALE",
                    "The dataset changed after this allocation. Results may be outdated — please run the allocation again.",
                    latest.id, latest.number, null, null, null, 0, null, latest.completedAt);
        }
        java.util.Optional<Assignment> a = assignments.findByRunIdAndCandidateIdAndSource(
                latest.id, p.id, AssignmentSource.GLOBAL);
        if (a.isEmpty()) {
            return new AllocationView("NOT_ALLOCATED",
                    "No opportunity was assigned in the latest run. This can happen when eligible seats are fewer than eligible candidates.",
                    latest.id, latest.number, null, null, null, 0, null, latest.completedAt);
        }
        Opportunity o = opportunities.findById(a.get().opportunityId).orElseThrow();
        Integer rank = preferences.findByCandidateId(p.id).stream()
                .filter(x -> x.opportunityId.equals(o.id)).map(x -> x.rank).findFirst().orElse(null);
        return new AllocationView("ALLOCATED",
                "You are fit for " + o.title + ". Allocated in the latest global allocation.",
                latest.id, latest.number, o.title, Labels.label(o.sector), o.state,
                a.get().suitability, rank, latest.completedAt);
    }

    public record HistoryEntry(Long runId, int runNumber, java.time.Instant completedAt,
                               String status, String opportunityTitle, double suitability) { }

    public List<HistoryEntry> myHistory(AuthUser me) {
        CandidateProfile p = requireSelfProfile(me);
        return assignments.findByCandidateIdAndSource(p.id, AssignmentSource.GLOBAL).stream()
                .sorted(java.util.Comparator.comparing((in.pragati.domain.Assignment a) -> a.runId).reversed())
                .map(a -> {
                    AllocationRun r = runs.findById(a.runId).orElse(null);
                    if (r == null) return null;
                    String title = opportunities.findById(a.opportunityId)
                            .map(o -> o.title).orElse("Opportunity");
                    return new HistoryEntry(r.id, r.number, r.completedAt,
                            r.status == RunStatus.COMPLETED ? "Completed" : Labels.label(r.status),
                            title, a.suitability);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public record EligibilityEntry(Long opportunityId, String title, String sector, String state,
                                   int capacity, boolean eligible, List<String> reasons) { }

    public Page<EligibilityEntry> myEligibility(AuthUser me, int page, int size) {
        CandidateProfile p = requireSelfProfile(me);
        Set<String> mySkills = skills.findByCandidateId(p.id).stream()
                .filter(s -> s.validated).map(s -> s.canonical)
                .collect(java.util.stream.Collectors.toSet());
        List<Opportunity> opps = opportunities.findAllByStatus(OppStatus.ACTIVE);
        Map<Long, List<OpportunitySkill>> reqMap = new HashMap<>();
        List<Long> ids = opps.stream().map(o -> o.id).toList();
        for (OpportunitySkill s : oppSkills.findByOpportunityIdIn(ids)) {
            reqMap.computeIfAbsent(s.opportunityId, k -> new ArrayList<>()).add(s);
        }
        List<EligibilityEntry> all = new ArrayList<>();
        for (Opportunity o : opps) {
            SuitabilityService.EligibilityResult res =
                    suitability.check(p, mySkills, o, reqMap.getOrDefault(o.id, List.of()));
            all.add(new EligibilityEntry(o.id, o.title, Labels.label(o.sector), o.state,
                    o.capacity, res.eligible(), res.reasons()));
        }
        int from = Math.min(page * size, all.size());
        return new org.springframework.data.domain.PageImpl<>(
                all.subList(from, Math.min(from + size, all.size())),
                PageRequest.of(page, size), all.size());
    }
}
