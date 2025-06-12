package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseDropletFlow;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * BubbleFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class BubbleFlowNode extends TwoPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubbleFlowNode.class);
  private double averageBubbleDiameter = 0.001;

  /**
   * <p>
   * Constructor for BubbleFlowNode.
   * </p>
   */
  public BubbleFlowNode() {
    this.flowNodeType = "bubble";
  }

  /**
   * <p>
   * Constructor for BubbleFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public BubbleFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "bubble";
    this.interphaseTransportCoefficient = new InterphaseDropletFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for BubbleFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public BubbleFlowNode(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "bubble";
    this.interphaseTransportCoefficient = new InterphaseDropletFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /** {@inheritDoc} */
  @Override
  public double calcGasLiquidContactArea() {
    interphaseContactArea = pipe.getNodeLength() * interphaseContactLength[0];
    return interphaseContactArea;
  }

  /** {@inheritDoc} */
  @Override
  public void initFlowCalc() {
    // phaseFraction[0] = bulkSystem.getPhase(0).getBeta();
    phaseFraction[0] = getBulkSystem().getVolumeFraction(0);
    phaseFraction[1] = 1.0 - phaseFraction[0];
    initVelocity();
    this.init();

    initVelocity();
  }

  /** {@inheritDoc} */
  @Override
  public BubbleFlowNode clone() {
    BubbleFlowNode clonedSystem = null;
    try {
      clonedSystem = (BubbleFlowNode) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
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

    double volumeOfBubble = 4.0 / 3.0 * Math.PI * Math.pow(averageBubbleDiameter / 2.0, 3.0);
    double surfaceAreaOfBubble = 4.0 * Math.PI * Math.pow(averageBubbleDiameter / 2.0, 2.0);

    double numbDropletsPerTime = getBulkSystem().getPhase(0).getVolume("m3") / volumeOfBubble;
    interphaseContactLength[0] = numbDropletsPerTime * surfaceAreaOfBubble / velocity[0];
    interphaseContactLength[1] = interphaseContactLength[0];

    return wallContactLength[0];
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    BubbleFlowNode newNode = this.clone();

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
    SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkSchwartzentruberEos(295.3, 50.01325);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    PipeData pipe1 = new PipeData(0.0250203, 0.00025);
    testSystem.addComponent("CO2", 100.1061152181, "kg/hr", 0);
    testSystem.addComponent("water", 1000.206862204876, "kg/hr", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
    testSystem.initPhysicalProperties();
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    testSystem.display();
    FlowNodeInterface test = new BubbleFlowNode(testSystem, pipe1);
    test.setInterphaseModelType(1);
    test.setLengthOfNode(0.0001);
    test.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 20.0);

    test.getFluidBoundary().setHeatTransferCalc(false);
    test.getFluidBoundary().setMassTransferCalc(true);
    double length = 0;
    test.initFlowCalc();
    double[][] temperatures2 = new double[3][1000];
    int k = 0;
    for (int i = 0; i < 10000; i++) {
      length += test.getLengthOfNode();
      test.initFlowCalc();
      test.calcFluxes();
      if (i > 1 && (i % 100) == 0) {
        k++;
        test.display("length " + length);
        test.getBulkSystem().display("length " + length);
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

  /**
   * <p>
   * Getter for the field <code>averageBubbleDiameter</code>.
   * </p>
   *
   * @return a double
   */
  public double getAverageBubbleDiameter() {
    return averageBubbleDiameter;
  }

  /**
   * <p>
   * Setter for the field <code>averageBubbleDiameter</code>.
   * </p>
   *
   * @param averageBubbleDiameter a double
   */
  public void setAverageBubbleDiameter(double averageBubbleDiameter) {
    this.averageBubbleDiameter = averageBubbleDiameter;
  }
}
