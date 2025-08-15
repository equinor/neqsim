package neqsim.fluidmechanics.flownode.twophasenode.twophasestirredcellnode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.stirredcell.InterphaseStirredCellFlow;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.stirredcell.StirredCell;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * StirredCellNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class StirredCellNode extends TwoPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(StirredCellNode.class);

  private double[] stirrerRate = {1.0, 1.0};
  private double[] stirrerDiameter = {1.0, 1.0};
  private double dt = 1.0;

  /**
   * <p>
   * Constructor for StirredCellNode.
   * </p>
   */
  public StirredCellNode() {
    this.flowNodeType = "stirred cell";
  }

  /**
   * <p>
   * Constructor for StirredCellNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public StirredCellNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "stirred cell";
    this.interphaseTransportCoefficient = new InterphaseStirredCellFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for StirredCellNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public StirredCellNode(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "stirred cell";
    this.interphaseTransportCoefficient = new InterphaseStirredCellFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /** {@inheritDoc} */
  @Override
  public double calcHydraulicDiameter() {
    return getGeometry().getDiameter();
  }

  /** {@inheritDoc} */
  @Override
  public double calcReynoldNumber() {
    reynoldsNumber[1] = Math.pow(stirrerDiameter[1], 2.0) * stirrerRate[1]
        * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[1].getPhysicalProperties().getViscosity();
    reynoldsNumber[0] = Math.pow(stirrerDiameter[0], 2.0) * stirrerRate[0]
        * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
        / bulkSystem.getPhases()[0].getPhysicalProperties().getViscosity();
    return reynoldsNumber[1];
  }

  /** {@inheritDoc} */
  @Override
  public StirredCellNode clone() {
    StirredCellNode clonedSystem = null;
    try {
      clonedSystem = (StirredCellNode) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    this.calcContactLength();
    super.init();
  }

  /** {@inheritDoc} */
  @Override
  public void initFlowCalc() {
    this.init();
  }

  /** {@inheritDoc} */
  @Override
  public double calcContactLength() {
    wallContactLength[1] = 1.0;
    wallContactLength[0] = 1.0;

    interphaseContactLength[0] = pi * Math.pow(pipe.getDiameter(), 2.0) / 4.0;
    interphaseContactLength[1] = interphaseContactLength[0];
    interphaseContactArea = interphaseContactLength[0];
    return wallContactLength[0];
  }

  /** {@inheritDoc} */
  @Override
  public double calcGasLiquidContactArea() {
    return pi * Math.pow(pipe.getDiameter(), 2.0) / 4.0;
  }

  /** {@inheritDoc} */
  @Override
  public void update() {
    for (int componentNumber = 0; componentNumber < getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); componentNumber++) {
      double liquidMolarRate = getFluidBoundary().getInterphaseMolarFlux(componentNumber)
          * getInterphaseContactArea() * getDt();
      double gasMolarRate = -getFluidBoundary().getInterphaseMolarFlux(componentNumber)
          * getInterphaseContactArea() * getDt();
      // System.out.println("liquidMolarRate" + liquidMolarRate);
      getBulkSystem().getPhases()[0].addMoles(componentNumber, gasMolarRate);
      getBulkSystem().getPhases()[1].addMoles(componentNumber, liquidMolarRate);
    }
    // getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().init(1);

    if (bulkSystem.isChemicalSystem()) {
      getOperations().chemicalEquilibrium();
    }
    getBulkSystem().init(1);
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    StirredCellNode newNode = this.clone();
    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      // newNode.getBulkSystem().getPhases()[0].addMoles(i, -molarMassTransfer[i]);
      // newNode.getBulkSystem().getPhases()[1].addMoles(i, +molarMassTransfer[i]);
    }
    return newNode;
  }

  /**
   * Getter for property stirrerRate.
   *
   * @param i a int
   * @return Value of property stirrerRate.
   */
  public double getStirrerRate(int i) {
    return stirrerRate[i];
  }

  /**
   * Setter for property stirrerRate.
   *
   * @param stirrerRate New value of property stirrerRate.
   * @param i a int
   */
  public void setStirrerSpeed(int i, double stirrerRate) {
    this.stirrerRate[i] = stirrerRate;
  }

  /**
   * <p>
   * setStirrerSpeed.
   * </p>
   *
   * @param stirrerRate a double
   */
  public void setStirrerSpeed(double stirrerRate) {
    this.stirrerRate[0] = stirrerRate;
    this.stirrerRate[1] = stirrerRate;
  }

  /**
   * Getter for property dt.
   *
   * @return Value of property dt.
   */
  public double getDt() {
    return dt;
  }

  /**
   * Setter for property dt.
   *
   * @param dt New value of property dt.
   */
  public void setDt(double dt) {
    this.dt = dt;
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
    // SystemInterface testSystem = new SystemFurstElectrolyteEos(275.3,
    // ThermodynamicConstantsInterface.referencePressure);
    // SystemInterface testSystem = new SystemSrkEos(313.3, 70.01325);
    SystemInterface testSystem = new SystemSrkCPAstatoil(313.3, 70.01325);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    StirredCell pipe1 = new StirredCell(2.0, 0.05);

    testSystem.addComponent("methane", 0.1061152181, "MSm3/hr", 0);
    testSystem.addComponent("water", 10.206862204876, "kg/min", 0);
    testSystem.addComponent("methanol", 1011.206862204876, "kg/min", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();
    StirredCellNode test = new StirredCellNode(testSystem, pipe1);
    test.setInterphaseModelType(1);
    test.getFluidBoundary().useFiniteFluxCorrection(true);
    test.getFluidBoundary().useThermodynamicCorrections(true);
    test.setStirrerSpeed(10.0 / 60.0);
    test.setStirrerDiameter(0.05);
    test.setDt(1.10);

    test.initFlowCalc();
    // testSystem.init(0);
    // testOps.TPflash();

    // test.display();
    for (int i = 0; i < 120; i++) {
      test.initFlowCalc();
      test.calcFluxes();
      test.update();
      // test.display("new");
      // test.getBulkSystem().display();
      // test.getFluidBoundary().display("test");
    }
    test.getBulkSystem().prettyPrint();
  }

  /**
   * Getter for property stirrerDiameter.
   *
   * @return Value of property stirrerDiameter.
   */
  public double[] getStirrerDiameter() {
    return this.stirrerDiameter;
  }

  /**
   * <p>
   * Setter for the field <code>stirrerDiameter</code>.
   * </p>
   *
   * @param stirrerDiameter a double
   */
  public void setStirrerDiameter(double stirrerDiameter) {
    this.stirrerDiameter[0] = stirrerDiameter;
    this.stirrerDiameter[1] = stirrerDiameter;
  }

  /**
   * Setter for property stirrerDiameter.
   *
   * @param stirrerDiameter New value of property stirrerDiameter.
   */
  public void setStirrerDiameter(double[] stirrerDiameter) {
    this.stirrerDiameter = stirrerDiameter;
  }
}
