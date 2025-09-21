package neqsim.process.controllerdevice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.util.NamedBaseClass;

/**
 * General-purpose model predictive controller (MPC) for NeqSim process equipment.
 * <p>
 * The controller supports both single-input operation and multivariable configurations with linear
 * quality constraints. A first-order discrete process model is used internally to predict the
 * future trajectory of the controlled variable across a configurable prediction horizon. The
 * controller minimises a quadratic objective consisting of tracking error, absolute control effort
 * and control movement. The optimal actuation is calculated analytically which keeps the
 * implementation dependency free while still representing a full MPC formulation.
 * </p>
 */
public class ModelPredictiveController extends NamedBaseClass
    implements ControllerDeviceInterface {
  private static final long serialVersionUID = 1000L;

  private MeasurementDeviceInterface transmitter;
  private double controllerSetPoint = 0.0;
  private String unit = "[?]";
  private boolean reverseActing = false;
  private boolean isActive = true;
  private double response = 0.0;
  private double minResponse = Double.NEGATIVE_INFINITY;
  private double maxResponse = Double.POSITIVE_INFINITY;
  private double processGain = 1.0;
  private double timeConstant = 10.0;
  private double processBias = 0.0;
  private int predictionHorizon = 10;
  private double outputWeight = 1.0;
  private double controlWeight = 0.0;
  private double moveWeight = 0.0;
  private double preferredControlValue = 0.0;
  private UUID calcIdentifier;
  private double lastSampledValue = 0.0;
  private double lastSampleTime = 1.0;
  private double lastAppliedControl = 0.0;
  private final List<String> controlNames = new ArrayList<>();
  private double[] controlVector = new double[0];
  private double[] lastControlVector = new double[0];
  private double[] minControlVector = new double[0];
  private double[] maxControlVector = new double[0];
  private double[] preferredControlVector = new double[0];
  private double[] controlWeightsVector = new double[0];
  private double[] moveWeightsVector = new double[0];
  private int primaryControlIndex = 0;
  private final List<QualityConstraint> qualityConstraints = new ArrayList<>();
  private Map<String, Double> lastFeedComposition = new LinkedHashMap<>();
  private Map<String, Double> pendingFeedComposition = new LinkedHashMap<>();
  private double lastFeedRate = 0.0;
  private double pendingFeedRate = 0.0;
  private boolean feedInitialised = false;
  private final Map<String, Double> predictedQualityValues = new HashMap<>();

  /**
   * Representation of a quality constraint handled by the MPC. Each constraint
   * links a measurement device with linear sensitivities describing how the
   * quality responds to control actions, feed composition shifts and feed rate
   * changes. Inequality limits are enforced softly by the quadratic program to
   * keep the process within specification while still penalising unnecessary control effort.
   */
  public static final class QualityConstraint {
    private final String name;
    private final MeasurementDeviceInterface measurement;
    private final String unit;
    private final double limit;
    private final double margin;
    private final double[] controlSensitivity;
    private final Map<String, Double> compositionSensitivity;
    private final double rateSensitivity;
    private double lastMeasurement = Double.NaN;
    private double predictedValue = Double.NaN;

    private QualityConstraint(Builder builder) {
      this.name = builder.name;
      this.measurement = builder.measurement;
      this.unit = builder.unit != null ? builder.unit
          : measurement != null ? measurement.getUnit() : "[?]";
      this.limit = builder.limit;
      this.margin = Math.max(0.0, builder.margin);
      this.controlSensitivity = Arrays.copyOf(builder.controlSensitivity,
          builder.controlSensitivity.length);
      this.compositionSensitivity = Collections.unmodifiableMap(
          new LinkedHashMap<>(builder.compositionSensitivity));
      this.rateSensitivity = builder.rateSensitivity;
    }

    /**
     * Create a builder for a {@link QualityConstraint}.
     *
     * @param name identifier for the constraint
     * @return builder instance
     */
    public static Builder builder(String name) {
      return new Builder(name);
    }

    double[] getControlSensitivity() {
      return controlSensitivity;
    }

    double computeFeedEffect(Map<String, Double> deltaComposition, double deltaRate) {
      double effect = rateSensitivity * deltaRate;
      if (deltaComposition != null && !deltaComposition.isEmpty()) {
        for (Map.Entry<String, Double> entry : compositionSensitivity.entrySet()) {
          double delta = deltaComposition.getOrDefault(entry.getKey(), 0.0);
          effect += entry.getValue() * delta;
        }
      }
      return effect;
    }

    MeasurementDeviceInterface getMeasurement() {
      return measurement;
    }

    String getUnit() {
      return unit;
    }

    double getLimit() {
      return limit;
    }

    double getMargin() {
      return margin;
    }

    String getName() {
      return name;
    }

    double getLastMeasurement() {
      return lastMeasurement;
    }

    void setLastMeasurement(double value) {
      this.lastMeasurement = value;
    }

    double getPredictedValue() {
      return predictedValue;
    }

    void setPredictedValue(double value) {
      this.predictedValue = value;
    }

    /**
     * Builder for {@link QualityConstraint} instances.
     */
    public static final class Builder {
      private final String name;
      private MeasurementDeviceInterface measurement;
      private String unit;
      private double limit;
      private double margin;
      private double[] controlSensitivity = new double[0];
      private final Map<String, Double> compositionSensitivity = new LinkedHashMap<>();
      private double rateSensitivity;

      private Builder(String name) {
        if (name == null || name.trim().isEmpty()) {
          throw new IllegalArgumentException("Constraint name must be provided");
        }
        this.name = name;
      }

      public Builder measurement(MeasurementDeviceInterface device) {
        this.measurement = device;
        return this;
      }

      public Builder unit(String unit) {
        this.unit = unit;
        return this;
      }

      public Builder limit(double limit) {
        if (!Double.isFinite(limit)) {
          throw new IllegalArgumentException("Constraint limit must be finite");
        }
        this.limit = limit;
        return this;
      }

      public Builder margin(double margin) {
        if (!Double.isFinite(margin) || margin < 0.0) {
          throw new IllegalArgumentException("Constraint margin must be non-negative and finite");
        }
        this.margin = margin;
        return this;
      }

      public Builder controlSensitivity(double... sensitivity) {
        if (sensitivity == null || sensitivity.length == 0) {
          throw new IllegalArgumentException("Control sensitivity must have at least one value");
        }
        this.controlSensitivity = Arrays.copyOf(sensitivity, sensitivity.length);
        return this;
      }

      public Builder compositionSensitivity(String component, double sensitivity) {
        if (component == null || component.trim().isEmpty()) {
          throw new IllegalArgumentException("Component name must be provided");
        }
        this.compositionSensitivity.put(component, sensitivity);
        return this;
      }

      public Builder compositionSensitivities(Map<String, Double> sensitivities) {
        if (sensitivities != null) {
          for (Map.Entry<String, Double> entry : sensitivities.entrySet()) {
            compositionSensitivity.put(entry.getKey(), entry.getValue());
          }
        }
        return this;
      }

      public Builder rateSensitivity(double sensitivity) {
        this.rateSensitivity = sensitivity;
        return this;
      }

      public QualityConstraint build() {
        if (measurement == null) {
          throw new IllegalStateException("Measurement device must be provided for constraint '"
              + name + "'");
        }
        if (controlSensitivity.length == 0) {
          throw new IllegalStateException(
              "Control sensitivity must be defined for constraint '" + name + "'");
        }
        if (!Double.isFinite(limit)) {
          throw new IllegalStateException(
              "Constraint limit must be finite for constraint '" + name + "'");
        }
        return new QualityConstraint(this);
      }
    }
  }

  /**
   * Default constructor assigning a generic name.
   */
  public ModelPredictiveController() {
    this("mpc");
  }

  /**
   * Construct an MPC controller with a specific name.
   *
   * @param name controller name
   */
  public ModelPredictiveController(String name) {
    super(name);
  }

  /**
   * Configure the set of manipulated variables handled by the MPC. Existing
   * quality constraints are cleared because their sensitivity dimensions may no
   * longer match the new set of controls.
   *
   * @param names ordered list of control names (e.g. pressure, temperature)
   */
  public final void configureControls(String... names) {
    if (names == null || names.length == 0) {
      throw new IllegalArgumentException("At least one control variable must be specified");
    }
    qualityConstraints.clear();
    controlNames.clear();
    for (String name : names) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Control names must be non-empty");
      }
      controlNames.add(name);
    }
    int count = controlNames.size();
    controlVector = new double[count];
    lastControlVector = new double[count];
    minControlVector = new double[count];
    maxControlVector = new double[count];
    preferredControlVector = new double[count];
    controlWeightsVector = new double[count];
    moveWeightsVector = new double[count];
    Arrays.fill(minControlVector, Double.NEGATIVE_INFINITY);
    Arrays.fill(maxControlVector, Double.POSITIVE_INFINITY);
    Arrays.fill(preferredControlVector, 0.0);
    Arrays.fill(controlWeightsVector, 1.0);
    Arrays.fill(moveWeightsVector, 0.0);
    Arrays.fill(controlVector, 0.0);
    Arrays.fill(lastControlVector, 0.0);
    primaryControlIndex = Math.min(primaryControlIndex, count - 1);
    predictedQualityValues.clear();
  }

  private void ensureControlLength(int expectedLength) {
    if (controlVector.length != expectedLength) {
      throw new IllegalStateException(
          "Controller configured for " + controlVector.length + " controls but received "
              + expectedLength);
    }
  }

  private void ensureControlIndex(int index) {
    if (index < 0 || index >= controlVector.length) {
      throw new IllegalArgumentException("Control index out of range: " + index);
    }
  }

  /**
   * Specify the starting values of all manipulated variables.
   *
   * @param values control vector in the configured order
   */
  public void setInitialControlValues(double... values) {
    if (values == null) {
      throw new IllegalArgumentException("Initial control values must be provided");
    }
    ensureControlLength(values.length);
    System.arraycopy(values, 0, controlVector, 0, values.length);
    System.arraycopy(values, 0, lastControlVector, 0, values.length);
  }

  /**
   * Choose which control variable is exposed via {@link #getResponse()} to
   * maintain compatibility with the {@link ControllerDeviceInterface}.
   *
   * @param index index of the primary control variable
   */
  public void setPrimaryControlIndex(int index) {
    ensureControlIndex(index);
    primaryControlIndex = index;
  }

  /**
   * Set lower and upper bounds for a control variable.
   *
   * @param index control index
   * @param min minimum allowed value (may be {@link Double#NEGATIVE_INFINITY})
   * @param max maximum allowed value (may be {@link Double#POSITIVE_INFINITY})
   */
  public void setControlLimits(int index, double min, double max) {
    ensureControlIndex(index);
    if (min > max) {
      throw new IllegalArgumentException("Minimum limit must not exceed maximum limit");
    }
    minControlVector[index] = min;
    maxControlVector[index] = max;
    controlVector[index] = Math.max(min, Math.min(max, controlVector[index]));
    lastControlVector[index] = Math.max(min, Math.min(max, lastControlVector[index]));
  }

  /**
   * Convenience overload allowing limits to be set by control name.
   *
   * @param controlName name of the control variable
   * @param min minimum allowed value
   * @param max maximum allowed value
   */
  public void setControlLimits(String controlName, double min, double max) {
    int index = controlNames.indexOf(controlName);
    if (index < 0) {
      throw new IllegalArgumentException("Unknown control name: " + controlName);
    }
    setControlLimits(index, min, max);
  }

  /**
   * Set quadratic weights on the absolute value of each control variable. Values must be
   * non-negative.
   *
   * @param weights absolute control weights
   */
  public void setControlWeights(double... weights) {
    if (weights == null) {
      throw new IllegalArgumentException("Control weights must be provided");
    }
    ensureControlLength(weights.length);
    for (int i = 0; i < weights.length; i++) {
      if (weights[i] < 0.0) {
        throw new IllegalArgumentException("Control weights must be non-negative");
      }
      controlWeightsVector[i] = weights[i];
    }
  }

  /**
   * Set quadratic penalty on control moves for each variable.
   *
   * @param weights move weights
   */
  public void setMoveWeights(double... weights) {
    if (weights == null) {
      throw new IllegalArgumentException("Move weights must be provided");
    }
    ensureControlLength(weights.length);
    for (int i = 0; i < weights.length; i++) {
      if (weights[i] < 0.0) {
        throw new IllegalArgumentException("Move weights must be non-negative");
      }
      moveWeightsVector[i] = weights[i];
    }
  }

  /**
   * Define the preferred steady-state operating point for each control variable. This represents
   * the control vector that minimises the absolute control penalty when no tracking error is
   * present.
   *
   * @param references preferred control levels
   */
  public void setPreferredControlVector(double... references) {
    if (references == null) {
      throw new IllegalArgumentException("Preferred control values must be provided");
    }
    ensureControlLength(references.length);
    System.arraycopy(references, 0, preferredControlVector, 0, references.length);
  }

  /**
   * @deprecated Use {@link #setPreferredControlVector(double...)} to configure the nominal control
   *             point. This method is retained for backwards compatibility with earlier snapshots of
   *             the MPC implementation.
   */
  @Deprecated
  public void setEnergyReferenceVector(double... references) {
    setPreferredControlVector(references);
  }

  /**
   * Retrieve the ordered list of configured control names.
   *
   * @return unmodifiable list of control names
   */
  public List<String> getControlNames() {
    return Collections.unmodifiableList(controlNames);
  }

  /**
   * Get the latest control value by name.
   *
   * @param controlName name of control variable
   * @return value of the control variable
   */
  public double getControlValue(String controlName) {
    int index = controlNames.indexOf(controlName);
    if (index < 0) {
      throw new IllegalArgumentException("Unknown control name: " + controlName);
    }
    return controlVector[index];
  }

  /**
   * Get the latest control value by index.
   *
   * @param index control index
   * @return value of the control variable
   */
  public double getControlValue(int index) {
    ensureControlIndex(index);
    return controlVector[index];
  }

  /**
   * Copy of the current control vector.
   *
   * @return current control vector
   */
  public double[] getControlVector() {
    return Arrays.copyOf(controlVector, controlVector.length);
  }

  /**
   * Register a new quality constraint. The sensitivity vector must match the
   * number of configured controls.
   *
   * @param constraint quality constraint description
   */
  public void addQualityConstraint(QualityConstraint constraint) {
    if (constraint == null) {
      throw new IllegalArgumentException("Constraint must not be null");
    }
    ensureControlLength(constraint.getControlSensitivity().length);
    qualityConstraints.add(constraint);
  }

  /**
   * Clear all registered quality constraints.
   */
  public void clearQualityConstraints() {
    qualityConstraints.clear();
    predictedQualityValues.clear();
  }

  /**
   * Update the predicted incoming feed composition and flow rate. The values are
   * used as feedforward information in the MPC optimisation.
   *
   * @param composition component molar fractions (will be normalised outside the method)
   * @param feedRate molar feed rate
   */
  public void updateFeedConditions(Map<String, Double> composition, double feedRate) {
    Map<String, Double> copy = new LinkedHashMap<>();
    if (composition != null) {
      for (Map.Entry<String, Double> entry : composition.entrySet()) {
        copy.put(entry.getKey(), entry.getValue());
      }
    }
    pendingFeedComposition = copy;
    pendingFeedRate = feedRate;
    if (!feedInitialised) {
      lastFeedComposition = new LinkedHashMap<>(pendingFeedComposition);
      lastFeedRate = feedRate;
      feedInitialised = true;
    }
  }

  /**
   * Get the last predicted quality value produced by the controller for a named
   * constraint.
   *
   * @param name constraint name
   * @return predicted value or {@link Double#NaN} if unavailable
   */
  public double getPredictedQuality(String name) {
    if (name == null) {
      return Double.NaN;
    }
    return predictedQualityValues.getOrDefault(name, Double.NaN);
  }

  /**
   * Configure the internal first order process model.
   *
   * @param gain steady-state process gain relating control action to the measured variable
   * @param timeConstant dominant time constant of the process model (seconds)
   */
  public void setProcessModel(double gain, double timeConstant) {
    if (!Double.isFinite(gain)) {
      throw new IllegalArgumentException("Process gain must be finite");
    }
    if (!Double.isFinite(timeConstant) || timeConstant <= 0.0) {
      throw new IllegalArgumentException("Time constant must be positive and finite");
    }
    this.processGain = gain;
    this.timeConstant = timeConstant;
  }

  /**
   * Configure the internal first order process model including dead time. Dead time is represented
   * as an equivalent time constant increase because the simplified controller does not maintain an
   * explicit delay line.
   *
   * @param gain steady-state process gain relating control action to the measured variable
   * @param timeConstant dominant time constant of the process model (seconds)
   * @param deadTimeSeconds estimated dead time (seconds)
   */
  public void setProcessModel(double gain, double timeConstant, double deadTimeSeconds) {
    double adjustedTimeConstant = timeConstant + Math.max(deadTimeSeconds, 0.0);
    setProcessModel(gain, adjustedTimeConstant);
  }

  /**
   * Set the steady-state bias of the process model. The bias corresponds to the measured value when
   * the manipulated variable is zero (for example ambient temperature).
   *
   * @param bias process bias value
   */
  public void setProcessBias(double bias) {
    if (!Double.isFinite(bias)) {
      throw new IllegalArgumentException("Process bias must be finite");
    }
    this.processBias = bias;
  }

  /**
   * Set quadratic weights for output tracking, absolute control effort and control movement.
   *
   * @param outputWeight weight on future tracking error
   * @param controlWeight weight on absolute control magnitude (e.g. energy/emission usage)
   * @param moveWeight weight on control movements between successive time steps
   */
  public void setWeights(double outputWeight, double controlWeight, double moveWeight) {
    if (outputWeight < 0.0 || controlWeight < 0.0 || moveWeight < 0.0) {
      throw new IllegalArgumentException("MPC weights must be non-negative");
    }
    this.outputWeight = outputWeight;
    this.controlWeight = controlWeight;
    this.moveWeight = moveWeight;
  }

  /**
   * Specify the preferred steady-state control level for the single-input MPC mode. This value
   * represents the operating point that minimises the absolute control penalty when tracking is not
   * active.
   *
   * @param reference preferred control level
   */
  public void setPreferredControlValue(double reference) {
    if (!Double.isFinite(reference)) {
      throw new IllegalArgumentException("Preferred control value must be finite");
    }
    this.preferredControlValue = reference;
  }

  /**
   * @deprecated Use {@link #setPreferredControlValue(double)} when configuring the MPC economic
   *             target. This method is kept for compatibility with earlier code samples.
   */
  @Deprecated
  public void setEnergyReference(double reference) {
    setPreferredControlValue(reference);
  }

  /**
   * Set the number of future steps used when predicting the controlled variable trajectory.
   *
   * @param horizon prediction horizon length
   */
  public void setPredictionHorizon(int horizon) {
    if (horizon <= 0) {
      throw new IllegalArgumentException("Prediction horizon must be positive");
    }
    this.predictionHorizon = horizon;
  }

  /**
   * Retrieve the last sampled process value. Mainly intended for diagnostics and testing.
   *
   * @return last measured value
   */
  public double getLastSampledValue() {
    return lastSampledValue;
  }

  /**
   * Retrieve the last applied control signal.
   *
   * @return last actuation sent to the process
   */
  public double getLastAppliedControl() {
    return lastAppliedControl;
  }

  @Override
  public double getMeasuredValue() {
    if (transmitter == null) {
      throw new IllegalStateException("MPC controller has no transmitter configured");
    }
    return transmitter.getMeasuredValue();
  }

  @Override
  public double getMeasuredValue(String unit) {
    if (transmitter == null) {
      throw new IllegalStateException("MPC controller has no transmitter configured");
    }
    if (unit == null || unit.isEmpty() || unit.equals("[?]")) {
      return transmitter.getMeasuredValue();
    }
    return transmitter.getMeasuredValue(unit);
  }

  @Override
  public void setControllerSetPoint(double signal) {
    this.controllerSetPoint = signal;
  }

  @Override
  public double getControllerSetPoint() {
    return controllerSetPoint;
  }

  @Override
  public String getUnit() {
    return unit;
  }

  @Override
  public void setUnit(String unit) {
    this.unit = unit;
  }

  @Override
  public void setTransmitter(MeasurementDeviceInterface device) {
    this.transmitter = device;
  }

  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    calcIdentifier = id;
    lastSampleTime = dt;
    if (!qualityConstraints.isEmpty()) {
      if (!isActive) {
        clampControlVector();
        response = controlVector[Math.min(primaryControlIndex, controlVector.length - 1)];
        lastAppliedControl = response;
        return;
      }
      runMultivariable();
      return;
    }
    if (!isActive) {
      response = clamp(initResponse);
      lastAppliedControl = response;
      return;
    }

    double previousControl = Double.isNaN(initResponse) ? lastAppliedControl : initResponse;
    previousControl = clamp(previousControl);

    double measurement = getMeasuredValue(unit);
    lastSampledValue = measurement;

    response = computeOptimalControl(measurement, dt, previousControl);
    lastAppliedControl = response;
  }

  private void clampControlVector() {
    for (int i = 0; i < controlVector.length; i++) {
      double value = controlVector[i];
      if (!Double.isInfinite(minControlVector[i])) {
        value = Math.max(minControlVector[i], value);
      }
      if (!Double.isInfinite(maxControlVector[i])) {
        value = Math.min(maxControlVector[i], value);
      }
      controlVector[i] = value;
    }
  }

  private void runMultivariable() {
    int controlCount = controlVector.length;
    if (controlCount == 0) {
      return;
    }

    double[] previousControl = Arrays.copyOf(lastControlVector, controlCount);

    Map<String, Double> deltaComposition = new LinkedHashMap<>();
    double deltaRate = 0.0;
    if (feedInitialised) {
      Set<String> keys = new LinkedHashSet<>();
      keys.addAll(lastFeedComposition.keySet());
      keys.addAll(pendingFeedComposition.keySet());
      for (String key : keys) {
        double future = pendingFeedComposition.getOrDefault(key, 0.0);
        double past = lastFeedComposition.getOrDefault(key, 0.0);
        double delta = future - past;
        if (Math.abs(delta) > 1.0e-12) {
          deltaComposition.put(key, delta);
        }
      }
      deltaRate = pendingFeedRate - lastFeedRate;
    }

    List<double[]> constraintRows = new ArrayList<>();
    List<Double> constraintBounds = new ArrayList<>();

    for (int idx = 0; idx < qualityConstraints.size(); idx++) {
      QualityConstraint constraint = qualityConstraints.get(idx);
      double measurement = constraint.getLastMeasurement();
      MeasurementDeviceInterface device = constraint.getMeasurement();
      if (device != null) {
        try {
          double measured = constraint.getUnit() == null
              ? device.getMeasuredValue() : device.getMeasuredValue(constraint.getUnit());
          if (Double.isFinite(measured)) {
            measurement = measured;
          }
        } catch (Exception ex) {
          // ignore measurement exceptions and fall back to last value
        }
      }
      if (!Double.isFinite(measurement)) {
        measurement = constraint.getLimit();
      }
      constraint.setLastMeasurement(measurement);
      if (idx == 0) {
        lastSampledValue = measurement;
      }

      double[] sensitivity = constraint.getControlSensitivity();
      double feedEffect = constraint.computeFeedEffect(deltaComposition, deltaRate);
      double rhs = constraint.getLimit() - constraint.getMargin() - measurement - feedEffect;
      double[] row = new double[controlCount];
      double dotPrev = 0.0;
      for (int i = 0; i < controlCount; i++) {
        row[i] = sensitivity[i];
        dotPrev += sensitivity[i] * previousControl[i];
      }
      constraintRows.add(row);
      constraintBounds.add(rhs + dotPrev);
    }

    for (int i = 0; i < controlCount; i++) {
      if (!Double.isInfinite(minControlVector[i])) {
        double[] row = new double[controlCount];
        row[i] = -1.0;
        constraintRows.add(row);
        constraintBounds.add(-minControlVector[i]);
      }
      if (!Double.isInfinite(maxControlVector[i])) {
        double[] row = new double[controlCount];
        row[i] = 1.0;
        constraintRows.add(row);
        constraintBounds.add(maxControlVector[i]);
      }
    }

    double[][] constraintMatrix = constraintRows.toArray(new double[constraintRows.size()][]);
    double[] constraintVector = new double[constraintBounds.size()];
    for (int i = 0; i < constraintBounds.size(); i++) {
      constraintVector[i] = constraintBounds.get(i);
    }

    double[][] hessian = new double[controlCount][controlCount];
    double[] gradient = new double[controlCount];
    for (int i = 0; i < controlCount; i++) {
      double absoluteWeight = Math.max(controlWeightsVector[i], 0.0);
      double moveWeight = Math.max(moveWeightsVector[i], 0.0);
      double diagonal = absoluteWeight + moveWeight;
      if (diagonal < 1.0e-9) {
        diagonal = 1.0e-9;
      }
      hessian[i][i] = diagonal;
      gradient[i] = -absoluteWeight * preferredControlVector[i] - moveWeight * previousControl[i];
    }

    double[] solution = solveQuadraticProgram(hessian, gradient, constraintMatrix, constraintVector);
    if (solution == null) {
      solution = Arrays.copyOf(previousControl, controlCount);
    }

    for (int i = 0; i < controlCount; i++) {
      double value = solution[i];
      if (!Double.isInfinite(minControlVector[i])) {
        value = Math.max(minControlVector[i], value);
      }
      if (!Double.isInfinite(maxControlVector[i])) {
        value = Math.min(maxControlVector[i], value);
      }
      controlVector[i] = value;
    }

    for (QualityConstraint constraint : qualityConstraints) {
      double[] sensitivity = constraint.getControlSensitivity();
      double measurement = constraint.getLastMeasurement();
      double feedEffect = constraint.computeFeedEffect(deltaComposition, deltaRate);
      double dotNew = 0.0;
      double dotPrev = 0.0;
      for (int i = 0; i < controlCount; i++) {
        dotNew += sensitivity[i] * controlVector[i];
        dotPrev += sensitivity[i] * previousControl[i];
      }
      double predicted = measurement + (dotNew - dotPrev) + feedEffect;
      constraint.setPredictedValue(predicted);
      predictedQualityValues.put(constraint.getName(), predicted);
    }

    System.arraycopy(controlVector, 0, lastControlVector, 0, controlCount);
    response = controlVector[Math.min(primaryControlIndex, controlVector.length - 1)];
    lastAppliedControl = response;
    lastFeedComposition = new LinkedHashMap<>(pendingFeedComposition);
    lastFeedRate = pendingFeedRate;
    feedInitialised = true;
  }

  private double[] solveQuadraticProgram(double[][] hessian, double[] gradient,
      double[][] constraints, double[] bounds) {
    int n = gradient.length;
    double[] inverseDiagonal = new double[n];
    for (int i = 0; i < n; i++) {
      double value = hessian[i][i];
      if (value < 1.0e-12) {
        value = 1.0e-12;
      }
      inverseDiagonal[i] = 1.0 / value;
    }

    double[] unconstrained = new double[n];
    for (int i = 0; i < n; i++) {
      unconstrained[i] = -inverseDiagonal[i] * gradient[i];
    }
    if (isFeasible(unconstrained, constraints, bounds)) {
      return unconstrained;
    }

    int constraintCount = constraints == null ? 0 : constraints.length;
    double bestObjective = Double.POSITIVE_INFINITY;
    double[] bestSolution = null;
    if (constraintCount > 0) {
      int combinations = 1 << constraintCount;
      for (int mask = 1; mask < combinations; mask++) {
        if (Integer.bitCount(mask) > n) {
          continue;
        }
        double[][] activeConstraints = buildActiveMatrix(constraints, mask);
        double[] activeBounds = buildActiveVector(bounds, mask);
        double[] candidate = solveEqualityConstrained(inverseDiagonal, gradient,
            activeConstraints, activeBounds);
        if (candidate == null) {
          continue;
        }
        if (!isFeasible(candidate, constraints, bounds)) {
          continue;
        }
        double objective = objectiveValue(hessian, gradient, candidate);
        if (objective < bestObjective) {
          bestObjective = objective;
          bestSolution = candidate;
        }
      }
    }

    if (bestSolution != null) {
      return bestSolution;
    }

    double[] fallback = Arrays.copyOf(unconstrained, n);
    if (constraints != null && bounds != null) {
      for (int row = 0; row < constraints.length; row++) {
        double[] constraint = constraints[row];
        double violation = 0.0;
        for (int i = 0; i < n; i++) {
          violation += constraint[i] * fallback[i];
        }
        violation -= bounds[row];
        if (violation > 0.0) {
          double norm = 0.0;
          for (double coefficient : constraint) {
            norm += coefficient * coefficient;
          }
          if (norm > 1.0e-12) {
            double factor = violation / norm;
            for (int i = 0; i < n; i++) {
              fallback[i] -= factor * constraint[i];
            }
          }
        }
      }
    }
    if (isFeasible(fallback, constraints, bounds)) {
      return fallback;
    }
    return null;
  }

  private double[][] buildActiveMatrix(double[][] matrix, int mask) {
    if (matrix == null || matrix.length == 0) {
      return new double[0][];
    }
    int rows = Integer.bitCount(mask);
    if (rows == 0) {
      return new double[0][];
    }
    double[][] active = new double[rows][];
    int index = 0;
    for (int row = 0; row < matrix.length; row++) {
      if ((mask & (1 << row)) != 0) {
        active[index++] = Arrays.copyOf(matrix[row], matrix[row].length);
      }
    }
    return active;
  }

  private double[] buildActiveVector(double[] vector, int mask) {
    if (vector == null || vector.length == 0) {
      return new double[0];
    }
    int rows = Integer.bitCount(mask);
    double[] active = new double[rows];
    int index = 0;
    for (int row = 0; row < vector.length; row++) {
      if ((mask & (1 << row)) != 0) {
        active[index++] = vector[row];
      }
    }
    return active;
  }

  private double[] solveEqualityConstrained(double[] inverseDiagonal, double[] gradient,
      double[][] constraints, double[] bounds) {
    int n = gradient.length;
    int m = constraints.length;
    if (m == 0) {
      double[] solution = new double[n];
      for (int i = 0; i < n; i++) {
        solution[i] = -inverseDiagonal[i] * gradient[i];
      }
      return solution;
    }

    double[][] reduced = new double[m][m];
    for (int row = 0; row < m; row++) {
      for (int col = 0; col < m; col++) {
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
          sum += constraints[row][i] * inverseDiagonal[i] * constraints[col][i];
        }
        reduced[row][col] = sum;
      }
    }

    double[] rhs = new double[m];
    for (int row = 0; row < m; row++) {
      double sum = 0.0;
      for (int i = 0; i < n; i++) {
        sum += constraints[row][i] * inverseDiagonal[i] * gradient[i];
      }
      rhs[row] = -bounds[row] - sum;
    }

    double[] multipliers = solveLinearSystem(reduced, rhs);
    if (multipliers == null) {
      return null;
    }

    double[] solution = new double[n];
    for (int i = 0; i < n; i++) {
      double sum = gradient[i];
      for (int row = 0; row < m; row++) {
        sum += constraints[row][i] * multipliers[row];
      }
      solution[i] = -inverseDiagonal[i] * sum;
    }
    return solution;
  }

  private double[] solveLinearSystem(double[][] matrix, double[] vector) {
    int n = matrix.length;
    if (n == 0) {
      return new double[0];
    }
    double[][] augmented = new double[n][n + 1];
    for (int i = 0; i < n; i++) {
      System.arraycopy(matrix[i], 0, augmented[i], 0, n);
      augmented[i][n] = vector[i];
    }

    for (int pivot = 0; pivot < n; pivot++) {
      int bestRow = pivot;
      double bestValue = Math.abs(augmented[pivot][pivot]);
      for (int row = pivot + 1; row < n; row++) {
        double value = Math.abs(augmented[row][pivot]);
        if (value > bestValue) {
          bestValue = value;
          bestRow = row;
        }
      }
      if (bestValue < 1.0e-12) {
        return null;
      }
      if (bestRow != pivot) {
        double[] tmp = augmented[pivot];
        augmented[pivot] = augmented[bestRow];
        augmented[bestRow] = tmp;
      }
      double diagonal = augmented[pivot][pivot];
      for (int col = pivot; col <= n; col++) {
        augmented[pivot][col] /= diagonal;
      }
      for (int row = 0; row < n; row++) {
        if (row == pivot) {
          continue;
        }
        double factor = augmented[row][pivot];
        for (int col = pivot; col <= n; col++) {
          augmented[row][col] -= factor * augmented[pivot][col];
        }
      }
    }

    double[] solution = new double[n];
    for (int i = 0; i < n; i++) {
      solution[i] = augmented[i][n];
    }
    return solution;
  }

  private boolean isFeasible(double[] candidate, double[][] constraints, double[] bounds) {
    if (candidate == null) {
      return false;
    }
    if (constraints == null || bounds == null) {
      return true;
    }
    for (int row = 0; row < constraints.length; row++) {
      double lhs = 0.0;
      for (int i = 0; i < candidate.length; i++) {
        lhs += constraints[row][i] * candidate[i];
      }
      if (lhs > bounds[row] + 1.0e-8) {
        return false;
      }
    }
    return true;
  }

  private double objectiveValue(double[][] hessian, double[] gradient, double[] candidate) {
    double value = 0.0;
    for (int i = 0; i < candidate.length; i++) {
      value += 0.5 * hessian[i][i] * candidate[i] * candidate[i] + gradient[i] * candidate[i];
    }
    return value;
  }

  private double computeOptimalControl(double measurement, double dt, double previousControl) {
    double tau = Math.max(timeConstant, 1.0e-9);
    double a = Math.exp(-Math.abs(dt) / tau);
    double effectiveGain = reverseActing ? -Math.abs(processGain) : processGain;

    double sumBetaSquared = 0.0;
    double sumBetaError = 0.0;
    double alpha = a;
    double delta = measurement - processBias;

    for (int step = 1; step <= predictionHorizon; step++) {
      double beta = effectiveGain * (1.0 - alpha);
      double gamma = processBias + alpha * delta - controllerSetPoint;
      sumBetaSquared += outputWeight * beta * beta;
      sumBetaError += outputWeight * beta * gamma;
      alpha *= a;
    }

    double quadratic = sumBetaSquared + controlWeight + moveWeight;
    double linear = 2.0 * sumBetaError - 2.0 * controlWeight * preferredControlValue
        - 2.0 * moveWeight * previousControl;

    if (quadratic < 1.0e-12) {
      if (controlWeight + moveWeight > 0.0) {
        double weighted = (controlWeight * preferredControlValue + moveWeight * previousControl)
            / (controlWeight + moveWeight);
        return clamp(weighted);
      }
      return clamp(previousControl);
    }

    double candidate = -linear / (2.0 * quadratic);
    if (!Double.isFinite(candidate)) {
      candidate = previousControl;
    }
    return clamp(candidate);
  }

  private double clamp(double value) {
    return Math.max(minResponse, Math.min(maxResponse, value));
  }

  @Override
  public double getResponse() {
    return response;
  }

  @Override
  public boolean isReverseActing() {
    return reverseActing;
  }

  @Override
  public void setReverseActing(boolean reverseActing) {
    this.reverseActing = reverseActing;
  }

  @Override
  public void setControllerParameters(double Kp, double Ti, double Td) {
    setWeights(Kp, Ti, Td);
  }

  @Override
  public void setOutputLimits(double min, double max) {
    if (min > max) {
      throw new IllegalArgumentException("Minimum limit must not exceed maximum limit");
    }
    this.minResponse = min;
    this.maxResponse = max;
    response = clamp(response);
    lastAppliedControl = clamp(lastAppliedControl);
    if (controlVector.length > 0) {
      setControlLimits(Math.min(primaryControlIndex, controlVector.length - 1), min, max);
    }
  }

  @Override
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  @Override
  public boolean isActive() {
    return isActive;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ModelPredictiveController controller = (ModelPredictiveController) other;
    return Objects.equals(getName(), controller.getName());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName());
  }

  /**
   * Get the identifier of the last run transient calculation.
   *
   * @return calculation identifier
   */
  public UUID getCalcIdentifier() {
    return calcIdentifier;
  }

  /**
   * Get the controller output lower bound.
   *
   * @return minimum response
   */
  public double getMinResponse() {
    return minResponse;
  }

  /**
   * Get the controller output upper bound.
   *
   * @return maximum response
   */
  public double getMaxResponse() {
    return maxResponse;
  }

  /**
   * Get the configured prediction horizon.
   *
   * @return prediction horizon length
   */
  public int getPredictionHorizon() {
    return predictionHorizon;
  }

  /**
   * Get the last sampling interval seen by the controller.
   *
   * @return sampling time in seconds
   */
  public double getLastSampleTime() {
    return lastSampleTime;
  }
}
