export interface Page<T> { content: T[]; totalElements: number; totalPages: number; }

export interface ScenarioSummary { key: string; name: string; description: string; active: boolean; lastLoadedAt: string | null; }
export interface DatasetSummary { key: string; name: string; candidateCount: number; opportunityCount: number; seatCount: number; version: number; fingerprint: string; synthetic?: boolean; }
export interface Overview {
  scenario: DatasetSummary | null;
  readiness: { ready: number; partial: number; incomplete: number; average: number };
  allocation: { allocated: number; unallocated: number; totalCandidates: number; seatUtilization: number; avgSuitability: number; preferenceSatisfaction: number; runNumber: number } | null;
  latestRun: { id: number; number: number; code: string; status: string; solverStatus: string | null; scenario: string; createdAt: string; totalRuntimeMs: number | null; stages: { stage: string; at: string; ms: number }[] } | null;
  aiMode: string;
}
export interface RunSummary {
  id: number; number: number; code: string; scenario: string; policyName: string;
  status: string; solverStatus: string | null; createdAt: string; completedAt: string | null;
  totalRuntimeMs: number | null; stale: boolean; reallocation: boolean; parentRunNumber: number | null;
}
export interface RunDetail {
  run: RunSummary;
  dataset: DatasetSummary | null;
  infeasibleMessage: string | null;
  stages: { stage: string; at: string; ms: number }[];
  provenance: {
    runId: string; runNumber: number; scenario: string; datasetVersion: string; datasetFingerprint: string;
    candidates: number; opportunities: number; seats: number; seed: number; policyName: string; policyKey: string;
    policyVersion: string; weights: Record<string, number>; optimizer: string; solverStatus: string | null;
    objective: number | null; variables: number | null; constraints: number | null; solverRuntimeMs: number | null;
    totalRuntimeMs: number | null; workers: number; solverSeed: number;
  } | null;
}
export interface Comparison {
  globalAllocated: number; baselineAllocated: number; globalSuitability: number; baselineSuitability: number;
  globalPreferenceSatisfaction: number; baselinePreferenceSatisfaction: number; seatUtilization: number;
  globalObjective: number; baselineObjective: number; globalSolverSeconds: number; summary: string;
}
export interface WhyFactor { key: string; label: string; fit: number; weight: number; contribution: number; }
export interface WhyDto {
  candidateId: number; candidateName: string; opportunityId: number; opportunityTitle: string; sector: string;
  state: string; suitability: number; factors: WhyFactor[]; narrative: string; reason: string;
  eligibleCount: number; seatCount: number; preferenceRank: number | null; runId: number; runNumber: number;
}
export interface GroupMetric { groupValue: string; population: number; allocated: number; rate: number; avgSuitability: number; preferenceSatisfaction: number; }
export interface Fairness { ruralUrban: GroupMetric[]; states: GroupMetric[]; }
export interface GeoRow { state: string; demand: number; capacity: number; allocated: number; unmetDemand: number; pressure: number; allocationRate: number; }
export interface Movement { candidateId: number; candidateName: string; before: string | null; after: string | null; beforeScore: number | null; afterScore: number | null; status: string; }
export interface ConflictRow { opportunityId: number; title: string; eligible: number; seats: number; allocated: number; unmetDemand: number; pressure: number; }
export interface PolicyPreset { id: number; key: string; name: string; description: string; weightsJson: string; fullCoverage: boolean; fairnessFloorPct: number | null; isDefault: boolean; version: string; }
export interface SimulationRow { id: number; baseRunId: number; policyName: string; status: string; solverStatus: string | null; createdAt: string; runtimeMs: number | null; affected: number | null; stability: number | null; metricsJson: string | null; }
export interface AuditRow { id: number; at: string; action: string; actor: string; summary: string; details: Record<string, any> | null; }
export interface JudgeStep { n: number; title: string; what: string; why: string; how: string; url: string; }
export interface Technical {
  ai: { status: string; aiMode?: string; engineDescription?: string; embeddingModel?: string | null; optimizer?: string; optimizerVersion?: string; serviceVersion?: string; note?: string };
  solver: { lastRun: string | null; solverStatus: string | null; objective: number | null; eligiblePairs: number | null; variables: number | null; constraints: number | null; solverRuntimeMs: number | null; totalRuntimeMs: number | null } | null;
  dataset: { candidates: number; opportunities: number; seats: number; scenario: string | null; version: number | null; fingerprint?: string; seed?: number } | null;
  stack: Record<string, string>;
  links: Record<string, string>;
}
export interface DqCheck { area: string; status: string; what: string; why: string; fix: string; affected: number; }
export interface OppInfo {
  id: number; title: string; sector: string; state: string; city?: string | null;
  capacity: number; durationMonths: number; minQualification: string; active: boolean;
  mandatorySkills: string[]; niceSkills: string[];
}
export interface FitFactor { key: string; label: string; fit: number; weight: number; contribution: number; lostPoints: number; explanation: string; }
export interface FitAnalysis {
  opportunity: OppInfo; eligible: boolean; eligibilityReasons: string[];
  overallScore: number; possibleScore: number; lostPoints: number;
  recommendationLabel: string; weightsSource: string;
  factors: FitFactor[]; strongestMatches: string[]; topGaps: string[]; preferenceRank: number | null;
}
export interface Recommendation {
  rank: number; opportunity: OppInfo; eligible: boolean; overallScore: number;
  strongestMatches: string[]; topGap: string | null; preferenceRank: number | null; reason: string;
}
export interface Recommendations { weightsSource: string; note: string; items: Recommendation[]; }
export interface SkillGap {
  opportunity: OppInfo; eligible: boolean;
  coveredSkills: string[]; mandatoryGaps: string[]; preferredGaps: string[];
  readinessPercent: number; assistantSummary: string;
}
export interface GuidanceRes { answer: string; engine: string; grounding: string; aiAssisted: boolean; }
export interface FieldSuggestion { field: string; value: string; confidence: string; evidence: string; }
export interface ResumeSuggestionsView {
  resumeId: number | null; status: string; statusMessage: string | null;
  fields: FieldSuggestion[]; skills: string[]; interests: string[];
}

export interface Opp {
  id: number; title: string; provider?: string; sector: string; state: string; city?: string | null;
  capacity: number; durationMonths: number; minQualification: string; status: string;
  mandatorySkills: string[]; niceSkills?: string[]; eligible?: boolean; eligibilityReasons?: string[];
  myPreferenceRank?: number | null; description?: string; fitScore?: number | null;
}
export interface OppDetail { card: Opp; description: string; }
export interface CandidateAllocation {
  state: string; message: string; runId: number | null; runNumber: number | null; opportunityTitle: string | null;
  sector: string | null; opportunityState: string | null; suitability: number | null; preferenceRank: number | null; runAt: string | null;
}
export interface ReadinessItem { key: string; label: string; maxScore: number; earned: number; status: string; detail: string; }
export interface Readiness { overall: number; items: ReadinessItem[]; }
