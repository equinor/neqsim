package neqsim.process.logic.condition;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicCondition;

/**
 * Condition that becomes true after a specified time delay.
 * 
 * <p>
 * Used for:
 * <ul>
 * <li>Minimum wait times between steps</li>
 * <li>Equipment warm-up periods</li>
 * <li>Stabilization delays</li>
 * <li>Timeout detection</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Wait 30 seconds before starting pump
 * TimerCondition warmUp = new TimerCondition(30.0);
 * warmUp.start();
 * 
 * // In simulation loop
 * warmUp.update(timeStep);
 * if (warmUp.evaluate()) {
 *   // 30 seconds elapsed
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class TimerCondition implements LogicCondition {
  private final double duration; // seconds
  private double elapsed = 0.0;
  private boolean started = false;

  /**
   * Creates a timer condition.
   *
   * @param duration duration in seconds
   */
  public TimerCondition(double duration) {
    this.duration = duration;
  }

  /**
   * Starts the timer.
   */
  public void start() {
    started = true;
    elapsed = 0.0;
  }

  /**
   * Resets the timer.
   */
  public void reset() {
    started = false;
    elapsed = 0.0;
  }

  /**
   * Updates the timer with a time step.
   *
   * @param timeStep time step in seconds
   */
  public void update(double timeStep) {
    if (started) {
      elapsed += timeStep;
    }
  }

  @Override
  public boolean evaluate() {
    return started && elapsed >= duration;
  }

  /**
   * Gets the elapsed time.
   *
   * @return elapsed time in seconds
   */
  public double getElapsed() {
    return elapsed;
  }

  /**
   * Gets the remaining time.
   *
   * @return remaining time in seconds, or 0 if complete
   */
  public double getRemaining() {
    return Math.max(0.0, duration - elapsed);
  }

  @Override
  public String getDescription() {
    return String.format("Wait %.1f seconds", duration);
  }

  @Override
  public ProcessEquipmentInterface getTargetEquipment() {
    return null;
  }

  @Override
  public String getCurrentValue() {
    return String.format("%.1f s", elapsed);
  }

  @Override
  public String getExpectedValue() {
    return String.format("%.1f s", duration);
  }
}
