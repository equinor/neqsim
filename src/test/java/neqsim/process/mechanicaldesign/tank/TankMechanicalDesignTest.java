package neqsim.process.mechanicaldesign.tank;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for TankMechanicalDesign class.
 *
 * <p>
 * Tests API 650/620 tank sizing calculations.
 * </p>
 *
 * @author NeqSim Development Team
 */
public class TankMechanicalDesignTest {
  private Tank tank;
  private TankMechanicalDesign mechDesign;

  @BeforeEach
  public void setUp() {
    // Create a test fluid
    SystemInterface fluid = new SystemSrkEos(298.15, 1.5);
    fluid.addComponent("n-heptane", 0.9);
    fluid.addComponent("n-octane", 0.1);
    fluid.setMixingRule("classic");

    Stream feedStream = new Stream("Feed", fluid);
    feedStream.setFlowRate(500.0, "kg/hr");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(1.5, "bara");
    feedStream.run();

    tank = new Tank("TestTank", feedStream);
    tank.run();

    mechDesign = tank.getMechanicalDesign();
    mechDesign.calcDesign();
  }

  @Test
  public void testMechanicalDesignNotNull() {
    assertNotNull(mechDesign, "Mechanical design should not be null");
  }

  @Test
  public void testTankDimensions() {
    double diameter = mechDesign.getTankDiameter();
    double height = mechDesign.getTankHeight();

    assertTrue(diameter > 0, "Tank diameter should be positive");
    assertTrue(height > 0, "Tank height should be positive");
    System.out.println("Tank diameter: " + diameter + " m");
    System.out.println("Tank height: " + height + " m");
  }

  @Test
  public void testShellThicknesses() {
    double[] thicknesses = mechDesign.getShellThicknesses();
    assertNotNull(thicknesses, "Shell thicknesses array should not be null");

    if (thicknesses.length > 0) {
      // Bottom course should be thickest
      double bottomCourse = thicknesses[0];
      assertTrue(bottomCourse > 0, "Bottom course should have positive thickness");

      // Each course should be >= the one above it
      for (int i = 1; i < thicknesses.length; i++) {
        assertTrue(thicknesses[i - 1] >= thicknesses[i],
            "Lower courses should be thicker or equal");
      }
      System.out.println("Number of shell courses: " + thicknesses.length);
      System.out.println("Bottom course thickness: " + bottomCourse + " mm");
    }
  }

  @Test
  public void testNominalCapacity() {
    double capacity = mechDesign.getNominalCapacity();
    assertTrue(capacity > 0, "Nominal capacity should be positive");
    System.out.println("Nominal capacity: " + capacity + " m³");
  }

  @Test
  public void testFoundationLoad() {
    double load = mechDesign.getFoundationLoad();
    assertTrue(load > 0, "Foundation load should be positive");
    System.out.println("Foundation load: " + load + " kN");
  }

  @Test
  public void testTankType() {
    TankMechanicalDesign.TankType tankType = mechDesign.getTankType();
    assertNotNull(tankType, "Tank type should be set");
    System.out.println("Tank type: " + tankType);
  }

  @Test
  public void testRoofType() {
    TankMechanicalDesign.RoofType roofType = mechDesign.getRoofType();
    assertNotNull(roofType, "Roof type should be set");
    System.out.println("Roof type: " + roofType);
  }

  @Test
  public void testWeight() {
    double weight =
        mechDesign.getShellWeight() + mechDesign.getBottomWeight() + mechDesign.getRoofWeight();
    assertTrue(weight > 0, "Weight should be positive");
    System.out.println("Total weight: " + weight + " kg");
  }

  @Test
  public void testBottomThickness() {
    double bottomThickness = mechDesign.getBottomThickness();
    assertTrue(bottomThickness >= 6.0, "Bottom thickness should be at least 6mm per API 650");
    System.out.println("Bottom thickness: " + bottomThickness + " mm");
  }

  @Test
  public void testRoofThickness() {
    double roofThickness = mechDesign.getRoofThickness();
    assertTrue(roofThickness > 0, "Roof thickness should be positive");
    System.out.println("Roof thickness: " + roofThickness + " mm");
  }

  @Test
  public void testStructuralWeights() {
    double shellWeight = mechDesign.getShellWeight();
    double bottomWeight = mechDesign.getBottomWeight();
    double roofWeight = mechDesign.getRoofWeight();
    assertTrue(shellWeight >= 0, "Shell weight should be non-negative");
    assertTrue(bottomWeight >= 0, "Bottom weight should be non-negative");
    assertTrue(roofWeight >= 0, "Roof weight should be non-negative");
    System.out.println("Shell: " + shellWeight + " kg, Bottom: " + bottomWeight + " kg, Roof: "
        + roofWeight + " kg");
  }
}
