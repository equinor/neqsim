---
title: "Mercury Removal Guard Beds"
description: "Process equipment documentation for the MercuryRemovalBed unit operation. Covers chemisorption modelling with PuraSpec-type sorbents, transient bed loading, breakthrough detection, degradation effects, mechanical design, and cost estimation for LNG pre-treatment mercury guard beds."
---

# Mercury Removal Guard Beds

## Overview

The `MercuryRemovalBed` class (`neqsim.process.equipment.adsorber`) models fixed-bed chemisorption of elemental mercury (Hg$^0$) onto metal-sulphide sorbents — the standard mercury removal technology in LNG pre-treatment and gas processing plants.

**Location:** `neqsim.process.equipment.adsorber.MercuryRemovalBed`

**Related classes:**

| Class                            | Package                             | Purpose                                     |
| -------------------------------- | ----------------------------------- | ------------------------------------------- |
| `MercuryRemovalBed`              | `process.equipment.adsorber`        | Unit operation — process simulation         |
| `MercuryRemovalMechanicalDesign` | `process.mechanicaldesign.adsorber` | Pressure vessel sizing and weight breakdown |
| `MercuryRemovalCostEstimate`     | `process.costestimation.adsorber`   | CAPEX and OPEX estimation                   |

---

## Why Mercury Removal Matters

Mercury must be removed from natural gas before cryogenic processing because:

1. **Aluminium embrittlement** — Even ng/Nm$^3$ levels of Hg can cause catastrophic failure of brazed aluminium heat exchangers (BAHX) in LNG and NGL plants
2. **Catalyst poisoning** — Pd/Pt catalysts in downstream hydrogenation and reforming are sensitive to mercury
3. **Environmental compliance** — Product specifications typically require Hg < 0.01 µg/Nm$^3$
4. **Health & safety** — TLV-TWA for Hg$^0$ vapour is 0.025 mg/m$^3$

### Typical Inlet Concentrations

| Source         | Hg Content (µg/Nm$^3$) |
| -------------- | ---------------------- |
| North Sea gas  | 0.01 – 10              |
| SE Asian gas   | 10 – 300               |
| Algerian gas   | 50 – 200               |
| Australian gas | 1 – 100                |

---

## Technology Background

### Chemisorption Mechanism

Mercury guard beds use metal sulphide sorbents (CuS, ZnS, FeS) supported on alumina or carbon carriers. The key reaction is an irreversible sulphide exchange:

$$\text{Hg}^0_{(g)} + \text{CuS}_{(s)} \rightarrow \text{HgS}_{(s)} + \text{Cu}_{(s)}$$

This differs fundamentally from physical adsorption:

| Property      | Physical Adsorption  | Mercury Chemisorption        |
| ------------- | -------------------- | ---------------------------- |
| Bonding       | Van der Waals        | Chemical (covalent)          |
| Reversibility | Reversible (TSA/PSA) | Irreversible                 |
| Regeneration  | Yes                  | No — bed replaced when spent |
| Capacity      | Lower                | Higher (up to 25 wt%)        |
| Selectivity   | Low–moderate         | High for Hg$^0$              |

### Commercial Sorbents

| Product       | Vendor          | Active Phase             | Typical Capacity |
| ------------- | --------------- | ------------------------ | ---------------- |
| PuraSpec 1156 | Johnson Matthey | CuS/Al$_2$O$_3$          | 10–25 wt% Hg     |
| MRU           | Various         | CuS or CuS/ZnS           | 8–15 wt% Hg      |
| HgSIV         | UOP             | Silver-exchanged zeolite | Lower (~5 wt%)   |

---

## Mathematical Model

### Kinetics

The model uses an irreversible Langmuir-Hinshelwood rate law:

$$r = k_{eff}(T) \cdot C_{Hg} \cdot (1 - \theta)$$

where:
- $r$ is the reaction rate (µg/Nm$^3$/s)
- $k_{eff}$ is the temperature-corrected rate constant (1/s)
- $C_{Hg}$ is the local gas-phase mercury concentration (µg/Nm$^3$)
- $\theta = q / q_{max}$ is the fractional saturation of the sorbent

### Temperature Dependence

The rate constant follows Arrhenius behaviour:

$$k_{eff}(T) = k_{ref} \cdot \exp\left[-\frac{E_a}{R}\left(\frac{1}{T} - \frac{1}{T_{ref}}\right)\right]$$

where $E_a$ is the activation energy (default 25 kJ/mol) and $T_{ref}$ is the reference temperature for $k_{ref}$.

### Steady-State Mode

In steady state, the bed is treated as a single NTU unit:

$$\eta = (1 - f_{bypass}) \cdot (1 - e^{-NTU})$$

where $NTU = k_{eff} \cdot \tau_{res} \cdot F_{deg}$ with $\tau_{res}$ being the gas residence time in the void volume, and $F_{deg}$ the degradation factor.

### Transient Mode

For time-dependent simulation, the bed is discretised into $N$ axial cells. Each time step consists of:

1. **Convective transport** (upwind scheme): gas-phase Hg moves from cell to cell
2. **Chemisorption reaction**: mercury is consumed from the gas phase and accumulated on the sorbent in each cell
3. **CFL-limited sub-stepping**: automatically chosen for numerical stability

The cell-level mass balances are:

$$\frac{\partial C_{Hg}}{\partial t} + u_{int}\frac{\partial C_{Hg}}{\partial z} = -k_{eff} \cdot C_{Hg} \cdot (1 - \theta)$$

$$\frac{\partial q}{\partial t} = k_{eff} \cdot C_{Hg} \cdot (1 - \theta) \cdot \frac{V_{void}}{m_{sorbent}} \cdot 10^{-3}$$

### Pressure Drop

The Ergun equation provides the bed pressure drop:

$$\frac{\Delta P}{L} = 150 \frac{\mu \cdot u_s \cdot (1-\varepsilon)^2}{\varepsilon^3 \cdot d_p^2} + 1.75 \frac{\rho \cdot u_s^2 \cdot (1-\varepsilon)}{\varepsilon^3 \cdot d_p}$$

### Degradation Model

Two parameters model degraded column internals:

| Parameter                 | Effect                                            | Typical Cause                                   |
| ------------------------- | ------------------------------------------------- | ----------------------------------------------- |
| `degradationFactor` (0–1) | Reduces effective capacity $q_{max}$ and rate $k$ | Sorbent fouling, liquid carry-over              |
| `bypassFraction` (0–1)    | Gas bypasses the sorbent entirely                 | Channelling from damaged bed support, wall gaps |

The combined effect reduces removal efficiency significantly. For example, a bed with `degradationFactor = 0.6` and `bypassFraction = 0.1` will have both lower capacity and 10% of the gas untreated.

---

## API Reference

### Construction

```java
// Name only (configure later)
MercuryRemovalBed bed = new MercuryRemovalBed("Hg Guard");

// Name + inlet stream
MercuryRemovalBed bed = new MercuryRemovalBed("Hg Guard", feedStream);
```

### Bed Geometry

| Method                    | Parameter               | Unit | Default |
| ------------------------- | ----------------------- | ---- | ------- |
| `setBedDiameter(d)`       | Internal diameter       | m    | 1.5     |
| `setBedLength(L)`         | Packed section height   | m    | 4.0     |
| `setVoidFraction(eps)`    | Inter-particle porosity | —    | 0.40    |
| `setParticleDiameter(dp)` | Sorbent pellet diameter | m    | 0.004   |

### Sorbent Properties

| Method                       | Parameter         | Unit             | Default    |
| ---------------------------- | ----------------- | ---------------- | ---------- |
| `setSorbentType(name)`       | Trade name / type | —                | "PuraSpec" |
| `setSorbentBulkDensity(rho)` | Bulk density      | kg/m$^3$         | 1100       |
| `setMaxMercuryCapacity(q)`   | Max Hg loading    | mg Hg/kg sorbent | 100,000    |

### Kinetics

| Method                       | Parameter                     | Unit  | Default |
| ---------------------------- | ----------------------------- | ----- | ------- |
| `setReactionRateConstant(k)` | LDF rate constant             | 1/s   | 0.05    |
| `setActivationEnergy(Ea)`    | Arrhenius activation energy   | J/mol | 25,000  |
| `setReferenceTemperature(T)` | Reference temperature for $k$ | K     | 298.15  |

### Degradation

| Method                    | Parameter                | Range  | Default |
| ------------------------- | ------------------------ | ------ | ------- |
| `setDegradationFactor(f)` | Capacity/rate multiplier | 0–1    | 1.0     |
| `setBypassFraction(f)`    | Gas bypass fraction      | 0–0.99 | 0.0     |

### Transient Simulation

| Method                           | Description                                  |
| -------------------------------- | -------------------------------------------- |
| `setCalculateSteadyState(false)` | Enable transient mode                        |
| `setNumberOfCells(n)`            | Set axial discretisation (default 50)        |
| `initialiseTransientGrid()`      | Reset grid to empty bed                      |
| `runTransient(dt, id)`           | Advance by `dt` seconds                      |
| `preloadBed(fraction)`           | Pre-load to simulate spent bed               |
| `resetBed()`                     | Reset to fresh bed                           |
| `setBreakthroughThreshold(r)`    | Set C/C$_0$ ratio for breakthrough detection |

### Results Queries

| Method                                         | Returns                       | Unit      |
| ---------------------------------------------- | ----------------------------- | --------- |
| `getRemovalEfficiency()`                       | Mercury removal fraction      | 0–1       |
| `getAverageLoading()`                          | Mean sorbent loading          | mg/kg     |
| `getBedUtilisation()`                          | Average loading / capacity    | 0–1       |
| `getLoadingProfile()`                          | Per-cell loading array        | mg/kg     |
| `getConcentrationProfile()`                    | Per-cell gas Hg concentration | µg/Nm$^3$ |
| `getMassTransferZoneLength()`                  | MTZ length                    | m         |
| `estimateBedLifetime()`                        | Estimated time to exhaustion  | hours     |
| `getPressureDrop()` / `getPressureDrop("bar")` | Bed pressure drop             | Pa or bar |
| `isBreakthroughOccurred()`                     | Whether breakthrough detected | boolean   |
| `getBreakthroughTimeHours()`                   | Time of breakthrough          | hours     |
| `getSorbentMass()`                             | Total sorbent in bed          | kg        |
| `getBedVolume()`                               | Packed bed volume             | m$^3$     |
| `toJson()`                                     | Full JSON report              | String    |

---

## Mechanical Design

The `MercuryRemovalMechanicalDesign` class sizes the pressure vessel:

```java
MercuryRemovalMechanicalDesign design = bed.getMechanicalDesign();
design.setMaxOperationPressure(60.0);       // bara
design.setMaxOperationTemperature(353.15);  // K
design.calcDesign();

System.out.println("Wall thickness: " + design.getWallThickness() + " mm");
System.out.println("Total weight:   " + design.getWeightTotal() + " kg");
System.out.println(design.toJson());
```

### Design Outputs

| Output                | Method                                 | Unit |
| --------------------- | -------------------------------------- | ---- |
| Wall thickness        | `getWallThickness()`                   | mm   |
| Inner/outer diameter  | `innerDiameter` / `getOuterDiameter()` | m    |
| Tan-tan length        | `tantanLength`                         | m    |
| Vessel shell weight   | `getWeigthVesselShell()`               | kg   |
| Internals weight      | `getInternalsWeight()`                 | kg   |
| Sorbent charge weight | `getSorbentChargeWeight()`             | kg   |
| Total skid weight     | `getWeightTotal()`                     | kg   |
| Module footprint      | `getModuleWidth/Length/Height()`       | m    |
| Bill of materials     | `generateBillOfMaterials()`            | List |

### Wall Thickness Calculation

Uses the Barlow / hoop-stress formula per ASME VIII Div 1 (simplified):

$$t = \frac{P_d \cdot D}{2 \cdot S \cdot E - P_d}$$

where $P_d$ is design pressure (110% of max operating), $D$ is inner diameter, $S$ is allowable stress (137.9 MPa for SA-516-70), and $E$ is joint efficiency (0.85). A minimum thickness of 6 mm is enforced.

---

## Cost Estimation

The `MercuryRemovalCostEstimate` class provides CAPEX/OPEX:

```java
MercuryRemovalCostEstimate cost = design.getCostEstimate();
cost.setSorbentUnitPrice(25.0);  // USD/kg for PuraSpec
cost.calculateCostEstimate();

System.out.println("Total module cost: " + cost.getTotalModuleCost());
System.out.println("Sorbent replacement: " + cost.getSorbentReplacementCost());
System.out.println(cost.toJson());
```

### Cost Structure

| Element             | Code       | Calculation                                                         |
| ------------------- | ---------- | ------------------------------------------------------------------- |
| Vessel steel cost   | PEC (part) | (shell + internals + nozzles weight) × steel rate × material factor |
| Sorbent charge      | PEC (part) | sorbent mass × unit price                                           |
| Bare module         | BMC        | PEC × bare module factor                                            |
| Total module        | TMC        | BMC × contingency factor                                            |
| Grassroots          | GRC        | TMC × site factor                                                   |
| Sorbent replacement | OPEX       | sorbent mass × unit price × 1.25 (labour)                           |
| Annual maintenance  | OPEX       | TMC × 3%                                                            |

### Adjustable Cost Parameters

| Parameter              | Default   | Method                    |
| ---------------------- | --------- | ------------------------- |
| Sorbent unit price     | 25 USD/kg | `setSorbentUnitPrice()`   |
| Steel fabrication cost | 8 USD/kg  | `setSteelCostPerKg()`     |
| Installation factor    | 1.5       | `setInstallationFactor()` |
| Maintenance factor     | 3%        | (internal)                |

---

## Usage Examples

### Steady-State Example (Java)

```java
SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 60.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.addComponent("nitrogen", 0.04);
gas.addComponent("mercury", 1.0e-9);
gas.createDatabase(true);
gas.setMixingRule(2);
gas.init(0);

Stream feed = new Stream("feed", gas);
feed.setFlowRate(50000.0, "kg/hr");
feed.run();

MercuryRemovalBed bed = new MercuryRemovalBed("HgGuard", feed);
bed.setBedDiameter(1.5);
bed.setBedLength(4.0);
bed.setSorbentType("PuraSpec");
bed.run(UUID.randomUUID());

System.out.println("Efficiency: " + bed.getRemovalEfficiency());
System.out.println("Pressure drop: " + bed.getPressureDrop("bar") + " bar");
```

### Transient Breakthrough Example (Java)

```java
bed.setCalculateSteadyState(false);
bed.setNumberOfCells(50);
bed.initialiseTransientGrid();

UUID id = UUID.randomUUID();
double dt = 3600.0; // 1-hour steps

for (int hour = 0; hour < 10000; hour++) {
    bed.runTransient(dt, id);
    if (bed.isBreakthroughOccurred()) {
        System.out.println("Breakthrough at " + bed.getBreakthroughTimeHours() + " hours");
        break;
    }
}

System.out.println("Average loading: " + bed.getAverageLoading() + " mg/kg");
System.out.println("Utilisation: " + (bed.getBedUtilisation() * 100) + "%");
```

### Python Example

```python
from neqsim import jneqsim

MercuryRemovalBed = jneqsim.process.equipment.adsorber.MercuryRemovalBed
# ... (see Jupyter notebook for full example)
```

---

## Validation

The `validateSetup()` method checks for common configuration errors:

| Check              | Error if   |
| ------------------ | ---------- |
| Bed diameter       | ≤ 0        |
| Bed length         | ≤ 0        |
| Void fraction      | ≤ 0 or ≥ 1 |
| Mercury capacity   | ≤ 0        |
| Degradation factor | < 0 or > 1 |
| Bypass fraction    | < 0 or ≥ 1 |

Each error includes a remediation hint for automated recovery.

---

## Related Documentation

- [Adsorption Bed](equipment/adsorption_bed.md) — Physical adsorption (PSA/TSA) unit operation
- [Adsorption Recipes](../cookbook/adsorption-recipes.md) — Quick-start recipes for adsorption tasks
- [Adsorption Isotherm Models](../thermo/adsorption_isotherms.md) — Thermodynamic adsorption theory
- [Cost Estimation API](COST_ESTIMATION_API_REFERENCE.md) — General cost estimation framework
- [Mechanical Design](mechanical_design.md) — General mechanical design framework
- [Jupyter Notebook Tutorial](../examples/MercuryRemoval_LNG_Pretreatment.ipynb) — Interactive Python tutorial
