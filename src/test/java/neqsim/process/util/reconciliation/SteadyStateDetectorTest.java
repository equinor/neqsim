package neqsim.process.util.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the steady-state detector.
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class SteadyStateDetectorTest {

  private SteadyStateDetector detector;

  @BeforeEach
  void setUp() {
    detector = new SteadyStateDetector(10);
    detector.setRThreshold(0.5);
  }

  // ==================== Basic steady-state detection ====================

  @Test
  void testConstantSignalIsSteady() {
    SteadyStateVariable v = new SteadyStateVariable("flow", 10);
    detector.addVariable(v);

    // Fill window with constant value
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 100.0);
    }

    SteadyStateResult result = detector.evaluate();
    assertTrue(result.isAtSteadyState(), "Constant signal should be at steady state");
    assertEquals(1, result.getSteadyCount());
    assertEquals(0, result.getTransientCount());
  }

  @Test
  void testNoisyConstantIsSteady() {
    SteadyStateVariable v = new SteadyStateVariable("flow", 10);
    detector.addVariable(v);

    // Fill with readings that fluctuate randomly around 100
    double[] values = {100.2, 99.8, 100.1, 99.9, 100.0, 100.3, 99.7, 100.1, 99.9, 100.0};
    for (double val : values) {
      detector.updateVariable("flow", val);
    }

    SteadyStateResult result = detector.evaluate();
    assertTrue(result.isAtSteadyState(), "Noisy constant signal should be at steady state");
  }

  @Test
  void testRampIsTransient() {
    SteadyStateVariable v = new SteadyStateVariable("flow", 10);
    detector.addVariable(v);

    // Fill with a ramp: 100, 101, 102, ..., 109
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 100.0 + i);
    }

    SteadyStateResult result = detector.evaluate();
    assertFalse(result.isAtSteadyState(), "Ramp signal should NOT be at steady state");
    assertEquals(0, result.getSteadyCount());
    assertEquals(1, result.getTransientCount());
  }

  @Test
  void testStepChangeIsTransient() {
    SteadyStateVariable v = new SteadyStateVariable("flow", 10);
    detector.addVariable(v);

    // 5 values at 100, then 5 values at 200 => step change
    for (int i = 0; i < 5; i++) {
      detector.updateVariable("flow", 100.0);
    }
    for (int i = 0; i < 5; i++) {
      detector.updateVariable("flow", 200.0);
    }

    SteadyStateResult result = detector.evaluate();
    assertFalse(result.isAtSteadyState(), "Step change should NOT be at steady state");
  }

  @Test
  void testRecoveryAfterStepChange() {
    SteadyStateVariable v = new SteadyStateVariable("flow", 10);
    detector.addVariable(v);

    // Initial ramp
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 100.0 + i * 5);
    }
    SteadyStateResult r1 = detector.evaluate();
    assertFalse(r1.isAtSteadyState(), "During ramp should be transient");

    // Now push constant values to fill the window
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 150.0);
    }
    SteadyStateResult r2 = detector.evaluate();
    assertTrue(r2.isAtSteadyState(), "After settling should be at steady state");
  }

  // ==================== Multiple variables ====================

  @Test
  void testMultipleVariablesAllSteady() {
    detector.addVariable(new SteadyStateVariable("flow", 10));
    detector.addVariable(new SteadyStateVariable("temp", 10));
    detector.addVariable(new SteadyStateVariable("pressure", 10));

    // Use deterministic alternating noise (white-noise-like, R stays near 1)
    double[] flowNoise = {0.2, -0.1, 0.3, -0.2, 0.1, -0.3, 0.2, -0.1, 0.3, -0.2};
    double[] tempNoise = {0.05, -0.03, 0.04, -0.05, 0.02, -0.04, 0.05, -0.03, 0.04, -0.02};
    double[] presNoise = {0.01, -0.01, 0.01, -0.01, 0.01, -0.01, 0.01, -0.01, 0.01, -0.01};
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 1000.0 + flowNoise[i]);
      detector.updateVariable("temp", 80.0 + tempNoise[i]);
      detector.updateVariable("pressure", 50.0 + presNoise[i]);
    }

    SteadyStateResult result = detector.evaluate();
    assertTrue(result.isAtSteadyState());
    assertEquals(3, result.getSteadyCount());
  }

  @Test
  void testOneTransientBlocksOverall() {
    detector.addVariable(new SteadyStateVariable("flow", 10));
    detector.addVariable(new SteadyStateVariable("temp", 10));

    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 1000.0); // steady
      detector.updateVariable("temp", 80.0 + i * 3); // ramp
    }

    SteadyStateResult result = detector.evaluate();
    assertFalse(result.isAtSteadyState(), "One transient variable should block overall");
    assertEquals(1, result.getSteadyCount());
    assertEquals(1, result.getTransientCount());
    assertEquals("temp", result.getTransientVariables().get(0).getName());
  }

  @Test
  void testRequiredFractionAllowsPartialSteady() {
    detector.setRequiredFraction(0.5); // 50% is enough
    detector.addVariable(new SteadyStateVariable("flow", 10));
    detector.addVariable(new SteadyStateVariable("temp", 10));

    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 1000.0); // steady
      detector.updateVariable("temp", 80.0 + i * 3); // ramp
    }

    SteadyStateResult result = detector.evaluate();
    assertTrue(result.isAtSteadyState(),
        "With 50% required fraction, 1 of 2 steady should pass overall");
  }

  // ==================== Window and threshold configuration ====================

  @Test
  void testInsufficientSamplesIsTransient() {
    detector.addVariable(new SteadyStateVariable("flow", 10));

    // Only 3 samples (window not full, requireFullWindow=true)
    detector.updateVariable("flow", 100.0);
    detector.updateVariable("flow", 100.0);
    detector.updateVariable("flow", 100.0);

    SteadyStateResult result = detector.evaluate();
    assertFalse(result.isAtSteadyState(), "Incomplete window should be transient");
  }

  @Test
  void testRequireFullWindowDisabled() {
    detector.setRequireFullWindow(false);
    detector.addVariable(new SteadyStateVariable("flow", 10));

    // 5 constant samples (not full window of 10)
    for (int i = 0; i < 5; i++) {
      detector.updateVariable("flow", 100.0);
    }

    SteadyStateResult result = detector.evaluate();
    assertTrue(result.isAtSteadyState(),
        "With requireFullWindow=false, constant partial window should be steady");
  }

  @Test
  void testSlopeThreshold() {
    detector.setSlopeThreshold(0.1);
    detector.addVariable(new SteadyStateVariable("flow", 10));

    // Slow drift: 100.0, 100.05, 100.1, ...
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 100.0 + i * 0.05);
    }

    SteadyStateResult result = detector.evaluate();
    // R-statistic may pass for slow drift, but slope test should catch it
    SteadyStateVariable v = detector.getVariable("flow");
    assertTrue(Math.abs(v.getSlope()) > 0.04, "Slope should be measurable");
  }

  @Test
  void testStdDevThreshold() {
    detector.setStdDevThreshold(1.0); // max 1.0 std.dev allowed
    detector.addVariable(new SteadyStateVariable("noisy", 10));

    // Very noisy signal with std.dev >> 1
    double[] vals = {100, 110, 90, 115, 85, 120, 80, 105, 95, 112};
    for (double val : vals) {
      detector.updateVariable("noisy", val);
    }

    SteadyStateResult result = detector.evaluate();
    assertFalse(result.isAtSteadyState(), "Very noisy signal should fail std.dev test");
  }

  // ==================== R-statistic computation ====================

  @Test
  void testRStatisticConstantSignal() {
    SteadyStateVariable v = new SteadyStateVariable("x", 10);
    for (int i = 0; i < 10; i++) {
      v.addValue(42.0);
    }
    assertEquals(1.0, v.getRStatistic(), 1e-10, "Constant signal should have R = 1.0");
    assertEquals(0.0, v.getStandardDeviation(), 1e-10);
  }

  @Test
  void testRStatisticPureRamp() {
    SteadyStateVariable v = new SteadyStateVariable("x", 20);
    // Pure ramp: 0, 1, 2, ..., 19
    for (int i = 0; i < 20; i++) {
      v.addValue(i);
    }
    // For a pure ramp: filtered var = 1/2, unfiltered var = n(n+1)/12
    // R = filtered/unfiltered, which is small for monotonic trend
    assertTrue(v.getRStatistic() < 0.1, "Pure ramp should have R << 1, got " + v.getRStatistic());
    assertEquals(1.0, v.getSlope(), 1e-10, "Ramp slope should be 1.0 per sample");
  }

  @Test
  void testRStatisticRandomNoise() {
    // For truly random white noise, R should be close to 1.0
    SteadyStateVariable v = new SteadyStateVariable("x", 100);
    java.util.Random rng = new java.util.Random(12345);
    for (int i = 0; i < 100; i++) {
      v.addValue(100.0 + rng.nextGaussian() * 5.0);
    }
    // R should be near 1.0 for white noise (typically 0.8-1.2)
    assertTrue(v.getRStatistic() > 0.5,
        "White noise should have R near 1.0, got " + v.getRStatistic());
  }

  // ==================== Update and evaluate convenience ====================

  @Test
  void testUpdateAllAndEvaluate() {
    detector.addVariable(new SteadyStateVariable("flow", 10));
    detector.addVariable(new SteadyStateVariable("temp", 10));

    // Fill with constant values
    for (int i = 0; i < 10; i++) {
      Map<String, Double> vals = new LinkedHashMap<String, Double>();
      vals.put("flow", 1000.0);
      vals.put("temp", 80.0);
      SteadyStateResult result = detector.updateAndEvaluate(vals);
      if (i == 9) {
        assertTrue(result.isAtSteadyState());
      }
    }
  }

  @Test
  void testAddVariableByName() {
    SteadyStateVariable v = detector.addVariable("test_tag");
    assertNotNull(v);
    assertEquals("test_tag", v.getName());
    assertEquals(10, v.getWindowSize()); // should use default
    assertEquals(1, detector.getVariableCount());
  }

  @Test
  void testRemoveVariable() {
    detector.addVariable(new SteadyStateVariable("a", 10));
    detector.addVariable(new SteadyStateVariable("b", 10));
    assertEquals(2, detector.getVariableCount());

    assertTrue(detector.removeVariable("a"));
    assertEquals(1, detector.getVariableCount());
    assertFalse(detector.removeVariable("nonexistent"));
  }

  @Test
  void testClear() {
    detector.addVariable(new SteadyStateVariable("a", 10));
    detector.addVariable(new SteadyStateVariable("b", 10));
    detector.clear();
    assertEquals(0, detector.getVariableCount());
  }

  // ==================== Bridge to reconciliation ====================

  @Test
  void testCreateReconciliationEngine() {
    detector.setRequireFullWindow(false);

    SteadyStateVariable v1 =
        new SteadyStateVariable("feed", 5).setUncertainty(20.0).setUnit("kg/hr");
    SteadyStateVariable v2 =
        new SteadyStateVariable("gas", 5).setUncertainty(15.0).setUnit("kg/hr");
    SteadyStateVariable v3 =
        new SteadyStateVariable("liquid", 5).setUncertainty(10.0).setUnit("kg/hr");
    detector.addVariable(v1);
    detector.addVariable(v2);
    detector.addVariable(v3);

    // Push constant data (steady state)
    for (int i = 0; i < 5; i++) {
      detector.updateVariable("feed", 1000.0);
      detector.updateVariable("gas", 600.0);
      detector.updateVariable("liquid", 400.0);
    }

    SteadyStateResult ssResult = detector.evaluate();
    assertTrue(ssResult.isAtSteadyState());

    DataReconciliationEngine engine = detector.createReconciliationEngine();
    assertEquals(3, engine.getVariableCount());

    // Add constraint and reconcile
    engine.addMassBalanceConstraint("sep", new String[] {"feed"}, new String[] {"gas", "liquid"});
    ReconciliationResult recResult = engine.reconcile();
    assertTrue(recResult.isConverged());
  }

  @Test
  void testCreateReconciliationEngineExcludesTransient() {
    detector.setRequireFullWindow(false);

    SteadyStateVariable v1 = new SteadyStateVariable("steady_var", 5).setUncertainty(20.0);
    SteadyStateVariable v2 = new SteadyStateVariable("transient_var", 5).setUncertainty(15.0);
    detector.addVariable(v1);
    detector.addVariable(v2);

    // Push constant for v1, ramp for v2
    for (int i = 0; i < 5; i++) {
      detector.updateVariable("steady_var", 100.0);
      detector.updateVariable("transient_var", 100.0 + i * 20);
    }

    detector.evaluate();

    DataReconciliationEngine engine = detector.createReconciliationEngine();
    // Only steady variables should be included
    assertEquals(1, engine.getVariableCount(),
        "Only steady-state variables should be in the engine");
    assertNotNull(engine.getVariable("steady_var"));
  }

  @Test
  void testCreateReconciliationEngineSkipsNoUncertainty() {
    detector.setRequireFullWindow(false);

    SteadyStateVariable v1 = new SteadyStateVariable("with_sigma", 5).setUncertainty(20.0);
    SteadyStateVariable v2 = new SteadyStateVariable("no_sigma", 5);
    // v2 has no uncertainty set (NaN)
    detector.addVariable(v1);
    detector.addVariable(v2);

    for (int i = 0; i < 5; i++) {
      detector.updateVariable("with_sigma", 100.0);
      detector.updateVariable("no_sigma", 100.0);
    }

    detector.evaluate();

    DataReconciliationEngine engine = detector.createReconciliationEngine();
    assertEquals(1, engine.getVariableCount(), "Variables without uncertainty should be excluded");
  }

  // ==================== Output formats ====================

  @Test
  void testToReport() {
    detector.addVariable(new SteadyStateVariable("flow", 10));
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 100.0);
    }

    SteadyStateResult result = detector.evaluate();
    String report = result.toReport();
    assertNotNull(report);
    assertTrue(report.contains("Steady-State Detection Report"));
    assertTrue(report.contains("flow"));
    assertTrue(report.contains("STEADY STATE"));
  }

  @Test
  void testToJson() {
    detector.addVariable(new SteadyStateVariable("flow", 10));
    for (int i = 0; i < 10; i++) {
      detector.updateVariable("flow", 100.0);
    }

    SteadyStateResult result = detector.evaluate();
    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"atSteadyState\": true"));
    assertTrue(json.contains("\"flow\""));
  }

  // ==================== Edge cases ====================

  @Test
  void testEmptyDetectorEvaluatesToTransient() {
    SteadyStateResult result = detector.evaluate();
    assertFalse(result.isAtSteadyState(), "Empty detector should not be at steady state");
    assertEquals(0, result.getSteadyCount());
  }

  @Test
  void testVariableToString() {
    SteadyStateVariable v = new SteadyStateVariable("flow", 10);
    v.setUnit("kg/hr");
    for (int i = 0; i < 10; i++) {
      v.addValue(100.0);
    }
    String s = v.toString();
    assertTrue(s.contains("flow"));
    assertTrue(s.contains("kg/hr"));
  }

  @Test
  void testDetectorToString() {
    String s = detector.toString();
    assertTrue(s.contains("SteadyStateDetector"));
    assertTrue(s.contains("window=10"));
  }

  @Test
  void testDuplicateVariableNameThrows() {
    detector.addVariable(new SteadyStateVariable("flow", 10));
    try {
      detector.addVariable(new SteadyStateVariable("flow", 10));
      assertTrue(false, "Should throw IllegalArgumentException for duplicate name");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("already exists"));
    }
  }

  @Test
  void testUpdateNonexistentVariableThrows() {
    try {
      detector.updateVariable("nonexistent", 42.0);
      assertTrue(false, "Should throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("not found"));
    }
  }

  @Test
  void testWindowSizeTooSmallThrows() {
    try {
      new SteadyStateVariable("x", 2);
      assertTrue(false, "Should throw IllegalArgumentException for window < 3");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("at least 3"));
    }
  }

  @Test
  void testVariableClear() {
    SteadyStateVariable v = new SteadyStateVariable("x", 10);
    for (int i = 0; i < 10; i++) {
      v.addValue(100.0);
    }
    assertEquals(10, v.getCount());
    v.clear();
    assertEquals(0, v.getCount());
    assertFalse(v.isAtSteadyState());
  }
}
