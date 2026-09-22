package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Rigid, adiabatic, well-mixed inventory with an explicitly selected withdrawal phase and short-opening release model.
 *
 * <p>
 * Native transient calls remove selected-phase composition and upstream stagnation enthalpy, then solve the remaining
 * EOS volume/internal-energy state. Explicit Euler substeps are bounded by the configured maximum duration and one
 * percent of current selected-phase mass. Every substep must close component, energy and volume balances. A failed call
 * leaves this unit's inventory, cumulative discharge, clock and calculation identity unchanged; the surrounding process
 * need not be transactional.
 * </p>
 *
 * <p>
 * The supplied fluid is cloned and scaled to the specified volume at its initial temperature and pressure. Its original
 * mole count is not treated as a vessel size or flow rate. The compatibility constructor retains the historical
 * single-gas restriction; the phase-selective constructor accepts equilibrium gas, oil, generic-liquid or aqueous
 * withdrawal from a fluid inventory containing only those phases. Reacting/forced phases, solids, hydrates, heat input,
 * inflow, entrainment, pipe decompression and non-equilibrium transfer are outside scope. A step crossing the receiving
 * pressure is located by bounded bisection and conservatively lands on the no-flow boundary while the process clock
 * advances through the caller's full timestep. Numerical closure does not confer engineering qualification.
 * </p>
 */
public final class ReleaseInventory extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1L;
  private static final double CLOSURE_TOLERANCE = 1e-7;
  private static final double PRESSURE_EVENT_RELATIVE_TOLERANCE = 1e-9;
  private static final int MAX_PRESSURE_EVENT_ITERATIONS = 64;
  private static final int MAX_SUBSTEPS = 10000;
  private final double volumeM3;
  private final double diameterM;
  private final double dischargeCoefficient;
  private final double backPressurePa;
  private final double maxSubstepS;
  private final ReleaseFlowModel releaseModel;
  private final PhaseType withdrawalPhaseType;
  private final boolean phaseSelective;
  private final Map<String, Double> initialComponentMassKg;
  private final double initialEnergyJ;
  private SystemInterface inventory;
  private double releasedMassKg;
  private double releasedEnergyJ;
  private Map<String, Double> releasedComponentMassKg = new TreeMap<String, Double>();
  private boolean releaseEnabled = true;
  private int lastSubsteps;
  private int lastVolumeEnergySolves;
  private UUID lastTransientId;
  private double lastTransientDurationS;
  private boolean lastPressureEquilibrationEvent;
  private double lastReleaseDurationS;

  /**
   * Creates an independently owned inventory, initially at time zero.
   *
   * @param name unique process equipment name
   * @param initialFluid initial gas composition, EOS, temperature in K and absolute pressure
   * @param volumeM3 fixed vessel volume in m3
   * @param diameterM physical opening diameter in m
   * @param dischargeCoefficient discharge coefficient in (0,1]
   * @param backPressurePa constant absolute receiving pressure in Pa
   * @param releaseModel caller-selected release model; screening results are rejected
   * @param maxSubstepS largest Euler substep in seconds, to be refined for convergence
   * @throws IllegalArgumentException for invalid configuration or unsupported initial state
   * @throws IllegalStateException if initialization or its closure checks fail
   */
  public ReleaseInventory(String name, SystemInterface initialFluid, double volumeM3, double diameterM,
      double dischargeCoefficient, double backPressurePa, ReleaseFlowModel releaseModel, double maxSubstepS) {
    this(name, initialFluid, volumeM3, diameterM, dischargeCoefficient, backPressurePa, releaseModel, maxSubstepS,
        PhaseType.GAS, false);
  }

  /**
   * Creates an independently owned equilibrium inventory with explicit phase withdrawal.
   *
   * <p>
   * The selected phase supplies the release-model upstream state, component withdrawal fractions and stagnation
   * enthalpy. The remaining total inventory is re-equilibrated at fixed volume and internal energy after every substep.
   * No entrainment, slip, interfacial transfer rate or fallback phase is inferred.
   * </p>
   *
   * @param name unique process equipment name
   * @param initialFluid initial equilibrium composition, EOS, temperature in K and absolute pressure
   * @param volumeM3 fixed vessel volume in m3
   * @param diameterM physical opening diameter in m
   * @param dischargeCoefficient discharge coefficient in (0,1]
   * @param backPressurePa constant absolute receiving pressure in Pa
   * @param releaseModel caller-selected release model; screening results are rejected
   * @param maxSubstepS largest Euler substep in seconds, to be refined for convergence
   * @param withdrawalPhaseType explicitly selected GAS, OIL, LIQUID or AQUEOUS inventory phase
   * @throws IllegalArgumentException for invalid configuration or unsupported initial state
   * @throws IllegalStateException if initialization or its closure checks fail
   */
  public ReleaseInventory(String name, SystemInterface initialFluid, double volumeM3, double diameterM,
      double dischargeCoefficient, double backPressurePa, ReleaseFlowModel releaseModel, double maxSubstepS,
      PhaseType withdrawalPhaseType) {
    this(name, initialFluid, volumeM3, diameterM, dischargeCoefficient, backPressurePa, releaseModel, maxSubstepS,
        withdrawalPhaseType, true);
  }

  private ReleaseInventory(String name, SystemInterface initialFluid, double volumeM3, double diameterM,
      double dischargeCoefficient, double backPressurePa, ReleaseFlowModel releaseModel, double maxSubstepS,
      PhaseType withdrawalPhaseType, boolean phaseSelective) {
    super(name);
    this.volumeM3 = ReleaseFlowRequest.positive(volumeM3, "volumeM3");
    this.maxSubstepS = ReleaseFlowRequest.positive(maxSubstepS, "maxSubstepS");
    this.releaseModel = Objects.requireNonNull(releaseModel, "releaseModel");
    this.withdrawalPhaseType = requireWithdrawalPhase(withdrawalPhaseType);
    this.phaseSelective = phaseSelective;
    ReleaseFlowRequest configuration = new ReleaseFlowRequest(initialFluid, diameterM, dischargeCoefficient,
        backPressurePa);
    this.diameterM = configuration.getDiameterM();
    this.dischargeCoefficient = configuration.getDischargeCoefficient();
    this.backPressurePa = configuration.getBackPressurePa();
    inventory = configuration.getFluid();
    requireInventory(inventory, false);
    new ThermodynamicOperations(inventory).TPflash();
    inventory.init(3);
    requireInventory(inventory, true);
    requireSelectedPhase(inventory, true);
    if (!phaseSelective) {
      requireSingleGas(inventory);
    }
    double initialVolume = ReleaseFlowRequest.positive(inventory.getVolume("m3"), "initial volume");
    inventory.setTotalNumberOfMoles(inventory.getTotalNumberOfMoles() * volumeM3 / initialVolume);
    new ThermodynamicOperations(inventory).TPflash();
    inventory.init(3);
    requireInventory(inventory, true);
    requireSelectedPhase(inventory, true);
    if (!phaseSelective) {
      requireSingleGas(inventory);
    }
    close(inventory.getVolume("m3"), volumeM3, volumeM3, "VOLUME_CLOSURE_FAILED");
    initialComponentMassKg = componentMasses(inventory);
    initialEnergyJ = finite(inventory.getInternalEnergy("J"), "initial internal energy");
    for (String component : initialComponentMassKg.keySet()) {
      releasedComponentMassKg.put(component, 0.0);
    }
    setCalculateSteadyState(false);
  }

  /**
   * Checks the current state without resetting or depleting inventory.
   *
   * @param id successful calculation identity
   * @throws IllegalStateException for an unusable or incompatible release calculation
   */
  @Override
  public synchronized void run(UUID id) {
    Objects.requireNonNull(id, "id");
    if (releaseEnabled) {
      calculate(inventory);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Advances the inventory atomically within this unit. No scalar or flash fallback is used.
   *
   * @param dt duration in seconds, finite and positive
   * @param id successful calculation identity
   * @throws IllegalArgumentException for invalid duration or steady-state mode
   * @throws IllegalStateException for model failure, phase/pressure boundary, excessive substeps, or failed
   * conservation; this unit remains at the pre-call state
   */
  @Override
  public synchronized void runTransient(double dt, UUID id) {
    ReleaseFlowRequest.positive(dt, "dt");
    Objects.requireNonNull(id, "id");
    if (getCalculateSteadyState()) {
      throw new IllegalArgumentException("ReleaseInventory requires dynamic mode");
    }
    if (id.equals(lastTransientId)) {
      if (dt != lastTransientDurationS) {
        throw new IllegalArgumentException("Repeated transient identity has a different duration");
      }
      return;
    }
    double targetTime = getTime() + dt;
    if (!Double.isFinite(targetTime) || targetTime <= getTime()) {
      throw new IllegalArgumentException("Unrepresentable inventory timestep");
    }
    SystemInterface candidate = inventory.clone();
    Map<String, Double> componentLoss = new TreeMap<String, Double>(releasedComponentMassKg);
    double massLoss = releasedMassKg;
    double energyLoss = releasedEnergyJ;
    double elapsed = 0.0;
    int steps = 0;
    int volumeEnergySolves = 0;
    boolean pressureEquilibrationEvent = false;
    double releaseDurationS = 0.0;
    while (releaseEnabled && elapsed < dt) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException("INVENTORY_STEP_INTERRUPTED");
      }
      if (++steps > MAX_SUBSTEPS) {
        throw new IllegalStateException("INVENTORY_SUBSTEP_LIMIT: reduce requested duration");
      }
      ReleaseFlowResult release = calculate(candidate);
      double rate = release.getMassFlowRateKgS();
      if (rate == 0.0) {
        break;
      }
      double selectedPhaseMass = selectedPhase(candidate).getMass("kg");
      double h = Math.min(Math.min(maxSubstepS, dt - elapsed), 0.01 * selectedPhaseMass / rate);
      if (!(h > 0.0) || elapsed + h <= elapsed) {
        throw new IllegalStateException("INVENTORY_TIMESTEP_UNREPRESENTABLE");
      }
      InventoryStep step = advance(candidate, rate, h);
      volumeEnergySolves += step.volumeEnergySolves;
      double pressurePa = step.next.getPressure() * 1e5;
      double pressureTolerance = Math.max(1e-3, backPressurePa * PRESSURE_EVENT_RELATIVE_TOLERANCE);
      boolean reachesPressureBoundary = pressurePa <= backPressurePa + pressureTolerance;
      if (pressurePa < backPressurePa) {
        PressureEvent located = locateReceivingPressureEvent(candidate, rate, h);
        step = located.step;
        volumeEnergySolves += located.additionalVolumeEnergySolves;
        pressurePa = step.next.getPressure() * 1e5;
        reachesPressureBoundary = true;
      }
      Map<String, Double> after = componentMasses(step.next);
      for (String component : step.beforeComponentMassKg.keySet()) {
        double loss = step.removedComponentMassKg.get(component);
        close(after.get(component) + loss, step.beforeComponentMassKg.get(component),
            step.beforeComponentMassKg.get(component), "COMPONENT_CLOSURE_FAILED");
        componentLoss.put(component, componentLoss.get(component) + loss);
      }
      massLoss = finite(massLoss + step.removedMassKg, "cumulative released mass");
      energyLoss = finite(energyLoss + step.outflowEnergyJ, "cumulative released enthalpy");
      elapsed += step.durationS;
      releaseDurationS = elapsed;
      candidate = step.next;
      if (reachesPressureBoundary) {
        close(pressurePa, backPressurePa, backPressurePa, "RECEIVING_PRESSURE_EVENT_FAILED");
        pressureEquilibrationEvent = true;
        break;
      }
    }
    // Check accumulated residuals too, so individually acceptable flash errors cannot drift silently.
    Map<String, Double> remaining = componentMasses(candidate);
    for (String component : initialComponentMassKg.keySet()) {
      close(remaining.get(component) + componentLoss.get(component), initialComponentMassKg.get(component),
          initialComponentMassKg.get(component), "CUMULATIVE_COMPONENT_CLOSURE_FAILED");
    }
    close(candidate.getInternalEnergy("J") + energyLoss, initialEnergyJ,
        Math.max(Math.abs(initialEnergyJ), Math.abs(energyLoss)), "CUMULATIVE_ENERGY_CLOSURE_FAILED");
    if (releaseEnabled) {
      calculate(candidate);
    }
    inventory = candidate;
    releasedComponentMassKg = componentLoss;
    releasedMassKg = massLoss;
    releasedEnergyJ = energyLoss;
    lastSubsteps = steps;
    lastVolumeEnergySolves = volumeEnergySolves;
    lastTransientId = id;
    lastTransientDurationS = dt;
    lastPressureEquilibrationEvent = pressureEquilibrationEvent;
    lastReleaseDurationS = releaseDurationS;
    setTime(targetTime);
    setCalculationIdentifier(id);
  }

  private InventoryStep advance(SystemInterface candidate, double rate, double durationS) {
    double beforeMass = candidate.getMass("kg");
    double removedMass = rate * durationS;
    SystemInterface withdrawal = selectedPhase(candidate);
    double withdrawalMass = withdrawal.getMass("kg");
    if (!(removedMass > 0.0) || removedMass >= withdrawalMass) {
      throw new IllegalStateException("INVENTORY_SELECTED_PHASE_OVERDRAW");
    }
    double outflowEnergy = removedMass * withdrawal.getEnthalpy("J/kg");
    double beforeEnergy = finite(candidate.getInternalEnergy("J"), "internal energy");
    double targetEnergy = finite(beforeEnergy - outflowEnergy, "target energy");
    Map<String, Double> beforeComponents = componentMasses(candidate);
    Map<String, Double> withdrawalComponents = componentMasses(withdrawal);
    Map<String, Double> removedComponents = new TreeMap<String, Double>();
    double[] remainingMoleFractions = new double[candidate.getNumberOfComponents()];
    double remainingMoles = 0.0;
    for (int component = 0; component < candidate.getNumberOfComponents(); component++) {
      String name = candidate.getComponent(component).getComponentName();
      double componentLoss = removedMass * withdrawalComponents.get(name) / withdrawalMass;
      double remainingMass = beforeComponents.get(name) - componentLoss;
      if (!Double.isFinite(remainingMass) || remainingMass < 0.0) {
        throw new IllegalStateException("INVENTORY_COMPONENT_OVERDRAW: " + name);
      }
      removedComponents.put(name, componentLoss);
      remainingMoleFractions[component] = remainingMass / candidate.getComponent(component).getMolarMass();
      remainingMoles += remainingMoleFractions[component];
    }
    ReleaseFlowRequest.positive(remainingMoles, "remaining inventory moles");
    SystemInterface next = candidate.clone();
    next.setTotalNumberOfMoles(remainingMoles);
    next.setMolarComposition(remainingMoleFractions);
    int solves = solveVolumeEnergy(next, targetEnergy, Math.max(Math.abs(beforeEnergy), Math.abs(outflowEnergy)));
    requireInventory(next, true);
    close(next.getVolume("m3"), volumeM3, volumeM3, "VOLUME_CLOSURE_FAILED");
    close(next.getInternalEnergy("J"), targetEnergy, Math.max(Math.abs(beforeEnergy), Math.abs(outflowEnergy)),
        "ENERGY_CLOSURE_FAILED");
    close(next.getMass("kg") + removedMass, beforeMass, beforeMass, "MASS_CLOSURE_FAILED");
    return new InventoryStep(next, beforeComponents, removedComponents, removedMass, outflowEnergy, durationS, solves);
  }

  private PressureEvent locateReceivingPressureEvent(SystemInterface candidate, double rate, double upperDurationS) {
    double lower = 0.0;
    double upper = upperDurationS;
    double pressureTolerance = Math.max(1e-3, backPressurePa * PRESSURE_EVENT_RELATIVE_TOLERANCE);
    int additionalSolves = 0;
    for (int iteration = 0; iteration < MAX_PRESSURE_EVENT_ITERATIONS; iteration++) {
      double duration = 0.5 * (lower + upper);
      if (!(duration > lower) || !(duration < upper)) {
        break;
      }
      InventoryStep trial = advance(candidate, rate, duration);
      additionalSolves += trial.volumeEnergySolves;
      double pressurePa = trial.next.getPressure() * 1e5;
      if (pressurePa >= backPressurePa) {
        lower = duration;
        if (pressurePa - backPressurePa <= pressureTolerance) {
          return new PressureEvent(trial, additionalSolves);
        }
      } else {
        upper = duration;
      }
    }
    throw new IllegalStateException(
        "RECEIVING_PRESSURE_EVENT_FAILED: bounded event solve did not reach pressure tolerance");
  }

  private ReleaseFlowResult calculate(SystemInterface fluid) {
    SystemInterface withdrawal = selectedPhase(fluid);
    ReleaseFlowResult result = releaseModel.calculate(request(fluid));
    if (result == null || !result.isUsable() || !releaseModel.getModelId().equals(result.getModelId())
        || !releaseModel.getModelVersion().equals(result.getModelVersion())) {
      throw new IllegalStateException("INVENTORY_RELEASE_FAILED: selected model returned no usable matching result");
    }
    for (Diagnostic diagnostic : result.getDiagnostics()) {
      if ("SCREENING_ONLY".equals(diagnostic.getCode()) || "UNRESOLVED_STATIONS".equals(diagnostic.getCode())) {
        throw new IllegalStateException("INVENTORY_SCREENING_UNSUPPORTED");
      }
    }
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    close(upstream.getPressurePa(), withdrawal.getPressure() * 1e5, upstream.getPressurePa(), "RELEASE_STATE_MISMATCH");
    close(upstream.getTemperatureK(), withdrawal.getTemperature(), withdrawal.getTemperature(),
        "RELEASE_STATE_MISMATCH");
    close(upstream.getEnthalpyJkg(), withdrawal.getEnthalpy("J/kg"), Math.abs(withdrawal.getEnthalpy("J/kg")),
        "RELEASE_ENERGY_MISMATCH");
    ReleaseState exit = result.getStations().get(Station.ORIFICE_EXIT);
    close(exit.getEnthalpyJkg() + 0.5 * exit.getVelocityMs() * exit.getVelocityMs(), upstream.getEnthalpyJkg(),
        Math.max(Math.abs(upstream.getEnthalpyJkg()), exit.getVelocityMs() * exit.getVelocityMs()),
        "RELEASE_ENERGY_MISMATCH");
    Map<String, Double> mass = componentMasses(withdrawal);
    for (Station station : new Station[] {Station.UPSTREAM_STAGNATION, Station.ORIFICE_EXIT}) {
      Map<String, Double> fractions = result.getStations().get(station).getComponentMassFractions();
      if (!fractions.keySet().equals(mass.keySet())) {
        throw new IllegalStateException("RELEASE_COMPOSITION_MISMATCH");
      }
      for (String name : mass.keySet()) {
        close(fractions.get(name), mass.get(name) / withdrawal.getMass("kg"), 1.0, "RELEASE_COMPOSITION_MISMATCH");
      }
    }
    return result;
  }

  private ReleaseFlowRequest request(SystemInterface fluid) {
    return new ReleaseFlowRequest(selectedPhase(fluid), diameterM, dischargeCoefficient, backPressurePa);
  }

  private int solveVolumeEnergy(SystemInterface fluid, double targetEnergy, double energyScale) {
    // The general VU solver has looser stopping criteria than cumulative release accounting.
    // Refine the SAME equations and property model, never accept a substitute flash/property path.
    for (int solve = 1; solve <= 32; solve++) {
      new ThermodynamicOperations(fluid).VUflash(volumeM3, targetEnergy, "m3", "J");
      fluid.init(3);
      requireInventory(fluid, true);
      double volumeError = Math.abs(fluid.getVolume("m3") - volumeM3) / volumeM3;
      double energyError = Math.abs(fluid.getInternalEnergy("J") - targetEnergy) / Math.max(1e-10, energyScale);
      if (volumeError <= 1e-11 && energyError <= 1e-11) {
        return solve;
      }
    }
    throw new IllegalStateException("INVENTORY_VU_REFINEMENT_FAILED: strict volume/energy residuals not reached");
  }

  private static PhaseType requireWithdrawalPhase(PhaseType type) {
    if (type != PhaseType.GAS && type != PhaseType.OIL && type != PhaseType.LIQUID && type != PhaseType.AQUEOUS) {
      throw new IllegalArgumentException("INVENTORY_WITHDRAWAL_PHASE_UNSUPPORTED: " + type);
    }
    return type;
  }

  private void requireInventory(SystemInterface fluid, boolean equilibrated) {
    if (fluid.isChemicalSystem() || fluid.isForcePhaseTypes() || fluid.doSolidPhaseCheck() || fluid.getHydrateCheck()
        || fluid.getTotalNumberOfMoles() <= 0.0) {
      throw new IllegalArgumentException("INVENTORY_REGIME_UNSUPPORTED: unforced, nonreacting fluid phases required");
    }
    ReleaseFlowRequest.positive(fluid.getTotalNumberOfMoles(), "inventory moles");
    ReleaseFlowRequest.positive(fluid.getTemperature(), "inventory temperature");
    ReleaseFlowRequest.positive(fluid.getPressure(), "inventory pressure");
    if (equilibrated) {
      ReleaseFlowRequest.positive(fluid.getMass("kg"), "inventory mass");
      ReleaseState.fromFluid(fluid, 0.0);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        PhaseType type = fluid.getPhase(phase).getType();
        requireWithdrawalPhase(type);
      }
    }
  }

  private void requireSelectedPhase(SystemInterface fluid, boolean configuration) {
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      if (fluid.getPhase(phase).getType() == withdrawalPhaseType) {
        return;
      }
    }
    String message = "INVENTORY_SELECTED_PHASE_ABSENT: " + withdrawalPhaseType;
    if (configuration) {
      throw new IllegalArgumentException(message);
    }
    throw new IllegalStateException(message);
  }

  private static void requireSingleGas(SystemInterface fluid) {
    if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalArgumentException(
          "INVENTORY_REGIME_UNSUPPORTED: compatibility constructor requires a single gas phase");
    }
  }

  private SystemInterface selectedPhase(SystemInterface fluid) {
    requireInventory(fluid, true);
    requireSelectedPhase(fluid, false);
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      if (fluid.getPhase(phase).getType() == withdrawalPhaseType) {
        SystemInterface selected = fluid.phaseToSystem(phase);
        selected.init(3);
        ReleaseFlowRequest.positive(selected.getMass("kg"), "selected phase mass");
        return selected;
      }
    }
    throw new IllegalStateException("INVENTORY_SELECTED_PHASE_ABSENT: " + withdrawalPhaseType);
  }

  private static Map<String, Double> componentMasses(SystemInterface fluid) {
    Map<String, Double> masses = new TreeMap<String, Double>();
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double mass = finite(fluid.getComponent(i).getNumberOfmoles() * fluid.getComponent(i).getMolarMass(),
          "component mass");
      if (mass < 0.0) {
        throw new IllegalStateException("Negative component inventory");
      }
      masses.put(fluid.getComponent(i).getComponentName(), mass);
    }
    return masses;
  }

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Nonfinite " + name);
    }
    return value;
  }

  private static void close(double actual, double expected, double scale, String code) {
    if (!Double.isFinite(actual) || !Double.isFinite(expected)
        || Math.abs(actual - expected) > CLOSURE_TOLERANCE * Math.max(1e-10, Math.abs(scale))) {
      throw new IllegalStateException(code + ": actual=" + actual + ", target=" + expected + ", scale=" + scale);
    }
  }

  /** @return defensive clone of the current inventory; modifying it does not change this unit */
  @Override
  public synchronized SystemInterface getThermoSystem() {
    return inventory.clone();
  }

  /**
   * Rejects an unconstrained pressure reset that would invalidate inventory energy accounting.
   *
   * @param pressure unused requested pressure in bara
   * @throws UnsupportedOperationException always; construct a new inventory for a new initial state
   */
  @Override
  public void setPressure(double pressure) {
    throw new UnsupportedOperationException("Inventory pressure is determined by its volume and energy balance");
  }

  /**
   * Rejects an unconstrained temperature reset that would invalidate inventory energy accounting.
   *
   * @param temperature unused requested temperature in K
   * @throws UnsupportedOperationException always; construct a new inventory for a new initial state
   */
  @Override
  public void setTemperature(double temperature) {
    throw new UnsupportedOperationException("Inventory temperature is determined by its energy balance");
  }

  /** @return immutable current opening request, including a cloned fluid */
  public synchronized ReleaseFlowRequest getReleaseRequest() {
    return request(inventory);
  }

  /** @return explicitly selected model; caller must not mutate a custom model during process access */
  public ReleaseFlowModel getReleaseModel() {
    return releaseModel;
  }

  /** @return explicitly selected inventory withdrawal phase */
  public PhaseType getWithdrawalPhaseType() {
    return withdrawalPhaseType;
  }

  /** @return true when constructed with the explicit phase-selective API */
  public boolean isPhaseSelective() {
    return phaseSelective;
  }

  /** @return configured maximum integration substep in seconds */
  public double getMaxSubstepS() {
    return maxSubstepS;
  }

  /** @return total VU solves, including strict residual refinement, in the last successful step */
  public synchronized int getLastVolumeEnergySolves() {
    return lastVolumeEnergySolves;
  }

  /** @return true when the last successful call located the receiving-pressure no-flow event */
  public synchronized boolean hadPressureEquilibrationEvent() {
    return lastPressureEquilibrationEvent;
  }

  /** @return seconds of physical release within the last successful caller timestep */
  public synchronized double getLastReleaseDurationS() {
    return lastReleaseDurationS;
  }

  /** @return whether the physical opening removes inventory during transient execution */
  public synchronized boolean isReleaseEnabled() {
    return releaseEnabled;
  }

  /**
   * Opens or closes the physical release between process calls. Retains all inventory and accounting. Invalidates
   * calculation identity until the next successful process run.
   *
   * @param enabled true to release, false to isolate this opening
   */
  public synchronized void setReleaseEnabled(boolean enabled) {
    releaseEnabled = enabled;
    setCalculationIdentifier(null);
  }

  /** @return immutable mass/energy accounting snapshot with explicit SI units */
  public synchronized Balance getBalance() {
    return new Balance(getTime(), volumeM3, initialEnergyJ, inventory.getInternalEnergy("J"), releasedEnergyJ,
        releasedMassKg, initialComponentMassKg, componentMasses(inventory), releasedComponentMassKg, lastSubsteps);
  }

  private static final class InventoryStep {
    private final SystemInterface next;
    private final Map<String, Double> beforeComponentMassKg;
    private final Map<String, Double> removedComponentMassKg;
    private final double removedMassKg;
    private final double outflowEnergyJ;
    private final double durationS;
    private final int volumeEnergySolves;

    private InventoryStep(SystemInterface next, Map<String, Double> beforeComponentMassKg,
        Map<String, Double> removedComponentMassKg, double removedMassKg, double outflowEnergyJ, double durationS,
        int volumeEnergySolves) {
      this.next = next;
      this.beforeComponentMassKg = beforeComponentMassKg;
      this.removedComponentMassKg = removedComponentMassKg;
      this.removedMassKg = removedMassKg;
      this.outflowEnergyJ = outflowEnergyJ;
      this.durationS = durationS;
      this.volumeEnergySolves = volumeEnergySolves;
    }
  }

  private static final class PressureEvent {
    private final InventoryStep step;
    private final int additionalVolumeEnergySolves;

    private PressureEvent(InventoryStep step, int additionalVolumeEnergySolves) {
      this.step = step;
      this.additionalVolumeEnergySolves = additionalVolumeEnergySolves;
    }
  }

  /** Immutable cumulative inventory accounting. Energy uses the selected EOS reference state. */
  public static final class Balance implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double timeS;
    private final double volumeM3;
    private final double initialEnergyJ;
    private final double internalEnergyJ;
    private final double releasedEnergyJ;
    private final double releasedMassKg;
    private final Map<String, Double> initialComponentMassKg;
    private final Map<String, Double> remainingComponentMassKg;
    private final Map<String, Double> releasedComponentMassKg;
    private final int substeps;

    private Balance(double timeS, double volumeM3, double initialEnergyJ, double internalEnergyJ,
        double releasedEnergyJ, double releasedMassKg, Map<String, Double> initial, Map<String, Double> remaining,
        Map<String, Double> released, int substeps) {
      this.timeS = timeS;
      this.volumeM3 = volumeM3;
      this.initialEnergyJ = initialEnergyJ;
      this.internalEnergyJ = internalEnergyJ;
      this.releasedEnergyJ = releasedEnergyJ;
      this.releasedMassKg = releasedMassKg;
      initialComponentMassKg = Collections.unmodifiableMap(new TreeMap<String, Double>(initial));
      remainingComponentMassKg = Collections.unmodifiableMap(new TreeMap<String, Double>(remaining));
      releasedComponentMassKg = Collections.unmodifiableMap(new TreeMap<String, Double>(released));
      this.substeps = substeps;
    }

    /** @return simulation time in seconds */
    public double getTimeS() {
      return timeS;
    }

    /** @return fixed inventory volume in m3 */
    public double getVolumeM3() {
      return volumeM3;
    }

    /** @return initial internal energy in J */
    public double getInitialEnergyJ() {
      return initialEnergyJ;
    }

    /** @return current internal energy in J */
    public double getInternalEnergyJ() {
      return internalEnergyJ;
    }

    /** @return integrated outgoing stagnation enthalpy in J; may be negative for an EOS reference */
    public double getReleasedEnergyJ() {
      return releasedEnergyJ;
    }

    /** @return cumulative released mass in kg */
    public double getReleasedMassKg() {
      return releasedMassKg;
    }

    /** @return immutable initial component masses in kg, sorted by name */
    public Map<String, Double> getInitialComponentMassKg() {
      return Collections.unmodifiableMap(new TreeMap<String, Double>(initialComponentMassKg));
    }

    /** @return immutable remaining component masses in kg, sorted by name */
    public Map<String, Double> getRemainingComponentMassKg() {
      return Collections.unmodifiableMap(new TreeMap<String, Double>(remainingComponentMassKg));
    }

    /** @return immutable cumulative released component masses in kg, sorted by name */
    public Map<String, Double> getReleasedComponentMassKg() {
      return Collections.unmodifiableMap(new TreeMap<String, Double>(releasedComponentMassKg));
    }

    /** @return number of substep evaluations in the last successful transient call */
    public int getSubsteps() {
      return substeps;
    }
  }
}
