package neqsim.fluidmechanics.flownode;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.FluidBoundaryInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.InterphaseTransportCoefficientBaseClass;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.InterphaseTransportCoefficientInterface;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.util.DoubleCloneable;

/**
 * <p>
 * Abstract FlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class FlowNode implements FlowNodeInterface, ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FlowNode.class);

  protected double distanceToCenterOfNode = 0;
  protected double lengthOfNode = 0;
  protected double veticalPositionOfNode = 0;
  protected double[] hydraulicDiameter;
  protected double[] reynoldsNumber;
  protected int[] flowDirection;
  protected double[] interphaseContactLength;
  protected double[] wallContactLength;
  protected double[] phaseFraction;
  public double[] molarFlowRate;
  public double[] massFlowRate;
  public double[] volumetricFlowRate;

  protected ThermodynamicOperations operations;
  protected String flowNodeType = null;
  protected FluidBoundaryInterface fluidBoundary = null;
  public SystemInterface bulkSystem;
  protected double inclination = 0;
  public DoubleCloneable[] velocityIn;
  public DoubleCloneable[] velocityOut;
  public double[] superficialVelocity;
  public double interphaseContactArea = 0;
  public double[] velocity;
  public GeometryDefinitionInterface pipe;
  protected InterphaseTransportCoefficientInterface interphaseTransportCoefficient;
  protected double[] wallFrictionFactor;

  protected double[] interphaseFrictionFactor;

  protected Double[] specifiedFrictionFactor = null;
  protected ThermodynamicOperations phaseOps;

  /**
   * <p>
   * Constructor for FlowNode.
   * </p>
   */
  public FlowNode() {
    this.bulkSystem = null;
    this.pipe = null;
    this.fluidBoundary = null;
    this.interphaseTransportCoefficient = null;
  }

  /**
   * <p>
   * Constructor for FlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FlowNode(SystemInterface system) {
    this.interphaseTransportCoefficient = new InterphaseTransportCoefficientBaseClass(this);
    this.velocityIn = new DoubleCloneable[2];
    this.velocityOut = new DoubleCloneable[2];
    this.velocity = new double[2];
    this.flowDirection = new int[2];
    this.wallFrictionFactor = new double[2];
    this.specifiedFrictionFactor = new Double[2];
    this.superficialVelocity = new double[2];
    phaseFraction = new double[2];
    hydraulicDiameter = new double[2];
    reynoldsNumber = new double[2];
    interphaseContactLength = new double[2];
    wallContactLength = new double[2];
    molarFlowRate = new double[2];
    massFlowRate = new double[2];
    volumetricFlowRate = new double[2];
    interphaseFrictionFactor = new double[2];
    velocity[0] = 0.0;

    this.bulkSystem = system.clone();
    if (this.bulkSystem.isChemicalSystem()) {
      this.bulkSystem.chemicalReactionInit();
    }
    operations = new ThermodynamicOperations(this.bulkSystem);
    // bulkSystem.getChemicalReactionOperations().setSystem(bulkSystem);
    for (int i = 0; i < 2; i++) {
      this.velocityOut[i] = new DoubleCloneable();
      this.velocityIn[i] = new DoubleCloneable();
      this.flowDirection[i] = 1;
    }
  }

  /**
   * <p>
   * Constructor for FlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public FlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    this(system);
    this.pipe = pipe.clone();
  }

  /**
   * <p>
   * Constructor for FlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   * @param lengthOfNode a double
   * @param distanceToCenterOfNode a double
   */
  public FlowNode(SystemInterface system, GeometryDefinitionInterface pipe, double lengthOfNode,
      double distanceToCenterOfNode) {
    this(system, pipe);
    this.lengthOfNode = lengthOfNode;
    this.distanceToCenterOfNode = distanceToCenterOfNode;
  }

  /** {@inheritDoc} */
  @Override
  public void setFrictionFactorType(int type) {
    if (type == 0) {
      interphaseTransportCoefficient = new InterphaseTransportCoefficientBaseClass(this);
    }
    if (type == 1) {
      interphaseTransportCoefficient = new InterphaseTransportCoefficientBaseClass(this);
    } else {
      System.out.println("error choosing friction type");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setGeometryDefinitionInterface(GeometryDefinitionInterface pipe) {
    this.pipe = pipe.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void setDistanceToCenterOfNode(double distanceToCenterOfNode) {
    this.distanceToCenterOfNode = distanceToCenterOfNode;
  }

  /** {@inheritDoc} */
  @Override
  public InterphaseTransportCoefficientInterface getInterphaseTransportCoefficient() {
    return interphaseTransportCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double getDistanceToCenterOfNode() {
    return this.distanceToCenterOfNode;
  }

  /** {@inheritDoc} */
  @Override
  public double getVerticalPositionOfNode() {
    return this.veticalPositionOfNode;
  }

  /** {@inheritDoc} */
  @Override
  public void setVerticalPositionOfNode(double veticalPositionOfNode) {
    this.veticalPositionOfNode = veticalPositionOfNode;
  }

  /** {@inheritDoc} */
  @Override
  public double getSuperficialVelocity(int i) {
    return this.superficialVelocity[i];
  }

  /** {@inheritDoc} */
  @Override
  public String getFlowNodeType() {
    return this.flowNodeType;
  }

  /** {@inheritDoc} */
  @Override
  public void setLengthOfNode(double lengthOfNode) {
    this.lengthOfNode = lengthOfNode;
    getGeometry().setNodeLength(lengthOfNode);
  }

  /** {@inheritDoc} */
  @Override
  public double getLengthOfNode() {
    return lengthOfNode;
  }

  /** {@inheritDoc} */
  @Override
  public FlowNode clone() {
    FlowNode clonedSystem = null;
    try {
      clonedSystem = (FlowNode) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());;
    }

    clonedSystem.bulkSystem = bulkSystem.clone();
    clonedSystem.pipe = pipe.clone();
    clonedSystem.velocity = new double[2];
    clonedSystem.volumetricFlowRate = new double[2];
    clonedSystem.reynoldsNumber = new double[2];
    clonedSystem.wallFrictionFactor = new double[2];
    clonedSystem.hydraulicDiameter = new double[2];
    clonedSystem.reynoldsNumber = new double[2];
    clonedSystem.interphaseContactLength = new double[2];
    clonedSystem.wallContactLength = new double[2];
    clonedSystem.flowDirection = new int[2];
    clonedSystem.phaseFraction = new double[2];
    clonedSystem.molarFlowRate = new double[2];
    clonedSystem.massFlowRate = new double[2];
    clonedSystem.interphaseFrictionFactor = new double[2];

    clonedSystem.velocityIn = velocityIn.clone();
    clonedSystem.velocityOut = velocityOut.clone();

    for (int i = 0; i < 2; i++) {
      clonedSystem.velocityIn[i] = velocityIn[i].clone();
      clonedSystem.velocityOut[i] = velocityOut[i].clone();
    }
    System.arraycopy(this.flowDirection, 0, clonedSystem.flowDirection, 0,
        this.flowDirection.length);
    System.arraycopy(this.wallContactLength, 0, clonedSystem.wallContactLength, 0,
        this.wallContactLength.length);
    System.arraycopy(this.interphaseFrictionFactor, 0, clonedSystem.interphaseFrictionFactor, 0,
        this.interphaseFrictionFactor.length);
    System.arraycopy(this.molarFlowRate, 0, clonedSystem.molarFlowRate, 0,
        this.molarFlowRate.length);
    System.arraycopy(this.massFlowRate, 0, clonedSystem.massFlowRate, 0, this.massFlowRate.length);
    System.arraycopy(this.volumetricFlowRate, 0, clonedSystem.volumetricFlowRate, 0,
        this.volumetricFlowRate.length);
    System.arraycopy(this.phaseFraction, 0, clonedSystem.phaseFraction, 0,
        this.phaseFraction.length);
    System.arraycopy(this.velocity, 0, clonedSystem.velocity, 0, this.velocity.length);
    System.arraycopy(this.hydraulicDiameter, 0, clonedSystem.hydraulicDiameter, 0,
        this.hydraulicDiameter.length);
    System.arraycopy(this.reynoldsNumber, 0, clonedSystem.reynoldsNumber, 0,
        this.reynoldsNumber.length);
    System.arraycopy(this.wallFrictionFactor, 0, clonedSystem.wallFrictionFactor, 0,
        this.wallFrictionFactor.length);

    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    initBulkSystem();
  }

  /** {@inheritDoc} */
  @Override
  public void initBulkSystem() {
    bulkSystem.initBeta();
    bulkSystem.init_x_y();
    bulkSystem.init(3);
    bulkSystem.initPhysicalProperties();
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getBulkSystem() {
    return bulkSystem;
  }

  /** {@inheritDoc} */
  @Override
  public DoubleCloneable getVelocityOut(int i) {
    return velocityOut[i];
  }

  /** {@inheritDoc} */
  @Override
  public DoubleCloneable getVelocityIn(int i) {
    return velocityIn[i];
  }

  /** {@inheritDoc} */
  @Override
  public void setVelocity(int phase, double vel) {
    velocity[phase] = vel;
  }

  /** {@inheritDoc} */
  @Override
  public void setVelocityIn(int phase, double vel) {
    velocityIn[phase].set(vel);
  }

  /** {@inheritDoc} */
  @Override
  public void setVelocityIn(int phase, DoubleCloneable vel) {
    velocityIn[phase] = vel;
  }

  /** {@inheritDoc} */
  @Override
  public void setVelocityOut(int phase, double vel) {
    velocityOut[phase].set(vel);
  }

  /** {@inheritDoc} */
  @Override
  public void setVelocityOut(int phase, DoubleCloneable vel) {
    velocityOut[phase] = vel;
  }

  /** {@inheritDoc} */
  @Override
  public double getVelocity(int phase) {
    return velocity[phase];
  }

  /** {@inheritDoc} */
  @Override
  public void setWallFrictionFactor(int phase, double frictionFactor) {
    if (specifiedFrictionFactor[0] == null) {
      specifiedFrictionFactor = new Double[2];
    }
    specifiedFrictionFactor[phase] = frictionFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getWallFrictionFactor(int phase) {
    if (specifiedFrictionFactor != null && specifiedFrictionFactor[phase] != null) {
      return specifiedFrictionFactor[phase];
    }
    return wallFrictionFactor[phase];
  }

  /** {@inheritDoc} */
  @Override
  public double getInterPhaseFrictionFactor() {
    return interphaseFrictionFactor[0];
  }

  /** {@inheritDoc} */
  @Override
  public double getReynoldsNumber(int i) {
    return reynoldsNumber[i];
  }

  /** {@inheritDoc} */
  @Override
  public double getHydraulicDiameter(int i) {
    return hydraulicDiameter[i];
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getInterphaseSystem() {
    return fluidBoundary.getInterphaseSystem();
  }

  /** {@inheritDoc} */
  @Override
  public FluidBoundaryInterface getFluidBoundary() {
    return fluidBoundary;
  }

  /** {@inheritDoc} */
  @Override
  public GeometryDefinitionInterface getGeometry() {
    return pipe;
  }

  /** {@inheritDoc} */
  @Override
  public void setInterphaseSystem(SystemInterface interphaseSystem) {
    fluidBoundary.setInterphaseSystem(interphaseSystem.clone());
  }

  /** {@inheritDoc} */
  @Override
  public void setInterphaseModelType(int i) {
    if (i == 0) {
      // System.out.println("set equilibrium");
      this.fluidBoundary =
          new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.equilibriumfluidboundary.EquilibriumFluidBoundary(
              this);
    } else {
      // System.out.println("set non equilibrium");
      if (bulkSystem.isChemicalSystem()) {
        this.fluidBoundary =
            new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.reactivefilmmodel.ReactiveKrishnaStandartFilmModel(
                this);
      } else {
        this.fluidBoundary =
            new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
                this);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setBulkSystem(SystemInterface bulkSystem) {
    this.bulkSystem = bulkSystem.clone();
    phaseOps = new ThermodynamicOperations(this.getBulkSystem());
    phaseOps.TPflash();
    init();
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    return this.clone();
  }

  /** {@inheritDoc} */
  @Override
  public double getVolumetricFlow() {
    return volumetricFlowRate[0];
  }

  /** {@inheritDoc} */
  @Override
  public void calcFluxes() {}

  /** {@inheritDoc} */
  @Override
  public void setFluxes(double[] dn) {}

  /** {@inheritDoc} */
  @Override
  public double calcSherwoodNumber(double schmidtNumber, int phase) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcNusseltNumber(double prandtlNumber, int phase) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getPrandtlNumber(int phaseNum) {
    return getBulkSystem().getPhase(phaseNum).getCp()
        * getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getViscosity()
        / getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
  }

  /** {@inheritDoc} */
  @Override
  public double getSchmidtNumber(int phase, int component1, int component2) {
    return getBulkSystem().getPhase(phase).getPhysicalProperties()
        .getDiffusionCoefficient(component1, component2)
        / getBulkSystem().getPhase(phase).getPhysicalProperties().getKinematicViscosity();
  }

  /** {@inheritDoc} */
  @Override
  public double getEffectiveSchmidtNumber(int phase, int component) {
    getBulkSystem().getPhase(phase).getPhysicalProperties().calcEffectiveDiffusionCoefficients();
    return getBulkSystem().getPhase(phase).getPhysicalProperties().getKinematicViscosity()
        / getBulkSystem().getPhase(phase).getPhysicalProperties()
            .getEffectiveDiffusionCoefficient(component);
  }

  /** {@inheritDoc} */
  @Override
  public double calcStantonNumber(double schmidtNumber, int phase) {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public double getArea(int i) {
    return pipe.getArea() * phaseFraction[i];
  }

  /** {@inheritDoc} */
  @Override
  public void updateMolarFlow() {}

  /** {@inheritDoc} */
  @Override
  public double getPhaseFraction(int phase) {
    return phaseFraction[phase];
  }

  /** {@inheritDoc} */
  @Override
  public double getInterphaseContactArea() {
    return interphaseContactArea;
  }

  /** {@inheritDoc} */
  @Override
  public void setPhaseFraction(int phase, double frac) {
    phaseFraction[phase] = frac;
  }

  /** {@inheritDoc} */
  @Override
  public double getWallContactLength(int phase) {
    return wallContactLength[phase];
  }

  /** {@inheritDoc} */
  @Override
  public double getInterphaseContactLength(int phase) {
    return interphaseContactLength[phase];
  }

  /** {@inheritDoc} */
  @Override
  public double getMassFlowRate(int phase) {
    return massFlowRate[phase];
  }

  /** {@inheritDoc} */
  @Override
  public void increaseMolarRate(double moles) {}

  /** {@inheritDoc} */
  @Override
  public double calcTotalHeatTransferCoefficient(int phaseNum) {
    double prandtlNumber = getBulkSystem().getPhase(phaseNum).getCp()
        / getBulkSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
        * getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getViscosity()
        / getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getConductivity();
    double temp = 1.0 / (1.0
        / interphaseTransportCoefficient.calcWallHeatTransferCoefficient(phaseNum, prandtlNumber,
            this)
        + 1.0 / pipe.getWallHeatTransferCoefficient()
        + 1.0 / pipe.getSurroundingEnvironment().getHeatTransferCoefficient());
    return temp;
  }

  /** {@inheritDoc} */
  @Override
  public void setEnhancementType(int type) {}

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void display(String name) {
    DecimalFormat nf = new DecimalFormat();

    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    JDialog dialog = new JDialog(new JFrame(), "Node-Report");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new BorderLayout());
    Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
    dialog.setSize(screenDimension.width / 2, screenDimension.height / 2); // pack();
    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    String[][] table = createTable(name);
    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    if (table.length > 0) {
      Jtab.setRowHeight(dialog.getHeight() / table.length);
      Jtab.setFont(new Font("Serif", Font.PLAIN,
          dialog.getHeight() / table.length - dialog.getHeight() / table.length / 10));
      // dialog.pack();
    }
    dialog.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void update() {}

  /** {@inheritDoc} */
  @Override
  public neqsim.thermodynamicoperations.ThermodynamicOperations getOperations() {
    return operations;
  }

  /**
   * Setter for property operations.
   *
   * @param operations New value of property operations.
   */
  public void setOperations(neqsim.thermodynamicoperations.ThermodynamicOperations operations) {
    this.operations = operations;
  }

  /** {@inheritDoc} */
  @Override
  public double getMolarMassTransferRate(int componentNumber) {
    return getFluidBoundary().getInterphaseMolarFlux(componentNumber) * interphaseContactArea;
  }

  /** {@inheritDoc} */
  @Override
  public int getFlowDirection(int i) {
    return this.flowDirection[i];
  }

  /** {@inheritDoc} */
  @Override
  public void setFlowDirection(int flowDirection, int i) {
    this.flowDirection[i] = flowDirection;
  }

  /**
   * <p>
   * createTable.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] createTable(String name) {
    int rows = 0;
    if (bulkSystem == null) {
      String[][] table = new String[0][5];
      return table;
    }

    rows = bulkSystem.getPhases()[0].getNumberOfComponents() * 10;
    String[][] table = new String[rows][5];

    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);
    for (int i = 0; i < bulkSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = bulkSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] =
            nf.format(bulkSystem.getPhase(bulkSystem.getPhaseIndex(i)).getComponent(j).getx(), buf,
                test).toString();
        table[j + 1][4] = "[-] bulk";
      }

      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][0] =
            getInterphaseSystem().getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] =
            nf.format(getInterphaseSystem().getPhase(getInterphaseSystem().getPhaseIndex(i))
                .getComponent(j).getx(), buf, test).toString();
        table[j + bulkSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[-] interface";
      }

      for (int j = 0; j < bulkSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][0] =
            bulkSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] =
            nf.format(getFluidBoundary().getInterphaseMolarFlux(j), buf, test).toString();
        table[j + 2 * bulkSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[mol/sec*m^2]";
      }
      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Reynolds Number";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] =
          nf.format(reynoldsNumber[i], buf, test).toString();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[-]";

      // Double.longValue(system.getPhase(phaseIndex[i]).getBeta());
      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 6][0] = "Velocity";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 6][i + 1] =
          nf.format(velocity[i], buf, test).toString();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 6][4] = "[m/sec]";

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Gas Heat Flux";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] =
          nf.format(getFluidBoundary().getInterphaseHeatFlux(0), buf, test).toString();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[J/sec*m^2]";

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Pressure";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] =
          Double.toString(bulkSystem.getPhase(bulkSystem.getPhaseIndex(i)).getPressure());
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[bar]";

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 9][0] = "Bulk Temperature";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 9][i + 1] =
          Double.toString(bulkSystem.getPhase(bulkSystem.getPhaseIndex(i)).getTemperature());
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 9][4] = "[K]";

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 10][0] =
          "Interface Temperature";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] = Double
          .toString(getInterphaseSystem().getPhase(bulkSystem.getPhaseIndex(i)).getTemperature());
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[K]";

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Interface Area";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] =
          nf.format(getInterphaseContactArea());
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[m^2]";

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 12][0] =
          "Inner wall temperature";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 12][i + 1] =
          Double.toString(pipe.getInnerWallTemperature());
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 12][4] = "K";

      buf = new StringBuffer();
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Node";
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
      table[3 * bulkSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
    }

    return table;
  }

  /** {@inheritDoc} */
  @Override
  public void write(String name, String filename, boolean newfile) {
    String[][] table = createTable(name);
    neqsim.datapresentation.filehandling.TextFile file =
        new neqsim.datapresentation.filehandling.TextFile();
    if (newfile) {
      file.newFile(filename);
    }
    file.setOutputFileName(filename);
    file.setValues(table);
    file.createFile();
    getBulkSystem().write(("thermo for " + name), filename, false);
    getFluidBoundary().write(("boundary for " + name), filename, false);
  }
}
