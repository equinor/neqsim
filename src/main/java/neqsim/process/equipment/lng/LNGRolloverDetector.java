package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Detects rollover risk in stratified LNG tanks.
 *
 * <p>
 * LNG rollover is a sudden mixing event in a stratified tank where a denser upper layer (formed by
 * preferential evaporation of light components) rapidly exchanges position with a warmer, lighter
 * lower layer (heated by wall ingress). The resulting flash of the superheated lower layer produces
 * a surge of BOG that can exceed the tank's pressure relief capacity.
 * </p>
 *
 * <p>
 * Detection criteria (based on published LNG rollover incidents and research):
 * </p>
 * <ul>
 * <li><b>Density inversion:</b> Upper layer denser than lower layer by more than a threshold
 * (typically 1-5 kg/m3)</li>
 * <li><b>Temperature differential:</b> Temperature difference between layers above a threshold
 * (typically 0.5-2.0 K) with lower layer warmer</li>
 * <li><b>Rayleigh number criterion:</b> Natural convection onset when Ra exceeds critical value
 * (~1700 for horizontal liquid layers)</li>
 * <li><b>Stability index:</b> Combined density and temperature stratification metric</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGRolloverDetector implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1008L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGRolloverDetector.class);

  /** Density difference threshold for rollover warning (kg/m3). */
  private double densityWarningThreshold = 1.0;

  /** Density difference threshold for rollover alarm (kg/m3). */
  private double densityAlarmThreshold = 3.0;

  /** Temperature difference threshold for rollover concern (K). */
  private double temperatureThreshold = 0.5;

  /** Critical Rayleigh number for convection onset in LNG. */
  private double criticalRayleighNumber = 1700.0;

  /** LNG thermal expansion coefficient (1/K). Approximate value for LNG. */
  private double thermalExpansionCoeff = 3.5e-3;

  /** LNG kinematic viscosity (m2/s). Approximate for LNG at -160C. */
  private double kinematicViscosity = 2.0e-7;

  /** LNG thermal diffusivity (m2/s). Approximate for LNG. */
  private double thermalDiffusivity = 8.5e-8;

  /** History of density differences for trend extrapolation (most recent last). */
  private List<Double> densityDiffHistory = new ArrayList<Double>();

  /** Maximum history length for trend analysis. */
  private int maxHistoryLength = 100;

  /**
   * Risk level enumeration for rollover assessment.
   */
  public enum RolloverRiskLevel {
    /** No rollover risk detected. */
    NONE,
    /** Low risk — minor stratification detected, monitoring recommended. */
    LOW,
    /** Medium risk — significant stratification, mixing action recommended. */
    MEDIUM,
    /** High risk — density inversion detected, immediate action required. */
    HIGH,
    /** Critical — rollover imminent or in progress, emergency response needed. */
    CRITICAL
  }

  /**
   * Result of a rollover assessment.
   */
  public static class RolloverAssessment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1009L;

    /** Overall risk level. */
    private RolloverRiskLevel riskLevel;

    /** Maximum density difference between adjacent layers (kg/m3). */
    private double maxDensityDifference;

    /** Maximum temperature difference between layers (K). */
    private double maxTemperatureDifference;

    /** Whether a density inversion exists (heavier on top). */
    private boolean densityInversion;

    /** Computed Rayleigh number (0 if single layer). */
    private double rayleighNumber;

    /** Layer index pair where maximum risk exists. */
    private int riskLayerLower;

    /** Layer index pair where maximum risk exists. */
    private int riskLayerUpper;

    /** Estimated time until rollover occurs (hours). -1 = no rollover predicted. */
    private double estimatedTimeToRolloverHours = -1;

    /** Descriptive message. */
    private String message;

    /**
     * Constructor for RolloverAssessment.
     *
     * @param riskLevel the assessed risk level
     * @param message descriptive message
     */
    public RolloverAssessment(RolloverRiskLevel riskLevel, String message) {
      this.riskLevel = riskLevel;
      this.message = message;
    }

    /**
     * Get the risk level.
     *
     * @return risk level
     */
    public RolloverRiskLevel getRiskLevel() {
      return riskLevel;
    }

    /**
     * Get the maximum density difference.
     *
     * @return density difference (kg/m3)
     */
    public double getMaxDensityDifference() {
      return maxDensityDifference;
    }

    /**
     * Set the maximum density difference.
     *
     * @param diff density difference (kg/m3)
     */
    public void setMaxDensityDifference(double diff) {
      this.maxDensityDifference = diff;
    }

    /**
     * Get the maximum temperature difference.
     *
     * @return temperature difference (K)
     */
    public double getMaxTemperatureDifference() {
      return maxTemperatureDifference;
    }

    /**
     * Set the maximum temperature difference.
     *
     * @param diff temperature difference (K)
     */
    public void setMaxTemperatureDifference(double diff) {
      this.maxTemperatureDifference = diff;
    }

    /**
     * Check if density inversion exists.
     *
     * @return true if heavier layer is on top
     */
    public boolean isDensityInversion() {
      return densityInversion;
    }

    /**
     * Set whether density inversion exists.
     *
     * @param inversion true if density inversion
     */
    public void setDensityInversion(boolean inversion) {
      this.densityInversion = inversion;
    }

    /**
     * Get the Rayleigh number.
     *
     * @return Rayleigh number
     */
    public double getRayleighNumber() {
      return rayleighNumber;
    }

    /**
     * Set the Rayleigh number.
     *
     * @param ra Rayleigh number
     */
    public void setRayleighNumber(double ra) {
      this.rayleighNumber = ra;
    }

    /**
     * Get the lower risk layer index.
     *
     * @return layer index
     */
    public int getRiskLayerLower() {
      return riskLayerLower;
    }

    /**
     * Set the lower risk layer index.
     *
     * @param index layer index
     */
    public void setRiskLayerLower(int index) {
      this.riskLayerLower = index;
    }

    /**
     * Get the upper risk layer index.
     *
     * @return layer index
     */
    public int getRiskLayerUpper() {
      return riskLayerUpper;
    }

    /**
     * Set the upper risk layer index.
     *
     * @param index layer index
     */
    public void setRiskLayerUpper(int index) {
      this.riskLayerUpper = index;
    }

    /**
     * Get the estimated time to rollover.
     *
     * @return hours until rollover, or -1 if no rollover predicted
     */
    public double getEstimatedTimeToRolloverHours() {
      return estimatedTimeToRolloverHours;
    }

    /**
     * Set the estimated time to rollover.
     *
     * @param hours hours until rollover, or -1 if no rollover predicted
     */
    public void setEstimatedTimeToRolloverHours(double hours) {
      this.estimatedTimeToRolloverHours = hours;
    }

    /**
     * Get the assessment message.
     *
     * @return descriptive message
     */
    public String getMessage() {
      return message;
    }
  }

  /**
   * Default constructor.
   */
  public LNGRolloverDetector() {}

  /**
   * Assess rollover risk based on current layer state.
   *
   * @param layers list of tank layers from bottom to top
   * @return rollover risk assessment
   */
  public RolloverAssessment assess(List<LNGTankLayer> layers) {
    if (layers == null || layers.size() < 2) {
      return new RolloverAssessment(RolloverRiskLevel.NONE,
          "Single layer or no layers — no rollover risk");
    }

    double maxDensityDiff = 0;
    double maxTempDiff = 0;
    boolean hasInversion = false;
    int worstLower = 0;
    int worstUpper = 1;

    for (int i = 0; i < layers.size() - 1; i++) {
      LNGTankLayer lower = layers.get(i);
      LNGTankLayer upper = layers.get(i + 1);

      double densityDiff = upper.getDensity() - lower.getDensity();
      double tempDiff = lower.getTemperature() - upper.getTemperature();

      // Positive densityDiff means upper is heavier — potential inversion
      if (densityDiff > maxDensityDiff) {
        maxDensityDiff = densityDiff;
        worstLower = i;
        worstUpper = i + 1;
      }
      if (densityDiff > 0) {
        hasInversion = true;
      }

      // Positive tempDiff means lower is warmer — buoyancy instability
      if (tempDiff > maxTempDiff) {
        maxTempDiff = tempDiff;
      }
    }

    // Calculate Rayleigh number for the worst pair
    double layerHeight = estimateLayerHeight(layers.get(worstLower));
    double ra = calculateRayleighNumber(maxTempDiff, layerHeight);

    // Determine risk level
    RolloverRiskLevel level;
    String msg;

    if (hasInversion && maxDensityDiff > densityAlarmThreshold) {
      level = RolloverRiskLevel.CRITICAL;
      msg = String.format(
          "CRITICAL: Density inversion %.1f kg/m3 exceeds alarm threshold %.1f. "
              + "Rollover imminent. Initiate emergency mixing.",
          maxDensityDiff, densityAlarmThreshold);
    } else if (hasInversion && maxDensityDiff > densityWarningThreshold) {
      level = RolloverRiskLevel.HIGH;
      msg = String.format("HIGH: Density inversion %.1f kg/m3 between layers %d and %d. "
          + "Initiate pump circulation or jet mixing.", maxDensityDiff, worstLower, worstUpper);
    } else if (ra > criticalRayleighNumber || maxTempDiff > temperatureThreshold) {
      level = RolloverRiskLevel.MEDIUM;
      msg = String.format("MEDIUM: Thermal stratification dT=%.2f K, Ra=%.0f. "
          + "Monitor closely, consider mixing.", maxTempDiff, ra);
    } else if (maxDensityDiff > densityWarningThreshold * 0.5) {
      level = RolloverRiskLevel.LOW;
      msg = String.format(
          "LOW: Minor stratification detected. Density diff=%.2f kg/m3. " + "Continue monitoring.",
          maxDensityDiff);
    } else {
      level = RolloverRiskLevel.NONE;
      msg = "No rollover risk — layers well mixed or stable configuration.";
    }

    RolloverAssessment assessment = new RolloverAssessment(level, msg);
    assessment.setMaxDensityDifference(maxDensityDiff);
    assessment.setMaxTemperatureDifference(maxTempDiff);
    assessment.setDensityInversion(hasInversion);
    assessment.setRayleighNumber(ra);
    assessment.setRiskLayerLower(worstLower);
    assessment.setRiskLayerUpper(worstUpper);

    // Time-to-rollover prediction based on density difference trend
    densityDiffHistory.add(maxDensityDiff);
    if (densityDiffHistory.size() > maxHistoryLength) {
      densityDiffHistory.remove(0);
    }
    double ttr = estimateTimeToRollover(maxDensityDiff);
    assessment.setEstimatedTimeToRolloverHours(ttr);

    if (level.ordinal() >= RolloverRiskLevel.HIGH.ordinal()) {
      logger.warn("Rollover risk: " + msg);
    } else if (level.ordinal() >= RolloverRiskLevel.MEDIUM.ordinal()) {
      logger.info("Rollover risk: " + msg);
    }

    return assessment;
  }

  /**
   * Calculate the Rayleigh number for natural convection onset.
   *
   * <p>
   * Ra = g * beta * dT * H^3 / (nu * alpha)
   * </p>
   *
   * <p>
   * where g = 9.81 m/s2, beta = thermal expansion coefficient, dT = temperature difference, H =
   * layer height, nu = kinematic viscosity, alpha = thermal diffusivity.
   * </p>
   *
   * @param deltaT temperature difference between layers (K)
   * @param layerHeight height of the layer (m)
   * @return Rayleigh number
   */
  public double calculateRayleighNumber(double deltaT, double layerHeight) {
    if (deltaT <= 0 || layerHeight <= 0) {
      return 0;
    }
    double g = 9.81;
    return g * thermalExpansionCoeff * deltaT * Math.pow(layerHeight, 3)
        / (kinematicViscosity * thermalDiffusivity);
  }

  /**
   * Estimate the height of a layer based on its volume and tank geometry.
   *
   * <p>
   * Assumes a cylindrical tank for simplicity: h = V / (pi * r^2). Default tank diameter 40m gives
   * an approximation.
   * </p>
   *
   * @param layer the tank layer
   * @return estimated layer height (m)
   */
  private double estimateLayerHeight(LNGTankLayer layer) {
    // Assume cylindrical tank with 40m diameter
    double tankDiameter = 40.0;
    double crossSectionArea = Math.PI * Math.pow(tankDiameter / 2.0, 2);
    double volume = layer.getVolume();
    if (volume <= 0) {
      return 1.0; // default 1m
    }
    return volume / crossSectionArea;
  }

  /**
   * Get density warning threshold.
   *
   * @return threshold (kg/m3)
   */
  public double getDensityWarningThreshold() {
    return densityWarningThreshold;
  }

  /**
   * Set density warning threshold.
   *
   * @param threshold threshold (kg/m3)
   */
  public void setDensityWarningThreshold(double threshold) {
    this.densityWarningThreshold = threshold;
  }

  /**
   * Get density alarm threshold.
   *
   * @return threshold (kg/m3)
   */
  public double getDensityAlarmThreshold() {
    return densityAlarmThreshold;
  }

  /**
   * Set density alarm threshold.
   *
   * @param threshold threshold (kg/m3)
   */
  public void setDensityAlarmThreshold(double threshold) {
    this.densityAlarmThreshold = threshold;
  }

  /**
   * Get temperature threshold.
   *
   * @return threshold (K)
   */
  public double getTemperatureThreshold() {
    return temperatureThreshold;
  }

  /**
   * Set temperature threshold.
   *
   * @param threshold threshold (K)
   */
  public void setTemperatureThreshold(double threshold) {
    this.temperatureThreshold = threshold;
  }

  /**
   * Get the critical Rayleigh number.
   *
   * @return critical Rayleigh number
   */
  public double getCriticalRayleighNumber() {
    return criticalRayleighNumber;
  }

  /**
   * Set the critical Rayleigh number.
   *
   * @param criticalRa critical Rayleigh number
   */
  public void setCriticalRayleighNumber(double criticalRa) {
    this.criticalRayleighNumber = criticalRa;
  }

  /**
   * Set LNG physical properties for Rayleigh calculation.
   *
   * @param thermalExpansionCoeff thermal expansion coefficient (1/K)
   * @param kinematicViscosity kinematic viscosity (m2/s)
   * @param thermalDiffusivity thermal diffusivity (m2/s)
   */
  public void setLNGProperties(double thermalExpansionCoeff, double kinematicViscosity,
      double thermalDiffusivity) {
    this.thermalExpansionCoeff = thermalExpansionCoeff;
    this.kinematicViscosity = kinematicViscosity;
    this.thermalDiffusivity = thermalDiffusivity;
  }

  /**
   * Estimate the time to rollover based on density difference trend.
   *
   * <p>
   * Uses linear extrapolation of the density difference history to predict when a density inversion
   * (heavier layer on top) will reach the alarm threshold. Requires at least 3 history points for
   * extrapolation.
   * </p>
   *
   * @param currentDensityDiff current density difference (kg/m3)
   * @return estimated hours to rollover, or -1 if no rollover trend detected
   */
  private double estimateTimeToRollover(double currentDensityDiff) {
    if (densityDiffHistory.size() < 3) {
      return -1;
    }

    // Simple linear regression on the last N points (y = a + b*x, x in hours)
    int n = densityDiffHistory.size();
    int windowSize = Math.min(n, 20);
    double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

    for (int i = n - windowSize; i < n; i++) {
      double x = i - (n - windowSize);
      double y = densityDiffHistory.get(i);
      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    double denom = windowSize * sumX2 - sumX * sumX;
    if (Math.abs(denom) < 1e-20) {
      return -1;
    }

    double slope = (windowSize * sumXY - sumX * sumY) / denom;

    // If density difference is decreasing (slope < 0), rollover is not approaching
    if (slope <= 0) {
      return -1;
    }

    // Extrapolate: how many more steps until density diff reaches alarm threshold?
    double remainingDiff = densityAlarmThreshold - currentDensityDiff;
    if (remainingDiff <= 0) {
      return 0; // Already at or above alarm
    }

    // Each step in the history corresponds to one assessment call (typically one time step)
    return remainingDiff / slope;
  }

  /**
   * Clear the density difference history.
   */
  public void clearHistory() {
    densityDiffHistory.clear();
  }

  /**
   * Get the density difference history for analysis.
   *
   * @return list of historical density differences
   */
  public List<Double> getDensityDiffHistory() {
    return densityDiffHistory;
  }
}
