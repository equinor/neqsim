---
title: Dry Gas Seal Condensation and Conditioning Screening
description: Sampled PH/TP evidence, retained-gas GCU duties, units, failure handling, and qualification limits.
---

# Dry gas seal condensation and conditioning screening

`DryGasSealAnalyzer` screens seal-gap isenthalpic expansion, a pressure/temperature
condensation grid, dead-leg cooling and condensate accumulation. It also estimates
conditioning thermal duties. It does not establish a complete compressor/seal operating
permissive, filter cleanliness, hydrate-free operation or protection-system adequacy.

## Inputs and physical basis

Supply the composition and mixing rule through `setSealGas`. The analyzer keeps an owned
clone. Configure cavity and primary-vent pressures with explicit units; bara is absolute.
The inlet-to-vent pressure interval must be positive and ordered. Set temperatures in C or K.
All setters invalidate previous analysis evidence, so rerun after a basis change.

`setSealLeakageRate` accepts NL/min, Nm3/hr or kg/hr. Normal volumes are **ideal-gas
molar equivalents at 273.15 K and 1.01325 bara**, not actual volumetric flow at seal pressure
or an EOS-flashed liquid volume at standard conditions. Mass leakage is converted using
the supplied composition's initialized molar mass, even if flow was entered first.
Unknown leakage units, negative flow and nonfinite flow are rejected.

`setGCUSupplyFlowNLmin` specifies conditioning inlet throughput independently of leakage.
Without it, the legacy screening assumption remains three times primary leakage. Actual
supply includes the process-side labyrinth flow and other consumers; derive it from the
OEM seal/support-system design rather than treating leakage as total demand.

The expansion and TP grid sum hydrocarbon and aqueous liquid volumes, including single
liquid states. The grid spans the complete configured vent-to-cavity pressure range and
starts 15 K below the lower of ambient and cavity temperature. Both scans report expected
point counts, failed point counts and `calculation_complete`.

`isSafeToOperate()` is retained as a legacy name. A true value means **only the two sampled
condensation scans pass** the legacy 0.01 vol-percent liquid threshold. Missing/incomplete
scans, nonfinite fractions and vent-flash errors cannot pass. A grid cannot exclude a narrow
unsampled condensation region; the result does not establish OEM superheat margin,
continuous-path dryness, gas cleanliness or compressor protection readiness.

## GCU thermal duties and composition

The legacy design screen samples dew temperatures across pressures and chooses its
maximum as the cooling-pressure basis. This approximates a maximum dew temperature;
it is not a rigorously traced cricondentherm and does not design the booster required
if conditioning pressure is below seal supply pressure. No valid dew-temperature solution
means the GCU requirement is unresolved, not proof that conditioning is unnecessary.

Cooling flashes the feed at the selected pressure. Only the gas phase survives ideal
liquid separation. Reheating uses that retained composition and molar flow:

$$Q_h=\dot n_{gas}(h_{out}-h_{gas,cold})$$

Here $Q_h$ is W, $\dot n_{gas}$ is mol/s and the enthalpies are J/mol. The results expose
`dry_gas_composition`, `gas_outlet_mol_fraction`, `dry_gas_molar_flow_mol_s` and enthalpy
values so the calculation can be checked independently. Reheat target conservatively
uses the feed's sampled maximum dew temperature plus the configured margin; a full
conditioned-gas downstream envelope and heater/booster design remain to be qualified.

`cooling_duty_kW`, `reheat_duty_kW` and `total_thermal_duty_kW` are thermal loads.
The legacy `total_electrical_kW` key remains but is NaN: electrical demand requires a
refrigeration COP, heater efficiency and booster/auxiliary power model. Consumers must
handle nonfinite values; `toJson()` retains its existing special-floating-value behavior.

Margins in `setGCUMargins` are user/OEM inputs. The legacy 17 K defaults are screening
assumptions and must not be described as universal API 692 requirements.

## Design philosophy and validation

A complete conditioning model should connect supply/source selection or a booster,
pressure regulation, cooling/separation, coalescing/particulate filtration and reheating
to named NeqSim streams. Check hydrocarbon and water condensation after JT letdown,
minimum ambient/dead-leg temperature, standby/startup/shutdown, hydrate conditions,
filter differential pressure, heater capacity, liquid removal and component/energy balance.
The current analyzer does not model these as one validated composable supply system.
Its fixed-pressure dead-leg cooling and pressure-impact estimates remain simplified.

`DryGasSealEvidenceTest` checks water/single-liquid detection, incomplete/nonfinite
evidence, normal-flow conservation, independently flashed separated composition and
retained-gas reheating balance using public synthetic fluids. These are regression and
conservation checks, not independent experimental qualification of the EOS or seal model.

Follow the acceptance criteria in [issue #4144](https://github.com/equinor/neqsim/issues/4144)
and the [program roadmap #4149](https://github.com/equinor/neqsim/issues/4149).
Primary technical references include the [John Crane Type 28 AT operation guide](https://www.johncrane.com/media/l3gp1gxw/jcr0107-type-28at-dry-gas-seal-iom.pdf)
and [EagleBurgmann seal management systems](https://www.eagleburgmann.com/en/products/seal-supply-systems/gas-supply-systems/seal-management-system-sms/).
Apply the requirements for the actual supplied seal model and project.
