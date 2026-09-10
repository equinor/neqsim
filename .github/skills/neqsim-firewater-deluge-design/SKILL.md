---
name: neqsim-firewater-deluge-design
description: Fire-water and deluge coverage design for process areas — NORSOK S-001 / ISO 13702 / NFPA 15 application rates, area versus dedicated object protection, deluge nozzle-net sizing from both the flow and the spacing criterion, fire-monitor screening with wind drift, hydraulic-feasibility gating against an existing fire-water system, and the active-versus-passive substitution rules. USE WHEN a task asks whether an area has adequate deluge coverage, how many nozzles are needed, whether passive fire protection can replace fire water, whether monitors can replace a fixed system, or whether an existing fire-water system can absorb a new deluge section. Anchors on neqsim.process.safety.firewater.
---

# Fire-water and deluge coverage design

Sizing and adjudicating **active** fire protection for a process area. Complements
`neqsim-consequence-analysis` (which sizes the fire), `neqsim-relief-flare-network`
(which sizes the relief), and `neqsim-depressurization-mdmt` (which removes the
inventory). This skill answers the question those three do not: *how much water,
where, and is it even the right barrier?*

## When to use

- A finding or a TTS/verification item says an area lacks deluge coverage.
- A modification adds equipment to an area and the fire-water demand must be re-established.
- Somebody proposes passive fire protection, or fire monitors, *instead of* deluge.
- You must decide between blanket area coverage and dedicated object protection.
- You need to know whether an existing fire-water system can take a new deluge section.

## The four questions, in order

Answer them in this order. Reversing the order is the usual way these studies go wrong.

1. **What fire are we protecting against?** Get this from `neqsim-consequence-analysis`.
   A pressurised **gas jet fire** is not extinguished by water — the creditable function
   is cooling of exposed surfaces and control of escalation. A **liquid pool fire** can be
   controlled and extinguished by foam-water. If the area is labelled "gas" but holds lube
   and seal-oil tanks, the pool-fire case is still live and is a common omission.
2. **Can the inventory be removed faster than the target fails?** NORSOK S-001 makes
   blowdown the *primary* means of protecting a pressurised inventory. Run
   `DepressurizationSimulator` against `PfpDemandCalculator.bareSteelTimeToCriticalS()`.
   Thick-walled vessels frequently outlive the blowdown; thin support steel frequently
   does not — that asymmetry, not a blanket rule, is what decides where protection goes.
3. **What does the requirement actually demand?** See the requirement table below, and
   read the *edition* of the standard carefully (see "Traps").
4. **Only then**: size the water, lay out the nozzles, and test the hydraulics.

## Java API

`neqsim.process.safety.firewater` (Java 8, all classes `Serializable`, all emit
schema-versioned JSON via `toJson()`).

```java
// 1. demand: blanket area coverage
FireWaterDemandCalculator area = new FireWaterDemandCalculator(510.0)
    .setAreaRate(FireWaterDemandCalculator.NORSOK_PROCESS_AREA_LPM_M2)  // 10 (l/min)/m2
    .setDurationMin(30.0)
    .setFoamConcentratePercent(1.0);
double lpm = area.totalDemandLpm();          // 5100
double m3h = area.totalDemandM3PerHour();    // 306
double vol = area.waterVolumeM3();           // 153

// 1b. demand: dedicated object protection instead
FireWaterDemandCalculator obj = new FireWaterDemandCalculator(0.0).setAreaRate(0.0);
obj.addHorizontalVessel("HA-13-0001", 0.72, 6.606, 10.0);   // shell OD, length, rate
double saving = 1.0 - obj.totalDemandLpm() / area.totalDemandLpm();

// 2. nozzle net — governed by the LARGER of the flow and spacing criteria
DelugeNozzleLayout.Nozzle hv26 = new DelugeNozzleLayout.Nozzle(
    "HV26", 42.9, 3.5, 3.0, 1.0);   // K [(l/min)/sqrt(bar)], p_min, max spacing, clear depth
DelugeNozzleLayout net = new DelugeNozzleLayout(510.0, 10.0, hv26)
    .setOperatingPressureBarg(5.0)
    .setCoverageEfficiency(1.0);    // < 1.0 to tighten the grid for wind or shadowing
net.nozzleCountFromFlow();          // 54
net.nozzleCountFromCoverage();      // 57
net.nozzleCount();                  // 57 — coverage governs
net.governingCriterion();           // "coverage"
net.gridSpacingM();                 // 2.99
net.isClearanceAdequate(0.8);       // false for an HV nozzle needing 1.0 m

// 3. monitors as an alternative
FireMonitorCoverage mon = new FireMonitorCoverage(510.0, 10.0)
    .setMonitors(4, 4000.0).setGeometry(40.0, 120.0)
    .setWind(15.0, 12.0)                       // wind speed, droplet fall height
    .setCharacteristicTargetDimensionM(25.5)
    .setLineOfSightObstructed(true);
mon.nominalDensityLpmPerM2();     // 31.4 — passes in still air
mon.effectiveDensityLpmPerM2();   //  3.7 — fails once the pattern drifts
mon.verdict();

// 4. can the existing system take it?
FireWaterCoverageAssessment gate = new FireWaterCoverageAssessment(510.0, 5100.0)
    .setCoveredAreaM2(0.0)
    .setSpareSupplyFlowLpm(16748.0)
    .setWorstCasePressureMarginBar(-0.9, "XX-71-0010B")   // worst DESIGN case, not normal case
    .setRequiredPressureMarginBar(0.5);
gate.verdict();          // SUPPLY_PRESSURE_DEFICIT
gate.recommendation();

// 5. active vs passive — what the framework does and does not allow
ActiveFireProtectionScreening s = new ActiveFireProtectionScreening(
        ActiveFireProtectionScreening.FireScenario.GAS_JET_FIRE)
    .setIncidentHeatFluxKWPerM2(200.0)
    .setBlowdownTimeToTargetS(359.0)
    .setBareSteelTimeToCriticalS(98.7)
    .setLoadBearingStructureExposed(true)
    .setNozzlesCanBePlacedAboveEquipment(false);
s.isWaterExtinguishing();             // false for a gas jet fire
s.isPassiveSubstitutionPermitted();   // false — always
s.isBlowdownFasterThanFailure();      // false here: thin steel loses the race
s.rankedMeasures();
```

## Application rates

| Basis | Applies to | Rate |
|---|---|---|
| NORSOK S-001 Ed.4 §20.4.4 (Ed.5/6 §21) | Process areas **and equipment surfaces** | 10 (l/min)/m² |
| NORSOK S-001 Ed.4 §20.4.4 | Wellhead, riser balconies, FPSO turret manifolds | 20 (l/min)/m² |
| NORSOK S-001 Ed.4 §20.4.4 | Water at design pressure after a confirmed fire signal | ≤ 30 s |
| NFPA 15 | Exposure protection of vessel shells | 10.2 (l/min)/m² (0.25 gpm/ft²) |
| Common project practice | Enclosed utility/machinery rooms, sprinkler | 6 (l/min)/m² |

`NORSOK S-001 does not state a fire-water duration.` Take the duration from the project
basis (30 min is the usual foam figure and is often carried across), and say where it
came from.

## Traps

- **Clause renumbering.** NORSOK S-001 **Ed.4 (2008)**: clause 20 = fire fighting systems,
  clause 21 = escape and evacuation. **Ed.5 (2018) / Ed.6 (2020)**: clause 20 = passive fire
  protection, clause **21 = active fire protection**. A finding citing "21.4.4" is citing
  Ed.5/6. Check which edition the installation's own basis invokes before agreeing that a
  requirement applies. Ed.6's foreword states it is a modality-only revision of Ed.5.
- **Fire water is never a substitute for passive fire protection.** The Norwegian Facilities
  Regulations §29 forbid taking credit for the cooling effect of fire-fighting equipment when
  designing PFP, and §11 forbids crediting fire water when establishing design accidental
  loads. NORSOK S-001 Ed.4 §19.4.2/§19.4.3 say the same. **The converse is also not permitted**:
  putting PFP on one item does not discharge an area fire-water requirement, because the
  requirement is written against the area.
- **Design on flow alone and you will under-count the nozzles.** A high-K nozzle meets the
  density with few nozzles spaced far apart, leaving dry patches. Always take the larger of
  the flow and spacing counts.
- **Take the pressure margin from the worst *design* case, not the normal case.** Mature
  fire-water systems are commonly pressure-limited, not flow-limited, and the margin can be
  negative in a two-pump case while looking healthy in the three-pump case.
- **"Nozzles cannot go above the equipment" is not a reason to have no coverage.** It is a
  reason to use side-mounted nozzles or a dedicated spray ring. If a project basis excludes
  coverage over an item for maintenance reasons, that is a *deviation*, and it needs to be
  recorded and accepted as one.
- **Monitors are not equivalent to a fixed system.** NORSOK S-001 Ed.4 permits monitor
  substitution only for the turret, and §20.4.7 then binds monitors to the same density and
  functionality. On an open deck, wind drift and shadowing usually settle the question.

## Chains to

| Direction | Skill | Why |
|---|---|---|
| upstream | `neqsim-consequence-analysis` | fire type, heat flux, flame reach |
| upstream | `neqsim-depressurization-mdmt` | blowdown time, the primary barrier |
| upstream | `neqsim-technical-document-reading` | area geometry, nozzle K-factors, hydraulic report |
| downstream | `neqsim-process-safety` | barrier register, performance standards, LOPA |
| downstream | `neqsim-standards-lookup` | clause traceability into `results.json` |
| downstream | `neqsim-professional-reporting` | figure→discussion→linked_results |

## Validation

`src/test/java/neqsim/process/safety/firewater/FireWaterDesignTest.java` — 23 tests covering
the orifice law, the two nozzle-count criteria, the wind-drift model, the verdict routing and
the JSON contract.
