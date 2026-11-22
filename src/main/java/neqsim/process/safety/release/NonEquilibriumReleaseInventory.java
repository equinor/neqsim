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
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Opt-in rigid gas/liquid inventory with retained phase compositions and finite-rate interphase transfer.
 *
 * <p>
 * Unlike {@link ReleaseInventory}, this unit never reflashes the committed component partition. It retains separate gas
 * and liquid component masses and evaluates each as a forced homogeneous EOS phase. The default constructors solve one
 * common pressure and temperature from total rigid volume and total internal energy. An explicit
 * {@link ReleaseInventoryInterphaseHeatTransfer} instead activates separate gas and liquid energy inventories and
 * temperatures at a common mechanical pressure. A provenance-required {@link ReleaseInventoryInterphaseTransfer} moves
 * components in either direction across the live geometry area with donor limits. The phase-enthalpy difference is
 * retained as a latent-energy diagnostic while the volume/internal- energy solve enforces the complete closed-system
 * energy effect. Release withdrawal removes the selected phase composition and stagnation enthalpy. Candidate calls are
 * committed atomically only after component, total-mass, volume and energy closure checks pass.
 * </p>
 *
 * <p>
 * Exactly one gas phase and exactly one oil, generic liquid or aqueous phase are retained. The implementation does not
 * predict heat- or mass-transfer coefficients, nucleation delay, entrainment, population balance, wall thermal inertia,
 * pipe waves or solid transport. Coefficients are prescribed sensitivity inputs and do not confer engineering
 * qualification.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class NonEquilibriumReleaseInventory extends ProcessEquipmentBaseClass implements CoupledReleaseSource {
  private static final long serialVersionUID = 1L;
  private static final int GAS = 0;
  private static final int LIQUID = 1;
  private static final int PHASES = 2;
  private static final double CLOSURE_TOLERANCE = 1.0e-7;
  private static final double SOLVER_TOLERANCE = 1.0e-10;
  private static final double PRESSURE_EVENT_RELATIVE_TOLERANCE = 1.0e-9;
  private static final int MAX_SOLVER_ITERATIONS = 50;
  private static final int MAX_SUBSTEPS = 10000;

  private final double volumeM3;
  private final double diameterM;
  private final double dischargeCoefficient;
  private final double backPressurePa;
  private final double flowPathLengthM;
  private final double darcyFrictionFactor;
  private final double maxSubstepS;
  private final ReleaseFlowModel releaseModel;
  private final PhaseType withdrawalPhaseType;
  private final PhaseType liquidPhaseType;
  private final ReleaseInventoryPhaseGeometry geometry;
  private final ReleaseInventoryInterphaseTransfer interphaseTransfer;
  private final ReleaseInventoryHeatTransfer heatTransfer;
  private final ReleaseInventoryInterphaseHeatTransfer interphaseHeatTransfer;
  private final SystemInterface template;
  private final String[] componentNames;
  private final double[] componentMolarMassKgMol;
  private final Map<String, Double> initialComponentMassKg;
  private final double initialEnergyJ;

  private double[][] phaseComponentMassKg;
  private State state;
  private Map<String, Double> releasedComponentMassKg;
  private double releasedMassKg;
  private double releasedEnergyJ;
  private double heatTransferEnergyJ;
  private double interphaseLatentEnergyJ;
  private double interphaseHeatTransferEnergyJ;
  private double interphaseTransferredMassKg;
  private int donorLimitedComponents;
  private int lastSubsteps;
  private int lastStateSolves;
  private UUID lastTransientId;
  private double lastTransientDurationS;
  private boolean releaseEnabled = true;
  private boolean lastPressureEquilibrationEvent;

  /**
   * Creates an independently owned two-phase non-equilibrium inventory.
   *
   * @param name unique process-equipment name
   * @param initialFluid equilibrium initial fluid containing exactly one gas and one supported liquid phase
   * @param volumeM3 fixed vessel volume in m3; must equal the cylindrical geometry capacity
   * @param diameterM physical release opening diameter in m
   * @param dischargeCoefficient discharge coefficient in (0, 1]
   * @param backPressurePa constant absolute receiving pressure in Pa
   * @param releaseModel caller-selected release model; screening results are rejected
   * @param maxSubstepS largest explicit substep in s
   * @param withdrawalPhaseType explicit GAS, OIL, LIQUID or AQUEOUS release phase
   * @param geometry live cylindrical level/interface/opening geometry
   * @param interphaseTransfer prescribed two-way component-film closure
   */
  public NonEquilibriumReleaseInventory(String name, SystemInterface initialFluid, double volumeM3, double diameterM,
      double dischargeCoefficient, double backPressurePa, ReleaseFlowModel releaseModel, double maxSubstepS,
      PhaseType withdrawalPhaseType, ReleaseInventoryPhaseGeometry geometry,
      ReleaseInventoryInterphaseTransfer interphaseTransfer) {
    this(name, initialFluid, volumeM3, diameterM, dischargeCoefficient, backPressurePa, 0.0, 0.0, releaseModel,
        maxSubstepS, withdrawalPhaseType, geometry, interphaseTransfer, ReleaseInventoryHeatTransfer.adiabatic(), null,
        false);
  }

  /**
   * Creates an independently owned two-phase non-equilibrium inventory with pipe and heat boundaries.
   *
   * @param name unique process-equipment name
   * @param initialFluid equilibrium initial fluid containing exactly one gas and one supported liquid phase
   * @param volumeM3 fixed vessel volume in m3; must equal the cylindrical geometry capacity
   * @param diameterM physical opening or pipe diameter in m
   * @param dischargeCoefficient discharge coefficient or full-bore area factor in (0, 1]
   * @param backPressurePa constant absolute receiving pressure in Pa
   * @param flowPathLengthM constant-area release path length in m; zero selects a short opening
   * @param darcyFrictionFactor caller-specified Darcy factor; zero selects a short opening
   * @param releaseModel caller-selected release model; screening results are rejected
   * @param maxSubstepS largest explicit substep in s
   * @param withdrawalPhaseType explicit GAS, OIL, LIQUID or AQUEOUS release phase
   * @param geometry live cylindrical level/interface/opening geometry
   * @param interphaseTransfer prescribed two-way component-film closure
   * @param heatTransfer explicit external heat boundary
   */
  public NonEquilibriumReleaseInventory(String name, SystemInterface initialFluid, double volumeM3, double diameterM,
      double dischargeCoefficient, double backPressurePa, double flowPathLengthM, double darcyFrictionFactor,
      ReleaseFlowModel releaseModel, double maxSubstepS, PhaseType withdrawalPhaseType,
      ReleaseInventoryPhaseGeometry geometry, ReleaseInventoryInterphaseTransfer interphaseTransfer,
      ReleaseInventoryHeatTransfer heatTransfer) {
    this(name, initialFluid, volumeM3, diameterM, dischargeCoefficient, backPressurePa, flowPathLengthM,
        darcyFrictionFactor, releaseModel, maxSubstepS, withdrawalPhaseType, geometry, interphaseTransfer, heatTransfer,
        null, false);
  }

  /**
   * Creates an independently owned inventory with separate phase energies and temperatures.
   *
   * <p>
   * The external heat boundary is applied to the selected withdrawal phase. Interfacial heat is equal and opposite
   * between phases.
   * </p>
   *
   * @param name unique process-equipment name
   * @param initialFluid equilibrium initial fluid containing exactly one gas and one supported liquid phase
   * @param volumeM3 fixed vessel volume in m3; must equal the cylindrical geometry capacity
   * @param diameterM physical opening or pipe diameter in m
   * @param dischargeCoefficient discharge coefficient or full-bore area factor in (0, 1]
   * @param backPressurePa constant absolute receiving pressure in Pa
   * @param flowPathLengthM constant-area release path length in m; zero selects a short opening
   * @param darcyFrictionFactor caller-specified Darcy factor; zero selects a short opening
   * @param releaseModel caller-selected release model; screening results are rejected
   * @param maxSubstepS largest explicit substep in s
   * @param withdrawalPhaseType explicit GAS, OIL, LIQUID or AQUEOUS release phase
   * @param geometry live cylindrical level/interface/opening geometry
   * @param interphaseTransfer prescribed two-way component-film closure
   * @param heatTransfer explicit external heat boundary applied to the withdrawal phase
   * @param interphaseHeatTransfer prescribed interfacial heat-transfer closure
   */
  public NonEquilibriumReleaseInventory(String name, SystemInterface initialFluid, double volumeM3, double diameterM,
      double dischargeCoefficient, double backPressurePa, double flowPathLengthM, double darcyFrictionFactor,
      ReleaseFlowModel releaseModel, double maxSubstepS, PhaseType withdrawalPhaseType,
      ReleaseInventoryPhaseGeometry geometry, ReleaseInventoryInterphaseTransfer interphaseTransfer,
      ReleaseInventoryHeatTransfer heatTransfer, ReleaseInventoryInterphaseHeatTransfer interphaseHeatTransfer) {
    this(name, initialFluid, volumeM3, diameterM, dischargeCoefficient, backPressurePa, flowPathLengthM,
        darcyFrictionFactor, releaseModel, maxSubstepS, withdrawalPhaseType, geometry, interphaseTransfer, heatTransfer,
        Objects.requireNonNull(interphaseHeatTransfer, "interphaseHeatTransfer"), true);
  }

  /**
   * Creates the common- or separate-temperature implementation.
   *
   * @param name unique process-equipment name
   * @param initialFluid initial equilibrium fluid
   * @param volumeM3 fixed volume in m3
   * @param diameterM opening diameter in m
   * @param dischargeCoefficient opening coefficient
   * @param backPressurePa receiving pressure in Pa
   * @param flowPathLengthM flow-path length in m
   * @param darcyFrictionFactor Darcy factor
   * @param releaseModel release model
   * @param maxSubstepS maximum substep in s
   * @param withdrawalPhaseType selected phase
   * @param geometry phase geometry
   * @param interphaseTransfer component transfer closure
   * @param heatTransfer external heat boundary
   * @param interphaseHeatTransfer optional interfacial heat closure
   * @param separatePhaseEnergy true only for the explicit separate-energy constructor
   */
  private NonEquilibriumReleaseInventory(String name, SystemInterface initialFluid, double volumeM3, double diameterM,
      double dischargeCoefficient, double backPressurePa, double flowPathLengthM, double darcyFrictionFactor,
      ReleaseFlowModel releaseModel, double maxSubstepS, PhaseType withdrawalPhaseType,
      ReleaseInventoryPhaseGeometry geometry, ReleaseInventoryInterphaseTransfer interphaseTransfer,
      ReleaseInventoryHeatTransfer heatTransfer, ReleaseInventoryInterphaseHeatTransfer interphaseHeatTransfer,
      boolean separatePhaseEnergy) {
    super(name);
    this.volumeM3 = ReleaseFlowRequest.positive(volumeM3, "volumeM3");
    this.maxSubstepS = ReleaseFlowRequest.positive(maxSubstepS, "maxSubstepS");
    this.releaseModel = Objects.requireNonNull(releaseModel, "releaseModel");
    this.withdrawalPhaseType = requireWithdrawalPhase(withdrawalPhaseType);
    this.geometry = Objects.requireNonNull(geometry, "geometry");
    this.interphaseTransfer = Objects.requireNonNull(interphaseTransfer, "interphaseTransfer");
    this.heatTransfer = Objects.requireNonNull(heatTransfer, "heatTransfer");
    this.interphaseHeatTransfer = separatePhaseEnergy
        ? Objects.requireNonNull(interphaseHeatTransfer, "interphaseHeatTransfer")
        : null;
    geometry.requireCapacity(this.volumeM3);
    ReleaseFlowRequest configuration = new ReleaseFlowRequest(initialFluid, diameterM, dischargeCoefficient,
        backPressurePa, flowPathLengthM, darcyFrictionFactor);
    this.diameterM = configuration.getDiameterM();
    this.dischargeCoefficient = configuration.getDischargeCoefficient();
    this.backPressurePa = configuration.getBackPressurePa();
    this.flowPathLengthM = configuration.getFlowPathLengthM();
    this.darcyFrictionFactor = configuration.getDarcyFrictionFactor();

    SystemInterface scaled = configuration.getFluid();
    new ThermodynamicOperations(scaled).TPflash();
    scaled.init(3);
    double initialVolume = ReleaseFlowRequest.positive(scaled.getVolume("m3"), "initial volume");
    scaled.setTotalNumberOfMoles(scaled.getTotalNumberOfMoles() * volumeM3 / initialVolume);
    new ThermodynamicOperations(scaled).TPflash();
    scaled.init(3);
    this.liquidPhaseType = requireTwoPhaseState(scaled);
    if (withdrawalPhaseType != PhaseType.GAS && withdrawalPhaseType != liquidPhaseType) {
      throw new IllegalArgumentException("INVENTORY_WITHDRAWAL_PHASE_NOT_PRESENT: " + withdrawalPhaseType);
    }
    this.template = scaled.clone();
    this.componentNames = new String[scaled.getNumberOfComponents()];
    this.componentMolarMassKgMol = new double[componentNames.length];
    for (int component = 0; component < componentNames.length; component++) {
      componentNames[component] = scaled.getComponent(component).getComponentName();
      componentMolarMassKgMol[component] = scaled.getComponent(component).getMolarMass();
      ReleaseFlowRequest.positive(componentMolarMassKgMol[component], "component molar mass");
    }
    for (String coefficientComponent : interphaseTransfer.getCoefficientsMS().keySet()) {
      boolean found = false;
      for (String componentName : componentNames) {
        found |= componentName.equals(coefficientComponent);
      }
      if (!found) {
        throw new IllegalArgumentException("INVENTORY_INTERPHASE_COMPONENT_NOT_PRESENT: " + coefficientComponent);
      }
    }
    this.phaseComponentMassKg = extractPhaseComponentMasses(scaled, liquidPhaseType);
    this.state = evaluate(phaseComponentMassKg, scaled.getPressure() * 1.0e5, scaled.getTemperature());
    close(state.volumeM3, volumeM3, volumeM3, "INVENTORY_VOLUME_CLOSURE_FAILED");
    requireGeometry(state);
    this.initialComponentMassKg = totalComponentMasses(phaseComponentMassKg);
    this.initialEnergyJ = state.internalEnergyJ;
    this.releasedComponentMassKg = zeroComponentMap();
    setCalculateSteadyState(false);
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void run(UUID id) {
    Objects.requireNonNull(id, "id");
    if (releaseEnabled) {
      calculate(state);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Advances release, external heat and interphase transfer as one atomic transient transaction.
   *
   * @param dt positive caller duration in s
   * @param id successful calculation identity
   */
  @Override
  public synchronized void runTransient(double dt, UUID id) {
    ReleaseFlowRequest.positive(dt, "dt");
    Objects.requireNonNull(id, "id");
    if (getCalculateSteadyState()) {
      throw new IllegalArgumentException("NonEquilibriumReleaseInventory requires dynamic mode");
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
    double[][] candidateMass = copy(phaseComponentMassKg);
    State candidateState = state;
    TreeMap<String, Double> componentLoss = new TreeMap<String, Double>(releasedComponentMassKg);
    double massLoss = releasedMassKg;
    double energyLoss = releasedEnergyJ;
    double heatInput = heatTransferEnergyJ;
    double latent = interphaseLatentEnergyJ;
    double interphaseHeat = interphaseHeatTransferEnergyJ;
    double transferred = interphaseTransferredMassKg;
    int limited = donorLimitedComponents;
    int solves = 0;
    int steps = 0;
    double elapsed = 0.0;
    boolean pressureEvent = false;
    while (elapsed < dt) {
      if (Thread.currentThread().isInterrupted()) {
        throw new IllegalStateException("INVENTORY_STEP_INTERRUPTED");
      }
      if (++steps > MAX_SUBSTEPS) {
        throw new IllegalStateException("INVENTORY_SUBSTEP_LIMIT: reduce requested duration");
      }
      ReleaseFlowResult release = releaseEnabled ? calculate(candidateState) : null;
      double releaseRateKgS = release == null ? 0.0 : release.getMassFlowRateKgS();
      double heatRateW = heatTransfer.getHeatTransferRateW(ReleaseState.fromFluid(selectedState(candidateState), 0.0));
      double duration = Math.min(maxSubstepS, dt - elapsed);
      if (releaseRateKgS > 0.0) {
        duration = Math.min(duration, 0.01 * phaseMass(candidateMass, withdrawalIndex()) / releaseRateKgS);
      }
      Step step = advance(candidateMass, candidateState, releaseRateKgS, heatRateW, duration);
      solves += step.stateSolves;
      if (releaseRateKgS == 0.0 && heatRateW == 0.0 && step.absoluteTransferredMassKg == 0.0
          && step.interphaseHeatTransferEnergyJ == 0.0) {
        break;
      }
      double pressureTolerance = Math.max(1.0e-3, backPressurePa * PRESSURE_EVENT_RELATIVE_TOLERANCE);
      if (releaseRateKgS > 0.0 && step.state.pressurePa < backPressurePa) {
        PressureEvent located = locateReceivingPressureEvent(candidateMass, candidateState, releaseRateKgS, heatRateW,
            duration);
        step = located.step;
        solves += located.additionalStateSolves;
        pressureEvent = true;
      }
      if (releaseRateKgS > 0.0 && step.state.pressurePa <= backPressurePa + pressureTolerance) {
        close(step.state.pressurePa, backPressurePa, backPressurePa, "RECEIVING_PRESSURE_EVENT_FAILED");
        pressureEvent = true;
      }
      for (String name : componentNames) {
        componentLoss.put(name, componentLoss.get(name) + step.removedComponentMassKg.get(name));
      }
      massLoss += step.removedMassKg;
      energyLoss += step.outflowEnergyJ;
      heatInput += step.heatTransferEnergyJ;
      latent += step.interphaseLatentEnergyJ;
      interphaseHeat += step.interphaseHeatTransferEnergyJ;
      transferred += step.absoluteTransferredMassKg;
      limited += step.donorLimitedComponents;
      elapsed += step.durationS;
      candidateMass = step.phaseComponentMassKg;
      candidateState = step.state;
      if (pressureEvent && !heatTransfer.isActive()
          && (interphaseHeatTransfer == null || !interphaseHeatTransfer.isActive())) {
        break;
      }
    }
    Map<String, Double> remaining = totalComponentMasses(candidateMass);
    for (String name : componentNames) {
      close(remaining.get(name) + componentLoss.get(name), initialComponentMassKg.get(name),
          initialComponentMassKg.get(name), "CUMULATIVE_COMPONENT_CLOSURE_FAILED");
    }
    close(candidateState.internalEnergyJ + energyLoss - heatInput, initialEnergyJ,
        Math.max(Math.abs(initialEnergyJ), Math.max(Math.abs(energyLoss), Math.abs(heatInput))),
        "CUMULATIVE_ENERGY_CLOSURE_FAILED");
    phaseComponentMassKg = candidateMass;
    state = candidateState;
    releasedComponentMassKg = componentLoss;
    releasedMassKg = massLoss;
    releasedEnergyJ = energyLoss;
    heatTransferEnergyJ = heatInput;
    interphaseLatentEnergyJ = latent;
    interphaseHeatTransferEnergyJ = interphaseHeat;
    interphaseTransferredMassKg = transferred;
    donorLimitedComponents = limited;
    lastSubsteps = steps;
    lastStateSolves = solves;
    lastTransientId = id;
    lastTransientDurationS = dt;
    lastPressureEquilibrationEvent = pressureEvent;
    setTime(targetTime);
    setCalculationIdentifier(id);
  }

  /**
   * Advances one immutable candidate substep.
   *
   * @param beforeMass retained phase/component masses
   * @param beforeState retained forced phase state
   * @param releaseRateKgS selected-phase release rate in kg/s
   * @param heatRateW signed external heat input in W
   * @param durationS substep duration in s
   * @return checked candidate step
   */
  private Step advance(double[][] beforeMass, State beforeState, double releaseRateKgS, double heatRateW,
      double durationS) {
    ReleaseFlowRequest.positive(durationS, "inventory substep");
    int withdrawal = withdrawalIndex();
    double[][] nextMass = copy(beforeMass);
    ReleaseInventoryPhaseGeometry.Assessment geometryAssessment = requireGeometry(beforeState);
    ReleaseInventoryInterphaseTransfer.Step transfer = interphaseTransfer.calculate(beforeState.gas, beforeState.liquid,
        componentMap(beforeMass[GAS]), componentMap(beforeMass[LIQUID]), geometryAssessment.getInterfacialAreaM2(),
        durationS);
    double absoluteTransferredMass = 0.0;
    double gasMassTransferEnergy = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      double amount = transfer.getGasDirectedComponentMassKg().get(componentNames[component]);
      nextMass[GAS][component] += amount;
      nextMass[LIQUID][component] -= amount;
      absoluteTransferredMass += Math.abs(amount);
      gasMassTransferEnergy += amount
          * (amount >= 0.0 ? beforeState.liquid.getEnthalpy("J/kg") : beforeState.gas.getEnthalpy("J/kg"));
    }
    double removedMass = releaseRateKgS * durationS;
    double withdrawalMass = phaseMass(beforeMass, withdrawal);
    if (!Double.isFinite(removedMass) || removedMass < 0.0 || removedMass >= withdrawalMass) {
      throw new IllegalStateException("INVENTORY_SELECTED_PHASE_OVERDRAW");
    }
    TreeMap<String, Double> removed = new TreeMap<String, Double>();
    for (int component = 0; component < componentNames.length; component++) {
      double loss = removedMass * beforeMass[withdrawal][component] / withdrawalMass;
      nextMass[withdrawal][component] -= loss;
      removed.put(componentNames[component], loss);
    }
    requirePositivePhases(nextMass);
    double outflowEnergy = removedMass * selectedState(beforeState).getEnthalpy("J/kg");
    double heatEnergy = heatRateW * durationS;
    double interphaseHeatEnergy = interphaseHeatTransfer == null ? 0.0
        : interphaseHeatTransfer.getGasHeatTransferRateW(beforeState.gasTemperatureK, beforeState.liquidTemperatureK)
            * durationS;
    double targetEnergy = beforeState.internalEnergyJ - outflowEnergy + heatEnergy;
    SolveResult solved;
    if (interphaseHeatTransfer == null) {
      solved = solve(nextMass, targetEnergy, beforeState.pressurePa, beforeState.gasTemperatureK);
    } else {
      double targetGasEnergy = beforeState.gasInternalEnergyJ + gasMassTransferEnergy + interphaseHeatEnergy;
      double targetLiquidEnergy = beforeState.liquidInternalEnergyJ - gasMassTransferEnergy - interphaseHeatEnergy;
      if (withdrawal == GAS) {
        targetGasEnergy += heatEnergy - outflowEnergy;
      } else {
        targetLiquidEnergy += heatEnergy - outflowEnergy;
      }
      solved = solveSeparate(nextMass, targetGasEnergy, targetLiquidEnergy, beforeState.pressurePa,
          beforeState.gasTemperatureK, beforeState.liquidTemperatureK);
      close(solved.state.gasInternalEnergyJ, targetGasEnergy,
          Math.max(Math.abs(beforeState.gasInternalEnergyJ), Math.abs(targetGasEnergy)),
          "INVENTORY_GAS_ENERGY_CLOSURE_FAILED");
      close(solved.state.liquidInternalEnergyJ, targetLiquidEnergy,
          Math.max(Math.abs(beforeState.liquidInternalEnergyJ), Math.abs(targetLiquidEnergy)),
          "INVENTORY_LIQUID_ENERGY_CLOSURE_FAILED");
    }
    requireGeometry(solved.state);
    close(solved.state.volumeM3, volumeM3, volumeM3, "INVENTORY_VOLUME_CLOSURE_FAILED");
    close(solved.state.internalEnergyJ, targetEnergy,
        Math.max(Math.abs(beforeState.internalEnergyJ), Math.max(Math.abs(outflowEnergy), Math.abs(heatEnergy))),
        "INVENTORY_ENERGY_CLOSURE_FAILED");
    close(totalMass(nextMass) + removedMass, totalMass(beforeMass), totalMass(beforeMass),
        "INVENTORY_MASS_CLOSURE_FAILED");
    Map<String, Double> beforeTotal = totalComponentMasses(beforeMass);
    Map<String, Double> afterTotal = totalComponentMasses(nextMass);
    for (String name : componentNames) {
      close(afterTotal.get(name) + removed.get(name), beforeTotal.get(name), beforeTotal.get(name),
          "INVENTORY_COMPONENT_CLOSURE_FAILED");
    }
    return new Step(nextMass, solved.state, removed, removedMass, outflowEnergy, heatEnergy,
        transfer.getLatentEnergyJ(), interphaseHeatEnergy, absoluteTransferredMass,
        transfer.getDonorLimitedComponents(), durationS, solved.evaluations);
  }

  /**
   * Locates the receiving-pressure event with bounded bisection.
   *
   * @param beforeMass beginning-of-step masses
   * @param beforeState beginning-of-step state
   * @param releaseRateKgS fixed beginning-of-step release rate
   * @param heatRateW fixed beginning-of-step heat rate
   * @param upperDurationS first duration that crossed receiving pressure
   * @return located event step and extra solve count
   */
  private PressureEvent locateReceivingPressureEvent(double[][] beforeMass, State beforeState, double releaseRateKgS,
      double heatRateW, double upperDurationS) {
    double lower = 0.0;
    double upper = upperDurationS;
    double tolerance = Math.max(1.0e-3, backPressurePa * PRESSURE_EVENT_RELATIVE_TOLERANCE);
    int solves = 0;
    for (int iteration = 0; iteration < 64; iteration++) {
      double duration = 0.5 * (lower + upper);
      if (!(duration > lower) || !(duration < upper)) {
        break;
      }
      Step trial = advance(beforeMass, beforeState, releaseRateKgS, heatRateW, duration);
      solves += trial.stateSolves;
      if (trial.state.pressurePa >= backPressurePa) {
        lower = duration;
        if (trial.state.pressurePa - backPressurePa <= tolerance) {
          return new PressureEvent(trial, solves);
        }
      } else {
        upper = duration;
      }
    }
    throw new IllegalStateException("RECEIVING_PRESSURE_EVENT_FAILED");
  }

  /**
   * Solves common pressure and temperature for fixed phase component inventories.
   *
   * @param masses retained phase/component masses
   * @param targetEnergyJ total internal-energy target in J
   * @param initialPressurePa initial pressure iterate in Pa
   * @param initialTemperatureK initial temperature iterate in K
   * @return converged state and evaluation count
   */
  private SolveResult solve(double[][] masses, double targetEnergyJ, double initialPressurePa,
      double initialTemperatureK) {
    double pressure = initialPressurePa;
    double temperature = initialTemperatureK;
    int evaluations = 0;
    double scale = Math.max(1.0, Math.abs(targetEnergyJ));
    State current = null;
    for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
      current = evaluate(masses, pressure, temperature);
      evaluations++;
      double volumeResidual = current.volumeM3 - volumeM3;
      double energyResidual = current.internalEnergyJ - targetEnergyJ;
      double norm = Math.max(Math.abs(volumeResidual) / volumeM3, Math.abs(energyResidual) / scale);
      if (norm <= SOLVER_TOLERANCE) {
        return new SolveResult(current, evaluations);
      }
      double dTemperature = Math.max(1.0e-3, 1.0e-4 * temperature);
      double dPressure = Math.max(10.0, 1.0e-4 * pressure);
      State temperatureProbe = evaluate(masses, pressure, temperature + dTemperature);
      State pressureProbe = evaluate(masses, pressure + dPressure, temperature);
      evaluations += 2;
      double vt = (temperatureProbe.volumeM3 - current.volumeM3) / dTemperature;
      double vp = (pressureProbe.volumeM3 - current.volumeM3) / dPressure;
      double ut = (temperatureProbe.internalEnergyJ - current.internalEnergyJ) / dTemperature;
      double up = (pressureProbe.internalEnergyJ - current.internalEnergyJ) / dPressure;
      double determinant = vt * up - vp * ut;
      if (!Double.isFinite(determinant) || determinant == 0.0) {
        throw new IllegalStateException("INVENTORY_NON_EQUILIBRIUM_JACOBIAN_SINGULAR");
      }
      double deltaTemperature = (-volumeResidual * up + vp * energyResidual) / determinant;
      double deltaPressure = (ut * volumeResidual - vt * energyResidual) / determinant;
      boolean accepted = false;
      for (int lineSearch = 0; lineSearch < 20; lineSearch++) {
        double damping = Math.scalb(1.0, -lineSearch);
        double trialTemperature = temperature + damping * deltaTemperature;
        double trialPressure = pressure + damping * deltaPressure;
        if (!(trialTemperature > 50.0) || !(trialPressure > 1000.0) || !Double.isFinite(trialTemperature)
            || !Double.isFinite(trialPressure)) {
          continue;
        }
        State trial = evaluate(masses, trialPressure, trialTemperature);
        evaluations++;
        double trialNorm = Math.max(Math.abs(trial.volumeM3 - volumeM3) / volumeM3,
            Math.abs(trial.internalEnergyJ - targetEnergyJ) / scale);
        if (trialNorm < norm) {
          pressure = trialPressure;
          temperature = trialTemperature;
          accepted = true;
          break;
        }
      }
      if (!accepted) {
        throw new IllegalStateException("INVENTORY_NON_EQUILIBRIUM_LINE_SEARCH_FAILED");
      }
    }
    throw new IllegalStateException("INVENTORY_NON_EQUILIBRIUM_STATE_SOLVE_FAILED");
  }

  /**
   * Solves common pressure and independent phase temperatures for fixed phase energies.
   *
   * @param masses retained phase/component masses
   * @param targetGasEnergyJ gas internal-energy target in J
   * @param targetLiquidEnergyJ liquid internal-energy target in J
   * @param initialPressurePa initial pressure iterate in Pa
   * @param initialGasTemperatureK initial gas-temperature iterate in K
   * @param initialLiquidTemperatureK initial liquid-temperature iterate in K
   * @return converged state and evaluation count
   */
  private SolveResult solveSeparate(double[][] masses, double targetGasEnergyJ, double targetLiquidEnergyJ,
      double initialPressurePa, double initialGasTemperatureK, double initialLiquidTemperatureK) {
    double pressure = initialPressurePa;
    double gasTemperature = initialGasTemperatureK;
    double liquidTemperature = initialLiquidTemperatureK;
    double gasScale = Math.max(1.0, Math.abs(targetGasEnergyJ));
    double liquidScale = Math.max(1.0, Math.abs(targetLiquidEnergyJ));
    int evaluations = 0;
    for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
      State current = evaluateSeparate(masses, pressure, gasTemperature, liquidTemperature);
      evaluations++;
      double[] residual = separateResidual(current, targetGasEnergyJ, targetLiquidEnergyJ, gasScale, liquidScale);
      double norm = maximumAbsolute(residual);
      if (norm <= SOLVER_TOLERANCE) {
        return new SolveResult(current, evaluations);
      }
      double dPressure = Math.max(10.0, 1.0e-4 * pressure);
      double dGasTemperature = Math.max(1.0e-3, 1.0e-4 * gasTemperature);
      double dLiquidTemperature = Math.max(1.0e-3, 1.0e-4 * liquidTemperature);
      State pressureProbe = evaluateSeparate(masses, pressure + dPressure, gasTemperature, liquidTemperature);
      State gasProbe = evaluateSeparate(masses, pressure, gasTemperature + dGasTemperature, liquidTemperature);
      State liquidProbe = evaluateSeparate(masses, pressure, gasTemperature, liquidTemperature + dLiquidTemperature);
      evaluations += 3;
      double[][] jacobian = new double[3][3];
      fillDerivative(jacobian, 0, residual,
          separateResidual(pressureProbe, targetGasEnergyJ, targetLiquidEnergyJ, gasScale, liquidScale), dPressure);
      fillDerivative(jacobian, 1, residual,
          separateResidual(gasProbe, targetGasEnergyJ, targetLiquidEnergyJ, gasScale, liquidScale), dGasTemperature);
      fillDerivative(jacobian, 2, residual,
          separateResidual(liquidProbe, targetGasEnergyJ, targetLiquidEnergyJ, gasScale, liquidScale),
          dLiquidTemperature);
      double[] delta = solveThreeByThree(jacobian, new double[] {-residual[0], -residual[1], -residual[2]});
      boolean accepted = false;
      for (int lineSearch = 0; lineSearch < 20; lineSearch++) {
        double damping = Math.scalb(1.0, -lineSearch);
        double trialPressure = pressure + damping * delta[0];
        double trialGasTemperature = gasTemperature + damping * delta[1];
        double trialLiquidTemperature = liquidTemperature + damping * delta[2];
        if (!(trialPressure > 1000.0) || !(trialGasTemperature > 50.0) || !(trialLiquidTemperature > 50.0)
            || !Double.isFinite(trialPressure) || !Double.isFinite(trialGasTemperature)
            || !Double.isFinite(trialLiquidTemperature)) {
          continue;
        }
        State trial = evaluateSeparate(masses, trialPressure, trialGasTemperature, trialLiquidTemperature);
        evaluations++;
        double trialNorm = maximumAbsolute(
            separateResidual(trial, targetGasEnergyJ, targetLiquidEnergyJ, gasScale, liquidScale));
        if (trialNorm < norm) {
          pressure = trialPressure;
          gasTemperature = trialGasTemperature;
          liquidTemperature = trialLiquidTemperature;
          accepted = true;
          break;
        }
      }
      if (!accepted) {
        throw new IllegalStateException("INVENTORY_SEPARATE_ENERGY_LINE_SEARCH_FAILED");
      }
    }
    throw new IllegalStateException("INVENTORY_SEPARATE_ENERGY_STATE_SOLVE_FAILED");
  }

  /**
   * Returns scaled volume and phase-energy residuals.
   *
   * @param candidate evaluated state
   * @param targetGasEnergyJ gas-energy target in J
   * @param targetLiquidEnergyJ liquid-energy target in J
   * @param gasScale gas-energy scale in J
   * @param liquidScale liquid-energy scale in J
   * @return three scaled residuals
   */
  private double[] separateResidual(State candidate, double targetGasEnergyJ, double targetLiquidEnergyJ,
      double gasScale, double liquidScale) {
    return new double[] {(candidate.volumeM3 - volumeM3) / volumeM3,
        (candidate.gasInternalEnergyJ - targetGasEnergyJ) / gasScale,
        (candidate.liquidInternalEnergyJ - targetLiquidEnergyJ) / liquidScale};
  }

  /**
   * Fills one finite-difference Jacobian column.
   *
   * @param jacobian destination matrix
   * @param column destination column
   * @param base base residual
   * @param probe perturbed residual
   * @param increment variable increment
   */
  private static void fillDerivative(double[][] jacobian, int column, double[] base, double[] probe, double increment) {
    for (int row = 0; row < 3; row++) {
      jacobian[row][column] = (probe[row] - base[row]) / increment;
    }
  }

  /**
   * Solves one three-by-three linear system with scaled partial pivoting.
   *
   * @param matrix coefficient matrix
   * @param rightHandSide right-hand side
   * @return solution vector
   */
  private static double[] solveThreeByThree(double[][] matrix, double[] rightHandSide) {
    double[][] augmented = new double[3][4];
    for (int row = 0; row < 3; row++) {
      System.arraycopy(matrix[row], 0, augmented[row], 0, 3);
      augmented[row][3] = rightHandSide[row];
    }
    for (int column = 0; column < 3; column++) {
      int pivot = column;
      double largest = Math.abs(augmented[column][column]);
      for (int row = column + 1; row < 3; row++) {
        double candidate = Math.abs(augmented[row][column]);
        if (candidate > largest) {
          largest = candidate;
          pivot = row;
        }
      }
      if (!(largest > 1.0e-20) || !Double.isFinite(largest)) {
        throw new IllegalStateException("INVENTORY_SEPARATE_ENERGY_JACOBIAN_SINGULAR");
      }
      double[] swap = augmented[column];
      augmented[column] = augmented[pivot];
      augmented[pivot] = swap;
      for (int row = column + 1; row < 3; row++) {
        double factor = augmented[row][column] / augmented[column][column];
        for (int entry = column; entry < 4; entry++) {
          augmented[row][entry] -= factor * augmented[column][entry];
        }
      }
    }
    double[] solution = new double[3];
    for (int row = 2; row >= 0; row--) {
      double value = augmented[row][3];
      for (int column = row + 1; column < 3; column++) {
        value -= augmented[row][column] * solution[column];
      }
      solution[row] = value / augmented[row][row];
      if (!Double.isFinite(solution[row])) {
        throw new IllegalStateException("INVENTORY_SEPARATE_ENERGY_JACOBIAN_SINGULAR");
      }
    }
    return solution;
  }

  /**
   * Returns the maximum absolute vector entry.
   *
   * @param values finite values
   * @return maximum absolute value
   */
  private static double maximumAbsolute(double[] values) {
    double maximum = 0.0;
    for (double value : values) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }

  /**
   * Evaluates forced homogeneous phases for one common pressure and temperature.
   *
   * @param masses retained phase/component masses
   * @param pressurePa absolute pressure in Pa
   * @param temperatureK common phase temperature in K
   * @return immutable evaluated state
   */
  private State evaluate(double[][] masses, double pressurePa, double temperatureK) {
    SystemInterface gas = createPhaseState(masses[GAS], PhaseType.GAS, pressurePa, temperatureK);
    SystemInterface liquid = createPhaseState(masses[LIQUID], liquidPhaseType, pressurePa, temperatureK);
    double volume = gas.getVolume("m3") + liquid.getVolume("m3");
    double energy = gas.getInternalEnergy("J") + liquid.getInternalEnergy("J");
    if (!(volume > 0.0) || !Double.isFinite(volume) || !Double.isFinite(energy)) {
      throw new IllegalStateException("INVENTORY_NON_EQUILIBRIUM_STATE_INVALID");
    }
    return new State(gas, liquid, pressurePa, temperatureK, temperatureK, volume, gas.getInternalEnergy("J"),
        liquid.getInternalEnergy("J"));
  }

  /**
   * Evaluates forced homogeneous phases at one pressure and independent temperatures.
   *
   * @param masses retained phase/component masses
   * @param pressurePa common absolute pressure in Pa
   * @param gasTemperatureK gas temperature in K
   * @param liquidTemperatureK liquid temperature in K
   * @return immutable evaluated state
   */
  private State evaluateSeparate(double[][] masses, double pressurePa, double gasTemperatureK,
      double liquidTemperatureK) {
    SystemInterface gas = createPhaseState(masses[GAS], PhaseType.GAS, pressurePa, gasTemperatureK);
    SystemInterface liquid = createPhaseState(masses[LIQUID], liquidPhaseType, pressurePa, liquidTemperatureK);
    double volume = gas.getVolume("m3") + liquid.getVolume("m3");
    double gasEnergy = gas.getInternalEnergy("J");
    double liquidEnergy = liquid.getInternalEnergy("J");
    if (!(volume > 0.0) || !Double.isFinite(volume) || !Double.isFinite(gasEnergy) || !Double.isFinite(liquidEnergy)) {
      throw new IllegalStateException("INVENTORY_NON_EQUILIBRIUM_STATE_INVALID");
    }
    return new State(gas, liquid, pressurePa, gasTemperatureK, liquidTemperatureK, volume, gasEnergy, liquidEnergy);
  }

  /**
   * Creates one forced homogeneous EOS phase without a TP flash.
   *
   * @param componentMassKg named component masses in fixed slate order
   * @param phaseType forced native phase identity
   * @param pressurePa absolute pressure in Pa
   * @param temperatureK temperature in K
   * @return initialized independent phase state
   */
  private SystemInterface createPhaseState(double[] componentMassKg, PhaseType phaseType, double pressurePa,
      double temperatureK) {
    double[] moles = new double[componentNames.length];
    double totalMoles = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      if (!Double.isFinite(componentMassKg[component]) || componentMassKg[component] < 0.0) {
        throw new IllegalStateException("INVENTORY_PHASE_COMPONENT_INVALID: " + componentNames[component]);
      }
      moles[component] = componentMassKg[component] / componentMolarMassKgMol[component];
      totalMoles += moles[component];
    }
    ReleaseFlowRequest.positive(totalMoles, "retained phase moles");
    SystemInterface fluid = template.clone();
    fluid.setTotalNumberOfMoles(totalMoles);
    fluid.setMolarComposition(moles);
    fluid.setPressure(pressurePa / 1.0e5);
    fluid.setTemperature(temperatureK);
    fluid.setNumberOfPhases(1);
    fluid.setMaxNumberOfPhases(1);
    fluid.setForcePhaseTypes(true);
    fluid.init(0);
    fluid.setNumberOfPhases(1);
    fluid.setBeta(0, 1.0);
    fluid.setPhaseType(0, phaseType);
    fluid.initProperties();
    return fluid;
  }

  /**
   * Calculates the selected release and verifies state identity and energy closure.
   *
   * @param current committed or candidate state
   * @return usable release result
   */
  private ReleaseFlowResult calculate(State current) {
    requireGeometry(current);
    SystemInterface withdrawal = selectedState(current);
    ReleaseFlowResult result = releaseModel.calculate(request(current));
    if (result == null || !result.isUsable() || !releaseModel.getModelId().equals(result.getModelId())
        || !releaseModel.getModelVersion().equals(result.getModelVersion())) {
      throw new IllegalStateException("INVENTORY_RELEASE_FAILED");
    }
    for (Diagnostic diagnostic : result.getDiagnostics()) {
      if ("SCREENING_ONLY".equals(diagnostic.getCode()) || "UNRESOLVED_STATIONS".equals(diagnostic.getCode())) {
        throw new IllegalStateException("INVENTORY_SCREENING_UNSUPPORTED");
      }
    }
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    close(upstream.getPressurePa(), current.pressurePa, current.pressurePa, "RELEASE_STATE_MISMATCH");
    close(upstream.getTemperatureK(), selectedTemperatureK(current), selectedTemperatureK(current),
        "RELEASE_STATE_MISMATCH");
    close(upstream.getEnthalpyJkg(), withdrawal.getEnthalpy("J/kg"), Math.abs(withdrawal.getEnthalpy("J/kg")),
        "RELEASE_ENERGY_MISMATCH");
    ReleaseState exit = result.getStations().get(Station.ORIFICE_EXIT);
    close(exit.getEnthalpyJkg() + 0.5 * exit.getVelocityMs() * exit.getVelocityMs(), upstream.getEnthalpyJkg(),
        Math.max(Math.abs(upstream.getEnthalpyJkg()), exit.getVelocityMs() * exit.getVelocityMs()),
        "RELEASE_ENERGY_MISMATCH");
    return result;
  }

  /**
   * Creates the immutable release request from the retained withdrawal phase.
   *
   * @param current committed state
   * @return release request
   */
  private ReleaseFlowRequest request(State current) {
    return new ReleaseFlowRequest(selectedState(current), diameterM, dischargeCoefficient, backPressurePa,
        flowPathLengthM, darcyFrictionFactor);
  }

  /**
   * Verifies live phase geometry and opening ownership.
   *
   * @param current evaluated state
   * @return live geometry assessment
   */
  private ReleaseInventoryPhaseGeometry.Assessment requireGeometry(State current) {
    ReleaseInventoryPhaseGeometry.Assessment assessment = geometry.assess(current.gas.getVolume("m3"),
        current.liquid.getVolume("m3"), liquidPhaseType);
    if (!assessment.isInterfaceBandClear() || assessment.getPhaseAtOpening() != withdrawalPhaseType) {
      throw new IllegalStateException("INVENTORY_GEOMETRY_OPENING_PHASE_MISMATCH");
    }
    return assessment;
  }

  /**
   * Returns the selected forced phase.
   *
   * @param current evaluated state
   * @return gas or liquid withdrawal state
   */
  private SystemInterface selectedState(State current) {
    return withdrawalIndex() == GAS ? current.gas : current.liquid;
  }

  /**
   * Returns the selected phase temperature.
   *
   * @param current evaluated state
   * @return selected phase temperature in K
   */
  private double selectedTemperatureK(State current) {
    return withdrawalIndex() == GAS ? current.gasTemperatureK : current.liquidTemperatureK;
  }

  /**
   * Returns the retained phase index selected for release.
   *
   * @return gas=0 or liquid=1
   */
  private int withdrawalIndex() {
    return withdrawalPhaseType == PhaseType.GAS ? GAS : LIQUID;
  }

  /**
   * Validates an explicit release phase.
   *
   * @param type caller phase type
   * @return validated type
   */
  private static PhaseType requireWithdrawalPhase(PhaseType type) {
    if (!(type == PhaseType.GAS || type == PhaseType.OIL || type == PhaseType.LIQUID || type == PhaseType.AQUEOUS)) {
      throw new IllegalArgumentException("INVENTORY_WITHDRAWAL_PHASE_UNSUPPORTED");
    }
    return type;
  }

  /**
   * Validates exactly one gas and one supported liquid phase.
   *
   * @param fluid initialized equilibrium initial fluid
   * @return retained native liquid phase type
   */
  private static PhaseType requireTwoPhaseState(SystemInterface fluid) {
    PhaseType liquid = null;
    int gasCount = 0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseType type = fluid.getPhase(phase).getType();
      if (fluid.getPhase(phase).getMass() <= 0.0) {
        continue;
      }
      if (type == PhaseType.GAS) {
        gasCount++;
      } else if (type == PhaseType.OIL || type == PhaseType.LIQUID || type == PhaseType.AQUEOUS) {
        if (liquid != null) {
          throw new IllegalArgumentException("INVENTORY_NON_EQUILIBRIUM_MULTIPLE_LIQUIDS_UNSUPPORTED");
        }
        liquid = type;
      } else {
        throw new IllegalArgumentException("INVENTORY_NON_EQUILIBRIUM_PHASE_UNSUPPORTED: " + type);
      }
    }
    if (gasCount != 1 || liquid == null) {
      throw new IllegalArgumentException("INVENTORY_NON_EQUILIBRIUM_TWO_PHASE_REQUIRED");
    }
    return liquid;
  }

  /**
   * Extracts initial gas and liquid component masses.
   *
   * @param fluid scaled equilibrium initial state
   * @param liquidType retained liquid identity
   * @return phase/component masses in kg
   */
  private double[][] extractPhaseComponentMasses(SystemInterface fluid, PhaseType liquidType) {
    double[][] masses = new double[PHASES][componentNames.length];
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface source = fluid.getPhase(phase);
      int target = source.getType() == PhaseType.GAS ? GAS : source.getType() == liquidType ? LIQUID : -1;
      if (target < 0 || source.getMass() <= 0.0) {
        continue;
      }
      for (int component = 0; component < componentNames.length; component++) {
        masses[target][component] = source.getComponent(componentNames[component]).getNumberOfMolesInPhase()
            * componentMolarMassKgMol[component];
      }
    }
    requirePositivePhases(masses);
    return masses;
  }

  /**
   * Rejects phase disappearance before EOS evaluation.
   *
   * @param masses phase/component masses
   */
  private static void requirePositivePhases(double[][] masses) {
    for (int phase = 0; phase < PHASES; phase++) {
      double total = 0.0;
      for (double amount : masses[phase]) {
        if (!Double.isFinite(amount) || amount < -1.0e-14) {
          throw new IllegalStateException("INVENTORY_NON_EQUILIBRIUM_COMPONENT_OVERDRAW");
        }
        total += Math.max(0.0, amount);
      }
      if (!(total > 1.0e-12)) {
        throw new IllegalStateException("INVENTORY_PHASE_DISAPPEARANCE_UNSUPPORTED");
      }
    }
  }

  /**
   * Converts one fixed-order component vector to an immutable named map.
   *
   * @param values component values in fixed slate order
   * @return immutable named values
   */
  private Map<String, Double> componentMap(double[] values) {
    TreeMap<String, Double> result = new TreeMap<String, Double>();
    for (int component = 0; component < componentNames.length; component++) {
      result.put(componentNames[component], values[component]);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Sums both retained phases by named component.
   *
   * @param masses phase/component values
   * @return named total component masses in kg
   */
  private Map<String, Double> totalComponentMasses(double[][] masses) {
    TreeMap<String, Double> result = new TreeMap<String, Double>();
    for (int component = 0; component < componentNames.length; component++) {
      result.put(componentNames[component], masses[GAS][component] + masses[LIQUID][component]);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Creates a mutable zero-valued component ledger.
   *
   * @return zero ledger
   */
  private TreeMap<String, Double> zeroComponentMap() {
    TreeMap<String, Double> result = new TreeMap<String, Double>();
    for (String name : componentNames) {
      result.put(name, 0.0);
    }
    return result;
  }

  /**
   * Sums one retained phase mass.
   *
   * @param masses phase/component values
   * @param phase retained phase index
   * @return phase mass in kg
   */
  private static double phaseMass(double[][] masses, int phase) {
    double result = 0.0;
    for (double amount : masses[phase]) {
      result += amount;
    }
    return result;
  }

  /**
   * Sums total retained mass.
   *
   * @param masses phase/component values
   * @return total mass in kg
   */
  private static double totalMass(double[][] masses) {
    return phaseMass(masses, GAS) + phaseMass(masses, LIQUID);
  }

  /**
   * Copies a two-dimensional primitive matrix.
   *
   * @param values source matrix
   * @return deep copy
   */
  private static double[][] copy(double[][] values) {
    double[][] result = new double[values.length][];
    for (int i = 0; i < values.length; i++) {
      result[i] = values[i].clone();
    }
    return result;
  }

  /**
   * Verifies one scaled numerical closure.
   *
   * @param actual actual value
   * @param expected target value
   * @param scale physical scale
   * @param code machine-readable failure code
   */
  private static void close(double actual, double expected, double scale, String code) {
    if (!Double.isFinite(actual) || !Double.isFinite(expected)
        || Math.abs(actual - expected) > CLOSURE_TOLERANCE * Math.max(1.0e-10, Math.abs(scale))) {
      throw new IllegalStateException(code + ": actual=" + actual + ", target=" + expected + ", scale=" + scale);
    }
  }

  /** @return defensive clone of the retained gas phase */
  public synchronized SystemInterface getGasPhase() {
    return state.gas.clone();
  }

  /** @return defensive clone of the retained liquid phase */
  public synchronized SystemInterface getLiquidPhase() {
    return state.liquid.clone();
  }

  /**
   * Returns the selected phase used by the equipment-level thermodynamic accessor.
   *
   * <p>
   * A single {@link SystemInterface} cannot represent two independently retained compositions without reflashing them.
   * Whole-inventory users must therefore use {@link #getGasPhase()}, {@link #getLiquidPhase()} and
   * {@link #getBalance()} rather than treating this selected-phase view as an equilibrium bulk state.
   * </p>
   *
   * @return defensive clone of the selected withdrawal phase
   */
  @Override
  public synchronized SystemInterface getThermoSystem() {
    return selectedState(state).clone();
  }

  /** @return defensive clone of the selected withdrawal phase */
  public synchronized SystemInterface getFluid() {
    return getThermoSystem();
  }

  /** {@inheritDoc} */
  @Override
  public synchronized ReleaseFlowRequest getReleaseRequest() {
    return request(state);
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowModel getReleaseModel() {
    return releaseModel;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized ReleaseFlowResult getReleaseResult() {
    return calculate(state);
  }

  /** {@inheritDoc} */
  @Override
  public synchronized boolean isReleaseEnabled() {
    return releaseEnabled;
  }

  /**
   * Opens or closes release while retaining heat and interphase evolution.
   *
   * @param enabled true to release the selected phase
   */
  public synchronized void setReleaseEnabled(boolean enabled) {
    releaseEnabled = enabled;
    setCalculationIdentifier(null);
  }

  /** {@inheritDoc} */
  @Override
  public String getReleaseBasis() {
    return interphaseHeatTransfer == null ? "COUPLED_RIGID_THERMAL_EQUILIBRIUM_FINITE_RATE_PHASE_INVENTORY"
        : "COUPLED_RIGID_SEPARATE_PHASE_ENERGY_FINITE_RATE_PHASE_INVENTORY";
  }

  /** {@inheritDoc} */
  @Override
  public synchronized Map<String, String> getReleaseProvenance() {
    ReleaseInventoryPhaseGeometry.Assessment assessment = requireGeometry(state);
    TreeMap<String, String> provenance = new TreeMap<String, String>();
    provenance.put("inventoryTimeS", Double.toString(getTime()));
    provenance.put("inventoryVolumeM3", Double.toString(volumeM3));
    provenance.put("inventoryPressurePa", Double.toString(state.pressurePa));
    provenance.put("inventoryTemperatureK", Double.toString(selectedTemperatureK(state)));
    provenance.put("inventoryGasTemperatureK", Double.toString(state.gasTemperatureK));
    provenance.put("inventoryLiquidTemperatureK", Double.toString(state.liquidTemperatureK));
    provenance.put("inventoryGasMassKg", Double.toString(state.gas.getMass("kg")));
    provenance.put("inventoryLiquidMassKg", Double.toString(state.liquid.getMass("kg")));
    provenance.put("inventoryLiquidPhaseType", liquidPhaseType.name());
    provenance.put("inventoryWithdrawalPhase", withdrawalPhaseType.name());
    provenance.put("inventoryInterfacialAreaM2", Double.toString(assessment.getInterfacialAreaM2()));
    provenance.put("inventoryInterphaseTransferModel", "PRESCRIBED_FUGACITY_FILM_V1");
    provenance.put("inventoryInterphaseTransferProvenance", interphaseTransfer.getProvenance());
    provenance.put("inventoryInterphaseCoefficientsMS", interphaseTransfer.getCoefficientsMS().toString());
    provenance.put("inventoryInterphaseMaximumDonorFraction",
        Double.toString(interphaseTransfer.getMaximumDonorFraction()));
    provenance.put("inventoryCumulativeInterphaseTransferredMassKg", Double.toString(interphaseTransferredMassKg));
    provenance.put("inventoryCumulativeInterphaseLatentEnergyJ", Double.toString(interphaseLatentEnergyJ));
    provenance.put("inventoryCumulativeInterphaseHeatTransferToGasJ", Double.toString(interphaseHeatTransferEnergyJ));
    provenance.put("inventoryDonorLimitedComponentEvents", Integer.toString(donorLimitedComponents));
    provenance.put("inventoryHeatTransferType", heatTransfer.getType().name());
    provenance.put("inventoryHeatTransferProvenance", heatTransfer.getProvenance());
    provenance.put("inventoryCumulativeReleasedMassKg", Double.toString(releasedMassKg));
    provenance.put("inventoryCumulativeReleasedEnthalpyJ", Double.toString(releasedEnergyJ));
    provenance.put("inventoryCumulativeExternalHeatTransferJ", Double.toString(heatTransferEnergyJ));
    provenance.put("inventoryIntegrator",
        interphaseHeatTransfer == null ? "EXPLICIT_EULER_RETAINED_PHASE_COMPONENT_VU_V1"
            : "EXPLICIT_EULER_RETAINED_PHASE_COMPONENT_VUGUL_V1");
    provenance.put("inventoryMaximumSubstepS", Double.toString(maxSubstepS));
    provenance.put("inventoryLastSubsteps", Integer.toString(lastSubsteps));
    provenance.put("inventoryLastStateEvaluations", Integer.toString(lastStateSolves));
    provenance.put("inventoryPressureEquilibrationEvent", Boolean.toString(lastPressureEquilibrationEvent));
    provenance.put("inventoryThermalBoundary",
        interphaseHeatTransfer == null ? "COMMON_PHASE_TEMPERATURE" : "SEPARATE_PHASE_ENERGY");
    provenance.put("inventoryExternalHeatAllocation",
        interphaseHeatTransfer == null ? "WHOLE_INVENTORY" : "WITHDRAWAL_PHASE");
    provenance.put("inventoryInterphaseHeatTransferType",
        interphaseHeatTransfer == null ? "NOT_APPLICABLE" : interphaseHeatTransfer.getType().name());
    provenance.put("inventoryInterphaseHeatTransferProvenance",
        interphaseHeatTransfer == null ? "not-applicable:common-temperature" : interphaseHeatTransfer.getProvenance());
    provenance.put("inventoryInterphaseHeatConductanceWK",
        interphaseHeatTransfer == null ? "0.0" : Double.toString(interphaseHeatTransfer.getConductanceWK()));
    provenance.put("inventoryPhaseLifecycle", "PHASE_DISAPPEARANCE_FAILS_CLOSED");
    provenance.put("rateTimeBasis", "INSTANTANEOUS_AT_FRAME_TIME");
    return Collections.unmodifiableMap(provenance);
  }

  /** @return immutable accounting and retained phase-component snapshot */
  public synchronized Balance getBalance() {
    return new Balance(getTime(), volumeM3, initialEnergyJ, state.internalEnergyJ, releasedEnergyJ, heatTransferEnergyJ,
        interphaseLatentEnergyJ, interphaseHeatTransferEnergyJ, state.gasInternalEnergyJ, state.liquidInternalEnergyJ,
        state.gasTemperatureK, state.liquidTemperatureK, releasedMassKg, interphaseTransferredMassKg,
        initialComponentMassKg, totalComponentMasses(phaseComponentMassKg), releasedComponentMassKg,
        componentMap(phaseComponentMassKg[GAS]), componentMap(phaseComponentMassKg[LIQUID]), lastSubsteps);
  }

  /** Immutable accounting snapshot with explicit SI units. */
  public static final class Balance implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double timeS;
    private final double volumeM3;
    private final double initialEnergyJ;
    private final double internalEnergyJ;
    private final double releasedEnergyJ;
    private final double heatTransferEnergyJ;
    private final double interphaseLatentEnergyJ;
    private final double interphaseHeatTransferEnergyJ;
    private final double gasInternalEnergyJ;
    private final double liquidInternalEnergyJ;
    private final double gasTemperatureK;
    private final double liquidTemperatureK;
    private final double releasedMassKg;
    private final double interphaseTransferredMassKg;
    private final Map<String, Double> initialComponentMassKg;
    private final Map<String, Double> remainingComponentMassKg;
    private final Map<String, Double> releasedComponentMassKg;
    private final Map<String, Double> gasComponentMassKg;
    private final Map<String, Double> liquidComponentMassKg;
    private final int substeps;

    /**
     * Creates one immutable balance.
     *
     * @param timeS simulation time in s
     * @param volumeM3 rigid volume in m3
     * @param initialEnergyJ initial internal energy in J
     * @param internalEnergyJ current internal energy in J
     * @param releasedEnergyJ cumulative released enthalpy in J
     * @param heatTransferEnergyJ cumulative external heat input in J
     * @param interphaseLatentEnergyJ cumulative signed latent-energy diagnostic in J
     * @param interphaseHeatTransferEnergyJ cumulative signed interfacial heat entering gas in J
     * @param gasInternalEnergyJ gas internal energy in J
     * @param liquidInternalEnergyJ liquid internal energy in J
     * @param gasTemperatureK gas temperature in K
     * @param liquidTemperatureK liquid temperature in K
     * @param releasedMassKg cumulative released mass in kg
     * @param interphaseTransferredMassKg cumulative absolute interphase component transfer in kg
     * @param initial initial component masses in kg
     * @param remaining remaining component masses in kg
     * @param released released component masses in kg
     * @param gas gas-held component masses in kg
     * @param liquid liquid-held component masses in kg
     * @param substeps accepted substeps in the last call
     */
    private Balance(double timeS, double volumeM3, double initialEnergyJ, double internalEnergyJ,
        double releasedEnergyJ, double heatTransferEnergyJ, double interphaseLatentEnergyJ,
        double interphaseHeatTransferEnergyJ, double gasInternalEnergyJ, double liquidInternalEnergyJ,
        double gasTemperatureK, double liquidTemperatureK, double releasedMassKg, double interphaseTransferredMassKg,
        Map<String, Double> initial, Map<String, Double> remaining, Map<String, Double> released,
        Map<String, Double> gas, Map<String, Double> liquid, int substeps) {
      this.timeS = timeS;
      this.volumeM3 = volumeM3;
      this.initialEnergyJ = initialEnergyJ;
      this.internalEnergyJ = internalEnergyJ;
      this.releasedEnergyJ = releasedEnergyJ;
      this.heatTransferEnergyJ = heatTransferEnergyJ;
      this.interphaseLatentEnergyJ = interphaseLatentEnergyJ;
      this.interphaseHeatTransferEnergyJ = interphaseHeatTransferEnergyJ;
      this.gasInternalEnergyJ = gasInternalEnergyJ;
      this.liquidInternalEnergyJ = liquidInternalEnergyJ;
      this.gasTemperatureK = gasTemperatureK;
      this.liquidTemperatureK = liquidTemperatureK;
      this.releasedMassKg = releasedMassKg;
      this.interphaseTransferredMassKg = interphaseTransferredMassKg;
      this.initialComponentMassKg = immutable(initial);
      this.remainingComponentMassKg = immutable(remaining);
      this.releasedComponentMassKg = immutable(released);
      this.gasComponentMassKg = immutable(gas);
      this.liquidComponentMassKg = immutable(liquid);
      this.substeps = substeps;
    }

    /**
     * Creates an immutable sorted map.
     *
     * @param values source map
     * @return immutable copy
     */
    private static Map<String, Double> immutable(Map<String, Double> values) {
      return Collections.unmodifiableMap(new TreeMap<String, Double>(values));
    }

    /** @return simulation time in s */
    public double getTimeS() {
      return timeS;
    }

    /** @return rigid volume in m3 */
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

    /** @return cumulative released enthalpy in J */
    public double getReleasedEnergyJ() {
      return releasedEnergyJ;
    }

    /** @return cumulative external heat input in J */
    public double getHeatTransferEnergyJ() {
      return heatTransferEnergyJ;
    }

    /** @return cumulative signed latent-energy diagnostic in J */
    public double getInterphaseLatentEnergyJ() {
      return interphaseLatentEnergyJ;
    }

    /** @return cumulative signed interfacial heat entering gas in J */
    public double getInterphaseHeatTransferEnergyJ() {
      return interphaseHeatTransferEnergyJ;
    }

    /** @return current gas internal energy in J */
    public double getGasInternalEnergyJ() {
      return gasInternalEnergyJ;
    }

    /** @return current liquid internal energy in J */
    public double getLiquidInternalEnergyJ() {
      return liquidInternalEnergyJ;
    }

    /** @return current gas temperature in K */
    public double getGasTemperatureK() {
      return gasTemperatureK;
    }

    /** @return current liquid temperature in K */
    public double getLiquidTemperatureK() {
      return liquidTemperatureK;
    }

    /** @return cumulative released mass in kg */
    public double getReleasedMassKg() {
      return releasedMassKg;
    }

    /** @return cumulative absolute interphase component transfer in kg */
    public double getInterphaseTransferredMassKg() {
      return interphaseTransferredMassKg;
    }

    /** @return immutable initial component masses in kg */
    public Map<String, Double> getInitialComponentMassKg() {
      return initialComponentMassKg;
    }

    /** @return immutable remaining component masses in kg */
    public Map<String, Double> getRemainingComponentMassKg() {
      return remainingComponentMassKg;
    }

    /** @return immutable released component masses in kg */
    public Map<String, Double> getReleasedComponentMassKg() {
      return releasedComponentMassKg;
    }

    /** @return immutable gas-held component masses in kg */
    public Map<String, Double> getGasComponentMassKg() {
      return gasComponentMassKg;
    }

    /** @return immutable liquid-held component masses in kg */
    public Map<String, Double> getLiquidComponentMassKg() {
      return liquidComponentMassKg;
    }

    /** @return accepted substeps in the last call */
    public int getSubsteps() {
      return substeps;
    }
  }

  /** Immutable evaluated common-pressure state. */
  private static final class State implements Serializable {
    private static final long serialVersionUID = 1L;
    private final SystemInterface gas;
    private final SystemInterface liquid;
    private final double pressurePa;
    private final double gasTemperatureK;
    private final double liquidTemperatureK;
    private final double volumeM3;
    private final double gasInternalEnergyJ;
    private final double liquidInternalEnergyJ;
    private final double internalEnergyJ;

    /**
     * Creates one evaluated state.
     *
     * @param gas forced gas phase
     * @param liquid forced liquid phase
     * @param pressurePa common absolute pressure in Pa
     * @param gasTemperatureK gas temperature in K
     * @param liquidTemperatureK liquid temperature in K
     * @param volumeM3 total phase volume in m3
     * @param gasInternalEnergyJ gas internal energy in J
     * @param liquidInternalEnergyJ liquid internal energy in J
     */
    private State(SystemInterface gas, SystemInterface liquid, double pressurePa, double gasTemperatureK,
        double liquidTemperatureK, double volumeM3, double gasInternalEnergyJ, double liquidInternalEnergyJ) {
      this.gas = gas;
      this.liquid = liquid;
      this.pressurePa = pressurePa;
      this.gasTemperatureK = gasTemperatureK;
      this.liquidTemperatureK = liquidTemperatureK;
      this.volumeM3 = volumeM3;
      this.gasInternalEnergyJ = gasInternalEnergyJ;
      this.liquidInternalEnergyJ = liquidInternalEnergyJ;
      this.internalEnergyJ = gasInternalEnergyJ + liquidInternalEnergyJ;
    }
  }

  /** Immutable nonlinear-solve result. */
  private static final class SolveResult {
    private final State state;
    private final int evaluations;

    /**
     * Creates one solve result.
     *
     * @param state converged state
     * @param evaluations EOS state evaluations
     */
    private SolveResult(State state, int evaluations) {
      this.state = state;
      this.evaluations = evaluations;
    }
  }

  /** Immutable candidate substep. */
  private static final class Step {
    private final double[][] phaseComponentMassKg;
    private final State state;
    private final Map<String, Double> removedComponentMassKg;
    private final double removedMassKg;
    private final double outflowEnergyJ;
    private final double heatTransferEnergyJ;
    private final double interphaseLatentEnergyJ;
    private final double interphaseHeatTransferEnergyJ;
    private final double absoluteTransferredMassKg;
    private final int donorLimitedComponents;
    private final double durationS;
    private final int stateSolves;

    /**
     * Creates one checked candidate step.
     *
     * @param phaseComponentMassKg candidate phase/component masses
     * @param state candidate state
     * @param removedComponentMassKg released component masses
     * @param removedMassKg released mass
     * @param outflowEnergyJ released enthalpy
     * @param heatTransferEnergyJ external heat
     * @param interphaseLatentEnergyJ latent diagnostic
     * @param interphaseHeatTransferEnergyJ signed interfacial heat entering gas in J
     * @param absoluteTransferredMassKg absolute component transfer
     * @param donorLimitedComponents donor-limit count
     * @param durationS duration
     * @param stateSolves EOS evaluations
     */
    private Step(double[][] phaseComponentMassKg, State state, Map<String, Double> removedComponentMassKg,
        double removedMassKg, double outflowEnergyJ, double heatTransferEnergyJ, double interphaseLatentEnergyJ,
        double interphaseHeatTransferEnergyJ, double absoluteTransferredMassKg, int donorLimitedComponents,
        double durationS, int stateSolves) {
      this.phaseComponentMassKg = phaseComponentMassKg;
      this.state = state;
      this.removedComponentMassKg = removedComponentMassKg;
      this.removedMassKg = removedMassKg;
      this.outflowEnergyJ = outflowEnergyJ;
      this.heatTransferEnergyJ = heatTransferEnergyJ;
      this.interphaseLatentEnergyJ = interphaseLatentEnergyJ;
      this.interphaseHeatTransferEnergyJ = interphaseHeatTransferEnergyJ;
      this.absoluteTransferredMassKg = absoluteTransferredMassKg;
      this.donorLimitedComponents = donorLimitedComponents;
      this.durationS = durationS;
      this.stateSolves = stateSolves;
    }
  }

  /** Located pressure event. */
  private static final class PressureEvent {
    private final Step step;
    private final int additionalStateSolves;

    /**
     * Creates a located event.
     *
     * @param step event step
     * @param additionalStateSolves additional EOS evaluations
     */
    private PressureEvent(Step step, int additionalStateSolves) {
      this.step = step;
      this.additionalStateSolves = additionalStateSolves;
    }
  }
}
