---
title: "Mercury Thermodynamics in NeqSim"
description: "How to model elemental mercury (Hg0) in NeqSim using SRK-TwuCoon-Statoil-EOS or UMR-PRU-MC with the mercury calculator parameters, calculate a separate liquid or solid mercury phase, configure TPflash workflows, and use thesis-derived binary interaction parameters and correlations."
---

This page describes practical workflows for modeling elemental mercury ($Hg^0$) in hydrocarbon systems with NeqSim.

It is aligned with the PhD reference:

- Stylianos Kossifos, *Mercury in Natural Gas and Liquid Hydrocarbons and New Numerical and Experimental Methods to Simulate and Evaluate Processes for Mercury Removal and Mercury Reduction* (NTUA, 2022):
  [Mercury PhD_complete.pdf](https://dspace.lib.ntua.gr/xmlui/bitstream/handle/123456789/53076/Mercury%20PhD_complete.pdf?sequence=1&isAllowed=y)

## Recommended EOS for Mercury Systems

For mercury-containing natural gas and light hydrocarbon systems, use:

- `SystemSrkTwuCoonStatoilEos`

This model is the NeqSim SRK-TwuCoon variant used for mercury-focused calculations.

To reproduce the Equinor mercury calculator within a few percent, or to calculate a separate liquid or solid
mercury phase, see [UMR-PRU-MC with the mercury calculator parameters](#umr-pru-mc-with-the-mercury-calculator-parameters).

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
- `SystemUMRPRUMCEos`, whose mercury and water parameters are those of the mercury calculator; see
  [UMR-PRU-MC with the mercury calculator parameters](#umr-pru-mc-with-the-mercury-calculator-parameters)

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

## UMR-PRU-MC with the mercury calculator parameters

`SystemUMRPRUMCEos` uses the mercury and water group-interaction parameters of the Equinor mercury
calculator library (libhg 2.0.3). Use it, with `setMixingRule("HV", "UNIFAC_UMRPRU")`, when the mercury
distribution between gas, oil and water should agree with that calculator.

The parameters are in the `UNIFACInterParamA_UMRMC`, `B_UMRMC` and `C_UMRMC` tables: the mercury and the
water group against CO2, N2, methane, ethane, the alkane, aromatic, alkyl-aromatic and cyclic groups, H2S,
hydrogen and methanol. Most hydrocarbon, CO2 and N2 group parameters were already the same as in the
calculator; the H2S, hydrogen and substituted cyclic carbon groups still differ. With the water parameters
used before, UMR-PRU-MC formed no aqueous phase for a gas-condensate with 15 mol% water. UMR-CPA, which
describes water with an association term, keeps those parameters in its own `UNIFAC*_UMRCPA` tables and is
not changed.

Three things are not taken from the calculator, so the agreement is close but not exact:

- The critical properties and Mathias-Copeman coefficients of the components come from the NeqSim
  component table. They differ for some components, mainly the n-alkanes from nC20.
- A branched alkane has no separate group for its methyl branch.
- **Glycols are not covered.** The calculator treats MEG and TEG as single groups with their own mercury
  parameters. UMR-PRU-MC builds them from CH2, OH and ether groups, which have no mercury parameters. In the
  two cases checked, the mercury content of the gas over aqueous MEG and TEG was 2400 and 130 times lower
  than in the calculator. Do not use UMR-PRU-MC for mercury in a fluid with glycol.

### Separate mercury phase

`MercuryPhaseEquilibrium` calculates the gas, oil and aqueous phases together with a separate phase of pure
mercury, liquid above 234.321 K and solid below. It works on a copy of the fluid taken when the object is
created, so create a new `MercuryPhaseEquilibrium` after changing temperature, pressure or composition.
`getFluid()` returns a new copy of the result on each call; keep it in a variable.

```java
SystemInterface fluid = new SystemUMRPRUMCEos(273.15 + 20.0, 21.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("n-heptane", 0.10);
fluid.addComponent("water", 0.19);
fluid.addComponent("mercury", 0.01);
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

MercuryPhaseEquilibrium mercury = new MercuryPhaseEquilibrium(fluid);
mercury.run();

String state = mercury.getFreeMercuryState(); // "none", "liquid" or "solid"
double freeFraction = mercury.getFreeMercuryFraction(); // mole fraction of the feed
SystemInterface phases = mercury.getFluid(); // gas, oil and aqueous phases with dissolved mercury
for (int i = 0; i < phases.getNumberOfPhases(); i++) {
  double phaseFraction = mercury.getPhaseFraction(i);
  double moleFraction = mercury.getMercuryMoleFraction(i);
  double ugPerSm3 = mercury.getMercuryConcentration(i, "ug/Sm3");
  double ugPerKg = mercury.getMercuryConcentration(i, "ug/kg");
}
```

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemUMRPRUMCEos(273.15 + 20.0, 21.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("n-heptane", 0.10)
fluid.addComponent("water", 0.19)
fluid.addComponent("mercury", 0.01)
fluid.setMixingRule("HV", "UNIFAC_UMRPRU")

mercury = jneqsim.thermo.util.mercury.MercuryPhaseEquilibrium(fluid)
mercury.run()
print(mercury.getFreeMercuryState(), mercury.getFreeMercuryFraction())
phases = mercury.getFluid()
for i in range(phases.getNumberOfPhases()):
    print(phases.getPhase(i).getType(), mercury.getMercuryConcentration(i, "ug/Sm3"))
```

Mercury is treated as a dilute solute: the other components are split by a flash at trace mercury content
and the mercury content of each phase is then solved. The error is of the order of the dissolved mercury
mole fraction. `MercuryPhaseEquilibrium` takes the pure mercury fugacity from the equation of state of the
fluid, so it also runs with other models, such as `SystemSrkTwuCoonStatoilEos`.

An ordinary `TPflash` on a fluid above mercury saturation does not give this result. The equation of state
then forms a mercury-rich liquid, which held 5 to 13 mol% of other components in the cases checked, and it
takes the place of one of the three fluid phases NeqSim reports.

### Mercury through a process

To track mercury through a process, convert the study fluid with `MercuryFluidConverter`, let the separators
hold back free mercury and read the report of `MercuryProcessAnalyzer`. See
[Mercury distribution studies in process simulation](../process/mercury-distribution-study.md).

### Agreement with the mercury calculator

Checked on 2026-11-19 against libhg 2.0.3, as the mercury mole fraction in each phase.

| Fluids | Conditions | NeqSim relative to libhg |
|--------|------------|--------------------------|
| Gas and gas-condensate up to nC20, with and without water and H2S (3 cases) | -30 to 30 C, 21 to 70 bara, below saturation | -1.0 % to +0.2 % |
| The same fluids with a liquid mercury phase (2 cases) | -20 and 20 C, 21 and 70 bara | -0.9 % to +0.9 % |
| Gas-condensate with n-alkanes up to nC39, with and without water (8 cases) | -30 to 60 C, 21 and 70 bara | -3.7 % to +4.4 % |
| Gas over aqueous MEG or TEG (2 cases) | 10 and 30 C, 60 and 80 bara | not comparable, see above |

Phase fractions agree within 0.004. `MercuryPhaseEquilibriumTest` holds the cases of the first two rows.

Apart from the parameters, the results differ from the calculator in four situations:

- **Mercury phase not found by libhg.** libhg reports a mercury phase only when its stability analysis
  finds one. In 3 of 10 checked cases above saturation, by a factor 2.9 to 350, it reported no mercury
  phase and gave the supersaturated concentrations. `MercuryPhaseEquilibrium` compares the mercury fugacity
  with that of pure mercury and reported the mercury phase in all ten.
- **Solid mercury under pressure.** libhg divides the volume term of solid mercury by a gas constant that
  is ten times too small for its units. At -50 C its mercury solubility is therefore 10 % higher at 30 bara
  and 40 % higher at 100 bara than with consistent units, which NeqSim uses.
- **Number of fluid phases.** The two programs use different stability analyses. In one case NeqSim found a
  condensate phase that libhg did not report, and in one case at -50 C libhg found a second, aromatic-rich
  liquid that NeqSim did not report. NeqSim reports at most three fluid phases.
- **Concentration basis of a dense gas.** libhg labels a phase with compressibility below 0.307 as liquid
  and reports it in ug/kg. Ask `getMercuryConcentration` for the unit you need.

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
