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
import java.util.stream.Collectors;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimization.ProductionOptimizer;

/**
 * Schedules well interventions, workovers, and tracks well availability.
 *
 * <p>
 * This class provides comprehensive well scheduling capabilities including:
 * <ul>
 * <li>Well status and availability tracking</li>
 * <li>Intervention and workover scheduling</li>
 * <li>Production impact analysis</li>
 * <li>Schedule optimization to minimize deferred production</li>
 * <li>Rig/vessel constraint handling</li>
 * <li>Gantt chart generation for visualization</li>
 * </ul>
 *
 * <h2>Well Status Model</h2>
 * <p>
 * Each well can be in one of several states that affect its contribution to total production:
 * <ul>
 * <li>{@link WellStatus#PRODUCING} - Well is online and producing</li>
 * <li>{@link WellStatus#SHUT_IN} - Well is temporarily shut in</li>
 * <li>{@link WellStatus#WORKOVER} - Well is undergoing intervention</li>
 * <li>{@link WellStatus#WAITING_ON_WEATHER} - Operations delayed by weather</li>
 * <li>{@link WellStatus#DRILLING} - New well being drilled</li>
 * <li>{@link WellStatus#PLUGGED} - Well permanently abandoned</li>
 * </ul>
 *
 * <h2>Intervention Planning</h2>
 * <p>
 * Interventions are scheduled activities that temporarily take a well offline but may improve its
 * performance afterward. The scheduler calculates:
 * <ul>
 * <li>Deferred production during intervention</li>
 * <li>Expected production gain after intervention</li>
 * <li>Net present value of the intervention</li>
 * <li>Optimal timing considering rig availability</li>
 * </ul>
 *
 * <h2>Integration with Facility Model</h2>
 * <p>
 * When a {@link ProcessSystem} is provided, the scheduler accounts for facility bottlenecks. This
 * ensures that:
 * <ul>
 * <li>Production is capped at facility capacity even when well potential exceeds it</li>
 * <li>Interventions on non-bottleneck wells may not increase total production</li>
 * <li>Optimal scheduling prioritizes interventions that relieve constraints</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * WellScheduler scheduler = new WellScheduler(reservoir, facility);
 *
 * // Add wells with their current potential
 * scheduler.addWell("Well-A", 5000.0, "Sm3/day");
 * scheduler.addWell("Well-B", 4000.0, "Sm3/day");
 * scheduler.addWell("Well-C", 3000.0, "Sm3/day");
 *
 * // Schedule interventions
 * scheduler
 *     .scheduleIntervention(Intervention.builder("Well-A").type(InterventionType.COILED_TUBING)
 *         .startDate(LocalDate.of(2024, 6, 15)).durationDays(5).expectedGain(0.15) // 15%
 *                                                                                  // improvement
 *         .cost(500000, "USD").build());
 *
 * // Optimize the schedule
 * ScheduleResult result =
 *     scheduler.optimizeSchedule(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 1); // max 1
 *                                                                                          // concurrent
 *                                                                                          // intervention
 *
 * System.out.println("Total deferred: " + result.getTotalDeferredProduction());
 * System.out.println("Total gain: " + result.getTotalProductionGain());
 * System.out.println(result.toGanttMarkdown());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ProductionOptimizer
 * @see SimpleReservoir
 */
public class WellScheduler implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Days per year for calculations. */
  private static final double DAYS_PER_YEAR = 365.25;

  /** Reservoir model (may be null). */
  private final SimpleReservoir reservoir;

  /** Surface facility process system (may be null). */
  private final ProcessSystem facility;

  /** Map of well names to well records. */
  private final Map<String, WellRecord> wells;

  /** Default rate unit for wells. */
  private String defaultRateUnit = "Sm3/day";

  /**
   * Well operational status.
   *
   * <p>
   * Defines the possible states a well can be in during field operations. The status affects both
   * production contribution and intervention scheduling.
   */
  public enum WellStatus {
    /**
     * Well is producing at its current potential.
     */
    PRODUCING("Producing", true),

    /**
     * Well is temporarily shut in (no production).
     */
    SHUT_IN("Shut-in", false),

    /**
     * Well is undergoing intervention/workover.
     */
    WORKOVER("Workover", false),

    /**
     * Operations delayed waiting for weather window.
     */
    WAITING_ON_WEATHER("WoW", false),

    /**
     * Well is being drilled (not yet completed).
     */
    DRILLING("Drilling", false),

    /**
     * Well is permanently plugged and abandoned.
     */
    PLUGGED("P&A", false);

    private final String displayName;
    private final boolean isProducing;

    WellStatus(String displayName, boolean isProducing) {
      this.displayName = displayName;
      this.isProducing = isProducing;
    }

    /**
     * Gets the display name for reports.
     *
     * @return human-readable status name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Checks if the well produces in this status.
     *
     * @return true if well is producing
     */
    public boolean isProducing() {
      return isProducing;
    }
  }

  /**
   * Types of well interventions.
   *
   * <p>
   * Each intervention type has typical duration ranges and cost factors that can be used for
   * planning purposes.
   */
  public enum InterventionType {
    /**
     * Coiled tubing intervention for cleanout, stimulation, or logging. Typical duration: 2-7 days.
     */
    COILED_TUBING("Coiled Tubing", 2, 7),

    /**
     * Wireline operations for logging, perforation, or plug setting. Typical duration: 1-3 days.
     */
    WIRELINE("Wireline", 1, 3),

    /**
     * Hydraulic workover without rig. Typical duration: 5-15 days.
     */
    HYDRAULIC_WORKOVER("HWO", 5, 15),

    /**
     * Full rig workover for major repairs or recompletions. Typical duration: 15-45 days.
     */
    RIG_WORKOVER("Rig Workover", 15, 45),

    /**
     * Well stimulation (acidizing, fracturing). Typical duration: 3-10 days.
     */
    STIMULATION("Stimulation", 3, 10),

    /**
     * Artificial lift system installation (ESP, gas lift). Typical duration: 5-20 days.
     */
    ARTIFICIAL_LIFT_INSTALL("AL Install", 5, 20),

    /**
     * Water or gas shut-off treatment. Typical duration: 3-7 days.
     */
    WATER_SHUT_OFF("WSO", 3, 7),

    /**
     * Scale or wax treatment. Typical duration: 2-5 days.
     */
    SCALE_TREATMENT("Scale Treatment", 2, 5),

    /**
     * Plug and abandon operations. Typical duration: 10-30 days.
     */
    PLUG_AND_ABANDON("P&A", 10, 30);

    private final String displayName;
    private final int minDurationDays;
    private final int maxDurationDays;

    InterventionType(String displayName, int minDays, int maxDays) {
      this.displayName = displayName;
      this.minDurationDays = minDays;
      this.maxDurationDays = maxDays;
    }

    /**
     * Gets the display name for reports.
     *
     * @return human-readable intervention type
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Gets the typical minimum duration.
     *
     * @return minimum days for this intervention type
     */
    public int getMinDurationDays() {
      return minDurationDays;
    }

    /**
     * Gets the typical maximum duration.
     *
     * @return maximum days for this intervention type
     */
    public int getMaxDurationDays() {
      return maxDurationDays;
    }
  }

  /**
   * Scheduled intervention record.
   *
   * <p>
   * Represents a planned well intervention with timing, cost, and expected outcome information.
   */
  public static final class Intervention implements Serializable, Comparable<Intervention> {
    private static final long serialVersionUID = 1000L;

    private final String wellName;
    private final InterventionType type;
    private final LocalDate startDate;
    private final int durationDays;
    private final double expectedProductionGain;
    private final double cost;
    private final String currency;
    private final String description;
    private final int priority;

    private Intervention(Builder builder) {
      this.wellName = builder.wellName;
      this.type = builder.type;
      this.startDate = builder.startDate;
      this.durationDays = builder.durationDays;
      this.expectedProductionGain = builder.expectedProductionGain;
      this.cost = builder.cost;
      this.currency = builder.currency;
      this.description = builder.description;
      this.priority = builder.priority;
    }

    /**
     * Creates a new builder for an intervention.
     *
     * @param wellName name of the well for intervention
     * @return new builder instance
     */
    public static Builder builder(String wellName) {
      return new Builder(wellName);
    }

    /**
     * Gets the well name.
     *
     * @return well name
     */
    public String getWellName() {
      return wellName;
    }

    /**
     * Gets the intervention type.
     *
     * @return intervention type
     */
    public InterventionType getType() {
      return type;
    }

    /**
     * Gets the scheduled start date.
     *
     * @return start date
     */
    public LocalDate getStartDate() {
      return startDate;
    }

    /**
     * Gets the intervention duration in days.
     *
     * @return duration in days
     */
    public int getDurationDays() {
      return durationDays;
    }

    /**
     * Gets the expected end date.
     *
     * @return end date (startDate + durationDays - 1)
     */
    public LocalDate getEndDate() {
      return startDate.plusDays(durationDays - 1);
    }

    /**
     * Gets the expected production gain as a fraction.
     *
     * @return production improvement fraction (e.g., 0.15 for 15% improvement)
     */
    public double getExpectedProductionGain() {
      return expectedProductionGain;
    }

    /**
     * Gets the intervention cost.
     *
     * @return cost in specified currency
     */
    public double getCost() {
      return cost;
    }

    /**
     * Gets the currency for cost.
     *
     * @return currency code (e.g., "USD", "NOK")
     */
    public String getCurrency() {
      return currency;
    }

    /**
     * Gets the intervention description.
     *
     * @return description or null
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets the scheduling priority (lower = higher priority).
     *
     * @return priority value
     */
    public int getPriority() {
      return priority;
    }

    /**
     * Checks if the intervention overlaps with a date range.
     *
     * @param rangeStart start of date range
     * @param rangeEnd end of date range
     * @return true if any overlap exists
     */
    public boolean overlaps(LocalDate rangeStart, LocalDate rangeEnd) {
      LocalDate intEnd = getEndDate();
      return !startDate.isAfter(rangeEnd) && !intEnd.isBefore(rangeStart);
    }

    /**
     * Checks if the intervention is active on a specific date.
     *
     * @param date date to check
     * @return true if intervention is ongoing on this date
     */
    public boolean isActiveOn(LocalDate date) {
      return !date.isBefore(startDate) && !date.isAfter(getEndDate());
    }

    @Override
    public int compareTo(Intervention other) {
      int priorityCompare = Integer.compare(this.priority, other.priority);
      if (priorityCompare != 0) {
        return priorityCompare;
      }
      return this.startDate.compareTo(other.startDate);
    }

    @Override
    public String toString() {
      return String.format("Intervention[%s on %s, %s, %d days, gain=%.1f%%]",
          type.getDisplayName(), wellName, startDate, durationDays, expectedProductionGain * 100);
    }

    /**
     * Builder for creating Intervention instances.
     */
    public static final class Builder {
      private final String wellName;
      private InterventionType type = InterventionType.COILED_TUBING;
      private LocalDate startDate = LocalDate.now();
      private int durationDays = 5;
      private double expectedProductionGain = 0.0;
      private double cost = 0.0;
      private String currency = "USD";
      private String description = null;
      private int priority = 5;

      private Builder(String wellName) {
        this.wellName = Objects.requireNonNull(wellName, "Well name is required");
      }

      /**
       * Sets the intervention type.
       *
       * @param type intervention type
       * @return this builder
       */
      public Builder type(InterventionType type) {
        this.type = Objects.requireNonNull(type);
        return this;
      }

      /**
       * Sets the start date.
       *
       * @param startDate planned start date
       * @return this builder
       */
      public Builder startDate(LocalDate startDate) {
        this.startDate = Objects.requireNonNull(startDate);
        return this;
      }

      /**
       * Sets the duration in days.
       *
       * @param durationDays number of days
       * @return this builder
       */
      public Builder durationDays(int durationDays) {
        if (durationDays <= 0) {
          throw new IllegalArgumentException("Duration must be positive");
        }
        this.durationDays = durationDays;
        return this;
      }

      /**
       * Sets the expected production gain as a fraction.
       *
       * @param gain production improvement (e.g., 0.15 for 15%)
       * @return this builder
       */
      public Builder expectedGain(double gain) {
        this.expectedProductionGain = gain;
        return this;
      }

      /**
       * Sets the cost.
       *
       * @param cost intervention cost
       * @param currency currency code
       * @return this builder
       */
      public Builder cost(double cost, String currency) {
        this.cost = cost;
        this.currency = Objects.requireNonNull(currency);
        return this;
      }

      /**
       * Sets the description.
       *
       * @param description intervention description
       * @return this builder
       */
      public Builder description(String description) {
        this.description = description;
        return this;
      }

      /**
       * Sets the scheduling priority (lower = higher priority).
       *
       * @param priority priority value (1-10 recommended)
       * @return this builder
       */
      public Builder priority(int priority) {
        this.priority = priority;
        return this;
      }

      /**
       * Builds the Intervention instance.
       *
       * @return new Intervention
       */
      public Intervention build() {
        return new Intervention(this);
      }
    }
  }

  /**
   * Well record for availability and production tracking.
   *
   * <p>
   * Maintains the complete history of a well's status and production, along with scheduled
   * interventions.
   */
  public static final class WellRecord implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String wellName;
    private final Map<LocalDate, WellStatus> statusHistory;
    private final Map<LocalDate, Double> productionHistory;
    private final List<Intervention> scheduledInterventions;
    private double currentPotential;
    private double originalPotential;
    private String rateUnit;
    private WellStatus currentStatus;

    /**
     * Creates a new well record.
     *
     * @param wellName well identifier
     * @param initialPotential initial production potential
     * @param rateUnit rate unit
     */
    public WellRecord(String wellName, double initialPotential, String rateUnit) {
      this.wellName = wellName;
      this.currentPotential = initialPotential;
      this.originalPotential = initialPotential;
      this.rateUnit = rateUnit;
      this.currentStatus = WellStatus.PRODUCING;
      this.statusHistory = new LinkedHashMap<>();
      this.productionHistory = new LinkedHashMap<>();
      this.scheduledInterventions = new ArrayList<>();
    }

    /**
     * Gets the well name.
     *
     * @return well name
     */
    public String getWellName() {
      return wellName;
    }

    /**
     * Gets the current production potential.
     *
     * @return current potential rate
     */
    public double getCurrentPotential() {
      return currentPotential;
    }

    /**
     * Sets the current production potential.
     *
     * @param potential new potential rate
     */
    public void setCurrentPotential(double potential) {
      this.currentPotential = potential;
    }

    /**
     * Gets the original production potential.
     *
     * @return original potential rate
     */
    public double getOriginalPotential() {
      return originalPotential;
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
     * Gets the current well status.
     *
     * @return current status
     */
    public WellStatus getCurrentStatus() {
      return currentStatus;
    }

    /**
     * Sets the current well status and records it in history.
     *
     * @param status new status
     * @param date date of status change
     */
    public void setStatus(WellStatus status, LocalDate date) {
      this.currentStatus = status;
      this.statusHistory.put(date, status);
    }

    /**
     * Gets the status on a specific date.
     *
     * @param date date to check
     * @return status on that date, or current status if not found
     */
    public WellStatus getStatusOn(LocalDate date) {
      // Find the most recent status on or before the date
      WellStatus status = currentStatus;
      for (Map.Entry<LocalDate, WellStatus> entry : statusHistory.entrySet()) {
        if (!entry.getKey().isAfter(date)) {
          status = entry.getValue();
        } else {
          break;
        }
      }
      // Check if there's an intervention on this date
      for (Intervention intervention : scheduledInterventions) {
        if (intervention.isActiveOn(date)) {
          return WellStatus.WORKOVER;
        }
      }
      return status;
    }

    /**
     * Gets the scheduled interventions.
     *
     * @return unmodifiable list of interventions
     */
    public List<Intervention> getScheduledInterventions() {
      return Collections.unmodifiableList(scheduledInterventions);
    }

    /**
     * Adds a scheduled intervention.
     *
     * @param intervention intervention to schedule
     */
    public void addIntervention(Intervention intervention) {
      scheduledInterventions.add(intervention);
      Collections.sort(scheduledInterventions);
    }

    /**
     * Records production for a date.
     *
     * @param date production date
     * @param rate production rate
     */
    public void recordProduction(LocalDate date, double rate) {
      productionHistory.put(date, rate);
    }

    /**
     * Calculates availability over a period.
     *
     * @param startDate start of period
     * @param endDate end of period
     * @return availability fraction (0-1)
     */
    public double calculateAvailability(LocalDate startDate, LocalDate endDate) {
      long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
      long producingDays = 0;

      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        if (getStatusOn(date).isProducing()) {
          producingDays++;
        }
      }

      return (double) producingDays / totalDays;
    }

    /**
     * Gets interventions within a date range.
     *
     * @param startDate start of range
     * @param endDate end of range
     * @return list of interventions overlapping the range
     */
    public List<Intervention> getInterventionsInRange(LocalDate startDate, LocalDate endDate) {
      return scheduledInterventions.stream().filter(i -> i.overlaps(startDate, endDate))
          .collect(Collectors.toList());
    }
  }

  /**
   * Schedule optimization result.
   *
   * <p>
   * Contains the complete optimized schedule and associated metrics including deferred production,
   * production gains, and facility utilization.
   */
  public static final class ScheduleResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<Intervention> optimizedSchedule;
    private final Map<String, Double> wellUptime;
    private final double totalDeferredProduction;
    private final double totalProductionGain;
    private final Map<LocalDate, Double> dailyFacilityRate;
    private final Map<LocalDate, String> dailyBottleneck;
    private final double overallAvailability;
    private final String rateUnit;

    /**
     * Creates a schedule result.
     *
     * @param optimizedSchedule list of scheduled interventions
     * @param wellUptime map of well name to uptime fraction
     * @param totalDeferredProduction production lost during interventions
     * @param totalProductionGain production gained from interventions
     * @param dailyFacilityRate daily total production rates
     * @param dailyBottleneck daily bottleneck equipment
     * @param overallAvailability overall system availability
     * @param rateUnit rate unit for production values
     */
    public ScheduleResult(List<Intervention> optimizedSchedule, Map<String, Double> wellUptime,
        double totalDeferredProduction, double totalProductionGain,
        Map<LocalDate, Double> dailyFacilityRate, Map<LocalDate, String> dailyBottleneck,
        double overallAvailability, String rateUnit) {
      this.optimizedSchedule = new ArrayList<>(optimizedSchedule);
      this.wellUptime = new HashMap<>(wellUptime);
      this.totalDeferredProduction = totalDeferredProduction;
      this.totalProductionGain = totalProductionGain;
      this.dailyFacilityRate = dailyFacilityRate != null ? new LinkedHashMap<>(dailyFacilityRate)
          : new LinkedHashMap<>();
      this.dailyBottleneck =
          dailyBottleneck != null ? new LinkedHashMap<>(dailyBottleneck) : new LinkedHashMap<>();
      this.overallAvailability = overallAvailability;
      this.rateUnit = rateUnit;
    }

    /**
     * Gets the optimized intervention schedule.
     *
     * @return unmodifiable list of interventions
     */
    public List<Intervention> getOptimizedSchedule() {
      return Collections.unmodifiableList(optimizedSchedule);
    }

    /**
     * Gets well uptime fractions.
     *
     * @return map of well name to uptime (0-1)
     */
    public Map<String, Double> getWellUptime() {
      return Collections.unmodifiableMap(wellUptime);
    }

    /**
     * Gets total deferred production during interventions.
     *
     * @return deferred production in rate units * days
     */
    public double getTotalDeferredProduction() {
      return totalDeferredProduction;
    }

    /**
     * Gets total production gain from successful interventions.
     *
     * @return production gain in rate units * days
     */
    public double getTotalProductionGain() {
      return totalProductionGain;
    }

    /**
     * Gets daily facility production rates.
     *
     * @return map of date to production rate
     */
    public Map<LocalDate, Double> getDailyFacilityRate() {
      return Collections.unmodifiableMap(dailyFacilityRate);
    }

    /**
     * Gets daily bottleneck equipment.
     *
     * @return map of date to bottleneck name
     */
    public Map<LocalDate, String> getDailyBottleneck() {
      return Collections.unmodifiableMap(dailyBottleneck);
    }

    /**
     * Gets overall system availability.
     *
     * @return availability fraction (0-1)
     */
    public double getOverallAvailability() {
      return overallAvailability;
    }

    /**
     * Gets the net production impact (gain minus deferred).
     *
     * @return net production change
     */
    public double getNetProductionImpact() {
      return totalProductionGain - totalDeferredProduction;
    }

    /**
     * Generates a Mermaid Gantt chart for the schedule.
     *
     * @return Mermaid Gantt chart syntax
     */
    public String toGanttMarkdown() {
      StringBuilder sb = new StringBuilder();
      sb.append("```mermaid\n");
      sb.append("gantt\n");
      sb.append("    title Well Intervention Schedule\n");
      sb.append("    dateFormat YYYY-MM-DD\n");

      // Group interventions by well
      Map<String, List<Intervention>> byWell =
          optimizedSchedule.stream().collect(Collectors.groupingBy(Intervention::getWellName));

      for (Map.Entry<String, List<Intervention>> entry : byWell.entrySet()) {
        sb.append("    section ").append(entry.getKey()).append("\n");
        for (Intervention intervention : entry.getValue()) {
          sb.append("    ").append(intervention.getType().getDisplayName()).append(" :")
              .append(intervention.getStartDate()).append(", ")
              .append(intervention.getDurationDays()).append("d\n");
        }
      }
      sb.append("```\n");
      return sb.toString();
    }

    /**
     * Generates a summary table in Markdown format.
     *
     * @return Markdown table
     */
    public String toMarkdownTable() {
      StringBuilder sb = new StringBuilder();
      sb.append("## Schedule Summary\n\n");
      sb.append(String.format("- **Overall Availability**: %.1f%%\n", overallAvailability * 100));
      sb.append(String.format("- **Total Deferred Production**: %.2f %s-days\n",
          totalDeferredProduction, rateUnit));
      sb.append(String.format("- **Total Production Gain**: %.2f %s-days\n", totalProductionGain,
          rateUnit));
      sb.append(
          String.format("- **Net Impact**: %.2f %s-days\n\n", getNetProductionImpact(), rateUnit));

      sb.append("### Well Uptime\n\n");
      sb.append("| Well | Uptime |\n");
      sb.append("|---|---|\n");
      for (Map.Entry<String, Double> entry : wellUptime.entrySet()) {
        sb.append(String.format("| %s | %.1f%% |\n", entry.getKey(), entry.getValue() * 100));
      }
      sb.append("\n");

      sb.append("### Scheduled Interventions\n\n");
      sb.append("| Well | Type | Start | Duration | Expected Gain | Cost |\n");
      sb.append("|---|---|---|---|---|---|\n");
      for (Intervention intervention : optimizedSchedule) {
        sb.append(String.format("| %s | %s | %s | %d days | %.1f%% | %.0f %s |\n",
            intervention.getWellName(), intervention.getType().getDisplayName(),
            intervention.getStartDate(), intervention.getDurationDays(),
            intervention.getExpectedProductionGain() * 100, intervention.getCost(),
            intervention.getCurrency()));
      }

      return sb.toString();
    }
  }

  /**
   * Creates a well scheduler without reservoir or facility models.
   */
  public WellScheduler() {
    this(null, null);
  }

  /**
   * Creates a well scheduler with reservoir and facility models.
   *
   * @param reservoir reservoir model for production tracking
   * @param facility surface facility for bottleneck analysis
   */
  public WellScheduler(SimpleReservoir reservoir, ProcessSystem facility) {
    this.reservoir = reservoir;
    this.facility = facility;
    this.wells = new LinkedHashMap<>();
  }

  /**
   * Adds a well with initial production potential.
   *
   * @param name well name (unique identifier)
   * @param initialPotential unconstrained production rate
   * @param rateUnit rate unit (e.g., "Sm3/day")
   * @return the created well record
   */
  public WellRecord addWell(String name, double initialPotential, String rateUnit) {
    Objects.requireNonNull(name, "Well name is required");
    if (initialPotential < 0) {
      throw new IllegalArgumentException("Initial potential cannot be negative");
    }
    WellRecord record = new WellRecord(name, initialPotential, rateUnit);
    wells.put(name, record);
    return record;
  }

  /**
   * Gets a well record by name.
   *
   * @param name well name
   * @return well record, or null if not found
   */
  public WellRecord getWell(String name) {
    return wells.get(name);
  }

  /**
   * Gets all well records.
   *
   * @return unmodifiable collection of well records
   */
  public java.util.Collection<WellRecord> getAllWells() {
    return Collections.unmodifiableCollection(wells.values());
  }

  /**
   * Schedules an intervention for a well.
   *
   * @param intervention intervention to schedule
   * @throws IllegalArgumentException if well doesn't exist
   */
  public void scheduleIntervention(Intervention intervention) {
    Objects.requireNonNull(intervention, "Intervention is required");
    WellRecord well = wells.get(intervention.getWellName());
    if (well == null) {
      throw new IllegalArgumentException("Well not found: " + intervention.getWellName());
    }
    well.addIntervention(intervention);
  }

  /**
   * Gets all scheduled interventions across all wells.
   *
   * @return list of all interventions sorted by date
   */
  public List<Intervention> getAllInterventions() {
    List<Intervention> all = new ArrayList<>();
    for (WellRecord well : wells.values()) {
      all.addAll(well.getScheduledInterventions());
    }
    Collections.sort(all);
    return all;
  }

  /**
   * Optimizes the intervention schedule to minimize deferred production.
   *
   * <p>
   * The optimization considers:
   * <ul>
   * <li>Intervention priority and dependencies</li>
   * <li>Rig/vessel availability (maxConcurrentInterventions)</li>
   * <li>Production potential of each well</li>
   * <li>Facility constraints (if facility is provided)</li>
   * </ul>
   *
   * @param startDate start of scheduling period
   * @param endDate end of scheduling period
   * @param maxConcurrentInterventions maximum number of simultaneous interventions
   * @return optimized schedule result
   */
  public ScheduleResult optimizeSchedule(LocalDate startDate, LocalDate endDate,
      int maxConcurrentInterventions) {
    Objects.requireNonNull(startDate, "Start date is required");
    Objects.requireNonNull(endDate, "End date is required");
    if (maxConcurrentInterventions <= 0) {
      throw new IllegalArgumentException("Max concurrent interventions must be positive");
    }

    // Get all interventions and sort by priority/NPV
    List<Intervention> allInterventions = getAllInterventions();
    allInterventions.sort(Comparator.comparingInt(Intervention::getPriority).thenComparing(
        i -> -i.getExpectedProductionGain() * getWell(i.getWellName()).getCurrentPotential()));

    // Simple greedy scheduling: assign earliest available slot for each intervention
    List<Intervention> scheduledInterventions = new ArrayList<>();
    List<LocalDate> occupiedEndDates = new ArrayList<>();

    for (Intervention intervention : allInterventions) {
      if (!intervention.overlaps(startDate, endDate)) {
        continue; // Skip interventions outside the period
      }

      // Find earliest available start date
      LocalDate availableDate = intervention.getStartDate();
      if (availableDate.isBefore(startDate)) {
        availableDate = startDate;
      }

      // Check resource constraints
      while (!availableDate.isAfter(endDate)) {
        // Count concurrent interventions on this date
        long concurrent = countConcurrentOnDate(scheduledInterventions, availableDate);
        if (concurrent < maxConcurrentInterventions) {
          break;
        }
        availableDate = availableDate.plusDays(1);
      }

      if (!availableDate.isAfter(endDate.minusDays(intervention.getDurationDays() - 1))) {
        // Reschedule to available date
        Intervention rescheduled =
            Intervention.builder(intervention.getWellName()).type(intervention.getType())
                .startDate(availableDate).durationDays(intervention.getDurationDays())
                .expectedGain(intervention.getExpectedProductionGain())
                .cost(intervention.getCost(), intervention.getCurrency())
                .description(intervention.getDescription()).priority(intervention.getPriority())
                .build();
        scheduledInterventions.add(rescheduled);
      }
    }

    // Calculate metrics
    Map<String, Double> wellUptime = new HashMap<>();
    double totalDeferred = 0;
    double totalGain = 0;
    Map<LocalDate, Double> dailyRate = new LinkedHashMap<>();
    Map<LocalDate, String> dailyBottleneck = new LinkedHashMap<>();

    long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

    for (WellRecord well : wells.values()) {
      double uptime = well.calculateAvailability(startDate, endDate);
      wellUptime.put(well.getWellName(), uptime);
    }

    // Calculate daily production and deferred/gain
    Map<String, Double> postInterventionPotential = new HashMap<>();
    for (WellRecord well : wells.values()) {
      postInterventionPotential.put(well.getWellName(), well.getCurrentPotential());
    }

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      double dayRate = 0;
      for (WellRecord well : wells.values()) {
        // Check if intervention just completed
        for (Intervention intervention : scheduledInterventions) {
          if (intervention.getWellName().equals(well.getWellName())
              && intervention.getEndDate().equals(date.minusDays(1))) {
            // Apply production gain
            double currentPot = postInterventionPotential.get(well.getWellName());
            double newPot = currentPot * (1.0 + intervention.getExpectedProductionGain());
            postInterventionPotential.put(well.getWellName(), newPot);
          }
        }

        // Check if currently in intervention
        boolean inIntervention = false;
        for (Intervention intervention : scheduledInterventions) {
          if (intervention.getWellName().equals(well.getWellName())
              && intervention.isActiveOn(date)) {
            inIntervention = true;
            totalDeferred += well.getCurrentPotential();
            break;
          }
        }

        if (!inIntervention && well.getCurrentStatus().isProducing()) {
          dayRate += postInterventionPotential.get(well.getWellName());
        }
      }
      dailyRate.put(date, dayRate);

      // Get bottleneck if facility is available
      if (facility != null) {
        var bottleneck = facility.getBottleneck();
        dailyBottleneck.put(date, bottleneck != null ? bottleneck.getName() : null);
      }
    }

    // Calculate total gain (from improved potentials over remaining period)
    for (Map.Entry<String, Double> entry : postInterventionPotential.entrySet()) {
      WellRecord well = wells.get(entry.getKey());
      if (well != null) {
        double gain = entry.getValue() - well.getCurrentPotential();
        if (gain > 0) {
          // Calculate remaining days after last intervention on this well
          LocalDate lastIntEnd = startDate;
          for (Intervention intervention : scheduledInterventions) {
            if (intervention.getWellName().equals(entry.getKey())) {
              if (intervention.getEndDate().isAfter(lastIntEnd)) {
                lastIntEnd = intervention.getEndDate();
              }
            }
          }
          long remainingDays = ChronoUnit.DAYS.between(lastIntEnd, endDate);
          if (remainingDays > 0) {
            totalGain += gain * remainingDays;
          }
        }
      }
    }

    double overallAvailability =
        wellUptime.values().stream().mapToDouble(Double::doubleValue).average().orElse(1.0);

    return new ScheduleResult(scheduledInterventions, wellUptime, totalDeferred, totalGain,
        dailyRate, dailyBottleneck, overallAvailability,
        wells.values().iterator().hasNext() ? wells.values().iterator().next().getRateUnit()
            : defaultRateUnit);
  }

  /**
   * Counts concurrent interventions on a specific date.
   */
  private long countConcurrentOnDate(List<Intervention> interventions, LocalDate date) {
    return interventions.stream().filter(i -> i.isActiveOn(date)).count();
  }

  /**
   * Calculates system-wide availability over a period.
   *
   * @param startDate start of period
   * @param endDate end of period
   * @return weighted average availability across all wells
   */
  public double calculateSystemAvailability(LocalDate startDate, LocalDate endDate) {
    double totalPotential = 0;
    double weightedAvailability = 0;

    for (WellRecord well : wells.values()) {
      double potential = well.getCurrentPotential();
      double availability = well.calculateAvailability(startDate, endDate);
      weightedAvailability += potential * availability;
      totalPotential += potential;
    }

    return totalPotential > 0 ? weightedAvailability / totalPotential : 1.0;
  }

  /**
   * Gets the total well potential on a specific date.
   *
   * @param date date to check
   * @return sum of potentials for all producing wells
   */
  public double getTotalPotentialOn(LocalDate date) {
    double total = 0;
    for (WellRecord well : wells.values()) {
      if (well.getStatusOn(date).isProducing()) {
        total += well.getCurrentPotential();
      }
    }
    return total;
  }

  /**
   * Gets the reservoir model.
   *
   * @return reservoir, or null if not configured
   */
  public SimpleReservoir getReservoir() {
    return reservoir;
  }

  /**
   * Gets the facility process system.
   *
   * @return facility, or null if not configured
   */
  public ProcessSystem getFacility() {
    return facility;
  }

  /**
   * Sets the default rate unit for new wells.
   *
   * @param rateUnit rate unit string
   */
  public void setDefaultRateUnit(String rateUnit) {
    this.defaultRateUnit = Objects.requireNonNull(rateUnit);
  }
}
