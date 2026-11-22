---
title: "Sample validation report of the synthetic SURF design study"
description: "Validation report written by neqsim surf-study --demo (synthetic data, readiness SCREENING)."
---

# SURF study validation report

Overall: **PASS** (31 checks, 20 pass, 0 fail, 11 warn). Readiness: **SCREENING** - screening grade: synthetic inputs, 8 open specialist hand-off(s), 5 data gap(s)

| Check | Result | Description | Detail |
|---|---|---|---|
| V-INPUT | PASS | Specification has no input errors |  |
| V-CONCEPTS | PASS | At least two meaningful concepts are compared | A, B, C |
| V-CONVERGE | WARN | Every case of the reference/selected concept solved: coupling converged, process model converged, no retained hydraulic fallback | C/BASE: hydraulic fallback or failure on FL-TRUNK (BEGGS_BRILL_FALLBACK_DARCY); no distributed pro (concepts that are not reference or selected are reported infeasible and excluded from ranking) |
| V-FALLBACK | WARN | No retained hydraulic fallback or failed-edge status in the reference/selected concept | C/BASE/FL-TRUNK: BEGGS_BRILL_FALLBACK_DARCY |
| V-HAND | PASS | Hydrostatic, friction, thermal and mass-closure hand checks agree with the solved values | 27 checks; failed: none |
| V-MESH | PASS | Spatial-sensitivity gate (study mesh against a finer mesh) | max dp 0.279 bar, max dT 2.16 K (tolerances {'p_bar': 1.0, 't_k': 3.0}) |
| V-COOLDOWN | PASS | Lumped cooldown time agrees with the independent RK4 integration |  |
| V-FLUID | WARN | SRK pure-water properties against tabulated water | SRK pure-water heat capacity deviates 14.0% |
| V-DIMENSIONS | PASS | OD, wall and inside-diameter relations and corrosion allowance consistent |  |
| V-RECONCILE | PASS | Model, register, design basis and cost basis agree (counts, lengths, diameters) |  |
| V-COST | WARN | Cost basis present for every concept (currency, price year, class, scope) | screening-level estimate from the existing SURFCostEstimator; not a maturity claim |
| V-FEASIBILITY | PASS | Cost ranking only among concepts that are feasible in every case | selected: A |
| V-STRUCTURAL | WARN | Structural kernel results: screened, blocked and not-assessed lines are explicit | 1 line(s) screened with the DNV-ST-F101 kernel (synthetic inputs); 11 not assessed (evidence missing); blocked: none |
| V-STANDARDS | WARN | Standards register makes no conformity claim; text availability is stated | 23 standards listed; none read at clause level |
| V-CRITERIA | PASS | Every acceptance criterion was assessed |  |
| V-BENCHMARK | WARN | Independent multiphase benchmark against public/lab/field data | none available in this toolkit: Beggs and Brill carries no general experimental qualification; hand checks cover single-phase hydrostatics, friction, thermal and mass only |
| V-ENERGY | WARN | Independent energy/enthalpy closure of the network | conservative mixing is owned by the solver; only mass closure is independently re-computed |
| V-HANDOFFS | WARN | Specialist hand-offs for unsupported analyses are listed with prepared inputs | 8 open hand-off(s): HO-01, HO-02, HO-03, HO-04, HO-05, HO-06, HO-07, HO-08 |
| V-GAPS | WARN | Data gaps and assumptions are recorded | 5 data gap(s), 6 assumption(s) |
| V-DWG-LAYOUT | PASS | Every sheet passes the automated layout checks (bounds, overlaps, minimum text height) |  |
| V-DWG-NUMBERS | PASS | Drawing numbers are unique and the sheet count matches the register | 15 sheets |
| V-DWG-ASSETS | PASS | Every model asset and line of the reference concept is drawn, and no drawn identity is unknown to the dataset | missing: none; unknown: none |
| V-DWG-TITLEBLOCK | PASS | Checker and approver fields stay unassigned until a person is recorded | checked_by=not assigned; approved_by=not assigned |
| V-DWG-FILES | PASS | SVG, PNG and the vector PDF exist for every sheet | SURF-SYN-001_drawings.pdf; 15 sheets |
| V-DOC-TOKENS | PASS | Every data token resolves with its unit | unresolved: []; unit errors: [] |
| V-DOC-BADTOKENS | PASS | No invalid-value marker or unresolved token appears in the document text |  |
| V-DOC-SECTIONS | PASS | The 20 mandatory report chapters are present |  |
| V-DOC-CAPTIONS | PASS | Numbered figures and tables exist | 19 figures, 34 tables |
| V-DOC-DATASET | PASS | The report was built from the retained dataset (hash agrees) | 7a2b4b0b67d3d98726e0d58d |
| V-DOC-OPEN | PASS | Open items, hand-offs and data gaps are visible in the document |  |
| V-DOC-PDF | WARN | PDF export | no PDF written (Word or LibreOffice not available or --no-word) |
