package neqsim.fluidmechanics.flownode.twophasenode.twophasereactorflownode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasereactorflow.InterphasePackedBed;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.reactor.ReactorData;
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TwoPhasePackedBedFlowNode class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class TwoPhasePackedBedFlowNode extends TwoPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TwoPhasePackedBedFlowNode.class);

  /**
   * <p>
   * Constructor for TwoPhasePackedBedFlowNode.
   * </p>
   */
  public TwoPhasePackedBedFlowNode() {
    this.flowNodeType = "packed bed";
  }

  /**
   * <p>
   * Constructor for TwoPhasePackedBedFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public TwoPhasePackedBedFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "packed bed";
    this.interphaseTransportCoefficient = new InterphasePackedBed(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for TwoPhasePackedBedFlowNode.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public TwoPhasePackedBedFlowNode(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "packed bed";
    this.interphaseTransportCoefficient = new InterphasePackedBed(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /** {@inheritDoc} */
  @Override
  public TwoPhasePackedBedFlowNode clone() {
    TwoPhasePackedBedFlowNode clonedSystem = null;
    try {
      clonedSystem = (TwoPhasePackedBedFlowNode) super.clone();
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
    super.init();
  }

  /** {@inheritDoc} */
  @Override
  public void initFlowCalc() {
    phaseFraction[0] = 1.0;
    phaseFraction[1] = 1.0;
    initVelocity();
    this.init();
  }

  /** {@inheritDoc} */
  @Override
  public double calcHydraulicDiameter() {
    return getGeometry().getDiameter();
  }

  /** {@inheritDoc} */
  @Override
  public double calcReynoldNumber() {
    reynoldsNumber[1] =
        getSuperficialVelocity(1) / getGeometry().getPacking().getSurfaceAreaPrVolume()
            * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
            / bulkSystem.getPhases()[1].getPhysicalProperties().getViscosity();
    reynoldsNumber[0] =
        getSuperficialVelocity(0) / getGeometry().getPacking().getSurfaceAreaPrVolume()
            * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
            / bulkSystem.getPhases()[0].getPhysicalProperties().getViscosity();
    System.out.println("rey liq " + reynoldsNumber[1]);
    System.out.println("rey gas " + reynoldsNumber[0]);
    return reynoldsNumber[1];
  }

  /** {@inheritDoc} */
  @Override
  public double calcContactLength() {
    interphaseContactArea =
        pipe.getPacking().getSurfaceAreaPrVolume() * getLengthOfNode() * pipe.getArea();
    return wallContactLength[0];
  }

  /** {@inheritDoc} */
  @Override
  public double calcGasLiquidContactArea() {
    return pipe.getPacking().getSurfaceAreaPrVolume() * getLengthOfNode() * pipe.getArea() * 5.0;
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    TwoPhasePackedBedFlowNode newNode = this.clone();

    for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
      // newNode.getBulkSystem().getPhases()[0].addMoles(i, -molarMassTransfer[i]);
      // newNode.getBulkSystem().getPhases()[1].addMoles(i, +molarMassTransfer[i]);
    }

    return newNode;
  }

  /** {@inheritDoc} */
  @Override
  public void update() {
    for (int componentNumber = 0; componentNumber < getBulkSystem().getPhases()[0]
        .getNumberOfComponents(); componentNumber++) {
      if (componentNumber == 1) {
        double liquidMolarRate =
            getFluidBoundary().getInterphaseMolarFlux(componentNumber) * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();
        double gasMolarRate = -getFluidBoundary().getInterphaseMolarFlux(componentNumber)
            * getInterphaseContactArea(); // getInterphaseContactLength(0)*getGeometry().getNodeLength();
        System.out.println("liquidMolarRate" + liquidMolarRate);
        // getBulkSystem().getPhase(0).addMoles(componentNumber,
        // this.flowDirection[0]*gasMolarRate);
        getBulkSystem().addComponent(componentNumber, this.flowDirection[0] * gasMolarRate, 0);
        getBulkSystem().getPhase(1).addMolesChemReac(componentNumber,
            this.flowDirection[1] * liquidMolarRate);
      }
    }

    getBulkSystem().initBeta();
    getBulkSystem().init_x_y();
    getBulkSystem().init(1);

    if (bulkSystem.isChemicalSystem()) {
      getOperations().chemicalEquilibrium();
    }
    getBulkSystem().init(3);
    System.out.println(
        "reac heat " + getBulkSystem().getChemicalReactionOperations().getDeltaReactionHeat());
    double heatFlux = getInterphaseTransportCoefficient().calcInterphaseHeatTransferCoefficient(0,
        getPrandtlNumber(0), this)
        * (getBulkSystem().getPhase(1).getTemperature()
            - getBulkSystem().getPhase(0).getTemperature())
        * getInterphaseContactArea();
    double liquid_dT = -this.flowDirection[1] * heatFlux / getBulkSystem().getPhase(1).getCp();
    double gas_dT = this.flowDirection[0] * heatFlux / getBulkSystem().getPhase(0).getCp();
    liquid_dT += 0.0;
    // getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(1,
    // this)*(getBulkSystem().getPhase(1).getTemperature()-pipe.getOuterTemperature())
    // * getWallContactLength(1) *
    // getGeometry().getNodeLength()/getBulkSystem().getPhase(1).getCp();
    liquid_dT += 0.0;
    // getInterphaseTransportCoefficient().calcWallHeatTransferCoefficient(0,
    // this)*
    // (getBulkSystem().getPhase(0).getTemperature()-pipe.getOuterTemperature())*
    // getWallContactLength(0) *
    // getGeometry().getNodeLength()/getBulkSystem().getPhase(0).getCp();
    System.out.println("liq dT1 " + liquid_dT);
    liquid_dT += this.flowDirection[1]
        * getBulkSystem().getChemicalReactionOperations().getDeltaReactionHeat()
        / getBulkSystem().getPhase(1).getCp();
    System.out.println("Cp " + getBulkSystem().getPhase(1).getCp());
    System.out.println("liq dT2 " + liquid_dT);
    System.out.println("gas dT " + gas_dT);
    getBulkSystem().getPhase(1)
        .setTemperature(getBulkSystem().getPhase(1).getTemperature() + liquid_dT);
    getBulkSystem().getPhase(0)
        .setTemperature(getBulkSystem().getPhase(0).getTemperature() + gas_dT);

    getBulkSystem().init(3);
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
    SystemInterface testSystem = new SystemFurstElectrolyteEos(313.315, 50.01325);
    // SystemInterface testSystem = new SystemSrkEos(295.3, 100.01325);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    ReactorData pipe1 = new ReactorData(0.25, 0.025);
    pipe1.setPackingType("pallring", "metal", 50);

    testSystem.addComponent("methane", 100000.11152187, "Nlitre/min", 0);
    testSystem.addComponent("CO2", 3000.511152181, "Nlitre/min", 0);
    testSystem.addComponent("water", 100.502204876, "kg/min", 1);
    testSystem.addComponent("MDEA", 100.502204876, "kg/min", 1);
    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
    testOps.TPflash();
    testSystem.addComponent("CO2", 2000.11152181, "Nlitre/min", 0);
    testSystem.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);
    testSystem.init_x_y();
    testSystem.getPhases()[1].setTemperature(313.0);
    testSystem.getPhases()[0].setTemperature(325.0);

    FlowNodeInterface test = new TwoPhasePackedBedFlowNode(testSystem, pipe1);
    test.setLengthOfNode(0.1);
    test.setInterphaseModelType(1);
    test.getFluidBoundary().useFiniteFluxCorrection(false);
    test.getFluidBoundary().useThermodynamicCorrections(false);
    test.setFlowDirection(-1, 0);
    test.initFlowCalc();
    test.initFlowCalc();

    String fileName = "c:/labsim/exp-abs-heat.txt";
    test.write("node 0", fileName, true);
    for (int i = 0; i < 120; i++) {
      test.calcFluxes();
      test.update();
      test.display();
      test.write(("node " + i), fileName, false);
    }
  }
}
