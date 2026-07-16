package neqsim.process.engineering.automation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Governed decision-variable, objective and constraint definition for bounded engineering automation. */
public final class EngineeringAutomationStudy implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum ObjectiveSense {
    MINIMIZE, MAXIMIZE
  }

  public enum ConstraintSeverity {
    HARD, ADVISORY
  }

  /** One bounded variable mapped to a canonical graph object and a supported process-model address. */
  public static final class DecisionVariable implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String graphNodeId;
    private final String processAddress;
    private final double lowerBound;
    private final double upperBound;
    private final double initialValue;
    private final String unit;
    private int screeningLevels = 3;

    public DecisionVariable(String id, String graphNodeId, String processAddress, double lowerBound, double upperBound,
        double initialValue, String unit) {
      this.id = requireText(id, "id");
      this.graphNodeId = requireText(graphNodeId, "graphNodeId");
      this.processAddress = requireText(processAddress, "processAddress");
      if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound) || lowerBound >= upperBound) {
        throw new IllegalArgumentException("Decision-variable bounds must be finite and increasing");
      }
      if (!Double.isFinite(initialValue) || initialValue < lowerBound || initialValue > upperBound) {
        throw new IllegalArgumentException("Decision-variable initial value must be inside its bounds");
      }
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.initialValue = initialValue;
      this.unit = requireText(unit, "unit");
    }

    public DecisionVariable setScreeningLevels(int value) {
      if (value < 2) {
        throw new IllegalArgumentException("screeningLevels must be at least 2");
      }
      screeningLevels = value;
      return this;
    }

    public String getId() {
      return id;
    }

    public String getGraphNodeId() {
      return graphNodeId;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("graphNodeId", graphNodeId);
      result.put("processAddress", processAddress);
      result.put("lowerBound", Double.valueOf(lowerBound));
      result.put("upperBound", Double.valueOf(upperBound));
      result.put("initialValue", Double.valueOf(initialValue));
      result.put("unit", unit);
      result.put("screeningLevels", Integer.valueOf(screeningLevels));
      return result;
    }
  }

  /** One weighted metric objective. */
  public static final class Objective implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String metricKey;
    private final ObjectiveSense sense;
    private final double weight;

    public Objective(String id, String metricKey, ObjectiveSense sense, double weight) {
      this.id = requireText(id, "id");
      this.metricKey = requireText(metricKey, "metricKey");
      if (sense == null || !Double.isFinite(weight) || weight <= 0.0) {
        throw new IllegalArgumentException("Objective sense and a positive finite weight are required");
      }
      this.sense = sense;
      this.weight = weight;
    }

    public String getId() {
      return id;
    }

    public String getMetricKey() {
      return metricKey;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("metricKey", metricKey);
      result.put("sense", sense.name());
      result.put("weight", Double.valueOf(weight));
      return result;
    }
  }

  /** One hard or advisory metric acceptance constraint. */
  public static final class Constraint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String metricKey;
    private final Double lowerBound;
    private final Double upperBound;
    private final String unit;
    private final ConstraintSeverity severity;
    private final String standardsReference;

    public Constraint(String id, String metricKey, Double lowerBound, Double upperBound, String unit,
        ConstraintSeverity severity, String standardsReference) {
      this.id = requireText(id, "id");
      this.metricKey = requireText(metricKey, "metricKey");
      if (lowerBound == null && upperBound == null) {
        throw new IllegalArgumentException("Constraint requires a lower or upper bound");
      }
      if (lowerBound != null && !Double.isFinite(lowerBound.doubleValue())
          || upperBound != null && !Double.isFinite(upperBound.doubleValue())
          || lowerBound != null && upperBound != null && lowerBound.doubleValue() > upperBound.doubleValue()) {
        throw new IllegalArgumentException("Constraint bounds must be finite and ordered");
      }
      if (severity == null) {
        throw new IllegalArgumentException("severity must not be null");
      }
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.unit = requireText(unit, "unit");
      this.severity = severity;
      this.standardsReference = standardsReference == null ? "" : standardsReference.trim();
    }

    public String getId() {
      return id;
    }

    public String getMetricKey() {
      return metricKey;
    }

    public Double getLowerBound() {
      return lowerBound;
    }

    public Double getUpperBound() {
      return upperBound;
    }

    public ConstraintSeverity getSeverity() {
      return severity;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("metricKey", metricKey);
      result.put("lowerBound", lowerBound);
      result.put("upperBound", upperBound);
      result.put("unit", unit);
      result.put("severity", severity.name());
      result.put("standardsReference", standardsReference);
      return result;
    }
  }

  private final String id;
  private final String name;
  private final List<DecisionVariable> decisionVariables = new ArrayList<DecisionVariable>();
  private final List<Objective> objectives = new ArrayList<Objective>();
  private final List<Constraint> constraints = new ArrayList<Constraint>();

  public EngineeringAutomationStudy(String id, String name) {
    this.id = requireText(id, "id");
    this.name = requireText(name, "name");
  }

  public EngineeringAutomationStudy addDecisionVariable(DecisionVariable value) {
    if (value == null) {
      throw new IllegalArgumentException("decision variable must not be null");
    }
    for (DecisionVariable existing : decisionVariables) {
      if (existing.getId().equals(value.getId())) {
        throw new IllegalArgumentException("Duplicate decision variable " + value.getId());
      }
    }
    decisionVariables.add(value);
    return this;
  }

  public EngineeringAutomationStudy addObjective(Objective value) {
    if (value == null) {
      throw new IllegalArgumentException("objective must not be null");
    }
    for (Objective existing : objectives) {
      if (existing.getId().equals(value.getId())) {
        throw new IllegalArgumentException("Duplicate objective " + value.getId());
      }
    }
    objectives.add(value);
    return this;
  }

  public EngineeringAutomationStudy addConstraint(Constraint value) {
    if (value == null) {
      throw new IllegalArgumentException("constraint must not be null");
    }
    for (Constraint existing : constraints) {
      if (existing.getId().equals(value.getId())) {
        throw new IllegalArgumentException("Duplicate constraint " + value.getId());
      }
    }
    constraints.add(value);
    return this;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public List<DecisionVariable> getDecisionVariables() {
    return Collections.unmodifiableList(decisionVariables);
  }

  public List<Objective> getObjectives() {
    return Collections.unmodifiableList(objectives);
  }

  public List<Constraint> getConstraints() {
    return Collections.unmodifiableList(constraints);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
