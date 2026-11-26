package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks the analytic equilibrium-stage derivatives against central finite differences of an independent stage
 * evaluation.
 *
 * @author NeqSim / AI-assisted
 * @version 1.0
 */
class MeshStageDerivativesTest extends neqsim.NeqSimTest {
  /** Stage pressure in bara. */
  private static final double PRESSURE = 10.0;

  /** Stage temperature in K, chosen inside the two-phase region of the test mixture. */
  private double stageTemperature;

  /** Total vapor flow of the stage. */
  private static final double VAPOR_FLOW = 40.0;

  /** Reference fluid that supplies the thermodynamic model. */
  private SystemInterface reference;

  /** Number of components. */
  private int componentCount;

  /** Equilibrium liquid component flows of the stage. */
  private double[] liquidFlows;

  /** Converged K-values at the base state. */
  private double[] baseKValues;

  /** Result holder of one independent stage evaluation. */
  private static final class StageValues {
    /** Vapor component flows. */
    private double[] vaporFlows;
    /** Summation function sum(K x) - 1. */
    private double summation;
    /** Liquid enthalpy flow L hL. */
    private double liquidEnthalpyFlow;
    /** Vapor enthalpy flow V hV. */
    private double vaporEnthalpyFlow;
    /** Molar liquid enthalpy. */
    private double liquidMolarEnthalpy;
    /** Molar vapor enthalpy. */
    private double vaporMolarEnthalpy;
    /** Converged K-values. */
    private double[] kValues;
  }

  /**
   * Build a two-phase propane, n-butane and n-pentane stage from a flash.
   */
  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(330.0, PRESSURE);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.20);
    fluid.setMixingRule("classic");
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    for (stageTemperature = 330.0; stageTemperature < 400.0; stageTemperature += 2.0) {
      fluid.setTemperature(stageTemperature);
      operations.TPflash();
      if (fluid.getNumberOfPhases() == 2 && fluid.getBeta() > 0.2 && fluid.getBeta() < 0.8) {
        break;
      }
    }
    assertEquals(2, fluid.getNumberOfPhases(), "The test stage must be two-phase");
    reference = fluid;
    componentCount = fluid.getPhase(0).getNumberOfComponents();
    int liquidPhase = fluid.getPhase(0).getType() == PhaseType.GAS ? 1 : 0;
    liquidFlows = new double[componentCount];
    baseKValues = new double[componentCount];
    for (int i = 0; i < componentCount; i++) {
      double x = fluid.getPhase(liquidPhase).getComponent(i).getx();
      double y = fluid.getPhase(1 - liquidPhase).getComponent(i).getx();
      liquidFlows[i] = 60.0 * x;
      baseKValues[i] = y / x;
    }
  }

  /**
   * Evaluate one forced-root phase holding one mole.
   *
   * @param composition normalized composition
   * @param temperature temperature in K
   * @param vapor true for the gas root
   * @param level thermodynamic initialization level, or zero to leave derivative initialization to the caller
   * @return evaluated one-phase system
   */
  private SystemInterface evaluatePhase(double[] composition, double temperature, boolean vapor, int level) {
    SystemInterface system = reference.cloneThermoOnly();
    system.setTemperature(temperature);
    system.setPressure(PRESSURE);
    system.setTotalNumberOfMoles(1.0);
    system.setMolarComposition(composition);
    system.init(0);
    system.setNumberOfPhases(1);
    system.setPhaseType(0, vapor ? PhaseType.GAS : PhaseType.LIQUID);
    system.setForcePhaseTypes(true);
    if (level > 0) {
      system.init(level);
    }
    return system;
  }

  /**
   * Evaluate the stage functions by converging the K-value fixed point without using derivatives.
   *
   * @param flows liquid component flows
   * @param temperature stage temperature in K
   * @param vaporFlow total vapor flow
   * @return stage function values
   */
  private StageValues evaluateStage(double[] flows, double temperature, double vaporFlow) {
    double liquidFlow = 0.0;
    for (double flow : flows) {
      liquidFlow += flow;
    }
    double[] x = new double[componentCount];
    for (int i = 0; i < componentCount; i++) {
      x[i] = flows[i] / liquidFlow;
    }
    SystemInterface liquid = evaluatePhase(x, temperature, false, 2);
    double[] k = baseKValues.clone();
    double[] y = new double[componentCount];
    double sumKx = 0.0;
    for (int sweep = 0; sweep < 500; sweep++) {
      sumKx = 0.0;
      for (int i = 0; i < componentCount; i++) {
        sumKx += k[i] * x[i];
      }
      for (int i = 0; i < componentCount; i++) {
        y[i] = k[i] * x[i] / sumKx;
      }
      SystemInterface vapor = evaluatePhase(y, temperature, true, 1);
      double change = 0.0;
      for (int i = 0; i < componentCount; i++) {
        double updated = liquid.getPhase(0).getComponent(i).getFugacityCoefficient()
            / vapor.getPhase(0).getComponent(i).getFugacityCoefficient();
        change = Math.max(change, Math.abs(Math.log(updated / k[i])));
        k[i] = updated;
      }
      if (change < 1.0e-14) {
        break;
      }
    }
    sumKx = 0.0;
    for (int i = 0; i < componentCount; i++) {
      sumKx += k[i] * x[i];
    }
    for (int i = 0; i < componentCount; i++) {
      y[i] = k[i] * x[i] / sumKx;
    }
    SystemInterface vapor = evaluatePhase(y, temperature, true, 2);
    StageValues values = new StageValues();
    values.kValues = k;
    values.vaporFlows = new double[componentCount];
    for (int i = 0; i < componentCount; i++) {
      values.vaporFlows[i] = k[i] * x[i] * vaporFlow;
    }
    values.summation = sumKx - 1.0;
    values.liquidMolarEnthalpy = liquid.getPhase(0).getEnthalpy() / liquid.getPhase(0).getNumberOfMolesInPhase();
    values.vaporMolarEnthalpy = vapor.getPhase(0).getEnthalpy() / vapor.getPhase(0).getNumberOfMolesInPhase();
    values.liquidEnthalpyFlow = liquidFlow * values.liquidMolarEnthalpy;
    values.vaporEnthalpyFlow = vaporFlow * values.vaporMolarEnthalpy;
    return values;
  }

  /**
   * Evaluate the stage after perturbing one stage variable.
   *
   * @param variable stage variable index: component flows, then temperature, then vapor flow
   * @param step signed perturbation
   * @return stage function values at the perturbed state
   */
  private StageValues evaluatePerturbed(int variable, double step) {
    double[] flows = liquidFlows.clone();
    double temperature = stageTemperature;
    double vaporFlow = VAPOR_FLOW;
    if (variable < componentCount) {
      flows[variable] += step;
    } else if (variable == componentCount) {
      temperature += step;
    } else {
      vaporFlow += step;
    }
    return evaluateStage(flows, temperature, vaporFlow);
  }

  /**
   * Assert that an analytic derivative agrees with its finite-difference estimate.
   *
   * @param label quantity description
   * @param expected finite-difference estimate
   * @param actual analytic derivative
   */
  private static void assertDerivative(String label, double expected, double actual) {
    assertEquals(expected, actual, 2.0e-5 * Math.abs(expected) + 1.0e-6, label);
  }

  /**
   * Every analytic stage derivative must match a central finite difference of the independent evaluation.
   */
  @Test
  void analyticStageDerivativesMatchFiniteDifferences() {
    StageValues base = evaluateStage(liquidFlows, stageTemperature, VAPOR_FLOW);
    double liquidFlow = 0.0;
    for (double flow : liquidFlows) {
      liquidFlow += flow;
    }
    double[] x = new double[componentCount];
    double[] y = new double[componentCount];
    double sumKx = 0.0;
    for (int i = 0; i < componentCount; i++) {
      x[i] = liquidFlows[i] / liquidFlow;
      sumKx += base.kValues[i] * x[i];
    }
    for (int i = 0; i < componentCount; i++) {
      y[i] = base.kValues[i] * x[i] / sumKx;
    }

    MeshStageDerivatives derivatives = new MeshStageDerivatives(componentCount);
    assertTrue(derivatives.loadPhase(evaluatePhase(x, stageTemperature, false, 0), false));
    assertTrue(derivatives.loadPhase(evaluatePhase(y, stageTemperature, true, 0), true));
    assertTrue(derivatives.compute(liquidFlows, VAPOR_FLOW, base.kValues));

    for (int variable = 0; variable < componentCount + 2; variable++) {
      double step = variable == componentCount ? 1.0e-3 : 1.0e-4;
      StageValues plus = evaluatePerturbed(variable, step);
      StageValues minus = evaluatePerturbed(variable, -step);
      for (int i = 0; i < componentCount; i++) {
        assertDerivative("vapor flow " + i + " by variable " + variable,
            (plus.vaporFlows[i] - minus.vaporFlows[i]) / (2.0 * step), derivatives.getVaporFlowDerivative(i, variable));
      }
      assertDerivative("summation by variable " + variable, (plus.summation - minus.summation) / (2.0 * step),
          derivatives.getSummationDerivative(variable));
      assertDerivative("liquid enthalpy flow by variable " + variable,
          (plus.liquidEnthalpyFlow - minus.liquidEnthalpyFlow) / (2.0 * step),
          derivatives.getLiquidEnthalpyFlowDerivative(variable));
      assertDerivative("vapor enthalpy flow by variable " + variable,
          (plus.vaporEnthalpyFlow - minus.vaporEnthalpyFlow) / (2.0 * step),
          derivatives.getVaporEnthalpyFlowDerivative(variable));
      assertDerivative("liquid molar enthalpy by variable " + variable,
          (plus.liquidMolarEnthalpy - minus.liquidMolarEnthalpy) / (2.0 * step),
          derivatives.getLiquidMolarEnthalpyDerivative(variable));
      assertDerivative("vapor molar enthalpy by variable " + variable,
          (plus.vaporMolarEnthalpy - minus.vaporMolarEnthalpy) / (2.0 * step),
          derivatives.getVaporMolarEnthalpyDerivative(variable));
    }
  }

  /**
   * A model missing either derivative and a non-finite stage temperature must trigger finite differences.
   */
  @Test
  void unavailableDerivativesAndInvalidTemperatureAreRejected() {
    double[] composition = {0.3, 0.4, 0.3};
    MeshStageDerivatives derivatives = new MeshStageDerivatives(componentCount);
    SystemInterface system = evaluatePhase(composition, stageTemperature, false, 0);
    system.setImplementedCompositionDeriativesofFugacity(false);
    assertFalse(derivatives.loadPhase(system, false));
    system.setImplementedCompositionDeriativesofFugacity(true);
    system.setImplementedTemperatureDeriativesofFugacity(false);
    assertFalse(derivatives.loadPhase(system, false));
    system.setImplementedTemperatureDeriativesofFugacity(true);
    system.setTemperature(Double.NaN);
    assertFalse(derivatives.loadPhase(system, false));
  }
}
