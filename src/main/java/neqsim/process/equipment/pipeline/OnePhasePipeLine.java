/*
 * OnePhasePipeLine.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsolver.AdvectionScheme;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * One-phase pipeline with compositional tracking support.
 *
 * <p>
 * This class wraps {@link PipeFlowSystem} for use in process simulations. It supports both
 * steady-state and transient simulations, including compositional tracking for scenarios like gas
 * switching (e.g., natural gas to nitrogen transitions).
 * </p>
 *
 * <h2>Transient Compositional Tracking</h2>
 * <p>
 * For gas switching scenarios, use {@link #setAdvectionScheme(AdvectionScheme)} to select a
 * higher-order scheme that reduces numerical dispersion:
 * </p>
 * <ul>
 * <li>{@link AdvectionScheme#FIRST_ORDER_UPWIND} - Default, most stable but high dispersion</li>
 * <li>{@link AdvectionScheme#TVD_VAN_LEER} - Recommended for compositional tracking</li>
 * <li>{@link AdvectionScheme#TVD_SUPERBEE} - Sharpest fronts, best for gas switching</li>
 * </ul>
 *
 * <h2>Example: Gas Switching Simulation</h2>
 * 
 * <pre>{@code
 * // Create pipeline
 * OnePhasePipeLine pipe = new OnePhasePipeLine("GasPipe", inletStream);
 * pipe.setNumberOfLegs(1);
 * pipe.setNumberOfNodesInLeg(100);
 * pipe.setPipeDiameters(new double[] {0.3, 0.3});
 * pipe.setLegPositions(new double[] {0.0, 5000.0});
 *
 * // Select TVD scheme for sharp composition fronts
 * pipe.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);
 * pipe.setCompositionalTracking(true);
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
 *   // Access outlet composition
 *   double methane = pipe.getOutletStream().getFluid().getComponent("methane").getx();
 * }
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
   * Higher-order schemes reduce numerical dispersion (front spreading) during compositional
   * tracking. For gas switching scenarios, TVD schemes are recommended.
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
   * When enabled, the transient solver tracks component mass fractions through the pipe. Use this
   * for gas switching or composition gradient tracking scenarios.
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
    pipe.solveSteadyState(10, id);
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
   * This method advances the pipe simulation by the specified time step and updates the outlet
   * stream with the current outlet conditions. The inlet boundary is updated from the current inlet
   * stream state.
   * </p>
   *
   * <p>
   * If compositional tracking is enabled, the solver tracks component mass fractions through the
   * pipe using the selected advection scheme.
   * </p>
   *
   * @param dt time step in seconds
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
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
      double[] times = {simulationTime, simulationTime + stepDt};
      SystemInterface[] systems =
          {inStream.getThermoSystem().clone(), inStream.getThermoSystem().clone()};

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
}
