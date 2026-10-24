package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Differential consistency checks for temperature-dependent modified Huron-Vidal NRTL.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ComponentGENRTLmodifiedHVDerivativeTest extends neqsim.NeqSimTest {
  /** Prescribed interaction energies in K, independent of the fitted database. */
  private static final double[][] ENERGY = {{0.0, 0.0, 650.0, 200.0}, {0.0, 0.0, 1100.0, 450.0},
      {300.0, 1700.0, 0.0, -180.0}, {450.0, 900.0, 270.0, 0.0}};
  /** Prescribed dimensionless temperature coefficients. */
  private static final double[][] SLOPE = {{0.0, 0.0, -0.2, 0.12}, {0.0, 0.0, 0.3, -0.25}, {0.15, -0.35, 0.0, 0.1},
      {-0.1, 0.4, -0.08, 0.0}};
  /** Pair selectors include both hydrocarbon classic pairs and polar HV pairs. */
  private static final String[][] RULE = {{"Classic", "Classic", "HV", "HV"}, {"Classic", "Classic", "HV", "HV"},
      {"HV", "HV", "Classic", "HV"}, {"HV", "HV", "HV", "Classic"}};
  /** Symmetric nonrandomness coefficients. */
  private static final double[][] ALPHA = {{0.0, 0.0, 0.3, 0.25}, {0.0, 0.0, 0.4, 0.3}, {0.3, 0.4, 0.0, 0.2},
      {0.25, 0.3, 0.2, 0.0}};
  /** Symmetric classic binary interaction coefficients. */
  private static final double[][] KIJ = {{0.0, 0.025, 0.0, 0.0}, {0.025, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0},
      {0.0, 0.0, 0.0, 0.0}};
  /** Unequal mole amounts exercise normalization and extensive derivatives. */
  private static final double[] MOLES = {0.7, 0.3, 0.8, 0.2};

  /**
   * Preserves the established first derivative across every component and temperature.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void firstTemperatureDerivativeAgreesWithFiniteDifferences(boolean pengRobinson) {
    PhaseInterface phase = createPhase(pengRobinson);
    for (double temperature : new double[] {280.0, 330.0, 380.0}) {
      double step = 0.01;
      ComponentGENRTLmodifiedHV[] center = evaluate(phase, temperature, MOLES, 3);
      ComponentGENRTLmodifiedHV[] plus = evaluate(phase, temperature + step, MOLES, 1);
      ComponentGENRTLmodifiedHV[] minus = evaluate(phase, temperature - step, MOLES, 1);
      for (int i = 0; i < MOLES.length; i++) {
        double first = (plus[i].getLnGamma() - minus[i].getLnGamma()) / (2.0 * step);
        assertEquals(first, center[i].getLnGammadt(), 2e-9, "first T derivative, component " + i);
      }
    }
  }

  /**
   * Tests both analytical temperature derivatives without differencing a second time numerically.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void temperatureDerivativesAgreeWithFiniteDifferences(boolean pengRobinson) {
    PhaseInterface phase = createPhase(pengRobinson);
    for (double temperature : new double[] {280.0, 330.0, 380.0}) {
      double step = 0.01;
      ComponentGENRTLmodifiedHV[] center = evaluate(phase, temperature, MOLES, 3);
      ComponentGENRTLmodifiedHV[] plus = evaluate(phase, temperature + step, MOLES, 3);
      ComponentGENRTLmodifiedHV[] minus = evaluate(phase, temperature - step, MOLES, 3);
      for (int i = 0; i < MOLES.length; i++) {
        double first = (plus[i].getLnGamma() - minus[i].getLnGamma()) / (2.0 * step);
        double second = (plus[i].getLnGammadt() - minus[i].getLnGammadt()) / (2.0 * step);
        assertEquals(first, center[i].getLnGammadt(), 2e-9, "first T derivative, component " + i);
        assertEquals(second, center[i].getLnGammadtdt(), 2e-10, "second T derivative, component " + i);
      }
    }
  }

  /**
   * Verifies the Hessian of the same excess Gibbs energy used for activity coefficients.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void compositionDerivativesSatisfyFiniteDifferencesAndGibbsDuhem(boolean pengRobinson) {
    PhaseInterface phase = createPhase(pengRobinson);
    ComponentGENRTLmodifiedHV[] center = evaluate(phase, 330.0, MOLES, 3);
    double step = 1e-5;
    for (int j = 0; j < MOLES.length; j++) {
      double[] plusMoles = MOLES.clone();
      double[] minusMoles = MOLES.clone();
      plusMoles[j] += step;
      minusMoles[j] -= step;
      ComponentGENRTLmodifiedHV[] plus = evaluate(phase, 330.0, plusMoles, 3);
      ComponentGENRTLmodifiedHV[] minus = evaluate(phase, 330.0, minusMoles, 3);
      double gibbsDuhem = 0.0;
      double euler = 0.0;
      for (int i = 0; i < MOLES.length; i++) {
        double finiteDifference = (plus[i].getLnGamma() - minus[i].getLnGamma()) / (2.0 * step);
        assertEquals(finiteDifference, center[i].getLnGammadn(j), 2e-8, "composition derivative " + i + ", " + j);
        assertEquals(center[j].getLnGammadn(i), center[i].getLnGammadn(j), 2e-12, "excess Gibbs Hessian symmetry");
        gibbsDuhem += MOLES[i] * center[i].getLnGammadn(j);
        euler += MOLES[i] * center[j].getLnGammadn(i);
      }
      assertEquals(0.0, gibbsDuhem, 3e-12, "Gibbs-Duhem weighted column");
      assertEquals(0.0, euler, 3e-12, "homogeneity weighted row");
    }
  }

  /**
   * A component permutation with identically permuted parameter matrices leaves every derivative unchanged.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void componentOrderDoesNotChangeTheModel(boolean pengRobinson) {
    PhaseInterface original = createPhase(pengRobinson);
    ComponentGENRTLmodifiedHV[] reference = evaluate(original, 330.0, MOLES, 3);
    int count = MOLES.length;
    double[][] energy = new double[count][count];
    double[][] slope = new double[count][count];
    double[][] alpha = new double[count][count];
    double[][] kij = new double[count][count];
    String[][] rules = new String[count][count];
    SystemInterface fluid = pengRobinson ? new SystemPrEos(330.0, 30.0) : new SystemSrkEos(330.0, 30.0);
    for (int i = 0; i < count; i++) {
      int oi = count - 1 - i;
      fluid.addComponent(original.getComponent(oi).getName(), MOLES[oi]);
      for (int j = 0; j < count; j++) {
        int oj = count - 1 - j;
        energy[i][j] = ENERGY[oi][oj];
        slope[i][j] = SLOPE[oi][oj];
        alpha[i][j] = ALPHA[oi][oj];
        kij[i][j] = KIJ[oi][oj];
        rules[i][j] = RULE[oi][oj];
      }
    }
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(1);
    PhaseInterface reversed = fluid.getPhase(0);
    reversed.setInitType(3);
    for (int i = 0; i < count; i++) {
      int oi = count - 1 - i;
      ComponentGENRTLmodifiedHV actual = new ComponentGENRTLmodifiedHV(reversed.getComponent(i).getName(), MOLES[oi],
          MOLES[oi], i);
      actual.getGamma(reversed, count, 330.0, 30.0, PhaseType.GAS, alpha, energy, slope, kij, rules);
      assertEquals(reference[oi].getLnGamma(), actual.getLnGamma(), 2e-12);
      assertEquals(reference[oi].getLnGammadt(), actual.getLnGammadt(), 2e-13);
      assertEquals(reference[oi].getLnGammadtdt(), actual.getLnGammadtdt(), 2e-14);
      for (int j = 0; j < count; j++) {
        assertEquals(reference[oi].getLnGammadn(count - 1 - j), actual.getLnGammadn(j), 2e-12);
      }
    }
  }

  /**
   * Re-evaluation at lower initialization levels must not publish a previous second derivative.
   *
   * @param pengRobinson whether to use PR instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void initializationLevelsPublishTheSameGammaAndClearUnrequestedDerivatives(boolean pengRobinson) {
    PhaseInterface phase = createPhase(pengRobinson);
    ComponentGENRTLmodifiedHV[] derivatives = evaluate(phase, 330.0, MOLES, 3);
    for (int level : new int[] {1, 2, 3, 1}) {
      phase.setInitType(level);
      for (int i = 0; i < MOLES.length; i++) {
        ComponentGENRTLmodifiedHV activity = derivatives[i];
        double expectedGamma = activity.getGamma();
        activity.getGamma(phase, MOLES.length, 330.0, 30.0, PhaseType.GAS, ALPHA, ENERGY, SLOPE, KIJ, RULE);
        assertEquals(expectedGamma, activity.getGamma(), Math.abs(expectedGamma) * 1e-13);
        if (level == 1) {
          assertEquals(0.0, activity.getLnGammadt(), 0.0);
          assertEquals(0.0, activity.getLnGammadtdt(), 0.0);
        }
      }
    }
  }

  /**
   * Creates a forced single phase; these tests qualify the model kernel rather than flash selection.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @return initialized EOS phase
   */
  private static PhaseInterface createPhase(boolean pengRobinson) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(330.0, 30.0) : new SystemSrkEos(330.0, 30.0);
    String[] names = {"methane", "n-decane", "water", "MEG"};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], MOLES[i]);
    }
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(1);
    return fluid.getPhase(0);
  }

  /**
   * Evaluates independent component objects at normalized composition and the requested derivative order.
   *
   * @param phase EOS phase supplying pure-component parameters
   * @param temperature evaluation temperature in K
   * @param moles component mole amounts
   * @param initType derivative initialization level
   * @return activity coefficients and their analytical derivatives
   */
  private static ComponentGENRTLmodifiedHV[] evaluate(PhaseInterface phase, double temperature, double[] moles,
      int initType) {
    double total = 0.0;
    for (double amount : moles) {
      total += amount;
    }
    for (int i = 0; i < moles.length; i++) {
      phase.getComponent(i).setx(moles[i] / total);
      phase.getComponent(i).setNumberOfMolesInPhase(total);
    }
    phase.setInitType(initType);
    ComponentGENRTLmodifiedHV[] result = new ComponentGENRTLmodifiedHV[moles.length];
    for (int i = 0; i < moles.length; i++) {
      result[i] = new ComponentGENRTLmodifiedHV(phase.getComponent(i).getName(), moles[i], moles[i], i);
      result[i].getGamma(phase, moles.length, temperature, 30.0, PhaseType.GAS, ALPHA, ENERGY, SLOPE, KIJ, RULE);
    }
    return result;
  }
}
