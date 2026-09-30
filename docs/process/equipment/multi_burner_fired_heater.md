---
title: "Multi-burner gas-fired hot-oil heater"
description: "Native NeqSim equipment with optional detailed finite-rate combustion, burner switching, tube radiation, refractory loss and a separate hot-oil energy balance."
---

# Multi-burner gas-fired hot-oil heater

`MultiBurnerFiredHeater` is a native `ProcessSystem` unit with a fuel inlet, a common
air inlet, a flue-gas outlet and optional hot-oil inlet/outlet. It exposes individual
burner states while preserving the total supplied fuel. An optional Cantera backend
solves real chemical reaction rates and ideal-gas mechanism thermochemistry.

The intended application is a reduced engineering model of an onshore gas-fired
hot-oil heater. It is useful for separating changes in burner loading, local air
capture, finite-rate CO burnout and stack dilution. It does not certify industrial
CO emissions or replace a resolved flame/CFD model, furnace vendor data or a plant
combustion safety system. No plant measurements or proprietary geometry are used
by the executable example.

## Draft validation status

This feature remains a research draft. The native seven/five burner demonstration
now passes against workspace Java classes in hosted CI with the default 1e-6
projection tolerance unchanged. The earlier native hydrogen-element rejection
has not been reproduced in this hosted environment, including with the old
mappings. Its historical cause is therefore not established. Supported ammonia
and methanol are now retained in the EOS projection without claiming that they
caused that rejection.
For the seven/five burner cases, omitted hydrogen fractions are approximately
8.72e-7 / 8.62e-7; unmapped mass fractions are approximately 1.52e-7 / 1.50e-7.
The native integration job also reports the result with the old mappings,
allowing a direct comparison instead of assuming the cause of a prior failure. Exact species remain available after rejection.

Every backend result now includes `elementProjectionDiagnostics`: inlet, exact
outlet and omitted molar atom flows, exact-mechanism and EOS-projection relative
residuals, and omitted species ranked by their contribution to each element.
Relative values use the inlet elemental inventory, matching the Java gate. For
an absent inlet element the relative value is null; absolute outlet and omitted
flows remain available. `unmappedMechanismMassFraction` is a separate diagnostic.
No diagnostic changes the existing acceptance tolerances or renormalizes species.

A bounded comparison with supplied field observations did not establish plant
calibration. Air capture, fuel assay, geometry and temperature remain insufficiently
identified. The example-only staged-air curve is a conditional sensitivity; it
has not demonstrated the cause or amplitude of the observed plant CO decrease.
Measurements, site details and external mechanism files are excluded from the PR.

## Physical network

Each lit burner has a fixed-volume perfectly stirred reactor (PSR). Fuel shares
are normalized over lit burner weights. The local captured air is an independently
configured fraction of the common air stream. Products mix by conserving species,
mass and enthalpy, and explicitly uncaptured air joins that mixing point. A common
constant-pressure reacting plug-flow region then integrates burnout and cooling
until its accumulated swept volume reaches the configured chamber volume.

Switching from seven to five burners increases fuel per lit burner when the total
fuel stream is unchanged. It does not, by itself, determine local air, temperature
or CO. Two air-routing examples are available: redistribute all common air among
the five lit burners, or retain caller-specified burner capture fractions and mix
the explicitly uncaptured air downstream. Neither option asserts air leaks through
closed burner ports.

Burner IDs are caller-defined labels. In the example, IDs 5 and 6 can represent the
two burners nearest the air inlet under the caller's ordering. The reduced network
does not resolve burner distance or inlet position. Spatial effects must be expressed
through justified air-capture fractions or resolved by an external flow model.

## Inputs and units

| Input | Unit or meaning |
|---|---|
| Fuel and air streams | NeqSim streams; composition, flow, temperature and absolute pressure |
| Burner index/state | Zero-based installed index; on or off |
| Burner fuel weight | Positive relative share, normalized over lit burners |
| Burner air-capture fraction | Fraction of total air; active fractions must sum to at most one |
| Burner reactive volume | m3, fixed stirred-zone volume |
| Chamber diameter/axial length | m, distinct from stack height |
| Common reactive volume | Gross cylinder volume minus active burner-zone volumes |
| Effective tube area | m2 |
| Effective convection coefficient | W/(m2 K) |
| Effective gas/tube emissivity | Fraction from zero to one, including the assumed view-factor treatment |
| Primary heating-area fraction | Fraction of tube and refractory area assigned to burner PSRs |
| Effective oil-side tube sink temperature | K, prescribed boundary; not automatically set to inlet or outlet oil temperature |
| Brick thickness/conductivity | m and W/(m K), caller-sourced |
| Refractory emissivity/convection | Fraction and W/(m2 K), caller-sourced effective values |
| Ambient cold-side temperature | K |
| Ignition initialization temperature | K, initial burner inventory; cold reactant inflow is unchanged |
| EOS projection tolerance | Maximum omitted mass fraction and fraction of each inlet element, default 1e-6 |

The example's 10–40 MW scaling changes reactive volumes and tube area explicitly.
It does not extrapolate a laboratory flame solely by multiplying its mass flow.
Firing duty is fuel LHV input. Useful oil duty, shell loss and stack energy are
reported separately; a nominal 30 MW firing heater is not a promise of 30 MW oil duty.

## Heat and energy balances

Tube exchange combines convection and grey radiation:

$$Q_{oil}=A_t[h_t(T_g-T_o)+\epsilon_t\sigma(T_g^4-T_o^4)]$$

Here $A_t$ is effective tube area [m2], $h_t$ is the convection coefficient
[W/(m2 K)], $T_g$ and $T_o$ are gas and prescribed tube-sink temperatures [K],
$\epsilon_t$ is effective emissivity, and $\sigma$ is the Stefan–Boltzmann constant
[W/(m2 K4)]. The common plug-flow region integrates the spatially distributed heat
independently as extra CVODES state variables, alongside accumulated swept volume.

The refractory inner temperature $T_w$ solves a monotonic steady balance:

$$h_r(T_g-T_w)+\epsilon_r\sigma(T_g^4-T_w^4)=\frac{k_r}{d_r}(T_w-T_a)$$

$h_r$, $\epsilon_r$, $k_r$, $d_r$ and $T_a$ are the specified refractory-side
convection, emissivity, conductivity, thickness and ambient temperature. Shell
loss is the conduction term multiplied by effective refractory area. The cold
boundary is prescribed; external shell convection, wall thermal inertia and
refractory density/heat capacity are not modeled in this steady version.

The reported complete energy split is:

$$Q_{fuel}+H_{in,sens}=Q_{oil}+Q_{shell}+H_{stack,sens}+Q_{chemical,out}$$

Chemical power uses complete gaseous products at 298.15 K (LHV water-vapor basis).
Stack sensible heat uses the actual outlet composition relative to 298.15 K.
Residual chemical power includes incomplete combustion and fuel slip, rather than
calling all unabsorbed fuel energy a stack sensible loss. Inlet sensible heat is
included explicitly. The detailed result contains the relative residual, individual
PSR balances and independently integrated post-flame wall-heat balance.
Each burner now separately rejects mass-flow residuals above 1e-7, relative
pressure deviations above 1e-5 and energy residuals above 1e-6 of the local
enthalpy/heat scale (with a 1 MJ/kg mass-flow scale floor). This prevents local
errors from cancelling in the total furnace balance. `postFlameVolumeM3` reports
the actual integrated swept volume; `specifiedPostFlameVolumeM3` reports the
requested volume. A relative mismatch above 1e-6 rejects the solve.
`airChemicalPowerW` exposes any chemical energy carried by the air stream, so
that reactive contaminants can be included explicitly in the full energy split.

If a hot-oil stream is connected, `HotOilHeatBalance` applies only the useful tube
heat to a cloned oil fluid with a NeqSim PH flash, preserving pressure and flow.
The existing outlet stream object is updated, so downstream equipment stays
connected. Oil enthalpy gain is checked separately. Cantera NASA formation enthalpy
and NeqSim EOS enthalpy have separate reference contracts; equality across the two
packages is not assumed. The fixed effective tube-sink temperature is a reduced
boundary condition, not a solved oil-coil temperature profile.

## Optional detailed-chemistry backend

Java requires no Cantera dependency and does not select a chemical mechanism
implicitly. `CombustionKineticsBackend` accepts a version-1 JSON SI-unit contract.
`examples/combustion/cantera_backend.py` implements that interface with JPype and
Cantera 3.2. The adapter has no fabricated global rate or CO factor.

The executable demonstration defaults to Cantera's bundled GRI-Mech 3.0 only as a
software/physics smoke test. GRI was developed principally for natural-gas chemistry;
that choice is not a propane validation claim. For C2/C3-rich industrial studies,
select and qualify a suitable mechanism against relevant published measurements.
AramcoMech 3.0 can be supplied externally; no mechanism file is redistributed in
this change. Verify its source, license and validity before use.

Default component mappings are one-to-one and independently checked against the
NeqSim element database. Methane, ethane, propane and common stable combustion
species are mapped when present in the selected mechanism. Methanol (`CH3OH`) and
ammonia (`NH3`) are also retained: both have component and elemental records in
NeqSim. This preserves real stable species; it does not recombine radicals or
replace missing species with surrogate molecules. C4 mappings are enabled
only when the mechanism contains `C4H10`/`NC4H10` and/or `IC4H10`. Unsupported inlet
species fail explicitly. GRI's C2H2 remains in the exact result because NeqSim does
not have independently supported acetylene component/element data for this mapping.

The exact mechanism species and atom counts are retained, including radicals and
intermediates. The stable EOS projection must satisfy both the total missing mass
fraction and every elemental omission limit. Missing atoms are never renormalized
into available species. An unaccepted projection throws before downstream execution
and leaves the exact result available for diagnosis. Significant negative numerical
species fractions are rejected; sub-1e-12 negative trace fractions are clipped and
their total is reported explicitly, without renormalizing the remaining species.

Mechanism provenance includes solver version, species/reaction counts and a SHA-256
fingerprint of canonical resolved phase/species/reaction input data. Generated
timestamps and phase state are excluded from that fingerprint.

## CO, fuel slip and other species

CO is obtained from the reacted mechanism state. Reports include kg/h, dry ppmv
and dry mg/Nm3 at 273.15 K and 101325 Pa. Reference-O2 correction is separately
identified; it is invalid and returns null near or above 20.9% dry O2. Compare plant
values only after checking normal/actual volume, wet/dry basis and reference O2.

Low CO alone is not evidence of good combustion. Inspect useful oil heat, burner
temperature, carbon-to-CO2 conversion and total organic-carbon fraction, including
oxygenates. The `BURNING` diagnostic requires positive useful heat, residual chemical power
below 1% of the supplied chemical power, and (for carbon-containing fuel) more
than 99% carbon conversion to CO2 with less than 1% organic carbon. The energy
criterion prevents a carbon-only check from hiding H2 slip. Carbon-free fuel
uses the energy criterion without dividing by a nonexistent carbon inventory.
These thresholds are diagnostic conventions, not burner safety limits. Other points retain
their species and energy diagnostics. Extinguished cases cannot explain a plant
that continues to supply useful hot-oil heat.

The adapter also reports CH4 and organic-carbon/NMVOC concentration surrogates in
mgC/Nm3. The organic sum includes exact C/H-containing gas species and oxygenates;
NMVOC excludes methane. These are carbon-mass diagnostics, not a calibrated FID
instrument response. Pollutants absent from a selected mechanism are marked
`NOT_COMPUTED`, never reported as a modeled zero. AramcoMech 3.0, for example,
does not include NO, NO2, N2O, SO2, SO3 or HCl in the supplied research mechanism.

## Executable example and validation

After building the branch and installing Cantera 3.2 plus the normal NeqSim Python
runtime, run `examples/combustion/multi_burner_fired_heater.py` with the repository
root and a selected mechanism. `--sweep` adds an explicitly assumed staged-air
entrainment hypothesis at constant total air. Its nominal capture coefficient is
not fitted to plant measurements and is not a default native-equipment law.

The optional sweep declares `--sweep-projection-tolerance 1e-3` and prints the actual
mass and elemental omissions. Its largest omitted elemental fraction in the
synthetic run was 3.37e-4. The native default remains 1e-6; callers must assess any
explicitly relaxed downstream projection. Exact mechanism emissions remain available.
The prescribed 550 K effective sink makes the final stack temperature relatively
insensitive and does not validate the plant temperature response.

For a built checkout with the normal NeqSim Python runtime and Cantera 3.2:

```bash
python examples/combustion/multi_burner_fired_heater.py --project-root . --mechanism gri30.yaml --sweep --scale-study --output heater_results.json
python -m unittest discover -s examples/combustion -p "test_*.py"
```

The example constructs SRK fuel, common-air and hot-oil streams, attaches the
backend, adds the native unit to `ProcessSystem`, executes seven/five lit burners
at constant total fuel/air, and prints complete energy/CO/conversion/projection
diagnostics. The two off burner IDs can represent the nearest pair only under the
caller's ordering; distance is not a resolved model input.

`--scale-study` executes explicitly scaled 10, 20, 30 and 40 MW cases. The optional
constant-air `--sweep` declares a separate 1e-3 projection bound for frozen trace
oxygenates/intermediates (maximum 0.1% of any element), adjustable with
`--sweep-projection-tolerance`. The native default stays 1e-6. Every sweep row
prints the selected bound, actual omitted elemental fractions and omitted mass;
exact species and CO reporting remain authoritative. A stricter bound can reject
a quenched EOS projection without invalidating the retained detailed chemistry.
The prescribed 550 K effective tube sink and large illustrative area keep the
example's final stack temperature close to that sink over the sweep. This can
hide a stronger bulk-temperature trend seen in a real heater. Inspect the burner
temperature separately, and supply actual oil-side/geometry boundaries before
attempting a plant comparison. A CO peak is not numerically tuned to the plant.

The public synthetic 30 MW GRI smoke case produced 26.27 MW useful oil heat,
0.09 MW shell loss, 3.65 MW stack sensible energy, 574 K flue temperature and
3.05/2.86 ppm dry CO for seven/five lit burners with all air redistributed. The
maximum unmapped mass fraction was 1.6e-7, below the default 1e-6 gate. Full
mechanism energy residual was below 6e-8 relative. These numbers validate execution,
conservation and a conditional trend; they are not the private plant's CO curve.

Focused Java tests cover ports, total-supply preservation, switching, outlet
identity, failure invalidation and utility PH closure. Optional Python tests cover
finite-rate chemistry, carbon-free hydrogen fuel, independently integrated nonzero wall heat, species/element
balances, radiation effects, reproducible provenance and seven/five burner cases.

The `Optional combustion physics` workflow installs Cantera 3.2.0 and runs these
Python tests for combustion changes; Java-only CI is not chemistry validation.
A separate native job compiles workspace Java classes and runs the seven/five
burner demonstration through JPype with the default projection tolerance. The
example prints retained omitted-atom diagnostics before rethrowing a rejected
projection.
Analytical diagnostics tests can run without Cantera. This validates the public native seven/five burner demonstration. Broader native
operating-envelope validation, mechanism qualification and plant calibration
remain separate acceptance gates.

## Related APIs and primary sources

- [Chemical reactors](reactors.md)
- [Python extension patterns](../../development/python_extension_patterns.md)
- [Cantera reactor equations](https://cantera.org/3.2/reference/reactors/index.html)
- [Cantera steady combustor example](https://cantera.org/3.2/examples/python/reactors/combustor.html)
- [Cantera extensible reactor example](https://cantera.org/3.2/examples/python/reactors/custom2.html)
- [GRI-Mech 3.0 bundled mechanism](https://github.com/Cantera/cantera/blob/v3.2.0/data/gri30.yaml)
- [University of Galway combustion mechanisms](https://www.universityofgalway.ie/combustionchemistrycentre/mechanismdownloads/)
