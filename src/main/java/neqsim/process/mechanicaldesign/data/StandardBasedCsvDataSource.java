package neqsim.process.mechanicaldesign.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import neqsim.process.mechanicaldesign.DesignLimitData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads mechanical design limits from CSV files with support for international standards.
 *
 * <p>
 * This data source supports two CSV formats:
 * </p>
 *
 * <h2>Standard Format (with STANDARD_CODE column):</h2>
 * 
 * <pre>
 * STANDARD_CODE,STANDARD_VERSION,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,DESCRIPTION
 * NORSOK-L-001,Rev 6,Pipeline,DesignPressureMargin,1.1,1.1,-,Design pressure safety factor
 * ASME-VIII-Div1,2021,Separator,MaxPressure,0,150,barg,Maximum design pressure
 * </pre>
 *
 * <h2>Company Format (legacy compatibility):</h2>
 * 
 * <pre>
 * EQUIPMENTTYPE,COMPANY,MAXPRESSURE,MINPRESSURE,MAXTEMPERATURE,MINTEMPERATURE,CORROSIONALLOWANCE,JOINTEFFICIENCY
 * Pipeline,StatoilTR,100,0,150,-50,3.0,0.85
 * </pre>
 *
 * <p>
 * The data source automatically detects the format based on column headers.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class StandardBasedCsvDataSource implements MechanicalDesignDataSource {
  private static final Logger logger = LogManager.getLogger(StandardBasedCsvDataSource.class);

  private final Path csvPath;
  private final String resourcePath;
  private final boolean isResource;

  // Cached data
  private List<StandardDataRow> cachedData = null;
  private boolean isStandardFormat = false;

  /**
   * Create a data source from a file path.
   *
   * @param csvPath path to the CSV file
   */
  public StandardBasedCsvDataSource(Path csvPath) {
    this.csvPath = csvPath;
    this.resourcePath = null;
    this.isResource = false;
  }

  /**
   * Create a data source from a classpath resource.
   *
   * @param resourcePath the classpath resource path (e.g., "designdata/standards/norsok.csv")
   */
  public StandardBasedCsvDataSource(String resourcePath) {
    this.csvPath = null;
    this.resourcePath = resourcePath;
    this.isResource = true;
  }

  @Override
  public Optional<DesignLimitData> getDesignLimits(String equipmentTypeName,
      String companyIdentifier) {
    ensureLoaded();

    if (!isStandardFormat) {
      // Legacy format - search by equipment and company
      return findByCompany(equipmentTypeName, companyIdentifier);
    }

    // Standard format - treat companyIdentifier as standard code
    return getDesignLimitsByStandard(companyIdentifier, null, equipmentTypeName);
  }

  @Override
  public Optional<DesignLimitData> getDesignLimitsByStandard(String standardCode, String version,
      String equipmentTypeName) {
    ensureLoaded();

    if (!isStandardFormat) {
      // Fallback for legacy format
      return findByCompany(equipmentTypeName, standardCode);
    }

    String normalizedStandard = normalize(standardCode);
    String normalizedEquipment = normalize(equipmentTypeName);
    String normalizedVersion = version != null ? normalize(version) : null;

    DesignLimitData.Builder builder = DesignLimitData.builder();
    boolean found = false;

    for (StandardDataRow row : cachedData) {
      if (!normalize(row.standardCode).equals(normalizedStandard)) {
        continue;
      }
      if (normalizedVersion != null && !normalize(row.version).equals(normalizedVersion)) {
        continue;
      }
      if (!normalize(row.equipmentType).equals(normalizedEquipment)) {
        continue;
      }

      // Apply specification values
      found = applySpecification(builder, row) || found;
    }

    return found ? Optional.of(builder.build()) : Optional.empty();
  }

  @Override
  public List<String> getAvailableStandards(String equipmentTypeName) {
    ensureLoaded();

    List<String> standards = new ArrayList<String>();
    String normalizedEquipment = equipmentTypeName != null ? normalize(equipmentTypeName) : null;

    for (StandardDataRow row : cachedData) {
      String code = row.standardCode;
      if (code == null || code.isEmpty()) {
        code = row.company; // Fallback for legacy format
      }
      if (code != null && !standards.contains(code)) {
        if (normalizedEquipment == null
            || normalize(row.equipmentType).equals(normalizedEquipment)) {
          standards.add(code);
        }
      }
    }

    return standards;
  }

  @Override
  public List<String> getAvailableVersions(String standardCode) {
    ensureLoaded();

    List<String> versions = new ArrayList<String>();
    String normalizedStandard = normalize(standardCode);

    for (StandardDataRow row : cachedData) {
      if (normalize(row.standardCode).equals(normalizedStandard)) {
        if (row.version != null && !row.version.isEmpty() && !versions.contains(row.version)) {
          versions.add(row.version);
        }
      }
    }

    return versions;
  }

  private Optional<DesignLimitData> findByCompany(String equipmentTypeName,
      String companyIdentifier) {
    String normalizedEquipment = normalize(equipmentTypeName);
    String normalizedCompany = normalize(companyIdentifier);

    DesignLimitData.Builder builder = DesignLimitData.builder();
    boolean found = false;

    for (StandardDataRow row : cachedData) {
      if (!normalize(row.equipmentType).equals(normalizedEquipment)) {
        continue;
      }
      if (!normalize(row.company).equals(normalizedCompany)) {
        continue;
      }

      // Apply direct values for legacy format
      if (!Double.isNaN(row.maxPressure)) {
        builder.maxPressure(row.maxPressure);
        found = true;
      }
      if (!Double.isNaN(row.minPressure)) {
        builder.minPressure(row.minPressure);
        found = true;
      }
      if (!Double.isNaN(row.maxTemperature)) {
        builder.maxTemperature(row.maxTemperature);
        found = true;
      }
      if (!Double.isNaN(row.minTemperature)) {
        builder.minTemperature(row.minTemperature);
        found = true;
      }
      if (!Double.isNaN(row.corrosionAllowance)) {
        builder.corrosionAllowance(row.corrosionAllowance);
        found = true;
      }
      if (!Double.isNaN(row.jointEfficiency)) {
        builder.jointEfficiency(row.jointEfficiency);
        found = true;
      }
    }

    return found ? Optional.of(builder.build()) : Optional.empty();
  }

  private boolean applySpecification(DesignLimitData.Builder builder, StandardDataRow row) {
    if (row.specification == null) {
      return false;
    }

    String spec = row.specification.toLowerCase(Locale.ROOT);
    double value = selectValue(row.minValue, row.maxValue);

    if (Double.isNaN(value)) {
      return false;
    }

    if (spec.contains("maxpressure") || spec.equals("designpressure")) {
      builder.maxPressure(row.maxValue);
      return true;
    } else if (spec.contains("minpressure")) {
      builder.minPressure(row.minValue);
      return true;
    } else if (spec.contains("maxtemperature") || spec.equals("designtemperature")) {
      builder.maxTemperature(row.maxValue);
      return true;
    } else if (spec.contains("mintemperature")) {
      builder.minTemperature(row.minValue);
      return true;
    } else if (spec.contains("corrosion")) {
      builder.corrosionAllowance(value);
      return true;
    } else if (spec.contains("joint") || spec.contains("efficiency")) {
      builder.jointEfficiency(value);
      return true;
    }

    return false;
  }

  private double selectValue(double minValue, double maxValue) {
    if (!Double.isNaN(maxValue)) {
      return maxValue;
    }
    if (!Double.isNaN(minValue)) {
      return minValue;
    }
    return Double.NaN;
  }

  private void ensureLoaded() {
    if (cachedData != null) {
      return;
    }
    cachedData = new ArrayList<StandardDataRow>();
    loadData();
  }

  private void loadData() {
    try {
      BufferedReader reader = openReader();
      if (reader == null) {
        return;
      }

      try {
        String header = reader.readLine();
        if (header == null) {
          return;
        }

        String[] columns = header.split(",");
        ColumnIndex index = ColumnIndex.from(columns);
        isStandardFormat = index.hasStandardColumns();

        String line;
        while ((line = reader.readLine()) != null) {
          String[] tokens = parseCsvLine(line);
          if (tokens.length < 2) {
            continue;
          }

          StandardDataRow row = parseRow(tokens, index);
          if (row != null) {
            cachedData.add(row);
          }
        }
      } finally {
        reader.close();
      }
    } catch (IOException ex) {
      logger.error("Failed to read design standard CSV", ex);
    }
  }

  private BufferedReader openReader() throws IOException {
    if (isResource) {
      InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (is == null) {
        logger.warn("Resource not found: {}", resourcePath);
        return null;
      }
      return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    } else {
      if (csvPath == null || !Files.isReadable(csvPath)) {
        logger.warn("CSV file not readable: {}", csvPath);
        return null;
      }
      return Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
    }
  }

  private String[] parseCsvLine(String line) {
    // Simple CSV parsing that handles quoted fields
    List<String> tokens = new ArrayList<String>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        tokens.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    tokens.add(current.toString().trim());

    return tokens.toArray(new String[0]);
  }

  private StandardDataRow parseRow(String[] tokens, ColumnIndex index) {
    StandardDataRow row = new StandardDataRow();

    row.standardCode = getString(tokens, index.standardCodeIndex);
    row.version = getString(tokens, index.versionIndex);
    row.equipmentType = getString(tokens, index.equipmentTypeIndex);
    row.company = getString(tokens, index.companyIndex);
    row.specification = getString(tokens, index.specificationIndex);

    row.minValue = getDouble(tokens, index.minValueIndex);
    row.maxValue = getDouble(tokens, index.maxValueIndex);

    // Legacy format direct columns
    row.maxPressure = getDouble(tokens, index.maxPressureIndex);
    row.minPressure = getDouble(tokens, index.minPressureIndex);
    row.maxTemperature = getDouble(tokens, index.maxTemperatureIndex);
    row.minTemperature = getDouble(tokens, index.minTemperatureIndex);
    row.corrosionAllowance = getDouble(tokens, index.corrosionAllowanceIndex);
    row.jointEfficiency = getDouble(tokens, index.jointEfficiencyIndex);

    return row;
  }

  private String getString(String[] tokens, int index) {
    if (index < 0 || index >= tokens.length) {
      return "";
    }
    String value = tokens[index];
    // Remove surrounding quotes
    if (value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    return value.trim();
  }

  private double getDouble(String[] tokens, int index) {
    String value = getString(tokens, index);
    if (value.isEmpty()) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ex) {
      return Double.NaN;
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  /** Internal data structure for parsed rows. */
  private static final class StandardDataRow {
    String standardCode = "";
    String version = "";
    String equipmentType = "";
    String company = "";
    String specification = "";
    double minValue = Double.NaN;
    double maxValue = Double.NaN;

    // Legacy format columns
    double maxPressure = Double.NaN;
    double minPressure = Double.NaN;
    double maxTemperature = Double.NaN;
    double minTemperature = Double.NaN;
    double corrosionAllowance = Double.NaN;
    double jointEfficiency = Double.NaN;
  }

  /** Column index mapper. */
  private static final class ColumnIndex {
    int standardCodeIndex = -1;
    int versionIndex = -1;
    int equipmentTypeIndex = -1;
    int companyIndex = -1;
    int specificationIndex = -1;
    int minValueIndex = -1;
    int maxValueIndex = -1;

    // Legacy format
    int maxPressureIndex = -1;
    int minPressureIndex = -1;
    int maxTemperatureIndex = -1;
    int minTemperatureIndex = -1;
    int corrosionAllowanceIndex = -1;
    int jointEfficiencyIndex = -1;

    boolean hasStandardColumns() {
      return standardCodeIndex >= 0;
    }

    static ColumnIndex from(String[] columns) {
      ColumnIndex index = new ColumnIndex();

      for (int i = 0; i < columns.length; i++) {
        String col = columns[i].trim().toUpperCase(Locale.ROOT).replace("\"", "");

        if (col.equals("STANDARD_CODE") || col.equals("STANDARDCODE")) {
          index.standardCodeIndex = i;
        } else if (col.equals("STANDARD_VERSION") || col.equals("VERSION")) {
          index.versionIndex = i;
        } else if (col.equals("EQUIPMENTTYPE") || col.equals("EQUIPMENT_TYPE")) {
          index.equipmentTypeIndex = i;
        } else if (col.equals("COMPANY")) {
          index.companyIndex = i;
        } else if (col.equals("SPECIFICATION")) {
          index.specificationIndex = i;
        } else if (col.equals("MINVALUE") || col.equals("MIN_VALUE")) {
          index.minValueIndex = i;
        } else if (col.equals("MAXVALUE") || col.equals("MAX_VALUE")) {
          index.maxValueIndex = i;
        } else if (col.equals("MAXPRESSURE")) {
          index.maxPressureIndex = i;
        } else if (col.equals("MINPRESSURE")) {
          index.minPressureIndex = i;
        } else if (col.equals("MAXTEMPERATURE")) {
          index.maxTemperatureIndex = i;
        } else if (col.equals("MINTEMPERATURE")) {
          index.minTemperatureIndex = i;
        } else if (col.equals("CORROSIONALLOWANCE")) {
          index.corrosionAllowanceIndex = i;
        } else if (col.equals("JOINTEFFICIENCY")) {
          index.jointEfficiencyIndex = i;
        }
      }

      return index;
    }
  }
}
