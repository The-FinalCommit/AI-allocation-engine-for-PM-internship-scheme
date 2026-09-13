package in.pragati.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.pragati.common.Labels;
import in.pragati.domain.*;
import in.pragati.repo.ConflictMetricRepository;
import in.pragati.repo.GeoMetricRepository;
import in.pragati.repo.GroupMetricRepository;

/**
 * Aggregates fairness, geography and conflict metrics for a completed run.
 * The same aggregation powers the map and the table, so both always agree.
 */
@Service
public class MetricsService {

    private final GroupMetricRepository groupMetrics;
    private final GeoMetricRepository geoMetrics;
    private final ConflictMetricRepository conflictMetrics;

    public MetricsService(GroupMetricRepository groupMetrics, GeoMetricRepository geoMetrics,
                          ConflictMetricRepository conflictMetrics) {
        this.groupMetrics = groupMetrics;
        this.geoMetrics = geoMetrics;
        this.conflictMetrics = conflictMetrics;
    }

    @Transactional
    public void persist(AllocationRun run, List<Assignment> global,
                        List<CandidateProfile> candidates, List<Opportunity> opps,
                        Map<Long, Map<Long, Integer>> prefMap,
                        Map<Long, Integer> eligiblePerOpp,
                        Map<Long, Integer> capacityOverrides) {
        Map<Long, Assignment> byCandidate = new HashMap<>();
        for (Assignment a : global) byCandidate.put(a.candidateId, a);

        // ---- Group metrics (rural/urban, state, qualification) ----
        Map<String, Map<String, List<CandidateProfile>>> groups = new LinkedHashMap<>();
        for (CandidateProfile p : candidates) {
            groups.computeIfAbsent("RURAL_URBAN", k -> new LinkedHashMap<>())
                    .computeIfAbsent(Labels.label(p.locationType), k -> new ArrayList<>()).add(p);
            if (p.state != null) {
                groups.computeIfAbsent("STATE", k -> new LinkedHashMap<>())
                        .computeIfAbsent(p.state, k -> new ArrayList<>()).add(p);
            }
            if (p.qualification != null) {
                groups.computeIfAbsent("QUALIFICATION", k -> new LinkedHashMap<>())
                        .computeIfAbsent(Labels.label(p.qualification), k -> new ArrayList<>()).add(p);
            }
        }
        List<GroupMetric> gm = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<CandidateProfile>>> type : groups.entrySet()) {
            for (Map.Entry<String, List<CandidateProfile>> entry : type.getValue().entrySet()) {
                List<CandidateProfile> members = entry.getValue();
                GroupMetric m = new GroupMetric();
                m.runId = run.id;
                m.groupType = type.getKey();
                m.groupValue = entry.getKey();
                m.population = members.size();
                int allocated = 0;
                double sumSuit = 0;
                int prefSatisfied = 0;
                for (CandidateProfile p : members) {
                    Assignment a = byCandidate.get(p.id);
                    if (a != null) {
                        allocated++;
                        sumSuit += a.suitability;
                        Map<Long, Integer> prefs = prefMap.getOrDefault(p.id, Map.of());
                        if (prefs.containsKey(a.opportunityId)) prefSatisfied++;
                    }
                }
                m.allocated = allocated;
                m.avgSuitability = allocated > 0 ? r1(sumSuit / allocated) : 0;
                m.preferenceSatisfaction = allocated > 0 ? r1(prefSatisfied * 100.0 / allocated) : 0;
                m.allocationRate = m.population > 0 ? r1(allocated * 100.0 / m.population) : 0;
                gm.add(m);
            }
        }
        groupMetrics.saveAll(gm);

        // ---- Geography metrics ----
        Map<String, Integer> demandByState = new LinkedHashMap<>();
        for (CandidateProfile p : candidates) {
            if (p.state != null) demandByState.merge(p.state, 1, Integer::sum);
        }
        Map<String, Integer> capacityByState = new LinkedHashMap<>();
        for (Opportunity o : opps) {
            capacityByState.merge(o.state, capacityOverrides.getOrDefault(o.id, o.capacity), Integer::sum);
        }
        for (String s : capacityByState.keySet()) demandByState.putIfAbsent(s, 0);
        Map<String, Integer> allocatedByState = new HashMap<>();
        for (CandidateProfile p : candidates) {
            if (byCandidate.containsKey(p.id) && p.state != null) {
                allocatedByState.merge(p.state, 1, Integer::sum);
            }
        }
        List<GeoMetric> geoms = new ArrayList<>();
        for (Map.Entry<String, Integer> e : demandByState.entrySet()) {
            GeoMetric m = new GeoMetric();
            m.runId = run.id;
            m.state = e.getKey();
            m.demand = e.getValue();
            m.capacity = capacityByState.getOrDefault(e.getKey(), 0);
            m.allocated = allocatedByState.getOrDefault(e.getKey(), 0);
            m.unmetDemand = Math.max(0, m.demand - m.allocated);
            m.pressure = m.capacity > 0 ? r1(m.demand * 1.0 / m.capacity) : 0;
            m.allocationRate = m.demand > 0 ? r1(m.allocated * 100.0 / m.demand) : 0;
            geoms.add(m);
        }
        geoMetrics.saveAll(geoms);

        // ---- Conflict metrics (genuinely contested opportunities only) ----
        List<ConflictMetric> conflicts = new ArrayList<>();
        for (Opportunity o : opps) {
            int eligible = eligiblePerOpp.getOrDefault(o.id, 0);
            int seats = capacityOverrides.getOrDefault(o.id, o.capacity);
            if (eligible <= seats) continue;
            ConflictMetric m = new ConflictMetric();
            m.runId = run.id;
            m.opportunityId = o.id;
            m.eligibleCount = eligible;
            m.seatCount = seats;
            m.unmetDemand = eligible - seats;
            m.pressure = seats > 0 ? r1(eligible * 1.0 / seats) : 0;
            conflicts.add(m);
        }
        conflictMetrics.saveAll(conflicts);
    }

    private double r1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
