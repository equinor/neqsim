package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/**
 * Unit tests for normal-regime matching and deterministic hypothesis ranking.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class RcaDiagnosisEngineTest extends NeqSimTest {
  /**
   * Verifies that the closest normal regime is used and a sensor-only deviation is ranked without faulty training data.
   */
  @Test
  public void testRegimeMatchingAndSensorBiasDiagnosis() {
    RcaProcessWindow low = RcaProcessWindow.builder("LOW", 1.0).operatingCondition("flow_setpoint", 80.0)
        .signal("pressure", new double[] { 10.0, 10.1, 9.9, 10.0, 10.1, 9.9 })
        .signal("flow", new double[] { 79.0, 80.0, 81.0, 80.0, 79.5, 80.5 }).build();
    RcaProcessWindow high = RcaProcessWindow.builder("HIGH", 1.0).operatingCondition("flow_setpoint", 120.0)
        .signal("pressure", new double[] { 12.0, 12.1, 11.9, 12.0, 12.1, 11.9 })
        .signal("flow", new double[] { 119.0, 120.0, 121.0, 120.0, 119.5, 120.5 }).build();
    RcaProcessWindow biased = RcaProcessWindow.builder("TEST", 1.0).operatingCondition("flow_setpoint", 118.0)
        .signal("pressure", new double[] { 14.0, 14.1, 13.9, 14.0, 14.1, 13.9 })
        .signal("flow", new double[] { 119.0, 120.0, 121.0, 120.0, 119.5, 120.5 }).build();

    RcaNormalOperationModel model = RcaNormalOperationModel.fit(Arrays.asList(low, high));
    RcaFaultHypothesis normal = RcaFaultHypothesis.builder("NORMAL", "No material deviation.")
        .overallRule(RcaFaultHypothesis.Expectation.NEAR_ZERO, 1.0, 1.0, "Normal windows are not anomalous.").build();
    RcaFaultHypothesis sensorBias = RcaFaultHypothesis
        .builder("PRESSURE_SENSOR_BIAS", "Pressure is biased while flow remains normal.")
        .signalRule("pressure", RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.POSITIVE, 3.0,
            3.0, "Biased pressure moves high.")
        .signalRule("flow", RcaFaultHypothesis.Metric.MEAN_Z_SCORE, RcaFaultHypothesis.Expectation.NEAR_ZERO, 2.0, 1.0,
            "Physical flow is unchanged.")
        .build();
    RcaDiagnosisEngine engine = new RcaDiagnosisEngine();
    RcaDiagnosis diagnosis = engine.diagnose(model, biased, Arrays.asList(normal, sensorBias));

    assertEquals("HIGH", diagnosis.getEvidence().getMatchedRegimeId());
    assertEquals("PRESSURE_SENSOR_BIAS", diagnosis.getTopHypothesis().getName());
    assertTrue(diagnosis.getEvidence().getSignalEvidence("pressure").getMeanZScore() > 10.0);
    assertTrue(diagnosis.toJson().contains("\"rankedHypotheses\""));
    assertEquals(diagnosis.toJson(), engine.diagnose(model, biased, Arrays.asList(normal, sensorBias)).toJson());
  }

  /**
   * Verifies physical input validation and immutable signal copies.
   */
  @Test
  public void testWindowCopiesSignalsAndRejectsMismatchedSchema() {
    double[] values = { 1.0, 2.0, 3.0 };
    RcaProcessWindow window = RcaProcessWindow.builder("NORMAL", 1.0).signal("x", values).build();
    values[0] = 99.0;
    assertEquals(1.0, window.getSignal("x")[0], 0.0);

    RcaNormalOperationModel model = RcaNormalOperationModel.fit(Collections.singletonList(window));
    RcaProcessWindow other = RcaProcessWindow.builder("TEST", 1.0).signal("y", new double[] { 1.0, 2.0, 3.0 }).build();
    boolean failed = false;
    try {
      model.analyze(other);
    } catch (IllegalArgumentException expected) {
      failed = true;
    }
    assertTrue(failed);
  }

  /**
   * Verifies that regime distances cannot be compared across different operating-condition dimensions.
   */
  @Test
  public void testNormalWindowsRequireConsistentOperatingConditionSchema() {
    RcaProcessWindow first = RcaProcessWindow.builder("FIRST", 1.0).operatingCondition("gas_flow", 100.0)
        .operatingCondition("liquid_flow", 10.0).signal("pressure", new double[] { 10.0, 10.1, 9.9 }).build();
    RcaProcessWindow second = RcaProcessWindow.builder("SECOND", 1.0).operatingCondition("gas_flow", 120.0)
        .signal("pressure", new double[] { 11.0, 11.1, 10.9 }).build();

    boolean failed = false;
    try {
      RcaNormalOperationModel.fit(Arrays.asList(first, second));
    } catch (IllegalArgumentException expected) {
      failed = true;
    }
    assertTrue(failed);
  }

  /**
   * Verifies that trimming names cannot silently overwrite a signal or regime coordinate.
   */
  @Test
  public void testWindowRejectsNamesThatCollideAfterNormalization() {
    boolean signalFailed = false;
    try {
      RcaProcessWindow.builder("TEST", 1.0).signal("pressure", new double[] { 10.0, 10.1, 9.9 })
          .signal(" pressure ", new double[] { 11.0, 11.1, 10.9 }).build();
    } catch (IllegalArgumentException expected) {
      signalFailed = true;
    }
    assertTrue(signalFailed);

    boolean conditionFailed = false;
    try {
      RcaProcessWindow.builder("TEST", 1.0).operatingCondition("flow", 100.0).operatingCondition(" flow ", 101.0)
          .signal("pressure", new double[] { 10.0, 10.1, 9.9 }).build();
    } catch (IllegalArgumentException expected) {
      conditionFailed = true;
    }
    assertTrue(conditionFailed);
  }
}
