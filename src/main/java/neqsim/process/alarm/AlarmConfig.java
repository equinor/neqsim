package neqsim.process.alarm;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration describing the alarm limits, deadband and delay for a measurement signal.
 */
public final class AlarmConfig implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Double lowLowLimit;
  private final Double lowLimit;
  private final Double highLimit;
  private final Double highHighLimit;
  private final double deadband;
  private final double delay;
  private final String unit;

  private AlarmConfig(Builder builder) {
    this.lowLowLimit = builder.lowLowLimit;
    this.lowLimit = builder.lowLimit;
    this.highLimit = builder.highLimit;
    this.highHighLimit = builder.highHighLimit;
    this.deadband = builder.deadband;
    this.delay = builder.delay;
    this.unit = builder.unit;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Double getLowLowLimit() {
    return lowLowLimit;
  }

  public Double getLowLimit() {
    return lowLimit;
  }

  public Double getHighLimit() {
    return highLimit;
  }

  public Double getHighHighLimit() {
    return highHighLimit;
  }

  public double getDeadband() {
    return deadband;
  }

  public double getDelay() {
    return delay;
  }

  public String getUnit() {
    return unit;
  }

  public boolean hasLimit(AlarmLevel level) {
    return getLimit(level) != null;
  }

  public Double getLimit(AlarmLevel level) {
    switch (Objects.requireNonNull(level, "level")) {
      case LOLO:
        return lowLowLimit;
      case LO:
        return lowLimit;
      case HI:
        return highLimit;
      case HIHI:
        return highHighLimit;
      default:
        return null;
    }
  }

  /**
   * Builder for {@link AlarmConfig} instances.
   */
  public static final class Builder {
    private Double lowLowLimit;
    private Double lowLimit;
    private Double highLimit;
    private Double highHighLimit;
    private double deadband;
    private double delay;
    private String unit = "";

    private Builder() {}

    public Builder lowLowLimit(Double value) {
      this.lowLowLimit = value;
      return this;
    }

    public Builder lowLimit(Double value) {
      this.lowLimit = value;
      return this;
    }

    public Builder highLimit(Double value) {
      this.highLimit = value;
      return this;
    }

    public Builder highHighLimit(Double value) {
      this.highHighLimit = value;
      return this;
    }

    public Builder deadband(double value) {
      this.deadband = Math.max(0.0, value);
      return this;
    }

    public Builder delay(double value) {
      this.delay = Math.max(0.0, value);
      return this;
    }

    public Builder unit(String unit) {
      this.unit = unit == null ? "" : unit;
      return this;
    }

    public AlarmConfig build() {
      return new AlarmConfig(this);
    }
  }
}
