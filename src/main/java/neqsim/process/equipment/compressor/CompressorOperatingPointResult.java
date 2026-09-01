package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Immutable, capacity-aware snapshot of one compressor operating point.
 *
 * <p>
 * The result combines thermodynamic performance, compressor-map position, anti-surge recycle screening, pressure-target
 * status, and the universal NeqSim capacity constraints. It is intended for production optimization, bottleneck
 * analysis, field-life studies, and exchange with external energy and emissions tools.
 * </p>
 *
 * <p>
 * All physical values use fixed units stated in the getter names and JavaDoc. Values that cannot be evaluated are
 * represented by {@link Double#NaN}. The result does not retain references to the compressor or its mutable
 * constraints.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CompressorOperatingPointResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Default relative tolerance for discharge-pressure target evaluation. */
  public static final double DEFAULT_PRESSURE_TOLERANCE_FRACTION = 0.02;

  /** Overall feasibility status for the operating point. */
  public enum OperatingStatus {
    /** All evaluated physical, map, capacity, and pressure-target checks pass. */
    VALID,
    /** Essential physical values are missing or non-finite. */
    INVALID,
    /** The operating point is on the unstable side of the surge line. */
    SURGE,
    /** The operating point is beyond the stonewall or choke boundary. */
    STONEWALL,
    /** At least one equipment capacity constraint is exceeded. */
    CAPACITY_LIMIT,
    /** The calculated discharge pressure is outside the configured target tolerance. */
    PRESSURE_TARGET_NOT_MET
  }

  /** Status of calculated discharge pressure relative to the compressor target. */
  public enum PressureTargetStatus {
    /** A target comparison could not be evaluated. */
    NOT_EVALUATED,
    /** Calculated pressure is within the configured relative tolerance. */
    ON_TARGET,
    /** Calculated pressure is below the target minus tolerance. */
    BELOW_TARGET,
    /** Calculated pressure is above the target plus tolerance. */
    ABOVE_TARGET
  }

  /** Immutable snapshot of one {@link CapacityConstraint}. */
  public static final class ConstraintSnapshot implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String name;
    private final String unit;
    private final CapacityConstraint.ConstraintType type;
    private final CapacityConstraint.ConstraintSeverity severity;
    private final double currentValue;
    private final double designValue;
    private final double minimumValue;
    private final double maximumValue;
    private final double utilization;
    private final double margin;
    private final boolean enabled;
    private final boolean violated;
    private final String dataSource;
    private final String description;

    /**
     * Creates a detached snapshot of a mutable capacity constraint.
     *
     * @param constraint capacity constraint to read
     */
    private ConstraintSnapshot(CapacityConstraint constraint) {
      name = constraint.getName();
      unit = constraint.getUnit();
      type = constraint.getType();
      severity = constraint.getSeverity();
      currentValue = safeDouble(constraint::getCurrentValue);
      designValue = constraint.getDisplayDesignValue();
      minimumValue = constraint.getMinValue();
      maximumValue = constraint.getMaxValue();
      utilization = safeDouble(constraint::getUtilization);
      margin = safeDouble(constraint::getMargin);
      enabled = constraint.isEnabled();
      violated = safeBoolean(constraint::isViolated);
      dataSource = constraint.getDataSource();
      description = constraint.getDescription();
    }

    /**
     * Gets the constraint name.
     *
     * @return constraint name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the engineering unit.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the capacity constraint type.
     *
     * @return constraint type
     */
    public CapacityConstraint.ConstraintType getType() {
      return type;
    }

    /**
     * Gets the optimization severity.
     *
     * @return constraint severity
     */
    public CapacityConstraint.ConstraintSeverity getSeverity() {
      return severity;
    }

    /**
     * Gets the evaluated current value.
     *
     * @return current value in {@link #getUnit()}
     */
    public double getCurrentValue() {
      return currentValue;
    }

    /**
     * Gets the design value used for reporting.
     *
     * @return design value in {@link #getUnit()}
     */
    public double getDesignValue() {
      return designValue;
    }

    /**
     * Gets the required minimum value.
     *
     * @return minimum value in {@link #getUnit()}
     */
    public double getMinimumValue() {
      return minimumValue;
    }

    /**
     * Gets the absolute maximum value.
     *
     * @return maximum value in {@link #getUnit()}
     */
    public double getMaximumValue() {
      return maximumValue;
    }

    /**
     * Gets capacity utilization.
     *
     * @return utilization fraction, where 1.0 is the limit
     */
    public double getUtilization() {
      return utilization;
    }

    /**
     * Gets remaining capacity margin.
     *
     * @return margin fraction, where positive is feasible
     */
    public double getMargin() {
      return margin;
    }

    /**
     * Checks whether the constraint participates in capacity analysis.
     *
     * @return true when enabled
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Checks whether the constraint is violated.
     *
     * @return true when utilization exceeds 1.0
     */
    public boolean isViolated() {
      return violated;
    }

    /**
     * Gets the provenance of the design value.
     *
     * @return data-source label
     */
    public String getDataSource() {
      return dataSource;
    }

    /**
     * Gets the engineering description.
     *
     * @return constraint description
     */
    public String getDescription() {
      return description;
    }
  }

  private final String schemaVersion = "1.0";
  private final String compressorName;
  private final double flowM3PerHour;
  private final double polytropicHeadKJPerKg;
  private final double speedRpm;
  private final double polytropicEfficiency;
  private final double powerKW;
  private final double inletPressureBara;
  private final double requestedDischargePressureBara;
  private final double actualDischargePressureBara;
  private final double dischargePressureErrorFraction;
  private final double dischargeTemperatureC;
  private final double pressureToleranceFraction;
  private final PressureTargetStatus pressureTargetStatus;
  private final boolean chartActive;
  private final boolean withinChart;
  private final boolean inSurge;
  private final boolean inStonewall;
  private final double distanceToSurge;
  private final double distanceToStonewall;
  private final double surgeFlowM3PerHour;
  private final double surgeControlLineFlowM3PerHour;
  private final double requiredRecycleFraction;
  private final double recyclePowerLossKW;
  private final double recycleCoolerDutyKW;
  private final double maximumCapacityUtilization;
  private final String limitingConstraint;
  private final boolean capacityExceeded;
  private final boolean hardLimitExceeded;
  private final OperatingStatus operatingStatus;
  private final List<ConstraintSnapshot> constraints;

  /**
   * Creates an immutable snapshot from a solved compressor.
   *
   * @param compressor compressor to evaluate
   * @param pressureToleranceFraction relative discharge-pressure tolerance
   */
  private CompressorOperatingPointResult(Compressor compressor, double pressureToleranceFraction) {
    compressorName = compressor.getName();
    this.pressureToleranceFraction = pressureToleranceFraction;

    StreamInterface inlet = compressor.getInletStream();
    StreamInterface outlet = compressor.getOutletStream();
    flowM3PerHour = inlet == null ? Double.NaN : safeDouble(() -> inlet.getFlowRate("m3/hr"));
    polytropicHeadKJPerKg = safeDouble(compressor::getPolytropicFluidHead);
    speedRpm = safeDouble(compressor::getSpeed);
    polytropicEfficiency = safeDouble(compressor::getPolytropicEfficiency);
    powerKW = safeDouble(() -> compressor.getPower("kW"));
    inletPressureBara = inlet == null ? Double.NaN : safeDouble(() -> inlet.getPressure("bara"));
    requestedDischargePressureBara = safeDouble(compressor::getOutletPressure);
    actualDischargePressureBara = outlet == null ? Double.NaN : safeDouble(() -> outlet.getPressure("bara"));
    dischargeTemperatureC = outlet == null ? Double.NaN : safeDouble(() -> outlet.getTemperature("C"));

    dischargePressureErrorFraction = calculateRelativeError(actualDischargePressureBara,
        requestedDischargePressureBara);
    pressureTargetStatus = determinePressureTargetStatus(dischargePressureErrorFraction, pressureToleranceFraction);

    chartActive = safeBoolean(
        () -> compressor.getCompressorChart() != null && compressor.getCompressorChart().isUseCompressorChart());
    inSurge = chartActive && safeBoolean(compressor::isSurge);
    inStonewall = chartActive && safeBoolean(compressor::isStoneWall);
    withinChart = !chartActive || (!inSurge && !inStonewall);
    distanceToSurge = chartActive ? safeDouble(compressor::getDistanceToSurge) : Double.NaN;
    distanceToStonewall = chartActive ? safeDouble(compressor::getDistanceToStoneWall) : Double.NaN;
    surgeFlowM3PerHour = chartActive ? safeDouble(compressor::getSurgeFlowRate) : Double.NaN;
    surgeControlLineFlowM3PerHour = chartActive ? safeDouble(compressor::getControlLineFlow) : Double.NaN;
    requiredRecycleFraction = chartActive
        ? clampFraction(safeDouble(compressor::getRequiredRecycleFractionToControlLine))
        : 0.0;
    recyclePowerLossKW = chartActive
        ? safeDouble(() -> compressor.getAntiSurgeRecyclePower(requiredRecycleFraction, "kW"))
        : 0.0;
    recycleCoolerDutyKW = chartActive
        ? safeDouble(() -> compressor.getAntiSurgeRecycleHeatDuty(requiredRecycleFraction, "kW"))
        : 0.0;

    constraints = snapshotConstraints(compressor);
    maximumCapacityUtilization = safeDouble(compressor::getMaxUtilization);
    CapacityConstraint bottleneck = safeBottleneck(compressor);
    limitingConstraint = determineLimitingConstraint(bottleneck, inSurge, inStonewall);
    capacityExceeded = safeBoolean(compressor::isCapacityExceeded);
    hardLimitExceeded = safeBoolean(compressor::isHardLimitExceeded);
    operatingStatus = determineOperatingStatus();
  }

  /**
   * Creates a result using the default two-percent pressure-target tolerance.
   *
   * @param compressor compressor to evaluate
   * @return immutable operating-point result
   * @throws IllegalArgumentException if compressor is null
   */
  public static CompressorOperatingPointResult from(Compressor compressor) {
    return from(compressor, DEFAULT_PRESSURE_TOLERANCE_FRACTION);
  }

  /**
   * Creates a result using a caller-defined pressure-target tolerance.
   *
   * @param compressor compressor to evaluate
   * @param pressureToleranceFraction non-negative relative tolerance
   * @return immutable operating-point result
   * @throws IllegalArgumentException if compressor is null or tolerance is invalid
   */
  public static CompressorOperatingPointResult from(Compressor compressor, double pressureToleranceFraction) {
    if (compressor == null) {
      throw new IllegalArgumentException("compressor must not be null");
    }
    if (!isFinite(pressureToleranceFraction) || pressureToleranceFraction < 0.0) {
      throw new IllegalArgumentException("pressureToleranceFraction must be finite and non-negative");
    }
    return new CompressorOperatingPointResult(compressor, pressureToleranceFraction);
  }

  private static List<ConstraintSnapshot> snapshotConstraints(Compressor compressor) {
    List<ConstraintSnapshot> result = new ArrayList<ConstraintSnapshot>();
    Map<String, CapacityConstraint> constraints;
    try {
      constraints = compressor.getCapacityConstraints();
    } catch (Exception ex) {
      return Collections.emptyList();
    }

    for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
      CapacityConstraint constraint = entry.getValue();
      if (constraint == null) {
        continue;
      }
      try {
        result.add(new ConstraintSnapshot(constraint));
      } catch (Exception ex) {
        // Ignore individual constraint failures to preserve partial snapshots.
      }
    }

    return Collections.unmodifiableList(result);
  }

  private static CapacityConstraint safeBottleneck(Compressor compressor) {
    try {
      return compressor.getBottleneckConstraint();
    } catch (Exception ex) {
      return null;
    }
  }

  private static String determineLimitingConstraint(CapacityConstraint bottleneck, boolean inSurge,
      boolean inStonewall) {
    if (bottleneck != null) {
      return bottleneck.getName();
    }
    if (inSurge) {
      return "surgeMargin";
    }
    if (inStonewall) {
      return "stonewallMargin";
    }
    return "none";
  }

  private static double calculateRelativeError(double actual, double target) {
    if (!isFinite(actual) || !isFinite(target) || target <= 0.0) {
      return Double.NaN;
    }
    return (actual - target) / Math.abs(target);
  }

  private static PressureTargetStatus determinePressureTargetStatus(double relativeError, double tolerance) {
    if (!isFinite(relativeError)) {
      return PressureTargetStatus.NOT_EVALUATED;
    }
    if (relativeError < -tolerance) {
      return PressureTargetStatus.BELOW_TARGET;
    }
    if (relativeError > tolerance) {
      return PressureTargetStatus.ABOVE_TARGET;
    }
    return PressureTargetStatus.ON_TARGET;
  }

  private OperatingStatus determineOperatingStatus() {
    if (!isFiniteNonNegative(flowM3PerHour) || !isFinite(powerKW) || !isFinite(actualDischargePressureBara)) {
      return OperatingStatus.INVALID;
    }
    if (inSurge) {
      return OperatingStatus.SURGE;
    }
    if (inStonewall) {
      return OperatingStatus.STONEWALL;
    }
    if (capacityExceeded || hardLimitExceeded) {
      return OperatingStatus.CAPACITY_LIMIT;
    }
    if (pressureTargetStatus == PressureTargetStatus.BELOW_TARGET
        || pressureTargetStatus == PressureTargetStatus.ABOVE_TARGET) {
      return OperatingStatus.PRESSURE_TARGET_NOT_MET;
    }
    return OperatingStatus.VALID;
  }

  private static boolean isFiniteNonNegative(double value) {
    return isFinite(value) && value >= 0.0;
  }

  private static double clampFraction(double value) {
    if (!isFinite(value)) {
      return Double.NaN;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static double safeDouble(DoubleSupplier supplier) {
    try {
      return supplier.getAsDouble();
    } catch (Exception | StackOverflowError ex) {
      return Double.NaN;
    }
  }

  private static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  private static boolean safeBoolean(BooleanSupplier supplier) {
    try {
      return supplier.getAsBoolean();
    } catch (Exception | StackOverflowError ex) {
      return false;
    }
  }

  /**
   * Gets the result schema version.
   *
   * @return schema version
   */
  public String getSchemaVersion() {
    return schemaVersion;
  }

  /**
   * Gets the compressor name.
   *
   * @return compressor name
   */
  public String getCompressorName() {
    return compressorName;
  }

  /**
   * Gets actual inlet volumetric flow.
   *
   * @return flow in m3/hr
   */
  public double getFlowM3PerHour() {
    return flowM3PerHour;
  }

  /**
   * Gets polytropic fluid head.
   *
   * @return head in kJ/kg
   */
  public double getPolytropicHeadKJPerKg() {
    return polytropicHeadKJPerKg;
  }

  /**
   * Gets shaft speed.
   *
   * @return speed in rpm
   */
  public double getSpeedRpm() {
    return speedRpm;
  }

  /**
   * Gets polytropic efficiency.
   *
   * @return efficiency fraction
   */
  public double getPolytropicEfficiency() {
    return polytropicEfficiency;
  }

  /**
   * Gets shaft power.
   *
   * @return power in kW
   */
  public double getPowerKW() {
    return powerKW;
  }

  /**
   * Gets compressor inlet pressure.
   *
   * @return inlet pressure in bara
   */
  public double getInletPressureBara() {
    return inletPressureBara;
  }

  /**
   * Gets requested discharge pressure.
   *
   * @return requested pressure in bara
   */
  public double getRequestedDischargePressureBara() {
    return requestedDischargePressureBara;
  }

  /**
   * Gets calculated discharge pressure.
   *
   * @return actual pressure in bara
   */
  public double getActualDischargePressureBara() {
    return actualDischargePressureBara;
  }

  /**
   * Gets signed relative discharge-pressure error.
   *
   * @return {@code (actual - target) / abs(target)}
   */
  public double getDischargePressureErrorFraction() {
    return dischargePressureErrorFraction;
  }

  /**
   * Gets calculated discharge temperature.
   *
   * @return temperature in degrees Celsius
   */
  public double getDischargeTemperatureC() {
    return dischargeTemperatureC;
  }

  /**
   * Gets the relative pressure tolerance used for classification.
   *
   * @return pressure tolerance fraction
   */
  public double getPressureToleranceFraction() {
    return pressureToleranceFraction;
  }

  /**
   * Gets discharge-pressure target status.
   *
   * @return pressure-target status
   */
  public PressureTargetStatus getPressureTargetStatus() {
    return pressureTargetStatus;
  }

  /**
   * Checks whether a compressor performance chart is active.
   *
   * @return true when a chart is active
   */
  public boolean isChartActive() {
    return chartActive;
  }

  /**
   * Checks whether the point is between surge and stonewall.
   *
   * @return true when inside the map, or when no map is active
   */
  public boolean isWithinChart() {
    return withinChart;
  }

  /**
   * Checks whether the point is in surge.
   *
   * @return true when in surge
   */
  public boolean isInSurge() {
    return inSurge;
  }

  /**
   * Checks whether the point is beyond stonewall.
   *
   * @return true when beyond stonewall
   */
  public boolean isInStonewall() {
    return inStonewall;
  }

  /**
   * Gets fractional distance to surge.
   *
   * @return {@code flow / surgeFlow - 1}
   */
  public double getDistanceToSurge() {
    return distanceToSurge;
  }

  /**
   * Gets fractional distance to stonewall.
   *
   * @return {@code stonewallFlow / flow - 1}
   */
  public double getDistanceToStonewall() {
    return distanceToStonewall;
  }

  /**
   * Gets surge-line flow at the current head.
   *
   * @return flow in m3/hr
   */
  public double getSurgeFlowM3PerHour() {
    return surgeFlowM3PerHour;
  }

  /**
   * Gets anti-surge control-line flow at the current head.
   *
   * @return flow in m3/hr
   */
  public double getSurgeControlLineFlowM3PerHour() {
    return surgeControlLineFlowM3PerHour;
  }

  /**
   * Gets screening recycle fraction required to reach the anti-surge control line.
   *
   * @return recycle fraction from 0 to 1
   */
  public double getRequiredRecycleFraction() {
    return requiredRecycleFraction;
  }

  /**
   * Gets screening shaft-power loss due to recycle.
   *
   * @return recycle power loss in kW
   */
  public double getRecyclePowerLossKW() {
    return recyclePowerLossKW;
  }

  /**
   * Gets screening recycle-cooler duty.
   *
   * @return cooler duty in kW
   */
  public double getRecycleCoolerDutyKW() {
    return recycleCoolerDutyKW;
  }

  /**
   * Gets maximum utilization across enabled compressor constraints.
   *
   * @return utilization fraction, where 1.0 is the limit
   */
  public double getMaximumCapacityUtilization() {
    return maximumCapacityUtilization;
  }

  /**
   * Gets the limiting capacity constraint.
   *
   * @return constraint name, or {@code "none"}
   */
  public String getLimitingConstraint() {
    return limitingConstraint;
  }

  /**
   * Checks whether any capacity constraint is exceeded.
   *
   * @return true when capacity is exceeded
   */
  public boolean isCapacityExceeded() {
    return capacityExceeded;
  }

  /**
   * Checks whether any hard capacity limit is exceeded.
   *
   * @return true when a hard limit is exceeded
   */
  public boolean isHardLimitExceeded() {
    return hardLimitExceeded;
  }

  /**
   * Gets the overall operating status.
   *
   * @return operating status
   */
  public OperatingStatus getOperatingStatus() {
    return operatingStatus;
  }

  /**
   * Checks aggregate operating-point feasibility.
   *
   * @return true when {@link #getOperatingStatus()} is {@link OperatingStatus#VALID}
   */
  public boolean isFeasible() {
    return operatingStatus == OperatingStatus.VALID;
  }

  /**
   * Gets immutable snapshots of all compressor constraints.
   *
   * @return unmodifiable constraint list
   */
  public List<ConstraintSnapshot> getConstraints() {
    return constraints;
  }

  /**
   * Serializes this result to schema-versioned JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(this);
  }
}
