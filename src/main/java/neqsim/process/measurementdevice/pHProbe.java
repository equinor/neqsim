package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * pHProbe class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class pHProbe extends StreamMeasurementDeviceBaseClass
    implements TransientStateParticipant<pHProbe.PHProbeState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for this class. */
  private static final Logger logger = LogManager.getLogger(pHProbe.class);
  /** Bounded device-level refinements used to certify the final aqueous reaction state. */
  private static final int MAXIMUM_REACTION_REFINEMENTS = 8;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  protected SystemInterface reactiveThermoSystem;
  protected ThermodynamicOperations thermoOps;

  private double alkalinity = 0.0;

  private transient StreamInterface lastMeasuredStream;
  private double lastMeasuredAlkalinity = Double.NaN;
  private double lastMeasuredPH = Double.NaN;
  private boolean hasCachedPH = false;

  /**
   * Constructor for pHProbe.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public pHProbe(StreamInterface stream) {
    this("phProbe", stream);
  }

  /**
   * Constructor for pHProbe.
   *
   * @param name Name of pHProbe
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public pHProbe(String name, StreamInterface stream) {
    super(name, "", stream);
  }

  /**
   * run.
   */
  public void run() {
    hasCachedPH = false;
    if (stream != null && stream.getFluid().hasPhaseType("aqueous")) {
      reactiveThermoSystem = stream.getFluid().clone();
      reactiveThermoSystem = reactiveThermoSystem.phaseToSystem("aqueous");
      reactiveThermoSystem = reactiveThermoSystem.setModel("Electrolyte-CPA-EOS-statoil");
      reactiveThermoSystem.setNumberOfPhases(1);
      reactiveThermoSystem.setPhaseType(0, neqsim.thermo.phase.PhaseType.AQUEOUS);
      if (getAlkalinity() > 1e-10) {
        double waterkg = reactiveThermoSystem.getComponent("water").getTotalFlowRate("kg/sec");
        reactiveThermoSystem.addComponent("Na+", waterkg * getAlkalinity() / 1e3);
        reactiveThermoSystem.addComponent("OH-", waterkg * getAlkalinity() / 1e3);
      }
      reactiveThermoSystem.chemicalReactionInit();
      reactiveThermoSystem.setMixingRule(10);
      reactiveThermoSystem.setMultiPhaseCheck(false);
      thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
      thermoOps.TPflash();
      boolean reactionConverged = false;
      for (int refinement = 0; refinement < MAXIMUM_REACTION_REFINEMENTS && !reactionConverged; refinement++) {
        reactionConverged = reactiveThermoSystem.getChemicalReactionOperations().solveChemEq(1);
      }
      if (!reactionConverged) {
        throw new IllegalStateException("pH calculation did not close chemical reactions and electroneutrality");
      }

      lastMeasuredPH = reactiveThermoSystem.getPhase("aqueous").getpH();
      lastMeasuredStream = stream;
      lastMeasuredAlkalinity = alkalinity;
      hasCachedPH = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(
          new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit", "can only be empty."));
    }
    if (stream != null) {
      if (stream.getFluid().hasPhaseType("aqueous")) {
        if (hasCachedPH && stream == lastMeasuredStream && Double.compare(lastMeasuredAlkalinity, alkalinity) == 0) {
          return lastMeasuredPH;
        }

        run();
        return hasCachedPH ? lastMeasuredPH : Double.NaN;
      } else {
        logger.warn("No aqueous phase is available for pH analyser '{}'", getName());
        return 7.0;
      }
    } else {
      logger.warn("No stream is connected to pH analyser '{}'", getName());
    }
    return Double.NaN;
  }

  /**
   * Getter for the field <code>alkalinity</code>.
   *
   * @return the alkalinity
   */
  public double getAlkalinity() {
    return alkalinity;
  }

  /**
   * Setter for the field <code>alkalinity</code>.
   *
   * @param alkalinity the alkalinity to set
   */
  public void setAlkalinity(double alkalinity) {
    this.alkalinity = alkalinity;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:ph-probe:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete local pH probe.
   *
   * @return blocking diagnostic for descendants or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != pHProbe.class) {
      return "pH-probe subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public PHProbeState captureTransientState() {
    return new PHProbeState(getTransientStateIdentity(), stream, reactiveThermoSystem, thermoOps, alkalinity,
        lastMeasuredStream, lastMeasuredAlkalinity, lastMeasuredPH, hasCachedPH,
        captureMeasurementDeviceTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(PHProbeState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("pH-probe transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException("pH-probe snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    reactiveThermoSystem = snapshot.reactiveThermoSystem;
    thermoOps = snapshot.thermoOps;
    alkalinity = snapshot.alkalinity;
    lastMeasuredStream = snapshot.lastMeasuredStream;
    lastMeasuredAlkalinity = snapshot.lastMeasuredAlkalinity;
    lastMeasuredPH = snapshot.lastMeasuredPH;
    hasCachedPH = snapshot.hasCachedPH;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
  }

  /** Immutable pH-probe rollback point. */
  public static final class PHProbeState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String stateIdentity;
    private final StreamInterface stream;
    private final SystemInterface reactiveThermoSystem;
    private final ThermodynamicOperations thermoOps;
    private final double alkalinity;
    private final StreamInterface lastMeasuredStream;
    private final double lastMeasuredAlkalinity;
    private final double lastMeasuredPH;
    private final boolean hasCachedPH;
    private final MeasurementDeviceTransientState measurementState;

    private PHProbeState(String stateIdentity, StreamInterface stream, SystemInterface reactiveThermoSystem,
        ThermodynamicOperations thermoOps, double alkalinity, StreamInterface lastMeasuredStream,
        double lastMeasuredAlkalinity, double lastMeasuredPH, boolean hasCachedPH,
        MeasurementDeviceTransientState measurementState) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.reactiveThermoSystem = reactiveThermoSystem;
      this.thermoOps = thermoOps;
      this.alkalinity = alkalinity;
      this.lastMeasuredStream = lastMeasuredStream;
      this.lastMeasuredAlkalinity = lastMeasuredAlkalinity;
      this.lastMeasuredPH = lastMeasuredPH;
      this.hasCachedPH = hasCachedPH;
      this.measurementState = measurementState;
    }
  }
}
