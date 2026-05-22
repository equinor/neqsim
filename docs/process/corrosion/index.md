---
title: "Corrosion Analysis Module"
description: "NORSOK M-506 CO2 corrosion rate prediction and NORSOK M-001 material selection for pipelines and process piping in NeqSim. Includes integration with pipeline mechanical design."
---

# Corrosion Analysis Module

The `neqsim.process.corrosion` package provides industry-standard corrosion assessment tools for carbon steel and CRA material selection in CO2/H2S environments.

## Classes

| Class | Standard | Purpose |
|-------|----------|---------|
| [`MaterialsReviewEngine`](materials_review) | NORSOK M-001/M-506, ISO 15156, API 581/583, API 941 | Process-wide materials review from process simulation and STID/material registers |
| [`NorsokM506CorrosionRate`](norsok_m506_corrosion_rate) | NORSOK M-506 | CO2 corrosion rate prediction (mm/yr) |
| [`NorsokM001MaterialSelection`](norsok_m001_material_selection) | NORSOK M-001 | Material grade recommendation and corrosion allowance |
| [`SourServiceAssessment`](sour_service_assessment) | ISO 15156 / NACE MR0175 | Sour region classification, SSC/HIC/SOHIC risk |
| [`CO2CorrosionMaterialSelection`](co2_corrosion_material_selection) | NORSOK M-001 / EFC 17 | CRA selection hierarchy based on CO2 corrosion rate |
| [`ChlorideSCCAssessment`](chloride_scc_assessment) | NORSOK M-001 / MTI 15 | Chloride stress corrosion cracking risk for stainless steels |
| [`OxygenCorrosionAssessment`](oxygen_corrosion_assessment) | NORSOK M-001 / NACE SP0499 | Dissolved oxygen corrosion and pitting assessment |
| [`DensePhaseCO2Corrosion`](dense_phase_co2_corrosion) | DNV-RP-J202 / ISO 27913 | CCS pipeline corrosion — impurity limits, free water risk |
| [`AmmoniaCompatibility`](ammonia_compatibility) | CGA G-2.1 / ASME B31.3 | Ammonia service material compatibility and SCC assessment |
| [`HydrogenMaterialAssessment`](hydrogen_material_assessment) | API 941 / ASME B31.12 | Hydrogen embrittlement and HTHA assessment |
| [`NelsonCurveAssessment`](nelson_curve_assessment) | API 941 8th Ed | High-temperature hydrogen attack (HTHA) Nelson curve check |

## Integration

The corrosion module integrates with the pipeline mechanical design system:

| Integration Point | Description |
|--------------------|-------------|
| [`PipelineMechanicalDesign`](../pipeline_mechanical_design) | Orchestrates corrosion analysis from stream data |
| [`Pipeline`](../equipment/pipeline_simulation) | Convenience methods for corrosion analysis on pipeline equipment |

See the [Pipeline Corrosion Integration Guide](pipeline_corrosion_integration) for full workflow.

For asset- or project-level review packages, see the [Process-Wide Materials Review](materials_review), which combines process conditions, STID/material-register data, degradation mechanisms, CUI screening, remaining-life checks, and MCP integration.

## Quick Start

### Standalone Corrosion Rate

```java
import neqsim.process.corrosion.NorsokM506CorrosionRate;

NorsokM506CorrosionRate model = new NorsokM506CorrosionRate();
model.setTemperatureCelsius(60.0);
model.setTotalPressureBara(100.0);
model.setCO2MoleFraction(0.02);
model.setFlowVelocityMs(3.0);
model.setPipeDiameterM(0.254);
model.calculate();

System.out.println("Corrosion rate: " + model.getCorrectedCorrosionRate() + " mm/yr");
System.out.println("Severity: " + model.getCorrosionSeverity());
System.out.println("pH: " + model.getCalculatedPH());
```

### Material Selection

```java
import neqsim.process.corrosion.NorsokM001MaterialSelection;

NorsokM001MaterialSelection selector = new NorsokM001MaterialSelection();
selector.setCO2CorrosionRateMmyr(2.5);
selector.setH2SPartialPressureBar(0.05);
selector.setChlorideConcentrationMgL(50000);
selector.setDesignTemperatureC(80.0);
selector.setDesignLifeYears(25);
selector.evaluate();

System.out.println("Material: " + selector.getRecommendedMaterial());
System.out.println("Service: " + selector.getServiceCategory());
System.out.println("CA: " + selector.getRecommendedCorrosionAllowanceMm() + " mm");
```

### Integrated Pipeline Analysis

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

// After running the pipeline simulation
pipe.setDesignLifeYears(25);
pipe.setInhibitorEfficiency(0.0);
pipe.runCorrosionAnalysis();

System.out.println("Rate: " + pipe.getCorrosionRate() + " mm/yr");
System.out.println("Material: " + pipe.getRecommendedMaterial());
System.out.println("CA: " + pipe.getRecommendedCorrosionAllowanceMm() + " mm");
```

### Python (Jupyter)

```python
from neqsim import jneqsim

NorsokM506 = jneqsim.process.corrosion.NorsokM506CorrosionRate

model = NorsokM506()
model.setTemperatureCelsius(60.0)
model.setTotalPressureBara(100.0)
model.setCO2MoleFraction(0.02)
model.setFlowVelocityMs(3.0)
model.setPipeDiameterM(0.254)
model.calculate()

print(f"Corrosion rate: {model.getCorrectedCorrosionRate():.2f} mm/yr")
print(f"Severity: {model.getCorrosionSeverity()}")
```

## Standards Coverage

| Standard | Scope | Implementation |
|----------|-------|----------------|
| NORSOK M-506 (2005/2017) | CO2 corrosion rate model | `NorsokM506CorrosionRate` |
| NORSOK M-001 | Material selection guidelines | `NorsokM001MaterialSelection`, `CO2CorrosionMaterialSelection`, `ChlorideSCCAssessment`, `OxygenCorrosionAssessment` |
| NACE MR0175 / ISO 15156 | Sour service classification | `SourServiceAssessment`, `NorsokM001MaterialSelection` |
| ISO 15156-2 | Carbon steel in sour service | `SourServiceAssessment` |
| ISO 15156-3 | CRA in sour service | `SourServiceAssessment`, `CO2CorrosionMaterialSelection` |
| EFC 16/17 | CO2/H2S corrosion guidelines | `SourServiceAssessment`, `CO2CorrosionMaterialSelection` |
| MTI Publication 15 | Chloride SCC guidelines | `ChlorideSCCAssessment` |
| NACE SP0499 | Corrosion in water injection | `OxygenCorrosionAssessment` |
| DNV-RP-J202 | CCS pipeline corrosion | `DensePhaseCO2Corrosion` |
| ISO 27913 | CO2 transport by pipeline | `DensePhaseCO2Corrosion` |
| CGA G-2.1 | Ammonia piping/equipment | `AmmoniaCompatibility` |
| ASME B31.3 / B31.12 | Process piping / H2 piping | `AmmoniaCompatibility`, `HydrogenMaterialAssessment` |
| API 941 | Nelson curves / HTHA | `HydrogenMaterialAssessment`, `NelsonCurveAssessment` |
| API 581 / API 583 | CUI and risk-based inspection screening | `MaterialsReviewEngine`, `CUIRiskAssessment` |

## Related Documentation

- [Flow Assurance Screening Tools](../../pvtsimulation/flowassurance/flow_assurance_screening_tools) — De Waard-Milliams (simpler screening model), cooldown, scale, wax
- [Process-Wide Materials Review](materials_review) — STID-backed material, degradation, integrity, and lifetime review
- [Pipeline Mechanical Design](../pipeline_mechanical_design) — Wall thickness, stress analysis, cost estimation
- [Erosion Prediction](../../pvtsimulation/flowassurance/erosion_prediction) — API RP 14E and DNV RP O501
