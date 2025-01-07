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
    for (int i = 0; i < this.getTimeSeries().getTime().length; i++) {
      getNode(0).setBulkSystem(this.getTimeSeries().getThermoSystem()[i]);
      // getNode(0).getBulkSystem().setPressure();
      getNode(0).initFlowCalc();
      getNode(0).setVelocityIn(getNode(0).getVelocity());
      flowNode[0].setVelocityOut(this.flowNode[0].getVelocity());
      // flowNode[1].setVelocityIn(this.flowNode[0].getVelocity());
      // flowNode[getTotalNumberOfNodes()-1].setVelocity(this.getTimeSeries().getOutletMolarFlowRates()[i]);
      System.out.println("vel: " + this.flowNode[0].getVelocity());
      getSolver().setTimeStep(this.getTimeSeries().getTimeStep()[i]);
      // System.out.println("time step: " + i + " " +
      // this.getTimeSeries().getTimeStep()[i]);
      // System.out.println("time: " + i + " " + this.getTimeSeries().getTime()[i]);
      // flowSolver.solveTDMA();
      display.setNextData(this, this.getTimeSeries().getTime(i));
    }
    calcIdentifier = id;
  }
}
