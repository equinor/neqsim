package neqsim.process.safety.release;

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
 * Conservative one-dimensional perfect-gas pipe decompression and line-packing inventory.
 *
 * <p>
 * The pipe is initially uniform, closed at its upstream end and connected at its downstream end to a constant-pressure
 * receiver. A first-order local Lax-Friedrichs finite-volume scheme solves the compressible Euler mass, momentum and
 * total-energy equations. Specified Darcy friction acts only in the momentum equation, so the adiabatic wall does not
 * remove total energy. The solver chooses CFL-limited substeps, commits state atomically and exports the exact boundary
 * mass and energy flux used by the inventory update.
 * </p>
 *
 * <p>
 * This model is intentionally limited to one calorically perfect equilibrium gas with fixed composition and constant
 * heat-capacity ratio. It excludes upstream vessel coupling, heat transfer, changing properties, phase change,
 * non-equilibrium slip, entrainment, solids, pipe elasticity and two-sided rupture. Numerical validation does not
 * confer engineering qualification.
 */
public final class IdealGasPipeDecompression extends ProcessEquipmentBaseClass implements CoupledReleaseSource {
  private static final long serialVersionUID = 1L;
  private static final double GAS_CONSTANT = 8.31446261815324;
  private static final int MAX_SUBSTEPS = 100000;
  private static final double CLOSURE_TOLERANCE = 2.0e-10;

  private final SystemInterface compositionReference;
  private final double initialPressurePa;
  private final double initialTemperatureK;
  private final double lengthM;
  private final double diameterM;
  private final double areaM2;
  private final double cellVolumeM3;
  private final double dischargeCoefficient;
  private final double backPressurePa;
  private final double darcyFrictionFactor;
  private final double cfl;
  private final double gamma;
  private final double gasConstantJkgK;
  private final double cvJkgK;
  private final double cpJkgK;
  private final double referenceEntropyJkgK;
  private final double[] massFractions;
  private final TransientBoundaryModel releaseModel = new TransientBoundaryModel();
  private double[] density;
  private double[] momentum;
  private double[] energy;
  private double initialMassKg;
  private double initialEnergyJ;
  private double releasedMassKg;
  private double releasedEnergyJ;
  private double lastMassFlowKgS;
  private double lastEnergyFlowW;
  private int lastSubsteps;
  private boolean releaseEnabled = true;
  private UUID lastTransientId;
  private double lastTransientDurationS;
  private ReleaseFlowResult currentResult;

  /**
   * Creates a one-sided transient pipe inventory.
   *
   * @param name unique process-equipment name
   * @param initialGas initial uniform equilibrium gas composition, temperature and pressure
   * @param lengthM pipe length in m
   * @param diameterM internal diameter in m
   * @param dischargeCoefficient effective full-bore area factor in (0,1]
   * @param backPressurePa constant absolute receiver pressure in Pa
   * @param darcyFrictionFactor specified Darcy friction factor in [0,1]
   * @param cells finite-volume cell count, at least 8
   * @param cfl Courant number in (0,0.9]
   */
  public IdealGasPipeDecompression(String name, SystemInterface initialGas, double lengthM, double diameterM,
      double dischargeCoefficient, double backPressurePa, double darcyFrictionFactor, int cells, double cfl) {
    super(name);
    Objects.requireNonNull(initialGas, "initialGas");
    this.lengthM = ReleaseFlowRequest.positive(lengthM, "lengthM");
    this.diameterM = ReleaseFlowRequest.positive(diameterM, "diameterM");
    this.dischargeCoefficient = ReleaseFlowRequest.positive(dischargeCoefficient, "dischargeCoefficient");
    this.backPressurePa = ReleaseFlowRequest.positive(backPressurePa, "backPressurePa");
    this.darcyFrictionFactor = ReleaseFlowRequest.nonnegative(darcyFrictionFactor, "darcyFrictionFactor");
    if (dischargeCoefficient > 1.0 || darcyFrictionFactor > 1.0 || cells < 8 || cells > 2000 || !Double.isFinite(cfl)
        || cfl <= 0.0 || cfl > 0.9) {
      throw new IllegalArgumentException("Invalid decompression discretization or geometry");
    }
    this.cfl = cfl;
    compositionReference = initialGas.clone();
    new ThermodynamicOperations(compositionReference).TPflash();
    compositionReference.init(3);
    requirePerfectGasState(compositionReference);
    initialPressurePa = ReleaseFlowRequest.positive(compositionReference.getPressure("Pa"), "initial pressure");
    initialTemperatureK = ReleaseFlowRequest.positive(compositionReference.getTemperature(), "initial temperature");
    if (initialPressurePa < backPressurePa) {
      throw new IllegalArgumentException("Initial pressure must not be below receiver pressure");
    }
    gamma = finite(compositionReference.getGamma(), "heat-capacity ratio");
    if (gamma <= 1.0 || gamma > 2.0) {
      throw new IllegalArgumentException("Perfect-gas heat-capacity ratio must be in (1,2]");
    }
    gasConstantJkgK = GAS_CONSTANT
        / ReleaseFlowRequest.positive(compositionReference.getMolarMass(), "mixture molar mass");
    cvJkgK = gasConstantJkgK / (gamma - 1.0);
    cpJkgK = gamma * cvJkgK;
    referenceEntropyJkgK = finite(compositionReference.getEntropy("J/kgK"), "reference entropy");
    areaM2 = Math.PI * diameterM * diameterM / 4.0;
    cellVolumeM3 = areaM2 * lengthM / cells;
    density = new double[cells];
    momentum = new double[cells];
    energy = new double[cells];
    double initialDensity = initialPressurePa / (gasConstantJkgK * initialTemperatureK);
    double initialEnergyDensity = initialPressurePa / (gamma - 1.0);
    Arrays.fill(density, initialDensity);
    Arrays.fill(energy, initialEnergyDensity);
    massFractions = componentMassFractions(compositionReference);
    initialMassKg = totalMass(density);
    initialEnergyJ = totalEnergy(energy);
    currentResult = result(density, momentum, energy, 0.0);
    setCalculateSteadyState(false);
  }

  /** Commits a stationary source snapshot without changing line-pack state. */
  @Override
  public synchronized void run(UUID id) {
    Objects.requireNonNull(id, "id");
    Flux outlet = boundaryFlux(density, momentum, energy);
    lastMassFlowKgS = outlet.mass * areaM2;
    lastEnergyFlowW = outlet.energy * areaM2;
    currentResult = result(density, momentum, energy, outlet.mass);
    setCalculationIdentifier(id);
  }

  /**
   * Advances the finite-volume state atomically with internally selected CFL substeps.
   *
   * @param dt caller timestep in s
   * @param id successful calculation identity
   */
  @Override
  public synchronized void runTransient(double dt, UUID id) {
    ReleaseFlowRequest.positive(dt, "dt");
    Objects.requireNonNull(id, "id");
    if (getCalculateSteadyState()) {
      throw new IllegalArgumentException("IdealGasPipeDecompression requires dynamic mode");
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
    double candidateReleasedMass = releasedMassKg;
    double candidateReleasedEnergy = releasedEnergyJ;
    double elapsed = 0.0;
    int substeps = 0;
    Flux outlet = boundaryFlux(candidateDensity, candidateMomentum, candidateEnergy);
    while (elapsed < dt) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException("PIPE_DECOMPRESSION_INTERRUPTED");
      }
      if (++substeps > MAX_SUBSTEPS) {
        throw new IllegalStateException("PIPE_DECOMPRESSION_SUBSTEP_LIMIT");
      }
      double step = Math.min(dt - elapsed, stableStep(candidateDensity, candidateMomentum, candidateEnergy));
      if (!(step > 0.0) || elapsed + step <= elapsed) {
        throw new IllegalStateException("PIPE_DECOMPRESSION_TIMESTEP_UNREPRESENTABLE");
      }
      Step next = advance(candidateDensity, candidateMomentum, candidateEnergy, step);
      candidateDensity = next.density;
      candidateMomentum = next.momentum;
      candidateEnergy = next.energy;
      candidateReleasedMass += next.outlet.mass * areaM2 * step;
      candidateReleasedEnergy += next.outlet.energy * areaM2 * step;
      outlet = next.outlet;
      elapsed += step;
    }
    close(totalMass(candidateDensity) + candidateReleasedMass, initialMassKg, initialMassKg,
        "PIPE_TOTAL_MASS_CLOSURE_FAILED");
    close(totalEnergy(candidateEnergy) + candidateReleasedEnergy, initialEnergyJ, initialEnergyJ,
        "PIPE_TOTAL_ENERGY_CLOSURE_FAILED");
    density = candidateDensity;
    momentum = candidateMomentum;
    energy = candidateEnergy;
    releasedMassKg = candidateReleasedMass;
    releasedEnergyJ = candidateReleasedEnergy;
    lastMassFlowKgS = outlet.mass * areaM2;
    lastEnergyFlowW = outlet.energy * areaM2;
    lastSubsteps = substeps;
    lastTransientId = id;
    lastTransientDurationS = dt;
    currentResult = result(density, momentum, energy, outlet.mass);
    setTime(getTime() + dt);
    setCalculationIdentifier(id);
  }

  private Step advance(double[] rho, double[] mom, double[] totalEnergy, double dt) {
    int cells = rho.length;
    Flux[] faces = new Flux[cells + 1];
    faces[0] = flux(state(rho[0], -mom[0], totalEnergy[0]), state(rho[0], mom[0], totalEnergy[0]));
    for (int face = 1; face < cells; face++) {
      faces[face] = flux(state(rho[face - 1], mom[face - 1], totalEnergy[face - 1]),
          state(rho[face], mom[face], totalEnergy[face]));
    }
    faces[cells] = boundaryFlux(rho, mom, totalEnergy);
    double dx = lengthM / cells;
    double scale = dt / dx;
    double[] nextRho = new double[cells];
    double[] nextMom = new double[cells];
    double[] nextEnergy = new double[cells];
    for (int cell = 0; cell < cells; cell++) {
      nextRho[cell] = rho[cell] - scale * (faces[cell + 1].mass - faces[cell].mass);
      nextMom[cell] = mom[cell] - scale * (faces[cell + 1].momentum - faces[cell].momentum);
      nextEnergy[cell] = totalEnergy[cell] - scale * (faces[cell + 1].energy - faces[cell].energy);
      double velocity = nextMom[cell] / nextRho[cell];
      nextMom[cell] -= dt * darcyFrictionFactor * nextRho[cell] * velocity * Math.abs(velocity) / (2.0 * diameterM);
      state(nextRho[cell], nextMom[cell], nextEnergy[cell]);
    }
    return new Step(nextRho, nextMom, nextEnergy, faces[cells]);
  }

  private double stableStep(double[] rho, double[] mom, double[] totalEnergy) {
    double maximumSignal = 0.0;
    for (int cell = 0; cell < rho.length; cell++) {
      State value = state(rho[cell], mom[cell], totalEnergy[cell]);
      maximumSignal = Math.max(maximumSignal, Math.abs(value.velocity) + value.soundSpeed);
    }
    return cfl * lengthM / rho.length / ReleaseFlowRequest.positive(maximumSignal, "maximum signal speed");
  }

  private Flux boundaryFlux(double[] rho, double[] mom, double[] totalEnergy) {
    int last = rho.length - 1;
    State inside = state(rho[last], mom[last], totalEnergy[last]);
    if (!releaseEnabled || inside.pressure <= backPressurePa * (1.0 + 1.0e-10)) {
      return flux(state(rho[last], mom[last], totalEnergy[last]), state(rho[last], -mom[last], totalEnergy[last]));
    }
    double receiverDensity = backPressurePa / (gasConstantJkgK * inside.temperature);
    State receiver = state(receiverDensity, 0.0, backPressurePa / (gamma - 1.0));
    Flux candidate = flux(inside, receiver);
    if (candidate.mass <= 0.0 || candidate.energy <= 0.0) {
      return new Flux(0.0, inside.pressure, 0.0);
    }
    return new Flux(candidate.mass * dischargeCoefficient, candidate.momentum * dischargeCoefficient,
        candidate.energy * dischargeCoefficient);
  }

  private Flux flux(State left, State right) {
    double waveSpeed = Math.max(Math.abs(left.velocity) + left.soundSpeed, Math.abs(right.velocity) + right.soundSpeed);
    double leftEnergyFlux = (left.energy + left.pressure) * left.velocity;
    double rightEnergyFlux = (right.energy + right.pressure) * right.velocity;
    return new Flux(0.5 * (left.momentum + right.momentum - waveSpeed * (right.density - left.density)),
        0.5 * (left.momentum * left.velocity + left.pressure + right.momentum * right.velocity + right.pressure
            - waveSpeed * (right.momentum - left.momentum)),
        0.5 * (leftEnergyFlux + rightEnergyFlux - waveSpeed * (right.energy - left.energy)));
  }

  private State state(double rho, double mom, double totalEnergy) {
    if (!Double.isFinite(rho) || rho <= 0.0 || !Double.isFinite(mom) || !Double.isFinite(totalEnergy)) {
      throw new IllegalStateException("PIPE_DECOMPRESSION_NONPHYSICAL_CONSERVATIVE_STATE");
    }
    double velocity = mom / rho;
    double internalEnergyDensity = totalEnergy - 0.5 * mom * velocity;
    double pressure = (gamma - 1.0) * internalEnergyDensity;
    double temperature = pressure / (rho * gasConstantJkgK);
    if (!Double.isFinite(pressure) || pressure <= 0.0 || !Double.isFinite(temperature) || temperature <= 0.0) {
      throw new IllegalStateException("PIPE_DECOMPRESSION_NONPOSITIVE_PRESSURE_OR_TEMPERATURE");
    }
    return new State(rho, mom, totalEnergy, velocity, pressure, temperature, Math.sqrt(gamma * pressure / rho));
  }

  private ReleaseFlowResult result(double[] rho, double[] mom, double[] totalEnergy, double outletMassFlux) {
    int last = rho.length - 1;
    State exit = state(rho[last], mom[last], totalEnergy[last]);
    double positiveFlux = releaseEnabled ? Math.max(0.0, outletMassFlux) : 0.0;
    double boundaryVelocity = positiveFlux / exit.density;
    double stagnationTemperature = exit.temperature + 0.5 * boundaryVelocity * boundaryVelocity / cpJkgK;
    double stagnationPressure = exit.pressure
        * Math.pow(stagnationTemperature / exit.temperature, gamma / (gamma - 1.0));
    double stagnationDensity = stagnationPressure / (gasConstantJkgK * stagnationTemperature);
    double stagnationEntropy = entropy(stagnationPressure, stagnationTemperature);
    ReleaseState upstream = ReleaseState.idealGas(compositionReference, stagnationPressure, stagnationTemperature,
        stagnationDensity, cpJkgK * stagnationTemperature, stagnationEntropy, 0.0);
    ReleaseState opening = ReleaseState.idealGas(compositionReference, exit.pressure, exit.temperature, exit.density,
        cpJkgK * exit.temperature, entropy(exit.pressure, exit.temperature), boundaryVelocity);
    Map<Station, ReleaseState> stations = new EnumMap<Station, ReleaseState>(Station.class);
    stations.put(Station.UPSTREAM_STAGNATION, upstream);
    stations.put(Station.THROAT_CRITICAL, opening);
    stations.put(Station.ORIFICE_EXIT, opening);
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("IDEAL_GAS_TRANSIENT_PIPE_ASSUMPTIONS",
        "One-dimensional adiabatic calorically perfect gas; closed upstream end and constant-pressure receiver"));
    diagnostics.add(new Diagnostic("FINITE_VOLUME_LINE_PACKING",
        "cells=" + rho.length + ", CFL=" + cfl + ", Darcy friction=" + darcyFrictionFactor));
    diagnostics.add(new Diagnostic("TRANSIENT_MODEL_UNQUALIFIED",
        "Numerical conservation and refinement do not establish facility qualification"));
    double soundSpeed = Math.sqrt(gamma * exit.pressure / exit.density);
    return ReleaseFlowResult.success(releaseModel, positiveFlux * areaM2, boundaryVelocity >= soundSpeed * 0.999,
        stations, diagnostics, soundSpeed, false);
  }

  private double entropy(double pressurePa, double temperatureK) {
    return referenceEntropyJkgK + cpJkgK * Math.log(temperatureK / initialTemperatureK)
        - gasConstantJkgK * Math.log(pressurePa / initialPressurePa);
  }

  private static void requirePerfectGasState(SystemInterface fluid) {
    if (fluid.isChemicalSystem() || fluid.isForcePhaseTypes() || fluid.doSolidPhaseCheck() || fluid.getHydrateCheck()
        || fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalArgumentException("Ideal-gas decompression requires one nonreacting equilibrium gas phase");
    }
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
      throw new IllegalStateException("PIPE_COMPOSITION_MASS_FRACTIONS_DO_NOT_CLOSE");
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

  private double totalEnergy(double[] totalEnergy) {
    double sum = 0.0;
    for (double value : totalEnergy) {
      sum += value * cellVolumeM3;
    }
    return sum;
  }

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
    return value;
  }

  private static void close(double actual, double expected, double scale, String code) {
    if (!Double.isFinite(actual) || Math.abs(actual - expected) > CLOSURE_TOLERANCE * Math.max(1.0, Math.abs(scale))) {
      throw new IllegalStateException(code + ": residual=" + (actual - expected));
    }
  }

  /** @return outlet-adjacent thermodynamic snapshot */
  @Override
  public synchronized SystemInterface getFluid() {
    State outlet = state(density[density.length - 1], momentum[momentum.length - 1], energy[energy.length - 1]);
    SystemInterface fluid = compositionReference.clone();
    fluid.setPressure(outlet.pressure, "Pa");
    fluid.setTemperature(outlet.temperature);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    requirePerfectGasState(fluid);
    return fluid;
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
    return "COUPLED_1D_IDEAL_GAS_PIPE_DECOMPRESSION_LINE_PACKING";
  }

  /** @return immutable SI accounting and discretization provenance */
  @Override
  public synchronized Map<String, String> getReleaseProvenance() {
    Map<String, String> provenance = new LinkedHashMap<String, String>();
    provenance.put("pipeLengthM", Double.toString(lengthM));
    provenance.put("pipeDiameterM", Double.toString(diameterM));
    provenance.put("pipeCells", Integer.toString(density.length));
    provenance.put("pipeCfl", Double.toString(cfl));
    provenance.put("pipeDarcyFrictionFactor", Double.toString(darcyFrictionFactor));
    provenance.put("pipeIntegrator", "FINITE_VOLUME_LOCAL_LAX_FRIEDRICHS_V1");
    provenance.put("inventoryTimeS", Double.toString(getTime()));
    provenance.put("inventoryVolumeM3", Double.toString(areaM2 * lengthM));
    provenance.put("cumulativeReleasedMassKg", Double.toString(releasedMassKg));
    provenance.put("cumulativeReleasedTotalEnergyJ", Double.toString(releasedEnergyJ));
    provenance.put("instantaneousMassFlowKgS", Double.toString(lastMassFlowKgS));
    provenance.put("instantaneousTotalEnergyFlowW", Double.toString(lastEnergyFlowW));
    provenance.put("inventoryLastSubsteps", Integer.toString(lastSubsteps));
    provenance.put("rateTimeBasis", "INSTANTANEOUS_AT_FRAME_TIME");
    return Collections.unmodifiableMap(provenance);
  }

  /** @return initial perfect-gas line-pack mass in kg */
  public double getInitialMassKg() {
    return initialMassKg;
  }

  /** @return current finite-volume line-pack mass in kg */
  public synchronized double getRemainingMassKg() {
    return totalMass(density);
  }

  /** @return cumulative discharged mass in kg */
  public synchronized double getReleasedMassKg() {
    return releasedMassKg;
  }

  /** @return initial conservative total energy in J */
  public double getInitialEnergyJ() {
    return initialEnergyJ;
  }

  /** @return current conservative line-pack total energy in J */
  public synchronized double getRemainingEnergyJ() {
    return totalEnergy(energy);
  }

  /** @return cumulative discharged total energy in J */
  public synchronized double getReleasedEnergyJ() {
    return releasedEnergyJ;
  }

  /** @return current cell-centre absolute pressures in Pa, ordered closed end to release end */
  public synchronized double[] getPressureProfilePa() {
    double[] pressure = new double[density.length];
    for (int cell = 0; cell < pressure.length; cell++) {
      pressure[cell] = state(density[cell], momentum[cell], energy[cell]).pressure;
    }
    return pressure;
  }

  /** @return fixed overall component mass fractions in input order */
  public double[] getComponentMassFractions() {
    return massFractions.clone();
  }

  private static final class State {
    private final double density;
    private final double momentum;
    private final double energy;
    private final double velocity;
    private final double pressure;
    private final double temperature;
    private final double soundSpeed;

    private State(double density, double momentum, double energy, double velocity, double pressure, double temperature,
        double soundSpeed) {
      this.density = density;
      this.momentum = momentum;
      this.energy = energy;
      this.velocity = velocity;
      this.pressure = pressure;
      this.temperature = temperature;
      this.soundSpeed = soundSpeed;
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
    private final Flux outlet;

    private Step(double[] density, double[] momentum, double[] energy, Flux outlet) {
      this.density = density;
      this.momentum = momentum;
      this.energy = energy;
      this.outlet = outlet;
    }
  }

  private static final class TransientBoundaryModel implements ReleaseFlowModel {
    private static final long serialVersionUID = 1L;

    @Override
    public String getModelId() {
      return "ideal-gas-pipe-decompression";
    }

    @Override
    public String getModelVersion() {
      return "1.0.0";
    }

    @Override
    public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
      return ReleaseFlowResult.failure(this, true, "TRANSIENT_STATE_REQUIRED",
          "Use IdealGasPipeDecompression through native process transient execution");
    }

    @Override
    public ReleaseModelEvidence getEvidence() {
      return new ReleaseModelEvidence("ideal-gas-pipe-decompression:1.0.0",
          Arrays.asList("ONE_DIMENSIONAL_PIPE", "CALORICALLY_PERFECT_GAS", "TRANSIENT_LINE_PACKING",
              "CONSERVATIVE_MASS_MOMENTUM_TOTAL_ENERGY"),
          Arrays.asList("NO_REAL_GAS_PROPERTY_EVOLUTION", "NO_HEAT_TRANSFER", "NO_PIPE_ELASTICITY",
              "NO_UPSTREAM_VESSEL", "NO_MULTIPHASE_SLIP", "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
          Arrays.asList(
              new ReleaseModelEvidence.Record("ideal-gas-pipe-conservation", ReleaseModelEvidence.Type.CONSERVATION,
                  "src/test/java/neqsim/process/safety/release/IdealGasPipeDecompressionTest.java",
                  "Finite-volume line-pack plus discharged mass and total energy close at every committed step", false),
              new ReleaseModelEvidence.Record("ideal-gas-pipe-refinement", ReleaseModelEvidence.Type.NUMERICAL,
                  "src/test/java/neqsim/process/safety/release/IdealGasPipeDecompressionTest.java",
                  "Pressure-wave arrival and discharged mass are checked under spatial refinement", false)));
    }
  }
}
