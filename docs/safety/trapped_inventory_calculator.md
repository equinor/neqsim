---
title: Trapped Inventory Calculator
description: Evidence-linked trapped gas and liquid inventory calculation for isolation, blowdown, flare-load, and MDMT screening studies using TrappedInventoryCalculator.
---

`neqsim.process.safety.inventory.TrappedInventoryCalculator` converts documented equipment and pipe volumes into a traceable trapped-inventory result. Use it when a technical-safety study needs a source term for blowdown, relief, flare-load, closed-flare, maintenance isolation, or MDMT screening.

The class is intended to sit between evidence extraction and dynamic safety calculations:

```text
P&ID / STID / line list / datasheet evidence
  -> equipment and pipe segment volumes
  -> TrappedInventoryCalculator
  -> gas and liquid inventory with evidence references
  -> DepressurizationSimulator, flare-load screen, MDMTCalculator, or report JSON
```

## Method

For each segment, the calculator flashes a cloned NeqSim fluid at the configured operating pressure and temperature, reads gas and liquid densities, and applies volume fill fractions.

$$
m_{gas,i} = V_{gas,i}\rho_g(P,T,z)
$$

$$
m_{liquid,i} = V_{liquid,i}\rho_l(P,T,z)
$$

For pipe segments, internal volume is calculated from internal diameter and length:

$$
V_{pipe} = \frac{\pi D_i^2 L}{4}
$$

The aggregate result reports total gas volume, liquid volume, gas mass, liquid mass, per-segment results, evidence records, and warnings for missing evidence or fallback liquid density use.

## Code Pattern

```java
import neqsim.process.safety.barrier.DocumentEvidence;
import neqsim.process.safety.depressurization.DepressurizationSimulator;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.inventory.TrappedInventoryCalculator;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gas = new SystemSrkEos(300.15, 20.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

DocumentEvidence evidence = new DocumentEvidence(
    "E-INV-001",
    "P-ID-001",
    "Gas recompression P&ID",
    "A",
    "isolation boundary",
    1,
    "references/P-ID-001.pdf",
    "Pipe P-100 and compressor casing inside the isolated boundary.",
    0.90);

TrappedInventoryCalculator calculator = new TrappedInventoryCalculator()
    .setFluid(gas)
    .setOperatingConditions(20.0, "bara", 27.0, "C")
    .addPipeSegment("P-100", 0.1524, 18.0, 0.0, evidence)
    .addEquipmentVolume("K-100 casing", 0.75, 0.0, evidence);

InventoryResult inventory = calculator.calculate();
double gasMassKg = inventory.getTotalGasMassKg();
double isolatedVolumeM3 = inventory.getTotalVolumeM3();
String inventoryJson = calculator.toJson();

SystemInterface blowdownFluid = calculator.createDepressurizationFluid();
DepressurizationSimulator simulator = new DepressurizationSimulator(
    blowdownFluid,
    inventory.getTotalGasVolumeM3(),
    0.010,
    0.72,
    1.5e5);
simulator.setMaxTime(300.0);
simulator.setTimeStep(1.0);
DepressurizationResult blowdown = simulator.run();
```

This example is covered by `TrappedInventoryCalculatorTest#documentedWorkflowCalculatesInventoryAndCreatesBlowdownFluid`.

## API Summary

| Method | Use |
|--------|-----|
| `setFluid(SystemInterface fluid)` | Sets the representative fluid. Configure components and mixing rule before passing it in. |
| `setOperatingConditions(double pressure, String pressureUnit, double temperature, String temperatureUnit)` | Sets pressure and temperature with unit conversion, for example `bara` and `C`. |
| `setOperatingConditions(double pressureBara, double temperatureK)` | Sets pressure in bara and temperature in K. |
| `setFallbackLiquidDensity(double densityKgPerM3)` | Sets fallback liquid density when a liquid fill fraction is specified but the flashed fluid has no liquid phase. |
| `addPipeSegment(...)` | Adds an isolated pipe segment from internal diameter, length, liquid fill fraction, and optional evidence. |
| `addEquipmentVolume(...)` | Adds a vessel, compressor casing, exchanger channel, or other documented equipment volume. |
| `addVolumeSegment(...)` | Adds a generic volume segment with engineering unit conversion. |
| `calculate()` | Returns an `InventoryResult` with totals, segment results, and warnings. |
| `createDepressurizationFluid()` | Creates a lumped gas inventory fluid for `DepressurizationSimulator`. |
| `toJson()` | Returns report-ready JSON with segment evidence included. |

## Evidence Handling

Pass `DocumentEvidence` to each segment when the volume is derived from drawings, line lists, equipment datasheets, or STID/P&ID extraction. Missing evidence is allowed, but `InventoryResult.getWarnings()` reports it so screening studies can distinguish documented assumptions from placeholders.

Useful evidence fields include:

| Field | Example use |
|-------|-------------|
| `evidenceId` | Stable identifier for the extracted data point. |
| `documentId` | Drawing, datasheet, report, or line-list identifier. |
| `section` and `page` | Location inside the source document. |
| `sourceReference` | Local reference path or system reference. |
| `excerpt` | Short quoted basis for the segment. |
| `confidence` | Extraction confidence or engineering confidence from 0 to 1. |

## Interpreting Results

Use gas inventory for blowdown and flare source terms. Use liquid inventory for flare knockout drum screening, drain/purge planning, and carryover checks. If liquid volume is specified but the flashed representative fluid has no liquid phase, the fallback density is used and a warning is emitted.

The result is a screening basis unless all of these are current and verified:

| Input | Why it matters |
|-------|----------------|
| Isolation boundary | Total inventory scales nearly linearly with trapped gas volume. |
| Internal pipe diameter and length | Nominal pipe size is not always the same as internal diameter. |
| Equipment hold-up volume | Compressor casings, scrubbers, exchangers, and dead legs can dominate small inventories. |
| Initial pressure and temperature | Density and blowdown stored energy depend directly on operating state. |
| Fluid composition | Richer gas increases density and may change condensation and cooling behavior. |
| BDV/orifice/Cv data | Controls peak mass flow and depressuring time after inventory is defined. |
| Material and thickness | Needed before a cold-temperature result can close an MDMT assessment. |

## Typical Workflow

1. Extract current isolation boundary from P&ID, STID, cause-and-effect, and operating procedure.
2. Convert each pipe or equipment item into a segment with evidence.
3. Flash the representative fluid at the initial operating condition.
4. Review warnings for missing evidence or fallback density use.
5. Use gas inventory as input to `DepressurizationSimulator` or a flare-load screen.
6. Use minimum blowdown temperatures with `MDMTCalculator` for material verification.
7. Save `toJson()` output in the task report so reviewers can trace each segment back to its source.

## See Also

- [Depressurization per API 521](depressurization_per_API_521.md)
- [MDMT Assessment](mdmt_assessment.md)
- [Barrier Management and SCE Traceability](barrier_management.md)
- [Automated HAZOP from STID and Simulation](automated_hazop_from_stid.md)
