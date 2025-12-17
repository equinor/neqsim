package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseDropletFlow;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * DropletFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class DropletFlowNode extends TwoPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private double averageDropletDiameter = 100.0e-6;

  /**
   * <p>
   * Constructor for DropletFlowNode.
   * </p>
   */
  public DropletFlowNode() {
    this.flowNodeType = "droplet";
  }

  /**
   * <p>
   * Constructor for DropletFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public DropletFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "droplet";
    this.interphaseTransportCoefficient = new InterphaseDropletFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for DropletFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public DropletFlowNode(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "droplet";
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
  public DropletFlowNode clone() {
    DropletFlowNode clonedSystem = null;
    try {
      clonedSystem = (DropletFlowNode) super.clone();
    } catch (Exception ex) {
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

    double volumeOfDroplet = 4.0 / 3.0 * Math.PI * Math.pow(averageDropletDiameter / 2.0, 3.0);
    double surfaceAreaOfDroplet = 4.0 * Math.PI * Math.pow(averageDropletDiameter / 2.0, 2.0);

    double numbDropletsPerTime = getBulkSystem().getPhase(1).getVolume("m3") / volumeOfDroplet;
    interphaseContactLength[0] = numbDropletsPerTime * surfaceAreaOfDroplet / velocity[0];
    interphaseContactLength[1] = interphaseContactLength[0];

    return wallContactLength[0];
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For droplet/mist flow, the interfacial area per unit volume is calculated using Sauter mean
   * diameter: a = 6 * α_L / d_32
   * </p>
   */
  @Override
  protected double calcGeometricInterfacialAreaPerVolume() {
    // For droplet flow: a = 6 * α_L / d_32 (Sauter mean diameter approach)
    if (averageDropletDiameter > 0 && phaseFraction[1] > 0) {
      return 6.0 * phaseFraction[1] / averageDropletDiameter;
    }
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For droplet flow, uses critical Weber number to calculate maximum stable droplet size: d_max =
   * We_crit * σ / (ρ_G * u_G²)
   * </p>
   */
  @Override
  protected double calcEmpiricalInterfacialAreaPerVolume() {
    // Calculate maximum stable droplet size using critical Weber number
    double surfaceTension = getBulkSystem().getInterphaseProperties().getSurfaceTension(0, 1);
    double gasDensity = getBulkSystem().getPhase(0).getPhysicalProperties().getDensity();
    double gasVelocity = velocity[0];

    // Critical Weber number (typically 12-22 for droplets)
    double weCrit = 12.0;

    if (gasDensity > 0 && gasVelocity > 0 && surfaceTension > 0) {
      double dMax = weCrit * surfaceTension / (gasDensity * gasVelocity * gasVelocity);
      // Use d_32 ≈ 0.6 * d_max for Sauter mean diameter
      double d32 = 0.6 * dMax;
      if (d32 > 0 && phaseFraction[1] > 0) {
        return 6.0 * phaseFraction[1] / d32;
      }
    }
    return calcGeometricInterfacialAreaPerVolume();
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    DropletFlowNode newNode = this.clone();

    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      // newNode.getBulkSystem().getPhases()[0].addMoles(i, -molarMassTransfer[i]);
      // newNode.getBulkSystem().getPhases()[1].addMoles(i, +molarMassTransfer[i]);
    }

    return newNode;
  }

  /**
   * <p>
   * mainOld.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void mainOld(String[] args) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 11.0, 60.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(275.3,
    // ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    PipeData pipe1 = new PipeData(0.203, 0.00025);

    testSystem.addComponent("methane", 250.0, "Nliter/min", 0);
    testSystem.addComponent("water", .00010, "Nliter/min", 0);
    // testSystem.addComponent("n-heptane", 20.0, "Nliter/min", 1);
    testSystem.addComponent("TEG", 90.0, "kg/min", 1);
    testSystem.addComponent("water", 1.0, "kg/min", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();

    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    // testOps.TPflash();
    testSystem.display();
    // testSystem.setTemperature(273.15+20);
    // testSystem.initPhysicalProperties();

    FlowNodeInterface test = new DropletFlowNode(testSystem, pipe1);

    test.setInterphaseModelType(1);
    test.setLengthOfNode(0.1);
    test.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 11.0);

    test.getFluidBoundary().setHeatTransferCalc(true);
    test.getFluidBoundary().setMassTransferCalc(true);
    double length = 0;
    // test.initFlowCalc();

    test.initFlowCalc();
    test.calcFluxes();
    test.getFluidBoundary().display("test");
    double[][] temperatures2 = new double[3][1000];
    int k = 0;
    for (int i = 0; i < 1000; i++) {
      length += test.getLengthOfNode();
      test.initFlowCalc();
      test.calcFluxes();

      if (i > 1 && (i % 1) == 0) {
        k++;
        // test.display("length " + length);
        // test.getBulkSystem().display("length " + length);
        // test.getInterphaseSystem().display("length " + length);
        // test.getFluidBoundary().display("length " + length);
        // test.setLengthOfNode(0.000005 + test.getLengthOfNode() / 2.0);
        temperatures2[0][k] = length;
        temperatures2[1][k] = test.getGeometry().getInnerWallTemperature();
        System.out.println(test.getBulkSystem().getPhase(0).getComponent("water").getx());
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
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.display();
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
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 5.0, 10.0e-3);

    testSystem.addComponent("oxygen", 50.0e-6, "kg/min", 0);
    testSystem.addComponent("oxygen", 8e-6, "kg/min", 1);
    testSystem.addComponent("water", 100.0, "kg/min", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.initPhysicalProperties();

    testSystem.init_x_y();
    testSystem.initBeta();
    testSystem.init(3);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.display();
    // testSystem.setTemperature(273.15+20);
    // testSystem.initPhysicalProperties();

    PipeData pipe1 = new PipeData(0.203, 0.00025);
    FlowNodeInterface test = new DropletFlowNode(testSystem, pipe1);

    test.setInterphaseModelType(1);
    test.setLengthOfNode(0.1);
    test.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 11.0);

    test.getFluidBoundary().setHeatTransferCalc(true);
    test.getFluidBoundary().setMassTransferCalc(true);
    double length = 0;
    // test.initFlowCalc();

    test.initFlowCalc();
    test.calcFluxes();
    test.getFluidBoundary().display("test");
    double[][] temperatures2 = new double[3][1000];
    int k = 0;
    for (int i = 0; i < 1000; i++) {
      length += test.getLengthOfNode();
      test.initFlowCalc();
      test.calcFluxes();

      if (i > 1 && (i % 1) == 0) {
        k++;
        // test.display("length " + length);
        // test.getBulkSystem().display("length " + length);
        // test.getInterphaseSystem().display("length " + length);
        // test.getFluidBoundary().display("length " + length);
        // test.setLengthOfNode(0.000005 + test.getLengthOfNode() / 2.0);
        temperatures2[0][k] = length;
        temperatures2[1][k] = test.getGeometry().getInnerWallTemperature();
        System.out.println(test.getBulkSystem().getPhase(0).getComponent("water").getx());
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
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.display();
  }

  /**
   * <p>
   * Getter for the field <code>averageDropletDiameter</code>.
   * </p>
   *
   * @return a double
   */
  public double getAverageDropletDiameter() {
    return averageDropletDiameter;
  }

  /**
   * <p>
   * Setter for the field <code>averageDropletDiameter</code>.
   * </p>
   *
   * @param averageDropletDiameter a double
   */
  public void setAverageDropletDiameter(double averageDropletDiameter) {
    this.averageDropletDiameter = averageDropletDiameter;
  }
}
