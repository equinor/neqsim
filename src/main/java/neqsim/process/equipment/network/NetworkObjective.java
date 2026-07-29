package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Composable network optimization objective term.
 */
public interface NetworkObjective extends Serializable {
  /**
   * Get term name.
   *
   * @return name
   */
  String getName();

  /**
   * Get scalarization weight.
   *
   * @return weight
   */
  double getWeight();

  /**
   * Evaluate the current solved state.
   *
   * <p>
   * Larger weighted values are always preferred. Minimization terms should return a negative cost.
   * </p>
   *
   * @param network network
   * @return unweighted term value
   */
  double evaluate(LoopedPipeNetwork network);
}
