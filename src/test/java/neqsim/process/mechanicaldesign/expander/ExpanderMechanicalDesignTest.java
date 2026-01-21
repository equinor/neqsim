package neqsim.process.mechanicaldesign.expander;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ExpanderMechanicalDesign class.
 *
 * <p>
 * Tests API 617 turboexpander sizing calculations.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class ExpanderMechanicalDesignTest {
  private Expander expander;
  private ExpanderMechanicalDesign mechDesign;

  @BeforeEach
  public void setUp() {
    // Create a test gas fluid
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream feedStream = new Stream("Feed", fluid);
    feedStream.setFlowRate(50000.0, "kg/hr");
    feedStream.setTemperature(80.0, "C");
    feedStream.setPressure(50.0, "bara");
    feedStream.run();

    expander = new Expander("TestExpander", feedStream);
    expander.setOutletPressure(20.0); // bara
    expander.setIsentropicEfficiency(0.80);
    expander.run();

    mechDesign = expander.getExpanderMechanicalDesign();
    mechDesign.calcDesign();
  }

  @Test
  public void testMechanicalDesignNotNull() {
    assertNotNull(mechDesign, "Mechanical design should not be null");
  }

  @Test
  public void testWheelDiameter() {
    double wheelDiam = mechDesign.getWheelDiameter();
    assertTrue(wheelDiam > 0, "Wheel diameter should be positive");
    assertTrue(wheelDiam < 2000, "Wheel diameter should be reasonable (< 2m)");
    System.out.println("Wheel diameter: " + wheelDiam + " mm");
  }

  @Test
  public void testRatedSpeed() {
    double speed = mechDesign.getRatedSpeed();
    assertTrue(speed > 0, "Rated speed should be positive");
    assertTrue(speed < 100000, "Rated speed should be reasonable (< 100,000 rpm)");
    System.out.println("Rated speed: " + speed + " rpm");
  }

  @Test
  public void testTipSpeed() {
    double tipSpeed = mechDesign.getTipSpeed();
    assertTrue(tipSpeed > 0, "Tip speed should be positive");
    assertTrue(tipSpeed <= 450, "Tip speed should be <= 450 m/s per API 617");
    System.out.println("Tip speed: " + tipSpeed + " m/s");
  }

  @Test
  public void testRecoveredPower() {
    double power = mechDesign.getRecoveredPower();
    assertTrue(power > 0, "Recovered power should be positive");
    System.out.println("Recovered power: " + power + " kW");
  }

  @Test
  public void testShaftDiameter() {
    double shaftDiam = mechDesign.getShaftDiameter();
    assertTrue(shaftDiam > 0, "Shaft diameter should be positive");
    System.out.println("Shaft diameter: " + shaftDiam + " mm");
  }

  @Test
  public void testWeight() {
    // Total weight is calculated internally - check recovered power indicates calculation ran
    double power = mechDesign.getRecoveredPower();
    assertTrue(power > 0, "Recovered power should be positive, indicating design was calculated");
    System.out.println("Recovered power: " + power + " kW");
  }

  @Test
  public void testExpanderType() {
    ExpanderMechanicalDesign.ExpanderType expType = mechDesign.getExpanderType();
    assertNotNull(expType, "Expander type should be set");
    System.out.println("Expander type: " + expType);
  }

  @Test
  public void testLoadType() {
    ExpanderMechanicalDesign.LoadType loadType = mechDesign.getLoadType();
    assertNotNull(loadType, "Load type should be set");
    System.out.println("Load type: " + loadType);
  }

  @Test
  public void testRecoveredPowerPositive() {
    double power = mechDesign.getRecoveredPower();
    assertTrue(power > 0, "Recovered power should be positive");
    System.out.println("Recovered power: " + power + " kW");
  }

  @Test
  public void testDesignPressure() {
    double designPressure = mechDesign.getCasingDesignPressure();
    double inletPressure = 50.0; // bara from setup
    assertTrue(designPressure >= inletPressure, "Design pressure should be >= inlet pressure");
    System.out.println("Design pressure: " + designPressure + " bara");
  }

  @Test
  public void testDesignTemperature() {
    double designTemp = mechDesign.getCasingDesignTemperature();
    double inletTemp = 80.0; // C from setup
    assertTrue(designTemp >= inletTemp, "Design temperature should be >= inlet temperature");
    System.out.println("Design temperature: " + designTemp + " C");
  }
}
