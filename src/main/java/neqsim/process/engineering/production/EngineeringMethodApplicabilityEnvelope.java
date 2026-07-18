package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structured and machine-evaluable applicability envelope for one method version. */
public final class EngineeringMethodApplicabilityEnvelope implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Result of evaluating supplied project service conditions against an envelope. */
  public static final class Assessment implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final boolean contextComplete;
    private final boolean withinEnvelope;
    private final boolean extrapolationProhibited;
    private final List<String> findings;

    Assessment(boolean contextComplete, boolean withinEnvelope, boolean extrapolationProhibited,
        List<String> findings) {
      this.contextComplete = contextComplete;
      this.withinEnvelope = withinEnvelope;
      this.extrapolationProhibited = extrapolationProhibited;
      this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
    }

    public boolean isContextComplete() {
      return contextComplete;
    }

    public boolean isWithinEnvelope() {
      return withinEnvelope;
    }

    public boolean isExecutionPermitted() {
      return contextComplete && (withinEnvelope || !extrapolationProhibited);
    }

    public List<String> getFindings() {
      return findings;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("contextComplete", Boolean.valueOf(contextComplete));
      result.put("withinEnvelope", Boolean.valueOf(withinEnvelope));
      result.put("extrapolationProhibited", Boolean.valueOf(extrapolationProhibited));
      result.put("executionPermitted", Boolean.valueOf(isExecutionPermitted()));
      result.put("findings", new ArrayList<String>(findings));
      return result;
    }
  }

  /** Inclusive numerical range in one controlled engineering unit. */
  public static final class NumericRange implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String unit;
    private final double minimum;
    private final double maximum;

    NumericRange(String name, String unit, double minimum, double maximum) {
      this.name = text(name, "range name");
      this.unit = text(unit, "range unit");
      if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum) {
        throw new IllegalArgumentException("range limits must be finite and maximum must be at least minimum");
      }
      this.minimum = minimum;
      this.maximum = maximum;
    }

    boolean contains(EngineeringMethodServiceContext.Quantity value) {
      return unit.equals(value.getUnit()) && value.getValue() >= minimum && value.getValue() <= maximum;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("name", name);
      result.put("unit", unit);
      result.put("minimum", Double.valueOf(minimum));
      result.put("maximum", Double.valueOf(maximum));
      result.put("inclusive", Boolean.TRUE);
      return result;
    }
  }

  private final String id;
  private final String revision;
  private final Set<String> requiredInputs = new LinkedHashSet<String>();
  private final Map<String, NumericRange> numericRanges = new LinkedHashMap<String, NumericRange>();
  private final Map<String, Set<String>> allowedCategories = new LinkedHashMap<String, Set<String>>();
  private final List<String> knownLimitations = new ArrayList<String>();
  private boolean extrapolationProhibited = true;
  private String uncertaintyBasis = "";

  public EngineeringMethodApplicabilityEnvelope(String id, String revision) {
    this.id = text(id, "id");
    this.revision = text(revision, "revision");
  }

  public EngineeringMethodApplicabilityEnvelope requireInput(String name) {
    requiredInputs.add(text(name, "required input"));
    return this;
  }

  public EngineeringMethodApplicabilityEnvelope numericRange(String name, String unit, double minimum,
      double maximum) {
    NumericRange range = new NumericRange(name, unit, minimum, maximum);
    numericRanges.put(range.name, range);
    requiredInputs.add(range.name);
    return this;
  }

  public EngineeringMethodApplicabilityEnvelope allowedValues(String name, String... values) {
    String key = text(name, "category name");
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("At least one allowed category is required");
    }
    Set<String> allowed = new LinkedHashSet<String>();
    for (String value : Arrays.asList(values)) {
      allowed.add(text(value, "allowed category"));
    }
    allowedCategories.put(key, allowed);
    requiredInputs.add(key);
    return this;
  }

  public EngineeringMethodApplicabilityEnvelope knownLimitation(String value) {
    String limitation = text(value, "known limitation");
    if (!knownLimitations.contains(limitation)) {
      knownLimitations.add(limitation);
    }
    return this;
  }

  public EngineeringMethodApplicabilityEnvelope uncertaintyBasis(String value) {
    uncertaintyBasis = text(value, "uncertainty basis");
    return this;
  }

  public EngineeringMethodApplicabilityEnvelope prohibitExtrapolation(boolean value) {
    extrapolationProhibited = value;
    return this;
  }

  public boolean isComplete() {
    return !requiredInputs.isEmpty() && (!numericRanges.isEmpty() || !allowedCategories.isEmpty())
        && !knownLimitations.isEmpty() && !uncertaintyBasis.isEmpty();
  }

  public Assessment assess(EngineeringMethodServiceContext context) {
    if (context == null) {
      throw new IllegalArgumentException("service context must not be null");
    }
    List<String> findings = new ArrayList<String>();
    boolean complete = true;
    boolean within = true;
    for (String input : requiredInputs) {
      if (!context.getSuppliedInputs().contains(input)) {
        complete = false;
        findings.add("Missing required service input " + input);
      }
    }
    for (Map.Entry<String, NumericRange> item : numericRanges.entrySet()) {
      EngineeringMethodServiceContext.Quantity value = context.getNumericValues().get(item.getKey());
      if (value == null) {
        continue;
      }
      NumericRange range = item.getValue();
      if (!range.unit.equals(value.getUnit())) {
        complete = false;
        findings.add("Unit mismatch for " + item.getKey() + ": expected " + range.unit + " but received "
            + value.getUnit());
      } else if (!range.contains(value)) {
        within = false;
        findings.add(item.getKey() + "=" + value.getValue() + " " + value.getUnit()
            + " is outside the inclusive range [" + range.minimum + ", " + range.maximum + "]");
      }
    }
    for (Map.Entry<String, Set<String>> item : allowedCategories.entrySet()) {
      String value = context.getCategoricalValues().get(item.getKey());
      if (value != null && !item.getValue().contains(value)) {
        within = false;
        findings.add(item.getKey() + "=" + value + " is not one of " + item.getValue());
      }
    }
    if (complete && within) {
      findings.add("Supplied service context is within the declared applicability envelope");
    }
    return new Assessment(complete, within, extrapolationProhibited, findings);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "engineering_method_applicability_envelope.v1");
    result.put("id", id);
    result.put("revision", revision);
    result.put("requiredInputs", new ArrayList<String>(requiredInputs));
    List<Map<String, Object>> ranges = new ArrayList<Map<String, Object>>();
    for (NumericRange range : numericRanges.values()) {
      ranges.add(range.toMap());
    }
    result.put("numericRanges", ranges);
    Map<String, Object> categories = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Set<String>> value : allowedCategories.entrySet()) {
      categories.put(value.getKey(), new ArrayList<String>(value.getValue()));
    }
    result.put("allowedCategories", categories);
    result.put("knownLimitations", new ArrayList<String>(knownLimitations));
    result.put("uncertaintyBasis", uncertaintyBasis);
    result.put("extrapolationProhibited", Boolean.valueOf(extrapolationProhibited));
    result.put("complete", Boolean.valueOf(isComplete()));
    return result;
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
