package neqsim.process.ml.controllers;

import java.util.Random;

/**
 * Random controller for baseline comparison.
 *
 * <p>
 * Samples random actions from uniform distribution within bounds.
 *
 * @author ESOL
 * @version 1.0
 */
public class RandomController implements Controller {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final int actionDim;
  private final double[] actionMin;
  private final double[] actionMax;
  private final Random random;

  /**
   * Create random controller for single action.
   *
   * @param name controller name
   * @param actionMin minimum action value
   * @param actionMax maximum action value
   */
  public RandomController(String name, double actionMin, double actionMax) {
    this(name, new double[] {actionMin}, new double[] {actionMax}, System.currentTimeMillis());
  }

  /**
   * Create random controller with seed.
   *
   * @param name controller name
   * @param actionMin minimum action values
   * @param actionMax maximum action values
   * @param seed random seed
   */
  public RandomController(String name, double[] actionMin, double[] actionMax, long seed) {
    this.name = name;
    this.actionDim = actionMin.length;
    this.actionMin = actionMin;
    this.actionMax = actionMax;
    this.random = new Random(seed);
  }

  @Override
  public double[] computeAction(double[] observation) {
    double[] action = new double[actionDim];
    for (int i = 0; i < actionDim; i++) {
      action[i] = actionMin[i] + random.nextDouble() * (actionMax[i] - actionMin[i]);
    }
    return action;
  }

  @Override
  public String getName() {
    return name;
  }
}
