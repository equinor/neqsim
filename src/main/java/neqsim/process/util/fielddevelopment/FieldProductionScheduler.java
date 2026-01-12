package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.reservoir.Well;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimization.ProductionOptimizer;

/**
 * Orchestrates field-level production scheduling and forecasting.
 *
 * <p>
 * The {@code FieldProductionScheduler} integrates multiple reservoirs, wells, and surface
 * facilities to create production schedules that respect:
 * </p>
 * <ul>
 * <li>Reservoir deliverability (pressure depletion, GOR evolution)</li>
 * <li>Well capacity (IPR, VLP constraints)</li>
 * <li>Facility bottlenecks (separation, compression, export)</li>
 * <li>Contractual obligations (plateau targets, minimum delivery)</li>
 * <li>Intervention schedules (workovers, shutdowns)</li>
 * </ul>
 *
 * <h2>Architecture Overview</h2>
 * 
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                      FieldProductionScheduler                                │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │                                                                              │
 * │   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐                   │
 * │   │ Reservoir 1  │   │ Reservoir 2  │   │ Reservoir N  │  (SimpleReservoir) │
 * │   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘                   │
 * │          │                  │                  │                            │
 * │          ▼                  ▼                  ▼                            │
 * │   ┌──────────────────────────────────────────────────────┐                 │
 * │   │                   Well Manager                        │                 │
 * │   │   ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐        │  (WellScheduler)  │
 * │   │   │ W1 │ │ W2 │ │ W3 │ │ W4 │ │ I1 │ │ I2 │        │                   │
 * │   │   └────┘ └────┘ └────┘ └────┘ └────┘ └────┘        │                   │
 * │   └──────────────────────────┬───────────────────────────┘                 │
 * │                              │                                              │
 * │                              ▼                                              │
 * │   ┌──────────────────────────────────────────────────────┐                 │
 * │   │              Surface Facility (ProcessSystem)         │                 │
 * │   │   Separator → Compressor → Dehydration → Export      │  (FacilityCapacity)
 * │   └──────────────────────────┬───────────────────────────┘                 │
 * │                              │                                              │
 * │                              ▼                                              │
 * │   ┌──────────────────────────────────────────────────────┐                 │
 * │   │                 Production Schedule                   │                 │
 * │   │   Time → Rates → Volumes → Pressures → Economics     │                 │
 * │   └──────────────────────────────────────────────────────┘                 │
 * │                                                                              │
 * └─────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Example Usage - Basic Field Scheduling</h2>
 * 
 * <pre>{@code
 * // Create reservoirs and facility
 * SimpleReservoir gasField = new SimpleReservoir("Gas Field");
 * gasField.setReservoirFluid(gasFluid, 5.0e9, 1.0, 1.0e7);
 * gasField.addGasProducer("GP-1");
 * gasField.addGasProducer("GP-2");
 * 
 * ProcessSystem facility = createFacilityModel();
 * 
 * // Create scheduler
 * FieldProductionScheduler scheduler = new FieldProductionScheduler("Offshore Field");
 * scheduler.addReservoir(gasField);
 * scheduler.setFacility(facility);
 * 
 * // Set production targets
 * scheduler.setPlateauRate(10.0, "MSm3/day");
 * scheduler.setPlateauDuration(5.0, "years");
 * scheduler.setMinimumRate(1.0, "MSm3/day");
 * 
 * // Run forecast
 * ProductionSchedule schedule = scheduler.generateSchedule(LocalDate.of(2025, 1, 1), 20.0, // years
 *     30.0 // days per step
 * );
 * 
 * // Review results
 * System.out.println(schedule.toMarkdownTable());
 * System.out.println("Cumulative gas: " + schedule.getCumulativeGas("GSm3") + " GSm3");
 * System.out.println("Field life: " + schedule.getFieldLife("years") + " years");
 * }</pre>
 *
 * <h2>Example Usage - With Economics</h2>
 * 
 * <pre>{@code
 * // Add economic parameters
 * scheduler.setGasPrice(8.0, "USD/MMBtu");
 * scheduler.setOilPrice(70.0, "USD/bbl");
 * scheduler.setDiscountRate(0.10);  // 10% per year
 * scheduler.setOperatingCost(5.0e6, "USD/year");
 * 
 * ProductionSchedule schedule = scheduler.generateSchedule(...);
 * 
 * System.out.println("Gross revenue: $" + schedule.getGrossRevenue() / 1e9 + "B");
 * System.out.println("NPV: $" + schedule.getNPV() / 1e6 + "M");
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SimpleReservoir
 * @see WellScheduler
 * @see ProductionProfile
 * @see FacilityCapacity
 * @see ProductionOptimizer
 */
public class FieldProductionScheduler implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Days per year for calculations. */
  private static final double DAYS_PER_YEAR = 365.25;

  /** Scheduler name. */
  private final String name;

  /** List of reservoirs in the field. */
  private final List<ReservoirRecord> reservoirs;

  /** Surface facility process system. */
  private ProcessSystem facility;

  /** Well scheduler for intervention tracking. */
  private WellScheduler wellScheduler;

  /** Facility capacity analyzer. */
  private transient FacilityCapacity facilityCapacity;

  // Production targets
  private double plateauRate = Double.NaN;
  private String plateauRateUnit = "MSm3/day";
  private double plateauDuration = 0.0;
  private double minimumRate = 0.0;
  private String minimumRateUnit = "MSm3/day";

  // Economic parameters
  private double gasPrice = 0.0;
  private String gasPriceUnit = "USD/MMBtu";
  private double oilPrice = 0.0;
  private String oilPriceUnit = "USD/bbl";
  private double discountRate = 0.10;
  private double operatingCostPerYear = 0.0;

  // Scheduling parameters
  private boolean respectFacilityConstraints = true;
  private boolean trackReservoirDepletion = true;
  private double lowPressureLimit = 50.0; // bara

  /**
   * Reservoir record containing reservoir reference and metadata.
   */
  public static final class ReservoirRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final SimpleReservoir reservoir;
    private final String fluidType;
    private double initialPressure;
    private double currentPressure;

    /**
     * Creates a reservoir record.
     *
     * @param reservoir the SimpleReservoir instance
     * @param fluidType "gas", "oil", or "condensate"
     */
    public ReservoirRecord(SimpleReservoir reservoir, String fluidType) {
      this.reservoir = Objects.requireNonNull(reservoir, "Reservoir is required");
      this.fluidType = Objects.requireNonNull(fluidType, "Fluid type is required");
      if (reservoir.getReservoirFluid() != null) {
        this.initialPressure = reservoir.getReservoirFluid().getPressure("bara");
        this.currentPressure = this.initialPressure;
      }
    }

    public SimpleReservoir getReservoir() {
      return reservoir;
    }

    public String getFluidType() {
      return fluidType;
    }

    public double getInitialPressure() {
      return initialPressure;
    }

    public double getCurrentPressure() {
      return currentPressure;
    }

    void updateCurrentPressure() {
      if (reservoir.getReservoirFluid() != null) {
        this.currentPressure = reservoir.getReservoirFluid().getPressure("bara");
      }
    }
  }

  /**
   * Single time step in a production schedule.
   */
  public static final class ScheduleStep implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final LocalDate date;
    private final double timeYears;
    private final double gasRate;
    private final double oilRate;
    private final double waterRate;
    private final double cumulativeGas;
    private final double cumulativeOil;
    private final double cumulativeWater;
    private final double reservoirPressure;
    private final double facilityUtilization;
    private final String limitingFactor;
    private final double revenue;
    private final double discountedRevenue;
    private final Map<String, Double> wellRates;

    /**
     * Creates a schedule step.
     *
     * @param date step date
     * @param timeYears time from start in years
     * @param gasRate gas production rate (Sm3/day)
     * @param oilRate oil production rate (Sm3/day)
     * @param waterRate water production rate (Sm3/day)
     * @param cumulativeGas cumulative gas produced (Sm3)
     * @param cumulativeOil cumulative oil produced (Sm3)
     * @param cumulativeWater cumulative water produced (Sm3)
     * @param reservoirPressure average reservoir pressure (bara)
     * @param facilityUtilization facility utilization fraction
     * @param limitingFactor what limits production
     * @param revenue period revenue (currency)
     * @param discountedRevenue discounted revenue (currency)
     * @param wellRates map of well name to rate
     */
    public ScheduleStep(LocalDate date, double timeYears, double gasRate, double oilRate,
        double waterRate, double cumulativeGas, double cumulativeOil, double cumulativeWater,
        double reservoirPressure, double facilityUtilization, String limitingFactor, double revenue,
        double discountedRevenue, Map<String, Double> wellRates) {
      this.date = date;
      this.timeYears = timeYears;
      this.gasRate = gasRate;
      this.oilRate = oilRate;
      this.waterRate = waterRate;
      this.cumulativeGas = cumulativeGas;
      this.cumulativeOil = cumulativeOil;
      this.cumulativeWater = cumulativeWater;
      this.reservoirPressure = reservoirPressure;
      this.facilityUtilization = facilityUtilization;
      this.limitingFactor = limitingFactor;
      this.revenue = revenue;
      this.discountedRevenue = discountedRevenue;
      this.wellRates = wellRates != null ? new LinkedHashMap<>(wellRates) : new LinkedHashMap<>();
    }

    public LocalDate getDate() {
      return date;
    }

    public double getTimeYears() {
      return timeYears;
    }

    public double getGasRate() {
      return gasRate;
    }

    public double getOilRate() {
      return oilRate;
    }

    public double getWaterRate() {
      return waterRate;
    }

    public double getCumulativeGas() {
      return cumulativeGas;
    }

    public double getCumulativeOil() {
      return cumulativeOil;
    }

    public double getCumulativeWater() {
      return cumulativeWater;
    }

    public double getReservoirPressure() {
      return reservoirPressure;
    }

    public double getFacilityUtilization() {
      return facilityUtilization;
    }

    public String getLimitingFactor() {
      return limitingFactor;
    }

    public double getRevenue() {
      return revenue;
    }

    public double getDiscountedRevenue() {
      return discountedRevenue;
    }

    public Map<String, Double> getWellRates() {
      return Collections.unmodifiableMap(wellRates);
    }
  }

  /**
   * Complete production schedule result.
   */
  public static final class ProductionSchedule implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String fieldName;
    private final LocalDate startDate;
    private final List<ScheduleStep> steps;
    private final String rateUnit;
    private double totalGrossRevenue;
    private double totalNPV;
    private double fieldLifeYears;

    /**
     * Creates a production schedule.
     *
     * @param fieldName field name
     * @param startDate schedule start date
     * @param rateUnit rate unit for display
     */
    public ProductionSchedule(String fieldName, LocalDate startDate, String rateUnit) {
      this.fieldName = fieldName;
      this.startDate = startDate;
      this.steps = new ArrayList<>();
      this.rateUnit = rateUnit;
    }

    void addStep(ScheduleStep step) {
      steps.add(step);
    }

    void setTotalGrossRevenue(double value) {
      this.totalGrossRevenue = value;
    }

    void setTotalNPV(double value) {
      this.totalNPV = value;
    }

    void setFieldLifeYears(double value) {
      this.fieldLifeYears = value;
    }

    public String getFieldName() {
      return fieldName;
    }

    public LocalDate getStartDate() {
      return startDate;
    }

    public List<ScheduleStep> getSteps() {
      return Collections.unmodifiableList(steps);
    }

    public String getRateUnit() {
      return rateUnit;
    }

    /**
     * Gets cumulative gas production.
     *
     * @param unit "Sm3", "MSm3", "GSm3", or "Bcf"
     * @return cumulative gas in requested unit
     */
    public double getCumulativeGas(String unit) {
      if (steps.isEmpty()) {
        return 0.0;
      }
      double sm3 = steps.get(steps.size() - 1).getCumulativeGas();
      switch (unit) {
        case "MSm3":
          return sm3 / 1.0e6;
        case "GSm3":
          return sm3 / 1.0e9;
        case "Bcf":
          return sm3 / 1.0e9 * 35.3147; // 1 GSm3 ≈ 35.3 Bcf
        default:
          return sm3;
      }
    }

    /**
     * Gets cumulative oil production.
     *
     * @param unit "Sm3", "MSm3", "Mbbl", or "MMbbl"
     * @return cumulative oil in requested unit
     */
    public double getCumulativeOil(String unit) {
      if (steps.isEmpty()) {
        return 0.0;
      }
      double sm3 = steps.get(steps.size() - 1).getCumulativeOil();
      switch (unit) {
        case "MSm3":
          return sm3 / 1.0e6;
        case "Mbbl":
          return sm3 * 6.2898 / 1000.0; // 1 m3 ≈ 6.29 bbl
        case "MMbbl":
          return sm3 * 6.2898 / 1.0e6;
        default:
          return sm3;
      }
    }

    /**
     * Gets field life (time to economic limit).
     *
     * @param unit "years", "months", or "days"
     * @return field life in requested unit
     */
    public double getFieldLife(String unit) {
      switch (unit) {
        case "months":
          return fieldLifeYears * 12.0;
        case "days":
          return fieldLifeYears * DAYS_PER_YEAR;
        default:
          return fieldLifeYears;
      }
    }

    public double getGrossRevenue() {
      return totalGrossRevenue;
    }

    public double getNPV() {
      return totalNPV;
    }

    /**
     * Generates a markdown table of the schedule.
     *
     * @return markdown formatted table
     */
    public String toMarkdownTable() {
      StringBuilder sb = new StringBuilder();
      sb.append("# Production Schedule: ").append(fieldName).append("\n\n");
      sb.append("| Date | Years | Gas Rate | Oil Rate | Cum Gas | Cum Oil | Pressure | Limit |\n");
      sb.append("|------|-------|----------|----------|---------|---------|----------|-------|\n");

      for (ScheduleStep step : steps) {
        sb.append(
            String.format("| %s | %.1f | %.2f | %.2f | %.2f | %.2f | %.1f | %s |\n", step.getDate(),
                step.getTimeYears(), step.getGasRate() / 1.0e6, step.getOilRate() / 1000.0,
                step.getCumulativeGas() / 1.0e9, step.getCumulativeOil() / 1.0e6,
                step.getReservoirPressure(), step.getLimitingFactor()));
      }

      sb.append("\n**Summary:**\n");
      sb.append(String.format("- Cumulative Gas: %.2f GSm3\n", getCumulativeGas("GSm3")));
      sb.append(String.format("- Cumulative Oil: %.2f MSm3\n", getCumulativeOil("MSm3")));
      sb.append(String.format("- Field Life: %.1f years\n", fieldLifeYears));
      if (totalGrossRevenue > 0) {
        sb.append(String.format("- Gross Revenue: $%.1f B\n", totalGrossRevenue / 1.0e9));
        sb.append(String.format("- NPV (10%%): $%.1f M\n", totalNPV / 1.0e6));
      }

      return sb.toString();
    }

    /**
     * Exports schedule to CSV format.
     *
     * @return CSV formatted string
     */
    public String toCsv() {
      StringBuilder sb = new StringBuilder();
      sb.append("Date,Years,GasRate_MSm3d,OilRate_Sm3d,WaterRate_Sm3d,");
      sb.append("CumGas_GSm3,CumOil_MSm3,Pressure_bara,Utilization,LimitingFactor,Revenue\n");

      for (ScheduleStep step : steps) {
        sb.append(String.format("%s,%.3f,%.4f,%.2f,%.2f,%.6f,%.6f,%.2f,%.3f,%s,%.0f\n",
            step.getDate(), step.getTimeYears(), step.getGasRate() / 1.0e6, step.getOilRate(),
            step.getWaterRate(), step.getCumulativeGas() / 1.0e9, step.getCumulativeOil() / 1.0e6,
            step.getReservoirPressure(), step.getFacilityUtilization(), step.getLimitingFactor(),
            step.getRevenue()));
      }

      return sb.toString();
    }
  }

  /**
   * Creates a field production scheduler.
   *
   * @param name field name
   */
  public FieldProductionScheduler(String name) {
    this.name = Objects.requireNonNull(name, "Field name is required");
    this.reservoirs = new ArrayList<>();
  }

  /**
   * Adds a reservoir to the field.
   *
   * @param reservoir SimpleReservoir instance
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler addReservoir(SimpleReservoir reservoir) {
    return addReservoir(reservoir, "gas");
  }

  /**
   * Adds a reservoir to the field with specified fluid type.
   *
   * @param reservoir SimpleReservoir instance
   * @param fluidType "gas", "oil", or "condensate"
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler addReservoir(SimpleReservoir reservoir, String fluidType) {
    reservoirs.add(new ReservoirRecord(reservoir, fluidType));
    return this;
  }

  /**
   * Sets the surface facility process system.
   *
   * @param facility ProcessSystem representing the facility
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setFacility(ProcessSystem facility) {
    this.facility = facility;
    if (facility != null) {
      this.facilityCapacity = new FacilityCapacity(facility);
    }
    return this;
  }

  /**
   * Sets the well scheduler for intervention tracking.
   *
   * @param scheduler WellScheduler instance
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setWellScheduler(WellScheduler scheduler) {
    this.wellScheduler = scheduler;
    return this;
  }

  /**
   * Sets the plateau production rate target.
   *
   * @param rate plateau rate
   * @param unit rate unit (e.g., "MSm3/day", "kg/hr")
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setPlateauRate(double rate, String unit) {
    this.plateauRate = rate;
    this.plateauRateUnit = unit;
    return this;
  }

  /**
   * Sets the plateau duration.
   *
   * @param duration duration value
   * @param unit "years", "months", or "days"
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setPlateauDuration(double duration, String unit) {
    switch (unit.toLowerCase()) {
      case "months":
        this.plateauDuration = duration / 12.0;
        break;
      case "days":
        this.plateauDuration = duration / DAYS_PER_YEAR;
        break;
      default:
        this.plateauDuration = duration;
    }
    return this;
  }

  /**
   * Sets the minimum economic rate (production stops below this).
   *
   * @param rate minimum rate
   * @param unit rate unit
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setMinimumRate(double rate, String unit) {
    this.minimumRate = rate;
    this.minimumRateUnit = unit;
    return this;
  }

  /**
   * Sets the gas price for economic calculations.
   *
   * @param price gas price
   * @param unit price unit (e.g., "USD/MMBtu", "USD/Sm3")
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setGasPrice(double price, String unit) {
    this.gasPrice = price;
    this.gasPriceUnit = unit;
    return this;
  }

  /**
   * Sets the oil price for economic calculations.
   *
   * @param price oil price
   * @param unit price unit (e.g., "USD/bbl", "USD/Sm3")
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setOilPrice(double price, String unit) {
    this.oilPrice = price;
    this.oilPriceUnit = unit;
    return this;
  }

  /**
   * Sets the discount rate for NPV calculations.
   *
   * @param rate annual discount rate (e.g., 0.10 for 10%)
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setDiscountRate(double rate) {
    this.discountRate = rate;
    return this;
  }

  /**
   * Sets the annual operating cost.
   *
   * @param cost annual operating cost
   * @param unit currency unit (for documentation)
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setOperatingCost(double cost, String unit) {
    this.operatingCostPerYear = cost;
    return this;
  }

  /**
   * Sets whether to respect facility constraints during scheduling.
   *
   * @param respect true to respect constraints
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setRespectFacilityConstraints(boolean respect) {
    this.respectFacilityConstraints = respect;
    return this;
  }

  /**
   * Sets whether to track reservoir depletion dynamically.
   *
   * @param track true to run reservoir transient simulation
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setTrackReservoirDepletion(boolean track) {
    this.trackReservoirDepletion = track;
    return this;
  }

  /**
   * Sets the low pressure limit for reservoir abandonment.
   *
   * @param pressure minimum reservoir pressure (bara)
   * @return this scheduler for chaining
   */
  public FieldProductionScheduler setLowPressureLimit(double pressure) {
    this.lowPressureLimit = pressure;
    return this;
  }

  /**
   * Generates a production schedule for the field.
   *
   * @param startDate schedule start date
   * @param durationYears forecast duration in years
   * @param timeStepDays time step in days
   * @return production schedule
   */
  public ProductionSchedule generateSchedule(LocalDate startDate, double durationYears,
      double timeStepDays) {
    ProductionSchedule schedule = new ProductionSchedule(name, startDate, plateauRateUnit);

    double cumulativeGas = 0.0;
    double cumulativeOil = 0.0;
    double cumulativeWater = 0.0;
    double totalRevenue = 0.0;
    double totalDiscountedRevenue = 0.0;

    int totalSteps = (int) Math.ceil(durationYears * DAYS_PER_YEAR / timeStepDays);
    double timeStepSeconds = timeStepDays * 24.0 * 60.0 * 60.0;
    UUID calcId = UUID.randomUUID();

    // Initialize reservoirs
    for (ReservoirRecord record : reservoirs) {
      record.getReservoir().setLowPressureLimit(lowPressureLimit, "bara");
      record.getReservoir().run(calcId);
    }

    for (int step = 0; step < totalSteps; step++) {
      double timeYears = step * timeStepDays / DAYS_PER_YEAR;
      LocalDate stepDate = startDate.plusDays((long) (step * timeStepDays));

      // Determine if any reservoir has reached low pressure limit
      boolean allDepleted = true;
      double avgPressure = 0.0;
      for (ReservoirRecord record : reservoirs) {
        record.updateCurrentPressure();
        avgPressure += record.getCurrentPressure();
        if (record.getCurrentPressure() > lowPressureLimit) {
          allDepleted = false;
        }
      }
      avgPressure /= reservoirs.size();

      if (allDepleted) {
        break; // End schedule if all reservoirs depleted
      }

      // Calculate production rates
      double gasRate = 0.0;
      double oilRate = 0.0;
      double waterRate = 0.0;
      String limitingFactor = "Reservoir";
      double facilityUtilization = 0.0;
      Map<String, Double> wellRates = new LinkedHashMap<>();

      // Aggregate from all reservoirs
      for (ReservoirRecord record : reservoirs) {
        SimpleReservoir res = record.getReservoir();
        gasRate += res.getGasProdution("Sm3/day");
        oilRate += res.getOilProdution("Sm3/day");
        waterRate += res.getWaterProdution("Sm3/day");
      }

      // Check plateau constraint
      if (!Double.isNaN(plateauRate) && timeYears < plateauDuration) {
        double targetRate = convertRate(plateauRate, plateauRateUnit, "Sm3/day");
        if (gasRate > targetRate) {
          // Reduce to plateau rate
          double factor = targetRate / gasRate;
          gasRate = targetRate;
          oilRate *= factor;
          limitingFactor = "Plateau";
        }
      }

      // Check minimum rate
      double minRateSm3 = convertRate(minimumRate, minimumRateUnit, "Sm3/day");
      if (gasRate < minRateSm3 && gasRate > 0) {
        break; // End schedule when below economic limit
      }

      // Update cumulative production
      cumulativeGas += gasRate * timeStepDays;
      cumulativeOil += oilRate * timeStepDays;
      cumulativeWater += waterRate * timeStepDays;

      // Calculate revenue
      double periodRevenue = calculateRevenue(gasRate, oilRate, timeStepDays);
      double discountFactor = Math.pow(1.0 + discountRate, -timeYears);
      double discountedRevenue = periodRevenue * discountFactor;
      totalRevenue += periodRevenue;
      totalDiscountedRevenue += discountedRevenue;

      // Create schedule step
      ScheduleStep scheduleStep = new ScheduleStep(stepDate, timeYears, gasRate, oilRate, waterRate,
          cumulativeGas, cumulativeOil, cumulativeWater, avgPressure, facilityUtilization,
          limitingFactor, periodRevenue, discountedRevenue, wellRates);
      schedule.addStep(scheduleStep);

      // Run transient for next step
      if (trackReservoirDepletion) {
        for (ReservoirRecord record : reservoirs) {
          record.getReservoir().runTransient(timeStepSeconds, calcId);
        }
      }
    }

    // Set summary values
    schedule.setTotalGrossRevenue(totalRevenue);
    schedule.setTotalNPV(totalDiscountedRevenue);
    if (!schedule.getSteps().isEmpty()) {
      schedule.setFieldLifeYears(
          schedule.getSteps().get(schedule.getSteps().size() - 1).getTimeYears());
    }

    return schedule;
  }

  /**
   * Converts rate between units.
   *
   * @param value rate value
   * @param fromUnit source unit
   * @param toUnit target unit
   * @return converted rate
   */
  private double convertRate(double value, String fromUnit, String toUnit) {
    // Convert to Sm3/day as base unit
    double sm3PerDay = value;

    if (fromUnit.equalsIgnoreCase("MSm3/day")) {
      sm3PerDay = value * 1.0e6;
    } else if (fromUnit.equalsIgnoreCase("kg/hr")) {
      // Approximate conversion for gas (depends on density)
      sm3PerDay = value * 24.0 / 0.8; // Approximate
    }

    // Convert from Sm3/day to target
    if (toUnit.equalsIgnoreCase("MSm3/day")) {
      return sm3PerDay / 1.0e6;
    } else if (toUnit.equalsIgnoreCase("kg/hr")) {
      return sm3PerDay * 0.8 / 24.0;
    }

    return sm3PerDay;
  }

  /**
   * Calculates revenue for a period.
   *
   * @param gasRate gas rate (Sm3/day)
   * @param oilRate oil rate (Sm3/day)
   * @param days period duration in days
   * @return revenue in currency units
   */
  private double calculateRevenue(double gasRate, double oilRate, double days) {
    double revenue = 0.0;

    // Gas revenue
    if (gasPrice > 0 && gasRate > 0) {
      double gasVolume = gasRate * days;
      double mmBtu = gasVolume * 0.0353147; // Sm3 to MMBtu (approximate)
      if (gasPriceUnit.contains("MMBtu")) {
        revenue += gasVolume * gasPrice * 0.0353147;
      } else {
        revenue += gasVolume * gasPrice;
      }
    }

    // Oil revenue
    if (oilPrice > 0 && oilRate > 0) {
      double oilVolume = oilRate * days;
      double bbl = oilVolume * 6.2898; // m3 to bbl
      if (oilPriceUnit.contains("bbl")) {
        revenue += bbl * oilPrice;
      } else {
        revenue += oilVolume * oilPrice;
      }
    }

    // Subtract operating costs
    revenue -= operatingCostPerYear * days / DAYS_PER_YEAR;

    return revenue;
  }

  /**
   * Gets the field name.
   *
   * @return field name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the list of reservoirs.
   *
   * @return unmodifiable list of reservoir records
   */
  public List<ReservoirRecord> getReservoirs() {
    return Collections.unmodifiableList(reservoirs);
  }

  /**
   * Gets the facility process system.
   *
   * @return facility or null if not set
   */
  public ProcessSystem getFacility() {
    return facility;
  }

  /**
   * Gets the well scheduler.
   *
   * @return well scheduler or null if not set
   */
  public WellScheduler getWellScheduler() {
    return wellScheduler;
  }
}
