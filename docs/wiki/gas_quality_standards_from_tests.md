---
title: "Gas quality standards validated by ISO 6976 tests"
description: "The ISO 6976 calorific value and Wobbe index calculations are verified in `Standard_ISO6976Test`. This page summarizes the tested configurations and equations so you can align custom gas-quality runs ..."
---

# Gas quality standards validated by ISO 6976 tests

The ISO 6976 calorific value and Wobbe index calculations are verified in `Standard_ISO6976Test`. This page summarizes the tested configurations and equations so you can align custom gas-quality runs with the regression suite.

## Base ISO 6976 calculation

`testCalculate` initializes a dry natural gas at 20 °C and 1 bar with a classic mixing rule and executes `Standard_ISO6976.calculate()` using volume-based reference conditions (0 °C volume base, 15.55 °C energy base).【F:src/test/java/neqsim/standards/gasquality/Standard_ISO6976Test.java†L23-L47】 The test confirms the gross calorific value (GCV) of 39,614.57 kJ/Sm³ and Wobbe index (WI) of 44.61 MJ/Sm³.

The Wobbe index relation checked in the test is

$
WI = \frac{GCV}{\sqrt{\rho_r}}\ ,
$

where $\rho_r$ is the relative density. Matching the test values indicates both combustion energy and density normalization are consistent with ISO 6976.

## Handling reference condition overrides

`testCalculateWithWrongReferenceState` shows that if non-standard reference temperatures are provided, the standard falls back to defined bases (15 °C for energy, 0 °C for volume) while still computing GCV and WI.【F:src/test/java/neqsim/standards/gasquality/Standard_ISO6976Test.java†L49-L73】 Use this behavior to guard against user input errors without failing calculations.

## Including pseudo-components

`testCalculateWithPSeudo` adds a TBP pseudo-fraction to the gas and re-runs the calculation to verify heavier fractions contribute to higher heating value (GCV ≈ 42,378 kJ/Sm³).【F:src/test/java/neqsim/standards/gasquality/Standard_ISO6976Test.java†L75-L96】 The setup demonstrates that ISO 6976 evaluation tolerates lumped heavy ends when a classic mixing rule and full flash initialization are applied.

## Full-property audit

`testCalculate2` and `testCalculate3` sweep alternative temperatures and reference pairs to assert a complete property set: compression factor, superior/inferior calorific values, Wobbe indices, relative density, and molar mass.【F:src/test/java/neqsim/standards/gasquality/Standard_ISO6976Test.java†L98-L200】 The tests also run a process `Stream` to ensure downstream WI reporting matches the standard calculation.

When configuring your own gas-quality evaluations:

- Always initialize the thermodynamic system (`init(0)`) before calling the standard.
- Select reference temperatures explicitly; unexpected inputs will be corrected but should be avoided for traceability.
- Validate both GCV and Wobbe index against expected tolerances to confirm combustion properties are consistent.
