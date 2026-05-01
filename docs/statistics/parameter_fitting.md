---
title: "Parameter Fitting"
description: "Detailed guide to NeqSim parameter fitting, including legacy SampleSet workflows, ExperimentalDataSet readers, YAML specifications, robust objectives, multi-start fitting, validation metrics, and reports."
---

The parameter fitting package calibrates model parameters against experimental data while preserving the original low-level NeqSim API. New work should usually start with `ExperimentalDataSet`, `ParameterFittingSpec`, and `ParameterFittingStudy`; existing code that builds `SampleValue`, `SampleSet`, and `LevenbergMarquardt` directly continues to work.

## Package Map

| Class | Purpose |
|-------|---------|
| `SampleValue` | Legacy single data point: measured value, standard deviation, dependent variables, and fitting function. |
| `SampleSet` | Legacy collection passed directly to `LevenbergMarquardt`. |
| `BaseFunction` | Base class for model functions with mutable fitting parameters. |
| `LevenbergMarquardtFunction` | Convenience base for nonlinear fitting functions. |
| `LevenbergMarquardt` | Weighted least-squares optimizer with structured `LevenbergMarquardtResult`. |
| `ExperimentalDataPoint` | Immutable measured value with units, reference, and description metadata. |
| `ExperimentalDataSet` | Metadata-rich data set that converts to `SampleSet` and supports train-validation splitting. |
| `ExperimentalDataReader` | CSV, JSON, and YAML readers with temperature and pressure unit conversion. |
| `ParameterFittingSpec` | Serializable fitting setup: parameters, bounds, transforms, objective, multi-start, and validation split. |
| `FittingParameter` | One parameter definition with physical bounds, unit, transform, category, and optional prior. |
| `ParameterFittingStudy` | High-level fitting workflow with robust reweighting, multi-start, validation metrics, and reports. |
| `ParameterFittingReport` | JSON and Markdown summary of fitted parameters and residual metrics. |

## Compatibility

The stable Java package is `neqsim.statistics.parameterfitting` with the nested optimizer package `neqsim.statistics.parameterfitting.nonlinearparameterfitting`. Backward compatibility is maintained by keeping the existing class names, constructors, and fluent methods intact:

- `LevenbergMarquardt.solve()` still returns `void`.
- `SampleValue`, `SampleSet`, `BaseFunction`, and `LevenbergMarquardtFunction` are still available in the same packages.
- Existing code can still set initial guesses on the function, create samples manually, call `optimizer.setSampleSet(sampleSet)`, and call `optimizer.solve()`.
- New diagnostics are additive through `optimizer.getResult()` and `ParameterFittingStudy.Result`.

## Weighted Least Squares

The default objective minimizes weighted residuals:

$$
\chi^2 = \sum_{i=1}^{N} \left( \frac{y_i^{\mathrm{exp}} - y_i^{\mathrm{calc}}(\mathbf{p})}{\sigma_i} \right)^2
$$

where $y_i^{\mathrm{exp}}$ is the experimental value, $y_i^{\mathrm{calc}}$ is the model prediction, $\sigma_i$ is the measurement standard deviation, and $\mathbf{p}$ is the parameter vector. A reduced $\chi^2$ near 1 usually means the model residuals are consistent with the stated experimental uncertainty.

## Legacy SampleSet Workflow

Use the low-level workflow when you already have code that creates `SampleValue` objects or when you need direct access to the optimizer internals.

```java
import java.util.ArrayList;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtFunction;

class LinearFunction extends LevenbergMarquardtFunction {
  @Override
  public double calcValue(double[] dependentValues) {
    return params[0] * dependentValues[0] + params[1];
  }

  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
  }
}

LinearFunction function = new LinearFunction();
function.setInitialGuess(new double[] {0.5, 0.0});

ArrayList<SampleValue> samples = new ArrayList<SampleValue>();
SampleValue sample1 = new SampleValue(1.3, 0.1, new double[] {1.0});
SampleValue sample2 = new SampleValue(3.8, 0.1, new double[] {2.0});
sample1.setFunction(function);
sample2.setFunction(function);
samples.add(sample1);
samples.add(sample2);

LevenbergMarquardt optimizer = new LevenbergMarquardt();
optimizer.setSampleSet(new SampleSet(samples));
optimizer.solve();

double slope = function.getFittingParams(0);
double intercept = function.getFittingParams(1);
double finalChiSquare = optimizer.getResult().getFinalChiSquare();
```

## Experimental Data Sets

The higher-level workflow stores names, units, references, and descriptions with each data point. It is the recommended entry point for experimental model calibration because it gives residual metrics and reports without losing access to the legacy optimizer.

```java
ExperimentalDataSet dataSet = new ExperimentalDataSet(
    "linear calibration",
    "response",
    "-",
    new String[] {"x"},
    new String[] {"-"});

dataSet.addPoint(1.3, 0.1, new double[] {1.0}, "lab", "first point");
dataSet.addPoint(3.8, 0.1, new double[] {2.0}, "lab", "second point");
```

Convert to a legacy optimizer data set when needed:

```java
SampleSet sampleSet = dataSet.toSampleSet(function);
```

Split deterministically for validation:

```java
ExperimentalDataSet[] split = dataSet.split(0.75);
ExperimentalDataSet trainingData = split[0];
ExperimentalDataSet validationData = split[1];
```

## CSV Data Files

Use CSV when data is tabular and usually comes from lab spreadsheets. A tested example is available at [docs/statistics/examples/parameter_fitting_data.csv](examples/parameter_fitting_data.csv).

```csv
temperature_C,pressure_psi,response,standardDeviation,reference,description
25.0,14.5037738,5.0,0.1,synthetic,row one
50.0,29.0075476,7.5,0.1,synthetic,row two
75.0,43.5113214,10.0,0.1,synthetic,row three
```

Read the file with explicit column mapping and source units:

```java
import java.io.File;
import neqsim.statistics.parameterfitting.ExperimentalDataReader;
import neqsim.statistics.parameterfitting.ExperimentalDataSet;

ExperimentalDataReader.CsvOptions options = new ExperimentalDataReader.CsvOptions(
    "documentation csv",
    "response",
    "-",
    new String[] {"temperature", "pressure"},
    new String[] {"K", "bara"});

options.setDependentVariableColumns(new String[] {"temperature_C", "pressure_psi"});
options.setSourceDependentVariableUnits(new String[] {"C", "psi"});

ExperimentalDataSet csvData = ExperimentalDataSet.fromCsv(
    new File("docs/statistics/examples/parameter_fitting_data.csv"), options);
```

The reader converts temperature with `TemperatureUnit` and pressure with `PressureUnit`. In the example above, `25.0 C` becomes `298.15 K`, and `14.5037738 psi` becomes approximately `1.0 bara`.

## YAML Data Files

Use YAML when data needs metadata, references, and a format that is easy to review in Git. A tested example is available at [docs/statistics/examples/parameter_fitting_data.yaml](examples/parameter_fitting_data.yaml).

```yaml
name: yaml response calibration
responseName: response
responseUnit: '-'
dependentVariableNames:
  - temperature
  - pressure
dependentVariableUnits:
  - K
  - bara
points:
  - measuredValue: 5.0
    standardDeviation: 0.1
    dependentValues:
      - 298.15
      - 1.0
    reference: synthetic
    description: row one
```

Read YAML directly:

```java
ExperimentalDataSet yamlData = ExperimentalDataSet.fromYaml(
    new File("docs/statistics/examples/parameter_fitting_data.yaml"));
```

The JSON reader uses the same field names:

```java
ExperimentalDataSet jsonData = ExperimentalDataSet.fromJson(jsonText);
```

## Fitting Specifications

`ParameterFittingSpec` defines the fitting problem in a reusable file: parameter names, initial values, physical bounds, transforms, robust objective settings, multi-start count, random seed, and optional training fraction. A tested example is available at [docs/statistics/examples/parameter_fitting_spec.yaml](examples/parameter_fitting_spec.yaml).

```yaml
name: positive gain robust fit
experimentType: GENERIC
objectiveFunctionType: HUBER
robustTuningConstant: 1.345
maxRobustIterations: 2
maxNumberOfIterations: 40
multiStartCount: 3
randomSeed: 7
parameters:
  - name: gain
    initialValue: 1.0
    lowerBound: 0.1
    upperBound: 10.0
    unit: '-'
    transform: LOG
    category: scale
```

Read a specification from YAML or JSON:

```java
ParameterFittingSpec spec = ParameterFittingSpec.fromYaml(
    new File("docs/statistics/examples/parameter_fitting_spec.yaml"));
```

Build the same spec in Java:

```java
ParameterFittingSpec spec = new ParameterFittingSpec("positive gain robust fit");
spec.setObjectiveFunctionType(ObjectiveFunctionType.HUBER);
spec.setMaxRobustIterations(2);
spec.setMultiStartCount(3);
spec.setRandomSeed(7L);
spec.setMaxNumberOfIterations(40);
spec.addParameter(new FittingParameter("gain", 1.0, 0.1, 10.0, "-",
    ParameterTransform.LOG, "scale", Double.NaN, Double.NaN));
```

## Parameter Transforms

Transforms let the optimizer move in a numerically convenient space while the model still receives physical parameter values.

| Transform | Use case | Bound requirement |
|-----------|----------|-------------------|
| `LINEAR` | Ordinary bounded or unbounded parameters. | Any finite or broad bounds. |
| `LOG` | Positive parameters spanning orders of magnitude. | Lower and upper bounds must be positive. |
| `LOG10` | Positive parameters where base-10 scaling is natural. | Lower and upper bounds must be positive. |
| `LOGISTIC` | Parameters that must stay strictly between finite lower and upper bounds. | Both bounds must be finite and ordered. |

The high-level study converts optimizer parameters back to physical values before calling your model function. This keeps existing `BaseFunction` implementations compatible.

## Robust Objectives

The default objective is `WEIGHTED_LEAST_SQUARES`. Robust objectives are implemented as iteratively reweighted least squares, so they reuse the same `LevenbergMarquardt` optimizer and keep the old API stable.

| Objective | Behavior |
|-----------|----------|
| `WEIGHTED_LEAST_SQUARES` | Standard weighted least squares. Best when uncertainties are reliable and outliers are absent. |
| `ABSOLUTE_DEVIATION` | Reduces sensitivity to large residuals by approximating an L1 objective. |
| `HUBER` | Quadratic near zero, linear for large standardized residuals. Good default robust choice. |
| `CAUCHY` | Stronger down-weighting of large residuals. Useful for noisy screening data. |
| `TUKEY_BIWEIGHT` | Very strong rejection of large outliers. Use carefully when outliers are clear. |

The tuning constant is dimensionless because residuals are standardized by the measurement standard deviation. `1.345` is a common Huber starting point.

## Study Workflow

This example fits a positive gain parameter using a log transform, Huber robust objective, deterministic multi-start, validation data, and a report.

```java
class PositiveGainFunction extends LevenbergMarquardtFunction {
  @Override
  public double calcValue(double[] dependentValues) {
    return params[0] * dependentValues[0];
  }

  @Override
  public void setFittingParams(int i, double value) {
    params[i] = value;
  }
}

ExperimentalDataSet training = new ExperimentalDataSet("positive slope", "response", "-",
    new String[] {"x"}, new String[] {"-"});
training.addPoint(3.0, 0.05, new double[] {1.0});
training.addPoint(6.0, 0.05, new double[] {2.0});
training.addPoint(9.0, 0.05, new double[] {3.0});

ExperimentalDataSet validation = new ExperimentalDataSet("validation", "response", "-",
    new String[] {"x"}, new String[] {"-"});
validation.addPoint(12.0, 0.05, new double[] {4.0});

ParameterFittingStudy study = new ParameterFittingStudy(training, new PositiveGainFunction(), spec)
    .setValidationDataSet(validation);

ParameterFittingStudy.Result result = study.fit();
double gain = result.getFittedParameter("gain");
double validationRmse = result.getValidationRootMeanSquareError();
ParameterFittingReport report = study.createReport();
String reportJson = report.toJson();
String reportMarkdown = report.toMarkdown();
```

Important result fields:

| Method | Meaning |
|--------|---------|
| `getFittedParameters()` | Final physical parameter values. |
| `getFittedParameter(name)` | Final physical value by parameter name. |
| `getCalculatedValues()` | Model predictions for the fitting data. |
| `getResiduals()` | Measured minus calculated values. |
| `getWeightedResiduals()` | Residuals divided by experimental standard deviations. |
| `getRootMeanSquareError()` | RMSE in response units. |
| `getWeightedRootMeanSquareError()` | RMSE in standardized residual units. |
| `getReducedChiSquare()` | Weighted chi-square divided by degrees of freedom. |
| `getValidationRootMeanSquareError()` | Validation RMSE when validation data is configured. |
| `getOptimizerResult()` | Low-level LM convergence reason, iterations, covariance, correlation, and standard errors. |

## Thermodynamic Model Adapters

For thermodynamic calibration, the fitting function usually sets physical parameters on a NeqSim model before running a flash or property calculation. `ParameterUpdateAdapter` lets that update live outside the objective function. `BinaryInteractionParameterAdapter` is provided for one binary interaction parameter on a `SystemInterface`.

Typical pattern:

1. Define the fluid and mixing rule.
2. Define a `FittingParameter` for `kij` with a physical range such as `[-0.5, 0.5]`.
3. Attach `BinaryInteractionParameterAdapter(system, component1, component2, parameter)` to the study.
4. Let the fitting function read the current model state and return the calculated experimental response.

This keeps the fitting study generic while allowing model-specific parameter updates.

## Levenberg-Marquardt Diagnostics

`LevenbergMarquardtResult` is available after `solve()`:

```java
LevenbergMarquardtResult result = optimizer.getResult();
int iterations = result.getIterations();
double chiSquare = result.getFinalChiSquare();
double gradientNorm = result.getGradientNorm();
double[][] covariance = result.getCovariance();
double[][] correlation = result.getCorrelation();
double[] standardErrors = result.getParameterStandardErrors();
```

Convergence reasons are:

| Reason | Meaning |
|--------|---------|
| `NOT_RUN` | Optimizer has not been executed. |
| `CHI_SQUARE_TOLERANCE` | Relative chi-square change was small enough. |
| `GRADIENT_TOLERANCE` | Weighted gradient norm was small enough. |
| `MAX_ITERATIONS_REACHED` | Iteration limit was reached before convergence. |
| `SINGULAR_MATRIX` | The linearized LM system could not be solved. |

## Numerical Derivatives

`NumericalDerivative.calcDerivative(...)` uses Ridders-style extrapolation. The first perturbation is based on `abs(parameter) / 1000`, with a `1.0e-10` fallback for zero-valued parameters. The original parameter value is restored after derivative evaluation, which is important when fitting thermodynamic models with mutable state.

## Practical Guidance

Use these defaults unless your data suggests otherwise:

| Situation | Recommended setup |
|-----------|-------------------|
| Clean lab data with trustworthy uncertainties | `WEIGHTED_LEAST_SQUARES`, one start, physically meaningful bounds. |
| Occasional outliers | `HUBER`, 2-5 robust iterations, inspect weighted residuals. |
| Positive scale factors | `LOG` transform with positive lower and upper bounds. |
| Strongly bounded fractions or efficiencies | `LOGISTIC` transform with finite bounds. |
| Non-convex model response | Increase `multiStartCount` and set a fixed `randomSeed` for reproducibility. |
| Reporting or review | Use `ParameterFittingReport.toJson()` and `toMarkdown()` and keep the CSV/YAML input files beside the report. |

## Troubleshooting

| Symptom | Likely cause | Action |
|---------|--------------|--------|
| `setInitialGuess must be called before run` | No spec and no initial guess was configured. | Call `setInitialGuess(...)` or pass a `ParameterFittingSpec`. |
| Singular matrix | Too few data points, collinear data, or insensitive parameters. | Add data, reduce parameter count, or widen the experimental design. |
| Reduced chi-square much larger than 1 | Poor model, underestimated standard deviations, or outliers. | Inspect residuals, use Huber/Cauchy, and check experimental uncertainty. |
| Reduced chi-square much smaller than 1 | Standard deviations are probably too large. | Review uncertainty estimates. |
| Parameter stuck on a bound | Bound too tight or model wants an unphysical value. | Check data quality and parameter range; do not widen bounds without physical justification. |
| Robust objective gives different answer | Large residual points are being down-weighted. | Compare weighted residuals and document which points drive the difference. |

## Verification

The examples in this guide are exercised by `ExperimentalParameterFittingStudyTest`, including:

- Loading [docs/statistics/examples/parameter_fitting_data.csv](examples/parameter_fitting_data.csv).
- Loading [docs/statistics/examples/parameter_fitting_data.yaml](examples/parameter_fitting_data.yaml).
- Loading [docs/statistics/examples/parameter_fitting_spec.yaml](examples/parameter_fitting_spec.yaml).
- Running a spec-driven robust fit with log-transformed parameters, multi-start, validation data, and report generation.
- Running legacy `SampleValue` / `SampleSet` / `LevenbergMarquardt` workflows.

## References

1. Marquardt, D. W. (1963). An Algorithm for Least-Squares Estimation of Nonlinear Parameters. SIAM Journal on Applied Mathematics, 11(2), 431-441.
2. Press, W. H., Teukolsky, S. A., Vetterling, W. T., and Flannery, B. P. (2007). Numerical Recipes, 3rd edition. Cambridge University Press.
3. Poling, B. E., Prausnitz, J. M., and O'Connell, J. P. (2001). The Properties of Gases and Liquids, 5th edition. McGraw-Hill.