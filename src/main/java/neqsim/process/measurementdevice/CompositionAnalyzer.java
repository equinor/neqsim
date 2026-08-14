package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Online composition analyzer (gas chromatograph surrogate). Reports the mole fraction of a specified component in a
 * chosen phase of the attached stream.
 *
 * <p>
 * The analyzer is read-only: it does not modify the stream and does not implement {@code applyFieldValue}. The
 * measurement unit is dimensionless ("mole/mole").
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompositionAnalyzer extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<CompositionAnalyzer.CompositionAnalyzerState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  /** Phase enumeration for the analyzer pickup point. */
  public enum AnalyzerPhase {
    /** Use the overall (total) feed composition. */
    OVERALL,
    /** First gas phase (phase index 0 when present). */
    GAS,
    /** First liquid phase (typically phase index 1 in a two-phase system). */
    LIQUID
  }

  private final String componentName;
  private final AnalyzerPhase analyzerPhase;

  /**
   * Constructor with default name derived from component.
   *
   * @param stream the stream to monitor
   * @param componentName the component to track
   * @param analyzerPhase the phase to sample
   */
  public CompositionAnalyzer(StreamInterface stream, String componentName, AnalyzerPhase analyzerPhase) {
    this("Composition Analyzer " + componentName, stream, componentName, analyzerPhase);
  }

  /**
   * Constructor.
   *
   * @param name device tag
   * @param stream the stream to monitor
   * @param componentName the component to track (must match a component in the stream)
   * @param analyzerPhase the phase to sample
   */
  public CompositionAnalyzer(String name, StreamInterface stream, String componentName, AnalyzerPhase analyzerPhase) {
    super(name, "mole/mole", stream);
    if (componentName == null || componentName.trim().isEmpty()) {
      throw new IllegalArgumentException("componentName must be non-blank");
    }
    if (analyzerPhase == null) {
      throw new IllegalArgumentException("analyzerPhase must be non-null");
    }
    this.componentName = componentName;
    this.analyzerPhase = analyzerPhase;
  }

  /**
   * Returns the tracked component name.
   *
   * @return component
   */
  public String getComponentName() {
    return componentName;
  }

  /**
   * Returns the analyzer pickup phase.
   *
   * @return phase
   */
  public AnalyzerPhase getAnalyzerPhase() {
    return analyzerPhase;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (stream == null || stream.getThermoSystem() == null) {
      return Double.NaN;
    }
    int idx = -1;
    for (int i = 0; i < stream.getThermoSystem().getNumberOfComponents(); i++) {
      if (stream.getThermoSystem().getComponent(i).getName().equals(componentName)) {
        idx = i;
        break;
      }
    }
    if (idx < 0) {
      return Double.NaN;
    }
    double value;
    switch (analyzerPhase) {
    case OVERALL:
      value = stream.getThermoSystem().getComponent(idx).getz();
      break;
    case GAS:
      if (stream.getThermoSystem().getNumberOfPhases() < 1) {
        return Double.NaN;
      }
      value = stream.getThermoSystem().getPhase(0).getComponent(idx).getx();
      break;
    case LIQUID:
      if (stream.getThermoSystem().getNumberOfPhases() < 2) {
        return Double.NaN;
      }
      value = stream.getThermoSystem().getPhase(1).getComponent(idx).getx();
      break;
    default:
      return Double.NaN;
    }
    return applySignalModifiers(value);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println(getName() + " " + componentName + " [" + analyzerPhase + "] = " + getMeasuredValue("mole/mole"));
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:composition:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local composition analyser.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != CompositionAnalyzer.class) {
      return "composition-analyser subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public CompositionAnalyzerState captureTransientState() {
    return new CompositionAnalyzerState(getTransientStateIdentity(), stream, captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(CompositionAnalyzerState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Composition analyser transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Composition analyser snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable composition-analyser rollback point. */
  public static final class CompositionAnalyzerState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;

    private CompositionAnalyzerState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
    }
  }
}
