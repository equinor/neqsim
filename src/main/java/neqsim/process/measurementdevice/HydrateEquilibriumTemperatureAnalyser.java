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
 * HydrateEquilibriumTemperatureAnalyser class.
 *
 * <p>
 * Concrete local instances participate in transient-step transactions. The snapshot restores the stream binding,
 * reference pressure and inherited measurement/alarm state. Descendants and online-signal operation remain fail-closed.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class HydrateEquilibriumTemperatureAnalyser extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<HydrateEquilibriumTemperatureAnalyser.HydrateAnalyserState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HydrateEquilibriumTemperatureAnalyser.class);

  private double referencePressure = 0;

  /**
   * Constructor for HydrateEquilibriumTemperatureAnalyser.
   *
   * @param name Name of HydrateEquilibriumTemperatureAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public HydrateEquilibriumTemperatureAnalyser(String name, StreamInterface stream) {
    super(name, "K", stream);
    setConditionAnalysisMaxDeviation(1.0);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    /*
     * try { // System.out.println("total water production [kg/dag]" + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles() * //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getMolarMass()*3600*24); //
     * System.out.println("water in phase 1 (ppm) " + //
     * stream.getThermoSystem().getPhase(0).getComponent("water").getx()*1e6); } finally { }
     */
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    SystemInterface tempFluid = stream.getThermoSystem().clone();
    if (!tempFluid.getHydrateCheck()) {
      tempFluid.setHydrateCheck(true);
    }
    tempFluid.setTemperature(10.0, "C");
    if (referencePressure > 1e-10) {
      tempFluid.setPressure(referencePressure);
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.hydrateFormationTemperature();
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

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:hydrate-equilibrium-temperature:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local analyser.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != HydrateEquilibriumTemperatureAnalyser.class) {
      return "hydrate-equilibrium-temperature-analyser subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public HydrateAnalyserState captureTransientState() {
    return new HydrateAnalyserState(getTransientStateIdentity(), stream, referencePressure,
        captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(HydrateAnalyserState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Hydrate analyser transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Hydrate analyser snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    referencePressure = snapshot.referencePressure;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable hydrate-equilibrium-temperature-analyser rollback point. */
  public static final class HydrateAnalyserState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface stream;
    private final double referencePressure;
    private final MeasurementDeviceTransientState measurementState;

    private HydrateAnalyserState(String stateIdentity, StreamInterface stream, double referencePressure,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.referencePressure = referencePressure;
      this.measurementState = measurementState;
    }
  }
}
