package in.pragati.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.pragati.ai.AiServiceClient;
import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.security.AuthUser;

/**
 * Tests the candidate analytics contract: recommendations, skill gap and the
 * guarantee that recommendation calls never touch candidate data.
 */
class AnalyticsServiceTest {

    private CandidateProfileRepository profiles;
    private CandidateSkillRepository skills;
    private CandidateInterestRepository interests;
    private CandidatePreferenceRepository preferences;
    private OpportunityRepository opportunities;
    private OpportunitySkillRepository oppSkills;
    private AllocationRunRepository runs;
    private AiServiceClient ai;
    private CandidateAnalyticsService svc;

    private static final AuthUser ME = new AuthUser(100L, "candidate@pragati.gov.in", "Aarav Sharma", "CANDIDATE");

    @BeforeEach
    void setUp() {
        profiles = mock(CandidateProfileRepository.class);
        skills = mock(CandidateSkillRepository.class);
        interests = mock(CandidateInterestRepository.class);
        preferences = mock(CandidatePreferenceRepository.class);
        opportunities = mock(OpportunityRepository.class);
        oppSkills = mock(OpportunitySkillRepository.class);
        runs = mock(AllocationRunRepository.class);
        ai = mock(AiServiceClient.class);

        svc = new CandidateAnalyticsService(profiles, skills, interests, preferences,
                opportunities, oppSkills, runs, new SuitabilityService(), ai, new ObjectMapper());

        CandidateProfile p = new CandidateProfile();
        p.id = 1L;
        p.userId = 100L;
        p.fullName = "Aarav Sharma";
        p.qualification = Qualification.BACHELORS;
        p.candidateStatus = CandidateStatus.STUDENT;
        p.candidateState = CandidateState.ACTIVE;
        p.state = "Delhi";
        p.experienceYears = 1.0;
        lenient().when(profiles.findByUserId(100L)).thenReturn(Optional.of(p));
        when(runs.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
    }

    private void candidateSkills(String... s) {
        List<CandidateSkill> list = new java.util.ArrayList<>();
        for (int i = 0; i < s.length; i++) {
            CandidateSkill sk = new CandidateSkill();
            sk.candidateId = 1L;
            sk.canonical = s[i];
            sk.validated = true;
            list.add(sk);
        }
        when(skills.findByCandidateId(1L)).thenReturn(list);
    }

    private void interests(Sector... s) {
        List<CandidateInterest> list = new java.util.ArrayList<>();
        for (Sector x : s) {
            CandidateInterest it = new CandidateInterest();
            it.candidateId = 1L;
            it.sector = x;
            list.add(it);
        }
        when(interests.findByCandidateId(1L)).thenReturn(list);
    }

    private Opportunity opp(long id, String title, Sector sector, String state, Qualification min) {
        Opportunity o = new Opportunity();
        o.id = id;
        o.title = title;
        o.sector = sector;
        o.state = state;
        o.city = state;
        o.capacity = 5;
        o.durationMonths = 3;
        o.minQualification = min;
        o.status = OppStatus.ACTIVE;
        return o;
    }

    private OpportunitySkill os(long oppId, String skill, boolean mandatory) {
        OpportunitySkill s = new OpportunitySkill();
        s.opportunityId = oppId;
        s.skill = skill;
        s.mandatory = mandatory;
        return s;
    }

    // ------------------------------------------------------------------
    // Recommendations
    // ------------------------------------------------------------------

    @Test
    void recommendationsReturnTopFiveEligibleOnlyRankedByScore() {
        // Candidate holds every mandatory skill of o11..o16 but NOT the one o17 needs.
        candidateSkills("Data Analysis", "SQL", "Finance", "Digital Marketing", "Policy Analysis");
        interests(Sector.DATA_ANALYTICS);
        when(preferences.findByCandidateId(1L)).thenReturn(List.of());

        Opportunity o1 = opp(11L, "Data Analytics Intern", Sector.DATA_ANALYTICS, "Delhi", Qualification.BACHELORS);
        Opportunity o2 = opp(12L, "Data Science Intern", Sector.DATA_ANALYTICS, "Delhi", Qualification.BACHELORS);
        Opportunity o3 = opp(13L, "BI Intern", Sector.DATA_ANALYTICS, "Delhi", Qualification.BACHELORS);
        Opportunity o4 = opp(14L, "Finance Intern", Sector.FINANCE_BANKING, "Delhi", Qualification.BACHELORS);
        Opportunity o5 = opp(15L, "Marketing Intern", Sector.MARKETING_COMMUNICATION, "Karnataka", Qualification.BACHELORS);
        Opportunity o6 = opp(16L, "Policy Intern", Sector.PUBLIC_ADMIN_POLICY, "Delhi", Qualification.BACHELORS);
        // o7 is active but the candidate lacks the mandatory skill → excluded.
        Opportunity o7 = opp(17L, "Cybersecurity Intern", Sector.SOFTWARE_IT, "Delhi", Qualification.BACHELORS);
        // o8 is paused → excluded even though skills would match.
        Opportunity o8 = opp(18L, "Paused Analytics Intern", Sector.DATA_ANALYTICS, "Delhi", Qualification.BACHELORS);
        o8.status = OppStatus.PAUSED;

        when(opportunities.findAllByStatus(OppStatus.ACTIVE)).thenReturn(List.of(o1, o2, o3, o4, o5, o6, o7));
        when(oppSkills.findByOpportunityIdIn(anyList())).thenReturn(List.of(
                os(11L, "Data Analysis", true), os(11L, "SQL", false),
                os(12L, "Data Analysis", true), os(12L, "Machine Learning", false),
                os(13L, "SQL", true), os(13L, "Data Analysis", false),
                os(14L, "Finance", true),
                os(15L, "Digital Marketing", true),
                os(16L, "Policy Analysis", true),
                os(17L, "Cybersecurity", true),
                os(18L, "Data Analysis", true)));

        CandidateAnalyticsService.Recommendations recs = svc.recommendations(ME);

        assertEquals(5, recs.items().size(), "exactly the top 5 are returned");
        List<Long> ids = recs.items().stream().map(r -> r.opportunity().id()).toList();
        assertFalse(ids.contains(17L), "ineligible opportunity must never be recommended: " + ids);
        assertFalse(ids.contains(18L), "paused opportunity must never be recommended: " + ids);
        for (int i = 0; i < recs.items().size(); i++) {
            assertEquals((long) (i + 1), recs.items().get(i).rank(), "ranks must be sequential from 1");
        }
        for (int i = 1; i < recs.items().size(); i++) {
            assertTrue(recs.items().get(i - 1).overallScore() >= recs.items().get(i).overallScore() - 1e-9,
                    "items must be sorted by fit score, descending");
        }
        for (var r : recs.items()) {
            assertTrue(r.eligible(), "every recommendation must be eligible");
            assertNotNull(r.reason(), "every recommendation must explain why it suits the candidate");
            assertFalse(r.opportunity().active() != true);
        }
        assertTrue(recs.note().contains("not a final allocation"),
                "recommendations must be labelled as decision support, not allocation: " + recs.note());
    }

    @Test
    void recommendationsNeverModifyCandidateData() {
        candidateSkills("Data Analysis");
        interests(Sector.DATA_ANALYTICS);
        when(preferences.findByCandidateId(1L)).thenReturn(List.of());
        Opportunity o1 = opp(11L, "Data Analytics Intern", Sector.DATA_ANALYTICS, "Delhi", Qualification.BACHELORS);
        when(opportunities.findAllByStatus(OppStatus.ACTIVE)).thenReturn(List.of(o1));
        when(oppSkills.findByOpportunityIdIn(anyList())).thenReturn(List.of(os(11L, "Data Analysis", true)));

        svc.recommendations(ME);
        svc.recommendations(ME);

        verify(profiles, never()).save(any(CandidateProfile.class));
        verify(profiles, never()).saveAll(any());
        verify(skills, never()).save(any(CandidateSkill.class));
        verify(skills, never()).deleteByCandidateId(anyLong());
        verify(interests, never()).save(any(CandidateInterest.class));
        verify(preferences, never()).save(any(CandidatePreference.class));
    }

    // ------------------------------------------------------------------
    // Skill gap
    // ------------------------------------------------------------------

    @Test
    void skillGapSplitsCoveredMandatoryGapsAndPreferredGaps() {
        candidateSkills("SQL", "Tableau");
        interests(Sector.DATA_ANALYTICS);
        when(preferences.findByCandidateId(1L)).thenReturn(List.of());

        Opportunity o = opp(11L, "Data Analytics Intern", Sector.DATA_ANALYTICS, "Delhi", Qualification.BACHELORS);
        when(opportunities.findById(11L)).thenReturn(Optional.of(o));
        when(oppSkills.findByOpportunityId(11L)).thenReturn(List.of(
                os(11L, "Data Analysis", true),
                os(11L, "SQL", true),
                os(11L, "Tableau", false)));

        CandidateAnalyticsService.SkillGap gap = svc.skillGap(ME, 11L);

        assertEquals(List.of("SQL", "Tableau"), gap.coveredSkills());
        assertEquals(List.of("Data Analysis"), gap.mandatoryGaps());
        assertTrue(gap.preferredGaps().isEmpty());
        assertFalse(gap.eligible(), "a missing mandatory skill must block eligibility");
        assertTrue(gap.assistantSummary().contains("Data Analysis"),
                "the summary must name the blocking skill: " + gap.assistantSummary());
        // readiness = covered / listed = 2/3 ≈ 66.7
        assertEquals(66.7, gap.readinessPercent(), 0.11);
    }

    @Test
    void eligibleCandidateAlwaysHasZeroMandatoryGaps() {
        candidateSkills("Data Analysis", "SQL", "Tableau");
        interests(Sector.DATA_ANALYTICS);
        when(preferences.findByCandidateId(1L)).thenReturn(List.of());

        Opportunity o = opp(11L, "Data Analytics Intern", Sector.DATA_ANALYTICS, "Delhi", Qualification.BACHELORS);
        when(opportunities.findById(11L)).thenReturn(Optional.of(o));
        when(oppSkills.findByOpportunityId(11L)).thenReturn(List.of(
                os(11L, "Data Analysis", true),
                os(11L, "SQL", true),
                os(11L, "Tableau", false)));

        CandidateAnalyticsService.SkillGap gap = svc.skillGap(ME, 11L);

        assertTrue(gap.eligible());
        assertTrue(gap.mandatoryGaps().isEmpty(),
                "an eligible candidate must never show a blocking mandatory gap");
        assertEquals(100.0, gap.readinessPercent(), 0.01, "all listed skills covered → 100% readiness");
    }
}
