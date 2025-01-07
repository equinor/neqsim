package neqsim.fluidmechanics.flownode.multiphasenode.waxnode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.NonEquilibriumFluidBoundary;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseStratifiedFlow;
import neqsim.fluidmechanics.flownode.multiphasenode.MultiPhaseFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * WaxDepositionFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WaxDepositionFlowNode extends MultiPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WaxDepositionFlowNode.class);

  /**
   * <p>
   * Constructor for WaxDepositionFlowNode.
   * </p>
   */
  public WaxDepositionFlowNode() {
    this.flowNodeType = "wax deposition node";
  }

  /**
   * <p>
   * Constructor for WaxDepositionFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public WaxDepositionFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "wax deposition node";
    this.interphaseTransportCoefficient = new InterphaseStratifiedFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for WaxDepositionFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public WaxDepositionFlowNode(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    this(system, pipe);
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
    SystemInterface testSystem = new SystemSrkEos(273.15 + 40.0, 10.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(275.3,
    // ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    PipeData pipe1 = new PipeData(0.203, 0.00025);

    testSystem.addComponent("methane", 25.0, 0);
    // testSystem.addComponent("water", 1.95, 1);
    testSystem.addComponent("nC16", 1.5, 1);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.display();

    FlowNodeInterface test = new StratifiedFlowNode(testSystem, pipe1);
    test.setInterphaseModelType(1);
    test.setLengthOfNode(0.005);
    test.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 40.0);

    test.getFluidBoundary().setHeatTransferCalc(true);
    test.getFluidBoundary().setMassTransferCalc(true);

    test.initFlowCalc();
    test.calcFluxes();
    test.update();
    test.getInterphaseSystem().display();
    test.getFluidBoundary().display("");
    test.getFluidBoundary().getInterphaseSystem().display();
    // test.getBulkSystem().display();

    for (int i = 0; i < 5; i++) {
      test.initFlowCalc();
      test.calcFluxes();
      test.update();
    }
    test.getBulkSystem().display();

    /*
     * double length = 0;
     *
     * double[][] temperatures2 = new double[3][1000]; int k = 0; for (int i = 0; i < 11; i++) {
     * length += test.getLengthOfNode(); test.initFlowCalc(); test.calcFluxes(); if (i > 1 && (i %
     * 1) == 0) { k++; test.display("length " + length); test.getBulkSystem().display("length " +
     * length); test.getInterphaseSystem().display("length " + length);
     * //test.getFluidBoundary().display("length " + length); test.setLengthOfNode(0.000005 +
     * test.getLengthOfNode() / 2.0); temperatures2[0][k] = length; temperatures2[1][k] =
     * test.getGeometry().getTemperature(); test.getFluidBoundary().display("test"); }
     *
     * //test.getBulkSystem().display(); test.update(); test.getFluidBoundary().display("length " +
     * length); test.getInterphaseSystem().display("length " + length);
     *
     * //test.getFluidBoundary().display("test"); }
     *
     * for (int i = 0; i < k; i++) { System.out.println("len temp  " + temperatures2[0][i] + " " +
     * temperatures2[1][i]); }
     */
  }
}
