package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.separator.GasScrubberMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternal;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternalWithDrainage;
import neqsim.process.mechanicaldesign.separator.primaryseparation.InletVaneWithMeshpad;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for GasScrubber equipment.
 * 
 * Tests basic functionality and integration of the GasScrubber separator class.
 */
@DisplayName("GasScrubber Tests")
public class GasScrubberTest {

  /**
   * Test 2: Create GasScrubber with inlet stream
   * 
   * Verifies that a GasScrubber can be instantiated with an inlet stream, and that the stream is
   * properly connected to the scrubber.
   */
  @Test
  @DisplayName("Test 2: GasScrubber with Inlet Stream")
  public void testGasScrubberWithInletStream() {
    // Create a thermodynamic system
    SystemInterface system = new SystemSrkEos(273.15 + 25, 60.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule("classic");

    // Create and configure inlet stream
    Stream inletStream = new Stream("inlet", system);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(273.15 + 20, "K");
    inletStream.setPressure(50.0, "bar");
    inletStream.run();

    // Create GasScrubber with inlet stream
    GasScrubber scrubber = new GasScrubber("TestScrubber2", inletStream);

    scrubber.run();

    // scrubber.getFeedStream().getName()

    // Assertions
    assertEquals("TestScrubber2", scrubber.getName(),
        "GasScrubber name should match constructor argument");

    assertNotNull(scrubber.getFeedStream(), "Feed stream should be connected");

    // Verify the feed stream has a thermodynamic system
    assertNotNull(scrubber.getFeedStream().getThermoSystem(),
        "Feed stream should have a thermodynamic system");

    // scrubber.getFeedStream().getThermoSystem().prettyPrint();

    assertEquals("vertical", scrubber.getOrientation(),
        "GasScrubber should have vertical orientation");
  }

  /**
   * Test 3: Mechanical Design Integration
   * 
   * Verifies that the GasScrubber properly returns a GasScrubberMechanicalDesign object through the
   * getMechanicalDesign() method.
   */
  @Test
  @DisplayName("Test 3: Mechanical Design Integration")
  public void testMechanicalDesignIntegration() {
    // Create a GasScrubber
    GasScrubber scrubber = new GasScrubber("TestScrubber3");

    // Get the mechanical design
    GasScrubberMechanicalDesign mechanicalDesign = scrubber.getMechanicalDesign();

    // Assertions
    assertNotNull(mechanicalDesign, "Mechanical design should not be null");

    assertInstanceOf(GasScrubberMechanicalDesign.class, mechanicalDesign,
        "Should return GasScrubberMechanicalDesign instance");

    assertEquals("GasScrubberMechanicalDesign", mechanicalDesign.getClass().getSimpleName(),
        "Mechanical design class name should be GasScrubberMechanicalDesign");
  }

  /**
   * Test 4: Deisting Internals Integration
   * 
   * Verifies that deisting internals (with and without drainage) can be added to the scrubber
   * mechanical design and that their properties and calculations work correctly.
   */
  @Test
  @DisplayName("Test 4: Demisting Internals Integration")
  public void testDemistingInternalsIntegration() {
    // Create a thermodynamic system
    SystemInterface system = new SystemSrkEos(273.15 + 25, 60.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule("classic");

    // Create and configure inlet stream
    Stream inletStream = new Stream("inlet", system);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(273.15 + 20, "K");
    inletStream.setPressure(50.0, "bar");
    inletStream.run();

    // Create GasScrubber with inlet stream
    GasScrubber scrubber = new GasScrubber("TestScrubberWithInternals", inletStream);
    scrubber.run();

    // Get mechanical design
    GasScrubberMechanicalDesign mechanicalDesign = scrubber.getMechanicalDesign();
    assertNotNull(mechanicalDesign, "Mechanical design should not be null");

    // Create deisting internals
    double internalArea1 = 0.5; // m²
    double euNumber1 = 2.5;
    DemistingInternal internal1 = new DemistingInternal(internalArea1, euNumber1);

    double internalArea2 = 0.3; // m²
    double euNumber2 = 2.0;
    double drainageEfficiency = 0.85;
    DemistingInternalWithDrainage internal2 =
        new DemistingInternalWithDrainage(internalArea2, euNumber2, drainageEfficiency);

    // Add internals to mechanical design
    mechanicalDesign.addDemistingInternal(internal1);
    mechanicalDesign.addDemistingInternal(internal2);

    // Verify internals were added
    assertEquals(2, mechanicalDesign.getNumberOfDemistingInternals(),
        "Should have 2 deisting internals");

    // Test gas velocity calculation
    double volumetricFlow = 0.1; // m³/s
    double gasVelocity1 = internal1.calcGasVelocity(volumetricFlow);
    assertEquals(volumetricFlow / internalArea1, gasVelocity1, 1e-10, "Gas velocity should be Q/A");

    // Test pressure drop calculation
    double gasDensity = 2.0; // kg/m³
    double pressureDrop1 = internal1.calcPressureDrop(gasDensity, gasVelocity1);
    double expectedDropEu1 = euNumber1 * gasDensity * gasVelocity1 * gasVelocity1;
    assertEquals(expectedDropEu1, pressureDrop1, 1e-6,
        "Pressure drop should follow Euler equation: Δp = Eu × ρ × v²");

    // Test liquid carry-over calculation
    double carryOver1 = internal1.calcLiquidCarryOver();
    assertTrue(carryOver1 >= 0, "Carry-over should be non-negative");
    System.out.println("Carry-over without drainage: " + carryOver1);

    // Test liquid carry-over with drainage (should be lower)
    double carryOver2 = internal2.calcLiquidCarryOver();
    System.out.println("Carry-over with drainage: " + carryOver2);
    assertTrue(carryOver2 <= carryOver1,
        "Carry-over with drainage should be less than or equal to without drainage");

    // Test total area calculation
    double totalArea = mechanicalDesign.getTotalDemistingArea();
    assertEquals(internalArea1 + internalArea2, totalArea, 1e-10,
        "Total area should be sum of all internal areas");

    // Verify internal properties
    assertEquals(internalArea1, internal1.getArea(), 1e-10, "Area should match");
    assertEquals(euNumber1, internal1.getEuNumber(), 1e-10, "Eu number should match");
    assertEquals(drainageEfficiency, internal2.getDrainageEfficiency(), 1e-10,
        "Drainage efficiency should match");

    System.out.println("Deisting internals test completed successfully");
  }

  /**
   * Test 5: Primary Separation Integration - InletVaneWithMeshpad [PLACEHOLDER - Needs validation]
   * 
   * Verifies that the InletVaneWithMeshpad primary separation device can be configured on the
   * scrubber, that its properties are correctly set, and that carry-over calculations work with the
   * cached inlet stream properties.
   */
  @Test
  @DisplayName("Test 5: Primary Separation Integration - InletVaneWithMeshpad")
  public void testPrimarySeparationIntegration() {
    // Create a thermodynamic system
    SystemInterface system = new SystemSrkEos(273.15 + 25, 60.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule("classic");

    // Create and configure inlet stream
    Stream inletStream = new Stream("inlet", system);
    inletStream.setFlowRate(100.0, "kg/hr");
    inletStream.setTemperature(273.15 + 20, "K");
    inletStream.setPressure(50.0, "bar");
    inletStream.run();

    // Create GasScrubber with inlet stream
    GasScrubber scrubber = new GasScrubber("TestScrubberPrimarySep", inletStream);
    scrubber.run();

    // Test InletVaneWithMeshpad
    System.out.println("--- Testing Inlet Vane with Meshpad ---");
    InletVaneWithMeshpad vaneMeshpad =
        new InletVaneWithMeshpad("InletVaneMeshpad1", 0.1, 0.3, 0.25);
    scrubber.setPrimarySeparation(vaneMeshpad);

    assertNotNull(scrubber.getPrimarySeparation(), "Primary separation should be set");
    assertInstanceOf(InletVaneWithMeshpad.class, scrubber.getPrimarySeparation(),
        "Should be InletVaneWithMeshpad instance");

    assertEquals("InletVaneMeshpad1", vaneMeshpad.getName(), "Name should match");
    assertEquals(0.3, vaneMeshpad.getVaneToMeshpadDistance(), 1e-10,
        "Vane to meshpad distance should match");
    assertEquals(0.25, vaneMeshpad.getFreeDistanceAboveMeshpad(), 1e-10,
        "Free distance above meshpad should match");

    // Test carry-over calculation (using parameter-less method)
    double vaneMeshpadCarryOver = vaneMeshpad.calcLiquidCarryOver();
    System.out.println("Inlet Vane with Meshpad carry-over: " + vaneMeshpadCarryOver);
    assertTrue(vaneMeshpadCarryOver >= 0, "Carry-over should be non-negative");

    // Test inlet nozzle momentum calculation
    double gasDensity = 2.0; // kg/m³
    double inletVelocity = 15.0; // m/s
    double nozzleMomentum = vaneMeshpad.calcInletNozzleMomentum(gasDensity, inletVelocity);
    System.out.println("Inlet nozzle momentum: " + nozzleMomentum + " kg·m/s²");
    assertTrue(nozzleMomentum > 0, "Momentum should be positive");

    System.out.println("\nPrimary separation test completed successfully");
  }
}
