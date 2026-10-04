---
title: Coupled sulfur/nitrogen fired-heater combustion balance
description: Close caller-owned CHSON fuel, dry-air, and complete-combustion flue-gas receipts.
---

# Coupled sulfur/nitrogen fired-heater combustion balance

`RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance` converts the
fuel mass from a qualified fired-heater utility receipt to a transparent
complete-combustion and flue-gas element balance. The caller supplies dry,
ash-free CHSON fuel mass fractions, dry-air oxygen mole fraction, and excess
oxygen fraction; the immutable receipt returns every input.

For carbon, hydrogen, sulfur, oxygen, and nitrogen atom flows
`nC`, `nH`, `nS`, `nO`, and `nN` in kmol atoms/h, the stoichiometric oxygen
demand is:

```text
O2 stoichiometric = nC + nH / 4 + nS - nO / 2
```

Complete combustion maps carbon to CO2, hydrogen to H2O, sulfur to SO2, and
fuel-bound nitrogen to N2. Supplied oxygen equals stoichiometric oxygen times
`1 + excess oxygen fraction`; the remaining oxygen is reported in dry flue gas.
The receipt closes both elements and total mass:

```text
fuel mass + dry-air mass = wet-flue-gas mass
```

```java
RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance combustion =
    RefineryHydrotreatingSulfurNitrogenFiredHeaterCombustionBalance.calculate(
        heaterUtility, carbonMassFraction, hydrogenMassFraction,
        sulfurMassFraction, oxygenMassFraction, nitrogenMassFraction,
        dryAirOxygenMoleFraction, excessOxygenFraction);

double airKgPerHour = combustion.getDryAirMassFlowKgPerHour();
double stackCo2KgPerHour = combustion.getDirectCarbonDioxideKgPerHour();
double dryStackOxygen = combustion.getDryOxygenMoleFraction();
```

For the public 1000 kg/h DOE/OEDI Big Hill material case, the qualified upstream
receipt requires 73.848455268 kg/h fuel. An illustrative methane elemental
basis (`12.011/16.043` carbon and `4.032/16.043` hydrogen by mass), 0.2095 dry-air
oxygen mole fraction, and 15% excess oxygen require 9.206314937 kmol/h
stoichiometric oxygen and 1457.891168725 kg/h dry air. The result is
202.580357034 kg/h direct CO2, 55.139015119 kmol/h wet flue gas, 3.006457785%
dry oxygen, and numerical-zero total-mass residual. These inputs qualify the
arithmetic only; they are not defaults, measurements, recommendations, or a
site fuel specification.

The molecular formula and molar mass used for the illustrative methane basis
are public NIST Chemistry WebBook data (SRD 69). U.S. EPA AP-42 Section 1.4,
Natural Gas Combustion, is retained as public fired-heater context, not as an
emission-factor input. The material and duty bases inherit the public DOE/OEDI
Big Hill, EIA, AIChE, and NIST evidence qualified by the upstream receipts.

This screening receipt assumes complete conversion to CO2, H2O, SO2, and N2.
It does not predict CO, NOx, unburned hydrocarbons, dissociation, soot, ash,
moisture, air humidity, burner mixing, stack temperature, heat transfer,
pressure drop, dew point, corrosion, dispersion, regulatory compliance, or
emission factors. The utility receipt's caller-supplied kg CO2e/kg fuel factor
has an independent boundary and is not reconciled to direct stack CO2.

Sources accessed 2026-09-26:

- [NIST Chemistry WebBook: methane](https://webbook.nist.gov/cgi/cbook.cgi?ID=C74828)
- [U.S. EPA AP-42 Section 1.4: Natural Gas Combustion](https://www.epa.gov/sites/default/files/2020-09/documents/1.4_natural_gas_combustion.pdf)
