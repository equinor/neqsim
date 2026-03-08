package neqsim.process.electricaldesign.components;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model of a power transformer for process electrical systems.
 *
 * <p>
 * Supports sizing based on load, efficiency calculation, and loss estimation per IEC 60076.
 * Handles step-down transformers from utility voltage to equipment voltage levels.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class Transformer implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Standard transformer kVA ratings per IEC. */
  private static final double[] STANDARD_RATINGS_KVA =
      {100, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000,
          5000, 6300, 8000, 10000, 12500, 16000, 20000, 25000};

  private double ratedPowerKVA;
  private double primaryVoltageV;
  private double secondaryVoltageV;
  private double frequencyHz = 50.0;

  // === Performance ===
  private double efficiencyPercent = 98.5;
  private double impedancePercent = 6.0;
  private double noLoadLossKW;
  private double fullLoadLossKW;
  private double totalLossKW;

  // === Configuration ===
  private String vectorGroup = "Dyn11";
  private String coolingType = "ONAN";
  private String tapChangerType = "Off-load";
  private int tapPositions = 5;
  private double tapRangePercent = 5.0;

  // === Physical ===
  private double weightKg;
  private double estimatedCostUSD;

  /**
   * Size the transformer based on the total load.
   *
   * @param totalLoadKVA total apparent power load in kVA
   * @param primaryV primary voltage in V
   * @param secondaryV secondary voltage in V
   */
  public void sizeTransformer(double totalLoadKVA, double primaryV, double secondaryV) {
    this.primaryVoltageV = primaryV;
    this.secondaryVoltageV = secondaryV;

    // Select next standard rating
    ratedPowerKVA = selectStandardRating(totalLoadKVA * 1.15); // 15% margin

    // Estimate losses based on rating
    noLoadLossKW = estimateNoLoadLoss(ratedPowerKVA);
    fullLoadLossKW = estimateFullLoadLoss(ratedPowerKVA);
    totalLossKW = noLoadLossKW + fullLoadLossKW;

    // Efficiency
    if (ratedPowerKVA > 0) {
      efficiencyPercent = (1.0 - totalLossKW / ratedPowerKVA) * 100.0;
    }

    // Impedance depends on voltage level
    if (primaryV > 30000) {
      impedancePercent = 10.0;
    } else if (primaryV > 10000) {
      impedancePercent = 6.0;
    } else {
      impedancePercent = 4.0;
    }

    // Cooling type
    if (ratedPowerKVA > 5000) {
      coolingType = "ONAF";
    } else if (ratedPowerKVA > 10000) {
      coolingType = "OFAF";
    }

    // Tap changer
    if (ratedPowerKVA > 2500) {
      tapChangerType = "On-load";
      tapPositions = 17;
      tapRangePercent = 10.0;
    }

    // Weight and cost estimates
    weightKg = estimateWeight(ratedPowerKVA);
    estimatedCostUSD = estimateCost(ratedPowerKVA, primaryV);
  }

  /**
   * Select the next standard transformer kVA rating.
   *
   * @param requiredKVA required apparent power in kVA
   * @return next standard rating
   */
  private double selectStandardRating(double requiredKVA) {
    for (double std : STANDARD_RATINGS_KVA) {
      if (std >= requiredKVA) {
        return std;
      }
    }
    return Math.ceil(requiredKVA / 1000.0) * 1000.0;
  }

  /**
   * Estimate no-load (iron) losses.
   *
   * @param ratingKVA transformer rating in kVA
   * @return no-load loss in kW
   */
  private double estimateNoLoadLoss(double ratingKVA) {
    // Approximately 0.15-0.3% of rating
    return ratingKVA * 0.002;
  }

  /**
   * Estimate full-load (copper) losses.
   *
   * @param ratingKVA transformer rating in kVA
   * @return full-load loss in kW
   */
  private double estimateFullLoadLoss(double ratingKVA) {
    // Approximately 0.8-1.5% of rating
    return ratingKVA * 0.01;
  }

  /**
   * Estimate transformer weight.
   *
   * @param ratingKVA transformer rating in kVA
   * @return weight in kg
   */
  private double estimateWeight(double ratingKVA) {
    if (ratingKVA <= 1000) {
      return ratingKVA * 3.0 + 200;
    } else if (ratingKVA <= 5000) {
      return ratingKVA * 2.0 + 1200;
    } else {
      return ratingKVA * 1.5 + 3700;
    }
  }

  /**
   * Estimate transformer cost.
   *
   * @param ratingKVA transformer rating in kVA
   * @param primaryV primary voltage in V
   * @return cost in USD
   */
  private double estimateCost(double ratingKVA, double primaryV) {
    double baseCost = ratingKVA * 15.0 + 5000;
    if (primaryV > 30000) {
      baseCost *= 1.5;
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
   * @return map of transformer parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("ratedPowerKVA", ratedPowerKVA);
    map.put("primaryVoltageV", primaryVoltageV);
    map.put("secondaryVoltageV", secondaryVoltageV);
    map.put("frequencyHz", frequencyHz);
    map.put("efficiencyPercent", efficiencyPercent);
    map.put("impedancePercent", impedancePercent);
    map.put("noLoadLossKW", noLoadLossKW);
    map.put("fullLoadLossKW", fullLoadLossKW);
    map.put("totalLossKW", totalLossKW);
    map.put("vectorGroup", vectorGroup);
    map.put("coolingType", coolingType);
    map.put("tapChangerType", tapChangerType);
    map.put("tapPositions", tapPositions);
    map.put("weightKg", weightKg);
    map.put("estimatedCostUSD", estimatedCostUSD);
    return map;
  }

  // === Getters and Setters ===

  /**
   * Get rated power in kVA.
   *
   * @return rated power in kVA
   */
  public double getRatedPowerKVA() {
    return ratedPowerKVA;
  }

  /**
   * Set rated power in kVA.
   *
   * @param ratedPowerKVA rated power in kVA
   */
  public void setRatedPowerKVA(double ratedPowerKVA) {
    this.ratedPowerKVA = ratedPowerKVA;
  }

  /**
   * Get primary voltage in V.
   *
   * @return primary voltage in V
   */
  public double getPrimaryVoltageV() {
    return primaryVoltageV;
  }

  /**
   * Set primary voltage in V.
   *
   * @param primaryVoltageV primary voltage in V
   */
  public void setPrimaryVoltageV(double primaryVoltageV) {
    this.primaryVoltageV = primaryVoltageV;
  }

  /**
   * Get secondary voltage in V.
   *
   * @return secondary voltage in V
   */
  public double getSecondaryVoltageV() {
    return secondaryVoltageV;
  }

  /**
   * Set secondary voltage in V.
   *
   * @param secondaryVoltageV secondary voltage in V
   */
  public void setSecondaryVoltageV(double secondaryVoltageV) {
    this.secondaryVoltageV = secondaryVoltageV;
  }

  /**
   * Get efficiency in percent.
   *
   * @return efficiency percent
   */
  public double getEfficiencyPercent() {
    return efficiencyPercent;
  }

  /**
   * Get impedance in percent.
   *
   * @return impedance percent
   */
  public double getImpedancePercent() {
    return impedancePercent;
  }

  /**
   * Get total losses in kW.
   *
   * @return total losses in kW
   */
  public double getTotalLossKW() {
    return totalLossKW;
  }

  /**
   * Get vector group.
   *
   * @return vector group
   */
  public String getVectorGroup() {
    return vectorGroup;
  }

  /**
   * Set vector group.
   *
   * @param vectorGroup vector group
   */
  public void setVectorGroup(String vectorGroup) {
    this.vectorGroup = vectorGroup;
  }

  /**
   * Get cooling type.
   *
   * @return cooling type (ONAN, ONAF, OFAF)
   */
  public String getCoolingType() {
    return coolingType;
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

  /**
   * Set frequency in Hz.
   *
   * @param frequencyHz frequency in Hz
   */
  public void setFrequencyHz(double frequencyHz) {
    this.frequencyHz = frequencyHz;
  }

  /**
   * Get frequency in Hz.
   *
   * @return frequency in Hz
   */
  public double getFrequencyHz() {
    return frequencyHz;
  }
}
