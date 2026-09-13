package in.pragati.seed;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.Collections;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.pragati.domain.*;
import in.pragati.domain.enums.*;
import in.pragati.repo.*;
import in.pragati.service.SkillTaxonomy;

/**
 * Deterministic synthetic dataset generation for all PRAGATI scenarios.
 * Every scenario is reproducible: same seed, same data, every time.
 * Generation order guarantees referential integrity: candidates and
 * profiles are persisted first, then opportunities, then preferences.
 */
@Service
public class ScenarioSeeder {

    private final UserRepository users;
    private final CandidateProfileRepository profiles;
    private final CandidateSkillRepository skills;
    private final CandidateInterestRepository interests;
    private final CandidatePreferenceRepository preferences;
    private final ProviderRepository providers;
    private final OpportunityRepository opportunities;
    private final OpportunitySkillRepository oppSkills;
    private final DatasetSnapshotRepository snapshots;
    private final AssignmentRepository assignments;
    private final in.pragati.repo.GroupMetricRepository groupMetrics;
    private final in.pragati.repo.GeoMetricRepository geoMetrics;
    private final in.pragati.repo.ConflictMetricRepository conflictMetrics;
    private final SimulationRunRepository simulations;
    private final AllocationRunRepository runs;
    private final ResumeFileRepository resumes;

    /** The demo candidate profile is created at most once per scenario (unique user_id). */
    private boolean demoAssigned = false;

    public ScenarioSeeder(UserRepository users, CandidateProfileRepository profiles,
                          CandidateSkillRepository skills, CandidateInterestRepository interests,
                          CandidatePreferenceRepository preferences, ProviderRepository providers,
                          OpportunityRepository opportunities, OpportunitySkillRepository oppSkills,
                          DatasetSnapshotRepository snapshots, AssignmentRepository assignments,
                          in.pragati.repo.GroupMetricRepository groupMetrics,
                          in.pragati.repo.GeoMetricRepository geoMetrics,
                          in.pragati.repo.ConflictMetricRepository conflictMetrics,
                          SimulationRunRepository simulations, AllocationRunRepository runs,
                          ResumeFileRepository resumes) {
        this.users = users; this.profiles = profiles; this.skills = skills;
        this.interests = interests; this.preferences = preferences; this.providers = providers;
        this.opportunities = opportunities; this.oppSkills = oppSkills; this.snapshots = snapshots;
        this.assignments = assignments; this.groupMetrics = groupMetrics;
        this.geoMetrics = geoMetrics; this.conflictMetrics = conflictMetrics;
        this.simulations = simulations; this.runs = runs; this.resumes = resumes;
    }

    // ------------------------------------------------------------------
    // Deterministic constants
    // ------------------------------------------------------------------

    private static final Map<ScenarioKey, Long> SEEDS = new LinkedHashMap<>();
    static {
        SEEDS.put(ScenarioKey.STANDARD_SHOWCASE, 25033001L);
        SEEDS.put(ScenarioKey.MICRO_CONFLICT, 25033002L);
        SEEDS.put(ScenarioKey.HIGH_CONFLICT, 25033003L);
        SEEDS.put(ScenarioKey.GEOGRAPHIC, 25033004L);
        SEEDS.put(ScenarioKey.FAIRNESS, 25033005L);
        SEEDS.put(ScenarioKey.INFEASIBLE, 25033006L);
        SEEDS.put(ScenarioKey.BENCHMARK, 25033007L);
    }

    private static final String[] FIRST_NAMES = {
        "Aarav", "Ananya", "Arjun", "Aisha", "Aditya", "Anika", "Rohan", "Riya",
        "Vivaan", "Diya", "Reyansh", "Ishaan", "Kiara", "Vedant", "Sarthak", "Myra",
        "Prajwal", "Neha", "Harsh", "Tanvi", "Kabir", "Sana", "Advik", "Pooja",
        "Dhruv", "Anshika", "Yash", "Shruti", "Aarush", "Malvika", "Krish", "Navya",
        "Shaurya", "Ritu", "Pranav", "Simran", "Nikhil", "Aishwarya", "Gaurav", "Meera",
        "Sanjay", "Kavya", "Rakesh", "Divya", "Manish", "Lakshmi", "Vikram", "Sunita"
    };

    private static final String[] LAST_NAMES = {
        "Sharma", "Verma", "Patel", "Reddy", "Nair", "Iyer", "Gupta", "Singh",
        "Kumar", "Das", "Roy", "Chatterjee", "Mukherjee", "Joshi", "Menon", "Pillai",
        "Rao", "Kulkarni", "Deshmukh", "Shetty", "Gowda", "Hegde", "Pandey", "Mishra",
        "Tiwari", "Yadav", "Meena", "Akhtar", "Sheikh", "Khan", "Ansari", "Qureshi",
        "Bhatt", "Shah", "Trivedi", "Vyas", "Jaiswal", "Srivastava", "Dubey", "Agarwal"
    };

    private record StateInfo(String name, double weight, String[] districts) { }

    private static final List<StateInfo> STANDARD_STATES = List.of(
        new StateInfo("Uttar Pradesh", 15, new String[]{"Lucknow", "Kanpur", "Varanasi", "Agra"}),
        new StateInfo("Maharashtra", 12, new String[]{"Mumbai", "Pune", "Nagpur", "Nashik"}),
        new StateInfo("Delhi", 10, new String[]{"New Delhi", "South Delhi", "Dwarka", "Rohini"}),
        new StateInfo("Karnataka", 8, new String[]{"Bengaluru", "Mysuru", "Mangaluru", "Hubballi"}),
        new StateInfo("Tamil Nadu", 8, new String[]{"Chennai", "Coimbatore", "Madurai", "Salem"}),
        new StateInfo("West Bengal", 7, new String[]{"Kolkata", "Howrah", "Durgapur", "Asansol"}),
        new StateInfo("Rajasthan", 6, new String[]{"Jaipur", "Jodhpur", "Udaipur", "Ajmer"}),
        new StateInfo("Gujarat", 6, new String[]{"Ahmedabad", "Surat", "Vadodara", "Rajkot"}),
        new StateInfo("Telangana", 6, new String[]{"Hyderabad", "Warangal", "Nizamabad", "Karimnagar"}),
        new StateInfo("Andhra Pradesh", 5, new String[]{"Visakhapatnam", "Vijayawada", "Guntur", "Kurnool"}),
        new StateInfo("Bihar", 4, new String[]{"Patna", "Gaya", "Muzaffarpur", "Ranchi"}),
        new StateInfo("Kerala", 3, new String[]{"Kochi", "Thiruvananthapuram", "Kozhikode", "Kottayam"}),
        new StateInfo("Punjab", 2, new String[]{"Ludhiana", "Amritsar", "Jalandhar", "Patiala"}),
        new StateInfo("Haryana", 2, new String[]{"Gurugram", "Faridabad", "Hisar", "Panipat"}),
        new StateInfo("Madhya Pradesh", 2, new String[]{"Bhopal", "Indore", "Gwalior", "Jabalpur"}),
        new StateInfo("Odisha", 2, new String[]{"Bhubaneswar", "Cuttack", "Rourkela", "Berhampur"})
    );

    private static final Map<Sector, String[]> SECTOR_SKILLS = Map.of(
        Sector.SOFTWARE_IT, new String[]{"Python", "Java", "C++", "Web Development", "React", "Node.js", "JavaScript", "Git", "Cloud Computing", "DevOps", "Cybersecurity"},
        Sector.DATA_ANALYTICS, new String[]{"Data Analysis", "SQL", "Machine Learning", "Excel", "Statistics", "Power BI", "Tableau", "NLP", "Deep Learning"},
        Sector.FINANCE_BANKING, new String[]{"Finance", "Accounting", "Excel", "Budgeting", "Statistics", "Data Analysis"},
        Sector.DESIGN_MEDIA, new String[]{"Graphic Design", "UI/UX Design", "Content Writing", "Presentation"},
        Sector.MARKETING_COMMUNICATION, new String[]{"Digital Marketing", "Social Media", "SEO", "Content Writing", "Communication", "Presentation"},
        Sector.PUBLIC_ADMIN_POLICY, new String[]{"Public Administration", "Policy Analysis", "Research", "Communication", "Statistics"},
        Sector.HEALTHCARE_BIOTECH, new String[]{"Healthcare Data", "Research", "Statistics", "Environmental Science"},
        Sector.MANUFACTURING_OPERATIONS, new String[]{"Operations", "Supply Chain", "Excel", "Project Management"},
        Sector.RESEARCH_DEVELOPMENT, new String[]{"Research", "Statistics", "Data Analysis", "C++", "Machine Learning"},
        Sector.ENVIRONMENTAL_ENERGY, new String[]{"Environmental Science", "Research", "Data Analysis", "Policy Analysis"}
    );

    private static final Map<Sector, List<String>> SECTOR_TITLES = Map.of(
        Sector.SOFTWARE_IT, List.of("Software Engineering Intern", "Full-Stack Development Intern", "Cloud & DevOps Intern"),
        Sector.DATA_ANALYTICS, List.of("Data Analytics Intern", "Business Intelligence Intern", "Machine Learning Intern"),
        Sector.FINANCE_BANKING, List.of("Finance Intern", "Financial Analyst Intern", "Investment Operations Intern"),
        Sector.DESIGN_MEDIA, List.of("Product Design Intern", "Visual Communication Intern"),
        Sector.MARKETING_COMMUNICATION, List.of("Digital Marketing Intern", "Brand Communication Intern"),
        Sector.PUBLIC_ADMIN_POLICY, List.of("Public Policy Intern", "Program Management Intern", "Social Impact Research Intern"),
        Sector.HEALTHCARE_BIOTECH, List.of("Health Data Intern", "Biotechnology Research Intern"),
        Sector.MANUFACTURING_OPERATIONS, List.of("Operations Intern", "Supply Chain Intern"),
        Sector.RESEARCH_DEVELOPMENT, List.of("Research & Development Intern", "Quantitative Research Intern"),
        Sector.ENVIRONMENTAL_ENERGY, List.of("Sustainability Research Intern", "Energy Systems Intern")
    );

    // ------------------------------------------------------------------
    // Field-depth pools: Full Stack Development + Data Analytics — the two
    // fields candidates and reviewers explore most, given real breadth of
    // titles, skill combinations and locations (see addFieldDepthOpportunities).
    // ------------------------------------------------------------------

    private static final String[] FULL_STACK_TITLES = {
        "Full Stack Development Intern", "Frontend Development Intern", "Backend Development Intern",
        "MERN Stack Development Intern", "Full Stack Engineering Intern", "Web Application Development Intern",
    };
    private static final String[][] FULL_STACK_MANDATORY = {
        {"React", "JavaScript"}, {"Node.js", "JavaScript"}, {"Java", "Web Development"},
        {"Python", "Web Development"}, {"React", "Node.js"}, {"JavaScript", "Git"},
    };
    private static final String[][] FULL_STACK_OPTIONAL = {
        {"Cloud Computing", "Git"}, {"DevOps", "Cloud Computing"}, {"React", "SQL"},
        {"Node.js", "Cybersecurity"}, {"Git", "Cloud Computing"}, {"DevOps", "React"},
    };

    private static final String[] DATA_ANALYTICS_TITLES = {
        "Data Analytics Intern", "Business Intelligence Intern", "Machine Learning Intern",
        "Data Science Intern", "Data Platform Intern", "Quantitative Analytics Intern",
    };
    private static final String[][] DATA_ANALYTICS_MANDATORY = {
        {"Data Analysis"}, {"SQL", "Data Analysis"}, {"Machine Learning"},
        {"Statistics", "Data Analysis"}, {"SQL"}, {"Excel", "Data Analysis"},
    };
    private static final String[][] DATA_ANALYTICS_OPTIONAL = {
        {"SQL", "Power BI"}, {"Tableau", "Excel"}, {"Deep Learning", "Statistics"},
        {"NLP", "Machine Learning"}, {"Power BI", "Statistics"}, {"Tableau", "NLP"},
    };

    private static final String[] PROVIDER_ORGS = {
        "Bharat Analytics", "Utkarsh FinServ", "Pragya Design Studio", "Vidya Public Foundation",
        "Aarogya Biotech", "Shakti Manufacturing", "Drishti Research Labs", "GreenGrid Energy", "Sanskriti Media"
    };

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Deletes all scenario data (keeps demo users, providers and snapshots) and reseeds. */
    @Transactional
    public void reset() {
        assignments.deleteAll();
        groupMetrics.deleteAll();
        geoMetrics.deleteAll();
        conflictMetrics.deleteAll();
        simulations.deleteAll();
        runs.deleteAll();
        resumes.deleteAll();
        preferences.deleteAll();
        skills.deleteAll();
        interests.deleteAll();
        profiles.deleteAll();
        for (User u : users.findAll()) {
            if (u.role == Role.CANDIDATE && !UserSeeder.CANDIDATE_EMAIL.equals(u.email)) {
                users.delete(u);
            }
        }
        oppSkills.deleteAll();
        opportunities.deleteAll();
        for (Provider p : providers.findAll()) {
            if (p.userId == null) providers.delete(p);
        }
    }

    /** Seeds one scenario deterministically and records the dataset snapshot. */
    @Transactional
    public DatasetSnapshot seed(ScenarioKey key) {
        Random rng = new Random(SEEDS.get(key));
        int[] counters = new int[]{0, 0, 0};
        demoAssigned = false;
        ensureProviders(rng);
        switch (key) {
            case STANDARD_SHOWCASE -> seedShowcase(rng, counters);
            case MICRO_CONFLICT -> seedMicro(rng, counters);
            case HIGH_CONFLICT -> seedHighConflict(rng, counters);
            case GEOGRAPHIC -> seedGeographic(rng, counters);
            case FAIRNESS -> seedFairness(rng, counters);
            case INFEASIBLE -> seedInfeasible(rng, counters);
            case BENCHMARK -> seedBenchmark(rng, counters);
        }
        validateDataset(key);
        DatasetSnapshot s = new DatasetSnapshot();
        s.scenario = key;
        s.version = (int) snapshots.countByScenario(key) + 1;
        s.fingerprint = fingerprint(key, SEEDS.get(key), counters[0], counters[1], counters[2]);
        s.seed = SEEDS.get(key);
        s.candidateCount = counters[0];
        s.opportunityCount = counters[1];
        s.seatCount = counters[2];
        return snapshots.save(s);
    }

    // ------------------------------------------------------------------
    // Scenarios
    // ------------------------------------------------------------------

    /**
     * The main 500+ candidate showcase.
     *
     * Deliberately engineered bottlenecks: the "hot" internships are common,
     * easy-to-qualify-for roles with very limited seats, while a large share
     * of the candidate pool holds exactly the required skill — producing
     * real 5:1 to 9:1 eligible-candidate-to-seat competition. The remaining
     * opportunities span all sectors, states and demand levels (including
     * some with excess capacity). Every number below is produced by the
     * real generator; nothing is faked.
     */
    private void seedShowcase(Random rng, int[] c) {
        Provider demoProvider = providers.findAll().get(0);
        List<Opportunity> opps = new ArrayList<>();

        // Hot internships: common skill, limited seats, high competition.
        opps.add(createOpportunity(rng, demoProvider.id, "Java Backend Engineering Intern",
                Sector.SOFTWARE_IT, "Delhi", "New Delhi", 12, 3, Qualification.BACHELORS,
                List.of("Java"), List.of("SQL", "Cloud Computing"),
                "Design, build and test REST APIs for public services alongside the platform team.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 1), "Data Analytics Intern",
                Sector.DATA_ANALYTICS, "Karnataka", "Bengaluru", 10, 3, Qualification.BACHELORS,
                List.of("Data Analysis"), List.of("SQL", "Power BI"),
                "Clean real datasets, build dashboards and turn numbers into decisions.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 2), "Financial Analysis Intern",
                Sector.FINANCE_BANKING, "Maharashtra", "Mumbai", 10, 3, Qualification.BACHELORS,
                List.of("Finance"), List.of("Excel", "Statistics"),
                "Support forecasting, reconciliation and budget analysis for a public financial programme.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 3), "Public Policy Research Intern",
                Sector.PUBLIC_ADMIN_POLICY, "Delhi", "New Delhi", 8, 3, Qualification.BACHELORS,
                List.of("Policy Analysis"), List.of("Research", "Statistics"),
                "Research evidence for policy briefs and programme monitoring.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 4), "Digital Marketing Intern",
                Sector.MARKETING_COMMUNICATION, "Telangana", "Hyderabad", 12, 2, Qualification.BACHELORS,
                List.of("Digital Marketing"), List.of("Content Writing", "Social Media"),
                "Run awareness campaigns and measure their reach across channels.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 5), "Health Data Intern",
                Sector.HEALTHCARE_BIOTECH, "Tamil Nadu", "Chennai", 6, 3, Qualification.BACHELORS,
                List.of("Healthcare Data"), List.of("Statistics", "Data Analysis"),
                "Work with health programme data to support monitoring and evaluation.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 6), "Cybersecurity Intern",
                Sector.SOFTWARE_IT, "Haryana", "Gurugram", 8, 3, Qualification.BACHELORS,
                List.of("Cybersecurity"), List.of("Cloud Computing", "DevOps"),
                "Assist with security audits, vulnerability reviews and hardening checklists.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 7), "Renewable Energy Research Intern",
                Sector.ENVIRONMENTAL_ENERGY, "Maharashtra", "Pune", 6, 4, Qualification.POST_GRADUATE,
                List.of("Environmental Science"), List.of("Research", "Data Analysis"),
                "Study solar and wind programme data and draft research notes.", c));
        // Low demand / excess capacity roles (intentionally easy to fill).
        opps.add(createOpportunity(rng, nextProvider(rng, 8), "Supply Chain Intern",
                Sector.MANUFACTURING_OPERATIONS, "Madhya Pradesh", "Indore", 15, 3, Qualification.BACHELORS,
                List.of("Supply Chain"), List.of("Excel", "Operations"),
                "Map procurement flows and support inventory planning.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 9), "Visual Communication Intern",
                Sector.DESIGN_MEDIA, "Rajasthan", "Jaipur", 10, 2, Qualification.BACHELORS,
                List.of("Graphic Design"), List.of("UI/UX Design", "Content Writing"),
                "Design campaign visuals and public-awareness material.", c));

        // Extra depth for Data & Analytics: a broader spread of roles, skills
        // and locations so candidates and reviewers interested in this sector
        // see a richer, more varied set of live opportunities (not just the
        // single hot listing above).
        opps.add(createOpportunity(rng, nextProvider(rng, 7), "Business Intelligence Intern",
                Sector.DATA_ANALYTICS, "Maharashtra", "Pune", 7, 3, Qualification.BACHELORS,
                List.of("SQL", "Data Analysis"), List.of("Tableau", "Excel"),
                "Build BI reports and dashboards that drive weekly programme decisions.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 8), "Machine Learning Intern",
                Sector.DATA_ANALYTICS, "Telangana", "Hyderabad", 5, 4, Qualification.BACHELORS,
                List.of("Machine Learning"), List.of("Deep Learning", "Statistics"),
                "Prototype machine learning models and evaluate them against real data.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 9), "Data Analytics Intern",
                Sector.DATA_ANALYTICS, "Tamil Nadu", "Chennai", 9, 2, Qualification.HIGHER_SECONDARY,
                List.of("Excel"), List.of("Data Analysis", "Statistics"),
                "Support senior analysts with data cleaning, reporting and visualisation.", c));
        opps.add(createOpportunity(rng, nextProvider(rng, 1), "Business Intelligence Intern",
                Sector.DATA_ANALYTICS, "Uttar Pradesh", "Lucknow", 6, 3, Qualification.BACHELORS,
                List.of("Data Analysis"), List.of("Power BI", "NLP"),
                "Turn programme datasets into decision-ready dashboards and briefs.", c));

        // 50 more roles giving real depth to the two most-explored fields:
        // 25 Full Stack Development + 25 Data Analytics opportunities.
        addFieldDepthOpportunities(rng, opps, c);

        // The remaining field: all sectors, varied states, 4–9 seats each.
        opps.addAll(createOpps(rng, 38, STANDARD_STATES, 4, 9, c));

        // A modest-size internship whose combined-skill requirement (Data Analysis
        // AND Python) leaves a small eligible pool — the demo candidate, based in
        // Delhi with exactly those skills, competes here on fair, visible merit.
        Opportunity demoOpp = createOpportunity(rng, demoProvider.id, "Data Engineering Intern",
                Sector.SOFTWARE_IT, "Delhi", "New Delhi", 5, 3, Qualification.BACHELORS,
                List.of("Data Analysis", "Python"), List.of("SQL", "Communication"),
                "Build ETL pipelines and data products for public services alongside the platform data team.", c);

        // ~700 candidates. 45% concentrate on the three hottest sectors, each of
        // which guarantees the hot internship's required skill — producing large,
        // real eligible pools against 8–12 seats.
        Map<Sector, String> guaranteed = Map.of(
                Sector.SOFTWARE_IT, "Java",
                Sector.DATA_ANALYTICS, "Data Analysis",
                Sector.FINANCE_BANKING, "Finance");
        createCandidates(rng, 700, STANDARD_STATES, List.of(Sector.SOFTWARE_IT, Sector.DATA_ANALYTICS, Sector.FINANCE_BANKING),
                0.62, 0.25, 0.08, 0.05, opps, c, 0.45, null, guaranteed, demoOpp.id);
    }

    private void seedBenchmark(Random rng, int[] c) {
        List<Opportunity> opps = createOpps(rng, 80, STANDARD_STATES, 4, 9, c);
        createCandidates(rng, 1000, STANDARD_STATES, null, 0.62, 0.25, 0.08, 0.05, opps, c, 0.30);
    }

    /**
     * Engineered conflict proof: 4 candidates, 2 opportunities, 1 seat each.
     * Sequential (candidate-by-candidate) processing locks in two weaker fits;
     * the global allocation swaps both seats to the stronger candidates.
     * All numbers below are produced by the real engine — nothing is hardcoded.
     */
    private void seedMicro(Random rng, int[] c) {
        Provider demoProvider = providers.findAll().get(0);
        Opportunity o1 = createOpportunity(rng, demoProvider.id, "Data Analytics Intern",
                Sector.DATA_ANALYTICS, "Delhi", "New Delhi", 1, 2, Qualification.BACHELORS,
                List.of("Data Analysis"), List.of("SQL", "Python"),
                "Assist the analytics team with data cleaning, dashboards and insight reports.", c);
        Opportunity o2 = createOpportunity(rng, demoProvider.id, "Finance Intern",
                Sector.FINANCE_BANKING, "Delhi", "New Delhi", 1, 2, Qualification.BACHELORS,
                List.of("Excel"), List.of("Finance", "Statistics"),
                "Support budgeting, reconciliation and financial reporting workflows.", c);

        User demo = users.findByEmail(UserSeeder.CANDIDATE_EMAIL).orElseThrow();
        CandidateProfile p1 = createCandidate(rng, demo, "Aarav Sharma", Qualification.BACHELORS,
                CandidateStatus.STUDENT, LocationType.URBAN, "Delhi", "New Delhi", 0.5,
                "Final-year bachelor's student from New Delhi, Delhi with a strong interest in data and software.", c);
        CandidateProfile p2 = createCandidate(rng, null, "Ananya Verma", Qualification.BACHELORS,
                CandidateStatus.STUDENT, LocationType.URBAN, "Delhi", "South Delhi", 0.5,
                "Finance-focused student from South Delhi, Delhi who enjoys spreadsheets and markets.", c);
        CandidateProfile p3 = createCandidate(rng, null, "Rohan Gupta", Qualification.BACHELORS,
                CandidateStatus.GRADUATE, LocationType.URBAN, "Delhi", "Dwarka", 1.0,
                "Recent graduate from Dwarka, Delhi working on finance and statistics projects.", c);
        CandidateProfile p4 = createCandidate(rng, null, "Diya Patel", Qualification.BACHELORS,
                CandidateStatus.STUDENT, LocationType.URBAN, "Delhi", "Rohini", 0.5,
                "Data enthusiast from Rohini, Delhi building analytics projects in SQL and Python.", c);

        addSkills(p1, List.of("Data Analysis", "Excel", "Finance"));
        addSkills(p2, List.of("Data Analysis", "SQL", "Excel"));
        addSkills(p3, List.of("Excel", "Finance", "Statistics"));
        addSkills(p4, List.of("Data Analysis", "SQL", "Python"));
        addInterests(p1, List.of(Sector.DATA_ANALYTICS));
        addInterests(p2, List.of(Sector.FINANCE_BANKING));
        addInterests(p3, List.of(Sector.FINANCE_BANKING));
        addInterests(p4, List.of(Sector.DATA_ANALYTICS));
        addPreference(p1, o1.id, 1);
        addPreference(p1, o2.id, 2);
        addPreference(p2, o2.id, 1);
        addPreference(p2, o1.id, 2);
        addPreference(p3, o2.id, 1);
        addPreference(p4, o1.id, 1);
    }

    private void seedHighConflict(Random rng, int[] c) {
        // ~550 candidates against ~45 seats, concentrated in three hot sectors.
        List<Opportunity> opps = new ArrayList<>();
        Provider demoProvider = providers.findAll().get(0);
        List<Sector> hot = List.of(Sector.DATA_ANALYTICS, Sector.SOFTWARE_IT, Sector.FINANCE_BANKING);
        Sector[] allSectors = Sector.values();
        for (int i = 0; i < 14; i++) {
            Sector sector = i < 8 ? hot.get(i % 3) : allSectors[(i + 4) % allSectors.length];
            StateInfo st = STANDARD_STATES.get(i % STANDARD_STATES.size());
            int cap = i < 8 ? 1 + (i % 3) : 2 + (i % 4);
            opps.add(createOpportunity(rng, i == 0 ? demoProvider.id : nextProvider(rng, i),
                    titleFor(sector, rng), sector, st.name, st.districts[i % st.districts.length],
                    cap, 1 + (i % 3), Qualification.BACHELORS,
                    mandatoryFor(sector, rng), optionalFor(sector, rng), descriptionFor(sector), c));
        }
        Map<Sector, String> guaranteed = Map.of(
                Sector.SOFTWARE_IT, "Java",
                Sector.DATA_ANALYTICS, "Data Analysis",
                Sector.FINANCE_BANKING, "Finance");
        createCandidates(rng, 550, STANDARD_STATES, hot, 0.58, 0.28, 0.09, 0.05, opps, c,
                0.65, null, guaranteed, null);
    }

    private void seedGeographic(Random rng, int[] c) {
        // ~550 candidates spread across all 16 states; opportunities concentrated
        // in six high-capacity states — strong geographic demand/capacity contrast.
        List<Opportunity> opps = new ArrayList<>();
        Provider demoProvider = providers.findAll().get(0);
        List<StateInfo> geoStates = List.of(
            new StateInfo("Karnataka", 0, new String[]{"Bengaluru", "Mysuru"}),
            new StateInfo("Maharashtra", 0, new String[]{"Mumbai", "Pune"}),
            new StateInfo("Delhi", 0, new String[]{"New Delhi", "Dwarka"}),
            new StateInfo("Tamil Nadu", 0, new String[]{"Chennai", "Coimbatore"}),
            new StateInfo("Kerala", 0, new String[]{"Kochi", "Thiruvananthapuram"}),
            new StateInfo("Telangana", 0, new String[]{"Hyderabad", "Warangal"}));
        Sector[] allSectors = Sector.values();
        for (int i = 0; i < 24; i++) {
            StateInfo st = geoStates.get(i % geoStates.size());
            Sector sector = allSectors[i % allSectors.length];
            opps.add(createOpportunity(rng, i == 0 ? demoProvider.id : nextProvider(rng, i),
                    titleFor(sector, rng), sector, st.name, st.districts[i % st.districts.length],
                    4 + (i % 4), 1 + (i % 3), Qualification.BACHELORS,
                    mandatoryFor(sector, rng), optionalFor(sector, rng), descriptionFor(sector), c));
        }
        // Candidates spread evenly across all 16 states.
        List<StateInfo> flat = STANDARD_STATES.stream().map(s -> new StateInfo(s.name(), 1, s.districts())).toList();
        createCandidates(rng, 550, flat, null, 0.60, 0.25, 0.10, 0.05, opps, c, 0.30, null, Map.of(), null);
    }

    private void seedFairness(Random rng, int[] c) {
        Provider demoProvider = providers.findAll().get(0);
        Sector[] allSectors = Sector.values();
        List<Opportunity> opps = new ArrayList<>();
        // 12 opportunities in metros (80 seats), 6 in rural-heavy states (15 seats).
        List<StateInfo> metroStates = List.of(
            new StateInfo("Delhi", 0, new String[]{"New Delhi", "Dwarka"}),
            new StateInfo("Maharashtra", 0, new String[]{"Mumbai", "Pune"}),
            new StateInfo("Karnataka", 0, new String[]{"Bengaluru", "Mysuru"}),
            new StateInfo("Tamil Nadu", 0, new String[]{"Chennai", "Coimbatore"}));
        List<StateInfo> ruralStates = List.of(
            new StateInfo("Uttar Pradesh", 0, new String[]{"Lucknow", "Kanpur", "Varanasi"}),
            new StateInfo("Bihar", 0, new String[]{"Patna", "Gaya"}),
            new StateInfo("Madhya Pradesh", 0, new String[]{"Bhopal", "Indore"}),
            new StateInfo("Rajasthan", 0, new String[]{"Jaipur", "Jodhpur"}));
        for (int i = 0; i < 12; i++) {
            StateInfo st = metroStates.get(i % 4);
            Sector sector = allSectors[i % allSectors.length];
            opps.add(createOpportunity(rng, i == 0 ? demoProvider.id : nextProvider(rng, i),
                    titleFor(sector, rng), sector, st.name, st.districts[i % st.districts.length],
                    6 + (i % 2), 1 + (i % 3), Qualification.BACHELORS,
                    mandatoryFor(sector, rng), optionalFor(sector, rng), descriptionFor(sector), c));
        }
        for (int i = 0; i < 6; i++) {
            StateInfo st = ruralStates.get(i % 4);
            Sector sector = allSectors[(i + 3) % allSectors.length];
            opps.add(createOpportunity(rng, nextProvider(rng, 10 + i),
                    titleFor(sector, rng), sector, st.name, st.districts[i % st.districts.length],
                    2 + (i % 2), 1 + (i % 3), Qualification.BACHELORS,
                    mandatoryFor(sector, rng), optionalFor(sector, rng), descriptionFor(sector), c));
        }
        // 80 rural candidates spread across rural states, 80 urban in metros.
        List<StateInfo> ruralSpread = List.of(
            new StateInfo("Uttar Pradesh", 20, new String[]{"Lucknow", "Kanpur", "Varanasi", "Agra"}),
            new StateInfo("Bihar", 20, new String[]{"Patna", "Gaya", "Muzaffarpur"}),
            new StateInfo("Madhya Pradesh", 20, new String[]{"Bhopal", "Indore", "Gwalior"}),
            new StateInfo("Rajasthan", 20, new String[]{"Jaipur", "Jodhpur", "Udaipur"}),
            new StateInfo("West Bengal", 20, new String[]{"Kolkata", "Howrah", "Durgapur"}));
        List<StateInfo> metroSpread = List.of(
            new StateInfo("Delhi", 30, new String[]{"New Delhi", "South Delhi", "Dwarka"}),
            new StateInfo("Maharashtra", 30, new String[]{"Mumbai", "Pune", "Nagpur"}),
            new StateInfo("Karnataka", 20, new String[]{"Bengaluru", "Mysuru"}));
        createRuralBlock(rng, 275, ruralSpread, opps, c);
        createUrbanBlock(rng, 275, metroSpread, opps, c);
    }

    /**
     * Genuine infeasibility: 60 eligible candidates but only 15 seats.
     * The demonstration "Full Coverage" policy (hard rule: every candidate
     * must be allocated) makes the constraint system impossible, and the
     * real solver returns INFEASIBLE.
     */
    private void seedInfeasible(Random rng, int[] c) {
        Provider demoProvider = providers.findAll().get(0);
        Sector[] allSectors = Sector.values();
        List<StateInfo> states = STANDARD_STATES.subList(0, 6);
        List<Opportunity> opps = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            StateInfo st = states.get(i % states.size());
            Sector sector = allSectors[i % allSectors.length];
            opps.add(createOpportunity(rng, i == 0 ? demoProvider.id : nextProvider(rng, i),
                    titleFor(sector, rng), sector, st.name, st.districts[i % st.districts.length],
                    1 + (i % 2), 1 + (i % 3), Qualification.BACHELORS,
                    mandatoryFor(sector, rng), optionalFor(sector, rng), descriptionFor(sector), c));
        }
        List<Sector> all = List.of(allSectors);
        createCandidates(rng, 60, states, all, 1.0, 0.0, 0.0, 0.0, opps, c, 0.30);
    }

    // ------------------------------------------------------------------
    // Builders
    // ------------------------------------------------------------------

    private void ensureProviders(Random rng) {
        long seeded = providers.findAll().stream().filter(p -> p.userId == null).count();
        if (seeded > 0) return;
        for (int i = 0; i < PROVIDER_ORGS.length; i++) {
            Provider p = new Provider();
            p.orgName = PROVIDER_ORGS[i];
            p.orgType = "Industry / Institute";
            p.state = STANDARD_STATES.get(i % STANDARD_STATES.size()).name();
            p.about = "Synthetic demonstration organisation used to seed internship opportunities.";
            providers.save(p);
        }
    }

    private long nextProvider(Random rng, int i) {
        List<Provider> all = providers.findAll();
        // all.get(0) is the demo provider; the rest are seeded orgs.
        Provider p = all.get(1 + (i % (all.size() - 1)));
        return p.id;
    }

    private List<Opportunity> createOpps(Random rng, int n, List<StateInfo> states, int capMin, int capMax, int[] c) {
        List<Opportunity> opps = new ArrayList<>();
        Sector[] all = Sector.values();
        for (int i = 0; i < n; i++) {
            Sector sector = all[i % all.length];
            StateInfo st = states.get(i % states.size());
            int cap = capMin + rng.nextInt(capMax - capMin + 1);
            double q = rng.nextDouble();
            Qualification minQual = q < 0.15 ? Qualification.POST_GRADUATE
                    : (q < 0.30 ? Qualification.HIGHER_SECONDARY : Qualification.BACHELORS);
            opps.add(createOpportunity(rng, i < 6 ? demoProviderId() : nextProvider(rng, i),
                    titleFor(sector, rng), sector, st.name, st.districts[rng.nextInt(st.districts.length)],
                    cap, 1 + rng.nextInt(3), minQual,
                    mandatoryFor(sector, rng), optionalFor(sector, rng), descriptionFor(sector), c));
        }
        return opps;
    }

    private long demoProviderId() {
        return providers.findAll().get(0).id;
    }

    private Opportunity createOpportunity(Random rng, long providerId, String title, Sector sector,
                                          String state, String city, int capacity, int duration,
                                          Qualification minQual, List<String> mandatory,
                                          List<String> optional, String description, int[] c) {
        Opportunity o = new Opportunity();
        o.providerId = providerId;
        o.title = title;
        o.sector = sector;
        o.state = state;
        o.city = city;
        o.capacity = capacity;
        o.durationMonths = duration;
        o.minQualification = minQual;
        o.status = OppStatus.ACTIVE;
        o.description = description;
        Opportunity saved = opportunities.save(o);
        c[1]++;
        c[2] += capacity;
        // A skill must appear at most once per opportunity. mandatoryFor(...) and
        // optionalFor(...) sample independently from the same sector pool, so a
        // skill can otherwise be picked as both — producing duplicate chips
        // ("Data Analysis" twice) in the candidate-facing UI. Mandatory wins.
        Set<String> addedSkills = new LinkedHashSet<>();
        for (String s : mandatory) if (addedSkills.add(s)) oppSkills.save(oppSkill(saved.id, s, true));
        for (String s : optional) if (addedSkills.add(s)) oppSkills.save(oppSkill(saved.id, s, false));
        return saved;
    }

    private OpportunitySkill oppSkill(Long oppId, String skill, boolean mandatory) {
        OpportunitySkill s = new OpportunitySkill();
        s.opportunityId = oppId;
        s.skill = skill;
        s.mandatory = mandatory;
        return s;
    }

    private void createRuralBlock(Random rng, int n, List<StateInfo> states, List<Opportunity> opps, int[] c) {
        createCandidates(rng, n, states, null, 0.60, 0.25, 0.10, 0.05, opps, c, 0.30, LocationType.RURAL);
    }

    private void createUrbanBlock(Random rng, int n, List<StateInfo> states, List<Opportunity> opps, int[] c) {
        createCandidates(rng, n, states, null, 0.60, 0.25, 0.10, 0.05, opps, c, 0.30, LocationType.URBAN);
    }

    private void createCandidates(Random rng, int n, List<StateInfo> states, List<Sector> biasSectors,
                                  double wB, double wPG, double wHS, double wD,
                                  List<Opportunity> opps, int[] c, double sectorBias) {
        createCandidates(rng, n, states, biasSectors, wB, wPG, wHS, wD, opps, c, sectorBias, null, Map.of(), null);
    }

    private void createCandidates(Random rng, int n, List<StateInfo> states, List<Sector> biasSectors,
                                  double wB, double wPG, double wHS, double wD,
                                  List<Opportunity> opps, int[] c, double sectorBias,
                                  LocationType forcedLocation) {
        createCandidates(rng, n, states, biasSectors, wB, wPG, wHS, wD, opps, c, sectorBias, forcedLocation, Map.of(), null);
    }

    /**
     * Full-featured generator. {@code guaranteedSkills} maps a primary sector
     * to a skill that every candidate of that sector receives — used to build
     * real, large eligible pools against limited seats. {@code demoPreferenceOppId}
     * is the opportunity the demo candidate ranks first (their stated preference
     * — the engine still allocates purely on merit).
     */
    private void createCandidates(Random rng, int n, List<StateInfo> states, List<Sector> biasSectors,
                                  double wB, double wPG, double wHS, double wD,
                                  List<Opportunity> opps, int[] c, double sectorBias,
                                  LocationType forcedLocation, Map<Sector, String> guaranteedSkills,
                                  Long demoPreferenceOppId) {
        User demo = users.findByEmail(UserSeeder.CANDIDATE_EMAIL).orElseThrow();
        Qualification[] quals = {Qualification.BACHELORS, Qualification.POST_GRADUATE, Qualification.HIGHER_SECONDARY, Qualification.DOCTORAL};
        double[] qualW = {wB, wPG, wHS, wD};
        CandidateStatus[] statuses = {CandidateStatus.STUDENT, CandidateStatus.GRADUATE, CandidateStatus.WORKING};
        double[] statusW = {0.55, 0.25, 0.20};
        Sector[] allSectors = Sector.values();
        for (int i = 0; i < n; i++) {
            StateInfo st = weightedPick(rng, states);
            String district = st.districts()[rng.nextInt(st.districts().length)];
            LocationType lt = forcedLocation != null ? forcedLocation
                    : rng.nextDouble() < 0.40 ? LocationType.RURAL
                    : (rng.nextDouble() < 0.74 ? LocationType.URBAN : LocationType.SEMI_URBAN);
            Qualification q = weightedPick(rng, quals, qualW);
            CandidateStatus cs = weightedPick(rng, statuses, statusW);
            double exp = switch (cs) {
                case STUDENT -> Math.round(rng.nextDouble() * 10) / 10.0;
                case GRADUATE -> Math.round(rng.nextDouble() * 20) / 10.0;
                case WORKING -> Math.round((1 + rng.nextDouble() * 4) * 10) / 10.0;
            };
            String name = (i == 0) ? demo.name
                    : FIRST_NAMES[(i * 7 + rng.nextInt(13)) % FIRST_NAMES.length]
                    + " " + LAST_NAMES[rng.nextInt(LAST_NAMES.length)];
            String statusWord = switch (cs) {
                case STUDENT -> "student";
                case GRADUATE -> "graduate";
                case WORKING -> "working professional";
            };
            // The demo candidate is attached only to the very first candidate of the
            // whole scenario, even when a scenario is built from several blocks.
            boolean useDemo = (i == 0) && !demoAssigned;
            User u = useDemo ? demo : null;
            if (useDemo) demoAssigned = true;
            // The demo persona is fixed: Aarav Sharma from New Delhi, Delhi, with
            // interests in data and software — identical in every scenario.
            String stateFor = useDemo ? "Delhi" : st.name();
            String districtFor = useDemo ? "New Delhi" : district;
            String bio = useDemo
                    ? "Final-year student from New Delhi, Delhi with strong interests in data and software."
                    : statusWord + " from " + district + ", " + st.name()
                      + ". Interested in building practical experience through internships.";
            CandidateProfile p = createCandidate(rng, u, name, q, cs, lt, stateFor, districtFor, exp, bio, c);

            Sector primary;
            if (useDemo) {
                primary = Sector.DATA_ANALYTICS;
            } else {
                primary = (biasSectors != null && rng.nextDouble() < sectorBias)
                        ? biasSectors.get(rng.nextInt(biasSectors.size()))
                        : allSectors[rng.nextInt(allSectors.length)];
            }
            List<Sector> intList = new ArrayList<>();
            Sector secondary;
            if (useDemo) {
                secondary = Sector.SOFTWARE_IT;
                intList.add(Sector.DATA_ANALYTICS);
                intList.add(Sector.SOFTWARE_IT);
            } else {
                secondary = allSectors[rng.nextInt(allSectors.length)];
                intList.add(primary);
                if (rng.nextDouble() < 0.5 && secondary != primary) intList.add(secondary);
            }
            addInterests(p, intList);

            List<String> skillPool = new ArrayList<>(List.of(SECTOR_SKILLS.get(primary)));
            List<String> skillPool2 = new ArrayList<>(List.of(SECTOR_SKILLS.get(secondary)));
            List<String> chosen = new ArrayList<>();
            if (useDemo) {
                chosen = new ArrayList<>(List.of("Data Analysis", "Python", "Communication"));
            } else {
                // Distinct skills, primary pool first, never more than the union can provide.
                List<String> poolOrder = new ArrayList<>(skillPool);
                for (String s : skillPool2) if (!poolOrder.contains(s)) poolOrder.add(s);
                Collections.shuffle(poolOrder, rng);
                int k = Math.min(2 + rng.nextInt(4), Math.max(1, poolOrder.size()));
                chosen = new ArrayList<>(poolOrder.subList(0, k));
                String guaranteed = guaranteedSkills.get(primary);
                if (guaranteed != null && !chosen.contains(guaranteed)) chosen.add(guaranteed);
            }
            addSkills(p, chosen);

            List<Opportunity> shuffled = new ArrayList<>(opps);
            Collections.shuffle(shuffled, rng);
            if (useDemo && demoPreferenceOppId != null) {
                addPreference(p, demoPreferenceOppId, 1);
                int rank = 2;
                for (Opportunity op : shuffled) {
                    if (rank > 3) break;
                    if (op.id.equals(demoPreferenceOppId)) continue;
                    addPreference(p, op.id, rank);
                    rank++;
                }
            } else {
                int prefCount = (useDemo) ? Math.min(3, shuffled.size())
                        : 1 + rng.nextInt(Math.min(4, Math.max(1, shuffled.size())));
                for (int j = 0; j < prefCount && j < shuffled.size(); j++) {
                    addPreference(p, shuffled.get(j).id, j + 1);
                }
            }
        }
    }

    private CandidateProfile createCandidate(Random rng, User user, String name, Qualification q,
                                             CandidateStatus cs, LocationType lt, String state,
                                             String district, double exp, String bio, int[] c) {
        if (user == null) {
            user = new User();
            user.email = "cand" + String.format("%03d", c[0] + 1) + "@pragati.gov.in";
            user.passwordHash = "noop-not-login-enabled";
            user.name = name;
            user.role = Role.CANDIDATE;
            users.save(user);
        }
        CandidateProfile p = new CandidateProfile();
        p.userId = user.id;
        p.fullName = name;
        p.phone = "98" + String.format("%08d", rng.nextInt(100_000_000));
        int year = 1998 + rng.nextInt(10);
        p.dob = LocalDate.of(year, 1 + rng.nextInt(12), 1 + rng.nextInt(28));
        p.qualification = q;
        p.candidateStatus = cs;
        p.state = state;
        p.district = district;
        p.locationType = lt;
        p.bio = bio;
        p.experienceYears = exp;
        CandidateProfile saved = profiles.save(p);
        c[0]++;
        return saved;
    }

    private void addSkills(CandidateProfile p, List<String> canonical) {
        for (String s : canonical) {
            CandidateSkill sk = new CandidateSkill();
            sk.candidateId = p.id;
            sk.rawLabel = s;
            sk.canonical = s;
            sk.source = SkillSource.PROFILE;
            sk.validated = true;
            skills.save(sk);
        }
    }

    private void addInterests(CandidateProfile p, List<Sector> sectors) {
        for (Sector s : sectors) {
            CandidateInterest it = new CandidateInterest();
            it.candidateId = p.id;
            it.sector = s;
            interests.save(it);
        }
    }

    private void addPreference(CandidateProfile p, Long opportunityId, int rank) {
        CandidatePreference pref = new CandidatePreference();
        pref.candidateId = p.id;
        pref.opportunityId = opportunityId;
        pref.rank = rank;
        preferences.save(pref);
    }

    // ------------------------------------------------------------------
    // Small deterministic helpers
    // ------------------------------------------------------------------

    private <T> T weightedPick(Random rng, T[] items, double[] weights) {
        double total = 0;
        for (double w : weights) total += w;
        double r = rng.nextDouble() * total;
        for (int i = 0; i < items.length; i++) {
            r -= weights[i];
            if (r <= 0) return items[i];
        }
        return items[items.length - 1];
    }

    private StateInfo weightedPick(Random rng, List<StateInfo> states) {
        double total = 0;
        for (StateInfo s : states) total += s.weight();
        if (total <= 0) return states.get(rng.nextInt(states.size()));
        double r = rng.nextDouble() * total;
        for (StateInfo s : states) {
            r -= s.weight();
            if (r <= 0) return s;
        }
        return states.get(states.size() - 1);
    }

    /**
     * 50 additional opportunities giving real breadth in the two fields
     * candidates explore most: 25 Full Stack Development roles (Software &
     * IT) and 25 Data Analytics roles — varied titles, skill combinations,
     * capacities, durations and locations, not copies of one template.
     */
    private void addFieldDepthOpportunities(Random rng, List<Opportunity> opps, int[] c) {
        for (int i = 0; i < 25; i++) {
            StateInfo st = STANDARD_STATES.get(i % STANDARD_STATES.size());
            String title = FULL_STACK_TITLES[i % FULL_STACK_TITLES.length];
            List<String> mandatory = List.of(FULL_STACK_MANDATORY[i % FULL_STACK_MANDATORY.length]);
            List<String> optional = List.of(FULL_STACK_OPTIONAL[i % FULL_STACK_OPTIONAL.length]);
            int capacity = 4 + rng.nextInt(9);
            int duration = 2 + rng.nextInt(4);
            double q = rng.nextDouble();
            Qualification minQual = q < 0.15 ? Qualification.POST_GRADUATE
                    : (q < 0.25 ? Qualification.HIGHER_SECONDARY : Qualification.BACHELORS);
            opps.add(createOpportunity(rng, nextProvider(rng, i + 11), title, Sector.SOFTWARE_IT,
                    st.name, st.districts[i % st.districts.length], capacity, duration, minQual,
                    mandatory, optional, descriptionFor(Sector.SOFTWARE_IT), c));
        }
        for (int i = 0; i < 25; i++) {
            StateInfo st = STANDARD_STATES.get((i + 3) % STANDARD_STATES.size());
            String title = DATA_ANALYTICS_TITLES[i % DATA_ANALYTICS_TITLES.length];
            List<String> mandatory = List.of(DATA_ANALYTICS_MANDATORY[i % DATA_ANALYTICS_MANDATORY.length]);
            List<String> optional = List.of(DATA_ANALYTICS_OPTIONAL[i % DATA_ANALYTICS_OPTIONAL.length]);
            int capacity = 4 + rng.nextInt(9);
            int duration = 2 + rng.nextInt(4);
            double q = rng.nextDouble();
            Qualification minQual = q < 0.12 ? Qualification.POST_GRADUATE
                    : (q < 0.22 ? Qualification.HIGHER_SECONDARY : Qualification.BACHELORS);
            opps.add(createOpportunity(rng, nextProvider(rng, i + 37), title, Sector.DATA_ANALYTICS,
                    st.name, st.districts[(i + 1) % st.districts.length], capacity, duration, minQual,
                    mandatory, optional, descriptionFor(Sector.DATA_ANALYTICS), c));
        }
    }

    private String titleFor(Sector sector, Random rng) {
        List<String> titles = SECTOR_TITLES.get(sector);
        return titles.get(rng.nextInt(titles.size()));
    }

    private List<String> mandatoryFor(Sector sector, Random rng) {
        String[] pool = SECTOR_SKILLS.get(sector);
        List<String> copy = new ArrayList<>(List.of(pool));
        Collections.shuffle(copy, rng);
        int n = Math.min(copy.size(), 1 + (rng.nextInt(2)));
        return new ArrayList<>(copy.subList(0, n));
    }

    private List<String> optionalFor(Sector sector, Random rng) {
        String[] pool = SECTOR_SKILLS.get(sector);
        List<String> copy = new ArrayList<>(List.of(pool));
        Collections.shuffle(copy, rng);
        int n = Math.min(copy.size(), 1 + rng.nextInt(2));
        return new ArrayList<>(copy.subList(0, n));
    }

    private String descriptionFor(Sector sector) {
        return switch (sector) {
            case SOFTWARE_IT -> "Build and ship software features alongside the engineering team.";
            case DATA_ANALYTICS -> "Analyse real datasets, build dashboards and support data-driven decisions.";
            case FINANCE_BANKING -> "Support finance workflows including budgeting, reporting and analysis.";
            case DESIGN_MEDIA -> "Contribute to product and communication design projects.";
            case MARKETING_COMMUNICATION -> "Assist with campaigns, content and audience analysis.";
            case PUBLIC_ADMIN_POLICY -> "Support policy research, programme monitoring and stakeholder work.";
            case HEALTHCARE_BIOTECH -> "Work with health datasets and research teams on applied projects.";
            case MANUFACTURING_OPERATIONS -> "Support operations, process improvement and supply chain tasks.";
            case RESEARCH_DEVELOPMENT -> "Conduct literature and data research on emerging technology topics.";
            case ENVIRONMENTAL_ENERGY -> "Work on sustainability, energy and environment analysis projects.";
        };
    }

    /**
     * Seed-time dataset validation: fail LOUDLY if the generated dataset is
     * structurally broken (missing geography, unknown skills, dangling
     * preferences, seatless opportunities, duplicate skills). A broken
     * dataset must never be served — it would corrupt allocation runs and
     * every downstream metric silently.
     */
    private void validateDataset(ScenarioKey key) {
        List<CandidateProfile> allProfiles = profiles.findAll();
        if (allProfiles.isEmpty()) {
            throw new IllegalStateException("Seed validation failed [" + key + "]: no candidates generated");
        }
        Set<Long> candidateIds = new HashSet<>();
        for (CandidateProfile p : allProfiles) {
            if (p.fullName == null || p.fullName.isBlank())
                throw new IllegalStateException("Seed validation failed [" + key + "]: candidate " + p.id + " has no name");
            if (p.state == null || p.state.isBlank() || p.district == null || p.district.isBlank())
                throw new IllegalStateException("Seed validation failed [" + key + "]: candidate " + p.id + " has incomplete geography");
            if (p.qualification == null || p.locationType == null || p.candidateStatus == null)
                throw new IllegalStateException("Seed validation failed [" + key + "]: candidate " + p.id + " has a missing profile attribute");
            Set<String> seenSkills = new HashSet<>();
            for (CandidateSkill s : skills.findByCandidateId(p.id)) {
                if (s.canonical == null || !SkillTaxonomy.CANONICAL.contains(s.canonical))
                    throw new IllegalStateException("Seed validation failed [" + key + "]: unknown canonical skill '" + s.canonical + "' on candidate " + p.id);
                if (!seenSkills.add(s.canonical))
                    throw new IllegalStateException("Seed validation failed [" + key + "]: duplicate skill '" + s.canonical + "' on candidate " + p.id);
            }
            candidateIds.add(p.id);
        }
        List<Opportunity> allOpps = opportunities.findAll();
        if (allOpps.isEmpty()) {
            throw new IllegalStateException("Seed validation failed [" + key + "]: no opportunities generated");
        }
        Set<Long> oppIds = new HashSet<>();
        for (Opportunity o : allOpps) {
            if (o.capacity < 1 || o.sector == null || o.state == null || o.minQualification == null)
                throw new IllegalStateException("Seed validation failed [" + key + "]: opportunity " + o.id + " is incomplete");
            List<OpportunitySkill> os = oppSkills.findByOpportunityId(o.id);
            if (os.stream().noneMatch(s -> s.mandatory))
                throw new IllegalStateException("Seed validation failed [" + key + "]: opportunity " + o.id + " has no mandatory skills");
            for (OpportunitySkill s : os) {
                if (s.skill == null || !SkillTaxonomy.CANONICAL.contains(s.skill))
                    throw new IllegalStateException("Seed validation failed [" + key + "]: opportunity " + o.id + " references unknown skill '" + s.skill + "'");
            }
            oppIds.add(o.id);
        }
        for (CandidatePreference pref : preferences.findAll()) {
            if (!candidateIds.contains(pref.candidateId) || !oppIds.contains(pref.opportunityId))
                throw new IllegalStateException("Seed validation failed [" + key + "]: dangling preference " + pref.id);
        }
    }

    private String fingerprint(ScenarioKey key, long seed, int candidates, int opportunities, int seats) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(("pragati|" + key + "|" + seed + "|" + candidates + "|"
                    + opportunities + "|" + seats).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute dataset fingerprint", e);
        }
    }
}
