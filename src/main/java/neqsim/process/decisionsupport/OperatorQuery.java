package neqsim.process.decisionsupport;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;

/**
 * Structured representation of an operator's engineering question for decision support.
 *
 * <p>
 * Models questions such as "Can we run at X with today's gas?" or "What is the safest derate option
 * now?" in a company-independent, serializable format that can be processed by
 * {@link DecisionSupportEngine}.
 * </p>
 *
 * <p>
 * Use the {@link Builder} to construct queries:
 * </p>
 *
 * <pre>
 * OperatorQuery query = OperatorQuery.builder().queryType(QueryType.RATE_CHANGE_FEASIBILITY)
 *     .parameter("targetFlowRate", 150000.0).parameter("flowRateUnit", "kg/hr")
 *     .requestedBy("operator-shift-A").urgency(Urgency.ROUTINE).build();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OperatorQuery implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = GsonFactory.instance();

  private final String queryId;
  private final QueryType queryType;
  private final Map<String, Object> parameters;
  private final Map<String, Double> currentConditions;
  private final List<String> constraintNames;
  private final String requestedBy;
  private final Urgency urgency;
  private final Instant timestamp;
  private String description;

  /**
   * Types of operator queries supported by the decision support engine.
   */
  public enum QueryType {
    /** Can we run at a different rate with current gas quality? */
    RATE_CHANGE_FEASIBILITY,

    /** What happens if the gas composition changes? */
    GAS_QUALITY_IMPACT,

    /** What is the safest derate option given current constraints? */
    DERATE_OPTIONS,

    /** Does the current product meet export/sales specifications? */
    PRODUCT_SPEC_CHECK,

    /** What is the status of a specific piece of equipment relative to its limits? */
    EQUIPMENT_STATUS,

    /** Generic what-if: change a parameter and see the impact. */
    WHAT_IF,

    /** Custom query handled by a user-registered workflow. */
    CUSTOM
  }

  /**
   * Urgency level of the query.
   */
  public enum Urgency {
    /** Routine check, no time pressure. */
    ROUTINE,

    /** Priority question, answer needed soon. */
    PRIORITY,

    /** Urgent question, answer needed immediately. */
    URGENT
  }

  /**
   * Private constructor — use {@link Builder} to create instances.
   *
   * @param builder the builder with configured values
   */
  private OperatorQuery(Builder builder) {
    this.queryId = builder.queryId;
    this.queryType = builder.queryType;
    this.parameters = new HashMap<>(builder.parameters);
    this.currentConditions = new HashMap<>(builder.currentConditions);
    this.constraintNames = new ArrayList<>(builder.constraintNames);
    this.requestedBy = builder.requestedBy;
    this.urgency = builder.urgency;
    this.timestamp = builder.timestamp;
    this.description = builder.description;
  }

  /**
   * Creates a new builder for constructing an OperatorQuery.
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Deserializes an OperatorQuery from JSON.
   *
   * @param json the JSON string
   * @return the deserialized query
   */
  public static OperatorQuery fromJson(String json) {
    return GSON.fromJson(json, OperatorQuery.class);
  }

  /**
   * Serializes this query to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return GSON.toJson(this);
  }

  /**
   * Gets the unique query identifier.
   *
   * @return the query ID
   */
  public String getQueryId() {
    return queryId;
  }

  /**
   * Gets the type of query.
   *
   * @return the query type
   */
  public QueryType getQueryType() {
    return queryType;
  }

  /**
   * Gets query-specific parameters.
   *
   * @return unmodifiable map of parameter name to value
   */
  public Map<String, Object> getParameters() {
    return java.util.Collections.unmodifiableMap(parameters);
  }

  /**
   * Gets a specific parameter value.
   *
   * @param name the parameter name
   * @return the parameter value, or null if not set
   */
  public Object getParameter(String name) {
    return parameters.get(name);
  }

  /**
   * Gets a parameter as a double value.
   *
   * @param name the parameter name
   * @param defaultValue value to return if parameter is not set or not a number
   * @return the parameter value as double
   */
  public double getParameterAsDouble(String name, double defaultValue) {
    Object value = parameters.get(name);
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    return defaultValue;
  }

  /**
   * Gets a parameter as a string value.
   *
   * @param name the parameter name
   * @param defaultValue value to return if parameter is not set
   * @return the parameter value as string
   */
  public String getParameterAsString(String name, String defaultValue) {
    Object value = parameters.get(name);
    if (value instanceof String) {
      return (String) value;
    }
    if (value != null) {
      return value.toString();
    }
    return defaultValue;
  }

  /**
   * Gets the current operating conditions.
   *
   * @return unmodifiable map of condition name to value
   */
  public Map<String, Double> getCurrentConditions() {
    return java.util.Collections.unmodifiableMap(currentConditions);
  }

  /**
   * Gets the list of constraint names to check.
   *
   * @return unmodifiable list of constraint names
   */
  public List<String> getConstraintNames() {
    return java.util.Collections.unmodifiableList(constraintNames);
  }

  /**
   * Gets the operator or system that requested this query.
   *
   * @return the requester identifier
   */
  public String getRequestedBy() {
    return requestedBy;
  }

  /**
   * Gets the urgency level of the query.
   *
   * @return the urgency level
   */
  public Urgency getUrgency() {
    return urgency;
  }

  /**
   * Gets the timestamp when the query was created.
   *
   * @return the creation timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the human-readable description of the query.
   *
   * @return the description, or null if not set
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return "OperatorQuery{" + "queryId='" + queryId + '\'' + ", queryType=" + queryType
        + ", urgency=" + urgency + ", parameters=" + parameters.size() + " params"
        + ", requestedBy=" + requestedBy + '}';
  }

  /**
   * Builder for constructing {@link OperatorQuery} instances.
   */
  public static class Builder {
    private String queryId = UUID.randomUUID().toString();
    private QueryType queryType = QueryType.WHAT_IF;
    private final Map<String, Object> parameters = new HashMap<>();
    private final Map<String, Double> currentConditions = new HashMap<>();
    private final List<String> constraintNames = new ArrayList<>();
    private String requestedBy = "unknown";
    private Urgency urgency = Urgency.ROUTINE;
    private Instant timestamp = Instant.now();
    private String description;

    /**
     * Creates a new Builder with default values.
     */
    Builder() {}

    /**
     * Sets the query type.
     *
     * @param queryType the type of query
     * @return this builder
     */
    public Builder queryType(QueryType queryType) {
      this.queryType = queryType;
      return this;
    }

    /**
     * Adds a query parameter.
     *
     * @param name the parameter name
     * @param value the parameter value
     * @return this builder
     */
    public Builder parameter(String name, Object value) {
      this.parameters.put(name, value);
      return this;
    }

    /**
     * Sets all query parameters at once.
     *
     * @param parameters map of parameter names to values
     * @return this builder
     */
    public Builder parameters(Map<String, Object> parameters) {
      this.parameters.putAll(parameters);
      return this;
    }

    /**
     * Adds a current operating condition.
     *
     * @param name the condition name (e.g., "separator.pressure")
     * @param value the current measured value
     * @return this builder
     */
    public Builder currentCondition(String name, double value) {
      this.currentConditions.put(name, value);
      return this;
    }

    /**
     * Sets all current conditions at once.
     *
     * @param conditions map of condition names to values
     * @return this builder
     */
    public Builder currentConditions(Map<String, Double> conditions) {
      this.currentConditions.putAll(conditions);
      return this;
    }

    /**
     * Adds a constraint name to check.
     *
     * @param constraintName the constraint name
     * @return this builder
     */
    public Builder constraintName(String constraintName) {
      this.constraintNames.add(constraintName);
      return this;
    }

    /**
     * Sets the requester identifier.
     *
     * @param requestedBy the requester ID
     * @return this builder
     */
    public Builder requestedBy(String requestedBy) {
      this.requestedBy = requestedBy;
      return this;
    }

    /**
     * Sets the urgency level.
     *
     * @param urgency the urgency level
     * @return this builder
     */
    public Builder urgency(Urgency urgency) {
      this.urgency = urgency;
      return this;
    }

    /**
     * Sets a human-readable description of the query.
     *
     * @param description the query description
     * @return this builder
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * Sets the query ID (defaults to a random UUID).
     *
     * @param queryId the query ID
     * @return this builder
     */
    public Builder queryId(String queryId) {
      this.queryId = queryId;
      return this;
    }

    /**
     * Sets the timestamp (defaults to now).
     *
     * @param timestamp the timestamp
     * @return this builder
     */
    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    /**
     * Builds the OperatorQuery.
     *
     * @return the constructed query
     * @throws IllegalStateException if queryType is null
     */
    public OperatorQuery build() {
      if (queryType == null) {
        throw new IllegalStateException("queryType must not be null");
      }
      return new OperatorQuery(this);
    }
  }
}
