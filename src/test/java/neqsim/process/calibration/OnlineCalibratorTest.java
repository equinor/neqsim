package neqsim.process.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Tests for OnlineCalibrator class.
 */
class OnlineCalibratorTest {

  private ProcessSystem processSystem;
  private OnlineCalibrator calibrator;

  @BeforeEach
  void setUp() {
    processSystem = new ProcessSystem();
    calibrator = new OnlineCalibrator(processSystem);
    calibrator.setTunableParameters(Arrays.asList("k_factor", "efficiency"));
    calibrator.setDeviationThreshold(0.1);
  }

  @Test
  void testRecordDataPoint() {
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("flowrate", 100.0);
    measurements.put("pressure", 50.0);

    Map<String, Double> predictions = new HashMap<>();
    predictions.put("flowrate", 95.0);
    predictions.put("pressure", 51.0);

    // 5% error in flowrate, 2% in pressure - both under 10% threshold
    boolean exceeds = calibrator.recordDataPoint(measurements, predictions);
    assertFalse(exceeds, "Error should not exceed threshold");

    assertEquals(1, calibrator.getHistorySize());
  }

  @Test
  void testDeviationDetection() {
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("flowrate", 100.0);

    Map<String, Double> predictions = new HashMap<>();
    predictions.put("flowrate", 85.0); // 15% error

    boolean exceeds = calibrator.recordDataPoint(measurements, predictions);
    assertTrue(exceeds, "15% error should exceed 10% threshold");
  }

  @Test
  void testIncrementalUpdate() {
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("output", 100.0);

    Map<String, Double> predictions = new HashMap<>();
    predictions.put("output", 90.0);

    CalibrationResult result = calibrator.incrementalUpdate(measurements, predictions);

    assertNotNull(result);
    assertTrue(result.isSuccessful());
    assertEquals(1, result.getIterations());
    assertNotNull(result.getCalibratedParameters());
  }

  @Test
  void testFullRecalibrationInsufficientData() {
    // Should fail with insufficient data
    CalibrationResult result = calibrator.fullRecalibration();
    assertFalse(result.isSuccessful());
    assertTrue(result.getMessage().contains("Insufficient data"));
  }

  @Test
  void testFullRecalibrationWithData() {
    // Add sufficient data points
    for (int i = 0; i < 20; i++) {
      Map<String, Double> measurements = new HashMap<>();
      measurements.put("output", 100.0 + i);

      Map<String, Double> predictions = new HashMap<>();
      predictions.put("output", 98.0 + i);

      Map<String, Double> conditions = new HashMap<>();
      conditions.put("temperature", 300.0 + i * 0.5);
      conditions.put("pressure", 10.0 + i * 0.1);

      calibrator.recordDataPoint(measurements, predictions, conditions);
    }

    assertEquals(20, calibrator.getHistorySize());

    CalibrationResult result = calibrator.fullRecalibration();
    assertNotNull(result);
    assertTrue(result.isSuccessful());
    assertNotNull(calibrator.getLastCalibrationTime());
    assertNotNull(calibrator.getQualityMetrics());
  }

  @Test
  void testCalibrationQuality() {
    // Add data and calibrate
    for (int i = 0; i < 50; i++) {
      Map<String, Double> measurements = new HashMap<>();
      measurements.put("output", 100.0 + Math.random() * 10);

      Map<String, Double> predictions = new HashMap<>();
      predictions.put("output", 99.0 + Math.random() * 10);

      Map<String, Double> conditions = new HashMap<>();
      conditions.put("temperature", 300.0 + i);

      calibrator.recordDataPoint(measurements, predictions, conditions);
    }

    calibrator.fullRecalibration();
    CalibrationQuality quality = calibrator.getQualityMetrics();

    assertNotNull(quality);
    assertTrue(quality.getOverallScore() >= 0 && quality.getOverallScore() <= 100);
    assertNotNull(quality.getRating());
    assertTrue(quality.getSampleCount() > 0);
  }

  @Test
  void testHistoryManagement() {
    calibrator.setMaxHistorySize(10);

    // Add more than max size
    for (int i = 0; i < 15; i++) {
      Map<String, Double> measurements = new HashMap<>();
      measurements.put("value", (double) i);

      Map<String, Double> predictions = new HashMap<>();
      predictions.put("value", (double) i);

      calibrator.recordDataPoint(measurements, predictions);
    }

    // History should be trimmed to max size
    assertEquals(10, calibrator.getHistorySize());

    // Clear history
    calibrator.clearHistory();
    assertEquals(0, calibrator.getHistorySize());
  }

  @Test
  void testExportHistory() {
    for (int i = 0; i < 5; i++) {
      Map<String, Double> measurements = new HashMap<>();
      measurements.put("value", (double) i);

      Map<String, Double> predictions = new HashMap<>();
      predictions.put("value", (double) i);

      calibrator.recordDataPoint(measurements, predictions);
    }

    var history = calibrator.exportHistory();
    assertEquals(5, history.size());

    // Check first data point
    var first = history.get(0);
    assertNotNull(first.getTimestamp());
    assertEquals(0.0, first.getMeasurements().get("value"), 0.001);
  }
}
