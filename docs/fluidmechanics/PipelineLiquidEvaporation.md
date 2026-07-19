---
title: "Finite-rate Evaporation and Gas Dissolution in Pipelines"
description: "Maxwell-Stefan mass transfer, heat transfer, droplets, bubbles, films, slip, and completion distance."
---

# Finite-rate Evaporation and Gas Dissolution in Pipelines

## What this model answers

`PipelineEvaporationStudy` estimates the axial distance needed for an injected hydrocarbon liquid to fall below a
specified remaining mass fraction. `PipelineDissolutionStudy` applies the same finite-rate calculation to injected gas
bubbles dissolving into oil or water. The supported geometries are:

- spherical droplets represented by an evolving Sauter mean diameter, $d_{32}$; and
- a wall film represented by an evolving thickness and wetted-perimeter fraction; and
- spherical gas bubbles represented by an evolving Sauter mean diameter.

The completion criterion is the remaining fraction of the *initially injected dispersed-phase inventory*: liquid for
evaporation and gas for dissolution. Hydrodynamic holdup or void fraction is never used as a transfer measure. If the
continuous liquid reaches thermodynamic saturation before the injected gas meets the criterion, the result is
incomplete and reports the full profile to the pipe outlet; the solver does not force phase removal.

## Model formulation

At each accepted axial position the model creates a local NeqSim droplet, bubble, or annular flow node and solves the
`KrishnaStandartFilmModel` boundary. This gives:

1. EOS fugacity equality at the gas-liquid interface;
2. binary diffusivities from NeqSim physical-property models;
3. coupled Maxwell-Stefan resistance matrices in gas and liquid;
4. thermodynamic-factor and finite-flux corrections; and
5. simultaneous interphase heat and component fluxes.

Droplet and bubble transfer coefficients use Ranz-Marshall in the continuous phase and Kronig-Brink internally. The optional
Abramzon-Sirignano correction uses the aggregate injected-vapor *mass fraction* to calculate the Spalding mass-transfer
number and is applied only to evaporating liquid droplets. Film calculations use the annular-flow transfer correlations.

The sign convention is positive from gas to liquid. Therefore an evaporating liquid component normally has a negative
flux.

### Interfacial area

For droplets, conservation of droplet number gives

\[
\frac{A_i}{L}=\frac{6\dot V_L}{u_L d_{32}}, \qquad
d_{32}=d_{32,0}\left(\frac{\dot V_L}{\dot V_{L,0}}\right)^{1/3}.
\]

For a wall film,

\[
\frac{A_i}{L}=f_w\pi(D-2\delta), \qquad
\delta=\delta_0\frac{\dot V_L}{\dot V_{L,0}}.
\]

For bubbles, the equivalent conserved-population relation is

\[
\frac{A_i}{L}=\frac{6\dot V_G}{u_G d_{32}}, \qquad
d_{32}=d_{32,0}\left(\frac{\dot V_G}{\dot V_{G,0}}\right)^{1/3}.
\]

The geometry follows total liquid-phase volume, so gas absorbed into the liquid can grow a droplet or film even while
the tracked injected material evaporates. Completion still uses only the mass provenance of the initial liquid phase.

These relations do not model breakup, coalescence, entrainment, deposition, or dry-patch formation. Supply a realistic
inlet $d_{32}$, film thickness, and wetted fraction, and perform sensitivity cases.

### Slip and residence time

`USER_SPECIFIED` retains separate user-supplied actual gas and liquid velocities. `TERMINAL_VELOCITY` recalculates a
local Schiller-Naumann force balance as particle diameter and phase properties change. The terminal speed is used in the
Ranz-Marshall particle Reynolds number. Its gravity-aligned component is projected onto the pipe axis, so it also
changes dispersed-phase residence time and area per pipe length. Pipe inclination is in radians and positive uphill.
For a horizontal pipe, terminal rise or settling is transverse: it enhances transfer but does not create an axial
gravity-slip component.

The force-balance option represents dilute spherical particles. Use prescribed velocities or an external hydraulic
profile for dense dispersions, churn/slug flow, annular films, and cases governed by pressure-gradient momentum closure.

### Conservative axial integration

The component update over a step is

\[
\Delta\dot n_i=N_i A_i,
\]

with equal and opposite changes in the two phases. Donor inventories are bounded so component flows cannot become
negative. Separate gas and liquid enthalpy targets use the boundary heat fluxes; optional wall heat is added to the gas
for droplets and to the liquid for bubbles or a wall film. The step is halved until both the maximum donor depletion and estimated
temperature change meet their configured limits.

No TP flash is run between steps. A TP flash would impose instantaneous phase equilibrium and erase the finite-rate
question being solved.

## Java example

```java
import neqsim.process.equipment.pipeline.evaporation.LiquidDistribution;
import neqsim.process.equipment.pipeline.evaporation.PipelineEvaporationConfig;
import neqsim.process.equipment.pipeline.evaporation.PipelineEvaporationResult;
import neqsim.process.equipment.pipeline.evaporation.PipelineEvaporationStudy;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface inlet = new SystemSrkEos(335.15, 10.0);
inlet.addComponent("methane", 500.0, "kg/hr", 0);
inlet.addComponent("ethane", 20.0, "kg/hr", 0);
inlet.addComponent("n-heptane", 1.0, "kg/hr", 1);
inlet.addComponent("nC10", 1.0, "kg/hr", 1);
inlet.createDatabase(true);
inlet.setMixingRule(2);
inlet.setPhaseType(0, PhaseType.GAS);
inlet.setPhaseType(1, PhaseType.OIL);
inlet.getPhase(0).setTemperature(335.15);
inlet.getPhase(1).setTemperature(295.15);
inlet.initBeta();
inlet.init_x_y();
inlet.init(3);
inlet.initPhysicalProperties();

PipelineEvaporationConfig settings = new PipelineEvaporationConfig();
settings.setLiquidDistribution(LiquidDistribution.DROPLETS);
settings.setPipeLength(100.0);
settings.setPipeDiameter(0.20);
settings.setGasVelocity(10.0);          // actual phase velocity, m/s
settings.setLiquidVelocity(0.5);        // droplet velocity, m/s
settings.setInitialDropletDiameter(200.0e-6);
settings.setOverallWallHeatTransferCoefficient(8.0); // W/(m2 K)
settings.setAmbientTemperature(323.15);
settings.setCompletionFraction(1.0e-4); // 99.99% of injected mass evaporated

PipelineEvaporationResult result = new PipelineEvaporationStudy(inlet, settings).run();
if (result.isCompleteEvaporation()) {
  System.out.println("Completion distance [m]: " + result.getCompleteEvaporationDistance());
} else {
  System.out.println("Remaining fraction: "
      + result.getProfile().get(result.getProfile().size() - 1)
          .getRemainingInjectedLiquidFraction());
}
System.out.println("Maximum component balance error [mol/s]: "
    + result.getMaximumComponentMolarBalanceError());
System.out.println("Relative energy balance error: " + result.getRelativeEnergyBalanceError());
```

For a film, change the distribution and specify the geometry:

```java
settings.setLiquidDistribution(LiquidDistribution.WALL_FILM);
settings.setInitialFilmThickness(0.50e-3);
settings.setWettedPerimeterFraction(1.0);
```

## Gas dissolution example

Use an explicit injected gas in phase 0 and an undersaturated continuous liquid in phase 1. For water-rich systems,
prefer CPA or electrolyte CPA with validated gas-water interaction parameters.

```java
import neqsim.process.equipment.pipeline.evaporation.DispersedPhaseSlipModel;
import neqsim.process.equipment.pipeline.evaporation.EvaporationProfilePoint;
import neqsim.process.equipment.pipeline.evaporation.PipelineDissolutionResult;
import neqsim.process.equipment.pipeline.evaporation.PipelineDissolutionStudy;
import neqsim.thermo.system.SystemSrkCPAstatoil;

SystemInterface inlet = new SystemSrkCPAstatoil(295.15, 50.0);
inlet.addComponent("CO2", 0.10, 0);   // injected gas flow, mol/s
inlet.addComponent("water", 20.0, 1); // continuous liquid flow, mol/s
inlet.createDatabase(true);
inlet.setMixingRule(10);
inlet.setPhaseType(0, PhaseType.GAS);
inlet.setPhaseType(1, PhaseType.AQUEOUS);
inlet.initBeta();
inlet.init_x_y();
inlet.init(3);
inlet.initPhysicalProperties();
inlet.setPhaseType(1, PhaseType.AQUEOUS);
inlet.getPhase(1).setType(PhaseType.AQUEOUS);

PipelineEvaporationConfig settings = new PipelineEvaporationConfig();
settings.setPipeLength(100.0);
settings.setPipeDiameter(0.10);
settings.setLiquidVelocity(0.30);
settings.setGasVelocity(0.50);
settings.setInitialBubbleDiameter(1.0e-3);
settings.setSlipModel(DispersedPhaseSlipModel.TERMINAL_VELOCITY);
settings.setPipeInclinationAngle(Math.toRadians(5.0));
settings.setCompletionFraction(1.0e-4); // 99.99% of injected gas dissolved

PipelineDissolutionResult result = new PipelineDissolutionStudy(inlet, settings).run();
if (result.isCompleteDissolution()) {
  System.out.println("Dissolution distance [m]: " + result.getCompleteDissolutionDistance());
} else {
  EvaporationProfilePoint outlet = result.getProfile().get(result.getProfile().size() - 1);
  System.out.println("Injected gas remaining at outlet: " + outlet.getRemainingTrackedPhaseFraction());
}
```

“Complete dissolution” means the configured tracked-gas mass threshold was reached. “Solubility” is the equilibrium
capacity predicted by the selected thermodynamic model. A finite gas phase can legitimately remain because the pipe is
too short, transfer is too slow, the liquid saturates, or local conditions favor reverse transfer.

Run at least coarse and fine numerical cases. A practical grid check is to halve
`maximumDonorFractionPerStep` and `maximumTemperatureChangePerStep`; the reported completion distance should be stable
for the intended engineering decision.

## Accuracy and validation status

| Layer | Implemented check | Main uncertainty or limitation |
|---|---|---|
| Component conservation | Equal-and-opposite inventory update; reported molar residual | Floating-point tolerance |
| Energy conservation | Separate phase enthalpy targets; reported overall residual | Accuracy of enthalpy and heat-transfer closure |
| Interface equilibrium | EOS fugacity equality | EOS and binary-interaction parameters; validate VLE first |
| Multicomponent diffusion | Krishna Maxwell-Stefan matrices with thermodynamic and finite-flux corrections | Binary diffusivity model and conditioning at trace composition |
| Droplet coefficients | Equation-level Ranz-Marshall, Kronig-Brink, and Abramzon-Sirignano tests | Isolated-sphere assumptions; no breakup or coalescence |
| Bubble coefficients | Ranz-Marshall/Kronig-Brink with explicit local relative speed | Spherical dilute-bubble assumption; no swarm or contamination correction |
| Slip closure | Schiller-Naumann force balance and separate axial/total relative velocity | Not a replacement for coupled momentum equations in dense or regime-transition flow |
| Film coefficients | Existing annular-flow correlations | Waves, entrainment, dry patches, and wetted perimeter |
| Axial solver | Adaptive donor/temperature limits and inventory positivity | No pressure-drop or full momentum-equation solution |

The correlation layer is traceable to Ranz and Marshall (1952) and Abramzon and Sirignano (1989). A useful published
multicomponent validation target is the forced-convection n-heptane/n-decane droplet experiment of Daïf et al. (1998):
21.3 mass-% n-heptane, initial radius 743 µm, initial droplet temperature 294 K, air at 345 K and 0.1 MPa, and 3.1 m/s
relative velocity. The paper reports radius and temperature histories. Those experimental points are not distributed
with NeqSim, so the present automated suite validates the equations, conservation, bounds, and short axial integration,
but it does **not** claim an absolute completion-distance accuracy against that experiment.

Recommended project validation is:

1. reproduce mixture VLE and transport properties independently;
2. compare a single-droplet time history with Daïf et al. or project-specific spray data;
3. measure or bound $d_{32}$, relative velocity, film coverage, and wall heat transfer;
4. compare outlet liquid carryover at two or more pipeline lengths; and
5. calibrate geometry parameters on one case and validate on another.

## Relation to other tools

- OpenFOAM Lagrangian spray models resolve droplet trajectories, breakup, and local CFD fields and include
  liquid-evaporation/boiling submodels. They are appropriate when spatial mixing and spray dynamics determine the answer.
  This NeqSim model is a faster one-dimensional engineering calculation with rigorous EOS equilibrium and
  multicomponent film coupling.
- A process simulator equilibrium flash answers whether liquid *can* remain at an equilibrium outlet state. It does not
  determine the finite distance required to reach that state.
- A full NeqSim two-fluid pipe solver is appropriate when pressure drop, flow-regime transitions, and hydrodynamic
  holdup are the primary coupled problem. Near complete phase depletion, use this dedicated inventory model for the
  completion distance and couple it piecewise to a hydraulic pressure/velocity profile.

## References

- W. E. Ranz and W. R. Marshall, *Evaporation from Drops*, Chemical Engineering Progress 48 (1952), parts I and II.
- L. Schiller and A. Naumann, *A Drag Coefficient Correlation*, VDI Zeitung 77 (1935), 318-320.
- B. Abramzon and W. A. Sirignano, *Droplet vaporization model for spray combustion calculations*, International
  Journal of Heat and Mass Transfer 32 (1989), 1605-1618,
  [doi:10.1016/0017-9310(89)90043-4](https://doi.org/10.1016/0017-9310(89)90043-4).
- A. Daïf et al., *Comparison of multicomponent fuel droplet vaporization experiments in forced convection with the
  Sirignano model*, Experimental Thermal and Fluid Science 18 (1998), 282-290,
  [doi:10.1016/S0894-1777(98)10035-3](https://doi.org/10.1016/S0894-1777(98)10035-3).
- E. Solbraa, *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing*, NTNU (2002),
  [hdl:11250/231326](https://hdl.handle.net/11250/231326).
- OpenFOAM Foundation, `LiquidEvaporationBoil` source documentation,
  [cpp.openfoam.org](https://cpp.openfoam.org/v4/LiquidEvaporationBoil_8C_source.html).
