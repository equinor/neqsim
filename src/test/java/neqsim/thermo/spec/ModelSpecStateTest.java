package neqsim.thermo.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentGEUnifac;
import neqsim.thermo.component.ComponentGEWilson;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUNIFAC;
import neqsim.thermo.system.SystemUNIFACpsrk;

/** Nearby-state checks complement fixed anchors; all comparisons drive production APIs. */
class ModelSpecStateTest extends neqsim.NeqSimTest {
  @Test
  void wilsonRefreshesStoredValueAfterCompositionChanges() {
    SystemGEWilson system = new SystemGEWilson(298.15, 1.0);
    system.addComponent("methanol", 0.5);
    system.addComponent("water", 0.5);
    system.setMixingRule("classic");
    system.init(0);
    PhaseInterface liquid = system.getPhase(1);
    for (int i = 0; i < 2; i++) {
      liquid.getcomponentArray()[i] = new ComponentGEWilson(i == 0 ? "methanol" : "water", 0.5, 0.5, i) {
        private static final long serialVersionUID = 1L;

        @Override
        public double getCharEnergyParamter(PhaseInterface phase, int first, int second) {
          return first == second ? 1.0 : first == 0 ? 2.0 : 0.5;
        }
      };
      liquid.getComponent(i).setx(0.5);
    }
    ComponentGEWilson methanol = (ComponentGEWilson) liquid.getComponent(0);
    double first = methanol.getGamma(liquid, 2, 298.15, 1.0, PhaseType.LIQUID);
    assertEquals(Math.exp(1.0 / 3.0) / 1.5, first, 1e-12);
    liquid.getComponent(0).setx(0.2);
    liquid.getComponent(1).setx(0.8);
    double second = methanol.getGamma(liquid, 2, 298.15, 1.0, PhaseType.LIQUID);
    assertEquals(Math.exp(4.0 / 9.0) / 1.8, second, 1e-12);
    assertEquals(second, methanol.getGamma(), 1e-12);
    assertEquals(Math.log(second), methanol.getLnGamma(), 1e-12);
    assertNotEquals(first, second);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  void binaryGroupModelsAreOrderIndependentAndReinitializable(boolean psrk) {
    SystemInterface ordered = groupSystem(psrk, false);
    SystemInterface reversed = groupSystem(psrk, true);
    for (double temperature : new double[] { 290.0, 310.0, 290.0 }) {
      ordered.setTemperature(temperature);
      reversed.setTemperature(temperature);
      ordered.init(0);
      reversed.init(0);
      for (String component : new String[] { "methanol", "water" }) {
        double first = gamma(ordered, component);
        double second = gamma(reversed, component);
        ModelSpecFixtures.positive(first, component);
        assertEquals(first, second, 1e-10, component);
      }
    }
  }

  private static SystemInterface groupSystem(boolean psrk, boolean reverse) {
    SystemInterface system = psrk ? new SystemUNIFACpsrk(290.0, 1.0) : new SystemUNIFAC(290.0, 1.0);
    system.addComponent(reverse ? "water" : "methanol", reverse ? 0.7 : 0.3);
    system.addComponent(reverse ? "methanol" : "water", reverse ? 0.3 : 0.7);
    system.setMixingRule("classic");
    system.init(0);
    return system;
  }

  private static double gamma(SystemInterface system, String name) {
    PhaseInterface phase = system.getPhase(1);
    ComponentGEUnifac component = (ComponentGEUnifac) phase.getComponent(name);
    assertTrue(component.getUnifacGroups().length > 0);
    return component.getGamma(phase, 2, system.getTemperature(), system.getPressure(), phase.getType());
  }
}
