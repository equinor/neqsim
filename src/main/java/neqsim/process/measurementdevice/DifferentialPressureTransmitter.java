package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Differential-pressure transmitter that reports the pressure difference between two streams in bar. Useful for orifice
 * meters, filter ΔP monitoring, and across-equipment health checks. The device convention is ΔP = P(high) − P(low);
 * negative readings indicate reversed flow.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DifferentialPressureTransmitter extends MeasurementDeviceBaseClass
    implements TransientStateParticipant<DifferentialPressureTransmitter.DifferentialPressureTransmitterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  private StreamInterface highPressureStream;
  private StreamInterface lowPressureStream;

  /**
   * Constructor with default name "DP Transmitter".
   *
   * @param highPressureStream the upstream / high-pressure stream
   * @param lowPressureStream the downstream / low-pressure stream
   */
  public DifferentialPressureTransmitter(StreamInterface highPressureStream, StreamInterface lowPressureStream) {
    this("DP Transmitter", highPressureStream, lowPressureStream);
  }

  /**
   * Constructor.
   *
   * @param name device tag (non-null)
   * @param highPressureStream upstream stream
   * @param lowPressureStream downstream stream
   */
  public DifferentialPressureTransmitter(String name, StreamInterface highPressureStream,
      StreamInterface lowPressureStream) {
    super(name, "bar");
    if (highPressureStream == null || lowPressureStream == null) {
      throw new IllegalArgumentException("both streams must be non-null");
    }
    this.highPressureStream = highPressureStream;
    this.lowPressureStream = lowPressureStream;
  }

  /**
   * Returns the upstream (high) stream.
   *
   * @return stream
   */
  public StreamInterface getHighPressureStream() {
    return highPressureStream;
  }

  /**
   * Returns the downstream (low) stream.
   *
   * @return stream
   */
  public StreamInterface getLowPressureStream() {
    return lowPressureStream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    double pHigh = highPressureStream.getThermoSystem().getPressure(unit);
    double pLow = lowPressureStream.getThermoSystem().getPressure(unit);
    return applySignalModifiers(pHigh - pLow);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println(getName() + ": ΔP = " + getMeasuredValue("bar") + " bar");
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:differential-pressure:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete differential-pressure transmitter in local in-memory mode.
   *
   * @return blocking diagnostic for subclasses or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != DifferentialPressureTransmitter.class) {
      return "differential-pressure-transmitter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public DifferentialPressureTransmitterState captureTransientState() {
    return new DifferentialPressureTransmitterState(getTransientStateIdentity(), highPressureStream, lowPressureStream,
        captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(DifferentialPressureTransmitterState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Differential-pressure transmitter transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Differential-pressure transmitter snapshot identity does not match " + getTransientStateIdentity());
    }
    highPressureStream = snapshot.highPressureStream;
    lowPressureStream = snapshot.lowPressureStream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable differential-pressure-transmitter rollback point. */
  public static final class DifferentialPressureTransmitterState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final StreamInterface highPressureStream;
    private final StreamInterface lowPressureStream;
    private final MeasurementDeviceTransientState measurementState;

    private DifferentialPressureTransmitterState(String stateIdentity, StreamInterface highPressureStream,
        StreamInterface lowPressureStream, MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.highPressureStream = highPressureStream;
      this.lowPressureStream = lowPressureStream;
      this.measurementState = measurementState;
    }
  }
}
