---
title: "Fouling thermal-hydraulic rating"
description: "Model tube deposits, heat-recovery loss and pressure-drop growth in a fixed-geometry single-phase heat exchanger."
---

# Fouling thermal-hydraulic rating

`ThermalDesignCalculator` can represent a uniform deposit inside the tubes of an existing exchanger.
`HeatExchanger` can use that geometry to calculate heat recovery and apply correlated pressure losses
to its outlet streams. This supports the feed-preheater use case in [issue #3718](https://github.com/equinor/neqsim/issues/3718).

## Define the two different fouling inputs

| Input | Thermal effect | Hydraulic effect | Area basis |
| --- | --- | --- | --- |
| `setFoulingTube(resistance)` | Adds specified resistance | None | Nominal clean inner tube area |
| `setTubeFoulingLayer(thicknessM, conductivityWmK)` | Cylindrical deposit conduction and changed film area | Reduces bore, updates velocity, Reynolds number and pressure loss | Deposit resistance getter reports nominal outside area |
| `setFoulingShell(resistance)` | Adds specified resistance | None | Nominal outside tube area |

The prescribed resistance and layer contributions are additive. Set the tube resistance to zero when
the layer represents the same deposit; otherwise it would be counted twice. The existing nonzero
default prescribed resistances remain unchanged. Setting layer thickness to zero restores the
nominal bore. Setting a new thickness replaces the old thickness; repeated calculations do not
accumulate deposits. Time evolution can be supplied externally, for example using calibrated
`FoulingModel` resistances, but resistance alone does not determine thickness without conductivity.

For nominal inner and outer diameters $d_i,d_o$ (m), deposit thickness $\delta$ (m), and conductivity
$k_f$ (W/(m K)), the effective bore is $d_f=d_i-2\delta$. The exact deposit resistance on the nominal
outside area is:

$$R_{f,\mathrm{layer},o}=\frac{d_o}{2k_f}\ln\left(\frac{d_i}{d_f}\right)$$

The overall resistance is:

$$\frac{1}{U_o}=\frac{1}{h_o}+R_{f,o}+\frac{d_o}{2k_w}\ln\left(\frac{d_o}{d_i}\right)+\frac{d_o}{d_i}R_{f,i}+R_{f,\mathrm{layer},o}+\frac{d_o}{d_f h_i}$$

Here $h_i,h_o$ are film coefficients in W/(m² K), $k_w$ is metal conductivity in W/(m K), and all
resistances after area conversion have units m² K/W. The metal wall and installed outside area
remain unchanged. Tube friction uses the reduced bore and the existing smooth-tube Darcy correlation.
No deposit roughness or shell-side blockage model is implied.

## Apply the rating to a process

Configure the known geometry in a `ThermalDesignCalculator`, attach it with `setRatingCalculator`,
and set `setRatingArea(calculator.getOutsideHeatTransferArea())` for a geometrically consistent
installed area. Then enable `setUseRatingPressureDrop(true)` before `run()` or `ProcessSystem.run()`.

**Stream 0 is the tube side; stream 1 is the shell side.** Either can be the hotter stream. This
mapping follows existing rating conventions and is independent of the builder's hot/cold labels.
For a cold tube feed and hot shell utility, construct the exchanger with the cold stream first.

The new option defaults to false. Existing thermal-only rating retains its inlet pressures. With
the option enabled, each outlet pressure is its own inlet pressure minus its correlated pressure
loss. An enthalpy flash at that outlet pressure conserves the heat duty and material inventory.
Both outlet states are committed only after both flashes pass validation. Invalid pressure budgets,
missing rating data, nonfinite properties, zero flows and multiphase states raise an exception.
Fixed outlet-temperature and delta-T specifications are incompatible with this rating option.

The calculation uses inlet transport properties and heat-capacity rates obtained from enthalpy
secants across the inlet temperature interval. It is a lumped single-phase rating, not a segmented
two-phase model. The selected existing effectiveness relation is retained; the default is ideal
counterflow. In particular, the legacy `"shell and tube"` label is a counterflow approximation,
not a complete TEMA multipass thermal model. Use a more detailed model for temperature-dependent
transport properties, phase change, maldistribution or detailed multipass design.

Rating recalculates when the process is rerun, including after mutation of its attached calculator.
Pressure losses are always calculated from inlet pressure, so repeated runs do not compound them.
The calculator remains transient in the exchanger's serialization contract: reattach it after
deserialization before using rating.

## Validation and process interpretation

`TubeDepositThermalDesignTest` checks cylindrical resistance against radial conduction and laminar
pressure losses against Hagen–Poiseuille plus the documented return losses at three thicknesses.
It also checks the zero-deposit limit, resistance area basis, cleaning, repeated runs and blocked
bores. `HeatExchangerFoulingRatingTest` checks pressure mapping, both directions of heat transfer,
component/mass/enthalpy conservation, calculation identity, equal inlet temperatures and failure
without committing outlet states.

A downstream `Heater` with a fixed outlet temperature quantifies the additional feed-heating duty
caused by lower heat recovery. This is an energy requirement at the selected target state, not an
independent prediction of distillation-column reboiler duty. A column model with fixed separation
targets is required to make that further claim.

The inspiring paper is Soylu and Demirel (2025), *Thermal-Hydraulic Analysis of a Shell-and-Tube
Heat Exchanger Due to Fouling in the Distillation Process Using Aspen Plus*,
[DOI 10.1115/1.4068191](https://doi.org/10.1115/1.4068191). Its complete geometry, fluid data and case
inputs were not available during this implementation. The analytical checks and synthetic process
demonstration are not a reproduction of its Aspen cases or an experimental validation.

The resistance derivation follows [MIT's cylindrical conduction notes](https://web.mit.edu/course/16/16.unified/www/FALL/thermodynamics/notes/node119.html)
and [combined convection/conduction notes](https://web.mit.edu/16.unified/www/FALL/thermodynamics/notes/node123.html).
