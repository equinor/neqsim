---
title: Legacy CO2 Impurity Kinetics Experiment Guide
description: Experimental Python tutorial material for CO2 impurity kinetics and its relationship to the maintained Java reactor.
---

# Legacy CO2 impurity kinetics experiments

This page describes the experimental Python tutorials in `docs/tutorials`. For the maintained
Java equipment model, use the
[experimental CO2 impurity kinetic reactor guide](chemicalreactions/co2_impurity_kinetics_guide.md).

> **Model status:** experimental. The bundled kinetic parameters and screening correlations are
> illustrative, not calibrated design correlations. Validate the reaction network and parameters
> against representative laboratory data before using any predicted conversion, acid formation,
> or sulfur formation in engineering decisions.

## Relationship between the Python and Java models

The Python module is a tutorial prototype. It uses the NeqSim SRK equation of state when the NeqSim
Python package is available and otherwise emits a warning and uses a simple screening correlation.
The maintained Java reactor always derives density from a flashed NeqSim inlet fluid. The two
implementations can therefore give different numerical results and should not be treated as mutual
validation.

Both implementations use the same balanced reaction-network interpretation:

| ID | Net reaction | Role |
|---|---|---|
| R1 | $\mathrm{SO_2 + 0.5 O_2 + H_2O \rightarrow H_2SO_4}$ | Direct SO2 oxidation |
| R2 | $\mathrm{H_2S + 3 NO_2 \rightarrow SO_2 + H_2O + 3 NO}$ | H2S oxidation by NO2 |
| R3A | $\mathrm{SO_2 + NO_2 + H_2O \rightarrow H_2SO_4 + NO}$ | NO2-assisted SO2 oxidation |
| R3B | $\mathrm{SO_2 + 0.5 O_2 + H_2O \rightarrow H_2SO_4}$ | H2S/NO2 co-catalysed pathway |
| R4 | $\mathrm{2 NO + O_2 \rightarrow 2 NO_2}$ | NO oxidation |
| R5 | $\mathrm{3 NO_2 + H_2O \rightarrow 2 HNO_3 + NO}$ | Nitric-acid formation |
| R6 | $\mathrm{H_2S + 1.5 O_2 \rightarrow SO_2 + H_2O}$ | H2S oxidation by oxygen |
| R7 | $\mathrm{5 H_2S + 6 NO + 4 H_2O \rightarrow 6 NH_3 + 5 SO_2}$ | Reduced nitrogen/sulfur pathway |
| R8 | $\mathrm{H_2S + 0.5 O_2 \rightarrow \frac{1}{8}S_8 + H_2O}$ | Wall-material-dependent sulfur formation |

R3B treats H2S and NO2 as rate-law co-catalysts. They are not consumed by the R3B net reaction.

The Arrhenius expression is

$$k_j(T)=A_j\exp\left(-\frac{E_{a,j}}{RT}\right)$$

where $T$ is in kelvin, $R=8.314462618\ \mathrm{J\,mol^{-1}\,K^{-1}}$, and $E_a$ is
stored in J/mol. Reaction orders differ across the illustrative rate laws, so the pre-exponential
factor units depend on the selected reaction.

## Python tutorial API

Install the numerical dependencies before running the tutorials:

```bash
python -m pip install numpy pandas matplotlib scipy
```

The model can be used for a single batch-style screening case:

```python
from neqsim_co2_kinetics import CO2ImpurityKineticsModel

model = CO2ImpurityKineticsModel(
    T_kelvin=248.15,
    P_bar=25.0,
    water_ppm=10.0,
    material="carbon_steel",
)
result = model.simulate(
    {"H2S": 10.0, "SO2": 10.0, "NO2": 10.0, "O2": 10.0, "H2O": 10.0},
    duration_sec=10.0 * 3600.0,
    num_points=101,
)
```

Or configure a multi-phase CSTR-style tutorial experiment:

```python
from neqsim_co2_kinetics import CO2ImpurityReactorExperiment

experiment = CO2ImpurityReactorExperiment(
    target_pressure_bar=25.0,
    target_temp_C=-25.0,
    diameter_cm=6.50,
    volume_ml=300.0,
    mass_flow_g_h=50.0,
)
experiment.add_phase(10.0, {}, "Pure CO2")
experiment.add_phase(
    40.0,
    {"SO2": 10.0, "NO2": 10.0, "O2": 10.0, "H2O": 10.0},
    "Without H2S",
)
experiment.add_phase(
    50.0,
    {"H2S": 10.0, "SO2": 10.0, "NO2": 10.0, "O2": 10.0, "H2O": 10.0},
    "All impurities",
)
experiment.run_experiment()
table = experiment.get_table_results(resolution_hours=2.0)
figure, axes = experiment.plot_results()
```

The runner scripts write CSV and PNG artifacts to the current directory by default. Set
`NEQSIM_TUTORIAL_OUTPUT_DIR` or pass `output_dir` to a runner function to select another location.

## Current Java API

The maintained Java class accepts reaction identifiers such as `R3B`; it does not accept a
reaction equation as the identifier. The following example also follows the project logging
convention:

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.reactor.CO2ImpurityKineticReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class CO2ImpurityGuideExample {
  private static final Logger logger =
      LogManager.getLogger(CO2ImpurityGuideExample.class);

  private CO2ImpurityGuideExample() {}

  public static void run() {
    SystemInterface fluid = new SystemSrkEos(248.15, 25.0);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("H2S", 10.0e-6);
    fluid.addComponent("SO2", 10.0e-6);
    fluid.addComponent("NO2", 10.0e-6);
    fluid.addComponent("oxygen", 10.0e-6);
    fluid.addComponent("water", 10.0e-6);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("CO2 feed", fluid);
    feed.run();

    CO2ImpurityKineticReactor reactor =
        new CO2ImpurityKineticReactor("CO2 impurity reactor", feed);
    reactor.setReactorGeometry(6.50, 300.0, 50.0);
    reactor.setReactionConstants("R3B", 2.13e8, 15.0);
    logger.info("{}", reactor.generateReactorReport());
  }
}
```

## Validation boundaries

The Python tutorial does not establish accuracy against experiments. Before engineering use,
validate the selected model over the intended pressure, temperature, phase, impurity, water,
residence-time, and wall-material ranges. At minimum, verify elemental conservation,
non-negative compositions, parameter sensitivity, and uncertainty when extrapolating.
