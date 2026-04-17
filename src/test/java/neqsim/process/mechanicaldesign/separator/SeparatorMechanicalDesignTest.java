package neqsim.process.mechanicaldesign.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.entrainment.InletDeviceModel;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternal;
import neqsim.process.mechanicaldesign.separator.internals.DemistingInternalWithDrainage;
import neqsim.process.mechanicaldesign.separator.primaryseparation.InletCyclones;
import neqsim.process.mechanicaldesign.separator.primaryseparation.InletVane;
import neqsim.process.mechanicaldesign.separator.primaryseparation.InletVaneWithMeshpad;
import neqsim.process.mechanicaldesign.separator.primaryseparation.PrimarySeparation;
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

    SeparatorMechanicalDesign design = (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    design.calcDesign();

    assertTrue(design.isDetailedEntrainmentUsed(),
        "Detailed entrainment should be enabled after calcDesign");
    System.out
        .println("Entrainment enabled — efficiency: " + design.getOverallGasLiquidEfficiency());
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
    assertNotNull(design.getEntrainmentDetailJson(), "Entrainment detail JSON should not be null");
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

    SeparatorMechanicalDesign design = (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    design.calcDesign();

    // Get JSON output
    String json = design.toJson();
    assertNotNull(json, "JSON output should not be null");
    assertTrue(json.contains("detailedEntrainmentUsed"),
        "JSON should contain detailedEntrainmentUsed");
    assertTrue(json.contains("overallGasLiquidEfficiency"),
        "JSON should contain overallGasLiquidEfficiency");
    assertTrue(json.contains("oilInGasFraction"), "JSON should contain oilInGasFraction");
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

    SeparatorMechanicalDesign design = (SeparatorMechanicalDesign) sep.getMechanicalDesign();
    design.calcDesign();

    SeparatorMechanicalDesignResponse response = new SeparatorMechanicalDesignResponse(design);

    // Verify response fields match design fields
    assertEquals(design.isDetailedEntrainmentUsed(), response.isDetailedEntrainmentUsed(),
        "detailedEntrainmentUsed should match");
    assertEquals(design.getOverallGasLiquidEfficiency(), response.getOverallGasLiquidEfficiency(),
        1e-10, "overallGasLiquidEfficiency should match");
    assertEquals(design.getOilInGasFraction(), response.getOilInGasFraction(), 1e-10,
        "oilInGasFraction should match");
    assertEquals(design.getLiquidInGasCalibrationFactor(),
        response.getLiquidInGasCalibrationFactor(), 1e-10,
        "liquidInGasCalibrationFactor should match");
  }

  // ============================================================================
  // Bridge Method Tests (MechanicalDesign -> Separator delegation)
  // ============================================================================

  @Test
  public void testSetInletPipeDiameterBridge() {
    double diameter = 0.305; // 12-inch pipe
    mechDesign.setInletPipeDiameter(diameter);

    // Verify that the separator's performance calculator received the value
    double actual = mechDesign.getInletPipeDiameter();
    assertEquals(diameter, actual, 1e-6,
        "Inlet pipe diameter should be set via MechanicalDesign bridge");
  }

  @Test
  public void testSetInletDeviceTypeBridge() {
    // Setting inlet device type via MechanicalDesign should delegate to Separator
    mechDesign.setInletDeviceType(InletDeviceModel.InletDeviceType.INLET_VANE);
    // No exception means success; the type is set on the separator's performance calculator
  }

  @Test
  public void testAddSeparatorSectionBridge() {
    int initialCount = mechDesign.getSeparatorSections().size();
    mechDesign.addSeparatorSection("DemisterMesh", "meshpad");
    int afterCount = mechDesign.getSeparatorSections().size();
    assertTrue(afterCount > initialCount,
        "Adding a section via MechanicalDesign should increase section count");
  }

  @Test
  public void testGetSeparatorSectionByName() {
    mechDesign.addSeparatorSection("TestVane", "vane");
    assertNotNull(mechDesign.getSeparatorSection("TestVane"),
        "Should retrieve section by name via MechanicalDesign");
  }

  @Test
  public void testSetGasLiquidSurfaceTensionBridge() {
    // Should not throw; delegates to Separator's performance calculator
    mechDesign.setGasLiquidSurfaceTension(0.020);
  }

  // ============================================================================
  // DemistingInternal Tests
  // ============================================================================

  @Test
  public void testDemistingInternalDefaults() {
    DemistingInternal demister = new DemistingInternal("WireMesh", "wire_mesh");
    assertEquals(0.107, demister.getKFactor(), 1e-6, "Wire mesh K-factor default");
    assertEquals(0.97, demister.getVoidFraction(), 1e-6, "Wire mesh void fraction default");
    assertTrue(demister.getThickness() > 0, "Thickness should be positive");
  }

  @Test
  public void testDemistingInternalGasVelocity() {
    DemistingInternal demister = new DemistingInternal("TestDemister", "wire_mesh");
    double gasDensity = 10.0; // kg/m3
    double liquidDensity = 700.0; // kg/m3
    double maxVel = demister.calcGasVelocity(0.0, gasDensity, liquidDensity);

    // K * sqrt((rhoL - rhoG) / rhoG) = 0.107 * sqrt((700-10)/10) = 0.107 * 8.307 = 0.889
    assertTrue(maxVel > 0.5, "Max gas velocity should be significant");
    assertTrue(maxVel < 2.0, "Max gas velocity should be reasonable");
  }

  @Test
  public void testDemistingInternalPressureDrop() {
    DemistingInternal demister = new DemistingInternal("TestDemister", "wire_mesh");
    double dp = demister.calcPressureDrop(1.0, 10.0); // 1 m/s, 10 kg/m3
    // Eu * 0.5 * rho * v^2 = 150 * 0.5 * 10 * 1 = 750 Pa
    assertEquals(750.0, dp, 1e-6, "Pressure drop calculation");
  }

  @Test
  public void testDemistingInternalCarryOver() {
    DemistingInternal demister = new DemistingInternal("TestDemister", "wire_mesh");

    // Below 80% of max: zero carry-over
    assertEquals(0.0, demister.calcLiquidCarryOver(0.5, 1.0), 1e-6,
        "Below 80% max should have zero carry-over");

    // At 100% of max: some carry-over
    double co = demister.calcLiquidCarryOver(1.0, 1.0);
    assertTrue(co > 0, "At max velocity should have some carry-over");
    assertTrue(co < 1.0, "At max velocity should not be fully flooded");

    // Well above max: approaching 1.0
    double highCo = demister.calcLiquidCarryOver(1.5, 1.0);
    assertTrue(highCo > co, "Above max should have higher carry-over");
  }

  @Test
  public void testDemistingInternalTypeDefaults() {
    DemistingInternal vanePack = new DemistingInternal("VanePack", "vane_pack");
    assertEquals(0.15, vanePack.getKFactor(), 1e-6, "Vane pack K-factor");
    assertEquals(40.0, vanePack.getEuNumber(), 1e-6, "Vane pack Eu number");

    DemistingInternal cyclone = new DemistingInternal("Cyclone", "cyclone");
    assertEquals(0.20, cyclone.getKFactor(), 1e-6, "Cyclone K-factor");
    assertEquals(60.0, cyclone.getEuNumber(), 1e-6, "Cyclone Eu number");
  }

  @Test
  public void testDemistingInternalWithDrainage() {
    DemistingInternalWithDrainage demister =
        new DemistingInternalWithDrainage("MeshWithDrainage", "wire_mesh");
    demister.setDrainageEfficiency(0.6);

    // Carry-over should be reduced by drainage
    double baseCarryOver = new DemistingInternal("Base", "wire_mesh").calcLiquidCarryOver(1.0, 1.0);
    double drainedCarryOver = demister.calcLiquidCarryOver(1.0, 1.0);

    assertTrue(drainedCarryOver < baseCarryOver, "Drainage should reduce carry-over: base="
        + baseCarryOver + " drained=" + drainedCarryOver);
    assertEquals(baseCarryOver * 0.4, drainedCarryOver, 1e-6,
        "Drainage efficiency 0.6 should give carry-over * 0.4");
  }

  // ============================================================================
  // PrimarySeparation Tests
  // ============================================================================

  @Test
  public void testPrimarySeparationInletMomentum() {
    PrimarySeparation primary = new PrimarySeparation("TestInlet");
    primary.setInletNozzleDiameter(0.254); // 10 inches

    double rhoMix = 50.0; // kg/m3
    double qv = 0.5; // m3/s
    double momentum = primary.calcInletMomentum(rhoMix, qv);

    assertTrue(momentum > 0, "Inlet momentum should be positive");
  }

  @Test
  public void testPrimarySeparationCheckMomentum() {
    PrimarySeparation primary = new PrimarySeparation("TestInlet");
    primary.setInletNozzleDiameter(0.254);
    primary.setMaxInletMomentum(3000.0);

    // Low flow should be within limits
    assertTrue(primary.checkInletMomentum(10.0, 0.1), "Low flow should be within momentum limits");
  }

  @Test
  public void testInletVaneDefaults() {
    InletVane vane = new InletVane("TestVane");
    assertEquals(6000.0, vane.getMaxInletMomentum(), 1e-6,
        "Inlet vane max momentum should be 6000 Pa");
    assertEquals(0.85, vane.getBulkSeparationEfficiency(), 1e-6,
        "Inlet vane efficiency should be 85%");
  }

  @Test
  public void testInletVaneWithMeshpadDefaults() {
    InletVaneWithMeshpad vaneWithMesh = new InletVaneWithMeshpad("TestVaneMesh");
    assertEquals(6000.0, vaneWithMesh.getMaxInletMomentum(), 1e-6,
        "Inlet vane+mesh max momentum should be 6000 Pa");
    assertEquals(0.92, vaneWithMesh.getBulkSeparationEfficiency(), 1e-6,
        "Inlet vane+mesh efficiency should be 92%");
  }

  @Test
  public void testInletCyclonesDefaults() {
    InletCyclones cyclones = new InletCyclones("TestCyclone");
    assertEquals(8000.0, cyclones.getMaxInletMomentum(), 1e-6,
        "Inlet cyclone max momentum should be 8000 Pa");
    assertEquals(0.95, cyclones.getBulkSeparationEfficiency(), 1e-6,
        "Inlet cyclone efficiency should be 95%");
    assertEquals(4, cyclones.getNumberOfCyclones(), "Default number of cyclones");
  }

  @Test
  public void testInletVaneWithMeshpadCarryOver() {
    InletVaneWithMeshpad vaneWithMesh = new InletVaneWithMeshpad("TestVaneMesh");
    vaneWithMesh.setInletNozzleDiameter(0.254);
    PrimarySeparation vane = new InletVane("CompareVane");
    vane.setInletNozzleDiameter(0.254);

    double rhoMix = 50.0;
    double qv = 0.5;

    double vaneCO = vane.calcLiquidCarryOver(rhoMix, qv);
    double vaneMeshCO = vaneWithMesh.calcLiquidCarryOver(rhoMix, qv);

    assertTrue(vaneMeshCO <= vaneCO,
        "Vane+mesh should have less or equal carry-over than plain vane");
  }
}
