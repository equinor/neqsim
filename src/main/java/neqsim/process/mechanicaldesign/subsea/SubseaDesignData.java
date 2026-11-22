package neqsim.process.mechanicaldesign.subsea;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the bundled subsea screening tables without substituting an unknown material grade.
 *
 * @author ESOL
 * @version 1.0
 */
final class SubseaDesignData {
  /** Immutable resource rows cached by table name. */
  private static final Map<String, List<Map<String, String>>> TABLES = new ConcurrentHashMap<>();

  /** Prevents instantiation of this table utility. */
  private SubseaDesignData() {
  }

  /**
   * Gets room-temperature yield strength from the material table. The historical F22 name denotes Class 3; Class 1 must
   * be selected explicitly. Inconel cladding receives no structural credit.
   *
   * @param materialGrade table grade, or the legacy F22-Inconel625 hub name
   * @return specified minimum yield strength in MPa
   * @throws IllegalArgumentException if the material grade is absent or unknown
   */
  static double yieldStrength(String materialGrade) {
    String grade = normalize(materialGrade);
    if (grade.equals("F22INCONEL625") || grade.equals("F22CLASS3") || grade.equals("SA182F22")) {
      grade = "F22";
    }
    double strength = value("MaterialProperties_Subsea", "SMYS_MPa", "MaterialGrade", grade);
    if (strength <= 0) {
      throw new IllegalArgumentException("Nonpositive subsea yield strength for " + materialGrade);
    }
    return strength;
  }

  /**
   * Finds a nonnegative numeric table value by exact, case-insensitive selectors.
   *
   * @param table resource table name without the extension
   * @param column numeric column name
   * @param selectors alternating selector column names and values
   * @return table value in the units stated in the table
   * @throws IllegalArgumentException if no row matches, a selector is malformed, or data are invalid
   */
  static double value(String table, String column, String... selectors) {
    if (selectors.length == 0 || selectors.length % 2 != 0) {
      throw new IllegalArgumentException("Subsea table selectors require column/value pairs");
    }
    for (Map<String, String> row : TABLES.computeIfAbsent(table, SubseaDesignData::readTable)) {
      boolean matches = true;
      for (int i = 0; i < selectors.length; i += 2) {
        matches &= normalize(selectors[i + 1]).equals(normalize(row.get(selectors[i])));
      }
      if (matches) {
        double result = Double.parseDouble(row.get(column));
        if (!Double.isFinite(result) || result < 0) {
          throw new IllegalArgumentException("Invalid " + column + " in subsea table " + table);
        }
        return result;
      }
    }
    throw new IllegalArgumentException("No qualified row in " + table + " for " + java.util.Arrays.toString(selectors));
  }

  /**
   * Reads a bundled, unquoted screening CSV table with one record per line.
   *
   * @param table resource table name without extension
   * @return immutable rows keyed by the CSV header
   * @throws IllegalStateException if the required resource is missing or unreadable
   */
  private static List<Map<String, String>> readTable(String table) {
    String resource = "designdata/" + table + ".csv";
    InputStream stream = SubseaDesignData.class.getClassLoader().getResourceAsStream(resource);
    if (stream == null) {
      throw new IllegalStateException("Missing subsea design table: " + resource);
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      if (header == null) {
        throw new IllegalStateException("Empty subsea design table: " + resource);
      }
      String[] columns = header.split(",", -1);
      List<Map<String, String>> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        String[] values = line.split(",", -1);
        if (values.length != columns.length) {
          throw new IllegalStateException("Malformed row in subsea design table: " + resource);
        }
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < columns.length; i++) {
          row.put(columns[i].trim(), values[i].trim());
        }
        rows.add(Collections.unmodifiableMap(row));
      }
      return Collections.unmodifiableList(rows);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot read subsea design table: " + resource, ex);
    }
  }

  /**
   * Normalizes separators in table identifiers without partial matching alloy names.
   *
   * @param value table identifier or null
   * @return normalized identifier, or an empty string for null
   */
  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_-]", "");
  }
}
