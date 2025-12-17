package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Results from safety screening.
 *
 * @author ESOL
 * @version 1.0
 */
public final class SafetyReport implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Safety classification levels.
   */
  public enum SafetyLevel {
    /**
     * Standard safety requirements - no special measures needed.
     */
    STANDARD("Standard", "Normal safety design"),

    /**
     * Enhanced safety requirements - additional measures needed.
     */
    ENHANCED("Enhanced", "Additional safety measures required"),

    /**
     * High safety requirements - comprehensive safety case needed.
     */
    HIGH("High", "Comprehensive safety case required");

    private final String displayName;
    private final String description;

    SafetyLevel(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    public String getDisplayName() {
      return displayName;
    }

    public String getDescription() {
      return description;
    }
  }

  private final SafetyLevel overallLevel;
  private final double estimatedBlowdownTimeMinutes;
  private final double minimumMetalTempC;
  private final double inventoryTonnes;
  private final double psvRequiredCapacityKgPerHr;
  private final boolean h2sPresent;
  private final boolean highPressure;
  private final boolean mannedFacility;
  private final Map<String, String> requirements;
  private final Map<String, Double> scenarios;

  private SafetyReport(Builder builder) {
    this.overallLevel = builder.overallLevel;
    this.estimatedBlowdownTimeMinutes = builder.estimatedBlowdownTimeMinutes;
    this.minimumMetalTempC = builder.minimumMetalTempC;
    this.inventoryTonnes = builder.inventoryTonnes;
    this.psvRequiredCapacityKgPerHr = builder.psvRequiredCapacityKgPerHr;
    this.h2sPresent = builder.h2sPresent;
    this.highPressure = builder.highPressure;
    this.mannedFacility = builder.mannedFacility;
    this.requirements = new LinkedHashMap<>(builder.requirements);
    this.scenarios = new LinkedHashMap<>(builder.scenarios);
  }

  public static Builder builder() {
    return new Builder();
  }

  // Getters
  public SafetyLevel getOverallLevel() {
    return overallLevel;
  }

  public double getEstimatedBlowdownTimeMinutes() {
    return estimatedBlowdownTimeMinutes;
  }

  public double getMinimumMetalTempC() {
    return minimumMetalTempC;
  }

  public double getInventoryTonnes() {
    return inventoryTonnes;
  }

  public double getPsvRequiredCapacityKgPerHr() {
    return psvRequiredCapacityKgPerHr;
  }

  public boolean isH2sPresent() {
    return h2sPresent;
  }

  public boolean isHighPressure() {
    return highPressure;
  }

  public boolean isMannedFacility() {
    return mannedFacility;
  }

  public Map<String, String> getRequirements() {
    return new LinkedHashMap<>(requirements);
  }

  public Map<String, Double> getScenarios() {
    return new LinkedHashMap<>(scenarios);
  }

  /**
   * Checks if blowdown meets typical 15-minute target.
   *
   * @return true if within target
   */
  public boolean meetsBlowdownTarget() {
    return estimatedBlowdownTimeMinutes <= 15.0;
  }

  /**
   * Gets summary suitable for reporting.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Safety Assessment: ").append(overallLevel.getDisplayName()).append("\n");
    sb.append("  Blowdown time: ").append(String.format("%.1f", estimatedBlowdownTimeMinutes))
        .append(" min");
    sb.append(meetsBlowdownTarget() ? " (OK)" : " (EXCEEDS TARGET)").append("\n");
    sb.append("  Min metal temp: ").append(String.format("%.0f", minimumMetalTempC)).append("°C\n");
    sb.append("  Inventory: ").append(String.format("%.1f", inventoryTonnes)).append(" tonnes\n");
    sb.append("  PSV capacity: ").append(String.format("%.0f", psvRequiredCapacityKgPerHr))
        .append(" kg/hr\n");
    if (h2sPresent) {
      sb.append("  WARNING: H2S present - toxic gas considerations\n");
    }
    if (highPressure) {
      sb.append("  NOTE: High pressure system\n");
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("SafetyReport[level=%s, blowdown=%.1fmin, minTemp=%.0f°C]", overallLevel,
        estimatedBlowdownTimeMinutes, minimumMetalTempC);
  }

  /**
   * Builder for SafetyReport.
   */
  public static final class Builder {
    private SafetyLevel overallLevel = SafetyLevel.STANDARD;
    private double estimatedBlowdownTimeMinutes = Double.NaN;
    private double minimumMetalTempC = Double.NaN;
    private double inventoryTonnes = Double.NaN;
    private double psvRequiredCapacityKgPerHr = Double.NaN;
    private boolean h2sPresent = false;
    private boolean highPressure = false;
    private boolean mannedFacility = true;
    private final Map<String, String> requirements = new LinkedHashMap<>();
    private final Map<String, Double> scenarios = new LinkedHashMap<>();

    public Builder overallLevel(SafetyLevel level) {
      this.overallLevel = level;
      return this;
    }

    public Builder blowdownTime(double minutes) {
      this.estimatedBlowdownTimeMinutes = minutes;
      return this;
    }

    public Builder minimumMetalTemp(double tempC) {
      this.minimumMetalTempC = tempC;
      return this;
    }

    public Builder inventory(double tonnes) {
      this.inventoryTonnes = tonnes;
      return this;
    }

    public Builder psvCapacity(double kgPerHr) {
      this.psvRequiredCapacityKgPerHr = kgPerHr;
      return this;
    }

    public Builder h2sPresent(boolean present) {
      this.h2sPresent = present;
      return this;
    }

    public Builder highPressure(boolean high) {
      this.highPressure = high;
      return this;
    }

    public Builder mannedFacility(boolean manned) {
      this.mannedFacility = manned;
      return this;
    }

    public Builder addRequirement(String key, String requirement) {
      this.requirements.put(key, requirement);
      return this;
    }

    public Builder addScenario(String name, double frequency) {
      this.scenarios.put(name, frequency);
      return this;
    }

    public SafetyReport build() {
      return new SafetyReport(this);
    }
  }
}
