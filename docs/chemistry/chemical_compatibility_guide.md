---
title: Chemical Compatibility and Root Cause Analysis Guide
description: Comprehensive guide to NeqSim's production chemistry toolkit — compatibility screening, scale and corrosion inhibitor performance, acid stimulation, H2S scavenger sizing, and rule-based root cause analysis for well, flow assurance, and topside process incidents.
---

# Chemical Compatibility and Root Cause Analysis

This guide describes NeqSim's production-chemistry capabilities for evaluating
**chemical compatibility** and performing **root cause analysis (RCA)** of
chemistry-driven problems in wells, flow assurance, and process operation. It
covers scale, corrosion, acid treatment, and H₂S scavenging, with a unified
entry point for incident investigation.

---

## 1. Overview

The toolkit lives in `neqsim.process.chemistry` and its sub-packages:

| Package                                       | Purpose                                                     |
| --------------------------------------------- | ----------------------------------------------------------- |
| `neqsim.process.chemistry`                    | Chemical inventory, interaction rules, compatibility verdict |
| `neqsim.process.chemistry.scale`              | Scale inhibitor performance & control                        |
| `neqsim.process.chemistry.corrosion`          | Corrosion inhibitor performance                              |
| `neqsim.process.chemistry.acid`               | Acid stimulation simulator                                   |
| `neqsim.process.chemistry.scavenger`          | H₂S scavenger sizing                                         |
| `neqsim.process.chemistry.rca`                | Rule-based root cause analyser                               |

All classes follow the standard NeqSim pattern: configure with setters, call
`evaluate()` (or `analyse()`), then read structured results via getters and
`toMap()` / `toJson()`.

---

## 2. Chemical Compatibility Screening

### 2.1 Build a chemical inventory

```java
ProductionChemical ci = ProductionChemical.corrosionInhibitor("CI-quat-50", 50.0);
ProductionChemical si = ProductionChemical.scaleInhibitor("SI-phosphonate", 15.0);
ProductionChemical scav = ProductionChemical.h2sScavenger("MEA-triazine", 100.0);
ProductionChemical hcl = ProductionChemical.acid("HCl-15%", 15.0);
```

The factory methods set ionic nature, default thermal limits, and chemical
type. Sixteen chemical types are supported (see `ProductionChemical.ChemicalType`).

### 2.2 Run the assessor

```java
ChemicalCompatibilityAssessor assessor = new ChemicalCompatibilityAssessor();
assessor.addChemical(ci);
assessor.addChemical(si);
assessor.addChemical(scav);
assessor.setTemperatureCelsius(85.0);
assessor.setCalciumMgL(2500.0);
assessor.setMaterial("carbon_steel");
assessor.evaluate();

ChemicalCompatibilityAssessor.Verdict verdict = assessor.getVerdict();
// COMPATIBLE | CAUTION | INCOMPATIBLE
```

Rules are loaded from `src/main/resources/data/chemical_compatibility_rules.csv`
and cover ~30 known interactions: cationic CI + anionic SI (HIGH), CI + acid,
phosphonate + HCl, phosphonate + high Ca, triazine + high T, etc.

### 2.3 Severity ranking

Issues are tagged HIGH / MEDIUM / LOW / INFO. The worst severity drives the
verdict:

| Worst severity | Verdict        |
| -------------- | -------------- |
| HIGH           | INCOMPATIBLE   |
| MEDIUM         | CAUTION        |
| LOW / INFO     | COMPATIBLE     |

---

## 3. Scale Inhibitor Performance

`ScaleInhibitorPerformance` predicts the minimum inhibitor concentration (MIC),
recommended dose, and efficiency for a given brine.

```java
ScaleInhibitorPerformance sip = new ScaleInhibitorPerformance();
sip.setScaleType(ScaleInhibitorPerformance.ScaleType.CACO3);
sip.setInhibitorChemistry(ScaleInhibitorPerformance.InhibitorChemistry.PHOSPHONATE);
sip.setTemperatureCelsius(80.0);
sip.setSaturationRatio(50.0);    // SR from a scale predictor
sip.setCalciumMgL(2000.0);
sip.setTdsMgL(80000.0);
sip.setAvailableDoseMgL(20.0);
sip.evaluate();

double mic = sip.getMinimumInhibitorConcentrationMgL();
double rec = sip.getRecommendedDoseMgL();
double eff = sip.getEfficiency();
```

The MIC scales with temperature, TDS, supersaturation, and Ca content.
Recommended dose = 1.5 × MIC. Efficiency saturates as `1 − exp(−(dose/MIC − 1))`.

### 3.1 Combined scale prediction + inhibitor

`ScaleControlAssessor` wraps a `ScalePredictionCalculator` plus inhibitor
models per scale type:

```java
ScalePredictionCalculator predictor = new ScalePredictionCalculator();
predictor.setTemperatureCelsius(80.0);
predictor.setCalciumConcentration(2000.0);
predictor.setBicarbonateConcentration(800.0);
predictor.setSulphateConcentration(500.0);

ScaleControlAssessor assessor = new ScaleControlAssessor(predictor);
assessor.addInhibitor(ScaleInhibitorPerformance.ScaleType.CACO3, sip);
assessor.evaluate();

double residualSI = assessor.getResidualSI(ScaleInhibitorPerformance.ScaleType.CACO3);
boolean controlled = assessor.isControlled(0.5);
```

The residual SI is approximated as $SI_\text{inh} = SI + \log_{10}(1 - \eta)$.

---

## 4. Corrosion Inhibitor Performance

`CorrosionInhibitorPerformance` uses a Langmuir adsorption framework with
chemistry-specific maximum efficiencies (0.90–0.97) and penalty factors for
temperature, shear, oxygen, organic acids, and acid service.

```java
CorrosionInhibitorPerformance cip = new CorrosionInhibitorPerformance();
cip.setInhibitorChemistry(CorrosionInhibitorPerformance.InhibitorChemistry.IMIDAZOLINE);
cip.setBaseCorrosionRateMmYr(2.0);
cip.setDoseMgL(50.0);
cip.setTemperatureCelsius(90.0);
cip.setWallShearStressPa(80.0);
cip.setOxygenPpb(20.0);
cip.evaluate();

double inhibitedRate = cip.getInhibitedCorrosionRateMmYr();
double efficiency = cip.getEfficiency();
```

Warnings are raised for thermal degradation (>150 °C non phosphate-ester),
high shear (>150 Pa), oxygen ingress (>50 ppb), acid / sour service.

---

## 5. Acid Treatment Simulator

`AcidTreatmentSimulator` models matrix acid jobs and returns the dissolved
scale mass, CO₂ generated, and spent-acid pH.

```java
AcidTreatmentSimulator sim = new AcidTreatmentSimulator();
sim.setAcidType(AcidTreatmentSimulator.AcidType.HCL);
sim.setAcidVolumeM3(1.0);
sim.setAcidWtPct(15.0);
sim.setScaleMassKg(500.0);
sim.setScaleMineralogy("CACO3");
sim.setTemperatureCelsius(80.0);
sim.setMaterial("carbon_steel");
sim.evaluate();

double dissolvedKg = sim.getScaleDissolvedKg();
double co2Kg = sim.getCO2GeneratedKg();
double spentPH = sim.getSpentAcidPH();
```

Reactions covered: $2\,\text{HCl} + \text{CaCO}_3$, $\text{HF} + \text{CaCO}_3$
(with CaF₂ risk), formic / acetic acid for HT applications. Severe-corrosion
warnings trigger when HCl > 5 wt% on carbon steel above 60 °C without inhibitor.

---

## 6. H₂S Scavenger Sizing

`H2SScavengerPerformance` sizes triazine and solid-bed scavengers.

```java
H2SScavengerPerformance scav = new H2SScavengerPerformance();
scav.setScavengerChemistry(
    H2SScavengerPerformance.ScavengerChemistry.MEA_TRIAZINE);
scav.setGasFlowSm3PerDay(2.0e6);
scav.setH2SInletPpm(150.0);
scav.setH2SOutletTargetPpm(4.0);
scav.setTemperatureCelsius(50.0);
scav.setInventoryKg(5000.0);
scav.evaluate();

double demandKgPerDay = scav.getH2SRemovedKgPerDay();
double doseLPerDay = scav.getRequiredDoseLPerDay();
double breakthroughDays = scav.getBreakthroughDays();
```

Conversion uses ISO 13443: 1 Sm³ ≈ 41.6 mol gas. Triazine stoichiometry is set
to 1.0 mol triazine per mol H₂S (engineering value, vs. theoretical 2/3) to
account for non-ideal contact. Warnings are raised for triazine decomposition
(>80 °C), dithiazine deposition risk (>1000 ppm H₂S inlet on MEA-triazine), and
pyrophoric FeS handling on solid beds.

---

## 7. Root Cause Analysis

`RootCauseAnalyser` is the integrative entry point. Given observed symptoms and
process context, it returns ranked candidate root causes with evidence and
mitigation recommendations.

### 7.1 Symptoms

A `Symptom` carries a category, a description, optional measurements, and a
confidence score.

| Category            | Example                                            |
| ------------------- | -------------------------------------------------- |
| `DEPOSIT`           | "White crystalline scale found in cooler tubes"   |
| `CORROSION`         | "Internal wall thinning observed in flowline"     |
| `EMULSION`          | "Stable rag layer at HP separator"                |
| `PH_EXCURSION`      | "Outlet pH dropped to 4.2"                         |
| `FLOW_RESTRICTION`  | "Pressure drop doubled across tubing"             |
| `H2S_BREAKTHROUGH`  | "H₂S = 12 ppm at sales gas, target 4 ppm"         |
| `SAMPLE_APPEARANCE` | "Black gel found at injection manifold"           |
| `OFF_SPEC`          | "BS&W exceeded 0.5%"                              |
| `OTHER`             | Custom                                             |

### 7.2 Run an analysis

```java
RootCauseAnalyser rca = new RootCauseAnalyser();
rca.setTemperatureCelsius(75.0);
rca.setPH(7.5);
rca.setCalciumMgL(2500.0);
rca.setCO2PartialPressureBar(2.0);
rca.setMaterial("carbon_steel");

rca.addSymptom(new Symptom(Symptom.Category.DEPOSIT,
    "White crystalline scale found in cooler tubes")
        .withMeasurement("depositMassGrams", 250.0));
rca.addSymptom(new Symptom(Symptom.Category.CORROSION,
    "Internal wall thinning observed in flowline")
        .withMeasurement("corrosionRateMmYr", 1.2));

rca.analyse();

RootCauseCandidate primary = rca.getPrimary();
List<RootCauseCandidate> all = rca.getCandidates();
String json = rca.toJson();
```

### 7.3 Output structure

Each candidate has:

| Field            | Meaning                                                    |
| ---------------- | ---------------------------------------------------------- |
| `code`           | Stable machine-readable identifier (e.g. `MINERAL_SCALE`) |
| `description`    | Plain-language summary                                    |
| `score`          | 0..1 ranked likelihood                                     |
| `tag`            | PRIMARY / CONTRIBUTING / POSSIBLE / RULED_OUT             |
| `evidence`       | Narrative justification (auditable)                       |
| `recommendation` | Concrete mitigation step                                  |

The highest-scoring candidate is always tagged PRIMARY. Scores ≥ 0.4 are
CONTRIBUTING, ≥ 0.15 POSSIBLE, below that RULED_OUT.

### 7.4 Combining RCA with the compatibility assessor

If multiple chemicals are declared, attach a configured
`ChemicalCompatibilityAssessor` so the analyser can elevate
chemical-incompatibility candidates:

```java
rca.setCompatibilityAssessor(assessor);
```

If declared but not attached, a data gap is reported:
`"Multiple chemicals declared but no ChemicalCompatibilityAssessor configured"`.

---

## 8. Standards and References

| Topic                       | Reference                                |
| --------------------------- | ---------------------------------------- |
| Scale prediction & SI       | NACE TM 0374, SPE 130901, SPE 169787    |
| Corrosion inhibition        | NORSOK M-506, NACE SP0775, NACE MR0175  |
| Sour service                | NACE MR0175 / ISO 15156                  |
| Acid stimulation            | API RP 87, NACE TM 0169                  |
| H₂S scavenger               | GPSA Engineering Data Book, ISO 13443    |
| Material compatibility      | ASTM G31, DNV-RP-O501                    |

---

## 9. Related Documentation

- [Flow assurance index](../flowassurance/index.md)
- [Scale prediction calculator](../flowassurance/scale_prediction.md) (if available)
- [PVT simulation](../pvtsimulation/index.md)

---

## 10. Worked Example

See the consolidated notebook
[`chemical_compatibility_and_rca.ipynb`](../../examples/notebooks/chemical_compatibility_and_rca.ipynb)
for an end-to-end walkthrough covering compatibility screening, scale-control
assessment, acid treatment sizing, scavenger sizing, and root-cause analysis on
a representative incident.
