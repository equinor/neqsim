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

/**
 * TemperatureTransmitter class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TemperatureTransmitter extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<TemperatureTransmitter.TemperatureTransmitterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

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
        captureMeasurementDeviceTransientState());
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
  }

  /** Immutable temperature-transmitter rollback point. */
  public static final class TemperatureTransmitterState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;

    private TemperatureTransmitterState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
    }
  }
}
