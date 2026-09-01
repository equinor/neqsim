package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Flow-ratio meter that reports the ratio of two stream flows (mass or mole basis). Useful for fuel:air ratio control,
 * reflux ratio monitoring, recycle:fresh-feed ratio surveillance, and combustion-air trim.
 *
 * <p>
 * The measurement is dimensionless (numerator / denominator). When the denominator flow is zero or negative the reading
 * is reported as NaN (rather than +∞) to keep downstream controllers well-behaved.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class FlowRatioMeter extends MeasurementDeviceBaseClass
    implements TransientStateParticipant<FlowRatioMeter.FlowRatioMeterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  /** Basis for ratio computation. */
  public enum FlowBasis {
    /** Mass flow basis (kg/hr). */
    MASS,
    /** Molar flow basis (mole/sec). */
    MOLE,
    /** Volumetric flow basis (m3/hr). */
    VOLUME
  }

  private StreamInterface numeratorStream;
  private StreamInterface denominatorStream;
  private FlowBasis flowBasis;

  /**
   * Constructor with default name.
   *
   * @param numeratorStream stream whose flow forms the numerator
   * @param denominatorStream stream whose flow forms the denominator
   * @param flowBasis basis on which the ratio is computed
   */
  public FlowRatioMeter(StreamInterface numeratorStream, StreamInterface denominatorStream, FlowBasis flowBasis) {
    this("Flow Ratio Meter", numeratorStream, denominatorStream, flowBasis);
  }

  /**
   * Constructor.
   *
   * @param name device tag
   * @param numeratorStream non-null numerator stream
   * @param denominatorStream non-null denominator stream
   * @param flowBasis MASS, MOLE, or VOLUME (non-null)
   */
  public FlowRatioMeter(String name, StreamInterface numeratorStream, StreamInterface denominatorStream,
      FlowBasis flowBasis) {
    super(name, "");
    if (numeratorStream == null || denominatorStream == null) {
      throw new IllegalArgumentException("both streams must be non-null");
    }
    if (flowBasis == null) {
      throw new IllegalArgumentException("flowBasis must be non-null");
    }
    this.numeratorStream = numeratorStream;
    this.denominatorStream = denominatorStream;
    this.flowBasis = flowBasis;
  }

  /**
   * Returns the numerator stream.
   *
   * @return stream
   */
  public StreamInterface getNumeratorStream() {
    return numeratorStream;
  }

  /**
   * Returns the denominator stream.
   *
   * @return stream
   */
  public StreamInterface getDenominatorStream() {
    return denominatorStream;
  }

  /**
   * Returns the flow basis.
   *
   * @return basis
   */
  public FlowBasis getFlowBasis() {
    return flowBasis;
  }

  /**
   * Reads the flow of a stream on the configured basis.
   *
   * @param stream the stream to query
   * @return flow value
   */
  private double flowOf(StreamInterface stream) {
    switch (flowBasis) {
    case MASS:
      return stream.getFlowRate("kg/hr");
    case MOLE:
      return stream.getFlowRate("mole/sec");
    case VOLUME:
      return stream.getFlowRate("m3/hr");
    default:
      return Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    double num = flowOf(numeratorStream);
    double den = flowOf(denominatorStream);
    if (Double.isNaN(num) || Double.isNaN(den) || den <= 0.0) {
      return Double.NaN;
    }
    return applySignalModifiers(num / den);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println(getName() + " [" + flowBasis + "] ratio = " + getMeasuredValue(""));
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:flow-ratio:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local flow-ratio meter.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != FlowRatioMeter.class) {
      return "flow-ratio-meter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public FlowRatioMeterState captureTransientState() {
    return new FlowRatioMeterState(getTransientStateIdentity(), numeratorStream, denominatorStream, flowBasis,
        captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(FlowRatioMeterState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Flow-ratio meter transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Flow-ratio meter snapshot identity does not match " + getTransientStateIdentity());
    }
    numeratorStream = snapshot.numeratorStream;
    denominatorStream = snapshot.denominatorStream;
    flowBasis = snapshot.flowBasis;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable flow-ratio-meter rollback point. */
  public static final class FlowRatioMeterState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface numeratorStream;
    private final StreamInterface denominatorStream;
    private final FlowBasis flowBasis;
    private final MeasurementDeviceTransientState measurementState;

    private FlowRatioMeterState(String stateIdentity, StreamInterface numeratorStream,
        StreamInterface denominatorStream, FlowBasis flowBasis, MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.numeratorStream = numeratorStream;
      this.denominatorStream = denominatorStream;
      this.flowBasis = flowBasis;
      this.measurementState = measurementState;
    }
  }
}
