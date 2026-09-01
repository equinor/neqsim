package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * CricondenbarAnalyser class.
 *
 * <p>
 * Concrete local instances participate in transient-step transactions. The snapshot restores the stream binding and
 * inherited measurement/alarm state. Descendants and online-signal operation remain fail-closed.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class CricondenbarAnalyser extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<CricondenbarAnalyser.CricondenbarAnalyserState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CricondenbarAnalyser.class);

  /**
   * Constructor for CricondenbarAnalyser.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public CricondenbarAnalyser(StreamInterface stream) {
    this("CricondenbarAnalyser", stream);
  }

  /**
   * Constructor for CricondenbarAnalyser.
   *
   * @param name Name of CricondenbarAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public CricondenbarAnalyser(String name, StreamInterface stream) {
    super(name, "K", stream);
    setConditionAnalysisMaxDeviation(1.0);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    /*
     * try { // System.out.println("total water production [kg/dag]" + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles()*stream.
     * getThermoSystem().getPhase(0).getComponent("water").getMolarMass()*3600*24); //
     * System.out.println("water in phase 1 (ppm) " + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getx()*1e6); } finally { }
     */
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    SystemInterface tempFluid = stream.getThermoSystem().clone();
    tempFluid.removeComponent("water");
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.setRunAsThread(true);
      thermoOps.calcPTphaseEnvelope(false, 1.);
      thermoOps.waitAndCheckForFinishedCalculation(15000);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return thermoOps.get("cricondenbar")[1];
  }

  /**
   * getMeasuredValue2.
   *
   * @param unit a {@link java.lang.String} object
   * @param temp a double
   * @return a double
   */
  public double getMeasuredValue2(String unit, double temp) {
    SystemInterface tempFluid = stream.getThermoSystem().clone();
    tempFluid.setTemperature(temp, "C");
    tempFluid.setPressure(10.0, "bara");
    if (tempFluid.getPhase(0).hasComponent("water")) {
      tempFluid.removeComponent("water");
    }
    neqsim.pvtsimulation.simulation.SaturationPressure thermoOps = new neqsim.pvtsimulation.simulation.SaturationPressure(
        tempFluid);
    try {
      thermoOps.run();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return thermoOps.getSaturationPressure();
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:cricondenbar:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local analyser.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != CricondenbarAnalyser.class) {
      return "cricondenbar-analyser subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public CricondenbarAnalyserState captureTransientState() {
    return new CricondenbarAnalyserState(getTransientStateIdentity(), stream, captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(CricondenbarAnalyserState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Cricondenbar analyser transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Cricondenbar analyser snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable cricondenbar-analyser rollback point. */
  public static final class CricondenbarAnalyserState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;

    private CricondenbarAnalyserState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
    }
  }
}
