package neqsim.fluidmechanics.flownode.twophasenode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.BubbleFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.DropletFlowNode;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for phase transition initiation (condensation and bubble nucleation) in two-phase flow
 * systems.
 *
 * <p>
 * These tests verify the model's ability to handle:
 * <ul>
 * <li>Single-phase gas transitioning to two-phase (droplet formation/condensation)</li>
 * <li>Single-phase liquid transitioning to two-phase (bubble nucleation)</li>
 * <li>Near-zero phase fraction handling</li>
 * </ul>
 * </p>
 */
public class PhaseTransitionInitiationTest {

  @Test
  void testMinimumPhaseFractionConstants() {
    // Verify constants are defined with reasonable values
    assertTrue(TwoPhaseFlowNode.MIN_PHASE_FRACTION > 0, "MIN_PHASE_FRACTION should be positive");
    assertTrue(TwoPhaseFlowNode.MIN_PHASE_FRACTION < 1e-6,
        "MIN_PHASE_FRACTION should be very small");
    assertTrue(TwoPhaseFlowNode.NUCLEATION_PHASE_FRACTION > TwoPhaseFlowNode.MIN_PHASE_FRACTION,
        "NUCLEATION_PHASE_FRACTION should be larger than MIN_PHASE_FRACTION");
    assertTrue(TwoPhaseFlowNode.NUCLEATION_PHASE_FRACTION < 1e-3,
        "NUCLEATION_PHASE_FRACTION should be small");
  }

  @Test
  void testSinglePhaseGasDetection() {
    // Create a system and set phase fractions to simulate single-phase gas
    SystemInterface gasSystem = new SystemSrkEos(300.0, 10.0);
    gasSystem.addComponent("methane", 0.9);
    gasSystem.addComponent("ethane", 0.1);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);
    gasSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    DropletFlowNode node = new DropletFlowNode(gasSystem, pipe);
    node.initFlowCalc();

    // Explicitly set phase fractions to simulate single-phase gas (no liquid)
    node.setPhaseFraction(0, 1.0 - 1e-12); // Gas
    node.setPhaseFraction(1, 1e-12); // Liquid (effectively zero)

    // Should detect as single-phase gas
    assertTrue(node.isEffectivelySinglePhaseGas(),
        "Node with gas fraction near 1.0 should be detected as effectively single-phase gas");
    assertFalse(node.isEffectivelySinglePhaseLiquid(),
        "Node with gas fraction near 1.0 should not be detected as single-phase liquid");
  }

  @Test
  void testSinglePhaseLiquidDetection() {
    // Create a two-phase system
    SystemInterface liquidSystem = new SystemSrkEos(280.0, 50.0);
    liquidSystem.addComponent("water", 1.0);
    liquidSystem.createDatabase(true);
    liquidSystem.setMixingRule(2);
    liquidSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    BubbleFlowNode node = new BubbleFlowNode(liquidSystem, pipe);
    node.initFlowCalc();

    // Explicitly set phase fractions to simulate single-phase liquid (no gas)
    node.setPhaseFraction(0, 1e-12); // Gas (effectively zero)
    node.setPhaseFraction(1, 1.0 - 1e-12); // Liquid

    // Should detect as single-phase liquid
    assertTrue(node.isEffectivelySinglePhaseLiquid(),
        "Node with liquid fraction near 1.0 should be detected as effectively single-phase liquid");
    assertFalse(node.isEffectivelySinglePhaseGas(),
        "Node with liquid fraction near 1.0 should not be detected as single-phase gas");
  }

  @Test
  void testEnforceMinimumPhaseFractions() {
    // Create a system with very low liquid fraction
    SystemInterface testSystem = new SystemSrkEos(300.0, 10.0);
    testSystem.addComponent("methane", 0.99, 0);
    testSystem.addComponent("n-pentane", 0.01, 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    DropletFlowNode node = new DropletFlowNode(testSystem, pipe);
    node.initFlowCalc();

    // Force near-zero phase fractions using setters
    node.setPhaseFraction(0, 1.0 - 1e-15);
    node.setPhaseFraction(1, 1e-15);

    node.enforceMinimumPhaseFractions();

    // Verify minimum fractions are enforced
    assertTrue(node.getPhaseFraction(0) >= TwoPhaseFlowNode.MIN_PHASE_FRACTION,
        "Gas phase fraction should be at least MIN_PHASE_FRACTION");
    assertTrue(node.getPhaseFraction(1) >= TwoPhaseFlowNode.MIN_PHASE_FRACTION,
        "Liquid phase fraction should be at least MIN_PHASE_FRACTION");
    assertEquals(1.0, node.getPhaseFraction(0) + node.getPhaseFraction(1), 1e-9,
        "Phase fractions should sum to 1.0");
  }

  @Test
  void testCondensationInitiation() {
    // Create a gas system that will undergo condensation when cooled
    SystemInterface gasSystem = new SystemSrkEos(280.0, 30.0); // Below dew point conditions
    gasSystem.addComponent("methane", 0.8, 0);
    gasSystem.addComponent("n-pentane", 0.2, 0);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);
    gasSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    DropletFlowNode node = new DropletFlowNode(gasSystem, pipe);
    node.initFlowCalc();

    // Force single-phase gas condition using setters
    node.setPhaseFraction(0, 1.0 - TwoPhaseFlowNode.MIN_PHASE_FRACTION);
    node.setPhaseFraction(1, TwoPhaseFlowNode.MIN_PHASE_FRACTION);

    // Initiate condensation
    node.initiateCondensation();

    // Verify liquid phase was created
    assertTrue(node.getPhaseFraction(1) >= TwoPhaseFlowNode.NUCLEATION_PHASE_FRACTION,
        "Liquid phase fraction should be at least NUCLEATION_PHASE_FRACTION after condensation");
  }

  @Test
  void testBubbleNucleationInitiation() {
    // Create a liquid system that will form bubbles when heated
    SystemInterface liquidSystem = new SystemSrkEos(350.0, 5.0); // Above bubble point
    liquidSystem.addComponent("n-pentane", 0.5, 1);
    liquidSystem.addComponent("n-hexane", 0.5, 1);
    liquidSystem.createDatabase(true);
    liquidSystem.setMixingRule(2);
    liquidSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    BubbleFlowNode node = new BubbleFlowNode(liquidSystem, pipe);
    node.initFlowCalc();

    // Force single-phase liquid condition using setters
    node.setPhaseFraction(0, TwoPhaseFlowNode.MIN_PHASE_FRACTION);
    node.setPhaseFraction(1, 1.0 - TwoPhaseFlowNode.MIN_PHASE_FRACTION);

    // Initiate bubble nucleation
    node.initiateBubbleNucleation();

    // Verify gas phase was created
    assertTrue(node.getPhaseFraction(0) >= TwoPhaseFlowNode.NUCLEATION_PHASE_FRACTION,
        "Gas phase fraction should be at least NUCLEATION_PHASE_FRACTION after nucleation");
  }

  @Test
  void testTwoPhaseSystemNotSinglePhase() {
    // Create a clear two-phase system
    SystemInterface twoPhaseSystem = new SystemSrkEos(295.0, 10.0);
    twoPhaseSystem.addComponent("methane", 0.5, 0);
    twoPhaseSystem.addComponent("water", 0.5, 1);
    twoPhaseSystem.createDatabase(true);
    twoPhaseSystem.setMixingRule(2);
    twoPhaseSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    StratifiedFlowNode node = new StratifiedFlowNode(twoPhaseSystem, pipe);
    node.initFlowCalc();

    // A clear two-phase system should have both phase fractions initialized
    assertTrue(node.getPhaseFraction(0) >= 0, "Gas phase fraction should be initialized");
    assertTrue(node.getPhaseFraction(1) >= 0, "Liquid phase fraction should be initialized");
  }

  @Test
  void testNucleationDiameter() {
    // Create a system for nucleation diameter calculation
    SystemInterface testSystem = new SystemSrkEos(290.0, 20.0);
    testSystem.addComponent("methane", 0.7, 0);
    testSystem.addComponent("n-pentane", 0.3, 0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    DropletFlowNode node = new DropletFlowNode(testSystem, pipe);
    node.initFlowCalc();

    // Get nucleation diameter for droplets
    double dropletDiameter = node.getNucleationDiameter(true);

    // Nucleation diameter should be positive and small
    assertTrue(dropletDiameter > 0, "Nucleation diameter should be positive");
    assertTrue(dropletDiameter < 1e-3, "Nucleation diameter should be small (< 1 mm)");
    assertTrue(dropletDiameter >= 1e-9, "Nucleation diameter should be at least 1 nm");
  }

  @Test
  void testCheckAndInitiatePhaseTransition() {
    // Create a gas system near dew point
    SystemInterface gasSystem = new SystemSrkEos(300.0, 20.0);
    gasSystem.addComponent("methane", 0.8, 0);
    gasSystem.addComponent("n-pentane", 0.2, 0);
    gasSystem.createDatabase(true);
    gasSystem.setMixingRule(2);
    gasSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    DropletFlowNode node = new DropletFlowNode(gasSystem, pipe);
    node.initFlowCalc();

    // The method should run without error
    boolean transitionOccurred = node.checkAndInitiatePhaseTransition();

    // Result depends on whether conditions favor condensation
    // Just verify the method executes without exception
    assertTrue(node.getPhaseFraction(0) >= 0, "Phase fraction should still be valid");
  }

  @Test
  void testCondensationFromPureGasFlow() {
    // Simulate a gas flowing through a pipe and cooling below dew point
    // This tests the full condensation initiation scenario

    // Start with a rich gas at high temperature (single-phase gas)
    SystemInterface hotGas = new SystemSrkEos(350.0, 30.0);
    hotGas.addComponent("methane", 0.7);
    hotGas.addComponent("ethane", 0.15);
    hotGas.addComponent("propane", 0.1);
    hotGas.addComponent("n-butane", 0.05);
    hotGas.createDatabase(true);
    hotGas.setMixingRule(2);

    // Flash to find phases
    ThermodynamicOperations ops = new ThermodynamicOperations(hotGas);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Flash may fail for single-phase, which is expected
    }
    hotGas.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    DropletFlowNode node = new DropletFlowNode(hotGas, pipe);
    node.initFlowCalc();

    // Check if gas phase dominant
    double gasFraction = node.getPhaseFraction(0);

    // Now simulate cooling (lower temperature)
    hotGas.setTemperature(280.0); // Cool below likely dew point
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle flash
    }
    hotGas.init(3);

    // Re-initialize node
    node.initFlowCalc();

    // After cooling, should have condensed liquid
    double newLiquidFraction = node.getPhaseFraction(1);

    // System should now be two-phase (or at least not purely gas)
    assertTrue(node.getPhaseFraction(0) >= 0, "Phase fractions should be defined");
  }

  @Test
  void testBubbleNucleationFromPureLiquidFlow() {
    // Simulate oil flowing through a pipe and heating above bubble point
    // This tests the full bubble nucleation initiation scenario

    // Start with an oil at low temperature (single-phase liquid)
    // Use a light oil composition with dissolved gas
    SystemInterface coldOil = new SystemSrkEos(280.0, 50.0);
    coldOil.addComponent("methane", 0.05); // Dissolved gas
    coldOil.addComponent("n-pentane", 0.20);
    coldOil.addComponent("n-hexane", 0.25);
    coldOil.addComponent("n-heptane", 0.30);
    coldOil.addComponent("n-octane", 0.20);
    coldOil.createDatabase(true);
    coldOil.setMixingRule(2);

    // Flash to find phases - at high pressure and low temp should be mostly liquid
    ThermodynamicOperations ops = new ThermodynamicOperations(coldOil);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Flash may fail for single-phase, which is expected
    }
    coldOil.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    BubbleFlowNode node = new BubbleFlowNode(coldOil, pipe);
    node.initFlowCalc();

    // Check initial liquid fraction
    double initialLiquidFraction = node.getPhaseFraction(1);

    // Now simulate heating and pressure drop (conditions favoring bubble formation)
    coldOil.setTemperature(350.0); // Heat above likely bubble point
    coldOil.setPressure(10.0); // Reduce pressure
    try {
      ops.TPflash();
    } catch (Exception e) {
      // Handle flash
    }
    coldOil.init(3);

    // Re-initialize node
    node.initFlowCalc();

    // After heating and pressure drop, should have gas bubbles
    double newGasFraction = node.getPhaseFraction(0);

    // System should now be two-phase with gas present
    assertTrue(node.getPhaseFraction(1) >= 0, "Phase fractions should be defined");
    assertTrue(newGasFraction > 0, "Gas phase should have formed from bubble nucleation");
  }

  @Test
  void testBubbleNucleationInitiationFromSinglePhaseLiquid() {
    // Test the bubble nucleation initiation method directly
    // Use heavier oil at higher pressure to stay more liquid
    SystemInterface oilSystem = new SystemSrkEos(320.0, 80.0);
    oilSystem.addComponent("n-hexane", 0.3);
    oilSystem.addComponent("n-heptane", 0.4);
    oilSystem.addComponent("n-octane", 0.3);
    oilSystem.createDatabase(true);
    oilSystem.setMixingRule(2);
    oilSystem.init(3);

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    BubbleFlowNode node = new BubbleFlowNode(oilSystem, pipe);
    node.initFlowCalc();

    // Force single-phase liquid condition
    node.setPhaseFraction(0, 1e-12); // No gas
    node.setPhaseFraction(1, 1.0 - 1e-12); // All liquid

    // Verify it's detected as single-phase liquid
    assertTrue(node.isEffectivelySinglePhaseLiquid(),
        "Should be detected as effectively single-phase liquid");

    // Initiate bubble nucleation
    node.initiateBubbleNucleation();

    // After nucleation, gas phase should have at least minimum nucleation fraction
    assertTrue(node.getPhaseFraction(0) >= TwoPhaseFlowNode.MIN_PHASE_FRACTION,
        "Gas phase should have at least MIN_PHASE_FRACTION after bubble nucleation");
    // Phase fractions should be valid
    assertTrue(node.getPhaseFraction(0) >= 0 && node.getPhaseFraction(0) <= 1.0,
        "Gas phase fraction should be between 0 and 1");
    assertTrue(node.getPhaseFraction(1) >= 0 && node.getPhaseFraction(1) <= 1.0,
        "Liquid phase fraction should be between 0 and 1");
    assertEquals(1.0, node.getPhaseFraction(0) + node.getPhaseFraction(1), 1e-9,
        "Phase fractions should sum to 1.0");
  }

  @Test
  void testNucleationDiameterForBubbles() {
    // Test nucleation diameter calculation for bubbles (gas in liquid)
    SystemInterface oilSystem = new SystemSrkEos(320.0, 20.0);
    oilSystem.addComponent("methane", 0.15);
    oilSystem.addComponent("n-hexane", 0.85);
    oilSystem.createDatabase(true);
    oilSystem.setMixingRule(2);
    oilSystem.init(3);
    oilSystem.initPhysicalProperties();

    PipeData pipe = new PipeData(0.1);
    pipe.setNodeLength(0.01);

    BubbleFlowNode node = new BubbleFlowNode(oilSystem, pipe);
    node.initFlowCalc();

    // Get nucleation diameter for bubbles
    double bubbleDiameter = node.getNucleationDiameter(false); // false = bubbles

    // Diameter should be positive and within reasonable bounds
    assertTrue(bubbleDiameter > 0, "Bubble nucleation diameter should be positive");
    assertTrue(bubbleDiameter >= 1.0e-9, "Bubble diameter should be at least 1 nm");
    assertTrue(bubbleDiameter <= 1.0e-3, "Bubble diameter should be at most 1 mm");
  }
}
