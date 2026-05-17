package neqsim.statistics.parameterfitting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/**
 * Reader utilities for metadata-rich experimental parameter fitting data sets.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ExperimentalDataReader {
  /** Column name used when no explicit standard-deviation column is configured. */
  private static final String DEFAULT_STANDARD_DEVIATION_COLUMN = "standardDeviation";

  /** Smallest effective robust standard deviation scaling weight. */
  private static final double MINIMUM_WEIGHT = 1.0e-12;

  /**
   * Utility class constructor.
   */
  private ExperimentalDataReader() {}

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
    CsvOptions options = new CsvOptions(name, responseName, responseUnit, dependentVariableNames,
        dependentVariableUnits);
    return fromCsv(file, options);
  }

  /**
   * Reads an experimental data set from a CSV file.
   *
   * @param file CSV file with a header row
   * @param options CSV mapping options
   * @return experimental data set
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if required columns are missing or values are invalid
   */
  public static ExperimentalDataSet fromCsv(File file, CsvOptions options) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    if (options == null) {
      throw new IllegalArgumentException("options cannot be null");
    }
    options.validate();
    BufferedReader reader = new BufferedReader(new FileReader(file));
    try {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new IllegalArgumentException("CSV file is empty: " + file);
      }
      List<String> header = parseCsvLine(headerLine);
      Map<String, Integer> headerIndex = createHeaderIndex(header);
      ExperimentalDataSet dataSet = new ExperimentalDataSet(options.getName(),
          options.getResponseName(), options.getResponseUnit(), options.getDependentVariableNames(),
          options.getDependentVariableUnits());
      String measuredColumn = options.resolveMeasuredValueColumn();
      String standardDeviationColumn = options.resolveStandardDeviationColumn(headerIndex);
      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.trim().isEmpty()) {
          continue;
        }
        List<String> values = parseCsvLine(line);
        double measuredValue = parseDouble(getColumnValue(values, headerIndex, measuredColumn),
            measuredColumn, lineNumber);
        measuredValue = convertAbsoluteValue(options.getResponseName(), measuredValue,
            options.getSourceResponseUnit(), options.getResponseUnit());
        double standardDeviation =
            parseDouble(getColumnValue(values, headerIndex, standardDeviationColumn),
                standardDeviationColumn, lineNumber);
        standardDeviation = convertDeltaValue(options.getResponseName(), standardDeviation,
            options.getSourceResponseUnit(), options.getResponseUnit());
        double[] dependentValues = new double[options.getDependentVariableNames().length];
        for (int i = 0; i < dependentValues.length; i++) {
          String column = options.resolveDependentVariableColumn(i);
          double value =
              parseDouble(getColumnValue(values, headerIndex, column), column, lineNumber);
          dependentValues[i] = convertAbsoluteValue(options.getDependentVariableNames()[i], value,
              options.getSourceDependentVariableUnits()[i], options.getDependentVariableUnits()[i]);
        }
        String reference = optionalColumnValue(values, headerIndex, options.getReferenceColumn());
        String description =
            optionalColumnValue(values, headerIndex, options.getDescriptionColumn());
        dataSet.addPoint(measuredValue, Math.max(standardDeviation, MINIMUM_WEIGHT),
            dependentValues, reference, description);
      }
      return dataSet;
    } finally {
      reader.close();
    }
  }

  /**
   * Reads an experimental data set from JSON text.
   *
   * @param json JSON text
   * @return experimental data set
   * @throws IOException if parsing fails
   */
  public static ExperimentalDataSet fromJson(String json) throws IOException {
    return fromTree(createJsonMapper().readTree(json));
  }

  /**
   * Reads an experimental data set from a JSON file.
   *
   * @param file JSON file
   * @return experimental data set
   * @throws IOException if reading or parsing fails
   */
  public static ExperimentalDataSet fromJson(File file) throws IOException {
    return fromTree(createJsonMapper().readTree(file));
  }

  /**
   * Reads an experimental data set from YAML text.
   *
   * @param yaml YAML text
   * @return experimental data set
   * @throws IOException if parsing fails
   */
  public static ExperimentalDataSet fromYaml(String yaml) throws IOException {
    return fromTree(createYamlMapper().readTree(yaml));
  }

  /**
   * Reads an experimental data set from a YAML file.
   *
   * @param file YAML file
   * @return experimental data set
   * @throws IOException if reading or parsing fails
   */
  public static ExperimentalDataSet fromYaml(File file) throws IOException {
    return fromTree(createYamlMapper().readTree(file));
  }

  /**
   * Converts a parsed JSON/YAML tree to an experimental data set.
   *
   * @param root parsed data tree
   * @return experimental data set
   */
  private static ExperimentalDataSet fromTree(JsonNode root) {
    if (root == null || root.isMissingNode()) {
      throw new IllegalArgumentException("data set document is empty");
    }
    String[] dependentNames = readStringArray(root, "dependentVariableNames");
    String[] dependentUnits = readStringArray(root, "dependentVariableUnits");
    ExperimentalDataSet dataSet = new ExperimentalDataSet(readText(root, "name", "data set"),
        readText(root, "responseName", "response"), readText(root, "responseUnit", ""),
        dependentNames, dependentUnits);
    JsonNode points = root.get("points");
    if (points == null || !points.isArray()) {
      throw new IllegalArgumentException("data set document must contain an array named points");
    }
    for (int i = 0; i < points.size(); i++) {
      JsonNode point = points.get(i);
      dataSet.addPoint(readDouble(point, "measuredValue"), readDouble(point, "standardDeviation"),
          readDoubleArray(point, "dependentValues"), readText(point, "reference", "unknown"),
          readText(point, "description", "unknown"));
    }
    return dataSet;
  }

  /**
   * Parses one CSV line using RFC4180-style quote handling.
   *
   * @param line CSV line
   * @return parsed column values
   */
  private static List<String> parseCsvLine(String line) {
    ArrayList<String> values = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);
      if (character == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (character == ',' && !quoted) {
        values.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(character);
      }
    }
    values.add(current.toString().trim());
    return values;
  }

  /**
   * Creates a map from header names to zero-based column indexes.
   *
   * @param header parsed header values
   * @return header index map
   */
  private static Map<String, Integer> createHeaderIndex(List<String> header) {
    HashMap<String, Integer> index = new HashMap<String, Integer>();
    for (int i = 0; i < header.size(); i++) {
      index.put(header.get(i), Integer.valueOf(i));
    }
    return index;
  }

  /**
   * Returns a required column value.
   *
   * @param values row values
   * @param headerIndex header index map
   * @param column column name
   * @return column value text
   */
  private static String getColumnValue(List<String> values, Map<String, Integer> headerIndex,
      String column) {
    Integer index = headerIndex.get(column);
    if (index == null) {
      throw new IllegalArgumentException("Missing required CSV column: " + column);
    }
    if (index.intValue() >= values.size()) {
      throw new IllegalArgumentException("Missing value for CSV column: " + column);
    }
    return values.get(index.intValue());
  }

  /**
   * Returns an optional column value.
   *
   * @param values row values
   * @param headerIndex header index map
   * @param column optional column name
   * @return column value text, or unknown when unavailable
   */
  private static String optionalColumnValue(List<String> values, Map<String, Integer> headerIndex,
      String column) {
    if (column == null || column.trim().isEmpty() || !headerIndex.containsKey(column)) {
      return "unknown";
    }
    Integer index = headerIndex.get(column);
    if (index.intValue() >= values.size()) {
      return "unknown";
    }
    String value = values.get(index.intValue());
    return value == null || value.trim().isEmpty() ? "unknown" : value;
  }

  /**
   * Parses a finite double value from CSV text.
   *
   * @param value value text
   * @param column column name
   * @param lineNumber one-based line number
   * @return parsed value
   */
  private static double parseDouble(String value, String column, int lineNumber) {
    try {
      double parsed = Double.parseDouble(value);
      if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
        throw new NumberFormatException("not finite");
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "Invalid numeric value in column " + column + " at line " + lineNumber, ex);
    }
  }

  /**
   * Converts an absolute value between recognized units.
   *
   * @param variableName variable name used for diagnostics
   * @param value value to convert
   * @param fromUnit source unit
   * @param toUnit target unit
   * @return converted value
   */
  private static double convertAbsoluteValue(String variableName, double value, String fromUnit,
      String toUnit) {
    if (sameOrEmptyUnit(fromUnit, toUnit)) {
      return value;
    }
    if (isTemperatureUnit(fromUnit) && isTemperatureUnit(toUnit)) {
      return new TemperatureUnit(value, fromUnit).getValue(toUnit);
    }
    if (isPressureUnit(fromUnit) && isPressureUnit(toUnit)) {
      return new PressureUnit(value, fromUnit).getValue(toUnit);
    }
    throw new IllegalArgumentException(
        "No unit conversion available for " + variableName + ": " + fromUnit + " to " + toUnit);
  }

  /**
   * Converts a standard deviation between recognized units without applying offsets.
   *
   * @param variableName variable name used for diagnostics
   * @param value standard deviation value
   * @param fromUnit source unit
   * @param toUnit target unit
   * @return converted standard deviation
   */
  private static double convertDeltaValue(String variableName, double value, String fromUnit,
      String toUnit) {
    if (sameOrEmptyUnit(fromUnit, toUnit)) {
      return value;
    }
    if (isTemperatureUnit(fromUnit) && isTemperatureUnit(toUnit)) {
      double zero = new TemperatureUnit(0.0, fromUnit).getValue(toUnit);
      double one = new TemperatureUnit(1.0, fromUnit).getValue(toUnit);
      return value * Math.abs(one - zero);
    }
    if (isPressureUnit(fromUnit) && isPressureUnit(toUnit)) {
      double zero = new PressureUnit(0.0, fromUnit).getValue(toUnit);
      double one = new PressureUnit(1.0, fromUnit).getValue(toUnit);
      return value * Math.abs(one - zero);
    }
    throw new IllegalArgumentException(
        "No unit conversion available for " + variableName + ": " + fromUnit + " to " + toUnit);
  }

  /**
   * Returns whether units are effectively equal or unspecified.
   *
   * @param fromUnit source unit
   * @param toUnit target unit
   * @return true if no conversion is needed
   */
  private static boolean sameOrEmptyUnit(String fromUnit, String toUnit) {
    String from = fromUnit == null ? "" : fromUnit.trim();
    String to = toUnit == null ? "" : toUnit.trim();
    return from.isEmpty() || to.isEmpty() || from.equals(to);
  }

  /**
   * Returns whether a unit is a recognized temperature unit.
   *
   * @param unit unit text
   * @return true for K, C, F, or R
   */
  private static boolean isTemperatureUnit(String unit) {
    return "K".equals(unit) || "C".equals(unit) || "F".equals(unit) || "R".equals(unit);
  }

  /**
   * Returns whether a unit is a recognized pressure unit.
   *
   * @param unit unit text
   * @return true for common pressure units supported by NeqSim
   */
  private static boolean isPressureUnit(String unit) {
    return "bara".equals(unit) || "bar".equals(unit) || "barg".equals(unit) || "psi".equals(unit)
        || "psia".equals(unit) || "psig".equals(unit) || "Pa".equals(unit) || "kPa".equals(unit)
        || "MPa".equals(unit) || "atm".equals(unit);
  }

  /**
   * Reads a text value from a JSON/YAML node.
   *
   * @param node parent node
   * @param field field name
   * @param defaultValue fallback value
   * @return text value or fallback
   */
  private static String readText(JsonNode node, String field, String defaultValue) {
    JsonNode child = node.get(field);
    return child == null || child.isNull() ? defaultValue : child.asText(defaultValue);
  }

  /**
   * Reads a required double value from a JSON/YAML node.
   *
   * @param node parent node
   * @param field field name
   * @return double value
   */
  private static double readDouble(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || !child.isNumber()) {
      throw new IllegalArgumentException("Missing numeric field: " + field);
    }
    return child.asDouble();
  }

  /**
   * Reads a string array from a JSON/YAML node.
   *
   * @param node parent node
   * @param field field name
   * @return string array
   */
  private static String[] readStringArray(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || !child.isArray()) {
      throw new IllegalArgumentException("Missing array field: " + field);
    }
    String[] values = new String[child.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = child.get(i).asText();
    }
    return values;
  }

  /**
   * Reads a double array from a JSON/YAML node.
   *
   * @param node parent node
   * @param field field name
   * @return double array
   */
  private static double[] readDoubleArray(JsonNode node, String field) {
    JsonNode child = node.get(field);
    if (child == null || !child.isArray()) {
      throw new IllegalArgumentException("Missing array field: " + field);
    }
    double[] values = new double[child.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = child.get(i).asDouble();
    }
    return values;
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
   * CSV mapping options for experimental data loading.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static class CsvOptions implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    private String name = "CSV data set";
    private String responseName = "measuredValue";
    private String responseUnit = "";
    private String sourceResponseUnit = "";
    private String measuredValueColumn;
    private String standardDeviationColumn = DEFAULT_STANDARD_DEVIATION_COLUMN;
    private String[] dependentVariableNames = new String[0];
    private String[] dependentVariableUnits = new String[0];
    private String[] sourceDependentVariableUnits = new String[0];
    private String[] dependentVariableColumns;
    private String referenceColumn = "reference";
    private String descriptionColumn = "description";

    /**
     * Creates empty options for serialization frameworks.
     */
    public CsvOptions() {}

    /**
     * Creates CSV options from data-set metadata.
     *
     * @param name data set name
     * @param responseName response name
     * @param responseUnit response unit
     * @param dependentVariableNames independent variable names
     * @param dependentVariableUnits independent variable target units
     */
    public CsvOptions(String name, String responseName, String responseUnit,
        String[] dependentVariableNames, String[] dependentVariableUnits) {
      this.name = name;
      this.responseName = responseName;
      this.responseUnit = responseUnit;
      this.sourceResponseUnit = responseUnit;
      this.dependentVariableNames = copyArray(dependentVariableNames);
      this.dependentVariableUnits = copyArray(dependentVariableUnits);
      this.sourceDependentVariableUnits = copyArray(dependentVariableUnits);
    }

    /**
     * Validates option consistency.
     */
    public void validate() {
      if (dependentVariableNames == null || dependentVariableNames.length == 0) {
        throw new IllegalArgumentException("dependentVariableNames must not be empty");
      }
      if (dependentVariableUnits == null
          || dependentVariableUnits.length != dependentVariableNames.length) {
        throw new IllegalArgumentException("dependentVariableUnits length must match names");
      }
      if (sourceDependentVariableUnits == null
          || sourceDependentVariableUnits.length != dependentVariableNames.length) {
        sourceDependentVariableUnits = copyArray(dependentVariableUnits);
      }
      if (dependentVariableColumns != null
          && dependentVariableColumns.length != dependentVariableNames.length) {
        throw new IllegalArgumentException("dependentVariableColumns length must match names");
      }
      if (sourceResponseUnit == null || sourceResponseUnit.trim().isEmpty()) {
        sourceResponseUnit = responseUnit;
      }
    }

    /**
     * Resolves the measured-value column name.
     *
     * @return measured-value column name
     */
    public String resolveMeasuredValueColumn() {
      return measuredValueColumn == null || measuredValueColumn.trim().isEmpty() ? responseName
          : measuredValueColumn;
    }

    /**
     * Resolves the standard-deviation column name.
     *
     * @param headerIndex CSV header index
     * @return standard-deviation column name
     */
    public String resolveStandardDeviationColumn(Map<String, Integer> headerIndex) {
      if (standardDeviationColumn != null && headerIndex.containsKey(standardDeviationColumn)) {
        return standardDeviationColumn;
      }
      if (headerIndex.containsKey("stdDev")) {
        return "stdDev";
      }
      return DEFAULT_STANDARD_DEVIATION_COLUMN;
    }

    /**
     * Resolves an independent-variable column name.
     *
     * @param index independent variable index
     * @return column name
     */
    public String resolveDependentVariableColumn(int index) {
      if (dependentVariableColumns == null || dependentVariableColumns[index] == null
          || dependentVariableColumns[index].trim().isEmpty()) {
        return dependentVariableNames[index];
      }
      return dependentVariableColumns[index];
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
     * Sets the data set name.
     *
     * @param name data set name
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Returns the response name.
     *
     * @return response name
     */
    public String getResponseName() {
      return responseName;
    }

    /**
     * Sets the response name.
     *
     * @param responseName response name
     */
    public void setResponseName(String responseName) {
      this.responseName = responseName;
    }

    /**
     * Returns the response unit.
     *
     * @return response unit
     */
    public String getResponseUnit() {
      return responseUnit;
    }

    /**
     * Sets the response unit.
     *
     * @param responseUnit response unit
     */
    public void setResponseUnit(String responseUnit) {
      this.responseUnit = responseUnit;
    }

    /**
     * Returns the source response unit.
     *
     * @return source response unit
     */
    public String getSourceResponseUnit() {
      return sourceResponseUnit;
    }

    /**
     * Sets the source response unit.
     *
     * @param sourceResponseUnit source response unit
     */
    public void setSourceResponseUnit(String sourceResponseUnit) {
      this.sourceResponseUnit = sourceResponseUnit;
    }

    /**
     * Returns the measured-value column.
     *
     * @return measured-value column
     */
    public String getMeasuredValueColumn() {
      return measuredValueColumn;
    }

    /**
     * Sets the measured-value column.
     *
     * @param measuredValueColumn measured-value column
     */
    public void setMeasuredValueColumn(String measuredValueColumn) {
      this.measuredValueColumn = measuredValueColumn;
    }

    /**
     * Returns the standard-deviation column.
     *
     * @return standard-deviation column
     */
    public String getStandardDeviationColumn() {
      return standardDeviationColumn;
    }

    /**
     * Sets the standard-deviation column.
     *
     * @param standardDeviationColumn standard-deviation column
     */
    public void setStandardDeviationColumn(String standardDeviationColumn) {
      this.standardDeviationColumn = standardDeviationColumn;
    }

    /**
     * Returns independent variable names.
     *
     * @return independent variable names
     */
    public String[] getDependentVariableNames() {
      return copyArray(dependentVariableNames);
    }

    /**
     * Sets independent variable names.
     *
     * @param dependentVariableNames independent variable names
     */
    public void setDependentVariableNames(String[] dependentVariableNames) {
      this.dependentVariableNames = copyArray(dependentVariableNames);
    }

    /**
     * Returns target independent variable units.
     *
     * @return target independent variable units
     */
    public String[] getDependentVariableUnits() {
      return copyArray(dependentVariableUnits);
    }

    /**
     * Sets target independent variable units.
     *
     * @param dependentVariableUnits target independent variable units
     */
    public void setDependentVariableUnits(String[] dependentVariableUnits) {
      this.dependentVariableUnits = copyArray(dependentVariableUnits);
    }

    /**
     * Returns source independent variable units.
     *
     * @return source independent variable units
     */
    public String[] getSourceDependentVariableUnits() {
      return copyArray(sourceDependentVariableUnits);
    }

    /**
     * Sets source independent variable units.
     *
     * @param sourceDependentVariableUnits source independent variable units
     */
    public void setSourceDependentVariableUnits(String[] sourceDependentVariableUnits) {
      this.sourceDependentVariableUnits = copyArray(sourceDependentVariableUnits);
    }

    /**
     * Returns independent variable column names.
     *
     * @return independent variable column names
     */
    public String[] getDependentVariableColumns() {
      return copyArray(dependentVariableColumns);
    }

    /**
     * Sets independent variable column names.
     *
     * @param dependentVariableColumns independent variable column names
     */
    public void setDependentVariableColumns(String[] dependentVariableColumns) {
      this.dependentVariableColumns = copyArray(dependentVariableColumns);
    }

    /**
     * Returns the optional reference column.
     *
     * @return reference column
     */
    public String getReferenceColumn() {
      return referenceColumn;
    }

    /**
     * Sets the optional reference column.
     *
     * @param referenceColumn reference column
     */
    public void setReferenceColumn(String referenceColumn) {
      this.referenceColumn = referenceColumn;
    }

    /**
     * Returns the optional description column.
     *
     * @return description column
     */
    public String getDescriptionColumn() {
      return descriptionColumn;
    }

    /**
     * Sets the optional description column.
     *
     * @param descriptionColumn description column
     */
    public void setDescriptionColumn(String descriptionColumn) {
      this.descriptionColumn = descriptionColumn;
    }

    /**
     * Copies a string array.
     *
     * @param values values to copy
     * @return copied values, or null if values is null
     */
    private static String[] copyArray(String[] values) {
      if (values == null) {
        return null;
      }
      String[] copy = new String[values.length];
      System.arraycopy(values, 0, copy, 0, values.length);
      return copy;
    }
  }
}
