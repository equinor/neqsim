package neqsim.process.util.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Regression coverage for the data reconciliation guide's streaming measurements. */
class SteadyStateDocumentationRegressionTest {
  @Test
  void constantDecimalReadingsPassTheSteadyStateGateAndReconcile() {
    SteadyStateDetector detector = new SteadyStateDetector(30);
    double[] readings = { 10050.123456789, 6000.987654321, 4050.135802468 };
    String[] names = { "feed", "gas", "liquid" };
    for (String name : names) {
      detector.addVariable(new SteadyStateVariable(name, 30).setUnit("kg/hr").setUncertainty(20.0));
    }
    for (int sample = 0; sample < 35; sample++) {
      for (int index = 0; index < names.length; index++) {
        detector.updateVariable(names[index], readings[index]);
      }
    }
    SteadyStateResult steady = detector.evaluate();
    assertTrue(steady.isAtSteadyState(), "Identical decimal measurements must not look like a transient");
    for (SteadyStateVariable variable : steady.getVariables()) {
      assertEquals(1.0, variable.getRStatistic(), 0.0);
      assertEquals(0.0, variable.getStandardDeviation(), 0.0);
      assertEquals(0.0, variable.getSlope(), 0.0);
    }
    DataReconciliationEngine engine = detector.createReconciliationEngine();
    engine.addMassBalanceConstraint("separator", new String[] { "feed" }, new String[] { "gas", "liquid" });
    ReconciliationResult result = engine.reconcile();
    assertTrue(result.isConverged());
    assertTrue(result.isGlobalTestPassed());
    assertEquals(0.0, engine.getVariable("feed").getReconciledValue() - engine.getVariable("gas").getReconciledValue()
        - engine.getVariable("liquid").getReconciledValue(), 1e-8);
  }

  @Test
  void statisticsRetainSmallRampOnLargeOperatingOffset() {
    SteadyStateVariable centered = new SteadyStateVariable("centered", 30);
    SteadyStateVariable shifted = new SteadyStateVariable("shifted", 30);
    for (int sample = 0; sample < 35; sample++) {
      double value = sample * 0.125;
      centered.addValue(value);
      shifted.addValue(1.0e12 + value);
    }
    assertEquals(0.125, centered.getSlope(), 1e-15);
    assertEquals(centered.getSlope(), shifted.getSlope(), 1e-15);
    assertEquals(centered.getStandardDeviation(), shifted.getStandardDeviation(), 1e-15);
    assertEquals(centered.getRStatistic(), shifted.getRStatistic(), 1e-15);
    assertTrue(shifted.getRStatistic() < 0.5, "A ramp must still fail the default steady-state threshold");
  }
}
