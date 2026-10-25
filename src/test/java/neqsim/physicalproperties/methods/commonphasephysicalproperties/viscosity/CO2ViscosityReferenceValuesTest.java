package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Published check values from Laesecke and Muzny, JPCRD 46, 013107 (2017), Table 6.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CO2ViscosityReferenceValuesTest {
  /**
   * Checks all zero-density rows, including the explicitly documented extrapolation to 10000 K.
   *
   * @param temperature temperature in K
   * @param expectedMpas published dilute viscosity in mPa s
   * @param roundingMpas half the last printed digit in mPa s
   */
  @ParameterizedTest
  @CsvSource({"100,0.0053757,0.00000005", "2000,0.066079,0.0000005", "10000,0.17620,0.000005"})
  void publishedDiluteValues(double temperature, double expectedMpas, double roundingMpas) {
    assertEquals(expectedMpas * 1e-3, CO2ViscosityMethod.calculateDiluteGasViscosity(temperature), roundingMpas * 1e-3);
  }

  /**
   * Checks all six finite-density rows without conflating the transport correlation with an EOS density error.
   *
   * @param temperature temperature in K
   * @param density mass density in kg/m3
   * @param expectedMpas published viscosity in mPa s, excluding critical enhancement
   * @param roundingMpas half the last printed digit in mPa s
   */
  @ParameterizedTest
  @CsvSource({"220,3,0.011104,0.0000005", "225,1150,0.22218,0.000005", "300,65,0.015563,0.0000005",
      "300,1400,0.50594,0.000005", "700,100,0.033112,0.0000005", "700,1200,0.22980,0.000005"})
  void publishedTable6Values(double temperature, double density, double expectedMpas, double roundingMpas) {
    SystemInterface fluid = new SystemSrkEos(temperature, 1.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(1);
    PhaseInterface phase = fluid.getPhase(0);
    // NeqSim's internal molar-volume unit is 1e-5 m3/mol. Fix density directly as specified by the paper.
    phase.setMolarVolume(1e5 * phase.getMolarMass() / density);
    assertEquals(density, phase.getDensity(), density * 1e-14);
    CO2ViscosityMethod method = new CO2ViscosityMethod(phase.getPhysicalProperties());
    assertEquals(expectedMpas * 1e-3, method.calcViscosity(), roundingMpas * 1e-3);
    assertEquals(expectedMpas * 1e-3, CO2ViscosityMethod.calculateViscosity(temperature, density), roundingMpas * 1e-3);
  }

  /** Checks the analytic dilute limit and the independently evaluated sign-changing initial-density slope. */
  @Test
  void lowDensityLimitAndSlope() {
    double[][] states = {{220, -4.745694691894384e-9}, {300, 5.140534404833654e-9}, {700, 1.3037201889216724e-8}};
    for (double[] state : states) {
      double dilute = CO2ViscosityMethod.calculateDiluteGasViscosity(state[0]);
      assertEquals(dilute, CO2ViscosityMethod.calculateViscosity(state[0], 0.0), 0.0);
      assertEquals(dilute, CO2ViscosityMethod.calculateViscosity(state[0], 1e-20), dilute * 1e-14);
      double density = 1e-4;
      double slope = (CO2ViscosityMethod.calculateViscosity(state[0], density) - dilute) / density;
      assertEquals(state[1], slope, Math.abs(state[1]) * 1e-4);
    }
  }

  /** Rejects invalid scalars, finite-density extrapolation and an overflowing residual instead of returning NaN. */
  @Test
  void invalidInputsAreExplicit() {
    for (double temperature : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0, 99.999, 2000.001}) {
      assertThrows(IllegalArgumentException.class, () -> CO2ViscosityMethod.calculateViscosity(temperature, 65));
    }
    for (double density : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, Double.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> CO2ViscosityMethod.calculateViscosity(300, density));
    }
    assertThrows(IllegalArgumentException.class, () -> CO2ViscosityMethod.calculateDiluteGasViscosity(10000.001));
    assertTrue(CO2ViscosityMethod.calculateViscosity(100, 0) > 0.0);
    assertTrue(CO2ViscosityMethod.calculateViscosity(2000, 0) > 0.0);
  }
}
