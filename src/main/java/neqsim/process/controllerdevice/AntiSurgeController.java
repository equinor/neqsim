package neqsim.process.controllerdevice;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.measurementdevice.TransientMeasurementDevice;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.valve.ThrottlingValve;

/**
 * AntiSurgeController is a transient anti-surge regulator (P3) that reads the distance to surge from a
 * {@link neqsim.process.equipment.compressor.Compressor} and drives a recycle
 * {@link neqsim.process.equipment.valve.ThrottlingValve} to keep the machine away from its surge line during trip,
 * blow-down, start-up and load-rejection transients.
 *
 * <p>
 * The controlled variable is the dimensionless surge margin returned by {@link Compressor#getDistanceToSurge()}
 * (operating flow / surge flow - 1). When the margin falls below the configured set point the controller opens the
 * recycle valve using a proportional-integral law; when the margin recovers the valve is closed again. The controller
 * integrates the existing NeqSim transient controller framework
 * ({@link neqsim.process.processmodel.ProcessSystem#runTransient}) and adds no new compressor physics.
 * </p>
 *
 * <p>
 * Output is the recycle valve command in percent (0..100). The controller supplies a target to the recycle valve each
 * transient step; actual position advances only during the valve physical step. Attaching it through
 * {@code valve.addController("anti-surge", controller)} or {@code compressor.addController("anti-surge", controller)}
 * is sufficient.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AntiSurgeController extends ControllerDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  static final Logger logger = LogManager.getLogger(AntiSurgeController.class);

  /** The compressor whose surge margin is being protected. */
  private Compressor compressor = null;

  /** Optional actual held calculated-margin input; null retains the legacy direct-map input. */
  private MeasurementDeviceInterface surgeMarginMeasurement;

  /** The recycle valve used as the anti-surge actuator. */
  private ThrottlingValve recycleValve = null;

  /** Surge-margin set point (distance to surge, dimensionless). Default 0.10 = 10%. */
  private double surgeMarginSetPoint = 0.10;

  /** Proportional gain (percent opening per unit margin error). */
  private double proportionalGain = 400.0;

  /** Integral time constant in seconds. */
  private double integralTime = 20.0;

  /** Minimum valve opening in percent. */
  private double minOpening = 0.0;

  /** Maximum valve opening in percent. */
  private double maxOpening = 100.0;

  /** Current valve opening output in percent. */
  private double valveOpening = 0.0;

  /** Accumulated integral term. */
  private double integralState = 0.0;

  /** Last measured surge margin. */
  private double lastMargin = Double.NaN;

  /** Enables rate-aware predictive supervision when true. */
  private boolean predictiveActionEnabled = false;

  /** Prediction horizon in seconds for margin-rate supervision. */
  private double predictionHorizon = 0.0;

  /** Time constant in seconds for filtering the measured margin rate. */
  private double marginRateFilterTime = 5.0;

  /** Filtered margin approach rate in distance-to-surge units per second. */
  private double filteredMarginRate = 0.0;

  /** Predicted distance to surge at the configured prediction horizon. */
  private double predictedMargin = Double.NaN;

  /** Candidate valve opening before actuator rate/lag handling. */
  private double targetValveOpening = 0.0;

  /** Maximum valve movement in percent opening per second. Infinite disables the limiter. */
  private double valveRateLimit = Double.POSITIVE_INFINITY;

  /** First-order actuator time constant in seconds. Zero disables actuator lag. */
  private double actuatorTimeConstant = 0.0;

  /** Emergency margin below which a minimum emergency opening is forced. */
  private double emergencyMargin = 0.0;

  /** Minimum valve opening in percent when the emergency margin is crossed. */
  private double emergencyOpening = 100.0;

  /**
   * Default constructor.
   */
  public AntiSurgeController() {
    super("anti-surge controller");
  }

  /**
   * Constructs an AntiSurgeController with the given name.
   *
   * @param name the controller name
   */
  public AntiSurgeController(String name) {
    super(name);
  }

  /**
   * Constructs an AntiSurgeController wired to a compressor and recycle valve.
   *
   * @param name the controller name
   * @param compressor the compressor whose surge margin is protected
   * @param recycleValve the recycle valve actuator
   */
  public AntiSurgeController(String name, Compressor compressor, ThrottlingValve recycleValve) {
    super(name);
    this.compressor = compressor;
    this.recycleValve = recycleValve;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Reads the compressor distance to surge, computes the recycle valve opening with a proportional-integral anti-surge
   * law (reverse acting: low margin opens the valve), clamps the output and applies it to the recycle valve.
   * </p>
   */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    if (id != null && hasRunTransient(id)) {
      return;
    }
    if (!isActive()) {
      valveOpening = initResponse;
      calcIdentifier = id;
      return;
    }
    if (compressor == null) {
      logger.warn("AntiSurgeController has no compressor configured; output held");
      calcIdentifier = id;
      return;
    }
    double previousMargin = lastMargin;
    double margin = readMargin(id);
    if (!Double.isFinite(margin)) {
      // No surge data: hold the valve closed
      valveOpening = clamp(minOpening);
      applyToValve();
      calcIdentifier = id;
      return;
    }

    updatePredictedMargin(previousMargin, margin, dt);
    lastMargin = margin;

    // Reverse acting: error is positive when the margin is below the set point.
    double controlMargin = predictiveActionEnabled ? Math.min(margin, predictedMargin) : margin;
    double error = surgeMarginSetPoint - controlMargin;

    double proportional = proportionalGain * error;
    if (integralTime > 0.0) {
      integralState += proportionalGain / integralTime * error * dt;
    }
    double output = proportional + integralState;

    if (margin <= emergencyMargin) {
      output = Math.max(output, emergencyOpening);
    }

    // Anti-windup clamping.
    if (output > maxOpening) {
      output = maxOpening;
      if (integralTime > 0.0) {
        integralState -= proportionalGain / integralTime * error * dt;
      }
    } else if (output < minOpening) {
      output = minOpening;
      if (integralTime > 0.0) {
        integralState -= proportionalGain / integralTime * error * dt;
      }
    }

    targetValveOpening = clamp(output);
    valveOpening = applyActuatorDynamics(targetValveOpening, dt);
    applyToValve();
    calcIdentifier = id;
  }

  /**
   * Updates the filtered margin rate and predicted distance to surge.
   *
   * @param previousMargin the previous measured distance to surge
   * @param margin the current measured distance to surge
   * @param dt the transient time step in seconds
   */
  private void updatePredictedMargin(double previousMargin, double margin, double dt) {
    if (!predictiveActionEnabled || predictionHorizon <= 0.0 || dt <= 0.0 || !Double.isFinite(previousMargin)) {
      predictedMargin = margin;
      return;
    }

    double rawRate = (margin - previousMargin) / dt;
    if (marginRateFilterTime > 0.0) {
      double alpha = dt / (marginRateFilterTime + dt);
      filteredMarginRate += alpha * (rawRate - filteredMarginRate);
    } else {
      filteredMarginRate = rawRate;
    }
    predictedMargin = margin + filteredMarginRate * predictionHorizon;
  }

  /**
   * Applies optional valve rate limiting and first-order actuator lag.
   *
   * @param requestedOpening requested valve opening in percent
   * @param dt the transient time step in seconds
   * @return the actuator-limited valve opening in percent
   */
  private double applyActuatorDynamics(double requestedOpening, double dt) {
    double limitedOpening = requestedOpening;
    if (Double.isFinite(valveRateLimit) && valveRateLimit >= 0.0 && dt > 0.0) {
      double maximumChange = valveRateLimit * dt;
      double requestedChange = requestedOpening - valveOpening;
      if (requestedChange > maximumChange) {
        limitedOpening = valveOpening + maximumChange;
      } else if (requestedChange < -maximumChange) {
        limitedOpening = valveOpening - maximumChange;
      }
    }

    if (actuatorTimeConstant > 0.0 && dt > 0.0) {
      double alpha = Math.min(dt / actuatorTimeConstant, 1.0);
      return clamp(valveOpening + alpha * (limitedOpening - valveOpening));
    }
    return clamp(limitedOpening);
  }

  /**
   * Apply the current valve opening to the recycle valve if one is configured.
   */
  private void applyToValve() {
    if (recycleValve != null) {
      recycleValve.setTargetPercentValveOpening(valveOpening);
    }
  }

  /**
   * Binds an explicitly sampled calculated margin input without changing the legacy default.
   *
   * @param measurement actual sample-and-hold device, or null to use the compressor map directly
   * @throws IllegalArgumentException if the supplied device cannot provide held physical-step samples
   */
  public void setSurgeMarginMeasurement(MeasurementDeviceInterface measurement) {
    if (measurement != null && !(measurement instanceof TransientMeasurementDevice)) {
      throw new IllegalArgumentException("Anti-surge margin input requires physical-step sample-and-hold");
    }
    surgeMarginMeasurement = measurement;
  }

  /** @return actual held calculated-margin input, or null for the direct compressor-map policy */
  public MeasurementDeviceInterface getSurgeMarginMeasurement() {
    return surgeMarginMeasurement;
  }

  /**
   * Reads the configured margin once without advancing an instrument signal chain.
   *
   * @param id current physical-step identifier
   * @return held dimensionless margin or the legacy direct-map value
   * @throws IllegalStateException if an explicitly configured held input is missing its current sample
   */
  private double readMargin(UUID id) {
    if (surgeMarginMeasurement == null) {
      return compressor.getDistanceToSurge();
    }
    TransientMeasurementDevice sampled = (TransientMeasurementDevice) surgeMarginMeasurement;
    if (!sampled.isTransientSamplingEnabled() || !sampled.hasTransientSample(id)) {
      throw new IllegalStateException("Configured anti-surge input has no sample for the physical step");
    }
    return sampled.getTransientSample();
  }

  /**
   * Returns detached static control policy and actual input/target identities, never integrator state.
   *
   * @return complete supported anti-surge configuration; the UNBOUNDED rate policy denotes an unlimited output rate
   */
  public Map<String, Object> getControlConfiguration() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "neqsim.anti_surge_control_configuration.v1");
    map.put("nativeClass", getClass().getName());
    map.put("compressorTag", compressor == null ? "" : compressor.getName());
    map.put("recycleValveTag", recycleValve == null ? "" : recycleValve.getName());
    map.put("nativeMapReference", nativeMapReference());
    map.put("inputMode", surgeMarginMeasurement == null ? "DIRECT_COMPRESSOR_MAP" : "HELD_CALCULATED_MARGIN");
    map.put("inputTag", surgeMarginMeasurement == null ? "" : surgeMarginMeasurement.getName());
    map.put("inputClass", surgeMarginMeasurement == null ? "" : surgeMarginMeasurement.getClass().getName());
    map.put("active", Boolean.valueOf(isActive()));
    map.put("surgeMarginSetPoint", Double.valueOf(surgeMarginSetPoint));
    map.put("proportionalGain", Double.valueOf(proportionalGain));
    map.put("integralTimeSeconds", Double.valueOf(integralTime));
    map.put("minimumOpeningPercent", Double.valueOf(minOpening));
    map.put("maximumOpeningPercent", Double.valueOf(maxOpening));
    map.put("predictiveActionEnabled", Boolean.valueOf(predictiveActionEnabled));
    map.put("predictionHorizonSeconds", Double.valueOf(predictionHorizon));
    map.put("marginRateFilterSeconds", Double.valueOf(marginRateFilterTime));
    map.put("valveRateLimitPercentPerSecond",
        Double.isInfinite(valveRateLimit) ? "UNBOUNDED" : Double.valueOf(valveRateLimit));
    map.put("outputLagSeconds", Double.valueOf(actuatorTimeConstant));
    map.put("emergencyMargin", Double.valueOf(emergencyMargin));
    map.put("emergencyOpeningPercent", Double.valueOf(emergencyOpening));
    return map;
  }

  /**
   * Captures full-precision native map data rather than the rounded human-readable chart export.
   *
   * @return detached-compatible map arrays and source types
   */
  private Map<String, Object> nativeMapReference() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    if (compressor == null || compressor.getCompressorChart() == null) {
      return new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(values), Map.class);
    }
    neqsim.process.equipment.compressor.CompressorChartInterface chart = compressor.getCompressorChart();
    values.put("chartClass", chart.getClass().getName());
    values.put("headUnit", chart.getHeadUnit());
    values.put("active", Boolean.valueOf(chart.isUseCompressorChart()));
    values.put("referenceConditions", chart.getChartConditions());
    values.put("speedRpm", chart.getSpeeds());
    values.put("actualFlowM3PerHour", chart.getFlows());
    values.put("head", chart.getHeads());
    values.put("efficiency", chart.getPolytropicEfficiencies());
    values.put("surgeCurveHead", chart.getSurgeCurve().getSortedHead());
    values.put("surgeCurveFlow", chart.getSurgeCurve().getSortedFlow());
    return new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(values), Map.class);
  }

  /**
   * Applies a complete declared static policy while retaining actual input and actuator references.
   *
   * @param values supported configuration produced by getControlConfiguration or its governed revision
   * @throws IllegalArgumentException if keys, identities or bounded numerical settings are invalid
   */
  public void applyControlConfiguration(Map<String, Object> values) {
    Map<String, Object> current = getControlConfiguration();
    if (values == null || !values.keySet().equals(current.keySet())) {
      throw new IllegalArgumentException("A complete supported anti-surge configuration is required");
    }
    for (String key : new String[] {"schemaVersion", "nativeClass", "compressorTag", "recycleValveTag", "inputMode",
        "inputTag", "inputClass", "nativeMapReference"}) {
      if (!new com.google.gson.Gson().toJsonTree(current.get(key))
          .equals(new com.google.gson.Gson().toJsonTree(values.get(key)))) {
        throw new IllegalArgumentException("Anti-surge actual identity differs: " + key);
      }
    }
    double margin = number(values, "surgeMarginSetPoint");
    double gain = number(values, "proportionalGain");
    double integral = number(values, "integralTimeSeconds");
    double minimum = number(values, "minimumOpeningPercent");
    double maximum = number(values, "maximumOpeningPercent");
    double horizon = number(values, "predictionHorizonSeconds");
    double filter = number(values, "marginRateFilterSeconds");
    double rate = "UNBOUNDED".equals(values.get("valveRateLimitPercentPerSecond")) ? Double.POSITIVE_INFINITY
        : number(values, "valveRateLimitPercentPerSecond");
    double lag = number(values, "outputLagSeconds");
    double emergency = number(values, "emergencyMargin");
    double emergencyOutput = number(values, "emergencyOpeningPercent");
    if (margin < 0 || gain < 0 || integral < 0 || minimum < 0 || maximum > 100 || minimum > maximum || horizon < 0
        || filter < 0 || rate <= 0 || lag < 0 || emergencyOutput < 0 || emergencyOutput > 100
        || !(values.get("active") instanceof Boolean) || !(values.get("predictiveActionEnabled") instanceof Boolean)) {
      throw new IllegalArgumentException("Invalid bounded anti-surge control policy");
    }
    setSurgeMarginSetPoint(margin);
    setProportionalGain(gain);
    setIntegralTime(integral);
    setOpeningRange(minimum, maximum);
    setPredictiveActionEnabled(((Boolean) values.get("predictiveActionEnabled")).booleanValue());
    setPredictionHorizon(horizon);
    setMarginRateFilterTime(filter);
    setActuatorDynamics(rate, lag);
    setEmergencyAction(emergency, emergencyOutput);
    setActive(((Boolean) values.get("active")).booleanValue());
  }

  /**
   * Reads one finite scalar without silently replacing an absent or non-numeric setting.
   *
   * @param values complete static policy
   * @param key required scalar name
   * @return finite numerical value
   * @throws IllegalArgumentException if the value is missing or non-finite
   */
  private static double number(Map<String, Object> values, String key) {
    Object value = values.get(key);
    if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
      throw new IllegalArgumentException("Finite anti-surge setting required: " + key);
    }
    return ((Number) value).doubleValue();
  }

  /**
   * Clamp a candidate valve opening to the configured min/max range.
   *
   * @param value the candidate opening in percent
   * @return the clamped opening in percent
   */
  private double clamp(double value) {
    if (value < minOpening) {
      return minOpening;
    }
    if (value > maxOpening) {
      return maxOpening;
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override
  public double getResponse() {
    return valveOpening;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    return Double.isNaN(lastMargin) && compressor != null ? compressor.getDistanceToSurge() : lastMargin;
  }

  /**
   * Get the current recycle valve opening output.
   *
   * @return the valve opening in percent (0..100)
   */
  public double getValveOpening() {
    return valveOpening;
  }

  /**
   * Get the current target valve opening before actuator rate and lag limits.
   *
   * @return the target valve opening in percent
   */
  public double getTargetValveOpening() {
    return targetValveOpening;
  }

  /**
   * Get the filtered distance-to-surge rate used by predictive supervision.
   *
   * @return the filtered margin rate in distance-to-surge units per second
   */
  public double getFilteredMarginRate() {
    return filteredMarginRate;
  }

  /**
   * Get the predicted distance to surge at the configured prediction horizon.
   *
   * @return the predicted distance to surge, or {@code NaN} before a finite margin has been read
   */
  public double getPredictedMargin() {
    return predictedMargin;
  }

  /**
   * Get the compressor being protected.
   *
   * @return the compressor, or {@code null} if none is set
   */
  public Compressor getCompressor() {
    return compressor;
  }

  /**
   * Set the compressor whose surge margin is protected.
   *
   * @param compressor the compressor
   */
  public void setCompressor(Compressor compressor) {
    this.compressor = compressor;
  }

  /**
   * Get the recycle valve actuator.
   *
   * @return the recycle valve, or {@code null} if none is set
   */
  public ThrottlingValve getRecycleValve() {
    return recycleValve;
  }

  /**
   * Set the recycle valve actuator.
   *
   * @param recycleValve the recycle valve
   */
  public void setRecycleValve(ThrottlingValve recycleValve) {
    this.recycleValve = recycleValve;
  }

  /**
   * Get the surge-margin set point.
   *
   * @return the surge-margin set point (dimensionless distance to surge)
   */
  public double getSurgeMarginSetPoint() {
    return surgeMarginSetPoint;
  }

  /**
   * Set the surge-margin set point. The recycle valve starts to open when the compressor distance to surge falls below
   * this value.
   *
   * @param surgeMarginSetPoint the surge-margin set point (dimensionless, e.g. 0.10 for 10%)
   */
  public void setSurgeMarginSetPoint(double surgeMarginSetPoint) {
    this.surgeMarginSetPoint = surgeMarginSetPoint;
  }

  /**
   * Get the proportional gain.
   *
   * @return the proportional gain in percent opening per unit margin error
   */
  public double getProportionalGain() {
    return proportionalGain;
  }

  /**
   * Set the proportional gain.
   *
   * @param proportionalGain the proportional gain in percent opening per unit margin error
   */
  public void setProportionalGain(double proportionalGain) {
    this.proportionalGain = proportionalGain;
  }

  /**
   * Get the integral time constant.
   *
   * @return the integral time constant in seconds
   */
  public double getIntegralTime() {
    return integralTime;
  }

  /**
   * Set the integral time constant.
   *
   * @param integralTime the integral time constant in seconds (0 disables integral action)
   */
  public void setIntegralTime(double integralTime) {
    this.integralTime = integralTime;
  }

  /**
   * Set the allowable valve opening range.
   *
   * @param minOpening the minimum opening in percent
   * @param maxOpening the maximum opening in percent
   */
  public void setOpeningRange(double minOpening, double maxOpening) {
    this.minOpening = minOpening;
    this.maxOpening = maxOpening;
  }

  /**
   * Enable or disable rate-aware predictive anti-surge supervision.
   *
   * @param predictiveActionEnabled true to use predicted distance to surge in the PI error
   */
  public void setPredictiveActionEnabled(boolean predictiveActionEnabled) {
    this.predictiveActionEnabled = predictiveActionEnabled;
  }

  /**
   * Checks whether predictive anti-surge supervision is enabled.
   *
   * @return true if predictive supervision is enabled
   */
  public boolean isPredictiveActionEnabled() {
    return predictiveActionEnabled;
  }

  /**
   * Set the prediction horizon for rate-aware anti-surge supervision.
   *
   * @param predictionHorizon the prediction horizon in seconds; values less than or equal to zero disable prediction
   */
  public void setPredictionHorizon(double predictionHorizon) {
    this.predictionHorizon = predictionHorizon;
  }

  /**
   * Get the prediction horizon.
   *
   * @return the prediction horizon in seconds
   */
  public double getPredictionHorizon() {
    return predictionHorizon;
  }

  /**
   * Set the margin-rate filter time constant.
   *
   * @param marginRateFilterTime the filter time constant in seconds; zero uses the raw finite-difference rate
   */
  public void setMarginRateFilterTime(double marginRateFilterTime) {
    this.marginRateFilterTime = marginRateFilterTime;
  }

  /**
   * Set actuator dynamics for the recycle valve command.
   *
   * @param valveRateLimit the maximum valve movement in percent opening per second
   * @param actuatorTimeConstant the first-order actuator time constant in seconds; zero disables lag
   */
  public void setActuatorDynamics(double valveRateLimit, double actuatorTimeConstant) {
    this.valveRateLimit = valveRateLimit;
    this.actuatorTimeConstant = actuatorTimeConstant;
  }

  /**
   * Set the emergency fallback opening applied when the measured margin is very low.
   *
   * @param emergencyMargin the measured distance to surge that triggers emergency action
   * @param emergencyOpening the minimum valve opening in percent during emergency action
   */
  public void setEmergencyAction(double emergencyMargin, double emergencyOpening) {
    this.emergencyMargin = emergencyMargin;
    this.emergencyOpening = emergencyOpening;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean hasCompleteControllerTransientStateCoverage() {
    return getClass() == AntiSurgeController.class;
  }

  /** {@inheritDoc} */
  @Override
  protected Serializable captureControllerSubclassTransientState() {
    double recycleValveOpening = recycleValve == null ? Double.NaN : recycleValve.getPercentValveOpening();
    double recycleValveTarget = recycleValve == null ? Double.NaN : recycleValve.getTargetPercentValveOpening();
    return new AntiSurgeTransientState(compressor, recycleValve, surgeMarginMeasurement, surgeMarginSetPoint,
        proportionalGain, integralTime, minOpening, maxOpening, valveOpening, integralState, lastMargin,
        predictiveActionEnabled, predictionHorizon, marginRateFilterTime, filteredMarginRate, predictedMargin,
        targetValveOpening, valveRateLimit, actuatorTimeConstant, emergencyMargin, emergencyOpening,
        recycleValveOpening, recycleValveTarget);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreControllerSubclassTransientState(Serializable state) {
    if (!(state instanceof AntiSurgeTransientState)) {
      throw new IllegalArgumentException("Anti-surge controller requires an anti-surge transient snapshot");
    }
    AntiSurgeTransientState snapshot = (AntiSurgeTransientState) state;
    compressor = snapshot.compressor;
    surgeMarginMeasurement = snapshot.surgeMarginMeasurement;
    recycleValve = snapshot.recycleValve;
    surgeMarginSetPoint = snapshot.surgeMarginSetPoint;
    proportionalGain = snapshot.proportionalGain;
    integralTime = snapshot.integralTime;
    minOpening = snapshot.minOpening;
    maxOpening = snapshot.maxOpening;
    valveOpening = snapshot.valveOpening;
    integralState = snapshot.integralState;
    lastMargin = snapshot.lastMargin;
    predictiveActionEnabled = snapshot.predictiveActionEnabled;
    predictionHorizon = snapshot.predictionHorizon;
    marginRateFilterTime = snapshot.marginRateFilterTime;
    filteredMarginRate = snapshot.filteredMarginRate;
    predictedMargin = snapshot.predictedMargin;
    targetValveOpening = snapshot.targetValveOpening;
    valveRateLimit = snapshot.valveRateLimit;
    actuatorTimeConstant = snapshot.actuatorTimeConstant;
    emergencyMargin = snapshot.emergencyMargin;
    emergencyOpening = snapshot.emergencyOpening;
    if (recycleValve != null) {
      recycleValve.setPercentValveOpening(snapshot.recycleValveOpening);
      recycleValve.setTargetPercentValveOpening(snapshot.recycleValveTarget);
    }
  }

  /**
   * Reset the controller integral state and output to fully closed.
   */
  public void reset() {
    this.integralState = 0.0;
    this.valveOpening = minOpening;
    this.targetValveOpening = minOpening;
    this.lastMargin = Double.NaN;
    this.filteredMarginRate = 0.0;
    this.predictedMargin = Double.NaN;
  }

  /** Immutable anti-surge state appended to the base controller rollback snapshot. */
  private static final class AntiSurgeTransientState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final Compressor compressor;
    private final ThrottlingValve recycleValve;
    private final MeasurementDeviceInterface surgeMarginMeasurement;
    private final double surgeMarginSetPoint;
    private final double proportionalGain;
    private final double integralTime;
    private final double minOpening;
    private final double maxOpening;
    private final double valveOpening;
    private final double integralState;
    private final double lastMargin;
    private final boolean predictiveActionEnabled;
    private final double predictionHorizon;
    private final double marginRateFilterTime;
    private final double filteredMarginRate;
    private final double predictedMargin;
    private final double targetValveOpening;
    private final double valveRateLimit;
    private final double actuatorTimeConstant;
    private final double emergencyMargin;
    private final double emergencyOpening;
    private final double recycleValveOpening;
    private final double recycleValveTarget;

    private AntiSurgeTransientState(Compressor compressor, ThrottlingValve recycleValve,
        MeasurementDeviceInterface surgeMarginMeasurement, double surgeMarginSetPoint, double proportionalGain,
        double integralTime, double minOpening, double maxOpening, double valveOpening, double integralState,
        double lastMargin, boolean predictiveActionEnabled, double predictionHorizon, double marginRateFilterTime,
        double filteredMarginRate, double predictedMargin, double targetValveOpening, double valveRateLimit,
        double actuatorTimeConstant, double emergencyMargin, double emergencyOpening, double recycleValveOpening,
        double recycleValveTarget) {
      this.compressor = compressor;
      this.recycleValve = recycleValve;
      this.surgeMarginMeasurement = surgeMarginMeasurement;
      this.surgeMarginSetPoint = surgeMarginSetPoint;
      this.proportionalGain = proportionalGain;
      this.integralTime = integralTime;
      this.minOpening = minOpening;
      this.maxOpening = maxOpening;
      this.valveOpening = valveOpening;
      this.integralState = integralState;
      this.lastMargin = lastMargin;
      this.predictiveActionEnabled = predictiveActionEnabled;
      this.predictionHorizon = predictionHorizon;
      this.marginRateFilterTime = marginRateFilterTime;
      this.filteredMarginRate = filteredMarginRate;
      this.predictedMargin = predictedMargin;
      this.targetValveOpening = targetValveOpening;
      this.valveRateLimit = valveRateLimit;
      this.actuatorTimeConstant = actuatorTimeConstant;
      this.emergencyMargin = emergencyMargin;
      this.emergencyOpening = emergencyOpening;
      this.recycleValveOpening = recycleValveOpening;
      this.recycleValveTarget = recycleValveTarget;
    }
  }
}
