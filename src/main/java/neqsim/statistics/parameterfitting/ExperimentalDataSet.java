package neqsim.statistics.parameterfitting;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata-aware collection of experimental data points for parameter fitting.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ExperimentalDataSet implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final String name;
  private final String responseName;
  private final String responseUnit;
  private final String[] dependentVariableNames;
  private final String[] dependentVariableUnits;
  private final ArrayList<ExperimentalDataPoint> points = new ArrayList<ExperimentalDataPoint>();

  /**
   * Creates an experimental data set.
   *
   * @param name data set name
   * @param responseName measured response name
   * @param responseUnit measured response unit
   * @param dependentVariableNames names of independent variables passed to the fitting function
   * @param dependentVariableUnits units of independent variables passed to the fitting function
   * @throws IllegalArgumentException if dependent variable metadata is empty or inconsistent
   */
  public ExperimentalDataSet(String name, String responseName, String responseUnit,
      String[] dependentVariableNames, String[] dependentVariableUnits) {
    validateDependentVariableMetadata(dependentVariableNames, dependentVariableUnits);
    this.name = defaultString(name, "experimental data set");
    this.responseName = defaultString(responseName, "response");
    this.responseUnit = defaultString(responseUnit, "");
    this.dependentVariableNames = copyArray(dependentVariableNames);
    this.dependentVariableUnits = copyArray(dependentVariableUnits);
  }

  /**
   * Creates an experimental data set without explicit unit metadata.
   *
   * @param name data set name
   * @param responseName measured response name
   * @param dependentVariableNames names of independent variables passed to the fitting function
   * @throws IllegalArgumentException if dependent variable metadata is empty
   */
  public ExperimentalDataSet(String name, String responseName, String[] dependentVariableNames) {
    this(name, responseName, "", dependentVariableNames, createEmptyUnits(dependentVariableNames));
  }

  /**
   * Adds an experimental data point.
   *
   * @param point data point to add
   * @return this data set for fluent construction
   * @throws IllegalArgumentException if point is null or has the wrong number of independent values
   */
  public ExperimentalDataSet addPoint(ExperimentalDataPoint point) {
    if (point == null) {
      throw new IllegalArgumentException("point cannot be null");
    }
    if (point.getDependentValues().length != dependentVariableNames.length) {
      throw new IllegalArgumentException("point dependent value count does not match metadata");
    }
    points.add(point);
    return this;
  }

  /**
   * Adds an experimental data point.
   *
   * @param measuredValue measured response value
   * @param standardDeviation positive standard deviation for the measured response
   * @param dependentValues independent variable values used by the fitting function
   * @return this data set for fluent construction
   * @throws IllegalArgumentException if values are invalid or dimensions do not match metadata
   */
  public ExperimentalDataSet addPoint(double measuredValue, double standardDeviation,
      double[] dependentValues) {
    return addPoint(new ExperimentalDataPoint(measuredValue, standardDeviation, dependentValues));
  }

  /**
   * Adds an experimental data point with reference metadata.
   *
   * @param measuredValue measured response value
   * @param standardDeviation positive standard deviation for the measured response
   * @param dependentValues independent variable values used by the fitting function
   * @param reference source reference for the data point
   * @param description short description of the data point
   * @return this data set for fluent construction
   * @throws IllegalArgumentException if values are invalid or dimensions do not match metadata
   */
  public ExperimentalDataSet addPoint(double measuredValue, double standardDeviation,
      double[] dependentValues, String reference, String description) {
    return addPoint(new ExperimentalDataPoint(measuredValue, standardDeviation, dependentValues,
        reference, description));
  }

  /**
   * Converts the data set to the legacy SampleSet representation.
   *
   * @param function fitting function to attach to each sample
   * @return sample set compatible with the existing optimizer API
   * @throws IllegalArgumentException if function is null
   */
  public SampleSet toSampleSet(BaseFunction function) {
    ArrayList<SampleValue> samples = new ArrayList<SampleValue>();
    for (int i = 0; i < points.size(); i++) {
      samples.add(points.get(i).toSampleValue(function));
    }
    return new SampleSet(samples);
  }

  /**
   * Splits the data set into training and validation subsets while preserving point order.
   *
   * @param trainingFraction fraction of rows assigned to the training subset, exclusive range
   *        {@code 0.0 < trainingFraction < 1.0}
   * @return two data sets: index 0 is training and index 1 is validation
   * @throws IllegalArgumentException if the fraction is outside the valid range or fewer than two
   *         points are available
   */
  public ExperimentalDataSet[] split(double trainingFraction) {
    if (trainingFraction <= 0.0 || trainingFraction >= 1.0) {
      throw new IllegalArgumentException("trainingFraction must be between 0.0 and 1.0");
    }
    if (points.size() < 2) {
      throw new IllegalArgumentException("at least two points are required for splitting");
    }
    int trainingCount = (int) Math.round(points.size() * trainingFraction);
    trainingCount = Math.max(1, Math.min(points.size() - 1, trainingCount));
    ExperimentalDataSet training = createEmptyCopy(name + " training");
    ExperimentalDataSet validation = createEmptyCopy(name + " validation");
    for (int i = 0; i < points.size(); i++) {
      if (i < trainingCount) {
        training.addPoint(points.get(i));
      } else {
        validation.addPoint(points.get(i));
      }
    }
    return new ExperimentalDataSet[] {training, validation};
  }

  /**
   * Returns the data set name.
   *
   * @return data set name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the response name.
   *
   * @return measured response name
   */
  public String getResponseName() {
    return responseName;
  }

  /**
   * Returns the response unit.
   *
   * @return measured response unit
   */
  public String getResponseUnit() {
    return responseUnit;
  }

  /**
   * Returns independent variable names.
   *
   * @return copy of independent variable names
   */
  public String[] getDependentVariableNames() {
    return copyArray(dependentVariableNames);
  }

  /**
   * Returns independent variable units.
   *
   * @return copy of independent variable units
   */
  public String[] getDependentVariableUnits() {
    return copyArray(dependentVariableUnits);
  }

  /**
   * Returns the number of independent variables.
   *
   * @return independent variable count
   */
  public int getDependentVariableCount() {
    return dependentVariableNames.length;
  }

  /**
   * Returns the number of data points.
   *
   * @return point count
   */
  public int size() {
    return points.size();
  }

  /**
   * Returns one data point.
   *
   * @param index zero-based point index
   * @return experimental data point
   * @throws IndexOutOfBoundsException if index is outside the point list
   */
  public ExperimentalDataPoint getPoint(int index) {
    return points.get(index);
  }

  /**
   * Returns an immutable snapshot of the data points.
   *
   * @return immutable point list snapshot
   */
  public List<ExperimentalDataPoint> getPoints() {
    return Collections.unmodifiableList(new ArrayList<ExperimentalDataPoint>(points));
  }

  /**
   * Reads an experimental data set from a CSV file.
   *
   * @param file CSV file with a header row
   * @param name data set name
   * @param responseName response name and default measured-value column
   * @param responseUnit response unit used in the resulting data set
   * @param dependentVariableNames independent variable names and default column names
   * @param dependentVariableUnits independent variable units used in the resulting data set
   * @return experimental data set
   * @throws IOException if the file cannot be read
   */
  public static ExperimentalDataSet fromCsv(File file, String name, String responseName,
      String responseUnit, String[] dependentVariableNames, String[] dependentVariableUnits)
      throws IOException {
    return ExperimentalDataReader.fromCsv(file, name, responseName, responseUnit,
        dependentVariableNames, dependentVariableUnits);
  }

  /**
   * Reads an experimental data set from a CSV file using explicit mapping options.
   *
   * @param file CSV file with a header row
   * @param options CSV mapping options
   * @return experimental data set
   * @throws IOException if the file cannot be read
   */
  public static ExperimentalDataSet fromCsv(File file, ExperimentalDataReader.CsvOptions options)
      throws IOException {
    return ExperimentalDataReader.fromCsv(file, options);
  }

  /**
   * Reads an experimental data set from JSON text.
   *
   * @param json JSON text
   * @return experimental data set
   * @throws IOException if parsing fails
   */
  public static ExperimentalDataSet fromJson(String json) throws IOException {
    return ExperimentalDataReader.fromJson(json);
  }

  /**
   * Reads an experimental data set from a JSON file.
   *
   * @param file JSON file
   * @return experimental data set
   * @throws IOException if reading or parsing fails
   */
  public static ExperimentalDataSet fromJson(File file) throws IOException {
    return ExperimentalDataReader.fromJson(file);
  }

  /**
   * Reads an experimental data set from YAML text.
   *
   * @param yaml YAML text
   * @return experimental data set
   * @throws IOException if parsing fails
   */
  public static ExperimentalDataSet fromYaml(String yaml) throws IOException {
    return ExperimentalDataReader.fromYaml(yaml);
  }

  /**
   * Reads an experimental data set from a YAML file.
   *
   * @param file YAML file
   * @return experimental data set
   * @throws IOException if reading or parsing fails
   */
  public static ExperimentalDataSet fromYaml(File file) throws IOException {
    return ExperimentalDataReader.fromYaml(file);
  }

  /**
   * Creates an empty data set with the same metadata.
   *
   * @param newName name for the copied data set
   * @return empty data set with copied metadata
   */
  private ExperimentalDataSet createEmptyCopy(String newName) {
    return new ExperimentalDataSet(newName, responseName, responseUnit, dependentVariableNames,
        dependentVariableUnits);
  }

  /**
   * Validates dependent variable metadata.
   *
   * @param names dependent variable names
   * @param units dependent variable units
   * @throws IllegalArgumentException if metadata is empty or inconsistent
   */
  private static void validateDependentVariableMetadata(String[] names, String[] units) {
    if (names == null || names.length == 0) {
      throw new IllegalArgumentException("dependentVariableNames must contain at least one name");
    }
    if (units == null || units.length != names.length) {
      throw new IllegalArgumentException(
          "dependentVariableUnits must have the same length as dependentVariableNames");
    }
  }

  /**
   * Creates empty unit labels for each dependent variable.
   *
   * @param dependentVariableNames dependent variable names
   * @return empty unit labels
   * @throws IllegalArgumentException if dependentVariableNames is null
   */
  private static String[] createEmptyUnits(String[] dependentVariableNames) {
    if (dependentVariableNames == null) {
      throw new IllegalArgumentException("dependentVariableNames cannot be null");
    }
    String[] units = new String[dependentVariableNames.length];
    for (int i = 0; i < units.length; i++) {
      units[i] = "";
    }
    return units;
  }

  /**
   * Returns a default string if the supplied value is null.
   *
   * @param value user supplied value
   * @param defaultValue fallback value
   * @return value or defaultValue if value is null
   */
  private static String defaultString(String value, String defaultValue) {
    return value == null ? defaultValue : value;
  }

  /**
   * Copies a string array.
   *
   * @param values values to copy
   * @return copied string array
   */
  private static String[] copyArray(String[] values) {
    String[] copy = new String[values.length];
    System.arraycopy(values, 0, copy, 0, values.length);
    return copy;
  }
}
