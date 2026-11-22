package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * End-to-end cubic HV derivative qualification with prescribed interactions independent of fitted database values.
 *
 * <p>
 * These fixed-root states test the EOS wiring and derivative identities, not stability or experimental accuracy.
 * Non-unit amounts and mixed classical/HV pairs exercise extensive derivatives and generated classical parameters.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalModelConsistencyTest extends neqsim.NeqSimTest {
  /** Prescribed directed modified-NRTL coefficients in kelvin. */
  private static final double[][] ENERGY = {{0.0, 0.0, 650.0, 200.0}, {0.0, 0.0, 1100.0, 450.0},
      {300.0, 1700.0, 0.0, -180.0}, {450.0, 900.0, 270.0, 0.0}};
  /** Prescribed directed dimensionless temperature offsets. */
  private static final double[][] OFFSET = {{0.0, 0.0, -0.2, 0.12}, {0.0, 0.0, 0.3, -0.25}, {0.15, -0.35, 0.0, 0.1},
      {-0.1, 0.4, -0.08, 0.0}};
  /** Symmetric nonrandomness matrix. */
  private static final double[][] ALPHA = {{0.0, 0.0, 0.3, 0.25}, {0.0, 0.0, 0.4, 0.3}, {0.3, 0.4, 0.0, 0.2},
      {0.25, 0.3, 0.2, 0.0}};

  /**
   * Checks every exposed fugacity derivative and the attraction derivatives consumed by caloric properties.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param mixingRule numeric HV mixing rule
   * @param phaseType requested cubic root
   */
  @ParameterizedTest
  @CsvSource({"false,3,GAS", "false,4,GAS", "true,3,GAS", "true,4,GAS", "false,3,LIQUID", "false,4,LIQUID",
      "true,3,LIQUID", "true,4,LIQUID"})
  void prescribedInteractionsSatisfyModelIdentitiesAndNumericalDerivatives(boolean pengRobinson, int mixingRule,
      PhaseType phaseType) {
    SystemInterface fluid = fluid(pengRobinson, mixingRule, phaseType, 330.0);
    for (double temperature : new double[] {280.0, 330.0, 380.0, 330.0}) {
      fluid.setTemperature(temperature);
      fluid.init(1);
      fluid.init(3);
      PhaseInterface state = fluid.getPhase(0);
      assertEquals(fluid.getPressure(), ((PhaseEos) state).calcPressure(), 1.0e-8,
          "the retained volume must satisfy the EOS pressure equation at T=" + temperature);
      ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
      checker.setMaxError(1.0e-8);
      assertTrue(checker.checkFugacityCoefficients(), "residual Gibbs energy identity");
      assertTrue(checker.checkFugacityCoefficientsDT(), "temperature/enthalpy identity");
      assertTrue(checker.checkFugacityCoefficientsDP(), "pressure/volume identity");
      assertTrue(checker.checkFugacityCoefficientsDn(), "Gibbs-Duhem identity");
      assertTrue(checker.checkFugacityCoefficientsDn2(), "composition Hessian symmetry");
      checker.setMaxError(1.0e-6);
      byte[] before = SerializationUtils.serialize(fluid);
      assertTrue(checker.checkNumerically(), "d(ln phi)/dT, dP and dn with independent refined differences");
      assertArrayEquals(before, SerializationUtils.serialize(fluid), "diagnostics must preserve the source state");

      double step = 0.01;
      PhaseInterface plus = fluid(pengRobinson, mixingRule, phaseType, temperature + step).getPhase(0);
      PhaseInterface minus = fluid(pengRobinson, mixingRule, phaseType, temperature - step).getPhase(0);
      assertClose(state.getAT(), (plus.getA() - minus.getA()) / (2.0 * step), "dA/dT");
      assertClose(state.getATT(), (plus.getAT() - minus.getAT()) / (2.0 * step), "d2A/dT2");
      for (int component = 0; component < state.getNumberOfComponents(); component++) {
        ComponentEosInterface center = (ComponentEosInterface) state.getComponent(component);
        double plusAi = ((ComponentEosInterface) plus.getComponent(component)).getAi();
        double minusAi = ((ComponentEosInterface) minus.getComponent(component)).getAi();
        assertClose(center.getAiT(), (plusAi - minusAi) / (2.0 * step), "d2A/dTdn for component " + component);
      }
    }
  }

  /**
   * Builds a fixed-root mixture with distinct directed coefficients and one generated classical hydrocarbon pair.
   *
   * @param pengRobinson whether to use PR instead of SRK
   * @param mixingRule numeric HV mixing rule
   * @param phaseType requested cubic root
   * @param temperature temperature in kelvin
   * @return independent system containing two moles at 20 bara
   */
  private static SystemInterface fluid(boolean pengRobinson, int mixingRule, PhaseType phaseType, double temperature) {
    SystemInterface fluid = pengRobinson ? new SystemPrEos(temperature, 20.0) : new SystemSrkEos(temperature, 20.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.3);
    fluid.addComponent("methanol", 0.8);
    fluid.addComponent("water", 0.2);
    fluid.setMixingRule(mixingRule);
    for (PhaseInterface phase : fluid.getPhases()) {
      if (phase == null) {
        continue;
      }
      HVMixingRulesInterface rule = (HVMixingRulesInterface) phase.getMixingRule();
      rule.setBinaryInteractionParameter(0, 1, 0.025);
      for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
          rule.setClassicOrHV(i, j, i == j || (i < 2 && j < 2) ? "Classic" : "HV");
          rule.setHVDijParameter(i, j, ENERGY[i][j]);
          rule.setHVDijTParameter(i, j, OFFSET[i][j]);
          rule.setHValphaParameter(i, j, ALPHA[i][j]);
        }
      }
    }
    fluid.setForceSinglePhase(phaseType);
    fluid.init(3);
    assertEquals(1, fluid.getNumberOfPhases());
    return fluid;
  }

  /**
   * Compares finite extensive derivatives with a scale-aware tolerance for central temperature differences.
   *
   * @param analytic analytic derivative
   * @param numerical independent numerical derivative
   * @param label derivative being checked
   */
  private static void assertClose(double analytic, double numerical, String label) {
    assertTrue(Double.isFinite(analytic) && Double.isFinite(numerical), label);
    assertEquals(analytic, numerical, 2.0e-7 * Math.max(1.0, Math.abs(analytic)), label);
  }
}
