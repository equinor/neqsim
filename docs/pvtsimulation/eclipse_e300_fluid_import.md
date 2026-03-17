---
title: "Eclipse E300 Fluid File Format and Import into NeqSim"
description: "Guide to Eclipse 300 compositional fluid file format (.e300), supported keywords, and how to read, create, and export fluids using NeqSim's EclipseFluidReadWrite. Covers PVTsim integration, reservoir simulation coupling, and wellstream recombination workflows."
---

## Overview

Eclipse 300 (E300) is the compositional reservoir simulator from SLB (formerly Schlumberger). It uses a text-based file format to define fluid compositions and equation-of-state (EOS) parameters for compositional reservoir simulation.

NeqSim can **read** E300 fluid files to create thermodynamic `SystemInterface` fluids, and **write** NeqSim fluids back to E300 format. This enables seamless round-tripping between PVT tools (such as PVTsim, Multiflash, or WinProp), reservoir simulators, and NeqSim process/flow assurance calculations.

The Java class that handles E300 files is `neqsim.thermo.util.readwrite.EclipseFluidReadWrite`.

## E300 File Format Reference

An E300 fluid file is a plain-text file (commonly with `.e300` or `.txt` extension) organized into **keyword blocks**. Each block starts with a keyword on its own line, followed by data values (one per line or space-separated), terminated by a `/` character or a `--` comment line.

### General Syntax Rules

- **Comments** start with `--` and run to the end of the line
- **Data termination** uses `/` at the end of the last data line or on its own line
- **Whitespace** is flexible — values can be separated by spaces or appear one per line
- **Units** follow the unit system declared in the `METRIC` or `FIELD` keyword

### Supported Keywords

The table below lists all keywords supported by NeqSim's E300 reader:

| Keyword    | Description                                                      | Unit (METRIC) | Required |
| ---------- | ---------------------------------------------------------------- | ------------- | -------- |
| `METRIC`   | Unit system declaration                                          | —             | Yes      |
| `NCOMPS`   | Number of components                                             | —             | Optional |
| `EOS`      | Equation of state type (`SRK` or `PR`)                           | —             | Yes      |
| `PRCORR`   | Peng-Robinson volume correction (follows `EOS PR`)               | —             | If PR    |
| `RTEMP`    | Reservoir temperature                                            | °C            | Optional |
| `STCOND`   | Standard conditions (temperature and pressure)                   | °C, bara      | Optional |
| `CNAMES`   | Component names                                                  | —             | Yes      |
| `TCRIT`    | Critical temperatures                                            | K             | Yes      |
| `PCRIT`    | Critical pressures                                               | bar           | Yes      |
| `ACF`      | Acentric factors                                                 | —             | Yes      |
| `MW`       | Molecular weights                                                | g/mol         | Yes      |
| `TBOIL`    | Normal boiling points                                            | K             | Yes      |
| `VCRIT`    | Critical volumes                                                 | m³/kmol       | Yes      |
| `ZCRIT`    | Critical Z-factors                                               | —             | Optional |
| `SSHIFT`   | Volume translation (shift) parameters                            | —             | Yes      |
| `PARACHOR` | Parachor parameters for IFT calculation                          | dyn/cm        | Yes      |
| `ZI`       | Overall mole fractions (composition)                             | —             | Yes      |
| `BIC`      | Binary interaction coefficients (lower triangular)               | —             | Yes      |
| `BICS`     | Binary interaction coefficients at surface conditions            | —             | Optional |
| `SSHIFTS`  | Volume translation at surface conditions                         | —             | Optional |
| `LBCCOEF`  | Lorentz-Bray-Clark viscosity correlation coefficients (5 values) | —             | Optional |
| `PEDERSEN` | Flag to use Pedersen (PFCT) viscosity model                      | —             | Optional |
| `OMEGAA`   | EOS OmegaA parameter                                             | —             | Optional |
| `OMEGAB`   | EOS OmegaB parameter                                             | —             | Optional |

### Example E300 File

Below is a complete example file for a 22-component oil with Peng-Robinson EOS:

```text
-- Example E300 Compositional Fluid File
--
-- Units
METRIC
-- Number of components:
NCOMPS
22 /
-- Equation of state
EOS
PR /
PRCORR

-- Reservoir temperature (C)
RTEMP
     90.00 /

-- Standard Conditions (C and bara)
STCOND
   15.00000    1.01325  /

-- Component names
CNAMES
N2
CO2
C1
C2
C3
iC4
C4
iC5
C5
C6
C7
C8
C9
C10-C12
C13-C14
C15-C17
C18-C21
C22-C28
C29-C36
C37-C45
C46-C58
C59-C80 /

-- Tc (K)
TCRIT
   126.200
   304.200
   190.600
   305.400
   369.800
   408.100
   425.200
   460.400
   469.600
   507.400
   548.083
   568.470
   592.686
   631.845
   680.299
   727.035
   774.284
   851.846
   943.373
  1038.592
  1152.236
  1317.304 /

-- Pc (Bar)
PCRIT
   33.9439
   73.7646
   46.0015
   48.8387
   42.4552
   36.4770
   37.9969
   33.8426
   33.7412
   29.6882
   29.4519
   27.6423
   25.5535
   22.7296
   20.0143
   18.1224
   16.7108
   15.1759
   14.0297
   13.2891
   12.7370
   12.2645 /

-- Omega (acentric factors)
ACF
   0.04000
   0.22500
   0.00800
   0.09800
   0.15200
   0.17600
   0.19300
   0.22700
   0.25100
   0.29600
   0.33744
   0.37547
   0.42325
   0.50535
   0.61393
   0.72473
   0.83712
   1.00708
   1.15740
   1.21951
   1.23925
   1.21155 /

-- Molecular Weight (g/mol)
MW
    28.0140
    44.0100
    16.0430
    30.0700
    44.0970
    58.1240
    58.1240
    72.1510
    72.1510
    86.1780
    96.0000
   107.0000
   121.0000
   148.0000
   190.0000
   237.0000
   291.0000
   384.0000
   510.0000
   656.0000
   849.0000
  1160.0000 /

-- Volume translation/co-volume
SSHIFT
 -0.175888
 -0.049181
 -0.194020
 -0.143142
 -0.112702
 -0.099214
 -0.089659
 -0.070455
 -0.056872
  0.012573
  0.074067
  0.085121
  0.081268
  0.069060
  0.048755
  0.018239
 -0.017443
 -0.077518
 -0.156174
 -0.235730
 -0.320950
 -0.420868 /

-- Parachors (dyn/cm)
PARACHOR
    41.000
    78.000
    77.300
   108.900
   151.900
   181.500
   191.700
   225.000
   233.900
   271.000
   283.940
   309.680
   342.440
   402.209
   485.824
   577.490
   679.641
   866.931
  1111.372
  1387.629
  1739.859
  2282.641 /

-- Overall mole fractions
ZI
  0.003912
  0.003010
  0.403275
  0.076341
  0.079752
  0.011938
  0.040929
  0.013944
  0.021568
  0.027988
  0.042936
  0.043237
  0.030898
  0.043939
  0.045143
  0.022571
  0.025180
  0.021188
  0.014111
  0.012845
  0.008955
  0.006340 /

-- Binary interaction coefficients (lower triangular)
BIC
 -0.0170
  0.0311  0.1200
  0.0515  0.1200  0.0000
  0.0852  0.1200  0.0000  0.0000
  ...
/

-- LBC viscosity coefficients
LBCCOEF
   0.1023000  0.0233640  0.0585330  -0.0407580  0.0093324 /
```

### Component Name Mapping

NeqSim maps E300 shorthand component names to its internal database names:

| E300 Name                              | NeqSim Name                                  |
| -------------------------------------- | -------------------------------------------- |
| `N2`                                   | nitrogen                                     |
| `CO2`                                  | CO2                                          |
| `C1`                                   | methane                                      |
| `C2`                                   | ethane                                       |
| `C3`                                   | propane                                      |
| `iC4`                                  | i-butane                                     |
| `C4`                                   | n-butane                                     |
| `iC5`                                  | i-pentane                                    |
| `C5`                                   | n-pentane                                    |
| `C6`                                   | n-hexane                                     |
| `H2O`                                  | water                                        |
| Any other name (e.g., `C7`, `C10-C12`) | Treated as a pseudo-component (TBP fraction) |

Pseudo-components (C7+, grouped fractions like `C10-C12`, `C22-C28`, etc.) are added as TBP fractions using the critical properties, molecular weight, and an estimated density from the correlation:

$$
\rho_{std} = 0.5046 \times \frac{MW}{1000} + 0.668468
$$

### Binary Interaction Coefficients (BIC)

The `BIC` section stores the lower triangular matrix of binary interaction parameters ($k_{ij}$). For $N$ components, there are $N-1$ rows. Row $i$ contains $i$ values representing $k_{1,i+1}, k_{2,i+1}, \ldots, k_{i,i+1}$:

```text
BIC
  k(1,2)
  k(1,3)  k(2,3)
  k(1,4)  k(2,4)  k(3,4)
  ...
/
```

The matrix is symmetric: $k_{ij} = k_{ji}$ and $k_{ii} = 0$.

### Equation of State Selection

The `EOS` keyword determines which cubic equation of state NeqSim uses:

| EOS Value       | NeqSim Class      | Notes                                             |
| --------------- | ----------------- | ------------------------------------------------- |
| `SRK`           | `SystemSrkEos`    | Soave-Redlich-Kwong                               |
| `PR`            | `SystemPrEos`     | Original Peng-Robinson (1976)                     |
| `PR` + `PRCORR` | `SystemPrEos1978` | Peng-Robinson with 1978 alpha function correction |

### Viscosity Models

Two viscosity correlation options are supported:

- **LBC** (Lorentz-Bray-Clark): Specified via the `LBCCOEF` keyword with 5 coefficients for the dense-fluid contribution
- **Pedersen** (PFCT): Activated by the `PEDERSEN` keyword flag; uses the corresponding states principle

## Reading E300 Files in NeqSim

### Java API

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;

// Basic read
SystemInterface fluid = EclipseFluidReadWrite.read("path/to/fluid.e300");

// Read with water addition (kij = 0.5 for water vs all components)
SystemInterface fluid = EclipseFluidReadWrite.read("path/to/fluid.e300", true);

// Read with water and custom kij
SystemInterface fluid = EclipseFluidReadWrite.read("path/to/fluid.e300", true, 0.45);

// Read with pseudo-name suffix (for multi-fluid models)
SystemInterface fluid = EclipseFluidReadWrite.read("path/to/fluid.e300", "_well1");

// Read multiple fluids from same file (each component set gets a suffix)
String[] fluidNames = {"well1", "well2"};
SystemInterface fluid = EclipseFluidReadWrite.read("path/to/fluid.e300", fluidNames);
```

After reading, the fluid is ready for thermodynamic calculations:

```java
// Set reservoir conditions
fluid.setPressure(100.0, "bara");
fluid.setTemperature(90.0, "C");

// Run flash calculation
neqsim.thermodynamicoperations.ThermodynamicOperations ops =
    new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

// Get results
System.out.println("Number of phases: " + fluid.getNumberOfPhases());
System.out.println("Gas density: " + fluid.getPhase("gas").getDensity("kg/m3"));
```

### Python API (neqsim-python)

The simplest way to read an E300 file in Python is via the `readEclipseFluid` function from `neqsim.thermo.thermoTools`:

```python
from neqsim.thermo.thermoTools import readEclipseFluid, TPflash, printFrame, bubp

# Read E300 file
fluid = readEclipseFluid('path/to/fluid.e300')

# Optionally add water
fluid.addComponent('water')
fluid.setMixingRule('classic')

# Set reservoir conditions
fluid.setPressure(100.0, 'bara')
fluid.setTemperature(90.0, 'C')

# Calculate saturation pressure
saturation_pressure = bubp(fluid)
print(f'Saturation pressure: {saturation_pressure:.2f} bara')

# Run TP flash
TPflash(fluid)
printFrame(fluid)
```

You can also update an existing fluid's composition from an E300 file (useful when the same EOS model is shared across wells with different compositions):

```python
from neqsim.thermo.thermoTools import setEclipseComposition

# Read base fluid
fluid = readEclipseFluid('base_fluid.e300')

# Update composition from another E300 file (same component set)
setEclipseComposition(fluid, 'well2_composition.e300')
```

For multiple wells with distinct pseudo-component naming:

```python
# Read with well-name suffix to keep pseudo-components unique
fluid = readEclipseFluid('fluid.e300', wellName='_wellA')
```

### Direct Java Access from Python

For full control, use the `jneqsim` gateway directly:

```python
from neqsim import jneqsim

EclipseFluidReadWrite = jneqsim.thermo.util.readwrite.EclipseFluidReadWrite

# Read with water
fluid = EclipseFluidReadWrite.read('fluid.e300', True)

# Read with custom water kij
fluid = EclipseFluidReadWrite.read('fluid.e300', True, 0.45)
```

## Writing E300 Files from NeqSim

NeqSim can export any fluid to E300 format, enabling workflows where you build a fluid in NeqSim (or fit it to experimental data) and then export for reservoir simulation.

### Java API

```java
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;

// Write to file
EclipseFluidReadWrite.write(fluid, "output.e300");

// Write with reservoir temperature
EclipseFluidReadWrite.write(fluid, "output.e300", 120.0);  // 120 °C

// Get E300 content as string (for logging or embedding)
String e300Content = EclipseFluidReadWrite.toE300String(fluid);
System.out.println(e300Content);
```

### Python API

```python
from neqsim import jneqsim

EclipseFluidReadWrite = jneqsim.thermo.util.readwrite.EclipseFluidReadWrite

# Write fluid to file
EclipseFluidReadWrite.write(fluid, "exported_fluid.e300")

# Write with reservoir temperature
EclipseFluidReadWrite.write(fluid, "exported_fluid.e300", 120.0)

# Get as string
e300_string = EclipseFluidReadWrite.toE300String(fluid)
print(e300_string)
```

## Workflow: E300 Fluid to Reservoir + Process Simulation

A common field-development workflow uses E300 files to couple PVT tools, reservoir simulation, and process simulation:

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  PVTsim /   │     │  Eclipse 300 │     │    NeqSim        │
│  Multiflash  │────▶│  .e300 file  │────▶│  Process Sim     │
│  WinProp    │     │              │     │  Flow Assurance  │
└─────────────┘     └──────────────┘     └─────────────────┘
        │                                        │
        │          ┌──────────────┐              │
        └─────────▶│  Reservoir   │◀─────────────┘
                   │  Simulator   │   (export VFP tables,
                   └──────────────┘    updated compositions)
```

### Step-by-Step Example

Below is a condensed version of the workflow demonstrated in the [reference notebook](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/PVT/eclipseFluidCharNeqSim.ipynb):

#### 1. Read the E300 fluid and characterize at reservoir conditions

```python
from neqsim.thermo.thermoTools import readEclipseFluid, TPflash, printFrame, bubp

# Read Eclipse fluid
fluid = readEclipseFluid('reservoir_fluid.e300')
fluid.addComponent('water')
fluid.setMixingRule('classic')

# Set reservoir conditions
fluid.setPressure(100.0, 'bara')
fluid.setTemperature(90.0, 'C')

# Calculate bubble point
sat_pressure = bubp(fluid)
print(f'Bubble point at 90°C: {sat_pressure:.1f} bara')

# Flash at reservoir conditions
TPflash(fluid)
printFrame(fluid)
```

#### 2. Run reservoir depletion simulation

```python
from neqsim.process.processTools import simplereservoir, clearProcess

clearProcess()
resfluid = fluid.clone()

# Set up reservoir with gas cap and oil volumes
reservoir = simplereservoir(
    "Reservoir", resfluid,
    gasvolume=5.0e7,       # m³
    oilvolume=552.0e6,     # m³
    watervolume=10.0e3     # m³
)

# Add producers and injectors
oil_producer = reservoir.addOilProducer("OilWell")
oil_producer.setFlowRate(50000000.0, "kg/day")

gas_producer = reservoir.addGasProducer("GasWell")
gas_producer.setFlowRate(0.01, "MSm3/day")

# Run transient depletion over 10 years
reservoir.run()
timestep = 365 * 24 * 3600.0  # 1 year in seconds

production_snapshots = []
pressures = []
for year in range(10):
    production_snapshots.append(oil_producer.clone())
    pressures.append(reservoir.getReservoirFluid().getPressure('bara'))
    reservoir.runTransient(timestep)
```

#### 3. Measure test separator rates

```python
from neqsim.process import clearProcess, heater, separator, stream

gas_rates = []
oil_rates = []

for t in range(10):
    clearProcess()
    # Flash to test separator conditions (1 atm, 15°C)
    well_tp = heater('heater', production_snapshots[t])
    well_tp.setOutTemperature(15.0, 'C')
    well_tp.setOutPressure(1.01325, 'bara')
    well_tp.run()

    sep = separator('test_sep', well_tp.getOutStream())
    sep.run()

    gas_rates.append(sep.getGasOutStream().getFlowRate('MSm3/day'))
    oil_rates.append(sep.getLiquidOutStream().getFlowRate('m3/hr'))
```

#### 4. Recombine wellstream using FlowSetter

```python
from neqsim import jneqsim
from jpype.types import JDouble

for t in range(10):
    clearProcess()
    feed = stream('feed', fluid.clone())
    feed.run()

    flowset = jneqsim.process.equipment.util.FlowSetter("flowset", feed)
    flowset.setSeparationPT(
        JDouble[:]([1.01325]), "bara",
        JDouble[:]([15.0]), "C"
    )
    flowset.setGasFlowRate(gas_rates[t], "MSm3/day")
    flowset.setOilFlowRate(oil_rates[t], "m3/hr")
    flowset.run()
    # flowset.getOutStream() now has the recombined wellstream
```

#### 5. Improve results with CME correction

For better accuracy during depletion, flash the original fluid at the current reservoir pressure to get the equilibrium oil composition before recombining:

```python
for t in range(10):
    clearProcess()
    depleted_fluid = fluid.clone()
    depleted_fluid.setPressure(pressures[t])
    TPflash(depleted_fluid)

    # Use oil phase only (simulating reservoir oil production below bubble point)
    feed = stream('feed', depleted_fluid.phaseToSystem('oil'))
    feed.run()

    flowset = jneqsim.process.equipment.util.FlowSetter("flowset", feed)
    flowset.setSeparationPT(
        JDouble[:]([1.01325]), "bara",
        JDouble[:]([15.0]), "C"
    )
    flowset.setGasFlowRate(gas_rates[t], "MSm3/day")
    flowset.setOilFlowRate(oil_rates[t], "m3/hr")
    flowset.run()
```

This CME-corrected approach significantly reduces errors in gas molecular weight and composition predictions as reservoir pressure declines below the bubble point.

## Advanced Features

### Water Handling

E300 fluid files from PVTsim may or may not include water. NeqSim provides the `read(file, addWater)` overload that adds a water component with:

- Zero mole fraction (so it does not affect hydrocarbon equilibrium)
- Binary interaction parameter $k_{ij} = 0.5$ against all other components (configurable)
- Volume correction constant of 0.084004
- Parachor parameter of 10.0
- Multi-phase check enabled for aqueous phase detection

```java
// Java
SystemInterface fluid = EclipseFluidReadWrite.read("fluid.e300", true);
// or with custom water kij
SystemInterface fluid = EclipseFluidReadWrite.read("fluid.e300", true, 0.45);
```

### Multi-Fluid Models

When modeling multiple reservoir zones or wells with different compositions but the same EOS characterization, use the `fluidNames` array variant. Each component set gets a unique suffix so they can coexist in one `SystemInterface`:

```java
String[] zones = {"zone1", "zone2"};
SystemInterface multiFluid = EclipseFluidReadWrite.read("fluid.e300", zones);
// Components named: methane_zone1, methane_zone2, C7_zone1, C7_zone2, etc.
```

### Pseudo-Name Suffixes

When reading multiple E300 files into the same simulation (e.g., commingled production from different reservoirs), use pseudo-name suffixes to prevent name collisions:

```python
fluid_a = readEclipseFluid('reservoir_A.e300', wellName='_A')
fluid_b = readEclipseFluid('reservoir_B.e300', wellName='_B')
# Pseudo-components will be named C7_A, C7_B, etc.
```

### Round-Trip Verification

You can verify E300 read/write fidelity by round-tripping a fluid:

```java
// Read original
SystemInterface original = EclipseFluidReadWrite.read("input.e300");

// Write it out
EclipseFluidReadWrite.write(original, "roundtrip.e300", 90.0);

// Read back and compare
SystemInterface roundtripped = EclipseFluidReadWrite.read("roundtrip.e300");

// Compare properties
for (int i = 0; i < original.getNumberOfComponents(); i++) {
    System.out.printf("%-12s  Tc_orig=%.3f  Tc_rt=%.3f%n",
        original.getComponent(i).getComponentName(),
        original.getComponent(i).getTC(),
        roundtripped.getComponent(i).getTC());
}
```

## Troubleshooting

| Problem                                                       | Cause                           | Solution                                                     |
| ------------------------------------------------------------- | ------------------------------- | ------------------------------------------------------------ |
| `IllegalArgumentException: Eclipse fluid file does not exist` | Wrong file path                 | Verify the path is correct and the file exists               |
| Wrong EOS used                                                | Missing `PRCORR` after `EOS PR` | Add `PRCORR` keyword on the line after `PR /`                |
| Negative densities or unrealistic properties                  | Missing `SSHIFT` or `PARACHOR`  | Ensure the E300 file includes volume shift and parachor data |
| Composition does not sum to 1.0                               | E300 file has unnormalized `ZI` | NeqSim normalizes internally, but check for typos            |
| Water phase not appearing                                     | Water not added                 | Use `read(file, true)` or add water manually                 |
| Name collision with multiple wells                            | Same pseudo-names               | Use `wellName` suffix or `fluidNames` array                  |
| Viscosity not matching PVTsim                                 | Missing `LBCCOEF` or `PEDERSEN` | Ensure viscosity keywords are in the E300 file               |

## Related Resources

- [PVT Workflow Guide](pvt_workflow.md) — overall PVT simulation methodology
- [Fluid Characterization Mathematics](fluid_characterization_mathematics.md) — TBP fraction correlations
- [Phase Envelope Guide](phase_envelope_guide.md) — calculating phase envelopes from E300 fluids
- [Reference Colab Notebook](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/PVT/eclipseFluidCharNeqSim.ipynb) — interactive E300 fluid characterization example

## API Reference

### `EclipseFluidReadWrite` (Java)

| Method                                                                   | Description                                 |
| ------------------------------------------------------------------------ | ------------------------------------------- |
| `read(String inputFile)`                                                 | Read E300 file, return `SystemInterface`    |
| `read(String inputFile, boolean addWater)`                               | Read with optional water addition (kij=0.5) |
| `read(String inputFile, boolean addWater, double waterKij)`              | Read with custom water kij                  |
| `read(String inputFile, String pseudoNameIn)`                            | Read with pseudo-name suffix                |
| `read(String inputFile, String pseudoNameIn, boolean addWater)`          | Read with suffix and water                  |
| `read(String inputFile, String[] fluidNames)`                            | Read multi-fluid model                      |
| `setComposition(SystemInterface fluid, String inputFile)`                | Update composition from file                |
| `write(SystemInterface fluid, String outputFile)`                        | Write to E300 format                        |
| `write(SystemInterface fluid, String outputFile, double reservoirTempC)` | Write with reservoir temperature            |
| `toE300String(SystemInterface fluid)`                                    | Convert to E300 string                      |
| `addWaterToFluid(SystemInterface fluid, double waterKij)`                | Add water to existing fluid                 |

### `thermoTools` (Python)

| Function                                              | Description                             |
| ----------------------------------------------------- | --------------------------------------- |
| `readEclipseFluid(filename, wellName="")`             | Read E300 file, return fluid object     |
| `setEclipseComposition(fluid, filename, wellName="")` | Update fluid composition from E300 file |

## See Also

- [JSON Fluid Format](json_fluid_format.md) — equivalent JSON-based format with named BIC pairs, structured component definitions, and E300 interconversion support
