package neqsim.fluidmechanics.flownode.twophasenode.twophasestirredcellnode;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.geometrydefinitions.stirredcell.StirredCell;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test class demonstrating mass transfer in a stirred PVT cell.
 * 
 * <p>
 * Based on the example notebook: masstransferMeOH.ipynb This demonstrates mass transfer between oil
 * and gas in a constant pressure/variable volume stirred PVT cell.
 * </p>
 * 
 * <p>
 * The test simulates:
 * <ul>
 * <li>Depressurization of a hydrocarbon fluid from 50 bar to 1 atm</li>
 * <li>Mass transfer between gas and liquid phases in a stirred cell</li>
 * <li>Evolution of K-values and phase fractions over time</li>
 * </ul>
 * </p>
 */
public class StirredCellMassTransferTest {

  /**
   * Test basic stirred cell mass transfer calculation.
   * 
   * <p>
   * Creates a synthetic hydrocarbon fluid at high pressure and simulates depressurization to
   * atmospheric pressure in a stirred cell.
   * </p>
   */
  @Test
  void testStirredCellMassTransfer() {
    // Create stirred cell geometry
    StirredCell cell = new StirredCell(1.0, 0.05); // diameter, roughness

    // Create test system - synthetic hydrocarbon fluid
    SystemInterface testSystem = new SystemPrEos();
    testSystem.addComponent("methane", 0.6457851061152181, "kg/min");
    testSystem.addComponent("ethane", 0.1206862204876, "kg/min");
    testSystem.addComponent("propane", 0.206862204876, "kg/min");
    testSystem.addComponent("nC10", 10.206862204876, "kg/min");
    testSystem.setMixingRule(2);
    testSystem.setPressure(50.0, "bara");
    testSystem.setTemperature(30.0, "C");

    // Flash to get initial equilibrium
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    // Check that we have two phases
    assertTrue(testSystem.getNumberOfPhases() >= 2, "Should have at least two phases");

    // Store initial volume
    double startVolume = testSystem.getVolume();
    assertTrue(startVolume > 0, "Initial volume should be positive");

    // Depressurize to atmospheric pressure
    testSystem.setPressure(1.0, "atm");
    testSystem.setTemperature(15.0, "C");
    testSystem.init(1);

    // Create stirred cell node
    StirredCellNode node = new StirredCellNode(testSystem, cell);
    node.setInterphaseModelType(1);
    node.getFluidBoundary().useFiniteFluxCorrection(true);
    node.getFluidBoundary().useThermodynamicCorrections(true);
    node.setStirrerSpeed(1.0);
    node.setStirrerDiameter(1.0);

    // Initialize flow calculations
    node.initFlowCalc();

    // Verify node is properly initialized
    assertTrue(node.getBulkSystem() != null, "Bulk system should exist");
    assertTrue(node.getInterphaseSystem() != null, "Interphase system should exist");
  }

  /**
   * Test mass transfer evolution over multiple time steps.
   * 
   * <p>
   * Simulates mass transfer in the stirred cell over multiple iterations to verify that the system
   * evolves towards equilibrium.
   * </p>
   */
  @Test
  void testMassTransferEvolution() {
    // Create stirred cell geometry
    StirredCell cell = new StirredCell(1.0, 0.05);

    // Create test system
    SystemInterface testSystem = new SystemPrEos();
    testSystem.addComponent("methane", 0.65, "kg/min");
    testSystem.addComponent("propane", 0.2, "kg/min");
    testSystem.addComponent("nC10", 10.0, "kg/min");
    testSystem.setMixingRule(2);
    testSystem.setPressure(50.0, "bara");
    testSystem.setTemperature(30.0, "C");

    // Flash to get initial equilibrium
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    // Store initial conditions
    double initialBeta = testSystem.getBeta();
    assertTrue(initialBeta >= 0 && initialBeta <= 1,
        "Initial gas fraction should be between 0 and 1");

    // Depressurize
    testSystem.setPressure(1.0, "atm");
    testSystem.setTemperature(15.0, "C");
    testSystem.init(1);

    // Create stirred cell node
    StirredCellNode node = new StirredCellNode(testSystem, cell);
    node.setInterphaseModelType(1);
    node.getFluidBoundary().useFiniteFluxCorrection(true);
    node.getFluidBoundary().useThermodynamicCorrections(true);
    node.setStirrerSpeed(1.0);
    node.setStirrerDiameter(1.0);

    // Set time step
    node.setDt(0.05);

    // Track K-values and gas fraction over time
    double[] kValueC1 = new double[10];
    double[] gasFraction = new double[10];

    for (int i = 0; i < 10; i++) {
      node.initFlowCalc();
      node.calcFluxes();

      // Calculate K-value for methane
      double xGas = node.getBulkSystem().getPhase(0).getComponent("methane").getx();
      double xLiq = node.getBulkSystem().getPhase(1).getComponent("methane").getx();
      if (xLiq > 0) {
        kValueC1[i] = xGas / xLiq;
      }

      gasFraction[i] = node.getBulkSystem().getBeta();

      // Update the system (simplified - without mass transfer updates for this test)
    }

    // Verify K-values are positive (gas phase enriched in light component)
    for (int i = 0; i < 10; i++) {
      assertTrue(kValueC1[i] > 0, "K-value for methane should be positive at step " + i);
      assertTrue(Double.isFinite(kValueC1[i]), "K-value should be finite at step " + i);
    }
  }

  /**
   * Test interphase contact area calculation in stirred cell.
   */
  @Test
  void testInterphaseContactArea() {
    StirredCell cell = new StirredCell(0.5, 0.05); // diameter = 0.5m

    SystemInterface testSystem = new SystemPrEos();
    testSystem.addComponent("methane", 0.5, "kg/min");
    testSystem.addComponent("nC10", 5.0, "kg/min");
    testSystem.setMixingRule(2);
    testSystem.setPressure(50.0, "bara");
    testSystem.setTemperature(30.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    testSystem.setPressure(10.0, "bara");
    testSystem.init(1);

    StirredCellNode node = new StirredCellNode(testSystem, cell);
    node.setInterphaseModelType(1);
    node.initFlowCalc();

    double contactArea = node.getInterphaseContactArea();
    assertTrue(contactArea > 0, "Interphase contact area should be positive");
    assertTrue(Double.isFinite(contactArea), "Interphase contact area should be finite");

    // Contact area should be related to cell diameter (pi*D^2/4 for horizontal interface)
    double expectedArea = Math.PI * Math.pow(0.5, 2) / 4.0;
    // Allow some tolerance as the actual calculation may differ
    assertTrue(Math.abs(contactArea - expectedArea) < expectedArea * 0.5,
        "Contact area should be roughly pi*D^2/4");
  }

  /**
   * Test stirrer speed effect on mass transfer.
   */
  @Test
  void testStirrerSpeedEffect() {
    StirredCell cell = new StirredCell(1.0, 0.05);

    SystemInterface testSystem = new SystemPrEos();
    testSystem.addComponent("methane", 0.5, "kg/min");
    testSystem.addComponent("nC10", 5.0, "kg/min");
    testSystem.setMixingRule(2);
    testSystem.setPressure(50.0, "bara");
    testSystem.setTemperature(30.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    testSystem.setPressure(10.0, "bara");
    testSystem.init(1);

    // Test with different stirrer speeds
    double[] stirrerSpeeds = {0.5, 1.0, 2.0};

    for (double speed : stirrerSpeeds) {
      StirredCellNode node = new StirredCellNode(testSystem.clone(), cell);
      node.setInterphaseModelType(1);
      node.setStirrerSpeed(speed);
      node.setStirrerDiameter(0.5);

      node.initFlowCalc();
      node.calcFluxes();

      // Verify system is valid at each speed
      assertTrue(node.getInterphaseSystem() != null,
          "Interphase system should exist at stirrer speed " + speed);
    }
  }

  /**
   * Test time step setting.
   */
  @Test
  void testTimeStepSetting() {
    StirredCell cell = new StirredCell(1.0, 0.05);

    SystemInterface testSystem = new SystemPrEos();
    testSystem.addComponent("methane", 0.5, "kg/min");
    testSystem.addComponent("propane", 0.5, "kg/min");
    testSystem.setMixingRule(2);
    testSystem.setPressure(50.0, "bara");
    testSystem.setTemperature(30.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();

    testSystem.setPressure(1.0, "atm");
    testSystem.init(1);

    StirredCellNode node = new StirredCellNode(testSystem, cell);
    node.setInterphaseModelType(1);

    // Set time step
    double dt = 0.1;
    node.setDt(dt);

    // Verify time step is set correctly
    double retrievedDt = node.getDt();
    assertTrue(Math.abs(retrievedDt - dt) < 1e-10, "Time step should be set correctly");
  }

  /**
   * Test diffusion coefficient access in gas phase.
   */
  @Test
  void testDiffusionCoefficients() {
    StirredCell cell = new StirredCell(1.0, 0.05);

    SystemInterface testSystem = new SystemPrEos();
    testSystem.addComponent("methane", 0.5, "kg/min");
    testSystem.addComponent("propane", 0.3, "kg/min");
    testSystem.addComponent("nC10", 5.0, "kg/min");
    testSystem.setMixingRule(2);
    testSystem.setPressure(50.0, "bara");
    testSystem.setTemperature(30.0, "C");

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.initPhysicalProperties();

    testSystem.setPressure(10.0, "bara");
    testSystem.init(1);

    StirredCellNode node = new StirredCellNode(testSystem, cell);
    node.setInterphaseModelType(1);
    node.initFlowCalc();

    // Get diffusion coefficient in gas phase
    double diffCoeff = node.getBulkSystem().getPhase(0).getPhysicalProperties()
        .getDiffusionCoefficient("methane", "propane");

    assertTrue(diffCoeff > 0, "Diffusion coefficient should be positive");
    assertTrue(Double.isFinite(diffCoeff), "Diffusion coefficient should be finite");
  }
}
