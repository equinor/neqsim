---
title: "CPA Water, Ice and Hydrate Boundaries"
description: "Calculate and explain liquid-water, ice and hydrate onset temperatures together with Statoil CPA, inhibitors and multiphase fluid-state diagnostics."
---

`ThermodynamicOperations.cpaWaterPhaseBoundaryTemperatures()` provides one
calculation for three water-related equilibrium boundaries at the supplied
absolute pressure. It requires a configured `SystemSrkCPAstatoil` fluid with
numeric mixing rule **10** and positive water content. The calculation uses
independent copies and preserves the supplied fluid.

## Agent-facing MCP operation

For a new JSON-defined fluid, use the existing `run_flow_assurance` capability
with `analysis: "waterPhaseBoundaries"`. Read its current input schema and run
`validateInput` before invoking the connected tool. The analysis defaults to
Statoil CPA and numeric mixing rule 10; an explicitly incompatible model is
rejected. Absolute `pressure_bara` is required.

This payload is executed by `FlowAssuranceRunnerTest.testCpaWaterPhaseBoundaries`:

```json
{
  "analysis": "waterPhaseBoundaries",
  "model": "CPA",
  "temperature_C": 20.0,
  "pressure_bara": 50.0,
  "components": {
    "methane": 0.89991,
    "ethane": 0.079992,
    "propane": 0.019998,
    "water": 0.0001
  }
}
```

The component map is the complete normalized wet-gas composition: it represents
a 90/8/2 dry methane/ethane/propane gas with 100 ppmv wet-basis water. For
new characterized oil inputs, use the schema's typed `tbpFractions` and
`plusFraction` definitions rather than inventing component names. Existing tuned
or characterized Java fluids should use the facade directly, as shown below,
so reconstructing a JSON fluid does not discard their fitted model basis.

The response has top-level `status` equal to `success`, `partial` or `error`.
For `error`, report the returned diagnostic and any retained `partialData`;
do not assume the normal `data` object exists. Successful and partial responses
contain `data.liquidWater`, `data.ice` and `data.hydrate` results, each with
`status`, `converged`, `temperature_C`, `residual`, `waterActivity`,
`phaseReference` and `diagnostic`. Failed or inapplicable numerical values are
JSON `null`, never a usable last-trial temperature. Inspect all three outcomes
even when the overall status is `partial`, and report the retained explanation
and failure reasons.

## Meaning of the results

| Result | Equilibrium reference | Interpretation |
| --- | --- | --- |
| `getLiquidWater()` | An incipient water-rich liquid, including dissolved components | A fixed-feed-gas dew point; below freezing its liquid reference is metastable |
| `getIce()` | Pure ice Ih against the equilibrium fluid | A freezing/frost boundary with its actual fluid reference identified |
| `getHydrate()` | A water/guest-gas clathrate against the equilibrium fluid | Includes the water activity of supplied aqueous inhibitors and the fluid-phase split |

The operations are separate onset calculations. Once water, ice or hydrate
actually forms, the remaining water and guest inventories change. The three
temperatures therefore do not describe successive points on one finite-inventory
cooling path. The highest applicable equilibrium boundary can identify the first
thermodynamically permitted phase during cooling, but does not predict
nucleation delay or a growth rate.

The residuals are dimensionless but have boundary-specific definitions:

| Boundary | Returned residual |
| --- | --- |
| Liquid water | Logarithm of gas-water fugacity divided by incipient-liquid-water fugacity |
| Ice | The freezing solver's logarithmic overall-composition/multiphase fugacity residual |
| Hydrate | One minus hydrate-water fugacity divided by fluid-water fugacity |

Compare each residual with its own convergence criterion; equal residual numbers
do not imply equal temperature uncertainty between the three models.

## Executable Java 8 setup

This synthetic gas has a dry composition of 90% methane, 8% ethane and
2% propane, with **100 ppmv water on a wet-gas basis**, at **50 bara**.

<!-- cpa-water-boundaries-example: complete -->
```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.WaterPhaseBoundaryResult;

SystemInterface gas = new SystemSrkCPAstatoil(293.15, 50.0);
double waterMoleFraction = 100.0e-6;
double dryMoleFraction = 1.0 - waterMoleFraction;
gas.addComponent("methane", 0.90 * dryMoleFraction);
gas.addComponent("ethane", 0.08 * dryMoleFraction);
gas.addComponent("propane", 0.02 * dryMoleFraction);
gas.addComponent("water", waterMoleFraction);
gas.setMixingRule(10);

WaterPhaseBoundaryResult boundaries =
    new ThermodynamicOperations(gas).cpaWaterPhaseBoundaryTemperatures();

double liquidWaterC = boundaries.getLiquidWater().getTemperatureC();
double iceC = boundaries.getIce().getTemperatureC();
double hydrateC = boundaries.getHydrate().getTemperatureC();
```

Before using a temperature, inspect the corresponding result's `getStatus()` and
`isConverged()`, followed by `getResidual()`, `getPhaseReference()`,
`getWaterActivity()` and `getDiagnostic()`. A failed or inapplicable result has
no usable onset temperature. The top-level result retains `getPressureBara()`,
`getModel()`, `getMixingRule()`, `getWaterMoleFraction()` and `getExplanation()`.

The configured-fluid and caller-preservation contracts are exercised by
`CpaWaterPhaseBoundaryCalculatorTest`. Solver regressions separately exercise
warm/cold starts, water content, pressure, inhibitors, phase inventory and final
equilibrium residuals. These checks establish the stated numerical contracts;
they do not establish experimental accuracy for every composition.

## Inhibitors, oil and multiple phases

Retain oil characterization, tuned parameters, the actual water inventory and
all inhibitor components in the CPA fluid. Use `setMultiPhaseCheck(true)` when
multiple liquid phases can occur. Specify whether an inhibitor concentration is
an aqueous-phase mass fraction, an overall mole fraction or another basis.
Replacing a wet oil/gas/water inventory with an arbitrary gas ppmv changes the
engineering problem.

The hydrate calculation uses the water activity of the actual fluid, including
supplied MEG or methanol, and updates its fluid-phase split during the
temperature solve. A free-water hydrate curve is a different input basis from
trace-water gas. The combined calculation does not inject water, remove oil or
remove inhibitors to produce a result.

The liquid-water dew-point operation is a fixed-feed-gas operation with a
water-rich incipient liquid. If the supplied equilibrium fluid already contains
oil or aqueous liquid, this boundary is `NOT_APPLICABLE`; its diagnostic explains
why. The independently evaluated ice and hydrate results remain available.
Read `getPhaseReference()` to distinguish gas-only from coexisting-fluid
references. Do not describe an equilibrium-fluid freezing result as a
fixed-gas-composition frost point.

## Ice pressure model and qualification

The ice calculation retains the existing fusion-enthalpy and constant
heat-capacity-difference approximation relative to the same CPA pure-liquid
water reference. Its water pressure correction uses the ice-Ih melting-curve
slope at the triple point from
[IAPWS R14-08(2011), equation 1](https://iapws.org/public/documents/MdUFK/MeltSub2011.pdf).
The triple-point values are 273.16 K and 611.657 Pa; pressure in bara must be
converted to pascals in the pressure-volume contribution.

The solid-minus-liquid volume difference is obtained from the Clapeyron relation
at that reference state. It restores the expected decrease in pure-water
melting temperature with increasing pressure without relying on the bundled
empirical water density records. This remains a constant-volume fusion/Cp
approximation, not a complete IAPWS ice equation of state or a general
high-pressure ice-polymorph model. The pressure correction is checked against
the IAPWS ice-Ih melting curve over **1–100 bara**; that comparison does not
extend the model to other ice polymorphs or establish the experimental accuracy
of a natural-gas frost point. Retain the tested range and independent reference
evidence with engineering results.

## Agent use

For **water dew point with CPA** or **vannduggpunkt med CPA**, the
`neqsim-cpa-water-boundaries` skill asks the gas-quality and thermo-fluid agents
to report all three boundaries by default, their convergence status and their
physical differences. Existing characterized fluids stay intact; unsupported
input models require explicit configuration rather than an unsafe conversion
from component names.

GERG-water/ISO 18453 is a separate model comparison. Below its coefficient
switch it uses an ice reference, and above that switch it uses liquid water.
Label that reference explicitly when comparing a GERG result with a CPA
liquid-water or ice result.

## Related guides

- [Thermodynamic operations overview](README.md)
- [Water and hydrocarbon dew-point methods](../standards/dew_point_standards.md)
- [Hydrate flash operations](hydrate_flash_operations.md)
- [Hydrate models](../thermo/hydrate_models.md)
