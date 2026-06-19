package neqsim.process.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SafetyAnalysisFunctionEvaluation} (API RP 14C / ISO 10418 coverage).
 *
 * @author NeqSim Community
 * @version 1.0
 */
public class SafetyAnalysisFunctionEvaluationTest {

  /**
   * A fully protected separator reports complete coverage with an "ok" warning.
   */
  @Test
  public void testSeparatorFullCoverage() {
    SafetyAnalysisFunctionEvaluation evaluator = new SafetyAnalysisFunctionEvaluation();
    SafetyAnalysisFunctionEvaluation.Result result =
        evaluator.evaluate("separator", Arrays.asList("PSH", "PSL", "PSV", "LSH", "LSL"));
    assertEquals("separator", result.getComponentType());
    assertTrue(result.getMissingFunctions().isEmpty());
    assertEquals(1.0, result.getCoverageRatio(), 1.0e-9);
    assertEquals("ok", result.getCoverageWarning());
  }

  /**
   * A missing protective function is detected and flagged as a coverage gap.
   */
  @Test
  public void testFiredHeaterMissingFunctionIsGap() {
    SafetyAnalysisFunctionEvaluation evaluator = new SafetyAnalysisFunctionEvaluation();
    // Fired heater requires PSH, PSL, TSH, PSV, BSDV — omit BSDV.
    SafetyAnalysisFunctionEvaluation.Result result =
        evaluator.evaluate("fired heater", Arrays.asList("psh", "psl", "tsh", "psv"));
    assertEquals(Arrays.asList("BSDV"), result.getMissingFunctions());
    assertEquals(0.8, result.getCoverageRatio(), 1.0e-9);
    assertEquals("gap", result.getCoverageWarning());
  }

  /**
   * Duplicate provided codes are de-duplicated and case is normalised.
   */
  @Test
  public void testProvidedFunctionsNormalised() {
    SafetyAnalysisFunctionEvaluation evaluator = new SafetyAnalysisFunctionEvaluation();
    SafetyAnalysisFunctionEvaluation.Result result =
        evaluator.evaluate("pump", Arrays.asList("PSH", "psh", "PSL", "PSV"));
    assertEquals(Arrays.asList("PSH", "PSL", "PSV"), result.getProvidedFunctions());
    assertEquals("ok", result.getCoverageWarning());
  }

  /**
   * An unsupported component type is rejected.
   */
  @Test
  public void testUnsupportedComponentTypeRejected() {
    final SafetyAnalysisFunctionEvaluation evaluator = new SafetyAnalysisFunctionEvaluation();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        evaluator.evaluate("reactor", Arrays.asList("PSH"));
      }
    });
  }

  /**
   * A blank provided function code is rejected.
   */
  @Test
  public void testBlankFunctionCodeRejected() {
    final SafetyAnalysisFunctionEvaluation evaluator = new SafetyAnalysisFunctionEvaluation();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        evaluator.evaluate("pump", Arrays.asList("PSH", "  "));
      }
    });
  }
}
