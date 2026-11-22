import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import neqsim.pvtsimulation.util.CalibratedPvtModel;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Candidate;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Posterior;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Existing-API baseline reproducer for tiny strictly positive upper-tail support.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class QuantileEndpointQualification {
  /** Prevents construction. */
  private QuantileEndpointQualification() {
  }

  /**
   * Retains every endpoint check before throwing on a wrong support value.
   *
   * @param args endpoint output JSON path, optionally followed by query-timing output path
   * @throws Exception if file access or an exact support-value check fails
   */
  public static void main(String[] args) throws Exception {
    SystemInterface fluid = new SystemSrkEos(400.0, 200.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    CalibratedPvtModel model = new CalibratedPvtModel(fluid, fluid, "synthetic support endpoint fixture",
        "fixed SRK", "identical SRK", "explicit positive prior tail; no tuning", new String[] {"synthetic"});
    Step calibration = new Step(100.0, new double[1], new double[1], 200.0, 1.0, false);
    Step forecast = new Step(100.0, new double[] {100000.0}, new double[1], Double.NaN, Double.NaN, false);
    CompositionalReservoirHistory history = new CompositionalReservoirHistory(model, "prescribed methane amount",
        new String[] {"methane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {calibration, forecast});
    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    Posterior timingPosterior = null;
    int failures = 0;
    for (double tail : new double[] {1.0e-2, 1.0e-8, 1.0e-16, 1.0e-20, 1.0e-100, Double.MIN_VALUE}) {
      Candidate lower = new Candidate(history, 900.0, null, 1.0, "dominant smaller-volume scenario");
      Candidate upper = new Candidate(history, 1100.0, null, tail, "tiny positive larger-volume scenario");
      for (int order = 0; order < 2; order++) {
        Posterior posterior = ReservoirHistoryEnsemble.evaluate(order == 0 ? new Candidate[] {lower, upper}
            : new Candidate[] {upper, lower});
        timingPosterior = posterior;
        int lowerIndex = order == 0 ? 0 : 1;
        int upperIndex = 1 - lowerIndex;
        double low = posterior.getResults()[lowerIndex].getPressuresBara()[2];
        double high = posterior.getResults()[upperIndex].getPressuresBara()[2];
        double positiveMass = posterior.getPosteriorMasses()[upperIndex];
        for (double q : new double[] {0.0, 0.5, 1.0}) {
          double expected = q == 1.0 ? high : low;
          double actual = posterior.pressureQuantiles(q)[2];
          boolean pass = positiveMass > 0.0 && high > low && actual == expected;
          failures += pass ? 0 : 1;
          Map<String, Object> row = new LinkedHashMap<String, Object>();
          row.put("priorTail", tail);
          row.put("order", order);
          row.put("positivePosteriorTail", positiveMass);
          row.put("q", q);
          row.put("expectedBara", expected);
          row.put("actualBara", actual);
          row.put("pass", pass);
          rows.add(row);
        }
      }
    }
    try (FileWriter writer = new FileWriter(args[0])) {
      new Gson().toJson(rows, writer);
    }
    if (args.length > 1) {
      List<Map<String, Object>> timings = new ArrayList<Map<String, Object>>();
      double checksum = 0.0;
      for (int block = 0; block < 18; block++) {
        long start = System.nanoTime();
        for (int query = 0; query < 20000; query++) {
          // Interior probability keeps the timing control independent of the intentional endpoint correction.
          checksum += timingPosterior.pressureQuantiles(0.5)[2];
        }
        Map<String, Object> timing = new LinkedHashMap<String, Object>();
        timing.put("block", block);
        timing.put("warmup", block < 3);
        timing.put("queries", 20000);
        timing.put("seconds", (System.nanoTime() - start) / 1.0e9);
        timing.put("checksum", checksum);
        timings.add(timing);
      }
      try (FileWriter writer = new FileWriter(args[1])) {
        new Gson().toJson(timings, writer);
      }
    }
    if (failures != 0) {
      throw new IllegalStateException(failures + " support endpoint checks failed");
    }
  }
}
