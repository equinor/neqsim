---
name: neqsim-consequence-analysis
version: "1.1.0"
description: "Quantitative consequence analysis - jet/pool fire, VCE, BLEVE, Gaussian and heavy-gas dispersion, probit fatality, individual and societal risk. USE WHEN: a task requires fire-radiation contours, dispersion to LFL/IDLH/ERPG, BLEVE thermal/missile assessment, QRA-style integration of release outcomes, or unignited flare/vent gas at a helideck and flight path. Anchors on neqsim.process.safety.fire, .dispersion and .qra."
last_verified: "2026-11-20"
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
double flux50m = jet.incidentHeatFlux(50.0);   // W/m²
double dist125 = jet.distanceToFlux(12500.0); // m where flux = 12.5 kW/m²
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

// Dike diameter 8 m, prescribed mass burning rate 0.05 kg/(m²·s)
PoolFireModel pool = new PoolFireModel(8.0, 0.05);
double flux = pool.incidentHeatFlux(25.0); // W/m² at 25 m
```

## Method 3 — Vapour Cloud Explosion (TNO Multi-Energy)

```java
import neqsim.process.safety.fire.VCEModel;

// 200 kg flammable in cloud, congestion class 7 (heavy)
VCEModel vce = new VCEModel(200.0, 50.0e6, 7);
double overpressure = vce.overpressurePa(60.0); // Pa at 60 m
double safeDist = vce.distanceToOverpressure(20684.0); // m for 3 psi
```

## Method 4 — BLEVE

```java
import neqsim.process.safety.fire.BLEVECalculator;

// 50 t propane vessel
BLEVECalculator bleve = new BLEVECalculator(50000.0, 50.0e6, 0.40);
double fireballDiameter = bleve.fireballDiameterM();   // m
double fireballDuration = bleve.fireballDurationS();   // s
double flux = bleve.incidentHeatFlux(150.0);          // W/m²
```

For pool-fire, BLEVE and VCE contours, check for `Double.NaN` before using the
returned distance. It means the target is unbracketed within the model's
search range. See [Dispersion and Consequence Analysis](../../../docs/safety/dispersion_and_consequence.md).

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

### Method 5b — Elevated flare or vent gas at a helideck and flight path

`GasDispersionAnalyzer` and `GaussianPlume` model a release at one height with passive
spreading; they have no jet momentum rise, no stable-air plume-rise limit, no
receptor geometry (helideck, flight path) and no wind-rose weighting. In `AUTO` mode
the analyzer picks Gaussian or dense gas from the density at **ambient** temperature,
so a cold (-107 C) dense flare gas is routed to Gaussian: set the model explicitly
and set the endpoint explicitly (`sts0131IntegralEndpoint()` = 20 % LFL; the default is
50 % LFL). For an unignited flare, cold vent or relief tail pipe near a helideck use
the community skill `neqsim-flare-helideck-dispersion` (any release source and any receptor
- helideck, flight paths, air intakes, muster areas, escape routes - in one facility frame)
and agent
`flare-helideck-dispersion-agent` (neqsim-community-skills / neqsim-community-agents)
and keep these measured lessons (offshore platform study, 2026-11-20, screening model
checked against `GaussianPlume` within 2 %):

- Report the exceedance of **both** 10 % and 20 % LFL on the helideck, on the into-wind
  approach path and on any path of the obstacle-free sector, weighted by the joint
  wind direction x speed table; the result is **conditional on a release**, not a
  frequency.
- Apply the Briggs **stable-air limits** to momentum rise and to the sinking of a dense
  plume in classes E and F: neutral rise in stable air hid the exposure of the warm
  blowdown completely (0 % of hours above 20 % LFL instead of 6.6 %) in an independent
  review.
- Resolve flight-path bearings at 5 deg or finer; 15 deg steps understated the
  exceedance hours of narrow plumes (up to a factor of 2 in the review).
- Scan the tip inclination (20-70 deg): for cold dense gas the helideck peak went from
  0.2 % LFL at 40 deg to 19 % at 35 deg and above 100 % at 30 deg.
- API 520 sizes the relief device and fixes the rated release rate; atmospheric
  discharge and dispersion criteria are in API 521 section 5.8.
- Platform wake, turbulence over the deck and hot exhaust are not in a screening
  model; hand the low-wind cases to CFD (source term from
  `ConsequenceAnalysisEngine.exportSourceTerm()`).

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

## Method 9 — Flare Flame Radiation & Noise (API 537)

```java
import neqsim.process.safety.fire.Api537FlareFlameModel;

Api537FlareFlameModel flame = new Api537FlareFlameModel(
        50.0, 50.0e6, 0.20, 200.0)   // mDot[kg/s], HoC[J/kg], radiantFrac, vExit[m/s]
    .setStackHeightM(40.0)
    .setWindSpeedMPerS(10.0);

double r473 = flame.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_4_73_KW); // property line
double q75  = flame.heatFluxAtGroundDistance(75.0);  // W/m²
double spl  = flame.soundPressureLevelDb(100.0);     // dB at 100 m
```

Use this instead of the point-source jet-fire form when the source is an elevated
flare tip (it accounts for stack height, wind tilt, and flame geometry).
Height and wind speed must be finite and nonnegative. Transmissivity must be a
finite fraction in [0, 1]. Invalid values throw `IllegalArgumentException`.

## Method 10 — Hazardous-Area Zone Classification (IEC 60079-10-1)

```java
import neqsim.process.safety.dispersion.HazardousAreaCalculator;
import neqsim.process.safety.dispersion.HazardousAreaCalculator.ReleaseGrade;

HazardousAreaCalculator calc = new HazardousAreaCalculator(
        0.1,      // release mass flow [kg/s]
        6.0,      // process pressure [bara]
        340.0,    // temperature [K]
        0.044,    // LFL [volume fraction]
        0.01604)  // molar mass [kg/mol]
    .setReleaseGrade(ReleaseGrade.SECONDARY)  // CONTINUOUS / PRIMARY / SECONDARY
    .setSafetyFactor(0.5);

double dHaz = calc.hazardousDistanceM();
String zone = calc.zoneClassification();   // "Zone 0" / "Zone 1" / "Zone 2"
```

Maps the dispersion result to an Ex zone for electrical-equipment selection.
CONTINUOUS → Zone 0, PRIMARY → Zone 1, SECONDARY → Zone 2.

## Method 11 — Passive Fire Protection (PFP) Demand (API 521 / NORSOK S-001)

```java
import neqsim.process.safety.fire.PfpDemandCalculator;
import neqsim.process.safety.fire.PfpDemandCalculator.FireType;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpDemandResult;

PfpDemandResult pfp = new PfpDemandCalculator(
        100.0e3,   // fire heat flux [W/m²] (pool ~100 kW/m², jet ~250+ kW/m²)
        0.012)     // wall thickness [m]
    .setFireType(FireType.POOL)               // POOL / JET
    .evaluate(3600.0);                        // required survival time [s]

boolean need = pfp.isPfpRequired();
double thkMm = pfp.getRequiredPfpThicknessMm();
PfpDemandResult.PfpRating rating = pfp.getRating(); // NONE / H60 / J120 ...
```

Determines whether unprotected steel reaches its critical temperature before the
required survival time, and if so the intumescent thickness and H/J rating.
For custom temperatures, supply finite values in kelvin. The critical steel
temperature must exceed the positive initial steel temperature, and the
effective fire temperature must exceed their arithmetic mean. See the
[PFP demand guide](../../../docs/safety/flare_flame_hazardous_area_pfp.md).

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
./mvnw test -Dtest=FireModelsTest,GaussianPlumeTest,ProbitModelTest,ConsequenceAnalysisEngineTest,Api537FlareFlameModelTest,HazardousAreaCalculatorTest,PfpDemandCalculatorTest
```

## See Also

- `neqsim-process-safety` — frequency side (HAZOP / LOPA / SIL)
- `neqsim-firewater-deluge-design` — the mitigation side: how much fire water the area needs, and whether water is the right barrier for the fire type you just characterised
- `neqsim-relief-flare-network` — PSV sizing and flare radiation
- `neqsim-depressurization-mdmt` — emergency depressurization source terms
- `neqsim-agent-handoff` — source-term JSON schema
