---
title: "Finite-rate Liquid Evaporation in Gas Pipelines"
description: "Maxwell-Stefan mass transfer, heat transfer, droplets, films, completion distance, and validation limits."
---

# Finite-rate Liquid Evaporation in Gas Pipelines

## What this model answers

`PipelineEvaporationStudy` estimates the axial distance needed for an injected hydrocarbon liquid to fall below a
specified remaining mass fraction. It supports two prescribed liquid geometries:

- spherical droplets represented by an evolving Sauter mean diameter, $d_{32}$; and
- a wall film represented by an evolving thickness and wetted-perimeter fraction.

The completion criterion is the remaining fraction of the *initially injected liquid inventory*. Liquid holdup is not
used as an evaporation measure. Holdup can change because velocities, densities, or the flow regime change even if no
component evaporates.

## Model formulation

At each accepted axial position the model creates a local NeqSim droplet or annular flow node and solves the
`KrishnaStandartFilmModel` boundary. This gives:

1. EOS fugacity equality at the gas-liquid interface;
2. binary diffusivities from NeqSim physical-property models;
3. coupled Maxwell-Stefan resistance matrices in gas and liquid;
4. thermodynamic-factor and finite-flux corrections; and
5. simultaneous interphase heat and component fluxes.

Droplet transfer coefficients use Ranz-Marshall externally and Kronig-Brink internally. The optional
Abramzon-Sirignano correction uses the aggregate injected-vapor *mass fraction* to calculate the Spalding mass-transfer
number. Film calculations use the annular-flow transfer correlations.

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

The geometry follows total liquid-phase volume, so gas absorbed into the liquid can grow a droplet or film even while
the tracked injected material evaporates. Completion still uses only the mass provenance of the initial liquid phase.

These relations do not model breakup, coalescence, entrainment, deposition, or dry-patch formation. Supply a realistic
inlet $d_{32}$, film thickness, and wetted fraction, and perform sensitivity cases.

### Conservative axial integration

The component update over a step is

\[
\Delta\dot n_i=N_i A_i,
\]

with equal and opposite changes in the two phases. Donor inventories are bounded so component flows cannot become
negative. Separate gas and liquid enthalpy targets use the boundary heat fluxes; optional wall heat is added to the gas
for droplets and to the liquid for a wall film. The step is halved until both the maximum donor depletion and estimated
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
| Film coefficients | Existing annular-flow correlations | Waves, entrainment, dry patches, and wetted perimeter |
| Axial solver | Adaptive donor/temperature limits and inventory positivity | Prescribed velocities; no momentum or pressure-drop solution |

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
- A full NeqSim two-fluid pipe solver is appropriate when pressure drop and hydrodynamic holdup are the primary coupled
  problem. Near complete phase depletion, use this dedicated inventory model for the completion distance and couple it
  piecewise to a hydraulic pressure/velocity profile.

## References

- W. E. Ranz and W. R. Marshall, *Evaporation from Drops*, Chemical Engineering Progress 48 (1952), parts I and II.
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
