package in.pragati.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import in.pragati.common.Weights;
import in.pragati.domain.CandidateProfile;
import in.pragati.domain.Opportunity;
import in.pragati.domain.OpportunitySkill;
import in.pragati.domain.enums.*;

/**
 * Unit tests for the pure eligibility + suitability logic. These are the two functions
 * that determine *who can* be allocated (hard gate) and *how well* (soft objective).
 * No Spring context or database is required.
 */
class SuitabilityServiceTest {

    private final SuitabilityService svc = new SuitabilityService();

    private CandidateProfile candidate(Qualification q, String state) {
        CandidateProfile p = new CandidateProfile();
        p.qualification = q;
        p.state = state;
        p.candidateState = CandidateState.ACTIVE;
        p.candidateStatus = CandidateStatus.STUDENT;
        p.experienceYears = 1.0;
        return p;
    }

    private Opportunity opportunity(Qualification min, OppStatus status, String state) {
        Opportunity o = new Opportunity();
        o.minQualification = min;
        o.status = status;
        o.state = state;
        o.sector = Sector.DATA_ANALYTICS;
        return o;
    }

    private OpportunitySkill skill(String name, boolean mandatory) {
        OpportunitySkill s = new OpportunitySkill();
        s.skill = name;
        s.mandatory = mandatory;
        return s;
    }

    // ------------------------------------------------------------------
    // Eligibility — the hard gate
    // ------------------------------------------------------------------

    @Test
    void rejectsQualificationBelowMinimum() {
        SuitabilityService.EligibilityResult r =
                svc.check(candidate(Qualification.BACHELORS, "Delhi"), Set.of(),
                        opportunity(Qualification.POST_GRADUATE, OppStatus.ACTIVE, "Delhi"),
                        List.of());
        assertFalse(r.eligible());
        assertTrue(r.reasons().stream().anyMatch(x -> x.toLowerCase().contains("qualification")));
    }

    @Test
    void acceptsQualificationAtOrAboveMinimum() {
        SuitabilityService.EligibilityResult r =
                svc.check(candidate(Qualification.DOCTORAL, "Delhi"), Set.of(),
                        opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi"),
                        List.of());
        assertTrue(r.eligible(), "over-qualified candidate must still be eligible: " + r.reasons());
    }

    @Test
    void rejectsMissingMandatorySkill() {
        SuitabilityService.EligibilityResult r =
                svc.check(candidate(Qualification.BACHELORS, "Delhi"), Set.of("Python"),
                        opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi"),
                        List.of(skill("SQL", true), skill("Python", false)));
        assertFalse(r.eligible());
        assertTrue(r.reasons().stream().anyMatch(x -> x.contains("SQL")));
    }

    @Test
    void ignoresMissingOptionalSkill() {
        SuitabilityService.EligibilityResult r =
                svc.check(candidate(Qualification.BACHELORS, "Delhi"), Set.of(),
                        opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi"),
                        List.of(skill("Tableau", false)));
        assertTrue(r.eligible(), "optional skills must never block eligibility: " + r.reasons());
    }

    @Test
    void rejectsInactiveOpportunity() {
        for (OppStatus s : List.of(OppStatus.PAUSED, OppStatus.CLOSED)) {
            SuitabilityService.EligibilityResult r =
                    svc.check(candidate(Qualification.BACHELORS, "Delhi"), Set.of(),
                            opportunity(Qualification.BACHELORS, s, "Delhi"), List.of());
            assertFalse(r.eligible(), s + " opportunity must be ineligible");
        }
    }

    @Test
    void rejectsWithdrawnCandidate() {
        CandidateProfile p = candidate(Qualification.BACHELORS, "Delhi");
        p.candidateState = CandidateState.WITHDRAWN;
        SuitabilityService.EligibilityResult r =
                svc.check(p, Set.of(),
                        opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi"), List.of());
        assertFalse(r.eligible());
    }

    @Test
    void fullyEligiblePairPasses() {
        SuitabilityService.EligibilityResult r =
                svc.check(candidate(Qualification.BACHELORS, "Delhi"), Set.of("SQL"),
                        opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi"),
                        List.of(skill("SQL", true), skill("Python", false)));
        assertTrue(r.eligible(), r.reasons().toString());
    }

    // ------------------------------------------------------------------
    // Suitability — the soft objective
    // ------------------------------------------------------------------

    @Test
    void scoringIsDeterministic() {
        CandidateProfile p = candidate(Qualification.BACHELORS, "Delhi");
        Opportunity o = opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi");
        List<OpportunitySkill> req = List.of(skill("SQL", true), skill("Python", false));
        Weights w = Weights.defaultBalanced();
        SuitabilityService.PairScore a = svc.score(p, Set.of("SQL"), Set.of(Sector.DATA_ANALYTICS),
                1, o, req, w);
        SuitabilityService.PairScore b = svc.score(p, Set.of("SQL"), Set.of(Sector.DATA_ANALYTICS),
                1, o, req, w);
        assertEquals(a, b, "identical inputs must give identical scores");
    }

    @Test
    void overallScoreStaysWithinZeroToHundred() {
        CandidateProfile p = candidate(Qualification.BACHELORS, "Delhi");
        Opportunity o = opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Kerala");
        List<OpportunitySkill> req = List.of(skill("SQL", true), skill("Python", false), skill("Excel", false));
        Weights w = Weights.defaultBalanced();
        SuitabilityService.PairScore a = svc.score(p, Set.of("SQL", "Python", "Excel"), Set.of(), null, o, req, w);
        SuitabilityService.PairScore b = svc.score(p, Set.of(), Set.of(), 99, o, req, w);
        for (double v : List.of(a.overall(), b.overall())) {
            assertTrue(v >= 0 && v <= 100, "overall out of range: " + v);
        }
    }

    @Test
    void strongerSkillMatchScoresHigher() {
        CandidateProfile p = candidate(Qualification.BACHELORS, "Delhi");
        Opportunity o = opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi");
        List<OpportunitySkill> req = List.of(skill("SQL", true), skill("Python", false), skill("Tableau", false));
        Weights w = Weights.defaultBalanced();
        double high = svc.score(p, Set.of("SQL", "Python", "Tableau"), Set.of(Sector.DATA_ANALYTICS),
                1, o, req, w).overall();
        double low = svc.score(p, Set.of("SQL"), Set.of(Sector.DESIGN_MEDIA),
                5, o, req, w).overall();
        assertTrue(high > low, "more matched skills + aligned interest + rank-1 preference must score higher (" + high + " vs " + low + ")");
    }

    @Test
    void qualificationFactorPeaksAtExactMatchAndStaysHighWhenAbove() {
        Opportunity o = opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi");
        Weights w = Weights.defaultBalanced();
        double exact = svc.score(candidate(Qualification.BACHELORS, "Delhi"), Set.of(), Set.of(), null, o, List.of(), w).qualification();
        double above1 = svc.score(candidate(Qualification.POST_GRADUATE, "Delhi"), Set.of(), Set.of(), null, o, List.of(), w).qualification();
        double above2 = svc.score(candidate(Qualification.DOCTORAL, "Delhi"), Set.of(), Set.of(), null, o, List.of(), w).qualification();
        assertEquals(100.0, exact, "exact qualification match is the best fit");
        assertTrue(above1 >= 80 && above2 >= 70,
                "over-qualification keeps a high fit (" + above1 + ", " + above2 + ")");
    }

    @Test
    void defaultWeightsAlwaysSumTo100() {
        assertEquals(100.0, Weights.defaultBalanced().sum(), 1e-9,
                "PRAGATI default configurable weights must sum to 100");
    }

    // ------------------------------------------------------------------
    // Score math: contribution = fit × weight / 100, lost = weight − contribution
    // ------------------------------------------------------------------

    @Test
    void overallIsTheWeightedSumOfFactorContributions() {
        CandidateProfile p = candidate(Qualification.BACHELORS, "Delhi");
        Opportunity o = opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi");
        List<OpportunitySkill> req = List.of(skill("SQL", true), skill("Python", false), skill("Tableau", false));
        Weights w = Weights.defaultBalanced();
        SuitabilityService.PairScore a = svc.score(p, Set.of("SQL", "Python"), Set.of(Sector.DATA_ANALYTICS),
                2, o, req, w);
        // The analytics contract: contribution = fit × weight / 100. Factor fits are
        // displayed rounded to 0.1, so the recomputation from displayed values must
        // agree with the displayed overall within display rounding.
        double expected = (a.skills() * w.skills()
                + a.qualification() * w.qualification()
                + a.interest() * w.interest()
                + a.location() * w.location()
                + a.preference() * w.preference()
                + a.learning() * w.learning()
                + a.experience() * w.experience()) / 100.0;
        assertEquals(expected, a.overall(), 0.2,
                "overall must equal Σ(fit × weight / 100) — the exact math the analytics UI shows");
    }

    @Test
    void lostPointsAreWeightMinusContributionAndSumToOneHundredMinusOverall() {
        CandidateProfile p = candidate(Qualification.BACHELORS, "Kerala");
        Opportunity o = opportunity(Qualification.BACHELORS, OppStatus.ACTIVE, "Delhi");
        List<OpportunitySkill> req = List.of(skill("SQL", true), skill("Python", false), skill("Tableau", false));
        Weights w = Weights.defaultBalanced();
        SuitabilityService.PairScore a = svc.score(p, Set.of("SQL"), Set.of(), null, o, req, w);
        double[] fits = {a.skills(), a.qualification(), a.interest(), a.location(), a.preference(), a.learning(), a.experience()};
        double[] weights = {w.skills(), w.qualification(), w.interest(), w.location(), w.preference(), w.learning(), w.experience()};
        double totalLost = 0;
        for (int i = 0; i < fits.length; i++) {
            double lost = weights[i] - fits[i] * weights[i] / 100.0;
            assertTrue(lost >= -1e-9, "lost points can never be negative: " + lost);
            totalLost += lost;
        }
        assertEquals(100.0 - a.overall(), totalLost, 0.2,
                "remaining points (100 − overall) must equal the sum of per-factor lost points (display rounding allowed)");
    }

    @Test
    void strongPairScoresHighAndWeakPairScoresLow() {
        Opportunity o = opportunity(Qualification.POST_GRADUATE, OppStatus.ACTIVE, "Delhi");
        List<OpportunitySkill> req = List.of(skill("SQL", true));
        Weights w = Weights.defaultBalanced();

        CandidateProfile strong = candidate(Qualification.POST_GRADUATE, "Delhi");
        strong.candidateStatus = CandidateStatus.WORKING;
        strong.experienceYears = 5.0;
        SuitabilityService.PairScore high = svc.score(strong, Set.of("SQL", "Python", "Data Analysis", "Statistics"),
                Set.of(Sector.DATA_ANALYTICS), 1, o, req, w);

        CandidateProfile weak = candidate(Qualification.BACHELORS, "Kerala");
        weak.candidateStatus = CandidateStatus.STUDENT;
        weak.experienceYears = 0.0;
        SuitabilityService.PairScore low = svc.score(weak, Set.of(), Set.of(Sector.DESIGN_MEDIA), null, o, req, w);

        assertTrue(high.overall() >= 90, "a strong exact-match pair should score ≥ 90, got " + high.overall());
        assertTrue(low.overall() < high.overall() - 20, "a weak mismatched pair must score clearly lower (" + low.overall() + " vs " + high.overall() + ")");
    }
}
