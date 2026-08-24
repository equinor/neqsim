package neqsim.process.mechanicaldesign.distillation;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

/**
 * Comparison of baseline and candidate contactor-internals hydraulic capacity.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ContactorCapacityComparison implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Baseline capacity result. */
  private final ContactorCapacityResult baseline;
  /** Candidate capacity result. */
  private final ContactorCapacityResult candidate;
  /** Estimated candidate capacity increase relative to baseline [%]. */
  private final double estimatedCapacityIncreasePercent;

  /**
   * Create a baseline-versus-candidate capacity comparison.
   *
   * @param baseline baseline result
   * @param candidate candidate result
   */
  public ContactorCapacityComparison(ContactorCapacityResult baseline, ContactorCapacityResult candidate) {
    if (baseline == null || candidate == null) {
      throw new IllegalArgumentException("baseline and candidate results must be specified");
    }
    this.baseline = baseline;
    this.candidate = candidate;
    double baselineCapacity = baseline.getEstimatedMaximumGasFlowKgPerHour();
    estimatedCapacityIncreasePercent = baselineCapacity > 0.0
        ? 100.0 * (candidate.getEstimatedMaximumGasFlowKgPerHour() / baselineCapacity - 1.0)
        : Double.NaN;
  }

  /** @return baseline capacity result */
  public ContactorCapacityResult getBaseline() {
    return baseline;
  }

  /** @return candidate capacity result */
  public ContactorCapacityResult getCandidate() {
    return candidate;
  }

  /** @return estimated candidate capacity increase relative to baseline [%] */
  public double getEstimatedCapacityIncreasePercent() {
    return estimatedCapacityIncreasePercent;
  }

  /**
   * Serialize this comparison as pretty-printed JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
