---
name: neqsim-input-validation
description: "Input validation rules for NeqSim simulations. USE WHEN: setting up thermodynamic systems, process equipment, or running simulations. Catches physically impossible or suspect inputs before simulation runs, saving time and providing corrective guidance."
---

# NeqSim Input Validation Rules

Validate inputs **before** creating NeqSim objects or running simulations.
Each rule includes the check, the error it prevents, and the corrective action.

## Thermodynamic System Inputs

### Temperature

| Check | Valid Range | Error If Violated |
|-------|------------|-------------------|
| T > 0 K (> -273.15 C) | 50-1000 K typical | Absolute zero violation — physically impossible |
| T < 2000 K | Engineering range | Above 2000 K, EOS models are not validated |
| Constructor uses Kelvin | `SystemSrkEos(273.15 + T_C, P)` | Passing Celsius directly gives wrong results silently |

**Corrective action:** Convert Celsius to Kelvin: `T_K = 273.15 + T_C`

### Pressure

| Check | Valid Range | Error If Violated |
|-------|------------|-------------------|
| P > 0 bara | 0.001-2000 bara typical | Zero/negative pressure is physically impossible |
| P < 5000 bara | Engineering range | Above ~2000 bara, cubic EOS accuracy degrades significantly |

**Corrective action:** Verify units — NeqSim uses bara (absolute), not barg (gauge) or psia.

### Composition

| Check | Rule | Error If Violated |
|-------|------|-------------------|
| Sum of mole fractions | Should be ~1.0 (tolerance: 0.999-1.001) | Wrong mixture composition — all properties incorrect |
| Each fraction >= 0 | No negative fractions | Physically impossible |
| Each fraction <= 1 | No fraction > 100% | Physically impossible |
| At least 1 component | Cannot have empty system | NeqSim crashes on empty system |

**Corrective action:** Normalize fractions: `x_i = x_i / sum(x)`. Or alert user that fractions don't sum to 1.

### Component Names

Valid component names must match entries in `src/main/resources/data/COMP.csv`.
Common correct names:

| Correct Name | Common Mistakes |
|-------------|-----------------|
| `"methane"` | `"CH4"`, `"Methane"`, `"c1"` |
| `"ethane"` | `"C2H6"`, `"C2"` |
| `"propane"` | `"C3H8"`, `"C3"` |
| `"n-butane"` | `"butane"`, `"nC4"` |
| `"i-butane"` | `"isobutane"`, `"iC4"` |
| `"CO2"` | `"co2"`, `"carbon dioxide"` |
| `"H2S"` | `"h2s"`, `"hydrogen sulfide"` |
| `"nitrogen"` | `"N2"`, `"Nitrogen"` |
| `"water"` | `"H2O"`, `"Water"` |
| `"MEG"` | `"meg"`, `"ethylene glycol"` |

**Corrective action:** Search COMP.csv for the correct component name.

### Mixing Rule

| Check | Rule | Error If Violated |
|-------|------|-------------------|
| Mixing rule is set | `setMixingRule()` must be called after `addComponent()` | Flash gives wrong results or crashes |
| CPA systems use numeric rule | `setMixingRule(10)` for CPA EOS | String mixing rules don't work with CPA |
| Classic rule for cubic EOS | `setMixingRule("classic")` for SRK/PR | Wrong rule type causes errors |

**Corrective action:** Always call `fluid.setMixingRule("classic")` (or `10` for CPA) after adding all components.

## Process Equipment Inputs

### Stream

| Check | Rule |
|-------|------|
| Flow rate > 0 | Zero flow breaks downstream equipment |
| Flow rate units specified | `setFlowRate(value, "kg/hr")` — always include unit string |
| Stream has a fluid object | `new Stream("name", fluid)` — fluid must not be null |

### Compressor

| Check | Rule |
|-------|------|
| Outlet pressure > inlet pressure | Compressor cannot decompress (use valve/expander) |
| Pressure ratio < 6 per stage | Single-stage ratio above ~4-6 is unrealistic |
| Isentropic efficiency 0.5-0.95 | Values outside this range are non-physical |
| Inlet stream has gas phase | Compressor cannot handle pure liquid |

### Valve / Throttle

| Check | Rule |
|-------|------|
| Outlet pressure < inlet pressure | Valve reduces pressure (use compressor to increase) |
| Outlet pressure > 0 bara | Cannot throttle to zero pressure |

### Heat Exchanger / Cooler / Heater

| Check | Rule |
|-------|------|
| Outlet temperature set | Must specify target temperature or duty |
| Cooler: T_out < T_in | Cooler reduces temperature |
| Heater: T_out > T_in | Heater increases temperature |
| Temperature in Kelvin for `setOutTemperature()` | Common mistake: passing Celsius to method expecting Kelvin |

### Separator

| Check | Rule |
|-------|------|
| Inlet has 2+ phases at conditions | Single-phase feed produces no separation |
| Use ThreePhaseSeparator for water | Standard Separator handles gas/liquid only |

### Pipeline

| Check | Rule |
|-------|------|
| Length > 0 meters | Zero-length pipe has no effect |
| Diameter > 0 meters | Must specify pipe inner diameter |
| Diameter typically 0.05-1.5 m | Values outside range are suspect |

## EOS Selection Validation

| Fluid Type | Required EOS | Wrong EOS Symptom |
|-----------|-------------|-------------------|
| Hydrocarbons + water | CPA (`SystemSrkCPAstatoil`) | SRK/PR gives near-zero water solubility in HC |
| Hydrocarbons + MEG/methanol | CPA (`SystemSrkCPAstatoil`) | Wrong hydrate inhibition prediction |
| Pure hydrocarbons (no polar) | SRK or PR | CPA is slower with no benefit |
| Custody transfer / fiscal metering | GERG-2008 (`SystemGERG2008Eos`) | Cubic EOS not accurate enough for custody |
| Electrolyte / brine / pH | Electrolyte CPA | SRK/CPA ignores ionic interactions |

**Corrective action:** Match EOS to fluid type. When in doubt, CPA handles the widest range of systems.

## Order-of-Operations Validation

These operations must happen in the correct sequence:

```
1. Create fluid (SystemSrkEos, etc.)
2. Add ALL components
3. Set mixing rule                    ← BEFORE characterization or flash
4. Characterize plus fractions (if oil) ← AFTER mixing rule
5. Create database (if needed)
6. Run flash (TPflash, etc.)
7. Call initProperties()              ← AFTER flash, BEFORE reading properties
8. Read properties (density, viscosity, etc.)
```

**Common order violations:**
- Characterizing before setting mixing rule → `ArrayIndexOutOfBoundsException`
- Reading properties before `initProperties()` → zero values for transport properties
- Adding components after setting mixing rule → mixing rule parameters stale

## Quick Validation Checklist

Use this checklist before every simulation setup:

- [ ] Temperature in Kelvin for constructors (not Celsius)
- [ ] Pressure in bara (not barg or psia)
- [ ] Mole fractions sum to ~1.0
- [ ] Component names match COMP.csv exactly
- [ ] Mixing rule set after all components added
- [ ] EOS matches fluid type (CPA for water/polar systems)
- [ ] Flash called before reading properties
- [ ] `initProperties()` called after flash
- [ ] Equipment connected via outlet streams (not new streams)
- [ ] ProcessSystem.run() called once after building full flowsheet
