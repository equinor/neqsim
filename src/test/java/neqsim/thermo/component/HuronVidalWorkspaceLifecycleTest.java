package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks modified-NRTL workspace lifetime against independently constructed evaluations.
 *
 * <p>
 * Prescribed coefficients exercise mixed HV/classical pairs, not experimental calibration.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalWorkspaceLifecycleTest extends neqsim.NeqSimTest {
  /**
   * Writes exact hexadecimal activity/derivative fingerprints using either compiled main-class variant.
   *
   * @param args output CSV path
   * @throws IOException if the output cannot be written
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Supply an output CSV path");
    }
    try (PrintWriter out = new PrintWriter(args[0], "UTF-8")) {
      out.println("evaluation,pr,size,init,state,quantity,value_hex");
      int evaluation = 0;
      for (boolean pr : new boolean[] {false, true}) {
        ComponentGENRTLmodifiedHV retained = component();
        for (int size : new int[] {4, 1, 2, 4}) {
          for (int level : new int[] {3, 1, 2, 3}) {
            for (int state = 0; state < 7; state++) {
              double[] values = evaluate(retained, pr, size, level, state);
              for (int quantity = 0; quantity < values.length; quantity++) {
                out.printf("%d,%s,%d,%d,%d,%d,%s%n", evaluation, pr, size, level, state, quantity,
                    Double.toHexString(values[quantity]));
              }
              evaluation++;
            }
          }
        }
      }
      if (out.checkError()) {
        throw new IOException("Could not write fingerprint");
      }
    }
  }

  /**
   * Changes component count, temperature, composition and derivative level on the same evaluator.
   *
   * @param pr whether to use the PR pure-component convention
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reusedEvaluatorMatchesFreshPureTraceAndMixedStates(boolean pr) {
    ComponentGENRTLmodifiedHV retained = component();
    int[] sizes = {4, 2, 1, 4, 4, 2, 4, 4, 4};
    int[] levels = {3, 1, 2, 3, 1, 3, 2, 3, 2};
    for (int state = 0; state < sizes.length; state++) {
      double[] expected = evaluate(component(), pr, sizes[state], levels[state], state);
      double[] actual = evaluate(retained, pr, sizes[state], levels[state], state);
      assertArrayEquals(expected, actual, 0.0, "History must not change gamma or any exposed derivative");
      if (levels[state] != 3) {
        for (int i = 4; i < actual.length; i++) {
          assertEquals(0.0, actual[i], 0.0, "Lower levels must clear composition derivatives");
        }
      }
      retained.setLnGammadn(0, Double.NaN);
    }
  }

  /**
   * Evaluating a cloned or deserialized component must not overwrite retained source derivatives.
   *
   * @param pr whether to use PR
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void copiesKeepIndependentDerivativeStorage(boolean pr) {
    ComponentGENRTLmodifiedHV source = component();
    double[] expected = evaluate(source, pr, 4, 3, 0);
    ComponentGENRTLmodifiedHV cloned = source.clone();
    ComponentGENRTLmodifiedHV restored = SerializationUtils.clone(source);
    evaluate(cloned, pr, 4, 1, 1);
    evaluate(restored, pr, 4, 3, 2);
    assertArrayEquals(expected, snapshot(source, 4), 0.0);
    assertArrayEquals(evaluate(component(), pr, 4, 3, 3), evaluate(cloned, pr, 4, 3, 3), 0.0);
    assertArrayEquals(evaluate(component(), pr, 4, 1, 4), evaluate(restored, pr, 4, 1, 4), 0.0);
    assertArrayEquals(expected, snapshot(source, 4), 0.0);
  }

  /**
   * Changes pure alpha inputs at fixed temperature without retaining a previous evaluation's terms.
   *
   * @param pr whether to use PR
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pureAlphaChangesAtFixedTemperatureMatchFreshEvaluations(boolean pr) {
    ComponentGENRTLmodifiedHV retained = component();
    double[] initial = evaluate(retained, pr, 4, 3, 0, 0.0);
    for (double adjustment : new double[] {0.03, -0.01, 0.0}) {
      double[] expected = evaluate(component(), pr, 4, 3, 0, adjustment);
      double[] actual = evaluate(retained, pr, 4, 3, 0, adjustment);
      assertArrayEquals(expected, actual, 0.0, "Pure terms belong to this call's configured components");
      if (adjustment != 0.0) {
        assertTrue(Math.abs(initial[0] - actual[0]) > 1e-10, "The alpha perturbation must change the fixture");
      } else {
        assertArrayEquals(initial, actual, 0.0, "Returning the inputs must recover every exposed derivative");
      }
    }
  }

  /**
   * Constructs an evaluator without previously allocated derivative storage.
   *
   * @return the first component of a prescribed mixture
   */
  private static ComponentGENRTLmodifiedHV component() {
    return new ComponentGENRTLmodifiedHV("methanol", 1.0, 1.0, 0);
  }

  /**
   * Evaluates an independently built state, including generated classical hydrocarbon interactions.
   *
   * @param evaluator retained or fresh modified-NRTL component
   * @param pr whether to use PR
   * @param size component count
   * @param level requested initialization level
   * @param state deterministic temperature/composition variant
   * @return gamma and every exposed derivative
   */
  private static double[] evaluate(ComponentGENRTLmodifiedHV evaluator, boolean pr, int size, int level, int state) {
    return evaluate(evaluator, pr, size, level, state, 0.0);
  }

  /**
   * Evaluates prescribed interactions with an optional synthetic pure-alpha perturbation.
   *
   * @param evaluator retained or fresh modified-NRTL component
   * @param pr whether to use PR
   * @param size component count
   * @param level requested initialization level
   * @param state deterministic temperature/composition variant
   * @param pureAdjustment additive alpha m perturbation scaled by component index, not a calibrated fit
   * @return gamma and every exposed derivative
   */
  private static double[] evaluate(ComponentGENRTLmodifiedHV evaluator, boolean pr, int size, int level, int state,
      double pureAdjustment) {
    double temperature = 290.0 + 11.0 * state;
    SystemInterface fluid = pr ? new SystemPrEos(temperature, 20.0) : new SystemSrkEos(temperature, 20.0);
    String[] names = {"methanol", "water", "methane", "ethane"};
    for (int i = 0; i < size; i++) {
      fluid.addComponent(names[i], i == 0 && state == 3 ? 1e-12 : 0.2 + (i + 1.0) / (state + 2.0));
    }
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.LIQUID);
    fluid.init(3);
    PhaseInterface phase = fluid.getPhase(0);
    if (pureAdjustment != 0.0) {
      for (int i = 0; i < size; i++) {
        ComponentEosInterface pure = (ComponentEosInterface) phase.getComponent(i);
        pure.getAttractiveTerm().setm(pure.getAttractiveTerm().getm() + pureAdjustment * (i + 1));
      }
    }
    phase.setInitType(level);
    double[][] alpha = new double[size][size];
    double[][] energy = new double[size][size];
    double[][] offset = new double[size][size];
    double[][] classic = new double[size][size];
    String[][] activation = new String[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        activation[i][j] = i == j || (i >= 2 && j >= 2) ? "Classic" : "HV";
        alpha[i][j] = i == j ? 0.0 : 0.25;
        energy[i][j] = i == j ? 0.0 : 100.0 + 30.0 * i - 15.0 * j;
        offset[i][j] = i == j ? 0.0 : 0.03 * (i - j);
        classic[i][j] = i == j ? 0.0 : 0.02;
      }
    }
    evaluator.getGamma(phase, size, temperature, 20.0, phase.getType(), alpha, energy, offset, classic, activation);
    double[] result = snapshot(evaluator, size);
    for (double value : result) {
      assertTrue(Double.isFinite(value), "Every evaluated quantity must be finite");
    }
    return result;
  }

  /**
   * Copies all public activity results without retaining an alias to internal workspace.
   *
   * @param evaluator evaluated component
   * @param size number of composition derivatives
   * @return scalar and derivative snapshot
   */
  private static double[] snapshot(ComponentGENRTLmodifiedHV evaluator, int size) {
    double[] result = new double[size + 4];
    result[0] = evaluator.getGamma();
    result[1] = evaluator.getLnGamma();
    result[2] = evaluator.getLnGammadt();
    result[3] = evaluator.getLnGammadtdt();
    for (int i = 0; i < size; i++) {
      result[i + 4] = evaluator.getLnGammadn(i);
    }
    return result;
  }
}
