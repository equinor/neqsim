package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * Independent nitrogen/NaCl salting benchmarks withheld from the parameter fit.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteNitrogenSaltingTest extends neqsim.NeqSimTest {
  /** Tests the omitted Morrison–Billett temperature on its reported molal basis. */
  @Test
  void heldOutMolalCoefficient() {
    double coefficient = Math.log10(henry(1.0, 303.15, 1.01325) / henry(0.0, 303.15, 1.01325));
    assertEquals(0.121, coefficient, 0.010, "Morrison–Billett reported molal coefficient, kg/mol");
  }

  /**
   * Tests separately published pressure/concentration data on the reported molar basis.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @param reference base-10 molar Setschenow coefficient in L/mol
   */
  @ParameterizedTest
  @CsvSource({"303.15, 50.0, 0.124", "324.65, 100.0, 0.122"})
  void independentOneMolarPressureBenchmarks(double temperature, double pressure, double reference) {
    double lower = 0.0;
    double upper = 2.0;
    for (int iteration = 0; iteration < 50; iteration++) {
      double molality = 0.5 * (lower + upper);
      double molarity = molality * brineDensity(molality, temperature) / (1.0 + molality * 0.05844277);
      if (molarity < 1.0) {
        lower = molality;
      } else {
        upper = molality;
      }
    }
    double molality = 0.5 * (lower + upper);
    double henryRatio = henry(molality, temperature, pressure) / henry(0.0, temperature, pressure);
    double coefficient = Math.log10(
        henryRatio * (1.0 + molality * 0.05844277) * waterDensity(temperature) / brineDensity(molality, temperature));
    // Engineering qualification envelope, not the experimental measurement uncertainty.
    assertEquals(reference, coefficient, 0.012, "Independent molar coefficient, L/mol");
  }

  /**
   * Evaluates the infinite-dilution molal Henry constant using the public EOS initialization path.
   *
   * @param molality salt molality in mol/kg water
   * @param temperature temperature in K
   * @param pressure pressure in bar
   * @return molal Henry constant in bar kg/mol
   */
  private static double henry(double molality, double temperature, double pressure) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, pressure);
    fluid.addComponent("nitrogen", 1e-8);
    fluid.addComponent("water", 1.0);
    if (molality > 0.0) {
      fluid.addComponent("Na+", molality * 0.01801528);
      fluid.addComponent("Cl-", molality * 0.01801528);
    }
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setForceSinglePhase(PhaseType.AQUEOUS);
    fluid.init(3);
    PhaseInterface aqueous = fluid.getPhase(0);
    return aqueous.getComponent("nitrogen").getFugacityCoefficient() * pressure * aqueous.getComponent("water").getx()
        * aqueous.getComponent("water").getMolarMass();
  }

  /**
   * Returns the Laliberte–Cooper reference water density, not the EOS volume.
   *
   * @param temperature temperature in K
   * @return density in kg/L
   */
  private static double waterDensity(double temperature) {
    double t = temperature - 273.15;
    return (((((-2.8054253e-10 * t + 1.0556302e-7) * t - 4.6170461e-5) * t - 0.0079870401) * t + 16.945176) * t
        + 999.83952) / (1.0 + 0.01687985 * t) / 1000.0;
  }

  /**
   * Returns the Laliberte–Cooper NaCl solution density for concentration-basis conversion.
   *
   * @param molality salt molality in mol/kg water
   * @param temperature temperature in K
   * @return density in kg/L
   */
  private static double brineDensity(double molality, double temperature) {
    double t = temperature - 273.15;
    double massFraction = molality * 0.05844277 / (1.0 + molality * 0.05844277);
    double apparentVolume = (massFraction + 1.01660 + 0.014624 * t)
        / ((-0.00433 * massFraction + 0.06471) * Math.exp(1e-6 * (t + 3315.6) * (t + 3315.6)));
    return 1.0 / ((1.0 - massFraction) / (1000.0 * waterDensity(temperature)) + massFraction * apparentVolume) / 1000.0;
  }
}
