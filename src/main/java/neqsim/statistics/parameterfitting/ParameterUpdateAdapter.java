package neqsim.statistics.parameterfitting;

import java.io.Serializable;

/**
 * Applies fitted physical parameter values to a NeqSim model or fitting function.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public interface ParameterUpdateAdapter extends Serializable {
  /**
   * Returns parameter definitions handled by this adapter.
   *
   * @return parameter definitions in fitting order
   */
  FittingParameter[] getParameters();

  /**
   * Applies physical parameter values to the target model.
   *
   * @param parameterValues physical parameter values in fitting order
   */
  void applyParameters(double[] parameterValues);
}
