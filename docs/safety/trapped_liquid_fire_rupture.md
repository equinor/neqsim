---
title: Trapped Liquid Fire Rupture Screening
description: Generic workflow for screening blocked-in liquid-filled pipe segments exposed to fire using NeqSim trapped inventory, API 521 fire heat input, ASME piping and flange checks, temperature-dependent material strength, PFP demand, and source-term handoff.
---

Trapped liquid fire rupture screening evaluates blocked-in pipework or equipment volumes that can be heated by an external fire while thermal relief, depressurization, or other safeguards are unavailable or uncertain.

The NeqSim workflow is generic and industry-standard based. It does not encode operator-specific acceptance criteria. Project teams should configure the acceptance thresholds, material data, flange data, and required endurance for the jurisdiction and design basis under assessment.

## Workflow

1. Define the isolated inventory with `TrappedInventoryCalculator` using P&ID, line-list, datasheet, or other traceable evidence.
2. Select a fire exposure with `FireExposureScenario`, typically API 521 pool-fire heat input or a documented incident heat flux.
3. Select material strength using `MaterialStrengthCurve`, preferably from verified material records. The built-in API 5L lookup is a screening default.
4. Run `TrappedLiquidFireRuptureStudy` with pipe geometry, wall thickness, optional flange class, optional relief set pressure, and time controls.
5. Review event times for relief demand, vapor-pocket indication, pipe rupture, and flange failure.
6. Convert the result to a passive fire protection demand or a release source term when consequence analysis is needed.

## Java Example

```java
SystemInterface oil = new SystemSrkEos(298.15, 10.0);
oil.addComponent("n-heptane", 100.0);
oil.setMixingRule("classic");

InventoryResult inventory = new TrappedInventoryCalculator()
    .setFluid(oil)
    .setOperatingConditions(10.0, "bara", 25.0, "C")
    .addPipeSegment("TL-001", 0.10, 10.0, 1.0, null)
    .calculate();

TrappedLiquidFireRuptureResult result = TrappedLiquidFireRuptureStudy.builder()
    .segmentId("TL-001")
    .fluid(oil)
    .inventory(inventory)
    .pipeGeometry(0.10, "m", 3.0, "mm", 10.0, "m")
    .api5lMaterial("X52")
    .fireScenario(FireExposureScenario.api521PoolFire(3.4, 1.0))
    .flangeClass(900)
    .timeControls(1800.0, 2.0)
    .build()
    .run();

double failureTime = result.getMinimumFailureTimeSeconds();
SafetySystemDemand pfpDemand = result.toPassiveFireProtectionDemand("PFP-TL-001", 1800.0);
```

The calculation returns JSON-ready results through `result.toJson()`, including time histories, standards applied, warnings, and recommendations.

## Standards Basis

The default screening basis is aligned with common industry methods:

| Topic | Typical standard or method |
|----------|----------|
| Fire heat input and relief screening | API 521 / ISO 23251 |
| Pressure piping stress screening | ASME B31.3, ASME B31.4, ASME B31.8 |
| Flange pressure-temperature class screening | ASME B16.5 |
| Line pipe material strength | API 5L / ISO 3183 |
| Subsea pipeline design checks | DNV-ST-F101 / ISO 13623 |
| Consequence handoff | CCPS and TNO source-term / effect-model practice |

## Engineering Notes

This is a screening model. It is appropriate for identifying segments that need thermal relief, passive fire protection, drain/vent changes, operating procedures, or detailed analysis.

Final design decisions should verify:

- Current isolation boundary and trapped volume.
- Liquid composition, density, heat capacity, bulk modulus, and thermal expansion coefficient.
- Certified pipe, flange, bolt, gasket, and weld data at temperature.
- Fire scenario and exposed area from layout or consequence modelling.
- Relief device presence, capacity, and discharge path.
- Required PFP endurance and documented PFP condition.

