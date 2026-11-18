package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Candidate;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Kind;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Meter;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Status;

/**
 * Independent small-network oracle using every control-volume subset. Internal transfers cancel exactly across each
 * cut, so only crossing meter errors and enclosed storage errors contribute.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessBalanceCutSetTest {
  /** Compares the joint screen with exhaustive cut bounds over reproducible cyclic networks. */
  @Test
  void agreesWithEveryCutOnSmallNetworks() {
    Random random = new Random(782031L);
    for (int run = 0; run < 120; run++) {
      int count = 2 + run % 5;
      List<Meter> meters = new ArrayList<Meter>();
      Map<String, Double> storageBounds = new LinkedHashMap<String, Double>();
      RcaProcessWindow.Builder window = RcaProcessWindow.builder("held", 0.5);
      double[] expectedResiduals = new double[count];
      for (int node = 0; node < count; node++) {
        String tag = String.valueOf(node);
        storageBounds.put(tag, random.nextInt(3) / 4.0);
        expectedResiduals[node] = run % 6 == 0 ? 0 : (random.nextInt(13) - 6) / 4.0;
        double change = -expectedResiduals[node];
        window.signal(tag, new double[] {64, 64 + change / 2, 64 + change});
        addMeter(meters, window, null, tag, random);
        addMeter(meters, window, tag, String.valueOf((node + 1) % count), random);
      }
      ProcessBalanceDiagnosis result = ProcessBalanceDiagnosis.assess(window.build(), storageBounds, meters);
      if (limits(count, meters, storageBounds, expectedResiduals, null, null, 0, 0) != null) {
        assertEquals(Status.WITHIN_BOUNDS, result.getStatus(), "case " + run);
        continue;
      }

      Map<String, double[]> expected = new LinkedHashMap<String, double[]>();
      for (Meter meter : meters) {
        double[] range = limits(count, meters, storageBounds, expectedResiduals, meter.getFrom(), meter.getTo(),
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        if (range != null) {
          expected.put(meter.getTag(), range);
        }
      }
      for (String node : storageBounds.keySet()) {
        double[] range = limits(count, meters, storageBounds, expectedResiduals, null, node, 0,
            Double.POSITIVE_INFINITY);
        if (range != null) {
          expected.put(node, range);
        }
      }
      Status expectedStatus = expected.isEmpty() ? Status.UNEXPLAINED
          : (expected.size() == 1 ? Status.SINGLE_CANDIDATE : Status.AMBIGUOUS);
      assertEquals(expectedStatus, result.getStatus(), "case " + run);
      assertEquals(expected.size(), result.getCandidates().size());
      for (Candidate candidate : result.getCandidates()) {
        double[] range = expected.remove(candidate.getTag());
        assertNotNull(range, candidate.getTag());
        assertEquals(storageBounds.containsKey(candidate.getTag()) ? Kind.UNMETERED_OUTFLOW : Kind.METER_BIAS,
            candidate.getKind());
        assertEquals(range[0], candidate.getMinimumKgPerS(), 1e-10, "case " + run);
        assertEquals(range[1], candidate.getMaximumKgPerS(), 1e-10, "case " + run);
      }
      assertTrue(expected.isEmpty());
    }
  }

  /**
   * Registers a zero-reading meter with a random reproducible absolute error budget.
   *
   * @param meters meter list
   * @param window synchronous samples
   * @param from upstream endpoint
   * @param to downstream endpoint
   * @param random fixed-seed source
   */
  private static void addMeter(List<Meter> meters, RcaProcessWindow.Builder window, String from, String to,
      Random random) {
    String name = "F" + meters.size();
    meters.add(new Meter(name, from, to, random.nextInt(4) / 4.0));
    window.signal(name, new double[3]);
  }

  /**
   * Calculates the exact compatible interval by summing balances over every nonempty subset.
   *
   * @param count number of control volumes
   * @param meters measured edges
   * @param storageBounds one-second inventory-change bounds
   * @param residuals signed residuals in node order
   * @param from negative candidate endpoint
   * @param to positive candidate endpoint
   * @param low initial candidate lower bound
   * @param high initial candidate upper bound
   * @return exact interval or null when infeasible
   */
  private static double[] limits(int count, List<Meter> meters, Map<String, Double> storageBounds, double[] residuals,
      String from, String to, double low, double high) {
    for (int subset = 1; subset < (1 << count); subset++) {
      double observed = 0;
      double budget = 0;
      for (int node = 0; node < count; node++) {
        if (included(subset, String.valueOf(node)) != 0) {
          observed += residuals[node];
          budget += storageBounds.get(String.valueOf(node));
        }
      }
      for (Meter meter : meters) {
        if (included(subset, meter.getFrom()) != included(subset, meter.getTo())) {
          budget += meter.getErrorKgPerS();
        }
      }
      int direction = included(subset, to) - included(subset, from);
      if (direction == 0 && Math.abs(observed) > budget) {
        return null;
      }
      if (direction != 0) {
        low = Math.max(low, direction * observed - budget);
        high = Math.min(high, direction * observed + budget);
      }
    }
    return low <= high ? new double[] {low, high} : null;
  }

  /**
   * Tests membership of a numbered node in a bitmask.
   *
   * @param subset selected volumes
   * @param tag numeric node tag or null for exterior
   * @return one for membership and zero otherwise
   */
  private static int included(int subset, String tag) {
    return tag == null ? 0 : (subset >> Integer.parseInt(tag)) & 1;
  }
}
