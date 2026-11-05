---
name: neqsim-relief-flare-network
version: "1.0.0"
description: "Relief and flare design - PSV sizing per API 520 (gas/liquid/two-phase, fire), API 521 fire heat input, flare load summation, tip sizing, radiation contours, header back-pressure and Mach, and the TR3001 overpressure-study engine. USE WHEN: PSV sizing, relief contingency analysis, thermal relief of trapped liquid, flare hydraulics or radiation, PSV-to-flare integration, or a TR3001/API 521 study. Anchors on ReliefValveSizing, safety.overpressure, Flare, SafetyValve."
last_verified: "2026-06-27"
requires:
  java_packages: [neqsim.process.util.fire, neqsim.process.safety.overpressure, neqsim.process.equipment.flare, neqsim.process.equipment.valve]
---

# NeqSim Relief & Flare Network Skill

Relief-system screening and simulation: individual PSV sizing, native valve response,
plant load summation, flare-tip selection and consequence estimates. References include
**API 520**, **API 521**, **API 537**, **ISO 4126** and **ISO 23251**; using these
APIs does not establish compliance, certified capacity or installation approval.

**PSV unit/API correction, 2026-11-05:** `ReliefValveSizing` pressures are **Pa absolute**,
not barg or bara. Native `SafetyValve` and `SafetyReliefValve` pressure settings use
**bara**. Convert explicitly at that boundary. Do not infer a capacity, orifice-area or
certified-coefficient basis from a dynamic model's Cv. Select accumulation, blowdown,
back-pressure and radiation limits from the applicable project/code/vendor basis;
the example fractions below are study inputs, not universal acceptance limits.

## When to Use

- Sizing a single PSV (gas / liquid / two-phase / fire case)
- Checking whether a blocked-in liquid segment needs thermal relief or source-term
    handoff after rupture screening
- API 521 fire heat input on wetted area
- Aggregating simultaneous relief loads to a flare header
- Flare tip diameter and stack height (radiation)
- Header hydraulics: back-pressure on PSVs, Mach < 0.7
- Sour-gas dispersion check (toxic clouds)

Standards: **API 520 Part I/II**, **API 521** (relief contingencies + radiation),
**API 537** (flare equipment), **NFPA 30**, **EN ISO 23251**.

## Native PSV response and canonical P&ID feedback

Use `SafetyReliefValve` for a self-acting native pressure/hysteresis response with
declared lift lag, dwell and rate limits. Execute the UUID overload used by
`ProcessSystem`; a direct method-only ramp is not evidence that the process runner
exercises the same response. Keep opening at set pressure, full-open pressure and
reseat pressure distinct. Enable the native gas choked-flow capacity limit for credited gas relief response.
Record `getPressureReliefConfiguration()` and the actual
lift, flow and ordered pop/reseat observations. A cycle count is not an acoustic or
mechanical chatter model.

For a process/P&ID feedback study:

1. Connect the actual valve to a protected-vessel gas branch and an explicit
   downstream pressure boundary through a source-backed tee. Refresh branch
   intensive state before valve hydraulics; aggregate both actual demands before
   the vessel consumes its outlet boundary. Do not prescribe an arbitrary relief
   split or reconcile inventory afterwards.
2. Import it with `PidPressureReliefImporter` into the existing `PidDesignModel`.
   The initial supported route is an exact CONVENTIONAL `SafetyReliefValve`, LINEAR trim and a
   real two-outlet `Splitter`. Record a capacity-basis evidence reference; a
   self-acting PSV receives no fabricated controller or SIF signal.
3. Use `PidPressureReliefValidator` to apply canonical settings to an isolated
   process copy, read them back, fingerprint the actual configuration and attach
   strict native scenario evidence. Reject stale bindings, wrong endpoints,
   unsupported trim, secondary valve controllers/travel and ignored settings.
4. Check no-demand standby, actual relief demand, failed/limited relief,
   back-pressure sensitivity, slow response and recovery/refinement as applicable.
   Reseating requires an earlier observed opening and subsequent physical seating;
   an initially closed sample cannot satisfy recovery. No-demand PASS is not
   successful protection on a demand.
5. Retain full-window sampled pressure checks, actual positive discharge and
   unwanted-relief checks separately from per-vessel component, mass and energy
   closure. A successful response cannot clear numerical FAIL/HOLD or establish
   unobserved continuous peaks, certified sizing, SRS/SIL/IPL credit or construction
   approval. Check actual snapshot coverage before claiming transactional rollback
   of the entire process.

The synthetic `AdvancedV101PsvStudyTest`, `PidNativePressureReliefTest` and
`SafetyReliefValveUuidTest` provide executable examples and regression evidence.
See the [PSV workflow guide](../../../docs/process/safety/psv-pid-verification.md)
and [P&ID synthesis](../../../docs/pid-design-synthesis.md) for canonical authority,
source identities, explicit absolute-to-gauge references and separate engineering
review gates.

## Pattern 1 — Gas-phase area screening

```java
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.process.util.fire.ReliefValveSizing.PSVSizingResult;

PSVSizingResult psv = ReliefValveSizing.calculateRequiredArea(
    5000.0 / 3600.0, // kg/s
    110.0e5,         // set pressure, Pa absolute
    0.10,            // explicit example overpressure fraction
    1.013e5,         // back pressure, Pa absolute
    333.15,          // K
    0.018,           // kg/mol
    0.95,            // compressibility factor
    1.30,            // Cp/Cv
    false,           // balanced bellows
    false            // rupture disc upstream
);

double area_m2 = psv.getRequiredArea();
String orifice = psv.getRecommendedOrifice();
```

This exact gas call and the following liquid, two-phase and fire calls are exercised
by `ReliefValveSizingDocumentationTest`. See the complete tested unit contract in
[`relief_valve_sizing_api.md`](../../../docs/safety/relief_valve_sizing_api.md).
The selected standard area is a screening result, not a vendor capacity certificate.

## Pattern 2 — Wetted-area fire heat-input screening

```java
double Q_fire_W = ReliefValveSizing.calculateAPI521FireHeatInput(
    80.0, // wetted area, m2
    true, // adequate drainage
    true  // firefighting flag; current helper applies no firefighting credit
);
```

This helper is not a vessel energy-inventory simulation. Use an actual dynamic
vessel, declared heat input and a connected relief device to study pressure response.
Do not substitute an assumed latent heat for a phase/enthalpy calculation or invent
an insulation/firefighting credit not implemented and supported by project evidence.

## Pattern 3 — Liquid area screening

```java
import neqsim.process.util.fire.ReliefValveSizing.LiquidPSVSizingResult;

LiquidPSVSizingResult lpsv = ReliefValveSizing.calculateLiquidReliefArea(
    50.0 / 3600.0, // m3/s at relieving conditions
    850.0,         // kg/m3
    25.0e5,        // set pressure, Pa absolute
    0.10,          // explicit example overpressure fraction
    1.013e5,       // back pressure, Pa absolute
    0.004,         // dynamic viscosity, Pa s
    false          // balanced bellows
);
```

## Pattern 4 — Two-phase area screening

```java
double area = ReliefValveSizing.calculateTwoPhaseReliefArea(
    30000.0 / 3600.0, // kg/s
    80.0e5,          // set pressure, Pa absolute
    0.10,            // explicit example overpressure fraction
    5.0e5,           // back pressure, Pa absolute
    373.15,          // K
    0.30,            // gas mass fraction
    50.0,            // gas density, kg/m3
    700.0,           // liquid density, kg/m3
    250000.0,        // latent heat, J/kg
    2500.0           // liquid heat capacity, J/(kg K)
);
```

## Pattern 4b — Integrated Overpressure Study (TR3001 / API 521)

For a full overpressure-protection study on one protected item — enumerate
credible relief contingencies, pick the governing case, size the PSV, and check
acceptance — use `neqsim.process.safety.overpressure`. Each cause calculator is
fluent and returns an immutable `ReliefScenario`; the engine selects the
maximum-rate credible scenario and sizes accordingly (vapour via `ReliefValveSizing`
API 520; liquid via the API 520 liquid method; two-phase via the omega method).

```java
import neqsim.process.safety.overpressure.*;

// 1. Cause scenarios (fluent → ReliefScenario)
ReliefScenario blocked = new BlockedOutletRelief().setName("Blocked gas outlet")
    .setInflowRateKgPerHr(36000.0).setReliefPressureBara(50.0)
    .setReliefTemperatureC(20.0).setFluid(gas).calculate();
ReliefScenario fire = new FireCaseRelief().setName("Pool fire")
    .setVesselDiameterM(2.0).setWettedHeightM(3.0)   // or setWettedAreaM2(..)
    .setHasDrainage(true).setHasFireFighting(true)
    .setLatentHeatJPerKg(350000.0).setReliefPressureBara(60.0)
    .setReliefTemperatureC(120.0).setFluid(gas).calculate();

// 2. Engine: governing case + sizing + acceptance
ProtectedItem item = new ProtectedItem("V-100", 100.0)   // tag, MAWP [bara]
    .setReliefSetPressureBara(100.0).setBackPressureBara(1.5);
OverpressureStudyResult result = new OverpressureProtectionStudy(item)
    .addScenario(blocked).addScenario(fire).evaluate();

result.getGoverningScenario().getName();   // worst credible case
result.getRequiredAreaIn2();               // API 526 required orifice area
result.getRecommendedOrifice();            // orifice letter
result.isCapacityAdequate();               // area-based adequacy
result.getAcceptance().getAccumulationFraction();  // vs 1.10 / 1.16 / 1.21

// 3. TR3001 compliance findings (PASS/FAIL/NEEDS_REVIEW)
List<ComplianceFinding> findings = new TR3001ComplianceChecker().check(result);
boolean compliant = new TR3001ComplianceChecker().isCompliant(findings);

// 4. Roll relief loads up to a disposal header (API 521 §5.3)
ReliefDisposalResult disposal = new ReliefDisposalNetwork("Fire zone 1")
    .addRelief(resultA, true).addRelief(resultB, true).calculate();
disposal.getTotalSimultaneousKgPerS();   // simultaneous header load
disposal.getPeakSingleKgPerS();          // largest single contributor
disposal.getGoverningContributor();
```

- Cause calculators: `BlockedOutletRelief`, `CheckValveLeakRelief`,
  `ControlValveFailureRelief`, `TubeRuptureRelief`, `FireCaseRelief`.
- Set `ReliefScenario` phase to `LIQUID` (with `densityKgPerM3`/`viscosityPaS`)
  or `TWO_PHASE` (with `gasMassFraction`, `gasDensityKgPerM3`,
  `liquidDensityKgPerM3`, `latentHeatJPerKg`, `liquidHeatCapacityJPerKgK`) to
  trigger the matching sizing path; missing two-phase inputs are reported as warnings.
- The engine's screening factors are not universal code limits. Record the protected
  item's MAWP, gauge/absolute basis, contingency, number of installed devices,
  governing edition and project-approved accumulation criterion separately.
- Verified by `OverpressureProtectionStudyTest` + `OverpressureExtensionsTest`.

> **Adequacy is judged by AREA**, not by re-plugging the selected area into the
> nozzle equation: `calculateRequiredArea` (API 520 empirical) and the nozzle
> capacity formula use different coefficient bases and are not inverses.

## Pattern 5 — Flare Tip & Stack (API 537 + 521 §6)

```java
import neqsim.process.equipment.flare.Flare;

Flare flare = new Flare("MainFlare");
flare.setInletStream(reliefStream);
flare.setRadiantFraction(0.30);                // typical 0.20–0.40
flare.setTipDiameter(0.5);                     // m
flare.setDesignHeatDutyCapacity(150.0, "MW");
flare.run(UUID.randomUUID());

// Radiation at ground distance
double q_Wm2 = flare.estimateRadiationHeatFlux(75.0);   // 75 m
double dSafe = flare.radiationDistanceForFlux(4730.0);  // example 4.73 kW/m2 threshold
```

Illustrative radiation screening levels; verify the applicable exposure duration,
receptor location and governing project/standard basis before acceptance:

| Receiver                           | Allowable flux (kW/m²)      |
| ---------------------------------- | --------------------------- |
| Personnel — emergency only         | 9.46                        |
| Personnel — escape (≤1 min)        | 6.31                        |
| Property line / 2-min escape       | 4.73                        |
| Solar background                   | ~1.0 (subtract from above)  |

### Pattern 5b — Detailed Flare Flame & Sterile-Zone (API 537)

For sterile-zone radii, wind-tilted flame geometry, and flare noise, use
`Api537FlareFlameModel` (Kent 1968 flame length + tilt + iso-flux solver):

```java
import neqsim.process.safety.fire.Api537FlareFlameModel;

Api537FlareFlameModel flame = new Api537FlareFlameModel(
        50.0,      // relief mass flow [kg/s]
        50.0e6,    // heat of combustion [J/kg]
        0.20,      // radiant fraction
        200.0)     // tip exit velocity [m/s]
    .setStackHeightM(40.0)
    .setWindSpeedMPerS(10.0);

double lFlame = flame.flameLengthM();
double tilt   = flame.flameTiltRad();
double r158   = flame.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_1_58_KW); // ~personnel continuous
double r473   = flame.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_4_73_KW); // property line
double r946   = flame.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_9_46_KW); // emergency-only
double q75    = flame.heatFluxAtGroundDistance(75.0);  // W/m²
double pwl    = flame.soundPowerLevelDb();
double spl    = flame.soundPressureLevelDb(100.0);
```

Verified by `Api537FlareFlameModelTest`. Radii are nested (lower flux reaches
further); flame tilts downwind and the tip moves horizontally with wind speed.

## Pattern 6 — Plant Load Summation

For each contingency (general power failure, total reflux failure, fire zone):
1. List PSVs that lift simultaneously
2. Sum mass flows at each PSV at its **relieving** conditions
3. Pick the **governing** contingency (highest header load)
4. Size the flare for that load

```java
double totalReliefLoad = psvs.stream()
    .filter(p -> isActiveDuring(p, contingency))
    .mapToDouble(p -> p.getMassFlowCapacity())
    .sum();
```

For **simultaneous blowdown** contingencies (multiple BDVs into one header), use
`MultiVesselBlowdownStudy` (see `neqsim-depressurization-mdmt`) — it superimposes
the transient blowdown curves on a common time grid and reports the **peak**
combined header mass flow and the header Mach at that instant, which is the load
that actually sizes the header.

## Pattern 7 — Header Back-Pressure & Mach

Use the actual downstream stream/header pressure in the connected hydraulic model.
Distinguish superimposed from built-up back pressure and evaluate the selected valve's
certified operating envelope. A conventional/balanced/pilot label does not by itself
justify a fixed allowable percentage or correction factor.

`SafetyReliefValve` exposes `setValveType`, `setBackpressureSensitivity` and `setKbMax`
for its empirical response model. These parameters do not replace certified vendor
curves. Legacy `SafetyValve` uses `setPressureSpec` in bara; it has no
`setSetPressure`, `setBackPressure` or `getBackPressureCorrectionFactor` APIs.
Do not call those nonexistent overloads or pass barg to a bara setter.

Check header hydraulics and project Mach/velocity criteria separately. Include the
actual relief line and disposal destination in the canonical P&ID; a drawn off-page
connector alone is not a simulated flare network or validated back-pressure model.

## Pattern 8 — Dispersion of Unignited Release

```java
FlareDispersionSurrogateDTO disp = flare.getDispersionSurrogate();
// Use to bound H2S / SO2 ground concentration vs. IDLH/ERPG-2
```

For a governed production-readiness record, use
`neqsim.process.engineering.safety.FlareConsequenceCalculation`. It combines explicit
point-source radiation, neutral Gaussian centerline dispersion, spherical noise spreading,
and tip-Mach constraints in one typed result with uncertainty and provenance. Set
`productionQualification=true` only with standards/evidence references and
`consequenceMethodApplicability=approved`. This is a screening interface, not a substitute
for validated complex-terrain, stability-class, toxic, combustion, or detailed acoustic
modeling.

## Common Mistakes

| Mistake                                                   | Fix                                                                  |
| --------------------------------------------------------- | -------------------------------------------------------------------- |
| Treating a sample accumulation fraction as code approval   | Establish the contingency, applicable code/edition and pressure basis; declare the approved criterion |
| Wetted area = total surface                               | Establish actual wetted geometry, fire exposure and applicable standard limits |
| Inventing drainage, insulation or firefighting credit      | Verify project evidence and the exact helper implementation; the static fire helper applies no firefighting credit |
| Adding all PSV capacities for header                      | Use the governing **contingency**, not sum of nameplate capacities   |
| Assuming a discharge coefficient from a service label     | Obtain the applicable certified/vendor coefficient and correction-factor basis |
| Ignoring Mach and built-up pressure in the header          | Evaluate actual compressible hydraulics; a project Mach margin is not the sonic choking condition |
| Ignoring inlet-line loss and valve/system interaction      | Check applicable installation criteria and engineering-analysis requirements; a pressure-loss percentage alone does not prove chatter |
| Treating an API 526 letter as certified relief adequacy    | Check selected area, service, certified coefficients and vendor capacity; apply project margin explicitly |

## Validation Checklist

- [ ] Each contingency has a documented worst-case mass flow
- [ ] Wetted-area calculation records geometry, exposure and applicable limits
- [ ] Drainage / insulation credits supported by P&ID review
- [ ] Header hydraulics meet the declared project Mach/velocity criteria at peak load
- [ ] Superimposed/built-up back pressure meets the selected device's approved envelope
- [ ] Inlet-line losses and stability meet applicable installation/vendor requirements
- [ ] Radiation meets the declared location, exposure-time and background criteria
- [ ] Dispersion check at flame-out (toxic species)
- [ ] Results saved to `results.json` under `relief_system` with PSV table + flare load summary

## Related Skills

- [`neqsim-process-safety`](../neqsim-process-safety/SKILL.md) — when PSV is the IPL of last resort in LOPA
- [`neqsim-firewater-deluge-design`](../neqsim-firewater-deluge-design/SKILL.md) — fire-water demand for the area, which competes with the relief and flare load for the same simultaneous-demand budget
- [`neqsim-trapped-liquid-fire-rupture`](../neqsim-trapped-liquid-fire-rupture/SKILL.md) — blocked-in liquid fire rupture screening before thermal relief/PFP decisions
- [`neqsim-depressurization-mdmt`](../neqsim-depressurization-mdmt/SKILL.md) — blowdown transients + `MultiVesselBlowdownStudy` for coupled header loads
- [`neqsim-consequence-analysis`](../neqsim-consequence-analysis/SKILL.md) — `Api537FlareFlameModel` radiation/noise and hazardous-area zoning
- [`neqsim-dynamic-simulation`](../neqsim-dynamic-simulation/SKILL.md) — depressurization (blowdown) is separate from PSV
- [`neqsim-mechanical-design`](../neqsim-api-patterns/SKILL.md) — PSV mechanical via `SafetyValveMechanicalDesign`
- [`neqsim-standards-lookup`](../neqsim-standards-lookup/SKILL.md) — API 520 / 521 / 526 / 537
