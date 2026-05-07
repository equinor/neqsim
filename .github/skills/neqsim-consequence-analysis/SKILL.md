---
name: neqsim-consequence-analysis
version: "1.0.0"
description: "Quantitative consequence analysis for oil & gas hazards — jet fire, pool fire, vapour cloud explosion (VCE), BLEVE, Gaussian plume and heavy-gas dispersion, probit-based fatality probabilities, individual and societal risk roll-up. USE WHEN: a task requires fire-radiation contours, dispersion to LFL/IDLH/ERPG, BLEVE thermal/missile assessment, or QRA-style risk integration of multiple release outcomes. Anchors on neqsim.process.safety.fire, neqsim.process.safety.dispersion, neqsim.process.safety.qra."
last_verified: "2026-04-26"
requires:
  java_packages:
    - neqsim.process.safety.fire
    - neqsim.process.safety.dispersion
    - neqsim.process.safety.qra
---

# NeqSim Consequence Analysis Skill

Quantitative consequence modelling that converts a release scenario (mass flow,
inventory, ignition probability) into thermal radiation contours, overpressure
contours, dispersion footprints, and finally individual / societal fatality risk
per ISO 17776, NORSOK Z-013, API 752 and the CCPS *Guidelines for Chemical
Process Quantitative Risk Analysis*.

## When to Use

- Jet-fire, pool-fire, VCE or BLEVE source-term modelling
- Toxic / flammable dispersion to LFL, IDLH, ERPG-2/3 or 1 % fatality contours
- Probit-based fatality probability for thermal radiation, overpressure or toxic dose
- QRA roll-up: combine release frequency × ignition probability × fatality probability
- Source-term generation as input to PHAST / FLACS / KFX

Distinct from `neqsim-process-safety` (HAZOP / LOPA / SIL — frequency side) and
`neqsim-relief-flare-network` (PSV sizing / flare radiation — design side). This
skill is the *consequence* side of QRA.

## Standards

- **API 521** §6 — flare and vent radiation, fire heat input
- **API 752 / 753 / 756** — facility siting, occupied buildings
- **NORSOK Z-013 / S-001** — risk acceptance, fire & explosion loads
- **CCPS QRA Guidelines** — probit constants, ignition probabilities
- **TNO Yellow Book** — multi-energy / Baker-Strehlow VCE
- **EI 15** — hazardous area classification

## Method 1 — Jet Fire (API 521 + CCPS solid-flame)

```java
import neqsim.process.safety.fire.JetFireModel;
import neqsim.process.safety.dispersion.ProbitModel;

// 30 kg/s gas leak, 50 MJ/kg HoC, 25 % radiative fraction
JetFireModel jet = new JetFireModel(30.0, 50.0e6, 0.25);
double flux50m = jet.radiationFluxAt(50.0);   // W/m²
double dist125 = jet.distanceForFlux(12500.0); // m where flux = 12.5 kW/m²
```

Typical thermal radiation criteria (API 521 / NORSOK S-001):

| Flux (kW/m²) | Effect                                  | Use                        |
| ------------ | --------------------------------------- | -------------------------- |
| 1.6          | No discomfort for long exposure          | Public area limit          |
| 4.7          | Sufficient for evacuation in 30 s        | Escape route               |
| 12.5         | Wood ignites, equipment failure (5 min)  | Process equipment limit    |
| 37.5         | Structural failure of process equipment  | Damage to steel structure  |

## Method 2 — Pool Fire

```java
import neqsim.process.safety.fire.PoolFireModel;

// 500 kg liquid, burning rate 0.05 kg/m²·s, dike diameter 8 m, η = 0.30
PoolFireModel pool = new PoolFireModel(0.05, 8.0, 50.0e6, 0.30);
double flux = pool.radiationFluxAt(25.0); // W/m² at 25 m
```

## Method 3 — Vapour Cloud Explosion (TNO Multi-Energy)

```java
import neqsim.process.safety.fire.VCEModel;

// 200 kg flammable in cloud, congestion class 7 (heavy)
VCEModel vce = new VCEModel(200.0, 50.0e6, 7);
double overpressure = vce.overpressureAt(60.0); // Pa at 60 m
double safeDist = vce.distanceForOverpressure(20684.0); // m for 3 psi
```

## Method 4 — BLEVE

```java
import neqsim.process.safety.fire.BLEVECalculator;

// 50 t propane vessel
BLEVECalculator bleve = new BLEVECalculator(50000.0, 50.0e6, 0.40);
double fireballDiameter = bleve.fireballDiameter();   // m
double fireballDuration = bleve.fireballDuration();   // s
double thermalDose = bleve.thermalDoseAt(150.0);      // (W/m²)^(4/3)·s
```

## Method 5 — Gaussian Plume Dispersion (Briggs σ)

```java
import neqsim.process.safety.dispersion.GaussianPlume;

// 10 kg/s leak, ground-level source, 4 m/s wind, neutral stability D
GaussianPlume plume = new GaussianPlume(10.0, 0.0, 4.0,
    GaussianPlume.Stability.D, GaussianPlume.Terrain.RURAL);
double conc = plume.centerlineGroundConcentration(200.0); // kg/m³
double distLFL = plume.distanceToConcentration(0.044);    // m to methane LFL
```

Stability classes: A (very unstable) … F (stable). RURAL vs URBAN selects Briggs σ
coefficients per Pasquill-Gifford. Use `Stability.F` for worst-case dispersion
analysis (calm night, low wind).

## Method 6 — Heavy-Gas Dispersion

For dense releases (CO₂, propane, butane) the Gaussian model under-predicts
near-field concentrations. Use `HeavyGasDispersion` (Britter-McQuaid screening):

```java
import neqsim.process.safety.dispersion.HeavyGasDispersion;

HeavyGasDispersion hgs = new HeavyGasDispersion(
    50.0,    // continuous release rate [kg/s]
    1.98,    // gas density at release [kg/m³]
    1.20,    // ambient density [kg/m³]
    4.0);    // wind speed [m/s]
double distLFL = hgs.distanceToConcentration(0.05); // m
```

## Method 7 — Probit Fatality Probability

```java
import neqsim.process.safety.dispersion.ProbitModel;

// Thermal: Y = a + b·ln(t·F^(4/3)), 60 s exposure at 12.5 kW/m²
double pFatality = ProbitModel.thermalFatality()
    .fatalityProbability(60.0, 12500.0);

// Toxic H2S: Y = -31.42 + 3.008·ln(C^1.43·t)
ProbitModel h2s = ProbitModel.h2sFatality();
double pH2S = h2s.fatalityProbability(600.0, 5.0e-4); // 10 min, 500 ppm

// Overpressure (lung haemorrhage): Y = -77.1 + 6.91·ln(P)
ProbitModel ovp = ProbitModel.overpressureFatality();
```

Built-in factories: `thermalFatality()`, `overpressureFatality()`,
`h2sFatality()`, `cl2Fatality()`, `nh3Fatality()`, `coFatality()`. The
`ToxicLibrary` class centralises probit constants for common toxic gases.

## Method 8 — QRA Roll-up

```java
import neqsim.process.safety.qra.ConsequenceAnalysisEngine;

ConsequenceAnalysisEngine e = new ConsequenceAnalysisEngine(
    "10 mm gas leak", 1.0e-4); // release frequency [/yr]
e.addJetFire(0.05, jet, ProbitModel.thermalFatality(), 60.0);
e.addJetFire(0.02, pool, ProbitModel.thermalFatality(), 60.0);
e.addToxicCloud(0.01, plume, ProbitModel.h2sFatality(), 600.0);

double IRPA = e.individualFatalityRiskPerYear(50.0); // at 50 m
String text = e.report(50.0);
```

The engine sums:

`IRPA(d) = Σ_outcome  f_release · f_branch · P_fatality(d, outcome)`

Compare against acceptance criteria:

| Criterion        | Limit                  | Source              |
| ---------------- | ---------------------- | ------------------- |
| Worker IRPA      | 1·10⁻³ /yr (intolerable) | UK HSE R2P2         |
| Worker IRPA      | 1·10⁻⁶ /yr (broadly acceptable) | UK HSE R2P2 |
| NORSOK FAR       | 10 fatalities / 10⁸ h    | NORSOK S-001        |
| Public 1 % fatal | 35 m typical for 12.5 kW/m² | API 752         |

## Source Term for External CFD

`ConsequenceAnalysisEngine.exportSourceTerm()` writes a JSON block usable by
PHAST, FLACS, KFX or DNV Safeti containing release rate, momentum, density,
duration and chemistry — the standard handoff format described in
`neqsim-agent-handoff`.

## Common Pitfalls

- **Stability class** — using class D ("typical") instead of F for hazard contours
  under-predicts safe distance by 2–3×. Always run F at low wind for siting.
- **Heavy gas** — applying Gaussian to CO₂ / LPG releases under-predicts near-field
  concentration. Switch to `HeavyGasDispersion` when density ratio > 1.2.
- **Probit constants** — different sources give different (a, b, n). Always cite
  the source; the factories in `ProbitModel` use CCPS values.
- **Ignition probability** — small leaks (< 1 kg/s) often use 1–5 %, large
  leaks (> 50 kg/s) up to 30 % delayed. Use scenario-specific values, not 100 %.
- **Flame view-factor** — the simple 1/(4πr²) point-source form is conservative
  near the flame; use API 521 solid-flame for distances < 2 × flame length.

## Verification Tests

`src/test/java/neqsim/process/safety/{fire,dispersion,qra}/` contain JUnit 5
tests for every model. Run:

```bash
./mvnw test -Dtest=FireModelsTest,GaussianPlumeTest,ProbitModelTest,ConsequenceAnalysisEngineTest
```

## See Also

- `neqsim-process-safety` — frequency side (HAZOP / LOPA / SIL)
- `neqsim-relief-flare-network` — PSV sizing and flare radiation
- `neqsim-depressurization-mdmt` — emergency depressurization source terms
- `neqsim-agent-handoff` — source-term JSON schema
