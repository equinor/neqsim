package neqsim.process.safety.sif;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.PushButton;
import neqsim.process.measurementdevice.TransientMeasurementDevice;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Process-bound sensor or switch channel used by a closed-loop safety function.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SafetyFunctionChannel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Reads a scalar process value from the isolated process model. */
  public interface SignalExtractor extends Serializable {
    /**
     * Reads one signal without changing the physical process.
     *
     * @param process isolated executing process
     * @return finite scalar signal
     */
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

    /** Creates one immutable sampled state. */
    Sample(String tag, double rawValue, double indicatedValue, boolean demand, boolean tripped, boolean available,
        FaultMode faultMode) {
      this.tag = tag;
      this.rawValue = rawValue;
      this.indicatedValue = indicatedValue;
      this.demand = demand;
      this.tripped = tripped;
      this.available = available;
      this.faultMode = faultMode;
    }

    /** @return whether this channel has latched a trip */
    boolean isTripped() {
      return tripped;
    }

    /** @return whether this channel participates in the fixed configured vote */
    boolean isAvailable() {
      return available;
    }

    /** @return scalar sampled evidence */
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
  private final MeasurementDeviceInterface measurementSource;
  private final String inputIdentityStatus;
  private double continuousDemandSeconds;
  private boolean latchedTrip;

  /**
   * Creates a validated channel.
   *
   * @param builder requested definition
   */
  private SafetyFunctionChannel(Builder builder) {
    tag = requireText(builder.tag, "tag");
    unit = requireText(builder.unit, "unit");
    setpoint = finite(builder.setpoint, "setpoint");
    tripDirection = builder.tripDirection;
    responseDelaySeconds = nonNegative(builder.responseDelaySeconds, "responseDelaySeconds");
    faultMode = builder.faultMode;
    bias = finite(builder.bias, "bias");
    extractor = builder.extractor;
    measurementSource = builder.measurementSource;
    inputIdentityStatus = builder.inputIdentityStatus;
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

  /**
   * Starts a high-trip definition from an actual held-sample measurement.
   *
   * @param source actual measurement in the configured isolated process
   * @param unit engineering unit requested from the held sample
   * @param setpoint high-trip setpoint
   * @return measurement-backed channel builder
   * @throws IllegalArgumentException if the source lacks enabled transient sampling
   */
  public static Builder highTripFromMeasurement(MeasurementDeviceInterface source, String unit, double setpoint) {
    return fromMeasurement(source, unit, setpoint, TripDirection.HIGH);
  }

  /**
   * Starts a low-trip definition from an actual held-sample measurement.
   *
   * @param source actual measurement in the configured isolated process
   * @param unit engineering unit requested from the held sample
   * @param setpoint low-trip setpoint
   * @return measurement-backed channel builder
   * @throws IllegalArgumentException if the source lacks enabled transient sampling
   */
  public static Builder lowTripFromMeasurement(MeasurementDeviceInterface source, String unit, double setpoint) {
    return fromMeasurement(source, unit, setpoint, TripDirection.LOW);
  }

  /**
   * Creates a measurement-backed builder without accepting a separately invented channel tag.
   *
   * @param source actual configured measurement
   * @param unit held-sample engineering unit
   * @param setpoint trip setpoint
   * @param direction high or low demand direction
   * @return measurement-backed builder
   * @throws IllegalArgumentException if transient held sampling is unavailable
   */
  private static Builder fromMeasurement(final MeasurementDeviceInterface source, final String unit, double setpoint,
      TripDirection direction) {
    if (!(source instanceof TransientMeasurementDevice)
        || !((TransientMeasurementDevice) source).isTransientSamplingEnabled()) {
      throw new IllegalArgumentException("Measurement-backed channels require enabled transient held sampling");
    }
    Builder builder = new Builder(source.getName(), unit, setpoint, direction, new SignalExtractor() {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public double extract(ProcessSystem process) {
        requireLocalMeasurement(process, source);
        if (!((TransientMeasurementDevice) source).isTransientSamplingEnabled()) {
          throw new IllegalStateException("Measurement-backed channel lost enabled transient sampling");
        }
        return ((TransientMeasurementDevice) source).getTransientSample(unit);
      }
    });
    builder.measurementSource = source;
    builder.inputIdentityStatus = "NATIVE_HELD_MEASUREMENT";
    return builder;
  }

  /**
   * Starts a discrete manual-trip channel from an actual local push button.
   *
   * <p>
   * This reads the button's binary latch, not a pressure held sample. Linked automatic actions are prohibited so the
   * safety function, rather than a hidden button side effect, owns final-element actuation.
   * </p>
   *
   * @param source local native push button in the configured process
   * @return binary high-trip builder with a one-half threshold
   * @throws IllegalArgumentException if the button is absent, subclassed or owns linked automatic actions
   */
  public static Builder manualTripFromPushButton(final PushButton source) {
    if (source == null || source.getClass() != PushButton.class || !source.getLinkedLogics().isEmpty()
        || source.getLinkedBlowdownValve() != null && source.isAutoActivateValve()) {
      throw new IllegalArgumentException("Manual channels require a local native button without linked actions");
    }
    Builder builder = new Builder(source.getName(), "binary", 0.5, TripDirection.HIGH, new SignalExtractor() {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public double extract(ProcessSystem process) {
        requireLocalMeasurement(process, source);
        if (!source.getLinkedLogics().isEmpty()
            || source.getLinkedBlowdownValve() != null && source.isAutoActivateValve()) {
          throw new IllegalStateException("Manual channel acquired linked automatic actions");
        }
        return source.getMeasuredValue("binary");
      }
    });
    builder.measurementSource = source;
    builder.inputIdentityStatus = "NATIVE_DISCRETE_PUSH_BUTTON";
    return builder;
  }

  /**
   * Rejects a channel that captured a measurement from another process copy.
   *
   * @param process process actually executing the safety function
   * @param source captured native measurement
   * @throws IllegalStateException if the source is not registered or attached to this exact process
   */
  private static void requireLocalMeasurement(ProcessSystem process, MeasurementDeviceInterface source) {
    if (process != null) {
      for (MeasurementDeviceInterface candidate : process.getMeasurementDevices()) {
        if (candidate == source) {
          return;
        }
      }
      for (ControllerDeviceInterface controller : process.getControllerDevices()) {
        if (controller != null && controller.getTransmitter() == source) {
          return;
        }
      }
      for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
        for (ControllerDeviceInterface controller : unit.getControllers()) {
          if (controller != null && controller.getTransmitter() == source) {
            return;
          }
        }
      }
    }
    throw new IllegalStateException("Measurement-backed channel source is not in the executing process copy");
  }

  /** @return validated actual channel tag */
  public String getTag() {
    return tag;
  }

  /**
   * Exposes the actual measurement for configured-copy identity validation.
   *
   * @return actual measurement, or null for a legacy arbitrary extractor
   */
  public MeasurementDeviceInterface getMeasurementSource() {
    return measurementSource;
  }

  /**
   * Evaluates one held signal and advances its demand qualification timer.
   *
   * @param process isolated executing process
   * @param timeStepSeconds positive native logic scan interval
   * @return immutable sampled state
   */
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

  /** Clears channel demand history and the trip latch. */
  void reset() {
    continuousDemandSeconds = 0.0;
    latchedTrip = false;
  }

  /** @return configured values and source identity, without engineering approval */
  Map<String, Object> configurationMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("tag", tag);
    result.put("unit", unit);
    result.put("setpoint", Double.valueOf(setpoint));
    result.put("tripDirection", tripDirection.name());
    result.put("responseDelaySeconds", Double.valueOf(responseDelaySeconds));
    result.put("faultMode", faultMode.name());
    result.put("bias", Double.valueOf(bias));
    result.put("inputIdentityStatus",
        inputIdentityStatus == null ? "CUSTOM_EXTRACTOR_UNVERIFIED" : inputIdentityStatus);
    if (measurementSource != null) {
      Map<String, Object> source = new LinkedHashMap<String, Object>();
      source.put("name", measurementSource.getName());
      source.put("class", measurementSource.getClass().getName());
      if (measurementSource instanceof TransientMeasurementDevice) {
        source.put("transientSamplingEnabled",
            Boolean.valueOf(((TransientMeasurementDevice) measurementSource).isTransientSamplingEnabled()));
      }
      result.put("measurementSource", source);
    }
    return result;
  }

  /** Builder for one safety-function channel. */
  public static final class Builder {
    private final String tag;
    private final String unit;
    private final double setpoint;
    private final TripDirection tripDirection;
    private final SignalExtractor extractor;
    private MeasurementDeviceInterface measurementSource;
    private String inputIdentityStatus = "CUSTOM_EXTRACTOR_UNVERIFIED";
    private double responseDelaySeconds;
    private FaultMode faultMode = FaultMode.HEALTHY;
    private double bias;

    /**
     * Creates an unevaluated channel definition.
     *
     * @param tag actual or declared instrument tag
     * @param unit signal unit
     * @param setpoint trip threshold
     * @param tripDirection high or low demand
     * @param extractor scalar signal source
     */
    private Builder(String tag, String unit, double setpoint, TripDirection tripDirection, SignalExtractor extractor) {
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

  /**
   * Validates a nonblank identifier.
   *
   * @param value requested text
   * @param field diagnostic field name
   * @return trimmed text
   */
  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  /**
   * Validates a finite scalar.
   *
   * @param value requested scalar
   * @param field diagnostic field name
   * @return validated value
   */
  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }

  /**
   * Validates a nonnegative timer.
   *
   * @param value requested seconds
   * @param field diagnostic field name
   * @return validated value
   */
  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }
}
