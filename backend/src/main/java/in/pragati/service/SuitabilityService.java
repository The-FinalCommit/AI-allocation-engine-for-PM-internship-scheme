package in.pragati.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import in.pragati.common.Labels;
import in.pragati.common.Weights;
import in.pragati.domain.CandidateProfile;
import in.pragati.domain.Opportunity;
import in.pragati.domain.OpportunitySkill;
import in.pragati.domain.enums.CandidateState;
import in.pragati.domain.enums.OppStatus;
import in.pragati.domain.enums.Sector;

/**
 * Deterministic eligibility rules and suitability scoring.
 * Eligibility is a hard gate; suitability is a weighted 0-100 fit per factor.
 */
@Service
public class SuitabilityService {

    public record EligibilityResult(boolean eligible, List<String> reasons) { }

    public record EligiblePair(Long candidateId, Long opportunityId, PairScore score, Integer preferenceRank) { }

    public record PairScore(double skills, double qualification, double interest,
                            double location, double preference, double learning,
                            double experience, double overall) { }

    private static final Map<String, String> STATE_ZONE = Map.ofEntries(
            Map.entry("Uttar Pradesh", "NORTH"), Map.entry("Delhi", "NORTH"),
            Map.entry("Haryana", "NORTH"), Map.entry("Punjab", "NORTH"),
            Map.entry("Rajasthan", "NORTH"), Map.entry("Himachal Pradesh", "NORTH"),
            Map.entry("Jammu and Kashmir", "NORTH"), Map.entry("Chandigarh", "NORTH"),
            Map.entry("Madhya Pradesh", "CENTRAL"), Map.entry("Chhattisgarh", "CENTRAL"),
            Map.entry("Bihar", "EAST"), Map.entry("West Bengal", "EAST"),
            Map.entry("Jharkhand", "EAST"), Map.entry("Odisha", "EAST"),
            Map.entry("Maharashtra", "WEST"), Map.entry("Gujarat", "WEST"),
            Map.entry("Karnataka", "SOUTH"), Map.entry("Tamil Nadu", "SOUTH"),
            Map.entry("Kerala", "SOUTH"), Map.entry("Andhra Pradesh", "SOUTH"),
            Map.entry("Telangana", "SOUTH"), Map.entry("Lakshadweep", "SOUTH"),
            Map.entry("Puducherry", "SOUTH"), Map.entry("Andaman and Nicobar Islands", "EAST"),
            Map.entry("Dadra and Nagar Haveli", "WEST"), Map.entry("Daman and Diu", "WEST"),
            Map.entry("Assam", "NORTHEAST"), Map.entry("Meghalaya", "NORTHEAST"),
            Map.entry("Nagaland", "NORTHEAST"), Map.entry("Manipur", "NORTHEAST"),
            Map.entry("Tripura", "NORTHEAST"), Map.entry("Mizoram", "NORTHEAST"),
            Map.entry("Sikkim", "NORTHEAST"));

    private static final Map<Sector, Set<Sector>> RELATED = Map.ofEntries(
            Map.entry(Sector.SOFTWARE_IT, Set.of(Sector.DATA_ANALYTICS, Sector.RESEARCH_DEVELOPMENT)),
            Map.entry(Sector.DATA_ANALYTICS, Set.of(Sector.SOFTWARE_IT, Sector.RESEARCH_DEVELOPMENT, Sector.FINANCE_BANKING)),
            Map.entry(Sector.FINANCE_BANKING, Set.of(Sector.DATA_ANALYTICS, Sector.MANUFACTURING_OPERATIONS)),
            Map.entry(Sector.DESIGN_MEDIA, Set.of(Sector.MARKETING_COMMUNICATION)),
            Map.entry(Sector.MARKETING_COMMUNICATION, Set.of(Sector.DESIGN_MEDIA, Sector.DATA_ANALYTICS)),
            Map.entry(Sector.PUBLIC_ADMIN_POLICY, Set.of(Sector.RESEARCH_DEVELOPMENT, Sector.ENVIRONMENTAL_ENERGY)),
            Map.entry(Sector.HEALTHCARE_BIOTECH, Set.of(Sector.RESEARCH_DEVELOPMENT, Sector.ENVIRONMENTAL_ENERGY)),
            Map.entry(Sector.MANUFACTURING_OPERATIONS, Set.of(Sector.FINANCE_BANKING)),
            Map.entry(Sector.RESEARCH_DEVELOPMENT, Set.of(Sector.DATA_ANALYTICS, Sector.HEALTHCARE_BIOTECH)),
            Map.entry(Sector.ENVIRONMENTAL_ENERGY, Set.of(Sector.PUBLIC_ADMIN_POLICY, Sector.HEALTHCARE_BIOTECH)));

    public EligibilityResult check(CandidateProfile p, Set<String> skills,
                                   Opportunity o, List<OpportunitySkill> requirements) {
        List<String> reasons = new ArrayList<>();
        if (o.status != OppStatus.ACTIVE) {
            reasons.add("This opportunity is not currently open.");
        }
        if (p.candidateState != CandidateState.ACTIVE) {
            reasons.add("Your participation has been withdrawn.");
        }
        if (p.qualification == null || o.minQualification.rank > p.qualification.rank) {
            reasons.add("Qualification below the required level (" + Labels.label(o.minQualification) + " required).");
        }
        for (OpportunitySkill s : requirements) {
            if (s.mandatory && !skills.contains(s.skill)) {
                reasons.add("Required " + s.skill + " skill is missing.");
            }
        }
        return new EligibilityResult(reasons.isEmpty(), reasons);
    }

    public PairScore score(CandidateProfile p, Set<String> skills, Set<Sector> interestSectors,
                           Integer preferenceRank, Opportunity o,
                           List<OpportunitySkill> requirements, Weights w) {
        double skillsFit = skillsFit(skills, requirements);
        double qualificationFit = qualificationFit(p, o);
        double interestFit = interestFit(interestSectors, o.sector);
        double locationFit = locationFit(p.state, o.state);
        double preferenceFit = preferenceFit(preferenceRank);
        double learningFit = learningFit(p, skills.size());
        double experienceFit = experienceFit(p);
        double overall = (w.skills() * skillsFit
                + w.qualification() * qualificationFit
                + w.interest() * interestFit
                + w.location() * locationFit
                + w.preference() * preferenceFit
                + w.learning() * learningFit
                + w.experience() * experienceFit) / 100.0;
        return new PairScore(r1(skillsFit), r1(qualificationFit), r1(interestFit),
                r1(locationFit), r1(preferenceFit), r1(learningFit), r1(experienceFit), r1(overall));
    }

    private double skillsFit(Set<String> skills, List<OpportunitySkill> requirements) {
        int nice = 0, niceTotal = 0;
        for (OpportunitySkill s : requirements) {
            if (!s.mandatory) {
                niceTotal++;
                if (skills.contains(s.skill)) nice++;
            }
        }
        double fit = 75.0;
        if (niceTotal > 0) fit += 25.0 * (nice / (double) niceTotal);
        return fit;
    }

    private double qualificationFit(CandidateProfile p, Opportunity o) {
        if (p.qualification == null) return 0;
        int diff = p.qualification.rank - o.minQualification.rank;
        return diff == 0 ? 100 : diff == 1 ? 85 : 70;
    }

    private double interestFit(Set<Sector> interests, Sector sector) {
        if (interests.contains(sector)) return 100;
        Set<Sector> related = RELATED.getOrDefault(sector, Set.of());
        for (Sector s : interests) {
            if (related.contains(s)) return 65;
        }
        return 35;
    }

    private double locationFit(String candidateState, String oppState) {
        if (candidateState == null || oppState == null) return 40;
        if (candidateState.equalsIgnoreCase(oppState)) return 100;
        String cz = STATE_ZONE.get(candidateState);
        String oz = STATE_ZONE.get(oppState);
        return cz != null && cz.equals(oz) ? 70 : 40;
    }

    private double preferenceFit(Integer rank) {
        if (rank == null) return 20;
        return switch (rank) {
            case 1 -> 100;
            case 2 -> 85;
            case 3 -> 70;
            case 4 -> 55;
            default -> 40;
        };
    }

    private double learningFit(CandidateProfile p, int skillCount) {
        double base = switch (p.qualification == null ? in.pragati.domain.enums.Qualification.HIGHER_SECONDARY : p.qualification) {
            case HIGHER_SECONDARY -> 50;
            case BACHELORS -> 65;
            case POST_GRADUATE -> 80;
            case DOCTORAL -> 90;
        };
        return Math.min(100, base + Math.min(4, skillCount) * 2.5);
    }

    private double experienceFit(CandidateProfile p) {
        double years = p.experienceYears == null ? 0 : p.experienceYears;
        double fit = 25 + years * 18;
        if (p.candidateStatus == in.pragati.domain.enums.CandidateStatus.WORKING) fit += 20;
        return Math.min(100, fit);
    }

    private double r1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
