package neqsim.process.util.optimizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Loads installed equipment capacity limits from a CSV table and attaches them to a process model.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class InstalledCapacityTableLoader {

  /** Utility class constructor. */
  private InstalledCapacityTableLoader() {}

  /**
   * Installed capacity record loaded from the table.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class InstalledCapacityRecord implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Process area name. */
    private final String area;

    /** Equipment name. */
    private final String equipment;

    /** Constraint name. */
    private final String constraint;

    /** Current value automation address. */
    private final String currentValueAddress;

    /** Design value. */
    private final double designValue;

    /** Maximum value. */
    private final double maxValue;

    /** Unit. */
    private final String unit;

    /** Severity. */
    private final ConstraintSeverity severity;

    /** Whether the constraint is enabled. */
    private final boolean enabled;

    /**
     * Creates an installed capacity record.
     *
     * @param area process area name
     * @param equipment equipment name
     * @param constraint constraint name
     * @param currentValueAddress automation address for current value
     * @param designValue design value
     * @param maxValue maximum value
     * @param unit unit
     * @param severity severity
     * @param enabled true when enabled
     */
    public InstalledCapacityRecord(String area, String equipment, String constraint,
        String currentValueAddress, double designValue, double maxValue, String unit,
        ConstraintSeverity severity, boolean enabled) {
      this.area = area;
      this.equipment = equipment;
      this.constraint = constraint;
      this.currentValueAddress = currentValueAddress;
      this.designValue = designValue;
      this.maxValue = maxValue;
      this.unit = unit;
      this.severity = severity;
      this.enabled = enabled;
    }

    /**
     * Gets the area name.
     *
     * @return area name
     */
    public String getArea() {
      return area;
    }

    /**
     * Gets the equipment name.
     *
     * @return equipment name
     */
    public String getEquipment() {
      return equipment;
    }

    /**
     * Gets the constraint name.
     *
     * @return constraint name
     */
    public String getConstraint() {
      return constraint;
    }

    /**
     * Gets the current value address.
     *
     * @return current value automation address
     */
    public String getCurrentValueAddress() {
      return currentValueAddress;
    }

    /**
     * Gets the design value.
     *
     * @return design value
     */
    public double getDesignValue() {
      return designValue;
    }

    /**
     * Gets the maximum value.
     *
     * @return maximum value
     */
    public double getMaxValue() {
      return maxValue;
    }

    /**
     * Gets the unit.
     *
     * @return unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the severity.
     *
     * @return severity
     */
    public ConstraintSeverity getSeverity() {
      return severity;
    }

    /**
     * Checks whether the constraint is enabled.
     *
     * @return true when enabled
     */
    public boolean isEnabled() {
      return enabled;
    }
  }

  /**
   * Loads installed capacity constraints from a CSV file.
   *
   * @param model process model to attach constraints to
   * @param filePath CSV file path
   * @return loaded records
   * @throws IOException if reading fails
   */
  public static List<InstalledCapacityRecord> load(ProcessModel model, String filePath)
      throws IOException {
    return load(model, Paths.get(filePath));
  }

  /**
   * Loads installed capacity constraints from a CSV file.
   *
   * <p>
   * Required columns are {@code area}, {@code equipment}, {@code constraint},
   * {@code currentValueAddress}, {@code designValue}, {@code unit}, and {@code severity}. Optional
   * columns are {@code maxValue}, {@code type}, {@code enabled}, and {@code description}.
   * </p>
   *
   * @param model process model to attach constraints to
   * @param filePath CSV file path
   * @return loaded records
   * @throws IOException if reading fails
   */
  public static List<InstalledCapacityRecord> load(ProcessModel model, Path filePath)
      throws IOException {
    if (model == null) {
      throw new IllegalArgumentException("ProcessModel cannot be null");
    }

    List<InstalledCapacityRecord> records = new ArrayList<InstalledCapacityRecord>();
    BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
    try {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return records;
      }
      Map<String, Integer> headerIndex = buildHeaderIndex(parseCsvLine(headerLine));
      String line;
      int rowNumber = 1;
      while ((line = reader.readLine()) != null) {
        rowNumber++;
        if (line.trim().isEmpty() || line.trim().startsWith("#")) {
          continue;
        }
        InstalledCapacityRecord record =
            parseRecord(model, headerIndex, parseCsvLine(line), rowNumber);
        attachRecord(model, record, getOptional(headerIndex, parseCsvLine(line), "type"),
            getOptional(headerIndex, parseCsvLine(line), "description"));
        records.add(record);
      }
    } finally {
      reader.close();
    }
    return records;
  }

  /**
   * Parses and attaches one installed capacity record.
   *
   * @param model process model
   * @param headerIndex map from normalized header to column index
   * @param values CSV row values
   * @param rowNumber row number for diagnostics
   * @return installed capacity record
   */
  private static InstalledCapacityRecord parseRecord(ProcessModel model,
      Map<String, Integer> headerIndex, List<String> values, int rowNumber) {
    String area = getRequired(headerIndex, values, "area", rowNumber);
    String equipment = getRequired(headerIndex, values, "equipment", rowNumber);
    String constraint = getRequired(headerIndex, values, "constraint", rowNumber);
    String currentValueAddress = getRequired(headerIndex, values, "currentValueAddress", rowNumber);
    double designValue = parseDouble(getRequired(headerIndex, values, "designValue", rowNumber),
        "designValue", rowNumber);
    String maxValueText = getOptional(headerIndex, values, "maxValue");
    double maxValue =
        maxValueText.length() == 0 ? designValue : parseDouble(maxValueText, "maxValue", rowNumber);
    String unit = getRequired(headerIndex, values, "unit", rowNumber);
    ConstraintSeverity severity =
        parseSeverity(getRequired(headerIndex, values, "severity", rowNumber));
    boolean enabled = parseBoolean(getOptional(headerIndex, values, "enabled"), true);
    validateTargetExists(model, area, equipment, rowNumber);
    return new InstalledCapacityRecord(area, equipment, constraint, currentValueAddress,
        designValue, maxValue, unit, severity, enabled);
  }

  /**
   * Attaches a loaded record to its target equipment.
   *
   * @param model process model
   * @param record installed capacity record
   * @param typeText optional constraint type text
   * @param description optional description
   */
  private static void attachRecord(final ProcessModel model, final InstalledCapacityRecord record,
      String typeText, String description) {
    ProcessSystem area = model.get(record.getArea());
    ProcessEquipmentInterface equipment = area.getUnit(record.getEquipment());
    CapacityConstraint capacityConstraint = new CapacityConstraint(record.getConstraint(),
        record.getUnit(), parseType(typeText, record.getSeverity()))
            .setDesignValue(record.getDesignValue()).setMaxValue(record.getMaxValue())
            .setSeverity(record.getSeverity()).setEnabled(record.isEnabled())
            .setDataSource("installed_capacity_table").setDescription(description)
            .setValueSupplier(new java.util.function.DoubleSupplier() {
              /** {@inheritDoc} */
              @Override
              public double getAsDouble() {
                return model.getVariableValue(record.getCurrentValueAddress(), record.getUnit());
              }
            });
    equipment.addCapacityConstraint(capacityConstraint);
  }

  /**
   * Verifies that the target equipment exists.
   *
   * @param model process model
   * @param areaName area name
   * @param equipmentName equipment name
   * @param rowNumber CSV row number
   */
  private static void validateTargetExists(ProcessModel model, String areaName,
      String equipmentName, int rowNumber) {
    ProcessSystem area = model.get(areaName);
    if (area == null) {
      throw new IllegalArgumentException(
          "No ProcessModel area named '" + areaName + "' for capacity row " + rowNumber);
    }
    if (area.getUnit(equipmentName) == null) {
      throw new IllegalArgumentException("No equipment named '" + equipmentName + "' in area '"
          + areaName + "' for capacity row " + rowNumber);
    }
  }

  /**
   * Builds a normalized header index.
   *
   * @param headers header values
   * @return map from normalized header to index
   */
  private static Map<String, Integer> buildHeaderIndex(List<String> headers) {
    Map<String, Integer> headerIndex = new LinkedHashMap<String, Integer>();
    for (int i = 0; i < headers.size(); i++) {
      headerIndex.put(normalizeHeader(headers.get(i)), Integer.valueOf(i));
    }
    return headerIndex;
  }

  /**
   * Gets a required CSV value.
   *
   * @param headerIndex header index map
   * @param values row values
   * @param header required header
   * @param rowNumber row number for diagnostics
   * @return trimmed value
   */
  private static String getRequired(Map<String, Integer> headerIndex, List<String> values,
      String header, int rowNumber) {
    String value = getOptional(headerIndex, values, header);
    if (value.length() == 0) {
      throw new IllegalArgumentException(
          "Missing required column '" + header + "' at capacity row " + rowNumber);
    }
    return value;
  }

  /**
   * Gets an optional CSV value.
   *
   * @param headerIndex header index map
   * @param values row values
   * @param header optional header
   * @return trimmed value, or empty string when absent
   */
  private static String getOptional(Map<String, Integer> headerIndex, List<String> values,
      String header) {
    Integer index = headerIndex.get(normalizeHeader(header));
    if (index == null || index.intValue() < 0 || index.intValue() >= values.size()) {
      return "";
    }
    return values.get(index.intValue()).trim();
  }

  /**
   * Normalizes a CSV header.
   *
   * @param header header value
   * @return normalized header
   */
  private static String normalizeHeader(String header) {
    return header == null ? "" : header.trim().toLowerCase(Locale.US);
  }

  /**
   * Parses a CSV row using RFC 4180 quote escaping.
   *
   * @param line CSV line
   * @return parsed values
   */
  private static List<String> parseCsvLine(String line) {
    List<String> values = new ArrayList<String>();
    StringBuilder value = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);
      if (character == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          value.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (character == ',' && !inQuotes) {
        values.add(value.toString());
        value.setLength(0);
      } else {
        value.append(character);
      }
    }
    values.add(value.toString());
    return values;
  }

  /**
   * Parses a double value.
   *
   * @param value value text
   * @param fieldName field name for diagnostics
   * @param rowNumber row number for diagnostics
   * @return parsed value
   */
  private static double parseDouble(String value, String fieldName, int rowNumber) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Invalid " + fieldName + " value '" + value + "' at capacity row " + rowNumber,
          exception);
    }
  }

  /**
   * Parses a boolean value.
   *
   * @param value value text
   * @param defaultValue default value when text is empty
   * @return parsed boolean
   */
  private static boolean parseBoolean(String value, boolean defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    String normalized = value.trim().toLowerCase(Locale.US);
    return "true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized);
  }

  /**
   * Parses a constraint severity.
   *
   * @param severity severity text
   * @return parsed severity
   */
  private static ConstraintSeverity parseSeverity(String severity) {
    return ConstraintSeverity.valueOf(severity.trim().toUpperCase(Locale.US));
  }

  /**
   * Parses a constraint type, using severity when the type is absent.
   *
   * @param typeText type text
   * @param severity severity
   * @return parsed type
   */
  private static ConstraintType parseType(String typeText, ConstraintSeverity severity) {
    if (typeText != null && typeText.trim().length() > 0) {
      return ConstraintType.valueOf(typeText.trim().toUpperCase(Locale.US));
    }
    if (severity == ConstraintSeverity.SOFT) {
      return ConstraintType.SOFT;
    }
    if (severity == ConstraintSeverity.ADVISORY) {
      return ConstraintType.DESIGN;
    }
    return ConstraintType.HARD;
  }
}
