package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.unit.PressureUnit;

/**
 * PressureTransmitter class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PressureTransmitter extends StreamMeasurementDeviceBaseClass
    implements TransientMeasurementDevice, TransientStateParticipant<PressureTransmitter.PressureTransmitterState> {
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
  private String transientSampleUnit = "bar";

  /**
   * Constructor for PressureTransmitter.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public PressureTransmitter(StreamInterface stream) {
    this("Pressure Transmitter", stream);
  }

  /**
   * Constructor for PressureTransmitter.
   *
   * @param name Name of PressureTransmitter
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public PressureTransmitter(String name, StreamInterface stream) {
    super(name, "bar", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured temperature " + stream.getPressure());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return applySignalModifiers(stream.getThermoSystem().getPressure(unit));
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
      throw new IllegalArgumentException("Pressure-transmitter physical-step identifier must not be null");
    }
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Pressure-transmitter sample interval must be finite and positive: " + dt);
    }
    if (physicalStepIdentifier.equals(lastTransientSampleIdentifier)) {
      return;
    }
    String sampleUnit = getUnit();
    double rawValue = stream.getThermoSystem().getPressure(sampleUnit);
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
      throw new IllegalStateException("Pressure transmitter has no held physical-step sample");
    }
    return new PressureUnit(transientSampleValue, transientSampleUnit).getValue(unit);
  }

  /** {@inheritDoc} */
  @Override
  public void applyFieldValue() {
    if (getTagRole() == InstrumentTagRole.INPUT && hasFieldValue()) {
      stream.setPressure(getFieldValue(), getUnit());
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:pressure:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete pressure transmitter in local in-memory mode.
   *
   * @return blocking diagnostic for subclasses or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != PressureTransmitter.class) {
      return "pressure-transmitter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public PressureTransmitterState captureTransientState() {
    return new PressureTransmitterState(getTransientStateIdentity(), stream, captureMeasurementDeviceTransientState(),
        transientSamplingEnabled, lastTransientSampleIdentifier, transientSampleValue, transientSampleUnit);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(PressureTransmitterState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Pressure transmitter transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Pressure transmitter snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
    transientSamplingEnabled = snapshot.transientSamplingEnabled;
    lastTransientSampleIdentifier = snapshot.lastTransientSampleIdentifier;
    transientSampleValue = snapshot.transientSampleValue;
    transientSampleUnit = snapshot.transientSampleUnit;
  }

  /** Immutable pressure-transmitter rollback point. */
  public static final class PressureTransmitterState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;
    private final boolean transientSamplingEnabled;
    private final UUID lastTransientSampleIdentifier;
    private final double transientSampleValue;
    private final String transientSampleUnit;

    /**
     * Creates an immutable rollback snapshot.
     *
     * @param stateIdentity stable participant identity
     * @param stream associated process stream
     * @param measurementState base measurement-device state
     * @param transientSamplingEnabled whether physical-step sampling is enabled
     * @param lastTransientSampleIdentifier held-sample physical-step identity
     * @param transientSampleValue held sample value
     * @param transientSampleUnit held sample engineering unit
     */
    private PressureTransmitterState(String stateIdentity, StreamInterface stream,
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
