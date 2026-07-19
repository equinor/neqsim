package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Traceable debottleneck candidates derived from requested and admitted lifecycle operating points. */
public final class FacilityModificationPlan implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** One capacity or equipment modification candidate. */
  public static final class Candidate implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int firstRequiredYear;
    private final String bottleneck;
    private final String modificationScope;
    private final double peakRequestedUtilization;
    private final double requiredCapacityMultiplier;
    private final double associatedDeferredOilSm3;

    Candidate(int firstRequiredYear, String bottleneck, String modificationScope,
        double peakRequestedUtilization, double requiredCapacityMultiplier,
        double associatedDeferredOilSm3) {
      this.firstRequiredYear = firstRequiredYear;
      this.bottleneck = bottleneck;
      this.modificationScope = modificationScope;
      this.peakRequestedUtilization = peakRequestedUtilization;
      this.requiredCapacityMultiplier = requiredCapacityMultiplier;
      this.associatedDeferredOilSm3 = associatedDeferredOilSm3;
    }

    /** Returns first calendar year when the modification is indicated. */
    public int getFirstRequiredYear() {
      return firstRequiredYear;
    }

    /** Returns the nameplate constraint or detailed equipment name. */
    public String getBottleneck() {
      return bottleneck;
    }

    /** Returns a screening-level modification scope for engineering follow-up. */
    public String getModificationScope() {
      return modificationScope;
    }

    /** Returns peak requested utilization before rate reduction. */
    public double getPeakRequestedUtilization() {
      return peakRequestedUtilization;
    }

    /** Returns capacity multiplier required to meet the planner's target utilization. */
    public double getRequiredCapacityMultiplier() {
      return requiredCapacityMultiplier;
    }

    /** Returns deferred new-field oil associated with years where this bottleneck is active. */
    public double getAssociatedDeferredOilSm3() {
      return associatedDeferredOilSm3;
    }
  }

  private final String conceptName;
  private final double targetUtilization;
  private final List<Candidate> candidates;

  FacilityModificationPlan(String conceptName, double targetUtilization,
      List<Candidate> candidates) {
    this.conceptName = conceptName;
    this.targetUtilization = targetUtilization;
    this.candidates = Collections.unmodifiableList(new ArrayList<Candidate>(candidates));
  }

  /** Returns evaluated concept or host-route name. */
  public String getConceptName() {
    return conceptName;
  }

  /** Returns planner target utilization as a fraction. */
  public double getTargetUtilization() {
    return targetUtilization;
  }

  /** Returns immutable modification candidates in first-required-year order. */
  public List<Candidate> getCandidates() {
    return candidates;
  }

  /** Returns whether at least one modification candidate was identified. */
  public boolean hasCandidates() {
    return !candidates.isEmpty();
  }

  /** Returns a Markdown table for concept-select and brownfield review. */
  public String toMarkdownTable() {
    StringBuilder table = new StringBuilder();
    table.append("| First year | Bottleneck | Screening modification scope | Requested utilization ");
    table.append("| Capacity multiplier | Deferred oil (MSm3) |\n");
    table.append("|---:|---|---|---:|---:|---:|\n");
    for (Candidate candidate : candidates) {
      table.append(String.format("| %d | %s | %s | %.1f%% | %.2f | %.2f |%n",
          candidate.getFirstRequiredYear(), candidate.getBottleneck(),
          candidate.getModificationScope(), candidate.getPeakRequestedUtilization() * 100.0,
          candidate.getRequiredCapacityMultiplier(),
          candidate.getAssociatedDeferredOilSm3() / 1.0e6));
    }
    return table.toString();
  }
}
