package neqsim.process.mechanicaldesign.pump;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for PumpMechanicalDesign class.
 *
 * <p>
 * Tests API 610 pump sizing calculations including impeller, shaft, driver sizing.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class PumpMechanicalDesignTest {
  private Pump pump;
  private PumpMechanicalDesign mechDesign;

  @BeforeEach
  public void setUp() {
    // Create a test fluid (water at ambient conditions)
    SystemInterface fluid = new SystemSrkEos(298.15, 1.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");

    Stream feedStream = new Stream("Feed", fluid);
    feedStream.setFlowRate(100.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(1.0, "bara");
    feedStream.run();

    pump = new Pump("TestPump", feedStream);
    pump.setOutletPressure(10.0, "bara");
    pump.setIsentropicEfficiency(0.75);
    pump.run();

    mechDesign = pump.getMechanicalDesign();
    mechDesign.calcDesign();
  }

  @Test
  public void testMechanicalDesignNotNull() {
    assertNotNull(mechDesign, "Mechanical design should not be null");
    assertNotNull(pump.getMechanicalDesign(), "getMechanicalDesign should return the same object");
  }

  @Test
  public void testImpellerDiameter() {
    double impellerDiam = mechDesign.getImpellerDiameter();
    assertTrue(impellerDiam > 0, "Impeller diameter should be positive");
    assertTrue(impellerDiam < 2000, "Impeller diameter should be reasonable (< 2m)");
    System.out.println("Impeller diameter: " + impellerDiam + " mm");
  }

  @Test
  public void testShaftDiameter() {
    double shaftDiam = mechDesign.getShaftDiameter();
    assertTrue(shaftDiam > 0, "Shaft diameter should be positive");
    assertTrue(shaftDiam < impellerDiameterUpperBound(mechDesign.getImpellerDiameter()),
        "Shaft diameter should be smaller than impeller");
    System.out.println("Shaft diameter: " + shaftDiam + " mm");
  }

  private double impellerDiameterUpperBound(double impellerDiam) {
    return impellerDiam;
  }

  @Test
  public void testDriverPower() {
    double driverPower = mechDesign.getDriverPower();
    assertTrue(driverPower > 0, "Driver power should be positive");
    // Driver should be larger than hydraulic power (includes margin)
    double hydraulicPower = pump.getPower() / 1000.0; // Convert to kW
    assertTrue(driverPower >= hydraulicPower, "Driver power should be >= hydraulic power");
    System.out.println("Driver power: " + driverPower + " kW");
  }

  @Test
  public void testSpecificSpeed() {
    double ns = mechDesign.getSpecificSpeed();
    assertTrue(ns > 0, "Specific speed should be positive");
    System.out.println("Specific speed: " + ns);
  }

  @Test
  public void testNpshRequired() {
    double npshReq = mechDesign.getNpshRequired();
    assertTrue(npshReq > 0, "NPSH required should be positive");
    assertTrue(npshReq < 50, "NPSH required should be reasonable (< 50m)");
    System.out.println("NPSH required: " + npshReq + " m");
  }

  @Test
  public void testCasingWeight() {
    double weight = mechDesign.getCasingWeight();
    assertTrue(weight > 0, "Casing weight should be positive");
    System.out.println("Casing weight: " + weight + " kg");
  }

  @Test
  public void testWeight() {
    double weight = mechDesign.getCasingWeight() + mechDesign.getMotorWeight()
        + mechDesign.getBaseplateWeight();
    assertTrue(weight > 0, "Total weight should be positive");
    System.out.println("Total weight: " + weight + " kg");
  }

  @Test
  public void testPumpType() {
    PumpMechanicalDesign.PumpType pumpType = mechDesign.getPumpType();
    assertNotNull(pumpType, "Pump type should be set");
    System.out.println("Pump type: " + pumpType);
  }

  @Test
  public void testDesignPressure() {
    double designPressure = mechDesign.getDesignPressure();
    double outletPressure = 10.0; // bara from setup
    assertTrue(designPressure >= outletPressure, "Design pressure should be >= operating pressure");
    System.out.println("Design pressure: " + designPressure + " bara");
  }

  @Test
  public void testDesignTemperature() {
    double designTemp = mechDesign.getDesignTemperature();
    double operatingTemp = 25.0; // C from setup
    assertTrue(designTemp >= operatingTemp,
        "Design temperature should be >= operating temperature");
    System.out.println("Design temperature: " + designTemp + " C");
  }

  @Test
  public void testNozzleSizes() {
    double inletNozzle = mechDesign.getSuctionNozzleSize();
    double outletNozzle = mechDesign.getDischargeNozzleSize();
    assertTrue(inletNozzle > 0, "Inlet nozzle should be positive");
    assertTrue(outletNozzle > 0, "Outlet nozzle should be positive");
    assertTrue(inletNozzle >= outletNozzle, "Inlet nozzle should typically be >= outlet nozzle");
    System.out.println("Inlet nozzle: " + inletNozzle + " mm, Outlet: " + outletNozzle + " mm");
  }

  // ============================================================================
  // Process Design Parameter Tests
  // ============================================================================

  @Test
  public void testNpshMarginFactor() {
    double factor = mechDesign.getNpshMarginFactor();
    assertTrue(factor >= 1.0, "NPSH margin factor should be >= 1.0");
    assertTrue(factor <= 2.0, "NPSH margin factor should be reasonable (<= 2.0)");
    System.out.println("NPSH margin factor: " + factor);
  }

  @Test
  public void testPorFractions() {
    double porLow = mechDesign.getPorLowFraction();
    double porHigh = mechDesign.getPorHighFraction();
    assertTrue(porLow > 0 && porLow < 1.0, "POR low fraction should be between 0 and 1");
    assertTrue(porHigh > 1.0, "POR high fraction should be > 1");
    assertTrue(porHigh < porLow + 0.8, "POR range should be reasonable");
    System.out.println("POR: " + porLow + " - " + porHigh + " of BEP");
  }

  @Test
  public void testAorFractions() {
    double aorLow = mechDesign.getAorLowFraction();
    double aorHigh = mechDesign.getAorHighFraction();
    double porLow = mechDesign.getPorLowFraction();
    double porHigh = mechDesign.getPorHighFraction();
    assertTrue(aorLow <= porLow, "AOR low should be <= POR low");
    assertTrue(aorHigh >= porHigh, "AOR high should be >= POR high");
    System.out.println("AOR: " + aorLow + " - " + aorHigh + " of BEP");
  }

  @Test
  public void testMaxSuctionSpecificSpeed() {
    double maxNss = mechDesign.getMaxSuctionSpecificSpeed();
    assertTrue(maxNss >= 8000, "Max Nss should be >= 8000");
    assertTrue(maxNss <= 15000, "Max Nss should be <= 15000");
    System.out.println("Max suction specific speed: " + maxNss);
  }

  @Test
  public void testHeadMarginFactor() {
    double factor = mechDesign.getHeadMarginFactor();
    assertTrue(factor >= 1.0, "Head margin factor should be >= 1.0");
    assertTrue(factor <= 1.2, "Head margin factor should be reasonable (<= 1.2)");
    System.out.println("Head margin factor: " + factor);
  }

  // ============================================================================
  // Validation Method Tests
  // ============================================================================

  @Test
  public void testValidateNpshMargin() {
    // Test with adequate NPSH margin
    boolean result1 = mechDesign.validateNpshMargin(10.0, 5.0);
    assertTrue(result1, "Should pass with NPSH available > required * margin");

    // Test with inadequate NPSH margin
    boolean result2 = mechDesign.validateNpshMargin(5.0, 5.0);
    assertTrue(!result2 || mechDesign.getNpshMarginFactor() <= 1.0,
        "Should fail when NPSH margin is not met");
  }

  @Test
  public void testValidateOperatingInPOR() {
    // Test at BEP (100% of BEP)
    boolean atBep = mechDesign.validateOperatingInPOR(100.0, 100.0);
    assertTrue(atBep, "Operating at BEP should be within POR");

    // Test at very low flow
    boolean lowFlow = mechDesign.validateOperatingInPOR(30.0, 100.0);
    assertTrue(!lowFlow, "30% of BEP should typically be outside POR");

    // Test at very high flow
    boolean highFlow = mechDesign.validateOperatingInPOR(150.0, 100.0);
    assertTrue(!highFlow, "150% of BEP should typically be outside POR");
  }

  @Test
  public void testValidateOperatingInAOR() {
    // Test at BEP
    boolean atBep = mechDesign.validateOperatingInAOR(100.0, 100.0);
    assertTrue(atBep, "Operating at BEP should be within AOR");

    // Test outside AOR
    boolean extremeLow = mechDesign.validateOperatingInAOR(10.0, 100.0);
    assertTrue(!extremeLow, "10% of BEP should be outside AOR");
  }

  @Test
  public void testValidateSuctionSpecificSpeed() {
    // Test with acceptable Nss
    boolean acceptable = mechDesign.validateSuctionSpecificSpeed(10000.0);
    assertTrue(acceptable, "Nss of 10000 should be acceptable");

    // Test with high Nss
    boolean high = mechDesign.validateSuctionSpecificSpeed(15000.0);
    assertTrue(!high, "Nss of 15000 should exceed maximum");
  }

  @Test
  public void testComprehensiveValidation() {
    PumpMechanicalDesign.PumpValidationResult result = mechDesign.validateDesign();
    assertNotNull(result, "Validation result should not be null");
    assertNotNull(result.getIssues(), "Issues list should not be null");
    System.out.println("Validation valid: " + result.isValid());
    if (!result.isValid()) {
      for (String issue : result.getIssues()) {
        System.out.println("  Issue: " + issue);
      }
    }
  }
}
