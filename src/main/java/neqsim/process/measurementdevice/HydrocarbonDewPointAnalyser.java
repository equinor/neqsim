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
 * HydrocarbonDewPointAnalyser class.
 *
 * <p>
 * Concrete local instances participate in transient-step transactions. The snapshot restores the stream binding,
 * reference pressure, method and inherited measurement/alarm state. Descendants and online-signal operation remain
 * fail-closed.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class HydrocarbonDewPointAnalyser extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<HydrocarbonDewPointAnalyser.HydrocarbonDewPointAnalyserState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(WaterDewPointAnalyser.class);

  private double referencePressure = 50.0;
  private String method = "EOS";

  /**
   * Constructor for WaterDewPointAnalyser.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public HydrocarbonDewPointAnalyser(StreamInterface stream) {
    this("HydrocarbonDewPointAnalyser", stream);
  }

  /**
   * Constructor for WaterDewPointAnalyser.
   *
   * @param name Name of WaterDewPointAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public HydrocarbonDewPointAnalyser(String name, StreamInterface stream) {
    super(name, "K", stream);
    setConditionAnalysisMaxDeviation(1.0);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    try {
      // System.out.println("total water production [kg/dag]" +
      // stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles() *
      // stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass()*3600*24);
      // System.out.println("water in phase 1 (ppm) " +
      // stream.getThermoSystem().getPhase(0).getComponent("water").getx()*1e6);
    } finally {
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    SystemInterface tempFluid = stream.getThermoSystem().clone();
    if (tempFluid.hasComponent("water")) {
      tempFluid.removeComponent("water");
    }
    tempFluid.setPressure(referencePressure);
    tempFluid.setTemperature(-10.0, "C");
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.dewPointTemperatureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    return tempFluid.getTemperature(unit);
  }

  /**
   * Getter for the field <code>referencePressure</code>.
   *
   * @return Reference pressure in bara
   */
  public double getReferencePressure() {
    return referencePressure;
  }

  /**
   * Setter for the field <code>referencePressure</code>.
   *
   * @param referencePressure Reference pressure to set in in bara
   */
  public void setReferencePressure(double referencePressure) {
    this.referencePressure = referencePressure;
  }

  /**
   * Getter for the field <code>method</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public String getMethod() {
    return method;
  }

  /**
   * Setter for the field <code>method</code>.
   *
   * @param method a {@link java.lang.String} object
   */
  public void setMethod(String method) {
    this.method = method;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:hydrocarbon-dew-point:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local analyser.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != HydrocarbonDewPointAnalyser.class) {
      return "hydrocarbon-dew-point-analyser subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public HydrocarbonDewPointAnalyserState captureTransientState() {
    return new HydrocarbonDewPointAnalyserState(getTransientStateIdentity(), stream, referencePressure, method,
        captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(HydrocarbonDewPointAnalyserState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Hydrocarbon-dew-point analyser transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Hydrocarbon-dew-point analyser snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    referencePressure = snapshot.referencePressure;
    method = snapshot.method;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable hydrocarbon-dew-point-analyser rollback point. */
  public static final class HydrocarbonDewPointAnalyserState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface stream;
    private final double referencePressure;
    private final String method;
    private final MeasurementDeviceTransientState measurementState;

    private HydrocarbonDewPointAnalyserState(String stateIdentity, StreamInterface stream, double referencePressure,
        String method, MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.referencePressure = referencePressure;
      this.method = method;
      this.measurementState = measurementState;
    }
  }
}
