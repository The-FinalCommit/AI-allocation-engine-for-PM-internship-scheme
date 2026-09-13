# PRAGATI — Data Model & Invariants

Entities are JPA-managed. The demo uses in-memory H2; the `prod` profile points at MySQL 8.
All timestamps are UTC.

## People & organization

### User
Identity and role.
- `id`, `email` (unique), `passwordHash` (BCrypt), `name`, `role` (`CANDIDATE` | `PROVIDER` | `ADMIN`)
- Demo accounts are seeded at startup; per-scenario candidate users are synthetic.

### CandidateProfile
One per candidate user (unique on `userId`).
- `userId`, `fullName`, `phone`, `dob`, `qualification` (BACHELORS, POST_GRADUATE,
  HIGHER_SECONDARY, DOCTORAL), `candidateStatus` (STUDENT, GRADUATE, WORKING),
  `state`, `district`, `locationType` (RURAL, URBAN, SEMI_URBAN), `bio`,
  `experienceYears`, `updatedAt`

### CandidateSkill
- `candidateId`, `rawLabel` (as typed / extracted), `canonical` (normalized),
  `source` (PROFILE, RESUME), `validated` (accepted by the candidate)

### CandidateInterest
- `candidateId`, `sector` (one of the 10 sectors)

### CandidatePreference
Ranked wish, not a constraint.
- `candidateId`, `opportunityId`, `rank` (1 = most desired)

### ResumeFile
- `candidateId`, `fileName`, `contentType`, `sizeBytes`, `storedPath`,
  `status` (UPLOADED, EXTRACTING, READY, FAILED), `extractedAt`

## Supply side

### Provider
- `id`, `userId` (nullable — seeded orgs have none), `orgName`, `orgType`, `state`, `about`

### Opportunity
- `id`, `providerId`, `title`, `sector`, `state`, `city`, `capacity` (seats),
  `durationMonths`, `minQualification`, `status` (ACTIVE, PAUSED, CLOSED),
  `description`, timestamps

### OpportunitySkill
- `opportunityId`, `skill` (canonical), `mandatory` (true = required to be eligible)

## Dataset & runs

### DatasetSnapshot
Records each loaded scenario version — the reproducibility anchor.
- `id`, `scenario` (the 7 keys), `version` (per scenario, incrementing), `seed` (fixed per
  scenario), `fingerprint` (SHA-256 over key|seed|counts), `candidateCount`,
  `opportunityCount`, `seatCount`, `createdAt`

### AllocationRun
- `id`, `datasetSnapshotId`, `policyKey`, `policyWeights` (JSON), `fullCoverage`,
  `fairnessFloorPct`, `status` (PENDING, RUNNING, COMPLETED, INFEASIBLE, CANCELLED, FAILED),
- totals: `candidatesConsidered`, `eligiblePairs`, `assigned`, `unassignedEligible`,
  `seatsAvailable`, `seatsFilled`, `averageSuitability`, `preferenceSatisfactionPct`
- baseline totals (same fields for the sequential baseline)
- provenance: `solverStatus` (e.g. `OPTIMAL`), `objectiveValue`, `variableCount`,
  `constraintCount`, `solverRuntimeMs`, `totalRuntimeMs`, `notes`
- `createdAt`, `completedAt`

### Assignment
One row per made assignment. This is the heart of explainability.
- `id`, `runId`, `candidateId`, `opportunityId`
- `suitability` (total 0–100), `factorBreakdown` (JSON: each factor's weight, value and
  points — skills, mandatory coverage, qualification, interest, location, preference,
  experience), `candidateRank` (position of this opportunity in the candidate's preference
  list), `status` (ACTIVE, MOVED, REMOVED — updated by reallocations)

### GroupMetric
Fairness outcomes by group (e.g. location type, qualification).
- `runId`, `group`, `groupKey`, `candidates`, `eligible`, `assigned`, `sharePct`,
  `averageSuitability`

### GeoMetric
Geographic outcomes by state.
- `runId`, `state`, `demand` (eligible candidates), `capacity` (seats), `allocated`,
  `unmetDemand`

### ConflictMetric
Competition pressure by opportunity.
- `runId`, `opportunityId`, `eligible`, `capacity`, `competitionRatio`

## Simulation & change

### SimulationRun
A what-if sandbox against a base run.
- `id`, `baseRunId`, `weightChanges` (JSON), `fullCoverage`, `fairnessFloorPct`,
  `affectedCount`, `stabilityPct`, `runtimeMs`, `createdAt`

### Reallocation
A child run derived from a parent under operational changes.
- `id`, `parentRunId`, `changeDescription`, `moved`, `added`, `removed`, `unchanged`,
  `createdAt`
- diff entries (see API) give per-candidate before/after and a plain-language reason.

### AuditEntry
Append-only.
- `id`, `actorRole`, `actorEmail`, `action`, `detail`, `createdAt`
Recorded for: sign-in/out, scenario loads, run starts/cancels, simulations, reallocations,
provider opportunity changes, candidate profile/skill/interest/preference/resume changes.

## Invariants enforced

1. **Eligibility is a hard gate** — a candidate with an ineligible pair has no assignment
   variable and can never be assigned, regardless of score.
2. **One seat per candidate** — an assignment model constraint; also unique at the data
   layer per run.
3. **Capacity** — sum of assignments per opportunity ≤ `capacity`.
4. **Fairness floor** — when `fairnessFloorPct` is set, the protected group's minimum share
   is a hard constraint.
5. **Reproducibility** — same `DatasetSnapshot` (fingerprint) + same policy + same seed ⇒
   identical `Assignment` set.
6. **Immutability of history** — completed runs are never mutated; changes create child
   reallocations. Candidate profile edits affect *future* runs, not past ones.
7. **AI boundary** — `CandidateSkill.source=RESUME` rows are only candidates until
   `validated`; the AI service writes no assignments and no eligibility decisions.
