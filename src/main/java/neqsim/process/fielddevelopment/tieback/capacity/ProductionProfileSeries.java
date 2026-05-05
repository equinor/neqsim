package neqsim.process.fielddevelopment.tieback.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered production profile for host base production or satellite production.
 *
 * <p>
 * The planner aligns profiles by calendar year when possible and otherwise by list index. This
 * keeps screening workflows compact while still allowing explicit year-by-year scheduling.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ProductionProfileSeries implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Profile name used in reporting. */
  private final String name;

  /** Ordered list of production loads. */
  private final List<ProductionLoad> loads = new ArrayList<ProductionLoad>();

  /**
   * Creates an empty production profile.
   *
   * @param name profile name used in reports
   */
  public ProductionProfileSeries(String name) {
    this.name = name == null || name.trim().isEmpty() ? "production profile" : name;
  }

  /**
   * Creates a gas-only profile from an array of rates.
   *
   * @param name profile name
   * @param startYear first calendar year
   * @param gasRatesMSm3d gas rates in MSm3/d
   * @return profile containing one load per gas-rate entry
   */
  public static ProductionProfileSeries fromGasRates(String name, int startYear,
      double[] gasRatesMSm3d) {
    ProductionProfileSeries profile = new ProductionProfileSeries(name);
    if (gasRatesMSm3d != null) {
      for (int index = 0; index < gasRatesMSm3d.length; index++) {
        profile.addPeriod(startYear + index, gasRatesMSm3d[index], 0.0, 0.0, 0.0);
      }
    }
    return profile;
  }

  /**
   * Creates an oil-only profile from an array of rates.
   *
   * @param name profile name
   * @param startYear first calendar year
   * @param oilRatesBopd oil rates in bbl/d
   * @return profile containing one load per oil-rate entry
   */
  public static ProductionProfileSeries fromOilRates(String name, int startYear,
      double[] oilRatesBopd) {
    ProductionProfileSeries profile = new ProductionProfileSeries(name);
    if (oilRatesBopd != null) {
      for (int index = 0; index < oilRatesBopd.length; index++) {
        profile.addPeriod(startYear + index, 0.0, oilRatesBopd[index], 0.0, 0.0);
      }
    }
    return profile;
  }

  /**
   * Gets the profile name.
   *
   * @return profile name
   */
  public String getName() {
    return name;
  }

  /**
   * Adds a production load to the profile.
   *
   * @param load production load to append
   * @return this profile for method chaining
   */
  public ProductionProfileSeries add(ProductionLoad load) {
    if (load != null) {
      loads.add(load);
    }
    return this;
  }

  /**
   * Adds a production period with a default period name.
   *
   * @param year calendar year
   * @param gasRateMSm3d gas rate in MSm3/d
   * @param oilRateBopd oil rate in bbl/d
   * @param waterRateM3d water rate in m3/d
   * @param liquidRateM3d explicit total-liquid rate in m3/d, or zero to infer
   * @return this profile for method chaining
   */
  public ProductionProfileSeries addPeriod(int year, double gasRateMSm3d, double oilRateBopd,
      double waterRateM3d, double liquidRateM3d) {
    return add(new ProductionLoad(year, gasRateMSm3d, oilRateBopd, waterRateM3d, liquidRateM3d));
  }

  /**
   * Adds a named production period.
   *
   * @param periodName display name for the period
   * @param year calendar year
   * @param gasRateMSm3d gas rate in MSm3/d
   * @param oilRateBopd oil rate in bbl/d
   * @param waterRateM3d water rate in m3/d
   * @param liquidRateM3d explicit total-liquid rate in m3/d, or zero to infer
   * @return this profile for method chaining
   */
  public ProductionProfileSeries addPeriod(String periodName, int year, double gasRateMSm3d,
      double oilRateBopd, double waterRateM3d, double liquidRateM3d) {
    return add(new ProductionLoad(periodName, year, gasRateMSm3d, oilRateBopd, waterRateM3d,
        liquidRateM3d));
  }

  /**
   * Gets the number of loads in the profile.
   *
   * @return profile size
   */
  public int size() {
    return loads.size();
  }

  /**
   * Checks whether the profile has no loads.
   *
   * @return true if the profile is empty
   */
  public boolean isEmpty() {
    return loads.isEmpty();
  }

  /**
   * Gets a load by zero-based index.
   *
   * @param index zero-based index
   * @return production load
   */
  public ProductionLoad getLoad(int index) {
    return loads.get(index);
  }

  /**
   * Finds a load by calendar year.
   *
   * @param year calendar year
   * @return production load for the year, or null if none exists
   */
  public ProductionLoad getLoadByYear(int year) {
    for (ProductionLoad load : loads) {
      if (load.getYear() == year) {
        return load;
      }
    }
    return null;
  }

  /**
   * Gets a load by year and falls back to list index.
   *
   * @param year calendar year to look up first
   * @param index zero-based fallback index
   * @param fallbackPeriodName period name used for a zero fallback load
   * @return matching load or a zero load
   */
  public ProductionLoad getLoadByYearOrIndex(int year, int index, String fallbackPeriodName) {
    ProductionLoad byYear = getLoadByYear(year);
    if (byYear != null) {
      return byYear;
    }
    if (index >= 0 && index < loads.size()) {
      return loads.get(index);
    }
    return ProductionLoad.zero(year, fallbackPeriodName);
  }

  /**
   * Gets an unmodifiable view of the loads.
   *
   * @return production loads
   */
  public List<ProductionLoad> getLoads() {
    return Collections.unmodifiableList(loads);
  }
}
