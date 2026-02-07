package neqsim.process.util.optimizer;

import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Adapts equipment-level {@link CapacityConstraint} instances to the unified
 * {@link ProcessConstraint} interface.
 *
 * <p>
 * This adapter bridges the equipment capacity layer with the optimization constraint layer,
 * allowing equipment-level physical limits (compressor surge, separator flooding, pipe velocity,
 * etc.) to be consumed by any optimizer — both internal NeqSim optimizers and external solvers such
 * as SciPy, NLopt, or Pyomo.
 * </p>
 *
 * <p>
 * <strong>Margin convention:</strong> A positive margin means the constraint is satisfied (within
 * capacity). Specifically, margin = 1.0 - utilization, where utilization is currentValue /
 * designValue.
 * </p>
 *
 * <p>
 * <strong>Example:</strong>
 * </p>
 *
 * <pre>
 * // Wrap an equipment constraint for use with any optimizer
 * CapacityConstraint speedLimit = compressor.getCapacityConstraints().get("speed");
 * ProcessConstraint unified = new CapacityConstraintAdapter("Compressor1/speed", speedLimit);
 *
 * double margin = unified.margin(processSystem); // positive = ok
 * boolean ok = unified.isSatisfied(processSystem); // true if within limit
 * ConstraintSeverityLevel level = unified.getSeverityLevel(); // HARD, SOFT, etc.
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ProcessConstraint
 * @see CapacityConstraint
 */
public class CapacityConstraintAdapter implements ProcessConstraint {

  /** The wrapped equipment capacity constraint. */
  private final CapacityConstraint delegate;

  /** Qualified name (typically "equipmentName/constraintName"). */
  private final String qualifiedName;

  /** Default penalty weight for capacity constraints. */
  private static final double DEFAULT_PENALTY_WEIGHT = 100.0;

  /** Custom penalty weight (negative means use default). */
  private double penaltyWeight = -1.0;

  /**
   * Creates an adapter wrapping the given capacity constraint.
   *
   * @param qualifiedName qualified name for this constraint (e.g., "Compressor1/speed")
   * @param delegate the equipment capacity constraint to wrap
   * @throws IllegalArgumentException if qualifiedName or delegate is null
   */
  public CapacityConstraintAdapter(String qualifiedName, CapacityConstraint delegate) {
    if (qualifiedName == null) {
      throw new IllegalArgumentException("qualifiedName must not be null");
    }
    if (delegate == null) {
      throw new IllegalArgumentException("delegate constraint must not be null");
    }
    this.qualifiedName = qualifiedName;
    this.delegate = delegate;
  }

  /**
   * Creates an adapter with a custom penalty weight.
   *
   * @param qualifiedName qualified name for this constraint
   * @param delegate the equipment capacity constraint to wrap
   * @param penaltyWeight custom penalty weight (must be non-negative)
   * @throws IllegalArgumentException if arguments are invalid
   */
  public CapacityConstraintAdapter(String qualifiedName, CapacityConstraint delegate,
      double penaltyWeight) {
    this(qualifiedName, delegate);
    if (penaltyWeight < 0) {
      throw new IllegalArgumentException("penaltyWeight must be non-negative");
    }
    this.penaltyWeight = penaltyWeight;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns the qualified name (e.g., "Compressor1/speed").
   * </p>
   */
  @Override
  public String getName() {
    return qualifiedName;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Margin is computed as {@code 1.0 - utilization}, where utilization is the equipment's current
   * operating point relative to its design value. A value of 0.2 means 20% headroom remains.
   * </p>
   *
   * @param process the process system (not used for value lookup, since the underlying
   *        {@link CapacityConstraint} has its own value supplier; the process must have been run so
   *        equipment state is current)
   * @return margin (positive = satisfied, negative = violated)
   */
  @Override
  public double margin(ProcessSystem process) {
    return delegate.getMargin();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Maps from the 4-level {@link CapacityConstraint.ConstraintSeverity} to the unified
   * {@link ConstraintSeverityLevel}.
   * </p>
   */
  @Override
  public ConstraintSeverityLevel getSeverityLevel() {
    return ConstraintSeverityLevel.fromCapacitySeverity(delegate.getSeverity());
  }

  /** {@inheritDoc} */
  @Override
  public double getPenaltyWeight() {
    return penaltyWeight >= 0 ? penaltyWeight : DEFAULT_PENALTY_WEIGHT;
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return delegate.getDescription();
  }

  /**
   * Returns the underlying equipment capacity constraint.
   *
   * @return the wrapped CapacityConstraint
   */
  public CapacityConstraint getDelegate() {
    return delegate;
  }

  /**
   * Returns the current utilization of the equipment for this constraint.
   *
   * @return utilization as fraction (1.0 = 100% of design)
   */
  public double getUtilization() {
    return delegate.getUtilization();
  }

  /**
   * Returns the unit of measurement for the underlying constraint.
   *
   * @return unit string
   */
  public String getUnit() {
    return delegate.getUnit();
  }
}
