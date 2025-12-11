package neqsim.process.logic.voting;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic voting logic evaluator for redundant sensors or conditions.
 * 
 * <p>
 * Voting logic is used throughout process control and safety systems to increase reliability by
 * requiring multiple independent signals to agree before taking action. This generic implementation
 * supports:
 * <ul>
 * <li>Digital voting (boolean conditions: 1oo2, 2oo3, etc.)</li>
 * <li>Analog voting (continuous values: average, median, mid-value select)</li>
 * <li>Configurable voting patterns</li>
 * <li>Sensor fault detection and exclusion</li>
 * </ul>
 * 
 * <p>
 * Common applications:
 * <ul>
 * <li>Safety systems (HIPPS, Fire &amp; Gas, ESD)</li>
 * <li>Critical process measurements (pressure, temperature, level)</li>
 * <li>Redundant control loops</li>
 * <li>Quality measurements</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Digital voting: 2 out of 3 pressure switches must be high
 * VotingEvaluator&lt;Boolean&gt; pressureVoting = new VotingEvaluator&lt;&gt;(VotingPattern.TWO_OUT_OF_THREE);
 * pressureVoting.addInput(pt1.isHigh(), pt1.isFaulty());
 * pressureVoting.addInput(pt2.isHigh(), pt2.isFaulty());
 * pressureVoting.addInput(pt3.isHigh(), pt3.isFaulty());
 * boolean pressureHigh = pressureVoting.evaluateDigital();
 * 
 * // Analog voting: median of 3 temperature sensors
 * VotingEvaluator&lt;Double&gt; tempVoting = new VotingEvaluator&lt;&gt;(VotingPattern.TWO_OUT_OF_THREE);
 * tempVoting.addInput(tt1.getValue(), tt1.isFaulty());
 * tempVoting.addInput(tt2.getValue(), tt2.isFaulty());
 * tempVoting.addInput(tt3.getValue(), tt3.isFaulty());
 * double temperature = tempVoting.evaluateMedian();
 * </pre>
 *
 * @param <T> type of input values (Boolean for digital, Double for analog)
 * @author ESOL
 * @version 1.0
 */
public class VotingEvaluator<T> {
  private final VotingPattern pattern;
  private final List<VotingInput<T>> inputs = new ArrayList<>();

  /**
   * Creates a voting evaluator with specified pattern.
   *
   * @param pattern voting pattern (1oo1, 1oo2, 2oo2, 2oo3, etc.)
   */
  public VotingEvaluator(VotingPattern pattern) {
    this.pattern = pattern;
  }

  /**
   * Adds an input to the voting group.
   *
   * @param value current value (Boolean or Double)
   * @param faulty true if this input is faulty
   */
  public void addInput(T value, boolean faulty) {
    inputs.add(new VotingInput<>(value, faulty));
  }

  /**
   * Clears all inputs.
   */
  public void clearInputs() {
    inputs.clear();
  }

  /**
   * Evaluates digital (boolean) voting.
   * 
   * <p>
   * Counts how many non-faulty inputs are TRUE and applies voting pattern.
   * </p>
   *
   * @return true if voting condition is met
   * @throws IllegalStateException if inputs are not Boolean type
   */
  public boolean evaluateDigital() {
    int trueCount = 0;
    int validCount = 0;

    for (VotingInput<T> input : inputs) {
      if (!input.faulty) {
        validCount++;
        if (input.value instanceof Boolean && (Boolean) input.value) {
          trueCount++;
        }
      }
    }

    // Check if we have enough valid inputs
    if (validCount < pattern.getTotalSensors()) {
      // Not enough valid sensors - voting degraded
      return false;
    }

    return pattern.evaluate(trueCount);
  }

  /**
   * Evaluates analog voting using median selection.
   * 
   * <p>
   * Returns the median value of non-faulty inputs. Median is preferred over average for safety
   * systems as it's less sensitive to outliers.
   * </p>
   *
   * @return median value
   * @throws IllegalStateException if inputs are not Double type or insufficient valid inputs
   */
  public double evaluateMedian() {
    List<Double> validValues = new ArrayList<>();

    for (VotingInput<T> input : inputs) {
      if (!input.faulty && input.value instanceof Double) {
        validValues.add((Double) input.value);
      }
    }

    if (validValues.isEmpty()) {
      throw new IllegalStateException("No valid inputs for median calculation");
    }

    validValues.sort(Double::compareTo);
    int size = validValues.size();

    if (size % 2 == 0) {
      return (validValues.get(size / 2 - 1) + validValues.get(size / 2)) / 2.0;
    } else {
      return validValues.get(size / 2);
    }
  }

  /**
   * Evaluates analog voting using average.
   *
   * @return average of non-faulty inputs
   * @throws IllegalStateException if no valid inputs
   */
  public double evaluateAverage() {
    double sum = 0.0;
    int count = 0;

    for (VotingInput<T> input : inputs) {
      if (!input.faulty && input.value instanceof Double) {
        sum += (Double) input.value;
        count++;
      }
    }

    if (count == 0) {
      throw new IllegalStateException("No valid inputs for average calculation");
    }

    return sum / count;
  }

  /**
   * Evaluates analog voting using mid-value select.
   * 
   * <p>
   * For 3 inputs, returns the middle value (not highest, not lowest). This provides some outlier
   * rejection like median but is more intuitive for 3-input systems.
   * </p>
   *
   * @return mid-value
   * @throws IllegalStateException if insufficient valid inputs
   */
  public double evaluateMidValue() {
    List<Double> validValues = new ArrayList<>();

    for (VotingInput<T> input : inputs) {
      if (!input.faulty && input.value instanceof Double) {
        validValues.add((Double) input.value);
      }
    }

    if (validValues.size() < 3) {
      throw new IllegalStateException("Mid-value select requires at least 3 valid inputs");
    }

    validValues.sort(Double::compareTo);
    // Return middle value
    return validValues.get(validValues.size() / 2);
  }

  /**
   * Gets the voting pattern.
   *
   * @return voting pattern
   */
  public VotingPattern getPattern() {
    return pattern;
  }

  /**
   * Gets the number of valid (non-faulty) inputs.
   *
   * @return valid input count
   */
  public int getValidInputCount() {
    int count = 0;
    for (VotingInput<T> input : inputs) {
      if (!input.faulty) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets the number of faulty inputs.
   *
   * @return faulty input count
   */
  public int getFaultyInputCount() {
    int count = 0;
    for (VotingInput<T> input : inputs) {
      if (input.faulty) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets the total number of inputs.
   *
   * @return total input count
   */
  public int getTotalInputCount() {
    return inputs.size();
  }

  /**
   * Internal class to store voting input with fault status.
   *
   * @param <T> the type of value stored in this voting input
   */
  private static class VotingInput<T> {
    final T value;
    final boolean faulty;

    VotingInput(T value, boolean faulty) {
      this.value = value;
      this.faulty = faulty;
    }
  }
}
