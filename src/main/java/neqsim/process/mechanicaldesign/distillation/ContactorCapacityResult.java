package neqsim.process.mechanicaldesign.distillation;

import java.io.Serializable;
import com.google.gson.GsonBuilder;

/**
 * Immutable hydraulic-capacity result for a gas-liquid contactor.
 *
 * <p>
 * The result combines the controlling packing or tray flood limit, an optional outlet demister limit, and the total
 * pressure-drop limit. Utilization is expressed as a fraction of the configured design limit, where 1.0 is 100%. The
 * estimated gas-capacity multiplier assumes unchanged fluid properties and liquid rate and is intended for screening
 * and debottlenecking studies, not as a replacement for vendor rating software.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ContactorCapacityResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Internals type. */
  private final String internalsType;
  /** Packing name, or an empty string for tray columns. */
  private final String packingName;
  /** Relative packing hydraulic capacity factor. */
  private final double packingHydraulicCapacityFactor;
  /** Column internal diameter [m]. */
  private final double columnDiameter;
  /** Current gas mass flow [kg/hr]. */
  private final double gasFlowKgPerHour;
  /** Gas load factor Fs [m/s sqrt(kg/m3)]. */
  private final double fsFactor;
  /** Maximum percent flood across the contactor. */
  private final double percentFlood;
  /** Flood utilization relative to the selected design flood fraction. */
  private final double floodingUtilization;
  /** Whether the packing satisfies the minimum wetting check. */
  private final boolean wettingOk;
  /** Demister type, or an empty string when disabled. */
  private final String demisterType;
  /** Demister subtype, or an empty string when disabled. */
  private final String demisterSubType;
  /** Operating demister Souders-Brown K-factor [m/s]. */
  private final double demisterOperatingKFactor;
  /** Maximum demister Souders-Brown K-factor [m/s]. */
  private final double demisterMaximumKFactor;
  /** Demister utilization. */
  private final double demisterUtilization;
  /** Combined contactor pressure drop [bar]. */
  private final double pressureDropBar;
  /** Pressure-drop utilization. */
  private final double pressureDropUtilization;
  /** Highest controlling utilization. */
  private final double overallUtilization;
  /** Name of the controlling constraint. */
  private final String bottleneck;
  /** Estimated multiplier from current gas flow to limiting gas capacity. */
  private final double estimatedGasCapacityMultiplier;
  /** Estimated limiting gas mass flow [kg/hr]. */
  private final double estimatedMaximumGasFlowKgPerHour;
  /** Overall hydraulic design verdict. */
  private final boolean designOk;

  /**
   * Create an immutable contactor capacity result.
   *
   * @param internalsType internals type
   * @param packingName packing name, or empty for trays
   * @param packingHydraulicCapacityFactor relative packing capacity factor
   * @param columnDiameter column internal diameter [m]
   * @param gasFlowKgPerHour current gas mass flow [kg/hr]
   * @param fsFactor gas load factor Fs [m/s sqrt(kg/m3)]
   * @param percentFlood maximum percent flood
   * @param floodingUtilization flooding utilization
   * @param wettingOk whether minimum packing wetting is satisfied
   * @param demisterType demister type, or empty when disabled
   * @param demisterSubType demister subtype, or empty when disabled
   * @param demisterOperatingKFactor operating demister K-factor [m/s]
   * @param demisterMaximumKFactor maximum demister K-factor [m/s]
   * @param demisterUtilization demister utilization
   * @param pressureDropBar total contactor pressure drop [bar]
   * @param pressureDropUtilization pressure-drop utilization
   * @param overallUtilization highest utilization
   * @param bottleneck controlling constraint name
   * @param estimatedGasCapacityMultiplier estimated gas capacity multiplier
   * @param estimatedMaximumGasFlowKgPerHour estimated limiting gas flow [kg/hr]
   * @param designOk overall hydraulic verdict
   */
  public ContactorCapacityResult(String internalsType, String packingName, double packingHydraulicCapacityFactor,
      double columnDiameter, double gasFlowKgPerHour, double fsFactor, double percentFlood, double floodingUtilization,
      boolean wettingOk, String demisterType, String demisterSubType, double demisterOperatingKFactor,
      double demisterMaximumKFactor, double demisterUtilization, double pressureDropBar, double pressureDropUtilization,
      double overallUtilization, String bottleneck, double estimatedGasCapacityMultiplier,
      double estimatedMaximumGasFlowKgPerHour, boolean designOk) {
    this.internalsType = internalsType;
    this.packingName = packingName;
    this.packingHydraulicCapacityFactor = packingHydraulicCapacityFactor;
    this.columnDiameter = columnDiameter;
    this.gasFlowKgPerHour = gasFlowKgPerHour;
    this.fsFactor = fsFactor;
    this.percentFlood = percentFlood;
    this.floodingUtilization = floodingUtilization;
    this.wettingOk = wettingOk;
    this.demisterType = demisterType;
    this.demisterSubType = demisterSubType;
    this.demisterOperatingKFactor = demisterOperatingKFactor;
    this.demisterMaximumKFactor = demisterMaximumKFactor;
    this.demisterUtilization = demisterUtilization;
    this.pressureDropBar = pressureDropBar;
    this.pressureDropUtilization = pressureDropUtilization;
    this.overallUtilization = overallUtilization;
    this.bottleneck = bottleneck;
    this.estimatedGasCapacityMultiplier = estimatedGasCapacityMultiplier;
    this.estimatedMaximumGasFlowKgPerHour = estimatedMaximumGasFlowKgPerHour;
    this.designOk = designOk;
  }

  /** @return internals type */
  public String getInternalsType() {
    return internalsType;
  }

  /** @return packing name, or an empty string for trays */
  public String getPackingName() {
    return packingName;
  }

  /** @return relative packing hydraulic capacity factor */
  public double getPackingHydraulicCapacityFactor() {
    return packingHydraulicCapacityFactor;
  }

  /** @return column internal diameter [m] */
  public double getColumnDiameter() {
    return columnDiameter;
  }

  /** @return current gas mass flow [kg/hr] */
  public double getGasFlowKgPerHour() {
    return gasFlowKgPerHour;
  }

  /** @return gas load factor Fs [m/s sqrt(kg/m3)] */
  public double getFsFactor() {
    return fsFactor;
  }

  /** @return maximum percent flood */
  public double getPercentFlood() {
    return percentFlood;
  }

  /** @return flooding utilization relative to the design flood fraction */
  public double getFloodingUtilization() {
    return floodingUtilization;
  }

  /** @return whether the minimum packing wetting check is satisfied */
  public boolean isWettingOk() {
    return wettingOk;
  }

  /** @return demister type, or an empty string when disabled */
  public String getDemisterType() {
    return demisterType;
  }

  /** @return demister subtype, or an empty string when disabled */
  public String getDemisterSubType() {
    return demisterSubType;
  }

  /** @return operating demister K-factor [m/s] */
  public double getDemisterOperatingKFactor() {
    return demisterOperatingKFactor;
  }

  /** @return maximum demister K-factor [m/s] */
  public double getDemisterMaximumKFactor() {
    return demisterMaximumKFactor;
  }

  /** @return demister utilization */
  public double getDemisterUtilization() {
    return demisterUtilization;
  }

  /** @return total contactor pressure drop [bar] */
  public double getPressureDropBar() {
    return pressureDropBar;
  }

  /** @return pressure-drop utilization */
  public double getPressureDropUtilization() {
    return pressureDropUtilization;
  }

  /** @return highest controlling utilization */
  public double getOverallUtilization() {
    return overallUtilization;
  }

  /** @return controlling constraint name */
  public String getBottleneck() {
    return bottleneck;
  }

  /** @return estimated multiplier from current gas flow to limiting capacity */
  public double getEstimatedGasCapacityMultiplier() {
    return estimatedGasCapacityMultiplier;
  }

  /** @return estimated limiting gas mass flow [kg/hr] */
  public double getEstimatedMaximumGasFlowKgPerHour() {
    return estimatedMaximumGasFlowKgPerHour;
  }

  /** @return true when the hydraulic design checks are satisfied */
  public boolean isDesignOk() {
    return designOk;
  }

  /**
   * Serialize this result as pretty-printed JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
