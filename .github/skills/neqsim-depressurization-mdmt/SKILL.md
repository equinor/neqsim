---
name: neqsim-depressurization-mdmt
version: "1.0.0"
description: "Emergency depressurization (blowdown) per API 521 §5.20 and minimum design metal temperature (MDMT) assessment per ASME UCS-66 / API 579 / EN 13445 — VU-flash transient inventory model, time-to-target-pressure, low-temperature embrittlement screening, and integration with PSV/flare loads. USE WHEN: a task requires sizing a blowdown valve, generating a P-vs-time curve for a vessel under fire / depressurization, checking MDMT against blowdown end-temperature, providing source terms for relief and flare networks, or distinguishing blowdown from trapped-liquid fire rupture screening. Anchors on neqsim.process.safety.depressurization.DepressurizationSimulator and neqsim.process.safety.mdmt.MDMTCalculator."
last_verified: "2026-04-26"
requires:
  java_packages:
    - neqsim.process.safety.depressurization
    - neqsim.process.safety.mdmt
---

# NeqSim Depressurization & MDMT Skill

Transient blowdown / depressurization for inventory release on fire or
controlled emergency, plus the minimum design metal temperature (MDMT) check
that drives material selection. The two are linked: blowdown end-temperatures
(often −80 to −120 °C for hydrocarbon gas) usually drive MDMT, which in turn
drives whether LTCS, low-temperature carbon-Mn, 3.5 % Ni or 9 % Ni / 304L is
required.

## When to Use

- Sizing a blowdown / depressurization valve to reach 50 % pressure in 15 min
  (API 521 §5.20 fire case) or 7 bar in some operator standards
- Generating P(t), T(t), m(t) curves for the relief / flare load case
- Screening MDMT against end-of-blowdown vessel-wall temperature
- Producing source terms for the flare network (`neqsim-relief-flare-network`)
- Distinguishing depressurization cases from blocked-in liquid fire rupture cases,
  where `neqsim-trapped-liquid-fire-rupture` is the primary workflow

Distinct from `neqsim-relief-flare-network` (steady-state PSV sizing) and
`neqsim-dynamic-simulation` (continuous-process transients) — this skill is the
specific blowdown + MDMT pair.

## Standards

- **API 521** 7th ed. — Pressure-relieving and depressuring systems (§5.20 blowdown)
- **API STD 520** — PSV sizing, used for choke check at the BDV
- **ASME UCS-66 / UCS-66.1** — MDMT impact-test exemption curves (carbon steel)
- **ASME UHA-51** — austenitic stainless steel low-temperature service
- **API 579 / FFS-1 §3** — fitness-for-service, MDMT for in-service vessels
- **EN 13445-2 Annex B** — European MDMT and impact-test approach
- **NORSOK L-002** — piping system design (low-temperature operation)

## Method 1 — Blowdown Simulation (VU-flash)

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.safety.depressurization.DepressurizationSimulator;

SystemInterface gas = new SystemSrkEos(273.15 + 50.0, 100.0);
gas.addComponent("methane", 0.92);
gas.addComponent("ethane",  0.05);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");
gas.setTotalNumberOfMoles(5000.0); // mol — representative of vessel inventory

DepressurizationSimulator sim = new DepressurizationSimulator(gas);
sim.setVesselVolume(50.0);     // m³
sim.setOrificeArea(5.0e-4);    // m² — BDV equivalent area
sim.setBackPressure(1.5);      // bara — flare KO drum
sim.setHeatInput(0.0);         // adiabatic; > 0 for fire case
sim.run(900.0, 1.0);           // 15 min, 1 s timestep

double[] t = sim.timeSeries();
double[] p = sim.pressureSeries();
double[] T = sim.temperatureSeries();
double[] m = sim.massFlowSeries();
double pEnd = p[p.length - 1];
double tEnd = T[T.length - 1];
double t50  = sim.timeToPressure(50.0);  // s, time to 50 bar
```

The simulator uses the U–V flash (`ops.VUflash(V, U)`) at every step — internal
energy decreases by `h_out · ṁ · Δt` and volume is held constant by the vessel,
so each step is a fully consistent thermodynamic state. Joule-Thomson cooling
across the BDV is captured via an isenthalpic flash to the back pressure for the
exit-temperature output.

### Fire case

```java
sim.setHeatInput(60_000.0); // W — API 521 fire heat input
```

API 521 fire heat input on uninsulated vessels:

`Q = 43.2 · F · A^0.82  [W]`

with environment factor F (= 1.0 for un-insulated, 0.30 for fireproof
insulation, 0.075 for water-spray) and wetted area A in m². The simulator
accepts the value directly so any of the API 521, NFPA 30 or NORSOK
correlations can be used upstream.

## Method 2 — BDV Sizing Iteration

Typical workflow:

1. Start from a target such as 50 % of design pressure in 15 min (API 521), 7 bar in 15 min, or the relevant company/project criterion from the private basis.
2. Guess BDV `Cd · A`, run `sim.run(...)`, read `sim.timeToPressure(target)`.
3. Iterate area until target is met without choking the flare header.
4. Verify the *minimum* T(t) is above the vessel MDMT.

A reference iteration loop is available as
`DepressurizationSimulator.sizeForTargetPressure(targetBar, targetTimeS)`.

## Method 3 — MDMT Assessment

```java
import neqsim.process.safety.mdmt.MDMTCalculator;

MDMTCalculator mdmt = new MDMTCalculator();
mdmt.setMaterial("SA-516-70N");      // normalised CMn, common for CS vessels
mdmt.setThicknessMM(50.0);
mdmt.setStressRatio(0.35);           // operating / allowable stress ratio
double mdmtC = mdmt.computeUCS66();  // °C — ASME UCS-66 + UCS-66.1 reduction
```

The calculator implements:

- **UCS-66 Curve A / B / C / D** lookup vs material specification
- **UCS-66.1** stress-ratio reduction (lower stress → lower MDMT)
- **API 579 §3** Fitness-for-Service path for in-service vessels with crack
  reassessment factors
- **EN 13445-2** Annex B alternative if requested

### Pass / fail check

```java
double bdvEndTemp = sim.minTemperatureC();  // °C from blowdown sim
boolean acceptable = bdvEndTemp >= mdmtC;
if (!acceptable) {
    // Either:  thicker vessel, lower stress ratio, LTCS / 3.5%Ni material,
    //          slower BDV, or accept impact testing per UG-84.
}
```

Many company practices add a 5-10 °C margin between blowdown end-temperature
and MDMT. Record the actual project or operator margin in the private task
basis instead of hard-coding it in public guidance.

## Method 4 — Source Term to Flare Network

```java
double[] mdot = sim.massFlowSeries();
double[] T    = sim.temperatureSeries();
double[] P    = sim.pressureSeries();
// Pass to ReliefValveSizing peak-load aggregator or to
// FlareStack.estimateRadiationHeatFlux at peak ṁ.
double mdotPeak = sim.peakMassFlow();
```

This is the standard handoff between the depressurization model and the flare
network sizing skill (`neqsim-relief-flare-network`).

## Common Pitfalls

- **Adiabatic vs fire case** — running adiabatic blowdown gives the *coldest*
  end-temperature (worst for MDMT). Running fire case gives the *highest peak
  flow* (worst for flare network). Both must be checked separately.
- **Single component vs multi-component** — MDMT is driven by the
  end-of-blowdown temperature, which depends on JT coefficient and is sensitive
  to ethane / propane content. Always use a representative composition, not a
  pure-methane simplification.
- **Ignoring liquid level** — vessels with liquid have huge thermal mass; the
  gas phase cools quickly while the liquid holds temperature. The simulator
  handles two-phase systems automatically.
- **Choked vs sub-critical flow** — the BDV chokes for most of the blowdown.
  Make sure the simulator's flow model uses choked-flow correlations until
  P_vessel / P_back < 1/r_critical.
- **Stress ratio = 1** — using 1.0 for stress ratio gives the most conservative
  MDMT. Operating-pressure stress ratio (0.30–0.40) usually relaxes MDMT by
  10–30 °C.

## Verification Tests

```bash
./mvnw test -Dtest=DepressurizationSimulatorTest,MDMTCalculatorTest
```

## See Also

- `neqsim-relief-flare-network` — PSV sizing, flare radiation, header back-pressure
- `neqsim-trapped-liquid-fire-rupture` — blocked-in liquid thermal expansion, PFP demand, and rupture source-term handoff
- `neqsim-dynamic-simulation` — continuous-process transients with controllers
- `neqsim-consequence-analysis` — what happens after the released gas ignites
- `neqsim-flow-assurance` — JT cooling and hydrate formation in blowdown
- `neqsim-process-safety` — LOPA / SIL for the blowdown SIF (BDV-SIF)
