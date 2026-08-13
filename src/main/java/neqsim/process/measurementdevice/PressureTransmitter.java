package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * PressureTransmitter class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PressureTransmitter extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<PressureTransmitter.PressureTransmitterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

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
    return new PressureTransmitterState(getTransientStateIdentity(), stream, captureMeasurementDeviceTransientState());
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
  }

  /** Immutable pressure-transmitter rollback point. */
  public static final class PressureTransmitterState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;

    private PressureTransmitterState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
    }
  }

}
