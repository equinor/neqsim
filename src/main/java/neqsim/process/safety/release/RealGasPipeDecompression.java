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
 * Conservative one-dimensional EOS-backed gas-pipe decompression inventory.
 *
 * <p>
 * A first-order local Lax-Friedrichs finite-volume scheme advances mass, momentum and total energy. Every conservative
 * cell state is recovered with the caller-selected NeqSim EOS by a volume/internal-energy flash, so pressure,
 * temperature, density and acoustic speed evolve with the decompression. The pipe is rigid and adiabatic, initially
 * uniform, closed at its upstream end and connected at its downstream end to a constant-pressure receiver. Specified
 * Darcy friction acts only on momentum. The exact outlet mass and total-enthalpy flux is committed to the inventory and
 * exported to the source-term frame.
 * </p>
 *
 * <p>
 * The model requires one equilibrium gas phase with fixed composition. Phase appearance, reactions, forced phases,
 * solids and hydrate calculations fail closed. Heat transfer, pipe elasticity, upstream-vessel and two-sided-rupture
 * coupling, non-equilibrium slip, entrainment and solid-bearing transport are outside this model. Numerical validation
 * is not facility qualification.
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
  private final double dischargeCoefficient;
  private final double backPressurePa;
  private final double darcyFrictionFactor;
  private final double cfl;
  private final double[] massFractions;
  private final TransientBoundaryModel releaseModel = new TransientBoundaryModel();
  private double[] density;
  private double[] momentum;
  private double[] energy;
  private final double initialMassKg;
  private final double initialEnergyJ;
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
    super(name);
    Objects.requireNonNull(initialGas, "initialGas");
    this.lengthM = ReleaseFlowRequest.positive(lengthM, "lengthM");
    this.diameterM = ReleaseFlowRequest.positive(diameterM, "diameterM");
    this.dischargeCoefficient = ReleaseFlowRequest.positive(dischargeCoefficient, "dischargeCoefficient");
    this.backPressurePa = ReleaseFlowRequest.positive(backPressurePa, "backPressurePa");
    this.darcyFrictionFactor = ReleaseFlowRequest.nonnegative(darcyFrictionFactor, "darcyFrictionFactor");
    if (dischargeCoefficient > 1.0 || darcyFrictionFactor > 1.0 || cells < 8 || cells > 400 || !Double.isFinite(cfl)
        || cfl <= 0.0 || cfl > 0.7) {
      throw new IllegalArgumentException("Invalid decompression discretization or geometry");
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
    density = new double[cells];
    momentum = new double[cells];
    energy = new double[cells];
    double initialDensity = density(compositionReference);
    double initialInternalEnergy = finite(compositionReference.getInternalEnergy("J/kg"), "initial internal energy");
    Arrays.fill(density, initialDensity);
    Arrays.fill(energy, initialDensity * initialInternalEnergy);
    massFractions = componentMassFractions(compositionReference);
    initialMassKg = totalMass(density);
    initialEnergyJ = totalEnergy(energy);
    currentResult = result(cellStates(density, momentum, energy), 0.0);
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
    currentResult = result(states, outlet.mass);
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
    double candidateReleasedMass = releasedMassKg;
    double candidateReleasedEnergy = releasedEnergyJ;
    double elapsed = 0.0;
    int substeps = 0;
    CellState[] states = cellStates(candidateDensity, candidateMomentum, candidateEnergy);
    Flux outlet = boundaryFlux(states[states.length - 1]);
    while (elapsed < dt) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException("REAL_GAS_PIPE_DECOMPRESSION_INTERRUPTED");
      }
      if (++substeps > MAX_SUBSTEPS) {
        throw new IllegalStateException("REAL_GAS_PIPE_DECOMPRESSION_SUBSTEP_LIMIT");
      }
      double step = Math.min(dt - elapsed, stableStep(states));
      if (!(step > 0.0) || elapsed + step <= elapsed) {
        throw new IllegalStateException("REAL_GAS_PIPE_DECOMPRESSION_TIMESTEP_UNREPRESENTABLE");
      }
      Step next = advance(candidateDensity, candidateMomentum, candidateEnergy, states, step);
      candidateDensity = next.density;
      candidateMomentum = next.momentum;
      candidateEnergy = next.energy;
      candidateReleasedMass += next.outlet.mass * areaM2 * step;
      candidateReleasedEnergy += next.outlet.energy * areaM2 * step;
      outlet = next.outlet;
      states = next.states;
      elapsed += step;
    }
    close(totalMass(candidateDensity) + candidateReleasedMass, initialMassKg, initialMassKg,
        "REAL_GAS_PIPE_TOTAL_MASS_CLOSURE_FAILED");
    close(totalEnergy(candidateEnergy) + candidateReleasedEnergy, initialEnergyJ, initialEnergyJ,
        "REAL_GAS_PIPE_TOTAL_ENERGY_CLOSURE_FAILED");
    ReleaseFlowResult candidateResult = result(states, outlet.mass);
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
    currentResult = candidateResult;
    setTime(getTime() + dt);
    setCalculationIdentifier(id);
  }

  private Step advance(double[] rho, double[] mom, double[] totalEnergy, CellState[] states, double dt) {
    int cells = rho.length;
    Flux[] faces = new Flux[cells + 1];
    faces[0] = flux(states[0].reflected(), states[0]);
    for (int face = 1; face < cells; face++) {
      faces[face] = flux(states[face - 1], states[face]);
    }
    faces[cells] = boundaryFlux(states[cells - 1]);
    double scale = dt / (lengthM / cells);
    double[] nextRho = new double[cells];
    double[] nextMom = new double[cells];
    double[] nextEnergy = new double[cells];
    for (int cell = 0; cell < cells; cell++) {
      nextRho[cell] = rho[cell] - scale * (faces[cell + 1].mass - faces[cell].mass);
      nextMom[cell] = mom[cell] - scale * (faces[cell + 1].momentum - faces[cell].momentum);
      nextEnergy[cell] = totalEnergy[cell] - scale * (faces[cell + 1].energy - faces[cell].energy);
      double velocity = nextMom[cell] / nextRho[cell];
      nextMom[cell] -= dt * darcyFrictionFactor * nextRho[cell] * velocity * Math.abs(velocity) / (2.0 * diameterM);
    }
    CellState[] nextStates = cellStates(nextRho, nextMom, nextEnergy);
    return new Step(nextRho, nextMom, nextEnergy, nextStates, faces[cells]);
  }

  private double stableStep(CellState[] states) {
    double maximumSignal = 0.0;
    for (CellState state : states) {
      maximumSignal = Math.max(maximumSignal, Math.abs(state.velocity) + state.soundSpeed);
    }
    return cfl * lengthM / states.length / ReleaseFlowRequest.positive(maximumSignal, "maximum signal speed");
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

  private ReleaseFlowResult result(CellState[] states, double outletMassFlux) {
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
    CellState inlet = states[0];
    CellState exit = states[states.length - 1];
    double positiveFlux = releaseEnabled ? Math.max(0.0, outletMassFlux) : 0.0;
    double boundaryVelocity = positiveFlux / exit.density;
    Map<Station, ReleaseState> stations = new EnumMap<Station, ReleaseState>(Station.class);
    stations.put(Station.UPSTREAM_STAGNATION, ReleaseState.fromFluid(inlet.fluid, inlet.velocity));
    ReleaseState opening = ReleaseState.fromFluid(exit.fluid, boundaryVelocity);
    stations.put(Station.THROAT_CRITICAL, opening);
    stations.put(Station.ORIFICE_EXIT, opening);
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("REAL_GAS_TRANSIENT_PIPE_ASSUMPTIONS",
        "One-dimensional rigid adiabatic equilibrium gas; closed upstream end and constant-pressure receiver"));
    diagnostics.add(new Diagnostic("EOS_FINITE_VOLUME_LINE_PACKING", "EOS=" + compositionReference.getModelName()
        + ", cells=" + states.length + ", CFL=" + cfl + ", Darcy friction=" + darcyFrictionFactor));
    diagnostics.add(new Diagnostic("SOLID_RISK_ASSESSED",
        "Mixture-specific equilibrium-solid and hydrate checks passed for all " + states.length + " cells"));
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

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " must be finite");
    }
    return value;
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
    return "COUPLED_1D_REAL_GAS_PIPE_DECOMPRESSION_LINE_PACKING";
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
    provenance.put("pipeIntegrator", "EOS_VU_FINITE_VOLUME_LOCAL_LAX_FRIEDRICHS_V1");
    provenance.put("pipeThermodynamicModel", compositionReference.getModelName());
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

  /** @return initial EOS line-pack mass in kg */
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
    private final CellState[] states;
    private final Flux outlet;

    private Step(double[] density, double[] momentum, double[] energy, CellState[] states, Flux outlet) {
      this.density = density;
      this.momentum = momentum;
      this.energy = energy;
      this.states = states;
      this.outlet = outlet;
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
      return "1.0.0";
    }

    @Override
    public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
      return ReleaseFlowResult.failure(this, true, "TRANSIENT_STATE_REQUIRED",
          "Use RealGasPipeDecompression through native process transient execution");
    }

    @Override
    public ReleaseModelEvidence getEvidence() {
      return new ReleaseModelEvidence("real-gas-pipe-decompression:1.0.0",
          Arrays.asList("ONE_DIMENSIONAL_PIPE", "SINGLE_EQUILIBRIUM_GAS", "EOS_PROPERTY_EVOLUTION",
              "TRANSIENT_LINE_PACKING", "CONSERVATIVE_MASS_MOMENTUM_TOTAL_ENERGY", "MIXTURE_SOLID_RISK_ASSESSED"),
          Arrays.asList("NO_HEAT_TRANSFER", "NO_PIPE_ELASTICITY", "NO_UPSTREAM_VESSEL", "NO_MULTIPHASE_SLIP",
              "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
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
                  "Discharged mass and closed-end pressure are checked under spatial refinement", false)));
    }
  }
}
