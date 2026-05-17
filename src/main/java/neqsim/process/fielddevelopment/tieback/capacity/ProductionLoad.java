package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;

/**
 * Immutable production load for one planning period in a host tie-in study.
 *
 * <p>
 * Rates are average daily rates for the period. Volumes are calculated by multiplying rates by the
 * period length in days. Oil is reported in stock-tank barrels per day, while liquid capacity
 * checks use cubic metres per day; if an explicit total-liquid rate is not provided, oil is
 * converted to cubic metres and added to water.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ProductionLoad implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Conversion factor from stock-tank barrel to cubic metre. */
  public static final double BARREL_TO_M3 = 0.158987294928;

  /** Period label used in reports. */
  private final String periodName;

  /** Calendar year for discounting and profile alignment. */
  private final int year;

  /** Average gas rate in million standard cubic metres per day. */
  private final double gasRateMSm3d;

  /** Average oil rate in stock-tank barrels per day. */
  private final double oilRateBopd;

  /** Average produced-water rate in cubic metres per day. */
  private final double waterRateM3d;

  /** Explicit total-liquid rate in cubic metres per day, or zero to infer from oil and water. */
  private final double liquidRateM3d;

  /** Length of the planning period in days. */
  private final double periodLengthDays;

  /** Gas value in USD per million standard cubic metre. */
  private final double gasValueUsdPerMSm3;

  /** Oil value in USD per stock-tank barrel. */
  private final double oilValueUsdPerBbl;

  /** Water value in USD per cubic metre, often negative for handling cost. */
  private final double waterValueUsdPerM3;

  /** Liquid value in USD per cubic metre for explicit total-liquid streams. */
  private final double liquidValueUsdPerM3;

  /**
   * Creates a production load with a default period name and one-year length.
   *
   * @param year calendar year for the period
   * @param gasRateMSm3d average gas rate in MSm3/d
   * @param oilRateBopd average oil rate in bbl/d
   * @param waterRateM3d average water rate in m3/d
   * @param liquidRateM3d explicit total-liquid rate in m3/d, or zero to infer from oil and water
   */
  public ProductionLoad(int year, double gasRateMSm3d, double oilRateBopd, double waterRateM3d,
      double liquidRateM3d) {
    this("Y" + year, year, gasRateMSm3d, oilRateBopd, waterRateM3d, liquidRateM3d, 365.0, 0.0, 0.0,
        0.0, 0.0);
  }

  /**
   * Creates a production load with a named period and one-year length.
   *
   * @param periodName display name for the period
   * @param year calendar year for the period
   * @param gasRateMSm3d average gas rate in MSm3/d
   * @param oilRateBopd average oil rate in bbl/d
   * @param waterRateM3d average water rate in m3/d
   * @param liquidRateM3d explicit total-liquid rate in m3/d, or zero to infer from oil and water
   */
  public ProductionLoad(String periodName, int year, double gasRateMSm3d, double oilRateBopd,
      double waterRateM3d, double liquidRateM3d) {
    this(periodName, year, gasRateMSm3d, oilRateBopd, waterRateM3d, liquidRateM3d, 365.0, 0.0, 0.0,
        0.0, 0.0);
  }

  /**
   * Creates a fully specified production load.
   *
   * @param periodName display name for the period
   * @param year calendar year for the period
   * @param gasRateMSm3d average gas rate in MSm3/d
   * @param oilRateBopd average oil rate in bbl/d
   * @param waterRateM3d average water rate in m3/d
   * @param liquidRateM3d explicit total-liquid rate in m3/d, or zero to infer from oil and water
   * @param periodLengthDays number of days represented by the period
   * @param gasValueUsdPerMSm3 gas value in USD/MSm3
   * @param oilValueUsdPerBbl oil value in USD/bbl
   * @param waterValueUsdPerM3 water value in USD/m3
   * @param liquidValueUsdPerM3 liquid value in USD/m3
   */
  public ProductionLoad(String periodName, int year, double gasRateMSm3d, double oilRateBopd,
      double waterRateM3d, double liquidRateM3d, double periodLengthDays, double gasValueUsdPerMSm3,
      double oilValueUsdPerBbl, double waterValueUsdPerM3, double liquidValueUsdPerM3) {
    this.periodName = periodName == null || periodName.trim().isEmpty() ? "Y" + year : periodName;
    this.year = year;
    this.gasRateMSm3d = Math.max(0.0, gasRateMSm3d);
    this.oilRateBopd = Math.max(0.0, oilRateBopd);
    this.waterRateM3d = Math.max(0.0, waterRateM3d);
    this.liquidRateM3d = Math.max(0.0, liquidRateM3d);
    this.periodLengthDays = Math.max(0.0, periodLengthDays);
    this.gasValueUsdPerMSm3 = gasValueUsdPerMSm3;
    this.oilValueUsdPerBbl = oilValueUsdPerBbl;
    this.waterValueUsdPerM3 = waterValueUsdPerM3;
    this.liquidValueUsdPerM3 = liquidValueUsdPerM3;
  }

  /**
   * Creates a zero load for a planning period.
   *
   * @param year calendar year for the period
   * @param periodName display name for the period
   * @return zero production load
   */
  public static ProductionLoad zero(int year, String periodName) {
    return new ProductionLoad(periodName, year, 0.0, 0.0, 0.0, 0.0);
  }

  /**
   * Gets the period display name.
   *
   * @return period display name
   */
  public String getPeriodName() {
    return periodName;
  }

  /**
   * Gets the calendar year.
   *
   * @return calendar year
   */
  public int getYear() {
    return year;
  }

  /**
   * Gets the average gas rate.
   *
   * @return gas rate in MSm3/d
   */
  public double getGasRateMSm3d() {
    return gasRateMSm3d;
  }

  /**
   * Gets the average oil rate.
   *
   * @return oil rate in bbl/d
   */
  public double getOilRateBopd() {
    return oilRateBopd;
  }

  /**
   * Gets the average produced-water rate.
   *
   * @return water rate in m3/d
   */
  public double getWaterRateM3d() {
    return waterRateM3d;
  }

  /**
   * Gets the explicit total-liquid rate.
   *
   * @return explicit liquid rate in m3/d, or zero when inferred from oil and water
   */
  public double getLiquidRateM3d() {
    return liquidRateM3d;
  }

  /**
   * Gets the total liquid rate used for capacity checks.
   *
   * @return total liquid rate in m3/d
   */
  public double getTotalLiquidRateM3d() {
    if (liquidRateM3d > 0.0) {
      return liquidRateM3d;
    }
    return waterRateM3d + oilRateBopd * BARREL_TO_M3;
  }

  /**
   * Gets the period length.
   *
   * @return period length in days
   */
  public double getPeriodLengthDays() {
    return periodLengthDays;
  }

  /**
   * Gets the gas value.
   *
   * @return gas value in USD/MSm3
   */
  public double getGasValueUsdPerMSm3() {
    return gasValueUsdPerMSm3;
  }

  /**
   * Gets the oil value.
   *
   * @return oil value in USD/bbl
   */
  public double getOilValueUsdPerBbl() {
    return oilValueUsdPerBbl;
  }

  /**
   * Gets the water value.
   *
   * @return water value in USD/m3
   */
  public double getWaterValueUsdPerM3() {
    return waterValueUsdPerM3;
  }

  /**
   * Gets the liquid value.
   *
   * @return liquid value in USD/m3
   */
  public double getLiquidValueUsdPerM3() {
    return liquidValueUsdPerM3;
  }

  /**
   * Calculates period gas volume.
   *
   * @return gas volume in MSm3 for the period
   */
  public double getGasVolumeMSm3() {
    return gasRateMSm3d * periodLengthDays;
  }

  /**
   * Calculates period oil volume.
   *
   * @return oil volume in barrels for the period
   */
  public double getOilVolumeBbl() {
    return oilRateBopd * periodLengthDays;
  }

  /**
   * Calculates period produced-water volume.
   *
   * @return water volume in m3 for the period
   */
  public double getWaterVolumeM3() {
    return waterRateM3d * periodLengthDays;
  }

  /**
   * Calculates period total-liquid volume.
   *
   * @return liquid volume in m3 for the period
   */
  public double getLiquidVolumeM3() {
    return getTotalLiquidRateM3d() * periodLengthDays;
  }

  /**
   * Calculates daily value using embedded commodity values.
   *
   * @return daily value in USD/d
   */
  public double getDailyValueUsd() {
    return gasRateMSm3d * gasValueUsdPerMSm3 + oilRateBopd * oilValueUsdPerBbl
        + waterRateM3d * waterValueUsdPerM3 + getTotalLiquidRateM3d() * liquidValueUsdPerM3;
  }

  /**
   * Calculates total period value using embedded commodity values.
   *
   * @return total value in USD for the period
   */
  public double getPeriodValueUsd() {
    return getDailyValueUsd() * periodLengthDays;
  }

  /**
   * Returns true if all stream rates are effectively zero.
   *
   * @return true if the load has no production
   */
  public boolean isZero() {
    return gasRateMSm3d <= 0.0 && oilRateBopd <= 0.0 && waterRateM3d <= 0.0 && liquidRateM3d <= 0.0;
  }

  /**
   * Creates a load with a different period name and year.
   *
   * @param newPeriodName new period name
   * @param newYear new calendar year
   * @return copied load with the new period identity
   */
  public ProductionLoad withPeriod(String newPeriodName, int newYear) {
    return new ProductionLoad(newPeriodName, newYear, gasRateMSm3d, oilRateBopd, waterRateM3d,
        liquidRateM3d, periodLengthDays, gasValueUsdPerMSm3, oilValueUsdPerBbl, waterValueUsdPerM3,
        liquidValueUsdPerM3);
  }

  /**
   * Creates a load with updated commodity values.
   *
   * @param gasUsdPerMSm3 gas value in USD/MSm3
   * @param oilUsdPerBbl oil value in USD/bbl
   * @param waterUsdPerM3 water value in USD/m3
   * @param liquidUsdPerM3 liquid value in USD/m3
   * @return copied load with updated values
   */
  public ProductionLoad withCommodityValues(double gasUsdPerMSm3, double oilUsdPerBbl,
      double waterUsdPerM3, double liquidUsdPerM3) {
    return new ProductionLoad(periodName, year, gasRateMSm3d, oilRateBopd, waterRateM3d,
        liquidRateM3d, periodLengthDays, gasUsdPerMSm3, oilUsdPerBbl, waterUsdPerM3,
        liquidUsdPerM3);
  }

  /**
   * Creates a load with an updated period length.
   *
   * @param newPeriodLengthDays number of days represented by the load
   * @return copied load with updated period length
   */
  public ProductionLoad withPeriodLengthDays(double newPeriodLengthDays) {
    return new ProductionLoad(periodName, year, gasRateMSm3d, oilRateBopd, waterRateM3d,
        liquidRateM3d, newPeriodLengthDays, gasValueUsdPerMSm3, oilValueUsdPerBbl,
        waterValueUsdPerM3, liquidValueUsdPerM3);
  }

  /**
   * Scales all stream rates by a non-negative factor.
   *
   * @param factor multiplier applied to all rates
   * @return scaled production load
   */
  public ProductionLoad scale(double factor) {
    double safeFactor = Math.max(0.0, factor);
    return new ProductionLoad(periodName, year, gasRateMSm3d * safeFactor, oilRateBopd * safeFactor,
        waterRateM3d * safeFactor, liquidRateM3d * safeFactor, periodLengthDays, gasValueUsdPerMSm3,
        oilValueUsdPerBbl, waterValueUsdPerM3, liquidValueUsdPerM3);
  }

  /**
   * Adds another load to this load.
   *
   * @param other load to add
   * @return combined production load
   */
  public ProductionLoad plus(ProductionLoad other) {
    if (other == null) {
      return this;
    }
    return new ProductionLoad(periodName, year, gasRateMSm3d + other.gasRateMSm3d,
        oilRateBopd + other.oilRateBopd, waterRateM3d + other.waterRateM3d,
        combineLiquidRates(this, other), periodLengthDays, gasValueUsdPerMSm3, oilValueUsdPerBbl,
        waterValueUsdPerM3, liquidValueUsdPerM3);
  }

  /**
   * Subtracts another load and floors each rate at zero.
   *
   * @param other load to subtract
   * @return non-negative difference load
   */
  public ProductionLoad subtractNonNegative(ProductionLoad other) {
    if (other == null) {
      return this;
    }
    return new ProductionLoad(periodName, year, Math.max(0.0, gasRateMSm3d - other.gasRateMSm3d),
        Math.max(0.0, oilRateBopd - other.oilRateBopd),
        Math.max(0.0, waterRateM3d - other.waterRateM3d),
        Math.max(0.0, getTotalLiquidRateM3d() - other.getTotalLiquidRateM3d()), periodLengthDays,
        gasValueUsdPerMSm3, oilValueUsdPerBbl, waterValueUsdPerM3, liquidValueUsdPerM3);
  }

  /**
   * Combines explicit liquid rates when either load uses them.
   *
   * @param first first load
   * @param second second load
   * @return combined explicit total-liquid rate or zero when both loads infer liquid rate
   */
  private static double combineLiquidRates(ProductionLoad first, ProductionLoad second) {
    if (first.liquidRateM3d > 0.0 || second.liquidRateM3d > 0.0) {
      return first.getTotalLiquidRateM3d() + second.getTotalLiquidRateM3d();
    }
    return 0.0;
  }

  @Override
  public String toString() {
    return String.format("ProductionLoad[%s: gas=%.3f MSm3/d, oil=%.0f bbl/d, water=%.0f m3/d]",
        periodName, gasRateMSm3d, oilRateBopd, waterRateM3d);
  }
}
