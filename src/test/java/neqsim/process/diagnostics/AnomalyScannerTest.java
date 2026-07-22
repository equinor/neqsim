package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AnomalyScanner} unsupervised anomaly detection and symptom inference.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class AnomalyScannerTest {

  /**
   * Verifies that a threshold breach is detected, ranked above a trend, and mapped to the vibration symptom.
   */
  @Test
  void detectsThresholdBreachAndInfersSymptom() {
    int n = 40;
    double[] vibration = new double[n];
    double[] temperature = new double[n];
    double[] steady = new double[n];
    for (int i = 0; i < n; i++) {
      vibration[i] = 3.0 + 0.02 * i; // slow rise
      temperature[i] = 120.0 + 0.5 * i; // clear upward trend
      steady[i] = 50.0 + Math.sin(i * 0.5) * 0.01; // essentially flat
    }
    vibration[n - 1] = 9.0; // latest value breaches high limit

    Map<String, double[]> data = new HashMap<String, double[]>();
    data.put("Compressor-1.vibration", vibration);
    data.put("Compressor-1.temperature", temperature);
    data.put("Compressor-1.steadyTag", steady);

    AnomalyScanner scanner = new AnomalyScanner();
    scanner.setDesignLimit("Compressor-1.vibration", Double.NaN, 7.1);
    List<AnomalyScanner.Anomaly> anomalies = scanner.scan(data);

    assertNotNull(anomalies);
    assertFalse(anomalies.isEmpty(), "expected at least the vibration and temperature anomalies");

    AnomalyScanner.Anomaly top = anomalies.get(0);
    assertEquals("Compressor-1.vibration", top.getTag(), "threshold breach should rank first");
    assertEquals(AnomalyScanner.AnomalyKind.THRESHOLD_HIGH, top.getKind());

    Symptom suggested = scanner.suggestSymptom(anomalies);
    assertEquals(Symptom.HIGH_VIBRATION, suggested, "top anomaly on a vibration tag should map to HIGH_VIBRATION");
  }

  /**
   * Verifies that a strong upward trend is detected on its own and mapped to a temperature symptom.
   */
  @Test
  void detectsTrendWithoutDesignLimit() {
    int n = 50;
    double[] temperature = new double[n];
    for (int i = 0; i < n; i++) {
      temperature[i] = 100.0 + 1.5 * i; // strong monotonic rise
    }
    Map<String, double[]> data = new HashMap<String, double[]>();
    data.put("HX-1.temperature", temperature);

    AnomalyScanner scanner = new AnomalyScanner();
    List<AnomalyScanner.Anomaly> anomalies = scanner.scan(data);

    assertEquals(1, anomalies.size());
    assertEquals(AnomalyScanner.AnomalyKind.TREND_UP, anomalies.get(0).getKind());
    assertEquals(Symptom.HIGH_TEMPERATURE, scanner.suggestSymptom(anomalies));
  }

  /**
   * Verifies that flat, in-range data produces no anomalies and no inferred symptom.
   */
  @Test
  void returnsNoAnomalyForSteadyData() {
    int n = 30;
    double[] steady = new double[n];
    for (int i = 0; i < n; i++) {
      steady[i] = 42.0;
    }
    Map<String, double[]> data = new HashMap<String, double[]>();
    data.put("PT-1.pressure", steady);

    AnomalyScanner scanner = new AnomalyScanner();
    List<AnomalyScanner.Anomaly> anomalies = scanner.scan(data);
    assertTrue(anomalies.isEmpty());
    assertTrue(scanner.suggestSymptom(anomalies) == null);
  }
}
