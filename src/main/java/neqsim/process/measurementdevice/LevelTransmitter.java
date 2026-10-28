package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.tank.Tank;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * LevelTransmitter class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class LevelTransmitter extends MeasurementDeviceBaseClass
    implements TransientMeasurementDevice, TransientStateParticipant<LevelTransmitter.LevelTransmitterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Class logger. */
  private static final Logger logger = LogManager.getLogger(LevelTransmitter.class);

  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();
  /** Whether process transient execution samples and holds one value per physical step. */
  private boolean transientSamplingEnabled = false;
  /** Physical-step identity associated with the held sample. */
  private UUID lastTransientSampleIdentifier;
  /** Held total-level fraction. */
  private double transientSampleValue = Double.NaN;

  /** Separator carrying the measured total-liquid level, when applicable. */
  protected Separator separator = null;
  /** Tank carrying the measured total-liquid level, when applicable. */
  protected Tank tank = null;

  /**
   * Constructor for LevelTransmitter.
   *
   * @param separator a {@link neqsim.process.equipment.separator.Separator} object
   */
  public LevelTransmitter(Separator separator) {
    this("LevelTransmitter", separator);
  }

  /**
   * Constructor for LevelTransmitter.
   *
   * @param name Name of LevelTransmitter
   * @param separator a {@link neqsim.process.equipment.separator.Separator} object
   */
  public LevelTransmitter(String name, Separator separator) {
    this(name, (ProcessEquipmentInterface) separator);
  }

  /**
   * Constructor for a tank level transmitter.
   *
   * @param tank tank whose liquid level is measured
   */
  public LevelTransmitter(Tank tank) {
    this("LevelTransmitter", tank);
  }

  /**
   * Constructor for a named tank level transmitter.
   *
   * @param name name of the level transmitter
   * @param tank tank whose liquid level is measured
   */
  public LevelTransmitter(String name, Tank tank) {
    this(name, (ProcessEquipmentInterface) tank);
  }

  /**
   * Constructor for a level transmitter backed by supported vessel equipment.
   *
   * @param vessel a separator or tank
   * @throws IllegalArgumentException when the equipment does not expose a supported vessel level
   */
  public LevelTransmitter(ProcessEquipmentInterface vessel) {
    this("LevelTransmitter", vessel);
  }

  /**
   * Constructor for a named level transmitter backed by supported vessel equipment.
   *
   * @param name name of the level transmitter
   * @param vessel a separator or tank
   * @throws IllegalArgumentException when the equipment does not expose a supported vessel level
   */
  public LevelTransmitter(String name, ProcessEquipmentInterface vessel) {
    super(name, "");
    this.setMaximumValue(1);
    this.setMinimumValue(0);
    if (vessel instanceof Separator) {
      separator = (Separator) vessel;
    } else if (vessel instanceof Tank) {
      tank = (Tank) vessel;
    } else {
      throw new IllegalArgumentException("LevelTransmitter requires a Separator or Tank");
    }
  }

  /**
   * Returns the separator whose liquid level this transmitter measures.
   *
   * @return the associated {@link neqsim.process.equipment.separator.Separator}, or {@code null} if none was set
   */
  public Separator getSeparator() {
    return separator;
  }

  /**
   * Returns the tank whose liquid level this transmitter measures.
   *
   * @return associated tank, or {@code null} when this transmitter measures a separator
   */
  public Tank getTank() {
    return tank;
  }

  /**
   * Returns the vessel carrying the measured liquid inventory.
   *
   * @return associated separator or tank, or {@code null} if no source was configured
   */
  public ProcessEquipmentInterface getLevelEquipment() {
    return separator != null ? separator : tank;
  }

  /**
   * Returns the vessel carrying the measured liquid inventory.
   *
   * @return associated separator or tank
   */
  public ProcessEquipmentInterface getVessel() {
    return getLevelEquipment();
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    logger.info("measured level {}", measuredLevel());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getMeasuredValue", "unit",
          "currently only supports \"\""));
    }
    return applySignalModifiers(measuredLevel());
  }

  /** {@inheritDoc} */
  @Override
  public void setTransientSamplingEnabled(boolean enabled) {
    transientSamplingEnabled = enabled;
    if (!enabled) {
      lastTransientSampleIdentifier = null;
      transientSampleValue = Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTransientSamplingEnabled() {
    return transientSamplingEnabled;
  }

  /** {@inheritDoc} */
  @Override
  public void sampleTransient(double dt, UUID physicalStepIdentifier) {
    if (!transientSamplingEnabled) {
      return;
    }
    if (physicalStepIdentifier == null) {
      throw new IllegalArgumentException("Level-transmitter physical-step identifier must not be null");
    }
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Level-transmitter sample interval must be finite and positive: " + dt);
    }
    if (physicalStepIdentifier.equals(lastTransientSampleIdentifier)) {
      return;
    }
    transientSampleValue = applySignalModifiers(measuredLevel(), dt);
    lastTransientSampleIdentifier = physicalStepIdentifier;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasTransientSample(UUID physicalStepIdentifier) {
    return transientSamplingEnabled && physicalStepIdentifier != null
        && physicalStepIdentifier.equals(lastTransientSampleIdentifier);
  }

  /** {@inheritDoc} */
  @Override
  public double getTransientSample(String unit) {
    if (!unit.equalsIgnoreCase("")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "getTransientSample", "unit",
          "currently only supports \"\""));
    }
    if (lastTransientSampleIdentifier == null || !Double.isFinite(transientSampleValue)) {
      throw new IllegalStateException("Level transmitter has no held physical-step sample");
    }
    return transientSampleValue;
  }

  /**
   * Reads the authoritative total-liquid fraction from the configured vessel.
   *
   * @return unitless total-liquid fraction
   */
  private double measuredLevel() {
    if (separator != null) {
      return separator.getLiquidLevel();
    }
    if (tank != null) {
      return tank.getLiquidLevel();
    }
    throw new IllegalStateException("Level transmitter has no separator or tank source");
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:level:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete level transmitter in local in-memory mode.
   *
   * @return blocking diagnostic for subclasses, invalid vessel wiring or external online-signal operation, otherwise
   * {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != LevelTransmitter.class) {
      return "level-transmitter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    if ((separator == null) == (tank == null)) {
      return "level transmitter must reference exactly one Separator or Tank";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public LevelTransmitterState captureTransientState() {
    String coverageIssue = getTransientStateCoverageIssue();
    if (coverageIssue != null) {
      throw new IllegalStateException("Cannot capture level transmitter '" + getName() + "': " + coverageIssue);
    }
    return new LevelTransmitterState(getTransientStateIdentity(), separator, tank,
        captureMeasurementDeviceTransientState(), transientSamplingEnabled, lastTransientSampleIdentifier,
        transientSampleValue);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(LevelTransmitterState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Level transmitter transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Level transmitter snapshot identity does not match " + getTransientStateIdentity());
    }
    separator = snapshot.separator;
    tank = snapshot.tank;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
    transientSamplingEnabled = snapshot.transientSamplingEnabled;
    lastTransientSampleIdentifier = snapshot.lastTransientSampleIdentifier;
    transientSampleValue = snapshot.transientSampleValue;
  }

  /** Immutable level-transmitter rollback point. */
  public static final class LevelTransmitterState implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final Separator separator;
    private final Tank tank;
    private final MeasurementDeviceTransientState measurementState;
    private final boolean transientSamplingEnabled;
    private final UUID lastTransientSampleIdentifier;
    private final double transientSampleValue;

    /**
     * Creates an immutable rollback snapshot.
     *
     * @param stateIdentity stable participant identity
     * @param separator associated separator, or {@code null}
     * @param tank associated tank, or {@code null}
     * @param measurementState base measurement-device state
     * @param transientSamplingEnabled whether physical-step sampling is enabled
     * @param lastTransientSampleIdentifier held-sample physical-step identity
     * @param transientSampleValue held total-level fraction
     */
    private LevelTransmitterState(String stateIdentity, Separator separator, Tank tank,
        MeasurementDeviceTransientState measurementState, boolean transientSamplingEnabled,
        UUID lastTransientSampleIdentifier, double transientSampleValue) {
      this.stateIdentity = stateIdentity;
      this.separator = separator;
      this.tank = tank;
      this.measurementState = measurementState;
      this.transientSamplingEnabled = transientSamplingEnabled;
      this.lastTransientSampleIdentifier = lastTransientSampleIdentifier;
      this.transientSampleValue = transientSampleValue;
    }
  }
}
