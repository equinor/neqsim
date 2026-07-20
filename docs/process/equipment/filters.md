---
title: Filter Equipment
description: Oil and gas filter simulation with type presets, beta-ratio efficiency, hydraulic curves, Ergun pressure drop, dynamic loading, bypass, and preliminary mechanical design.
---

NeqSim's filter equipment represents particulate filters, coalescers, strainers,
and granular-media beds as two-port process units. The default is a fixed clean
differential pressure. Optional models add flow-dependent hydraulics,
particle-size efficiency, loading and breakthrough, bypass, backwash or
regeneration, and preliminary vessel design.

## Contents

- [Classes and scope](#classes-and-scope)
- [Verified quick start](#verified-quick-start)
- [Filter types](#filter-types)
- [Particle and droplet capture](#particle-and-droplet-capture)
- [Pressure-drop models](#pressure-drop-models)
- [Dynamic loading and maintenance](#dynamic-loading-and-maintenance)
- [Differential-pressure bypass](#differential-pressure-bypass)
- [Mechanical design](#mechanical-design)
- [Specialized filters](#specialized-filters)
- [Standards basis](#standards-basis)
- [Related documentation](#related-documentation)

## Classes and scope

The main classes are in `neqsim.process.equipment.filter`.

| Class | Purpose |
| --- | --- |
| `Filter` | Generic particulate, coalescer, strainer, or media-filter unit |
| `CharCoalFilter` | Compatibility class that selects activated-carbon media defaults |
| `SulfurFilter` | Detects and captures a configurable fraction of solid elemental sulfur (`S8`) |
| `FilterPerformanceCurve` | Particle size versus beta-ratio test data |
| `FilterPressureDropCurve` | Actual volumetric flow versus clean differential-pressure data |

The generic concentration model is an external solids or aerosol inventory. It
reports contaminant capture but does not remove a thermodynamic component from
the outlet fluid. Use contaminant-specific equipment when molecular removal must
be included in the stream material balance.

## Verified quick start

This complete Java 8 example creates a gas stream, configures a cartridge
filter from beta-ratio data, calculates steady-state performance and preliminary
mechanical design, and then advances the dynamic loading state.

```java
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.filter.FilterPerformanceCurve;
import neqsim.process.equipment.filter.FilterType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.filter.FilterMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class FilterQuickStart {
  private FilterQuickStart() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("filter feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    FilterPerformanceCurve curve = new FilterPerformanceCurve(
        new double[] { 5.0, 10.0, 20.0 },
        new double[] { 2.0, 100.0, 1000.0 });
    curve.setTestStandard("ISO 16889:2022");

    Filter filter = new Filter("inlet cartridge filter", feed);
    filter.setFilterServiceType(FilterType.CARTRIDGE);
    filter.setDeltaP(0.10); // bar
    filter.setPerformanceCurve(curve);
    filter.setParticleSize(10.0); // micrometres
    filter.setInletParticleConcentration(100.0); // mg/kg
    filter.setTerminalDeltaP(1.0); // bar
    filter.setElementCollapsePressure(5.0); // bar
    filter.setElementIntegrityVerified(true);
    filter.run();

    StreamInterface cleanGas = filter.getOutletStream();
    double efficiency = filter.getCurrentRemovalEfficiency();
    double outletConcentration = filter.getOutletParticleConcentration(); // mg/kg
    double capturedRate = filter.getCalculatedCapturedRate(); // kg/hr

    FilterMechanicalDesign design =
        (FilterMechanicalDesign) filter.getMechanicalDesign();
    design.setMaxOperationPressure(70.0); // bara
    design.setMaxOperationTemperature(333.15); // K
    design.calcDesign();

    int elements = design.getRequiredElements();
    double vesselId = design.getInnerDiameter(); // m
    List<String> warnings = design.getDesignWarnings();

    filter.setLoadingCapacity(2.0); // kg
    filter.setPressureDropIncreaseAtCapacity(0.5); // bar
    filter.setCalculateSteadyState(false);
    filter.runTransient(3600.0, UUID.randomUUID());

    System.out.printf(
        "Pout=%.3f bara, efficiency=%.4f, Cout=%.3f mg/kg, "
            + "captured=%.6f kg/hr, elements=%d, ID=%.3f m, "
            + "loading=%.6f kg, warnings=%d%n",
        cleanGas.getPressure("bara"), efficiency, outletConcentration,
        capturedRate, elements, vesselId, filter.getSolidsLoading(),
        warnings.size());
  }
}
```

At the stated beta ratio, the nominal removal efficiency is 0.99 and the
reported outlet concentration is 1 mg/kg. The first transient hour adds the
calculated captured mass to `solidsLoading`. The component composition and
enthalpy of `cleanGas` are unchanged apart from the pressure-flash response.

## Filter types

`setFilterServiceType(...)` selects construction-specific hydraulic and design
defaults. Replace these screening defaults with project or vendor data.

| `FilterType` | Typical service | Default clean-pressure-drop model |
| --- | --- | --- |
| `CARTRIDGE` | Gas or liquid particulate removal | Flow-scaled |
| `BAG` | Produced water and liquid filtration | Flow-scaled |
| `Y_STRAINER` | Compact coarse protection | Flow-scaled, quadratic |
| `BASKET_STRAINER` | Coarse liquid protection | Flow-scaled, quadratic |
| `COALESCER` | Aerosol or dispersed-droplet capture | Flow-scaled, quadratic |
| `GRANULAR_MEDIA` | Sand, nutshell, or guard media | Ergun |
| `BACKWASHABLE_MEDIA` | Regenerable produced-water media | Ergun |
| `ACTIVATED_CARBON` | Activated-carbon guard bed | Ergun |

## Particle and droplet capture

For particles at or above size $x$, the beta ratio and fractional efficiency
are

$$
\beta_x = \frac{N_{\mathrm{upstream},x}}
                 {N_{\mathrm{downstream},x}}
$$

$$
\eta_x = 1 - \frac{1}{\beta_x}.
$$

`FilterPerformanceCurve` uses log-linear interpolation between supplied beta
ratios and clamps to the nearest endpoint outside the tested particle-size
range. `setParticleSize(...)` takes micrometres.
`setInletParticleConcentration(...)` takes mg/kg of process fluid. After
`run()`, `getOutletParticleConcentration()` reports mg/kg and
`getCalculatedCapturedRate()` reports kg/hr.

Loading is accumulated only by `runTransient(...)` when steady-state mode is
disabled. A steady-state `run()` calculates the capture rate but does not change
the accumulated loading state.

## Pressure-drop models

Actual volumetric flow is evaluated at the filter inlet.

| Model | Relation | Required data |
| --- | --- | --- |
| `FIXED` | Constant clean differential pressure | `setDeltaP(...)` |
| `FLOW_SCALED` | $\Delta P=\Delta P_{ref}(Q/Q_{ref})^n$ | Clean differential pressure, reference actual flow, exponent |
| `TABULATED` | Linear interpolation; defined endpoint extrapolation | `FilterPressureDropCurve` |
| `ERGUN` | Viscous and inertial packed-bed terms | Area, depth, media diameter, void fraction |

Use `setReferenceFlowRate(...)` in actual m3/hr for the flow-scaled model.
Installing a `FilterPressureDropCurve` selects the tabulated model. Use
`setMediaGeometry(areaM2, bedDepthM, particleDiameterM, voidFraction)` for the
Ergun model. NeqSim obtains density and viscosity from the inlet thermodynamic
state, so phase, pressure, and temperature affect the result.

The Ergun implementation is a homogeneous packed-bed screening model. It does
not represent channeling, distributor maldistribution, non-spherical-particle
corrections, bed compaction, or multiphase flow through the media.

## Dynamic loading and maintenance

Dynamic operation requires `setCalculateSteadyState(false)` followed by
`runTransient(dtSeconds, calculationId)`. The generic state includes:

- `solidsLoading` and `loadingCapacity` in kg;
- captured, backwash, and regeneration rates in kg/hr;
- a clean differential pressure plus a configured increase at one capacity;
- breakthrough beginning at a configured capacity fraction; and
- optional holdup volume in m3 and calculated residence time in seconds.

`setSolidsLoadingRate(...)` supplies a measured captured rate and disables the
concentration-based rate. Alternatively, particle concentration and removal
efficiency determine the captured rate automatically. `startBackwash()` and
`startRegeneration()` activate their configured removal rates during transient
steps. Stop them explicitly, or call `resetDynamicState()` after element
replacement or completed maintenance.

The loading pressure contribution is

$$
\Delta P = \Delta P_{\mathrm{clean}}
         + \Delta P_{\mathrm{capacity}} f_{\mathrm{loading}}.
$$

Breakthrough is zero up to `breakthroughStartFraction` and increases linearly to
one at a loading fraction of one. Loading may exceed the nominal capacity; use
`isReplacementRequired()` as an operating signal rather than assuming the state
is capped.

## Differential-pressure bypass

`setBypassCrackingDeltaP(...)` enables a parallel-path screening calculation.
When unrestricted differential pressure exceeds the cracking setting, the
applied pressure drop is capped and `getBypassFraction()` reports the estimated
unfiltered fraction. Bypassed flow reduces the current removal efficiency.

The model assumes a quadratic parallel bypass path. Use explicit splitter,
valve, recycle, and control equipment when the bypass network or valve
characteristic matters.

## Mechanical design

`Filter` constructs a `FilterMechanicalDesign` with the equipment. After the
process calculation, `calcDesign()` performs preliminary:

- type-specific element or media-area sizing;
- element count and face-velocity checks;
- vessel diameter and tangent-length screening;
- shell and 2:1 ellipsoidal-head membrane-thickness screening;
- inlet/outlet nozzle velocity sizing;
- terminal and collapse differential-pressure checks; and
- weight, bill of materials, purchase-cost, maintenance, and lifecycle estimates.

Maximum operating pressure is in bara and maximum operating temperature is in
kelvin. The design class applies configurable pressure and temperature margins.
The result is screening-level evidence, not a certified pressure-vessel or
filter-element design.

Final design requires the governing code edition, project design rules,
certified material allowables, weld efficiency, corrosion and erosion
allowances, external loads, nozzle reinforcement, closure and support design,
fatigue, fire and relief cases, materials compatibility, inspection strategy,
and vendor review.

## Specialized filters

`CharCoalFilter` selects `ACTIVATED_CARBON` defaults and inherits the generic
Ergun, loading, and regeneration models. It does not remove a named molecular
component. Use an [adsorption bed](adsorption_bed.md) for component adsorption.

`SulfurFilter` performs a solid-aware flash when `S8` is present and captures the
configured fraction of solid-phase S8. It removes that captured amount from the
outlet thermodynamic inventory and adds the captured rate to dynamic loading
during `runTransient(...)`. The sulfur element capacity and installed element
count define the inherited loading capacity. See the
[reactor guide](reactors.md#sulfur-oxidation-reactor) for a complete
`SulfurOxidationReactor` to `SulfurFilter` example.

For molecular removal, use the dedicated
[mercury guard-bed model](../mercury_removal.md),
[adsorption-bed model](adsorption_bed.md), or
[H2S scavenger](../H2S_scavenger_guide.md), as applicable.

## Standards basis

NeqSim stores user-supplied laboratory or vendor results and applies open,
configurable calculations. It does not embed protected acceptance tables,
certify equipment, or establish project compliance.

| Reference | Use in NeqSim |
| --- | --- |
| [ISO 16889:2022](https://www.iso.org/standard/77245.html) | Record and interpolate supplied beta-ratio data |
| [ISO 3968:2017](https://www.iso.org/standard/64104.html) | Record and interpolate supplied clean differential-pressure data |
| [ISO 2942:2018](https://www.iso.org/standard/68005.html) | Record project or supplier evidence of element fabrication integrity |
| [ISO 2941:2009](https://www.iso.org/standard/41062.html) | Compare calculated differential pressure with a user-supplied collapse or burst rating |
| [ASME BPVC Section VIII, Division 1](https://www.asme.org/codes-standards/find-codes-standards/bpvc-viii-1-bpvc-section-viii-rules-construction-pressure-vessels-division-1) | Basis for preliminary shell and head membrane-thickness equations |

Confirm that a cited edition and method apply to the fluid, element, and project.
The ISO references above are primarily hydraulic-fluid filter test methods; gas
coalescers and special media may require different vendor or project methods.

## Related documentation

- [Separators](separators.md) - phase separation and entrainment
- [Absorbers](absorbers.md) - mass-transfer columns
- [Reactors](reactors.md) - sulfur oxidation and other reactor models
- [Streams](streams.md) - stream construction and handling
