package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.CubicPureSaturation;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent polynomial/spinodal reference checks for ordinary pure cubic saturation.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CubicPureSaturationTest {
  /**
   * Checks different starting pressures and repeated calculations against independent NumPy roots and bisection.
   *
   * @param pr whether to use PR
   * @param component pure component
   * @param temperature temperature in kelvin
   * @param expected independently calculated cubic saturation pressure in bara
   * @throws Exception if the saturation calculation fails
   */
  @ParameterizedTest
  @CsvSource({"false,hydrogen,20.369,.9596548844353908", "false,CO2,302,70.37442538366142",
      "false,methane,150,10.511359267172828", "false,water,373.15,.9252928152457528",
      "true,hydrogen,20.369,1.0198903075454124", "true,CO2,302,70.30798013148089",
      "true,methane,150,10.471626681115215", "true,water,373.15,.9620601707083997"})
  void retainsDistinctPressureValidRoots(boolean pr, String component, double temperature, double expected)
      throws Exception {
    for (double start : new double[] {0.1, 100.0, expected}) {
      for (int method = 0; method < 3; method++) {
        SystemInterface fluid = pr ? new SystemPrEos(temperature, start) : new SystemSrkEos(temperature, start);
        fluid.addComponent(component, 1.0);
        fluid.setMixingRule(2);
        ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
        if (method == 0) {
          operations.bubblePointPressureFlash();
        } else {
          operations.bubblePointPressureFlash(method == 1);
        }
        fluid.init(3);
        assertEquals(expected, fluid.getPressure(), 5.0e-7);
        byte[] before = org.apache.commons.lang3.SerializationUtils.serialize(fluid);
        assertEquals(expected, CubicPureSaturation.pressure((PhaseEos) fluid.getPhase(0)), 5.0e-7);
        assertArrayEquals(before, org.apache.commons.lang3.SerializationUtils.serialize(fluid));
        double first = fluid.getPhase(0).getMolarVolume();
        double second = fluid.getPhase(1).getMolarVolume();
        assertTrue(Math.abs(first - second) > 1.0e-5 * Math.max(first, second));
        for (int phase = 0; phase < 2; phase++) {
          PhaseEos state = (PhaseEos) fluid.getPhase(phase);
          assertEquals(expected, state.calcPressure(), 5.0e-7);
          assertTrue(state.calcPressuredV() < 0.0, "coexisting roots must be mechanically stable");
        }
        assertEquals(Math.log(fluid.getPhase(0).getComponent(0).getFugacityCoefficient()),
            Math.log(fluid.getPhase(1).getComponent(0).getFugacityCoefficient()), 1.0e-8);
      }
    }
  }

  /** Verifies that the mathematical helper rejects supercritical states and mixtures explicitly. */
  @Test
  void rejectsMissingTwoRootBracketsAndMixtures() {
    SystemInterface fluid = new SystemSrkEos(400.0, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);
    fluid.init(3);
    assertThrows(IllegalStateException.class, () -> CubicPureSaturation.pressure((PhaseEos) fluid.getPhase(0)));
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(3);
    assertThrows(IllegalArgumentException.class, () -> CubicPureSaturation.pressure((PhaseEos) fluid.getPhase(0)));
  }
}
