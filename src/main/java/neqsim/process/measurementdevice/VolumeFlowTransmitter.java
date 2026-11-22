package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * VolumeFlowTransmitter class.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class VolumeFlowTransmitter extends StreamMeasurementDeviceBaseClass
    implements TransientMeasurementDevice, TransientStateParticipant<VolumeFlowTransmitter.VolumeFlowTransmitterState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();
  /** Whether process transient execution samples and holds one value per physical step. */
  private boolean transientSamplingEnabled = false;
  /** Physical-step identity associated with the held sample. */
  private UUID lastTransientSampleIdentifier;
  /** Held signal value in {@link #transientSampleUnit}. */
  private double transientSampleValue = Double.NaN;
  /** Engineering unit used when the held sample was produced. */
  private String transientSampleUnit = "kg/hr";
  /** Held mass-flow equivalent in kg/hr. */
  private double transientSampleKgPerHour = Double.NaN;
  /** Held actual phase-volume equivalent in m3/hr. */
  private double transientSampleCubicMetresPerHour = Double.NaN;
  /** Held normal-volume equivalent in Nm3/day. */
  private double transientSampleNormalCubicMetresPerDay = Double.NaN;
  /** Held standard-volume equivalent in Sm3/day. */
  private double transientSampleStandardCubicMetresPerDay = Double.NaN;
  /** Phase index used for phase-specific volume-flow readings. */
  private int measuredPhaseNumber = 0;

  /**
   * Constructor for VolumeFlowTransmitter.
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public VolumeFlowTransmitter(StreamInterface stream) {
    this("volume flow rate", stream);
  }

  /**
   * Constructor for VolumeFlowTransmitter.
   *
   * @param name Name of VolumeFlowTransmitter
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public VolumeFlowTransmitter(String name, StreamInterface stream) {
    super(name, "m^3/hr", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured volume " + Double.toString(getMeasuredValue()) + " " + unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    stream.getThermoSystem().initPhysicalProperties();
    return getRawMeasuredValue(unit);
  }

  /**
   * Reads an unmodified flow signal after physical properties have been initialized.
   *
   * @param requestedUnit requested engineering unit
   * @return raw flow signal
   */
  private double getRawMeasuredValue(String requestedUnit) {
    if (requestedUnit.equals("kg/hr")) {
      return stream.getFlowRate(requestedUnit);
    } else if (requestedUnit.equals("m^3/hr")) {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * stream.getThermoSystem().getPhase(measuredPhaseNumber).getMolarMass()
          / stream.getThermoSystem().getPhase(measuredPhaseNumber).getPhysicalProperties().getDensity() * 3600.0;
    } else if (requestedUnit.equals("Nm^3/day")) {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * neqsim.thermo.ThermodynamicConstantsInterface.R
          * neqsim.thermo.ThermodynamicConstantsInterface.normalStateTemperature / ThermodynamicConstantsInterface.atm
          * 3600.0 * 24;
    } else if (requestedUnit.equals("Sm^3/day")) {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * neqsim.thermo.ThermodynamicConstantsInterface.R
          * neqsim.thermo.ThermodynamicConstantsInterface.standardStateTemperature / ThermodynamicConstantsInterface.atm
          * 3600.0 * 24;
    } else {
      return stream.getThermoSystem().getPhase(measuredPhaseNumber).getNumberOfMolesInPhase()
          * stream.getThermoSystem().getPhase(measuredPhaseNumber).getMolarMass()
          / stream.getThermoSystem().getPhase(measuredPhaseNumber).getPhysicalProperties().getDensity() * 3600.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setTransientSamplingEnabled(boolean enabled) {
    transientSamplingEnabled = enabled;
    if (!enabled) {
      clearTransientSample();
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
      throw new IllegalArgumentException("Flow-transmitter physical-step identifier must not be null");
    }
    if (!Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("Flow-transmitter sample interval must be finite and positive: " + dt);
    }
    if (physicalStepIdentifier.equals(lastTransientSampleIdentifier)) {
      return;
    }
    stream.getThermoSystem().initPhysicalProperties();
    String sampleUnit = getUnit();
    double rawValue = getRawMeasuredValue(sampleUnit);
    double modifiedValue = applySignalModifiers(rawValue, dt);
    double scale = rawValue == 0.0 ? 1.0 : modifiedValue / rawValue;
    transientSampleValue = modifiedValue;
    transientSampleUnit = sampleUnit;
    transientSampleKgPerHour = scaledRawValue("kg/hr", scale, rawValue, modifiedValue, sampleUnit);
    transientSampleCubicMetresPerHour = scaledRawValue("m^3/hr", scale, rawValue, modifiedValue, sampleUnit);
    transientSampleNormalCubicMetresPerDay = scaledRawValue("Nm^3/day", scale, rawValue, modifiedValue, sampleUnit);
    transientSampleStandardCubicMetresPerDay = scaledRawValue("Sm^3/day", scale, rawValue, modifiedValue, sampleUnit);
    lastTransientSampleIdentifier = physicalStepIdentifier;
  }

  /**
   * Converts the configured held signal using flow-unit ratios frozen at the sample instant.
   *
   * @param requestedUnit unit whose raw sample is required
   * @param scale ratio of modified to raw configured signal
   * @param rawConfiguredValue raw signal in the configured unit
   * @param modifiedConfiguredValue modified signal in the configured unit
   * @param configuredUnit configured transmitter unit
   * @return held value in the requested unit
   */
  private double scaledRawValue(String requestedUnit, double scale, double rawConfiguredValue,
      double modifiedConfiguredValue, String configuredUnit) {
    if (requestedUnit.equals(configuredUnit)) {
      return modifiedConfiguredValue;
    }
    if (rawConfiguredValue == 0.0 && modifiedConfiguredValue != 0.0) {
      return Double.NaN;
    }
    return getRawMeasuredValue(requestedUnit) * scale;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasTransientSample(UUID physicalStepIdentifier) {
    return transientSamplingEnabled && physicalStepIdentifier != null
        && physicalStepIdentifier.equals(lastTransientSampleIdentifier);
  }

  /** {@inheritDoc} */
  @Override
  public double getTransientSample(String requestedUnit) {
    if (lastTransientSampleIdentifier == null || !Double.isFinite(transientSampleValue)) {
      throw new IllegalStateException("Flow transmitter has no held physical-step sample");
    }
    double value;
    if (requestedUnit.equals(transientSampleUnit)) {
      value = transientSampleValue;
    } else if (requestedUnit.equals("kg/hr")) {
      value = transientSampleKgPerHour;
    } else if (requestedUnit.equals("m^3/hr")) {
      value = transientSampleCubicMetresPerHour;
    } else if (requestedUnit.equals("Nm^3/day")) {
      value = transientSampleNormalCubicMetresPerDay;
    } else if (requestedUnit.equals("Sm^3/day")) {
      value = transientSampleStandardCubicMetresPerDay;
    } else {
      throw new IllegalArgumentException("Unsupported held flow-sample unit: " + requestedUnit);
    }
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(
          "A non-zero configured flow signal cannot be converted from a zero raw sample to " + requestedUnit);
    }
    return value;
  }

  /** Clears held physical-step sample state. */
  private void clearTransientSample() {
    lastTransientSampleIdentifier = null;
    transientSampleValue = Double.NaN;
    transientSampleUnit = getUnit();
    transientSampleKgPerHour = Double.NaN;
    transientSampleCubicMetresPerHour = Double.NaN;
    transientSampleNormalCubicMetresPerDay = Double.NaN;
    transientSampleStandardCubicMetresPerDay = Double.NaN;
  }

  /** {@inheritDoc} */
  @Override
  public void applyFieldValue() {
    if (getTagRole() == InstrumentTagRole.INPUT && hasFieldValue()) {
      stream.setFlowRate(getFieldValue(), getUnit());
    }
  }

  /**
   * Getter for the field <code>measuredPhaseNumber</code>.
   *
   * @return a int
   */
  public int getMeasuredPhaseNumber() {
    return measuredPhaseNumber;
  }

  /**
   * Setter for the field <code>measuredPhaseNumber</code>.
   *
   * @param measuredPhase a int
   */
  public void setMeasuredPhaseNumber(int measuredPhase) {
    this.measuredPhaseNumber = measuredPhase;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "measurement:volume-flow:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for the concrete flow transmitter in local in-memory mode.
   *
   * @return blocking diagnostic for subclasses or external online-signal operation, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != VolumeFlowTransmitter.class) {
      return "volume-flow-transmitter subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return getMeasurementTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public VolumeFlowTransmitterState captureTransientState() {
    return new VolumeFlowTransmitterState(getTransientStateIdentity(), stream, captureMeasurementDeviceTransientState(),
        measuredPhaseNumber, transientSamplingEnabled, lastTransientSampleIdentifier, transientSampleValue,
        transientSampleUnit, transientSampleKgPerHour, transientSampleCubicMetresPerHour,
        transientSampleNormalCubicMetresPerDay, transientSampleStandardCubicMetresPerDay);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(VolumeFlowTransmitterState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Flow transmitter transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Flow transmitter snapshot identity does not match " + getTransientStateIdentity());
    }
    stream = snapshot.stream;
    restoreMeasurementDeviceTransientState(snapshot.measurementState);
    measuredPhaseNumber = snapshot.measuredPhaseNumber;
    transientSamplingEnabled = snapshot.transientSamplingEnabled;
    lastTransientSampleIdentifier = snapshot.lastTransientSampleIdentifier;
    transientSampleValue = snapshot.transientSampleValue;
    transientSampleUnit = snapshot.transientSampleUnit;
    transientSampleKgPerHour = snapshot.transientSampleKgPerHour;
    transientSampleCubicMetresPerHour = snapshot.transientSampleCubicMetresPerHour;
    transientSampleNormalCubicMetresPerDay = snapshot.transientSampleNormalCubicMetresPerDay;
    transientSampleStandardCubicMetresPerDay = snapshot.transientSampleStandardCubicMetresPerDay;
  }

  /** Immutable volume-flow-transmitter rollback point. */
  public static final class VolumeFlowTransmitterState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final StreamInterface stream;
    private final MeasurementDeviceTransientState measurementState;
    private final int measuredPhaseNumber;
    private final boolean transientSamplingEnabled;
    private final UUID lastTransientSampleIdentifier;
    private final double transientSampleValue;
    private final String transientSampleUnit;
    private final double transientSampleKgPerHour;
    private final double transientSampleCubicMetresPerHour;
    private final double transientSampleNormalCubicMetresPerDay;
    private final double transientSampleStandardCubicMetresPerDay;

    /**
     * Creates an immutable rollback snapshot.
     *
     * @param stateIdentity stable participant identity
     * @param stream associated process stream
     * @param measurementState base measurement-device state
     * @param measuredPhaseNumber sampled phase index
     * @param transientSamplingEnabled whether physical-step sampling is enabled
     * @param lastTransientSampleIdentifier held-sample physical-step identity
     * @param transientSampleValue held sample in the configured unit
     * @param transientSampleUnit configured unit at the sample instant
     * @param transientSampleKgPerHour held mass-flow equivalent
     * @param transientSampleCubicMetresPerHour held actual-volume equivalent
     * @param transientSampleNormalCubicMetresPerDay held normal-volume equivalent
     * @param transientSampleStandardCubicMetresPerDay held standard-volume equivalent
     */
    private VolumeFlowTransmitterState(String stateIdentity, StreamInterface stream,
        MeasurementDeviceTransientState measurementState, int measuredPhaseNumber, boolean transientSamplingEnabled,
        UUID lastTransientSampleIdentifier, double transientSampleValue, String transientSampleUnit,
        double transientSampleKgPerHour, double transientSampleCubicMetresPerHour,
        double transientSampleNormalCubicMetresPerDay, double transientSampleStandardCubicMetresPerDay) {
      this.stateIdentity = stateIdentity;
      this.stream = stream;
      this.measurementState = measurementState;
      this.measuredPhaseNumber = measuredPhaseNumber;
      this.transientSamplingEnabled = transientSamplingEnabled;
      this.lastTransientSampleIdentifier = lastTransientSampleIdentifier;
      this.transientSampleValue = transientSampleValue;
      this.transientSampleUnit = transientSampleUnit;
      this.transientSampleKgPerHour = transientSampleKgPerHour;
      this.transientSampleCubicMetresPerHour = transientSampleCubicMetresPerHour;
      this.transientSampleNormalCubicMetresPerDay = transientSampleNormalCubicMetresPerDay;
      this.transientSampleStandardCubicMetresPerDay = transientSampleStandardCubicMetresPerDay;
    }
  }
}
