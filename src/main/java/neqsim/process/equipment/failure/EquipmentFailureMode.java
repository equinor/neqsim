package neqsim.process.equipment.failure;

import java.io.Serializable;

/**
 * Represents a failure mode for process equipment.
 *
 * <p>
 * Equipment failure modes define how equipment can fail and the consequences of that failure on
 * process operations. This class supports both complete failures (trips) and degraded operation
 * modes.
 * </p>
 *
 * <p>
 * <b>Typical Usage:</b>
 * </p>
 * 
 * <pre>
 * {@code
 * EquipmentFailureMode tripMode = EquipmentFailureMode.builder().name("Compressor Trip")
 *     .type(FailureType.TRIP).capacityFactor(0.0) // Complete loss
 *     .mttr(24.0) // 24 hours to repair
 *     .build();
 *
 * compressor.setFailureMode(tripMode);
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EquipmentFailureMode implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Types of equipment failure.
   */
  public enum FailureType {
    /**
     * Equipment trips and stops completely. No output, requires restart.
     */
    TRIP,

    /**
     * Equipment operates at reduced capacity. Partial output available.
     */
    DEGRADED,

    /**
     * Partial failure - some functions lost but equipment continues.
     */
    PARTIAL_FAILURE,

    /**
     * Complete failure - equipment non-functional.
     */
    FULL_FAILURE,

    /**
     * Planned maintenance shutdown.
     */
    MAINTENANCE,

    /**
     * Equipment bypassed - flow routed around.
     */
    BYPASSED
  }

  /** Name of the failure mode. */
  private final String name;

  /** Description of the failure mode. */
  private final String description;

  /** Type of failure. */
  private final FailureType type;

  /**
   * Capacity factor when failed (0.0 = complete loss, 0.5 = 50% capacity, 1.0 = no effect).
   */
  private final double capacityFactor;

  /**
   * Efficiency factor when failed (multiplier on normal efficiency).
   */
  private final double efficiencyFactor;

  /** Mean Time To Repair in hours. */
  private final double mttr;

  /** Failure frequency per year. */
  private final double failureFrequency;

  /** Whether the failure requires immediate plant action. */
  private final boolean requiresImmediateAction;

  /** Whether the failure can be recovered automatically. */
  private final boolean autoRecoverable;

  /** Time in seconds before auto-recovery (if autoRecoverable). */
  private final double autoRecoveryTime;

  private EquipmentFailureMode(Builder builder) {
    this.name = builder.name;
    this.description = builder.description;
    this.type = builder.type;
    this.capacityFactor = builder.capacityFactor;
    this.efficiencyFactor = builder.efficiencyFactor;
    this.mttr = builder.mttr;
    this.failureFrequency = builder.failureFrequency;
    this.requiresImmediateAction = builder.requiresImmediateAction;
    this.autoRecoverable = builder.autoRecoverable;
    this.autoRecoveryTime = builder.autoRecoveryTime;
  }

  // Getters

  /**
   * Gets the failure mode name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the failure mode description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the failure type.
   *
   * @return the failure type
   */
  public FailureType getType() {
    return type;
  }

  /**
   * Gets the capacity factor (0.0 to 1.0).
   *
   * @return the capacity factor
   */
  public double getCapacityFactor() {
    return capacityFactor;
  }

  /**
   * Gets the efficiency factor.
   *
   * @return the efficiency factor
   */
  public double getEfficiencyFactor() {
    return efficiencyFactor;
  }

  /**
   * Gets the mean time to repair in hours.
   *
   * @return the MTTR
   */
  public double getMttr() {
    return mttr;
  }

  /**
   * Gets the failure frequency per year.
   *
   * @return the failure frequency
   */
  public double getFailureFrequency() {
    return failureFrequency;
  }

  /**
   * Checks if immediate action is required.
   *
   * @return true if immediate action needed
   */
  public boolean isRequiresImmediateAction() {
    return requiresImmediateAction;
  }

  /**
   * Checks if failure can auto-recover.
   *
   * @return true if auto-recoverable
   */
  public boolean isAutoRecoverable() {
    return autoRecoverable;
  }

  /**
   * Gets the auto-recovery time in seconds.
   *
   * @return the auto-recovery time
   */
  public double getAutoRecoveryTime() {
    return autoRecoveryTime;
  }

  /**
   * Checks if this is a complete failure (no output).
   *
   * @return true if complete failure
   */
  public boolean isCompleteFailure() {
    return capacityFactor <= 0.0 || type == FailureType.TRIP || type == FailureType.FULL_FAILURE;
  }

  /**
   * Gets the production loss factor (1.0 - capacityFactor).
   *
   * @return the production loss as a fraction
   */
  public double getProductionLossFactor() {
    return 1.0 - capacityFactor;
  }

  // Pre-defined common failure modes

  /**
   * Creates a standard trip failure mode.
   *
   * @param equipmentType type of equipment for MTTR estimation
   * @return trip failure mode
   */
  public static EquipmentFailureMode trip(String equipmentType) {
    double mttr = estimateMttr(equipmentType, FailureType.TRIP);
    return builder().name("Trip").description("Equipment trip - complete shutdown")
        .type(FailureType.TRIP).capacityFactor(0.0).mttr(mttr).requiresImmediateAction(true)
        .build();
  }

  /**
   * Creates a degraded operation failure mode.
   *
   * @param capacityPercent remaining capacity percentage (0-100)
   * @return degraded failure mode
   */
  public static EquipmentFailureMode degraded(double capacityPercent) {
    return builder().name("Degraded").description("Operating at reduced capacity")
        .type(FailureType.DEGRADED).capacityFactor(capacityPercent / 100.0).mttr(8.0).build();
  }

  /**
   * Creates a bypass failure mode.
   *
   * @return bypass failure mode
   */
  public static EquipmentFailureMode bypassed() {
    return builder().name("Bypassed").description("Equipment bypassed - flow routed around")
        .type(FailureType.BYPASSED).capacityFactor(0.0).mttr(0.0).build();
  }

  /**
   * Creates a maintenance shutdown mode.
   *
   * @param durationHours planned maintenance duration
   * @return maintenance failure mode
   */
  public static EquipmentFailureMode maintenance(double durationHours) {
    return builder().name("Maintenance").description("Planned maintenance shutdown")
        .type(FailureType.MAINTENANCE).capacityFactor(0.0).mttr(durationHours).build();
  }

  private static double estimateMttr(String equipmentType, FailureType type) {
    // Default MTTR estimates based on equipment type (hours)
    // These should eventually come from database
    if (equipmentType == null) {
      return 24.0;
    }
    String lowerType = equipmentType.toLowerCase();
    if (lowerType.contains("compressor")) {
      return type == FailureType.TRIP ? 4.0 : 48.0;
    } else if (lowerType.contains("pump")) {
      return type == FailureType.TRIP ? 2.0 : 24.0;
    } else if (lowerType.contains("separator")) {
      return type == FailureType.TRIP ? 1.0 : 12.0;
    } else if (lowerType.contains("heat") || lowerType.contains("exchanger")) {
      return type == FailureType.TRIP ? 2.0 : 72.0;
    } else if (lowerType.contains("valve")) {
      return type == FailureType.TRIP ? 1.0 : 8.0;
    }
    return 24.0;
  }

  /**
   * Creates a new builder.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return String.format("FailureMode[%s, type=%s, capacity=%.0f%%, MTTR=%.1fh]", name, type,
        capacityFactor * 100, mttr);
  }

  /**
   * Builder for EquipmentFailureMode.
   */
  public static class Builder {
    private String name = "Unknown";
    private String description = "";
    private FailureType type = FailureType.TRIP;
    private double capacityFactor = 0.0;
    private double efficiencyFactor = 1.0;
    private double mttr = 24.0;
    private double failureFrequency = 0.0;
    private boolean requiresImmediateAction = false;
    private boolean autoRecoverable = false;
    private double autoRecoveryTime = 0.0;

    /**
     * Sets the failure mode name.
     *
     * @param name the name
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Sets the description.
     *
     * @param description the description
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Sets the failure type.
     *
     * @param type the failure type
     * @return this builder
     */
    public Builder type(FailureType type) {
      this.type = type;
      return this;
    }

    /**
     * Sets the capacity factor (0.0 to 1.0).
     *
     * @param factor the capacity factor
     * @return this builder
     */
    public Builder capacityFactor(double factor) {
      this.capacityFactor = Math.max(0.0, Math.min(1.0, factor));
      return this;
    }

    /**
     * Sets the efficiency factor.
     *
     * @param factor the efficiency factor
     * @return this builder
     */
    public Builder efficiencyFactor(double factor) {
      this.efficiencyFactor = factor;
      return this;
    }

    /**
     * Sets the mean time to repair in hours.
     *
     * @param hours the MTTR
     * @return this builder
     */
    public Builder mttr(double hours) {
      this.mttr = hours;
      return this;
    }

    /**
     * Sets the failure frequency per year.
     *
     * @param frequency failures per year
     * @return this builder
     */
    public Builder failureFrequency(double frequency) {
      this.failureFrequency = frequency;
      return this;
    }

    /**
     * Sets whether immediate action is required.
     *
     * @param required true if immediate action needed
     * @return this builder
     */
    public Builder requiresImmediateAction(boolean required) {
      this.requiresImmediateAction = required;
      return this;
    }

    /**
     * Sets whether the failure is auto-recoverable.
     *
     * @param recoverable true if auto-recoverable
     * @return this builder
     */
    public Builder autoRecoverable(boolean recoverable) {
      this.autoRecoverable = recoverable;
      return this;
    }

    /**
     * Sets the auto-recovery time in seconds.
     *
     * @param seconds time to auto-recover
     * @return this builder
     */
    public Builder autoRecoveryTime(double seconds) {
      this.autoRecoveryTime = seconds;
      return this;
    }

    /**
     * Builds the failure mode.
     *
     * @return the failure mode
     */
    public EquipmentFailureMode build() {
      return new EquipmentFailureMode(this);
    }
  }
}
