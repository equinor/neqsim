package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * An ordered sequence of restart steps to bring a process system back to normal operation.
 *
 * <p>
 * Steps are executed in order. Each step can have a duration, ramp rate, or condition to wait for
 * before proceeding to the next step. The estimated total duration is the sum of all step durations
 * plus ramp times.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RestartSequence implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final String name;
  private final List<RestartStep> steps;

  /**
   * Constructs a named restart sequence.
   *
   * @param name sequence name (e.g. "Post-Compressor-Trip Restart")
   */
  public RestartSequence(String name) {
    this.name = name;
    this.steps = new ArrayList<>();
  }

  /**
   * Returns the sequence name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Adds a step to the sequence.
   *
   * @param step the step to add
   */
  public void addStep(RestartStep step) {
    steps.add(step);
  }

  /**
   * Returns the ordered list of steps.
   *
   * @return unmodifiable list of steps
   */
  public List<RestartStep> getSteps() {
    return Collections.unmodifiableList(steps);
  }

  /**
   * Returns the number of steps.
   *
   * @return step count
   */
  public int size() {
    return steps.size();
  }

  /**
   * Returns the next uncompleted step, or null if all steps are done.
   *
   * @return next step to execute
   */
  public RestartStep getNextStep() {
    for (RestartStep step : steps) {
      if (!step.isCompleted()) {
        return step;
      }
    }
    return null;
  }

  /**
   * Returns whether all steps have been completed.
   *
   * @return true if all steps are completed
   */
  public boolean isComplete() {
    for (RestartStep step : steps) {
      if (!step.isCompleted()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the estimated total duration of the sequence in seconds.
   *
   * <p>
   * Sums all step durations. For steps with ramp rates, estimates the time based on target value /
   * ramp rate as a rough approximation.
   * </p>
   *
   * @return estimated duration in seconds
   */
  public double getEstimatedDurationSeconds() {
    double total = 0.0;
    for (RestartStep step : steps) {
      if (step.getDurationSeconds() > 0) {
        total += step.getDurationSeconds();
      } else if (step.getRampRate() > 0 && Math.abs(step.getTargetValue()) > 0) {
        total += Math.abs(step.getTargetValue()) / step.getRampRate();
      }
    }
    return total;
  }

  /**
   * Returns the estimated total duration in minutes.
   *
   * @return estimated duration in minutes
   */
  public double getEstimatedDurationMinutes() {
    return getEstimatedDurationSeconds() / 60.0;
  }

  /**
   * Serialises the sequence to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("name", name);
    map.put("stepCount", steps.size());
    map.put("estimatedDuration_min", getEstimatedDurationMinutes());

    List<Map<String, Object>> stepList = new ArrayList<>();
    for (RestartStep step : steps) {
      stepList.add(step.toMap());
    }
    map.put("steps", stepList);
    return GSON.toJson(map);
  }

  @Override
  public String toString() {
    return String.format("RestartSequence{name='%s', steps=%d, estimatedMinutes=%.1f}", name,
        steps.size(), getEstimatedDurationMinutes());
  }
}
