---
name: neqsim-production-chemistry
description: "Production-chemistry patterns for NeqSim. USE WHEN: selecting or dosing production chemicals (scale inhibitor, corrosion inhibitor, MEG/MeOH THI, KHI/AA LDHI, wax inhibitor, asphaltene inhibitor, H2S scavenger, oxygen scavenger, biocide, demulsifier, antifoam, pH adjuster, chelant, acid), checking chemical-chemical or chemical-fluid COMPATIBILITY of an injection cocktail, computing minimum effective dose / MIC / residual saturation index / inhibited corrosion rate / scavenger breakthrough, placing a chemical injection point in a flowsheet, optimising demulsifier dose against an oil-in-water spec, quantifying how a treatment changes brine pH and scaling tendency, or running an explainable chemical root-cause analysis on a deposit, emulsion, pH excursion or H2S breakthrough. Anchors on neqsim.process.chemistry and neqsim.process.equipment.watertreatment."
last_verified: "2026-09-07"
---

# Production Chemistry with NeqSim

`neqsim.process.chemistry` is an open, standards-traceable production-chemistry stack:
chemical inventory, compatibility rules, per-threat dose-response models, a
flowsheet-visible injection point, and an explainable root-cause analyser. Every model
exposes `getStandardsApplied()` and `toMap()` / `toJson()` so results drop straight into
`results.json`.

## When to Use This Skill

- Which chemical, which chemistry family, and **what dose** for a given threat
- **Compatibility** of a chemical cocktail with itself, with the produced water, and with the material
- Minimum inhibitor concentration (MIC), residual SI after treatment, inhibited corrosion rate
- H2S scavenger demand, capacity and breakthrough time
- Hydrate inhibitor (MEG/MeOH) injection rate for a target subcooling; KHI induction time
- Demulsifier dose vs oil-in-water spec, dosing lag, monthly compliance
- How a pH adjuster / scavenger shifts the brine chemistry and the scaling tendency
- Chemical incident root cause (deposit, emulsion, corrosion, pH excursion, H2S breakthrough)
- Uncertainty (P10/P50/P90) on any of the above

## What This Skill Is NOT

- **Thermodynamics of the threat** — hydrate curve, WAT, asphaltene onset, SI from an ion
  table: load `neqsim-flow-assurance`, `neqsim-wax-calculations`, `neqsim-electrolyte-systems`.
  This skill consumes those results and answers *what chemical, how much, and what happens*.
- **Rigorous reaction kinetics** — load `neqsim-reaction-engineering`.
- **Equipment root cause from historian data** — load `neqsim-root-cause-analysis`.
  `RootCauseAnalyser` here is the *chemical* RCA (deposit / chemistry symptoms).

## Applicable Standards

| Domain | Standards |
|--------|-----------|
| Scale prediction and inhibitor testing | NACE TM0374, NORSOK M-001 |
| Corrosion inhibitor selection and monitoring | NACE SP0775, NORSOK M-506, ISO 21457 |
| Sour service / H2S | NACE MR0175 / ISO 15156, NACE TM0169, GPSA §21 |
| Produced water discharge | OSPAR 2001/1 (30 mg/L OiW monthly average), NORSOK S-002 |
| Chemical selection / HSE | OSPAR HOCNF, PLONOR (screening context only — NeqSim does not score ecotoxicity) |

Load `neqsim-standards-lookup` and emit `standards_applied` in `results.json`.

## 1. Chemical Inventory — `ProductionChemical`

Every chemical is described once and reused by the compatibility, scenario and RCA models.

```java
import neqsim.process.chemistry.ProductionChemical;

// Factory helpers set typical defaults for the family
ProductionChemical si = ProductionChemical.scaleInhibitor("SI-A", 20.0);      // 20 ppm
ProductionChemical ci = ProductionChemical.corrosionInhibitor("CI-B", 50.0);  // 50 ppm
ProductionChemical meg = ProductionChemical.thermodynamicHydrateInhibitor("MEG", 0.0);
ProductionChemical scav = ProductionChemical.h2sScavenger("Triazine", 100.0);

// Tune before evaluation
si.setActiveIngredient("phosphonate");
si.setActiveWtPct(35.0);
si.setIonicNature(ProductionChemical.IonicNature.ANIONIC);
si.setTemperatureRangeC(4.0, 150.0);
si.setPH(3.5);

boolean ok = si.isStableAt(120.0);
```

`ChemicalType`: `SCALE_INHIBITOR`, `CORROSION_INHIBITOR`, `HYDRATE_INHIBITOR_THERMODYNAMIC`,
`HYDRATE_INHIBITOR_LDHI`, `DEMULSIFIER`, `WAX_INHIBITOR`, `ASPHALTENE_INHIBITOR`, `BIOCIDE`,
`OXYGEN_SCAVENGER`, `H2S_SCAVENGER`, `ANTIFOAM`, `DRAG_REDUCER`, `PH_ADJUSTER`, `ACID`,
`CHELANT`, `OTHER`.

`IonicNature`: `CATIONIC`, `ANIONIC`, `NON_IONIC`, `AMPHOTERIC`, `UNKNOWN` — this is what
drives most incompatibility findings (anionic SI + cationic CI → precipitation).

## 2. Compatibility — `ChemicalCompatibilityAssessor`

Rule base is CSV-driven (`/data/chemical_compatibility_rules.csv`, loaded by
`ChemicalInteractionRule.loadDefaultRules()`), combined with the operating conditions and
water chemistry you supply.

```java
import neqsim.process.chemistry.ChemicalCompatibilityAssessor;

ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
assessor.addChemical(si);
assessor.addChemical(ci);
assessor.addChemical(scav);
assessor.setTemperatureCelsius(85.0);
assessor.setPressureBara(90.0);
assessor.setCalciumMgL(1200.0);
assessor.setIronMgL(5.0);
assessor.setBicarbonateMgL(300.0);
assessor.setMaterial("carbon steel");
assessor.evaluate();

ChemicalCompatibilityAssessor.Verdict verdict = assessor.getVerdict(); // COMPATIBLE / CAUTION / INCOMPATIBLE
List<Map<String, Object>> issues = assessor.getIssues();               // mechanism + mitigation per issue
Map<String, Map<String, String>> matrix = assessor.getInteractionMatrix();
Map<String, Boolean> thermal = assessor.getThermalStability();         // per chemical at T
String json = assessor.toJson();
```

**Always run this before recommending a dose.** A dose that is right in isolation can be
destroyed by the neighbouring injection point.

`ChemicalCompatibilityAssessor.fromStream(stream)` pre-fills T, P and the water chemistry
from a live NeqSim stream — see §8.

## 3. Dose-Response per Threat

All performance models follow the same pattern: setters → `evaluate()` → getters +
`getWarnings()` + `getStandardsApplied()` + `toMap()`.

### 3.1 Scale inhibitor — `ScaleInhibitorPerformance`

```java
import neqsim.process.chemistry.scale.ScaleInhibitorPerformance;

ScaleInhibitorPerformance sip = new ScaleInhibitorPerformance();
sip.setScaleType(ScaleInhibitorPerformance.ScaleType.BASO4);      // CACO3 BASO4 SRSO4 CASO4 FECO3
sip.setInhibitorChemistry(ScaleInhibitorPerformance.InhibitorChemistry.PHOSPHONATE);
sip.setTemperatureCelsius(95.0);
sip.setSaturationRatio(12.0);      // from the SI/SR calculation (see neqsim-flow-assurance)
sip.setTdsMgL(90000.0);
sip.setCalciumMgL(1200.0);
sip.setAvailableDoseMgL(15.0);     // what the squeeze/umbilical actually delivers
sip.evaluate();

double mic = sip.getMinimumInhibitorConcentrationMgL();
double recommended = sip.getRecommendedDoseMgL();
double eff = sip.getEfficiency();
boolean adequate = sip.isAdequate();   // available dose >= MIC
```

`InhibitorChemistry`: `PHOSPHONATE`, `POLYMALEATE`, `POLYACRYLATE`, `PHOSPHATE_ESTER`,
`VINYL_SULPHONATE`.

### 3.2 Whole-brine scale control — `ScaleControlAssessor`

Couples a `ScalePredictionCalculator` with one inhibitor model per mineral and reports the
**residual** SI and a kinetic risk index.

```java
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;
import neqsim.process.chemistry.scale.ScaleControlAssessor;

ScalePredictionCalculator pred = new ScalePredictionCalculator();
pred.setTemperatureCelsius(95.0);
pred.setPressureBara(150.0);
pred.setCalciumConcentration(1200.0);
pred.setBariumConcentration(250.0);
pred.setSulphateConcentration(1800.0);
pred.setBicarbonateConcentration(300.0);
pred.setTotalDissolvedSolids(90000.0);
pred.setPH(6.2);

ScaleControlAssessor control = new ScaleControlAssessor(pred);
control.addInhibitor(ScaleInhibitorPerformance.ScaleType.BASO4, sip);
control.evaluate();

double residual = control.getResidualSI(ScaleInhibitorPerformance.ScaleType.BASO4);
double worst = control.getWorstResidualSI();
boolean controlled = control.isControlled(0.0);          // residual SI <= threshold
boolean kinetic = control.isKineticallyControlled(1.0);
```

### 3.3 Corrosion inhibitor — `CorrosionInhibitorPerformance`

Langmuir adsorption with a van 't Hoff temperature term, shear-stripping, and penalties for
O2, organic acid and H2S.

```java
import neqsim.process.chemistry.corrosion.CorrosionInhibitorPerformance;

CorrosionInhibitorPerformance cip = new CorrosionInhibitorPerformance();
cip.setChemistry(CorrosionInhibitorPerformance.InhibitorChemistry.IMIDAZOLINE);
cip.setDoseMgL(50.0);
cip.setBaseCorrosionRateMmYr(2.4);      // from NORSOK M-506 / de Waard-Milliams
cip.setTemperatureCelsius(70.0);
cip.setWallShearStressPa(35.0);
cip.setOrganicAcidPpm(200.0);
cip.setH2SPartialPressureBar(0.02);
cip.setOxygenPpb(10.0);
cip.evaluate();

double efficiency = cip.getEfficiency();                       // 0..1
double inhibited = cip.getInhibitedCorrosionRateMmYr();
double minDose = cip.getMinimumEffectiveDoseMgL();
Map<String, String> warn = cip.getWarnings();                  // e.g. O2 > 50 ppb kills film
```

`InhibitorChemistry`: `IMIDAZOLINE`, `QUATERNARY_AMMONIUM`, `AMIDO_AMINE`, `PHOSPHATE_ESTER`,
`PYRIDINE`, `MERCAPTAN`. `setFromDeWaardMilliams(baseline)` takes the uninhibited rate
directly from a `DeWaardMilliamsCorrosion` object.

> **O2 ingress dominates.** Above ~50 ppb the model warns that film-forming CI is undermined —
> the answer is an oxygen scavenger or eliminating the ingress, not more CI.

### 3.4 Thermodynamic hydrate inhibitor (MEG / MeOH) — `ThermodynamicHydrateInhibitorPerformance`

```java
import neqsim.process.chemistry.hydrate.ThermodynamicHydrateInhibitorPerformance;

ThermodynamicHydrateInhibitorPerformance thi =
    new ThermodynamicHydrateInhibitorPerformance();
thi.setInhibitorChemistry(ThermodynamicHydrateInhibitorPerformance.InhibitorChemistry.MEG);
thi.setTargetSubcoolingC(8.0);          // hydrate T minus minimum operating T
thi.setWaterFlowKgPerHour(1500.0);      // free + condensed water
thi.setInhibitorPurityWtPct(90.0);      // rich/lean MEG purity
thi.setLeanInhibitorWtPctInWater(0.0);  // already present in the water
thi.evaluate();

double wtPct = thi.getRequiredInhibitorWtPctInWater();
double kgHr = thi.getRequiredInjectionKgPerHour();
```

`InhibitorChemistry`: `METHANOL`, `MEG`, `DEG`, `TEG` (Hammerschmidt K built in).

> Hammerschmidt is a screening correlation. For the design number, compute the inhibited
> hydrate curve with `SystemSrkCPAstatoil` + `hydrateFormationTemperature()`
> (`neqsim-flow-assurance`) and use this model for the injection-rate bookkeeping and
> lean/rich balance.

### 3.5 Kinetic hydrate inhibitor (KHI) — `KineticHydrateInhibitorPerformance`

```java
import neqsim.process.chemistry.hydrate.KineticHydrateInhibitorPerformance;

KineticHydrateInhibitorPerformance khi = new KineticHydrateInhibitorPerformance();
khi.setSubcoolingC(6.0);
khi.setDoseWtPct(0.5);
khi.setTargetInductionTimeHours(24.0);
khi.evaluate();

double tInd = khi.getPredictedInductionTimeHours();
double required = khi.getRequiredDoseWtPct();
```

Calibrate with `setCoefficients(a, b, c)` against vendor rocking-cell data before quoting.

### 3.6 Wax and asphaltene inhibitors

```java
import neqsim.process.chemistry.wax.WaxInhibitorPerformance;
import neqsim.process.chemistry.asphaltene.AsphalteneInhibitorPerformance;

WaxInhibitorPerformance wax = new WaxInhibitorPerformance();
wax.setInhibitorChemistry(WaxInhibitorPerformance.InhibitorChemistry.EVA);
wax.setBasePourPointC(24.0);
wax.setBaseWaxAppearanceTemperatureC(32.0);   // untreated WAT from neqsim-wax-calculations
wax.setDoseMgL(300.0);
wax.evaluate();
double ppd = wax.getPourPointDepressionC();
double treatedWat = wax.getInhibitedWaxAppearanceTemperatureC();

AsphalteneInhibitorPerformance asp = new AsphalteneInhibitorPerformance();
asp.setInhibitorChemistry(AsphalteneInhibitorPerformance.InhibitorChemistry.ALKYLPHENOL_RESIN);
asp.setBaseColloidalInstabilityIndex(1.2);
asp.setBaseAsphalteneOnsetPressureBara(280.0);
asp.setDoseMgL(150.0);
asp.evaluate();
boolean stable = asp.isStableAfterTreatment();
```

A wax inhibitor depresses the **pour point** far more than the WAT — do not sell a WAT shift
the model does not give you.

### 3.7 H2S scavenger — `H2SScavengerPerformance`

```java
import neqsim.process.chemistry.scavenger.H2SScavengerPerformance;

H2SScavengerPerformance scv = new H2SScavengerPerformance();
scv.setChemistry(H2SScavengerPerformance.ScavengerChemistry.MEA_TRIAZINE);
scv.setActiveWtPct(40.0);
scv.setScavengerInventoryKg(20000.0);
scv.setGasFlowMSm3PerDay(4.0);
scv.setH2SInletPpm(35.0);
scv.setH2STargetPpm(4.0);       // sales-gas spec
scv.setTemperatureCelsius(40.0);
scv.setPressureBara(70.0);
scv.evaluate();

double removeKgD = scv.getH2SToRemoveKgPerDay();
double demandKgD = scv.getScavengerDemandKgPerDay();
double capacity = scv.getCapacityKgH2SPerKgActive();
double breakthroughDays = scv.getBreakthroughDays();
```

`ScavengerChemistry`: `MEA_TRIAZINE`, `MMA_TRIAZINE`, `IRON_CHELATE`, `IRON_SPONGE`,
`ALDEHYDE`. For a **solid packed bed** with an axial profile use
`neqsim.process.chemistry.scavenger.PackedBedScavengerReactor` (1D plug flow, per-cell
inventory depletion, breakthrough time). For a **gas contactor unit operation inside a
flowsheet**, use `neqsim.process.equipment.absorber.H2SScavenger`.

> Triazine overdose forms amorphous dithiazine deposits. If the symptom is a soft, sticky,
> sulphur-smelling deposit downstream of a scavenger skid, check
> `ScaleRemediationAdvisor` (it carries the amorphous scavenger-deposit case) and feed the
> symptom to `RootCauseAnalyser` — it has an explicit
> `H2S_SCAVENGER_UNDER_CAPACITY` / overdose candidate.

## 4. Injection Point as Flowsheet Equipment — `InhibitorInjectionPoint`

Makes chemical injection visible, snapshot-able and adjustable in a `ProcessSystem`.

```java
import neqsim.process.chemistry.equipment.InhibitorInjectionPoint;

InhibitorInjectionPoint inj = new InhibitorInjectionPoint("CI injection", wellStream);
inj.setChemical(ci);
inj.setDoseInPpmOnWater(50.0);     // or setDoseInKgPerHour(...)
process.add(inj);
process.run();

double ppmInWater = inj.getActiveIngredientPpmInWater();
double kgPerHour = inj.getInjectionRateKgPerHour();
```

`DoseMode`: `PPM` (on water mass), `PPM_TOTAL` (on total fluid mass), `KG_PER_HOUR`.

> It deliberately does **not** run an electrolyte flash on every call — it tracks the dose at
> the outlet and hands it to the dedicated chemistry models. Do not expect it to change the
> hydrate curve or pH by itself.

### 4.1 Does the chemical reach the gas? — `ChemicalInjectionNozzlePerformance`

`InhibitorInjectionPoint` and the dose-response models all assume the chemical is **dispersed** in
the phase it has to treat. For a liquid sprayed into a **gas** line — H2S scavenger into a separator
gas outlet, corrosion inhibitor or MEG into a wet-gas line — that assumption is the thing most
likely to be wrong. A bare injection quill releases a coarse jet that settles onto the pipe wall
within a few pipe diameters and treats nothing; an atomizing nozzle produces a fine spray that stays
entrained. `neqsim.process.chemistry.injection.ChemicalInjectionNozzlePerformance` quantifies the
difference so a quill-to-nozzle modification can be evaluated instead of asserted.

```java
import neqsim.process.chemistry.injection.ChemicalInjectionNozzlePerformance;
import neqsim.process.chemistry.injection.ChemicalInjectionNozzlePerformance.InjectionDevice;

ChemicalInjectionNozzlePerformance nozzle = new ChemicalInjectionNozzlePerformance();
nozzle.setInjectionDevice(InjectionDevice.FULL_CONE_NOZZLE);   // or PLAIN_QUILL
nozzle.setPipeInnerDiameter(0.4889);
nozzle.setGasVolumeFlow(gasStream.getFlowRate("m3/sec"));
nozzle.setGasDensity(gasStream.getFluid().getDensity("kg/m3"));
nozzle.setGasViscosity(gasStream.getFluid().getPhase("gas").getViscosity("kg/msec"));
nozzle.setChemicalVolumeFlow(235.0);          // l/h
nozzle.setChemicalDensity(1080.0);
nozzle.setChemicalViscosity(8.0e-3);
nozzle.setSurfaceTension(0.040);
nozzle.setNozzleDifferentialPressure(6.5);    // bar across the device
nozzle.setInsertionDepth(0.135);              // from the wall; piping specs cap this
nozzle.evaluate();

double smd = nozzle.getSauterMeanDiameterMicron();
double reachDiameters = nozzle.getWallImpingementLength() / 0.4889;
scavenger.setMixingEfficiency(nozzle.getDispersionIndex());
```

Rules of thumb the class encodes, and the reason each matters:

| Rule | Why |
|---|---|
| A bare quill needs about **10 m/s** gas velocity | Drop size from aerodynamic breakup is `We_crit σ / (ρ_G u²)` — velocity enters squared, so halving it quadruples the drop size |
| Target **10–50 µm**, preferably 20–40 µm | Interfacial area is `6 Q_L / (SMD · Q_G)`, so area buys treatment; below 10 µm the mist carries over into downstream scrubbers |
| Atomisation is bought with **pump ΔP** | Lefebvre gives `SMD ∝ ΔP^-0.5`; quartering ΔP doubles the drop size |
| A fixed-orifice nozzle **degrades on turndown** | `Q = K√ΔP` gives `SMD ∝ Q^-0.75`; halving the dose coarsens the spray 1.7× — so a ramp-up from low rates runs at the worst atomisation unless a separate low-rate nozzle is fitted |
| Two nozzles in parallel are **coarser** than one | Splitting a fixed total rate quarters the ΔP per nozzle. Parallel operation buys capacity, never quality |
| Insertion limits push the nozzle **off centre** | The drop flight path before wall contact is `u_G · h / v_t`; a shallower insertion shortens `h` and with it the treated length |

> Feed `getDispersionIndex()` into `H2SScavenger.setMixingEfficiency(...)` rather than guessing a
> value. If the index is low, the answer to poor treatment is the injection hardware, not more
> chemical.

## 5. Produced Water — Demulsifier Dose vs Oil-in-Water

```java
import neqsim.process.equipment.watertreatment.DemulsifierDoseResponseModel;
import neqsim.process.equipment.watertreatment.OilInWaterDoseOptimizer;

DemulsifierDoseResponseModel dr = new DemulsifierDoseResponseModel();
double rms = dr.calibrate(dosePpmArray, observedOiwArray, 250.0);  // untreated OiW mg/L
double oiw = dr.predictOilInWater(250.0, 12.0);                    // effective dose ppm

OilInWaterDoseOptimizer opt = new OilInWaterDoseOptimizer();
opt.setDoseResponseModel(dr);
opt.setDoseRange(2.0, 40.0, 0.5);
opt.setSafetyMarginMgL(3.0);
OilInWaterDoseOptimizer.DoseRecommendation rec = opt.recommendDose(250.0, 900.0, 18);
double setpoint = rec.getSetpointDosePpm();
boolean feasible = rec.isFeasible();
```

The optimiser accounts for **dosing lag** (`ChemicalDoseLagModel` — first-order chemical
inventory after a setpoint change), analyser drift, and the OSPAR 30 mg/L *monthly average*
via `addMonthlySample(oiwMgL, volumeM3)` — so a mid-month excursion is traded against the
running average rather than the instantaneous reading. Overdosing is modelled (the response
curve turns over past the optimum): more demulsifier is not monotonically better.

Emulsion viscosity for the hydraulics side: `EmulsionViscosityCalculator`
(`neqsim.pvtsimulation.flowassurance`) has a `demulsifierPresent` / efficiency correction.

## 6. Treatment Effect on Brine Chemistry — `ProductionChemicalScaleScenario`

Answers "if I inject this pH stabiliser / scavenger, what happens to the scaling tendency?"
It converts chemical alkalinity to a closed carbonate balance, consumes dissolved sulphide by
scavenger capacity, and reports SI **before and after**.

```java
import neqsim.process.chemistry.scale.ProductionChemicalScaleScenario;

ProductionChemicalScaleScenario sc = new ProductionChemicalScaleScenario();
sc.addChemical(ProductionChemical.causticPHAdjuster("NaOH", 500.0))
  .setTemperatureCelsius(80.0)
  .setPressureBara(60.0)
  .setPH(5.8)
  .setCalciumMgL(1200.0)
  .setBicarbonateMgL(300.0)
  .setSulphateMgL(1800.0)
  .setBariumMgL(250.0)
  .setTotalDissolvedSolidsMgL(90000.0)
  .setCO2PartialPressureBar(1.5);
sc.evaluate();

double treatedPH = sc.getTreatedPH();
double before = sc.getBaselineSaturationIndex("CaCO3");
double after = sc.getTreatedSaturationIndex("CaCO3");
double delta = sc.getSaturationIndexChange("CaCO3");
```

Supported pH actives: NaOH, soda ash, MDEA, MEA. This is the model that catches the classic
own-goal: a pH stabiliser injected for corrosion control pushes CaCO3 into scaling.

## 7. Chemical Root Cause — `RootCauseAnalyser`

Rule-based and **explainable** — every candidate carries an evidence narrative and a score,
tagged PRIMARY / CONTRIBUTING / POSSIBLE / RULED_OUT.

```java
import neqsim.process.chemistry.rca.RootCauseAnalyser;
import neqsim.process.chemistry.rca.Symptom;

RootCauseAnalyser rca = new RootCauseAnalyser();
rca.addSymptom(new Symptom(Symptom.Category.DEPOSIT, "hard white deposit in choke")
        .withMeasurement("deposit_thickness_mm", 3.0)
        .withConfidence(0.9));
rca.addSymptom(new Symptom(Symptom.Category.FLOW_RESTRICTION, "choke Cv down 25%"));
rca.addChemical(si);
rca.addChemical(ci);
rca.setCompatibilityAssessor(assessor);
rca.setChemicalTreatmentScenario(sc);
rca.setTemperatureCelsius(85.0);
rca.setPH(5.8);
rca.setCalciumMgL(1200.0);
rca.setBariumMgL(250.0);
rca.setSulphateMgL(1800.0);
rca.setOxygenPpb(15.0);
rca.setMaterial("carbon steel");
rca.analyse();

RootCauseCandidate primary = rca.getPrimary();
List<RootCauseCandidate> ranked = rca.getCandidates();
List<String> gaps = rca.getDataGaps();   // put these in results.json assumptions/gaps
```

`Symptom.Category`: `DEPOSIT`, `CORROSION`, `EMULSION`, `PH_EXCURSION`, `FLOW_RESTRICTION`,
`H2S_BREAKTHROUGH`, `SAMPLE_APPEARANCE`, `OFF_SPEC`, `OTHER`.

Add measurement evidence to sharpen the ranking:

```java
Map<String, Double> likelihoods = new LinkedHashMap<String, Double>();
likelihoods.put("BASO4_SCALE", 0.8);
likelihoods.put("CACO3_SCALE", 0.2);
rca.addEvidence(likelihoods);
Map<String, Double> posteriors = rca.getBayesianPosteriors();
```

Then close the loop with the remediation side:

```java
ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
List<ScaleRemediationAdvisor.RemediationOption> options = advisor.recommendFor("BaSO4");
```

`ScaleRemediationAdvisor` covers CaCO3, FeCO3, FeS, BaSO4, SrSO4, CaSO4, NaCl and the
amorphous H2S-scavenger deposit, with dissolver, concentration, method, temperature window,
cautions and a standard reference per option. Details in `neqsim-flow-assurance`.

## 8. Getting Inputs from a Live Stream — `StreamChemistryAdapter`

Do not hand-transcribe the water analysis. Pull it from the flowsheet:

```java
import neqsim.process.chemistry.util.StreamChemistryAdapter;

StreamChemistryAdapter ad = new StreamChemistryAdapter(stream);
double tC = ad.getTemperatureCelsius();
double pCO2 = ad.getPartialPressureBara("CO2");
double ca = ad.getCalciumMgL();
double tds = ad.getTdsMgL();
double h2sPpm = ad.getH2SInGasPpm();
double gasSm3d = ad.getGasFlowSm3PerDay();
double tau = ad.estimateWallShearStressPa(0.2, 4.0);   // pipe ID m, velocity m/s
```

Convenience constructors that do this for you:

| Call | Fills |
|------|-------|
| `ChemicalCompatibilityAssessor.fromStream(stream)` | T, P, Ca, Fe, HCO3 |
| `CorrosionInhibitorPerformance.fromStream(stream, pipeIdM, velocityMps)` | T, shear, pH2S, base rate inputs |
| `ScaleControlAssessor.fromStream(stream)` | full ion table into the predictor |
| `RootCauseAnalyser.setWaterChemistryFromStream(stream)` | full ion table + partial pressures |

The stream must carry an aqueous phase with the ions — build it with
`SystemElectrolyteCPAstatoil` (`neqsim-electrolyte-systems`). A dry-gas stream returns zeros;
check before trusting a "no scale risk" verdict.

## 9. Uncertainty — `ChemistryUncertaintyAnalyzer`

Doses are quoted with far more precision than the inputs justify. Report a band.

```java
import neqsim.process.chemistry.util.ChemistryUncertaintyAnalyzer;

ChemistryUncertaintyAnalyzer unc = new ChemistryUncertaintyAnalyzer();
unc.setNumberOfTrials(2000);
unc.setRandomSeed(42L);
unc.addParameter(unc.triangular("baseRateMmYr", 1.5, 2.4, 4.0));
unc.addParameter(unc.triangular("shearPa", 20.0, 35.0, 60.0));
unc.run(new java.util.function.ToDoubleFunction<double[]>() {
  public double applyAsDouble(double[] x) {
    CorrosionInhibitorPerformance m = new CorrosionInhibitorPerformance();
    m.setChemistry(CorrosionInhibitorPerformance.InhibitorChemistry.IMIDAZOLINE);
    m.setDoseMgL(50.0);
    m.setBaseCorrosionRateMmYr(x[0]);
    m.setWallShearStressPa(x[1]);
    m.setTemperatureCelsius(70.0);
    m.evaluate();
    return m.getInhibitedCorrosionRateMmYr();
  }
});

double p10 = unc.getP10();
double p50 = unc.getP50();
double p90 = unc.getP90();
List<Map<String, Object>> tornado = unc.getTornado();
```

Feed `p10/p50/p90` and `tornado` straight into the `uncertainty` block of `results.json`
(`neqsim-professional-reporting`). Java 8: use an anonymous `ToDoubleFunction`, not a lambda,
if the surrounding code style requires it — both compile.

## 10. MCP Tools

| Tool | `analysis` values |
|------|-------------------|
| `runChemistry` | `electrolyteScale`, `multiMineralScale`, `electrolyteScaleEquilibrium`, `electrolyteMultiScaleEquilibrium`, `mechanisticCorrosion`, `langmuirInhibitor`, `packedBedScavenger`, `pitzerQualification` |
| `runFlowAssurance` | `scalePrediction`, `erosion`, `pipelineCooldown`, `emulsionViscosity`, `demulsifierDoseOptimization`, hydrate analyses |

```json
{
  "analysis": "mechanisticCorrosion",
  "temperature_C": 60, "pressure_bara": 80, "co2_mol": 0.05,
  "velocity_ms": 2.0, "diameter_m": 0.15, "dose_mgL": 50
}
```

## Python Pattern (task notebooks)

```python
ProductionChemical = ns.JClass("neqsim.process.chemistry.ProductionChemical")
Assessor = ns.JClass("neqsim.process.chemistry.ChemicalCompatibilityAssessor")
CIP = ns.JClass("neqsim.process.chemistry.corrosion.CorrosionInhibitorPerformance")

si = ProductionChemical.scaleInhibitor("SI-A", 20.0)
ci = ProductionChemical.corrosionInhibitor("CI-B", 50.0)

a = Assessor()
a.addChemical(si); a.addChemical(ci)
a.setTemperatureCelsius(85.0); a.setCalciumMgL(1200.0)
a.evaluate()

import json
report = json.loads(str(a.toJson()))
print(report["verdict"])
```

Enums through jpype: `CIP.InhibitorChemistry.valueOf("IMIDAZOLINE")`.

## Offshore Workflow

1. **Basis** — build the produced-water stream (`SystemElectrolyteCPAstatoil`) from the ion
   analysis; get T/P/rates from the flowsheet or historian.
2. **Threat magnitude** — SI, uninhibited corrosion rate, hydrate subcooling, WAT, CII
   (`neqsim-flow-assurance`, `neqsim-wax-calculations`).
3. **Chemical selection + dose** — the §3 performance models.
4. **Compatibility of the full cocktail** — §2. Repeat per injection point, because the
   cocktail differs at the wellhead, the manifold and the topside inlet.
5. **Second-order effects** — §6 (does the pH adjuster create a scale problem?), §5 (does the
   demulsifier meet the OiW spec?).
6. **Uncertainty + gaps** — §9 plus `getWarnings()` / `getDataGaps()` from every model.
7. **Report** — `standards_applied`, `key_results`, `uncertainty`, `risk_evaluation`.

## Agent Cooperation

| Need | Route to |
|------|----------|
| Hydrate curve, WAT, SI, corrosion rate | `neqsim-flow-assurance`, `neqsim-wax-calculations` |
| Building the brine / electrolyte fluid | `neqsim-electrolyte-systems` |
| Material limits, sour service | `neqsim-standards-lookup`, `enterprise-materials-selection-screening` |
| MEG loop mass balance, regeneration | `enterprise-meg-loop` |
| Measured dose / OiW / H2S tags | `neqsim-plant-data` |
| Equipment-level RCA from historian trends | `neqsim-root-cause-analysis` |
| Report structure, results.json schema | `neqsim-professional-reporting` |

## Gotchas

| Symptom | Cause | Fix |
|---------|-------|-----|
| All getters return 0 | `evaluate()` not called | Setters → `evaluate()` → getters. `isEvaluated()` tells you. |
| Compatibility verdict COMPATIBLE with an obviously bad pair | Chemicals lack `activeIngredient` / `ionicNature` | The rule base matches on type **and** ingredient; set both. |
| Scale/corrosion models return zeros from a stream | Stream has no aqueous phase or no ions | Build with `SystemElectrolyteCPAstatoil` and add ions; verify with `StreamChemistryAdapter`. |
| MIC far above the umbilical capacity | Saturation ratio, not SI, is the input | `setSaturationRatio()` expects SR (=10^SI), not the log. |
| CI efficiency collapses | O2 ppb or shear too high | Read `getWarnings()`; the fix is scavenger / ingress control, not more CI. |
| Hydrate injection rate disagrees with the flash | Hammerschmidt is a screening correlation | Use the CPA inhibited hydrate curve for design; use this for rate bookkeeping. |
| `InhibitorInjectionPoint` does not change pH/hydrate T | By design (no flash on run) | Pass `getActiveIngredientPpmInWater()` into the dedicated chemistry model. |

## Reference Documentation

- [docs/chemistry/index.md](../../../docs/chemistry/index.md) — capability matrix and standards
- [docs/chemistry/chemical_compatibility_guide.md](../../../docs/chemistry/chemical_compatibility_guide.md)
- [docs/chemistry/mechanistic_corrosion.md](../../../docs/chemistry/mechanistic_corrosion.md)
- [docs/chemistry/packed_bed_scavenger.md](../../../docs/chemistry/packed_bed_scavenger.md)
- Notebooks: `examples/notebooks/chemical_integrity_digital_twin.ipynb`,
  `chemistry_corrosion_inhibitor_design.ipynb`
- Tests / regression baseline: `src/test/java/neqsim/process/chemistry/`
