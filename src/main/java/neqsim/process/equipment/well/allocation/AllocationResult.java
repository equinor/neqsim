package neqsim.process.equipment.well.allocation;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Result of well production allocation.
 *
 * @author ESOL
 * @version 1.0
 */
public class AllocationResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Instant timestamp;
  private final Map<String, Double> oilRates;
  private final Map<String, Double> gasRates;
  private final Map<String, Double> waterRates;
  private final Map<String, Double> uncertainties;
  private final double totalOilRate;
  private final double totalGasRate;
  private final double totalWaterRate;
  private final double allocationError;
  private final boolean balanced;

  /**
   * Creates an allocation result.
   *
   * @param oilRates oil rates by well name (Sm3/day)
   * @param gasRates gas rates by well name (Sm3/day)
   * @param waterRates water rates by well name (Sm3/day)
   * @param uncertainties allocation uncertainties by well
   * @param allocationError total allocation error
   */
  public AllocationResult(Map<String, Double> oilRates, Map<String, Double> gasRates,
      Map<String, Double> waterRates, Map<String, Double> uncertainties, double allocationError) {
    this.timestamp = Instant.now();
    this.oilRates = new HashMap<>(oilRates);
    this.gasRates = new HashMap<>(gasRates);
    this.waterRates = new HashMap<>(waterRates);
    this.uncertainties = new HashMap<>(uncertainties);
    this.allocationError = allocationError;

    this.totalOilRate = oilRates.values().stream().mapToDouble(Double::doubleValue).sum();
    this.totalGasRate = gasRates.values().stream().mapToDouble(Double::doubleValue).sum();
    this.totalWaterRate = waterRates.values().stream().mapToDouble(Double::doubleValue).sum();

    // Consider balanced if allocation error is less than 0.1%
    this.balanced = Math.abs(allocationError) < 0.001;
  }

  /**
   * Gets the allocation timestamp.
   *
   * @return timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the oil rate for a specific well.
   *
   * @param wellName well name
   * @return oil rate in Sm3/day
   */
  public double getOilRate(String wellName) {
    return oilRates.getOrDefault(wellName, 0.0);
  }

  /**
   * Gets the gas rate for a specific well.
   *
   * @param wellName well name
   * @return gas rate in Sm3/day
   */
  public double getGasRate(String wellName) {
    return gasRates.getOrDefault(wellName, 0.0);
  }

  /**
   * Gets the water rate for a specific well.
   *
   * @param wellName well name
   * @return water rate in Sm3/day
   */
  public double getWaterRate(String wellName) {
    return waterRates.getOrDefault(wellName, 0.0);
  }

  /**
   * Gets the allocation uncertainty for a specific well.
   *
   * @param wellName well name
   * @return uncertainty (0-1 scale)
   */
  public double getUncertainty(String wellName) {
    return uncertainties.getOrDefault(wellName, 1.0);
  }

  /**
   * Gets all oil rates.
   *
   * @return map of well name to oil rate
   */
  public Map<String, Double> getAllOilRates() {
    return new HashMap<>(oilRates);
  }

  /**
   * Gets all gas rates.
   *
   * @return map of well name to gas rate
   */
  public Map<String, Double> getAllGasRates() {
    return new HashMap<>(gasRates);
  }

  /**
   * Gets all water rates.
   *
   * @return map of well name to water rate
   */
  public Map<String, Double> getAllWaterRates() {
    return new HashMap<>(waterRates);
  }

  /**
   * Gets the total oil rate.
   *
   * @return total oil rate in Sm3/day
   */
  public double getTotalOilRate() {
    return totalOilRate;
  }

  /**
   * Gets the total gas rate.
   *
   * @return total gas rate in Sm3/day
   */
  public double getTotalGasRate() {
    return totalGasRate;
  }

  /**
   * Gets the total water rate.
   *
   * @return total water rate in Sm3/day
   */
  public double getTotalWaterRate() {
    return totalWaterRate;
  }

  /**
   * Gets the allocation error (imbalance).
   *
   * @return allocation error (relative)
   */
  public double getAllocationError() {
    return allocationError;
  }

  /**
   * Checks if the allocation is balanced.
   *
   * @return true if balanced
   */
  public boolean isBalanced() {
    return balanced;
  }

  /**
   * Gets the GOR for a specific well.
   *
   * @param wellName well name
   * @return GOR (Sm3/Sm3)
   */
  public double getGOR(String wellName) {
    double oil = getOilRate(wellName);
    if (oil > 0) {
      return getGasRate(wellName) / oil;
    }
    return 0;
  }

  /**
   * Gets the water cut for a specific well.
   *
   * @param wellName well name
   * @return water cut (0-1)
   */
  public double getWaterCut(String wellName) {
    double oil = getOilRate(wellName);
    double water = getWaterRate(wellName);
    double total = oil + water;
    if (total > 0) {
      return water / total;
    }
    return 0;
  }

  /**
   * Gets the well names.
   *
   * @return array of well names
   */
  public String[] getWellNames() {
    return oilRates.keySet().toArray(new String[0]);
  }

  /**
   * Gets the allocation for a single well as a map.
   *
   * @param wellName well name
   * @return map with oil, gas, water rates
   */
  public Map<String, Double> getWellAllocation(String wellName) {
    Map<String, Double> allocation = new HashMap<>();
    allocation.put("oil", getOilRate(wellName));
    allocation.put("gas", getGasRate(wellName));
    allocation.put("water", getWaterRate(wellName));
    allocation.put("gor", getGOR(wellName));
    allocation.put("waterCut", getWaterCut(wellName));
    allocation.put("uncertainty", getUncertainty(wellName));
    return allocation;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("AllocationResult[%s, balanced=%s, error=%.4f%%]\n",
        timestamp.toString(), balanced, allocationError * 100));
    for (String well : oilRates.keySet()) {
      sb.append(String.format("  %s: Oil=%.1f, Gas=%.1f, Water=%.1f, GOR=%.1f, WC=%.2f%%\n", well,
          getOilRate(well), getGasRate(well), getWaterRate(well), getGOR(well),
          getWaterCut(well) * 100));
    }
    return sb.toString();
  }
}
