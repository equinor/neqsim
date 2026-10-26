package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks composition-dependent attraction derivatives with synthetic asymmetric parameters.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ClassicTxTemperatureDerivativeTest {
  /**
   * Exercises ternary mixtures, both temperature laws and non-unit phase amounts.
   *
   * @param temperatureType zero for linear T dependence, one for inverse T
   * @param scale total mole-count multiplier
   */
  @ParameterizedTest
  @CsvSource({"0,1", "0,5", "1,1", "1,5"})
  void derivativesMatchAtFixedAmounts(int temperatureType, double scale) {
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    handler.intparam = new double[3][3];
    handler.intparamij = new double[3][3];
    handler.intparamji = new double[3][3];
    handler.intparamT = new double[3][3];
    handler.intparamTType = new int[3][3];
    EosMixingRuleHandler.ClassicSRKT2x rule = handler.new ClassicSRKT2x();
    for (int i = 0; i < 3; i++) {
      for (int j = i + 1; j < 3; j++) {
        // Keep the temperature-only base parameter different from the directional values.
        rule.setBinaryInteractionParameter(i, j, 0.04);
        rule.setBinaryInteractionParameterij(i, j, 0.01 * (i + j + 1));
        rule.setBinaryInteractionParameterji(i, j, 0.12 * (i + j + 1));
        rule.setBinaryInteractionParameterT1(i, j, temperatureType == 0 ? -0.0002 : 30.0);
        handler.intparamTType[i][j] = temperatureType;
        handler.intparamTType[j][i] = temperatureType;
      }
    }
    double temperature = 370;
    double step = 0.01;
    PhaseInterface center = phase(temperature, scale);
    PhaseInterface plus = phase(temperature + step, scale);
    PhaseInterface minus = phase(temperature - step, scale);
    double numerical = (rule.calcA(plus, temperature + step, 40, 3) - rule.calcA(minus, temperature - step, 40, 3))
        / (2 * step);
    double analytical = firstDerivative(rule, center);
    assertEquals(numerical, analytical, Math.abs(numerical) * 1e-7);
    double numericalSecond = (firstDerivative(rule, plus) - firstDerivative(rule, minus)) / (2 * step);
    assertEquals(numericalSecond, rule.calcATT(center, temperature, 40, 3), Math.abs(numericalSecond) * 1e-7);
    for (int i = 0; i < 3; i++) {
      double partial = (rule.calcAi(i, plus, temperature + step, 40, 3)
          - rule.calcAi(i, minus, temperature - step, 40, 3)) / (2 * step);
      assertEquals(partial, rule.calcAiT(i, center, temperature, 40, 3), Math.abs(partial) * 1e-7);
    }
  }

  /**
   * Sums the temperature/composition derivatives using degree-two homogeneity in the amounts.
   *
   * @param rule attraction mixing rule
   * @param phase fixed-composition phase
   * @return extensive attraction temperature derivative
   */
  private double firstDerivative(EosMixingRuleHandler.ClassicSRKT2x rule, PhaseInterface phase) {
    double sum = 0;
    for (int i = 0; i < 3; i++) {
      sum += 0.5 * phase.getComponent(i).getNumberOfMolesInPhase()
          * rule.calcAiT(i, phase, phase.getTemperature(), 40, 3);
    }
    return sum;
  }

  /**
   * Initializes pure-component attraction functions independently of the rule under test.
   *
   * @param temperature temperature in K
   * @param scale phase mole-count multiplier
   * @return initialized gas-root phase
   */
  private PhaseInterface phase(double temperature, double scale) {
    SystemInterface fluid = new SystemSrkEos(temperature, 40);
    fluid.addComponent("methane", 0.6 * scale);
    fluid.addComponent("ethane", 1.1 * scale);
    fluid.addComponent("n-heptane", 0.3 * scale);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.init(3);
    return fluid.getPhase(0);
  }
}
