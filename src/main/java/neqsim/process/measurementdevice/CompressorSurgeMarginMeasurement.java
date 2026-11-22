package neqsim.process.measurementdevice;

import java.io.Serializable;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.equipment.compressor.Compressor;

/**
 * Holds an explicitly calculated compressor-map margin signal for one physical step.
 *
 * <p>
 * This is a calculated simulation variable, not a physical flow, pressure or temperature transmitter. Its native source
 * consumes the actual held inlet volume-flow transmitter and the native active surge-curve flow at the declared model
 * head and speed. Pressure and temperature monitoring channels do not acquire causal fault coverage merely by being
 * drawn beside it. The consumed FT, head, speed and map-domain evidence remain frozen with each held sample.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class CompressorSurgeMarginMeasurement extends MeasurementDeviceBaseClass
    implements TransientMeasurementDevice, TransientStateParticipant<CompressorSurgeMarginMeasurement.MarginState> {
  private static final long serialVersionUID = 1000L;
  private final Compressor compressor;
  private final VolumeFlowTransmitter flowTransmitter;
  private String stateIdentity = UUID.randomUUID().toString();
  private boolean samplingEnabled;
  private UUID sampleIdentifier;
  private double sampleValue = Double.NaN;
  private Map<String, Object> lastReferenceEvidence = new LinkedHashMap<String, Object>();

  /**
   * Creates a source-backed calculated signal without inventing sensing hardware.
   *
   * @param name calculated signal tag
   * @param compressor actual source compressor
   * @param flowTransmitter actual held inlet volumetric-flow transmitter
   * @throws IllegalArgumentException if the source is null
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "The calculated signal must retain its actual native compressor and held inlet-flow measurement identities")
  public CompressorSurgeMarginMeasurement(String name, Compressor compressor, VolumeFlowTransmitter flowTransmitter) {
    super(requireSource(name, compressor, flowTransmitter), "fraction");
    this.compressor = compressor;
    this.flowTransmitter = flowTransmitter;
  }

  /**
   * Validates source identities before any superclass can initialize a partially constructed measurement.
   *
   * @param name calculated signal tag
   * @param compressor actual source compressor
   * @param flowTransmitter actual held inlet volumetric-flow transmitter
   * @return unchanged calculated signal tag
   * @throws IllegalArgumentException if a source is absent or the transmitter is not on the actual inlet
   */
  private static String requireSource(String name, Compressor compressor, VolumeFlowTransmitter flowTransmitter) {
    if (compressor == null || flowTransmitter == null || compressor.getInletStream() != flowTransmitter.getStream()) {
      throw new IllegalArgumentException("Calculated margin requires the actual compressor inlet-flow source");
    }
    return name;
  }

  /** @return actual native compressor used by the calculation */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The calculated signal and canonical bindings must refer to the same live native compressor")
  public Compressor getCompressor() {
    return compressor;
  }

  /** @return actual causal inlet-flow measurement */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Returning the actual held inlet-flow device preserves causal sample and fault identity")
  public VolumeFlowTransmitter getFlowTransmitter() {
    return flowTransmitter;
  }

  /**
   * Reads the native surge-reference flow at the declared model operating head and speed.
   *
   * @return positive actual cubic metres per hour
   * @throws IllegalStateException if the supported active kJ/kg surge map is absent or invalid
   */
  public double getReferenceSurgeFlowM3PerHour() {
    if (!compressor.getCompressorChart().getSurgeCurve().isActive()
        || !"kJ/kg".equals(compressor.getCompressorChart().getHeadUnit())) {
      throw new IllegalStateException("Measured margin requires the declared active kJ/kg native surge curve");
    }
    double reference = compressor.getSurgeFlowRate();
    if (!Double.isFinite(reference) || reference <= 0.0) {
      throw new IllegalStateException("Native surge-reference flow must be finite and positive");
    }
    return reference;
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    // Reader-facing results are obtained through the typed measurement API.
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    requireUnit(unit);
    return applySignalModifiers(flowTransmitter.getMeasuredValue("m^3/hr") / getReferenceSurgeFlowM3PerHour() - 1.0);
  }

  /** {@inheritDoc} */
  @Override
  public void setTransientSamplingEnabled(boolean enabled) {
    samplingEnabled = enabled;
    if (!enabled) {
      sampleIdentifier = null;
      sampleValue = Double.NaN;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTransientSamplingEnabled() {
    return samplingEnabled;
  }

  /** {@inheritDoc} */
  @Override
  public void sampleTransient(double dt, UUID id) {
    if (!samplingEnabled) {
      return;
    }
    if (id == null || !Double.isFinite(dt) || dt <= 0.0) {
      throw new IllegalArgumentException("A finite positive sample interval and physical-step identifier are required");
    }
    if (id.equals(sampleIdentifier)) {
      return;
    }
    requireUnit(getUnit());
    if (!flowTransmitter.hasTransientSample(id)) {
      throw new IllegalStateException("Calculated margin requires the actual inlet FT sample for this physical step");
    }
    double flow = flowTransmitter.getTransientSample("m^3/hr");
    double headAtSample = compressor.getPolytropicFluidHead();
    double speedAtSample = compressor.getSpeed();
    double reference = getReferenceSurgeFlowM3PerHour();
    recordReference(flow, reference, headAtSample, speedAtSample, id);
    sampleValue = applySignalModifiers(flow / reference - 1.0, dt);
    sampleIdentifier = id;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasTransientSample(UUID id) {
    return samplingEnabled && id != null && id.equals(sampleIdentifier);
  }

  /** {@inheritDoc} */
  @Override
  public double getTransientSample(String unit) {
    requireUnit(unit);
    if (sampleIdentifier == null || !Double.isFinite(sampleValue)) {
      throw new IllegalStateException("Calculated compressor margin has no finite held physical-step sample");
    }
    return sampleValue;
  }

  /** @return detached evidence for the reference actually consumed at the last held scan */
  public Map<String, Object> getLastReferenceEvidence() {
    return new LinkedHashMap<String, Object>(lastReferenceEvidence);
  }

  /** @return whether the last consumed native map/speed/surge-head references were inside their declared domain */
  public boolean isLastReferenceWithinMap() {
    return Boolean.TRUE.equals(lastReferenceEvidence.get("withinMap"));
  }

  /**
   * Records declared model references independently of the instrument indication.
   *
   * @param flow actual held inlet FT indication in actual cubic metres per hour
   * @param reference native surge-curve flow reference in actual cubic metres per hour
   * @param head native head captured at this held scan
   * @param speed native speed captured at this held scan
   * @param id actual physical-step identifier shared by the FT input
   */
  private void recordReference(double flow, double reference, double head, double speed, UUID id) {
    double[] curveHeads = compressor.getCompressorChart().getSurgeCurve().getSortedHead();
    double[] speeds = compressor.getCompressorChart().getSpeeds();
    boolean headRange = compressor.getCompressorChart().getSurgeCurve().isSinglePointSurge();
    if (curveHeads != null && curveHeads.length > 1) {
      headRange = head >= curveHeads[0] && head <= curveHeads[curveHeads.length - 1];
    }
    double minimumSpeed = Double.POSITIVE_INFINITY;
    double maximumSpeed = Double.NEGATIVE_INFINITY;
    if (speeds != null) {
      for (double value : speeds) {
        minimumSpeed = Math.min(minimumSpeed, value);
        maximumSpeed = Math.max(maximumSpeed, value);
      }
    }
    boolean speedRange = Double.isFinite(speed) && speed >= minimumSpeed && speed <= maximumSpeed;
    String flowStatus = compressor.getCompressorChart()
        .getFlowRangeStatus(compressor.getInletStream().getFlowRate("m3/hr"), speed);
    lastReferenceEvidence = new LinkedHashMap<String, Object>();
    lastReferenceEvidence.put("physicalStepIdentifier", id.toString());
    lastReferenceEvidence.put("causalFlowInputTag", flowTransmitter.getName());
    lastReferenceEvidence.put("actualInletFlowIndicationM3PerHour", Double.valueOf(flow));
    lastReferenceEvidence.put("nativeSurgeReferenceM3PerHour", Double.valueOf(reference));
    lastReferenceEvidence.put("nativeHeadKjPerKg", Double.valueOf(head));
    lastReferenceEvidence.put("nativeSpeedRpm", Double.valueOf(speed));
    lastReferenceEvidence.put("nativeChartFlowStatus", flowStatus);
    lastReferenceEvidence.put("surgeCurveHeadInRange", Boolean.valueOf(headRange));
    lastReferenceEvidence.put("mapSpeedInRange", Boolean.valueOf(speedRange));
    lastReferenceEvidence.put("withinMap", Boolean.valueOf(headRange && speedRange && "IN_RANGE".equals(flowStatus)));
    lastReferenceEvidence.put("qualification", "Native synthetic-map domain only; no vendor performance certification");
  }

  /**
   * Accepts only the explicitly dimensionless signal basis.
   *
   * @param unit requested signal unit
   * @throws IllegalArgumentException if an unsupported physical unit is requested
   */
  private static void requireUnit(String unit) {
    if (!"fraction".equals(unit) && !"distance to surge".equals(unit)) {
      throw new IllegalArgumentException("Calculated compressor margin uses a dimensionless fraction");
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (stateIdentity == null) {
      stateIdentity = UUID.randomUUID().toString();
    }
    return "measurement:compressor-margin:" + stateIdentity;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    return getClass() == CompressorSurgeMarginMeasurement.class ? getMeasurementTransientStateCoverageIssue()
        : "Calculated-margin subclasses require their own complete state contract";
  }

  /** {@inheritDoc} */
  @Override
  public MarginState captureTransientState() {
    return new MarginState(getTransientStateIdentity(), compressor, captureMeasurementDeviceTransientState(),
        samplingEnabled, sampleIdentifier, sampleValue, lastReferenceEvidence);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(MarginState state) {
    if (state == null || !getTransientStateIdentity().equals(state.identity) || compressor != state.compressor) {
      throw new IllegalArgumentException("Calculated-margin snapshot belongs to another actual source");
    }
    restoreMeasurementDeviceTransientState(state.measurementState);
    samplingEnabled = state.samplingEnabled;
    sampleIdentifier = state.sampleIdentifier;
    sampleValue = state.sampleValue;
    lastReferenceEvidence = new LinkedHashMap<String, Object>(state.lastReferenceEvidence);
  }

  /** Immutable calculated-margin sample and signal-chain snapshot. */
  public static final class MarginState implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String identity;
    private final Compressor compressor;
    private final MeasurementDeviceTransientState measurementState;
    private final boolean samplingEnabled;
    private final UUID sampleIdentifier;
    private final double sampleValue;
    private final Map<String, Object> lastReferenceEvidence;

    /**
     * Freezes owned sample and base signal state without copying process equipment.
     *
     * @param identity participant identity
     * @param compressor actual referenced source
     * @param measurementState complete base signal state
     * @param samplingEnabled held-sampling policy
     * @param sampleIdentifier held-step identity
     * @param sampleValue held dimensionless value
     * @param lastReferenceEvidence actual consumed native reference domain evidence
     */
    private MarginState(String identity, Compressor compressor, MeasurementDeviceTransientState measurementState,
        boolean samplingEnabled, UUID sampleIdentifier, double sampleValue, Map<String, Object> lastReferenceEvidence) {
      this.identity = identity;
      this.compressor = compressor;
      this.measurementState = measurementState;
      this.samplingEnabled = samplingEnabled;
      this.sampleIdentifier = sampleIdentifier;
      this.sampleValue = sampleValue;
      this.lastReferenceEvidence = new LinkedHashMap<String, Object>(lastReferenceEvidence);
    }
  }
}
