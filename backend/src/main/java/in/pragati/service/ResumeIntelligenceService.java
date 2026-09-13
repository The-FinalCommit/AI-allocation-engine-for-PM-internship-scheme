package in.pragati.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import in.pragati.domain.enums.CandidateStatus;
import in.pragati.domain.enums.Qualification;
import in.pragati.domain.enums.Sector;

/**
 * Deterministic resume → profile suggestion extraction.
 *
 * Honesty rules:
 *  - only fields with visible evidence in the resume text are suggested;
 *  - nothing is invented (no DOB, no guessed district, no assumed status);
 *  - every suggestion carries its confidence and the evidence line;
 *  - suggestions are NEVER persisted to the profile — the candidate reviews
 *    and confirms each one (see apply-suggestions).
 */
@Service
public class ResumeIntelligenceService {

    public record FieldSuggestion(String field, String value, String confidence, String evidence) { }
    public record ResumeSuggestions(List<FieldSuggestion> fields, List<String> skills, List<String> interests) { }

    public static final List<String> PROFILE_FIELDS = List.of(
            "fullName", "phone", "qualification", "candidateStatus", "state", "district", "experienceYears", "bio");

    private static final List<String> STATES = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chandigarh", "Chhattisgarh",
            "Delhi", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand",
            "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram",
            "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal");

    /** Districts known to the demo dataset, per state (used to validate a district suggestion). */
    private static final Map<String, List<String>> DISTRICTS = Map.ofEntries(
            Map.entry("Uttar Pradesh", List.of("Lucknow", "Kanpur", "Varanasi", "Agra", "Prayagraj")),
            Map.entry("Maharashtra", List.of("Mumbai", "Pune", "Nagpur", "Nashik", "Thane")),
            Map.entry("Delhi", List.of("New Delhi", "South Delhi", "Dwarka", "Rohini", "East Delhi", "North Delhi")),
            Map.entry("Karnataka", List.of("Bengaluru", "Mysuru", "Mangaluru", "Hubballi", "Belagavi")),
            Map.entry("Tamil Nadu", List.of("Chennai", "Coimbatore", "Madurai", "Salem")),
            Map.entry("West Bengal", List.of("Kolkata", "Howrah", "Durgapur", "Asansol")),
            Map.entry("Rajasthan", List.of("Jaipur", "Jodhpur", "Udaipur", "Ajmer")),
            Map.entry("Gujarat", List.of("Ahmedabad", "Surat", "Vadodara", "Rajkot")),
            Map.entry("Telangana", List.of("Hyderabad", "Warangal", "Nizamabad", "Karimnagar")),
            Map.entry("Andhra Pradesh", List.of("Visakhapatnam", "Vijayawada", "Guntur", "Kurnool")),
            Map.entry("Bihar", List.of("Patna", "Gaya", "Muzaffarpur", "Ranchi")),
            Map.entry("Kerala", List.of("Kochi", "Thiruvananthapuram", "Kozhikode", "Kottayam")),
            Map.entry("Punjab", List.of("Ludhiana", "Amritsar", "Jalandhar", "Patiala")),
            Map.entry("Haryana", List.of("Gurugram", "Faridabad", "Hisar", "Panipat")),
            Map.entry("Madhya Pradesh", List.of("Bhopal", "Indore", "Gwalior", "Jabalpur")),
            Map.entry("Odisha", List.of("Bhubaneswar", "Cuttack", "Rourkela", "Berhampur")),
            Map.entry("Jharkhand", List.of("Ranchi", "Jamshedpur", "Dhanbad")),
            Map.entry("Chhattisgarh", List.of("Raipur", "Bilaspur", "Durg")));

    private static final Map<Sector, List<String>> SECTOR_KEYWORDS = new LinkedHashMap<>();
    static {
        SECTOR_KEYWORDS.put(Sector.SOFTWARE_IT, List.of("software", "programming", "computer science", "coding", "app development"));
        SECTOR_KEYWORDS.put(Sector.DATA_ANALYTICS, List.of("data analysis", "data science", "analytics", "machine learning", "statistics"));
        SECTOR_KEYWORDS.put(Sector.FINANCE_BANKING, List.of("finance", "banking", "accounting", "investment", "financial analysis"));
        SECTOR_KEYWORDS.put(Sector.DESIGN_MEDIA, List.of("graphic design", "ui/ux", "visual design", "video editing", "creative design"));
        SECTOR_KEYWORDS.put(Sector.MARKETING_COMMUNICATION, List.of("marketing", "seo", "social media", "content writing", "branding"));
        SECTOR_KEYWORDS.put(Sector.PUBLIC_ADMIN_POLICY, List.of("public policy", "policy research", "government", "public administration", "governance"));
        SECTOR_KEYWORDS.put(Sector.HEALTHCARE_BIOTECH, List.of("healthcare", "biotech", "medical", "pharma", "health data"));
        SECTOR_KEYWORDS.put(Sector.MANUFACTURING_OPERATIONS, List.of("manufacturing", "supply chain", "operations management", "production planning"));
        SECTOR_KEYWORDS.put(Sector.RESEARCH_DEVELOPMENT, List.of("research", "r&d", "scientific research"));
        SECTOR_KEYWORDS.put(Sector.ENVIRONMENTAL_ENERGY, List.of("environment", "energy", "sustainability", "renewable", "climate"));
    }

    public ResumeSuggestions extract(String text) {
        if (text == null || text.isBlank()) return new ResumeSuggestions(List.of(), List.of(), List.of());
        List<String> lines = splitLines(text);
        List<FieldSuggestion> fields = new ArrayList<>();

        FieldSuggestion name = extractName(lines);
        if (name != null) fields.add(name);
        FieldSuggestion phone = extractPhone(lines);
        if (phone != null) fields.add(phone);
        FieldSuggestion qual = extractQualification(lines);
        if (qual != null) fields.add(qual);
        FieldSuggestion status = extractStatus(lines);
        if (status != null) fields.add(status);
        FieldSuggestion state = extractState(lines);
        if (state != null) fields.add(state);
        FieldSuggestion district = extractDistrict(lines, state);
        if (district != null) fields.add(district);
        FieldSuggestion exp = extractExperience(lines);
        if (exp != null) fields.add(exp);
        FieldSuggestion bio = extractBio(lines);
        if (bio != null) fields.add(bio);

        List<String> interests = extractInterests(lines);
        return new ResumeSuggestions(fields, List.of(), interests);
        // skills are filled by the caller (AI-assisted lexicon extraction, same pipeline as before)
    }

    // ------------------------------------------------------------------

    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        for (String l : text.split("\\r?\\n")) {
            String t = l.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private FieldSuggestion extractName(List<String> lines) {
        Pattern title = Pattern.compile("(?i)^(resume|curriculum vitae|cv|portfolio|cover letter)\\b");
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            String l = lines.get(i);
            if (title.matcher(l).find()) continue;
            if (l.length() < 3 || l.length() > 60) continue;
            if (!l.matches("^[A-Za-z][A-Za-z .'-]+$")) continue;
            int words = l.split("\\s+").length;
            if (words < 2 || words > 4) continue;
            if (l.contains("@") || l.matches(".*\\d.*")) continue;
            if (l.matches("(?i).*(phone|email|address|date|objective|summary).*")) continue;
            return new FieldSuggestion("fullName", l, i == 0 ? "High" : "Medium",
                    "Resume line " + (i + 1) + ": “" + clip(l, 60) + "”");
        }
        return null;
    }

    private FieldSuggestion extractPhone(List<String> lines) {
        Pattern phone = Pattern.compile("(?:(?:\\+|00)?91[\\s-]?)?(?:\\(\\d{2,4}\\)[\\s-]?)?\\b\\d{5}[\\s-]?\\d{5}\\b");
        Pattern contact = Pattern.compile("(?i)\\b(phone|mobile|contact|tel)\\b");
        for (int i = 0; i < Math.min(40, lines.size()); i++) {
            String l = lines.get(i);
            Matcher m = phone.matcher(l);
            if (m.find()) {
                String digits = m.group().replaceAll("[\\s-()\\+]", "");
                if (digits.length() > 10 && digits.startsWith("91")) digits = digits.substring(2);
                if (digits.length() < 10 || digits.length() > 12) continue;
                boolean labelled = contact.matcher(l).find();
                return new FieldSuggestion("phone", "+" + digits, labelled ? "High" : "Medium",
                        (labelled ? "Contact line: " : "Line ") + (i + 1) + ": “" + clip(l, 70) + "”");
            }
        }
        return null;
    }

    private FieldSuggestion extractQualification(List<String> lines) {
        record Rule(Qualification q, Pattern p) { }
        List<Rule> rules = List.of(
                new Rule(Qualification.DOCTORAL, Pattern.compile("(?i)\\bph\\.?d\\b|\\bdoctorate\\b|\\bdoctor of\\b")),
                new Rule(Qualification.POST_GRADUATE, Pattern.compile("(?i)\\bmba\\b|\\bm\\.?tech\\b|\\bmca\\b|\\bmsc\\b|\\bm\\.?sc\\b|\\bm\\.?e\\b(?!\\w)|master'?s? degree|post\\s?graduate")),
                new Rule(Qualification.BACHELORS, Pattern.compile("(?i)bachelor|\\bb\\.?tech\\b|\\bbe\\b(?!\\w)|\\bbba\\b|\\bbca\\b|\\bbcom\\b|\\bb\\.?sc\\b|undergraduate degree")),
                new Rule(Qualification.HIGHER_SECONDARY, Pattern.compile("(?i)higher secondary|\\b12th\\b|\\bhsc\\b|intermediate|\\+2\\b|junior college")));
        for (Rule rule : rules) {
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = rule.p().matcher(lines.get(i));
                if (m.find()) {
                    boolean inEducation = isNearHeading(lines, i, "education|academic|qualif|degree");
                    return new FieldSuggestion("qualification", rule.q().name(),
                            inEducation ? "High" : "Medium",
                            "Education evidence, line " + (i + 1) + ": “" + clip(lines.get(i), 80) + "”");
                }
            }
        }
        return null;
    }

    private FieldSuggestion extractStatus(List<String> lines) {
        Pattern working = Pattern.compile("(?i)currently working|currently employed|\\bwork experience\\b.*\\d+\\s*years?");
        Pattern student = Pattern.compile("(?i)final year|pursuing|currently a student|\\bstudent\\b");
        Pattern grad = Pattern.compile("(?i)recent graduate|passed out|\\d{4} graduate|\\bgraduate\\b");
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (working.matcher(l).find()) {
                return new FieldSuggestion("candidateStatus", CandidateStatus.WORKING.name(), "Medium",
                        "Line " + (i + 1) + ": “" + clip(l, 80) + "”");
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (student.matcher(l).find()) {
                return new FieldSuggestion("candidateStatus", CandidateStatus.STUDENT.name(), "Medium",
                        "Line " + (i + 1) + ": “" + clip(l, 80) + "”");
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (grad.matcher(l).find()) {
                return new FieldSuggestion("candidateStatus", CandidateStatus.GRADUATE.name(), "Medium",
                        "Line " + (i + 1) + ": “" + clip(l, 80) + "”");
            }
        }
        return null;
    }

    private FieldSuggestion extractState(List<String> lines) {
        int limit = Math.min(lines.size(), Math.max(6, (int) (lines.size() * 0.4)));
        List<String> lower = lines.stream().limit(limit).map(s -> s.toLowerCase()).toList();
        for (int i = 0; i < limit; i++) {
            String l = lower.get(i);
            for (String st : STATES) {
                if (l.contains(st.toLowerCase())) {
                    boolean labelled = l.matches(".*(from|address|location|based in|city).*");
                    return new FieldSuggestion("state", st, labelled ? "High" : "Medium",
                            "Line " + (i + 1) + ": “" + clip(lines.get(i), 80) + "”");
                }
            }
        }
        return null;
    }

    private FieldSuggestion extractDistrict(List<String> lines, FieldSuggestion state) {
        int limit = Math.min(lines.size(), Math.max(6, (int) (lines.size() * 0.4)));
        for (int i = 0; i < limit; i++) {
            String l = lines.get(i);
            String lower = l.toLowerCase();
            for (Map.Entry<String, List<String>> e : DISTRICTS.entrySet()) {
                for (String d : e.getValue()) {
                    if (lower.contains(d.toLowerCase())) {
                        // Must be plausibly tied to a state: same line/region mentions that state,
                        // or the state was found elsewhere in the resume header region.
                        boolean tied = l.toLowerCase().contains(e.getKey().toLowerCase());
                        if (!tied && state != null) tied = e.getKey().equalsIgnoreCase(state.value());
                        if (!tied) continue;
                        return new FieldSuggestion("district", d, "Medium",
                                "Line " + (i + 1) + ": “" + clip(l, 80) + "”");
                    }
                }
            }
        }
        return null;
    }

    private FieldSuggestion extractExperience(List<String> lines) {
        Pattern p1 = Pattern.compile("(?i)\\bexperience\\b[\\s:.-]*(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*years?");
        Pattern p2 = Pattern.compile("(?i)(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*years?\\s*(?:of\\s*)?experience");
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            Matcher m = p1.matcher(l);
            if (!m.find()) m = p2.matcher(l);
            if (m.find()) {
                double years;
                try { years = Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { continue; }
                if (years < 0 || years > 25) continue;
                return new FieldSuggestion("experienceYears", String.valueOf(years),
                        l.toLowerCase().contains("total") ? "High" : "Medium",
                        "Line " + (i + 1) + ": “" + clip(l, 80) + "”");
            }
        }
        return null;
    }

    private FieldSuggestion extractBio(List<String> lines) {
        Pattern heading = Pattern.compile("(?i)^(about me|about|profile summary|professional summary|career objective|objective|summary)$");
        for (int i = 0; i < lines.size(); i++) {
            if (!heading.matcher(lines.get(i)).find()) continue;
            StringBuilder sb = new StringBuilder();
            for (int j = i + 1; j < Math.min(i + 5, lines.size()) && sb.length() < 280; j++) {
                if (heading.matcher(lines.get(j)).find()) break;
                if (lines.get(j).length() < 20) continue;
                sb.append(lines.get(j)).append(" ");
            }
            String bio = sb.toString().trim();
            if (bio.length() < 40) continue;
            if (bio.length() > 280) bio = bio.substring(0, 277).trim() + "…";
            return new FieldSuggestion("bio", bio, "Medium",
                    "“" + clip(lines.get(i), 40) + "” section, line " + (i + 1));
        }
        return null;
    }

    private List<String> extractInterests(List<String> lines) {
        Map<Sector, Integer> hits = new HashMap<>();
        for (String l : lines) {
            String lower = l.toLowerCase();
            for (Map.Entry<Sector, List<String>> e : SECTOR_KEYWORDS.entrySet()) {
                for (String kw : e.getValue()) {
                    if (lower.contains(kw)) { hits.merge(e.getKey(), 1, Integer::sum); break; }
                }
            }
        }
        return hits.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<Sector, Integer>::getValue).reversed())
                .limit(3)
                .map(e -> e.getKey().name())
                .toList();
    }

    private static boolean isNearHeading(List<String> lines, int idx, String headingPattern) {
        Pattern p = Pattern.compile("(?i)^(education|academic|qualification|qualifications|degree)s?\\b");
        for (int j = Math.max(0, idx - 6); j <= idx; j++) {
            if (p.matcher(lines.get(j)).find() || lines.get(j).matches(".*" + headingPattern + ".*")) {
                return true;
            }
        }
        return false;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
