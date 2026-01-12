---
layout: default
title: "FieldDevelopmentWorkflow"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# FieldDevelopmentWorkflow

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`FieldDevelopmentWorkflow.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb).

---

# Field Development Workflow Tutorial

This notebook demonstrates NeqSim's integrated field development framework, showing how to use it at different fidelity levels - from quick screening studies to detailed FEED-level analysis.

## Overview

The `FieldDevelopmentWorkflow` class provides a unified interface for:
- **Screening studies** (±50% accuracy): Quick initial assessment
- **Conceptual studies** (±30% accuracy): EOS-based PVT, well models
- **Detailed studies** (±20% accuracy): Full process simulation, Monte Carlo economics

## Prerequisites

Make sure you have jpype installed with neqsim:

```python
import jpype
import jpype.imports
from jpype.types import *

# Start JVM with NeqSim
if not jpype.isJVMStarted():
    jpype.startJVM(classpath=['path/to/neqsim.jar'])

# Import NeqSim classes
from neqsim.thermo.system import SystemSrkEos, SystemSrkCPAstatoil
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.reservoir import WellSystem, SimpleReservoir
from neqsim.process.fielddevelopment.workflow import FieldDevelopmentWorkflow
from neqsim.process.fielddevelopment.economics import CashFlowEngine, NorwegianTaxModel
from neqsim.process.fielddevelopment.screening import FlowAssuranceResult
```

## 1. Screening Level Study

The screening level uses correlations and empirical models for quick assessment. This is suitable for:
- Initial opportunity screening
- Portfolio ranking
- Go/no-go decisions

### Quick Gas Tieback Example

```python
# Create a quick gas tieback screening study
workflow = FieldDevelopmentWorkflow.quickGasTieback(
    "Snorre_Satellite",
    50.0,    # reserves in GSm3
    120.0,   # distance to host in km
    350.0,   # water depth in m
    180.0,   # reservoir pressure in bara
    90.0     # reservoir temperature in °C
)

# Run the screening
result = workflow.run()

# Display results
print(result.getSummary())
```

```python
# Check flow assurance screening results
fa_result = result.getFlowAssuranceResult()
print("Flow Assurance Screening:")
print(f"  Hydrate Risk: {fa_result.hydrateRisk}")
print(f"  Wax Risk: {fa_result.waxRisk}")
print(f"  Slugging Risk: {fa_result.sluggingRisk}")
print(f"  Recommended MEG Rate: {fa_result.recommendedMegRate:.1f} kg/hr")
```

```python
# View production profile
print(result.getProductionTable())
```

```python
# View cash flow
print(result.getCashFlowTable())
```

### Quick Oil Development Example

```python
# Create an oil field screening study
oil_workflow = FieldDevelopmentWorkflow.quickOilDevelopment(
    "Johan_Sverdrup_Satellite",
    150.0,   # reserves in MSm3 oil
    5,       # number of wells
    120.0,   # water depth in m
    200.0,   # reservoir pressure in bara
    85.0     # reservoir temperature in °C
)

oil_result = oil_workflow.run()
print(oil_result.getSummary())
```

## 2. Conceptual Level Study

The conceptual level adds:
- EOS-based PVT modeling
- Well performance models
- More detailed cost estimation

### Setting Up an EOS Fluid

```python
# Create a detailed PVT fluid model
fluid = SystemSrkEos(85.0 + 273.15, 200.0)  # T in K, P in bara

# Add components (typical North Sea oil composition)
fluid.addComponent("nitrogen", 0.5)
fluid.addComponent("CO2", 1.2)
fluid.addComponent("methane", 35.0)
fluid.addComponent("ethane", 8.5)
fluid.addComponent("propane", 6.2)
fluid.addComponent("i-butane", 1.5)
fluid.addComponent("n-butane", 3.2)
fluid.addComponent("i-pentane", 1.8)
fluid.addComponent("n-pentane", 2.1)
fluid.addComponent("n-hexane", 3.5)
fluid.addComponent("C7", 15.0)  # C7+ fraction
fluid.addComponent("C10", 12.0)
fluid.addComponent("C15", 6.5)
fluid.addComponent("C20", 3.0)

fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

print(f"Fluid created with {fluid.getNumberOfComponents()} components")
```

```python
# Create conceptual workflow with EOS fluid
workflow_conceptual = FieldDevelopmentWorkflow("Barents_Development")
workflow_conceptual.setFidelityLevel(FieldDevelopmentWorkflow.FidelityLevel.CONCEPTUAL)
workflow_conceptual.setFluid(fluid)

# Set field parameters
workflow_conceptual.setReserves(200.0, "MSm3")  # Oil reserves
workflow_conceptual.setGasReserves(50.0, "GSm3")  # Associated gas
workflow_conceptual.setWaterDepth(380.0)
workflow_conceptual.setNumberOfWells(8)
workflow_conceptual.setTiebackDistance(0.0)  # Standalone facility

# Run conceptual study
conceptual_result = workflow_conceptual.run()
print(conceptual_result.getSummary())
```

## 3. Concept Comparison

Compare multiple development concepts side by side:

```python
# Define multiple concepts to compare
concepts = {
    "Subsea_Tieback": FieldDevelopmentWorkflow.quickGasTieback(
        "Concept_A", 50.0, 80.0, 350.0, 180.0, 90.0
    ),
    "FPSO_Standalone": FieldDevelopmentWorkflow.quickOilDevelopment(
        "Concept_B", 150.0, 6, 350.0, 200.0, 85.0
    ),
}

# Run all concepts
results = {}
for name, wf in concepts.items():
    results[name] = wf.run()

# Generate comparison report
comparison = FieldDevelopmentWorkflow.generateComparisonReport(results)
print(comparison)
```

## 4. Detailed Level with Monte Carlo

The detailed level adds:
- Full process simulation
- Monte Carlo economics
- Sensitivity analysis

```python
# Create detailed workflow
workflow_detailed = FieldDevelopmentWorkflow("North_Sea_Oil")
workflow_detailed.setFidelityLevel(FieldDevelopmentWorkflow.FidelityLevel.DETAILED)
workflow_detailed.setFluid(fluid)

# Set parameters
workflow_detailed.setReserves(250.0, "MSm3")
workflow_detailed.setWaterDepth(120.0)
workflow_detailed.setNumberOfWells(12)

# Configure Monte Carlo settings
workflow_detailed.setMonteCarloIterations(1000)

# Run detailed study (may take longer due to Monte Carlo)
detailed_result = workflow_detailed.run()

print("Economic Results with Uncertainty:")
print(f"  NPV P10: ${detailed_result.getNpvP10() / 1e9:.2f} billion")
print(f"  NPV P50: ${detailed_result.getNpvP50() / 1e9:.2f} billion")
print(f"  NPV P90: ${detailed_result.getNpvP90() / 1e9:.2f} billion")
```

## 5. Viability Assessment

```python
# Check project viability
print(f"Project Viable: {detailed_result.isViable()}")
print(f"Viable with 80% confidence: {detailed_result.isViableWithConfidence(0.8)}")
print(f"IRR: {detailed_result.getIrr() * 100:.1f}%")
print(f"Payback: {detailed_result.getPaybackYears():.1f} years")
```

## Summary

The FieldDevelopmentWorkflow provides:

| Level | Accuracy | Use Case | Time |
|-------|----------|----------|------|
| Screening | ±50% | Portfolio screening | Seconds |
| Conceptual | ±30% | Concept selection | Minutes |
| Detailed | ±20% | FEED basis | Hours |

Key integration points:
- PVT: `SystemSrkEos`, `SystemSrkCPAstatoil`
- Reservoir: `SimpleReservoir`, `WellSystem`  
- Process: `ProcessSystem`, unit operations
- Economics: `CashFlowEngine`, tax models

