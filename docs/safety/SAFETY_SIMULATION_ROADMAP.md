---
title: NeqSim Safety Simulation Roadmap
description: Current NeqSim process-safety capabilities, executable starting points, validation boundaries, and prioritized gaps.
keywords: "process safety, release source term, risk, safety envelope, depressurization, relief, screening"
---

Use this page to identify the maintained NeqSim safety capability that matches an engineering
question. Start with the [safety documentation hub](README) for task-oriented guides and the
[process-safety API overview](../process/safety/README) for lifecycle, unit, and validation
boundaries.

NeqSim results are engineering evidence, not design approval. A competent engineer must still
select credible scenarios, confirm inventories and boundary conditions, apply the project editions
of governing standards, validate models against suitable references, and document uncertainty and
acceptance criteria.

## Current capability map

| Engineering question | Maintained capability | Status and boundary |
| --- | --- | --- |
| Which flowsheet disturbances should be screened? | `ProcessSafetyScenario` and `ProcessSafetyAnalyzer` | Available for blocked-outlet, utility-loss, controller-set-point, and custom-manipulator screening. Generated scenarios require HAZOP and relief-study review. |
| What is the time-dependent release source term? | `LeakModel` and `SourceTermResult` | Available for bounded leak and vessel-inventory screening with mass rate, temperature, pressure, phase fraction, velocity, and momentum histories. Validate the discharge model for the release regime. |
| Where are hydrate, wax, CO2-freezing, MDMT, and phase limits? | `SafetyEnvelopeCalculator` and `SafetyEnvelope` | Available as thermodynamic screening envelopes. Material selection and operating limits require project-specific review. |
| How can event frequencies and consequences be combined? | `RiskModel`, `RiskEvent`, and `RiskResult` | Available for event-tree, Monte Carlo, sensitivity, and F-N screening. Frequencies and conditional probabilities remain project inputs. |
| How does a vessel depressurize? | `VesselDepressurization` and depressurization helpers | Available for transient pressure, temperature, wall-temperature, fire-case, and flow-assurance screening. See the [API 521 depressurization workflow](depressurization_per_API_521). |
| How should a relief device be screened? | `SafetyValve`, `SafetyReliefValve`, and `ReliefValveSizing` | Gas-service screening is available. See [relief-valve sizing screening](relief_valve_sizing_api); certify sizing and installation outside this example. |
| How are release cases handed to consequence work? | `ReleaseDispersionScenarioGenerator` and source-term exports | Available as a screening and handoff layer. See [release and dispersion scenarios](../process/safety/release-dispersion-scenarios). CFD geometry, congestion, ventilation, and acceptance remain external engineering inputs. |

## Executable release-source-term starting point

This complete Java 8 program creates a methane inventory at 50 bar(a), screens a 10 mm horizontal
release to 1.01325 bar(a), and checks that the result is physically usable. Diameter is supplied in
millimetres, volume in m3, duration and time step in seconds, and the returned mass rates are in
kg/s.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.safety.release.LeakModel;
import neqsim.process.safety.release.ReleaseOrientation;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class SafetySourceTermRoadmapExample {
  private static final Logger logger =
      LogManager.getLogger(SafetySourceTermRoadmapExample.class);

  private SafetySourceTermRoadmapExample() {}

  public static void main(String[] args) {
    SystemInterface methane = new SystemSrkEos(293.15, 50.0);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule("classic");

    LeakModel release =
        LeakModel.builder()
            .fluid(methane)
            .holeDiameter(10.0, "mm")
            .vesselVolume(1.0)
            .backPressure(1.01325, "bar")
            .orientation(ReleaseOrientation.HORIZONTAL)
            .scenarioName("50 bar(a) methane release")
            .build();

    SourceTermResult result = release.calculateSourceTerm(4.0, 1.0);
    double initialMassRate = result.getMassFlowRate()[0];

    assert result.getNumberOfPoints() == 5;
    assert initialMassRate > 0.0;
    assert result.getPeakMassFlowRate() >= initialMassRate;
    assert result.getTotalMassReleased() > 0.0;
    assert result.getPressure()[0] > 1.01325e5;
    assert result.getTemperature()[0] > 0.0;

    logger.info(
        "{}: initial rate={} kg/s, released={} kg",
        result.getScenarioName(),
        initialMassRate,
        result.getTotalMassReleased());
  }
}
```

The example deliberately stops at source-term generation. Do not treat its default discharge
coefficient, homogeneous phase treatment, inventory depletion approximation, or short duration as
a project release basis. The [release and dispersion guide](../process/safety/release-dispersion-scenarios)
describes the required downstream handoff and validation boundary.

## Prioritized remaining work

1. Couple relief-source calculations to time-dependent disposal-network back pressure without
   weakening the existing static screening route.
2. Add traceable reaction-force and discharge-piping calculations with explicit standard-edition
   assumptions and benchmark cases.
3. Extend and validate two-phase relief methods before presenting them as design calculations.
4. Improve shared inventory and phase-state handoff between scenario execution, depressurization,
   release, and consequence models.
5. Maintain benchmark datasets and uncertainty reporting for each safety calculation family.

These are capability gaps, not promises of calendar delivery. New methods need focused numerical
tests, source-backed documentation, and comparison with accepted engineering tools or published
benchmarks before their status changes here.

## Related guides

- [Safety documentation hub](README)
- [Process-safety API overview](../process/safety/README)
- [Release and dispersion scenarios](../process/safety/release-dispersion-scenarios)
- [API 521 depressurization workflow](depressurization_per_API_521)
- [Relief-valve sizing screening](relief_valve_sizing_api)
- [Risk framework](../risk/index)

