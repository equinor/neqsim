package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.RiserConfiguration.RiserType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for RiserConfiguration factory class.
 *
 * @author ASMF
 */
class RiserConfigurationTest {

  private Stream inletStream;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(323.15, 100.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");

    inletStream = new Stream("inlet", fluid);
    inletStream.setFlowRate(50000.0, "kg/hr");
    inletStream.run();
  }

  @Test
  void testCreateSCR() {
    PipeBeggsAndBrills riser = RiserConfiguration.createSCR("Production Riser", inletStream, 500.0);

    assertNotNull(riser);
    assertEquals("Production Riser", riser.getName());

    riser.run();
    assertNotNull(riser.getOutletStream());

    // After run, getLength() returns cumulative length
    assertTrue(riser.getLength() > 0);
  }

  @Test
  void testCreateTTR() {
    PipeBeggsAndBrills riser = RiserConfiguration.createTTR("TTR", inletStream, 300.0);

    assertNotNull(riser);

    riser.run();
    assertNotNull(riser.getOutletStream());

    // TTR should have run successfully
    assertTrue(riser.getLength() > 0);
  }

  @Test
  void testCreateLazyWave() {
    PipeBeggsAndBrills riser =
        RiserConfiguration.createLazyWave("Lazy Wave", inletStream, 800.0, 300.0);

    assertNotNull(riser);

    riser.run();
    assertNotNull(riser.getOutletStream());
    assertTrue(riser.getLength() > 0);
  }

  @Test
  void testAllRiserTypes() {
    for (RiserType type : RiserType.values()) {
      PipeBeggsAndBrills riser =
          RiserConfiguration.createRiser(type, type.name() + " Riser", inletStream, 400.0);

      assertNotNull(riser, "Riser should be created for type: " + type);

      riser.run();
      assertTrue(riser.getLength() > 0, "Riser length should be positive for type: " + type);
      assertNotNull(riser.getOutletStream(), "Outlet stream should exist for type: " + type);
    }
  }

  @Test
  void testBuilderPattern() {
    RiserConfiguration config = new RiserConfiguration(RiserType.LAZY_WAVE).setWaterDepth(1000.0)
        .setBuoyancyModuleDepth(400.0).setBuoyancyModuleLength(150.0).setTopAngle(15.0)
        .setInnerDiameter(0.254).setNumberOfSections(80).setAmbientTemperature(5.0);

    assertEquals(RiserType.LAZY_WAVE, config.getRiserType());
    assertEquals(1000.0, config.getWaterDepth(), 0.01);
    assertEquals(400.0, config.getBuoyancyModuleDepth(), 0.01);
    assertEquals(150.0, config.getBuoyancyModuleLength(), 0.01);
    assertEquals(15.0, config.getTopAngle(), 0.01);
    assertEquals(0.254, config.getInnerDiameter(), 0.001);
    assertEquals(80, config.getNumberOfSections());
    assertEquals(5.0, config.getAmbientTemperature(), 0.01);

    PipeBeggsAndBrills riser = config.create("Deepwater Riser", inletStream);
    assertNotNull(riser);
    assertEquals(0.254, riser.getDiameter(), 0.001);
  }

  @Test
  void testDiameterUnits() {
    RiserConfiguration config = new RiserConfiguration();

    // Test mm
    config.setInnerDiameter(254, "mm");
    assertEquals(0.254, config.getInnerDiameter(), 0.001);

    // Test inch
    config.setInnerDiameter(10, "inch");
    assertEquals(0.254, config.getInnerDiameter(), 0.001);

    // Test meters (default)
    config.setInnerDiameter(0.3, "m");
    assertEquals(0.3, config.getInnerDiameter(), 0.001);
  }

  @Test
  void testCalculateRiserLength() {
    RiserConfiguration config = new RiserConfiguration();
    config.setWaterDepth(500.0);

    config.setRiserType(RiserType.STEEL_CATENARY_RISER);
    assertEquals(575.0, config.calculateRiserLength(), 1.0);

    config.setRiserType(RiserType.TOP_TENSIONED_RISER);
    assertEquals(500.0, config.calculateRiserLength(), 0.1);

    config.setRiserType(RiserType.LAZY_WAVE);
    assertEquals(900.0, config.calculateRiserLength(), 1.0);

    config.setRiserType(RiserType.VERTICAL);
    assertEquals(500.0, config.calculateRiserLength(), 0.1);
  }

  @Test
  void testElevationProfile() {
    PipeBeggsAndBrills riser = RiserConfiguration.createSCR("SCR with profile", inletStream, 500.0);

    // Run to populate profiles
    riser.run();

    // SCR should have elevation profile set
    // The riser starts deep (negative) and ends at surface (zero or near zero)
    double outletPressure = riser.getOutletStream().getPressure("bara");
    double inletPressure = inletStream.getPressure("bara");

    // Pressure should decrease as we go up (hydrostatic head reduction)
    // For gas-dominated flow, pressure drop should be positive overall
    assertTrue(outletPressure > 0, "Outlet pressure should be positive");
  }

  @Test
  void testDescription() {
    RiserConfiguration config = new RiserConfiguration(RiserType.STEEL_CATENARY_RISER)
        .setWaterDepth(500.0).setTopAngle(12.0).setDepartureAngle(18.0).setInnerDiameter(0.254);

    String description = config.getDescription();

    assertTrue(description.contains("STEEL_CATENARY_RISER"));
    assertTrue(description.contains("500"));
    assertTrue(description.contains("12"));
    assertTrue(description.contains("254"));
  }

  @Test
  void testDeepwaterSCR() {
    // Test a deepwater SCR configuration
    RiserConfiguration config = new RiserConfiguration(RiserType.STEEL_CATENARY_RISER)
        .setWaterDepth(1500.0).setTopAngle(10.0).setDepartureAngle(20.0)
        .setInnerDiameter(12, "inch").setNumberOfSections(100).setAmbientTemperature(4.0);

    PipeBeggsAndBrills riser = config.create("Deepwater SCR", inletStream);

    assertNotNull(riser);
    assertEquals(0.3048, riser.getDiameter(), 0.001); // 12 inch

    riser.run();

    // Check length after run (SCR is longer than water depth due to catenary)
    assertTrue(riser.getLength() > 1500.0);

    // Check the outlet exists
    assertNotNull(riser.getOutletStream());
    assertTrue(riser.getOutletStream().getPressure("bara") > 0);
  }

  @Test
  void testFlexibleRiser() {
    PipeBeggsAndBrills riser = RiserConfiguration.createRiser(RiserType.FLEXIBLE_RISER,
        "Flexible Riser", inletStream, 350.0);

    assertNotNull(riser);

    riser.run();

    // Flexible riser length factor is 1.3 - check after run
    assertEquals(350.0 * 1.3, riser.getLength(), 1.0);
    assertNotNull(riser.getOutletStream());
  }

  @Test
  void testHybridRiser() {
    PipeBeggsAndBrills riser =
        RiserConfiguration.createRiser(RiserType.HYBRID_RISER, "Hybrid Riser", inletStream, 600.0);

    assertNotNull(riser);

    riser.run();

    // Hybrid riser length factor is 1.4 - check after run
    assertEquals(600.0 * 1.4, riser.getLength(), 1.0);
    assertNotNull(riser.getOutletStream());
  }

  @Test
  void testRiserWithMechanicalDesign() {
    PipeBeggsAndBrills riser =
        RiserConfiguration.createSCR("Riser for MechDesign", inletStream, 500.0);
    riser.run();

    // Initialize mechanical design
    riser.initMechanicalDesign();
    assertNotNull(riser.getMechanicalDesign());

    // Set design parameters
    riser.getMechanicalDesign().setMaxOperationPressure(150.0);
    riser.getMechanicalDesign().calcDesign();
  }
}
