---
name: neqsim-firewater-deluge-design
calculation_basis: "screening"
version: "0.2.0"
description: "Fire-water and deluge coverage design per NORSOK S-001 / ISO 13702 / NFPA 15: area versus dedicated object demand, nozzle-net sizing from flow and spacing, fire monitors with wind drift, hydraulic feasibility, active-versus-passive substitution. USE WHEN: a task asks if an area has adequate deluge coverage, how many nozzles are needed, or whether monitors or PFP can replace fire water."
last_verified: "2026-10-18"
requires:
  python_packages: []
  java_packages: [neqsim.process.safety.firewater]
  env: []
  network: []
---

# Fire-water and Deluge Coverage Design

Use this skill for public, educational fire-water coverage screening. It turns a protected
area and an application rate into a water demand, a nozzle count and a grid pitch, and it
tests the two alternatives that get proposed whenever a fixed deluge net is inconvenient:
protecting the individual items instead of the area, and using fire monitors instead of a
fixed system.

## When to Use

- A verification finding says an area lacks active fire protection and the demand must be scoped.
- A modification adds hydrocarbon-bearing equipment to an area and the fire-water demand changes.
- Somebody proposes fire monitors, or passive fire protection, in place of deluge.
- You need a first nozzle count and grid pitch before a hydraulic network calculation.

## Inputs

- `protected_area_m2`: plan area for general area coverage, m².
- `area_rate_lpm_per_m2`: application rate, (l/min)/m². Public minima are 10 for process
  areas and equipment surfaces and 20 for wellhead areas and riser balconies.
- `objects`: tuple of `(tag, surface_area_m2, rate_lpm_per_m2)` for dedicated protection.
- `duration_min`, `foam_concentrate_percent`, `simultaneous_area_factor`.
- Nozzle: `nozzle_k_lpm_per_sqrt_bar`, `nozzle_min_pressure_barg`, `max_spacing_m`,
  `operating_pressure_barg`, `coverage_efficiency`.
- Monitor: `monitor_count`, `monitor_flow_lpm`, `wind_speed_m_s`, `fall_height_m`,
  `characteristic_dimension_m`, `line_of_sight_obstructed`.

## Outputs

- `area_demand_lpm`, `object_demand_lpm`, `total_demand_lpm`, `total_demand_m3_per_h`,
  `water_volume_m3`, `foam_concentrate_m3`, `object_to_area_ratio`.
- `nozzle_count_from_flow`, `nozzle_count_from_coverage`, `nozzle_count`,
  `governing_criterion`, `grid_spacing_m`, `delivered_density_lpm_per_m2`,
  `pressure_adequate`, `density_met`.
- `nominal_density_lpm_per_m2`, `drift_displacement_m`, `wind_coverage_fraction`,
  `effective_density_lpm_per_m2`, `verdict`.
- `assumptions` on every result.

## Engineering Method

The Python class `FireWaterCoverageModel` uses open, published relations only.

- Demand is the application rate times the protected area, plus the sum of dedicated
  object surfaces times their rates, times a simultaneous-release factor.
- Nozzle discharge follows the orifice law $Q = K\sqrt{p}$ with $K$ in
  $(\mathrm{l/min})/\sqrt{\mathrm{bar}}$.
- The nozzle count is the **larger** of the flow criterion (total discharge must meet the
  density) and the coverage criterion (area divided by the square of the maximum permitted
  spacing). Sizing on flow alone is the classic error: it gives too few, too widely spaced
  nozzles that meet the average density but leave dry patches.
- Monitor droplets are drifted downwind by $\Delta x = h\,u_{\mathrm{wind}}/v_t$, and the
  coverage loss is taken as linear in the drift over the characteristic target dimension.
- Shadowing is a flag, not a model: a monitor cannot wet a surface it cannot see.

## Python Usage Pattern

```python
from firewater_deluge_design import FireWaterCoverageModel

model = FireWaterCoverageModel()

demand = model.demand(
    protected_area_m2=420.0,
    area_rate_lpm_per_m2=10.0,
    objects=(("20-VA-001", model.horizontal_vessel_surface_m2(3.0, 9.0), 10.0),),
    duration_min=30.0,
)
print(demand.total_demand_m3_per_h, demand.object_to_area_ratio)

layout = model.deluge_layout(
    protected_area_m2=420.0,
    required_density_lpm_per_m2=10.0,
    nozzle_k_lpm_per_sqrt_bar=57.0,
    nozzle_min_pressure_barg=2.0,
    max_spacing_m=3.7,
    operating_pressure_barg=3.5,
)
print(layout.nozzle_count, layout.governing_criterion, layout.grid_spacing_m)

monitors = model.monitor_screening(
    target_area_m2=420.0,
    required_density_lpm_per_m2=10.0,
    monitor_count=2,
    monitor_flow_lpm=2400.0,
    wind_speed_m_s=12.0,
    fall_height_m=12.0,
)
print(monitors.effective_density_lpm_per_m2, monitors.verdict)
```

## The Four Questions, in Order

Answer them in this order. Reversing the order is the usual way these studies go wrong.

1. **What fire are we protecting against?** A pressurised **gas jet fire** is not
   extinguished by water; the creditable function is cooling of exposed surfaces and
   control of escalation. A **liquid pool fire** can be controlled and extinguished by
   foam-water. An area labelled "gas" that holds lube and seal-oil tanks still has the
   pool-fire case live, a common omission. Take the fire from `neqsim-consequence-analysis`.
2. **Can the inventory be removed faster than the target fails?** NORSOK S-001 makes
   blowdown the *primary* means of protecting a pressurised inventory. Run
   `DepressurizationSimulator` against `PfpDemandCalculator.bareSteelTimeToCriticalS()`.
   Thick-walled vessels frequently outlive the blowdown; thin support steel frequently
   does not, and that asymmetry decides where protection goes.
3. **What does the requirement actually demand?** See the application-rate table and
   the traps below, and read the *edition* of the standard carefully.
4. **Only then:** size the water, lay out the nozzles, and test the hydraulics.

## Java API (`neqsim.process.safety.firewater`)

Java 8, all classes `Serializable`, all emit schema-versioned JSON via `toJson()`. Do
**not** use `FireProtectionDesign.firewaterDemand(...)`
(`neqsim.process.mechanicaldesign.designstandards`) for these questions: it returns one
lumped m³/h figure and carries no coverage, nozzle, monitor or hydraulic-feasibility logic.

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

// 2. nozzle net, governed by the LARGER of the flow and spacing criteria
DelugeNozzleLayout.Nozzle hv26 = new DelugeNozzleLayout.Nozzle(
    "HV26", 42.9, 3.5, 3.0, 1.0);   // K [(l/min)/sqrt(bar)], p_min, max spacing, clear depth
DelugeNozzleLayout net = new DelugeNozzleLayout(510.0, 10.0, hv26)
    .setOperatingPressureBarg(5.0)
    .setCoverageEfficiency(1.0);    // < 1.0 tightens the grid for wind or shadowing
net.nozzleCountFromFlow();          // 54
net.nozzleCountFromCoverage();      // 57
net.nozzleCount();                  // 57, coverage governs
net.governingCriterion();           // "coverage"
net.gridSpacingM();                 // 2.99
net.isClearanceAdequate(0.8);       // false for an HV nozzle needing 1.0 m

// 3. monitors as an alternative
FireMonitorCoverage mon = new FireMonitorCoverage(510.0, 10.0)
    .setMonitors(4, 4000.0).setGeometry(40.0, 120.0)
    .setWind(15.0, 12.0)                       // wind speed, droplet fall height
    .setCharacteristicTargetDimensionM(25.5)
    .setLineOfSightObstructed(true);
mon.nominalDensityLpmPerM2();     // 31.4, passes in still air
mon.effectiveDensityLpmPerM2();   //  3.7, fails once the pattern drifts
mon.verdict();

// 4. can the existing system take it?
FireWaterCoverageAssessment gate = new FireWaterCoverageAssessment(510.0, 5100.0)
    .setCoveredAreaM2(0.0)
    .setSpareSupplyFlowLpm(16748.0)
    .setWorstCasePressureMarginBar(-0.9, "XX-71-0010B")   // worst DESIGN case, not normal case
    .setRequiredPressureMarginBar(0.5);
gate.verdict();          // SUPPLY_PRESSURE_DEFICIT
gate.recommendation();

// 5. active vs passive: what the framework does and does not allow
ActiveFireProtectionScreening s = new ActiveFireProtectionScreening(
        ActiveFireProtectionScreening.FireScenario.GAS_JET_FIRE)
    .setIncidentHeatFluxKWPerM2(200.0)
    .setBlowdownTimeToTargetS(359.0)
    .setBareSteelTimeToCriticalS(98.7)
    .setLoadBearingStructureExposed(true)
    .setNozzlesCanBePlacedAboveEquipment(false);
s.isWaterExtinguishing();             // false for a gas jet fire
s.isPassiveSubstitutionPermitted();   // false, always
s.isBlowdownFasterThanFailure();      // false here: thin steel loses the race
s.rankedMeasures();
```

Validated by `FireWaterDesignTest` (orifice law, both nozzle-count criteria, wind-drift
model, verdict routing, JSON contract).

## Application Rates

| Basis | Applies to | Rate |
|---|---|---|
| NORSOK S-001 Ed.4 §20.4.4 (Ed.5/6 §21) | Process areas **and equipment surfaces** | 10 (l/min)/m² |
| NORSOK S-001 Ed.4 §20.4.4 | Wellhead, riser balconies, FPSO turret manifolds | 20 (l/min)/m² |
| NORSOK S-001 Ed.4 §20.4.4 | Water at design pressure after a confirmed fire signal | ≤ 30 s |
| NFPA 15 | Exposure protection of vessel shells | 10.2 (l/min)/m² (0.25 gpm/ft²) |
| Common project practice | Enclosed utility/machinery rooms, sprinkler | 6 (l/min)/m² |

NORSOK S-001 does not state a fire-water duration. Take it from the project basis (30 min
is the usual foam figure and is often carried across) and say where it came from.

## Traps

- **Clause renumbering.** NORSOK S-001 **Ed.4 (2008)**: clause 20 = fire fighting systems,
  clause 21 = escape and evacuation. **Ed.5 (2018) / Ed.6 (2020)**: clause 20 = passive fire
  protection, clause **21 = active fire protection**. A finding citing "21.4.4" cites
  Ed.5/6. Check which edition the installation's own basis invokes before agreeing that a
  requirement applies.
- **Fire water never substitutes for passive fire protection.** The Norwegian Facilities
  Regulations §29 forbid crediting the cooling effect of fire-fighting equipment when
  designing PFP, and §11 forbids crediting fire water when establishing design accidental
  loads (NORSOK S-001 Ed.4 §19.4.2/§19.4.3 say the same). **The converse is also not
  permitted**: PFP on one item does not discharge an area fire-water requirement.
- **Flow alone under-counts nozzles.** A high-K nozzle meets the density with few nozzles
  spaced far apart, leaving dry patches. Take the larger of the flow and spacing counts.
- **Take the pressure margin from the worst *design* case, not the normal case.** Mature
  fire-water systems are commonly pressure-limited, and the margin can be negative in a
  two-pump case while looking healthy in the three-pump case.
- **"Nozzles cannot go above the equipment" is not a reason to have no coverage.** Use
  side-mounted nozzles or a dedicated spray ring. A project basis that excludes coverage
  over an item is a *deviation* and must be recorded and accepted as one.
- **Monitors are not equivalent to a fixed system.** NORSOK S-001 Ed.4 permits monitor
  substitution only for the turret, and §20.4.7 binds monitors to the same density and
  functionality. On an open deck, wind drift and shadowing usually settle the question.

## Validation Checklist

- [ ] The application rate comes from the governing project standard, not from the
      default: 10 (l/min)/m² for process areas, 20 for wellheads and riser balconies.
- [ ] Object surfaces are exposed wetted surfaces, not plan footprints.
- [ ] The nozzle count reports `governing_criterion`; a flow-governed count has been
      cross-checked against the maximum permitted spacing.
- [ ] `pressure_adequate` and `density_met` are both true, or the deficit is stated.
- [ ] The duration used for `water_volume_m3` is traceable to the project basis.
- [ ] Monitor results are reported with the wind speed they assume.
- [ ] The result is presented as a screening input to a hydraulic network calculation.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Nozzle count too low, dry patches in the area | Sized on the flow criterion alone | Take the larger of the flow and coverage counts; check `governing_criterion` |
| Higher-K nozzles do not reduce the count | The layout is spacing-governed | Reduce the protected area or accept the grid; `K` cannot fix overlap |
| Demand far below expectation | Object surfaces entered as footprints | Use exposed wetted surface, e.g. `horizontal_vessel_surface_m2()` |
| Monitor concept looks adequate | Screened at zero wind | Re-run at the design wind speed and report `verdict` |
| Passive fire protection reduced because deluge was added | Substitution applied in the wrong direction | Keep passive protection; the rule runs one way only |

## Interpretation

- `object_to_area_ratio` well below 1 means selective protection of the hydrocarbon-bearing
  items is far cheaper in water than blanket coverage. Whether it is *permitted* is a
  requirement question, not a hydraulic one — the area-coverage requirement in NORSOK S-001
  and ISO 13702 is written against the area.
- `governing_criterion == "coverage"` means the nozzle net is set by spray overlap, so a
  higher-K nozzle will not reduce the count.
- `verdict == "marginal_wind_limited"` means the monitor concept only works in still air.

## Limitations

- Screening only. It performs no network hydraulics, no trajectory modelling and no CFD.
- It does not decide compliance. Application rates, durations and the acceptability of
  selective protection come from the governing project standard and the accepted deviations.
- It gives no credit for, and takes no credit from, passive fire protection. Fire water and
  passive fire protection are complementary barriers, and the regulatory rule runs one way:
  passive protection may not be reduced because active protection exists.

## Related Skills

- `neqsim-consequence-analysis` — fire type, heat flux and flame reach (upstream).
- `neqsim-depressurization-mdmt` — blowdown time, the primary barrier (upstream).
- `neqsim-technical-document-reading` — area geometry, nozzle K-factors, hydraulic report (upstream).
- `neqsim-process-safety` — barrier register, performance standards, LOPA (downstream).
- `neqsim-standards-lookup` — clause traceability into `results.json` (downstream).
- `neqsim-jet-fire-radiation-screening` and `neqsim-relief-load-screening` — the fire side.
- `neqsim-depressurization-screening` — inventory removal, the primary barrier for a
  pressurised system.
- `neqsim-safety-function-coverage-screening` — whether the protective functions exist at all.
- `neqsim-weather-data` — live or forecast `wind_speed_m_s` for the fire-monitor
  wind-drift screening, instead of an assumed value.

## References

- NORSOK S-001, Technical Safety — fire-water application rates and area coverage.
- ISO 13702, Control and Mitigation of Fires and Explosions on Offshore Production
  Installations.
- NFPA 15, Standard for Water Spray Fixed Systems for Fire Protection — nozzle spacing
  and density.
- API RP 2030, Application of Fixed Water Spray Systems for Fire Protection in the
  Petroleum and Petrochemical Industries.
- NeqSim repository: https://github.com/equinor/neqsim
