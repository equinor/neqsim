package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;
import neqsim.process.fielddevelopment.tieback.HostFacility;

/** Nameplate oil, gas, water, total-liquid and power capacities for a process facility. */
public final class FacilityCapacity implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double BBL_PER_SM3 = 6.28981077;
  private final double oilSm3PerDay;
  private final double gasSm3PerDay;
  private final double waterSm3PerDay;
  private final double liquidSm3PerDay;
  private final double powerKw;

  /** Creates a capacity envelope. Non-positive entries mean that dimension is not constrained. */
  public FacilityCapacity(double oilSm3PerDay, double gasSm3PerDay, double waterSm3PerDay,
      double liquidSm3PerDay, double powerKw) {
    this.oilSm3PerDay = capacity(oilSm3PerDay);
    this.gasSm3PerDay = capacity(gasSm3PerDay);
    this.waterSm3PerDay = capacity(waterSm3PerDay);
    this.liquidSm3PerDay = capacity(liquidSm3PerDay);
    this.powerKw = capacity(powerKw);
  }

  /** Creates a capacity envelope by applying a design margin to process design rates. */
  public static FacilityCapacity fromDesignRates(FacilityProductionRate rates, double designPowerKw,
      double designMargin) {
    if (rates == null || !Double.isFinite(designMargin) || designMargin < 1.0) {
      throw new IllegalArgumentException("valid design rates and a design margin of at least one are required");
    }
    return new FacilityCapacity(rates.getOilSm3PerDay() * designMargin, rates.getGasSm3PerDay() * designMargin,
        rates.getWaterSm3PerDay() * designMargin, rates.getLiquidSm3PerDay() * designMargin,
        designPowerKw * designMargin);
  }

  /** Converts an existing tieback host's nameplate capacities to lifecycle units. */
  public static FacilityCapacity fromHostFacility(HostFacility host, double powerCapacityKw) {
    if (host == null) {
      throw new IllegalArgumentException("host facility is required");
    }
    return new FacilityCapacity(host.getOilCapacityBopd() / BBL_PER_SM3, host.getGasCapacityMSm3d() * 1.0e6,
        host.getWaterCapacityM3d(), host.getLiquidCapacityM3d(), powerCapacityKw);
  }

  private static double capacity(double value) {
    if (Double.isNaN(value) || value == Double.NEGATIVE_INFINITY) {
      throw new IllegalArgumentException("capacity must be non-negative, positive infinity, or zero for unconstrained");
    }
    return value <= 0.0 ? Double.POSITIVE_INFINITY : value;
  }

  /** Returns the maximum common multiplier that fits a production vector inside this capacity. */
  public double scaleToFit(FacilityProductionRate rates) {
    double scale = 1.0;
    scale = Math.min(scale, ratio(oilSm3PerDay, rates.getOilSm3PerDay()));
    scale = Math.min(scale, ratio(gasSm3PerDay, rates.getGasSm3PerDay()));
    scale = Math.min(scale, ratio(waterSm3PerDay, rates.getWaterSm3PerDay()));
    scale = Math.min(scale, ratio(liquidSm3PerDay, rates.getLiquidSm3PerDay()));
    return Math.max(0.0, scale);
  }

  /** Returns the remaining nameplate capacity after admitting a production vector. */
  public FacilityCapacity remainingAfter(FacilityProductionRate rates) {
    return new FacilityCapacity(remaining(oilSm3PerDay, rates.getOilSm3PerDay()),
        remaining(gasSm3PerDay, rates.getGasSm3PerDay()), remaining(waterSm3PerDay, rates.getWaterSm3PerDay()),
        remaining(liquidSm3PerDay, rates.getLiquidSm3PerDay()), powerKw);
  }

  private static double remaining(double capacity, double used) {
    return Double.isInfinite(capacity) ? capacity : Math.max(1.0e-12, capacity - used);
  }

  private static double ratio(double capacity, double demand) {
    return demand <= 1.0e-12 || Double.isInfinite(capacity) ? 1.0 : capacity / demand;
  }

  /** Returns the highest nameplate utilization across oil, gas, water and total liquid. */
  public double getMaximumUtilization(FacilityProductionRate rates) {
    return Math.max(Math.max(utilization(rates.getOilSm3PerDay(), oilSm3PerDay),
        utilization(rates.getGasSm3PerDay(), gasSm3PerDay)),
        Math.max(utilization(rates.getWaterSm3PerDay(), waterSm3PerDay),
            utilization(rates.getLiquidSm3PerDay(), liquidSm3PerDay)));
  }

  /** Returns the name of the most utilized nameplate dimension. */
  public String getPrimaryConstraint(FacilityProductionRate rates) {
    String name = "oil capacity";
    double maximum = utilization(rates.getOilSm3PerDay(), oilSm3PerDay);
    double gas = utilization(rates.getGasSm3PerDay(), gasSm3PerDay);
    double water = utilization(rates.getWaterSm3PerDay(), waterSm3PerDay);
    double liquid = utilization(rates.getLiquidSm3PerDay(), liquidSm3PerDay);
    if (gas > maximum) {
      name = "gas capacity";
      maximum = gas;
    }
    if (water > maximum) {
      name = "water capacity";
      maximum = water;
    }
    if (liquid > maximum) {
      name = "total-liquid capacity";
    }
    return name;
  }

  private static double utilization(double duty, double capacity) {
    return Double.isInfinite(capacity) ? 0.0 : duty / capacity;
  }

  /** Returns oil capacity in Sm3/day, or positive infinity when unconstrained. */
  public double getOilSm3PerDay() {
    return oilSm3PerDay;
  }

  /** Returns gas capacity in Sm3/day, or positive infinity when unconstrained. */
  public double getGasSm3PerDay() {
    return gasSm3PerDay;
  }

  /** Returns produced-water capacity in Sm3/day, or positive infinity when unconstrained. */
  public double getWaterSm3PerDay() {
    return waterSm3PerDay;
  }

  /** Returns total-liquid capacity in Sm3/day, or positive infinity when unconstrained. */
  public double getLiquidSm3PerDay() {
    return liquidSm3PerDay;
  }

  /** Returns power capacity in kW, or positive infinity when unconstrained. */
  public double getPowerKw() {
    return powerKw;
  }
}
