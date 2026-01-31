package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Models compressor washing (online and offline) for performance recovery.
 *
 * <p>
 * Compressor fouling causes performance degradation over time due to:
 * </p>
 * <ul>
 * <li>Salt deposits from ingested sea air</li>
 * <li>Hydrocarbon deposits from process gas</li>
 * <li>Dust and particulates</li>
 * <li>Corrosion products</li>
 * </ul>
 *
 * <h2>Washing Methods</h2>
 * <table border="1">
 * <caption>Comparison of washing methods</caption>
 * <tr>
 * <th>Method</th>
 * <th>Effectiveness</th>
 * <th>Downtime</th>
 * <th>Water Usage</th>
 * </tr>
 * <tr>
 * <td>Online (wet)</td>
 * <td>30-50%</td>
 * <td>None</td>
 * <td>Continuous spray</td>
 * </tr>
 * <tr>
 * <td>Offline (soak)</td>
 * <td>80-95%</td>
 * <td>4-8 hours</td>
 * <td>Tank soak</td>
 * </tr>
 * <tr>
 * <td>Crank wash</td>
 * <td>90-98%</td>
 * <td>8-24 hours</td>
 * <td>Multiple cycles</td>
 * </tr>
 * </table>
 *
 * <h2>References</h2>
 * <ul>
 * <li>API 616 - Gas Turbines for Petroleum, Chemical and Gas Industry</li>
 * <li>GE GER-3601 - Gas Turbine Compressor Washing State of the Art</li>
 * <li>Siemens SGT-xxx Compressor Washing Guidelines</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorWashing implements Serializable {

  private static final long serialVersionUID = 1001L;
  private static final Logger logger = LogManager.getLogger(CompressorWashing.class);

  // ============================================================================
  // Washing Type Enumeration
  // ============================================================================

  /**
   * Compressor washing method types.
   */
  public enum WashingMethod {
    /** Online wet washing during operation. */
    ONLINE_WET("Online Wet", 0.40, 0.0, true),
    /** Offline soak wash - requires shutdown. */
    OFFLINE_SOAK("Offline Soak", 0.85, 4.0, false),
    /** Crank wash at slow roll - extended procedure. */
    CRANK_WASH("Crank Wash", 0.95, 12.0, false),
    /** Chemical cleaning with solvents. */
    CHEMICAL_CLEAN("Chemical Clean", 0.98, 24.0, false),
    /** Dry ice blasting for sensitive areas. */
    DRY_ICE_BLAST("Dry Ice Blast", 0.92, 8.0, false);

    private final String displayName;
    private final double recoveryEffectiveness; // Fraction of fouling removed
    private final double requiredDowntimeHours;
    private final boolean canRunOnline;

    WashingMethod(String displayName, double effectiveness, double downtime, boolean online) {
      this.displayName = displayName;
      this.recoveryEffectiveness = effectiveness;
      this.requiredDowntimeHours = downtime;
      this.canRunOnline = online;
    }

    /**
     * Gets display name of washing method.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Gets recovery effectiveness (0-1).
     *
     * @return fraction of fouling that can be removed
     */
    public double getRecoveryEffectiveness() {
      return recoveryEffectiveness;
    }

    /**
     * Gets required downtime in hours.
     *
     * @return downtime hours (0 for online methods)
     */
    public double getRequiredDowntimeHours() {
      return requiredDowntimeHours;
    }

    /**
     * Checks if method can be performed online.
     *
     * @return true if online operation supported
     */
    public boolean canRunOnline() {
      return canRunOnline;
    }
  }

  // ============================================================================
  // Fouling Model Parameters
  // ============================================================================

  /**
   * Fouling type affecting compressor.
   */
  public enum FoulingType {
    /** Salt deposits from marine environment. */
    SALT(0.002, 0.95, "NaCl crystallization on blades"),
    /** Hydrocarbon fouling from process gas. */
    HYDROCARBON(0.001, 0.80, "Oil mist and heavy HC deposits"),
    /** General dust and particulates. */
    PARTICULATE(0.0005, 0.90, "Airborne dust accumulation"),
    /** Corrosion products (rust, scale). */
    CORROSION(0.0003, 0.60, "Iron oxide and sulfide scale"),
    /** Biological growth (rare, wet conditions). */
    BIOLOGICAL(0.0002, 0.85, "Microbial growth in humid conditions");

    private final double foulingRatePerHour; // Head loss rate per operating hour
    private final double washabilityFactor; // How easily removed (0-1)
    private final String description;

    FoulingType(double rate, double washability, String description) {
      this.foulingRatePerHour = rate;
      this.washabilityFactor = washability;
      this.description = description;
    }

    /**
     * Gets fouling rate.
     *
     * @return fouling rate per operating hour
     */
    public double getFoulingRatePerHour() {
      return foulingRatePerHour;
    }

    /**
     * Gets washability factor.
     *
     * @return washability (0-1)
     */
    public double getWashabilityFactor() {
      return washabilityFactor;
    }

    /**
     * Gets description.
     *
     * @return fouling description
     */
    public String getDescription() {
      return description;
    }
  }

  // ============================================================================
  // State Variables
  // ============================================================================

  /** Current accumulated fouling factor (0 = clean, 1 = fully fouled). */
  private double currentFoulingFactor = 0.0;

  /** Maximum fouling before forced shutdown (0-1). */
  private double maxAllowableFouling = 0.15; // 15% head loss triggers alarm

  /** Dominant fouling type. */
  private FoulingType dominantFoulingType = FoulingType.SALT;

  /** Operating hours since last wash. */
  private double hoursSinceLastWash = 0.0;

  /** Total operating hours. */
  private double totalOperatingHours = 0.0;

  /** Last wash method used. */
  private WashingMethod lastWashMethod = null;

  /** Wash history for tracking. */
  private final List<WashEvent> washHistory = new ArrayList<>();

  /** Environmental severity factor (1.0 = normal, 2.0 = harsh offshore). */
  private double environmentalSeverity = 1.0;

  /** Inlet filtration efficiency (0-1). */
  private double inletFilterEfficiency = 0.95;

  // ============================================================================
  // Washing Parameters
  // ============================================================================

  /** Online wash water flow rate [L/min]. */
  private double onlineWashWaterFlow = 50.0;

  /** Online wash duration [minutes]. */
  private double onlineWashDuration = 15.0;

  /** Offline soak time [hours]. */
  private double offlineSoakTime = 4.0;

  /** Wash water temperature [°C]. */
  private double washWaterTemperature = 60.0;

  /** Use detergent additive. */
  private boolean useDetergent = true;

  /** Detergent concentration [%]. */
  private double detergentConcentration = 0.5;

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public CompressorWashing() {
    // Default initialization
  }

  /**
   * Constructor with dominant fouling type.
   *
   * @param foulingType expected dominant fouling mechanism
   */
  public CompressorWashing(FoulingType foulingType) {
    this.dominantFoulingType = foulingType;
  }

  // ============================================================================
  // Fouling Calculation Methods
  // ============================================================================

  /**
   * Update fouling based on operating hours.
   *
   * @param operatingHours hours operated since last update
   */
  public void updateFouling(double operatingHours) {
    if (operatingHours <= 0) {
      return;
    }

    // Calculate fouling increment
    double baseFoulingRate = dominantFoulingType.getFoulingRatePerHour();

    // Apply environmental severity
    double effectiveRate = baseFoulingRate * environmentalSeverity;

    // Apply inlet filter effectiveness
    effectiveRate = effectiveRate * (1.0 - inletFilterEfficiency * 0.8);

    // Accumulate fouling (diminishing rate as fouling increases)
    double foulingIncrement = effectiveRate * operatingHours * (1.0 - currentFoulingFactor * 0.5);
    currentFoulingFactor = Math.min(1.0, currentFoulingFactor + foulingIncrement);

    // Update counters
    hoursSinceLastWash += operatingHours;
    totalOperatingHours += operatingHours;
  }

  /**
   * Calculate current head loss due to fouling.
   *
   * @return head loss factor (0 = no loss, 0.1 = 10% loss)
   */
  public double getHeadLossFactor() {
    // Non-linear relationship: small fouling has less impact
    return currentFoulingFactor * currentFoulingFactor * 0.20; // Max 20% at full fouling
  }

  /**
   * Calculate efficiency degradation due to fouling.
   *
   * @return efficiency degradation factor (0 = no degradation)
   */
  public double getEfficiencyDegradation() {
    // Efficiency typically degrades faster than head
    return currentFoulingFactor * 0.10; // Max 10% efficiency loss
  }

  /**
   * Check if washing is recommended.
   *
   * @return true if washing should be performed
   */
  public boolean isWashingRecommended() {
    return currentFoulingFactor > maxAllowableFouling * 0.5;
  }

  /**
   * Check if washing is critical.
   *
   * @return true if compressor should be shut down for cleaning
   */
  public boolean isWashingCritical() {
    return currentFoulingFactor > maxAllowableFouling;
  }

  // ============================================================================
  // Washing Methods
  // ============================================================================

  /**
   * Perform compressor washing.
   *
   * @param method washing method to use
   * @return performance recovery achieved (0-1)
   */
  public double performWash(WashingMethod method) {
    // Calculate washable fouling
    double washableFouling = currentFoulingFactor * dominantFoulingType.getWashabilityFactor();

    // Calculate actual recovery based on method effectiveness
    double recovery = washableFouling * method.getRecoveryEffectiveness();

    // Apply recovery
    double previousFouling = currentFoulingFactor;
    currentFoulingFactor = Math.max(0.0, currentFoulingFactor - recovery);

    // Record wash event
    WashEvent event =
        new WashEvent(method, totalOperatingHours, previousFouling, currentFoulingFactor, recovery);
    washHistory.add(event);

    // Reset counter
    hoursSinceLastWash = 0.0;
    lastWashMethod = method;

    logger.info("Compressor wash performed: {} - Recovery: {:.1f}%", method.getDisplayName(),
        recovery * 100);

    return recovery;
  }

  /**
   * Perform online wet wash.
   *
   * @return performance recovery achieved
   */
  public double performOnlineWash() {
    return performWash(WashingMethod.ONLINE_WET);
  }

  /**
   * Perform offline soak wash.
   *
   * @return performance recovery achieved
   */
  public double performOfflineWash() {
    return performWash(WashingMethod.OFFLINE_SOAK);
  }

  /**
   * Estimate wash water consumption for a method.
   *
   * @param method washing method
   * @return water consumption in liters
   */
  public double estimateWaterConsumption(WashingMethod method) {
    switch (method) {
      case ONLINE_WET:
        return onlineWashWaterFlow * onlineWashDuration;
      case OFFLINE_SOAK:
        return 500.0; // Typical soak tank volume
      case CRANK_WASH:
        return 1000.0; // Multiple fill cycles
      case CHEMICAL_CLEAN:
        return 200.0; // Smaller volume with chemicals
      case DRY_ICE_BLAST:
        return 0.0; // No water
      default:
        return 500.0;
    }
  }

  /**
   * Estimate time between washes for target performance.
   *
   * @param maxHeadLoss maximum acceptable head loss (e.g., 0.05 for 5%)
   * @return recommended wash interval in hours
   */
  public double estimateWashInterval(double maxHeadLoss) {
    // Inverse of fouling model: hours = sqrt(maxHeadLoss / 0.20) / rate
    double targetFouling = Math.sqrt(maxHeadLoss / 0.20);
    double effectiveRate = dominantFoulingType.getFoulingRatePerHour() * environmentalSeverity
        * (1.0 - inletFilterEfficiency * 0.8);
    if (effectiveRate > 0) {
      return targetFouling / effectiveRate;
    }
    return 8760.0; // 1 year default
  }

  /**
   * Calculate annual wash chemical cost.
   *
   * @param washesPerYear number of washes per year
   * @param chemicalCostPerLiter cost per liter of wash solution
   * @return annual chemical cost
   */
  public double calculateAnnualChemicalCost(int washesPerYear, double chemicalCostPerLiter) {
    double avgWaterPerWash = 500.0; // Average liters
    double chemicalVolume = avgWaterPerWash * detergentConcentration / 100.0;
    return washesPerYear * chemicalVolume * chemicalCostPerLiter;
  }

  // ============================================================================
  // Performance Impact Methods
  // ============================================================================

  /**
   * Get corrected polytropic head accounting for fouling.
   *
   * @param cleanHead clean condition polytropic head [kJ/kg]
   * @return fouled polytropic head [kJ/kg]
   */
  public double getCorrectedHead(double cleanHead) {
    return cleanHead * (1.0 - getHeadLossFactor());
  }

  /**
   * Get corrected efficiency accounting for fouling.
   *
   * @param cleanEfficiency clean condition efficiency
   * @return fouled efficiency
   */
  public double getCorrectedEfficiency(double cleanEfficiency) {
    return cleanEfficiency * (1.0 - getEfficiencyDegradation());
  }

  /**
   * Estimate annual production loss due to fouling.
   *
   * @param baseProductionRate base production rate [units/hr]
   * @param avgFouling average fouling factor over year
   * @param operatingHours annual operating hours
   * @return production loss [units]
   */
  public double estimateAnnualProductionLoss(double baseProductionRate, double avgFouling,
      double operatingHours) {
    double avgHeadLoss = avgFouling * avgFouling * 0.20;
    // Assume production proportional to compressor capacity
    return baseProductionRate * avgHeadLoss * operatingHours;
  }

  // ============================================================================
  // Wash Event Record
  // ============================================================================

  /**
   * Record of a wash event.
   */
  public static class WashEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final WashingMethod method;
    private final double operatingHoursAtWash;
    private final double foulingBefore;
    private final double foulingAfter;
    private final double recovery;
    private final long timestamp;

    /**
     * Creates a wash event record.
     *
     * @param method washing method used
     * @param hours operating hours at time of wash
     * @param before fouling factor before wash
     * @param after fouling factor after wash
     * @param recovery performance recovery achieved
     */
    public WashEvent(WashingMethod method, double hours, double before, double after,
        double recovery) {
      this.method = method;
      this.operatingHoursAtWash = hours;
      this.foulingBefore = before;
      this.foulingAfter = after;
      this.recovery = recovery;
      this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the washing method.
     *
     * @return washing method
     */
    public WashingMethod getMethod() {
      return method;
    }

    /**
     * Gets operating hours at wash.
     *
     * @return operating hours
     */
    public double getOperatingHoursAtWash() {
      return operatingHoursAtWash;
    }

    /**
     * Gets fouling before wash.
     *
     * @return fouling factor before
     */
    public double getFoulingBefore() {
      return foulingBefore;
    }

    /**
     * Gets fouling after wash.
     *
     * @return fouling factor after
     */
    public double getFoulingAfter() {
      return foulingAfter;
    }

    /**
     * Gets recovery achieved.
     *
     * @return recovery factor
     */
    public double getRecovery() {
      return recovery;
    }

    /**
     * Gets timestamp.
     *
     * @return timestamp in milliseconds
     */
    public long getTimestamp() {
      return timestamp;
    }
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Gets current fouling factor.
   *
   * @return fouling factor (0-1)
   */
  public double getCurrentFoulingFactor() {
    return currentFoulingFactor;
  }

  /**
   * Sets current fouling factor.
   *
   * @param fouling fouling factor (0-1)
   */
  public void setCurrentFoulingFactor(double fouling) {
    this.currentFoulingFactor = Math.max(0.0, Math.min(1.0, fouling));
  }

  /**
   * Gets maximum allowable fouling.
   *
   * @return max fouling threshold
   */
  public double getMaxAllowableFouling() {
    return maxAllowableFouling;
  }

  /**
   * Sets maximum allowable fouling.
   *
   * @param maxFouling max fouling threshold
   */
  public void setMaxAllowableFouling(double maxFouling) {
    this.maxAllowableFouling = maxFouling;
  }

  /**
   * Gets dominant fouling type.
   *
   * @return fouling type
   */
  public FoulingType getDominantFoulingType() {
    return dominantFoulingType;
  }

  /**
   * Sets dominant fouling type.
   *
   * @param type fouling type
   */
  public void setDominantFoulingType(FoulingType type) {
    this.dominantFoulingType = type;
  }

  /**
   * Gets environmental severity.
   *
   * @return severity factor
   */
  public double getEnvironmentalSeverity() {
    return environmentalSeverity;
  }

  /**
   * Sets environmental severity.
   *
   * @param severity severity factor (1.0 normal, 2.0 harsh)
   */
  public void setEnvironmentalSeverity(double severity) {
    this.environmentalSeverity = Math.max(0.5, Math.min(3.0, severity));
  }

  /**
   * Gets inlet filter efficiency.
   *
   * @return filter efficiency (0-1)
   */
  public double getInletFilterEfficiency() {
    return inletFilterEfficiency;
  }

  /**
   * Sets inlet filter efficiency.
   *
   * @param efficiency filter efficiency (0-1)
   */
  public void setInletFilterEfficiency(double efficiency) {
    this.inletFilterEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Gets hours since last wash.
   *
   * @return operating hours since last wash
   */
  public double getHoursSinceLastWash() {
    return hoursSinceLastWash;
  }

  /**
   * Gets total operating hours.
   *
   * @return total operating hours
   */
  public double getTotalOperatingHours() {
    return totalOperatingHours;
  }

  /**
   * Gets wash history.
   *
   * @return list of wash events
   */
  public List<WashEvent> getWashHistory() {
    return new ArrayList<>(washHistory);
  }

  /**
   * Gets online wash water flow rate.
   *
   * @return flow rate [L/min]
   */
  public double getOnlineWashWaterFlow() {
    return onlineWashWaterFlow;
  }

  /**
   * Sets online wash water flow rate.
   *
   * @param flowRate flow rate [L/min]
   */
  public void setOnlineWashWaterFlow(double flowRate) {
    this.onlineWashWaterFlow = flowRate;
  }

  /**
   * Gets wash water temperature.
   *
   * @return temperature [°C]
   */
  public double getWashWaterTemperature() {
    return washWaterTemperature;
  }

  /**
   * Sets wash water temperature.
   *
   * @param temperature temperature [°C]
   */
  public void setWashWaterTemperature(double temperature) {
    this.washWaterTemperature = temperature;
  }

  /**
   * Checks if detergent is used.
   *
   * @return true if detergent used
   */
  public boolean isUseDetergent() {
    return useDetergent;
  }

  /**
   * Sets detergent usage.
   *
   * @param useDetergent true to use detergent
   */
  public void setUseDetergent(boolean useDetergent) {
    this.useDetergent = useDetergent;
  }

  /**
   * Prints washing status summary.
   */
  public void printSummary() {
    logger.info("=== Compressor Washing Status ===");
    logger.info("Current Fouling: {:.1f}%", currentFoulingFactor * 100);
    logger.info("Head Loss: {:.2f}%", getHeadLossFactor() * 100);
    logger.info("Efficiency Loss: {:.2f}%", getEfficiencyDegradation() * 100);
    logger.info("Hours Since Wash: {:.0f}", hoursSinceLastWash);
    logger.info("Washing Recommended: {}", isWashingRecommended() ? "YES" : "No");
    logger.info("Washing Critical: {}", isWashingCritical() ? "YES" : "No");
    logger.info("Dominant Fouling: {}", dominantFoulingType.getDescription());
    logger.info("Wash Events: {}", washHistory.size());
  }
}
