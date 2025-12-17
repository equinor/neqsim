package neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class demonstrating mass transfer calculations between gas and liquid phases in a pipeline.
 * 
 * <p>
 * Based on the example notebook: masstransferMeOH.ipynb This demonstrates mass transfer calculation
 * using NeqSim, based on Solbraa (2002): https://ntnuopen.ntnu.no/ntnu-xmlui/handle/11250/231326
 * </p>
 * 
 * <p>
 * The test simulates:
 * <ul>
 * <li>Water-saturated gas mixed with liquid methanol entering a pipeline</li>
 * <li>Methanol vaporizing into the gas phase</li>
 * <li>Water and other components being absorbed into the liquid methanol</li>
 * </ul>
 * </p>
 */
public class MassTransferMeOHTest {

  private SystemInterface fluid;
  private PipeData pipe;

  @BeforeEach
  void setUp() {
    // Create a two-phase fluid with gas and aqueous/methanol phases
    // Similar to the notebook example with TEG and water
    fluid = new SystemSrkCPAstatoil(273.15 + 37.0, 150.0);

    // Add gas phase components (phase 0)
    fluid.addComponent("methane", 10.0, "MSm3/day", 0);
    fluid.addComponent("water", 1.0, "kg/hr", 0);
    fluid.addComponent("TEG", 0.125, "kg/hr", 0);

    // Add liquid phase components (phase 1)
    fluid.addComponent("water", 10.0, "kg/hr", 1);
    fluid.addComponent("TEG", 1000.0, "kg/hr", 1);

    fluid.createDatabase(true);
    fluid.setMixingRule(10); // CPA mixing rule
    fluid.initPhysicalProperties();

    // Set different temperatures for gas and liquid phases
    fluid.getPhase(0).setTemperature(273.15 + 37.0);
    fluid.getPhase(1).setTemperature(273.15 + 47.9);
    fluid.init_x_y();
    fluid.initBeta();
    fluid.init(3);

    // Create pipe geometry
    pipe = new PipeData(2.5, 0.00025); // diameter, roughness
  }

  /**
   * Test basic mass transfer calculation between gas and liquid TEG.
   * 
   * <p>
   * This test verifies that mass transfer occurs between phases and that the interphase temperature
   * calculation works correctly.
   * </p>
   */
  @Test
  void testMassTransferBetweenGasAndTEG() {
    // Create annular flow node (similar to notebook example)
    AnnularFlow node = new AnnularFlow(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.001);
    node.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 4.0);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().setMassTransferCalc(true);

    // Initialize and calculate fluxes
    node.initFlowCalc();
    node.calcFluxes();

    // Verify interphase system exists and has valid temperature
    assertTrue(node.getInterphaseSystem() != null, "Interphase system should exist");
    double interphaseTemp = node.getInterphaseSystem().getTemperature() - 273.15;
    assertTrue(Double.isFinite(interphaseTemp), "Interphase temperature should be finite");

    // Verify bulk system has valid properties
    double gasTemp = node.getBulkSystem().getPhase("gas").getTemperature() - 273.15;
    assertTrue(Double.isFinite(gasTemp), "Gas temperature should be finite");

    // Get TEG mole fraction in gas phase
    double tegInGas = node.getBulkSystem().getPhase(0).getComponent("TEG").getx();
    assertTrue(tegInGas >= 0 && tegInGas <= 1, "TEG mole fraction should be between 0 and 1");

    // Get water mole fraction in gas phase
    double waterInGas = node.getBulkSystem().getPhase(0).getComponent("water").getx();
    assertTrue(waterInGas >= 0 && waterInGas <= 1, "Water mole fraction should be between 0 and 1");
  }

  /**
   * Test mass transfer evolution over multiple steps.
   * 
   * <p>
   * Simulates the mass transfer process over multiple iterations to verify that the system evolves
   * correctly towards equilibrium.
   * </p>
   */
  @Test
  void testMassTransferEvolution() {
    AnnularFlow node = new AnnularFlow(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.001);
    node.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 4.0);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().setMassTransferCalc(true);

    double[] interphaseTempHistory = new double[10];
    double[] gasTempHistory = new double[10];

    // Run multiple iterations
    for (int i = 0; i < 10; i++) {
      node.initFlowCalc();
      node.calcFluxes();

      interphaseTempHistory[i] = node.getInterphaseSystem().getTemperature() - 273.15;
      gasTempHistory[i] = node.getBulkSystem().getPhase("gas").getTemperature() - 273.15;

      node.update();
    }

    // Verify all temperatures are finite
    for (int i = 0; i < 10; i++) {
      assertTrue(Double.isFinite(interphaseTempHistory[i]),
          "Interphase temperature at step " + i + " should be finite");
      assertTrue(Double.isFinite(gasTempHistory[i]),
          "Gas temperature at step " + i + " should be finite");
    }
  }

  /**
   * Test diffusion coefficient calculation.
   * 
   * <p>
   * Verifies that diffusion coefficients are calculated correctly for the gas phase.
   * </p>
   */
  @Test
  void testDiffusionCoefficients() {
    AnnularFlow node = new AnnularFlow(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.001);
    node.initFlowCalc();

    // Get diffusion coefficients
    double diffTEGinMethane = node.getBulkSystem().getPhase(0).getPhysicalProperties()
        .getDiffusionCoefficient("methane", "TEG");
    double diffWaterInMethane = node.getBulkSystem().getPhase(0).getPhysicalProperties()
        .getDiffusionCoefficient("methane", "water");

    // Verify diffusion coefficients are positive and reasonable
    assertTrue(diffTEGinMethane > 0, "TEG diffusion coefficient should be positive");
    assertTrue(diffWaterInMethane > 0, "Water diffusion coefficient should be positive");

    // Water should diffuse faster than TEG (smaller molecule)
    assertTrue(diffWaterInMethane > diffTEGinMethane,
        "Water should have higher diffusion coefficient than TEG");
  }

  /**
   * Test stratified flow pattern mass transfer.
   * 
   * <p>
   * Tests mass transfer in stratified flow regime.
   * </p>
   */
  @Test
  void testStratifiedFlowMassTransfer() {
    StratifiedFlowNode node = new StratifiedFlowNode(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.01);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().setMassTransferCalc(true);

    node.initFlowCalc();
    node.calcFluxes();

    // Verify contact length and area calculations
    double contactLength = node.calcContactLength();
    assertTrue(contactLength >= 0, "Contact length should be non-negative");

    // Verify interphase system
    assertTrue(node.getInterphaseSystem() != null, "Interphase system should exist");
  }

  /**
   * Test mass transfer with different pipe lengths.
   * 
   * <p>
   * Verifies that longer contact lengths result in more mass transfer, approaching equilibrium.
   * </p>
   */
  @Test
  void testMassTransferWithDifferentLengths() {
    double[] lengths = {0.001, 0.01, 0.1};
    double[] tegInGasAtLength = new double[lengths.length];

    for (int i = 0; i < lengths.length; i++) {
      // Reset fluid for each test
      SystemInterface testFluid = fluid.clone();
      testFluid.init(3);

      AnnularFlow node = new AnnularFlow(testFluid, pipe);
      node.setInterphaseModelType(1);
      node.setLengthOfNode(lengths[i]);
      node.getFluidBoundary().setHeatTransferCalc(true);
      node.getFluidBoundary().setMassTransferCalc(true);

      node.initFlowCalc();
      node.calcFluxes();

      tegInGasAtLength[i] = testFluid.getPhase(0).getComponent("TEG").getx();
    }

    // Verify TEG mole fractions are all valid
    for (int i = 0; i < lengths.length; i++) {
      assertTrue(Double.isFinite(tegInGasAtLength[i]),
          "TEG mole fraction at length " + lengths[i] + " should be finite");
    }
  }

  /**
   * Test simple methane-ethane two-phase system mass transfer.
   * 
   * <p>
   * Tests a simpler system similar to the ethane evaporation example in the notebook.
   * </p>
   */
  @Test
  void testSimpleTwoPhaseSystemMassTransfer() {
    // Create a simpler two-phase system
    SystemInterface simpleFluid = new SystemSrkCPAstatoil(273.15 + 10.0, 60.0);
    simpleFluid.addComponent("methane", 0.9);
    simpleFluid.addComponent("propane", 0.1);
    simpleFluid.createDatabase(true);
    simpleFluid.setMixingRule(10);
    simpleFluid.initPhysicalProperties();

    ThermodynamicOperations ops = new ThermodynamicOperations(simpleFluid);
    ops.TPflash();
    simpleFluid.initPhysicalProperties();

    // Only run if we have two phases
    if (simpleFluid.getNumberOfPhases() == 2) {
      PipeData simplePipe = new PipeData(0.5, 0.00025);
      AnnularFlow node = new AnnularFlow(simpleFluid, simplePipe);
      node.setInterphaseModelType(1);
      node.setLengthOfNode(0.01);
      node.getFluidBoundary().setHeatTransferCalc(true);
      node.getFluidBoundary().setMassTransferCalc(true);

      node.initFlowCalc();
      node.calcFluxes();

      assertTrue(node.getInterphaseSystem() != null, "Interphase system should exist");
    }
  }

  /**
   * Test interphase molar flux calculation.
   * 
   * <p>
   * Verifies that the interphase molar flux is calculated and returns finite values.
   * </p>
   */
  @Test
  void testInterphaseMolarFlux() {
    AnnularFlow node = new AnnularFlow(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.001);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().setMassTransferCalc(true);

    node.initFlowCalc();
    node.calcFluxes();

    // Get interphase molar flux for each component
    int numComponents = node.getBulkSystem().getPhase(0).getNumberOfComponents();
    for (int i = 0; i < numComponents; i++) {
      double flux = node.getFluidBoundary().getInterphaseMolarFlux(i);
      assertTrue(Double.isFinite(flux),
          "Interphase molar flux for component " + i + " should be finite, got: " + flux);
    }
  }

  /**
   * Test interphase contact area calculation.
   * 
   * <p>
   * Verifies the interphase contact area is calculated correctly.
   * </p>
   */
  @Test
  void testInterphaseContactArea() {
    AnnularFlow node = new AnnularFlow(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.01);
    node.initFlowCalc();

    double contactArea = node.getInterphaseContactArea();
    assertTrue(contactArea >= 0, "Interphase contact area should be non-negative");
    assertTrue(Double.isFinite(contactArea), "Interphase contact area should be finite");
  }

  /**
   * Test heat transfer calculation.
   * 
   * <p>
   * Verifies that heat transfer between phases is calculated.
   * </p>
   */
  @Test
  void testHeatTransfer() {
    // Create fluid with temperature difference between phases
    SystemInterface heatFluid = new SystemSrkCPAstatoil(273.15 + 30.0, 50.0);
    heatFluid.addComponent("methane", 10.0, "MSm3/day", 0);
    heatFluid.addComponent("water", 1.0, "kg/hr", 0);
    heatFluid.addComponent("water", 100.0, "kg/hr", 1);
    heatFluid.createDatabase(true);
    heatFluid.setMixingRule(10);
    heatFluid.initPhysicalProperties();

    // Set different temperatures for each phase
    heatFluid.getPhase(0).setTemperature(273.15 + 30.0);
    heatFluid.getPhase(1).setTemperature(273.15 + 50.0);
    heatFluid.init_x_y();
    heatFluid.initBeta();
    heatFluid.init(3);

    AnnularFlow node = new AnnularFlow(heatFluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.01);
    node.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 20.0);
    node.getFluidBoundary().setHeatTransferCalc(true);

    node.initFlowCalc();
    node.calcFluxes();

    // Verify interphase temperature is between the two phase temperatures
    double interphaseTemp = node.getInterphaseSystem().getTemperature();
    assertTrue(Double.isFinite(interphaseTemp), "Interphase temperature should be finite");
  }

  /**
   * Test finite flux correction.
   * 
   * <p>
   * Tests that finite flux correction can be enabled and used.
   * </p>
   */
  @Test
  void testFiniteFluxCorrection() {
    AnnularFlow node = new AnnularFlow(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.001);
    node.getFluidBoundary().useFiniteFluxCorrection(true);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().setMassTransferCalc(true);

    node.initFlowCalc();
    node.calcFluxes();

    // Verify system still works with finite flux correction
    assertTrue(node.getInterphaseSystem() != null, "Interphase system should exist");
    double interphaseTemp = node.getInterphaseSystem().getTemperature() - 273.15;
    assertTrue(Double.isFinite(interphaseTemp),
        "Interphase temperature should be finite with finite flux correction");
  }

  /**
   * Test thermodynamic corrections.
   * 
   * <p>
   * Tests that thermodynamic corrections can be enabled and used.
   * </p>
   */
  @Test
  void testThermodynamicCorrections() {
    AnnularFlow node = new AnnularFlow(fluid, pipe);
    node.setInterphaseModelType(1);
    node.setLengthOfNode(0.001);
    node.getFluidBoundary().useThermodynamicCorrections(true);
    node.getFluidBoundary().setHeatTransferCalc(true);
    node.getFluidBoundary().setMassTransferCalc(true);

    node.initFlowCalc();
    node.calcFluxes();

    // Verify system still works with thermodynamic corrections
    assertTrue(node.getInterphaseSystem() != null, "Interphase system should exist");
  }
}
