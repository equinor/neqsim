# Black Oil PVT and Reservoir Simulator Export

This document describes NeqSim's black oil implementation and the ability to export PVT data to reservoir simulators like Eclipse and CMG.

## Overview

NeqSim provides a complete workflow for converting compositional EOS (Equation of State) fluid models to black oil PVT tables suitable for reservoir simulation. The `neqsim.blackoil` package includes:

- **BlackOilConverter** - Converts compositional fluids to black oil properties
- **BlackOilPVTTable** - Stores black oil PVT data (Bo, Rs, Bg, μo, μg, etc.)
- **EclipseEOSExporter** - Exports to Schlumberger Eclipse format
- **CMGEOSExporter** - Exports to CMG IMEX/GEM/STARS formats

## Black Oil Conversion

The `BlackOilConverter` class performs flash calculations at multiple pressures to generate black oil properties:

```java
import neqsim.blackoil.BlackOilConverter;
import neqsim.blackoil.BlackOilPVTTable;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// Create a compositional fluid
SystemInterface fluid = new SystemSrkEos(373.15, 200.0); // 100°C, 200 bar
fluid.addComponent("nitrogen", 0.01);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("methane", 0.50);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.03);
fluid.addComponent("n-pentane", 0.02);
fluid.addComponent("n-hexane", 0.02);
fluid.addComponent("n-heptane", 0.07);
fluid.addComponent("water", 0.20);
fluid.setMixingRule("classic");

// Define pressure grid for PVT table
double[] pressures = {20, 50, 100, 150, 200, 250, 300, 350, 400}; // bar

// Convert to black oil
double Tref = 373.15;  // Reference temperature (K)
double Pstd = 1.01325; // Standard pressure (bar)
double Tstd = 288.15;  // Standard temperature (K)

BlackOilPVTTable pvtTable = BlackOilConverter.convert(fluid, Tref, pressures, Pstd, Tstd);
```

### Black Oil Properties

The `BlackOilPVTTable` contains records with the following properties:

| Property | Symbol | Unit | Description |
|----------|--------|------|-------------|
| Pressure | p | bar | Reservoir pressure |
| Solution GOR | Rs | Sm³/Sm³ | Gas dissolved in oil at standard conditions |
| Oil FVF | Bo | Rm³/Sm³ | Oil formation volume factor |
| Oil viscosity | μo | Pa·s | Oil dynamic viscosity |
| Gas FVF | Bg | Rm³/Sm³ | Gas formation volume factor |
| Gas viscosity | μg | Pa·s | Gas dynamic viscosity |
| Vaporized OGR | Rv | Sm³/Sm³ | Oil vaporized in gas (for volatile oil/gas condensate) |
| Water FVF | Bw | Rm³/Sm³ | Water formation volume factor |
| Water viscosity | μw | Pa·s | Water dynamic viscosity |

## Eclipse Export

The `EclipseEOSExporter` generates PVT include files compatible with Schlumberger Eclipse reservoir simulator.

### Basic Usage

```java
import neqsim.blackoil.io.EclipseEOSExporter;
import java.nio.file.Path;

// Export with default settings (METRIC units)
EclipseEOSExporter.toFile(fluid, Path.of("PVT.INC"));

// Or get as string
String eclipseOutput = EclipseEOSExporter.toString(fluid);
```

### Configuration Options

```java
import neqsim.blackoil.io.EclipseEOSExporter;
import neqsim.blackoil.io.EclipseEOSExporter.ExportConfig;
import neqsim.blackoil.io.EclipseEOSExporter.Units;

ExportConfig config = new ExportConfig()
    .setUnits(Units.FIELD)                              // METRIC or FIELD
    .setIncludeHeader(true)                             // Include header comments
    .setComment("Generated for Field X, Well A-1")      // Custom comment
    .setPressureGrid(new double[]{50, 100, 150, 200, 250, 300, 350, 400})
    .setIncludePVTO(true)                               // Include live oil table
    .setIncludePVTG(true)                               // Include wet gas table
    .setIncludePVTW(true)                               // Include water properties
    .setIncludeDensity(true);                           // Include DENSITY keyword

String output = EclipseEOSExporter.toString(fluid, config);
```

### Export from Pre-computed PVT Table

```java
// If you already have a BlackOilPVTTable
double rhoOilSc = 820.0;   // Oil density at standard conditions (kg/m³)
double rhoGasSc = 1.2;     // Gas density at standard conditions (kg/m³)
double rhoWaterSc = 1000.0; // Water density at standard conditions (kg/m³)

EclipseEOSExporter.toFile(pvtTable, rhoOilSc, rhoGasSc, rhoWaterSc, Path.of("PVT.INC"));
```

### Eclipse Output Format

The exporter generates the following Eclipse keywords:

#### DENSITY
```
DENSITY
-- Oil      Gas       Water
   820.0    1.200000  1000.0 /
```

#### PVTO (Live Oil)
```
PVTO
-- Rs       P         Bo        mu_o
   50.0     100.0     1.250     0.00080
            150.0     1.220     0.00085
            200.0     1.200     0.00090 /
   80.0     150.0     1.350     0.00070
            200.0     1.320     0.00075 /
/
```

#### PVTG (Wet Gas)
```
PVTG
-- Pg       Rv        Bg        mu_g
   100.0    0.0       0.0120    1.50E-05
            0.0001    0.0122    1.52E-05 /
   150.0    0.0       0.0080    1.80E-05 /
/
```

#### PVTW (Water)
```
PVTW
-- Pref     Bw        Cw        mu_w    Cv
   200.0    1.020     4.5E-05   0.00050 0.0 /
```

### Unit Systems

| Property | METRIC | FIELD |
|----------|--------|-------|
| Pressure | bar (BARSA) | psia (PSIA) |
| Density | kg/m³ | lb/ft³ |
| FVF | rm³/sm³ | rb/stb |
| GOR | sm³/sm³ | scf/stb |
| Viscosity | cP | cP |

## CMG Export

The `CMGEOSExporter` generates PVT data files compatible with CMG reservoir simulators (IMEX, GEM, STARS).

### Basic Usage

```java
import neqsim.blackoil.io.CMGEOSExporter;
import java.nio.file.Path;

// Export with default settings (IMEX, SI units)
CMGEOSExporter.toFile(fluid, Path.of("PVT.DAT"));

// Or get as string
String cmgOutput = CMGEOSExporter.toString(fluid);
```

### Configuration Options

```java
import neqsim.blackoil.io.CMGEOSExporter;
import neqsim.blackoil.io.CMGEOSExporter.ExportConfig;
import neqsim.blackoil.io.CMGEOSExporter.Simulator;
import neqsim.blackoil.io.CMGEOSExporter.Units;

ExportConfig config = new ExportConfig()
    .setSimulator(Simulator.IMEX)                       // IMEX, GEM, or STARS
    .setUnits(Units.FIELD)                              // SI or FIELD
    .setModelName("RESERVOIR_FLUID_MODEL")              // Model identifier
    .setComment("PVT model for Field X")                // Custom comment
    .setPressureGrid(new double[]{50, 100, 150, 200, 250, 300});

CMGEOSExporter.toFile(fluid, Path.of("PVT.DAT"), config);
```

### CMG Simulators

| Simulator | Type | Description |
|-----------|------|-------------|
| **IMEX** | Black Oil | Implicit-Explicit black oil simulator |
| **GEM** | Compositional | Generalized equation-of-state model |
| **STARS** | Thermal | Steam, thermal, and advanced processes |

### IMEX Output Format

```
** ============================================================
** Generated by NeqSim - Black Oil PVT Export
** Simulator: IMEX
** Units: SI
** ============================================================

*MODEL *BLACKOIL

*DENSITY *OIL 820.0
*DENSITY *GAS 1.2
*DENSITY *WATER 1000.0

*BOTOIL
** P(kPa)    Rs(m3/m3)    Bo(m3/m3)    mu_o(cP)
   10000.0   50.0         1.250        0.80
   15000.0   80.0         1.350        0.70
   20000.0   100.0        1.420        0.65

*BOTGAS
** P(kPa)    Bg(m3/m3)    mu_g(cP)
   10000.0   0.0120       0.015
   15000.0   0.0080       0.018
   20000.0   0.0060       0.021
```

### Unit Systems

| Property | SI | FIELD |
|----------|-----|-------|
| Pressure | kPa | psia |
| Density | kg/m³ | lb/ft³ |
| FVF | m³/m³ | bbl/bbl |
| GOR | m³/m³ | scf/bbl |
| Viscosity | cP | cP |

## Complete Workflow Example

```java
import neqsim.blackoil.BlackOilConverter;
import neqsim.blackoil.BlackOilPVTTable;
import neqsim.blackoil.io.EclipseEOSExporter;
import neqsim.blackoil.io.CMGEOSExporter;
import neqsim.thermo.system.SystemSrkEos;
import java.nio.file.Path;

public class PVTExportExample {
    public static void main(String[] args) {
        // 1. Create compositional fluid model
        var fluid = new SystemSrkEos(373.15, 250.0);
        fluid.addComponent("methane", 0.60);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("n-butane", 0.03);
        fluid.addComponent("n-pentane", 0.02);
        fluid.addComponent("n-heptane", 0.12);
        fluid.addComponent("water", 0.10);
        fluid.setMixingRule("classic");
        
        // 2. Export to Eclipse (METRIC units)
        var eclipseConfig = new EclipseEOSExporter.ExportConfig()
            .setUnits(EclipseEOSExporter.Units.METRIC)
            .setComment("Light oil reservoir - Block A");
        EclipseEOSExporter.toFile(fluid, Path.of("eclipse_pvt.inc"), eclipseConfig);
        
        // 3. Export to CMG IMEX (FIELD units)
        var cmgConfig = new CMGEOSExporter.ExportConfig()
            .setSimulator(CMGEOSExporter.Simulator.IMEX)
            .setUnits(CMGEOSExporter.Units.FIELD)
            .setModelName("BLOCK_A_FLUID");
        CMGEOSExporter.toFile(fluid, Path.of("cmg_pvt.dat"), cmgConfig);
        
        System.out.println("PVT files exported successfully!");
    }
}
```

## E300 Compositional EOS Export

In addition to black-oil PVT tables, NeqSim can export the full compositional EOS model to Eclipse E300 format. This is useful when you need to preserve all EOS parameters for compositional reservoir simulation.

### E300 Format Background

The Eclipse E300 format is a **proprietary format** originated by Schlumberger for their Eclipse compositional reservoir simulator. While there is no public formal specification, the format is widely used and supported by:

- **Schlumberger Eclipse 300** - The original compositional simulator
- **OPM Flow** - Open-source simulator with E300 compatibility (see [OPM Reference Manual](https://opm-project.org/?page_id=955))
- **PVTsim Nova** by Calsep - Commercial PVT software with E300 export
- **Other commercial simulators** that read Eclipse-compatible files

The NeqSim E300 export is compatible with files generated by PVTsim Nova and produces output that can be read by Eclipse 300 and OPM Flow.

### Basic Usage

```java
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;

// Export fluid to E300 compositional format
EclipseFluidReadWrite.write(fluid, "RESERVOIR_FLUID.e300", 100.0);  // 100°C reservoir temp

// Get as string for inspection
String e300Content = EclipseFluidReadWrite.toE300String(fluid, 100.0);
```

### E300 File Keywords

The exported E300 file contains all EOS parameters needed for compositional simulation:

| Keyword | Description | Units |
|---------|-------------|-------|
| `METRIC` | Units system (always METRIC) | - |
| `NCOMPS` | Number of components | - |
| `EOS` | Equation of state type | SRK/PR |
| `PRCORR` | Peng-Robinson correction (for PR EOS only) | - |
| `RTEMP` | Reservoir temperature | °C |
| `STCOND` | Standard conditions | °C, bara |
| `CNAMES` | Component names | - |
| `TCRIT` | Critical temperatures | K |
| `PCRIT` | Critical pressures | bar |
| `ACF` | Acentric factors | - |
| `OMEGAA` | EOS parameter a (0.45724 for PR, 0.42748 for SRK) | - |
| `OMEGAB` | EOS parameter b (0.07780 for PR, 0.08664 for SRK) | - |
| `MW` | Molecular weights | g/mol |
| `TBOIL` | Normal boiling points | K |
| `VCRIT` | Critical volumes | m³/kmol |
| `ZCRIT` | Critical Z-factors | - |
| `SSHIFT` | Volume translation | - |
| `PARACHOR` | Parachor values | - |
| `ZI` | Mole fractions | - |
| `BIC` | Binary interaction coefficients | - |

### Round-Trip: Export and Import

```java
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and tune fluid
SystemInterface tunedFluid = /* ... your tuned fluid ... */;

// Export to E300 file
EclipseFluidReadWrite.write(tunedFluid, "TUNED.e300", 100.0);

// Read it back (e.g., in another session or application)
SystemInterface importedFluid = EclipseFluidReadWrite.read("TUNED.e300");

// Use the imported fluid
importedFluid.setPressure(200.0, "bara");
importedFluid.setTemperature(100.0, "C");
new ThermodynamicOperations(importedFluid).TPflash();
```

### Optional E300 Keywords

Some E300 files include additional keywords that are handled by NeqSim:

| Keyword | Description | Status in NeqSim |
|---------|-------------|-----------------|
| `BICS` | BIC at surface conditions | **Supported** (read and write) |
| `SSHIFTS` | Volume shift at surface conditions | **Supported** (read and write) |
| `LBCCOEF` | Lohrenz-Bray-Clark viscosity coefficients | **Supported** (read and write) |
| `PEDERSEN` | Pedersen viscosity correlation flag | **Supported** (read and write) |

#### LBCCOEF Support

NeqSim supports reading and writing the `LBCCOEF` keyword for the Lohrenz-Bray-Clark (LBC) viscosity correlation:

```
LBCCOEF
0.1084806 -0.0295031 0.1130421 -0.0553108 0.0093324 /
```

When reading an E300 file with `LBCCOEF`:
- NeqSim automatically applies the LBC viscosity model to all phases
- The 5 coefficients are set as the dense contribution parameters

When writing an E300 file:
- If the fluid uses the LBC viscosity model, the coefficients are exported

#### PEDERSEN Support

The `PEDERSEN` keyword is a flag indicating the Pedersen (PFCT) corresponding-states viscosity correlation:

```
PEDERSEN
```

When reading: NeqSim applies the PFCT viscosity model to all phases.
When writing: If PFCT viscosity model is active, the `PEDERSEN` keyword is output.

#### BICS and SSHIFTS Support

- **BICS**: Binary interaction coefficients at surface conditions (same format as BIC)
- **SSHIFTS**: Volume translation at surface conditions (same format as SSHIFT)

These are written automatically when exporting E300 files.

### Compatibility Notes

- **SRK vs PR**: The EOS type is auto-detected from the fluid class name. Peng-Robinson fluids (`SystemPrEos`, etc.) export with `PR` and include the `PRCORR` keyword.
- **Component Names**: Long component names are automatically shortened (e.g., "methane" → "C1", "n-butane" → "C4").
- **Binary Interaction Coefficients**: The lower triangular BIC matrix is exported.
- **OPM Flow**: The generated files are compatible with OPM Flow's compositional mode.

### Python Usage

```python
from jpype import JClass

EclipseFluidReadWrite = JClass('neqsim.thermo.util.readwrite.EclipseFluidReadWrite')

# Export to E300 file
EclipseFluidReadWrite.write(fluid, "tuned_fluid.e300", 100.0)

# Read back
imported_fluid = EclipseFluidReadWrite.read("tuned_fluid.e300")
```

## Integration with Whitson PVT Workflows

The export functionality enables seamless integration with PVT software like whitsonPVT:

1. **EOS Tuning** - Tune EOS parameters using experimental PVT data in NeqSim
2. **C7+ Characterization** - Use Whitson's Gamma model for plus-fraction splitting
3. **Export** - Generate simulator-ready PVT files for reservoir engineering

```java
// After EOS tuning and characterization
SystemInterface tunedFluid = /* ... tuned fluid model ... */;

// Export for reservoir simulation workflow
EclipseEOSExporter.toFile(tunedFluid, Path.of("TUNED_PVT.INC"));
```

## API Reference

### EclipseFluidReadWrite (Compositional EOS)

| Method | Description |
|--------|-------------|
| `read(String)` | Read E300 file into NeqSim fluid |
| `write(SystemInterface, String)` | Write fluid to E300 file |
| `write(SystemInterface, String, double)` | Write with reservoir temp (°C) |
| `toE300String(SystemInterface)` | Export to E300 format string |
| `toE300String(SystemInterface, double)` | Export with reservoir temp |

### EclipseEOSExporter (Black-Oil PVT)

| Method | Description |
|--------|-------------|
| `toString(SystemInterface)` | Export fluid to Eclipse format string |
| `toString(SystemInterface, ExportConfig)` | Export with configuration |
| `toString(BlackOilPVTTable, rhoO, rhoG, rhoW)` | Export PVT table |
| `toFile(SystemInterface, Path)` | Write to file |
| `toFile(SystemInterface, Path, ExportConfig)` | Write with configuration |

### CMGEOSExporter

| Method | Description |
|--------|-------------|
| `toString(SystemInterface)` | Export fluid to CMG format string |
| `toString(SystemInterface, ExportConfig)` | Export with configuration |
| `toString(BlackOilPVTTable, rhoO, rhoG, rhoW)` | Export PVT table |
| `toFile(SystemInterface, Path)` | Write to file |
| `toFile(SystemInterface, Path, ExportConfig)` | Write with configuration |

## See Also

- [PVT Experiments](thermo/pvt_experiments.md) - CCE, CVD, DLE simulations
- [C7+ Characterization](thermo/c7plus_characterization.md) - Plus-fraction modeling
- [Thermodynamic Models](modules.md) - EOS and mixing rules
