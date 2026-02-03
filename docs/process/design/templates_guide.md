---
title: Process Design Templates
description: The `neqsim.process.design.template` package provides pre-built process templates for common industrial applications. These templates simplify the creation of standard process configurations while all...
---

# Process Design Templates

The `neqsim.process.design.template` package provides pre-built process templates for common industrial applications. These templates simplify the creation of standard process configurations while allowing customization through a parameter-based API.

## Table of Contents

- [Overview](#overview)
- [Available Templates](#available-templates)
- [Template Interface](#template-interface)
- [Gas Compression Template](#gas-compression-template)
- [Dehydration Template](#dehydration-template)
- [CO2 Capture Template](#co2-capture-template)
- [Three-Stage Separation Template](#three-stage-separation-template)
- [Creating Custom Templates](#creating-custom-templates)
- [Best Practices](#best-practices)

---

## Overview

**Location:** `neqsim.process.design.template`

**Purpose:**
- Rapid creation of standard process configurations
- Consistent design practices across projects
- Parameter-driven customization
- Automatic equipment sizing based on design codes

---

## Available Templates

| Template | Description | Key Equipment |
|----------|-------------|---------------|
| `GasCompressionTemplate` | Multi-stage gas compression | Compressors, Coolers, KO Drums |
| `DehydrationTemplate` | TEG gas dehydration | Absorber, Regenerator, HX |
| `CO2CaptureTemplate` | Amine-based CO2 capture | Absorber, Stripper, HX |
| `ThreeStageSeparationTemplate` | Oil/gas separation train | HP/MP/LP Separators |

---

## Template Interface

All templates implement `ProcessTemplate`:

```java
public interface ProcessTemplate {
    /**
     * Creates the process system from design basis.
     */
    ProcessSystem create(ProcessBasis basis);
    
    /**
     * Checks if template is applicable for given fluid.
     */
    boolean isApplicable(SystemInterface fluid);
    
    /**
     * Returns required equipment types.
     */
    String[] getRequiredEquipmentTypes();
    
    /**
     * Returns expected outputs.
     */
    String[] getExpectedOutputs();
    
    /**
     * Returns template name.
     */
    String getName();
    
    /**
     * Returns template description.
     */
    String getDescription();
}
```

---

## Gas Compression Template

Multi-stage gas compression with interstage cooling and liquid knockout.

### Features

- Automatic stage calculation based on pressure ratio
- Interstage coolers with configurable outlet temperature
- Knockout drums for liquid removal between stages
- Support for wet gas applications

### Usage

```java
import neqsim.process.design.ProcessBasis;
import neqsim.process.design.template.GasCompressionTemplate;
import neqsim.thermo.system.SystemSrkEos;

// Create feed gas
SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 5.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.08);
gas.addComponent("propane", 0.04);
gas.addComponent("n-butane", 0.02);
gas.addComponent("water", 0.01);
gas.setMixingRule("classic");

// Configure design basis
ProcessBasis basis = new ProcessBasis();
basis.setFeedFluid(gas);
basis.setFeedPressure(5.0);           // bara
basis.setFeedTemperature(303.15);      // K
basis.setFeedFlowRate(50000.0);        // kg/hr

// Set compression parameters
basis.setParameter("dischargePressure", 100.0);     // bara
basis.setParameter("interstageTemperature", 40.0);  // °C
basis.setParameter("polytropicEfficiency", 0.78);

// Create and run
GasCompressionTemplate template = new GasCompressionTemplate();
ProcessSystem compression = template.create(basis);
compression.run();

// Get results
Compressor stage1 = (Compressor) compression.getUnit("Stage 1 Compressor");
System.out.println("Stage 1 power: " + stage1.getPower() / 1000.0 + " kW");
System.out.println("Stage 1 discharge temp: " + 
    (stage1.getOutletStream().getTemperature() - 273.15) + " °C");
```

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `dischargePressure` | double | 100.0 | Final discharge pressure (bara) |
| `interstageTemperature` | double | 40.0 | Interstage cooler outlet (°C) |
| `polytropicEfficiency` | double | 0.75 | Compressor polytropic efficiency |
| `numberOfStages` | int | auto | Number of stages (auto if 0) |
| `includeAftercooler` | double | 1.0 | Include final aftercooler (>0) |

### Stage Calculation

The template automatically calculates optimal stages:
- Target compression ratio per stage: 3.0
- Maximum compression ratio per stage: 4.5
- Minimum stages: 1

```java
// Manual stage specification
basis.setParameter("numberOfStages", 4);
```

---

## Dehydration Template

TEG (Triethylene Glycol) gas dehydration system.

### Features

- TEG absorber with configurable stages
- Rich glycol flash drum for hydrocarbon recovery
- Glycol-glycol heat exchanger for heat recovery
- Regeneration still with reboiler
- Lean glycol pump and cooler

### Usage

```java
import neqsim.process.design.template.DehydrationTemplate;

// Create wet gas
SystemInterface wetGas = new SystemSrkCPAstatoil(273.15 + 30.0, 70.0);
wetGas.addComponent("methane", 0.80);
wetGas.addComponent("ethane", 0.10);
wetGas.addComponent("propane", 0.05);
wetGas.addComponent("water", 0.05);
wetGas.setMixingRule(10);

// Configure
ProcessBasis basis = new ProcessBasis();
basis.setFeedFluid(wetGas);
basis.setFeedPressure(70.0);
basis.setFeedFlowRate(100000.0);

basis.setParameter("numberOfStages", 4);
basis.setParameter("reboilerTemperature", 204.0);  // °C
basis.setParameter("tegCirculationRate", 5.0);      // m3/hr

// Create
DehydrationTemplate template = new DehydrationTemplate();
ProcessSystem dehy = template.create(basis);
dehy.run();

// Check dry gas water content
Stream dryGas = (Stream) dehy.getUnit("TEG Absorber").getGasOutStream();
double waterContent = calculateWaterContent(dryGas);
System.out.println("Dry gas water content: " + waterContent + " lb/MMscf");
```

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `numberOfStages` | int | 4 | Absorber theoretical stages |
| `reboilerTemperature` | double | 204.0 | Reboiler temperature (°C) |
| `leanGlycolTemperature` | double | 45.0 | Lean glycol temperature (°C) |
| `tegCirculationRate` | double | auto | TEG rate (m³/hr) |

### Water Content Targets

| Application | Target Water Content |
|-------------|---------------------|
| Pipeline specification | 7 lb/MMscf |
| Cryogenic processing | < 1 ppm |
| LNG feed | < 0.1 ppm |

### Utility Methods

```java
// Calculate TEG circulation rate
double tegRate = DehydrationTemplate.calculateTEGRate(
    10.0,    // Gas flow (MMscfd)
    100.0,   // Inlet water (lb/MMscf)
    7.0      // Target water (lb/MMscf)
);

// Estimate equilibrium water content
double eqWater = DehydrationTemplate.estimateEquilibriumWater(
    0.995,   // TEG purity
    40.0,    // Temperature (°C)
    70.0     // Pressure (bara)
);
```

---

## CO2 Capture Template

Amine-based CO2 capture for flue gas treatment or natural gas sweetening.

### Features

- Support for multiple amine types (MEA, DEA, MDEA, MDEA+PZ)
- Amine absorber with configurable stages
- Rich amine flash drum
- Lean-rich heat exchanger
- Regenerator with reboiler
- Automatic amine circulation rate estimation

### Amine Types

| Type | Concentration | Reboiler Temp | Application |
|------|--------------|---------------|-------------|
| MEA | 15-30 wt% | 118°C | Fast kinetics, high removal |
| DEA | 25-35 wt% | 115°C | Selective H2S removal |
| MDEA | 35-50 wt% | 120°C | Lower energy, selective |
| MDEA+PZ | 35-45 wt% | 118°C | Enhanced kinetics |

### Usage

```java
import neqsim.process.design.template.CO2CaptureTemplate;
import neqsim.process.design.template.CO2CaptureTemplate.AmineType;

// Create flue gas
SystemInterface flueGas = new SystemSrkCPAstatoil(273.15 + 40.0, 1.1);
flueGas.addComponent("nitrogen", 0.73);
flueGas.addComponent("CO2", 0.12);
flueGas.addComponent("water", 0.10);
flueGas.addComponent("oxygen", 0.05);
flueGas.setMixingRule(10);

// Configure
ProcessBasis basis = new ProcessBasis();
basis.setFeedFluid(flueGas);
basis.setFeedPressure(1.1);
basis.setFeedFlowRate(500000.0);

basis.setParameterString("amineType", "MDEA");
basis.setParameter("amineConcentration", 0.45);
basis.setParameter("co2RemovalTarget", 0.90);
basis.setParameter("absorberStages", 20);
basis.setParameter("regeneratorStages", 12);

// Create with specific amine type
CO2CaptureTemplate template = new CO2CaptureTemplate(AmineType.MDEA);
ProcessSystem capture = template.create(basis);
capture.run();

// Calculate specific reboiler duty
double specificDuty = CO2CaptureTemplate.calculateSpecificReboilerDuty(
    AmineType.MDEA,
    0.50,   // Rich loading
    0.20    // Lean loading
);
System.out.println("Specific duty: " + specificDuty + " GJ/ton CO2");
```

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `amineType` | String | "MDEA" | Amine type (MEA/DEA/MDEA/MDEA+PZ) |
| `amineConcentration` | double | varies | Amine mass fraction |
| `co2RemovalTarget` | double | 0.90 | CO2 removal fraction |
| `absorberStages` | int | 20 | Absorber theoretical stages |
| `regeneratorStages` | int | 12 | Regenerator theoretical stages |
| `reboilerTemperature` | double | varies | Reboiler temperature (°C) |
| `leanAmineTemperature` | double | 40.0 | Lean amine to absorber (°C) |

### Performance Estimation

```java
// Estimate amine losses
double amineLoss = CO2CaptureTemplate.estimateAmineLoss(
    AmineType.MDEA,
    100.0   // Gas flow (MMscfd)
);
System.out.println("Amine loss: " + amineLoss + " kg/MMscf");
```

---

## Three-Stage Separation Template

Standard three-stage oil/gas/water separation train.

### Features

- HP, MP, LP separators
- Configurable pressure levels
- Water knockout at each stage
- Gas compression options

### Usage

```java
import neqsim.process.design.template.ThreeStageSeparationTemplate;

// Create reservoir fluid
SystemInterface oil = new SystemSrkEos(273.15 + 80.0, 150.0);
oil.addComponent("methane", 0.30);
oil.addComponent("ethane", 0.10);
oil.addComponent("propane", 0.08);
oil.addComponent("nC10", 0.40);
oil.addComponent("water", 0.12);
oil.setMixingRule("classic");

// Configure
ProcessBasis basis = new ProcessBasis();
basis.setFeedFluid(oil);
basis.setFeedPressure(150.0);
basis.setFeedFlowRate(200000.0);

basis.setParameter("hpPressure", 50.0);
basis.setParameter("mpPressure", 15.0);
basis.setParameter("lpPressure", 2.0);

// Create
ThreeStageSeparationTemplate template = new ThreeStageSeparationTemplate();
ProcessSystem separation = template.create(basis);
separation.run();
```

---

## Creating Custom Templates

### Step 1: Implement ProcessTemplate

```java
public class CustomProcessTemplate implements ProcessTemplate {
    
    @Override
    public ProcessSystem create(ProcessBasis basis) {
        ProcessSystem process = new ProcessSystem();
        
        // Get parameters
        SystemInterface feed = basis.getFeedFluid();
        double pressure = basis.getFeedPressure();
        
        // Build process
        Stream feedStream = new Stream("Feed", feed);
        feedStream.setFlowRate(basis.getFeedFlowRate(), "kg/hr");
        process.add(feedStream);
        
        // Add equipment...
        
        return process;
    }
    
    @Override
    public boolean isApplicable(SystemInterface fluid) {
        // Check if fluid is suitable
        return fluid.hasPhaseType("gas");
    }
    
    @Override
    public String[] getRequiredEquipmentTypes() {
        return new String[]{"Separator", "Compressor"};
    }
    
    @Override
    public String[] getExpectedOutputs() {
        return new String[]{
            "Product Gas - Main product",
            "Condensate - Liquid byproduct"
        };
    }
    
    @Override
    public String getName() {
        return "Custom Process";
    }
    
    @Override
    public String getDescription() {
        return "Custom process template for specific application.";
    }
}
```

### Step 2: Use ProcessBasis for Parameters

```java
// In create() method:
double customParam = basis.getParameter("customParameter", 100.0);
String mode = basis.getParameterString("operationMode", "normal");
```

---

## Best Practices

### 1. Validate Input Fluids

```java
@Override
public ProcessSystem create(ProcessBasis basis) {
    SystemInterface feed = basis.getFeedFluid();
    if (feed == null) {
        throw new IllegalArgumentException(
            "ProcessBasis must have a feed fluid defined");
    }
    
    if (!isApplicable(feed)) {
        throw new IllegalArgumentException(
            "Fluid is not suitable for this template");
    }
    // ...
}
```

### 2. Provide Sensible Defaults

```java
double pressure = basis.getFeedPressure();
if (Double.isNaN(pressure) || pressure <= 0) {
    pressure = 50.0;  // Default value
}
```

### 3. Document Parameters

Include comprehensive Javadoc with parameter tables:

```java
/**
 * @param basis Process basis with parameters:
 *   <ul>
 *   <li>feedPressure - Feed pressure (bara)</li>
 *   <li>customParam - Custom parameter (default: 100)</li>
 *   </ul>
 */
```

### 4. Enable Customization

Allow users to override automatic calculations:

```java
int stages = (int) basis.getParameter("numberOfStages", 0);
if (stages <= 0) {
    stages = calculateOptimalStages(conditions);
}
```

---

## See Also

- [Process Design Guide](../process_design_guide)
- [Design Framework](../DESIGN_FRAMEWORK)
- [Optimizer Guide](../../util/optimizer_guide)
- [Equipment Documentation](../equipment/README)
