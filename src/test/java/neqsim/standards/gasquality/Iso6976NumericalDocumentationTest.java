package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Reproduces the normalized four-component arithmetic in the ISO 6976 guide.
 *
 * <p>
 * This is an implementation consistency check, not independent standards qualification.
 *
 * @author NeqSim
 * @version 1.0
 */
class Iso6976NumericalDocumentationTest extends neqsim.NeqSimTest {
  /** Names in the guide's order. */
  private static final String[] NAMES = {"methane", "ethane", "nitrogen", "CO2"};
  /** Relative molar amounts, deliberately not summing to one. */
  private static final double[] AMOUNTS = {0.931819, 0.025618, 0.010335, 0.015391};
  /** Repository 1995 molar masses in g/mol. */
  private static final double[] MASSES = {16.043, 30.070, 28.0135, 44.010};
  /** Repository 1995 superior heats at 15 degrees Celsius in kJ/mol. */
  private static final double[] HEATS = {891.56, 1562.14, 0.0, 0.0};
  /** Repository 1995 summation factors at 15 degrees Celsius. */
  private static final double[] FACTORS = {0.0447, 0.0922, 0.0173, 0.0748};

  /**
   * Creates and initializes the same gas with a common amount multiplier.
   *
   * @param scale positive amount multiplier
   * @return initialized gas with normalized overall composition
   */
  private SystemInterface gas(double scale) {
    SystemInterface gas = new SystemSrkEos(288.15, 1.01325);
    for (int i = 0; i < NAMES.length; i++) {
      gas.addComponent(NAMES[i], scale * AMOUNTS[i]);
    }
    gas.setMixingRule("classic");
    gas.init(0);
    return gas;
  }

  /**
   * Calculates the documented real 15/15 volume-basis result.
   *
   * @param gas initialized gas
   * @return calculated legacy-edition quality properties
   */
  private Standard_ISO6976 quality(SystemInterface gas) {
    Standard_ISO6976 standard = new Standard_ISO6976(gas, 15.0, 15.0, "volume");
    standard.setReferenceState("real");
    standard.calculate();
    return standard;
  }

  /** Checks the complete formula chain without borrowing the eight-component test results. */
  @Test
  void normalizedFourComponentWorkedExample() {
    double total = 0.0;
    for (double amount : AMOUNTS) {
      total += amount;
    }
    assertEquals(0.983163, total, 1e-12);
    SystemInterface gas = gas(1.0);
    double mass = 0.0;
    double heat = 0.0;
    double factor = 0.0;
    for (int i = 0; i < NAMES.length; i++) {
      double x = AMOUNTS[i] / total;
      assertEquals(x, gas.getPhase(0).getComponent(i).getz(), 1e-12);
      mass += x * MASSES[i];
      heat += x * HEATS[i];
      factor += x * FACTORS[i];
    }
    double z = 1.0 - factor * factor;
    double molarDensity = 101325.0 / (8.314510 * 288.15 * z);
    double relativeDensity = mass / 28.96546 * 0.99958 / z;
    double gcv = heat * molarDensity;
    Standard_ISO6976 standard = quality(gas);
    assertEquals(mass, standard.getValue("MolarMass"), 1e-10);
    assertEquals(z, standard.getValue("CompressionFactor"), 1e-12);
    assertEquals(gcv, standard.getValue("GCV"), 1e-8);
    assertEquals(relativeDensity, standard.getValue("RelativeDensity"), 1e-12);
    assertEquals(gcv / Math.sqrt(relativeDensity), standard.getValue("SuperiorWobbeIndex"), 1e-8);
    assertEquals(mass / 1000.0 * molarDensity, standard.getValue("DensityReal"), 1e-12);
    standard.setReferenceType("molar");
    assertEquals(heat, standard.getValue("GCV"), 1e-10);
    assertEquals(37538.312497, gcv, 5e-6);
    assertEquals(48997.672257, gcv / Math.sqrt(relativeDensity), 5e-6);
  }

  /** Quality is intensive: normalizing or rescaling all amounts cannot change it. */
  @Test
  void amountScaleDoesNotChangeQuality() {
    Standard_ISO6976 baseline = quality(gas(1.0));
    String[] parameters = {"GCV", "LCV", "SuperiorWobbeIndex", "RelativeDensity", "CompressionFactor", "MolarMass",
        "DensityReal"};
    for (double scale : new double[] {0.01, 1.0 / 0.983163, 100.0}) {
      Standard_ISO6976 scaled = quality(gas(scale));
      for (String parameter : parameters) {
        assertEquals(baseline.getValue(parameter), scaled.getValue(parameter), 1e-8, parameter + " at scale " + scale);
      }
    }
  }
}
