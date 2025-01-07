package neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasereactorflowsystem;

import java.util.UUID;
import neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.twophaseflowvisualization.twophasepipeflowvisualization.TwoPhasePipeFlowVisualization;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TwoPhaseReactorFlowSystem class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TwoPhaseReactorFlowSystem
    extends neqsim.fluidmechanics.flowsystem.twophaseflowsystem.TwoPhaseFlowSystem {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for TwoPhaseReactorFlowSystem.
   * </p>
   */
  public TwoPhaseReactorFlowSystem() {}

  /** {@inheritDoc} */
  @Override
  public void createSystem() {
    // thermoSystem.init(1);
    flowLeg = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg[this.getNumberOfLegs()];

    for (int i = 0; i < getNumberOfLegs(); i++) {
      flowLeg[i] = new neqsim.fluidmechanics.flowleg.pipeleg.PipeLeg();
    }

    flowNode = new neqsim.fluidmechanics.flownode.FlowNodeInterface[totalNumberOfNodes];
    flowNode[0] =
        new neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode(
            thermoSystem, equipmentGeometry[0]);
    flowNode[totalNumberOfNodes - 1] = flowNode[0].getNextNode();

    super.createSystem();
    this.setNodes();
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    for (int j = 0; j < getTotalNumberOfNodes(); j++) {
      flowNode[j].setInterphaseModelType(1);
      flowNode[j].getGeometry(); // setPackingType()
      flowNode[j].initFlowCalc();
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
    double[] times = {0.0};
    display = new TwoPhasePipeFlowVisualization(this.getTotalNumberOfNodes(), 1);
    getTimeSeries().setTimes(times);
    neqsim.thermo.system.SystemInterface[] systems = {flowNode[0].getBulkSystem()};
    getTimeSeries().setInletThermoSystems(systems);
    getTimeSeries().setNumberOfTimeStepsInInterval(1);
    double[] outletFlowRates = {0.0, 0.0};
    getTimeSeries().setOutletMolarFlowRate(outletFlowRates);

    flowSolver =
        new neqsim.fluidmechanics.flowsolver.twophaseflowsolver.twophasepipeflowsolver.TwoPhaseFixedStaggeredGridSolver(
            this, getSystemLength(), this.getTotalNumberOfNodes(), false);
    flowSolver.setSolverType(type);
    flowSolver.solveTDMA();
    getTimeSeries().init(this);
    display.setNextData(this);
  }

  /** {@inheritDoc} */
  @Override
  public void solveTransient(int type, UUID id) {
    // pipeSolver pipeSolve = new pipeSolver(this, getSystemLength(),
    // getTotalNumberOfNodes());
    // pipeSolve.solveTDMA();
    calcIdentifier = id;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // Initierer et nyt rorsystem
    neqsim.fluidmechanics.flowsystem.FlowSystemInterface pipe = new TwoPhaseReactorFlowSystem();

    // Definerer termodyanmikken5
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(295.3, 5.0); // initierer
                                                           // et
                                                           // system
                                                           // som
                                                           // benytter
                                                           // SRK
                                                           // tilstandsligning
    // med trykk 305.3 K og 125 bar
    neqsim.thermodynamicoperations.ThermodynamicOperations testOps =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(testSystem); // gjor
                                                                                // termodyanmiske
                                                                                // Flash
                                                                                // rutiner
                                                                                // tilgjengelige
    testSystem.addComponent("methane", 0.11152181, 0);
    // testSystem.addComponent("ethane", 0.0011152181, 0);
    testSystem.addComponent("water", 0.04962204876, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // benytter klassiske blandingsregler

    pipe.setInletThermoSystem(testSystem); // setter termodyanmikken for rorsystemet
    pipe.setNumberOfLegs(5); // deler inn roret i et gitt antall legger
    pipe.setNumberOfNodesInLeg(100); // setter antall nodepunkter (beregningspunkter/grid) pr.
                                     // leg
    double[] height = {0, 0, 0, 0, 0, 0};
    double[] length = {0.0, 1.7, 3.5, 5.0, 7.5, 10.4};
    double[] outerTemperature =
        {278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 275.0, 275.0, 275.0, 275.0};

    pipe.setLegHeights(height); // setter inn hoyde for hver leg-ende
    pipe.setLegPositions(length); // setter avstand til hver leg-ende
    pipe.setLegOuterTemperatures(outerTemperature);

    neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface[] pipeGemometry =
        new neqsim.fluidmechanics.geometrydefinitions.reactor.ReactorData[5]; // Deffinerer
                                                                              // geometrien
                                                                              // for
                                                                              // roret
    double[] pipeDiameter = {0.02588, 0.02588, 0.02588, 0.02588, 0.02588};
    for (int i = 0; i < pipeDiameter.length; i++) {
      pipeGemometry[i] =
          new neqsim.fluidmechanics.geometrydefinitions.reactor.ReactorData(pipeDiameter[i], 1);
    }
    pipe.setEquipmentGeometry(pipeGemometry); // setter inn rorgeometrien for hver leg
    // utforer beregninger
    pipe.createSystem();
    pipe.init();

    pipe.solveSteadyState(2);
    // pipe.calcFluxes();
    // pipe.getDisplay().displayResult("temperature");
    // pipe.displayResults();
    // testOps.TPflash();
    // testOps.displayResult();
  }
}
