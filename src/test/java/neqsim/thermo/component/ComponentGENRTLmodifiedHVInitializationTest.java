package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that classic mixing pairs cannot inherit nonrandomness from an adjacent Huron-Vidal pair.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
class ComponentGENRTLmodifiedHVInitializationTest extends neqsim.NeqSimTest {
  /**
   * Zero interaction energies make an HV pair independent of its nonrandomness parameter at every initialization level.
   *
   * @param initType property initialization level
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3})
  void zeroEnergyHvPairsDoNotChangeClassicPairContributions(int initType) {
    SystemInterface fluid = new SystemSrkEos(350.0, 10.0);
    fluid.addComponent("methane", 0.2);
    fluid.addComponent("water", 0.5);
    fluid.addComponent("ethane", 0.3);
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(1);
    PhaseInterface phase = fluid.getPhase(0);
    phase.setInitType(initType);
    String[][] rules = {{"Classic", "HV", "Classic"}, {"HV", "Classic", "Classic"}, {"Classic", "Classic", "Classic"}};
    double[][] zero = new double[3][3];
    double[][] nonrandomness = {{0.0, 0.35, 0.0}, {0.35, 0.0, 0.0}, {0.0, 0.0, 0.0}};
    for (int component = 0; component < 3; component++) {
      ComponentGENRTLmodifiedHV activity = new ComponentGENRTLmodifiedHV(
          phase.getComponent(component).getComponentName(), 1.0, 1.0, component);
      double reference = activity.getGamma(phase, 3, 350.0, 10.0, PhaseType.GAS, zero, zero, zero, zero, rules);
      double actual = activity.getGamma(phase, 3, 350.0, 10.0, PhaseType.GAS, nonrandomness, zero, zero, zero, rules);
      assertEquals(Math.log(reference), Math.log(actual), 1e-13,
          "Zero-energy HV pairs must not alter classical contributions for component " + component);
    }
  }
}
