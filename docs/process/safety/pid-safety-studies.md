---
title: Safety studies on a generated P&ID
description: Size relief and blowdown valves, set trip points, screen SIL and quantify HAZOP safeguards from the converged simulation behind a synthesized P&ID.
---

# Safety studies on a generated P&ID

`PidSafetyStudies` takes the safeguards that the P&ID synthesis proposed (relief valves, blowdown
valves, trips) and calculates them from the converged process simulation. The results are written back
onto the P&ID elements, so the drawings, the DEXPI file, the HAZOP preparation and the package report
all show the same numbers.

```java
PidDesignModel pid = PidDesignSynthesizer.synthesize(project, basis, NorsokPidRuleCatalog.completeProposals());
PidStandardConnectionProposals.complete(pid, project.getEngineeringProcessSystem());
PidSafetyStudies.Result studies = PidSafetyStudies.run(pid, project.getEngineeringProcessSystem(),
    PidSafetyStudies.Policy.screening().segmentVolume("23-KA-001", 2.0));
PidSimulationBindings bindings = PidSimulationBindings.from(project, pid);
ProfessionalPidPackageExporter.export(project, pid, bindings, metadata, outDir);
```

Run the studies after `PidStandardConnectionProposals.complete(...)` and before
`PidSimulationBindings.from(...)`: the bindings fingerprint the proposal.

## What is calculated

| Study | Method | Written to |
| --- | --- | --- |
| Design pressure | max(operating x 1.10, operating + 3.5 bar), gauge | relief valve `studyProtectedDesignPressureBarg`; equipment callout `DESIGN: ... (PROP.)` |
| Relief, blocked outlet | Inlet flashed at relieving pressure; all vapour relieved | `studyReliefCases` |
| Relief, external fire | API 521: Q = 43200 F A^0.82 W on the wetted area at normal liquid level; latent heat from a bubble-point flash at relieving pressure | `studyReliefCases` |
| Relief, gas blow-by | A liquid valve from a higher-pressure vessel fails open: IEC 60534 choked gas flow with the valve's rated Cv | `studyReliefCases`; the HAZOP of the upstream vessel |
| Relief valve size | API 520 gas sizing for the governing case; standard orifice letter | `studySetPressureBarg`, `studyRelievingRateKgPerH`, `studyOrifice`; drawing `SET 64.9 barg / J`; DEXPI `SetPressureHigh` |
| Blowdown | `DepressurizationSimulator`, vapour withdrawal, smallest orifice reaching the lower of 6.9 barg and 50 % of design pressure within 15 min (API 521) | `orificeSizing`, `studyPeakBlowdownRateKgPerH`, `studyMinimumFluidTemperatureC`; drawing `RO 8 mm` |
| Flare load | Simultaneous blowdown of every sized segment against the largest single relief case | `flareLoad` in the report |
| Trip set points | Midway between operating value and the relief set pressure or design limit; level HH 85 %, LL 15 %; low flow 50 %; low pressure 50 % of operating | `setPoint`, `studySetPointBasis`; drawn next to the function |
| SIL target | NOG 070 minimum SIL for the function type | `silTarget` |
| SIL screening | IEC 61511 PFDavg of sensor, logic solver and the inlet isolation valves, upgraded step by step: partial-stroke testing, redundant sensors, redundant final element | `voting`, `studyPfdAvg`, `studySilVerification` |
| HAZOP | Every credited safeguard's result is quoted on the matching deviation | `quantifiedSafeguardEvidence` in `pid-hazop-study.json` |

The package exporter writes `pid-safety-studies.json` whenever any element carries study results, and
`professional-pid-readiness.json` points to it under `safetyStudies`.

## What stays open

Findings in the result and the report list what the studies could not settle. Typical findings:

- `BLOWDOWN_SEGMENT_VOLUME_REQUIRED`: the model has no geometry for a compressor segment. Declare it with
  `Policy.segmentVolume(tag, m3)`.
- `BLOWDOWN_LOW_TEMPERATURE`: the fluid falls below the carbon-steel limit (-29 C by default) during
  blowdown. Review the material selection.
- `FIRE_CASE_SUPERCRITICAL`: the liquid has no distinct latent heat at relieving pressure. Use the
  API 521 supercritical method.
- `BLOWBY_VALVE_CV_ASSUMED`: the liquid valve runs fully open in the model, so its operating Cv is used
  as the rated Cv. Use the datasheet Cv.
- `SIF_FINAL_ELEMENT_REQUIRED`: a trip has no final element yet (typically the low-low level trip).
- `SIF_REDUNDANT_FINAL_ELEMENT`: the SIL target needs a second valve in series, which is not drawn.

## Governance

- Results only replace placeholders (`..._REQUIRED`, `HOLD`). Project values are never overwritten.
- Every touched element keeps `REVIEW_REQUIRED` and gets `safetyStudyReference` starting with
  `NEQSIM-SAFETY-SCREENING-STUDY` and naming the policy.
- `Policy.screening()` values are documented screening assumptions. The reliability data are marked
  `SCREENING_NOT_CERTIFIED`. Supply project values with `designPressure(...)`, `flareBackPressure(...)`,
  `reliability(...)` and `segmentVolume(...)`; the policy name then becomes `PROJECT_POLICY`.
- The studies do not replace the HAZOP workshop, LOPA, the SRS or the relief and flare system study.
  Two-phase relief, flare network back pressure and ESD valve closure times are not calculated.
- Blowdown valves are proposed fail-open, as is standard for depressurisation valves.

## Related

- [HAZOP and LOPA to draft SRS handoff](hazop-lopa-srs-handoff.md)
- [SIF reliability and degraded modes](sif-reliability-and-degraded-modes.md)
- [P&ID design synthesis](../../pid-design-synthesis.md)
