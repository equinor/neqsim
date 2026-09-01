/*
 * OnePhasePipeLine.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsolver.AdvectionScheme;
import neqsim.fluidmechanics.flowsolver.SpeciesAdvectionScheme;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFlowConvergenceReport;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.OnePhaseSpeciesConservationHistory;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * One-phase pipeline with compositional tracking support.
 *
 * <p>
 * This class wraps {@link PipeFlowSystem} for use in process simulations. It supports both steady-state and transient
 * simulations, including compositional tracking for scenarios like gas switching (e.g., natural gas to nitrogen
 * transitions).
 * </p>
 *
 * <h2>Validated conservative compositional tracking</h2>
 * <p>
 * Use {@link #setConservativeCompositionalTracking(boolean)} for the validated one-phase, positive-flow, finite-volume
 * species path. It uses solver type 1, keeps component inventories authoritative, synchronizes the thermodynamic
 * composition, and exposes convergence, inventory, profile, and accepted-step history diagnostics.
 * </p>
 * <p>
 * The older {@link #setCompositionalTracking(boolean)} route selects staged solver type 20. It remains available for
 * compatibility, but is not the validated conservative hydraulic/EOS path. Schemes configured through
 * {@link #setAdvectionScheme(AdvectionScheme)} belong to that legacy route and are not applied by the conservative
 * mode. Select the conservative transport scheme independently with
 * {@link #setSpeciesAdvectionScheme(SpeciesAdvectionScheme)}.
 * </p>
 *
 * <h2>Example: Gas Switching Simulation</h2>
 *
 * <pre>{@code
 * // Create pipeline
 * OnePhasePipeLine pipe = new OnePhasePipeLine("GasPipe", inletStream);
 * pipe.setNumberOfLegs(1);
 * pipe.setNumberOfNodesInLeg(100);
 * pipe.setPipeDiameters(new double[] { 0.3, 0.3 });
 * pipe.setLegPositions(new double[] { 0.0, 5000.0 });
 *
 * pipe.setConservativeCompositionalTracking(true);
 * pipe.setStoreSpeciesConservationHistory(true);
 * pipe.setFailOnNonConvergence(true);
 *
 * // Initialize with steady state
 * pipe.run();
 *
 * // Run a three-interval event with changing inlet composition
 * UUID id = UUID.randomUUID();
 * pipe.runConservativeTransient(new double[] { 0.0, 30.0, 60.0, 90.0 },
 *     new SystemInterface[] { pulseGas, pulseGas, baselineGas }, 1, id);
 * String pythonReadyHistory = pipe.getSpeciesConservationHistory().toJson();
 * }</pre>
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnePhasePipeLine extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Whether to track composition during transient simulation. */
  private boolean compositionalTracking = false;

  /** Whether to use validated conservative species transport with solver type 1. */
  private boolean conservativeCompositionalTracking = false;

  /** Whether the pipe system has been initialized. */
  private boolean initialized = false;

  /** Current simulation time in seconds. */
  private double simulationTime = 0.0;

  /** Time step for internal solver. */
  private double internalTimeStep = 1.0;

  /**
   * Constructor for OnePhasePipeLine.
   *
   * @param inStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public OnePhasePipeLine(StreamInterface inStream) {
    this("OnePhasePipeLine", inStream);
  }

  /**
   * Constructor for OnePhasePipeLine.
   *
   * @param name name of pipe
   */
  public OnePhasePipeLine(String name) {
    super(name);
    pipe = new PipeFlowSystem();
  }

  /**
   * Constructor for OnePhasePipeLine.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public OnePhasePipeLine(String name, StreamInterface inStream) {
    super(name, inStream);
    pipe = new PipeFlowSystem();
  }

  /**
   * Creates the pipe system. Called automatically by run() if not already created.
   */
  public void createSystem() {
    // System is created in parent run() method
  }

  /**
   * Set the advection scheme for compositional tracking.
   *
   * <p>
   * Higher-order schemes reduce numerical dispersion (front spreading) during compositional tracking. For gas switching
   * scenarios, TVD schemes are recommended.
   * </p>
   *
   * @param scheme the advection scheme to use
   * @see AdvectionScheme
   */
  public void setAdvectionScheme(AdvectionScheme scheme) {
    pipe.setAdvectionScheme(scheme);
  }

  /**
   * Get the current advection scheme.
   *
   * @return the advection scheme
   */
  public AdvectionScheme getAdvectionScheme() {
    return pipe.getAdvectionScheme();
  }

  /**
   * Select the finite-volume advection method used by conservative species transport.
   *
   * <p>
   * The default is {@link SpeciesAdvectionScheme#FIRST_ORDER_IMPLICIT}. This setting is independent of the legacy
   * {@link AdvectionScheme} used by staged solver type 20.
   * </p>
   *
   * @param scheme non-null conservative species advection scheme
   */
  public void setSpeciesAdvectionScheme(SpeciesAdvectionScheme scheme) {
    getPipeFlowSystem().setSpeciesAdvectionScheme(scheme);
  }

  /**
   * Get the selected conservative species advection method.
   *
   * @return selected conservative species advection scheme
   */
  public SpeciesAdvectionScheme getSpeciesAdvectionScheme() {
    return getPipeFlowSystem().getSpeciesAdvectionScheme();
  }

  /**
   * Enable or disable compositional tracking during transient simulation.
   *
   * <p>
   * When enabled, the transient solver tracks component mass fractions through the pipe. Use this for gas switching or
   * composition gradient tracking scenarios.
   * </p>
   *
   * @param enable true to enable compositional tracking
   */
  public void setCompositionalTracking(boolean enable) {
    this.compositionalTracking = enable;
  }

  /**
   * Check if compositional tracking is enabled.
   *
   * @return true if compositional tracking is enabled
   */
  public boolean isCompositionalTracking() {
    return compositionalTracking;
  }

  /**
   * Enable or disable validated conservative one-phase compositional tracking.
   *
   * <p>
   * When enabled, steady initialization and transient propagation use solver type 1 with component inventories,
   * hydraulic/EOS coupling, bounded mass fractions, and fail-loud unsupported-flow diagnostics. This mode currently
   * supports a single gas phase with strictly positive flow. If both legacy and conservative tracking flags are true,
   * conservative mode takes precedence.
   * </p>
   *
   * @param enable true to use the validated conservative species path
   */
  public void setConservativeCompositionalTracking(boolean enable) {
    conservativeCompositionalTracking = enable;
    getPipeFlowSystem().setConservativeSpeciesTransport(enable);
  }

  /**
   * Check whether validated conservative compositional tracking is enabled.
   *
   * @return true when solver type 1 conservative species transport is selected
   */
  public boolean isConservativeCompositionalTracking() {
    return conservativeCompositionalTracking;
  }

  /**
   * Configure storage of immutable diagnostics for every accepted conservative step.
   *
   * @param store true to retain the full accepted-step history
   */
  public void setStoreSpeciesConservationHistory(boolean store) {
    getPipeFlowSystem().setStoreSpeciesConservationHistory(store);
  }

  /**
   * Configure whether failed hydraulic/EOS convergence throws after recording diagnostics.
   *
   * @param fail true to fail loudly on non-convergence
   */
  public void setFailOnNonConvergence(boolean fail) {
    getPipeFlowSystem().setFailOnNonConvergence(fail);
  }

  /**
   * Get hydraulic/EOS convergence diagnostics from the latest solve.
   *
   * @return immutable one-phase convergence report
   */
  public OnePhaseFlowConvergenceReport getConvergenceReport() {
    return getPipeFlowSystem().getConvergenceReport();
  }

  /**
   * Get component inventory, boundary-mass, boundedness, and synchronization diagnostics.
   *
   * @return immutable report from the latest conservative step
   */
  public OnePhaseSpeciesConservationReport getSpeciesConservationReport() {
    return getPipeFlowSystem().getSpeciesConservationReport();
  }

  /**
   * Get time-aligned reports from the latest conservative transient call.
   *
   * @return immutable accepted-step history with elapsed times in seconds
   */
  public OnePhaseSpeciesConservationHistory getSpeciesConservationHistory() {
    return getPipeFlowSystem().getSpeciesConservationHistory();
  }

  /**
   * Get the authoritative conservative mass-fraction profile for one component.
   *
   * <p>
   * The returned array contains physical finite-volume cells in inlet-to-outlet order. It is distinct from
   * {@link #getCompositionProfile(String)}, which reconstructs mass fractions from thermodynamic node mole fractions
   * and includes boundary nodes.
   * </p>
   *
   * @param componentName component name, matched case-insensitively
   * @return defensive copy of component mass fraction by physical cell
   * @throws IllegalStateException if conservative species transport has not run
   * @throws IllegalArgumentException if the component is absent from the report
   */
  public double[] getConservativeMassFractionProfile(String componentName) {
    OnePhaseSpeciesConservationReport report = getSpeciesConservationReport();
    String[] componentNames = report.getComponentNames();
    if (componentNames.length == 0) {
      throw new IllegalStateException("Conservative species transport has not produced a component profile.");
    }
    double[][] profiles = report.getMassFractionProfile();
    for (int component = 0; component < componentNames.length; component++) {
      if (componentNames[component].equalsIgnoreCase(componentName)) {
        return profiles[component];
      }
    }
    throw new IllegalArgumentException("Component is absent from conservative species report: " + componentName);
  }

  /**
   * Get the authoritative final total inventory for every physical finite-volume cell.
   *
   * @return defensive copy of total cell inventories in kg, in inlet-to-outlet order
   * @throws IllegalStateException if conservative species transport has not run
   */
  public double[] getConservativeCellInventoryKg() {
    double[] inventory = getSpeciesConservationReport().getFinalCellInventoryKg();
    if (inventory.length == 0) {
      throw new IllegalStateException("Conservative species transport has not produced a cell inventory profile.");
    }
    return inventory;
  }

  /**
   * Get the authoritative final inventory profile for one component.
   *
   * @param componentName component name, matched case-insensitively
   * @return defensive copy of component inventory in kg by physical finite-volume cell
   * @throws IllegalStateException if conservative species transport has not run
   * @throws IllegalArgumentException if the component is absent from the report
   */
  public double[] getConservativeComponentInventoryProfileKg(String componentName) {
    OnePhaseSpeciesConservationReport report = getSpeciesConservationReport();
    String[] componentNames = report.getComponentNames();
    if (componentNames.length == 0) {
      throw new IllegalStateException("Conservative species transport has not produced a component profile.");
    }
    double[][] profiles = report.getFinalComponentCellInventoryKg();
    for (int component = 0; component < componentNames.length; component++) {
      if (componentNames[component].equalsIgnoreCase(componentName)) {
        return profiles[component];
      }
    }
    throw new IllegalArgumentException("Component is absent from conservative species report: " + componentName);
  }

  /**
   * Get the authoritative conservative outlet-cell mass fraction for one component.
   *
   * @param componentName component name, matched case-insensitively
   * @return component mass fraction in the final physical cell
   */
  public double getConservativeOutletMassFraction(String componentName) {
    double[] profile = getConservativeMassFractionProfile(componentName);
    return profile[profile.length - 1];
  }

  /**
   * Get the current simulation time.
   *
   * @return simulation time in seconds
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * Reset the simulation time to zero.
   */
  public void resetSimulationTime() {
    this.simulationTime = 0.0;
  }

  /**
   * Set the internal time step for the solver.
   *
   * @param dt time step in seconds
   */
  public void setInternalTimeStep(double dt) {
    this.internalTimeStep = dt;
  }

  /**
   * Get the internal time step.
   *
   * @return time step in seconds
   */
  public double getInternalTimeStep() {
    return internalTimeStep;
  }

  /**
   * Get the composition profile along the pipe for a specific component.
   *
   * @param componentName name of the component
   * @return array of mass fractions at each node
   */
  public double[] getCompositionProfile(String componentName) {
    int nNodes = pipe.getTotalNumberOfNodes();
    double[] profile = new double[nNodes];

    for (int i = 0; i < nNodes; i++) {
      SystemInterface nodeSystem = pipe.getNode(i).getBulkSystem();
      int compIndex = nodeSystem.getPhase(0).getComponent(componentName).getComponentNumber();
      double x = nodeSystem.getPhase(0).getComponent(compIndex).getx();
      double molarMass = nodeSystem.getPhase(0).getComponent(compIndex).getMolarMass();
      double avgMolarMass = nodeSystem.getPhase(0).getMolarMass();
      profile[i] = x * molarMass / avgMolarMass;
    }

    return profile;
  }

  /**
   * Get the pressure profile along the pipe.
   *
   * @param unit pressure unit (e.g., "bara", "Pa")
   * @return array of pressures at each node
   */
  public double[] getPressureProfile(String unit) {
    int nNodes = pipe.getTotalNumberOfNodes();
    double[] profile = new double[nNodes];

    for (int i = 0; i < nNodes; i++) {
      double pressure = pipe.getNode(i).getBulkSystem().getPressure();
      if ("bara".equalsIgnoreCase(unit)) {
        profile[i] = pressure;
      } else if ("Pa".equalsIgnoreCase(unit)) {
        profile[i] = pressure * 1e5;
      } else {
        profile[i] = pressure; // default to bara
      }
    }

    return profile;
  }

  /**
   * Get the temperature profile along the pipe.
   *
   * @param unit temperature unit (e.g., "K", "C")
   * @return array of temperatures at each node
   */
  public double[] getTemperatureProfile(String unit) {
    int nNodes = pipe.getTotalNumberOfNodes();
    double[] profile = new double[nNodes];

    for (int i = 0; i < nNodes; i++) {
      double temp = pipe.getNode(i).getBulkSystem().getTemperature();
      if ("C".equalsIgnoreCase(unit)) {
        profile[i] = temp - 273.15;
      } else {
        profile[i] = temp; // default to K
      }
    }

    return profile;
  }

  /**
   * Get the velocity profile along the pipe.
   *
   * @return array of velocities (m/s) at each node
   */
  public double[] getVelocityProfile() {
    int nNodes = pipe.getTotalNumberOfNodes();
    double[] profile = new double[nNodes];

    for (int i = 0; i < nNodes; i++) {
      profile[i] = pipe.getNode(i).getVelocity();
    }

    return profile;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    UUID oldid = getCalculationIdentifier();
    super.run(id);
    setCalculationIdentifier(oldid);
    pipe.solveSteadyState(conservativeCompositionalTracking ? 1 : 10, id);
    initialized = true;
    simulationTime = 0.0;

    // Update outlet stream
    updateOutletStream();
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Run transient simulation for the specified time step.
   *
   * <p>
   * This method advances the pipe simulation by the specified time step and updates the outlet stream with the current
   * outlet conditions. The inlet boundary is updated from the current inlet stream state.
   * </p>
   *
   * <p>
   * If compositional tracking is enabled, the solver tracks component mass fractions through the pipe using the
   * selected advection scheme.
   * </p>
   *
   * @param dt time step in seconds
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (conservativeCompositionalTracking) {
      if (!Double.isFinite(dt) || dt <= 0.0) {
        throw new IllegalArgumentException("Conservative transient duration must be finite and positive: " + dt);
      }
      if (!Double.isFinite(internalTimeStep) || internalTimeStep <= 0.0) {
        throw new IllegalStateException(
            "Conservative internal timestep must be finite and positive: " + internalTimeStep);
      }
      int steps = (int) Math.ceil(dt / internalTimeStep);
      runConservativeTransient(new double[] { 0.0, dt }, new SystemInterface[] { inStream.getThermoSystem().clone() },
          steps, id);
      return;
    }

    // Initialize if not already done
    if (!initialized) {
      run(id);
    }

    // Update inlet boundary from current inlet stream
    updateInletBoundary();

    // Select solver type: 20 = compositional, 2 = momentum
    int solverType = compositionalTracking ? 20 : 2;

    // Run transient solver
    // The pipe uses internal time stepping, we need to advance by dt
    double timeRemaining = dt;
    while (timeRemaining > 0) {
      double stepDt = Math.min(internalTimeStep, timeRemaining);

      // Set up time series for single step
      double[] times = { simulationTime, simulationTime + stepDt };
      SystemInterface[] systems = { inStream.getThermoSystem().clone(), inStream.getThermoSystem().clone() };

      pipe.getTimeSeries().setTimes(times);
      pipe.getTimeSeries().setInletThermoSystems(systems);
      pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);

      pipe.solveTransient(solverType, id);

      simulationTime += stepDt;
      timeRemaining -= stepDt;
    }

    // Update outlet stream with current outlet conditions
    updateOutletStream();
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Run a validated conservative composition schedule in one time-aligned solve.
   *
   * <p>
   * Times are elapsed seconds relative to the start of this call and must begin at zero. Each inlet system applies over
   * the corresponding interval, so {@code inletSystems.length == elapsedTimesSeconds.length - 1}. Each inlet system
   * supplies both composition and a strictly positive mass flow. The conservative solver imposes that mass flow at the
   * authoritative finite-volume inlet face using the inlet EOS density. Every interval is divided into
   * {@code stepsPerInterval} equal accepted-step candidates. This shape maps directly to Java arrays from Python/JPype
   * and retains one report for every accepted step when history storage is enabled.
   * </p>
   *
   * @param elapsedTimesSeconds strictly increasing elapsed interval boundaries beginning at 0 s
   * @param inletSystems one single-gas-phase inlet system with a positive mass flow per interval
   * @param stepsPerInterval positive number of equal solver steps in every interval
   * @param id calculation identifier
   * @throws IllegalStateException if conservative mode is disabled or an inlet is not one gas phase
   * @throws IllegalArgumentException if the schedule is dimensionally invalid
   */
  public void runConservativeTransient(double[] elapsedTimesSeconds, SystemInterface[] inletSystems,
      int stepsPerInterval, UUID id) {
    validateConservativeSchedule(elapsedTimesSeconds, inletSystems, stepsPerInterval);
    if (!initialized) {
      run(id);
    }

    SystemInterface[] copiedInletSystems = new SystemInterface[inletSystems.length];
    for (int interval = 0; interval < inletSystems.length; interval++) {
      copiedInletSystems[interval] = inletSystems[interval].clone();
    }
    pipe.getTimeSeries().setTimes(elapsedTimesSeconds.clone());
    pipe.getTimeSeries().setInletThermoSystems(copiedInletSystems);
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(stepsPerInterval);
    pipe.getTimeSeries().setOutletMolarFlowRate(null);
    pipe.solveTransient(1, id);

    simulationTime += elapsedTimesSeconds[elapsedTimesSeconds.length - 1];
    updateOutletStream();
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Update the inlet boundary condition from the current inlet stream.
   */
  private void updateInletBoundary() {
    SystemInterface inletSystem = inStream.getThermoSystem().clone();
    pipe.getNode(0).setBulkSystem(inletSystem);
    pipe.getNode(0).initFlowCalc();
  }

  private PipeFlowSystem getPipeFlowSystem() {
    return (PipeFlowSystem) pipe;
  }

  private void validateConservativeSchedule(double[] elapsedTimesSeconds, SystemInterface[] inletSystems,
      int stepsPerInterval) {
    if (!conservativeCompositionalTracking) {
      throw new IllegalStateException(
          "Enable conservative compositional tracking before running a conservative schedule.");
    }
    if (elapsedTimesSeconds == null || elapsedTimesSeconds.length < 2 || elapsedTimesSeconds[0] != 0.0) {
      throw new IllegalArgumentException("Conservative elapsed times must contain at least [0.0, endSeconds].");
    }
    if (inletSystems == null || inletSystems.length != elapsedTimesSeconds.length - 1) {
      throw new IllegalArgumentException("Conservative schedule requires one inlet system per time interval.");
    }
    if (stepsPerInterval <= 0) {
      throw new IllegalArgumentException("Conservative steps per interval must be positive: " + stepsPerInterval);
    }
    for (int boundary = 0; boundary < elapsedTimesSeconds.length; boundary++) {
      double time = elapsedTimesSeconds[boundary];
      if (!Double.isFinite(time) || (boundary > 0 && time <= elapsedTimesSeconds[boundary - 1])) {
        throw new IllegalArgumentException(
            "Conservative elapsed times must be finite and strictly increasing at index " + boundary + ".");
      }
    }
    for (int interval = 0; interval < inletSystems.length; interval++) {
      SystemInterface inletSystem = inletSystems[interval];
      if (inletSystem == null) {
        throw new IllegalArgumentException(
            "Conservative OnePhasePipeLine requires a non-null inlet system for interval " + interval + ".");
      }
      double inletMassFlowKgPerSecond = inletSystem.getFlowRate("kg/sec");
      if (!Double.isFinite(inletMassFlowKgPerSecond) || inletMassFlowKgPerSecond <= 0.0) {
        throw new IllegalStateException(
            "Conservative OnePhasePipeLine currently supports strictly positive inlet mass flow only; invalid "
                + "interval " + interval + " has " + inletMassFlowKgPerSecond + " kg/s.");
      }
      SystemInterface phaseCheck = inletSystem.clone();
      phaseCheck.setMultiPhaseCheck(true);
      new ThermodynamicOperations(phaseCheck).TPflash();
      if (phaseCheck.getNumberOfPhases() != 1 || !phaseCheck.hasPhaseType("gas")) {
        throw new IllegalStateException(
            "Conservative OnePhasePipeLine currently supports one gas phase only; invalid interval " + interval + ".");
      }
    }
  }

  /**
   * Update the outlet stream with current outlet conditions from the pipe.
   */
  private void updateOutletStream() {
    int outletNode = pipe.getTotalNumberOfNodes() - 1;
    SystemInterface outletSystem = pipe.getNode(outletNode).getBulkSystem().clone();
    outletSystem.initProperties();
    outStream.setThermoSystem(outletSystem);
  }

  /**
   * Get the outlet composition for a specific component.
   *
   * @param componentName name of the component
   * @return mass fraction of the component at the outlet
   */
  public double getOutletMassFraction(String componentName) {
    int outletNode = pipe.getTotalNumberOfNodes() - 1;
    SystemInterface outletSystem = pipe.getNode(outletNode).getBulkSystem();
    int compIndex = outletSystem.getPhase(0).getComponent(componentName).getComponentNumber();
    double x = outletSystem.getPhase(0).getComponent(compIndex).getx();
    double molarMass = outletSystem.getPhase(0).getComponent(compIndex).getMolarMass();
    double avgMolarMass = outletSystem.getPhase(0).getMolarMass();
    return x * molarMass / avgMolarMass;
  }

  /**
   * Get the outlet mole fraction for a specific component.
   *
   * @param componentName name of the component
   * @return mole fraction of the component at the outlet
   */
  public double getOutletMoleFraction(String componentName) {
    int outletNode = pipe.getTotalNumberOfNodes() - 1;
    SystemInterface outletSystem = pipe.getNode(outletNode).getBulkSystem();
    return outletSystem.getPhase(0).getComponent(componentName).getx();
  }
}
