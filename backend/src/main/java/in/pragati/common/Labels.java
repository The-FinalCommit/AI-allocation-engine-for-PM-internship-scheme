package in.pragati.common;

import java.util.List;
import java.util.Map;

/**
 * Centralized label mapping layer.
 * Every raw enum value shown in the UI must pass through this class so the
 * user never sees machine names like BACHELORS or INFEASIBLE.
 */
public final class Labels {

    private static final Map<String, String> QUALIFICATION = Map.of(
            "HIGHER_SECONDARY", "Higher Secondary",
            "BACHELORS", "Bachelor's Degree",
            "POST_GRADUATE", "Postgraduate",
            "DOCTORAL", "Doctoral");

    private static final Map<String, String> LOCATION_TYPE = Map.of(
            "RURAL", "Rural",
            "URBAN", "Urban",
            "SEMI_URBAN", "Semi-Urban");

    private static final Map<String, String> CANDIDATE_STATUS = Map.of(
            "STUDENT", "Student",
            "GRADUATE", "Graduate",
            "WORKING", "Working Professional");

    private static final Map<String, String> SECTOR = Map.of(
            "SOFTWARE_IT", "Software & IT",
            "DATA_ANALYTICS", "Data & Analytics",
            "FINANCE_BANKING", "Finance & Banking",
            "DESIGN_MEDIA", "Design & Media",
            "MARKETING_COMMUNICATION", "Marketing & Communication",
            "PUBLIC_ADMIN_POLICY", "Public Administration & Policy",
            "HEALTHCARE_BIOTECH", "Healthcare & Biotech",
            "MANUFACTURING_OPERATIONS", "Manufacturing & Operations",
            "RESEARCH_DEVELOPMENT", "Research & Development",
            "ENVIRONMENTAL_ENERGY", "Environmental & Energy");

    private static final Map<String, String> OPP_STATUS = Map.of(
            "ACTIVE", "Active",
            "PAUSED", "Paused",
            "CLOSED", "Closed");

    private static final Map<String, String> RUN_STATUS = Map.of(
            "QUEUED", "Queued",
            "RUNNING", "Running",
            "COMPLETED", "Completed",
            "INFEASIBLE", "Infeasible allocation",
            "FAILED", "Failed");

    private static final Map<String, String> SOLVER_STATUS = Map.of(
            "OPTIMAL", "Optimal allocation",
            "FEASIBLE", "Feasible allocation",
            "INFEASIBLE", "Infeasible allocation");

    private static final Map<String, String> SCENARIO = Map.of(
            "STANDARD_SHOWCASE", "Standard Showcase",
            "MICRO_CONFLICT", "Micro Conflict Proof",
            "HIGH_CONFLICT", "High Competition",
            "GEOGRAPHIC", "Geographic Impact",
            "FAIRNESS", "Fairness Study",
            "INFEASIBLE", "Infeasibility Demo",
            "BENCHMARK", "Performance Benchmark");

    private static final Map<String, String> AUDIT_ACTION = Map.ofEntries(
            Map.entry("LOGIN", "Signed in"),
            Map.entry("ALLOCATION_STARTED", "Allocation started"),
            Map.entry("ALLOCATION_COMPLETED", "Allocation completed"),
            Map.entry("ALLOCATION_INFEASIBLE", "Allocation infeasible"),
            Map.entry("ALLOCATION_FAILED", "Allocation failed"),
            Map.entry("SIMULATION_COMPLETED", "Simulation completed"),
            Map.entry("REALLOCATION_COMPLETED", "Reallocation completed"),
            Map.entry("POLICY_UPDATED", "Policy updated"),
            Map.entry("SCENARIO_LOADED", "Scenario loaded"),
            Map.entry("OPPORTUNITY_CREATED", "Opportunity created"),
            Map.entry("OPPORTUNITY_UPDATED", "Opportunity updated"),
            Map.entry("OPPORTUNITY_STATUS_CHANGED", "Opportunity status changed"),
            Map.entry("PROFILE_UPDATED", "Profile updated"),
            Map.entry("RESUME_UPLOADED", "Resume uploaded"),
            Map.entry("RESUME_REVIEWED", "Resume suggestions reviewed"),
            Map.entry("ADMIN_ACTION", "Administrative change"));


    private static final Map<String, String> ROLE = Map.of(
            "CANDIDATE", "Candidate",
            "PROVIDER", "Provider",
            "ADMIN", "Administrator");

    private Labels() { }

    public static String label(Object value) {
        if (value == null) return null;
        String name = value.toString();
        for (Map<String, String> map : List.of(QUALIFICATION, LOCATION_TYPE, CANDIDATE_STATUS, SECTOR,
                OPP_STATUS, RUN_STATUS, SOLVER_STATUS, SCENARIO, AUDIT_ACTION, ROLE)) {
            String label = map.get(name);
            if (label != null) return label;
        }
        return name;
    }
}
