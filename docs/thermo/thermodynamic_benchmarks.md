---
title: "Thermodynamic Model Benchmarks"
description: "Auditable comparison of NeqSim property models with published experimental data."
---

# Thermodynamic model benchmarks

NeqSim provides a reusable benchmark framework in
`neqsim.thermo.util.benchmark.ThermodynamicBenchmark`. It keeps state,
composition, property units, experimental uncertainty, citation, DOI, and data
reuse information together with every comparison.

The framework reports:

- average absolute relative deviation (AARD);
- signed bias;
- root-mean-square relative error;
- maximum absolute relative error;
- uncertainty-normalized residuals when experimental uncertainty is available;
- all point-level experimental and predicted values.

## Hydrogen-containing CO2 benchmark

`H2CO2PhaseEquilibriumData.load()` reads 24 bubble- and dew-point values from
Tables IV and V of:

> Zhang et al. (2026), *Measurement of phase equilibrium characteristics and
> equation-of-state applicability for hydrogen-containing CO2 systems*,
> International Journal of Fluid Engineering 3, 013903.
> [doi:10.1063/5.0288386](https://doi.org/10.1063/5.0288386)

The systems cover:

- 96 mol% CO2 + 4 mol% H2;
- 96 mol% CO2 + 2 mol% H2 + 2 mol% N2;
- temperatures from -30 to 20 °C;
- bubble pressures from 13.8 to 64.3 bara;
- dew pressures from 65.3 to 112.1 bara.

The article does not report pointwise pressure uncertainties. NeqSim records
these as unavailable instead of assigning an unsupported uncertainty.

## Java example

```java
ThermodynamicBenchmark.Dataset dataset = H2CO2PhaseEquilibriumData.load();
NeqSimPhaseEquilibriumPrediction prediction =
    new NeqSimPhaseEquilibriumPrediction(
        NeqSimPhaseEquilibriumPrediction.Model.GERG_2008_H2);

ThermodynamicBenchmark.Report report =
    ThermodynamicBenchmark.run("GERG-2008-H2", dataset, prediction);

double aardPercent = report.getAverageAbsoluteRelativeDeviationPercent();
double biasPercent = report.getBiasPercent();
```

Supported configurations are SRK, PR, standard GERG-2008, and
GERG-2008-H2. Cubic models use NeqSim database interaction parameters and
mixing rule 2. The GERG-2008-H2 configuration enables the hydrogen-enhanced
binary parameters and departure functions.

The experimental pressure is used only as a numerical starting point. It is
not returned by the prediction adapter and does not alter model parameters.

## Interpretation

A low aggregate deviation is not sufficient evidence of universal model
validity. Inspect bubble and dew results separately, examine signed residuals
against temperature and composition, and retain the source validity range.
Do not tune binary interaction parameters without recording their provenance
and validating them on data not used for regression.
