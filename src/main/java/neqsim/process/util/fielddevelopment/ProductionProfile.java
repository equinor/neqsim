package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimization.ProductionOptimizer;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationResult;

/**
 * Models production decline curves and plateau rates for field development planning.
 *
 * <p>
 * This class provides comprehensive production forecasting capabilities including:
 * <ul>
 * <li>Standard decline curve models (exponential, hyperbolic, harmonic)</li>
 * <li>Plateau rate handling with automatic transition to decline</li>
 * <li>Integration with facility bottleneck analysis</li>
 * <li>Cumulative production tracking</li>
 * <li>Economic limit enforcement</li>
 * </ul>
 *
 * <h2>Decline Curve Theory</h2>
 * <p>
 * The class implements Arps decline curve equations:
 * <ul>
 * <li><b>Exponential (b=0)</b>: {@code q(t) = q_i * exp(-D*t)}</li>
 * <li><b>Hyperbolic (0&lt;b&lt;1)</b>: {@code q(t) = q_i / (1 + b*D*t)^(1/b)}</li>
 * <li><b>Harmonic (b=1)</b>: {@code q(t) = q_i / (1 + D*t)}</li>
 * </ul>
 * where:
 * <ul>
 * <li>{@code q_i} = initial production rate</li>
 * <li>{@code D} = nominal decline rate (typically per year)</li>
 * <li>{@code b} = hyperbolic exponent</li>
 * <li>{@code t} = time</li>
 * </ul>
 *
 * <h2>Facility Constraint Integration</h2>
 * <p>
 * When a {@link ProcessSystem} is provided, the production forecast respects facility constraints
 * by using {@link ProductionOptimizer} to determine the maximum sustainable rate at each time step.
 * This enables realistic forecasts where:
 * <ul>
 * <li>Plateau rate is limited by the tightest facility bottleneck</li>
 * <li>Bottleneck equipment shifts as production declines</li>
 * <li>Facility utilization is tracked throughout field life</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * // Basic decline curve calculation
 * DeclineParameters params = new DeclineParameters(10000.0, // initial rate: 10,000 Sm3/day
 *     0.15, // decline rate: 15% per year
 *     DeclineType.EXPONENTIAL, "Sm3/day");
 *
 * double rateAfter2Years = ProductionProfile.calculateRate(params, 2.0);
 *
 * // Full forecast with facility constraints
 * ProductionProfile profile = new ProductionProfile(facilityProcess);
 * ProductionForecast forecast = profile.forecast(feedStream, params, 8000.0, // plateau rate
 *     3.0, // plateau duration (years)
 *     500.0, // economic limit
 *     20.0, // forecast horizon (years)
 *     30.0); // time step (days)
 *
 * System.out.println("Total recovery: " + forecast.getTotalRecovery());
 * System.out.println(forecast.toMarkdownTable());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ProductionOptimizer
 * @see neqsim.process.equipment.reservoir.SimpleReservoir
 */
public class ProductionProfile implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Days per year for time conversion. */
  private static final double DAYS_PER_YEAR = 365.25;

  /** Process system representing the surface facility. */
  private final ProcessSystem facility;

  /** Production optimizer for bottleneck analysis. */
  private transient ProductionOptimizer optimizer;

  /**
   * Decline curve model types based on Arps equations.
   *
   * <p>
   * The decline type determines how production rate decreases over time:
   * <ul>
   * <li>{@link #EXPONENTIAL} - Constant percentage decline, most conservative</li>
   * <li>{@link #HYPERBOLIC} - Declining percentage decline, most common in practice</li>
   * <li>{@link #HARMONIC} - Special case of hyperbolic with b=1</li>
   * </ul>
   */
  public enum DeclineType {
    /**
     * Exponential decline: q(t) = q_i * exp(-D*t).
     * <p>
     * Characterized by constant percentage decline rate. Often used for:
     * <ul>
     * <li>Gas wells under depletion drive</li>
     * <li>Conservative decline estimates</li>
     * <li>Late-life production</li>
     * </ul>
     */
    EXPONENTIAL,

    /**
     * Hyperbolic decline: q(t) = q_i / (1 + b*D*t)^(1/b).
     * <p>
     * Most commonly observed in oil and gas wells. The exponent b typically ranges from 0.3 to 0.7
     * for oil wells and 0.4 to 0.8 for gas wells.
     */
    HYPERBOLIC,

    /**
     * Harmonic decline: q(t) = q_i / (1 + D*t).
     * <p>
     * Special case of hyperbolic decline with b=1. Represents the slowest possible decline rate and
     * is rarely observed in practice without enhanced recovery.
     */
    HARMONIC
  }

  /**
   * Container for decline curve parameters.
   *
   * <p>
   * Immutable class that holds all parameters needed to calculate production rate at any point in
   * time using Arps decline curve equations.
   *
   * <p>
   * <b>Parameter Guidelines</b>
   * </p>
   * <table border="1">
   * <caption>Typical decline parameters by reservoir type</caption>
   * <tr>
   * <th>Reservoir Type</th>
   * <th>Decline Type</th>
   * <th>D (1/year)</th>
   * <th>b</th>
   * </tr>
   * <tr>
   * <td>Solution gas drive</td>
   * <td>Hyperbolic</td>
   * <td>0.10-0.30</td>
   * <td>0.3-0.5</td>
   * </tr>
   * <tr>
   * <td>Water drive</td>
   * <td>Exponential</td>
   * <td>0.05-0.15</td>
   * <td>0</td>
   * </tr>
   * <tr>
   * <td>Gas cap drive</td>
   * <td>Hyperbolic</td>
   * <td>0.15-0.25</td>
   * <td>0.4-0.6</td>
   * </tr>
   * <tr>
   * <td>Tight gas</td>
   * <td>Hyperbolic</td>
   * <td>0.30-0.60</td>
   * <td>0.5-0.8</td>
   * </tr>
   * </table>
   */
  public static final class DeclineParameters implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double initialRate;
    private final double declineRate;
    private final double hyperbolicExponent;
    private final DeclineType type;
    private final String rateUnit;
    private final String timeUnit;

    /**
     * Creates decline parameters with default hyperbolic exponent of 0.5.
     *
     * @param initialRate initial production rate (q_i)
     * @param declineRate nominal decline rate (D), typically per year
     * @param type decline curve type
     * @param rateUnit engineering unit for rate (e.g., "Sm3/day", "kg/hr")
     */
    public DeclineParameters(double initialRate, double declineRate, DeclineType type,
        String rateUnit) {
      this(initialRate, declineRate, 0.5, type, rateUnit, "year");
    }

    /**
     * Creates decline parameters with specified hyperbolic exponent.
     *
     * @param initialRate initial production rate (q_i)
     * @param declineRate nominal decline rate (D)
     * @param hyperbolicExponent Arps b-factor (0 &lt; b &lt; 1 for hyperbolic, ignored for
     *        exponential)
     * @param type decline curve type
     * @param rateUnit engineering unit for rate (e.g., "Sm3/day", "kg/hr")
     */
    public DeclineParameters(double initialRate, double declineRate, double hyperbolicExponent,
        DeclineType type, String rateUnit) {
      this(initialRate, declineRate, hyperbolicExponent, type, rateUnit, "year");
    }

    /**
     * Creates decline parameters with full specification.
     *
     * @param initialRate initial production rate (q_i)
     * @param declineRate nominal decline rate (D)
     * @param hyperbolicExponent Arps b-factor (0 &lt; b &lt;= 1 for hyperbolic)
     * @param type decline curve type
     * @param rateUnit engineering unit for rate
     * @param timeUnit time unit for decline rate (e.g., "year", "day")
     * @throws IllegalArgumentException if parameters are invalid
     */
    public DeclineParameters(double initialRate, double declineRate, double hyperbolicExponent,
        DeclineType type, String rateUnit, String timeUnit) {
      if (initialRate <= 0) {
        throw new IllegalArgumentException("Initial rate must be positive: " + initialRate);
      }
      if (declineRate < 0) {
        throw new IllegalArgumentException("Decline rate cannot be negative: " + declineRate);
      }
      if (type == DeclineType.HYPERBOLIC && (hyperbolicExponent <= 0 || hyperbolicExponent > 1)) {
        throw new IllegalArgumentException(
            "Hyperbolic exponent must be in (0, 1]: " + hyperbolicExponent);
      }
      this.initialRate = initialRate;
      this.declineRate = declineRate;
      this.hyperbolicExponent = hyperbolicExponent;
      this.type = Objects.requireNonNull(type, "Decline type is required");
      this.rateUnit = Objects.requireNonNull(rateUnit, "Rate unit is required");
      this.timeUnit = Objects.requireNonNull(timeUnit, "Time unit is required");
    }

    /**
     * Gets the initial production rate (q_i).
     *
     * @return initial rate in specified rate units
     */
    public double getInitialRate() {
      return initialRate;
    }

    /**
     * Gets the nominal decline rate (D).
     *
     * @return decline rate in inverse time units (e.g., 1/year)
     */
    public double getDeclineRate() {
      return declineRate;
    }

    /**
     * Gets the hyperbolic exponent (b-factor).
     *
     * @return b-factor (0 for exponential, 1 for harmonic, between for hyperbolic)
     */
    public double getHyperbolicExponent() {
      return hyperbolicExponent;
    }

    /**
     * Gets the decline curve type.
     *
     * @return decline type enum
     */
    public DeclineType getType() {
      return type;
    }

    /**
     * Gets the rate unit string.
     *
     * @return rate unit (e.g., "Sm3/day")
     */
    public String getRateUnit() {
      return rateUnit;
    }

    /**
     * Gets the time unit string.
     *
     * @return time unit (e.g., "year")
     */
    public String getTimeUnit() {
      return timeUnit;
    }

    /**
     * Creates a copy with a different initial rate.
     *
     * @param newInitialRate new initial rate
     * @return new DeclineParameters with updated initial rate
     */
    public DeclineParameters withInitialRate(double newInitialRate) {
      return new DeclineParameters(newInitialRate, declineRate, hyperbolicExponent, type, rateUnit,
          timeUnit);
    }

    @Override
    public String toString() {
      return String.format("DeclineParameters[type=%s, qi=%.2f %s, D=%.4f/%s, b=%.2f]", type,
          initialRate, rateUnit, declineRate, timeUnit, hyperbolicExponent);
    }
  }

  /**
   * Production forecast result at a single time point.
   *
   * <p>
   * Contains the production rate, cumulative production, and facility status at a specific point in
   * time during the forecast.
   */
  public static final class ProductionPoint implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double time;
    private final String timeUnit;
    private final double rate;
    private final double cumulativeProduction;
    private final String rateUnit;
    private final String bottleneckEquipment;
    private final double facilityUtilization;
    private final boolean isOnPlateau;
    private final boolean isAboveEconomicLimit;

    /**
     * Creates a production point.
     *
     * @param time time from start
     * @param timeUnit time unit
     * @param rate production rate at this time
     * @param cumulativeProduction total production up to this time
     * @param rateUnit rate unit
     * @param bottleneckEquipment name of limiting equipment (null if unconstrained)
     * @param facilityUtilization facility utilization fraction (0-1)
     * @param isOnPlateau true if still in plateau phase
     * @param isAboveEconomicLimit true if rate exceeds economic limit
     */
    public ProductionPoint(double time, String timeUnit, double rate, double cumulativeProduction,
        String rateUnit, String bottleneckEquipment, double facilityUtilization,
        boolean isOnPlateau, boolean isAboveEconomicLimit) {
      this.time = time;
      this.timeUnit = timeUnit;
      this.rate = rate;
      this.cumulativeProduction = cumulativeProduction;
      this.rateUnit = rateUnit;
      this.bottleneckEquipment = bottleneckEquipment;
      this.facilityUtilization = facilityUtilization;
      this.isOnPlateau = isOnPlateau;
      this.isAboveEconomicLimit = isAboveEconomicLimit;
    }

    /**
     * Gets the time from forecast start.
     *
     * @return time value
     */
    public double getTime() {
      return time;
    }

    /**
     * Gets the time unit.
     *
     * @return time unit string
     */
    public String getTimeUnit() {
      return timeUnit;
    }

    /**
     * Gets the production rate at this time.
     *
     * @return production rate
     */
    public double getRate() {
      return rate;
    }

    /**
     * Gets the cumulative production up to this time.
     *
     * @return cumulative production
     */
    public double getCumulativeProduction() {
      return cumulativeProduction;
    }

    /**
     * Gets the rate unit.
     *
     * @return rate unit string
     */
    public String getRateUnit() {
      return rateUnit;
    }

    /**
     * Gets the name of the bottleneck equipment.
     *
     * @return equipment name, or null if production is not facility-constrained
     */
    public String getBottleneckEquipment() {
      return bottleneckEquipment;
    }

    /**
     * Gets the facility utilization fraction.
     *
     * @return utilization (0-1), or 0 if no facility analysis
     */
    public double getFacilityUtilization() {
      return facilityUtilization;
    }

    /**
     * Checks if production is still in plateau phase.
     *
     * @return true if on plateau
     */
    public boolean isOnPlateau() {
      return isOnPlateau;
    }

    /**
     * Checks if rate is above economic limit.
     *
     * @return true if economically viable
     */
    public boolean isAboveEconomicLimit() {
      return isAboveEconomicLimit;
    }
  }

  /**
   * Complete production forecast with plateau and decline phases.
   *
   * <p>
   * Contains the full time series of production points along with summary statistics and the
   * parameters used to generate the forecast.
   */
  public static final class ProductionForecast implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<ProductionPoint> profile;
    private final double plateauRate;
    private final double actualPlateauRate;
    private final double plateauDuration;
    private final double actualPlateauDuration;
    private final double economicLimit;
    private final double totalRecovery;
    private final double economicLifeYears;
    private final DeclineParameters declineParams;

    /**
     * Creates a production forecast.
     *
     * @param profile list of production points
     * @param plateauRate requested plateau rate
     * @param actualPlateauRate achieved plateau rate (may be lower due to constraints)
     * @param plateauDuration requested plateau duration in years
     * @param actualPlateauDuration achieved plateau duration
     * @param economicLimit minimum economic rate
     * @param totalRecovery total cumulative production
     * @param economicLifeYears years until economic limit reached
     * @param declineParams decline parameters used
     */
    public ProductionForecast(List<ProductionPoint> profile, double plateauRate,
        double actualPlateauRate, double plateauDuration, double actualPlateauDuration,
        double economicLimit, double totalRecovery, double economicLifeYears,
        DeclineParameters declineParams) {
      this.profile = new ArrayList<>(profile);
      this.plateauRate = plateauRate;
      this.actualPlateauRate = actualPlateauRate;
      this.plateauDuration = plateauDuration;
      this.actualPlateauDuration = actualPlateauDuration;
      this.economicLimit = economicLimit;
      this.totalRecovery = totalRecovery;
      this.economicLifeYears = economicLifeYears;
      this.declineParams = declineParams;
    }

    /**
     * Gets the production profile as an unmodifiable list.
     *
     * @return list of production points
     */
    public List<ProductionPoint> getProfile() {
      return Collections.unmodifiableList(profile);
    }

    /**
     * Gets the requested plateau rate.
     *
     * @return plateau rate
     */
    public double getPlateauRate() {
      return plateauRate;
    }

    /**
     * Gets the actual achieved plateau rate.
     *
     * @return actual plateau rate (may be lower than requested if constrained)
     */
    public double getActualPlateauRate() {
      return actualPlateauRate;
    }

    /**
     * Gets the requested plateau duration in years.
     *
     * @return plateau duration
     */
    public double getPlateauDuration() {
      return plateauDuration;
    }

    /**
     * Gets the actual plateau duration.
     *
     * @return actual plateau duration (may be shorter if reservoir can't sustain rate)
     */
    public double getActualPlateauDuration() {
      return actualPlateauDuration;
    }

    /**
     * Gets the economic limit rate.
     *
     * @return economic limit
     */
    public double getEconomicLimit() {
      return economicLimit;
    }

    /**
     * Gets the total cumulative recovery.
     *
     * @return total recovery over forecast period
     */
    public double getTotalRecovery() {
      return totalRecovery;
    }

    /**
     * Gets the economic life in years.
     *
     * @return years until production falls below economic limit
     */
    public double getEconomicLifeYears() {
      return economicLifeYears;
    }

    /**
     * Gets the decline parameters used for the forecast.
     *
     * @return decline parameters
     */
    public DeclineParameters getDeclineParams() {
      return declineParams;
    }

    /**
     * Generates a Markdown table representation of the forecast.
     *
     * @return Markdown formatted table
     */
    public String toMarkdownTable() {
      StringBuilder sb = new StringBuilder();
      sb.append("## Production Forecast\n\n");
      sb.append(String.format("- **Plateau Rate**: %.2f %s (actual: %.2f)\n", plateauRate,
          declineParams.getRateUnit(), actualPlateauRate));
      sb.append(String.format("- **Plateau Duration**: %.1f years (actual: %.1f)\n",
          plateauDuration, actualPlateauDuration));
      sb.append(String.format("- **Economic Limit**: %.2f %s\n", economicLimit,
          declineParams.getRateUnit()));
      sb.append(String.format("- **Total Recovery**: %.2f\n", totalRecovery));
      sb.append(String.format("- **Economic Life**: %.1f years\n\n", economicLifeYears));

      sb.append("| Time (years) | Rate | Cumulative | Phase | Bottleneck | Utilization |\n");
      sb.append("|---|---|---|---|---|---|\n");

      for (ProductionPoint point : profile) {
        String phase = point.isOnPlateau() ? "Plateau" : "Decline";
        if (!point.isAboveEconomicLimit()) {
          phase = "Below Limit";
        }
        sb.append(String.format("| %.2f | %.2f | %.2f | %s | %s | %.1f%% |\n", point.getTime(),
            point.getRate(), point.getCumulativeProduction(), phase,
            point.getBottleneckEquipment() != null ? point.getBottleneckEquipment() : "-",
            point.getFacilityUtilization() * 100));
      }
      return sb.toString();
    }

    /**
     * Exports the forecast to CSV format.
     *
     * @return CSV string with header row
     */
    public String toCSV() {
      StringBuilder sb = new StringBuilder();
      sb.append("Time,Rate,Cumulative,Phase,Bottleneck,Utilization\n");
      for (ProductionPoint point : profile) {
        String phase = point.isOnPlateau() ? "Plateau" : "Decline";
        if (!point.isAboveEconomicLimit()) {
          phase = "BelowLimit";
        }
        sb.append(String.format("%.4f,%.4f,%.4f,%s,%s,%.4f\n", point.getTime(), point.getRate(),
            point.getCumulativeProduction(), phase,
            point.getBottleneckEquipment() != null ? point.getBottleneckEquipment() : "",
            point.getFacilityUtilization()));
      }
      return sb.toString();
    }
  }

  /**
   * Creates a ProductionProfile without facility constraints.
   *
   * <p>
   * Use this constructor for simple decline curve calculations that don't need to consider facility
   * bottlenecks.
   */
  public ProductionProfile() {
    this.facility = null;
    this.optimizer = null;
  }

  /**
   * Creates a ProductionProfile with facility constraint analysis.
   *
   * <p>
   * When a ProcessSystem is provided, the forecast will use {@link ProductionOptimizer} to
   * determine the maximum sustainable rate considering all equipment capacities.
   *
   * @param facility process system representing the surface facility
   */
  public ProductionProfile(ProcessSystem facility) {
    this.facility = facility;
    this.optimizer = new ProductionOptimizer();
  }

  /**
   * Generates a production forecast with plateau and decline phases.
   *
   * <p>
   * The forecast proceeds as follows:
   * <ol>
   * <li>During plateau phase, production is maintained at the minimum of:
   * <ul>
   * <li>Requested plateau rate</li>
   * <li>Maximum facility rate (if facility is provided)</li>
   * <li>Reservoir deliverability from decline curve</li>
   * </ul>
   * </li>
   * <li>Decline phase begins when reservoir deliverability falls below plateau</li>
   * <li>Forecast continues until economic limit or end of horizon</li>
   * </ol>
   *
   * @param feedStream stream to adjust for facility analysis (can be null if no facility)
   * @param decline decline curve parameters
   * @param plateauRate desired plateau production rate
   * @param plateauDurationYears maximum duration of plateau phase
   * @param economicLimit minimum economic production rate
   * @param forecastYears total forecast horizon in years
   * @param timeStepDays time step for forecast points in days
   * @return complete production forecast
   */
  public ProductionForecast forecast(StreamInterface feedStream, DeclineParameters decline,
      double plateauRate, double plateauDurationYears, double economicLimit, double forecastYears,
      double timeStepDays) {
    Objects.requireNonNull(decline, "Decline parameters are required");

    List<ProductionPoint> points = new ArrayList<>();
    double timeStepYears = timeStepDays / DAYS_PER_YEAR;
    double cumulativeProduction = 0.0;
    double actualPlateauRate = plateauRate;
    double actualPlateauDuration = 0.0;
    double economicLifeYears = forecastYears;
    boolean onPlateau = true;
    boolean foundEconomicLimit = false;

    // Determine facility-constrained maximum rate
    double facilityMaxRate = Double.MAX_VALUE;
    if (facility != null && feedStream != null && optimizer != null) {
      try {
        OptimizationConfig config = new OptimizationConfig(economicLimit, decline.getInitialRate())
            .rateUnit(decline.getRateUnit()).tolerance(economicLimit * 0.01);
        OptimizationResult result = optimizer.optimize(facility, feedStream, config,
            Collections.emptyList(), Collections.emptyList());
        if (result.isFeasible()) {
          facilityMaxRate = result.getOptimalRate();
        }
      } catch (Exception e) {
        // If optimization fails, assume no facility constraint
        facilityMaxRate = Double.MAX_VALUE;
      }
    }

    // Constrain plateau to facility max
    actualPlateauRate = Math.min(plateauRate, facilityMaxRate);

    for (double t = 0; t <= forecastYears; t += timeStepYears) {
      // Calculate reservoir deliverability from decline curve
      double reservoirRate = calculateRate(decline, t);

      // Determine actual rate considering constraints
      double rate;
      String bottleneck = null;
      double utilization = 0.0;

      if (onPlateau && t <= plateauDurationYears && reservoirRate >= actualPlateauRate) {
        // Still on plateau
        rate = actualPlateauRate;
        actualPlateauDuration = t;

        if (actualPlateauRate < plateauRate && actualPlateauRate < facilityMaxRate) {
          // Limited by facility
          bottleneck = getFacilityBottleneckName();
          utilization = rate / facilityMaxRate;
        }
      } else {
        // In decline phase
        onPlateau = false;
        rate = Math.min(reservoirRate, facilityMaxRate);

        if (rate >= facilityMaxRate * 0.99) {
          bottleneck = getFacilityBottleneckName();
          utilization = 1.0;
        } else {
          utilization = facilityMaxRate > 0 ? rate / facilityMaxRate : 0.0;
        }
      }

      boolean aboveLimit = rate >= economicLimit;
      if (!aboveLimit && !foundEconomicLimit) {
        economicLifeYears = t;
        foundEconomicLimit = true;
      }

      // Calculate cumulative production (trapezoidal integration)
      if (!points.isEmpty()) {
        ProductionPoint lastPoint = points.get(points.size() - 1);
        double avgRate = (lastPoint.getRate() + rate) / 2.0;
        cumulativeProduction += avgRate * timeStepDays; // rate is per day
      }

      points.add(new ProductionPoint(t, "years", rate, cumulativeProduction, decline.getRateUnit(),
          bottleneck, utilization, onPlateau && t <= plateauDurationYears, aboveLimit));

      // Stop if below economic limit
      if (!aboveLimit) {
        break;
      }
    }

    return new ProductionForecast(points, plateauRate, actualPlateauRate, plateauDurationYears,
        actualPlateauDuration, economicLimit, cumulativeProduction, economicLifeYears, decline);
  }

  /**
   * Calculates production rate at a given time using decline curve equations.
   *
   * <p>
   * This is a static utility method that can be used without instantiating the class. Time should
   * be in the same units as the decline rate.
   *
   * @param params decline curve parameters
   * @param time time from start of decline
   * @return production rate at specified time
   */
  public static double calculateRate(DeclineParameters params, double time) {
    Objects.requireNonNull(params, "Decline parameters are required");

    if (time < 0) {
      throw new IllegalArgumentException("Time cannot be negative: " + time);
    }
    if (time == 0) {
      return params.getInitialRate();
    }

    double qi = params.getInitialRate();
    double d = params.getDeclineRate();
    double b = params.getHyperbolicExponent();

    switch (params.getType()) {
      case EXPONENTIAL:
        return qi * Math.exp(-d * time);

      case HYPERBOLIC:
        double denom = 1.0 + b * d * time;
        if (denom <= 0) {
          return 0.0; // Prevent negative rates
        }
        return qi / Math.pow(denom, 1.0 / b);

      case HARMONIC:
        return qi / (1.0 + d * time);

      default:
        return qi;
    }
  }

  /**
   * Calculates cumulative production over a time period using analytical integration.
   *
   * <p>
   * Uses closed-form solutions for each decline type:
   * <ul>
   * <li>Exponential: {@code Np = (qi/D) * (1 - exp(-D*t))}</li>
   * <li>Hyperbolic: {@code Np = (qi/(D*(1-b))) * (1 - (1+b*D*t)^(1-1/b))}</li>
   * <li>Harmonic: {@code Np = (qi/D) * ln(1 + D*t)}</li>
   * </ul>
   *
   * @param params decline curve parameters
   * @param time time from start of decline
   * @return cumulative production up to specified time
   */
  public static double calculateCumulativeProduction(DeclineParameters params, double time) {
    Objects.requireNonNull(params, "Decline parameters are required");

    if (time <= 0) {
      return 0.0;
    }

    double qi = params.getInitialRate();
    double d = params.getDeclineRate();
    double b = params.getHyperbolicExponent();

    if (d < 1e-12) {
      // No decline - constant rate
      return qi * time;
    }

    switch (params.getType()) {
      case EXPONENTIAL:
        return (qi / d) * (1.0 - Math.exp(-d * time));

      case HYPERBOLIC:
        if (Math.abs(b - 1.0) < 1e-12) {
          // b ≈ 1: harmonic case
          return (qi / d) * Math.log(1.0 + d * time);
        }
        double exponent = 1.0 - 1.0 / b;
        return (qi / (d * (1.0 - b))) * (1.0 - Math.pow(1.0 + b * d * time, exponent));

      case HARMONIC:
        return (qi / d) * Math.log(1.0 + d * time);

      default:
        return qi * time;
    }
  }

  /**
   * Fits decline parameters from historical production data.
   *
   * <p>
   * Uses least-squares regression to determine the best-fit decline parameters for the specified
   * decline type. For exponential decline, this uses linear regression on ln(q) vs t. For
   * hyperbolic, it uses iterative optimization.
   *
   * @param times list of time points
   * @param rates list of corresponding production rates
   * @param type desired decline curve type
   * @param rateUnit rate unit string
   * @return fitted decline parameters
   * @throws IllegalArgumentException if data is insufficient or invalid
   */
  public DeclineParameters fitDecline(List<Double> times, List<Double> rates, DeclineType type,
      String rateUnit) {
    Objects.requireNonNull(times, "Times list is required");
    Objects.requireNonNull(rates, "Rates list is required");
    Objects.requireNonNull(type, "Decline type is required");

    if (times.size() != rates.size()) {
      throw new IllegalArgumentException("Times and rates lists must have the same size");
    }
    if (times.size() < 2) {
      throw new IllegalArgumentException("At least 2 data points are required for fitting");
    }

    // Filter out zero or negative rates
    List<Double> validTimes = new ArrayList<>();
    List<Double> validRates = new ArrayList<>();
    for (int i = 0; i < times.size(); i++) {
      if (rates.get(i) > 0) {
        validTimes.add(times.get(i));
        validRates.add(rates.get(i));
      }
    }

    if (validTimes.size() < 2) {
      throw new IllegalArgumentException("At least 2 positive rate values are required");
    }

    double qi = validRates.get(0); // Use first rate as initial
    double d;

    switch (type) {
      case EXPONENTIAL:
        // Linear regression on ln(q) = ln(qi) - D*t
        double[] expFit = fitExponentialDecline(validTimes, validRates);
        qi = expFit[0];
        d = expFit[1];
        return new DeclineParameters(qi, d, type, rateUnit);

      case HYPERBOLIC:
        // Iterative fitting for hyperbolic
        double[] hypFit = fitHyperbolicDecline(validTimes, validRates);
        qi = hypFit[0];
        d = hypFit[1];
        double b = hypFit[2];
        return new DeclineParameters(qi, d, b, type, rateUnit);

      case HARMONIC:
        // Regression on 1/q = 1/qi + (D/qi)*t
        double[] harmFit = fitHarmonicDecline(validTimes, validRates);
        qi = harmFit[0];
        d = harmFit[1];
        return new DeclineParameters(qi, d, 1.0, type, rateUnit);

      default:
        return new DeclineParameters(qi, 0.0, type, rateUnit);
    }
  }

  /**
   * Fits exponential decline using linear regression on ln(q) vs t.
   */
  private double[] fitExponentialDecline(List<Double> times, List<Double> rates) {
    int n = times.size();
    double sumT = 0, sumLnQ = 0, sumT2 = 0, sumTLnQ = 0;

    for (int i = 0; i < n; i++) {
      double t = times.get(i);
      double lnQ = Math.log(rates.get(i));
      sumT += t;
      sumLnQ += lnQ;
      sumT2 += t * t;
      sumTLnQ += t * lnQ;
    }

    // Linear regression: ln(q) = a - D*t
    double slope = (n * sumTLnQ - sumT * sumLnQ) / (n * sumT2 - sumT * sumT);
    double intercept = (sumLnQ - slope * sumT) / n;

    double qi = Math.exp(intercept);
    double d = -slope;

    return new double[] {qi, Math.max(0, d)};
  }

  /**
   * Fits hyperbolic decline using grid search over b values.
   */
  private double[] fitHyperbolicDecline(List<Double> times, List<Double> rates) {
    double bestError = Double.MAX_VALUE;
    double bestQi = rates.get(0);
    double bestD = 0.1;
    double bestB = 0.5;

    // Grid search over b values
    for (double b = 0.1; b <= 0.9; b += 0.1) {
      // For each b, fit qi and D using linearized form
      double[] fit = fitHyperbolicForB(times, rates, b);
      double qi = fit[0];
      double d = fit[1];

      // Calculate sum of squared errors
      double error = 0;
      for (int i = 0; i < times.size(); i++) {
        double predicted = qi / Math.pow(1.0 + b * d * times.get(i), 1.0 / b);
        double diff = predicted - rates.get(i);
        error += diff * diff;
      }

      if (error < bestError) {
        bestError = error;
        bestQi = qi;
        bestD = d;
        bestB = b;
      }
    }

    return new double[] {bestQi, bestD, bestB};
  }

  /**
   * Fits hyperbolic decline for a fixed b value.
   */
  private double[] fitHyperbolicForB(List<Double> times, List<Double> rates, double b) {
    // Linearize: q^(-b) = qi^(-b) + (b*D/qi^b) * t
    int n = times.size();
    double sumT = 0, sumQnb = 0, sumT2 = 0, sumTQnb = 0;

    for (int i = 0; i < n; i++) {
      double t = times.get(i);
      double qnb = Math.pow(rates.get(i), -b);
      sumT += t;
      sumQnb += qnb;
      sumT2 += t * t;
      sumTQnb += t * qnb;
    }

    double slope = (n * sumTQnb - sumT * sumQnb) / (n * sumT2 - sumT * sumT);
    double intercept = (sumQnb - slope * sumT) / n;

    double qi = Math.pow(intercept, -1.0 / b);
    double d = slope * qi / (b * Math.pow(qi, -b));

    return new double[] {Math.max(qi, 1), Math.max(0, d)};
  }

  /**
   * Fits harmonic decline using linear regression on 1/q vs t.
   */
  private double[] fitHarmonicDecline(List<Double> times, List<Double> rates) {
    int n = times.size();
    double sumT = 0, sumInvQ = 0, sumT2 = 0, sumTInvQ = 0;

    for (int i = 0; i < n; i++) {
      double t = times.get(i);
      double invQ = 1.0 / rates.get(i);
      sumT += t;
      sumInvQ += invQ;
      sumT2 += t * t;
      sumTInvQ += t * invQ;
    }

    // Linear regression: 1/q = 1/qi + (D/qi)*t
    double slope = (n * sumTInvQ - sumT * sumInvQ) / (n * sumT2 - sumT * sumT);
    double intercept = (sumInvQ - slope * sumT) / n;

    double qi = 1.0 / intercept;
    double d = slope * qi;

    return new double[] {Math.max(qi, 1), Math.max(0, d)};
  }

  /**
   * Gets the name of the current facility bottleneck.
   */
  private String getFacilityBottleneckName() {
    if (facility == null) {
      return null;
    }
    ProcessEquipmentInterface bottleneck = facility.getBottleneck();
    return bottleneck != null ? bottleneck.getName() : null;
  }

  /**
   * Gets the facility process system.
   *
   * @return facility process system, or null if not configured
   */
  public ProcessSystem getFacility() {
    return facility;
  }
}
