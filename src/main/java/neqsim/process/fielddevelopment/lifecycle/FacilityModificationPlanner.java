package neqsim.process.fielddevelopment.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import neqsim.process.fielddevelopment.lifecycle.FacilityModificationPlan.Candidate;
import neqsim.process.fielddevelopment.lifecycle.FieldLifecycleResult.AnnualResult;

/** Creates screening modification candidates from lifecycle bottleneck and deferment evidence. */
public final class FacilityModificationPlanner {

  /**
   * Identifies nameplate and detailed-equipment debottleneck candidates.
   *
   * <p>
   * The multiplier is a screening target. Each candidate must be implemented in a cloned detailed {@code ProcessSystem}
   * or {@code ProcessModel}, mechanically sized, costed, and rerun as a new lifecycle option before selection.
   * </p>
   *
   * @param result evaluated field lifecycle
   * @param targetUtilization desired maximum utilization, greater than zero and at most one
   * @return traceable modification plan
   */
  public FacilityModificationPlan analyse(FieldLifecycleResult result, double targetUtilization) {
    if (result == null) {
      throw new IllegalArgumentException("field lifecycle result is required");
    }
    if (!(targetUtilization > 0.0) || targetUtilization > 1.0 || !Double.isFinite(targetUtilization)) {
      throw new IllegalArgumentException("target utilization must be finite, positive, and at most one");
    }

    Map<String, MutableCandidate> grouped = new LinkedHashMap<String, MutableCandidate>();
    for (AnnualResult annual : result.getAnnualResults()) {
      double requestedUtilization = annual.getUnconstrainedFacilityUtilization();
      if (requestedUtilization <= targetUtilization + 1.0e-9 && annual.getCapacityDeferredOilSm3() <= 1.0e-9) {
        continue;
      }
      String bottleneck = annual.getUnconstrainedBottleneck();
      if (bottleneck == null || bottleneck.trim().isEmpty()) {
        bottleneck = annual.getPrimaryBottleneck();
      }
      if (bottleneck == null || bottleneck.trim().isEmpty()) {
        bottleneck = "shared facility capacity";
      }
      MutableCandidate candidate = grouped.get(bottleneck);
      if (candidate == null) {
        candidate = new MutableCandidate(annual.getYear(), bottleneck);
        grouped.put(bottleneck, candidate);
      }
      candidate.peakRequestedUtilization = Math.max(candidate.peakRequestedUtilization, requestedUtilization);
      candidate.deferredOilSm3 += annual.getCapacityDeferredOilSm3();
    }

    List<Candidate> candidates = new ArrayList<Candidate>();
    for (MutableCandidate candidate : grouped.values()) {
      double multiplier = Math.max(1.0, candidate.peakRequestedUtilization / targetUtilization);
      candidates.add(new Candidate(candidate.firstYear, candidate.bottleneck, modificationScope(candidate.bottleneck),
          candidate.peakRequestedUtilization, multiplier, candidate.deferredOilSm3));
    }
    return new FacilityModificationPlan(result.getConceptName(), targetUtilization, candidates);
  }

  private static String modificationScope(String bottleneck) {
    String normalized = bottleneck.toLowerCase(Locale.ROOT);
    if (normalized.contains("surf") || normalized.contains("flowline") || normalized.contains("pipeline")
        || normalized.contains("riser") || normalized.contains("subsea")) {
      return "rerate the shared SURF route; evaluate pressure support, looping, a new riser, or an alternate tie-in";
    }
    if (normalized.contains("water") || normalized.contains("hydrocyclone")) {
      return "add or upgrade produced-water separation/treatment train";
    }
    if (normalized.contains("power")) {
      return "increase power generation/import and verify electrical distribution";
    }
    if (normalized.contains("gas") || normalized.contains("compressor")) {
      return "rerate or add compression, cooling, treatment and gas-export capacity";
    }
    if (normalized.contains("oil")) {
      return "increase separation, stabilization, pumping and oil-export capacity";
    }
    if (normalized.contains("liquid") || normalized.contains("separator")) {
      return "increase inlet/slug and liquid-handling capacity or add a parallel train";
    }
    return "rerate named equipment or evaluate a parallel process train";
  }

  private static final class MutableCandidate {
    private final int firstYear;
    private final String bottleneck;
    private double peakRequestedUtilization;
    private double deferredOilSm3;

    private MutableCandidate(int firstYear, String bottleneck) {
      this.firstYear = firstYear;
      this.bottleneck = bottleneck;
    }
  }
}
