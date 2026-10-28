package neqsim.process.equipment.separator.emulsion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Exact-equation reproduction and conservative integration checks for the dense-emulsion closure.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class DenseEmulsionLayerModelTest {
  /** Published Figure 7 droplet radius [m]. */
  private static final double RADIUS_M = 0.00135;

  /** Published Figure 7 fitted coalescence frequency [1/(m2 s)]. */
  private static final double OMEGA = 9500.0;

  /**
   * Creates a reproducible laboratory-scale model with measured interfacial tension.
   *
   * @return immutable model
   */
  private DenseEmulsionLayerModel model() {
    return new DenseEmulsionLayerModel(0.0029, 250.0, RADIUS_M, OMEGA);
  }

  /**
   * Reproduces independent primary numerical master curves, allowing published decimal rounding.
   *
   * @throws IOException if the attributed primary fixture cannot be read
   */
  @Test
  void reproducesPublishedDimensionlessMasterCurves() throws IOException {
    InputStream stream = getClass().getResourceAsStream("zlobin-2026-master-curve.csv");
    assertTrue(stream != null, "Primary-source curve fixture must be packaged");
    int rows = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.startsWith("phi") || line.trim().isEmpty()) {
          continue;
        }
        String[] columns = line.split(",");
        double phi = Double.parseDouble(columns[0]);
        double expectedHeight = Double.parseDouble(columns[1]);
        double expectedAreaRatio = Double.parseDouble(columns[2]);
        double expectedFlux = Double.parseDouble(columns[3]);
        double area = DenseEmulsionLayerModel.dimensionlessContactArea(phi);
        double height = DenseEmulsionLayerModel.dimensionlessLayerHeight(phi);
        assertEquals(expectedHeight, height, Math.max(2.0e-6, expectedHeight * 1.0e-4));
        assertEquals(expectedAreaRatio, area / DenseEmulsionLayerModel.dimensionlessContactArea(0.0),
            Math.max(2.0e-6, expectedAreaRatio * 5.0e-4));
        assertEquals(expectedFlux, 4.0 * Math.PI / 3.0 * area, Math.max(6.0e-6, expectedFlux * 8.0e-4));

        // Independently specified equation (5) connects the primary curve to the conserved API.
        double g = 7.3 * (0.26 - phi) * (0.26 - phi) / Math.sqrt(phi);
        DenseEmulsionLayerModel closure = model();
        double waterDepth = closure.getCharacteristicLengthM() * g;
        assertEquals(height, closure.getLayerHeightM(waterDepth) / closure.getCharacteristicLengthM(),
            Math.max(1.0e-12, height * 1.0e-10));
        double scale = 4.0 * Math.PI / 3.0 * OMEGA * RADIUS_M * RADIUS_M * RADIUS_M;
        assertEquals(area, closure.getCoalescenceFluxMPerS(waterDepth) / scale, 1.0e-12);
        rows++;
      }
    }
    assertEquals(7, rows);
  }

  /** Verifies an independently evaluated analytical critical flux and its area conversion. */
  @Test
  void reproducesAnalyticalCriticalCapacity() {
    DenseEmulsionLayerModel closure = model();
    assertEquals(1.0824377922849887e-4, closure.getCriticalFluxMPerS(), 1.0e-18);
    double area = Math.PI * 0.0175 * 0.0175;
    assertEquals(6.248563207286748, closure.getCriticalFluxMPerS() * area * 6.0e7, 1.0e-12);
    assertEquals(0.0, closure.getSteadyLayerHeightM(0.0));
    assertEquals(Double.POSITIVE_INFINITY, closure.getSteadyLayerHeightM(closure.getCriticalFluxMPerS()));
    assertEquals(Double.POSITIVE_INFINITY, closure.getSteadyLayerHeightM(1.1 * closure.getCriticalFluxMPerS()));
  }

  /** Checks height, trapped oil, coalescence saturation and increasing stationary thickness. */
  @Test
  void hasPhysicalMonotonicityAndFiniteOilHoldup() {
    DenseEmulsionLayerModel closure = model();
    double previousHeight = 0.0;
    double previousFlux = 0.0;
    for (double depth : new double[] {0.0, 1.0e-20, 1.0e-12, 1.0e-8, 1.0e-5, 0.001, 0.01, 1.0, 1.0e6}) {
      double height = closure.getLayerHeightM(depth);
      double flux = closure.getCoalescenceFluxMPerS(depth);
      assertTrue(height >= depth, "Layer contains all dispersed water plus nonnegative trapped oil");
      assertTrue(height <= depth / 0.74 + 1.0e-16);
      assertTrue(height >= previousHeight);
      assertTrue(flux >= previousFlux);
      assertTrue(flux <= closure.getCriticalFluxMPerS());
      previousHeight = height;
      previousFlux = flux;
    }
    double low = closure.getSteadyLayerHeightM(0.2 * closure.getCriticalFluxMPerS());
    double high = closure.getSteadyLayerHeightM(0.8 * closure.getCriticalFluxMPerS());
    assertTrue(low > 0.0 && high > low);
  }

  /** Checks the exact close-packing limit without cancellation for a nearly empty layer. */
  @Test
  void resolvesNearlyEmptyLayerWithoutCancellation() {
    DenseEmulsionLayerModel closure = model();
    double depth = 1.0e-25;
    assertEquals(depth / 0.74, closure.getLayerHeightM(depth), depth * 1.0e-10);
    assertTrue(closure.getCoalescenceFluxMPerS(depth) > 0.0);
    assertEquals(0.0, DenseEmulsionLayerModel.dimensionlessLayerHeight(0.26));
    assertEquals(0.0, DenseEmulsionLayerModel.dimensionlessContactArea(0.26));
  }

  /** Checks positivity and water balance when the chosen time step exceeds drainage time scales. */
  @Test
  void implicitStepPreservesWaterForLargeTimeSteps() {
    DenseEmulsionLayerModel closure = model();
    for (double dt : new double[] {0.0, 0.1, 100.0, 1.0e6}) {
      double initial = 0.01;
      double incoming = 0.4 * closure.getCriticalFluxMPerS();
      double next = closure.advance(initial, incoming, dt);
      double budget = initial + incoming * dt;
      assertTrue(next >= 0.0 && next <= budget);
      double coalesced = budget - next;
      assertEquals(coalesced, dt * closure.getCoalescenceFluxMPerS(next), Math.max(1.0e-15, budget * 1.0e-12));
    }
  }

  /** Checks stable first-order time-step convergence against a refined independent time mesh. */
  @Test
  void transientConvergesUnderTimeStepRefinement() {
    DenseEmulsionLayerModel closure = model();
    double initial = 0.02;
    double incoming = 0.3 * closure.getCriticalFluxMPerS();
    double reference = integrate(closure, initial, incoming, 300.0, 4096);
    double coarse = integrate(closure, initial, incoming, 300.0, 8);
    double medium = integrate(closure, initial, incoming, 300.0, 16);
    double fine = integrate(closure, initial, incoming, 300.0, 32);
    double coarseError = Math.abs(coarse - reference);
    double mediumError = Math.abs(medium - reference);
    double fineError = Math.abs(fine - reference);
    assertTrue(mediumError < 0.7 * coarseError);
    assertTrue(fineError < 0.7 * mediumError);
    assertTrue(reference > 0.0);
  }

  /** Checks no-feed monotonic drainage and initially empty positive-feed accumulation. */
  @Test
  void drainsAndAccumulatesWithCorrectSigns() {
    DenseEmulsionLayerModel closure = model();
    double initial = 0.01;
    double drained = closure.advance(initial, 0.0, 1.0e8);
    assertTrue(drained > 0.0 && drained < initial);
    assertTrue(closure.advance(drained, 0.0, 100.0) <= drained);
    double incoming = 2.0 * closure.getCriticalFluxMPerS();
    double first = closure.advance(0.0, incoming, 100.0);
    double second = closure.advance(first, incoming, 100.0);
    assertTrue(first > 0.0 && second > first);
  }

  /** Checks the zero-coalescence physical limit without singular steady calculations. */
  @Test
  void supportsCompleteCoalescenceInhibition() {
    DenseEmulsionLayerModel closure = new DenseEmulsionLayerModel(0.003, 250.0, RADIUS_M, 0.0);
    assertEquals(0.0, closure.getCriticalFluxMPerS());
    assertEquals(0.0, closure.getCoalescenceFluxMPerS(1.0));
    assertEquals(0.02, closure.advance(0.02, 0.0, 1000.0));
    assertEquals(0.03, closure.advance(0.02, 1.0e-5, 1000.0), 1.0e-16);
    assertEquals(Double.POSITIVE_INFINITY, closure.getSteadyLayerHeightM(1.0e-5));
  }

  /** Checks finite validated inputs and deterministic rejection of overflowing requested budgets. */
  @Test
  void rejectsInvalidInputsAndOverflow() {
    assertThrows(IllegalArgumentException.class, () -> new DenseEmulsionLayerModel(0.0, 250.0, RADIUS_M, OMEGA));
    assertThrows(IllegalArgumentException.class, () -> new DenseEmulsionLayerModel(0.003, -250.0, RADIUS_M, OMEGA));
    assertThrows(IllegalArgumentException.class, () -> new DenseEmulsionLayerModel(0.003, 250.0, Double.NaN, OMEGA));
    assertThrows(IllegalArgumentException.class, () -> new DenseEmulsionLayerModel(0.003, 250.0, RADIUS_M, -1.0));
    assertThrows(IllegalArgumentException.class,
        () -> new DenseEmulsionLayerModel(0.003, 250.0, 1.0e100, Double.MAX_VALUE));
    DenseEmulsionLayerModel closure = model();
    assertThrows(IllegalArgumentException.class, () -> closure.getLayerHeightM(-1.0));
    assertThrows(IllegalArgumentException.class, () -> closure.getCoalescenceFluxMPerS(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> closure.getSteadyLayerHeightM(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> closure.advance(0.0, 1.0, -1.0));
    assertThrows(IllegalArgumentException.class, () -> closure.advance(0.0, Double.MAX_VALUE, 2.0));
    assertThrows(IllegalArgumentException.class, () -> DenseEmulsionLayerModel.dimensionlessLayerHeight(0.0));
    assertThrows(IllegalArgumentException.class, () -> DenseEmulsionLayerModel.dimensionlessContactArea(0.3));
  }

  /**
   * Quantifies a limited experimental comparison separately from exact-equation reproduction.
   *
   * <p>
   * The primary Figure 7a experiment supplies raw layer measurements for 3 mL/min, gamma 2.9 mN/m, droplet radius 1.35
   * mm and a nominal PMMA-column radius 17.5 mm. The authors' corresponding numerical file supplies fitted omega 9300
   * m^-2 s^-1. Density difference 250 kg/m3 is an explicit reconstruction assumption, not a reported exact test
   * condition. Starting from measured 2.7778 mm at 200 s excludes initial monolayers. Feed stops at 680 s as recorded
   * by the numerical file. Nine later raw observations, including negative optical-baseline noise after drainage, give
   * RMSE about 0.8321 mm and MAE about 0.7628 mm. This is a discrepancy assessment for a partially reconstructed
   * laboratory case, not independent industrial validation or a predictive chemistry parameterization.
   * </p>
   *
   * @throws IOException if the primary experimental fixture cannot be read
   */
  @Test
  void quantifiesPublishedLaboratoryTrajectoryDiscrepancy() throws IOException {
    DenseEmulsionLayerModel closure = new DenseEmulsionLayerModel(0.0029, 250.0, RADIUS_M, 9300.0);
    double waterDepth = waterDepthForLayerHeight(closure, 0.0027778);
    double time = 200.0;
    double incoming = (3.0 / 6.0e7) / (Math.PI * 0.0175 * 0.0175);
    InputStream stream = getClass().getResourceAsStream("zlobin-2026-experimental-qw3.csv");
    assertTrue(stream != null, "Primary experimental fixture must be packaged");
    double squaredError = 0.0;
    double absoluteError = 0.0;
    int rows = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("#") || line.startsWith("time") || line.trim().isEmpty()) {
          continue;
        }
        String[] columns = line.split(",");
        double observationTime = Double.parseDouble(columns[0]);
        double measuredLayerMm = Double.parseDouble(columns[1]);
        while (time < observationTime) {
          double dt = Math.min(1.0, observationTime - time);
          if (time < 680.0) {
            dt = Math.min(dt, 680.0 - time);
          }
          waterDepth = closure.advance(waterDepth, time < 680.0 ? incoming : 0.0, dt);
          time += dt;
        }
        double errorMm = closure.getLayerHeightM(waterDepth) * 1000.0 - measuredLayerMm;
        squaredError += errorMm * errorMm;
        absoluteError += Math.abs(errorMm);
        rows++;
      }
    }
    assertEquals(9, rows);
    assertEquals(0.83206, Math.sqrt(squaredError / rows), 5.0e-4,
        "Known laboratory discrepancy must be reported separately from numerical curve accuracy");
    assertEquals(0.76282, absoluteError / rows, 5.0e-4);
  }

  /**
   * Initializes conserved inventory from a measured total layer height using the exact geometry.
   *
   * @param closure immutable closure
   * @param layerHeightM measured total layer height [m]
   * @return dispersed-water depth [m]
   */
  private double waterDepthForLayerHeight(DenseEmulsionLayerModel closure, double layerHeightM) {
    double lower = 0.74 * layerHeightM;
    double upper = layerHeightM;
    for (int i = 0; i < 70; i++) {
      double midpoint = lower + 0.5 * (upper - lower);
      if (closure.getLayerHeightM(midpoint) < layerHeightM) {
        lower = midpoint;
      } else {
        upper = midpoint;
      }
    }
    return lower + 0.5 * (upper - lower);
  }

  /**
   * Integrates a constant incoming dispersed-water flux on a uniform external time mesh.
   *
   * @param closure immutable physical model
   * @param initial initial dispersed-water depth [m]
   * @param incoming incoming dispersed-water flux [m/s]
   * @param duration simulation duration [s]
   * @param steps number of implicit time steps
   * @return final dispersed-water depth [m]
   */
  private double integrate(DenseEmulsionLayerModel closure, double initial, double incoming, double duration,
      int steps) {
    double depth = initial;
    for (int i = 0; i < steps; i++) {
      depth = closure.advance(depth, incoming, duration / steps);
    }
    return depth;
  }
}
