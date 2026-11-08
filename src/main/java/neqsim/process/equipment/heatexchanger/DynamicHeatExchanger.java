package neqsim.process.equipment.heatexchanger;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.mathlib.linearalgebra.EjmlLinearAlgebra;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSeawater;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Conservative single-phase exchanger with two mixed fluid inventories and a metal wall.
 *
 * <p>
 * Both steady and transient execution solve the same lumped enthalpy equations. Backward Euler uses actual
 * thermodynamic enthalpy, rather than Cp times a temperature increment, and reports fluid plus wall accumulation.
 * Pressure, composition and property package remain fixed on each side, with positive throughflow. Initial holdup
 * volumes define fixed masses; thermal expansion, pressure dynamics, phase change and distributed counterflow are
 * outside this model. Film coefficients and area determine heat transfer; the inherited steady NTU/UA specification is
 * not used.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DynamicHeatExchanger extends HeatExchanger
    implements Cloneable, TransientStateParticipant<DynamicHeatExchanger.Snapshot> {
  private static final long serialVersionUID = 1000L;
  /** Stable identity of this live transaction participant. */
  private String transientIdentity = UUID.randomUUID().toString();
  /** Last committed inventory, independent of connected inlet flow magnitudes. */
  private ThermalState thermalState;
  /** Start state retained for repeated evaluations of one physical interval. */
  private ThermalState stepStart;
  /** Last physical interval identity. */
  private UUID lastPhysicalId;
  /** Duration of the retained interval in seconds. */
  private volatile double lastStepSeconds;
  /** Clock at the retained interval start. */
  private volatile double stepStartTime;
  /** Last accepted thermal first-law receipt. */
  private Balance lastBalance;

  /**
   * Creates a two-stream exchanger; configure wall, films, area and nominal volumes before running.
   *
   * @param name equipment tag
   * @param first first fluid circuit
   * @param second independent second fluid circuit
   */
  public DynamicHeatExchanger(String name, StreamInterface first, StreamInterface second) {
    super(name, first, second);
    removeUnconnectedEnergyPort("heatDuty");
    setDynamicModelEnabled(true);
    setCalculateSteadyState(false);
  }

  /** {@inheritDoc} */
  @Override
  public DynamicHeatExchanger clone() {
    DynamicHeatExchanger result = SerializationUtils.clone(this);
    result.transientIdentity = UUID.randomUUID().toString();
    return result;
  }

  /**
   * Solves the lumped steady equations and initializes the nominal-mass inventories without advancing time.
   *
   * @param id calculation identity
   * @throws IllegalArgumentException if configuration or inlet states are unsupported
   * @throws IllegalStateException if the nonlinear thermal balance does not converge
   */
  @Override
  public void run(UUID id) {
    validateThermalConfiguration();
    SystemInterface[] fluids = inletWorkspaces();
    ThermalState initial = initialState(fluids);
    Evaluation result = solve(fluids, initial, Double.POSITIVE_INFINITY);
    double[] mass = new double[] {getShellHoldupVolume() * result.fluids[0].getDensity("kg/m3"),
        getTubeHoldupVolume() * result.fluids[1].getDensity("kg/m3")};
    ThermalState initialized = new ThermalState(result.temperatures, result.enthalpies, mass, initial.signatures,
        structuralConfiguration());
    publish(result, id);
    thermalState = initialized;
    stepStart = null;
    lastPhysicalId = null;
    lastBalance = null;
  }

  /**
   * Advances one implicit interval, or refines the same UUID from its retained start state.
   *
   * @param dt finite positive timestep in seconds
   * @param id non-null physical timestep identity; use a new identity for the next interval
   * @throws IllegalArgumentException if inputs, identity or fixed-inventory assumptions are invalid
   * @throws IllegalStateException if the nonlinear thermal balance does not converge
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!Double.isFinite(dt) || dt <= 0.0 || id == null) {
      throw new IllegalArgumentException("A finite positive timestep and physical UUID are required");
    }
    validateThermalConfiguration();
    SystemInterface[] fluids = inletWorkspaces();
    boolean repeated = id.equals(lastPhysicalId);
    if (repeated && Double.compare(dt, lastStepSeconds) != 0) {
      throw new IllegalArgumentException("One physical UUID cannot identify different interval durations");
    }
    ThermalState start = repeated ? stepStart : thermalState;
    if (start == null) {
      start = initialState(fluids);
    }
    requireSameInventory(start, fluids);
    Evaluation result = solve(fluids, start, dt);
    double oldEnergy = start.energy();
    ThermalState accepted = new ThermalState(result.temperatures, result.enthalpies, start.mass, start.signatures,
        start.structure);
    double boundaryPower = 0.0;
    for (int side = 0; side < 2; side++) {
      boundaryPower += getInStream(side).getFlowRate("kg/sec")
          * (fluids[side].getEnthalpy("J/kg") - result.enthalpies[side]);
    }
    Balance receipt = new Balance(oldEnergy, accepted.energy(), dt * boundaryPower, dt);
    double startTime = repeated ? stepStartTime : getTime();
    if (!Double.isFinite(startTime + dt) || !Double.isFinite(receipt.getResidualJ())) {
      throw new IllegalArgumentException("Thermal interval overflows the finite clock or first-law receipt");
    }
    publish(result, id);
    thermalState = accepted;
    stepStart = start;
    lastPhysicalId = id;
    lastStepSeconds = dt;
    stepStartTime = startTime;
    lastBalance = receipt;
    setTime(startTime + dt);
  }

  /**
   * Returns the last accepted interval's thermal enthalpy accumulation receipt.
   *
   * @return immutable receipt, or null before the first transient interval
   */
  public Balance getLastThermalBalance() {
    return lastBalance;
  }

  /**
   * Returns fluid-plus-wall thermal inventory on this constant-pressure model basis.
   *
   * @return thermal inventory in J, or NaN before initialization
   */
  public double getThermalInventoryEnergyJ() {
    return thermalState == null ? Double.NaN : thermalState.energy();
  }

  /** {@inheritDoc} */
  @Override
  public double getDuty(String unit) {
    return new neqsim.util.unit.PowerUnit(getDuty(), "W").getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result = super.validateSetup();
    try {
      validateThermalConfiguration();
    } catch (IllegalArgumentException ex) {
      result.addError("thermal configuration", ex.getMessage(),
          "Configure positive wall mass/Cp, films and area, nonnegative fluid volumes and closed thermal dynamics");
    }
    return result;
  }

  /**
   * Reads the physical thermal model inputs for a coordinated P&amp;ID/DEXPI handoff.
   *
   * @return detached parameter map with explicit units and model scope
   */
  public Map<String, Object> getThermalConfiguration() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("model", "SINGLE_PHASE_FIXED_PRESSURE_TWO_MIXED_FLUIDS_AND_WALL");
    values.put("integrator", "BACKWARD_EULER_ENTHALPY_NEWTON_EJML");
    values.put("wallMassKg", getWallMass());
    values.put("wallCpJPerKgK", getWallCp());
    values.put("firstNominalVolumeM3", getShellHoldupVolume());
    values.put("secondNominalVolumeM3", getTubeHoldupVolume());
    values.put("firstFilmWPerM2K", getShellSideHtc());
    values.put("secondFilmWPerM2K", getTubeSideHtc());
    values.put("areaM2", getHeatTransferArea());
    return values;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    return "equipment:dynamic-heat-exchanger:" + transientIdentity;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    String baseIssue = getBaseTransientStateCoverageIssue();
    if (baseIssue != null) {
      return baseIssue;
    }
    for (int side = 0; side < 2; side++) {
      if (getOutStream(side).getClass() != Stream.class) {
        return "Dynamic exchanger outlets require concrete, independently owned streams";
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Snapshot captureTransientState() {
    String issue = getTransientStateCoverageIssue();
    if (issue != null) {
      throw new IllegalStateException(issue);
    }
    return new Snapshot(this);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(Snapshot snapshot) {
    if (snapshot == null || !getTransientStateIdentity().equals(snapshot.identity)) {
      throw new IllegalArgumentException("Snapshot belongs to another exchanger");
    }
    restoreBaseTransientState(snapshot.base);
    setWallMass(snapshot.configuration[0]);
    setWallCp(snapshot.configuration[1]);
    setShellHoldupVolume(snapshot.configuration[2]);
    setTubeHoldupVolume(snapshot.configuration[3]);
    setShellSideHtc(snapshot.configuration[4]);
    setTubeSideHtc(snapshot.configuration[5]);
    setHeatTransferArea(snapshot.configuration[6]);
    setDynamicModelEnabled(snapshot.enabled);
    setWallTemperature(snapshot.wallTemperature);
    thermalState = snapshot.state;
    stepStart = snapshot.start;
    lastPhysicalId = snapshot.physicalId;
    lastStepSeconds = snapshot.stepSeconds;
    stepStartTime = snapshot.startTime;
    lastBalance = snapshot.balance;
    duty = snapshot.dutyW;
    for (int side = 0; side < 2; side++) {
      ((Stream) getOutStream(side)).restoreTransientState(snapshot.outlets[side]);
    }
  }

  /**
   * Validates physical parameters before any live state is mutated.
   *
   * @throws IllegalArgumentException if a thermal model prerequisite is absent
   */
  private void validateThermalConfiguration() {
    double[] positive = {getWallMass(), getWallCp(), getHeatTransferArea(), getShellSideHtc(), getTubeSideHtc()};
    for (double value : positive) {
      if (!Double.isFinite(value) || value <= 0.0) {
        throw new IllegalArgumentException("Wall mass/Cp, both film coefficients and area must be finite and positive");
      }
    }
    for (double volume : new double[] {getShellHoldupVolume(), getTubeHoldupVolume()}) {
      if (!Double.isFinite(volume) || volume < 0.0) {
        throw new IllegalArgumentException("Nominal fluid volumes must be finite and nonnegative");
      }
    }
    if (!isDynamicModelEnabled() || isSetEnergyStream() || !getEnergyPorts().isEmpty()) {
      throw new IllegalArgumentException("Closed two-fluid dynamics must be enabled without an external heat port");
    }
  }

  /**
   * Creates normalized, detached property workspaces so zero flow cannot erase thermal inventory.
   *
   * @return initialized independent inlet fluids at one kg/s
   */
  private SystemInterface[] inletWorkspaces() {
    SystemInterface[] result = new SystemInterface[2];
    for (int side = 0; side < 2; side++) {
      double flow = getInStream(side).getFlowRate("kg/sec");
      if (!Double.isFinite(flow) || flow <= 0.0) {
        throw new IllegalArgumentException("This thermal model requires finite positive inlet mass flow");
      }
      result[side] = getInStream(side).getFluid().clone();
      result[side].setTotalFlowRate(1.0, "kg/sec");
      flashSinglePhase(result[side]);
    }
    return result;
  }

  /**
   * Initializes nominal masses and the explicitly configured or mean wall temperature.
   *
   * @param fluids initialized inlet property workspaces
   * @return detached starting inventory
   */
  private ThermalState initialState(SystemInterface[] fluids) {
    double first = fluids[0].getTemperature();
    double second = fluids[1].getTemperature();
    double wall = Double.isNaN(getWallTemperature()) ? 0.5 * (first + second) : getWallTemperature();
    if (!Double.isFinite(wall) || wall <= 0.0) {
      throw new IllegalArgumentException("Initial wall temperature must be finite and positive");
    }
    return new ThermalState(new double[] {first, second, wall},
        new double[] {fluids[0].getEnthalpy("J/kg"), fluids[1].getEnthalpy("J/kg")},
        new double[] {getShellHoldupVolume() * fluids[0].getDensity("kg/m3"),
            getTubeHoldupVolume() * fluids[1].getDensity("kg/m3")},
        new String[] {signature(fluids[0]), signature(fluids[1])}, structuralConfiguration());
  }

  /**
   * Rejects inventory-model changes that would otherwise silently create energy or components.
   *
   * @param start retained starting state
   * @param fluids current detached inlet fluids
   */
  private void requireSameInventory(ThermalState start, SystemInterface[] fluids) {
    if (!Arrays.equals(start.structure, structuralConfiguration())) {
      throw new IllegalArgumentException("Wall/holdup configuration changed; reinitialize with run() before dynamics");
    }
    for (int side = 0; side < 2; side++) {
      if (!start.signatures[side].equals(signature(fluids[side]))) {
        throw new IllegalArgumentException(
            "Pressure, composition, salinity or property package changed during dynamics");
      }
    }
  }

  /**
   * Captures invariant wall capacity and nominal fluid-volume inputs.
   *
   * @return detached invariant configuration
   */
  private double[] structuralConfiguration() {
    return new double[] {getWallMass(), getWallCp(), getShellHoldupVolume(), getTubeHoldupVolume()};
  }

  /**
   * Identifies a supported fixed-composition, fixed-pressure property basis.
   *
   * @param fluid normalized initialized fluid
   * @return deterministic identity excluding temperature and throughput
   */
  private static String signature(SystemInterface fluid) {
    Class<?> type = fluid.getClass();
    if (type != neqsim.thermo.system.SystemSrkEos.class && type != neqsim.thermo.system.SystemPrEos.class
        && type != neqsim.thermo.system.SystemWaterIF97.class && type != SystemSeawater.class) {
      throw new IllegalArgumentException("Audited thermal fluids are SRK, PR, reference IF97 water and seawater");
    }
    StringBuilder value = new StringBuilder(fluid.getClass().getName());
    value.append('|').append(fluid.getPressure("bara")).append('|').append(fluid.getModelName()).append('|')
        .append(fluid.getMixingRule());
    if (fluid instanceof SystemSeawater) {
      value.append('|').append(((SystemSeawater) fluid).getSalinity());
    }
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      ComponentInterface item = fluid.getComponent(component);
      value.append('|').append(item.getComponentName()).append(':').append(Math.rint(item.getz() * 1e12) / 1e12)
          .append(':').append(item.getTC()).append(':').append(item.getPC()).append(':').append(item.getMolarMass())
          .append(':').append(item.getAcentricFactor()).append(':').append(item.getCpA()).append(':')
          .append(item.getCpB()).append(':').append(item.getCpC()).append(':').append(item.getCpD()).append(':')
          .append(item.getCpE());
      if (item.getAttractiveTerm() != null) {
        value.append(':').append(item.getAttractiveTerm().getClass().getName()).append(':')
            .append(item.getAttractiveTerm().getm());
        for (int parameter = 0; parameter < item.getAttractiveTerm().getNumberOfParameters(); parameter++) {
          value.append(':').append(item.getAttractiveTerm().getParameters(parameter));
        }
      }
    }
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      if (fluid.getPhase(phase) instanceof PhaseEosInterface) {
        EosMixingRulesInterface rule = ((PhaseEosInterface) fluid.getPhase(phase)).getEosMixingRule();
        if (rule == null) {
          continue;
        }
        String ruleClass = rule.getClass().getSimpleName();
        if (!"ClassicVdW".equals(ruleClass) && !"ClassicSRK".equals(ruleClass)) {
          throw new IllegalArgumentException("Thermal inventory currently supports classic EOS mixing rules only");
        }
        value.append('|').append(rule.getClass().getName()).append(':').append(rule.getName()).append(':')
            .append(rule.getBmixType());
        for (int first = 0; first < fluid.getNumberOfComponents(); first++) {
          for (int second = 0; second < fluid.getNumberOfComponents(); second++) {
            value.append(':').append(rule.getBinaryInteractionParameter(first, second)).append(':')
                .append(rule.getBinaryInteractionParameterT1(first, second));
          }
        }
      }
    }
    return value.toString();
  }

  /**
   * Solves both enthalpy inventories and the metal wall simultaneously with bounded Newton iterations.
   *
   * @param inlet normalized inlet property states
   * @param start interval starting inventory
   * @param dt timestep, or positive infinity for the lumped steady equations
   * @return fully converged detached thermal evaluation
   */
  private Evaluation solve(SystemInterface[] inlet, ThermalState start, double dt) {
    double[] temperature = start.temperature.clone();
    double minimum = Math.min(Math.min(temperature[0], temperature[1]), temperature[2]);
    double maximum = Math.max(Math.max(temperature[0], temperature[1]), temperature[2]);
    for (SystemInterface fluid : inlet) {
      minimum = Math.min(minimum, fluid.getTemperature());
      maximum = Math.max(maximum, fluid.getTemperature());
    }
    for (int iteration = 0; iteration < 40; iteration++) {
      Evaluation evaluated = evaluate(inlet, start, temperature, dt);
      if (evaluated.maximumResidual < 1.0e-5) {
        return evaluated;
      }
      double[] change = new EjmlLinearAlgebra().solve(evaluated.jacobian,
          new double[] {-evaluated.residual[0], -evaluated.residual[1], -evaluated.residual[2]});
      boolean accepted = false;
      for (double fraction = 1.0; fraction >= 1.0e-6; fraction *= 0.5) {
        double[] trial = new double[3];
        boolean bounded = true;
        for (int node = 0; node < 3; node++) {
          trial[node] = temperature[node] + fraction * change[node];
          bounded &= Double.isFinite(trial[node]) && trial[node] >= minimum - 1.0e-8 && trial[node] <= maximum + 1.0e-8;
        }
        if (bounded && evaluate(inlet, start, trial, dt).maximumResidual < evaluated.maximumResidual) {
          temperature = trial;
          accepted = true;
          break;
        }
      }
      if (!accepted) {
        throw new IllegalStateException("Bounded exchanger enthalpy solve failed to reduce its residual");
      }
    }
    throw new IllegalStateException("Exchanger enthalpy solve exceeded 40 iterations");
  }

  /**
   * Evaluates the nonlinear thermal first law and its exact local enthalpy derivative.
   *
   * @param inlet normalized inlet workspaces
   * @param start fixed interval starting state
   * @param temperature candidate fluid and wall temperatures in K
   * @param dt timestep, or positive infinity for steady state
   * @return residual, Jacobian and candidate fluids
   */
  private Evaluation evaluate(SystemInterface[] inlet, ThermalState start, double[] temperature, double dt) {
    SystemInterface[] fluids = new SystemInterface[2];
    double[] enthalpy = new double[2];
    double[] residual = new double[3];
    double[][] jacobian = new double[3][3];
    double[] conductance = {getShellSideHtc() * getHeatTransferArea(), getTubeSideHtc() * getHeatTransferArea()};
    double wallCapacity = getWallMass() * getWallCp();
    residual[2] = wallCapacity / dt * (temperature[2] - start.temperature[2]);
    jacobian[2][2] = wallCapacity / dt;
    for (int side = 0; side < 2; side++) {
      fluids[side] = inlet[side].clone();
      fluids[side].setTemperature(temperature[side]);
      flashSinglePhase(fluids[side]);
      enthalpy[side] = fluids[side].getEnthalpy("J/kg");
      double massFlow = getInStream(side).getFlowRate("kg/sec");
      double wallTransfer = conductance[side] * (temperature[side] - temperature[2]);
      residual[side] = start.mass[side] / dt * (enthalpy[side] - start.enthalpy[side])
          + massFlow * (enthalpy[side] - inlet[side].getEnthalpy("J/kg")) + wallTransfer;
      jacobian[side][side] = (start.mass[side] / dt + massFlow) * fluids[side].getCp("J/kgK") + conductance[side];
      jacobian[side][2] = -conductance[side];
      jacobian[2][side] = -conductance[side];
      jacobian[2][2] += conductance[side];
      residual[2] -= wallTransfer;
    }
    return new Evaluation(temperature, enthalpy, fluids, residual, jacobian);
  }

  /**
   * Flashes a detached property state and enforces the bounded single-phase thermal contract.
   *
   * @param fluid normalized detached fluid
   */
  private static void flashSinglePhase(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() != 1 || !Double.isFinite(fluid.getEnthalpy("J/kg"))
        || !Double.isFinite(fluid.getCp("J/kgK")) || fluid.getCp("J/kgK") <= 0.0
        || !Double.isFinite(fluid.getDensity("kg/m3")) || fluid.getDensity("kg/m3") <= 0.0) {
      throw new IllegalArgumentException("Thermal inventory requires a finite stable single-phase fluid");
    }
  }

  /**
   * Publishes both solved mixed outlets atomically after every flash has succeeded.
   *
   * @param result detached converged evaluation
   * @param id calculation identity
   */
  private void publish(Evaluation result, UUID id) {
    for (int side = 0; side < 2; side++) {
      result.fluids[side].setTotalFlowRate(getInStream(side).getFlowRate("kg/sec"), "kg/sec");
      // Rescaling flow reinitializes phase counts in reference systems. Reflash
      // the detached state instead of publishing duplicated phase properties.
      flashSinglePhase(result.fluids[side]);
    }
    for (int side = 0; side < 2; side++) {
      getOutStream(side).setThermoSystem(result.fluids[side]);
      getOutStream(side).setCalculationIdentifier(id);
    }
    setWallTemperature(result.temperatures[2]);
    duty = getShellSideHtc() * getHeatTransferArea() * (result.temperatures[0] - result.temperatures[2]);
    isSolved = true;
    setCalculationIdentifier(id);
  }

  /**
   * Immutable nonlinear solve workspace, never retained as live process state.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class Evaluation {
    private final double[] temperatures;
    private final double[] enthalpies;
    private final SystemInterface[] fluids;
    private final double[] residual;
    private final double[][] jacobian;
    private final double maximumResidual;

    /**
     * Stores one detached evaluation.
     *
     * @param temperatures candidate temperatures
     * @param enthalpies candidate specific enthalpies
     * @param fluids detached candidate fluids
     * @param residual thermal residuals in W
     * @param jacobian thermal derivative matrix
     */
    private Evaluation(double[] temperatures, double[] enthalpies, SystemInterface[] fluids, double[] residual,
        double[][] jacobian) {
      this.temperatures = temperatures.clone();
      this.enthalpies = enthalpies;
      this.fluids = fluids;
      this.residual = residual;
      this.jacobian = jacobian;
      maximumResidual = Math.max(Math.max(Math.abs(residual[0]), Math.abs(residual[1])), Math.abs(residual[2]));
    }
  }

  /**
   * Immutable fixed-mass thermal inventory.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ThermalState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double[] temperature;
    private final double[] enthalpy;
    private final double[] mass;
    private final String[] signatures;
    private final double[] structure;

    /**
     * Copies the thermal state and its fixed-inventory assumptions.
     *
     * @param temperature fluid and wall temperatures
     * @param enthalpy fluid specific enthalpies
     * @param mass nominal fixed fluid masses
     * @param signatures composition and pressure identities
     * @param structure wall and nominal-volume parameters
     */
    private ThermalState(double[] temperature, double[] enthalpy, double[] mass, String[] signatures,
        double[] structure) {
      this.temperature = temperature.clone();
      this.enthalpy = enthalpy.clone();
      this.mass = mass.clone();
      this.signatures = signatures.clone();
      this.structure = structure.clone();
    }

    /**
     * Calculates the total retained thermal inventory.
     *
     * @return fluid enthalpy inventory plus metal sensible energy in J
     */
    private double energy() {
      return mass[0] * enthalpy[0] + mass[1] * enthalpy[1] + structure[0] * structure[1] * temperature[2];
    }
  }

  /**
   * Immutable accepted-interval first-law evidence.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Balance implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double initialEnergyJ;
    private final double finalEnergyJ;
    private final double boundaryEnergyJ;
    private final double seconds;

    /**
     * Records actual inventory change against backward-Euler boundary quadrature.
     *
     * @param initial initial inventory in J
     * @param end final inventory in J
     * @param boundary net inlet-minus-outlet enthalpy over the interval in J
     * @param dt interval duration in seconds
     */
    private Balance(double initial, double end, double boundary, double dt) {
      initialEnergyJ = initial;
      finalEnergyJ = end;
      boundaryEnergyJ = boundary;
      seconds = dt;
    }

    /**
     * Reads the accepted inventory accumulation.
     *
     * @return accumulated fluid-plus-metal thermal energy change in J
     */
    public double getAccumulationJ() {
      return finalEnergyJ - initialEnergyJ;
    }

    /**
     * Reads integrated inlet-minus-outlet boundary power.
     *
     * @return net boundary energy entering the two circuits in J
     */
    public double getBoundaryEnergyJ() {
      return boundaryEnergyJ;
    }

    /**
     * Compares accumulation against net boundary energy.
     *
     * @return signed first-law residual in J
     */
    public double getResidualJ() {
      return getAccumulationJ() - boundaryEnergyJ;
    }

    /**
     * Exports a detached thermal balance receipt.
     *
     * @return unit-explicit first-law receipt
     */
    public Map<String, Object> toMap() {
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("initialThermalEnergyJ", initialEnergyJ);
      values.put("finalThermalEnergyJ", finalEnergyJ);
      values.put("boundaryEnergyJ", boundaryEnergyJ);
      values.put("accumulationJ", getAccumulationJ());
      values.put("residualJ", getResidualJ());
      values.put("timeStepSeconds", seconds);
      values.put("basis", "FIXED_MASS_CONSTANT_PRESSURE_ENTHALPY_INVENTORY_AND_METAL");
      return values;
    }
  }

  /**
   * Immutable snapshot preserving the live exchanger, outlet and measurement-reference identities.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Snapshot implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String identity;
    private final ProcessEquipmentTransientState base;
    private final ThermalState state;
    private final ThermalState start;
    private final UUID physicalId;
    private final double stepSeconds;
    private final double startTime;
    private final double[] configuration;
    private final double wallTemperature;
    private final double dutyW;
    private final boolean enabled;
    private final Balance balance;
    private final Stream.TransientState[] outlets;

    /**
     * Captures all fields changed by supported thermal execution and thermal-parameter setters.
     *
     * @param exchanger actual source participant
     */
    private Snapshot(DynamicHeatExchanger exchanger) {
      identity = exchanger.getTransientStateIdentity();
      base = exchanger.captureBaseTransientState();
      state = exchanger.thermalState;
      start = exchanger.stepStart;
      physicalId = exchanger.lastPhysicalId;
      stepSeconds = exchanger.lastStepSeconds;
      startTime = exchanger.stepStartTime;
      configuration = new double[] {exchanger.getWallMass(), exchanger.getWallCp(), exchanger.getShellHoldupVolume(),
          exchanger.getTubeHoldupVolume(), exchanger.getShellSideHtc(), exchanger.getTubeSideHtc(),
          exchanger.getHeatTransferArea()};
      wallTemperature = exchanger.getWallTemperature();
      dutyW = exchanger.duty;
      enabled = exchanger.isDynamicModelEnabled();
      balance = exchanger.lastBalance;
      outlets = new Stream.TransientState[] {((Stream) exchanger.getOutStream(0)).captureTransientState(),
          ((Stream) exchanger.getOutStream(1)).captureTransientState()};
    }
  }
}
