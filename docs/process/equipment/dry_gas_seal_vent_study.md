---
title: Compressor dry gas seal vent studies
description: "Calculate tandem seal cavity pressures, flare and atmospheric loads, and bearing-side process gas leakage under normal and fault conditions."
---

# Compressor dry gas seal vent studies

Use `neqsim.process.equipment.compressor.DryGasSealVentStudy` to answer a design
question for one compressor end. Given a leakage load and installed path capacities,
what pressure is required in each cavity, and where does process gas go?
Create a separate case for each operating mode, fault combination and receiving-header
pressure. A case is independent of the running compressor simulation.

## Establish the study boundary

| Input | Required basis |
| --- | --- |
| Primary-face source | Available upstream pressure in bara and process-origin leakage in kg/s for the case. Use a vendor leakage envelope or measured duty. Check applicability at the **solved** primary cavity backpressure. |
| Support gas | Non-process mass entering each cavity in kg/s after all upstream splits. Attribute process-origin seal gas to the tagged process load. Buffer or separation gas that returns to the casing or bypasses the cavities stays outside these balances. |
| Secondary face | Increasing table of signed transfer in kg/s versus primary minus secondary cavity pressure in bar. Include (0,0). Supply reverse data where applicable, and replace the table for a failed secondary seal. A single (0,0) pair specifies a gas-tight stage. |
| Primary vent | Installed cavity pressure in bara versus flow to the LP flare in kg/s, at a stated flare header pressure. Include restrictions, instruments, fittings, valves and piping. A blocked path uses `Vent.blocked()`. |
| Secondary vent and bearing housing | Use the two-column vent rating for a single atmospheric outlet. For a separation-seal leak, provide three columns at the same cavity pressure: absolute pressure, atmospheric vent flow and bearing-housing flow. Both outlet rates must be determined at their respective receiver pressures and the same gas condition. |
| Integrity limits | Project/OEM cavity pressure ratings, allowable forward and reverse seal differentials, bearing housing pressure, separation-seal rating and permitted releases. Record the weakest piping or instrument rating on each route. |

For a geometry sensitivity, `Vent.fromGasPipe` takes a NeqSim fluid (such as
`stream.getThermoSystem()`), receiver pressure in bara, effective length in m,
inside diameter in m, absolute roughness in m and increasing mass-rate points in kg/s
starting at zero. It clones the gas, flashes it at the receiver, and uses receiver
density and viscosity at the specified temperature. It evaluates the horizontal
isothermal relation $p_1^2=p_2^2+f_D(L/D)G^2 p_2/\rho_2$ in Pa, with mass flux
$G=\dot m/A$ and the Darcy friction factor from `AdiabaticPipe`. Include fittings
in the equivalent length. Refine the rate grid until the **study outputs** stabilize.
The helper rejects sampled nongas states and outlet Mach above 0.3. For high-speed
or choked discharge, discrete orifices, appreciable property changes, elevation
effects or condensation, supply an independently rated assembly curve. A horizontal
pipe curve alone omits the other parts of an installed vent path.

## Run a combined failure case

The following synthetic example uses an elevated primary-face leak and a
lower-resistance secondary face. Pressures are absolute. Flows are mass rates.
The primary and secondary vent ratings begin at 1.1 bara and 1.0 bara respectively.

```java
Vent flare = new Vent(new double[] {1.1, 4.1}, new double[] {0, 0.030});
Vent atmosphere = new Vent(new double[] {1.0, 4.0}, new double[] {0, 0.030});
DryGasSealVentStudy failed = new DryGasSealVentStudy(flare, atmosphere,
    new double[] {-0.020, 0, 0.020}, new double[] {-0.4, 0, 0.4});
Result combined = failed.evaluate(50, 0.010, 0.0002, 0.0003);
```

`Vent` and `Result` are nested types of `DryGasSealVentStudy`. The example in
`DryGasSealVentStudyTest` also evaluates the intact secondary face using
a differential of 40 bar at 0.020 kg/s. The process-origin primary load is
0.0008 kg/s when intact and 0.010 kg/s when failed. The purge rates stay fixed.

| Case | Primary cavity (bara) | Secondary cavity (bara) | Flare total (g/s) | Atmospheric total (g/s) | Process gas to atmosphere (g/s) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Both faces intact | 1.1923 | 1.0377 | 0.923 | 0.377 | 0.062 |
| Primary failure | 2.0705 | 1.0795 | 9.705 | 0.795 | 0.486 |
| Secondary failure | 1.1227 | 1.1073 | 0.227 | 1.073 | 0.618 |
| Combined failure | 1.6245 | 1.5255 | 5.245 | 5.255 | 4.857 |

The primary-only failure controls primary cavity pressure here. The combined failure
controls atmospheric process-gas discharge. For an illustrative 1.50 bara secondary
vent rating, the combined case exceeds the rating by 0.0255 bar.

## Interpret the result

- `transferKgS` is positive towards the secondary cavity. `flareKgS`,
  `atmosphereKgS` and `bearingKgS` report total mass. Their corresponding
  `*ProcessKgS` fields report the process-origin part after perfect cavity mixing.
  This tracer can contain several species. Use the actual composition for a methane
  emission or dispersion calculation.
- Compare `primaryBara` and `secondaryBara` to the respective pipe, cavity and
  instrument ratings. Compare their signed difference to the secondary-seal forward
  and reverse limits. `minimumPressureMarginBar` returns the smallest of these
  four margins. Check primary-face and separation-seal differentials against their
  actual adjacent chamber pressures. Check bearing ingress and atmospheric release
  independently of pressure margins.
- Include blocked/restricted primary and secondary paths, elevated flare pressure,
  separation-seal failure and loss of each support supply, singly and in credible
  combinations. Set a failed purge contribution to its case value and rerate affected
  paths for the changed mixture. Primary supply loss on a pressurized compressor can
  leave a process leak. Use its case-specific source pressure and leakage.
- The solver balances mass at both cavities and interpolates within the supplied
  tables. An isolated cavity, a required outlet backflow, insufficient capacity,
  unavailable reverse rating or incompatible primary source pressure leaves the case
  unresolved. An unresolved case supplies no pressure margin.
- Compare the source pressure, cavity pressures and vent flow against the actual
  instrument taps. Convert mass rate to the meter's declared reference volume and gas
  basis before using the [dry gas seal monitor](dry_gas_seal_monitoring.md).
  Its advisory timers require a time series. This steady study cannot establish
  peak pressure before trip, isolation or depressurization.
- Run each compressor end at consistent header boundary conditions. Where both ends
  share a restrictive header, calculate the header backpressure against their combined
  flow and iterate to consistency. The two local cavity balances alone cannot resolve
  a shared-header network or a transient flare load.

The regression cases use independent linear-network solutions, component balances
and a compressible laminar pipe solution. Machine-specific use requires installed
as-built capacities, OEM data and validation over the operating envelope.
See the [engineering review](dry_gas_seal_engineering_review.md) for the seal
arrangement, standards scope and literature.
