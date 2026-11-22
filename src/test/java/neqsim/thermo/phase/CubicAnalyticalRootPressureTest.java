package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Physical pressure/root closure for the cubic fallback, including cancellation-prone dilute pressures.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CubicAnalyticalRootPressureTest {
  /**
   * Checks requested cubic roots at three inventory scales without relying on a flash phase-count label.
   *
   * @param pr whether to use PR
   * @param component pure component
   * @param temperature temperature in kelvin
   * @throws Exception if the cubic fallback fails
   */
  @ParameterizedTest
  @CsvSource({"false,water,373.15", "false,CO2,280", "false,methane,150", "false,nC16,350", "true,water,373.15",
      "true,CO2,280", "true,methane,150", "true,nC16,350"})
  void selectedRootsSatisfyPressureAndExcludedVolume(boolean pr, String component, double temperature)
      throws Exception {
    for (double amount : new double[] {1.0e-6, 1.0, 1000.0}) {
      for (double pressure : new double[] {1.0e-10, 1.0e-8, 1.0e-6, 1.0e-4, 0.01, 1.0, 50.0, 200.0}) {
        SystemInterface fluid = pr ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
        fluid.addComponent(component, amount);
        fluid.setMixingRule(2);
        fluid.init(3);
        PhaseEos phase = (PhaseEos) fluid.getPhase(0);
        double gasVolume = phase.molarVolumeAnalytical(pressure, temperature, PhaseType.GAS);
        assertPhysicalRoot(phase, pressure);
        double liquidVolume = phase.molarVolumeAnalytical(pressure, temperature, PhaseType.LIQUID);
        assertPhysicalRoot(phase, pressure);
        assertTrue(liquidVolume <= gasVolume, "requested roots must retain their volume order");
        for (PhaseType requested : new PhaseType[] {PhaseType.GAS, PhaseType.LIQUID}) {
          fluid.setForceSinglePhase(requested);
          fluid.init(3);
          assertPhysicalRoot((PhaseEos) fluid.getPhase(0), pressure);
        }
        if (!"CO2".equals(component) && pressure <= 0.01) {
          assertTrue(gasVolume > 1000.0 * liquidVolume,
              "the low-pressure metastable liquid root must not disappear through cancellation");
        }
      }
    }
  }

  /**
   * Checks pressure and mechanical stability independently of the cubic polynomial.
   *
   * @param phase phase whose volume was just set by the fallback
   * @param pressure requested pressure in bara
   */
  private static void assertPhysicalRoot(PhaseEos phase, double pressure) {
    assertTrue(Double.isFinite(phase.getMolarVolume()) && phase.getMolarVolume() > phase.getb());
    assertEquals(pressure, phase.calcPressure(), 1.0e-8 * Math.max(1.0, pressure));
    assertTrue(phase.calcPressuredV() < 0.0, "the selected endpoint root must be mechanically stable");
  }
}
