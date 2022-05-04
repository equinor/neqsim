package neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.pipeFlowSystem;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.thermo.system.SystemInterface;


public class PipeFlowSystemTest extends neqsim.NeqSimTest {
  FlowSystemInterface pipe;

  @BeforeEach
  void setUp() {
    pipe = new PipeFlowSystem();

    SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(285.15, 200.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);
    testSystem.createDatabase(true);
    testSystem.init(0);
    testSystem.init(3);
    testSystem.initPhysicalProperties();
    testSystem.setTotalFlowRate(60.0, "MSm3/day");

    double[] height = {0, 0, 0}; // , 0, 0, 0, 0, 0, 0, 0, 0};
    double[] diameter = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
    double[] roughness =
        {1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5};
    double[] outHeatCoef = {5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0};
    double[] wallHeacCoef = {15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0};

    double[] length =
        {0, 10000, 50000, 150000, 200000, 400000, 500000, 600000, 650000, 700000, 750000};
    double[] outerTemperature =
        {278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0};

    neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface[] pipeGeometry =
        new neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData[height.length];

    for (int i = 0; i < height.length; i++) {
      pipeGeometry[i] = new neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData();
      pipeGeometry[i].setDiameter(diameter[i]);
      pipeGeometry[i].setInnerSurfaceRoughness(roughness[i]);
    }
    pipe.setInletThermoSystem(testSystem);
    pipe.setNumberOfLegs(height.length - 1);
    pipe.setNumberOfNodesInLeg(20);
    pipe.setEquipmentGeometry(pipeGeometry);
    pipe.setLegHeights(height);
    pipe.setLegPositions(Arrays.copyOfRange(length, 0, height.length));
    pipe.setLegOuterTemperatures(Arrays.copyOfRange(outerTemperature, 0, height.length));
    pipe.setLegWallHeatTransferCoefficients(Arrays.copyOfRange(wallHeacCoef, 0, height.length));
    pipe.setLegOuterHeatTransferCoefficients(Arrays.copyOfRange(outHeatCoef, 0, height.length));
  }

  @Test
  void testCreateSystem() {
    pipe.createSystem();
  }

  @Test
  void testInit() {
    testCreateSystem();
    pipe.init();
  }

  @Test
  void testSolveSteadyStateConstantFrictionFactor() {
    testInit();
    for (int i = 0; i < pipe.getFlowNodes().length; i++) {
      pipe.getNode(i).setWallFrictionFactor(0, 0.02);
    }
    pipe.solveSteadyState(10);
    System.out.println("pressure out "
        + pipe.getNode(pipe.getFlowNodes().length - 1).getBulkSystem().getPressure() + " bara");
  }

  @Test
  void testSolveSteadyState() {
    testInit();
    pipe.solveSteadyState(10);
    // pipe.print();
  }

  @Test
  void testSolveTransient() {
    testInit();
    // transient solver
    double[] times = {0, 10000, 20000};// , 30000, 40000, 50000};//, 60000, 70000, 80000,
    // 90000};
    pipe.getTimeSeries().setTimes(times);

    SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(285.15, 200.0);
    testSystem.addComponent("methane", 0.9);
    testSystem.addComponent("ethane", 0.1);

    SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos(315.15, 200.0);
    testSystem2.addComponent("methane", 26000.0);
    testSystem2.addComponent("ethane", 1.10);
    testSystem2.init(0);
    testSystem2.init(3);
    testSystem2.initPhysicalProperties();

    SystemInterface testSystem3 = new neqsim.thermo.system.SystemSrkEos(285.15, 200.0);
    testSystem3.addComponent("methane", 29000.0);
    testSystem3.addComponent("ethane", 1221.10);
    testSystem3.init(0);

    SystemInterface[] systems = {testSystem, testSystem2, testSystem2};// , testSystem2,
    // testSystem2,
    // testSystem2};//,testSystem2,testSystem2,testSystem2,testSystem2,testSystem2};
    pipe.getTimeSeries().setInletThermoSystems(systems);
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(10);
    // double[] outletFlowRates = {0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01,
    // 0.01, 0.01, 0.01};
    // pipe.getTimeSeries().setOutletMolarFlowRate(outletFlowRates);

    // pipe.solveTransient(20);
    // pipe.getDisplay().displayResult("composition");
    // pipe.getDisplay().displayResult("pressure");
    // pipe.getDisplay().displayResult("composition");
    // pipe.getDisplay().createNetCdfFile("c:/temp5.nc");
    // pipe.getDisplay(1).displayResult();
  }
}
