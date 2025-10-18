# Heat Exchanger Mechanical Design

## Overview

The `HeatExchangerMechanicalDesign` class provides sizing estimates for shell-and-tube, plate-and-frame, air cooler, and double-pipe exchangers. It can be attached to a full two-stream `HeatExchanger` or to single-stream `Heater` and `Cooler` units that supply an auxiliary utility specification. The mechanical design routine evaluates the candidate exchanger types, computes the required UA and approach temperatures, and selects a preferred configuration based on area, weight, or pressure-drop criteria.

## Prerequisites

- Run the process simulation so that the process-side temperatures, pressures, and duty are available.
- Ensure the equipment has been initialized (`initMechanicalDesign`) before requesting the sizing results.

## Two-Stream Heat Exchangers

For a `HeatExchanger`, the design routine uses the duty, stream inlet/outlet temperatures, and (if provided) UA or thermal effectiveness to compute the log-mean temperature difference (LMTD) and overall heat-transfer requirements. The selected exchanger geometry is exposed through `HeatExchangerSizingResult`.

```java
HeatExchanger exchanger = new HeatExchanger("hx", hotStream, coldStream);
exchanger.run();

exchanger.initMechanicalDesign();
HeatExchangerMechanicalDesign design = exchanger.getMechanicalDesign();
design.calcDesign();

HeatExchangerSizingResult result = design.getSelectedSizingResult();
System.out.println(result.getType() + " area = " + result.getRequiredArea());
```

## Single-Stream Heaters and Coolers

A `Heater` or `Cooler` needs a `UtilityStreamSpecification` so the mechanical design can derive the utility-side temperatures or heat capacity rate.

```java
Heater heater = new Heater("heater", feed);
heater.setOutTemperature(80.0, "C");

UtilityStreamSpecification utility = new UtilityStreamSpecification();
utility.setSupplyTemperature(180.0, "C");
utility.setReturnTemperature(160.0, "C");
utility.setOverallHeatTransferCoefficient(650.0);
heater.setUtilitySpecification(utility);

heater.run();
heater.initMechanicalDesign();
HeatExchangerMechanicalDesign design = heater.getMechanicalDesign();
design.calcDesign();
```

The specification supports setting:

- Supply or return temperature (in K or degC).
- Approach temperature (if only a minimum pinch is known).
- Heat-capacity rate (`m*Cp`) to back-calculate the return temperature from duty.
- Overall heat-transfer coefficient to influence area estimates.

You can also configure the utility through convenience setters such as `setUtilitySupplyTemperature`, `setUtilityReturnTemperature`, `setUtilityHeatCapacityRate`, and `setUtilityApproachTemperature`.

## Inspecting Sizing Alternatives

All evaluated designs are available through `getSizingResults()`. Use the selection helpers to control the preferred configuration:

```java
design.setCandidateTypes(
    HeatExchangerType.SHELL_AND_TUBE,
    HeatExchangerType.PLATE_AND_FRAME);

design.setSelectionCriterion(SelectionCriterion.MIN_WEIGHT);
design.calcDesign();
```

- `setManualSelection` forces a specific exchanger type when benchmarking alternatives.
- `setSelectionCriterion` controls the automatic choice (area, weight, or pressure drop).
- `getSizingSummary()` returns a short formatted overview suitable for logs or reports.

## Related Topics

- `UtilityStreamSpecification` (JavaDoc) for the full list of utility parameters.
- Unit tests in `HeaterCoolerMechanicalDesignTest` illustrate heater and cooler sizing workflows.
- The README section "Heat exchanger mechanical design" (see below) for a short summary.
