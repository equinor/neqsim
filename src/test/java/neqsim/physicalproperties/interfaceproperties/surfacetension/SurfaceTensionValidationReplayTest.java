package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Ensures repeated correlation calls validate both current bulk phases after an earlier successful calculation.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class SurfaceTensionValidationReplayTest {
  /**
   * Supplies both EOS models, correlations and sides of every two- and three-phase interface.
   *
   * @return model, topology, interface and corrupted-side combinations
   */
  private static Stream<Arguments> cases() {
    List<Arguments> cases = new ArrayList<Arguments>();
    for (boolean pr : new boolean[] {false, true}) {
      for (boolean three : new boolean[] {false, true}) {
        int phases = three ? 3 : 2;
        for (boolean parachor : new boolean[] {false, true}) {
          for (int first = 0; first < phases; first++) {
            for (int second = first + 1; second < phases; second++) {
              cases.add(Arguments.of(pr, three, parachor, first, second, first));
              cases.add(Arguments.of(pr, three, parachor, first, second, second));
            }
          }
        }
      }
    }
    return cases.stream();
  }

  /**
   * Revalidates current state on every call and recovers the same result when the valid state is restored.
   *
   * @param pr whether to use PR instead of SRK
   * @param three whether the feed has gas, oil and aqueous phases
   * @param parachor whether to use Parachor instead of Firozabadi-Ramley
   * @param first first interface phase
   * @param second second interface phase
   * @param changed phase to corrupt after the first successful evaluation
   */
  @ParameterizedTest
  @MethodSource("cases")
  void successfulCallDoesNotBypassLaterValidation(boolean pr, boolean three, boolean parachor, int first, int second,
      int changed) {
    SystemInterface fluid = pr ? new SystemPrEos(300.0, 10.0) : new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent(three ? "n-heptane" : "n-decane", 0.3);
    if (three) {
      fluid.addComponent("water", 0.2);
    }
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(three);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(three ? 3 : 2, fluid.getNumberOfPhases());
    SurfaceTension model = parachor ? new ParachorSurfaceTension(fluid) : new FirozabadiRamleyInterfaceTension(fluid);
    double expected = model.calcSurfaceTension(first, second);
    PhaseInterface phase = fluid.getPhase(changed);
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    double volume = phase.getMolarVolume();
    double fraction = phase.getComponent(0).getx();
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0}) {
      phase.setTemperature(invalid);
      assertThrows(IllegalStateException.class, () -> model.calcSurfaceTension(first, second));
      phase.setTemperature(temperature);
      phase.setPressure(invalid);
      assertThrows(IllegalStateException.class, () -> model.calcSurfaceTension(first, second));
      phase.setPressure(pressure);
      phase.setMolarVolume(invalid);
      assertThrows(IllegalStateException.class, () -> model.calcSurfaceTension(first, second));
      phase.setMolarVolume(volume);
      assertEquals(expected, model.calcSurfaceTension(first, second), 1e-12);
    }
    for (double invalid : new double[] {fraction + 0.01, fraction + 0.1}) {
      phase.getComponent(0).setx(invalid);
      assertThrows(IllegalStateException.class, () -> model.calcSurfaceTension(first, second));
      phase.getComponent(0).setx(fraction);
      assertEquals(expected, model.calcSurfaceTension(first, second), 1e-12);
    }
  }
}
