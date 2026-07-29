package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Evaluates a scaled hard or soft network constraint.
 */
public interface NetworkConstraint extends Serializable {
  /**
   * Get constraint name.
   *
   * @return name
   */
  String getName();

  /**
   * Check whether the constraint is hard.
   *
   * @return true for a hard feasibility constraint
   */
  boolean isHard();

  /**
   * Evaluate on the current solved network state.
   *
   * @param network network
   * @return constraint result with non-negative violation residual
   */
  NetworkConstraintResult evaluate(LoopedPipeNetwork network);
}
