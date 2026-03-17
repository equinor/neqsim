package neqsim.process.electricaldesign.components;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model of switchgear / motor control center (MCC) bucket for process electrical systems.
 *
 * <p>
 * Handles rated current, short-circuit withstand, protection coordination, and starter type
 * selection. Supports direct-on-line (DOL), star-delta, soft starter, and VFD starters per IEC
 * 61439.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class Switchgear implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Standard switchgear current ratings (A). */
  private static final double[] STANDARD_RATINGS_A =
      {100, 160, 250, 400, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000};

  // === Configuration ===
  private double ratedCurrentA;
  private double ratedVoltageV;
  private double shortCircuitCurrentKA = 25.0;
  private String starterType = "DOL";
  private String switchgearType = "MCC";

  // === Protection ===
  private double circuitBreakerRatingA;
  private double fuseRatingA;
  private String protectionRelay = "Overcurrent + Earth Fault";
  private boolean hasMotorProtection = true;

  // === Physical ===
  private double bucketWidthMM = 600;
  private double bucketHeightMM = 200;
  private double weightKg;
  private double estimatedCostUSD;

  /**
   * Size the switchgear based on motor parameters.
   *
   * @param motorRatedCurrentA motor full-load current in A
   * @param motorRatedPowerKW motor rated power in kW
   * @param voltageV system voltage in V
   * @param useVFD whether VFD is used
   */
  public void sizeSwitchgear(double motorRatedCurrentA, double motorRatedPowerKW, double voltageV,
      boolean useVFD) {
    this.ratedVoltageV = voltageV;

    // Select starter type
    if (useVFD) {
      starterType = "VFD";
    } else if (motorRatedPowerKW > 200) {
      starterType = "Soft Starter";
    } else if (motorRatedPowerKW > 11) {
      starterType = "Star-Delta";
    } else {
      starterType = "DOL";
    }

    // Determine switchgear type based on voltage
    if (voltageV > 1000) {
      switchgearType = "MV Switchgear";
    } else {
      switchgearType = "MCC";
    }

    // Circuit breaker sizing (1.25x motor FLC standard practice)
    circuitBreakerRatingA = selectStandardRating(motorRatedCurrentA * 1.25);
    ratedCurrentA = circuitBreakerRatingA;

    // Fuse sizing
    fuseRatingA = selectStandardFuseRating(motorRatedCurrentA);

    // Short-circuit rating based on voltage and system
    if (voltageV > 6000) {
      shortCircuitCurrentKA = 40.0;
    } else if (voltageV > 1000) {
      shortCircuitCurrentKA = 31.5;
    } else {
      shortCircuitCurrentKA = 25.0;
    }

    // Weight and cost
    weightKg = estimateWeight(voltageV, ratedCurrentA);
    estimatedCostUSD = estimateCost(voltageV, ratedCurrentA, starterType);
  }

  /**
   * Select next standard current rating.
   *
   * @param requiredA required current in A
   * @return next standard rating
   */
  private double selectStandardRating(double requiredA) {
    for (double std : STANDARD_RATINGS_A) {
      if (std >= requiredA) {
        return std;
      }
    }
    return Math.ceil(requiredA / 100.0) * 100.0;
  }

  /**
   * Select standard fuse rating for motor protection.
   *
   * @param motorFLC motor full-load current in A
   * @return fuse current rating in A
   */
  private double selectStandardFuseRating(double motorFLC) {
    double[] fuseRatings = {16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400,
        500, 630, 800, 1000, 1250};
    double target = motorFLC * 1.6;
    for (double fuse : fuseRatings) {
      if (fuse >= target) {
        return fuse;
      }
    }
    return fuseRatings[fuseRatings.length - 1];
  }

  /**
   * Estimate switchgear weight.
   *
   * @param voltageV system voltage in V
   * @param currentA rated current in A
   * @return weight in kg
   */
  private double estimateWeight(double voltageV, double currentA) {
    if (voltageV > 1000) {
      return currentA * 0.5 + 200;
    }
    return currentA * 0.15 + 50;
  }

  /**
   * Estimate switchgear cost.
   *
   * @param voltageV system voltage in V
   * @param currentA rated current in A
   * @param starter starter type
   * @return cost in USD
   */
  private double estimateCost(double voltageV, double currentA, String starter) {
    double baseCost;
    if (voltageV > 1000) {
      baseCost = 25000 + currentA * 20;
    } else {
      baseCost = 3000 + currentA * 5;
    }
    if ("VFD".equals(starter)) {
      baseCost *= 1.0; // VFD cost is handled separately
    } else if ("Soft Starter".equals(starter)) {
      baseCost *= 1.4;
    } else if ("Star-Delta".equals(starter)) {
      baseCost *= 1.2;
    }
    return baseCost;
  }

  /**
   * Serialize to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = toMap();
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }

  /**
   * Convert to a map.
   *
   * @return map of switchgear parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("switchgearType", switchgearType);
    map.put("ratedCurrentA", ratedCurrentA);
    map.put("ratedVoltageV", ratedVoltageV);
    map.put("shortCircuitCurrentKA", shortCircuitCurrentKA);
    map.put("starterType", starterType);
    map.put("circuitBreakerRatingA", circuitBreakerRatingA);
    map.put("fuseRatingA", fuseRatingA);
    map.put("protectionRelay", protectionRelay);
    map.put("hasMotorProtection", hasMotorProtection);
    map.put("weightKg", weightKg);
    map.put("estimatedCostUSD", estimatedCostUSD);
    return map;
  }

  // === Getters and Setters ===

  /**
   * Get rated current in A.
   *
   * @return rated current in A
   */
  public double getRatedCurrentA() {
    return ratedCurrentA;
  }

  /**
   * Get rated voltage in V.
   *
   * @return rated voltage in V
   */
  public double getRatedVoltageV() {
    return ratedVoltageV;
  }

  /**
   * Get short-circuit current in kA.
   *
   * @return short-circuit current in kA
   */
  public double getShortCircuitCurrentKA() {
    return shortCircuitCurrentKA;
  }

  /**
   * Set short-circuit current in kA.
   *
   * @param shortCircuitCurrentKA short-circuit current in kA
   */
  public void setShortCircuitCurrentKA(double shortCircuitCurrentKA) {
    this.shortCircuitCurrentKA = shortCircuitCurrentKA;
  }

  /**
   * Get starter type.
   *
   * @return starter type (DOL, Star-Delta, Soft Starter, VFD)
   */
  public String getStarterType() {
    return starterType;
  }

  /**
   * Set starter type.
   *
   * @param starterType starter type
   */
  public void setStarterType(String starterType) {
    this.starterType = starterType;
  }

  /**
   * Get switchgear type.
   *
   * @return switchgear type (MCC or MV Switchgear)
   */
  public String getSwitchgearType() {
    return switchgearType;
  }

  /**
   * Get circuit breaker rating in A.
   *
   * @return circuit breaker rating in A
   */
  public double getCircuitBreakerRatingA() {
    return circuitBreakerRatingA;
  }

  /**
   * Get fuse rating in A.
   *
   * @return fuse rating in A
   */
  public double getFuseRatingA() {
    return fuseRatingA;
  }

  /**
   * Get weight in kg.
   *
   * @return weight in kg
   */
  public double getWeightKg() {
    return weightKg;
  }

  /**
   * Get estimated cost in USD.
   *
   * @return estimated cost in USD
   */
  public double getEstimatedCostUSD() {
    return estimatedCostUSD;
  }
}
