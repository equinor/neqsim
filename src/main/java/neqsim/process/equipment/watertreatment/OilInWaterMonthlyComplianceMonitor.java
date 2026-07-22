package neqsim.process.equipment.watertreatment;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Monthly weighted oil-in-water compliance monitor for produced-water discharge.
 *
 * <p>
 * The monitor accumulates oil mass and water volume from time-weighted or batch samples, computes the weighted monthly
 * average, and estimates the remaining OIW budget for the rest of the month.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class OilInWaterMonthlyComplianceMonitor implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Compliance status categories. */
  public enum ComplianceStatus {
    /** Current trajectory is comfortably below the monthly limit. */
    NORMAL,
    /** Monthly budget should be watched but is not yet critical. */
    WATCH,
    /** The remaining monthly budget is tight and operator action is recommended. */
    WARNING,
    /** The current weighted average or remaining budget exceeds the limit. */
    EXCEEDED
  }

  /** Monthly weighted OIW limit in mg/L. */
  private double monthlyLimitMgL = 30.0;

  /** Fraction of limit at which warning status starts. */
  private double warningFraction = 0.85;

  /** Fraction of limit at which watch status starts. */
  private double watchFraction = 0.70;

  /** Number of days in the compliance month. */
  private int daysInMonth = 30;

  /** Projected daily produced-water volume in m3/day. */
  private double projectedDailyWaterVolumeM3 = 0.0;

  /** Cumulative discharged water volume in m3. */
  private double cumulativeWaterVolumeM3 = 0.0;

  /** Cumulative discharged oil mass in kg. */
  private double cumulativeOilMassKg = 0.0;

  /** Number of samples added to the monitor. */
  private int sampleCount = 0;

  /**
   * Immutable monthly compliance status result.
   */
  public static class MonthlyStatus implements Serializable {
    private static final long serialVersionUID = 1000L;

    /** Weighted average OIW to date in mg/L. */
    private final double weightedAverageMgL;

    /** Remaining allowed monthly average in mg/L. */
    private final double remainingAllowedAverageMgL;

    /** Projected total monthly water volume in m3. */
    private final double projectedMonthlyWaterVolumeM3;

    /** Remaining monthly water volume in m3. */
    private final double remainingWaterVolumeM3;

    /** Compliance status. */
    private final ComplianceStatus status;

    /** Human-readable recommendation. */
    private final String recommendation;

    /**
     * Creates a monthly status object.
     *
     * @param weightedAverageMgL weighted OIW average in mg/L
     * @param remainingAllowedAverageMgL remaining allowed average in mg/L
     * @param projectedMonthlyWaterVolumeM3 projected monthly water volume in m3
     * @param remainingWaterVolumeM3 remaining water volume in m3
     * @param status compliance status
     * @param recommendation recommendation text
     */
    public MonthlyStatus(double weightedAverageMgL, double remainingAllowedAverageMgL,
        double projectedMonthlyWaterVolumeM3, double remainingWaterVolumeM3, ComplianceStatus status,
        String recommendation) {
      this.weightedAverageMgL = weightedAverageMgL;
      this.remainingAllowedAverageMgL = remainingAllowedAverageMgL;
      this.projectedMonthlyWaterVolumeM3 = projectedMonthlyWaterVolumeM3;
      this.remainingWaterVolumeM3 = remainingWaterVolumeM3;
      this.status = status;
      this.recommendation = recommendation;
    }

    /**
     * Gets weighted OIW average.
     *
     * @return weighted OIW average in mg/L
     */
    public double getWeightedAverageMgL() {
      return weightedAverageMgL;
    }

    /**
     * Gets remaining allowed average.
     *
     * @return remaining allowed average in mg/L
     */
    public double getRemainingAllowedAverageMgL() {
      return remainingAllowedAverageMgL;
    }

    /**
     * Gets projected monthly water volume.
     *
     * @return projected monthly water volume in m3
     */
    public double getProjectedMonthlyWaterVolumeM3() {
      return projectedMonthlyWaterVolumeM3;
    }

    /**
     * Gets remaining monthly water volume.
     *
     * @return remaining water volume in m3
     */
    public double getRemainingWaterVolumeM3() {
      return remainingWaterVolumeM3;
    }

    /**
     * Gets compliance status.
     *
     * @return compliance status
     */
    public ComplianceStatus getStatus() {
      return status;
    }

    /**
     * Gets recommendation text.
     *
     * @return recommendation text
     */
    public String getRecommendation() {
      return recommendation;
    }
  }

  /**
   * Creates a monthly monitor with a 30 mg/L limit.
   */
  public OilInWaterMonthlyComplianceMonitor() {
  }

  /**
   * Adds an OIW sample weighted by discharged produced-water volume.
   *
   * @param oilInWaterMgL OIW concentration in mg/L
   * @param waterVolumeM3 produced-water volume represented by the sample in m3
   */
  public void addSample(double oilInWaterMgL, double waterVolumeM3) {
    if (waterVolumeM3 <= 0.0) {
      return;
    }
    double safeOiw = Math.max(0.0, oilInWaterMgL);
    cumulativeWaterVolumeM3 += waterVolumeM3;
    cumulativeOilMassKg += calculateOilMassKg(safeOiw, waterVolumeM3);
    sampleCount++;
  }

  /**
   * Calculates the weighted OIW average to date.
   *
   * @return weighted average in mg/L
   */
  public double getWeightedAverageMgL() {
    if (cumulativeWaterVolumeM3 <= 0.0) {
      return 0.0;
    }
    return cumulativeOilMassKg / (cumulativeWaterVolumeM3 * 0.001);
  }

  /**
   * Calculates the monthly compliance status and remaining budget.
   *
   * @param dayOfMonth current day of month, starting at 1
   * @return monthly status result
   */
  public MonthlyStatus calculateStatus(int dayOfMonth) {
    int safeDay = Math.max(1, Math.min(daysInMonth, dayOfMonth));
    double projectedMonthlyVolume = calculateProjectedMonthlyWaterVolume(safeDay);
    double remainingVolume = Math.max(0.0, projectedMonthlyVolume - cumulativeWaterVolumeM3);
    double allowedMonthlyOilKg = calculateOilMassKg(monthlyLimitMgL, projectedMonthlyVolume);
    double remainingOilKg = allowedMonthlyOilKg - cumulativeOilMassKg;
    double remainingAllowedAverage = remainingVolume > 0.0 ? remainingOilKg / (remainingVolume * 0.001) : 0.0;
    double weightedAverage = getWeightedAverageMgL();
    ComplianceStatus status = determineStatus(weightedAverage, remainingAllowedAverage);
    String recommendation = buildRecommendation(status);
    return new MonthlyStatus(weightedAverage, remainingAllowedAverage, projectedMonthlyVolume, remainingVolume, status,
        recommendation);
  }

  /**
   * Clears cumulative monthly data while preserving configuration.
   */
  public void reset() {
    cumulativeWaterVolumeM3 = 0.0;
    cumulativeOilMassKg = 0.0;
    sampleCount = 0;
  }

  /**
   * Serializes monitor state and current status to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("monthlyLimitMgL", monthlyLimitMgL);
    data.put("warningFraction", warningFraction);
    data.put("watchFraction", watchFraction);
    data.put("daysInMonth", daysInMonth);
    data.put("projectedDailyWaterVolumeM3", projectedDailyWaterVolumeM3);
    data.put("cumulativeWaterVolumeM3", cumulativeWaterVolumeM3);
    data.put("cumulativeOilMassKg", cumulativeOilMassKg);
    data.put("sampleCount", sampleCount);
    data.put("weightedAverageMgL", getWeightedAverageMgL());
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(data);
  }

  /**
   * Calculates oil mass from OIW and water volume.
   *
   * @param oilInWaterMgL OIW concentration in mg/L
   * @param waterVolumeM3 water volume in m3
   * @return oil mass in kg
   */
  private double calculateOilMassKg(double oilInWaterMgL, double waterVolumeM3) {
    return Math.max(0.0, oilInWaterMgL) * Math.max(0.0, waterVolumeM3) * 0.001;
  }

  /**
   * Calculates projected monthly water volume.
   *
   * @param dayOfMonth current day of month
   * @return projected monthly water volume in m3
   */
  private double calculateProjectedMonthlyWaterVolume(int dayOfMonth) {
    if (projectedDailyWaterVolumeM3 > 0.0) {
      return projectedDailyWaterVolumeM3 * daysInMonth;
    }
    if (cumulativeWaterVolumeM3 > 0.0 && dayOfMonth > 0) {
      return cumulativeWaterVolumeM3 / dayOfMonth * daysInMonth;
    }
    return 0.0;
  }

  /**
   * Determines status from weighted average and remaining allowed average.
   *
   * @param weightedAverage weighted OIW average in mg/L
   * @param remainingAllowedAverage remaining allowed average in mg/L
   * @return compliance status
   */
  private ComplianceStatus determineStatus(double weightedAverage, double remainingAllowedAverage) {
    if (weightedAverage > monthlyLimitMgL || remainingAllowedAverage <= 0.0) {
      return ComplianceStatus.EXCEEDED;
    }
    if (weightedAverage >= monthlyLimitMgL * warningFraction || remainingAllowedAverage < monthlyLimitMgL * 0.75) {
      return ComplianceStatus.WARNING;
    }
    if (weightedAverage >= monthlyLimitMgL * watchFraction || remainingAllowedAverage < monthlyLimitMgL) {
      return ComplianceStatus.WATCH;
    }
    return ComplianceStatus.NORMAL;
  }

  /**
   * Builds recommendation text for a status.
   *
   * @param status compliance status
   * @return recommendation text
   */
  private String buildRecommendation(ComplianceStatus status) {
    switch (status) {
    case EXCEEDED:
      return "Monthly OIW budget exceeded or no remaining oil budget; reduce discharge OIW immediately";
    case WARNING:
      return "Monthly OIW budget is tight; increase treatment margin or lower OIW target";
    case WATCH:
      return "Track OIW trend and keep dose recommendations below the remaining monthly budget";
    default:
      return "Current monthly OIW trajectory is within the configured margin";
    }
  }

  /**
   * Sets monthly OIW limit.
   *
   * @param monthlyLimitMgL monthly limit in mg/L
   */
  public void setMonthlyLimitMgL(double monthlyLimitMgL) {
    this.monthlyLimitMgL = Math.max(0.0, monthlyLimitMgL);
  }

  /**
   * Gets monthly OIW limit.
   *
   * @return monthly limit in mg/L
   */
  public double getMonthlyLimitMgL() {
    return monthlyLimitMgL;
  }

  /**
   * Sets warning fraction of monthly limit.
   *
   * @param warningFraction warning fraction from 0 to 1
   */
  public void setWarningFraction(double warningFraction) {
    this.warningFraction = Math.max(0.0, Math.min(1.0, warningFraction));
  }

  /**
   * Sets watch fraction of monthly limit.
   *
   * @param watchFraction watch fraction from 0 to 1
   */
  public void setWatchFraction(double watchFraction) {
    this.watchFraction = Math.max(0.0, Math.min(1.0, watchFraction));
  }

  /**
   * Sets number of days in the compliance month.
   *
   * @param daysInMonth number of days in month
   */
  public void setDaysInMonth(int daysInMonth) {
    this.daysInMonth = Math.max(1, daysInMonth);
  }

  /**
   * Gets number of days in the compliance month.
   *
   * @return days in month
   */
  public int getDaysInMonth() {
    return daysInMonth;
  }

  /**
   * Sets projected daily water volume.
   *
   * @param projectedDailyWaterVolumeM3 projected daily water volume in m3/day
   */
  public void setProjectedDailyWaterVolumeM3(double projectedDailyWaterVolumeM3) {
    this.projectedDailyWaterVolumeM3 = Math.max(0.0, projectedDailyWaterVolumeM3);
  }

  /**
   * Gets projected daily water volume.
   *
   * @return projected daily water volume in m3/day
   */
  public double getProjectedDailyWaterVolumeM3() {
    return projectedDailyWaterVolumeM3;
  }

  /**
   * Gets cumulative water volume.
   *
   * @return cumulative water volume in m3
   */
  public double getCumulativeWaterVolumeM3() {
    return cumulativeWaterVolumeM3;
  }

  /**
   * Gets cumulative oil mass.
   *
   * @return cumulative oil mass in kg
   */
  public double getCumulativeOilMassKg() {
    return cumulativeOilMassKg;
  }

  /**
   * Gets number of samples.
   *
   * @return sample count
   */
  public int getSampleCount() {
    return sampleCount;
  }
}
