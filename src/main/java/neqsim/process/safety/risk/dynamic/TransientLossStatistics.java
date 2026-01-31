package neqsim.process.safety.risk.dynamic;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Statistics for transient production losses.
 *
 * <p>
 * Tracks cumulative statistics for transient losses including:
 * </p>
 * <ul>
 * <li>Shutdown transient losses</li>
 * <li>Ramp-up transient losses</li>
 * <li>Total transient contribution</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TransientLossStatistics implements Serializable {

  private static final long serialVersionUID = 1000L;

  // Cumulative statistics
  private double totalShutdownLoss;
  private double totalRampUpLoss;
  private double totalSteadyStateLoss;
  private double totalTransientLoss;
  private double totalLoss;

  // Fractions
  private double shutdownFraction;
  private double rampUpFraction;
  private double steadyStateFraction;
  private double transientFraction;

  // Event counts
  private int shutdownEventCount;
  private int rampUpEventCount;
  private int totalEventCount;

  // Mean values
  private double meanShutdownLoss;
  private double meanRampUpLoss;
  private double meanTransientLoss;

  /**
   * Creates empty transient loss statistics.
   */
  public TransientLossStatistics() {
    reset();
  }

  /**
   * Resets all statistics to zero.
   */
  public void reset() {
    totalShutdownLoss = 0;
    totalRampUpLoss = 0;
    totalSteadyStateLoss = 0;
    totalTransientLoss = 0;
    totalLoss = 0;

    shutdownFraction = 0;
    rampUpFraction = 0;
    steadyStateFraction = 0;
    transientFraction = 0;

    shutdownEventCount = 0;
    rampUpEventCount = 0;
    totalEventCount = 0;

    meanShutdownLoss = 0;
    meanRampUpLoss = 0;
    meanTransientLoss = 0;
  }

  /**
   * Updates statistics from a dynamic risk result.
   *
   * @param result dynamic risk result
   */
  public void update(DynamicRiskResult result) {
    totalTransientLoss = result.getMeanTransientLoss() * result.getIterations();
    totalSteadyStateLoss = result.getMeanSteadyStateLoss() * result.getIterations();
    totalLoss = totalTransientLoss + totalSteadyStateLoss;
    totalEventCount = result.getTotalTransientEvents();

    transientFraction = result.getTransientLossFraction();
    steadyStateFraction = 1.0 - transientFraction;

    meanTransientLoss = result.getMeanTransientLoss();
    meanShutdownLoss = meanTransientLoss * 0.4; // Approximate split
    meanRampUpLoss = meanTransientLoss * 0.6;
  }

  /**
   * Adds a production profile to cumulative statistics.
   *
   * @param profile production profile to add
   */
  public void addProfile(ProductionProfile profile) {
    totalShutdownLoss += profile.getShutdownTransientLoss();
    totalRampUpLoss += profile.getRampUpTransientLoss();
    totalSteadyStateLoss += profile.getSteadyStateLoss();
    totalTransientLoss += profile.getTotalTransientLoss();
    totalLoss += profile.getTotalLoss();

    if (profile.getShutdownTransientLoss() > 0) {
      shutdownEventCount++;
    }
    if (profile.getRampUpTransientLoss() > 0) {
      rampUpEventCount++;
    }
    totalEventCount++;

    // Update fractions
    if (totalLoss > 0) {
      shutdownFraction = totalShutdownLoss / totalLoss;
      rampUpFraction = totalRampUpLoss / totalLoss;
      steadyStateFraction = totalSteadyStateLoss / totalLoss;
      transientFraction = totalTransientLoss / totalLoss;
    }

    // Update means
    if (shutdownEventCount > 0) {
      meanShutdownLoss = totalShutdownLoss / shutdownEventCount;
    }
    if (rampUpEventCount > 0) {
      meanRampUpLoss = totalRampUpLoss / rampUpEventCount;
    }
    if (totalEventCount > 0) {
      meanTransientLoss = totalTransientLoss / totalEventCount;
    }
  }

  // Getters

  /**
   * Gets total shutdown transient loss.
   *
   * @return shutdown loss in kg
   */
  public double getTotalShutdownLoss() {
    return totalShutdownLoss;
  }

  /**
   * Gets total ramp-up transient loss.
   *
   * @return ramp-up loss in kg
   */
  public double getTotalRampUpLoss() {
    return totalRampUpLoss;
  }

  /**
   * Gets total steady-state loss.
   *
   * @return steady-state loss in kg
   */
  public double getTotalSteadyStateLoss() {
    return totalSteadyStateLoss;
  }

  /**
   * Gets total transient loss.
   *
   * @return transient loss in kg
   */
  public double getTotalTransientLoss() {
    return totalTransientLoss;
  }

  /**
   * Gets total loss.
   *
   * @return total loss in kg
   */
  public double getTotalLoss() {
    return totalLoss;
  }

  /**
   * Gets shutdown fraction of total loss.
   *
   * @return shutdown fraction (0-1)
   */
  public double getShutdownFraction() {
    return shutdownFraction;
  }

  /**
   * Gets ramp-up fraction of total loss.
   *
   * @return ramp-up fraction (0-1)
   */
  public double getRampUpFraction() {
    return rampUpFraction;
  }

  /**
   * Gets steady-state fraction of total loss.
   *
   * @return steady-state fraction (0-1)
   */
  public double getSteadyStateFraction() {
    return steadyStateFraction;
  }

  /**
   * Gets transient fraction of total loss.
   *
   * @return transient fraction (0-1)
   */
  public double getTransientFraction() {
    return transientFraction;
  }

  /**
   * Gets transient percentage of total loss.
   *
   * @return transient percentage (0-100)
   */
  public double getTransientPercent() {
    return transientFraction * 100.0;
  }

  /**
   * Gets total event count.
   *
   * @return event count
   */
  public int getTotalEventCount() {
    return totalEventCount;
  }

  /**
   * Gets mean transient loss per event.
   *
   * @return mean transient loss in kg
   */
  public double getMeanTransientLoss() {
    return meanTransientLoss;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();

    // Totals
    Map<String, Object> totals = new HashMap<>();
    totals.put("shutdownLoss_kg", totalShutdownLoss);
    totals.put("rampUpLoss_kg", totalRampUpLoss);
    totals.put("steadyStateLoss_kg", totalSteadyStateLoss);
    totals.put("transientLoss_kg", totalTransientLoss);
    totals.put("totalLoss_kg", totalLoss);
    map.put("totals", totals);

    // Fractions
    Map<String, Object> fractions = new HashMap<>();
    fractions.put("shutdown", shutdownFraction);
    fractions.put("rampUp", rampUpFraction);
    fractions.put("steadyState", steadyStateFraction);
    fractions.put("transient", transientFraction);
    fractions.put("transientPercent", transientFraction * 100);
    map.put("fractions", fractions);

    // Counts
    Map<String, Object> counts = new HashMap<>();
    counts.put("shutdownEvents", shutdownEventCount);
    counts.put("rampUpEvents", rampUpEventCount);
    counts.put("totalEvents", totalEventCount);
    map.put("eventCounts", counts);

    // Means
    Map<String, Object> means = new HashMap<>();
    means.put("shutdownLossPerEvent_kg", meanShutdownLoss);
    means.put("rampUpLossPerEvent_kg", meanRampUpLoss);
    means.put("transientLossPerEvent_kg", meanTransientLoss);
    map.put("meanValues", means);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  @Override
  public String toString() {
    return String.format("TransientLossStatistics[total=%.0f kg, transient=%.1f%%, events=%d]",
        totalLoss, transientFraction * 100, totalEventCount);
  }
}
