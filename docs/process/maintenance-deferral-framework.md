---
title: "Maintenance Deferral and Run-Until-Repair Framework"
description: "Equipment degradation modeling, health assessment, and maintenance deferral decision support for compressors and heat exchangers. Covers fouling, washing recovery, temporary operating envelopes, and plant-wide coordination."
---

# Maintenance Deferral and Run-Until-Repair Framework

The `neqsim.process.maintenance` package provides a decision-support framework
for evaluating whether scheduled maintenance on process equipment can be safely
deferred. It connects equipment degradation physics (fouling, efficiency loss)
to a rule-based assessment engine that produces quantified risk/benefit
recommendations.

## Architecture

```
┌─────────────────────────┐
│   Compressor / HX       │  Equipment with degradationFactor, foulingFactor
│   (degradation wired    │  applied inside run() calculation paths
│    into run())          │
└────────┬────────────────┘
         │ reads factors
         ▼
┌─────────────────────────┐
│ EquipmentHealthAssessment│  Weighted health index (0–1)
│ - performance (30%)     │  Severity: NORMAL → WATCH → ALERT → CRITICAL
│ - age/overhaul (20%)    │  Estimated remaining useful life (RUL)
│ - condition ind. (50%)  │
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ MaintenanceDeferral-    │  7-rule decision engine
│ Assessment              │  → DeferralDecision (DEFER / PROCEED / EMERGENCY)
│                         │  → TemporaryOperatingEnvelope (constraints)
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│ PlantDeferralCoordinator │  Plant-wide coordination
│ - cumulative risk limits │  Revokes riskiest deferrals when plant limits exceeded
│ - auto-assess compressors│
└─────────────────────────┘
```

## Equipment Degradation (Phase 1)

### Compressor

The `Compressor` class has three fields that model performance degradation:

| Field | Default | Description |
|-------|---------|-------------|
| `degradationFactor` | 1.0 | Multiplier on efficiency/head (1.0 = new, 0.8 = 20% degraded) |
| `foulingFactor` | 0.0 | Fraction of head lost to fouling (0.0 = clean, 0.3 = 30% fouled) |
| `operatingHours` | 0.0 | Cumulative operating hours since installation |

These factors are applied inside `run()` across all calculation paths:

- **Chart-based paths** (with/without solveSpeed): polytropic fluid head is
  multiplied by `degradationFactor * (1.0 - foulingFactor)`
- **Non-chart polytropic**: polytropic efficiency is temporarily reduced by
  `degradationFactor` during the calculation
- **Isentropic path**: an effective isentropic efficiency is computed as
  `isentropicEfficiency * degradationFactor`

```java
Compressor comp = (Compressor) process.getUnit("Export Compressor");
comp.setDegradationFactor(0.92);   // 8% efficiency loss from wear
comp.setFoulingFactor(0.10);       // 10% head loss from fouling
comp.setOperatingHours(18000.0);
process.run();  // run() now uses degraded performance

// Recover fouling with online wash (~40% recovery)
comp.applyWashing(CompressorWashing.WashingMethod.ONLINE_WET);

// Increment operating hours with linear fouling model
comp.incrementOperatingHours(720.0, 0.00005);  // 720 hrs, 5e-5 per hour fouling rate
```

### Heat Exchanger

The `HeatExchanger` class accepts a `FoulingModel` that reduces the effective UA:

$$
UA_{\text{effective}} = \frac{1}{\frac{1}{UA_{\text{clean}}} + R_f}
$$

where $R_f$ is the fouling resistance from the model.

```java
HeatExchanger hx = (HeatExchanger) process.getUnit("Gas Cooler");
FoulingModel fouling = new FoulingModel();
fouling.setFoulingResistance(0.0003);  // m²·K/W
hx.setFoulingModel(fouling);
process.run();  // NTU calculation uses reduced UA

double effectiveUA = hx.getEffectiveUA();
```

## Health Assessment (Phase 2)

`EquipmentHealthAssessment` produces a unified health index from three
weighted components:

| Component | Weight | Source |
|-----------|--------|--------|
| Performance | 30% | `degradationFactor × (1 - foulingFactor)` |
| Age/overhaul | 20% | `1 - hoursSinceOverhaul / MTBO` |
| Condition indicators | 50% | Average of indicator health fractions |

The health index maps to a severity classification:

| Health Index | Severity |
|-------------|----------|
| ≥ 0.75 | NORMAL |
| 0.50 – 0.75 | WATCH |
| 0.25 – 0.50 | ALERT |
| < 0.25 | CRITICAL |

```java
EquipmentHealthAssessment health =
    new EquipmentHealthAssessment("Compressor-1", "compressor");
health.setDegradationFactor(0.92);
health.setFoulingFactor(0.08);
health.setOperatingHours(18000.0);
health.setHoursSinceOverhaul(15000.0);
health.setMeanTimeBetweenOverhaul(25000.0);

// Add condition monitoring data
health.addConditionIndicator(
    new EquipmentHealthAssessment.ConditionIndicator(
        "vibration", "mm/s", 4.5, 2.0, 7.0, 10.0));
health.addConditionIndicator(
    new EquipmentHealthAssessment.ConditionIndicator(
        "bearing_temp", "C", 75.0, 60.0, 90.0, 110.0));

health.calculate();

double index = health.getHealthIndex();          // 0.0–1.0
HealthSeverity sev = health.getSeverity();       // NORMAL/WATCH/ALERT/CRITICAL
double rul = health.getEstimatedRemainingLife();  // hours
```

## Deferral Decision Engine (Phase 3)

`MaintenanceDeferralAssessment` applies a 7-rule decision engine:

1. **Critical health veto** — CRITICAL severity → EMERGENCY (never defer)
2. **RUL check** — RUL < requested deferral → PROCEED or REDUCED_SCOPE
3. **Production impact** — estimate production loss at degraded conditions
4. **Safety veto** — safety risk > acceptable limit → PROCEED
5. **Production loss veto** — loss > acceptable limit → PROCEED
6. **Economic assessment** — benefit vs risk calculation
7. **Positive outcome** — all checks pass → DEFER with operating envelope

```java
MaintenanceDeferralAssessment assessment =
    new MaintenanceDeferralAssessment("Compressor-1");
assessment.setHealthAssessment(health);
assessment.setProcessSystem(process);
assessment.setRequestedDeferralHours(720.0);      // 30 days
assessment.setProductionValuePerHour(50000.0);     // USD/hr
assessment.setMaintenanceCostUSD(200000.0);
assessment.setUnplannedShutdownCostUSD(5000000.0);

DeferralDecision decision = assessment.assess();

decision.getRecommendation();           // DEFER / PROCEED_AS_PLANNED / REDUCED_SCOPE / EMERGENCY
decision.getRiskLevel();                // LOW / MEDIUM / HIGH / VERY_HIGH
decision.isDeferralViable();            // true if DEFER or REDUCED_SCOPE
decision.getProductionLossRiskPercent();
decision.getSafetyRiskScore();          // 0–10
decision.getEconomicBenefitUSD();
decision.getEconomicRiskUSD();
decision.getRationale();                // Human-readable explanation
decision.getOperatingEnvelope();        // TemporaryOperatingEnvelope
```

### Temporary Operating Envelope

When deferral is recommended, a `TemporaryOperatingEnvelope` specifies the
safe operating constraints:

```java
TemporaryOperatingEnvelope envelope = decision.getOperatingEnvelope();
envelope.getValidityPeriodHours();
envelope.getMonitoringRequirements();
envelope.getEscalationCriteria();

// Check if current conditions are within envelope
boolean safe = envelope.isWithinEnvelope(
    new String[]{"capacity_fraction", "vibration_mm_s"},
    new double[]{0.85, 5.2});
```

## Plant-Wide Coordination (Phase 4)

`PlantDeferralCoordinator` assesses multiple equipment items together and
enforces plant-level constraints:

```java
PlantDeferralCoordinator coordinator = new PlantDeferralCoordinator(process);
coordinator.setDefaultDeferralHours(720.0);
coordinator.setProductionValuePerHour(50000.0);
coordinator.setMaxCumulativeRisk(5.0);

// Option 1: Auto-discover compressors and create health assessments
coordinator.autoAssessCompressors();

// Option 2: Register health assessments manually
coordinator.addHealthAssessment("Compressor-1", health1);
coordinator.addHealthAssessment("Compressor-2", health2);

coordinator.assess();

coordinator.getPlantHealthIndex();      // Average health across all equipment
coordinator.getCumulativeSafetyRisk();  // Total risk score
coordinator.getDeferralCount();         // Number of viable deferrals

for (PlantDeferralCoordinator.EquipmentDeferralSummary s : coordinator.getSummaries()) {
    System.out.println(s.getEquipmentName() + ": " + s.getDecision().getRecommendation());
}
```

The coordinator automatically revokes the riskiest deferrals when:
- Cumulative safety risk exceeds the plant limit
- Too many items are deferred simultaneously (default max: 3)

## JSON Output

All classes support `toJson()` for structured reporting:

```java
String healthJson = health.toJson();
String decisionJson = decision.toJson();
String plantJson = coordinator.toJson();
```

## Classes Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `EquipmentHealthAssessment` | `process.maintenance` | Health index, severity, RUL |
| `MaintenanceDeferralAssessment` | `process.maintenance` | 7-rule decision engine |
| `DeferralDecision` | `process.maintenance` | Decision output (recommendation, risk, economics) |
| `TemporaryOperatingEnvelope` | `process.maintenance` | Safe operating constraints during deferral |
| `PlantDeferralCoordinator` | `process.maintenance` | Plant-wide multi-equipment coordination |

## Related

- [Compressors](equipment/compressors.md) — Compressor models and performance
- [Compressor Design](CompressorMechanicalDesign.md) — Mechanical design
- [Heat Exchangers](equipment/heat-exchangers.md) — HX models and sizing
