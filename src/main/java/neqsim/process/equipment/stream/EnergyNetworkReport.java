package neqsim.process.equipment.stream;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;
import neqsim.util.unit.PowerUnit;

/**
 * Auditable result of an {@link EnergyBus} balance calculation.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EnergyNetworkReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String busName;
  private final List<EnergyAllocation> allocations;
  private final double offeredSupply;
  private final double acceptedSupply;
  private final double requestedDemand;
  private final double servedDemand;
  private final double unmetDemand;
  private final double curtailedSupply;
  private final double balancingGeneration;
  private final double balancingConsumption;
  private final double conversionLoss;
  private final double fuelEnergyRate;
  private final double operatingCostPerHour;
  private final double co2EmissionRate;

  /**
   * Creates a network report.
   *
   * @param busName bus name
   * @param allocations participant allocations
   * @param offeredSupply offered supply in W
   * @param acceptedSupply accepted supply in W
   * @param requestedDemand requested demand in W
   * @param servedDemand served demand in W
   * @param unmetDemand unmet demand in W
   * @param curtailedSupply curtailed supply in W
   * @param balancingGeneration balancing generation in W
   * @param balancingConsumption balancing consumption in W
   * @param conversionLoss conversion loss in W
   * @param fuelEnergyRate accepted chemical or fuel energy rate in W
   * @param operatingCostPerHour operating cost per hour
   * @param co2EmissionRate CO2-equivalent emission rate in kg/h
   */
  public EnergyNetworkReport(String busName, List<EnergyAllocation> allocations, double offeredSupply,
      double acceptedSupply, double requestedDemand, double servedDemand, double unmetDemand, double curtailedSupply,
      double balancingGeneration, double balancingConsumption, double conversionLoss, double fuelEnergyRate,
      double operatingCostPerHour, double co2EmissionRate) {
    this.busName = busName;
    this.allocations = new ArrayList<EnergyAllocation>(allocations);
    this.offeredSupply = offeredSupply;
    this.acceptedSupply = acceptedSupply;
    this.requestedDemand = requestedDemand;
    this.servedDemand = servedDemand;
    this.unmetDemand = unmetDemand;
    this.curtailedSupply = curtailedSupply;
    this.balancingGeneration = balancingGeneration;
    this.balancingConsumption = balancingConsumption;
    this.conversionLoss = conversionLoss;
    this.fuelEnergyRate = fuelEnergyRate;
    this.operatingCostPerHour = operatingCostPerHour;
    this.co2EmissionRate = co2EmissionRate;
  }

  /**
   * Gets the bus name.
   *
   * @return bus name
   */
  public String getBusName() {
    return busName;
  }

  /**
   * Gets immutable participant results.
   *
   * @return allocation results
   */
  public List<EnergyAllocation> getAllocations() {
    return Collections.unmodifiableList(allocations);
  }

  /**
   * Gets offered supply.
   *
   * @return offered supply in W
   */
  public double getOfferedSupply() {
    return offeredSupply;
  }

  /**
   * Gets accepted supply.
   *
   * @return accepted supply in W
   */
  public double getAcceptedSupply() {
    return acceptedSupply;
  }

  /**
   * Gets requested demand.
   *
   * @return requested demand in W
   */
  public double getRequestedDemand() {
    return requestedDemand;
  }

  /**
   * Gets served demand.
   *
   * @return served demand in W
   */
  public double getServedDemand() {
    return servedDemand;
  }

  /**
   * Gets unmet demand.
   *
   * @return unmet demand in W
   */
  public double getUnmetDemand() {
    return unmetDemand;
  }

  /**
   * Gets curtailed supply.
   *
   * @return curtailed supply in W
   */
  public double getCurtailedSupply() {
    return curtailedSupply;
  }

  /**
   * Gets balancing generation.
   *
   * @return balancing generation in W
   */
  public double getBalancingGeneration() {
    return balancingGeneration;
  }

  /**
   * Gets balancing consumption.
   *
   * @return balancing consumption in W
   */
  public double getBalancingConsumption() {
    return balancingConsumption;
  }

  /**
   * Gets conversion losses reported by connected ports.
   *
   * @return conversion loss in W
   */
  public double getConversionLoss() {
    return conversionLoss;
  }

  /**
   * Gets accepted chemical or fuel energy rate.
   *
   * @return fuel energy rate in W
   */
  public double getFuelEnergyRate() {
    return fuelEnergyRate;
  }

  /**
   * Gets accepted chemical or fuel energy rate in a requested power unit.
   *
   * @param unit requested power unit
   * @return fuel energy rate in the requested unit
   */
  public double getFuelEnergyRate(String unit) {
    return new PowerUnit(fuelEnergyRate, "W").getValue(unit);
  }

  /**
   * Gets the hourly operating cost at the current load.
   *
   * @return cost per hour
   */
  public double getOperatingCostPerHour() {
    return operatingCostPerHour;
  }

  /**
   * Gets the current CO2-equivalent emission rate.
   *
   * @return emission rate in kg/h
   */
  public double getCo2EmissionRate() {
    return co2EmissionRate;
  }

  /**
   * Gets network delivery efficiency.
   *
   * @return served demand divided by accepted supply, or one when both are zero
   */
  public double getEfficiency() {
    if (acceptedSupply <= 0.0) {
      return servedDemand <= 0.0 ? 1.0 : 0.0;
    }
    return Math.max(0.0, Math.min(1.0, servedDemand / acceptedSupply));
  }

  /**
   * Serializes the report as JSON.
   *
   * @return JSON report
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
