package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import neqsim.process.processmodel.ProcessModel;

/**
 * Immutable definition of one simulator-bound operating action for a {@link ProcessModel}.
 *
 * <p>
 * An action retains stable identity, an area-qualified automation address, unit, provenance, and either continuous
 * bounds or an exact enumerated discrete domain. Candidate application uses
 * {@link ProcessModel#getVariableValue(String, String)} and
 * {@link ProcessModel#setVariableValue(String, double, String)} so the action remains attached to the existing
 * process-automation architecture. This class does not run the model or infer that a candidate is feasible.
 * </p>
 *
 * <p>
 * State capture and restoration are explicit. A captured state is bound to the complete action identity and cannot be
 * restored through a different or subsequently changed definition.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class ProcessModelOperatingAction implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Default relative tolerance used only for automation write/read-back verification. */
  private static final double DEFAULT_READ_BACK_RELATIVE_TOLERANCE = 1.0e-10;

  /** Provenance for the compatibility-preserving default read-back tolerance. */
  private static final String DEFAULT_READ_BACK_TOLERANCE_PROVENANCE = "ProcessModelOperatingAction default floating-point read-back tolerance";

  /** Declared action value semantics. */
  public enum ValueSemantics {
    /** A continuously variable operating set point inside inclusive bounds. */
    CONTINUOUS,
    /** An enumerated operating or line-up choice with no interpolation. */
    DISCRETE
  }

  /** Stable machine-readable action identifier. */
  private final String id;

  /** Human-readable action name. */
  private final String name;

  /** Area-qualified process-automation address. */
  private final String address;

  /** Unit used for reading and writing the action value. */
  private final String unit;

  /** Evidence source for bounds or allowed values. */
  private final String provenance;

  /** Value semantics. */
  private final ValueSemantics valueSemantics;

  /** Inclusive lower bound. */
  private final double lowerBound;

  /** Inclusive upper bound. */
  private final double upperBound;

  /** Exact allowed values for a discrete action, or an empty array for a continuous action. */
  private final double[] allowedValues;

  /** Absolute automation read-back tolerance in the declared action unit. */
  private final double readBackAbsoluteTolerance;

  /** Scale-aware relative automation read-back tolerance. */
  private final double readBackRelativeTolerance;

  /** Evidence source for the selected read-back tolerance. */
  private final String readBackToleranceProvenance;

  /**
   * Creates an action after factory validation.
   *
   * @param id stable identifier
   * @param name display name
   * @param address area-qualified automation address
   * @param unit declared unit
   * @param provenance evidence source
   * @param valueSemantics continuous or discrete semantics
   * @param lowerBound inclusive lower bound
   * @param upperBound inclusive upper bound
   * @param allowedValues exact allowed discrete values
   * @param readBackAbsoluteTolerance absolute read-back tolerance in the action unit
   * @param readBackRelativeTolerance scale-aware relative read-back tolerance
   * @param readBackToleranceProvenance evidence source for the read-back tolerance
   */
  private ProcessModelOperatingAction(String id, String name, String address, String unit, String provenance,
      ValueSemantics valueSemantics, double lowerBound, double upperBound, double[] allowedValues,
      double readBackAbsoluteTolerance, double readBackRelativeTolerance, String readBackToleranceProvenance) {
    this.id = requireText(id, "Operating-action id");
    this.name = requireText(name, "Operating-action name");
    this.address = requireText(address, "Operating-action address");
    this.unit = requireText(unit, "Operating-action unit");
    this.provenance = requireText(provenance, "Operating-action provenance");
    if (valueSemantics == null) {
      throw new IllegalArgumentException("Operating-action value semantics must not be null");
    }
    if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound) || lowerBound > upperBound) {
      throw new IllegalArgumentException("Operating-action bounds must be finite and ordered");
    }
    if (!Double.isFinite(readBackAbsoluteTolerance) || readBackAbsoluteTolerance < 0.0
        || !Double.isFinite(readBackRelativeTolerance) || readBackRelativeTolerance < 0.0
        || readBackAbsoluteTolerance == 0.0 && readBackRelativeTolerance == 0.0) {
      throw new IllegalArgumentException(
          "Operating-action read-back tolerances must be finite, non-negative, and not both zero");
    }
    this.valueSemantics = valueSemantics;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.allowedValues = Arrays.copyOf(allowedValues, allowedValues.length);
    this.readBackAbsoluteTolerance = readBackAbsoluteTolerance;
    this.readBackRelativeTolerance = readBackRelativeTolerance;
    this.readBackToleranceProvenance = requireText(readBackToleranceProvenance,
        "Operating-action read-back tolerance provenance");
  }

  /**
   * Creates a continuous action.
   *
   * @param id stable machine-readable identifier
   * @param name human-readable name
   * @param address area-qualified automation address
   * @param lowerBound inclusive lower bound
   * @param upperBound inclusive upper bound
   * @param unit unit used by the automation address
   * @param provenance source of the declared bounds
   * @return immutable continuous action
   */
  public static ProcessModelOperatingAction continuous(String id, String name, String address, double lowerBound,
      double upperBound, String unit, String provenance) {
    return new ProcessModelOperatingAction(id, name, address, unit, provenance, ValueSemantics.CONTINUOUS, lowerBound,
        upperBound, new double[0], 0.0, DEFAULT_READ_BACK_RELATIVE_TOLERANCE, DEFAULT_READ_BACK_TOLERANCE_PROVENANCE);
  }

  /**
   * Creates an enumerated discrete action.
   *
   * <p>
   * Values retain declaration order for deterministic external enumeration. Duplicates, non-finite values, and
   * interpolation between allowed values are rejected.
   * </p>
   *
   * @param id stable machine-readable identifier
   * @param name human-readable name
   * @param address area-qualified automation address
   * @param allowedValues exact permitted values
   * @param unit unit used by the automation address
   * @param provenance source of the allowed values
   * @return immutable discrete action
   */
  public static ProcessModelOperatingAction discrete(String id, String name, String address, double[] allowedValues,
      String unit, String provenance) {
    if (allowedValues == null || allowedValues.length == 0) {
      throw new IllegalArgumentException("A discrete action requires at least one allowed value");
    }
    double[] copy = Arrays.copyOf(allowedValues, allowedValues.length);
    double lowerBound = Double.POSITIVE_INFINITY;
    double upperBound = Double.NEGATIVE_INFINITY;
    for (int index = 0; index < copy.length; index++) {
      if (!Double.isFinite(copy[index])) {
        throw new IllegalArgumentException("Discrete operating-action values must be finite");
      }
      for (int previous = 0; previous < index; previous++) {
        if (sameDouble(copy[index], copy[previous])) {
          throw new IllegalArgumentException("Discrete operating-action values must be unique");
        }
      }
      lowerBound = Math.min(lowerBound, copy[index]);
      upperBound = Math.max(upperBound, copy[index]);
    }
    return new ProcessModelOperatingAction(id, name, address, unit, provenance, ValueSemantics.DISCRETE, lowerBound,
        upperBound, copy, 0.0, DEFAULT_READ_BACK_RELATIVE_TOLERANCE, DEFAULT_READ_BACK_TOLERANCE_PROVENANCE);
  }

  /**
   * Returns an otherwise identical action with an explicitly evidenced automation read-back tolerance.
   *
   * <p>
   * A read-back is accepted when its absolute residual does not exceed the larger of the declared absolute tolerance
   * and the declared relative tolerance times {@code max(1, abs(expected))}. The absolute tolerance uses
   * {@link #getUnit()}. This evidence only qualifies write/read-back consistency; it does not establish process
   * feasibility or operating approval.
   * </p>
   *
   * @param absoluteTolerance absolute tolerance in the declared action unit
   * @param relativeTolerance scale-aware relative tolerance
   * @param toleranceProvenance evidence source for the selected tolerance
   * @return immutable action with the selected read-back tolerance
   */
  public ProcessModelOperatingAction withReadBackTolerance(double absoluteTolerance, double relativeTolerance,
      String toleranceProvenance) {
    return new ProcessModelOperatingAction(id, name, address, unit, provenance, valueSemantics, lowerBound, upperBound,
        allowedValues, absoluteTolerance, relativeTolerance, toleranceProvenance);
  }

  /** @return stable machine-readable identifier */
  public String getId() {
    return id;
  }

  /** @return human-readable action name */
  public String getName() {
    return name;
  }

  /** @return area-qualified automation address */
  public String getAddress() {
    return address;
  }

  /** @return declared unit */
  public String getUnit() {
    return unit;
  }

  /** @return evidence source for bounds or allowed values */
  public String getProvenance() {
    return provenance;
  }

  /** @return absolute read-back tolerance in the declared action unit */
  public double getReadBackAbsoluteTolerance() {
    return readBackAbsoluteTolerance;
  }

  /** @return scale-aware relative read-back tolerance */
  public double getReadBackRelativeTolerance() {
    return readBackRelativeTolerance;
  }

  /** @return evidence source for the selected read-back tolerance */
  public String getReadBackToleranceProvenance() {
    return readBackToleranceProvenance;
  }

  /** @return continuous or discrete value semantics */
  public ValueSemantics getValueSemantics() {
    return valueSemantics;
  }

  /** @return inclusive lower bound */
  public double getLowerBound() {
    return lowerBound;
  }

  /** @return inclusive upper bound */
  public double getUpperBound() {
    return upperBound;
  }

  /**
   * Gets exact allowed values for a discrete action.
   *
   * @return defensive allowed-value array, empty for a continuous action
   */
  public double[] getAllowedValues() {
    return Arrays.copyOf(allowedValues, allowedValues.length);
  }

  /** @return true for an enumerated discrete action */
  public boolean isDiscrete() {
    return valueSemantics == ValueSemantics.DISCRETE;
  }

  /**
   * Checks a candidate against the declared action domain without modifying a model.
   *
   * @param value candidate value
   * @return true only when the candidate is finite and declared
   */
  public boolean accepts(double value) {
    if (!Double.isFinite(value)) {
      return false;
    }
    if (valueSemantics == ValueSemantics.CONTINUOUS) {
      return value >= lowerBound && value <= upperBound;
    }
    for (double allowedValue : allowedValues) {
      if (sameDouble(value, allowedValue)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Inspects whether the action target is readable in the supplied model.
   *
   * <p>
   * A readable finite baseline remains capturable even when it lies outside the candidate domain; this permits exact
   * restoration of an existing brownfield state. The assessment reports that condition explicitly and never proves
   * writability or process feasibility.
   * </p>
   *
   * @param model process model to inspect
   * @return immutable capability assessment
   */
  public CapabilityAssessment inspectCapability(ProcessModel model) {
    if (model == null) {
      return CapabilityAssessment.unavailable("Process model must not be null");
    }
    try {
      double currentValue = model.getVariableValue(address, unit);
      if (!Double.isFinite(currentValue)) {
        return CapabilityAssessment.unavailable("Automation address returned a non-finite current value");
      }
      boolean withinDomain = accepts(currentValue);
      List<String> diagnostics = new ArrayList<String>();
      diagnostics.add("Automation address is readable in the declared unit");
      if (!withinDomain) {
        diagnostics.add("Current value is outside the candidate domain but remains eligible for restoration");
      }
      diagnostics.add("Readability does not prove process feasibility or operational approval");
      return new CapabilityAssessment(true, currentValue, withinDomain, diagnostics);
    } catch (RuntimeException exception) {
      return CapabilityAssessment.unavailable("Automation address is unavailable: " + safeMessage(exception));
    }
  }

  /**
   * Captures the current value for later identity-checked restoration.
   *
   * @param model process model
   * @return immutable action state
   * @throws IllegalStateException when the address cannot be read as a finite value
   */
  public ActionState capture(ProcessModel model) {
    CapabilityAssessment capability = inspectCapability(model);
    if (!capability.isAvailable()) {
      throw new IllegalStateException(capability.getDiagnostics().get(0));
    }
    return new ActionState(this, capability.getCurrentValue());
  }

  /**
   * Applies a valid candidate without running the process model.
   *
   * <p>
   * Invalid candidates are rejected without writing. After a write, the value is read back. A write/read-back failure
   * triggers a best-effort rollback to the captured pre-write value and is reported rather than hidden.
   * </p>
   *
   * @param model process model
   * @param candidateValue candidate action value
   * @return immutable application result
   */
  public ApplicationResult apply(ProcessModel model, double candidateValue) {
    if (!accepts(candidateValue)) {
      return ApplicationResult.rejected(id, candidateValue, readBackTolerance(candidateValue),
          readBackToleranceProvenance, "Candidate is outside the declared operating-action domain");
    }
    final ActionState priorState;
    try {
      priorState = capture(model);
    } catch (RuntimeException exception) {
      return ApplicationResult.rejected(id, candidateValue, readBackTolerance(candidateValue),
          readBackToleranceProvenance, "Action target is unavailable: " + safeMessage(exception));
    }

    try {
      model.setVariableValue(address, candidateValue, unit);
      double readBack = model.getVariableValue(address, unit);
      if (!matchesReadBack(candidateValue, readBack)) {
        boolean rolledBack = restoreAfterFailure(model, priorState);
        return ApplicationResult.failed(id, candidateValue, readBack, readBackTolerance(candidateValue),
            readBackToleranceProvenance, priorState, rolledBack,
            "Automation read-back does not match the requested candidate");
      }
      return ApplicationResult.applied(id, candidateValue, readBack, readBackTolerance(candidateValue),
          readBackToleranceProvenance, priorState);
    } catch (RuntimeException exception) {
      boolean rolledBack = restoreAfterFailure(model, priorState);
      return ApplicationResult.failed(id, candidateValue, Double.NaN, readBackTolerance(candidateValue),
          readBackToleranceProvenance, priorState, rolledBack, "Action application failed: " + safeMessage(exception));
    }
  }

  /**
   * Applies a candidate or throws when it is rejected.
   *
   * <p>
   * This method is useful as an external-evaluator setter because {@link ProcessModelSimulationEvaluator} converts the
   * exception into explicit failed-evaluation evidence.
   * </p>
   *
   * @param model process model
   * @param candidateValue candidate action value
   * @return successful immutable application result
   * @throws IllegalArgumentException when the candidate cannot be applied and verified
   */
  public ApplicationResult applyOrThrow(ProcessModel model, double candidateValue) {
    ApplicationResult result = apply(model, candidateValue);
    if (!result.isApplied()) {
      throw new IllegalArgumentException(result.getDiagnostic());
    }
    return result;
  }

  /**
   * Restores an identity-matched captured state without running the model.
   *
   * @param model process model
   * @param state state previously captured by this exact action definition
   * @return immutable restoration result
   * @throws IllegalArgumentException for a null, foreign, or stale action state
   */
  public ApplicationResult restore(ProcessModel model, ActionState state) {
    if (state == null || !state.matches(this)) {
      throw new IllegalArgumentException("Action state does not match the complete operating-action identity");
    }
    try {
      model.setVariableValue(address, state.getValue(), unit);
      double readBack = model.getVariableValue(address, unit);
      if (!matchesReadBack(state.getValue(), readBack)) {
        return ApplicationResult.failed(id, state.getValue(), readBack, readBackTolerance(state.getValue()),
            readBackToleranceProvenance, state, false, "Restoration read-back does not match the captured value");
      }
      return ApplicationResult.applied(id, state.getValue(), readBack, readBackTolerance(state.getValue()),
          readBackToleranceProvenance, state);
    } catch (RuntimeException exception) {
      return ApplicationResult.failed(id, state.getValue(), Double.NaN, readBackTolerance(state.getValue()),
          readBackToleranceProvenance, state, false, "Action restoration failed: " + safeMessage(exception));
    }
  }

  /**
   * Registers this action as a parameter in an external process-model evaluator.
   *
   * <p>
   * Continuous actions expose their bounds. Discrete actions expose the minimum and maximum only as an optimizer vector
   * envelope; intermediate values still fail closed in the registered setter and callers must enumerate
   * {@link ActionParameterBinding#getAllowedValues()}. The current readable model value is retained as the parameter
   * initial value.
   * </p>
   *
   * @param evaluator evaluator to extend
   * @return immutable binding metadata
   * @throws IllegalArgumentException if the evaluator is null
   * @throws IllegalStateException if the action target is unavailable
   */
  public ActionParameterBinding registerWith(ProcessModelSimulationEvaluator evaluator) {
    if (evaluator == null) {
      throw new IllegalArgumentException("Process-model evaluator must not be null");
    }
    final ProcessModelOperatingAction action = this;
    ActionState initialState = capture(evaluator.getProcessModel());
    if (!accepts(initialState.getValue())) {
      throw new IllegalStateException(
          "The current model value is outside the action domain and is not a valid optimizer initial candidate");
    }
    int parameterIndex = evaluator.getParameterCount();
    evaluator.addParameterWithSetter(name, new BiConsumer<ProcessModel, Double>() {
      /** Serialization version is not required because evaluator setters are transient. */
      @Override
      public void accept(ProcessModel model, Double value) {
        action.applyOrThrow(model, value.doubleValue());
      }
    }, lowerBound, upperBound, unit);
    ProcessModelSimulationEvaluator.ParameterDefinition parameter = evaluator.getParameters().get(parameterIndex);
    parameter.setAddress(address);
    parameter.setInitialValue(initialState.getValue());
    parameter.setClampToBounds(false);
    return new ActionParameterBinding(this, parameterIndex, initialState.getValue());
  }

  /** Best-effort rollback used only after an attempted write failed verification. */
  private boolean restoreAfterFailure(ProcessModel model, ActionState priorState) {
    try {
      model.setVariableValue(address, priorState.getValue(), unit);
      double readBack = model.getVariableValue(address, unit);
      return matchesReadBack(priorState.getValue(), readBack);
    } catch (RuntimeException exception) {
      return false;
    }
  }

  /** Checks automation read-back against the action-specific evidenced tolerance. */
  private boolean matchesReadBack(double expected, double actual) {
    return Double.isFinite(actual) && Math.abs(expected - actual) <= readBackTolerance(expected);
  }

  /** Calculates the allowed read-back tolerance for one expected value. */
  private double readBackTolerance(double expected) {
    double scale = Math.max(1.0, Math.abs(expected));
    return Math.max(readBackAbsoluteTolerance, readBackRelativeTolerance * scale);
  }

  /** Compares this definition with all identity-bearing fields in another definition. */
  private boolean sameIdentity(ProcessModelOperatingAction other) {
    return other != null && id.equals(other.id) && name.equals(other.name) && address.equals(other.address)
        && unit.equals(other.unit) && provenance.equals(other.provenance) && valueSemantics == other.valueSemantics
        && sameDouble(lowerBound, other.lowerBound) && sameDouble(upperBound, other.upperBound)
        && Arrays.equals(toLongBits(allowedValues), toLongBits(other.allowedValues))
        && sameDouble(readBackAbsoluteTolerance, other.readBackAbsoluteTolerance)
        && sameDouble(readBackRelativeTolerance, other.readBackRelativeTolerance)
        && readBackToleranceProvenance.equals(other.readBackToleranceProvenance);
  }

  /** Converts doubles to exact bit identities. */
  private static long[] toLongBits(double[] values) {
    long[] bits = new long[values.length];
    for (int index = 0; index < values.length; index++) {
      bits[index] = Double.doubleToLongBits(values[index]);
    }
    return bits;
  }

  /** Validates and trims a required identity string. */
  private static String requireText(String value, String description) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(description + " must not be blank");
    }
    return value.trim();
  }

  /** Compares doubles by their canonical bit representation. */
  private static boolean sameDouble(double first, double second) {
    return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
  }

  /** Returns a useful message without exposing a null exception message. */
  private static String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.trim().isEmpty() ? exception.getClass().getSimpleName() : message;
  }

  /** Immutable capability inspection result. */
  public static final class CapabilityAssessment implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Whether the exact action target is readable as a finite value. */
    private final boolean available;

    /** Current value, or NaN when unavailable. */
    private final double currentValue;

    /** Whether the current value belongs to the candidate domain. */
    private final boolean currentValueWithinDomain;

    /** Immutable diagnostics. */
    private final List<String> diagnostics;

    /** Creates a capability result. */
    private CapabilityAssessment(boolean available, double currentValue, boolean currentValueWithinDomain,
        List<String> diagnostics) {
      this.available = available;
      this.currentValue = currentValue;
      this.currentValueWithinDomain = currentValueWithinDomain;
      this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
    }

    /** Creates an unavailable result. */
    private static CapabilityAssessment unavailable(String diagnostic) {
      return new CapabilityAssessment(false, Double.NaN, false, Collections.singletonList(diagnostic));
    }

    /** @return true when the action target is readable */
    public boolean isAvailable() {
      return available;
    }

    /** @return current value, or NaN when unavailable */
    public double getCurrentValue() {
      return currentValue;
    }

    /** @return true when the current value belongs to the candidate domain */
    public boolean isCurrentValueWithinDomain() {
      return currentValueWithinDomain;
    }

    /** @return immutable diagnostics */
    public List<String> getDiagnostics() {
      return diagnostics;
    }
  }

  /** Immutable identity-bound captured operating state. */
  public static final class ActionState implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Complete immutable action definition. */
    private final ProcessModelOperatingAction action;

    /** Captured value in the action unit. */
    private final double value;

    /** Creates an action state. */
    private ActionState(ProcessModelOperatingAction action, double value) {
      this.action = action;
      this.value = value;
    }

    /** @return complete action definition */
    public ProcessModelOperatingAction getAction() {
      return action;
    }

    /** @return captured value in the action unit */
    public double getValue() {
      return value;
    }

    /** Checks complete identity against an action definition. */
    private boolean matches(ProcessModelOperatingAction candidate) {
      return action.sameIdentity(candidate);
    }
  }

  /** Immutable action application or restoration result. */
  public static final class ApplicationResult implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Stable action identifier. */
    private final String actionId;

    /** Requested value. */
    private final double requestedValue;

    /** Read-back value, or NaN when no verified read-back exists. */
    private final double readBackValue;

    /** Absolute difference between requested and read-back values, or NaN. */
    private final double readBackResidual;

    /** Allowed absolute read-back tolerance in the action unit. */
    private final double readBackTolerance;

    /** Evidence source for the selected read-back tolerance. */
    private final String readBackToleranceProvenance;

    /** Whether the requested value was applied and verified. */
    private final boolean applied;

    /** Whether a failed write was rolled back and verified. */
    private final boolean rolledBackAfterFailure;

    /** Pre-write state, or null when no write was attempted. */
    private final ActionState priorState;

    /** Diagnostic. */
    private final String diagnostic;

    /** Creates an application result. */
    private ApplicationResult(String actionId, double requestedValue, double readBackValue, double readBackTolerance,
        String readBackToleranceProvenance, boolean applied, boolean rolledBackAfterFailure, ActionState priorState,
        String diagnostic) {
      this.actionId = actionId;
      this.requestedValue = requestedValue;
      this.readBackValue = readBackValue;
      this.readBackResidual = Double.isFinite(readBackValue) ? Math.abs(requestedValue - readBackValue) : Double.NaN;
      this.readBackTolerance = readBackTolerance;
      this.readBackToleranceProvenance = readBackToleranceProvenance;
      this.applied = applied;
      this.rolledBackAfterFailure = rolledBackAfterFailure;
      this.priorState = priorState;
      this.diagnostic = diagnostic + readBackEvidence(requestedValue, readBackValue, this.readBackResidual,
          readBackTolerance, readBackToleranceProvenance);
    }

    /** Creates a result for a candidate rejected before writing. */
    private static ApplicationResult rejected(String actionId, double requestedValue, double readBackTolerance,
        String readBackToleranceProvenance, String diagnostic) {
      return new ApplicationResult(actionId, requestedValue, Double.NaN, readBackTolerance, readBackToleranceProvenance,
          false, false, null, diagnostic);
    }

    /** Creates a failed write or restoration result. */
    private static ApplicationResult failed(String actionId, double requestedValue, double readBackValue,
        double readBackTolerance, String readBackToleranceProvenance, ActionState priorState, boolean rolledBack,
        String diagnostic) {
      return new ApplicationResult(actionId, requestedValue, readBackValue, readBackTolerance,
          readBackToleranceProvenance, false, rolledBack, priorState, diagnostic);
    }

    /** Creates a successful result. */
    private static ApplicationResult applied(String actionId, double requestedValue, double readBackValue,
        double readBackTolerance, String readBackToleranceProvenance, ActionState priorState) {
      return new ApplicationResult(actionId, requestedValue, readBackValue, readBackTolerance,
          readBackToleranceProvenance, true, false, priorState,
          "Action value was applied and verified; the process model was not run.");
    }

    /** Formats complete numeric write/read-back evidence for diagnostics. */
    private static String readBackEvidence(double requestedValue, double readBackValue, double readBackResidual,
        double readBackTolerance, String readBackToleranceProvenance) {
      return " Expected=" + requestedValue + ", actual=" + readBackValue + ", absolute residual=" + readBackResidual
          + ", allowed tolerance=" + readBackTolerance + ", tolerance provenance=" + readBackToleranceProvenance + ".";
    }

    /** @return stable action identifier */
    public String getActionId() {
      return actionId;
    }

    /** @return requested value */
    public double getRequestedValue() {
      return requestedValue;
    }

    /** @return read-back value, or NaN when unavailable */
    public double getReadBackValue() {
      return readBackValue;
    }

    /** @return absolute requested/read-back residual, or NaN when no read-back exists */
    public double getReadBackResidual() {
      return readBackResidual;
    }

    /** @return allowed absolute read-back tolerance in the action unit */
    public double getReadBackTolerance() {
      return readBackTolerance;
    }

    /** @return evidence source for the selected read-back tolerance */
    public String getReadBackToleranceProvenance() {
      return readBackToleranceProvenance;
    }

    /** @return true when the requested value was applied and verified */
    public boolean isApplied() {
      return applied;
    }

    /** @return true when a failed write was rolled back and verified */
    public boolean isRolledBackAfterFailure() {
      return rolledBackAfterFailure;
    }

    /** @return pre-write state, or null when no write was attempted */
    public ActionState getPriorState() {
      return priorState;
    }

    /** @return action diagnostic */
    public String getDiagnostic() {
      return diagnostic;
    }
  }

  /** Immutable metadata for one action registered with an evaluator parameter vector. */
  public static final class ActionParameterBinding implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Complete action definition. */
    private final ProcessModelOperatingAction action;

    /** Parameter-vector index. */
    private final int parameterIndex;

    /** Initial value captured at registration. */
    private final double initialValue;

    /** Creates a parameter binding. */
    private ActionParameterBinding(ProcessModelOperatingAction action, int parameterIndex, double initialValue) {
      this.action = action;
      this.parameterIndex = parameterIndex;
      this.initialValue = initialValue;
    }

    /** @return complete action definition */
    public ProcessModelOperatingAction getAction() {
      return action;
    }

    /** @return parameter-vector index */
    public int getParameterIndex() {
      return parameterIndex;
    }

    /** @return initial value captured at registration */
    public double getInitialValue() {
      return initialValue;
    }

    /** @return exact allowed values, empty for a continuous action */
    public double[] getAllowedValues() {
      return action.getAllowedValues();
    }
  }
}
