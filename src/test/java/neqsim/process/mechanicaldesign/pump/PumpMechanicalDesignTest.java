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
}
