package neqsim.process.safety.risk.dynamic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Represents a production profile during an equipment failure event.
 *
 * <p>
 * Captures the time-varying production rate including:
 * </p>
 * <ul>
 * <li>Shutdown transient as production drops</li>
 * <li>Steady-state degraded operation</li>
 * <li>Ramp-up transient as production recovers</li>
 * </ul>
 *
 * <h2>Production Profile Phases</h2>
 * 
 * <pre>
 * Production
 *     ^
 * 100%|_____                          _____
 *     |     \                        /
 *  50%|      \______________________/
 *     |       |                    |
 *   0%|-------|---------------------|-------&gt; Time
 *     |  Shut |   Degraded Period   | Ramp
 *     | down  |                     |  Up
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProductionProfile implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Equipment that failed. */
  private String equipmentName;

  /** Failure mode description. */
  private String failureMode;

  /** Baseline production rate (kg/hr). */
  private double baselineProduction;

  /** Degraded production rate (kg/hr). */
  private double degradedProduction;

  /** Total repair duration (hours). */
  private double repairDuration;

  // Shutdown transient
  /** Production loss during shutdown transient (kg). */
  private double shutdownTransientLoss;

  /** Shutdown transient duration (hours). */
  private double shutdownDuration;

  // Steady-state degraded
  /** Production loss during steady-state degraded operation (kg). */
  private double steadyStateLoss;

  /** Steady-state degraded duration (hours). */
  private double steadyStateDuration;

  // Ramp-up transient
  /** Production loss during ramp-up transient (kg). */
  private double rampUpTransientLoss;

  /** Ramp-up transient duration (hours). */
  private double rampUpDuration;

  // Totals
  /** Total production loss (kg). */
  private double totalLoss;

  /** Total production during event (kg). */
  private double totalProduction;

  /** Fraction of loss from transients. */
  private double transientLossFraction;

  /** Time series data for detailed analysis. */
  private List<TimePoint> timeSeries;

  /**
   * Time point in production profile.
   */
  public static class TimePoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private double time; // hours
    private double productionRate; // kg/hr
    private String phase; // shutdown, degraded, rampup, normal

    /**
     * Creates a time point.
     *
     * @param time time in hours
     * @param productionRate production rate in kg/hr
     * @param phase current phase
     */
    public TimePoint(double time, double productionRate, String phase) {
      this.time = time;
      this.productionRate = productionRate;
      this.phase = phase;
    }

    public double getTime() {
      return time;
    }

    public double getProductionRate() {
      return productionRate;
    }

    public String getPhase() {
      return phase;
    }
  }

  /**
   * Creates a production profile for an equipment failure.
   *
   * @param equipmentName name of failed equipment
   */
  public ProductionProfile(String equipmentName) {
    this.equipmentName = equipmentName;
    this.failureMode = "TRIP";
    this.timeSeries = new ArrayList<>();
  }

  /**
   * Creates a production profile with failure mode.
   *
   * @param equipmentName name of failed equipment
   * @param failureMode failure mode description
   */
  public ProductionProfile(String equipmentName, String failureMode) {
    this.equipmentName = equipmentName;
    this.failureMode = failureMode;
    this.timeSeries = new ArrayList<>();
  }

  // Getters

  /**
   * Gets the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Gets the failure mode.
   *
   * @return failure mode
   */
  public String getFailureMode() {
    return failureMode;
  }

  /**
   * Gets the baseline production rate.
   *
   * @return baseline production in kg/hr
   */
  public double getBaselineProduction() {
    return baselineProduction;
  }

  /**
   * Gets the degraded production rate.
   *
   * @return degraded production in kg/hr
   */
  public double getDegradedProduction() {
    return degradedProduction;
  }

  /**
   * Gets the repair duration.
   *
   * @return repair duration in hours
   */
  public double getRepairDuration() {
    return repairDuration;
  }

  /**
   * Gets the shutdown transient loss.
   *
   * @return shutdown loss in kg
   */
  public double getShutdownTransientLoss() {
    return shutdownTransientLoss;
  }

  /**
   * Gets the shutdown duration.
   *
   * @return shutdown duration in hours
   */
  public double getShutdownDuration() {
    return shutdownDuration;
  }

  /**
   * Gets the steady-state loss.
   *
   * @return steady-state loss in kg
   */
  public double getSteadyStateLoss() {
    return steadyStateLoss;
  }

  /**
   * Gets the steady-state duration.
   *
   * @return steady-state duration in hours
   */
  public double getSteadyStateDuration() {
    return steadyStateDuration;
  }

  /**
   * Gets the ramp-up transient loss.
   *
   * @return ramp-up loss in kg
   */
  public double getRampUpTransientLoss() {
    return rampUpTransientLoss;
  }

  /**
   * Gets the ramp-up duration.
   *
   * @return ramp-up duration in hours
   */
  public double getRampUpDuration() {
    return rampUpDuration;
  }

  /**
   * Gets the total production loss.
   *
   * @return total loss in kg
   */
  public double getTotalLoss() {
    return totalLoss;
  }

  /**
   * Gets the total production during the event.
   *
   * @return total production in kg
   */
  public double getTotalProduction() {
    return totalProduction;
  }

  /**
   * Gets the fraction of loss from transients.
   *
   * @return transient loss fraction (0-1)
   */
  public double getTransientLossFraction() {
    return transientLossFraction;
  }

  /**
   * Gets the time series data.
   *
   * @return list of time points
   */
  public List<TimePoint> getTimeSeries() {
    return new ArrayList<>(timeSeries);
  }

  /**
   * Gets the production loss percentage.
   *
   * @return loss percentage (0-100)
   */
  public double getProductionLossPercent() {
    if (baselineProduction <= 0) {
      return 100.0;
    }
    return (1.0 - degradedProduction / baselineProduction) * 100.0;
  }

  /**
   * Gets the total transient loss.
   *
   * @return total transient loss in kg
   */
  public double getTotalTransientLoss() {
    return shutdownTransientLoss + rampUpTransientLoss;
  }

  // Setters

  /**
   * Sets the failure mode.
   *
   * @param failureMode failure mode description
   */
  public void setFailureMode(String failureMode) {
    this.failureMode = failureMode;
  }

  /**
   * Sets the baseline production rate.
   *
   * @param rate production rate in kg/hr
   */
  public void setBaselineProduction(double rate) {
    this.baselineProduction = rate;
  }

  /**
   * Sets the degraded production rate.
   *
   * @param rate production rate in kg/hr
   */
  public void setDegradedProduction(double rate) {
    this.degradedProduction = rate;
  }

  /**
   * Sets the repair duration.
   *
   * @param hours repair duration in hours
   */
  public void setRepairDuration(double hours) {
    this.repairDuration = hours;
  }

  /**
   * Sets the shutdown transient loss.
   *
   * @param loss loss in kg
   */
  public void setShutdownTransientLoss(double loss) {
    this.shutdownTransientLoss = loss;
  }

  /**
   * Sets the shutdown duration.
   *
   * @param hours duration in hours
   */
  public void setShutdownDuration(double hours) {
    this.shutdownDuration = hours;
  }

  /**
   * Sets the steady-state loss.
   *
   * @param loss loss in kg
   */
  public void setSteadyStateLoss(double loss) {
    this.steadyStateLoss = loss;
  }

  /**
   * Sets the steady-state duration.
   *
   * @param hours duration in hours
   */
  public void setSteadyStateDuration(double hours) {
    this.steadyStateDuration = hours;
  }

  /**
   * Sets the ramp-up transient loss.
   *
   * @param loss loss in kg
   */
  public void setRampUpTransientLoss(double loss) {
    this.rampUpTransientLoss = loss;
  }

  /**
   * Sets the ramp-up duration.
   *
   * @param hours duration in hours
   */
  public void setRampUpDuration(double hours) {
    this.rampUpDuration = hours;
  }

  /**
   * Calculates total values from component values.
   */
  public void calculateTotals() {
    // Total loss
    totalLoss = shutdownTransientLoss + steadyStateLoss + rampUpTransientLoss;

    // What would have been produced at baseline
    double wouldHaveProduced = baselineProduction * repairDuration;

    // Actual production
    totalProduction = wouldHaveProduced - totalLoss;

    // Transient fraction
    double transientLoss = shutdownTransientLoss + rampUpTransientLoss;
    if (totalLoss > 0) {
      transientLossFraction = transientLoss / totalLoss;
    } else {
      transientLossFraction = 0;
    }

    // Build time series
    buildTimeSeries();
  }

  /**
   * Builds time series data for visualization.
   */
  private void buildTimeSeries() {
    timeSeries.clear();

    double t = 0;
    double dt = repairDuration / 100.0; // 100 points

    // Normal operation point
    timeSeries.add(new TimePoint(0, baselineProduction, "normal"));

    // Shutdown transient
    if (shutdownDuration > 0) {
      int shutdownSteps = Math.max(1, (int) (shutdownDuration / dt));
      for (int i = 1; i <= shutdownSteps; i++) {
        double progress = (double) i / shutdownSteps;
        double rate = baselineProduction - (baselineProduction - degradedProduction) * progress;
        timeSeries.add(new TimePoint(t + progress * shutdownDuration, rate, "shutdown"));
      }
      t += shutdownDuration;
    }

    // Steady-state degraded
    if (steadyStateDuration > 0) {
      int steadySteps = Math.max(1, (int) (steadyStateDuration / dt));
      for (int i = 0; i <= steadySteps; i++) {
        double progress = (double) i / steadySteps;
        timeSeries
            .add(new TimePoint(t + progress * steadyStateDuration, degradedProduction, "degraded"));
      }
      t += steadyStateDuration;
    }

    // Ramp-up transient
    if (rampUpDuration > 0) {
      int rampSteps = Math.max(1, (int) (rampUpDuration / dt));
      for (int i = 1; i <= rampSteps; i++) {
        double progress = (double) i / rampSteps;
        double rate = degradedProduction + (baselineProduction - degradedProduction) * progress;
        timeSeries.add(new TimePoint(t + progress * rampUpDuration, rate, "rampup"));
      }
      t += rampUpDuration;
    }

    // Return to normal
    timeSeries.add(new TimePoint(t + dt, baselineProduction, "normal"));
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("equipmentName", equipmentName);
    map.put("failureMode", failureMode);
    map.put("baselineProduction_kghr", baselineProduction);
    map.put("degradedProduction_kghr", degradedProduction);
    map.put("productionLossPercent", getProductionLossPercent());
    map.put("repairDuration_hours", repairDuration);

    // Shutdown transient
    Map<String, Object> shutdown = new HashMap<>();
    shutdown.put("loss_kg", shutdownTransientLoss);
    shutdown.put("duration_hours", shutdownDuration);
    map.put("shutdownTransient", shutdown);

    // Steady state
    Map<String, Object> steady = new HashMap<>();
    steady.put("loss_kg", steadyStateLoss);
    steady.put("duration_hours", steadyStateDuration);
    map.put("steadyState", steady);

    // Ramp up
    Map<String, Object> rampUp = new HashMap<>();
    rampUp.put("loss_kg", rampUpTransientLoss);
    rampUp.put("duration_hours", rampUpDuration);
    map.put("rampUpTransient", rampUp);

    // Totals
    Map<String, Object> totals = new HashMap<>();
    totals.put("totalLoss_kg", totalLoss);
    totals.put("totalProduction_kg", totalProduction);
    totals.put("transientLoss_kg", getTotalTransientLoss());
    totals.put("transientLossFraction", transientLossFraction);
    map.put("totals", totals);

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
    return String.format("ProductionProfile[%s: %.1f%% loss, %.1f hrs, transient=%.1f%%]",
        equipmentName, getProductionLossPercent(), repairDuration, transientLossFraction * 100);
  }
}
