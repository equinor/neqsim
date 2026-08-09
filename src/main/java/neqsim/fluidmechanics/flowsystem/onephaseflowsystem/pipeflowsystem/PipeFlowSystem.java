package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import java.util.UUID;
import neqsim.fluidmechanics.flowsolver.AxialDispersionModel;
import neqsim.fluidmechanics.flowsolver.NoAxialDispersion;
import neqsim.fluidmechanics.flowsolver.SpeciesAdvectionScheme;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFixedStaggeredGrid;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFlowConvergenceReport;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;
import neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.onephaseflowvisualization.pipeflowvisualization.PipeFlowVisualization;
import neqsim.thermo.system.SystemInterface;

/**
 * PipeFlowSystem class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeFlowSystem extends neqsim.fluidmechanics.flowsystem.onephaseflowsystem.OnePhaseFlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private boolean failOnNonConvergence;
  private boolean conservativeSpeciesTransportEnabled;
  /** Selected finite-volume method for conservative component transport. */
  private SpeciesAdvectionScheme speciesAdvectionScheme = SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT;
  /** Optional physical axial dispersion, disabled by default. */
  private AxialDispersionModel axialDispersionModel = NoAxialDispersion.INSTANCE;
  private boolean storeSpeciesConservationHistory;
  private OnePhaseSpeciesConservationHistory speciesConservationHistory = OnePhaseSpeciesConservationHistory.empty();
  private OnePhaseSpeciesConservationHistory.Builder speciesConservationHistoryBuilder;

  /**
   * Constructor for PipeFlowSystem.
   */
  public PipeFlowSystem() {
  }

  /**
   * Get nonlinear convergence and total-mass diagnostics from the latest pipe solve.
   *
   * <p>
   * The returned report is immutable. Before the first solve it has reason
   * {@link OnePhaseFlowConvergenceReport.ConvergenceReason#NOT_RUN}.
   * </p>
   *
   * @return latest one-phase convergence report
   */
  public OnePhaseFlowConvergenceReport getConvergenceReport() {
    if (flowSolver instanceof OnePhaseFixedStaggeredGrid) {
      return ((OnePhaseFixedStaggeredGrid) flowSolver).getLastConvergenceReport();
    }
    return OnePhaseFlowConvergenceReport.notRun();
  }

  /**
   * Get component inventories, boundary masses, residuals, and boundedness diagnostics.
   *
   * @return latest conservative species report, or a not-run report before opt-in transport
   */
  public OnePhaseSpeciesConservationReport getSpeciesConservationReport() {
    if (flowSolver instanceof OnePhaseFixedStaggeredGrid) {
      return ((OnePhaseFixedStaggeredGrid) flowSolver).getLastSpeciesConservationReport();
    }
    return OnePhaseSpeciesConservationReport.notRun();
  }

  /**
   * Get time-aligned diagnostics for every accepted conservative step in the latest transient solve.
   *
   * <p>
   * The immutable history is reset at the start of each {@link #solveTransient(int, UUID)} call. It contains only steps
   * that completed successfully, so a fail-loud solve retains diagnostics for any previously accepted steps. Each
   * report contains component profiles, inventories, boundary masses, residuals, boundedness, and EOS-coupling
   * diagnostics. {@link OnePhaseSpeciesConservationHistory#toJson()} provides a stable Python capture path.
   * </p>
   *
   * @return immutable accepted-step history, empty before a conservative transient solve
   */
  public OnePhaseSpeciesConservationHistory getSpeciesConservationHistory() {
    return speciesConservationHistoryBuilder == null ? speciesConservationHistory
        : speciesConservationHistoryBuilder.build();
  }

  /**
   * Configure storage of full per-step conservative species diagnostics.
   *
   * <p>
   * Storage is off by default to avoid retaining every component-by-cell profile in long simulations. Enabling it does
   * not alter the conservative solve, finite-volume state, or convergence criteria.
   * </p>
   *
   * @param store true to retain one immutable report for every accepted conservative step
   */
  public void setStoreSpeciesConservationHistory(boolean store) {
    storeSpeciesConservationHistory = store;
  }

  /**
   * Check whether full accepted-step species diagnostics are retained.
   *
   * @return true when per-step report storage is enabled
   */
  public boolean isSpeciesConservationHistoryStorageEnabled() {
    return storeSpeciesConservationHistory;
  }

  /**
   * Enable conservative n-1 species transport for transient solver type 1.
   *
   * <p>
   * The path is currently isothermal and requires strictly positive flow. Unsupported flow and any failed
   * hydraulic/species criterion throw so that a failed conservative state cannot advance to the next timestep.
   * </p>
   *
   * @param enabled true to couple conservative component inventories to hydraulics and EOS
   */
  public void setConservativeSpeciesTransport(boolean enabled) {
    conservativeSpeciesTransportEnabled = enabled;
    configureConvergencePolicy();
  }

  /**
   * Check whether conservative species transport is enabled.
   *
   * @return true when the opt-in component path is active
   */
  public boolean isConservativeSpeciesTransportEnabled() {
    return conservativeSpeciesTransportEnabled;
  }

  /**
   * Select the finite-volume advection method used by conservative species transport.
   *
   * <p>
   * The default remains {@link SpeciesAdvectionScheme#FIRST_ORDER_IMPLICIT}. The selected method has no effect unless
   * {@link #setConservativeSpeciesTransport(boolean)} is enabled.
   * </p>
   *
   * @param scheme non-null conservative species advection scheme
   * @throws IllegalArgumentException if {@code scheme} is null
   */
  public void setSpeciesAdvectionScheme(SpeciesAdvectionScheme scheme) {
    if (scheme == null) {
      throw new IllegalArgumentException("Conservative species advection scheme cannot be null.");
    }
    speciesAdvectionScheme = scheme;
    configureConvergencePolicy();
  }

  /**
   * Get the selected conservative species advection method.
   *
   * @return non-null typed scheme; first-order implicit by default
   */
  public SpeciesAdvectionScheme getSpeciesAdvectionScheme() {
    return speciesAdvectionScheme;
  }

  /**
   * Select a physical axial-dispersion model for conservative component transport.
   *
   * <p>
   * Physical dispersion is independent of the selected numerical advection scheme. Use {@link NoAxialDispersion} to
   * retain pure advection. The validated boundary conditions are a prescribed inlet composition and zero physical
   * diffusive flux at the outlet.
   * </p>
   *
   * @param model non-null physical axial-dispersion model
   * @throws IllegalArgumentException if {@code model} is null
   */
  public void setAxialDispersionModel(AxialDispersionModel model) {
    if (model == null) {
      throw new IllegalArgumentException(
          "Physical axial-dispersion model cannot be null; use NoAxialDispersion for pure advection.");
    }
    axialDispersionModel = model;
    configureConvergencePolicy();
  }

  /** @return selected non-null physical axial-dispersion model */
  public AxialDispersionModel getAxialDispersionModel() {
    return axialDispersionModel;
  }

  /**
   * Configure whether a transient solve throws when its convergence report fails.
   *
   * <p>
   * The default is false for source and behavioral compatibility. Failed convergence is still recorded by
   * {@link #getConvergenceReport()} and logged as a warning.
   * </p>
   *
   * @param failOnNonConvergence true to throw after recording a failed report
   */
  public void setFailOnNonConvergence(boolean failOnNonConvergence) {
    this.failOnNonConvergence = failOnNonConvergence;
    configureConvergencePolicy();
  }

  /**
   * Check whether failed transient convergence throws.
   *
   * @return true when strict fail-loud mode is enabled
   */
  public boolean isFailOnNonConvergence() {
    return failOnNonConvergence;
  }

  private void configureConvergencePolicy() {
    if (flowSolver instanceof OnePhaseFixedStaggeredGrid) {
      OnePhaseFixedStaggeredGrid onePhaseSolver = (OnePhaseFixedStaggeredGrid) flowSolver;
      onePhaseSolver.setFailOnNonConvergence(failOnNonConvergence);
      onePhaseSolver.setConservativeSpeciesTransportEnabled(conservativeSpeciesTransportEnabled);
      onePhaseSolver.setSpeciesAdvectionScheme(speciesAdvectionScheme);
      onePhaseSolver.setAxialDispersionModel(axialDispersionModel);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void createSystem() {
    thermoSystem.init(0);
    thermoSystem.init(1);
    flowLeg = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg[this.getNumberOfLegs()];

    for (int i = 0; i < getNumberOfLegs(); i++) {
      flowLeg[i] = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg();
    }

    flowNode = new neqsim.fluidmechanics.flownode.onephasenode.onephasepipeflownode.onePhasePipeFlowNode[totalNumberOfNodes];
    // System.out.println("nodes: " + totalNumberOfNodes);
    flowNode[0] = new neqsim.fluidmechanics.flownode.onephasenode.onephasepipeflownode.onePhasePipeFlowNode(
        thermoSystem, this.equipmentGeometry[0]);
    flowNode[0].initFlowCalc();
    super.createSystem();
    this.setNodes();
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    for (int j = 0; j < getTotalNumberOfNodes(); j++) {
      flowNode[j].initFlowCalc();
      flowNode[j].setVelocityIn(this.flowNode[j].getVelocity());
    }

    for (int k = 0; k < getTotalNumberOfNodes() - 1; k++) {
      this.flowNode[k].setVelocityOut(this.flowNode[k + 1].getVelocityIn());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void solveSteadyState(int type, UUID id) {
    double[] times = { 0.0 };
    display = new PipeFlowVisualization(this.getTotalNumberOfNodes(), 1);
    getTimeSeries().setTimes(times);
    SystemInterface[] systems = { flowNode[0].getBulkSystem() };
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    double[] outletFlowRates = { 0.0, 0.0 }; // this is not yet implemented
    getTimeSeries().setOutletMolarFlowRate(outletFlowRates);
    // SteadystateOnePhasePipeFlowSolver pipeSolve = new
    // SteadystateOnePhasePipeFlowSolver(this, getSystemLength(),
    // getTotalNumberOfNodes());
    flowSolver = new neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFixedStaggeredGrid(
        this, getSystemLength(), getTotalNumberOfNodes(), false);
    configureConvergencePolicy();
    flowSolver.setSolverType(type);
    flowSolver.solveTDMA();
    getTimeSeries().init(this);
    display.setNextData(this);
    calcIdentifier = id;
  }

  /** {@inheritDoc} */
  @Override
  public void solveTransient(int type, UUID id) {
    getTimeSeries().init(this);
    display = new PipeFlowVisualization(this.getTotalNumberOfNodes(), getTimeSeries().getTime().length);
    speciesConservationHistory = OnePhaseSpeciesConservationHistory.empty();
    speciesConservationHistoryBuilder = conservativeSpeciesTransportEnabled && storeSpeciesConservationHistory
        ? OnePhaseSpeciesConservationHistory.builder()
        : null;
    flowSolver.setDynamic(true);
    configureConvergencePolicy();
    flowSolver.setSolverType(type);

    int outletNodeIndex = getTotalNumberOfNodes() - 1;

    for (int i = 0; i < this.getTimeSeries().getTime().length; i++) {
      // Apply inlet boundary conditions
      SystemInterface scheduledInletSystem = this.getTimeSeries().getThermoSystem()[i];
      double scheduledInletMassFlowKgPerSecond = conservativeSpeciesTransportEnabled
          ? scheduledInletSystem.getFlowRate("kg/sec")
          : Double.NaN;
      getNode(0).setBulkSystem(scheduledInletSystem);
      if (conservativeSpeciesTransportEnabled) {
        getNode(0).getBulkSystem().setTotalFlowRate(scheduledInletMassFlowKgPerSecond, "kg/sec");
      }
      getNode(0).initFlowCalc();
      if (conservativeSpeciesTransportEnabled) {
        double inletDensity = getNode(0).getBulkSystem().getPhase(0).getDensity();
        double inletArea = getNode(0).getGeometry().getArea();
        if (!Double.isFinite(inletDensity) || inletDensity <= 0.0 || !Double.isFinite(inletArea) || inletArea <= 0.0) {
          throw new IllegalStateException("Cannot impose conservative inlet mass flow with density " + inletDensity
              + " kg/m3 and area " + inletArea + " m2.");
        }
        double inletVelocity = scheduledInletMassFlowKgPerSecond / (inletArea * inletDensity);
        getNode(0).setVelocity(inletVelocity);
        getNode(0).setVelocityIn(inletVelocity);
        flowNode[0].setVelocityOut(inletVelocity);
        if (getTotalNumberOfNodes() > 1) {
          getNode(1).setVelocityIn(inletVelocity);
        }
      } else {
        getNode(0).setVelocityIn(getNode(0).getVelocity());
        flowNode[0].setVelocityOut(this.flowNode[0].getVelocity());
      }

      // Apply outlet boundary conditions based on type
      applyOutletBoundaryCondition(i, outletNodeIndex);

      getSolver().setTimeStep(this.getTimeSeries().getTimeStep()[i]);
      try {
        flowSolver.solveTDMA();
      } finally {
        if (conservativeSpeciesTransportEnabled) {
          scheduledInletSystem.setTotalFlowRate(scheduledInletMassFlowKgPerSecond, "kg/sec");
        }
      }
      if (speciesConservationHistoryBuilder != null) {
        speciesConservationHistoryBuilder.append(getTimeSeries().getTime(i), getSpeciesConservationReport());
      }
      display.setNextData(this, this.getTimeSeries().getTime(i));
    }
    if (speciesConservationHistoryBuilder != null) {
      speciesConservationHistory = speciesConservationHistoryBuilder.build();
      speciesConservationHistoryBuilder = null;
    }
    calcIdentifier = id;
  }

  /**
   * Applies outlet boundary conditions for the current time step.
   *
   * @param timeStepIndex the current time step index
   * @param outletNodeIndex the index of the outlet node
   */
  private void applyOutletBoundaryCondition(int timeStepIndex, int outletNodeIndex) {
    neqsim.fluidmechanics.util.timeseries.TimeSeries ts = getTimeSeries();

    if (ts.isOutletClosed()) {
      // Closed outlet: set velocity to zero
      flowNode[outletNodeIndex].setVelocity(0.0);
      flowNode[outletNodeIndex].setVelocityIn(0, 0.0);
      if (outletNodeIndex > 0) {
        flowNode[outletNodeIndex - 1].setVelocityOut(0, 0.0);
      }
    } else if (ts.isOutletFlowControlled()) {
      // Flow-controlled outlet: set specified velocity
      double outletVelocity = ts.getOutletVelocity(timeStepIndex);
      if (!Double.isNaN(outletVelocity)) {
        flowNode[outletNodeIndex].setVelocity(outletVelocity);
        flowNode[outletNodeIndex].setVelocityIn(0, outletVelocity);
        if (outletNodeIndex > 0) {
          flowNode[outletNodeIndex - 1].setVelocityOut(0, outletVelocity);
        }
      }
    } else if (ts.isOutletPressureControlled()) {
      // Pressure-controlled outlet: set specified pressure
      double outletPressure = ts.getOutletPressure(timeStepIndex);
      if (!Double.isNaN(outletPressure) && outletPressure > 0) {
        flowNode[outletNodeIndex].getBulkSystem().setPressure(outletPressure);
        flowNode[outletNodeIndex].init();
      }
    }
  }

  /**
   * Runs a transient simulation with constant inlet conditions for a specified duration.
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * pipe.runTransient(3600.0, 60.0); // 1 hour simulation with 60s time steps
   * </pre>
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   */
  public void runTransient(double totalTime, double timeStep) {
    runTransient(totalTime, timeStep, 10);
  }

  /**
   * Runs a transient simulation with constant inlet conditions for a specified duration.
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   * @param solverType solver type (0=momentum, 1=mass, 10=energy, 20=composition)
   */
  public void runTransient(double totalTime, double timeStep, int solverType) {
    int numIntervals = Math.max(1, (int) Math.ceil(totalTime / timeStep));
    double[] times = new double[numIntervals + 1];
    for (int i = 0; i <= numIntervals; i++) {
      times[i] = i * timeStep;
    }

    // Use current inlet system for all intervals
    SystemInterface[] systems = new SystemInterface[numIntervals];
    for (int i = 0; i < numIntervals; i++) {
      systems[i] = flowNode[0].getBulkSystem().clone();
    }

    getTimeSeries().setTimes(times);
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    getTimeSeries().setOutletMolarFlowRate(null); // Reset to avoid array bounds issues

    solveTransient(solverType);
  }

  /**
   * Runs a transient simulation with a closed outlet (blocked pipe).
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * pipe.runTransientClosedOutlet(600.0, 30.0); // 10 min with outlet closed
   * </pre>
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   */
  public void runTransientClosedOutlet(double totalTime, double timeStep) {
    runTransientClosedOutlet(totalTime, timeStep, 10);
  }

  /**
   * Runs a transient simulation with a closed outlet (blocked pipe).
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   * @param solverType solver type (0=momentum, 1=mass, 10=energy, 20=composition)
   */
  public void runTransientClosedOutlet(double totalTime, double timeStep, int solverType) {
    int numIntervals = Math.max(1, (int) Math.ceil(totalTime / timeStep));
    double[] times = new double[numIntervals + 1];
    for (int i = 0; i <= numIntervals; i++) {
      times[i] = i * timeStep;
    }

    SystemInterface[] systems = new SystemInterface[numIntervals];
    for (int i = 0; i < numIntervals; i++) {
      systems[i] = flowNode[0].getBulkSystem().clone();
    }

    getTimeSeries().setTimes(times);
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    getTimeSeries().setOutletMolarFlowRate(null); // Reset to avoid array bounds issues
    getTimeSeries().setOutletClosed();

    solveTransient(solverType);
  }

  /**
   * Runs a transient simulation with controlled outlet velocity.
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * pipe.runTransientControlledOutletVelocity(600.0, 30.0, 2.5); // Outlet at 2.5 m/s
   * </pre>
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   * @param outletVelocity controlled outlet velocity in m/s
   */
  public void runTransientControlledOutletVelocity(double totalTime, double timeStep, double outletVelocity) {
    runTransientControlledOutletVelocity(totalTime, timeStep, outletVelocity, 10);
  }

  /**
   * Runs a transient simulation with controlled outlet velocity.
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   * @param outletVelocity controlled outlet velocity in m/s
   * @param solverType solver type (0=momentum, 1=mass, 10=energy, 20=composition)
   */
  public void runTransientControlledOutletVelocity(double totalTime, double timeStep, double outletVelocity,
      int solverType) {
    int numIntervals = Math.max(1, (int) Math.ceil(totalTime / timeStep));
    double[] times = new double[numIntervals + 1];
    double[] velocities = new double[numIntervals];
    for (int i = 0; i <= numIntervals; i++) {
      times[i] = i * timeStep;
    }
    for (int i = 0; i < numIntervals; i++) {
      velocities[i] = outletVelocity;
    }

    SystemInterface[] systems = new SystemInterface[numIntervals];
    for (int i = 0; i < numIntervals; i++) {
      systems[i] = flowNode[0].getBulkSystem().clone();
    }

    getTimeSeries().setTimes(times);
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    getTimeSeries().setOutletMolarFlowRate(null); // Reset to avoid array bounds issues
    getTimeSeries().setOutletVelocity(velocities);

    solveTransient(solverType);
  }

  /**
   * Runs a transient simulation with controlled outlet pressure.
   *
   * <p>
   * Example:
   * </p>
   *
   * <pre>
   * pipe.runTransientControlledOutletPressure(600.0, 30.0, 50.0); // Outlet at 50 bar
   * </pre>
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   * @param outletPressure controlled outlet pressure in bar
   */
  public void runTransientControlledOutletPressure(double totalTime, double timeStep, double outletPressure) {
    runTransientControlledOutletPressure(totalTime, timeStep, outletPressure, 10);
  }

  /**
   * Runs a transient simulation with controlled outlet pressure.
   *
   * @param totalTime total simulation time in seconds
   * @param timeStep time step size in seconds
   * @param outletPressure controlled outlet pressure in bar
   * @param solverType solver type (0=momentum, 1=mass, 10=energy, 20=composition)
   */
  public void runTransientControlledOutletPressure(double totalTime, double timeStep, double outletPressure,
      int solverType) {
    int numIntervals = Math.max(1, (int) Math.ceil(totalTime / timeStep));
    double[] times = new double[numIntervals + 1];
    double[] pressures = new double[numIntervals];
    for (int i = 0; i <= numIntervals; i++) {
      times[i] = i * timeStep;
    }
    for (int i = 0; i < numIntervals; i++) {
      pressures[i] = outletPressure;
    }

    SystemInterface[] systems = new SystemInterface[numIntervals];
    for (int i = 0; i < numIntervals; i++) {
      systems[i] = flowNode[0].getBulkSystem().clone();
    }

    getTimeSeries().setTimes(times);
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    getTimeSeries().setOutletMolarFlowRate(null); // Reset to avoid array bounds issues
    getTimeSeries().setOutletPressure(pressures);

    solveTransient(solverType);
  }

  /**
   * Sets the outlet as closed for subsequent transient simulations.
   */
  public void setOutletClosed() {
    getTimeSeries().setOutletClosed();
  }

  /**
   * Sets controlled outlet velocity for subsequent transient simulations.
   *
   * @param velocity outlet velocity in m/s
   */
  public void setOutletVelocity(double velocity) {
    getTimeSeries().setOutletVelocity(new double[] { velocity });
  }

  /**
   * Sets controlled outlet pressure for subsequent transient simulations.
   *
   * @param pressure outlet pressure in bar
   */
  public void setOutletPressure(double pressure) {
    getTimeSeries().setOutletPressure(new double[] { pressure });
  }
}
