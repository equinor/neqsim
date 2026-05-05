package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;

/**
 * Simple debottleneck investment decision derived from a tie-in capacity study.
 *
 * @author ESOL
 * @version 1.0
 */
public final class DebottleneckDecision implements Serializable, Comparable<DebottleneckDecision> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Bottleneck or capacity category addressed by the decision. */
  private final String bottleneckName;

  /** Decision description. */
  private final String description;

  /** Estimated investment in million USD. */
  private final double capexMusd;

  /** Estimated recoverable deferred value in million USD. */
  private final double recoveredValueMusd;

  /** Net present value after investment in million USD. */
  private final double npvMusd;

  /** Simple payback period in years. */
  private final double paybackYears;

  /** True when NPV is positive. */
  private final boolean recommended;

  /**
   * Creates a debottleneck decision.
   *
   * @param bottleneckName bottleneck or capacity category
   * @param description decision description
   * @param capexMusd estimated CAPEX in MUSD
   * @param recoveredValueMusd recovered value in MUSD
   * @param npvMusd net present value in MUSD
   * @param paybackYears simple payback period in years
   * @param recommended true if the decision is economically attractive
   */
  public DebottleneckDecision(String bottleneckName, String description, double capexMusd,
      double recoveredValueMusd, double npvMusd, double paybackYears, boolean recommended) {
    this.bottleneckName = bottleneckName;
    this.description = description;
    this.capexMusd = capexMusd;
    this.recoveredValueMusd = recoveredValueMusd;
    this.npvMusd = npvMusd;
    this.paybackYears = paybackYears;
    this.recommended = recommended;
  }

  /**
   * Gets the bottleneck name.
   *
   * @return bottleneck name
   */
  public String getBottleneckName() {
    return bottleneckName;
  }

  /**
   * Gets the decision description.
   *
   * @return decision description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets CAPEX.
   *
   * @return CAPEX in MUSD
   */
  public double getCapexMusd() {
    return capexMusd;
  }

  /**
   * Gets recovered value.
   *
   * @return recovered value in MUSD
   */
  public double getRecoveredValueMusd() {
    return recoveredValueMusd;
  }

  /**
   * Gets NPV.
   *
   * @return NPV in MUSD
   */
  public double getNpvMusd() {
    return npvMusd;
  }

  /**
   * Gets payback period.
   *
   * @return payback in years
   */
  public double getPaybackYears() {
    return paybackYears;
  }

  /**
   * Checks if the decision is recommended.
   *
   * @return true if NPV is positive
   */
  public boolean isRecommended() {
    return recommended;
  }

  @Override
  public int compareTo(DebottleneckDecision other) {
    return Double.compare(other.npvMusd, this.npvMusd);
  }
}
