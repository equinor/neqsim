---
layout: default
title: "Transparent field-development screening"
description: "Executable NeqSim tutorial for unit-safe gas-production profiles, after-tax cash flow, and bounded sensitivities."
parent: Examples
nav_order: 1
---

# Transparent field-development screening with NeqSim

> **Notebook:** This page mirrors
> [`FieldDevelopmentWorkflow.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb)
> or [open it in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb).

This tutorial builds an auditable gas-production forecast and after-tax cash flow from current NeqSim APIs. It
deliberately uses the lower-level production-profile and economics classes so every unit conversion and assumption is
visible.

> **Engineering boundary:** this is a deterministic screening example, not a reserves estimate, concept approval,
> FEED model, or investment recommendation. Replace the synthetic rates, costs, prices, fiscal basis, and decline
> assumptions with traceable project data and qualified engineering models.

## 1. Runtime setup

The setup installs the current public `neqsim` package only when it is missing. Restart the runtime after changing the
installed NeqSim version.

```python
import importlib.util
import subprocess
import sys

if importlib.util.find_spec("neqsim") is None:
    subprocess.check_call(
        [sys.executable, "-m", "pip", "install", "-q", "neqsim"]
    )
```

```python
import math

import jpype
import matplotlib.pyplot as plt
from neqsim import jneqsim  # Starts the JVM and exposes the packaged NeqSim JAR.

ProductionProfileGenerator = jpype.JClass(
    "neqsim.process.fielddevelopment.economics.ProductionProfileGenerator"
)
DeclineType = jpype.JClass(
    "neqsim.process.fielddevelopment.economics."
    "ProductionProfileGenerator$DeclineType"
)
CashFlowEngine = jpype.JClass(
    "neqsim.process.fielddevelopment.economics.CashFlowEngine"
)

DAYS_PER_YEAR = 365.25
```

## 2. Define the screening basis

The example represents a synthetic four-well gas development. Rates are standard cubic metres per day; yearly
profile values are standard cubic metres per year. Monetary inputs and outputs are million US dollars unless the API
label states otherwise.

| Assumption | Value |
|---|---:|
| Plateau gas rate | 8.0 MSm³/d |
| Forecast start / life | 2028 / 20 years |
| Ramp / plateau | 1 / 4 years |
| Exponential decline | 12%/year |
| CAPEX | 1,384.5 MUSD over 2026–2027 |
| Fixed OPEX proxy | 4% of CAPEX/year |
| Gas price / tariff | 0.30 / 0.02 USD/Sm³ |
| Discount rate | 8% |
| Fiscal model | `CashFlowEngine("NO")` |

The `ProductionProfileGenerator.generateFullProfile` input is a **daily rate**, but each returned map value is an
**annual volume**. Passing the annual value directly to `addAnnualProduction` avoids a second, erroneous
multiplication by days per year.

```python
def build_gas_case(
    name,
    plateau_rate_msm3_per_day,
    total_capex_musd,
    gas_price_usd_per_sm3=0.30,
):
    """Run one transparent deterministic gas-screening case."""
    peak_rate_sm3_per_day = plateau_rate_msm3_per_day * 1.0e6
    profile = ProductionProfileGenerator().generateFullProfile(
        peak_rate_sm3_per_day,
        1,
        4,
        0.12,
        DeclineType.EXPONENTIAL,
        2028,
        20,
    )
    annual_profile = {
        int(entry.getKey()): float(entry.getValue())
        for entry in profile.entrySet()
    }

    engine = CashFlowEngine("NO")
    engine.setCapex(0.77 * total_capex_musd, 2026)
    engine.addCapex(0.23 * total_capex_musd, 2027)
    engine.setOpexPercentOfCapex(0.04)
    engine.setGasPrice(gas_price_usd_per_sm3)
    engine.setGasTariff(0.02)

    for year, annual_gas_sm3 in annual_profile.items():
        engine.addAnnualProduction(year, 0.0, annual_gas_sm3, 0.0)

    result = engine.calculate(0.08)
    return {
        "name": name,
        "annual_profile": annual_profile,
        "engine": engine,
        "result": result,
    }
```

## 3. Generate and audit the production forecast

```python
base_case = build_gas_case("Base case", 8.0, 1384.5)
base_profile = base_case["annual_profile"]

first_year = min(base_profile)
first_daily_rate_msm3 = (
    base_profile[first_year] / DAYS_PER_YEAR / 1.0e6
)
cumulative_gsm3 = sum(base_profile.values()) / 1.0e9

print(f"First-year average rate: {first_daily_rate_msm3:.6f} MSm³/d")
print(f"Twenty-year cumulative gas: {cumulative_gsm3:.6f} GSm³")

assert math.isclose(first_daily_rate_msm3, 8.0, rel_tol=0.0, abs_tol=1e-12)
assert cumulative_gsm3 > 0.0
```

```python
years = list(base_profile)
daily_rates = [
    base_profile[year] / DAYS_PER_YEAR / 1.0e6 for year in years
]

fig, ax = plt.subplots(figsize=(8, 4.2))
ax.plot(years, daily_rates, marker="o", linewidth=2)
ax.set(
    title="Synthetic gas-production screening profile",
    xlabel="Calendar year",
    ylabel="Average gas rate (MSm³/d)",
)
ax.grid(alpha=0.3)
fig.tight_layout()
plt.show()
```

## 4. Inspect the after-tax screening economics

The model is intentionally deterministic. Its NPV, IRR, payback, and break-even price depend entirely on the synthetic
assumptions above and the selected `NO` fiscal implementation.

```python
cash_result = base_case["result"]
break_even_gas_price = base_case["engine"].calculateBreakevenGasPrice(0.08)

print(cash_result.getSummary())
print(f"Break-even gas price: {break_even_gas_price:.6f} USD/Sm³")

assert math.isfinite(cash_result.getNpv())
assert math.isfinite(cash_result.getIrr())
assert cash_result.getTotalCapex() > 0.0
assert 0.0 < break_even_gas_price < 2.0
```

## 5. Compare bounded sensitivities

A small deterministic sensitivity is more transparent than attaching unsupported P10/P50/P90 labels. Probabilistic
labels require distributions, correlations, sampling evidence, and a documented percentile convention.

```python
sensitivity_cases = [
    build_gas_case("Lower rate", 6.0, 1384.5),
    base_case,
    build_gas_case("Higher CAPEX", 8.0, 1700.0),
]

print("| Case | NPV (MUSD) | IRR (%) | Payback (years) |")
print("|---|---:|---:|---:|")
for case in sensitivity_cases:
    result = case["result"]
    print(
        f"| {case['name']} | {result.getNpv():.1f} | "
        f"{100.0 * result.getIrr():.1f} | {result.getPaybackYears():.1f} |"
    )

assert sensitivity_cases[0]["result"].getNpv() < cash_result.getNpv()
assert sensitivity_cases[2]["result"].getNpv() < cash_result.getNpv()
```

## 6. Interpretation and next fidelity step

This notebook verifies the mechanics of an annual production profile and cash-flow screen. It does **not** model
GIIP/STOIIP depletion, well deliverability, host capacity, multiphase hydraulics, product specifications, flow
assurance, schedule uncertainty, or process equipment.

Before concept selection:

1. Cap the forecast with a traceable recoverable-resource basis.
2. Replace the synthetic profile with a reservoir/well/network forecast.
3. Couple each concept to an independent `ProcessSystem` or `ProcessModel`.
4. Apply host and equipment limits before advancing reservoir state and economics.
5. Add documented uncertainty distributions and correlations before reporting percentiles.
6. Obtain discipline review of the technical, fiscal, cost, schedule, and safety basis.

For the physically coupled main-branch workflow, see
[Integrated Field Lifecycle Simulation](../fielddevelopment/FIELD_LIFECYCLE_SIMULATION.md). That API may be newer than
the latest public Python package, so use a repository build when reproducing main-branch examples.
