package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * MolarMassAnalyser class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class MolarMassAnalyser extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<MolarMassAnalyser.MolarMassAnalyserState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  /**
   * Constructor for MolarMassAnalyser.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public MolarMassAnalyser(StreamInterface stream) {
    this("molar mass analyser", stream);
  }

  /**
   * Constructor for MolarMassAnalyser.
   *
   * @param name Name of MolarMassAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public MolarMassAnalyser(String name, StreamInterface stream) {
    super(name, "gr/mol", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured Molar mass " + getMeasuredValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("gr/mol")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit",
          "currently only supports \"gr/mol\""));
    }
    return applySignalModifiers(stream.getThermoSystem().getMolarMass() * 1000.0);
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:molar-mass:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local molar-mass analyser.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != MolarMassAnalyser.class) {
      return "molar-mass-analyser subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public MolarMassAnalyserState captureTransientState() {
    return new MolarMassAnalyserState(getTransientStateIdentity(), stream, captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(MolarMassAnalyserState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Molar-mass analyser transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Molar-mass analyser snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable molar-mass-analyser rollback point. */
  public static final class MolarMassAnalyserState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;

    private MolarMassAnalyserState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
    }
  }
}
