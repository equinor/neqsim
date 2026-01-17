package neqsim.process.util.optimization;

import java.util.function.ToDoubleFunction;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Interface representing an optimization objective for multi-objective optimization.
 *
 * <p>
 * An objective function evaluates a process system and returns a scalar value to be optimized
 * (either maximized or minimized). Multiple objectives can be combined for Pareto optimization.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public interface ObjectiveFunction {

  /**
   * Direction of optimization.
   */
  enum Direction {
    /** Maximize the objective value. */
    MAXIMIZE,
    /** Minimize the objective value. */
    MINIMIZE
  }

  /**
   * Get the name of this objective for reporting.
   *
   * @return objective name
   */
  String getName();

  /**
   * Get the optimization direction.
   *
   * @return MAXIMIZE or MINIMIZE
   */
  Direction getDirection();

  /**
   * Evaluate the objective for the current process state.
   *
   * @param process the process system to evaluate
   * @return the objective value
   */
  double evaluate(ProcessSystem process);

  /**
   * Get the unit of the objective value for display.
   *
   * @return unit string (e.g., "kW", "kg/hr", "%")
   */
  String getUnit();

  /**
   * Get normalized value for comparison (converts MINIMIZE to negative for consistent sorting).
   *
   * @param process the process system to evaluate
   * @return normalized value where higher is always better
   */
  default double evaluateNormalized(ProcessSystem process) {
    double value = evaluate(process);
    return getDirection() == Direction.MAXIMIZE ? value : -value;
  }

  /**
   * Create a custom objective function.
   *
   * @param name objective name
   * @param evaluator function to evaluate the objective
   * @param direction optimization direction
   * @param unit unit string
   * @return new ObjectiveFunction instance
   */
  static ObjectiveFunction create(String name, ToDoubleFunction<ProcessSystem> evaluator,
      Direction direction, String unit) {
    return new ObjectiveFunction() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Direction getDirection() {
        return direction;
      }

      @Override
      public double evaluate(ProcessSystem process) {
        return evaluator.applyAsDouble(process);
      }

      @Override
      public String getUnit() {
        return unit;
      }
    };
  }
}
