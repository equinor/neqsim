---
title: Black-Oil Package
description: Build, flash, convert, and export black-oil PVT models with the current NeqSim API.
---

The `neqsim.blackoil` package supports pressure-dependent black-oil PVT tables,
three-component black-oil flashes, conversion from compositional equations of
state (EOS), and Eclipse or CMG table export.

## Package overview

| Class | Purpose |
| --- | --- |
| `BlackOilPVTTable` | Stores pressure-indexed PVT records and interpolates between them |
| `BlackOilFlash` | Splits standard oil, gas, and water totals at one pressure |
| `BlackOilFlashResult` | Reports standard totals, reservoir volumes, densities, viscosities, and PVT values |
| `BlackOilConverter` | Generates a table and `SystemBlackOil` from a compositional fluid |
| `SystemBlackOil` | Wraps a PVT table as a NeqSim thermodynamic system |
| `EclipseEOSExporter` | Writes Eclipse-compatible black-oil tables |
| `CMGEOSExporter` | Writes CMG IMEX, GEM, or STARS tables |

The table uses one consistent pressure unit, normally bar. Recommended property
units are:

| Property | Unit |
| --- | --- |
| `Rs`, `Rv` | Sm³/Sm³ |
| `Bo`, `Bg`, `Bw` | Rm³/Sm³ |
| `mu_o`, `mu_g`, `mu_w` | Pa·s |
| Standard-condition densities | kg/m³ |

The model does not apply Standing, Vasquez-Beggs, or another empirical
correlation internally. A manually supplied table contains the user's input
data; `BlackOilConverter` obtains its table from EOS flash calculations.

## Create and interpolate a PVT table

Each record has the constructor order
`(pressure, Rs, Bo, mu_o, Bg, mu_g, Rv, Bw, mu_w)`.

```java
import java.util.Arrays;
import java.util.List;
import neqsim.blackoil.BlackOilPVTTable;
import neqsim.blackoil.BlackOilPVTTable.Record;

List<Record> records = Arrays.asList(
    new Record(100.0, 100.0, 1.30, 1.2e-3, 8.0e-3, 1.6e-5,
        0.0, 1.01, 1.0e-3),
    new Record(200.0, 140.0, 1.40, 9.0e-4, 5.0e-3, 1.8e-5,
        0.0, 1.02, 9.0e-4),
    new Record(300.0, 160.0, 1.45, 8.0e-4, 3.0e-3, 2.0e-5,
        0.0, 1.03, 8.0e-4));

double bubblePointPressure = 250.0; // same pressure unit as the records
BlackOilPVTTable pvt = new BlackOilPVTTable(
    records, bubblePointPressure);

double pressure = 175.0;
double rs = pvt.Rs(pressure);
double bo = pvt.Bo(pressure);
double bg = pvt.Bg(pressure);
double oilViscosity = pvt.mu_o(pressure);
double gasViscosity = pvt.mu_g(pressure);
```

Interpolation is linear between adjacent records and clamps to the nearest
record outside the supplied pressure range. `RsEffective(pressure)` returns
`Rs` at the bubble point for pressures above the bubble point.

## Flash standard totals

For a table with vaporized-oil ratio `Rv`, the flash solves the standard gas
and oil split below. It uses `RsEffective(pressure)`, so solution GOR is clamped
to the bubble-point value above the bubble point.

$G_f^{sc} = \frac{G_t^{sc} - R_s^{eff} O_t^{sc}}{1 - R_s^{eff} R_v}$
$O_l^{sc} = O_t^{sc} - R_v G_f^{sc}$
$V_o = B_o O_l^{sc},\qquad V_g = B_g G_f^{sc},\qquad V_w = B_w W^{sc}$

When `Gtot_std <= RsEffective(pressure) * Otot_std`, `BlackOilFlash` reports no
free gas and retains the supplied stock-tank oil total. Computed free-gas and
liquid-oil totals are also bounded at zero, preventing negative phase volumes.
For the common `Rv = 0` case above the gas threshold, free gas equals total gas
minus dissolved gas.

```java
import neqsim.blackoil.BlackOilFlash;
import neqsim.blackoil.BlackOilFlashResult;

double oilDensitySc = 850.0;   // kg/m³
double gasDensitySc = 0.85;    // kg/m³
double waterDensitySc = 1000.0; // kg/m³

BlackOilFlash flash = new BlackOilFlash(
    pvt, oilDensitySc, gasDensitySc, waterDensitySc);

BlackOilFlashResult result = flash.flash(
    200.0,     // pressure, same unit as the table
    373.15,    // K; the table represents this reference temperature
    1000.0,    // total stock-tank oil, Sm³
    150000.0,  // total standard gas, Sm³
    500.0);    // standard water, Sm³

double oilVolume = result.V_o;       // reservoir m³
double gasVolume = result.V_g;       // reservoir m³
double waterVolume = result.V_w;     // reservoir m³
double oilDensity = result.rho_o;    // kg/m³
double oilViscosity = result.mu_o;   // Pa·s
double freeGasSc = result.Gf_std;    // Sm³
```

`BlackOilFlash` treats temperature as metadata because one table represents one
reference temperature. Build separate tables when temperature dependence is
material.

## Convert a compositional fluid

`BlackOilConverter` exposes a static `convert` method. The returned `Result`
contains the generated table, standard-condition densities, the detected
bubble point, and a configured `SystemBlackOil`.

```java
import neqsim.blackoil.BlackOilConverter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

SystemInterface oil = new SystemPrEos(373.15, 300.0);
oil.addComponent("nitrogen", 0.005);
oil.addComponent("CO2", 0.010);
oil.addComponent("methane", 0.350);
oil.addComponent("ethane", 0.070);
oil.addComponent("propane", 0.065);
oil.addComponent("i-butane", 0.025);
oil.addComponent("n-butane", 0.040);
oil.addComponent("i-pentane", 0.020);
oil.addComponent("n-pentane", 0.025);
oil.addComponent("n-hexane", 0.050);
oil.addComponent("n-heptane", 0.080);
oil.addComponent("n-octane", 0.080);
oil.addComponent("n-nonane", 0.060);
oil.addComponent("nC10", 0.120);
oil.setMixingRule("classic");
oil.useVolumeCorrection(true);
oil.setMultiPhaseCheck(true);

double[] pressureGrid = {
    25.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0
};

BlackOilConverter.Result converted = BlackOilConverter.convert(
    oil,
    373.15,      // table reference temperature, K
    pressureGrid,
    1.01325,     // standard pressure, bar
    288.15);     // standard temperature, K

double bubblePoint = converted.bubblePoint;
double oilDensitySc = converted.rho_o_sc;
double gasDensitySc = converted.rho_g_sc;
double boAt100Bar = converted.pvt.Bo(100.0);
```

The converter sorts the pressure grid. Supply at least two points that cover
the intended operating range. The recent phase-detection and property
initialization fixes ensure NeqSim oil phases and finite oil, gas, and water
viscosities are handled; callers should still reject non-finite values before
using a generated deck.

## Export tables

Use the current simulator-specific exporters. There is no
`BlackOilTableExporter` class.

```java
import neqsim.blackoil.io.CMGEOSExporter;
import neqsim.blackoil.io.EclipseEOSExporter;

double waterDensitySc = 1000.0; // kg/m³; dry example has no aqueous phase

String eclipseDeck = EclipseEOSExporter.toString(
    converted.pvt,
    converted.rho_o_sc,
    converted.rho_g_sc,
    waterDensitySc);

String cmgDeck = CMGEOSExporter.toString(
    converted.pvt,
    converted.rho_o_sc,
    converted.rho_g_sc,
    waterDensitySc);
```

To write files, call `EclipseEOSExporter.toFile(...)` or
`CMGEOSExporter.toFile(...)` with a `java.nio.file.Path`. Both exporters also
provide `ExportConfig` overloads for pressure grids, unit systems, comments,
and simulator-specific options. Validate generated files in the target
simulator before production use.

## Engineering checks

- Use a pressure grid dense enough around the saturation pressure.
- Verify `Rs`, `Bo`, `Bg`, and all viscosities are finite and positive where
  the corresponding phase exists.
- Confirm the table's standard pressure, temperature, and density basis.
- Compare generated tables with laboratory PVT data or a tuned compositional
  model.
- Treat gas-condensate, near-critical, and strongly temperature-dependent
  fluids with a compositional model unless the black-oil approximation has
  been independently validated.

## Related documentation

- [PVT simulation](../pvtsimulation/)
- [PVT workflow](../pvtsimulation/pvt_workflow)
- [Black-oil PVT export](../pvtsimulation/blackoil_pvt_export)
