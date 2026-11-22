# Rejected trial: not selected for production

`OptimizedVUflash.java.txt` is the exact coupled-step proposal compiled for the
64-JVM30-warmup/12-measurement matrix. The baseline is head29d8d307. Final selected
production source has **no coupled step**. Keep these records to explain why the
numerically promising proposal was rejected by broader qualification.

The timing matrix has768 raw samples; all cells have the same declared1e-6/1e-5
V/U gates, but achieved residuals and solver work differ. Candidate iterations
fall from179–419 to80 per20-target batch and allocations to0.25–0.65 of baseline.
Process-pair timing ratios range0.398–1.052; the PR-classical gas control includes
a5.2% slower process. These are limited workload observations, not general speed,
strict-inventory throughput or selected-source performance.

`separator-{baseline,candidate}.csv` columns are:
startT_K,startP_bar,endT_K,endP_bar,phaseCount,firstPhaseType,iterations,
lastRunConverged,Vspec_internal,Uspec_J,V_internal,U_J.
Multiply internal volume by1e-5 for m3. Both traces contain31 VU calls.
The original separator variance assertion passes the baseline even though20 VU
calls are rejected. The coupled trial changes the trajectory and fails that
assertion; neither result qualifies the full controlled transient. See #4583.

`returned-states.csv` and `inventory-candidate.csv` are trial results only.
`VuEvidence.java.txt` is a standalone reporter; its fixture class name is updated
to the final `VUCaloricStateQualificationTest` (the fixture/central-difference
methods are unchanged). Recompile the archived experimental production source
first on the common classpath to reproduce trial states. Do not use the selected
production source to claim the trial's strict-convergence or timing outcomes.
