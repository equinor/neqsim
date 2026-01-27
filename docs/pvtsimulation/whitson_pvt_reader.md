# Whitson PVT Parameter File Reader

The `WhitsonPVTReader` class enables NeqSim to read PVT parameter files exported from Whitson+ and similar PVT software, creating fully configured fluid systems with accurate thermodynamic properties.

## Overview

When working with PVT characterization software like Whitson+, you can export equation of state parameters and component properties to a tab-separated file format. The `WhitsonPVTReader` parses these files and creates NeqSim `SystemInterface` objects with:

- Correct EOS type (Peng-Robinson, SRK, or PR78)
- **LBC viscosity model with custom parameters automatically applied**
- C7+ gamma distribution parameters
- Custom Omega A and Omega B values
- Full component properties (MW, Tc, Pc, ω, volume shift, etc.)
- Binary interaction parameters (complete matrix)
- Volume correction enabled by default

## File Format

The Whitson PVT parameter file is a tab-separated text file with three main sections:

### 1. Parameters Section

Key-value pairs defining global EOS and model parameters:

```
Parameter	Value
Name	Predictive_EOS_Parameters
EOS Type	PR
LBC P0	0.1023
LBC P1	0.023364
LBC P2	0.058533
LBC P3	-0.037734245
LBC P4	0.00839916
LBC F0	0.1
C7+ Gamma Shape	0.677652
C7+ Gamma Bound	94.9981
Omega A, ΩA	0.457236
Omega B, ΩB	0.0777961
```

### 2. Component Table

Component properties with a header row and units row:

```
Component	MW	Pc	Tc	AF, ω	Volume Shift, s	ZcVisc	VcVisc	Vc	Zc	Pchor	SG	Tb	LMW
-	-	bara	C	-	-	-	m3/kmol	m3/kmol	-	-	-	C	-
CO2	44.01000	73.74000	30.97000	0.225000	0.001910	0.274330	0.09407	0.09407	0.274330	80.00	0.76193	-88.266	
N2	28.01400	33.98000	-146.95000	0.037000	-0.167580	0.291780	0.09010	0.09010	0.291780	59.10	0.28339	-195.903	
C1	16.04300	45.99000	-82.59000	0.011000	-0.149960	0.286200	0.09860	0.09860	0.286200	71.00	0.14609	-161.593	
...
```

Column definitions:
| Column | Description | Units |
|--------|-------------|-------|
| Component | Component name | - |
| MW | Molecular weight | g/mol |
| Pc | Critical pressure | bara |
| Tc | Critical temperature | °C |
| AF, ω | Acentric factor | - |
| Volume Shift, s | Peneloux volume shift | - |
| ZcVisc | Critical compressibility for viscosity | - |
| VcVisc | Critical volume for viscosity | m³/kmol |
| Vc | Critical volume | m³/kmol |
| Zc | Critical compressibility | - |
| Pchor | Parachor | - |
| SG | Specific gravity | - |
| Tb | Normal boiling point | °C |
| LMW | Lumped molecular weight (optional) | g/mol |

### 3. Binary Interaction Parameters (BIPs)

Full symmetric matrix of binary interaction parameters:

```
BIPS	CO2	N2	C1	C2	C3	...
CO2	0.00	0.00	0.11	0.13	0.13	...
N2	0.00	0.00	0.03	0.01	0.09	...
C1	0.11	0.03	0.00	0.00	0.00	...
...
```

## Usage Examples

### Basic Usage

Read a parameter file and create a fluid with equal molar composition:

```java
import neqsim.thermo.util.readwrite.WhitsonPVTReader;
import neqsim.thermo.system.SystemInterface;

// Read parameter file
SystemInterface fluid = WhitsonPVTReader.read("path/to/volveparam.txt");

// Set conditions
fluid.setTemperature(373.15);  // 100°C in Kelvin
fluid.setPressure(200.0);       // 200 bar

// Run flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Get properties
double density = fluid.getDensity("kg/m3");
double viscosity = fluid.getViscosity("cP");
```

### With Custom Composition

Specify molar composition matching the component order in the file:

```java
// Define composition (must match number of components in file)
double[] composition = {
    0.02,   // CO2
    0.01,   // N2
    0.70,   // C1
    0.10,   // C2
    0.05,   // C3
    0.08,   // C7
    0.04    // C10
};

SystemInterface fluid = WhitsonPVTReader.read("path/to/volveparam.txt", composition);
```

### Accessing Parsed Parameters

The reader provides getters for extracted parameters:

```java
WhitsonPVTReader reader = new WhitsonPVTReader();
reader.parseFile("path/to/volveparam.txt");

// Get LBC viscosity parameters
double[] lbcParams = reader.getLBCParameters();
// Returns: [P0, P1, P2, P3, P4, F0]

// Get C7+ gamma distribution parameters
double[] gammaParams = reader.getGammaParameters();
// Returns: [shape, bound]

// Get Omega parameters
double omegaA = reader.getOmegaA();
double omegaB = reader.getOmegaB();

// Get component information
int numComponents = reader.getNumberOfComponents();
List<String> names = reader.getComponentNames();
```

## Component Name Mapping

The reader automatically maps Whitson component names to NeqSim standard names:

| Whitson Name | NeqSim Name |
|--------------|-------------|
| C1 | methane |
| C2 | ethane |
| C3 | propane |
| i-C4 | i-butane |
| n-C4 | n-butane |
| i-C5 | i-pentane |
| n-C5 | n-pentane |
| NEO-C5 | 22-dim-C3 |
| C6 | n-hexane |
| N2 | nitrogen |
| CO2 | CO2 |
| H2S | H2S |
| H2O | water |

C7+ fractions (C7, C8, C9, ..., C36+) are added as pseudo-components with the suffix `_PC`.

## Supported EOS Types

| File Value | NeqSim Class |
|------------|--------------|
| PR | SystemPrEos |
| SRK | SystemSrkEos |
| PR78 | SystemPrEos1978 |

## Integration with Whitson+ Workflows

This reader enables seamless integration between Whitson+ PVT characterization and NeqSim process simulation:

1. **PVT Characterization**: Use Whitson+ to fit EOS parameters to laboratory data (CCE, CVD, DLE, separator tests)
2. **Parameter Export**: Export the fitted parameters to a tab-separated file
3. **NeqSim Import**: Use `WhitsonPVTReader` to create a NeqSim fluid
4. **Process Simulation**: Use the characterized fluid in NeqSim process simulations

This workflow ensures consistency between PVT modeling and process simulation, using the same EOS parameters throughout.

## PVT Simulations with Imported Fluids

Once a fluid is created from a Whitson file, you can run standard PVT simulations:

```java
import neqsim.pvtsimulation.simulation.*;
import neqsim.pvtsimulation.util.PVTReportGenerator;

// Create fluid from Whitson file
double[] composition = {0.02, 0.01, 0.70, 0.10, 0.05, 0.08, 0.04};
SystemInterface fluid = WhitsonPVTReader.read("volveparam.txt", composition);

// Initialize
fluid.setTemperature(373.15);  // 100°C
fluid.setPressure(300.0);
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Saturation pressure (dew point for gas condensate)
SaturationPressure satPres = new SaturationPressure(fluid);
satPres.setTemperature(100.0, "C");
satPres.run();
double psat = satPres.getSaturationPressure();

// Constant Composition Expansion (CCE)
ConstantMassExpansion cce = new ConstantMassExpansion(fluid);
cce.setTemperature(100.0, "C");
cce.setPressures(new double[]{300, 250, 200, 150, 100});
cce.runCalc();
double[] relVol = cce.getRelativeVolume();

// Multi-Stage Separator Test
MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(fluid);
sepTest.setReservoirConditions(300.0, 100.0);
sepTest.addSeparatorStage(50.0, 40.0, "HP Separator");
sepTest.addSeparatorStage(10.0, 30.0, "LP Separator");
sepTest.addSeparatorStage(1.01325, 15.0, "Stock Tank");
sepTest.run();

double gor = sepTest.getTotalGOR();        // Sm³/Sm³
double bo = sepTest.getBo();               // m³/Sm³
double apiGravity = sepTest.getStockTankAPIGravity();
double stockTankDensity = sepTest.getStockTankOilDensity();  // kg/m³

// Get oil properties at each separator stage
for (var stage : sepTest.getStageResults()) {
    double oilDensity = stage.getOilDensity();     // kg/m³
    double oilViscosity = stage.getOilViscosity(); // cP
}

// Generate PVT Report
PVTReportGenerator report = new PVTReportGenerator(fluid);
report.setProjectInfo("My Project", "Gas Condensate Sample")
      .setReservoirConditions(300.0, 100.0)
      .setSaturationPressure(psat, false)  // false = dew point
      .addCCE(cce)
      .addSeparatorTest(sepTest);

String markdownReport = report.generateMarkdownReport();
System.out.println(markdownReport);
```

### LBC Viscosity Model

The reader automatically applies the LBC viscosity model with the parameters from the Whitson file (P0-P4). The viscosity is calculated using:

```java
// Viscosity is automatically calculated with LBC model
fluid.initPhysicalProperties();

// Gas phase viscosity
double gasViscosity = fluid.getPhase("gas").getPhysicalProperties().getViscosity();
double gasViscosityCP = gasViscosity * 1000;  // Convert Pa·s to cP

// Oil phase viscosity (if oil phase exists)
if (fluid.hasPhaseType("oil")) {
    double oilViscosity = fluid.getPhase("oil").getPhysicalProperties().getViscosity();
    double oilViscosityCP = oilViscosity * 1000;  // Convert Pa·s to cP
}
```

You can also use `ViscositySim` to calculate viscosity at multiple pressures:

```java
import neqsim.pvtsimulation.simulation.ViscositySim;

ViscositySim viscSim = new ViscositySim(fluid);
double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
double[] temperatures = new double[pressures.length];
Arrays.fill(temperatures, 373.15);  // 100°C in Kelvin
viscSim.setTemperaturesAndPressures(temperatures, pressures);
viscSim.runCalc();

double[] gasViscosity = viscSim.getGasViscosity();   // Pa·s (multiply by 1000 for cP)
double[] oilViscosity = viscSim.getOilViscosity();   // Pa·s (multiply by 1000 for cP)
```

## Example Output

A typical PVT report for a gas condensate fluid created from a Whitson parameter file:

```markdown
# PVT Study Report

## Project Information

| Property | Value |
|----------|-------|
| Project | Whitson PVT Reader Test |
| Fluid Name | Test Gas Condensate |
| Report Date | 2025-12-15 12:59 |

## Reservoir Conditions

| Property | Value | Unit |
|----------|-------|------|
| Reservoir Pressure | 300.0 | bara |
| Reservoir Temperature | 100.0 | °C |
| Dew Point Pressure | 248.41 | bara |

## Fluid Composition

| Component | Mole Fraction | MW (g/mol) |
|-----------|--------------|------------|
| CO2 | 0.020000 | 44.01 |
| nitrogen | 0.010000 | 28.01 |
| methane | 0.700000 | 16.04 |
| ethane | 0.100000 | 30.07 |
| propane | 0.050000 | 44.10 |
| C7_PC | 0.080000 | 97.63 |
| C10_PC | 0.040000 | 134.47 |

## Constant Composition Expansion (CCE)

| Pressure (bara) | Rel. Volume | Y-Factor | Density (kg/m³) |
|-----------------|-------------|----------|-----------------|
| 298.1 | 0.8332 | - | 332.0 |
| 273.2 | 0.8703 | - | 318.3 |
| 248.4 | 0.9163 | - | 302.8 |
| 223.6 | 1.0042 | 1.1212 | 234.5 |
| 198.7 | 1.1188 | 1.0966 | 193.6 |
| 173.9 | 1.2725 | 1.0707 | 160.5 |

## Separator Test

=== Multi-Stage Separator Test Results ===

Reservoir Conditions: P = 300.0 bara, T = 100.0 °C
Number of Stages: 3

Stage-by-Stage Results:
| Stage | P (bara) | T (°C) | GOR (Sm³/Sm³) | Cum GOR (Sm³/Sm³) |
|-------|----------|--------|---------------|-------------------|
| HP Separator | 50.0 | 40.0 | 1096.9 | 1096.9 |
| LP Separator | 10.0 | 30.0 | 53.9 | 1150.8 |
| Stock Tank | 1.0 | 15.0 | 23.6 | 1174.4 |

Overall Results:
  Total GOR:           1174.4 Sm³/Sm³
  Bo (FVF):            5.0880 rm³/Sm³
  Stock Tank Density:  751.5 kg/m³
  API Gravity:         56.6 °API

## Quality Metrics

---
*Report generated by NeqSim PVT Report Generator*
```

### Additional PVT Data

#### Gas Viscosity (LBC Model)

| Pressure (bara) | Viscosity (cP) |
|-----------------|----------------|
| 300.0 | 0.0387 |
| 250.0 | 0.0340 |
| 200.0 | 0.0227 |
| 150.0 | 0.0178 |
| 100.0 | 0.0151 |

#### Gas Density

| Pressure (bara) | Density (kg/m³) |
|-----------------|-----------------|
| 300.0 | 333.04 |
| 250.0 | 303.87 |
| 200.0 | 195.47 |
| 150.0 | 132.69 |
| 100.0 | 82.11 |

#### Separator Stage Oil Properties

| Stage | P (bara) | T (°C) | Oil Density (kg/m³) | Oil Viscosity (cP) |
|-------|----------|--------|---------------------|---------------------|
| HP Separator | 50.0 | 40.0 | - | 0.6315 |
| LP Separator | 10.0 | 30.0 | 675.35 | 0.6143 |
| Stock Tank | 1.0 | 15.0 | 723.65 | 0.6643 |

#### Oil Properties vs Pressure

For gas condensates, an oil (condensate) phase forms below the dew point pressure. The properties are:

| Pressure (bara) | Density (kg/m³) | Viscosity (cP) |
|-----------------|-----------------|----------------|
| 200.0 | 482.81 | 0.0666 |
| 150.0 | 544.85 | 0.0903 |

*Note: Oil phase only exists below the dew point pressure (248.4 bara for this fluid).*

#### Gas Condensate Metrics Summary

| Property | Value | Unit |
|----------|-------|------|
| Dew Point Pressure | 248.41 | bara |
| GOR | 1174.4 | Sm³/Sm³ |
| CGR | 5.4 | bbl/MMscf |
| Bo | 5.0880 | m³/Sm³ |
| Stock Tank API | 56.6 | °API |
| Stock Tank Density | 751.5 | kg/m³ |
| Molar Mass | 30.79 | g/mol |

## See Also

- [Fluid Characterization](fluid_characterization_mathematics.md) - Mathematical background for C7+ characterization
- [PVT Workflow](pvt_workflow.md) - End-to-end PVT workflow
- [Viscosity Models](../physical_properties/viscosity_models.md) - LBC and other viscosity model documentation
