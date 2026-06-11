---
title: "Mercury Thermodynamics in NeqSim"
description: "How to model elemental mercury (Hg0) in NeqSim using SRK-TwuCoon-Statoil-EOS, configure TPflash workflows, and use thesis-derived binary interaction parameters and correlations."
---

# Mercury Thermodynamics in NeqSim

This page describes practical workflows for modeling elemental mercury ($Hg^0$) in hydrocarbon systems with NeqSim.

It is aligned with the PhD reference:

- Stylianos Kossifos, *Mercury in Natural Gas and Liquid Hydrocarbons and New Numerical and Experimental Methods to Simulate and Evaluate Processes for Mercury Removal and Mercury Reduction* (NTUA, 2022):
  [Mercury PhD_complete.pdf](https://dspace.lib.ntua.gr/xmlui/bitstream/handle/123456789/53076/Mercury%20PhD_complete.pdf?sequence=1&isAllowed=y)

## Recommended EOS for Mercury Systems

For mercury-containing natural gas and light hydrocarbon systems, use:

- `SystemSrkTwuCoonStatoilEos`

This model is the NeqSim SRK-TwuCoon variant used for mercury-focused calculations.

## Minimal TPflash Example (Java)

```java
SystemInterface fluid = new SystemSrkTwuCoonStatoilEos(273.15 - 172.0, 1.0);

fluid.addComponent("nitrogen", 2.97007999748152e-2);
fluid.addComponent("methane", 0.902244);
fluid.addComponent("ethane", 0.053167);
fluid.addComponent("propane", 0.010742);
fluid.addComponent("i-butane", 0.000902);
fluid.addComponent("n-heptane", 0.02692);
fluid.addComponent("mercury", 2.12608096955523e-10);

fluid.createDatabase(true);
fluid.setMixingRule(2);
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Optional post-processing examples
double pHg = fluid.getPhase(0).getComponent("mercury").getx() * fluid.getPressure();
```

A runnable repository example is available in:

- [TestmercuryTPflash.java (repository source)](https://github.com/equinor/neqsim/blob/master/src/test/java/neqsim/thermo/util/example/TestmercuryTPflash.java)

## Minimal TPflash Example (Python)

```python
import neqsim

fluid = neqsim.thermo.system.SystemSrkTwuCoonStatoilEos(273.15 - 172.0, 1.0)
fluid.addComponent("nitrogen", 2.97007999748152e-2)
fluid.addComponent("methane", 0.902244)
fluid.addComponent("ethane", 0.053167)
fluid.addComponent("propane", 0.010742)
fluid.addComponent("i-butane", 0.000902)
fluid.addComponent("n-heptane", 0.02692)
fluid.addComponent("mercury", 2.12608096955523e-10)

fluid.createDatabase(True)
fluid.setMixingRule(2)
fluid.setMultiPhaseCheck(True)

ops = neqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
```

## Mercury with UMR-PRU

In addition to SRK-TwuCoon, NeqSim also supports mercury calculations with UMR-PRU family models:

- `SystemUMRPRUEos`

UMR-PRU is useful when you want a predictive UNIFAC-driven mixing-rule framework for non-ideal mixtures, while still using a cubic EOS backbone.

### When to use UMR-PRU for mercury

Use UMR-PRU as a comparison or sensitivity model when:

- Your mixture has stronger non-ideality and you want UNIFAC-driven excess Gibbs contributions.
- You want to benchmark mercury partitioning predictions against SRK-TwuCoon.
- You are performing model uncertainty checks over composition and temperature ranges.

For direct alignment with the mercury thesis workflows, SRK-TwuCoon-Statoil-EOS remains the primary baseline model.

### Minimal UMR-PRU TPflash Example (Java)

```java
SystemInterface fluid = new SystemUMRPRUEos(273.15 - 172.0, 1.0);

fluid.addComponent("nitrogen", 2.97007999748152e-2);
fluid.addComponent("methane", 0.902244);
fluid.addComponent("ethane", 0.053167);
fluid.addComponent("propane", 0.010742);
fluid.addComponent("i-butane", 0.000902);
fluid.addComponent("n-heptane", 0.02692);
fluid.addComponent("mercury", 2.12608096955523e-10);

fluid.createDatabase(true);
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### Minimal UMR-PRU TPflash Example (Python)

```python
import neqsim

fluid = neqsim.thermo.system.SystemUMRPRUEos(273.15 - 172.0, 1.0)
fluid.addComponent("nitrogen", 2.97007999748152e-2)
fluid.addComponent("methane", 0.902244)
fluid.addComponent("ethane", 0.053167)
fluid.addComponent("propane", 0.010742)
fluid.addComponent("i-butane", 0.000902)
fluid.addComponent("n-heptane", 0.02692)
fluid.addComponent("mercury", 2.12608096955523e-10)

fluid.createDatabase(True)
fluid.setMixingRule("HV", "UNIFAC_UMRPRU")
fluid.setMultiPhaseCheck(True)

ops = neqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
```

### Recommended comparison workflow

For mercury studies, compare at least two models on the same fluid and conditions:

1. SRK-TwuCoon-Statoil-EOS (baseline)
2. UMR-PRU (predictive comparison)

Track differences in:

- Mercury gas-phase mole fraction
- Mercury partial pressure
- Liquid/gas partitioning trend versus temperature

This gives a practical uncertainty band for design and interpretation.

## Thesis-Derived BIP Usage in NeqSim

NeqSim uses `INTER.csv` as the main source of binary interaction parameters for SRK and PR family models.

For mercury-related systems, key updates can include:

- Fixed $k_{ij}$ rows for mercury-hydrocarbon pairs (SRK/PR)
- Temperature-dependent terms for selected polar pairs (for example mercury-water and mercury-MEG)

When explicit rows are not available for mercury-heavy pseudo components, NeqSim can estimate $k_{ij}$ using a correlation of the form:

$$
k_{ij} = A \cdot T_b + B \cdot MW + C
$$

where:

- $T_b$ is normal boiling temperature in K
- $MW$ is molecular weight in g/mol

Unit consistency is important: using $MW$ in kg/mol instead of g/mol changes $k_{ij}$ significantly.

## Practical Validation Workflow

For a mercury thermodynamics validation study:

1. Build one representative fluid from your process basis.
2. Run TPflash over your operating pressure-temperature envelope.
3. Track mercury partitioning between phases and partial pressure trends.
4. Compare against thesis plots/tables at matching conditions.
5. Document deviations and whether they are due to EOS limits, composition uncertainty, or BIP uncertainty.

## Related Documentation

- [Thermodynamic Models](thermodynamic_models)
- [Fluid Creation Guide](fluid_creation_guide)
- [INTER Table Guide](inter_table_guide)
- [Mixing Rules Guide](mixing_rules_guide)
- [Flash Calculations Guide](flash_calculations_guide)
- [Thermodynamic Operations](thermodynamic_operations)
