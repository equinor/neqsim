package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * WaterContentAnalyser class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaterContentAnalyser extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<WaterContentAnalyser.WaterContentAnalyserState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  /**
   * Constructor for WaterContentAnalyser.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WaterContentAnalyser(StreamInterface stream) {
    this("water analyser", stream);
  }

  /**
   * Constructor for WaterContentAnalyser.
   *
   * @param name Name of WaterContentAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public WaterContentAnalyser(String name, StreamInterface stream) {
    super(name, "kg/day", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    try {
      System.out.println("total water production [kg/dag]"
          + stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()
              * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass() * 3600 * 24);
      System.out
          .println("water in phase 1 (ppm) " + stream.getThermoSystem().getPhase(0).getComponent("water").getx() * 1e6);
    } finally {
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    double raw = stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()
        * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass() * 3600 * 24;
    return applySignalModifiers(raw);
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:water-content:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local water-content analyser.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != WaterContentAnalyser.class) {
      return "water-content-analyser subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public WaterContentAnalyserState captureTransientState() {
    return new WaterContentAnalyserState(getTransientStateIdentity(), stream, captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(WaterContentAnalyserState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Water-content analyser transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Water-content analyser snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable water-content-analyser rollback point. */
  public static final class WaterContentAnalyserState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;

    private WaterContentAnalyserState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
    }
  }
}
