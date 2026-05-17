---
name: neqsim-platform-modeling
description: "Production platform process modeling patterns for NeqSim. USE WHEN: building full topside process models for oil & gas platforms (FPSO, fixed, semi-sub) from design documents, P&IDs, or operational data. Covers fluid creation with TBP fractions, multi-stage separation with recycles, recompression trains with compressor curves and anti-surge, export/injection compression, oil stabilization, scrubber liquid recovery, iteration strategies, and structured result extraction. Derived from 15+ production NCS platform models."
last_verified: "2026-07-04"
---

# NeqSim Production Platform Modeling

Comprehensive patterns for building complete topside process simulations from
platform design documents. Derived from production-grade models of 15+ NCS
platforms (Åsgard A/B, Troll A/B, Grane, Castberg, Martin Linge, Gudrun, etc.)
in the NeqSim-dev-environment.

---

## 1. Architecture Overview

### 1.1 Standard Model Structure

Every production platform model follows the same architecture:

```
ProcessInput (pydantic) → simulate() function → ProcessSystem → calc_result() → ProcessOutput (pydantic)
```

| Component | Purpose | Pattern |
|-----------|---------|---------|
| `ProcessInput` | All operating conditions as typed fields | Pydantic BaseModel with 50-100+ fields |
| `simulate()` | Builds and runs the ProcessSystem | Single function, 500-1500 lines |
| `calc_result()` | Extracts structured results from solved system | Calls `simulate()`, builds ProcessOutput |
| `ProcessOutput` | All results in typed, serializable format | Pydantic BaseModel with response objects |
| `neqsim_responses.py` | Helper functions to extract equipment data | `get_separator_response()`, `get_compressor_response()`, etc. |
| `pydantic_classes.py` | Shared response types | `CompressorResponse`, `SeparatorResponse`, `HeaterResponse`, etc. |

### 1.2 Two Execution Strategies

**Strategy A — Manual Iteration (ASGA pattern):**
```python
NUMBER_OF_ITERATIONS = 25

operations = ProcessSystem()
# ... build entire flowsheet ...
for _ in range(NUMBER_OF_ITERATIONS):
    operations.run_step()
```
Best for: models with many recycles that need controlled convergence.

**Strategy B — Single Blocking Run (Martin Linge pattern):**
```python
operations = ProcessSystem()
# ... build entire flowsheet ...
thread = operations.runAsThread()
thread.join(config.SYNC_REQUEST_TIMEOUT_MS)  # e.g., 120000 ms

if thread.isAlive():
    thread.interrupt()
    raise CalculationTimeout("Timed out")
```
Best for: models with few recycles where NeqSim's internal solver handles convergence.

---

## 2. Fluid Creation

### 2.1 Composition with TBP Fractions (Recommended)

Production fluids almost always include heavy ends characterized as TBP
(True Boiling Point) fractions. The `fluid_creator()` pattern accepts a
composition dictionary with optional molar mass and density for TBP fractions:

```python
from neqsim import jneqsim

def fluid_creator(composition: dict) -> "SystemInterface":
    """Create a NeqSim fluid from a composition dictionary.

    Args:
        composition: dict with keys:
            - "component_name": list of component names
            - "molar_composition[-]": list of mole fractions
            - "molar_mass[kg/mol]": list (None for defined components)
            - "relative_density[-]": list (None for defined components)
    """
    fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 15.0, 1.01325)

    names = composition["component_name"]
    molfracs = composition["molar_composition[-]"]
    molar_masses = composition["molar_mass[kg/mol]"]
    rel_densities = composition["relative_density[-]"]

    for i, name in enumerate(names):
        if molar_masses[i] is not None and rel_densities[i] is not None:
            # TBP fraction — heavy hydrocarbon pseudo-component
            fluid.addTBPfraction(
                name, molfracs[i], molar_masses[i], rel_densities[i]
            )
        else:
            # Defined component (methane, ethane, CO2, water, etc.)
            fluid.addComponent(name, molfracs[i])

    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    fluid.useVolumeCorrection(True)
    return fluid
```

### 2.2 Typical NCS Gas Condensate Composition

```python
composition = {
    "component_name": [
        "water", "nitrogen", "CO2", "methane", "ethane",
        "propane", "i-butane", "n-butane", "i-pentane", "n-pentane",
        "2-methylpentane",  # proxy for nC6 fraction
        # TBP fractions for C10+ (named by carbon range)
        "nC10-nC12", "nC13-nC15", "nC16-nC18", "nC19-nC22",
        "nC23-nC26", "nC27-nC30", "nC31-nC34", "nC35-nC38", "nC39+"
    ],
    "molar_composition[-]": [
        0.058, 0.005, 0.037, 0.664, 0.086,
        0.050, 0.008, 0.016, 0.005, 0.006,
        0.006,
        0.010, 0.009, 0.006, 0.011,
        0.005, 0.004, 0.004, 0.003, 0.002
    ],
    "molar_mass[kg/mol]": [
        None, None, None, None, None,
        None, None, None, None, None,
        None,
        0.091, 0.103, 0.117, 0.146,
        0.181, 0.212, 0.248, 0.289, 0.330
    ],
    "relative_density[-]": [
        None, None, None, None, None,
        None, None, None, None, None,
        None,
        0.741, 0.769, 0.789, 0.804,
        0.825, 0.838, 0.849, 0.863, 0.875
    ],
}
```

### 2.3 Multi-Well Composition (Martin Linge Pattern)

When a platform receives fluid from multiple wells/fields, define a base
fluid template with ALL possible TBP fractions, then clone and adjust per well:

```python
# Base template with all TBP fraction definitions
base_fluid = jneqsim.thermo.system.SystemPrEos(273.15 + 30.0, 65.0)
base_fluid.addComponent("nitrogen", 0.08)
base_fluid.addComponent("CO2", 3.38)
base_fluid.addComponent("methane", 69.83)
# ... light HCs ...
base_fluid.addTBPfraction("C6_FieldA", 0.24, 84.99/1000, 695/1000)
base_fluid.addTBPfraction("C6_FieldB", 0.0, 84.0/1000, 684/1000)
# ... more TBP fractions for each field source ...
base_fluid.setMixingRule("classic")

# Per-well fluids via clone + setMolarComposition
well_fluid_A = base_fluid.clone()
well_fluid_A.setMolarComposition([0.108, 3.379, 69.5, ...])  # FieldA composition

well_fluid_B = base_fluid.clone()
well_fluid_B.setMolarComposition([0.095, 2.100, 72.1, ...])  # FieldB composition
```

**Key rule**: All wells must share the same component list (same TBP fraction
definitions). Use `setMolarComposition()` to set zero fractions for components
not present in a particular well.

---

## 3. Process Building Patterns

### 3.1 Pre-create Mixers Before Adding Streams

A **critical pattern** in production models: create `StaticMixer` objects
at the start, then add streams to them as equipment is built. This solves
the forward-reference problem (downstream mixer needs to exist before
upstream equipment creates its outlet stream).

```python
operations = ProcessSystem()

# Pre-create all mixers FIRST (no streams yet)
inlet_oil_mixer = jneqsim.process.equipment.mixer.StaticMixer("Inlet oil mixer")
recomp_gas_mixer = jneqsim.process.equipment.mixer.StaticMixer("Recomp gas mixer")
export_manifold = jneqsim.process.equipment.mixer.StaticMixer("Export manifold")
recycle_oil_mixer = jneqsim.process.equipment.mixer.StaticMixer("Recycle from export")

# Build upstream equipment...
# Then add their outlets to the pre-created mixers:
inlet_oil_mixer.addStream(oil_heater_test.getOutStream())
inlet_oil_mixer.addStream(oil_heater_prod.getOutStream())
# ... later, when you're ready to use the mixer:
operations.add(inlet_oil_mixer)
```

**Important**: Add the mixer to ProcessSystem AFTER all its inlet streams are
connected. All `addStream()` calls must happen before `operations.add(mixer)`.

### 3.2 Multi-Stage Separation Train

Standard NCS platform separation: HP → MP → LP with oil heating/letdown between stages:

```python
# === First Stage (HP) Separator ===
well_stream = Stream("Well Stream", well_fluid)
well_stream.setFlowRate(process_input.flow_rate, "kg/hr")
well_stream.setTemperature(process_input.inlet_temp, "C")
well_stream.setPressure(process_input.hp_pressure, "bara")
operations.add(well_stream)

hp_separator = ThreePhaseSeparator("HP Separator", well_stream)
operations.add(hp_separator)

# === Oil letdown to Second Stage ===
oil_heater_to_mp = Heater("Oil heater to MP", hp_separator.getLiquidOutStream())
oil_heater_to_mp.setOutTemperature(process_input.mp_temperature, "C")
oil_heater_to_mp.setOutPressure(process_input.mp_pressure, "bara")
operations.add(oil_heater_to_mp)

# Collect oil from multiple sources (see pre-created mixer pattern §3.1)
inlet_oil_mixer.addStream(oil_heater_to_mp.getOutStream())

# === Second Stage (MP) Separator ===
mp_separator = ThreePhaseSeparator("MP Separator", inlet_oil_mixer.getOutletStream())
operations.add(mp_separator)

# === Oil letdown to Third Stage ===
oil_to_lp = Heater("Oil to LP", mp_separator.getLiquidOutStream())
oil_to_lp.setOutTemperature(process_input.lp_temperature, "C")
oil_to_lp.setOutPressure(process_input.lp_pressure, "bara")
operations.add(oil_to_lp)

# === Third Stage (LP) Separator ===
lp_separator = Separator("LP Separator", oil_to_lp.getOutletStream())
operations.add(lp_separator)

# Oil export pump
oil_pump = Pump("Oil Export Pump", lp_separator.getLiquidOutStream())
oil_pump.setOutletPressure(process_input.oil_export_pressure + 1.01325)  # barg to bara
operations.add(oil_pump)
```

### 3.2.1 Separator Physical Configuration via MechanicalDesign

Physical dimensions (vessel ID, nozzle sizes), internals (demister type,
inlet device), and design parameters (K-factor, retention time) are set via
`SeparatorMechanicalDesign` — NOT directly on the `Separator`. This follows
the same pattern used for wells, pipelines, and compressors in NeqSim.

Call `initMechanicalDesign()` **after** the process has been `run()` so the
design calculation has access to process conditions.

```python
# After operations.run():
hp_separator.initMechanicalDesign()
hp_design = hp_separator.getMechanicalDesign()
hp_design.setMaxOperationPressure(85.0)
hp_design.setMaxOperationTemperature(273.15 + 80.0)
hp_design.setGasLoadFactor(0.107)       # K-factor [m/s]
hp_design.setRetentionTime(180.0)       # Liquid retention [s]
hp_design.setInletNozzleID(0.356)       # 14-inch inlet [m]
hp_design.setDemisterType("wire_mesh")
hp_design.readDesignSpecifications()
hp_design.calcDesign()

# Repeat for other separators
mp_separator.initMechanicalDesign()
mp_design = mp_separator.getMechanicalDesign()
mp_design.setMaxOperationPressure(25.0)
mp_design.setGasLoadFactor(0.107)
mp_design.setRetentionTime(120.0)
mp_design.readDesignSpecifications()
mp_design.calcDesign()
```

### 3.3 Heater as T/P Setter

A common pattern uses a `Heater` with both outlet T and P set. This acts as
a T/P setter — useful for ensuring streams enter equipment at the correct
conditions (especially after mixing or before scrubbers):

```python
tp_setter = Heater("TP Setter 3rd Stage", mixed_stream)
tp_setter.setOutTemperature(process_input.lp_temperature, "C")
tp_setter.setOutPressure(process_input.lp_pressure)
operations.add(tp_setter)

scrubber = Separator("LP Scrubber", tp_setter.getOutletStream())
operations.add(scrubber)
```

---

## 4. Recycle Patterns

### 4.1 Scrubber Liquid Recycle (Oil Recovery)

Liquid knocked out in recompression scrubbers is recycled back to the
separation train. The standard pattern creates clone seed streams:

```python
# Pre-create seed streams (cloned from the separator liquid for same composition)
recycle_seed_1 = mp_separator.getLiquidOutStream().clone()
recycle_seed_1.setName("Recycle from 1st stage scrubber")
recycle_seed_1.setFlowRate(1, "kg/hr")  # Small initial flow for convergence
recycle_seed_1.setPressure(process_input.lp_pressure)
recycle_seed_1.setTemperature(process_input.lp_temperature, "C")

recycle_seed_2 = mp_separator.getLiquidOutStream().clone()
recycle_seed_2.setName("Recycle from 2nd stage scrubber")
recycle_seed_2.setFlowRate(1, "kg/hr")
recycle_seed_2.setPressure(process_input.lp_pressure)
recycle_seed_2.setTemperature(process_input.lp_temperature, "C")

# Add seeds to operations AND to the mixer that feeds the LP separator
operations.add(recycle_seed_1)
operations.add(recycle_seed_2)

rec_oil_mixer = StaticMixer("Recycle oil mixer")
rec_oil_mixer.addStream(oil_to_lp.getOutletStream())  # Main oil flow
rec_oil_mixer.addStream(recycle_seed_1)
rec_oil_mixer.addStream(recycle_seed_2)
operations.add(rec_oil_mixer)

# ... later, after building the recompression scrubbers:
# Wire actual scrubber liquid to a Heater (TP setter), then to Recycle object

tp_set_scrub_liq_1 = Heater("TP set scrub liq 1", first_scrubber.getLiquidOutStream())
tp_set_scrub_liq_1.setOutTemperature(process_input.lp_temperature, "C")
tp_set_scrub_liq_1.setOutPressure(process_input.lp_pressure)
operations.add(tp_set_scrub_liq_1)

recycle_oil_1 = Recycle("Recycle oil 1")
recycle_oil_1.addStream(tp_set_scrub_liq_1.getOutletStream())
recycle_oil_1.setOutletStream(recycle_seed_1)
operations.add(recycle_oil_1)
```

**Critical details:**
- Seed streams need small but non-zero flow (1 kg/hr) for numerical stability
- Seed T, P must match the mixer/separator they feed into
- Clone the composition from the appropriate location in the process
- The Heater before the Recycle object acts as a T/P equalizer

### 4.2 Export/Injection Scrubber Liquid Recycle

Liquids from export and injection scrubbers are typically recycled back to
the MP (second stage) separator via a shared mixer:

```python
# Pre-create mixer for all high-pressure scrubber liquids
mixer_recycle_from_export = StaticMixer("Recycle from export line")

# ... later, add scrubber liquid streams from export/injection/booster:
if has_booster:
    mixer_recycle_from_export.addStream(booster_scrubber.getLiquidOutStream())
if has_export:
    mixer_recycle_from_export.addStream(export_scrubber.getLiquidOutStream())
if has_injection:
    mixer_recycle_from_export.addStream(inj_scrubber_1.getLiquidOutStream())
    mixer_recycle_from_export.addStream(inj_scrubber_2.getLiquidOutStream())

operations.add(mixer_recycle_from_export)

# Recycle back to MP separator via TP setter
tp_set_export_rec = Heater("TP set export rec", mixer_recycle_from_export.getOutletStream())
tp_set_export_rec.setOutTemperature(process_input.mp_temperature, "C")
tp_set_export_rec.setOutPressure(process_input.mp_pressure)
operations.add(tp_set_export_rec)

# Seed stream was added to inlet_oil_mixer earlier
recycle_from_export = Recycle("Recycle from export")
recycle_from_export.addStream(tp_set_export_rec.getOutletStream())
recycle_from_export.setOutletStream(export_recycle_seed)  # Pre-created seed
operations.add(recycle_from_export)
```

### 4.3 Recycle Topology Summary

Typical NCS platform has 4-6 recycle loops:

| Recycle | Source | Destination | Purpose |
|---------|--------|-------------|---------|
| LP scrubber liquids (×3) | R1/R2/R3 scrubber liquids | LP separator inlet | Oil recovery |
| Export recycle | Export/injection/booster scrubber liquids | MP separator inlet | Oil recovery |
| Anti-surge R1 | R1 compressor outlet | R1 compressor suction | Surge protection |
| Anti-surge R2 | R2 compressor outlet | R2 compressor suction | Surge protection |
| Anti-surge R3 | R3 compressor outlet | R3 compressor suction | Surge protection |
| Anti-surge export | Export compressor outlet | Export compressor suction | Surge protection |

---

## 5. Recompression Train Pattern

### 5.1 Standard Stage (Cooler → Scrubber → Compressor → Anti-surge)

Each recompression stage follows this repeating pattern:

```python
# === Anti-surge seed stream (pre-created for Recycle object) ===
asv_seed = lp_separator.getGasOutStream().clone()
asv_seed.setName("ASV seed R1")
asv_seed.setFlowRate(0.1, "kg/hr")  # Tiny flow for anti-surge recycle
asv_seed.setPressure(process_input.lp_pressure)
operations.add(asv_seed)

# === Mixer: main gas + anti-surge recycle ===
inlet_mixer = StaticMixer("Inlet cooler R1")
inlet_mixer.addStream(asv_seed)
inlet_mixer.addStream(lp_separator.getGasOutStream())
operations.add(inlet_mixer)

# === Pressure drop (piping + cooler shell-side) ===
pdrop = PressureDrop("PD R1 Cooler")
pdrop.setInletStream(inlet_mixer.getOutletStream())
pdrop.setPressureDrop(process_input.r1_cooler_dp, "bara")
operations.add(pdrop)

# === Aftercooler ===
cooler = Heater("R1 Cooler", pdrop.getOutletStream())
cooler.setOutTemperature(process_input.r1_scrubber_temp, "C")
operations.add(cooler)

# === Hydrate temperature measurement ===
hydrate_analyser = HydrateEquilibriumTemperatureAnalyser("Hydrate R1", cooler.getOutletStream())
operations.add(hydrate_analyser)

# === TP Setter before scrubber (ensures correct inlet conditions) ===
tp_set = Heater("TP set R1", cooler.getOutletStream())
tp_set.setOutTemperature(process_input.r1_scrubber_temp, "C")
tp_set.setOutPressure(process_input.r1_scrubber_pressure)
operations.add(tp_set)

# === Scrubber ===
scrubber = Separator("R1 Scrubber", tp_set.getOutletStream())
scrubber.setInternalDiameter(1.9)
operations.add(scrubber)

# === Compressor (T/P control version) ===
compressor = Compressor("R1 Compressor", scrubber.getGasOutStream())
compressor.setUsePolytropicCalc(True)
compressor.setOutletPressure(process_input.r1_outlet_pressure)
compressor.setOutTemperature(process_input.r1_outlet_temp + 273.15)  # Kelvin!
operations.add(compressor)

# === Anti-surge split + valve + recycle ===
asv_split = Splitter("ASV split R1", compressor.getOutletStream())
asv_split.setSplitNumber(2)
asv_split.setFlowRates([-1, asv_mass_flow], "kg/hr")  # -1 = remainder
operations.add(asv_split)

asv_valve = ThrottlingValve("ASV R1", asv_split.getSplitStream(1))
asv_valve.setOutletPressure(process_input.lp_pressure)
operations.add(asv_valve)

recycle_asv = Recycle("Recycle ASV R1")
recycle_asv.addStream(asv_valve.getOutletStream())
recycle_asv.setOutletStream(asv_seed)
operations.add(recycle_asv)
```

### 5.2 Compressor Performance Curves (Dual Object Pattern)

Production models use **two compressor objects per stage**:
1. **Control compressor**: sets outlet P and T (for converging the process)
2. **Curve compressor**: uses actual performance map (for monitoring/reporting)

```python
# Performance curves — separate object using the SAME inlet stream
comp_curves = Compressor("R1 Compressor Curves", scrubber.getGasOutStream())
comp_curves.setUsePolytropicCalc(True)

chart_conditions = [1.0, 1.0, 1.0, 1.0]  # Reference conditions multiplier
speeds = [4421, 5684, 6632]               # RPM

# flow[speed_index][point_index] in m3/hr (actual volume flow at suction)
flow = [
    [5758, 6429, 6679],
    [7616, 10079, 11344],
    [10722, 13295, 15046],
]

# head[speed_index][point_index] in kJ/kg (polytropic head)
head = [
    [51.4, 48.5, 47.7],
    [90.5, 84, 75],
    [123, 115, 94],
]

# efficiency[speed_index][point_index] in % (polytropic efficiency)
poly_eff = [
    [80, 80, 79.7],
    [77.5, 80.3, 77.91],
    [75.3, 78.24, 73],
]

comp_curves.getCompressorChart().setCurves(
    chart_conditions, speeds, flow, head, poly_eff
)
comp_curves.setSpeed(process_input.r1_speed)
comp_curves.getCompressorChart().setHeadUnit("kJ/kg")
operations.add(comp_curves)
```

**Optional: Surge curve definition** (Martin Linge pattern):
```python
surge_flow = [2770.39, 3199.03, 4395.44]   # m3/hr at each speed
surge_head = [97.63, 135.65, 235.06]        # kJ/kg at surge
comp_curves.getCompressorChart().getSurgeCurve().setCurve(
    chart_conditions, surge_flow, surge_head
)
comp_curves.getAntiSurge().setActive(True)
comp_curves.getAntiSurge().setSurgeControlFactor(1.05)  # 5% safety margin
```

### 5.3 Anti-Surge Valve Flow Calculation (Cv-based)

Compute anti-surge valve mass flow from Cv and valve opening:

```python
import math

def get_gas_valve_mass_flow(
    p_upstream_pa: float,
    p_downstream_pa: float,
    density_kgm3: float,
    cv_value: float,
    valve_opening_pct: float,
) -> float:
    """Gas valve mass flow using ISA/IEC valve sizing equation.

    Returns mass flow in kg/hr.
    """
    if valve_opening_pct < 10:  # MIN_VALVE_OPENING
        return 0.1  # Tiny seed flow

    dp = abs(p_upstream_pa - p_downstream_pa)
    n8 = 94.8  # ISA constant for mass flow

    mass_flow = (
        n8 * (valve_opening_pct / 100.0) * cv_value
        * math.sqrt(dp * density_kgm3)
    )
    return max(mass_flow, 0.1)
```

**Apply after first `operations.run_step()` call** (needs actual pressures/densities):
```python
# After initial run, calculate ASV flows from actual conditions
mass_r1 = get_gas_valve_mass_flow(
    operations.getUnit("ASV R1").getInletStream().getPressure("Pa"),
    operations.getUnit("ASV R1").getOutletStream().getPressure("Pa"),
    operations.getUnit("ASV R1").getInletStream().getFluid().getDensity("kg/m3"),
    process_input.cv_asv_r1,
    process_input.asv_opening_r1,
)
operations.getUnit("ASV split R1").setFlowRates([-1, mass_r1], "kg/hr")
operations.getUnit("ASV seed R1").setFlowRate(mass_r1, "kg/hr")
```

---

## 6. Export and Injection Gas Processing

### 6.1 Production Split

Gas from the recompression train goes to export and/or injection via a splitter:

```python
export_manifold.addStream(r3_output)  # All sources to manifold
operations.add(export_manifold)

production_split = Splitter("Prod split", export_manifold.getOutletStream())
production_split.setSplitFactors([split_export, split_injection])
operations.add(production_split)
```

### 6.2 Conditional Export/Injection Sections

Production models typically support switching export/injection on/off:

```python
MIN_SPLIT = 0.05

if split_export > MIN_SPLIT:
    # Export cooler → scrubber → compressor → aftercooler
    # Same pattern as recompression stage (§5.1) with anti-surge
    ...

if split_injection > MIN_SPLIT:
    # Multi-stage injection compression (2+ stages, same pattern)
    ...
```

### 6.3 Booster Compressor (Optional)

Some platforms have a booster between HP separation and the export manifold:

```python
MIN_BOOSTER_SPEED = 1000  # rpm threshold

if process_input.booster_speed > MIN_BOOSTER_SPEED:
    booster_mixer.addStream(hp_gas)
    operations.add(booster_mixer)
    # Same cooler → scrubber → compressor → ASV pattern
    ...
    export_manifold.addStream(booster_output)
else:
    # HP gas goes directly to export manifold
    export_manifold.addStream(hp_gas)
```

---

## 7. Measurement Devices

### 7.1 Hydrate Temperature Monitoring

Add at every cooler outlet to check hydrate risk:

```python
HydrateAnalyser = jneqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser

hydrate_mon = HydrateAnalyser("Hydrate R1 cooler", cooler.getOutletStream())
operations.add(hydrate_mon)

# Read after running:
hydrate_temp_C = operations.getMeasurementDevice("Hydrate R1 cooler").getMeasuredValue("C")
```

### 7.2 Well Allocators

For multi-well platforms, track each well's contribution to exports:

```python
WellAllocator = jneqsim.process.measurementdevice.WellAllocator

allocator = WellAllocator("Well A-3", well_stream_a3)
allocator.setExportGasStream(export_gas)
allocator.setExportOilStream(stable_oil)
operations.add(allocator)

# Read allocated rates per well
gas_alloc = allocator.getMeasuredValue("gas export rate", "kg/hr")
oil_alloc = allocator.getMeasuredValue("oil export rate", "kg/hr")
```

---

## 8. Result Extraction

### 8.1 Structured Response Helpers

Use standardized helper functions to extract equipment results into typed objects:

```python
def get_separator_response(separator) -> dict:
    """Extract separator state into structured dict."""
    result = {
        "name": str(separator.getName()),
        "pressure_bara": float(separator.getPressure("bara")),
        "temperature_C": float(separator.getTemperature("C")),
        "mass_flow_kghr": float(separator.getFluid().getFlowRate("kg/hr")),
        "gas_load_factor": float(separator.getGasLoadFactor()),
    }
    if separator.getThermoSystem().hasPhaseType("gas"):
        result["gas_flow_kghr"] = float(separator.getGasOutStream().getFlowRate("kg/hr"))
    if separator.getThermoSystem().hasPhaseType("oil"):
        result["oil_flow_kghr"] = float(
            separator.getThermoSystem().phaseToSystem("oil").getFlowRate("kg/hr")
        )
    return result


def get_compressor_response(compressor, asv_valve=None, curves=None) -> dict:
    """Extract compressor state with anti-surge and curve data."""
    result = {
        "name": str(compressor.getName()),
        "suction_P_bara": float(compressor.getInletStream().getPressure("bara")),
        "discharge_P_bara": float(compressor.getOutletStream().getPressure("bara")),
        "suction_T_C": float(compressor.getInletStream().getTemperature("C")),
        "discharge_T_C": float(compressor.getOutletStream().getTemperature("C")),
        "power_kW": float(compressor.getPower("kW")),
        "polytropic_head": float(compressor.getPolytropicFluidHead()),
        "polytropic_efficiency": float(compressor.getPolytropicEfficiency()),
        "mass_flow_kghr": float(compressor.getInletStream().getFlowRate("kg/hr")),
        "suction_vol_flow_m3hr": float(compressor.getInletStream().getFlowRate("m3/hr")),
    }
    if asv_valve:
        result["asv_flow_kghr"] = float(asv_valve.getOutletStream().getFlowRate("kg/hr"))
        result["net_flow_kghr"] = result["mass_flow_kghr"] - result["asv_flow_kghr"]
    if curves:
        result["curve_head"] = float(curves.getPolytropicHead())
        result["curve_efficiency"] = float(curves.getPolytropicEfficiency())
        result["speed"] = float(curves.getSpeed())
    return result
```

### 8.2 Key Output Extraction

```python
# Oil export
oil_rate_m3day = operations.getUnit("LP Separator").getLiquidOutStream().getFlowRate("m3/hr") * 24
oil_tvp = operations.getUnit("LP Separator").getLiquidOutStream().TVP(20.0, "C")
oil_density = operations.getUnit("LP Separator").getLiquidOutStream().getFluid().getDensity("kg/m3")

# Gas export
gas_rate_MSm3day = operations.getUnit("Export aftercooler").getOutletStream().getFlowRate("MSm3/day")

# Total power
total_power_kW = sum(
    operations.getUnit(name).getPower("kW")
    for name in ["R1 Compressor", "R2 Compressor", "R3 Compressor", "Export compressor"]
)

# Total cooling duty
total_cooling_kW = sum(
    operations.getUnit(name).getDuty() / 1000
    for name in ["R1 Cooler", "R2 Cooler", "R3 Cooler", "Export cooler"]
)
```

---

## 9. ProcessInput Configuration Pattern

### 9.1 Pydantic Model for All Operating Conditions

```python
from pydantic import BaseModel, Field

class ProcessInput(BaseModel):
    """All operating conditions for a platform process simulation."""

    # Feed conditions
    flow_rate_prod: float = Field(description="Production flow rate [kg/hr]")
    flow_rate_test: float = Field(0.0, description="Test separator flow [kg/hr]")

    # First stage separation
    pressure_prod_separator: float = Field(description="HP separator pressure [bara]")
    temperature_prod_separator: float = Field(description="HP separator temp [C]")

    # Second stage separation
    second_stage_pressure: float = Field(description="MP separator pressure [bara]")
    second_stage_temperature: float = Field(description="MP separator temp [C]")

    # Third stage separation
    third_stage_pressure: float = Field(description="LP separator pressure [bara]")
    third_stage_temperature: float = Field(description="LP separator temp [C]")

    # Recompression (R1)
    first_stage_recompressor_out_pressure: float = Field(description="R1 outlet P [bara]")
    first_stage_recompressor_out_temperature: float = Field(description="R1 outlet T [C]")
    first_stage_recompressor_scrubber_pressure: float = Field(description="R1 scrubber P [bara]")
    first_stage_recompressor_scrubber_temperature: float = Field(description="R1 scrubber T [C]")
    first_stage_recompressor_cooler_pressure_drop: float = Field(description="R1 cooler dP [bar]")
    first_stage_recompressor_speed: float = Field(description="R1 compressor speed [rpm]")

    # Anti-surge valves
    antisurge_valve_opening_r1: float = Field(0.0, description="R1 ASV opening [%]")
    Cv_value_antisurge_valve_r1: float = Field(description="R1 ASV Cv value")

    # ... repeat for R2, R3, booster, export, injection stages

    # Export
    export_compressor_outlet_pressure: float = Field(description="Export comp P [bara]")
    export_compressor_outlet_temperature: float = Field(description="Export comp T [C]")
    export_speed: float = Field(description="Export comp speed [rpm]")

    # Oil export
    export_oil_pressure: float = Field(description="Oil export P [barg]")

    # Production split
    split_export: float = Field(1.0, description="Fraction of gas to export [0-1]")
    split_injection: float = Field(0.0, description="Fraction of gas to injection [0-1]")
```

---

## 10. Common Constants

```python
MIN_VALVE_OPENING = 10      # % — below this, valve is treated as closed
MIN_BOOSTER_SPEED = 1000    # rpm — below this, booster is bypassed
MIN_SPLIT_PRODUCTION = 0.05 # fraction — below this, export/injection path skipped
NUMBER_OF_ITERATIONS = 25   # run_step iterations for convergence
SYNC_REQUEST_TIMEOUT_MS = 120_000  # ms timeout for blocking run
```

---

## 11. Checklist: Building a Platform Model

When building a new platform model from design documents:

- [ ] **Identify separation stages**: HP, MP, LP pressures and temperatures
- [ ] **Identify compression trains**: recompression (LP→HP), export, injection, booster
- [ ] **Map out all recycle loops**: scrubber liquids, export liquids, anti-surge valves
- [ ] **Pre-create all StaticMixers** before building equipment
- [ ] **Pre-create all recycle seed streams** (cloned, small flow rate, correct T/P)
- [ ] **Use Heater as T/P setter** before scrubbers and at recycle return points
- [ ] **Add hydrate temperature monitors** after every cooler
- [ ] **Add PressureDrop** elements before coolers (piping/shell losses)
- [ ] **Compressor curves**: dual object (control + curves) per stage
- [ ] **Anti-surge**: Splitter(2) → valve → Recycle back to suction seed
- [ ] **Conditional sections**: check booster speed, export/injection split fractions
- [ ] **Three-phase separators** for HP/MP (water), two-phase Separator for LP scrubbers
- [ ] **Oil TV P measurement**: `stream.TVP(20.0, "C")` for true vapor pressure at 20°C
- [ ] **Run iterations**: 25 `run_step()` calls or single threaded `runAsThread()` with timeout
- [ ] **Extract results**: structured response helpers for every equipment type
- [ ] **Validate**: mass balance, energy balance, hydrate temperatures above dewpoint

---

## 12. Notebook Template

For a Jupyter notebook implementation, see the complete starter in the
`neqsim-notebook-patterns` skill. The platform model follows the same
dual-boot setup cell pattern. Key additional imports:

```python
from neqsim import jneqsim

# Standard equipment
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Heater = jneqsim.process.equipment.heatexchanger.Heater
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Splitter = jneqsim.process.equipment.splitter.Splitter
StaticMixer = jneqsim.process.equipment.mixer.StaticMixer
Mixer = jneqsim.process.equipment.mixer.Mixer
Pump = jneqsim.process.equipment.pump.Pump
Recycle = jneqsim.process.equipment.util.Recycle
PressureDrop = jneqsim.process.equipment.util.PressureDrop

# Measurement devices
HydrateAnalyser = jneqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser
WellAllocator = jneqsim.process.measurementdevice.WellAllocator
```
