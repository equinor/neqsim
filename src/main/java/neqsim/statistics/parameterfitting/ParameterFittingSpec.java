package neqsim.statistics.parameterfitting;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serializable specification for a complete experimental parameter fitting study.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ParameterFittingSpec implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private String name = "parameter fitting study";
  private ExperimentType experimentType = ExperimentType.GENERIC;
  private ArrayList<FittingParameter> parameters = new ArrayList<FittingParameter>();
  private ObjectiveFunctionType objectiveFunctionType =
      ObjectiveFunctionType.WEIGHTED_LEAST_SQUARES;
  private double robustTuningConstant = 1.345;
  private int maxRobustIterations = 5;
  private int maxNumberOfIterations = 50;
  private int multiStartCount = 1;
  private long randomSeed = 12345L;
  private double trainingFraction = Double.NaN;

  /**
   * Creates an empty specification.
   */
  public ParameterFittingSpec() {}

  /**
   * Creates a named specification.
   *
   * @param name specification name
   */
  public ParameterFittingSpec(String name) {
    this.name = defaultString(name, "parameter fitting study");
  }

  /**
   * Adds a fitting parameter definition.
   *
   * @param parameter fitting parameter definition
   * @return this specification for fluent construction
   * @throws IllegalArgumentException if parameter is null or invalid
   */
  public ParameterFittingSpec addParameter(FittingParameter parameter) {
    if (parameter == null) {
      throw new IllegalArgumentException("parameter cannot be null");
    }
    parameter.validate();
    parameters.add(parameter);
    return this;
  }

  /**
   * Validates this specification.
   *
   * @throws IllegalArgumentException if any field is invalid
   */
  public void validate() {
    if (parameters == null || parameters.isEmpty()) {
      throw new IllegalArgumentException("at least one fitting parameter is required");
    }
    for (int i = 0; i < parameters.size(); i++) {
      if (parameters.get(i) == null) {
        throw new IllegalArgumentException("parameter " + i + " cannot be null");
      }
      parameters.get(i).validate();
    }
    if (robustTuningConstant <= 0.0 || Double.isNaN(robustTuningConstant)
        || Double.isInfinite(robustTuningConstant)) {
      throw new IllegalArgumentException("robustTuningConstant must be positive and finite");
    }
    if (maxRobustIterations <= 0) {
      throw new IllegalArgumentException("maxRobustIterations must be positive");
    }
    if (maxNumberOfIterations <= 0) {
      throw new IllegalArgumentException("maxNumberOfIterations must be positive");
    }
    if (multiStartCount <= 0) {
      throw new IllegalArgumentException("multiStartCount must be positive");
    }
    if (!Double.isNaN(trainingFraction) && (trainingFraction <= 0.0 || trainingFraction >= 1.0)) {
      throw new IllegalArgumentException("trainingFraction must be between 0.0 and 1.0");
    }
  }

  /**
   * Returns initial physical parameter values.
   *
   * @return physical initial parameter values
   */
  @JsonIgnore
  public double[] getInitialGuess() {
    validate();
    double[] values = new double[parameters.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = parameters.get(i).getInitialValue();
    }
    return values;
  }

  /**
   * Returns initial optimizer-space parameter values.
   *
   * @return optimizer-space initial parameter values
   */
  @JsonIgnore
  public double[] getInternalInitialGuess() {
    validate();
    double[] values = new double[parameters.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = parameters.get(i).getInternalInitialValue();
    }
    return values;
  }

  /**
   * Returns parameter names in fitting order.
   *
   * @return parameter names
   */
  @JsonIgnore
  public String[] getParameterNames() {
    validate();
    String[] names = new String[parameters.size()];
    for (int i = 0; i < names.length; i++) {
      names[i] = parameters.get(i).getName();
    }
    return names;
  }

  /**
   * Returns physical parameter bounds.
   *
   * @return bounds where each row is {@code [lower, upper]}
   */
  @JsonIgnore
  public double[][] getBounds() {
    validate();
    double[][] bounds = new double[parameters.size()][2];
    for (int i = 0; i < parameters.size(); i++) {
      bounds[i][0] = parameters.get(i).getLowerBound();
      bounds[i][1] = parameters.get(i).getUpperBound();
    }
    return bounds;
  }

  /**
   * Returns optimizer-space bounds when all parameters have finite transformed bounds.
   *
   * @return optimizer-space bounds, or null when at least one transformed parameter is unbounded
   */
  @JsonIgnore
  public double[][] getInternalBounds() {
    validate();
    double[][] bounds = new double[parameters.size()][2];
    for (int i = 0; i < parameters.size(); i++) {
      double[] parameterBounds = parameters.get(i).getInternalBounds();
      if (parameterBounds == null) {
        return null;
      }
      bounds[i][0] = parameterBounds[0];
      bounds[i][1] = parameterBounds[1];
    }
    return bounds;
  }

  /**
   * Converts optimizer-space parameter values to physical values.
   *
   * @param internalValues optimizer-space parameter values
   * @return physical parameter values
   * @throws IllegalArgumentException if the value count does not match the specification
   */
  public double[] toExternalValues(double[] internalValues) {
    validate();
    if (internalValues == null || internalValues.length != parameters.size()) {
      throw new IllegalArgumentException("internalValues must match the parameter count");
    }
    double[] values = new double[internalValues.length];
    for (int i = 0; i < values.length; i++) {
      values[i] = parameters.get(i).toExternalValue(internalValues[i]);
    }
    return values;
  }

  /**
   * Returns whether at least one parameter uses a non-linear optimizer transform.
   *
   * @return true when at least one parameter is transformed
   */
  @JsonIgnore
  public boolean hasTransformedParameters() {
    validate();
    for (int i = 0; i < parameters.size(); i++) {
      if (parameters.get(i).getTransform().isTransformed()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Serializes this specification as JSON.
   *
   * @return pretty-printed JSON
   * @throws IOException if serialization fails
   */
  public String toJson() throws IOException {
    return createJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
  }

  /**
   * Serializes this specification as YAML.
   *
   * @return YAML text
   * @throws IOException if serialization fails
   */
  public String toYaml() throws IOException {
    return createYamlMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
  }

  /**
   * Saves this specification as JSON.
   *
   * @param file target file
   * @throws IOException if writing fails
   */
  public void saveJson(File file) throws IOException {
    createJsonMapper().writerWithDefaultPrettyPrinter().writeValue(file, this);
  }

  /**
   * Saves this specification as YAML.
   *
   * @param file target file
   * @throws IOException if writing fails
   */
  public void saveYaml(File file) throws IOException {
    createYamlMapper().writerWithDefaultPrettyPrinter().writeValue(file, this);
  }

  /**
   * Reads a specification from JSON text.
   *
   * @param json JSON specification text
   * @return fitting specification
   * @throws IOException if parsing fails
   */
  public static ParameterFittingSpec fromJson(String json) throws IOException {
    ParameterFittingSpec spec = createJsonMapper().readValue(json, ParameterFittingSpec.class);
    spec.validate();
    return spec;
  }

  /**
   * Reads a specification from a JSON file.
   *
   * @param file JSON specification file
   * @return fitting specification
   * @throws IOException if reading or parsing fails
   */
  public static ParameterFittingSpec fromJson(File file) throws IOException {
    ParameterFittingSpec spec = createJsonMapper().readValue(file, ParameterFittingSpec.class);
    spec.validate();
    return spec;
  }

  /**
   * Reads a specification from YAML text.
   *
   * @param yaml YAML specification text
   * @return fitting specification
   * @throws IOException if parsing fails
   */
  public static ParameterFittingSpec fromYaml(String yaml) throws IOException {
    ParameterFittingSpec spec = createYamlMapper().readValue(yaml, ParameterFittingSpec.class);
    spec.validate();
    return spec;
  }

  /**
   * Reads a specification from a YAML file.
   *
   * @param file YAML specification file
   * @return fitting specification
   * @throws IOException if reading or parsing fails
   */
  public static ParameterFittingSpec fromYaml(File file) throws IOException {
    ParameterFittingSpec spec = createYamlMapper().readValue(file, ParameterFittingSpec.class);
    spec.validate();
    return spec;
  }

  /**
   * Returns the specification name.
   *
   * @return specification name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the specification name.
   *
   * @param name specification name
   */
  public void setName(String name) {
    this.name = defaultString(name, "parameter fitting study");
  }

  /**
   * Returns the experiment type.
   *
   * @return experiment type
   */
  public ExperimentType getExperimentType() {
    return experimentType;
  }

  /**
   * Sets the experiment type.
   *
   * @param experimentType experiment type
   */
  public void setExperimentType(ExperimentType experimentType) {
    this.experimentType = experimentType == null ? ExperimentType.GENERIC : experimentType;
  }

  /**
   * Returns an immutable snapshot of the parameter definitions.
   *
   * @return parameter definitions
   */
  public List<FittingParameter> getParameters() {
    return Collections.unmodifiableList(new ArrayList<FittingParameter>(parameters));
  }

  /**
   * Sets the parameter definitions.
   *
   * @param parameters parameter definitions
   */
  public void setParameters(List<FittingParameter> parameters) {
    this.parameters = new ArrayList<FittingParameter>();
    if (parameters != null) {
      this.parameters.addAll(parameters);
    }
  }

  /**
   * Returns the objective function type.
   *
   * @return objective function type
   */
  public ObjectiveFunctionType getObjectiveFunctionType() {
    return objectiveFunctionType;
  }

  /**
   * Sets the objective function type.
   *
   * @param objectiveFunctionType objective function type
   */
  public void setObjectiveFunctionType(ObjectiveFunctionType objectiveFunctionType) {
    this.objectiveFunctionType =
        objectiveFunctionType == null ? ObjectiveFunctionType.WEIGHTED_LEAST_SQUARES
            : objectiveFunctionType;
  }

  /**
   * Returns the robust tuning constant.
   *
   * @return robust tuning constant
   */
  public double getRobustTuningConstant() {
    return robustTuningConstant;
  }

  /**
   * Sets the robust tuning constant.
   *
   * @param robustTuningConstant robust tuning constant
   */
  public void setRobustTuningConstant(double robustTuningConstant) {
    this.robustTuningConstant = robustTuningConstant;
  }

  /**
   * Returns the maximum number of robust reweighting passes.
   *
   * @return robust iteration count
   */
  public int getMaxRobustIterations() {
    return maxRobustIterations;
  }

  /**
   * Sets the maximum number of robust reweighting passes.
   *
   * @param maxRobustIterations robust iteration count
   */
  public void setMaxRobustIterations(int maxRobustIterations) {
    this.maxRobustIterations = maxRobustIterations;
  }

  /**
   * Returns the optimizer iteration limit.
   *
   * @return optimizer iteration limit
   */
  public int getMaxNumberOfIterations() {
    return maxNumberOfIterations;
  }

  /**
   * Sets the optimizer iteration limit.
   *
   * @param maxNumberOfIterations optimizer iteration limit
   */
  public void setMaxNumberOfIterations(int maxNumberOfIterations) {
    this.maxNumberOfIterations = maxNumberOfIterations;
  }

  /**
   * Returns the number of multi-start candidates.
   *
   * @return multi-start candidate count
   */
  public int getMultiStartCount() {
    return multiStartCount;
  }

  /**
   * Sets the number of multi-start candidates.
   *
   * @param multiStartCount multi-start candidate count
   */
  public void setMultiStartCount(int multiStartCount) {
    this.multiStartCount = multiStartCount;
  }

  /**
   * Returns the random seed used for deterministic multi-start sampling.
   *
   * @return random seed
   */
  public long getRandomSeed() {
    return randomSeed;
  }

  /**
   * Sets the random seed used for deterministic multi-start sampling.
   *
   * @param randomSeed random seed
   */
  public void setRandomSeed(long randomSeed) {
    this.randomSeed = randomSeed;
  }

  /**
   * Returns the optional training fraction.
   *
   * @return training fraction, or NaN when disabled
   */
  public double getTrainingFraction() {
    return trainingFraction;
  }

  /**
   * Sets the optional training fraction.
   *
   * @param trainingFraction training fraction, or NaN to disable automatic splitting
   */
  public void setTrainingFraction(double trainingFraction) {
    this.trainingFraction = trainingFraction;
  }

  /**
   * Creates a JSON object mapper.
   *
   * @return JSON object mapper
   */
  private static ObjectMapper createJsonMapper() {
    return new ObjectMapper();
  }

  /**
   * Creates a YAML object mapper.
   *
   * @return YAML object mapper
   */
  private static ObjectMapper createYamlMapper() {
    return new ObjectMapper(new YAMLFactory());
  }

  /**
   * Returns a default string when the value is null.
   *
   * @param value user-supplied value
   * @param defaultValue fallback value
   * @return value or defaultValue
   */
  private static String defaultString(String value, String defaultValue) {
    return value == null ? defaultValue : value;
  }
}
