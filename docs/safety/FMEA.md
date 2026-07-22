---
title: "FMEA / FMECA Worksheet (IEC 60812)"
description: "Failure Modes and Effects Analysis using FMEAWorksheet — Severity, Occurrence and Detection scoring, RPN = S·O·D criticality ranking, and configurable threshold for hot-list filtering per IEC 60812."
---

# FMEA / FMECA Worksheet (IEC 60812)

`neqsim.process.safety.hazid.FMEAWorksheet` provides a Failure Modes, Effects
and Criticality Analysis worksheet aligned with IEC 60812 (2018). Each entry
captures the failure mode of a tagged component and its three risk dimensions.

## Risk Priority Number

$$
\mathrm{RPN} = S \cdot O \cdot D
$$

where each factor is on a 1–10 scale:

- **S** (Severity) — consequence severity if the mode occurs
- **O** (Occurrence) — likelihood of the mode
- **D** (Detection) — inverse detectability (10 = undetectable)

## Code pattern

```java
FMEAWorksheet fm = new FMEAWorksheet("Subsea Tree — XT-001");
fm.addEntry("PT-101", "Transmitter", "Drift high",
    "Sensor degradation",
    "Spurious ESD trip, lost production",
    4, 5, 3);    // S, O, D — RPN = 60
fm.addEntry("BDV-202", "Blowdown valve", "Fail to open",
    "Solenoid coil failure",
    "Cannot depressurize during fire — vessel rupture risk",
    9, 2, 6);    // RPN = 108

List<FMEAWorksheet.Entry> hotList = fm.criticalEntries(100); // RPN > 100
```

## Common thresholds

| Threshold | Action |
|-----------|--------|
| RPN > 200 | Mandatory mitigation — design change |
| RPN 100–200 | Mitigation recommended — added safeguard or inspection |
| RPN < 100 | Monitor in operating phase |

## See also

- [HAZOP Worksheet](HAZOP.md)
- [Event and Fault Trees](event_fault_trees.md)
