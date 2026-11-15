package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Conservative one-dimensional EOS-backed gas-pipe decompression inventory.
 *
 * <p>
 * A first-order local Lax-Friedrichs finite-volume scheme advances mass, momentum and total energy. Every conservative
 * cell state is recovered with the caller-selected NeqSim EOS by a volume/internal-energy flash, so pressure,
 * temperature, density and acoustic speed evolve with the decompression. The pipe is rigid, initially uniform, and
 * connected at its downstream end to a constant-pressure receiver. The upstream end is either closed or coupled to an
 * optional rigid, adiabatic, well-mixed gas vessel. The default pipe wall is adiabatic; opt-in boundaries apply either
 * a constant overall heat-transfer coefficient directly to a fixed external temperature or a finite-capacitance wall
 * with separate gas-side and environment-side coefficients. A cylindrical material wall can retain either one lumped
 * temperature or conservative inner/outer radial temperature nodes. Specified Darcy friction acts only on momentum. The
 * exact vessel/pipe interface flux, wall-energy transaction and outlet mass/total-enthalpy flux are committed
 * atomically, while the outlet flux is exported to the source-term frame.
 * </p>
 *
 * <p>
 * The model requires one equilibrium gas phase with fixed composition. Phase appearance, reactions, forced phases,
 * solids and hydrate calculations fail closed. All thermal coefficients and boundary temperatures are prescribed. Wall
 * capacity is either prescribed directly or derived from explicit cylindrical geometry, density and specific heat. The
 * one-node material factory uses a fail-closed lumped-Biot guard; the two-node factory resolves a low-order radial
 * gradient using cylindrical resistance. Predictive fire/ambient correlations, higher-order radial conduction, pipe
 * elasticity, two-sided-rupture coupling, non-equilibrium slip, entrainment and solid-bearing transport are outside
 * this model. The optional upstream vessel is a lumped boundary, not a vessel nozzle, internal-wave or phase-separation
 * model. Numerical validation is not facility qualification.
 * </p>
 */
public final class RealGasPipeDecompression extends ProcessEquipmentBaseClass implements CoupledReleaseSource {
  private static final long serialVersionUID = 1L;
  private static final int MAX_SUBSTEPS = 100000;
  private static final double CLOSURE_TOLERANCE = 3.0e-10;
  private static final double DENSITY_TOLERANCE = 2.0e-3;

  private final SystemInterface compositionReference;
  private final double lengthM;
  private final double diameterM;
  private final double areaM2;
  private final double cellVolumeM3;
  private final double upstreamVesselVolumeM3;
  private final double dischargeCoefficient;
  private final double backPressurePa;
  private final double darcyFrictionFactor;
  private final double heatTransferCoefficientWPerM2K;
  private final double wallToEnvironmentCoefficientWPerM2K;
  private final double wallArealHeatCapacityJPerM2K;
  private final double outerWallArealHeatCapacityJPerM2K;
  private final double radialWallConductanceWPerM2K;
  private final double initialWallTemperatureK;
  private final double externalTemperatureK;
  private final double cellExternalAreaM2;
  private final WallThermalBoundary wallThermalBoundary;
  private final double cfl;
  private final double[] massFractions;
  private final TransientBoundaryModel releaseModel = new TransientBoundaryModel();
  private double[] density;
  private double[] momentum;
  private double[] energy;
  private double[] wallTemperatureK;
  private double[] outerWallTemperatureK;
  private final double initialMassKg;
  private final double initialEnergyJ;
  private double upstreamVesselMassKg;
  private double upstreamVesselEnergyJ;
  private double releasedMassKg;
  private double releasedEnergyJ;
  private double cumulativeHeatTransferJ;
  private double cumulativeGasWallHeatTransferJ;
  private double lastMassFlowKgS;
  private double lastEnergyFlowW;
  private double lastHeatTransferRateW;
  private double lastGasWallHeatTransferRateW;
  private int lastSubsteps;
  private boolean releaseEnabled = true;
  private UUID lastTransientId;
  private double lastTransientDurationS;
  private ReleaseFlowResult currentResult;

  /**
   * Creates a one-sided EOS-backed transient pipe inventory.
   *
   * @param name unique process-equipment name
   * @param initialGas initial uniform equilibrium gas and selected EOS
   * @param lengthM pipe length in m
   * @param diameterM internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0,1]
   * @param backPressurePa constant absolute receiver pressure in Pa
   * @param darcyFrictionFactor specified Darcy friction factor in [0,1]
   * @param cells finite-volume cell count, at least 8
   * @param cfl Courant number in (0,0.7]
   */
  public RealGasPipeDecompression(String name, SystemInterface initialGas, double lengthM, double diameterM,
      double dischargeCoefficient, double backPressurePa, double darcyFrictionFactor, int cells, double cfl) {
    this(name, initialGas, lengthM, diameterM, dischargeCoefficient, backPressurePa, darcyFrictionFactor, 0.0, cells,
        cfl);
  }

  /**
   * Creates a one-sided EOS-backed transient pipe with a finite upstream gas vessel.
   *
   * <p>
   * The vessel is rigid, adiabatic and perfectly mixed, starts at the same thermodynamic state and composition as the
   * pipe, and exchanges the exact conservative interface mass and total-energy flux with the first pipe cell. A
   * positive volume is required; use the legacy constructor for a closed upstream end.
   * </p>
   *
   * @param name unique process-equipment name
   * @param initialGas initial uniform equilibrium gas and selected EOS
   * @param lengthM pipe length in m
   * @param diameterM internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0,1]
   * @param backPressurePa constant absolute receiver pressure in Pa
   * @param darcyFrictionFactor specified Darcy friction factor in [0,1]
   * @param upstreamVesselVolumeM3 rigid upstream vessel volume in m3; zero retains the closed upstream end
   * @param cells finite-volume cell count, at least 8
   * @param cfl Courant number in (0,0.7]
   */
  public RealGasPipeDecompression(String name, SystemInterface initialGas, double lengthM, double diameterM,
      double dischargeCoefficient, double backPressurePa, double darcyFrictionFactor, double upstreamVesselVolumeM3,
      int cells, double cfl) {
    this(name, initialGas, lengthM, diameterM, dischargeCoefficient, backPressurePa, darcyFrictionFactor,
        upstreamVesselVolumeM3, 0.0, 0.0, cells, cfl);
  }

  /**
   * Creates a one-sided EOS-backed transient pipe with optional vessel and distributed wall heat transfer.
   *
   * <p>
   * Positive heat is transferred into the pipe according to
   * {@code U * pi * diameter * cellLength * (externalTemperature - cellTemperature)}. The coefficient and fixed
   * external temperature are explicit sensitivity inputs, not a wall, soil, seawater, fire or ambient correlation. Pass
   * zero for both thermal inputs to retain the adiabatic model. The optional upstream vessel remains adiabatic.
   * </p>
   *
   * @param name unique process-equipment name
   * @param initialGas initial uniform equilibrium gas and selected EOS
   * @param lengthM pipe length in m
   * @param diameterM internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0,1]
   * @param backPressurePa constant absolute receiver pressure in Pa
   * @param darcyFrictionFactor specified Darcy friction factor in [0,1]
   * @param upstreamVesselVolumeM3 rigid upstream vessel volume in m3; zero retains the closed upstream end
   * @param heatTransferCoefficientWPerM2K prescribed overall pipe-to-environment coefficient in W/(m2 K); zero is
   * adiabatic
   * @param externalTemperatureK fixed external-boundary temperature in K; zero is required for the adiabatic mode
   * @param cells finite-volume cell count, at least 8
   * @param cfl Courant number in (0,0.7]
   */
  public RealGasPipeDecompression(String name, SystemInterface initialGas, double lengthM, double diameterM,
      double dischargeCoefficient, double backPressurePa, double darcyFrictionFactor, double upstreamVesselVolumeM3,
      double heatTransferCoefficientWPerM2K, double externalTemperatureK, int cells, double cfl) {
    this(name, initialGas, lengthM, diameterM, dischargeCoefficient, backPressurePa, darcyFrictionFactor,
        upstreamVesselVolumeM3,
        WallThermalBoundary.fixedExternalTemperature(heatTransferCoefficientWPerM2K, externalTemperatureK), cells, cfl);
  }

  /**
   * Creates a one-sided EOS-backed transient pipe with an explicit thermal boundary.
   *
   * <p>
   * Use {@link WallThermalBoundary#capacitive(double, double, double, double, double)} to retain one wall-temperature
   * state per finite-volume cell. The wall exchanges equal-and-opposite heat with the gas and receives heat from the
   * fixed external boundary. Gas, wall, upstream-vessel and released energy are advanced in one atomic transaction.
   * </p>
   *
   * @param name unique process-equipment name
   * @param initialGas initial uniform equilibrium gas and selected EOS
   * @param lengthM pipe length in m
   * @param diameterM internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0,1]
   * @param backPressurePa constant absolute receiver pressure in Pa
   * @param darcyFrictionFactor specified Darcy friction factor in [0,1]
   * @param upstreamVesselVolumeM3 rigid upstream vessel volume in m3; zero retains the closed upstream end
   * @param thermalBoundary immutable prescribed wall boundary
   * @param cells finite-volume cell count, at least 8
   * @param cfl Courant number in (0,0.7]
   */
  public RealGasPipeDecompression(String name, SystemInterface initialGas, double lengthM, double diameterM,
      double dischargeCoefficient, double backPressurePa, double darcyFrictionFactor, double upstreamVesselVolumeM3,
      WallThermalBoundary thermalBoundary, int cells, double cfl) {
    super(name);
    Objects.requireNonNull(initialGas, "initialGas");
    Objects.requireNonNull(thermalBoundary, "thermalBoundary");
    this.lengthM = ReleaseFlowRequest.positive(lengthM, "lengthM");
    this.diameterM = ReleaseFlowRequest.positive(diameterM, "diameterM");
    this.dischargeCoefficient = ReleaseFlowRequest.positive(dischargeCoefficient, "dischargeCoefficient");
    this.backPressurePa = ReleaseFlowRequest.positive(backPressurePa, "backPressurePa");
    this.darcyFrictionFactor = ReleaseFlowRequest.nonnegative(darcyFrictionFactor, "darcyFrictionFactor");
    this.heatTransferCoefficientWPerM2K = thermalBoundary.gasToWallCoefficientWPerM2K;
    this.wallToEnvironmentCoefficientWPerM2K = thermalBoundary.wallToEnvironmentCoefficientWPerM2K;
    this.wallArealHeatCapacityJPerM2K = thermalBoundary.wallArealHeatCapacityJPerM2K;
    this.outerWallArealHeatCapacityJPerM2K = thermalBoundary.outerWallArealHeatCapacityJPerM2K;
    this.radialWallConductanceWPerM2K = thermalBoundary.radialWallConductanceWPerM2K;
    this.initialWallTemperatureK = thermalBoundary.initialWallTemperatureK;
    this.wallThermalBoundary = thermalBoundary;
    if (!Double.isFinite(upstreamVesselVolumeM3) || upstreamVesselVolumeM3 < 0.0 || dischargeCoefficient > 1.0
        || darcyFrictionFactor > 1.0 || cells < 8 || cells > 400 || !Double.isFinite(cfl) || cfl <= 0.0 || cfl > 0.7) {
      throw new IllegalArgumentException("Invalid decompression discretization or geometry");
    }
    this.upstreamVesselVolumeM3 = upstreamVesselVolumeM3;
    this.externalTemperatureK = thermalBoundary.externalTemperatureK;
    if (thermalBoundary.hasMaterialGeometry()
        && Math.abs(thermalBoundary.innerDiameterM - diameterM) > 1.0e-12 * diameterM) {
      throw new IllegalArgumentException("Wall inner diameter must match the pipe internal diameter");
    }
    this.cfl = cfl;
    compositionReference = initialGas.clone();
    new ThermodynamicOperations(compositionReference).TPflash();
    compositionReference.init(3);
    requireGasOnly(compositionReference);
    double initialPressurePa = ReleaseFlowRequest.positive(compositionReference.getPressure("Pa"), "initial pressure");
    if (initialPressurePa < backPressurePa) {
      throw new IllegalArgumentException("Initial pressure must not be below receiver pressure");
    }
    areaM2 = Math.PI * diameterM * diameterM / 4.0;
    cellVolumeM3 = areaM2 * lengthM / cells;
    cellExternalAreaM2 = Math.PI * thermalBoundary.heatTransferAreaDiameterM(diameterM) * lengthM / cells;
    density = new double[cells];
    momentum = new double[cells];
    energy = new double[cells];
    wallTemperatureK = hasCapacitiveWall() ? new double[cells] : new double[0];
    outerWallTemperatureK = hasRadialWallConduction() ? new double[cells] : new double[0];
    double initialDensity = density(compositionReference);
    double initialInternalEnergy = finite(compositionReference.getInternalEnergy("J/kg"), "initial internal energy");
    Arrays.fill(density, initialDensity);
    Arrays.fill(energy, initialDensity * initialInternalEnergy);
    Arrays.fill(wallTemperatureK, initialWallTemperatureK);
    Arrays.fill(outerWallTemperatureK, initialWallTemperatureK);
    upstreamVesselMassKg = initialDensity * upstreamVesselVolumeM3;
    upstreamVesselEnergyJ = initialDensity * initialInternalEnergy * upstreamVesselVolumeM3;
    massFractions = componentMassFractions(compositionReference);
    initialMassKg = totalMass(density) + upstreamVesselMassKg;
    initialEnergyJ = totalEnergy(energy) + upstreamVesselEnergyJ + wallEnergy(wallTemperatureK, outerWallTemperatureK);
    CellState[] states = cellStates(density, momentum, energy);
    currentResult = result(states, upstreamState(upstreamVesselMassKg, upstreamVesselEnergyJ, states[0]), 0.0);
    setCalculateSteadyState(false);
  }

  /** Commits a stationary source snapshot without changing line-pack state. */
  @Override
  public synchronized void run(UUID id) {
    Objects.requireNonNull(id, "id");
    CellState[] states = cellStates(density, momentum, energy);
    Flux outlet = boundaryFlux(states[states.length - 1]);
    lastMassFlowKgS = outlet.mass * areaM2;
    lastEnergyFlowW = outlet.energy * areaM2;
    lastHeatTransferRateW = nextHeatTransferRate(states);
    lastGasWallHeatTransferRateW = nextGasWallHeatTransferRate(states, wallTemperatureK);
    currentResult = result(states, upstreamState(upstreamVesselMassKg, upstreamVesselEnergyJ, states[0]), outlet.mass);
    setCalculationIdentifier(id);
  }

  /**
   * Advances the conservative state atomically with internally selected CFL substeps.
   *
   * @param dt caller timestep in s
   * @param id successful calculation identity
   */
  @Override
  public synchronized void runTransient(double dt, UUID id) {
    ReleaseFlowRequest.positive(dt, "dt");
    Objects.requireNonNull(id, "id");
    if (getCalculateSteadyState()) {
      throw new IllegalArgumentException("RealGasPipeDecompression requires dynamic mode");
    }
    if (id.equals(lastTransientId)) {
      if (dt != lastTransientDurationS) {
        throw new IllegalArgumentException("Repeated transient identity has a different duration");
      }
      return;
    }
    double[] candidateDensity = density.clone();
    double[] candidateMomentum = momentum.clone();
    double[] candidateEnergy = energy.clone();
    double[] candidateWallTemperature = wallTemperatureK.clone();
    double[] candidateOuterWallTemperature = outerWallTemperatureK.clone();
    double candidateReleasedMass = releasedMassKg;
    double candidateReleasedEnergy = releasedEnergyJ;
    double candidateHeatTransfer = cumulativeHeatTransferJ;
    double candidateGasWallHeatTransfer = cumulativeGasWallHeatTransferJ;
    double candidateUpstreamMass = upstreamVesselMassKg;
    double candidateUpstreamEnergy = upstreamVesselEnergyJ;
    double elapsed = 0.0;
    int substeps = 0;
    CellState[] states = cellStates(candidateDensity, candidateMomentum, candidateEnergy);
    CellState upstream = upstreamState(candidateUpstreamMass, candidateUpstreamEnergy, states[0]);
    Flux outlet = boundaryFlux(states[states.length - 1]);
    while (elapsed < dt) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException("REAL_GAS_PIPE_DECOMPRESSION_INTERRUPTED");
      }
      if (++substeps > MAX_SUBSTEPS) {
        throw new IllegalStateException("REAL_GAS_PIPE_DECOMPRESSION_SUBSTEP_LIMIT");
      }
      double step = Math.min(dt - elapsed, stableStep(states, upstream, candidateUpstreamMass));
      if (!(step > 0.0) || elapsed + step <= elapsed) {
        throw new IllegalStateException("REAL_GAS_PIPE_DECOMPRESSION_TIMESTEP_UNREPRESENTABLE");
      }
      Step next = advance(candidateDensity, candidateMomentum, candidateEnergy, candidateWallTemperature,
          candidateOuterWallTemperature, states, upstream, candidateUpstreamMass, candidateUpstreamEnergy, step);
      candidateDensity = next.density;
      candidateMomentum = next.momentum;
      candidateEnergy = next.energy;
      candidateWallTemperature = next.wallTemperatureK;
      candidateOuterWallTemperature = next.outerWallTemperatureK;
      candidateUpstreamMass = next.upstreamMass;
      candidateUpstreamEnergy = next.upstreamEnergy;
      candidateReleasedMass += next.outlet.mass * areaM2 * step;
      candidateReleasedEnergy += next.outlet.energy * areaM2 * step;
      candidateHeatTransfer += next.heatTransferJ;
      candidateGasWallHeatTransfer += next.gasWallHeatTransferJ;
      outlet = next.outlet;
      states = next.states;
      upstream = next.upstream;
      elapsed += step;
    }
    close(totalMass(candidateDensity) + candidateUpstreamMass + candidateReleasedMass, initialMassKg, initialMassKg,
        "REAL_GAS_PIPE_TOTAL_MASS_CLOSURE_FAILED");
    close(
        totalEnergy(candidateEnergy) + wallEnergy(candidateWallTemperature, candidateOuterWallTemperature)
            + candidateUpstreamEnergy + candidateReleasedEnergy,
        initialEnergyJ + candidateHeatTransfer, initialEnergyJ + candidateHeatTransfer,
        "REAL_GAS_PIPE_TOTAL_ENERGY_CLOSURE_FAILED");
    ReleaseFlowResult candidateResult = result(states, upstream, outlet.mass);
    density = candidateDensity;
    momentum = candidateMomentum;
    energy = candidateEnergy;
    wallTemperatureK = candidateWallTemperature;
    outerWallTemperatureK = candidateOuterWallTemperature;
    upstreamVesselMassKg = candidateUpstreamMass;
    upstreamVesselEnergyJ = candidateUpstreamEnergy;
    releasedMassKg = candidateReleasedMass;
    releasedEnergyJ = candidateReleasedEnergy;
    cumulativeHeatTransferJ = candidateHeatTransfer;
    cumulativeGasWallHeatTransferJ = candidateGasWallHeatTransfer;
    lastMassFlowKgS = outlet.mass * areaM2;
    lastEnergyFlowW = outlet.energy * areaM2;
    lastHeatTransferRateW = substeps == 0 ? 0.0 : nextHeatTransferRate(states);
    lastGasWallHeatTransferRateW = substeps == 0 ? 0.0 : nextGasWallHeatTransferRate(states, wallTemperatureK);
    lastSubsteps = substeps;
    lastTransientId = id;
    lastTransientDurationS = dt;
    currentResult = candidateResult;
    setTime(getTime() + dt);
    setCalculationIdentifier(id);
  }

  private Step advance(double[] rho, double[] mom, double[] totalEnergy, double[] wallTemperatures,
      double[] outerWallTemperatures, CellState[] states, CellState upstream, double upstreamMass,
      double upstreamEnergy, double dt) {
    int cells = rho.length;
    Flux[] faces = new Flux[cells + 1];
    faces[0] = hasUpstreamVessel() ? flux(upstream, states[0]) : flux(states[0].reflected(), states[0]);
    for (int face = 1; face < cells; face++) {
      faces[face] = flux(states[face - 1], states[face]);
    }
    faces[cells] = boundaryFlux(states[cells - 1]);
    double scale = dt / (lengthM / cells);
    double[] nextRho = new double[cells];
    double[] nextMom = new double[cells];
    double[] nextEnergy = new double[cells];
    double[] nextWallTemperature = wallTemperatures.clone();
    double[] nextOuterWallTemperature = outerWallTemperatures.clone();
    double environmentHeatTransferRate = 0.0;
    double gasWallHeatTransferRate = 0.0;
    for (int cell = 0; cell < cells; cell++) {
      nextRho[cell] = rho[cell] - scale * (faces[cell + 1].mass - faces[cell].mass);
      nextMom[cell] = mom[cell] - scale * (faces[cell + 1].momentum - faces[cell].momentum);
      nextEnergy[cell] = totalEnergy[cell] - scale * (faces[cell + 1].energy - faces[cell].energy);
      double gasHeatRate = gasHeatTransferRate(states[cell], wallTemperatures, cell);
      double environmentHeatRate = environmentHeatTransferRate(states[cell], wallTemperatures, outerWallTemperatures,
          cell);
      nextEnergy[cell] += dt * gasHeatRate / cellVolumeM3;
      if (hasRadialWallConduction()) {
        double radialHeatRate = radialWallConductanceWPerM2K * cellExternalAreaM2
            * (outerWallTemperatures[cell] - wallTemperatures[cell]);
        nextWallTemperature[cell] += dt * (radialHeatRate - gasHeatRate)
            / (wallArealHeatCapacityJPerM2K * cellExternalAreaM2);
        nextOuterWallTemperature[cell] += dt * (environmentHeatRate - radialHeatRate)
            / (outerWallArealHeatCapacityJPerM2K * cellExternalAreaM2);
        ReleaseFlowRequest.positive(nextWallTemperature[cell], "pipe inner wall temperature");
        ReleaseFlowRequest.positive(nextOuterWallTemperature[cell], "pipe outer wall temperature");
      } else if (hasCapacitiveWall()) {
        nextWallTemperature[cell] += dt * (environmentHeatRate - gasHeatRate)
            / (wallArealHeatCapacityJPerM2K * cellExternalAreaM2);
        ReleaseFlowRequest.positive(nextWallTemperature[cell], "pipe wall temperature");
      }
      environmentHeatTransferRate += environmentHeatRate;
      gasWallHeatTransferRate += gasHeatRate;
      double velocity = nextMom[cell] / nextRho[cell];
      nextMom[cell] -= dt * darcyFrictionFactor * nextRho[cell] * velocity * Math.abs(velocity) / (2.0 * diameterM);
    }
    CellState[] nextStates = cellStates(nextRho, nextMom, nextEnergy);
    double nextUpstreamMass = upstreamMass;
    double nextUpstreamEnergy = upstreamEnergy;
    CellState nextUpstream = nextStates[0];
    if (hasUpstreamVessel()) {
      nextUpstreamMass -= faces[0].mass * areaM2 * dt;
      nextUpstreamEnergy -= faces[0].energy * areaM2 * dt;
      nextUpstream = upstreamState(nextUpstreamMass, nextUpstreamEnergy, nextStates[0]);
    }
    return new Step(nextRho, nextMom, nextEnergy, nextWallTemperature, nextOuterWallTemperature, nextStates,
        nextUpstream, nextUpstreamMass, nextUpstreamEnergy, faces[cells], environmentHeatTransferRate * dt,
        gasWallHeatTransferRate * dt);
  }

  private double stableStep(CellState[] states, CellState upstream, double upstreamMass) {
    double maximumSignal = 0.0;
    for (CellState state : states) {
      maximumSignal = Math.max(maximumSignal, Math.abs(state.velocity) + state.soundSpeed);
    }
    if (hasUpstreamVessel()) {
      maximumSignal = Math.max(maximumSignal, upstream.soundSpeed);
    }
    double step = cfl * lengthM / states.length / ReleaseFlowRequest.positive(maximumSignal, "maximum signal speed");
    if (hasUpstreamVessel()) {
      Flux inlet = flux(upstream, states[0]);
      if (inlet.mass > 0.0) {
        step = Math.min(step, 0.2 * upstreamMass / (inlet.mass * areaM2));
      }
    }
    if (hasDistributedHeatTransfer()) {
      for (int cell = 0; cell < states.length; cell++) {
        double gasCapacityJPerK = states[cell].density * cellVolumeM3
            * ReleaseFlowRequest.positive(states[cell].fluid.getCv("J/kgK"), "EOS constant-volume heat capacity");
        step = Math.min(step, 0.1 * gasCapacityJPerK / (heatTransferCoefficientWPerM2K * cellExternalAreaM2));
        if (hasCapacitiveWall()) {
          double wallCapacityJPerK = wallArealHeatCapacityJPerM2K * cellExternalAreaM2;
          double innerNodeConductance = heatTransferCoefficientWPerM2K
              + (hasRadialWallConduction() ? radialWallConductanceWPerM2K : wallToEnvironmentCoefficientWPerM2K);
          step = Math.min(step, 0.1 * wallCapacityJPerK / (innerNodeConductance * cellExternalAreaM2));
          if (hasRadialWallConduction()) {
            double outerWallCapacityJPerK = outerWallArealHeatCapacityJPerM2K * cellExternalAreaM2;
            step = Math.min(step, 0.1 * outerWallCapacityJPerK
                / ((radialWallConductanceWPerM2K + wallToEnvironmentCoefficientWPerM2K) * cellExternalAreaM2));
          }
        }
      }
    }
    return step;
  }

  private double gasHeatTransferRate(CellState state, double[] wallTemperatures, int cell) {
    if (!hasDistributedHeatTransfer()) {
      return 0.0;
    }
    double boundaryTemperatureK = hasCapacitiveWall() ? wallTemperatures[cell] : externalTemperatureK;
    return heatTransferCoefficientWPerM2K * cellExternalAreaM2 * (boundaryTemperatureK - state.temperatureK);
  }

  private double environmentHeatTransferRate(CellState state, double[] wallTemperatures, double[] outerWallTemperatures,
      int cell) {
    if (!hasDistributedHeatTransfer()) {
      return 0.0;
    }
    if (!hasCapacitiveWall()) {
      return gasHeatTransferRate(state, wallTemperatures, cell);
    }
    double boundaryTemperatureK = hasRadialWallConduction() ? outerWallTemperatures[cell] : wallTemperatures[cell];
    return wallToEnvironmentCoefficientWPerM2K * cellExternalAreaM2 * (externalTemperatureK - boundaryTemperatureK);
  }

  private double nextHeatTransferRate(CellState[] states) {
    double sum = 0.0;
    for (int cell = 0; cell < states.length; cell++) {
      sum += hasCapacitiveWall()
          ? wallToEnvironmentCoefficientWPerM2K * cellExternalAreaM2
              * (externalTemperatureK
                  - (hasRadialWallConduction() ? outerWallTemperatureK[cell] : wallTemperatureK[cell]))
          : gasHeatTransferRate(states[cell], wallTemperatureK, cell);
    }
    return sum;
  }

  private double nextGasWallHeatTransferRate(CellState[] states, double[] wallTemperatures) {
    double sum = 0.0;
    for (int cell = 0; cell < states.length; cell++) {
      sum += gasHeatTransferRate(states[cell], wallTemperatures, cell);
    }
    return sum;
  }

  private boolean hasDistributedHeatTransfer() {
    return heatTransferCoefficientWPerM2K > 0.0;
  }

  private boolean hasCapacitiveWall() {
    return wallArealHeatCapacityJPerM2K > 0.0;
  }

  private boolean hasRadialWallConduction() {
    return outerWallArealHeatCapacityJPerM2K > 0.0;
  }

  private boolean hasUpstreamVessel() {
    return upstreamVesselVolumeM3 > 0.0;
  }

  private CellState upstreamState(double massKg, double energyJ, CellState closedEndState) {
    if (!hasUpstreamVessel()) {
      return closedEndState;
    }
    if (!Double.isFinite(massKg) || massKg <= 0.0 || !Double.isFinite(energyJ)) {
      throw new IllegalStateException("REAL_GAS_UPSTREAM_VESSEL_NONPHYSICAL_STATE");
    }
    return cellState(massKg / upstreamVesselVolumeM3, 0.0, energyJ / upstreamVesselVolumeM3);
  }

  private Flux boundaryFlux(CellState inside) {
    if (!releaseEnabled || inside.pressurePa <= backPressurePa * (1.0 + 1.0e-10)) {
      return flux(inside, inside.reflected());
    }
    SystemInterface receiverFluid = compositionReference.clone();
    receiverFluid.setTemperature(inside.temperatureK);
    receiverFluid.setPressure(backPressurePa, "Pa");
    new ThermodynamicOperations(receiverFluid).TPflash();
    receiverFluid.init(3);
    requireGasOnly(receiverFluid);
    double receiverDensity = density(receiverFluid);
    double receiverEnergy = receiverDensity * finite(receiverFluid.getInternalEnergy("J/kg"), "receiver energy");
    CellState receiver = cellState(receiverDensity, 0.0, receiverEnergy);
    Flux candidate = flux(inside, receiver);
    if (candidate.mass <= 0.0 || !Double.isFinite(candidate.energy)) {
      return new Flux(0.0, inside.pressurePa, 0.0);
    }
    return new Flux(candidate.mass * dischargeCoefficient, candidate.momentum * dischargeCoefficient,
        candidate.energy * dischargeCoefficient);
  }

  private static Flux flux(CellState left, CellState right) {
    double waveSpeed = Math.max(Math.abs(left.velocity) + left.soundSpeed, Math.abs(right.velocity) + right.soundSpeed);
    double leftEnergyFlux = (left.energy + left.pressurePa) * left.velocity;
    double rightEnergyFlux = (right.energy + right.pressurePa) * right.velocity;
    return new Flux(0.5 * (left.momentum + right.momentum - waveSpeed * (right.density - left.density)),
        0.5 * (left.momentum * left.velocity + left.pressurePa + right.momentum * right.velocity + right.pressurePa
            - waveSpeed * (right.momentum - left.momentum)),
        0.5 * (leftEnergyFlux + rightEnergyFlux - waveSpeed * (right.energy - left.energy)));
  }

  private CellState[] cellStates(double[] rho, double[] mom, double[] totalEnergy) {
    CellState[] states = new CellState[rho.length];
    for (int cell = 0; cell < states.length; cell++) {
      states[cell] = cellState(rho[cell], mom[cell], totalEnergy[cell]);
    }
    return states;
  }

  private CellState cellState(double rho, double mom, double totalEnergy) {
    if (!Double.isFinite(rho) || rho <= 0.0 || !Double.isFinite(mom) || !Double.isFinite(totalEnergy)) {
      throw new IllegalStateException("REAL_GAS_PIPE_NONPHYSICAL_CONSERVATIVE_STATE");
    }
    double velocity = mom / rho;
    double internalEnergyDensity = totalEnergy - 0.5 * mom * velocity;
    if (!Double.isFinite(internalEnergyDensity)) {
      throw new IllegalStateException("REAL_GAS_PIPE_NONFINITE_INTERNAL_ENERGY");
    }
    SystemInterface fluid = compositionReference.clone();
    fluid.setTotalNumberOfMoles(rho / fluid.getMolarMass());
    new ThermodynamicOperations(fluid).VUflash(1.0, internalEnergyDensity, "m3", "J");
    fluid.init(3);
    requireGasOnly(fluid);
    double recoveredDensity = density(fluid);
    if (Math.abs(recoveredDensity - rho) > DENSITY_TOLERANCE * Math.max(1.0, rho)) {
      throw new IllegalStateException("REAL_GAS_PIPE_VU_DENSITY_CLOSURE_FAILED");
    }
    double pressurePa = ReleaseFlowRequest.positive(fluid.getPressure("Pa"), "EOS pressure");
    double temperatureK = ReleaseFlowRequest.positive(fluid.getTemperature(), "EOS temperature");
    double soundSpeed = ReleaseFlowRequest.positive(fluid.getPhase(0).getSoundSpeed(), "EOS sound speed");
    return new CellState(rho, mom, totalEnergy, velocity, pressurePa, temperatureK, soundSpeed, fluid);
  }

  private ReleaseFlowResult result(CellState[] states, CellState upstream, double outletMassFlux) {
    for (int cell = 0; cell < states.length; cell++) {
      ReleaseSolidRiskAssessment assessment = ReleaseSolidRiskAssessment.assess(states[cell].fluid);
      if (assessment.getStatus() == ReleaseSolidRiskAssessment.Status.UNRESOLVED) {
        throw new IllegalStateException(
            "SOLID_RISK_ASSESSMENT_FAILED in pipe cell " + cell + ": " + assessment.getMessage());
      }
      if (!assessment.isClear()) {
        throw new UnsupportedOperationException(
            assessment.getStatus().name() + " in pipe cell " + cell + ": " + assessment.getMessage());
      }
    }
    if (hasUpstreamVessel()) {
      ReleaseSolidRiskAssessment vesselAssessment = ReleaseSolidRiskAssessment.assess(upstream.fluid);
      if (vesselAssessment.getStatus() == ReleaseSolidRiskAssessment.Status.UNRESOLVED) {
        throw new IllegalStateException(
            "SOLID_RISK_ASSESSMENT_FAILED in upstream vessel: " + vesselAssessment.getMessage());
      }
      if (!vesselAssessment.isClear()) {
        throw new UnsupportedOperationException(
            vesselAssessment.getStatus().name() + " in upstream vessel: " + vesselAssessment.getMessage());
      }
    }
    CellState inlet = hasUpstreamVessel() ? upstream : states[0];
    CellState exit = states[states.length - 1];
    double positiveFlux = releaseEnabled ? Math.max(0.0, outletMassFlux) : 0.0;
    double boundaryVelocity = positiveFlux / exit.density;
    Map<Station, ReleaseState> stations = new EnumMap<Station, ReleaseState>(Station.class);
    stations.put(Station.UPSTREAM_STAGNATION, ReleaseState.fromFluid(inlet.fluid, inlet.velocity));
    ReleaseState opening = ReleaseState.fromFluid(exit.fluid, boundaryVelocity);
    stations.put(Station.THROAT_CRITICAL, opening);
    stations.put(Station.ORIFICE_EXIT, opening);
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    String thermalBoundary = hasRadialWallConduction()
        ? "distributed material-derived cylindrical wall with conservative inner/outer radial nodes"
        : wallThermalBoundary.hasMaterialGeometry()
            ? "distributed material-derived cylindrical lumped wall within a fail-closed Biot limit"
            : hasCapacitiveWall()
                ? "distributed finite-capacitance wall with prescribed gas-side and environment-side coefficients"
                : hasDistributedHeatTransfer() ? "distributed constant-U heat exchange to a fixed external temperature"
                    : "adiabatic pipe wall";
    diagnostics.add(new Diagnostic("REAL_GAS_TRANSIENT_PIPE_ASSUMPTIONS",
        hasUpstreamVessel()
            ? "One-dimensional rigid equilibrium gas; " + thermalBoundary
                + "; lumped rigid adiabatic upstream vessel and constant-pressure receiver"
            : "One-dimensional rigid equilibrium gas; " + thermalBoundary
                + "; closed upstream end and constant-pressure receiver"));
    diagnostics.add(new Diagnostic("EOS_FINITE_VOLUME_LINE_PACKING", "EOS=" + compositionReference.getModelName()
        + ", cells=" + states.length + ", CFL=" + cfl + ", Darcy friction=" + darcyFrictionFactor));
    diagnostics.add(
        new Diagnostic("SOLID_RISK_ASSESSED", "Mixture-specific equilibrium-solid and hydrate checks passed for all "
            + states.length + " cells" + (hasUpstreamVessel() ? " and the upstream vessel" : "")));
    diagnostics.add(new Diagnostic("TRANSIENT_MODEL_UNQUALIFIED",
        "Conservation, dilute-limit and refinement evidence do not establish facility qualification"));
    return ReleaseFlowResult.success(releaseModel, positiveFlux * areaM2, boundaryVelocity >= exit.soundSpeed * 0.999,
        stations, diagnostics, exit.soundSpeed, false);
  }

  private static void requireGasOnly(SystemInterface fluid) {
    if (fluid.isChemicalSystem() || fluid.isForcePhaseTypes() || fluid.doSolidPhaseCheck() || fluid.getHydrateCheck()) {
      throw new UnsupportedOperationException("Reactions, forced phases, solids and hydrates are excluded");
    }
    if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new UnsupportedOperationException("Real-gas decompression requires one equilibrium gas phase");
    }
  }

  private static double density(SystemInterface fluid) {
    return ReleaseFlowRequest.positive(fluid.getMass("kg") / fluid.getVolume("m3"), "EOS density");
  }

  private static double[] componentMassFractions(SystemInterface fluid) {
    double totalMass = ReleaseFlowRequest.positive(fluid.getMass("kg"), "composition mass");
    double[] fractions = new double[fluid.getNumberOfComponents()];
    double sum = 0.0;
    for (int component = 0; component < fractions.length; component++) {
      fractions[component] = fluid.getComponent(component).getNumberOfmoles()
          * fluid.getComponent(component).getMolarMass() / totalMass;
      sum += fractions[component];
    }
    if (Math.abs(sum - 1.0) > 1.0e-8) {
      throw new IllegalStateException("REAL_GAS_PIPE_COMPOSITION_MASS_FRACTIONS_DO_NOT_CLOSE");
    }
    return fractions;
  }

  private double totalMass(double[] rho) {
    double sum = 0.0;
    for (double value : rho) {
      sum += value * cellVolumeM3;
    }
    return sum;
  }

  private double totalEnergy(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value * cellVolumeM3;
    }
    return sum;
  }

  private double wallEnergy(double[] temperaturesK, double[] outerTemperaturesK) {
    if (!hasCapacitiveWall()) {
      return 0.0;
    }
    double sum = 0.0;
    for (int cell = 0; cell < temperaturesK.length; cell++) {
      sum += wallArealHeatCapacityJPerM2K * cellExternalAreaM2 * temperaturesK[cell];
      if (hasRadialWallConduction()) {
        sum += outerWallArealHeatCapacityJPerM2K * cellExternalAreaM2 * outerTemperaturesK[cell];
      }
    }
    return sum;
  }

  private double meanWallTemperatureK() {
    if (!hasCapacitiveWall()) {
      return 0.0;
    }
    if (!hasRadialWallConduction()) {
      return mean(wallTemperatureK);
    }
    return (wallArealHeatCapacityJPerM2K * mean(wallTemperatureK)
        + outerWallArealHeatCapacityJPerM2K * mean(outerWallTemperatureK))
        / (wallArealHeatCapacityJPerM2K + outerWallArealHeatCapacityJPerM2K);
  }

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " must be finite");
    }
    return value;
  }

  private static double mean(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return values.length == 0 ? 0.0 : sum / values.length;
  }

  private static void close(double actual, double expected, double scale, String code) {
    if (!Double.isFinite(actual) || Math.abs(actual - expected) > CLOSURE_TOLERANCE * Math.max(1.0, Math.abs(scale))) {
      throw new IllegalStateException(code + ": residual=" + (actual - expected));
    }
  }

  /** @return outlet-adjacent EOS snapshot */
  @Override
  public synchronized SystemInterface getFluid() {
    return cellState(density[density.length - 1], momentum[momentum.length - 1], energy[energy.length - 1]).fluid
        .clone();
  }

  /** @return current immutable release-boundary request */
  @Override
  public synchronized ReleaseFlowRequest getReleaseRequest() {
    return new ReleaseFlowRequest(getFluid(), diameterM, dischargeCoefficient, backPressurePa, lengthM,
        darcyFrictionFactor);
  }

  /** @return transient model identity; direct stateless calculation is unsupported */
  @Override
  public ReleaseFlowModel getReleaseModel() {
    return releaseModel;
  }

  /** @return exact committed end-of-step boundary result */
  @Override
  public synchronized ReleaseFlowResult getReleaseResult() {
    return currentResult;
  }

  /** @return whether the physical downstream boundary is open */
  @Override
  public synchronized boolean isReleaseEnabled() {
    return releaseEnabled;
  }

  /** Opens or closes the physical boundary before the next process calculation. */
  public synchronized void setReleaseEnabled(boolean enabled) {
    releaseEnabled = enabled;
    setCalculationIdentifier(null);
  }

  /** @return stable frame provenance basis */
  @Override
  public String getReleaseBasis() {
    return hasUpstreamVessel() ? "COUPLED_1D_REAL_GAS_VESSEL_PIPE_DECOMPRESSION"
        : "COUPLED_1D_REAL_GAS_PIPE_DECOMPRESSION_LINE_PACKING";
  }

  /** @return immutable SI accounting and discretization provenance */
  @Override
  public synchronized Map<String, String> getReleaseProvenance() {
    Map<String, String> provenance = new LinkedHashMap<String, String>();
    provenance.put("pipeLengthM", Double.toString(lengthM));
    provenance.put("pipeDiameterM", Double.toString(diameterM));
    provenance.put("pipeVolumeM3", Double.toString(areaM2 * lengthM));
    provenance.put("pipeCells", Integer.toString(density.length));
    provenance.put("pipeCfl", Double.toString(cfl));
    provenance.put("pipeDarcyFrictionFactor", Double.toString(darcyFrictionFactor));
    provenance.put("pipeHeatTransferBoundary",
        hasRadialWallConduction() ? "CYLINDRICAL_TWO_NODE_MATERIAL_WALL_FIXED_ENVIRONMENT"
            : wallThermalBoundary.hasMaterialGeometry() ? "CYLINDRICAL_LUMPED_MATERIAL_WALL_FIXED_ENVIRONMENT"
                : hasCapacitiveWall() ? "FINITE_CAPACITANCE_WALL_FIXED_ENVIRONMENT"
                    : hasDistributedHeatTransfer() ? "CONSTANT_U_FIXED_EXTERNAL_TEMPERATURE" : "ADIABATIC");
    provenance.put("pipeHeatTransferCoefficientWPerM2K", Double.toString(heatTransferCoefficientWPerM2K));
    provenance.put("pipeExternalAreaM2", Double.toString(cellExternalAreaM2 * density.length));
    if (hasDistributedHeatTransfer()) {
      provenance.put("pipeExternalTemperatureK", Double.toString(externalTemperatureK));
    }
    if (hasCapacitiveWall()) {
      provenance.put("pipeWallToEnvironmentCoefficientWPerM2K", Double.toString(wallToEnvironmentCoefficientWPerM2K));
      provenance.put("pipeWallArealHeatCapacityJPerM2K", Double.toString(wallArealHeatCapacityJPerM2K));
      provenance.put("pipeInitialWallTemperatureK", Double.toString(initialWallTemperatureK));
      provenance.put("pipeMeanWallTemperatureK", Double.toString(meanWallTemperatureK()));
      provenance.put("pipeWallStoredEnergyJ", Double.toString(wallEnergy(wallTemperatureK, outerWallTemperatureK)));
      if (wallThermalBoundary.hasMaterialGeometry()) {
        provenance.put("pipeWallInnerDiameterM", Double.toString(wallThermalBoundary.innerDiameterM));
        provenance.put("pipeWallOuterDiameterM", Double.toString(wallThermalBoundary.outerDiameterM));
        provenance.put("pipeWallThicknessM", Double.toString(wallThermalBoundary.wallThicknessM));
        provenance.put("pipeWallDensityKgPerM3", Double.toString(wallThermalBoundary.wallDensityKgPerM3));
        provenance.put("pipeWallSpecificHeatJPerKgK", Double.toString(wallThermalBoundary.wallSpecificHeatJPerKgK));
        provenance.put("pipeWallConductivityWPerMK", Double.toString(wallThermalBoundary.wallConductivityWPerMK));
        provenance.put("pipeWallInnerFilmCoefficientWPerM2K",
            Double.toString(wallThermalBoundary.innerFilmCoefficientWPerM2K));
        provenance.put("pipeWallOuterFilmCoefficientWPerM2K",
            Double.toString(wallThermalBoundary.outerFilmCoefficientWPerM2K));
        if (hasRadialWallConduction()) {
          provenance.put("pipeWallRadialNodes", "2");
          provenance.put("pipeWallInnerNodeRadiusM", Double.toString(wallThermalBoundary.innerNodeRadiusM));
          provenance.put("pipeWallOuterNodeRadiusM", Double.toString(wallThermalBoundary.outerNodeRadiusM));
          provenance.put("pipeWallSplitRadiusM", Double.toString(wallThermalBoundary.splitRadiusM));
          provenance.put("pipeWallInnerNodeArealHeatCapacityJPerM2K", Double.toString(wallArealHeatCapacityJPerM2K));
          provenance.put("pipeWallOuterNodeArealHeatCapacityJPerM2K",
              Double.toString(outerWallArealHeatCapacityJPerM2K));
          provenance.put("pipeWallRadialConductanceWPerM2K", Double.toString(radialWallConductanceWPerM2K));
          provenance.put("pipeMeanInnerWallTemperatureK", Double.toString(mean(wallTemperatureK)));
          provenance.put("pipeMeanOuterWallTemperatureK", Double.toString(mean(outerWallTemperatureK)));
        } else {
          provenance.put("pipeWallLumpedCharacteristicLengthM",
              Double.toString(wallThermalBoundary.lumpedCharacteristicLengthM));
          provenance.put("pipeWallLumpedBiotNumber", Double.toString(wallThermalBoundary.lumpedBiotNumber));
          provenance.put("pipeWallLumpedBiotLimit", Double.toString(WallThermalBoundary.MAX_LUMPED_BIOT_NUMBER));
        }
      }
    }
    provenance.put("pipeIntegrator",
        hasRadialWallConduction()
            ? hasUpstreamVessel() ? "EOS_VU_LUMPED_VESSEL_FINITE_VOLUME_PIPE_LLF_RADIAL_WALL_V1"
                : "EOS_VU_FINITE_VOLUME_LLF_RADIAL_WALL_V1"
            : hasUpstreamVessel() ? "EOS_VU_LUMPED_VESSEL_FINITE_VOLUME_PIPE_LLF_V1"
                : "EOS_VU_FINITE_VOLUME_LOCAL_LAX_FRIEDRICHS_V1");
    provenance.put("pipeThermodynamicModel", compositionReference.getModelName());
    provenance.put("inventoryTimeS", Double.toString(getTime()));
    provenance.put("inventoryVolumeM3", Double.toString(areaM2 * lengthM + upstreamVesselVolumeM3));
    provenance.put("upstreamBoundary", hasUpstreamVessel() ? "RIGID_ADIABATIC_WELL_MIXED_GAS_VESSEL" : "CLOSED_END");
    if (hasUpstreamVessel()) {
      CellState upstream = upstreamState(upstreamVesselMassKg, upstreamVesselEnergyJ,
          cellStates(density, momentum, energy)[0]);
      provenance.put("upstreamVesselVolumeM3", Double.toString(upstreamVesselVolumeM3));
      provenance.put("upstreamVesselMassKg", Double.toString(upstreamVesselMassKg));
      provenance.put("upstreamVesselInternalEnergyJ", Double.toString(upstreamVesselEnergyJ));
      provenance.put("upstreamVesselPressurePa", Double.toString(upstream.pressurePa));
      provenance.put("upstreamVesselTemperatureK", Double.toString(upstream.temperatureK));
    }
    provenance.put("cumulativeReleasedMassKg", Double.toString(releasedMassKg));
    provenance.put("cumulativeReleasedTotalEnergyJ", Double.toString(releasedEnergyJ));
    provenance.put("cumulativePipeHeatTransferJ", Double.toString(cumulativeHeatTransferJ));
    provenance.put("cumulativePipeGasWallHeatTransferJ", Double.toString(cumulativeGasWallHeatTransferJ));
    provenance.put("instantaneousPipeHeatTransferW", Double.toString(lastHeatTransferRateW));
    provenance.put("instantaneousPipeGasWallHeatTransferW", Double.toString(lastGasWallHeatTransferRateW));
    provenance.put("instantaneousMassFlowKgS", Double.toString(lastMassFlowKgS));
    provenance.put("instantaneousTotalEnergyFlowW", Double.toString(lastEnergyFlowW));
    provenance.put("inventoryLastSubsteps", Integer.toString(lastSubsteps));
    provenance.put("rateTimeBasis", "INSTANTANEOUS_AT_FRAME_TIME");
    return Collections.unmodifiableMap(provenance);
  }

  /** @return initial EOS line-pack mass in kg */
  public double getInitialMassKg() {
    return initialMassKg;
  }

  /** @return current finite-volume line-pack mass in kg */
  public synchronized double getRemainingMassKg() {
    return totalMass(density) + upstreamVesselMassKg;
  }

  /** @return current finite-volume pipe line-pack mass in kg, excluding the upstream vessel */
  public synchronized double getRemainingPipeMassKg() {
    return totalMass(density);
  }

  /** @return true when a finite rigid upstream gas vessel is coupled to the first pipe cell */
  public boolean hasFiniteUpstreamVessel() {
    return hasUpstreamVessel();
  }

  /** @return configured upstream vessel volume in m3, or zero for the legacy closed end */
  public double getUpstreamVesselVolumeM3() {
    return upstreamVesselVolumeM3;
  }

  /** @return current upstream vessel mass in kg, or zero for the legacy closed end */
  public synchronized double getUpstreamVesselMassKg() {
    return upstreamVesselMassKg;
  }

  /** @return current upstream vessel internal energy in J, or zero for the legacy closed end */
  public synchronized double getUpstreamVesselEnergyJ() {
    return upstreamVesselEnergyJ;
  }

  /** @return defensive current upstream-vessel EOS snapshot, or null for the legacy closed end */
  public synchronized SystemInterface getUpstreamVesselFluid() {
    if (!hasUpstreamVessel()) {
      return null;
    }
    CellState[] states = cellStates(density, momentum, energy);
    return upstreamState(upstreamVesselMassKg, upstreamVesselEnergyJ, states[0]).fluid.clone();
  }

  /** @return cumulative discharged mass in kg */
  public synchronized double getReleasedMassKg() {
    return releasedMassKg;
  }

  /** @return initial conservative gas, optional vessel and optional wall total energy in J */
  public double getInitialEnergyJ() {
    return initialEnergyJ;
  }

  /** @return current conservative gas, optional vessel and optional wall total energy in J */
  public synchronized double getRemainingEnergyJ() {
    return totalEnergy(energy) + wallEnergy(wallTemperatureK, outerWallTemperatureK) + upstreamVesselEnergyJ;
  }

  /** @return current conservative pipe total energy in J, excluding the upstream vessel */
  public synchronized double getRemainingPipeEnergyJ() {
    return totalEnergy(energy);
  }

  /** @return cumulative discharged total energy in J */
  public synchronized double getReleasedEnergyJ() {
    return releasedEnergyJ;
  }

  /** @return true when the pipe has a prescribed distributed wall heat-transfer boundary */
  public boolean hasDistributedWallHeatTransfer() {
    return hasDistributedHeatTransfer();
  }

  /** @return prescribed overall heat-transfer coefficient in W/(m2 K), or zero for adiabatic */
  public double getHeatTransferCoefficientWPerM2K() {
    return heatTransferCoefficientWPerM2K;
  }

  /** @return fixed external-boundary temperature in K, or zero for adiabatic */
  public double getExternalTemperatureK() {
    return externalTemperatureK;
  }

  /** @return cumulative heat transferred into the pipe in J; cooling is negative */
  public synchronized double getCumulativeHeatTransferJ() {
    return cumulativeHeatTransferJ;
  }

  /** @return latest distributed heat-transfer rate into the pipe in W; cooling is negative */
  public synchronized double getLastHeatTransferRateW() {
    return lastHeatTransferRateW;
  }

  /** @return true when each finite-volume cell retains an explicit wall-temperature state */
  public boolean hasFiniteWallThermalCapacitance() {
    return hasCapacitiveWall();
  }

  /** @return true when the material wall retains separate inner and outer radial temperature nodes */
  public boolean hasTwoNodeRadialWallConduction() {
    return hasRadialWallConduction();
  }

  /**
   * Returns the lumped or inner radial-node temperatures.
   *
   * @return defensive wall-temperature profile in K, or an empty array for a massless/adiabatic wall
   */
  public synchronized double[] getWallTemperatureProfileK() {
    return wallTemperatureK.clone();
  }

  /**
   * Returns the outer radial-node temperatures.
   *
   * @return defensive outer-node profile in K, or an empty array without two-node radial conduction
   */
  public synchronized double[] getOuterWallTemperatureProfileK() {
    return outerWallTemperatureK.clone();
  }

  /** @return current prescribed wall stored energy in J, or zero without wall capacitance */
  public synchronized double getWallStoredEnergyJ() {
    return wallEnergy(wallTemperatureK, outerWallTemperatureK);
  }

  /** @return cumulative heat transferred from the wall into the gas in J */
  public synchronized double getCumulativeGasWallHeatTransferJ() {
    return cumulativeGasWallHeatTransferJ;
  }

  /** @return latest heat-transfer rate from wall to gas in W */
  public synchronized double getLastGasWallHeatTransferRateW() {
    return lastGasWallHeatTransferRateW;
  }

  /** @return current cell-centre absolute pressures in Pa, ordered closed end to release end */
  public synchronized double[] getPressureProfilePa() {
    CellState[] states = cellStates(density, momentum, energy);
    double[] pressure = new double[states.length];
    for (int cell = 0; cell < states.length; cell++) {
      pressure[cell] = states[cell].pressurePa;
    }
    return pressure;
  }

  /** @return current cell-centre temperatures in K, ordered closed end to release end */
  public synchronized double[] getTemperatureProfileK() {
    CellState[] states = cellStates(density, momentum, energy);
    double[] temperature = new double[states.length];
    for (int cell = 0; cell < states.length; cell++) {
      temperature[cell] = states[cell].temperatureK;
    }
    return temperature;
  }

  /** @return fixed overall component mass fractions in input order */
  public double[] getComponentMassFractions() {
    return massFractions.clone();
  }

  /** Immutable prescribed thermal boundary for the finite-volume pipe wall. */
  public static final class WallThermalBoundary implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final double MAX_LUMPED_BIOT_NUMBER = 0.1;
    private final double gasToWallCoefficientWPerM2K;
    private final double wallToEnvironmentCoefficientWPerM2K;
    private final double wallArealHeatCapacityJPerM2K;
    private final double initialWallTemperatureK;
    private final double externalTemperatureK;
    private final double innerDiameterM;
    private final double outerDiameterM;
    private final double wallThicknessM;
    private final double wallDensityKgPerM3;
    private final double wallSpecificHeatJPerKgK;
    private final double wallConductivityWPerMK;
    private final double innerFilmCoefficientWPerM2K;
    private final double outerFilmCoefficientWPerM2K;
    private final double lumpedCharacteristicLengthM;
    private final double lumpedBiotNumber;
    private final double outerWallArealHeatCapacityJPerM2K;
    private final double radialWallConductanceWPerM2K;
    private final double innerNodeRadiusM;
    private final double outerNodeRadiusM;
    private final double splitRadiusM;

    /**
     * Creates a boundary without material-geometry provenance.
     *
     * @param gasToWallCoefficientWPerM2K gas coefficient on the heat-transfer area basis in W/(m2 K)
     * @param wallToEnvironmentCoefficientWPerM2K environment coefficient in W/(m2 K)
     * @param wallArealHeatCapacityJPerM2K wall heat capacity per heat-transfer area in J/(m2 K)
     * @param initialWallTemperatureK initial wall temperature in K
     * @param externalTemperatureK fixed environment temperature in K
     */
    private WallThermalBoundary(double gasToWallCoefficientWPerM2K, double wallToEnvironmentCoefficientWPerM2K,
        double wallArealHeatCapacityJPerM2K, double initialWallTemperatureK, double externalTemperatureK) {
      this(gasToWallCoefficientWPerM2K, wallToEnvironmentCoefficientWPerM2K, wallArealHeatCapacityJPerM2K,
          initialWallTemperatureK, externalTemperatureK, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
          0.0, 0.0, 0.0);
    }

    /**
     * Creates a boundary including optional material-geometry provenance.
     *
     * @param gasToWallCoefficientWPerM2K gas coefficient on the heat-transfer area basis in W/(m2 K)
     * @param wallToEnvironmentCoefficientWPerM2K environment coefficient in W/(m2 K)
     * @param wallArealHeatCapacityJPerM2K wall heat capacity per heat-transfer area in J/(m2 K)
     * @param initialWallTemperatureK initial wall temperature in K
     * @param externalTemperatureK fixed environment temperature in K
     * @param innerDiameterM pipe internal diameter in m, or zero without material geometry
     * @param outerDiameterM wall outer diameter in m, or zero without material geometry
     * @param wallThicknessM wall thickness in m, or zero without material geometry
     * @param wallDensityKgPerM3 wall density in kg/m3, or zero without material geometry
     * @param wallSpecificHeatJPerKgK wall specific heat in J/(kg K), or zero without material geometry
     * @param wallConductivityWPerMK wall conductivity in W/(m K), or zero without material geometry
     * @param innerFilmCoefficientWPerM2K inner-area film coefficient in W/(m2 K), or zero without material geometry
     * @param outerFilmCoefficientWPerM2K outer-area film coefficient in W/(m2 K), or zero without material geometry
     * @param lumpedCharacteristicLengthM lumped characteristic length in m, or zero without material geometry
     * @param lumpedBiotNumber lumped Biot number, or zero without material geometry
     * @param outerWallArealHeatCapacityJPerM2K outer radial-node heat capacity per outer area in J/(m2 K)
     * @param radialWallConductanceWPerM2K radial conductance between wall nodes on the outer-area basis in W/(m2 K)
     * @param innerNodeRadiusM inner radial-node centroid radius in m
     * @param outerNodeRadiusM outer radial-node centroid radius in m
     * @param splitRadiusM control-volume split radius in m
     */
    private WallThermalBoundary(double gasToWallCoefficientWPerM2K, double wallToEnvironmentCoefficientWPerM2K,
        double wallArealHeatCapacityJPerM2K, double initialWallTemperatureK, double externalTemperatureK,
        double innerDiameterM, double outerDiameterM, double wallThicknessM, double wallDensityKgPerM3,
        double wallSpecificHeatJPerKgK, double wallConductivityWPerMK, double innerFilmCoefficientWPerM2K,
        double outerFilmCoefficientWPerM2K, double lumpedCharacteristicLengthM, double lumpedBiotNumber,
        double outerWallArealHeatCapacityJPerM2K, double radialWallConductanceWPerM2K, double innerNodeRadiusM,
        double outerNodeRadiusM, double splitRadiusM) {
      this.gasToWallCoefficientWPerM2K = gasToWallCoefficientWPerM2K;
      this.wallToEnvironmentCoefficientWPerM2K = wallToEnvironmentCoefficientWPerM2K;
      this.wallArealHeatCapacityJPerM2K = wallArealHeatCapacityJPerM2K;
      this.initialWallTemperatureK = initialWallTemperatureK;
      this.externalTemperatureK = externalTemperatureK;
      this.innerDiameterM = innerDiameterM;
      this.outerDiameterM = outerDiameterM;
      this.wallThicknessM = wallThicknessM;
      this.wallDensityKgPerM3 = wallDensityKgPerM3;
      this.wallSpecificHeatJPerKgK = wallSpecificHeatJPerKgK;
      this.wallConductivityWPerMK = wallConductivityWPerMK;
      this.innerFilmCoefficientWPerM2K = innerFilmCoefficientWPerM2K;
      this.outerFilmCoefficientWPerM2K = outerFilmCoefficientWPerM2K;
      this.lumpedCharacteristicLengthM = lumpedCharacteristicLengthM;
      this.lumpedBiotNumber = lumpedBiotNumber;
      this.outerWallArealHeatCapacityJPerM2K = outerWallArealHeatCapacityJPerM2K;
      this.radialWallConductanceWPerM2K = radialWallConductanceWPerM2K;
      this.innerNodeRadiusM = innerNodeRadiusM;
      this.outerNodeRadiusM = outerNodeRadiusM;
      this.splitRadiusM = splitRadiusM;
    }

    /** @return an adiabatic boundary with no wall state */
    public static WallThermalBoundary adiabatic() {
      return new WallThermalBoundary(0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Creates a massless constant-U boundary to a fixed external temperature.
     *
     * @param overallCoefficientWPerM2K prescribed overall coefficient in W/(m2 K), or zero for adiabatic
     * @param externalTemperatureK fixed external temperature in K, or zero for adiabatic
     * @return immutable direct boundary
     */
    public static WallThermalBoundary fixedExternalTemperature(double overallCoefficientWPerM2K,
        double externalTemperatureK) {
      ReleaseFlowRequest.nonnegative(overallCoefficientWPerM2K, "overallCoefficientWPerM2K");
      if (overallCoefficientWPerM2K == 0.0 && externalTemperatureK == 0.0) {
        return adiabatic();
      }
      ReleaseFlowRequest.positive(overallCoefficientWPerM2K, "overallCoefficientWPerM2K");
      ReleaseFlowRequest.positive(externalTemperatureK, "externalTemperatureK");
      return new WallThermalBoundary(overallCoefficientWPerM2K, 0.0, 0.0, 0.0, externalTemperatureK);
    }

    /**
     * Creates a finite-capacitance wall coupled separately to gas and a fixed environment.
     *
     * <p>
     * The wall uses one lumped temperature per axial cell and no radial temperature profile. Coefficients, areal heat
     * capacity and temperatures are caller-prescribed SI inputs; this factory does not derive material properties or
     * fire, soil, ambient or seawater correlations.
     * </p>
     *
     * @param gasToWallCoefficientWPerM2K gas-to-wall coefficient in W/(m2 K)
     * @param wallToEnvironmentCoefficientWPerM2K wall-to-environment coefficient in W/(m2 K)
     * @param wallArealHeatCapacityJPerM2K wall heat capacity per pipe external area in J/(m2 K)
     * @param initialWallTemperatureK initial uniform wall temperature in K
     * @param externalTemperatureK fixed environment temperature in K
     * @return immutable capacitive boundary
     */
    public static WallThermalBoundary capacitive(double gasToWallCoefficientWPerM2K,
        double wallToEnvironmentCoefficientWPerM2K, double wallArealHeatCapacityJPerM2K, double initialWallTemperatureK,
        double externalTemperatureK) {
      return new WallThermalBoundary(
          ReleaseFlowRequest.positive(gasToWallCoefficientWPerM2K, "gasToWallCoefficientWPerM2K"),
          ReleaseFlowRequest.positive(wallToEnvironmentCoefficientWPerM2K, "wallToEnvironmentCoefficientWPerM2K"),
          ReleaseFlowRequest.positive(wallArealHeatCapacityJPerM2K, "wallArealHeatCapacityJPerM2K"),
          ReleaseFlowRequest.positive(initialWallTemperatureK, "initialWallTemperatureK"),
          ReleaseFlowRequest.positive(externalTemperatureK, "externalTemperatureK"));
    }

    /**
     * Derives a lumped cylindrical wall boundary from explicit geometry and material properties.
     *
     * <p>
     * The heat-transfer area is the cylinder outer area. The supplied inner film coefficient is converted to that area,
     * and the wall heat capacity is derived from the annular material volume. The lumped temperature approximation is
     * accepted only when {@code max(hInner, hOuter) * (outerDiameter - innerDiameter) / (4 * conductivity) < 0.1}. This
     * fail-closed check is an applicability guard, not a radial-conduction solution or material-property database.
     * </p>
     *
     * @param innerDiameterM pipe internal diameter in m
     * @param wallThicknessM wall thickness in m
     * @param wallDensityKgPerM3 wall material density in kg/m3
     * @param wallSpecificHeatJPerKgK wall material specific heat in J/(kg K)
     * @param wallConductivityWPerMK wall material conductivity in W/(m K)
     * @param innerFilmCoefficientWPerM2K gas-side film coefficient on inner area in W/(m2 K)
     * @param outerFilmCoefficientWPerM2K environment-side film coefficient on outer area in W/(m2 K)
     * @param initialWallTemperatureK initial uniform wall temperature in K
     * @param externalTemperatureK fixed environment temperature in K
     * @return immutable material-derived lumped wall boundary
     */
    public static WallThermalBoundary cylindricalLumpedWall(double innerDiameterM, double wallThicknessM,
        double wallDensityKgPerM3, double wallSpecificHeatJPerKgK, double wallConductivityWPerMK,
        double innerFilmCoefficientWPerM2K, double outerFilmCoefficientWPerM2K, double initialWallTemperatureK,
        double externalTemperatureK) {
      double innerDiameter = ReleaseFlowRequest.positive(innerDiameterM, "innerDiameterM");
      double thickness = ReleaseFlowRequest.positive(wallThicknessM, "wallThicknessM");
      double density = ReleaseFlowRequest.positive(wallDensityKgPerM3, "wallDensityKgPerM3");
      double specificHeat = ReleaseFlowRequest.positive(wallSpecificHeatJPerKgK, "wallSpecificHeatJPerKgK");
      double conductivity = ReleaseFlowRequest.positive(wallConductivityWPerMK, "wallConductivityWPerMK");
      double innerFilm = ReleaseFlowRequest.positive(innerFilmCoefficientWPerM2K, "innerFilmCoefficientWPerM2K");
      double outerFilm = ReleaseFlowRequest.positive(outerFilmCoefficientWPerM2K, "outerFilmCoefficientWPerM2K");
      double outerDiameter = ReleaseFlowRequest.positive(innerDiameter + 2.0 * thickness, "outerDiameterM");
      double characteristicLength = (outerDiameter - innerDiameter) / 4.0;
      double biotNumber = Math.max(innerFilm, outerFilm) * characteristicLength / conductivity;
      if (!Double.isFinite(biotNumber) || biotNumber >= MAX_LUMPED_BIOT_NUMBER) {
        throw new IllegalArgumentException(
            "Lumped cylindrical wall requires Biot number below 0.1; calculated " + biotNumber);
      }
      double gasCoefficientOnOuterArea = innerFilm * innerDiameter / outerDiameter;
      double arealHeatCapacity = density * specificHeat
          * (outerDiameter * outerDiameter - innerDiameter * innerDiameter) / (4.0 * outerDiameter);
      return new WallThermalBoundary(gasCoefficientOnOuterArea, outerFilm,
          ReleaseFlowRequest.positive(arealHeatCapacity, "wallArealHeatCapacityJPerM2K"),
          ReleaseFlowRequest.positive(initialWallTemperatureK, "initialWallTemperatureK"),
          ReleaseFlowRequest.positive(externalTemperatureK, "externalTemperatureK"), innerDiameter, outerDiameter,
          thickness, density, specificHeat, conductivity, innerFilm, outerFilm, characteristicLength, biotNumber, 0.0,
          0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Derives a conservative two-node radial cylindrical wall from explicit geometry and material properties.
     *
     * <p>
     * The annulus is split into equal-volume inner and outer control volumes. Each node retains its own temperature.
     * The node-to-node conductance uses the exact cylindrical resistance between the two volume-centroid radii. Film
     * coefficients and all wall properties remain caller-supplied SI inputs; this factory neither predicts boundary
     * correlations nor represents more than two radial wall nodes.
     * </p>
     *
     * @param innerDiameterM pipe internal diameter in m
     * @param wallThicknessM wall thickness in m
     * @param wallDensityKgPerM3 wall material density in kg/m3
     * @param wallSpecificHeatJPerKgK wall material specific heat in J/(kg K)
     * @param wallConductivityWPerMK wall material conductivity in W/(m K)
     * @param innerFilmCoefficientWPerM2K gas-side film coefficient on inner area in W/(m2 K)
     * @param outerFilmCoefficientWPerM2K environment-side film coefficient on outer area in W/(m2 K)
     * @param initialWallTemperatureK initial uniform wall temperature in K
     * @param externalTemperatureK fixed environment temperature in K
     * @return immutable two-node radial material wall boundary
     */
    public static WallThermalBoundary cylindricalTwoNodeWall(double innerDiameterM, double wallThicknessM,
        double wallDensityKgPerM3, double wallSpecificHeatJPerKgK, double wallConductivityWPerMK,
        double innerFilmCoefficientWPerM2K, double outerFilmCoefficientWPerM2K, double initialWallTemperatureK,
        double externalTemperatureK) {
      double innerDiameter = ReleaseFlowRequest.positive(innerDiameterM, "innerDiameterM");
      double thickness = ReleaseFlowRequest.positive(wallThicknessM, "wallThicknessM");
      double density = ReleaseFlowRequest.positive(wallDensityKgPerM3, "wallDensityKgPerM3");
      double specificHeat = ReleaseFlowRequest.positive(wallSpecificHeatJPerKgK, "wallSpecificHeatJPerKgK");
      double conductivity = ReleaseFlowRequest.positive(wallConductivityWPerMK, "wallConductivityWPerMK");
      double innerFilm = ReleaseFlowRequest.positive(innerFilmCoefficientWPerM2K, "innerFilmCoefficientWPerM2K");
      double outerFilm = ReleaseFlowRequest.positive(outerFilmCoefficientWPerM2K, "outerFilmCoefficientWPerM2K");
      double innerRadius = innerDiameter / 2.0;
      double outerRadius = innerRadius + thickness;
      double outerDiameter = 2.0 * outerRadius;
      double splitRadius = Math.sqrt((innerRadius * innerRadius + outerRadius * outerRadius) / 2.0);
      double innerNodeRadius = Math.sqrt((innerRadius * innerRadius + splitRadius * splitRadius) / 2.0);
      double outerNodeRadius = Math.sqrt((splitRadius * splitRadius + outerRadius * outerRadius) / 2.0);
      double gasCoefficientOnOuterArea = innerFilm * innerRadius / outerRadius;
      double innerNodeCapacity = density * specificHeat * (splitRadius * splitRadius - innerRadius * innerRadius)
          / (2.0 * outerRadius);
      double outerNodeCapacity = density * specificHeat * (outerRadius * outerRadius - splitRadius * splitRadius)
          / (2.0 * outerRadius);
      double radialConductance = conductivity / (outerRadius * Math.log(outerNodeRadius / innerNodeRadius));
      return new WallThermalBoundary(gasCoefficientOnOuterArea, outerFilm,
          ReleaseFlowRequest.positive(innerNodeCapacity, "innerWallArealHeatCapacityJPerM2K"),
          ReleaseFlowRequest.positive(initialWallTemperatureK, "initialWallTemperatureK"),
          ReleaseFlowRequest.positive(externalTemperatureK, "externalTemperatureK"), innerDiameter, outerDiameter,
          thickness, density, specificHeat, conductivity, innerFilm, outerFilm, 0.0, 0.0,
          ReleaseFlowRequest.positive(outerNodeCapacity, "outerWallArealHeatCapacityJPerM2K"),
          ReleaseFlowRequest.positive(radialConductance, "radialWallConductanceWPerM2K"), innerNodeRadius,
          outerNodeRadius, splitRadius);
    }

    private boolean hasMaterialGeometry() {
      return outerDiameterM > 0.0;
    }

    /**
     * Resolves the cylindrical area-basis diameter.
     *
     * @param defaultDiameterM pipe diameter in m for boundaries without explicit wall geometry
     * @return outer wall diameter for material geometry, otherwise the default diameter
     */
    private double heatTransferAreaDiameterM(double defaultDiameterM) {
      return hasMaterialGeometry() ? outerDiameterM : defaultDiameterM;
    }
  }

  private static final class CellState {
    private final double density;
    private final double momentum;
    private final double energy;
    private final double velocity;
    private final double pressurePa;
    private final double temperatureK;
    private final double soundSpeed;
    private final SystemInterface fluid;

    private CellState(double density, double momentum, double energy, double velocity, double pressurePa,
        double temperatureK, double soundSpeed, SystemInterface fluid) {
      this.density = density;
      this.momentum = momentum;
      this.energy = energy;
      this.velocity = velocity;
      this.pressurePa = pressurePa;
      this.temperatureK = temperatureK;
      this.soundSpeed = soundSpeed;
      this.fluid = fluid;
    }

    private CellState reflected() {
      return new CellState(density, -momentum, energy, -velocity, pressurePa, temperatureK, soundSpeed, fluid);
    }
  }

  private static final class Flux {
    private final double mass;
    private final double momentum;
    private final double energy;

    private Flux(double mass, double momentum, double energy) {
      this.mass = mass;
      this.momentum = momentum;
      this.energy = energy;
    }
  }

  private static final class Step {
    private final double[] density;
    private final double[] momentum;
    private final double[] energy;
    private final double[] wallTemperatureK;
    private final double[] outerWallTemperatureK;
    private final CellState[] states;
    private final CellState upstream;
    private final double upstreamMass;
    private final double upstreamEnergy;
    private final Flux outlet;
    private final double heatTransferJ;
    private final double gasWallHeatTransferJ;

    private Step(double[] density, double[] momentum, double[] energy, double[] wallTemperatureK,
        double[] outerWallTemperatureK, CellState[] states, CellState upstream, double upstreamMass,
        double upstreamEnergy, Flux outlet, double heatTransferJ, double gasWallHeatTransferJ) {
      this.density = density;
      this.momentum = momentum;
      this.energy = energy;
      this.wallTemperatureK = wallTemperatureK;
      this.outerWallTemperatureK = outerWallTemperatureK;
      this.states = states;
      this.upstream = upstream;
      this.upstreamMass = upstreamMass;
      this.upstreamEnergy = upstreamEnergy;
      this.outlet = outlet;
      this.heatTransferJ = heatTransferJ;
      this.gasWallHeatTransferJ = gasWallHeatTransferJ;
    }
  }

  private static final class TransientBoundaryModel implements ReleaseFlowModel {
    private static final long serialVersionUID = 1L;

    @Override
    public String getModelId() {
      return "real-gas-pipe-decompression";
    }

    @Override
    public String getModelVersion() {
      return "1.5.0";
    }

    @Override
    public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
      return ReleaseFlowResult.failure(this, true, "TRANSIENT_STATE_REQUIRED",
          "Use RealGasPipeDecompression through native process transient execution");
    }

    @Override
    public ReleaseModelEvidence getEvidence() {
      return new ReleaseModelEvidence("real-gas-pipe-decompression:1.5.0",
          Arrays.asList("ONE_DIMENSIONAL_PIPE", "SINGLE_EQUILIBRIUM_GAS", "EOS_PROPERTY_EVOLUTION",
              "TRANSIENT_LINE_PACKING", "OPTIONAL_LUMPED_UPSTREAM_GAS_VESSEL",
              "OPTIONAL_DISTRIBUTED_CONSTANT_U_HEAT_TRANSFER", "OPTIONAL_FINITE_CAPACITANCE_WALL",
              "OPTIONAL_CYLINDRICAL_LUMPED_MATERIAL_WALL", "OPTIONAL_TWO_NODE_RADIAL_MATERIAL_WALL",
              "CONSERVATIVE_MASS_MOMENTUM_TOTAL_ENERGY", "MIXTURE_SOLID_RISK_ASSESSED"),
          Arrays.asList("NO_PREDICTIVE_FIRE_OR_AMBIENT_CORRELATION", "NO_HIGHER_ORDER_RADIAL_WALL_CONDUCTION",
              "NO_PREDICTIVE_WALL_MATERIAL_DATABASE", "NO_PIPE_ELASTICITY", "NO_TWO_SIDED_RUPTURE",
              "NO_VESSEL_INTERNAL_WAVES_OR_PHASE_SEPARATION", "NO_MULTIPHASE_SLIP", "NO_SOLID_BEARING_FLOW",
              "NO_EXPERIMENTAL_QUALIFICATION"),
          Arrays.asList(
              new ReleaseModelEvidence.Record("real-gas-pipe-conservation", ReleaseModelEvidence.Type.CONSERVATION,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "EOS finite-volume line pack plus discharged mass and total energy close at every committed step",
                  false),
              new ReleaseModelEvidence.Record("real-gas-pipe-dilute-limit", ReleaseModelEvidence.Type.ANALYTICAL,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "EOS wave and discharge results approach the perfect-gas implementation at dilute conditions", false),
              new ReleaseModelEvidence.Record("real-gas-pipe-refinement", ReleaseModelEvidence.Type.NUMERICAL,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Discharged mass and closed-end pressure are checked under spatial refinement", false),
              new ReleaseModelEvidence.Record("real-gas-vessel-pipe-conservation",
                  ReleaseModelEvidence.Type.CONSERVATION,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Lumped upstream vessel plus pipe line pack plus discharge close mass and total energy", false),
              new ReleaseModelEvidence.Record("real-gas-vessel-pipe-refinement", ReleaseModelEvidence.Type.NUMERICAL,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Vessel pressure and discharged mass are checked under CFL refinement and nearby vessel volumes",
                  false),
              new ReleaseModelEvidence.Record("real-gas-pipe-distributed-heat-conservation",
                  ReleaseModelEvidence.Type.CONSERVATION,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Pipe plus vessel plus discharge energy closes against accumulated distributed wall heat", false),
              new ReleaseModelEvidence.Record("real-gas-pipe-distributed-heat-refinement",
                  ReleaseModelEvidence.Type.NUMERICAL,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Heating and cooling signs, zero-coefficient identity and CFL refinement are checked", false),
              new ReleaseModelEvidence.Record("real-gas-pipe-capacitive-wall-conservation",
                  ReleaseModelEvidence.Type.CONSERVATION,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Gas, wall, vessel and discharge energy close against prescribed environment heat", false),
              new ReleaseModelEvidence.Record("real-gas-pipe-capacitive-wall-refinement",
                  ReleaseModelEvidence.Type.NUMERICAL,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Wall temperature and environment heat are checked under CFL refinement", false),
              new ReleaseModelEvidence.Record("real-gas-pipe-cylindrical-wall-geometry",
                  ReleaseModelEvidence.Type.ANALYTICAL,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Annular volume, outer-area conversion and fail-closed lumped Biot applicability are checked", false),
              new ReleaseModelEvidence.Record("real-gas-pipe-two-node-radial-wall",
                  ReleaseModelEvidence.Type.ANALYTICAL,
                  "src/test/java/neqsim/process/safety/release/RealGasPipeDecompressionTest.java",
                  "Equal-volume annular nodes, cylindrical resistance, energy closure and timestep refinement are checked",
                  false)));
    }
  }
}
