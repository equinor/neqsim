---
title: "Relative Permeability Table Generation"
description: "Guide to generating Corey and LET relative permeability curves and Eclipse-format tables using NeqSim's RelativePermeabilityGenerator."
---

# Relative Permeability Table Generation

NeqSim provides a dedicated module for generating relative permeability tables using industry-standard Corey and LET models. The generated tables can be exported in Eclipse-compatible keyword format (SWOF, SGOF, SOF3, SLGOF).

## Overview

**Package:** `neqsim.pvtsimulation.reservoirproperties.relpermeability`

**Key Classes:**

| Class | Description |
|-------|-------------|
| `RelativePermeabilityGenerator` | Main generator for relative permeability tables |
| `RelPermTableType` | Enum for table types: SWOF, SGOF, SOF3, SLGOF |
| `RelPermModelFamily` | Enum for model families: COREY, LET |

## Supported Models

### Corey Power-Law Model (1954)

The Corey model uses a single exponent per phase to define the relative permeability curve:

$$
K_{rw} = K_{rw,max} \cdot S_{wn}^{n_w}
$$

$$
K_{row} = K_{ro,max} \cdot (1 - S_{wn})^{n_o}
$$

where $S_{wn}$ is the normalized water saturation:

$$
S_{wn} = \frac{S_w - S_{wc}}{1 - S_{wc} - S_{orw}}
$$

**Parameters:** Each phase requires one exponent ($n_o$, $n_w$, $n_g$). Typical values range from 1 to 6.

### LET Three-Parameter Model (2005)

The LET model (Lomeland, Ebeltoft, Thomas) provides greater flexibility for matching laboratory core-flood data:

$$
K_r = K_{r,max} \cdot \frac{S_n^L}{S_n^L + E \cdot (1 - S_n)^T}
$$

**Parameters:** Each phase requires three parameters (L, E, T):
- **L** controls low-saturation curvature
- **E** controls mid-range position
- **T** controls high-saturation curvature

## Saturation Endpoints

| Parameter | Symbol | Description |
|-----------|--------|-------------|
| `swc` | $S_{wc}$ | Connate (irreducible) water saturation |
| `swcr` | $S_{wcr}$ | Critical water saturation (onset of water flow) |
| `sorw` | $S_{orw}$ | Residual oil saturation to water |
| `sorg` | $S_{org}$ | Residual oil saturation to gas |
| `sgcr` | $S_{gcr}$ | Critical gas saturation |

## Supported Table Types

| Type | Eclipse Keyword | Columns | Use Case |
|------|----------------|---------|----------|
| SWOF | `SWOF` | Sw, Krw, Krow, Pcow | Water-oil two-phase |
| SGOF | `SGOF` | Sg, Krg, Krog, Pcog | Gas-oil two-phase |
| SOF3 | `SOF3` | So, Krow, Krog | Three-phase oil function |
| SLGOF | `SLGOF` | Sl, Krg, Krog, Pcog | Liquid-gas function |

## Usage Examples

### Water-Oil Corey Curves (Java)

```java
RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
gen.setTableType(RelPermTableType.SWOF);
gen.setModelFamily(RelPermModelFamily.COREY);
gen.setSwc(0.15);
gen.setSorw(0.20);
gen.setKroMax(1.0);
gen.setKrwMax(0.25);
gen.setNo(2.5);
gen.setNw(1.5);
gen.setRows(25);

Map<String, double[]> table = gen.generate();

double[] sw = table.get("Sw");
double[] krw = table.get("Krw");
double[] krow = table.get("Krow");
```

### Gas-Oil LET Curves (Java)

```java
RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
gen.setTableType(RelPermTableType.SGOF);
gen.setModelFamily(RelPermModelFamily.LET);
gen.setSwc(0.20);
gen.setSorg(0.15);
gen.setKroMax(1.0);
gen.setKrgMax(1.0);
gen.setLog(2.5);
gen.setEog(1.25);
gen.setTog(1.75);
gen.setLg(1.2);
gen.setEg(1.5);
gen.setTg(2.0);
gen.setRows(25);

Map<String, double[]> table = gen.generate();
```

### Export to Eclipse Format

```java
RelativePermeabilityGenerator gen = new RelativePermeabilityGenerator();
gen.setTableType(RelPermTableType.SWOF);
gen.setSwc(0.15);
gen.setSorw(0.20);
gen.setNo(2.5);
gen.setNw(1.5);
gen.setRows(20);

String eclipseKeyword = gen.toEclipseKeyword();
// Output:
// SWOF
// -- Sw       Krw       Krow       Pcow
//    0.15000000  0.00000000  1.00000000  0.00000000
//    ...
// /
```

### Python (via neqsim-python)

```python
from neqsim import jneqsim

RelPermGenerator = jneqsim.pvtsimulation.reservoirproperties.relpermeability.RelativePermeabilityGenerator
RelPermTableType = jneqsim.pvtsimulation.reservoirproperties.relpermeability.RelPermTableType
RelPermModelFamily = jneqsim.pvtsimulation.reservoirproperties.relpermeability.RelPermModelFamily

gen = RelPermGenerator()
gen.setTableType(RelPermTableType.SWOF)
gen.setModelFamily(RelPermModelFamily.COREY)
gen.setSwc(0.15)
gen.setSorw(0.20)
gen.setKroMax(1.0)
gen.setKrwMax(0.25)
gen.setNo(2.5)
gen.setNw(1.5)
gen.setRows(25)

table = gen.generate()

# Plot results
import matplotlib.pyplot as plt
sw = list(table.get("Sw"))
krw = list(table.get("Krw"))
krow = list(table.get("Krow"))

plt.plot(sw, krw, 'b-', label='Krw')
plt.plot(sw, krow, 'g-', label='Krow')
plt.xlabel('Sw')
plt.ylabel('Kr')
plt.title('SWOF Corey Relative Permeability')
plt.legend()
plt.grid(True)
plt.show()
```

## Critical vs Connate Water Saturation

The generator distinguishes between connate water saturation ($S_{wc}$) and critical water saturation ($S_{wcr}$):

- **$S_{wc}$**: Irreducible water saturation — the minimum Sw in the table
- **$S_{wcr}$**: Onset of water flow — Krw = 0 for all Sw below this value

When `swcr` is not explicitly set, it defaults to `swc`. Setting `swcr > swc` creates a region where water is present but immobile (Krw = 0).

## Related Documentation

- [PVT Simulation Package](README.md)
- [Black Oil PVT Export](blackoil_pvt_export.md)
