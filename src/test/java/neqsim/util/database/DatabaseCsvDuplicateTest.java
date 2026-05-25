package neqsim.util.database;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/**
 * Verifies that CSV-backed database resources do not contain duplicate lookup keys.
 *
 * @author Copilot
 * @version 1.0
 */
class DatabaseCsvDuplicateTest extends NeqSimTest {
  /** Maximum duplicate groups included in an assertion message. */
  private static final int MAX_DUPLICATES_TO_REPORT = 20;

  /**
   * Verifies that pure-component tables do not contain duplicate component names.
   *
   * @throws IOException if a CSV resource cannot be read
   */
  @Test
  void componentTablesShouldNotContainDuplicateNames() throws IOException {
    List<String> duplicateMessages = new ArrayList<String>();
    duplicateMessages.addAll(findDuplicateKeys("data/COMP.csv", false, "NAME"));
    duplicateMessages.addAll(findDuplicateKeys("data/COMP_EXT.csv", false, "NAME"));

    assertTrue(duplicateMessages.isEmpty(), formatDuplicateSummary(duplicateMessages));
  }

  /**
   * Verifies that component IDs do not point to different component names.
   *
   * @throws IOException if a CSV resource cannot be read
   */
  @Test
  void componentTableIdsShouldNotReferToDifferentComponentNames() throws IOException {
    List<String> duplicateMessages = new ArrayList<String>();
    duplicateMessages.addAll(findDuplicateKeysWithDifferentValues("data/COMP.csv", "ID", "NAME"));
    duplicateMessages
        .addAll(findDuplicateKeysWithDifferentValues("data/COMP_EXT.csv", "ID", "NAME"));

    assertTrue(duplicateMessages.isEmpty(), formatDuplicateSummary(duplicateMessages));
  }

  /**
   * Verifies that the component name index does not contain duplicate names.
   *
   * @throws IOException if the name index resource cannot be read
   */
  @Test
  void componentNameIndexShouldNotContainDuplicateNames() throws IOException {
    List<String> duplicateMessages = findDuplicateNameIndexEntries("neqsim_component_names.txt");

    assertTrue(duplicateMessages.isEmpty(), formatDuplicateSummary(duplicateMessages));
  }

  /**
   * Verifies that interaction parameters do not contain duplicate IDs or unordered pairs.
   *
   * @throws IOException if a CSV resource cannot be read
   */
  @Test
  void interactionTableShouldNotContainDuplicateIdsOrUnorderedPairs() throws IOException {
    List<String> duplicateMessages = new ArrayList<String>();
    duplicateMessages.addAll(findDuplicateKeys("data/INTER.csv", false, "ID"));
    duplicateMessages.addAll(findDuplicateKeys("data/INTER.csv", true, "COMP1", "COMP2"));

    assertTrue(duplicateMessages.isEmpty(), formatDuplicateSummary(duplicateMessages));
  }

  /**
   * Finds duplicate key values in a CSV resource.
   *
   * @param resourcePath classpath resource path to the CSV file
   * @param unordered whether multi-column keys should be sorted before grouping
   * @param keyColumns column names that define the unique key
   * @return formatted duplicate key descriptions
   * @throws IOException if the CSV resource cannot be read
   */
  private static List<String> findDuplicateKeys(String resourcePath, boolean unordered,
      String... keyColumns) throws IOException {
    CsvTable table = readCsvResource(resourcePath);
    for (String keyColumn : keyColumns) {
      if (!table.header.contains(keyColumn)) {
        throw new IOException("Missing key column " + keyColumn + " in " + resourcePath);
      }
    }
    Map<String, List<CsvRow>> rowsByKey = new LinkedHashMap<String, List<CsvRow>>();
    for (CsvRow row : table.rows) {
      String key = buildKey(row, keyColumns, unordered);
      if (key.trim().isEmpty()) {
        continue;
      }
      List<CsvRow> rows = rowsByKey.get(key);
      if (rows == null) {
        rows = new ArrayList<CsvRow>();
        rowsByKey.put(key, rows);
      }
      rows.add(row);
    }

    List<String> duplicateMessages = new ArrayList<String>();
    for (Map.Entry<String, List<CsvRow>> entry : rowsByKey.entrySet()) {
      if (entry.getValue().size() > 1) {
        duplicateMessages
            .add(formatDuplicate(resourcePath, keyColumns, entry.getKey(), entry.getValue()));
      }
    }

    return duplicateMessages;
  }

  /**
   * Finds duplicate key values that are attached to different values in another column.
   *
   * @param resourcePath classpath resource path to the CSV file
   * @param keyColumn column name that defines the lookup key
   * @param valueColumn column name that must stay consistent for a duplicate key
   * @return formatted duplicate key descriptions
   * @throws IOException if the CSV resource cannot be read
   */
  private static List<String> findDuplicateKeysWithDifferentValues(String resourcePath,
      String keyColumn, String valueColumn) throws IOException {
    CsvTable table = readCsvResource(resourcePath);
    if (!table.header.contains(keyColumn)) {
      throw new IOException("Missing key column " + keyColumn + " in " + resourcePath);
    }
    if (!table.header.contains(valueColumn)) {
      throw new IOException("Missing value column " + valueColumn + " in " + resourcePath);
    }

    Map<String, List<CsvRow>> rowsByKey = new LinkedHashMap<String, List<CsvRow>>();
    for (CsvRow row : table.rows) {
      String key = row.values.get(keyColumn);
      key = key == null ? "" : key.trim();
      if (key.isEmpty()) {
        continue;
      }
      List<CsvRow> rows = rowsByKey.get(key);
      if (rows == null) {
        rows = new ArrayList<CsvRow>();
        rowsByKey.put(key, rows);
      }
      rows.add(row);
    }

    List<String> duplicateMessages = new ArrayList<String>();
    for (Map.Entry<String, List<CsvRow>> entry : rowsByKey.entrySet()) {
      if (hasDifferentValues(entry.getValue(), valueColumn)) {
        duplicateMessages.add(formatDuplicate(resourcePath, new String[] {keyColumn},
            entry.getKey(), entry.getValue()));
      }
    }
    return duplicateMessages;
  }

  /**
   * Checks whether rows have more than one distinct value in a column.
   *
   * @param rows rows to inspect
   * @param valueColumn column name to compare
   * @return true if rows contain different non-empty values
   */
  private static boolean hasDifferentValues(List<CsvRow> rows, String valueColumn) {
    String firstValue = null;
    for (CsvRow row : rows) {
      String value = row.values.get(valueColumn);
      value = value == null ? "" : value.trim();
      if (value.isEmpty()) {
        continue;
      }
      if (firstValue == null) {
        firstValue = value;
      } else if (!firstValue.equals(value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds duplicate names in a one-name-per-line component index resource.
   *
   * @param resourcePath classpath resource path to the component name index
   * @return formatted duplicate name descriptions
   * @throws IOException if the component name index cannot be read
   */
  private static List<String> findDuplicateNameIndexEntries(String resourcePath)
      throws IOException {
    InputStream inputStream =
        DatabaseCsvDuplicateTest.class.getClassLoader().getResourceAsStream(resourcePath);
    if (inputStream == null) {
      throw new IOException("Missing component name index resource: " + resourcePath);
    }

    Map<String, List<Integer>> linesByName = new LinkedHashMap<String, List<Integer>>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        String name = normalizeNameIndexLine(line, lineNumber);
        if (name.isEmpty() || (lineNumber == 1 && "NAME".equals(name))) {
          continue;
        }
        List<Integer> lines = linesByName.get(name);
        if (lines == null) {
          lines = new ArrayList<Integer>();
          linesByName.put(name, lines);
        }
        lines.add(Integer.valueOf(lineNumber));
      }
    }

    List<String> duplicateMessages = new ArrayList<String>();
    for (Map.Entry<String, List<Integer>> entry : linesByName.entrySet()) {
      if (entry.getValue().size() > 1) {
        duplicateMessages
            .add(formatDuplicateNameIndexEntry(resourcePath, entry.getKey(), entry.getValue()));
      }
    }
    return duplicateMessages;
  }

  /**
   * Normalizes one component name index line.
   *
   * @param line raw line text
   * @param lineNumber one-based resource line number
   * @return normalized component name
   */
  private static String normalizeNameIndexLine(String line, int lineNumber) {
    String name = line.trim();
    if (lineNumber == 1 && name.length() > 0 && name.charAt(0) == '\ufeff') {
      name = name.substring(1).trim();
    }
    return name;
  }

  /**
   * Formats one duplicate component name index entry.
   *
   * @param resourcePath classpath resource path to the component name index
   * @param name duplicate component name
   * @param lines line numbers where the name appears
   * @return formatted duplicate name description
   */
  private static String formatDuplicateNameIndexEntry(String resourcePath, String name,
      List<Integer> lines) {
    StringBuilder message = new StringBuilder();
    message.append(resourcePath).append(" NAME=").append(name).append(" at lines ");
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0) {
        message.append(", ");
      }
      message.append(lines.get(i));
    }
    return message.toString();
  }

  /**
   * Builds a duplicate summary for a failed assertion.
   *
   * @param duplicateMessages formatted duplicate group descriptions
   * @return assertion message for duplicate rows
   */
  private static String formatDuplicateSummary(List<String> duplicateMessages) {
    StringBuilder message = new StringBuilder();
    message.append("Database resources contain ").append(duplicateMessages.size())
        .append(" duplicate group(s)");
    int duplicatesToReport = Math.min(MAX_DUPLICATES_TO_REPORT, duplicateMessages.size());
    for (int i = 0; i < duplicatesToReport; i++) {
      message.append(System.lineSeparator()).append("  - ").append(duplicateMessages.get(i));
    }
    if (duplicateMessages.size() > duplicatesToReport) {
      message.append(System.lineSeparator()).append("  - ... and ")
          .append(duplicateMessages.size() - duplicatesToReport).append(" more");
    }
    return message.toString();
  }

  /**
   * Builds a normalized key from one CSV row.
   *
   * @param row row to read
   * @param keyColumns column names that define the key
   * @param unordered whether key values should be sorted before joining
   * @return normalized key
   */
  private static String buildKey(CsvRow row, String[] keyColumns, boolean unordered) {
    List<String> values = new ArrayList<String>();
    for (String column : keyColumns) {
      String value = row.values.get(column);
      values.add(value == null ? "" : value.trim());
    }
    if (unordered) {
      Collections.sort(values);
    }
    StringBuilder key = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        key.append("||");
      }
      key.append(values.get(i));
    }
    return key.toString();
  }

  /**
   * Formats one duplicate group.
   *
   * @param resourcePath classpath resource path to the CSV file
   * @param keyColumns column names that define the duplicate key
   * @param key duplicate key value
   * @param rows rows sharing the duplicate key
   * @return formatted duplicate group description
   */
  private static String formatDuplicate(String resourcePath, String[] keyColumns, String key,
      List<CsvRow> rows) {
    StringBuilder message = new StringBuilder();
    message.append(resourcePath).append(" ").append(Arrays.toString(keyColumns)).append("=")
        .append(key).append(" at ");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) {
        message.append("; ");
      }
      CsvRow row = rows.get(i);
      message.append("line ").append(row.startLine);
      if (row.values.containsKey("ID")) {
        message.append(" ID=").append(row.values.get("ID"));
      }
      if (row.values.containsKey("NAME")) {
        message.append(" NAME=").append(row.values.get("NAME"));
      }
      if (row.values.containsKey("COMP1") && row.values.containsKey("COMP2")) {
        message.append(" COMP1=").append(row.values.get("COMP1")).append(" COMP2=")
            .append(row.values.get("COMP2"));
      }
    }
    return message.toString();
  }

  /**
   * Reads a classpath CSV resource.
   *
   * @param resourcePath classpath resource path to the CSV file
   * @return parsed CSV table
   * @throws IOException if the CSV resource cannot be read
   */
  private static CsvTable readCsvResource(String resourcePath) throws IOException {
    InputStream inputStream =
        DatabaseCsvDuplicateTest.class.getClassLoader().getResourceAsStream(resourcePath);
    if (inputStream == null) {
      throw new IOException("Missing CSV resource: " + resourcePath);
    }
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      int recordStartLine = 0;
      List<String> header = null;
      List<CsvRow> rows = new ArrayList<CsvRow>();
      StringBuilder record = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (record.length() == 0) {
          recordStartLine = lineNumber;
        } else {
          record.append('\n');
        }
        record.append(line);
        if (!isCompleteCsvRecord(record.toString())) {
          continue;
        }
        List<String> fields = parseCsvRecord(record.toString());
        if (header == null) {
          header = normalizeHeader(fields);
        } else {
          rows.add(new CsvRow(recordStartLine, mapFields(header, fields)));
        }
        record.setLength(0);
      }
      if (record.length() > 0) {
        throw new IOException(
            "Unterminated quoted CSV record in " + resourcePath + " at line " + recordStartLine);
      }
      if (header == null) {
        throw new IOException("CSV resource has no header: " + resourcePath);
      }
      return new CsvTable(header, rows);
    }
  }

  /**
   * Normalizes CSV header values.
   *
   * @param fields raw header fields
   * @return normalized header fields
   */
  private static List<String> normalizeHeader(List<String> fields) {
    List<String> normalized = new ArrayList<String>();
    for (int i = 0; i < fields.size(); i++) {
      String field = fields.get(i).trim();
      if (i == 0 && field.length() > 0 && field.charAt(0) == '\ufeff') {
        field = field.substring(1);
      }
      normalized.add(field);
    }
    return normalized;
  }

  /**
   * Maps CSV fields to header names.
   *
   * @param header header field names
   * @param fields row field values
   * @return map from header name to field value
   */
  private static Map<String, String> mapFields(List<String> header, List<String> fields) {
    Map<String, String> values = new HashMap<String, String>();
    for (int i = 0; i < header.size(); i++) {
      String value = i < fields.size() ? fields.get(i) : "";
      values.put(header.get(i), value);
    }
    return values;
  }

  /**
   * Checks whether a buffered CSV record has balanced quotes.
   *
   * @param record buffered CSV record text
   * @return true if the record is complete
   */
  private static boolean isCompleteCsvRecord(String record) {
    boolean inQuotes = false;
    for (int i = 0; i < record.length(); i++) {
      char character = record.charAt(i);
      if (character == '"') {
        if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      }
    }
    return !inQuotes;
  }

  /**
   * Parses one complete CSV record.
   *
   * @param record complete CSV record text
   * @return parsed fields
   */
  private static List<String> parseCsvRecord(String record) {
    List<String> fields = new ArrayList<String>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < record.length(); i++) {
      char character = record.charAt(i);
      if (character == '"') {
        if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (character == ',' && !inQuotes) {
        fields.add(field.toString());
        field.setLength(0);
      } else {
        field.append(character);
      }
    }
    fields.add(field.toString());
    return fields;
  }

  /** Parsed CSV table. */
  private static final class CsvTable {
    /** Header fields. */
    private final List<String> header;
    /** Data rows. */
    private final List<CsvRow> rows;

    /**
     * Creates a parsed CSV table.
     *
     * @param header header fields
     * @param rows data rows
     */
    private CsvTable(List<String> header, List<CsvRow> rows) {
      this.header = header;
      this.rows = rows;
    }
  }

  /** Parsed CSV row with the source line where the record starts. */
  private static final class CsvRow {
    /** Starting line number for the CSV record. */
    private final int startLine;
    /** Parsed row values by header name. */
    private final Map<String, String> values;

    /**
     * Creates a parsed CSV row.
     *
     * @param startLine starting line number for the record
     * @param values parsed row values by header name
     */
    private CsvRow(int startLine, Map<String, String> values) {
      this.startLine = startLine;
      this.values = values;
    }
  }
}
