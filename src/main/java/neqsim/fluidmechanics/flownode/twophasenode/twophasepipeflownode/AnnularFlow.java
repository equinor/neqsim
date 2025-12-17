package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow.InterphaseAnnularFlow;
import neqsim.fluidmechanics.flownode.twophasenode.TwoPhaseFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.MaterialLayer;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.fluidmechanics.geometrydefinitions.surrounding.PipeSurroundingEnvironment;
import neqsim.fluidmechanics.geometrydefinitions.surrounding.SurroundingEnvironment;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * AnnularFlow class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class AnnularFlow extends TwoPhaseFlowNode {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AnnularFlow.class);
  // ThermodynamicOperations interphaseOps = new ThermodynamicOperations();
  // double liquidFilmThickness=0;

  /**
   * <p>
   * Constructor for AnnularFlow.
   * </p>
   */
  public AnnularFlow() {
    this.flowNodeType = "annular";
  }

  /**
   * <p>
   * Constructor for AnnularFlow.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public AnnularFlow(SystemInterface system, GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "annular";
    this.interphaseTransportCoefficient = new InterphaseAnnularFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /**
   * <p>
   * Constructor for AnnularFlow.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param interphaseSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param pipe a {@link neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface}
   *        object
   */
  public AnnularFlow(SystemInterface system, SystemInterface interphaseSystem,
      GeometryDefinitionInterface pipe) {
    super(system, pipe);
    this.flowNodeType = "annular";
    this.interphaseTransportCoefficient = new InterphaseAnnularFlow(this);
    this.fluidBoundary =
        new neqsim.fluidmechanics.flownode.fluidboundary.heatmasstransfercalc.nonequilibriumfluidboundary.filmmodelboundary.KrishnaStandartFilmModel(
            this);
  }

  /** {@inheritDoc} */
  @Override
  public AnnularFlow clone() {
    AnnularFlow clonedSystem = null;
    try {
      clonedSystem = (AnnularFlow) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    inclination = 1.0;
    this.calcContactLength();
    super.init();
  }

  /** {@inheritDoc} */
  @Override
  public double calcContactLength() {
    wallContactLength[1] = pi * pipe.getDiameter();
    wallContactLength[0] = 0.0;

    interphaseContactLength[0] = pi * pipe.getDiameter() * Math.sqrt(phaseFraction[0]);
    interphaseContactLength[1] = pi * pipe.getDiameter() * Math.sqrt(phaseFraction[0]);
    return wallContactLength[0];
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNextNode() {
    AnnularFlow newNode = this.clone();

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
    System.out.println("Starter.....");
    String fileName = "c:/labsim/exp-heat.txt";

    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 55, 70.0);
    // SystemInterface testSystem = new SystemSrkEos(273.15 + 85, 50.0);
    // SystemInterface testSystem = new SystemFurstElectrolyteEos(298.15, 20.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    PipeData pipe1 = new PipeData(0.5, 1.0e-6);

    MaterialLayer epoxmat = new MaterialLayer("epoxy", 0.01);
    MaterialLayer steelmat = new MaterialLayer("steel", 0.02);
    MaterialLayer concretemat = new MaterialLayer("concrete", 0.05);
    pipe1.getWall().addMaterialLayer(epoxmat);
    pipe1.getWall().addMaterialLayer(steelmat);
    pipe1.getWall().addMaterialLayer(concretemat);
    System.out.println("pipe heat trans coef " + pipe1.getWall().getHeatTransferCoefficient());
    System.out.println("pipe heat trans coef " + pipe1.getWallHeatTransferCoefficient());

    SurroundingEnvironment surrEnv = new PipeSurroundingEnvironment("sea");
    pipe1.setSurroundingEnvironment(surrEnv);

    double gasflow = 10.0; // MSm^3/day
    double outtemperature = 273.15 + 95;
    pipe1.getSurroundingEnvironment().setTemperature(outtemperature);
    testSystem.addComponent("methane", 0.99847 * gasflow, "MSm^3/day", 0);
    testSystem.addComponent("water", 0.99847 * gasflow * 2000e-6, "MSm^3/day", 0);
    // testSystem.addComponent("water", 0.00151314 * gasflow, "MSm^3/day", 0);
    // testSystem.addComponent("MEG", 0.0000168455 * gasflow, "MSm^3/day", 0);
    // testSystem.init(1);
    // testSystem.addComponent("methane", 10, "MSm^3/day", 0);
    // testSystem.addComponent("water", 5.9, 0);
    // testSystem.addComponent("CO2", 100.333, "Nlitre/min", 0);

    double flow = 1000.0; // kg/min
    double wtpr = 0.99; // wt frac MEG
    testSystem.addComponent("water", 5 * (1.0 - wtpr) * flow, "kg/min", 1);
    testSystem.addComponent("MEG", (wtpr) * flow, "kg/min", 1);
    // testSystem.addComponent("MDEA", wtpr*flow, "kg/min", 1);

    // testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    // testSystem.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);
    // testSystem.setNumericDerivatives(true);
    testSystem.initPhysicalProperties();
    testSystem.getPhase(0).setTemperature(273.15 + 85);
    testSystem.getPhase(1).setTemperature(273.15 + 85);
    // testSystem.init(0);

    AnnularFlow test = new AnnularFlow(testSystem, pipe1);
    test.setInterphaseModelType(1);
    test.setLengthOfNode(0.01);
    test.getFluidBoundary().setHeatTransferCalc(true);
    test.getFluidBoundary().setMassTransferCalc(true);
    test.getFluidBoundary().useFiniteFluxCorrection(true);
    test.getFluidBoundary().useThermodynamicCorrections(true);
    test.initFlowCalc();
    // test.display("testnode 0");
    // test.write("node 0", fileName, true);
    System.out
        .println("rate " + test.getBulkSystem().getPhase(0).getComponent(1).getRate("Nlitre/min"));
    double oldRate = test.getBulkSystem().getPhase(0).getComponent(1).getRate("Nlitre/min");
    for (int i = 0; i < 1; i++) {
      test.initFlowCalc();
      test.init();
      test.calcFluxes();
      // test.getFluidBoundary().display("");
      // test.update();
      // test.write(("node " + i), fileName, false);
      // System.out.println("velocity " + test.getVelocity(1));
      // test.getInterphaseSystem().display("length ");
      test.getBulkSystem().prettyPrint();
      // test.display("testnode " + i);
      // test.getBulkSystem().display("test");
      // test.getFluidBoundary().display("test");
    }
    // test.display("testnode last");

    System.out
        .println("rate " + test.getBulkSystem().getPhase(0).getComponent(1).getRate("Nlitre/min"));
    System.out.println("diff "
        + (test.getBulkSystem().getPhase(0).getComponent(1).getRate("Nlitre/min") - oldRate));
    for (int i = 0; i < 1000; i++) {
      test.calcFluxes();
      test.update();
      if (i % 100 == 0) {
        // test.display("testnode " + i);
        test.getBulkSystem().prettyPrint();
      }
      System.out.println("aqueous phase " + test.getBulkSystem().getPhaseFraction("oil", "wt"));
    }

    // test.getFluidBoundary().display("test");
    // test.display("testnode4");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode5");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode6");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode7");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode8");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode9");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode10");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode11");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode12");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode13");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode14");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode15");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode16");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode17");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode18");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode19");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode20");
    // test.calcFluxes();
    // test.update();
    // test.display("testnod21");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode22");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode23");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode24");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode25");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode26");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode27");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode28");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode29");
    // test.calcFluxes();
    // test.update();
    // test.display("testnode30");
  }
}
