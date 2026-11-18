---
title: "Advanced Risk Framework Tutorial"
description: "Executable NeqSim tutorial for condition-based reliability, safety instrumented functions, bow-tie screening, and responsible engineering interpretation."
---

This tutorial introduces the risk-framework APIs that are present in the current NeqSim source tree.
It replaces older examples that used class names and method signatures that were never part of the
published API.

The calculation is suitable for software integration and screening. It does not establish a SIL
claim, approve a protection layer, predict an equipment failure, or replace a facility risk
assessment.

## Supported capability map

| Capability | Current Java entry point | Primary result |
|---|---|---|
| Dynamic production-risk simulation | `neqsim.process.safety.risk.dynamic.DynamicRiskSimulator` | Production and transient-loss distributions |
| SIS and LOPA screening | `neqsim.process.safety.risk.sis.SISIntegratedRiskModel` and `SafetyInstrumentedFunction` | Residual-frequency and SIL-screening results |
| Physics-based monitoring | `neqsim.process.safety.risk.realtime.PhysicsBasedRiskMonitor` | Equipment and system risk indicators |
| Bow-tie screening | `neqsim.process.safety.risk.bowtie.BowTieModel` | Threat, barrier, and consequence structure |
| Portfolio screening | `neqsim.process.safety.risk.portfolio.PortfolioRiskAnalyzer` | Aggregated production-risk statistics |
| Condition-based reliability | `neqsim.process.safety.risk.condition.ConditionBasedReliability` | Health-adjusted failure rate and failure probability |
| ML adapter | `neqsim.process.safety.risk.ml.RiskMLInterface` | Model registration, prediction, and feedback records |

Use these fully qualified names when accessing the classes through the Python `jneqsim` gateway.
The gateway exposes Java APIs; it does not translate method names into a separate Python contract.

## Complete Java 8 example

The example combines three bounded calculations:

1. a health-adjusted failure probability for a compressor;
2. a Safety Instrumented Function (SIF) definition; and
3. a bow-tie frequency screen with one prevention barrier.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.safety.risk.bowtie.BowTieModel;
import neqsim.process.safety.risk.condition.ConditionBasedReliability;
import neqsim.process.safety.risk.condition.ConditionBasedReliability.ConditionIndicator;
import neqsim.process.safety.risk.sis.SafetyInstrumentedFunction;

public final class AdvancedRiskFrameworkExample {
  private static final Logger logger =
      LogManager.getLogger(AdvancedRiskFrameworkExample.class);

  private AdvancedRiskFrameworkExample() {}

  public static void main(String[] args) {
    ConditionBasedReliability reliability =
        new ConditionBasedReliability("C-200", "Export compressor", 1.0e-4);
    reliability.setDegradationModel(
        ConditionBasedReliability.DegradationModel.WEIBULL);

    ConditionIndicator vibration = reliability.addVibrationIndicator(
        "VIB-001", "Bearing vibration", 1.0, 5.0, 10.0);
    vibration.setWeight(0.6);
    vibration.updateValue(6.0);

    ConditionIndicator temperature = reliability.addTemperatureIndicator(
        "TEMP-001", "Bearing temperature", 60.0, 95.0, 110.0);
    temperature.setWeight(0.4);
    temperature.updateValue(85.0);

    reliability.recalculateHealth();
    double healthIndex = reliability.getHealthIndex();
    double adjustedFailureRate = reliability.getAdjustedFailureRate();
    double annualFailureProbability =
        reliability.getProbabilityOfFailure(8760.0);

    SafetyInstrumentedFunction sif = SafetyInstrumentedFunction.builder()
        .id("SIF-001")
        .name("HP separator PAHH")
        .description("Isolate feed on confirmed high-high pressure")
        .sil(2)
        .pfd(0.005)
        .architecture("1oo2")
        .testIntervalHours(8760.0)
        .addProtectedEquipment("HP separator")
        .initiatingEvent("HP separator overpressure")
        .safeState("Feed isolated")
        .build();
    double riskReductionFactor = sif.getRiskReductionFactor();

    BowTieModel bowTie =
        new BowTieModel("HZ-001", "HP separator loss of containment");
    BowTieModel.Threat overpressure =
        new BowTieModel.Threat("T-001", "Overpressure", 1.0e-2);
    bowTie.addThreat(overpressure);

    BowTieModel.Barrier trip =
        new BowTieModel.Barrier("B-001", "Independent high-pressure trip", 0.005);
    trip.setBarrierType(BowTieModel.BarrierType.PREVENTION);
    bowTie.addBarrier(trip);
    bowTie.linkBarrierToThreat("T-001", "B-001");

    BowTieModel.Consequence release =
        new BowTieModel.Consequence("C-001", "Loss of containment", 5);
    bowTie.addConsequence(release);
    bowTie.calculateRisk();

    assert Double.isFinite(healthIndex)
        && healthIndex > 0.0 && healthIndex < 1.0;
    assert Double.isFinite(adjustedFailureRate)
        && adjustedFailureRate > reliability.getBaseFailureRate();
    assert Double.isFinite(annualFailureProbability)
        && annualFailureProbability > 0.0 && annualFailureProbability < 1.0;
    assert Math.abs(riskReductionFactor - 200.0) < 1.0e-12;
    assert Math.abs(bowTie.getUnmitigatedFrequency() - 1.0e-2) < 1.0e-12;
    assert Math.abs(bowTie.getMitigatedFrequency() - 5.0e-5) < 1.0e-12;
    assert bowTie.getMaxSeverity() == 5;

    logger.info("Health index: {}", healthIndex);
    logger.info("Adjusted failure rate: {} 1/h", adjustedFailureRate);
    logger.info("One-year failure probability: {}", annualFailureProbability);
    logger.info("SIF risk-reduction factor: {}", riskReductionFactor);
    logger.info("Bow-tie mitigated frequency: {} 1/year",
        bowTie.getMitigatedFrequency());
  }
}
```

Run with assertions enabled (`-ea`). The repository documentation contract extracts this exact
fence, compiles it with the Java 8 language target, and executes it.

## Interpret the results

### Condition-based reliability

The base failure rate in the example is an input assumption in failures per hour. The indicator
thresholds and weights are also study inputs. `recalculateHealth()` combines the indicator health
contributions, applies the selected degradation model, and updates the adjusted failure rate.
`getProbabilityOfFailure(8760.0)` then applies the class's exponential time-to-failure model over
one year.

A positive result is not a calibrated failure prediction. Thresholds, weights, the base failure
rate, and the degradation model require equipment-specific evidence and change control.

### SIF screening

The SIF example stores a target SIL, average probability of failure on demand, architecture,
proof-test interval, protected equipment, initiating event, and safe state. Its risk-reduction
factor is the reciprocal of the supplied PFD average.

Do not infer achieved SIL from the builder inputs. A real verification needs failure-rate data,
diagnostic coverage, common-cause assumptions, proof-test effectiveness, repair time, bypass
management, systematic-capability evidence, and the applicable IEC 61508/61511 lifecycle records.

### Bow-tie screening

`BowTieModel.calculateRisk()` sums active threat frequencies and multiplies the result by the PFD
of each functional prevention barrier. In this example, `1.0e-2 / year` multiplied by `0.005`
gives `5.0e-5 / year`.

The implementation is a screening model. Confirm barrier independence, applicability, failure
modes, dependency, common cause, and consequence frequencies before using the result in a decision.

## Building a defensible study

For every model run, record:

1. the source and units of failure rates, repair times, PFDs, and condition thresholds;
2. the equipment boundary and operating state represented by each input;
3. the model version, random seed where relevant, iteration count, and time horizon;
4. convergence and numerical diagnostics;
5. sensitivity cases for uncertain or correlated inputs; and
6. the reviewer, approval basis, and limitations attached to the result.

Keep measured condition data separate from assumed thresholds. Preserve raw evidence and generated
results so a later reviewer can reproduce the calculation.

## Engineering and safety boundary

NeqSim can organize and calculate risk-screening quantities, but it cannot establish independence
of protection layers, validate an instrumented function, certify a safety lifecycle, or determine
acceptable risk. Those decisions require competent engineering review, controlled source data,
applicable company practices, and governing standards and regulations.

The numerical values in this tutorial are illustrative software checks. They are not generic
targets for a real compressor, separator, SIF, or facility.

## Related documentation

- [Risk framework overview](../risk/README.md)
- [Dynamic risk simulation](../risk/dynamic-simulation.md)
- [Condition-based reliability](../risk/condition-based.md)
- [Process safety documentation](../process/safety/)
- [Troubleshooting guide](../troubleshooting/index.md)
