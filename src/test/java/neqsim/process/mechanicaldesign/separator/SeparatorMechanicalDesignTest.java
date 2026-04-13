package neqsim.process.mechanicaldesign.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for SeparatorMechanicalDesign class.
 *
 * <p>
 * Tests process design parameters and validation methods for separator vessels.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class SeparatorMechanicalDesignTest {
  private Separator separator;
  private SeparatorMechanicalDesign mechDesign;

  @BeforeEach
  public void setUp() {
    // Create a two-phase test fluid
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-pentane", 0.1);
    fluid.setMixingRule("classic");

    Stream feedStream = new Stream("Feed", fluid);
    feedStream.setFlowRate(10000.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(20.0, "bara");
    feedStream.run();

    separator = new Separator("TestSeparator", feedStream);
    separator.run();

    mechDesign = (SeparatorMechanicalDesign) separator.getMechanicalDesign();
    mechDesign.calcDesign();
  }

  // ============================================================================
  // Process Design Parameter Tests
  // ============================================================================

  @Test
  public void testFoamAllowanceFactor() {
    double factor = mechDesign.getFoamAllowanceFactor();
    assertTrue(factor >= 1.0, "Foam allowance factor should be >= 1.0");
    assertTrue(factor <= 2.0, "Foam allowance factor should be reasonable (<= 2.0)");
    System.out.println("Foam allowance factor: " + factor);
  }

  @Test
  public void testDropletDiameters() {
    double gasLiquid = mechDesign.getDropletDiameterGasLiquid();
    double liquidLiquid = mechDesign.getDropletDiameterLiquidLiquid();

    assertTrue(gasLiquid > 0, "Gas-liquid droplet diameter should be positive");
    assertTrue(liquidLiquid > 0, "Liquid-liquid droplet diameter should be positive");
    assertTrue(gasLiquid < liquidLiquid, "Gas-liquid droplet diameter should typically be smaller");

    System.out.println("Gas-liquid droplet diameter: " + gasLiquid + " um");
    System.out.println("Liquid-liquid droplet diameter: " + liquidLiquid + " um");
  }

  @Test
  public void testDemisterParameters() {
    double pressureDrop = mechDesign.getDemisterPressureDrop();
    double voidFraction = mechDesign.getDemisterVoidFraction();
    double thickness = mechDesign.getDemisterThickness();

    assertTrue(pressureDrop > 0, "Demister pressure drop should be positive");
    assertTrue(voidFraction > 0 && voidFraction < 1, "Void fraction should be between 0 and 1");
    assertTrue(thickness > 0, "Demister thickness should be positive");

    System.out.println("Demister pressure drop: " + pressureDrop + " mbar");
    System.out.println("Demister void fraction: " + voidFraction);
    System.out.println("Demister thickness: " + thickness + " m");
  }

  @Test
  public void testVelocityLimits() {
    double maxGas = mechDesign.getMaxGasVelocityLimit();
    double maxLiquid = mechDesign.getMaxLiquidVelocity();

    assertTrue(maxGas > 0, "Max gas velocity should be positive");
    assertTrue(maxLiquid > 0, "Max liquid velocity should be positive");
    assertTrue(maxGas > maxLiquid, "Max gas velocity should typically be > max liquid velocity");

    System.out.println("Max gas velocity: " + maxGas + " m/s");
    System.out.println("Max liquid velocity: " + maxLiquid + " m/s");
  }

  @Test
  public void testRetentionTimes() {
    double oilRetention = mechDesign.getMinOilRetentionTime();
    double waterRetention = mechDesign.getMinWaterRetentionTime();

    assertTrue(oilRetention > 0, "Min oil retention time should be positive");
    assertTrue(waterRetention > 0, "Min water retention time should be positive");

    System.out.println("Min oil retention time: " + oilRetention + " min");
    System.out.println("Min water retention time: " + waterRetention + " min");
  }

  @Test
  public void testDesignMargins() {
    double pressureMargin = mechDesign.getDesignPressureMargin();
    double tempMargin = mechDesign.getDesignTemperatureMarginC();

    assertTrue(pressureMargin >= 1.0, "Design pressure margin should be >= 1.0");
    assertTrue(tempMargin >= 0, "Design temperature margin should be >= 0");

    System.out.println("Design pressure margin: " + pressureMargin);
    System.out.println("Design temperature margin: " + tempMargin + " C");
  }

  // ============================================================================
  // Validation Method Tests
  // ============================================================================

  @Test
  public void testValidateGasVelocity() {
    double maxVel = mechDesign.getMaxGasVelocityLimit();

    // Test with velocity below limit
    assertTrue(mechDesign.validateGasVelocity(maxVel * 0.8), "Velocity 80% of max should pass");

    // Test with velocity above limit
    assertFalse(mechDesign.validateGasVelocity(maxVel * 1.2), "Velocity 120% of max should fail");
  }

  @Test
  public void testValidateLiquidVelocity() {
    double maxVel = mechDesign.getMaxLiquidVelocity();

    // Test with velocity below limit
    assertTrue(mechDesign.validateLiquidVelocity(maxVel * 0.5), "Velocity 50% of max should pass");

    // Test with velocity above limit
    assertFalse(mechDesign.validateLiquidVelocity(maxVel * 1.5),
        "Velocity 150% of max should fail");
  }

  @Test
  public void testValidateRetentionTime() {
    double minOilTime = mechDesign.getMinOilRetentionTime();

    // Test with adequate retention time
    assertTrue(mechDesign.validateRetentionTime(minOilTime * 2, true),
        "Retention time 2x minimum should pass for oil");

    // Test with inadequate retention time
    assertFalse(mechDesign.validateRetentionTime(minOilTime * 0.5, true),
        "Retention time 0.5x minimum should fail for oil");
  }

  @Test
  public void testValidateDropletDiameter() {
    double designDiam = mechDesign.getDropletDiameterGasLiquid();

    // Test with larger droplets (easier to separate)
    assertTrue(mechDesign.validateDropletDiameter(designDiam * 2, true),
        "Droplets 2x design should pass");

    // Test with smaller droplets (harder to separate)
    assertFalse(mechDesign.validateDropletDiameter(designDiam * 0.5, true),
        "Droplets 0.5x design should fail");
  }

  @Test
  public void testComprehensiveValidation() {
    SeparatorMechanicalDesign.SeparatorValidationResult result =
        mechDesign.validateDesignComprehensive();

    assertNotNull(result, "Validation result should not be null");
    assertNotNull(result.getIssues(), "Issues list should not be null");

    System.out.println("Separator validation valid: " + result.isValid());
    if (!result.isValid()) {
      for (String issue : result.getIssues()) {
        System.out.println("  Issue: " + issue);
      }
    }
  }

  // ============================================================================
  // Response Class Tests
  // ============================================================================

  @Test
  public void testResponseClassPopulation() {
    SeparatorMechanicalDesignResponse response = new SeparatorMechanicalDesignResponse(mechDesign);

    // Check process design parameters are populated
    assertEquals(mechDesign.getFoamAllowanceFactor(), response.getFoamAllowanceFactor(), 0.001,
        "Foam allowance factor should match");
    assertEquals(mechDesign.getDropletDiameterGasLiquid(), response.getDropletDiameterGasLiquid(),
        0.001, "Gas-liquid droplet diameter should match");
    assertEquals(mechDesign.getDemisterPressureDrop(), response.getDemisterPressureDrop(), 0.001,
        "Demister pressure drop should match");

    System.out.println("Response class populated successfully");
  }

  // ============================================================================
  // Entrainment Integration Tests
  // ============================================================================

  @Test
  public void testEntrainmentFieldsDefaultWhenNotEnabled() {
    // Without enabling detailed entrainment, fields should be at defaults
    assertFalse(mechDesign.isDetailedEntrainmentUsed(),
        "Detailed entrainment should be false by default");
    assertEquals(0.0, mechDesign.getOilInGasFraction(), 1e-10,
        "Oil-in-gas fraction should be 0 when not enabled");
    assertEquals(0.0, mechDesign.getWaterInGasFraction(), 1e-10,
        "Water-in-gas fraction should be 0 when not enabled");
    assertEquals(1.0, mechDesign.getLiquidInGasCalibrationFactor(), 1e-10,
        "LIG calibration factor should be 1.0 when not enabled");
    assertEquals(1.0, mechDesign.getGasCarryUnderCalibrationFactor(), 1e-10,
        "GCU calibration factor should be 1.0 when not enabled");
    assertEquals(1.0, mechDesign.getLiquidLiquidCalibrationFactor(), 1e-10,
        "LL calibration factor should be 1.0 when not enabled");
    assertFalse(mechDesign.isMistEliminatorFlooded(),
        "Mist eliminator should not be flooded by default");
  }

  @Test
  public void testEntrainmentFieldsPopulatedWhenEnabled() {
    // Create fluid with gas + liquid phases
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-pentane", 0.1);
    fluid.addComponent("n-heptane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed2", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(30.0, "bara");
    feed.run();

    Separator sep = new Separator("TestSep2", feed);
    sep.setDetailedEntrainmentCalculation(true);
    sep.run();

    SeparatorMechanicalDesign design =
        (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    design.calcDesign();

    assertTrue(design.isDetailedEntrainmentUsed(),
        "Detailed entrainment should be enabled after calcDesign");
    System.out.println("Entrainment enabled — efficiency: "
        + design.getOverallGasLiquidEfficiency());
    System.out.println("Oil-in-gas: " + design.getOilInGasFraction());
    System.out.println("K-factor utilization: " + design.getKFactorUtilization());
    System.out.println("Mist eliminator flooded: " + design.isMistEliminatorFlooded());
    System.out.println("Detail JSON null: " + (design.getEntrainmentDetailJson() == null));
    if (design.getEntrainmentDetailJson() != null) {
      System.out.println("Detail JSON length: " + design.getEntrainmentDetailJson().length());
    }
    // The performance calculator is populated after run() — entrainment fractions
    // and efficiency should be populated (may be 0 if no gas-liquid separation
    // is computed, but the fields should be available).
    assertTrue(design.getOverallGasLiquidEfficiency() >= 0,
        "Overall gas-liquid efficiency should be >= 0");
    assertNotNull(design.getEntrainmentDetailJson(),
        "Entrainment detail JSON should not be null");
    assertTrue(design.getEntrainmentDetailJson().contains("overallGasLiquidEfficiency"),
        "Entrainment JSON should contain efficiency field");
  }

  @Test
  public void testEntrainmentInResponseJson() {
    // Setup separator with detailed entrainment
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-pentane", 0.1);
    fluid.addComponent("n-heptane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed3", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(30.0, "bara");
    feed.run();

    Separator sep = new Separator("TestSep3", feed);
    sep.setDetailedEntrainmentCalculation(true);
    sep.run();

    SeparatorMechanicalDesign design =
        (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    design.calcDesign();

    // Get JSON output
    String json = design.toJson();
    assertNotNull(json, "JSON output should not be null");
    assertTrue(json.contains("detailedEntrainmentUsed"),
        "JSON should contain detailedEntrainmentUsed");
    assertTrue(json.contains("overallGasLiquidEfficiency"),
        "JSON should contain overallGasLiquidEfficiency");
    assertTrue(json.contains("oilInGasFraction"),
        "JSON should contain oilInGasFraction");
    assertTrue(json.contains("liquidInGasCalibrationFactor"),
        "JSON should contain calibration factor");

    System.out.println("Entrainment data present in mechanical design JSON: OK");
  }

  @Test
  public void testResponseEntrainmentFieldsMatch() {
    // Setup separator with detailed entrainment
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-pentane", 0.1);
    fluid.addComponent("n-heptane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Feed4", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(30.0, "bara");
    feed.run();

    Separator sep = new Separator("TestSep4", feed);
    sep.setDetailedEntrainmentCalculation(true);
    sep.run();

    SeparatorMechanicalDesign design =
        (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    design.calcDesign();

    SeparatorMechanicalDesignResponse response =
        new SeparatorMechanicalDesignResponse(design);

    // Verify response fields match design fields
    assertEquals(design.isDetailedEntrainmentUsed(), response.isDetailedEntrainmentUsed(),
        "detailedEntrainmentUsed should match");
    assertEquals(design.getOverallGasLiquidEfficiency(),
        response.getOverallGasLiquidEfficiency(), 1e-10,
        "overallGasLiquidEfficiency should match");
    assertEquals(design.getOilInGasFraction(), response.getOilInGasFraction(), 1e-10,
        "oilInGasFraction should match");
    assertEquals(design.getLiquidInGasCalibrationFactor(),
        response.getLiquidInGasCalibrationFactor(), 1e-10,
        "liquidInGasCalibrationFactor should match");
  }
}
