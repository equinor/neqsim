---
name: neqsim-model-calibration-and-data-reconciliation
description: "Model calibration and data reconciliation workflow for NeqSim digital twins. USE WHEN: reducing model-vs-plant mismatch, tuning process model parameters from historian data, reconciling noisy measurements, or building validation reports for calibrated simulations. Covers tag mapping, data quality gates, steady-state window selection, bounded optimization, residual diagnostics, and results.json reporting."
last_verified: "2026-04-19"
---

# Model Calibration and Data Reconciliation

A practical workflow for calibrating NeqSim models against plant historian data
and producing auditable validation output.

## When to Use This Skill

- When simulated values do not match measured plant data.
- When building digital twin loops for separators, compressors, or heat exchangers.
- When you need reproducible parameter tuning with physical bounds.
- When a study requires train/validation split and objective fit metrics.

## Scope and Non-Goals

This skill focuses on steady-state calibration and data reconciliation.

This skill does not replace dynamic controller tuning. For transient tuning,
load `neqsim-dynamic-simulation` in addition.

## Required Companion Skills

- `neqsim-plant-data`: historian read patterns (PI/IP.21), tag maps, quality flags.
- `neqsim-input-validation`: parameter bounds and physically realistic inputs.
- `neqsim-troubleshooting`: convergence recovery when each case is re-simulated.

## Calibration Workflow

1. Define objective and acceptance criteria.
2. Build a trusted tag map from plant tags to model variables.
3. Read, clean, and quality-filter plant data.
4. Detect steady-state windows for fitting.
5. Choose tunable model parameters with hard physical bounds.
6. Run bounded optimization and re-simulate the full NeqSim process each iteration.
7. Validate on holdout windows and publish fit diagnostics.

## Typical Tunable Parameters

| Equipment | Parameters to Tune | Keep Fixed Unless Needed |
|-----------|--------------------|--------------------------|
| Compressor | Polytropic efficiency, pressure ratio correction, anti-surge margin | Driver type, map family |
| Heat exchanger | UA correction factor, fouling factor | Duty sign convention, stream topology |
| Separator | Entrainment assumptions, liquid carry-over tuning factors | Phase model and EOS |
| Valve/choke | Cv multiplier, discharge coefficient | Control architecture |
| Pipeline | Roughness, overall heat transfer coefficient | Length and diameter |

## Data Reconciliation Quality Gates

Apply these gates before calibration:

- Unit harmonization complete (temperature, pressure, flow basis).
- At least 3 independent steady-state windows.
- Bad-quality tags removed or masked.
- Outlier handling documented.
- Sensor drift flags noted in notes and results.

## Python Pattern: Bounded Calibration Loop

```python
import numpy as np
from scipy.optimize import least_squares

# Example: tune compressor efficiency and HX UA multiplier
# x[0] -> compressor efficiency, x[1] -> UA multiplier
LOWER = np.array([0.60, 0.60])
UPPER = np.array([0.90, 1.40])
X0 = np.array([0.75, 1.00])

# Weights for [discharge pressure, discharge temperature, outlet temperature]
W = np.array([1.0, 1.0, 0.8])

def run_model_and_extract_outputs(process, x):
    # Example parameter writes using automation addresses
    auto = process.getAutomation()
    auto.setVariableValue("Main Compressor.polytropicEfficiency", float(x[0]), "")
    auto.setVariableValue("Main Cooler.UA_multiplier", float(x[1]), "")

    process.run()

    p_out = auto.getVariableValue("Main Compressor.outStream.pressure", "bara")
    t_out = auto.getVariableValue("Main Compressor.outStream.temperature", "C")
    t_cool = auto.getVariableValue("Main Cooler.outStream.temperature", "C")
    return np.array([p_out, t_out, t_cool])


def residuals(x, process, y_target):
    y_model = run_model_and_extract_outputs(process, x)
    return W * (y_model - y_target)


# y_target should come from a selected steady-state time window average
res = least_squares(
    fun=residuals,
    x0=X0,
    bounds=(LOWER, UPPER),
    args=(process, y_target),
    method="trf",
    max_nfev=150,
)

x_opt = res.x
print("Optimized parameters:", x_opt)
print("Cost:", res.cost)
```

## Objective Design Guidance

Use a weighted residual vector with consistent scaling.

A common objective is:

$$
J(\theta) = \sum_{i=1}^{N} w_i\left(\frac{y_i^{model}(\theta) - y_i^{meas}}{s_i}\right)^2
$$

Where:

- $\theta$ is the vector of tunable parameters.
- $w_i$ is the business/engineering importance weight.
- $s_i$ is a scaling term (for example nominal value or sensor sigma).

## Train/Validation Split Pattern

1. Train on at least two steady windows from different operating regions.
2. Validate on one holdout window not used in fitting.
3. Report both train and validation metrics.

Recommended minimum metrics:

- RMSE for each calibrated output tag.
- MAPE where denominator is safe and meaningful.
- Mean signed error (bias).
- Max absolute error.

## Common Failure Modes and Fixes

| Failure Mode | Likely Cause | Corrective Action |
|--------------|--------------|-------------------|
| Excellent train fit, poor validation | Overfit on narrow operating range | Add windows across wider throughput and pressure range |
| Non-physical fitted parameters | Missing bounds | Enforce hard bounds and pre-check with input validation |
| Optimization stalls | Flat objective or too many coupled variables | Reduce parameter count, stage the tuning in phases |
| Noisy residuals | Sensor spikes and transient periods | Increase filtering and steady-state screening |
| Unstable run during optimization | Recycle sensitivity | Start from converged base case and use troubleshooting recovery patterns |

## Reporting Pattern for results.json

Add a calibration block to the task `results.json`:

```json
{
  "calibration": {
    "calibrated_parameters": [
      {
        "name": "Main Compressor.polytropicEfficiency",
        "unit": "",
        "value": 0.781,
        "lower_bound": 0.60,
        "upper_bound": 0.90
      },
      {
        "name": "Main Cooler.UA_multiplier",
        "unit": "",
        "value": 1.12,
        "lower_bound": 0.60,
        "upper_bound": 1.40
      }
    ],
    "fit_metrics": {
      "train_rmse": {
        "compressor_outlet_pressure_bara": 0.84,
        "compressor_outlet_temperature_C": 1.6,
        "cooler_outlet_temperature_C": 0.9
      },
      "train_mape_pct": 1.8,
      "train_bias": {
        "compressor_outlet_pressure_bara": -0.22,
        "compressor_outlet_temperature_C": 0.35,
        "cooler_outlet_temperature_C": -0.10
      }
    },
    "validation_metrics": {
      "validation_mape_pct": 2.4,
      "max_abs_error": {
        "compressor_outlet_pressure_bara": 1.7,
        "compressor_outlet_temperature_C": 3.1,
        "cooler_outlet_temperature_C": 1.8
      }
    },
    "recommended_model_updates": [
      "Update base compressor efficiency from 0.75 to 0.78 for this operating period.",
      "Retain UA multiplier 1.12 and re-check after next exchanger cleaning campaign."
    ],
    "calibration_risks": [
      "Tag TT-402 intermittently flagged suspect quality during two windows.",
      "Calibration is validated for 70-90% throughput; extrapolation outside this range is not qualified."
    ]
  }
}
```

## Validation Checklist

- [ ] Parameter bounds are physically meaningful and documented.
- [ ] Calibration windows and validation windows are explicitly listed.
- [ ] Fit metrics are reported for both train and validation sets.
- [ ] Any excluded tags/windows have a documented reason.
- [ ] Final recommendations include applicability range and limitations.

## References

- ISO 5725 (accuracy and trueness concepts for measurement quality).
- IEC 61511 / ISA-95 practices for validated operational data usage.
- Internal NeqSim patterns in `neqsim-plant-data` and `neqsim-troubleshooting`.
