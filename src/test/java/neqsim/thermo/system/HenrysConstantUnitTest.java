package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests of the unit handling of {@link SystemInterface#calcHenrysConstant(String, String)}.
 *
 * <p>
 * The base method returns bar per mole fraction. The overload converts that to the concentration-based forms that
 * solubility data is usually quoted in, and the conversion is the point of failure a caller cannot check by inspection,
 * so it is asserted directly here.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class HenrysConstantUnitTest {
  /**
   * Build an oxygen-water system flashed at 40 C and 8 bara.
   *
   * @return a two-phase system ready for a Henry's constant evaluation
   */
  private SystemInterface oxygenWater() {
    SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 40.0, 8.0);
    fluid.addComponent("oxygen", 1.0);
    fluid.addComponent("water", 99.0);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /** A mol/m3/bar value and a mmol/L/bar value are the same number, since 1 mol/m3 is 1 mmol/L. */
  @Test
  public void testCubicMetreAndLitreFormsAreIdentical() {
    SystemInterface fluid = oxygenWater();
    double perCubicMetre = fluid.calcHenrysConstant("oxygen", "mol/m3/bar");
    double perLitre = fluid.calcHenrysConstant("oxygen", "mmol/L/bar");
    assertEquals(perCubicMetre, perLitre, 1e-12);
  }

  /** The default unit and an explicit "bar" must both give the bar per mole fraction form. */
  @Test
  public void testDefaultUnitIsBarPerMoleFraction() {
    SystemInterface fluid = oxygenWater();
    assertEquals(fluid.calcHenrysConstant("oxygen"), fluid.calcHenrysConstant("oxygen", "bar"), 1e-12);
    assertEquals(fluid.calcHenrysConstant("oxygen"), fluid.calcHenrysConstant("oxygen", null), 1e-12);
  }

  /** The mol/L and mg/L forms must be consistent scalings of the mol/m3 form. */
  @Test
  public void testConcentrationUnitsAreConsistent() {
    SystemInterface fluid = oxygenWater();
    double molPerCubicMetre = fluid.calcHenrysConstant("oxygen", "mol/m3/bar");
    assertEquals(molPerCubicMetre / 1000.0, fluid.calcHenrysConstant("oxygen", "mol/L/bar"), 1e-12);

    // Oxygen is 31.999 g/mol, so the mg/L form is about 32 times the mmol/L form.
    double mgPerLitre = fluid.calcHenrysConstant("oxygen", "mg/L/bar");
    assertEquals(molPerCubicMetre * 31.9988, mgPerLitre, 0.05 * mgPerLitre);
  }

  /** Oxygen solubility in water at 40 C is about 1 mol per cubic metre per bar. */
  @Test
  public void testOxygenInWaterMagnitude() {
    double h = oxygenWater().calcHenrysConstant("oxygen", "mol/m3/bar");
    assertTrue(h > 0.5 && h < 2.0, "expected about 1 mol/m3/bar for oxygen in water at 40 C, got " + h);
  }

  /** An unrecognised unit must be rejected rather than silently interpreted. */
  @Test
  public void testUnknownUnitThrows() {
    SystemInterface fluid = oxygenWater();
    assertThrows(IllegalArgumentException.class, () -> fluid.calcHenrysConstant("oxygen", "ppm/bar"));
  }

  /** A single-phase system must throw rather than return zero. */
  @Test
  public void testSinglePhaseThrows() {
    SystemInterface gas = new SystemSrkEos(273.15 + 40.0, 8.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    new ThermodynamicOperations(gas).TPflash();
    assertThrows(IllegalStateException.class, () -> gas.calcHenrysConstant("methane"));
  }
}
