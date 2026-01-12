package neqsim.process.processmodel.diagram;

/**
 * Defines the level of detail to include in process flow diagrams.
 *
 * <p>
 * Different detail levels are appropriate for different audiences and use cases:
 * </p>
 * <ul>
 * <li><b>CONCEPTUAL</b> - High-level overview for teaching, AI agents, documentation</li>
 * <li><b>ENGINEERING</b> - Full PFD with all process data, suitable for engineering review</li>
 * <li><b>DEBUG</b> - Maximum detail including solver info, stream compositions, etc.</li>
 * </ul>
 *
 * <p>
 * This mirrors modes in commercial simulators like UniSim and Aspen HYSYS.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public enum DiagramDetailLevel {
  /**
   * Conceptual level - clean, simplified diagram.
   *
   * <p>
   * Shows:
   * </p>
   * <ul>
   * <li>Equipment names and types</li>
   * <li>Stream connections</li>
   * <li>Phase indicators (gas/liquid)</li>
   * </ul>
   *
   * <p>
   * Hides:
   * </p>
   * <ul>
   * <li>Process conditions (T, P)</li>
   * <li>Flow rates</li>
   * <li>Compositions</li>
   * <li>Equipment specifications</li>
   * </ul>
   *
   * <p>
   * Use for: Teaching, AI agents, high-level documentation
   * </p>
   */
  CONCEPTUAL(false, false, false, false, true),

  /**
   * Engineering level - full PFD with process data.
   *
   * <p>
   * Shows:
   * </p>
   * <ul>
   * <li>Everything in CONCEPTUAL</li>
   * <li>Temperature and pressure</li>
   * <li>Flow rates</li>
   * <li>Equipment specifications</li>
   * <li>Phase fractions</li>
   * </ul>
   *
   * <p>
   * Hides:
   * </p>
   * <ul>
   * <li>Full compositions</li>
   * <li>Solver diagnostics</li>
   * </ul>
   *
   * <p>
   * Use for: Engineering review, process design
   * </p>
   */
  ENGINEERING(true, true, false, true, true),

  /**
   * Debug level - maximum detail for troubleshooting.
   *
   * <p>
   * Shows:
   * </p>
   * <ul>
   * <li>Everything in ENGINEERING</li>
   * <li>Full stream compositions</li>
   * <li>Solver convergence info</li>
   * <li>Calculation order</li>
   * <li>Recycle loop indicators</li>
   * <li>All internal streams</li>
   * </ul>
   *
   * <p>
   * Use for: Debugging, solver troubleshooting
   * </p>
   */
  DEBUG(true, true, true, true, false);

  /** Whether to show process conditions (T, P). */
  private final boolean showConditions;

  /** Whether to show flow rates. */
  private final boolean showFlowRates;

  /** Whether to show stream compositions. */
  private final boolean showCompositions;

  /** Whether to show equipment specifications. */
  private final boolean showSpecifications;

  /** Whether to use compact node labels. */
  private final boolean compactLabels;

  /**
   * Constructor for DiagramDetailLevel.
   *
   * @param showConditions whether to show T, P conditions
   * @param showFlowRates whether to show flow rates
   * @param showCompositions whether to show compositions
   * @param showSpecifications whether to show equipment specs
   * @param compactLabels whether to use compact labels
   */
  DiagramDetailLevel(boolean showConditions, boolean showFlowRates, boolean showCompositions,
      boolean showSpecifications, boolean compactLabels) {
    this.showConditions = showConditions;
    this.showFlowRates = showFlowRates;
    this.showCompositions = showCompositions;
    this.showSpecifications = showSpecifications;
    this.compactLabels = compactLabels;
  }

  /**
   * Whether to show process conditions (temperature, pressure).
   *
   * @return true if conditions should be shown
   */
  public boolean showConditions() {
    return showConditions;
  }

  /**
   * Whether to show flow rates.
   *
   * @return true if flow rates should be shown
   */
  public boolean showFlowRates() {
    return showFlowRates;
  }

  /**
   * Whether to show stream compositions.
   *
   * @return true if compositions should be shown
   */
  public boolean showCompositions() {
    return showCompositions;
  }

  /**
   * Whether to show equipment specifications.
   *
   * @return true if specifications should be shown
   */
  public boolean showSpecifications() {
    return showSpecifications;
  }

  /**
   * Whether to use compact node labels.
   *
   * @return true if compact labels should be used
   */
  public boolean useCompactLabels() {
    return compactLabels;
  }
}
