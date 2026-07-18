package neqsim.process.safety.sif;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;

/** Process-bound sensor or switch channel used by a closed-loop safety function. */
public final class SafetyFunctionChannel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Reads a scalar process value from the isolated process model. */
  public interface SignalExtractor extends Serializable {
    double extract(ProcessSystem process);
  }

  /** Direction in which the measured signal demands a trip. */
  public enum TripDirection {
    HIGH, LOW
  }

  /** Explicit channel state used for fault-injection and degraded-mode verification. */
  public enum FaultMode {
    HEALTHY, BYPASSED, STUCK_NORMAL, STUCK_TRIP, BIASED
  }

  /** One sampled channel state recorded in the scenario evidence. */
  static final class Sample {
    private final String tag;
    private final double rawValue;
    private final double indicatedValue;
    private final boolean demand;
    private final boolean tripped;
    private final boolean available;
    private final FaultMode faultMode;

    Sample(String tag, double rawValue, double indicatedValue, boolean demand, boolean tripped,
        boolean available, FaultMode faultMode) {
      this.tag = tag;
      this.rawValue = rawValue;
      this.indicatedValue = indicatedValue;
      this.demand = demand;
      this.tripped = tripped;
      this.available = available;
      this.faultMode = faultMode;
    }

    boolean isTripped() {
      return tripped;
    }

    boolean isAvailable() {
      return available;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("tag", tag);
      result.put("rawValue", Double.valueOf(rawValue));
      result.put("indicatedValue", Double.valueOf(indicatedValue));
      result.put("demand", Boolean.valueOf(demand));
      result.put("tripped", Boolean.valueOf(tripped));
      result.put("available", Boolean.valueOf(available));
      result.put("faultMode", faultMode.name());
      return result;
    }
  }

  private final String tag;
  private final String unit;
  private final double setpoint;
  private final TripDirection tripDirection;
  private final double responseDelaySeconds;
  private final FaultMode faultMode;
  private final double bias;
  private final SignalExtractor extractor;
  private double continuousDemandSeconds;
  private boolean latchedTrip;

  private SafetyFunctionChannel(Builder builder) {
    tag = requireText(builder.tag, "tag");
    unit = requireText(builder.unit, "unit");
    setpoint = finite(builder.setpoint, "setpoint");
    tripDirection = builder.tripDirection;
    responseDelaySeconds = nonNegative(builder.responseDelaySeconds, "responseDelaySeconds");
    faultMode = builder.faultMode;
    bias = finite(builder.bias, "bias");
    extractor = builder.extractor;
  }

  /**
   * Starts a high-trip channel definition.
   *
   * @param tag instrument tag
   * @param unit signal engineering unit
   * @param setpoint high-trip setpoint
   * @param extractor process signal binding
   * @return channel builder
   */
  public static Builder highTrip(String tag, String unit, double setpoint, SignalExtractor extractor) {
    return new Builder(tag, unit, setpoint, TripDirection.HIGH, extractor);
  }

  /**
   * Starts a low-trip channel definition.
   *
   * @param tag instrument tag
   * @param unit signal engineering unit
   * @param setpoint low-trip setpoint
   * @param extractor process signal binding
   * @return channel builder
   */
  public static Builder lowTrip(String tag, String unit, double setpoint, SignalExtractor extractor) {
    return new Builder(tag, unit, setpoint, TripDirection.LOW, extractor);
  }

  Sample sample(ProcessSystem process, double timeStepSeconds) {
    double rawValue = extractor.extract(process);
    if (!Double.isFinite(rawValue)) {
      throw new IllegalStateException("Channel " + tag + " returned a non-finite signal");
    }
    double indicatedValue = faultMode == FaultMode.BIASED ? rawValue + bias : rawValue;
    boolean available = faultMode != FaultMode.BYPASSED;
    boolean demand = tripDirection == TripDirection.HIGH ? indicatedValue >= setpoint : indicatedValue <= setpoint;

    if (faultMode == FaultMode.STUCK_NORMAL || faultMode == FaultMode.BYPASSED) {
      demand = false;
    } else if (faultMode == FaultMode.STUCK_TRIP) {
      demand = true;
    }

    if (demand && available) {
      continuousDemandSeconds += timeStepSeconds;
      if (continuousDemandSeconds + 1.0e-12 >= responseDelaySeconds) {
        latchedTrip = true;
      }
    } else {
      continuousDemandSeconds = 0.0;
    }
    return new Sample(tag, rawValue, indicatedValue, demand, latchedTrip, available, faultMode);
  }

  void reset() {
    continuousDemandSeconds = 0.0;
    latchedTrip = false;
  }

  Map<String, Object> configurationMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("tag", tag);
    result.put("unit", unit);
    result.put("setpoint", Double.valueOf(setpoint));
    result.put("tripDirection", tripDirection.name());
    result.put("responseDelaySeconds", Double.valueOf(responseDelaySeconds));
    result.put("faultMode", faultMode.name());
    result.put("bias", Double.valueOf(bias));
    return result;
  }

  /** Builder for one safety-function channel. */
  public static final class Builder {
    private final String tag;
    private final String unit;
    private final double setpoint;
    private final TripDirection tripDirection;
    private final SignalExtractor extractor;
    private double responseDelaySeconds;
    private FaultMode faultMode = FaultMode.HEALTHY;
    private double bias;

    private Builder(String tag, String unit, double setpoint, TripDirection tripDirection,
        SignalExtractor extractor) {
      if (extractor == null) {
        throw new IllegalArgumentException("extractor must not be null");
      }
      this.tag = tag;
      this.unit = unit;
      this.setpoint = setpoint;
      this.tripDirection = tripDirection;
      this.extractor = extractor;
    }

    /** @param value channel response delay in seconds @return this builder */
    public Builder responseDelaySeconds(double value) {
      responseDelaySeconds = value;
      return this;
    }

    /** @param value channel fault mode @return this builder */
    public Builder faultMode(FaultMode value) {
      if (value == null) {
        throw new IllegalArgumentException("faultMode must not be null");
      }
      faultMode = value;
      return this;
    }

    /** @param value additive signal bias in the channel unit @return this builder */
    public Builder bias(double value) {
      bias = value;
      return this;
    }

    /** @return validated channel */
    public SafetyFunctionChannel build() {
      if (faultMode != FaultMode.BIASED && Math.abs(bias) > 0.0) {
        throw new IllegalStateException("bias requires BIASED fault mode");
      }
      return new SafetyFunctionChannel(this);
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }
}
