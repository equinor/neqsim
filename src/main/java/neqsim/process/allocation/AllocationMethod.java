package neqsim.process.allocation;

/**
 * The family of production-allocation methods that {@link MultiMethodAllocator} can run and compare.
 *
 * <p>
 * The three methods form a fidelity-versus-cost ladder. All three are run on a common component slate and, when mass
 * closure is enforced, partition exactly the same measured (commingled) custody totals so that they can be compared on
 * a fair basis:
 * </p>
 *
 * <ul>
 * <li><b>{@link #COMPONENT_RATIO}</b> — a single common overall recovery factor per component and custody outlet,
 * extracted from the commingled base case and applied to every source. Cheapest; correct when one shared per-component
 * split is adequate for all sources.</li>
 * <li><b>{@link #ALL_IN}</b> — linearised per-equipment recovery factors propagated through the flowsheet by
 * superposition (the linear recovery-factor proxy network of {@link SourceAllocator}). Captures source-dependent
 * routing through the network; one base-case run plus cheap linear solves.</li>
 * <li><b>{@link #STAND_ALONE}</b> — each source is run through the process alone (the other feeds scaled to a near-zero
 * multiplier), and the resulting standalone custody distributions are renormalised to the measured commingled totals.
 * Captures the non-linear compositional coupling of commingling; most faithful but most expensive, requiring one
 * rigorous process run per source.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public enum AllocationMethod {

  /**
   * Common overall recovery factor: one shared per-component, per-custody recovery factor extracted from the commingled
   * base case and applied to every source. Relative cost 1 (no extra simulation).
   */
  COMPONENT_RATIO("Component ratio (common overall recovery factor)",
      "Applies a single common per-component recovery factor, taken from the commingled base case, to every source.",
      1),

  /**
   * Linearised per-equipment recovery factors propagated through the flowsheet by superposition (the linear proxy
   * network). Relative cost 2 (one base-case run plus linear solves).
   */
  ALL_IN("All-in (linearised per-equipment recovery factors)",
      "Freezes per-unit per-component split factors from the commingled base case and propagates each source through "
          + "the network by superposition.",
      2),

  /**
   * Isolated per-source re-simulation: run the process once per source with the other feeds suppressed, then
   * renormalise the standalone custody distributions to the measured commingled totals. Relative cost scales with the
   * number of sources.
   */
  STAND_ALONE("Stand-alone (isolated per-source re-simulation)",
      "Runs the process once per source with the other feeds suppressed, then renormalises the standalone custody "
          + "distributions to the measured commingled totals.",
      3);

  /** Human-readable display label. */
  private final String displayName;

  /** One-line description of the method. */
  private final String description;

  /** Relative computational cost rank (1 = cheapest). */
  private final int relativeCostRank;

  /**
   * Creates an allocation-method enum constant.
   *
   * @param displayName the human-readable label; must be non-null
   * @param description a one-line description of the method; must be non-null
   * @param relativeCostRank the relative cost rank (1 = cheapest, larger = more expensive)
   */
  AllocationMethod(String displayName, String description, int relativeCostRank) {
    this.displayName = displayName;
    this.description = description;
    this.relativeCostRank = relativeCostRank;
  }

  /**
   * Gets the human-readable display label of the method.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets the one-line description of the method.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the relative computational cost rank (1 = cheapest, larger = more expensive). For {@link #STAND_ALONE} the
   * actual cost additionally scales with the number of sources.
   *
   * @return the relative cost rank
   */
  public int getRelativeCostRank() {
    return relativeCostRank;
  }
}
