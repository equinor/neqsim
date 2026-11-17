package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compares the bundled SRK-CPA model with selected measured water/MEG equilibrium points near 0.8 bara. Source: Lancia,
 * Musmarra and Pepe (1996), tables 1, 4 and 7, DOI 10.1252/jcej.29.449.
 *
 * @author NeqSim
 * @version 1.0
 */
class MegWaterVacuumVleAssessmentTest {
  /**
   * Calculate pressure and vapor MEG at the measured temperature and liquid composition. The tolerance is a rounded
   * regression envelope around the current model error. The measured vapor fractions remain the reference targets.
   *
   * @param temperatureC measured equilibrium temperature in degrees C
   * @param pressureKpa measured bubble pressure in kPa
   * @param liquidWater liquid mole fraction of water
   * @param vaporWater measured vapor mole fraction of water
   * @throws Exception if the bubble-point calculation fails
   */
  @ParameterizedTest
  @CsvSource({"98, 65.92, 0.710, 0.992", "98, 81.07, 0.841, 0.997", "110, 71.44, 0.562, 0.978",
      "110, 79.71, 0.613, 0.989", "122, 70.43, 0.386, 0.941", "122, 83.72, 0.467, 0.959"})
  void evaluatesPressureAndMegCarryover(double temperatureC, double pressureKpa, double liquidWater, double vaporWater)
      throws Exception {
    SystemInterface system = new SystemSrkCPAstatoil(temperatureC + 273.15, pressureKpa / 100.0);
    system.addComponent("water", liquidWater);
    system.addComponent("MEG", 1.0 - liquidWater);
    system.setMixingRule(10);
    new ThermodynamicOperations(system).bubblePointPressureFlash();

    double predictedKpa = 100.0 * system.getPressure();
    double predictedVaporMeg = system.getPhase("gas").getComponent("MEG").getx();
    double measuredVaporMeg = 1.0 - vaporWater;
    assertEquals(pressureKpa, predictedKpa, 0.20 * pressureKpa, "Bubble pressure (kPa)");
    assertTrue(predictedVaporMeg > 0.0 && predictedVaporMeg < 1.0 - liquidWater);
    assertEquals(measuredVaporMeg, predictedVaporMeg, 0.012, "MEG vapor mole fraction");
  }
}
