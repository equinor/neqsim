package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import java.util.UUID;
import neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.onephaseflowvisualization.pipeflowvisualization.PipeFlowVisualization;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PipeFlowSystem class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeFlowSystem
    extends neqsim.fluidmechanics.flowsystem.onephaseflowsystem.OnePhaseFlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for PipeFlowSystem.
   * </p>
   */
  public PipeFlowSystem() {}

  /** {@inheritDoc} */
  @Override
  public void createSystem() {
    thermoSystem.init(0);
    thermoSystem.init(1);
    flowLeg = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg[this.getNumberOfLegs()];

    for (int i = 0; i < getNumberOfLegs(); i++) {
      flowLeg[i] = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg();
    }

    flowNode =
        new neqsim.fluidmechanics.flownode.onephasenode.onephasepipeflownode.onePhasePipeFlowNode[totalNumberOfNodes];
    // System.out.println("nodes: " + totalNumberOfNodes);
    flowNode[0] =
        new neqsim.fluidmechanics.flownode.onephasenode.onephasepipeflownode.onePhasePipeFlowNode(
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
    double[] times = {0.0};
    display = new PipeFlowVisualization(this.getTotalNumberOfNodes(), 1);
    getTimeSeries().setTimes(times);
    SystemInterface[] systems = {flowNode[0].getBulkSystem()};
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    double[] outletFlowRates = {0.0, 0.0}; // this is not yet implemented
    getTimeSeries().setOutletMolarFlowRate(outletFlowRates);
    // SteadystateOnePhasePipeFlowSolver pipeSolve = new
    // SteadystateOnePhasePipeFlowSolver(this, getSystemLength(),
    // getTotalNumberOfNodes());
    flowSolver =
        new neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFixedStaggeredGrid(
            this, getSystemLength(), getTotalNumberOfNodes(), false);
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
    display =
        new PipeFlowVisualization(this.getTotalNumberOfNodes(), getTimeSeries().getTime().length);
    flowSolver.setDynamic(true);
    flowSolver.setSolverType(type);

    int outletNodeIndex = getTotalNumberOfNodes() - 1;

    for (int i = 0; i < this.getTimeSeries().getTime().length; i++) {
      // Apply inlet boundary conditions
      getNode(0).setBulkSystem(this.getTimeSeries().getThermoSystem()[i]);
      getNode(0).initFlowCalc();
      getNode(0).setVelocityIn(getNode(0).getVelocity());
      flowNode[0].setVelocityOut(this.flowNode[0].getVelocity());

      // Apply outlet boundary conditions based on type
      applyOutletBoundaryCondition(i, outletNodeIndex);

      getSolver().setTimeStep(this.getTimeSeries().getTimeStep()[i]);
      flowSolver.solveTDMA();
      display.setNextData(this, this.getTimeSeries().getTime(i));
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
  public void runTransientControlledOutletVelocity(double totalTime, double timeStep,
      double outletVelocity) {
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
  public void runTransientControlledOutletVelocity(double totalTime, double timeStep,
      double outletVelocity, int solverType) {
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
  public void runTransientControlledOutletPressure(double totalTime, double timeStep,
      double outletPressure) {
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
  public void runTransientControlledOutletPressure(double totalTime, double timeStep,
      double outletPressure, int solverType) {
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
    getTimeSeries().setOutletVelocity(new double[] {velocity});
  }

  /**
   * Sets controlled outlet pressure for subsequent transient simulations.
   *
   * @param pressure outlet pressure in bar
   */
  public void setOutletPressure(double pressure) {
    getTimeSeries().setOutletPressure(new double[] {pressure});
  }
}
