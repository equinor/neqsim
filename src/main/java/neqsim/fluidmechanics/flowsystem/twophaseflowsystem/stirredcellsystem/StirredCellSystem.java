package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.stirredcellsystem;

import java.util.UUID;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * StirredCellSystem class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StirredCellSystem
    extends neqsim.fluidmechanics.flowsystem.twophaseflowsystem.TwoPhaseFlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for StirredCellSystem.
   * </p>
   */
  public StirredCellSystem() {}

  /** {@inheritDoc} */
  @Override
  public void createSystem() {
    flowLeg = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg[this.getNumberOfLegs()];

    for (int i = 0; i < getNumberOfLegs(); i++) {
      flowLeg[i] = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg();
    }

    flowNode = new neqsim.fluidmechanics.flownode.FlowNodeInterface[totalNumberOfNodes];
    flowNode[0] =
        new neqsim.fluidmechanics.flownode.twophasenode.twophasestirredcellnode.StirredCellNode(
            thermoSystem, equipmentGeometry[0]);

    flowNode[totalNumberOfNodes - 1] = flowNode[0].getNextNode();

    super.createSystem();
    this.setNodes();
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    for (int j = 0; j < getTotalNumberOfNodes(); j++) {
      flowNode[j].init();
    }

    for (int j = 0; j < getTotalNumberOfNodes(); j++) {
      for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
        flowNode[j].setVelocityOut(phaseNum, this.flowNode[j].getVelocity(phaseNum));
      }
    }

    for (int k = 1; k < getTotalNumberOfNodes(); k++) {
      for (int phaseNum = 0; phaseNum < 2; phaseNum++) {
        this.flowNode[k].setVelocityIn(phaseNum, this.flowNode[k - 1].getVelocityOut(phaseNum));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void solveSteadyState(int type, UUID id) {
    // TODO: double[] times = {0.0}; ?
    flowSolver =
        new neqsim.fluidmechanics.flowsolver.twophaseflowsolver.stirredcellsolver.StirredCellSolver(
            this, getSystemLength(), getTotalNumberOfNodes(), false);
    calcIdentifier = id;
    // TODO: getTimeSeries().init(this);
  }

  /** {@inheritDoc} */
  @Override
  public void solveTransient(int solverType, UUID id) {
    getTimeSeries().init(this);
    display =
        new neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.twophaseflowvisualization.twophasepipeflowvisualization.TwoPhasePipeFlowVisualization(
            this.getTotalNumberOfNodes(), getTimeSeries().getTime().length);
    for (int i = 0; i < this.getTimeSeries().getTime().length; i++) {
      getNode(0).setBulkSystem(this.getTimeSeries().getThermoSystem()[i]);
      flowNode[0].initFlowCalc();
      flowNode[0].init();
      flowNode[0].setVelocityIn(this.flowNode[0].getVelocity());
      flowNode[getTotalNumberOfNodes() - 1]
          .setVelocity(this.getTimeSeries().getOutletMolarFlowRates()[i]);
      // System.out.println("vel: " + this.flowNode[0].getVelocity());
      // getSolver().setTimeStep(this.getTimeSeries().getTimeStep()[i]);
      // System.out.println("time step: " + i + " " +
      // this.getTimeSeries().getTimeStep()[i]);
      System.out.println("time: " + i + "  " + this.getTimeSeries().getTime()[i]);
      flowSolver.solveTDMA();
      display.setNextData(this, this.getTimeSeries().getTime(i));
    }
    calcIdentifier = id;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // Initierer et nyt rorsystem
    neqsim.fluidmechanics.flowsystem.FlowSystemInterface pipe = new StirredCellSystem();

    // Definerer termodyanmikken5
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(295.3, 5.0);
    testSystem.addComponent("methane", 0.11152181, 0);
    // testSystem.addComponent("ethane", 0.0011152181, 0);
    testSystem.addComponent("water", 0.04962204876, 1);
    // testSystem.addComponent("TEG", 0.4962204876, 1);
    testSystem.setMixingRule(2);
    // benytter klassiske blandingsregler

    // setter termodyanmikken for rorsystemet
    pipe.setInletThermoSystem(testSystem);
    // deler inn roret i et gitt antall legger
    pipe.setNumberOfLegs(1);
    // setter antall nodepunkter (beregningspunkter/grid) pr. // leg
    pipe.setNumberOfNodesInLeg(10);

    double[] height = {0, 0};
    double[] length = {0.0, 10.0};
    double[] outerTemperature = {278.0, 278.0};
    double[] outerHeatTransferCoef = {2.0, 2.0};
    double[] wallHeatTransferCoef = {2.0, 2.0};

    pipe.setLegHeights(height); // setter inn hoyde for hver leg-ende
    pipe.setLegPositions(length); // setter avstand til hver leg-ende
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegOuterHeatTransferCoefficients(outerHeatTransferCoef);
    pipe.setLegWallHeatTransferCoefficients(wallHeatTransferCoef);

    // Deffinerer geometrien for roret
    neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface[] pipeGemometry =
        new neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData[6];
    double[] pipeDiameter = {0.02588, 0.02588};
    for (int i = 0; i < pipeDiameter.length; i++) {
      pipeGemometry[i] =
          new neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData(pipeDiameter[i]);
    }
    pipe.setEquipmentGeometry(pipeGemometry); // setter inn rorgeometrien for hver leg
    // utforer beregninger
    pipe.createSystem();
    pipe.init();

    double[] times = {0, 10};
    pipe.getTimeSeries().setTimes(times);
    SystemInterface[] systems = {testSystem, testSystem, testSystem};
    pipe.getTimeSeries().setInletThermoSystems(systems);
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);
    pipe.solveSteadyState(2);
    pipe.solveTransient(2);
    System.out.println("disp");
    pipe.getNode(0).display();
    // pipe.solveTransient(2);
    // pipe.solveTransient(2);

    pipe.getNode(0).display();
    pipe.getNode(10).display();
  }
}
