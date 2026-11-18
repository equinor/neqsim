package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Candidate;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Kind;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Meter;
import neqsim.process.diagnostics.ProcessBalanceDiagnosis.Status;

/**
 * Analytic inventories and fault signatures for a separator feeding a receiver.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessBalanceDiagnosisTest {
  private static final String SEPARATOR = "separator_kg";
  private static final String RECEIVER = "receiver_kg";

  /**
   * Defines a gas and liquid split with a common liquid transfer measurement.
   *
   * @return measured edges in kg/s
   */
  private List<Meter> meters() {
    return Arrays.asList(new Meter("feed", null, SEPARATOR, 0.02), new Meter("gas", SEPARATOR, null, 0.02),
        new Meter("transfer", SEPARATOR, RECEIVER, 0.02), new Meter("export", RECEIVER, null, 0.02));
  }

  /**
   * Supplies total inventory-change bounds over the two-second window.
   *
   * @return uncertainty bounds in kg
   */
  private Map<String, Double> bounds() {
    Map<String, Double> bounds = new LinkedHashMap<String, Double>();
    bounds.put(SEPARATOR, 0.08);
    bounds.put(RECEIVER, 0.12);
    return bounds;
  }

  /**
   * Samples a triangular inlet-flow ramp with independently integrated storage at 0, 1 and 2 s.
   *
   * @param transferBias common meter bias in kg/s
   * @param separatorLoss physical unmetered outflow from separator in kg/s
   * @param receiverLoss physical unmetered outflow from receiver in kg/s
   * @return synchronized measured window without fault labels
   */
  private RcaProcessWindow window(double transferBias, double separatorLoss, double receiverLoss) {
    return RcaProcessWindow.builder("test", 1.0).signal("feed", new double[] {6, 10, 6})
        .signal("gas", new double[] {2, 2, 2})
        .signal("transfer", new double[] {3 + transferBias, 3 + transferBias, 3 + transferBias})
        .signal("export", new double[] {2, 2, 2})
        .signal(SEPARATOR, new double[] {100, 103 - separatorLoss, 106 - 2 * separatorLoss})
        .signal(RECEIVER, new double[] {50, 51 - receiverLoss, 52 - 2 * receiverLoss}).build();
  }

  /** Normal filling produces zero residual despite unequal inlet and outlet totals. */
  @Test
  void accumulationClosesBothBalances() {
    ProcessBalanceDiagnosis result = ProcessBalanceDiagnosis.assess(window(0, 0, 0), bounds(), meters());
    assertEquals(2.0, result.getDurationSeconds());
    assertEquals(3.0, result.getResiduals().get(SEPARATOR).getNetInflowKgPerS());
    assertEquals(3.0, result.getResiduals().get(SEPARATOR).getAccumulationKgPerS());
    assertEquals(0.0, result.getResiduals().get(RECEIVER).getResidualKgPerS());
    assertEquals(Status.WITHIN_BOUNDS, result.getStatus());
    assertEquals(0, result.getCandidates().size());
  }

  /** A shared meter reads high or low in both vessels, including signed reverse readings. */
  @Test
  void sharedMeterBiasHasOppositeResidualsAndBoundedMagnitude() {
    for (double bias : new double[] {1.0, -4.0}) {
      ProcessBalanceDiagnosis result = ProcessBalanceDiagnosis.assess(window(bias, 0, 0), bounds(), meters());
      assertEquals(Status.SINGLE_CANDIDATE, result.getStatus());
      assertEquals(-bias, result.getResiduals().get(SEPARATOR).getResidualKgPerS());
      assertEquals(bias, result.getResiduals().get(RECEIVER).getResidualKgPerS());
      Candidate candidate = result.getCandidates().get(0);
      assertEquals(Kind.METER_BIAS, candidate.getKind());
      assertEquals("transfer", candidate.getTag());
      assertEquals(bias - 0.1, candidate.getMinimumKgPerS(), 1e-12);
      assertEquals(bias + 0.1, candidate.getMaximumKgPerS(), 1e-12);
    }
  }

  /** A local loss looks identical to errors at the outer metering boundary. */
  @Test
  void localLossRetainsIndistinguishableMeters() {
    ProcessBalanceDiagnosis result = ProcessBalanceDiagnosis.assess(window(0, 1, 0), bounds(), meters());
    assertEquals(Status.AMBIGUOUS, result.getStatus());
    assertEquals(3, result.getCandidates().size());
    assertEquals("feed", result.getCandidates().get(0).getTag());
    assertEquals("gas", result.getCandidates().get(1).getTag());
    assertEquals(Kind.UNMETERED_OUTFLOW, result.getCandidates().get(2).getKind());
    assertEquals(SEPARATOR, result.getCandidates().get(2).getTag());
    assertEquals(0.9, result.getCandidates().get(2).getMinimumKgPerS(), 1e-12);
    assertEquals(-0.9, result.getCandidates().get(1).getMaximumKgPerS(), 1e-12);
  }

  /** Each single fault must explain all nodes within their budgets. */
  @Test
  void incompatibleMultipleFaultsRemainUnexplained() {
    ProcessBalanceDiagnosis result = ProcessBalanceDiagnosis.assess(window(0, 1, 1), bounds(), meters());
    assertEquals(Status.UNEXPLAINED, result.getStatus());
    assertEquals(0, result.getCandidates().size());
  }

  /** An exact bound is included and a hidden small bias can be consistent with normal uncertainty. */
  @Test
  void acceptsEqualityAtErrorBound() {
    List<Meter> edges = Arrays.asList(new Meter("feed", null, SEPARATOR, 0.25), new Meter("gas", SEPARATOR, null, 0),
        new Meter("transfer", SEPARATOR, RECEIVER, 0), new Meter("export", RECEIVER, null, 0.25));
    Map<String, Double> exactBounds = bounds();
    exactBounds.replaceAll((name, bound) -> 0.0);
    assertEquals(Status.WITHIN_BOUNDS,
        ProcessBalanceDiagnosis.assess(window(0.25, 0, 0), exactBounds, edges).getStatus());
  }

  /** Shared-meter uncertainty cancels across the transfer even when each individual residual fits its budget. */
  @Test
  void rejectsJointlyInconsistentHealthyMeterErrors() {
    List<Meter> edges = Arrays.asList(new Meter("feed", null, SEPARATOR, 0), new Meter("gas", SEPARATOR, null, 0),
        new Meter("transfer", SEPARATOR, RECEIVER, 1), new Meter("export", RECEIVER, null, 0));
    Map<String, Double> exact = bounds();
    exact.replaceAll((name, bound) -> 0.0);
    ProcessBalanceDiagnosis result = ProcessBalanceDiagnosis.assess(window(0, 0.75, 0.75), exact, edges);
    assertEquals(Status.AMBIGUOUS, result.getStatus());
    assertEquals(5, result.getCandidates().size());
    for (Candidate candidate : result.getCandidates()) {
      assertEquals(1.5, Math.abs(candidate.getMinimumKgPerS()), 1e-10);
      assertEquals(candidate.getMinimumKgPerS(), candidate.getMaximumKgPerS(), 1e-10);
    }
    assertEquals(Status.WITHIN_BOUNDS, ProcessBalanceDiagnosis.assess(window(0.75, 0, 0), exact, edges).getStatus());
  }

  /** A plant-wide closure can conceal a transfer bias between two internally uncertain metering sections. */
  @Test
  void isolatesBiasWhenTotalAndEachIndividualBalanceFit() {
    Map<String, Double> exact = new LinkedHashMap<String, Double>();
    RcaProcessWindow.Builder data = RcaProcessWindow.builder("steady", 1.0);
    for (String name : Arrays.asList("A", "B", "C", "D")) {
      exact.put(name, 0.0);
      data.signal(name, new double[] {100, 100, 100});
    }
    double[] flow = {10, 9.25, 8.5, 9.25, 10};
    String[] endpoints = {null, "A", "B", "C", "D", null};
    List<Meter> edges = new java.util.ArrayList<Meter>();
    for (int i = 0; i < flow.length; i++) {
      String name = "F" + i;
      data.signal(name, new double[] {flow[i], flow[i], flow[i]});
      edges.add(new Meter(name, endpoints[i], endpoints[i + 1], i == 1 || i == 3 ? 1.0 : 0.0));
    }
    ProcessBalanceDiagnosis result = ProcessBalanceDiagnosis.assess(data.build(), exact, edges);
    assertEquals(Status.SINGLE_CANDIDATE, result.getStatus());
    Candidate candidate = result.getCandidates().get(0);
    assertEquals("F2", candidate.getTag());
    assertEquals(-1.5, candidate.getMinimumKgPerS(), 1e-10);
    assertEquals(-1.5, candidate.getMaximumKgPerS(), 1e-10);
  }

  /** Reject incomplete flowsheets and invalid uncertainties before reporting a diagnosis. */
  @Test
  void rejectsInvalidWiringAndMissingOrNonFiniteMeasurements() {
    RcaProcessWindow window = window(0, 0, 0);
    assertThrows(IllegalArgumentException.class, () -> new Meter("transfer", SEPARATOR, SEPARATOR, 0));
    assertThrows(IllegalArgumentException.class, () -> new Meter("transfer", null, null, 0));
    assertThrows(IllegalArgumentException.class, () -> new Meter("transfer", SEPARATOR, null, Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> ProcessBalanceDiagnosis.assess(window, bounds(), Arrays.asList(meters().get(0), meters().get(0))));
    assertThrows(IllegalArgumentException.class, () -> ProcessBalanceDiagnosis.assess(window, bounds(),
        Arrays.asList(new Meter("transfer", SEPARATOR, "missing", 0))));
    assertThrows(IllegalArgumentException.class, () -> ProcessBalanceDiagnosis.assess(window, bounds(),
        Arrays.asList(new Meter("missing", SEPARATOR, RECEIVER, 0))));
    assertThrows(IllegalArgumentException.class,
        () -> ProcessBalanceDiagnosis.assess(window, bounds(), Arrays.asList(new Meter("feed", null, SEPARATOR, 0))));
    Map<String, Double> incomplete = bounds();
    incomplete.put(RECEIVER, null);
    assertThrows(IllegalArgumentException.class, () -> ProcessBalanceDiagnosis.assess(window, incomplete, meters()));
    assertThrows(IllegalArgumentException.class,
        () -> ProcessBalanceDiagnosis.assess(window(0, 1000, 0), bounds(), meters()));
  }
}
