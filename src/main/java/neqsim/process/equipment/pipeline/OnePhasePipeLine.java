/*
 * OnePhasePipeLine.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsolver.AdvectionScheme;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFlowConvergenceReport;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.OnePhaseSpeciesConservationHistory;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * One-phase pipeline with legacy and conservative compositional tracking support.
 *
 * <p>
 * This class wraps {@link PipeFlowSystem} for use in process simulations. It supports both steady-state and transient
 * simulations, including validated isothermal conservative species tracking for positive-flow gas-quality events and
 * the legacy staged compositional solver.
 * </p>
 *
 * <h2>Validated Conservative Compositional Tracking</h2>
 * <p>
 * Use {@link #setConservativeCompositionalTracking(boolean)} for a component-conservative, positive-flow, isothermal
 * transient. This mode delegates to {@link PipeFlowSystem} solver type 1 and exposes immutable component-conservation
 * diagnostics and history. The legacy {@link #setCompositionalTracking(boolean)} mode selects the staged type 20 path,
 * whose changing-composition hydraulic/EOS coupling is not covered by the same validation.
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
 * // Run transient with changing inlet composition
 * UUID id = UUID.randomUUID();
 * for (int step = 0; step < 100; step++) {
 *   // Update inlet stream composition if needed
 *   pipe.runTransient(1.0, id); // 1 second time step
 *
 *   // Access the conservative physical-cell outlet mass fraction
 *   double methane = pipe.getConservativeOutletMassFraction("methane");
 * }
 * double[] acceptedTimes = pipe.getSpeciesConservationHistory().getElapsedTimeSeconds();
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

  /** Whether to use the validated conservative species path during transient simulation. */
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
   * Enable or disable validated conservative compositional tracking.
   *
   * <p>
   * When enabled, transient calls use solver type 1 with conservative component inventories coupled to the hydraulic
   * and EOS state. The current validated scope is positive, one-phase, isothermal flow with first-order upwind species
   * transport. This mode takes precedence if the legacy {@link #setCompositionalTracking(boolean)} flag is also true.
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
   * @return true when transient calls select the conservative type 1 path
   */
  public boolean isConservativeCompositionalTracking() {
    return conservativeCompositionalTracking;
  }

  /**
   * Configure storage of immutable diagnostics for every accepted conservative step.
   *
   * @param store true to retain per-step reports during the latest high-level transient call
   */
  public void setStoreSpeciesConservationHistory(boolean store) {
    getPipeFlowSystem().setStoreSpeciesConservationHistory(store);
  }

  /**
   * Check whether accepted conservative-step history storage is enabled.
   *
   * @return true when full species diagnostics are retained
   */
  public boolean isSpeciesConservationHistoryStorageEnabled() {
    return getPipeFlowSystem().isSpeciesConservationHistoryStorageEnabled();
  }

  /**
   * Configure fail-loud behavior for hydraulic or conservative-species non-convergence.
   *
   * @param fail true to throw after recording a failed convergence report
   */
  public void setFailOnNonConvergence(boolean fail) {
    getPipeFlowSystem().setFailOnNonConvergence(fail);
  }

  /**
   * Check whether failed transient convergence throws.
   *
   * @return true when strict fail-loud behavior is enabled
   */
  public boolean isFailOnNonConvergence() {
    return getPipeFlowSystem().isFailOnNonConvergence();
  }

  /**
   * Get hydraulic, EOS-density, and total-mass diagnostics for the latest solve.
   *
   * @return immutable latest convergence report
   */
  public OnePhaseFlowConvergenceReport getConvergenceReport() {
    return getPipeFlowSystem().getConvergenceReport();
  }

  /**
   * Get component inventories, boundary masses, and residuals for the latest conservative step.
   *
   * @return immutable latest species report, or a not-run report before conservative transport
   */
  public OnePhaseSpeciesConservationReport getSpeciesConservationReport() {
    return getPipeFlowSystem().getSpeciesConservationReport();
  }

  /**
   * Get accepted conservative-step diagnostics from the latest high-level transient call.
   *
   * <p>
   * Times are elapsed step-end seconds from the start of that call. The returned history and its reports are immutable,
   * and array getters return defensive copies suitable for Java or Python/JPype capture.
   * </p>
   *
   * @return immutable accepted-step history
   */
  public OnePhaseSpeciesConservationHistory getSpeciesConservationHistory() {
    return getPipeFlowSystem().getSpeciesConservationHistory();
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
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Internal time step must be finite and positive: " + dt);
    }
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
    if (conservativeCompositionalTracking) {
      validateSinglePhaseState(inStream.getThermoSystem(), "steady-state inlet");
    }
    UUID oldid = getCalculationIdentifier();
    super.run(id);
    setCalculationIdentifier(oldid);
    pipe.solveSteadyState(conservativeCompositionalTracking ? 1 : 10, id);
    if (conservativeCompositionalTracking) {
      validateConservativePipeState("steady-state initialization");
    }
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
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Transient time step must be finite and positive: " + dt);
    }
    // Initialize if not already done
    if (!initialized) {
      run(id);
    }

    // Update inlet boundary from current inlet stream
    updateInletBoundary();

    if (conservativeCompositionalTracking) {
      validateConservativePipeState("transient inlet");
    }

    // Select solver type: 1 = validated conservative species, 20 = legacy staged composition, 2 = momentum
    int solverType = conservativeCompositionalTracking ? 1 : (compositionalTracking ? 20 : 2);

    if (conservativeCompositionalTracking) {
      solveConservativeTransient(dt, solverType, id);
      validateConservativePipeState("accepted transient state");
      simulationTime += dt;
    } else {
      double timeRemaining = dt;
      while (timeRemaining > 0.0) {
        double stepDt = Math.min(internalTimeStep, timeRemaining);
        double[] times = { simulationTime, simulationTime + stepDt };
        SystemInterface[] systems = { inStream.getThermoSystem().clone(), inStream.getThermoSystem().clone() };
        pipe.getTimeSeries().setTimes(times);
        pipe.getTimeSeries().setInletThermoSystems(systems);
        pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
        pipe.solveTransient(solverType, id);
        simulationTime += stepDt;
        timeRemaining -= stepDt;
      }
    }

    // Update outlet stream with current outlet conditions
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

  /**
   * Get the conservative mass-fraction profile for one component across physical cells.
   *
   * <p>
   * This differs from {@link #getCompositionProfile(String)}, which derives node mass fractions from the current EOS
   * state and includes boundary nodes. This method reads the authoritative conservative finite-volume report.
   * </p>
   *
   * @param componentName component name, matched case-insensitively
   * @return defensive copy of component mass fraction by physical cell
   * @throws IllegalStateException if no converged conservative report is available
   * @throws IllegalArgumentException if the component is absent from the report
   */
  public double[] getConservativeMassFractionProfile(String componentName) {
    OnePhaseSpeciesConservationReport report = requireConservativeReport();
    int componentIndex = findComponentIndex(report.getComponentNames(), componentName);
    return report.getMassFractionProfile()[componentIndex];
  }

  /**
   * Get the latest conservative physical-cell outlet mass fraction.
   *
   * @param componentName component name
   * @return mass fraction in the last physical control volume
   */
  public double getConservativeOutletMassFraction(String componentName) {
    double[] profile = getConservativeMassFractionProfile(componentName);
    return profile[profile.length - 1];
  }

  /**
   * Get accepted-step outlet mass fractions from the latest high-level transient call.
   *
   * <p>
   * The values align one-to-one with {@link OnePhaseSpeciesConservationHistory#getElapsedTimeSeconds()} and are read
   * from the last physical control volume in each immutable conservative report.
   * </p>
   *
   * @param componentName component name
   * @return outlet mass-fraction history at accepted step-end times
   * @throws IllegalStateException if the latest transient call stored no conservative history
   */
  public double[] getConservativeOutletMassFractionHistory(String componentName) {
    OnePhaseSpeciesConservationHistory history = getSpeciesConservationHistory();
    if (history.isEmpty()) {
      throw new IllegalStateException(
          "No conservative species history is available. Enable history storage before runTransient(...).");
    }
    OnePhaseSpeciesConservationReport[] reports = history.getReports();
    double[] outletHistory = new double[reports.length];
    for (int step = 0; step < reports.length; step++) {
      int componentIndex = findComponentIndex(reports[step].getComponentNames(), componentName);
      double[] profile = reports[step].getMassFractionProfile()[componentIndex];
      outletHistory[step] = profile[profile.length - 1];
    }
    return outletHistory;
  }

  private PipeFlowSystem getPipeFlowSystem() {
    return (PipeFlowSystem) pipe;
  }

  private void solveConservativeTransient(double dt, int solverType, UUID id) {
    int numberOfSteps = Math.max(1, (int) Math.ceil(dt / internalTimeStep));
    double[] elapsedTimes = new double[numberOfSteps + 1];
    SystemInterface[] inletSystems = new SystemInterface[numberOfSteps];
    for (int step = 0; step < numberOfSteps; step++) {
      elapsedTimes[step + 1] = Math.min(dt, elapsedTimes[step] + internalTimeStep);
      inletSystems[step] = inStream.getThermoSystem().clone();
    }
    pipe.getTimeSeries().setTimes(elapsedTimes);
    pipe.getTimeSeries().setInletThermoSystems(inletSystems);
    // solveSteadyState(...) leaves a two-entry legacy outlet-flow array behind. The conservative
    // pressure-boundary solve does not use it, and retaining it would make TimeSeries.init(...) index
    // beyond that array whenever a transient call contains more than two internal steps.
    pipe.getTimeSeries().setOutletMolarFlowRate(null);
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    pipe.solveTransient(solverType, id);
  }

  private OnePhaseSpeciesConservationReport requireConservativeReport() {
    OnePhaseSpeciesConservationReport report = getSpeciesConservationReport();
    if (!report.isConverged()) {
      throw new IllegalStateException("No converged conservative species report is available: " + report.getMessage());
    }
    return report;
  }

  private static int findComponentIndex(String[] componentNames, String componentName) {
    if (componentName == null) {
      throw new IllegalArgumentException("Component name cannot be null.");
    }
    for (int index = 0; index < componentNames.length; index++) {
      if (componentName.equalsIgnoreCase(componentNames[index])) {
        return index;
      }
    }
    throw new IllegalArgumentException("Component is not present in the conservative report: " + componentName);
  }

  private void validateConservativePipeState(String context) {
    validateSinglePhaseState(inStream.getThermoSystem(), context + " stream");
  }

  private static void validateSinglePhaseState(SystemInterface state, String context) {
    if (state == null) {
      throw new IllegalStateException("Conservative one-phase transport requires a non-null " + context + " state.");
    }
    if (state.getNumberOfPhases() != 1) {
      throw new IllegalStateException("Conservative OnePhasePipeLine transport does not support phase appearance at "
          + context + ": found " + state.getNumberOfPhases() + " phases.");
    }
  }
}
