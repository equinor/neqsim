package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseStratifiedFlow;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * StratifiedFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StratifiedFlowNode extends TwoPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StratifiedFlowNode.class);

  /**
   * <p>
   * Constructor for StratifiedFlowNode.
   * </p>
   */
  public StratifiedFlowNode() {
    this.flowNodeType = "stratified";
  }

  /**
   * <p>
   * Constructor for StratifiedFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public StratifiedFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "stratified";
    this.interphaseTransportCoefficient = new InterphaseStratifiedFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for StratifiedFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public StratifiedFlowNode(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "stratified";
    this.interphaseTransportCoefficient = new InterphaseStratifiedFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /** {@inheritDoc} */
  @Override
  public StratifiedFlowNode clone() {
    StratifiedFlowNode clonedSystem = null;
    try {
      clonedSystem = (StratifiedFlowNode) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    inclination = 0.0;
    this.calcContactLength();
    // System.out.println("len " + this.calcContactLength());
    super.init();
  }

  /** {@inheritDoc} */
  @Override
  public double calcContactLength() {
    double phaseAngel =
        pi * phaseFraction[1] + Math.pow(3.0 * pi / 2.0, 1.0 / 3.0) * (1.0 - 2.0 * phaseFraction[1]
            + Math.pow(phaseFraction[1], 1.0 / 3.0) - Math.pow(phaseFraction[0], 1.0 / 3.0));
    wallContactLength[1] = phaseAngel * pipe.getDiameter();
    wallContactLength[0] = pi * pipe.getDiameter() - wallContactLength[1];
    interphaseContactLength[0] = pipe.getDiameter() * Math.sin(phaseAngel);
    interphaseContactLength[1] = pipe.getDiameter() * Math.sin(phaseAngel);
    return wallContactLength[0];
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    StratifiedFlowNode newNode = this.clone();

    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      // newNode.getBulkSystem().getPhases()[0].addMoles(i, -molarMassTransfer[i]);
      // newNode.getBulkSystem().getPhases()[1].addMoles(i, +molarMassTransfer[i]);
    }

    return newNode;
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
    // SystemInterface testSystem = new SystemSrkEos(273.15 + 11.0, 60.0);
    SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(325.3, 100.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    PipeData pipe1 = new PipeData(0.250203, 0.00025);
    testSystem.addComponent("methane", 0.1, "MSm3/day", 0);
    testSystem.addComponent("water", 0.4 * 5.0, "kg/hr", 1);
    testSystem.addComponent("MEG", 0.6 * 5.0, "kg/hr", 1);
    // testSystem.addComponent("nitrogen", 25.0, 0);
    // testSystem.addComponent("CO2", 250.0, 0);
    // testSystem.addComponent("methane", 5.0, 0);
    // testSystem.addComponent("nitrogen", 5.0, 1);
    // testSystem.addComponent("CO2", 250.0, 1);
    // testSystem.addComponent("methane", 25.0, 1);
    // testSystem.addComponent("n-pentane", 25.0, 1);
    // testSystem.addComponent("MDEA", 0.08, 1);
    // testSystem.getPhase(1).setTemperature(275);
    // testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    // testSystem.getPhase(0).setTemperature(273.15 + 100.0);
    testSystem.initPhysicalProperties();

    // testSystem.addComponent("nitrogen", testSystem.getPhase(1).getMolarVolume() /
    // testSystem.getPhase(0).getMolarVolume() *
    // testSystem.getPhase(0).getComponent("CO2").getNumberOfmoles(), 0);

    // testSystem.getChemicalReactionOperations().solveChemEq(1);
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    // testOps.TPflash();
    testSystem.display();
    // testSystem.setTemperature(273.15+20);
    // testSystem.initPhysicalProperties();

    // FlowNodeInterface test = new StratifiedFlowNode(testSystem, pipe1);
    // FlowNodeInterface test = new AnnularFlow(testSystem, pipe1);
    FlowNodeInterface test = new DropletFlowNode(testSystem, pipe1);
    test.setInterphaseModelType(1);
    test.setLengthOfNode(0.001);
    test.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 4.0);

    test.getFluidBoundary().setHeatTransferCalc(false);
    test.getFluidBoundary().setMassTransferCalc(true);
    double length = 0;
    test.initFlowCalc();
    double[][] temperatures2 = new double[3][1000];
    int k = 0;
    for (int i = 0; i < 100000; i++) {
      length += test.getLengthOfNode();
      test.initFlowCalc();
      test.calcFluxes();
      if (i > 1 && (i % 1000) == 0) {
        k++;
        test.display("length " + length);
        System.out.println("length " + length + " wt% MEG "
            + test.getBulkSystem().getPhase("aqueous").getWtFrac("MEG") * 100.0);
        // test.getBulkSystem().display("length " + length);
        // test.getInterphaseSystem().display("length " + length);
        // test.getFluidBoundary().display("length " + length);
        // test.setLengthOfNode(0.000005 + test.getLengthOfNode() / 2.0);
        temperatures2[0][k] = length;
        temperatures2[1][k] = test.getGeometry().getInnerWallTemperature();
        // test.getFluidBoundary().display("test");
      }

      // test.getBulkSystem().display();
      test.update();
      // test.getFluidBoundary().display("length " + length);
      // test.getInterphaseSystem().display("length " + length);

      // test.getFluidBoundary().display("test");
    }

    for (int i = 0; i < k; i++) {
      System.out.println("len temp  " + temperatures2[0][i] + " " + temperatures2[1][i]);
    }
    System.out.println("contact length " + test.getInterphaseContactArea());
  }
}
