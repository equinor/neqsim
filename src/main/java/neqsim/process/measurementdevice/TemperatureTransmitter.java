/*
 * TemperatureTransmitter.java
 *
 * Created on 6. juni 2006, 15:24
 */

package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.unit.TemperatureUnit;

/**
 * TemperatureTransmitter class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TemperatureTransmitter extends StreamMeasurementDeviceBaseClass implements TransientMeasurementDevice,
    TransientStateParticipant<TemperatureTransmitter.TemperatureTransmitterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();
  /** Whether process transient execution samples and holds one value per physical step. */
  private boolean transientSamplingEnabled = false;
  /** Physical-step identity associated with the held sample. */
  private UUID lastTransientSampleIdentifier;
  /** Held signal value in {@link #transientSampleUnit}. */
  private double transientSampleValue = Double.NaN;
  /** Engineering unit used when the held sample was produced. */
  private String transientSampleUnit = "K";

  /**
   * Constructor for TemperatureTransmitter.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public TemperatureTransmitter(StreamInterface stream) {
    this("Temperature Transmitter", stream);
  }

  /**
   * Constructor for TemperatureTransmitter.
   *
   * @param name Name of TemperatureTransmitter
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public TemperatureTransmitter(String name, StreamInterface stream) {
    super(name, "K", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured temperature " + getMeasuredValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return applySignalModifiers(stream.getThermoSystem().getTemperature(unit));
  }

  /** {@inheritDoc} */
  @Override
  public void setTransientSamplingEnabled(boolean enabled) {
    transientSamplingEnabled = enabled;
    if (!enabled) {
      lastTransientSampleIdentifier = null;
      transientSampleValue = Double.NaN;
      transientSampleUnit = getUnit();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTransientSamplingEnabled() {
    return transientSamplingEnabled;
  }

  /** {@inheritDoc} */
  @Override
  public void sampleTransient(double dt, UUID physicalStepIdentifier) {
    if (!transientSamplingEnabled) {
      return;
    }
    if (physicalStepIdentifier == null) {
      throw new IllegalArgumentException("Temperature-transmitter physical-step identifier must not be null");
    }
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Temperature-transmitter sample interval must be finite and positive: " + dt);
    }
    if (physicalStepIdentifier.equals(lastTransientSampleIdentifier)) {
      return;
    }
    String sampleUnit = getUnit();
    double rawValue = stream.getThermoSystem().getTemperature(sampleUnit);
    transientSampleValue = applySignalModifiers(rawValue, dt);
    transientSampleUnit = sampleUnit;
    lastTransientSampleIdentifier = physicalStepIdentifier;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasTransientSample(UUID physicalStepIdentifier) {
    return transientSamplingEnabled && physicalStepIdentifier != null
        && physicalStepIdentifier.equals(lastTransientSampleIdentifier);
  }

  /** {@inheritDoc} */
  @Override
  public double getTransientSample(String unit) {
    if (lastTransientSampleIdentifier == null || !Double.isFinite(transientSampleValue)) {
      throw new IllegalStateException("Temperature transmitter has no held physical-step sample");
    }
    return new TemperatureUnit(transientSampleValue, transientSampleUnit).getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void applyFieldValue() {
    if (getTagRole() == InstrumentTagRole.INPUT && hasFieldValue()) {
      stream.setTemperature(getFieldValue(), getUnit());
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:temperature:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete temperature transmitter in local in-memory mode.
   *
   * @return blocking diagnostic for subclasses or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != TemperatureTransmitter.class) {
      return "temperature-transmitter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public TemperatureTransmitterState captureTransientState() {
    return new TemperatureTransmitterState(getTransientStateIdentity(), stream,
        captureMeasurementDeviceTransientState(), transientSamplingEnabled, lastTransientSampleIdentifier,
        transientSampleValue, transientSampleUnit);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(TemperatureTransmitterState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Temperature transmitter transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Temperature transmitter snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
    transientSamplingEnabled = snapshot.transientSamplingEnabled;
    lastTransientSampleIdentifier = snapshot.lastTransientSampleIdentifier;
    transientSampleValue = snapshot.transientSampleValue;
    transientSampleUnit = snapshot.transientSampleUnit;
  }

  /** Immutable temperature-transmitter rollback point. */
  public static final class TemperatureTransmitterState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;
    private final boolean transientSamplingEnabled;
    private final UUID lastTransientSampleIdentifier;
    private final double transientSampleValue;
    private final String transientSampleUnit;

    private TemperatureTransmitterState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState, boolean transientSamplingEnabled,
        UUID lastTransientSampleIdentifier, double transientSampleValue, String transientSampleUnit) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
      this.transientSamplingEnabled = transientSamplingEnabled;
      this.lastTransientSampleIdentifier = lastTransientSampleIdentifier;
      this.transientSampleValue = transientSampleValue;
      this.transientSampleUnit = transientSampleUnit;
    }
  }
}
