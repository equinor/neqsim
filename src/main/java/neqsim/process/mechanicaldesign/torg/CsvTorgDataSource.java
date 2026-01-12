package neqsim.process.mechanicaldesign.torg;

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
import java.util.Map;
import java.util.Optional;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CSV-based data source for loading Technical Requirements Documents (TORG).
 *
 * <p>
 * This implementation supports two CSV file formats:
 * </p>
 *
 * <h2>Format 1: Standards-focused (standards.csv)</h2>
 * 
 * <pre>
 * PROJECT_ID,PROJECT_NAME,COMPANY,DESIGN_CATEGORY,STANDARD_CODE,VERSION,PRIORITY
 * PROJ-001,Offshore Platform,Equinor,pressure vessel design code,ASME-VIII-Div1,2021,1
 * PROJ-001,Offshore Platform,Equinor,separator process design,API-12J,8th Ed,1
 * PROJ-001,Offshore Platform,Equinor,pipeline design codes,NORSOK-L-001,Rev 6,1
 * </pre>
 *
 * <h2>Format 2: Full TORG (torg_master.csv)</h2>
 * 
 * <pre>
 * PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,ISSUE_DATE,MIN_AMBIENT_TEMP,MAX_AMBIENT_TEMP,...
 * PROJ-001,Offshore Platform,Equinor,2,-40,45,...
 * </pre>
 *
 * <p>
 * When using Format 1, the data source automatically combines rows with the same PROJECT_ID into a
 * single TechnicalRequirementsDocument.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class CsvTorgDataSource implements TorgDataSource {
  private static final Logger logger = LogManager.getLogger(CsvTorgDataSource.class);

  /** CSV column names for standards format. */
  private static final String COL_PROJECT_ID = "PROJECT_ID";
  private static final String COL_PROJECT_NAME = "PROJECT_NAME";
  private static final String COL_COMPANY = "COMPANY";
  private static final String COL_REVISION = "REVISION";
  private static final String COL_ISSUE_DATE = "ISSUE_DATE";
  private static final String COL_DESIGN_CATEGORY = "DESIGN_CATEGORY";
  private static final String COL_STANDARD_CODE = "STANDARD_CODE";
  private static final String COL_VERSION = "VERSION";
  private static final String COL_MIN_AMBIENT_TEMP = "MIN_AMBIENT_TEMP";
  private static final String COL_MAX_AMBIENT_TEMP = "MAX_AMBIENT_TEMP";
  private static final String COL_SEAWATER_TEMP = "SEAWATER_TEMP";
  private static final String COL_SEISMIC_ZONE = "SEISMIC_ZONE";
  private static final String COL_CORROSION_ALLOWANCE = "CORROSION_ALLOWANCE";
  private static final String COL_PRESSURE_SF = "PRESSURE_SAFETY_FACTOR";
  private static final String COL_PLATE_MATERIAL = "DEFAULT_PLATE_MATERIAL";
  private static final String COL_PIPE_MATERIAL = "DEFAULT_PIPE_MATERIAL";

  private final Path csvPath;
  private Map<String, TechnicalRequirementsDocument> cache = null;
  private boolean isLoaded = false;

  /**
   * Create a CsvTorgDataSource from a file path.
   *
   * @param csvPath path to the CSV file
   */
  public CsvTorgDataSource(Path csvPath) {
    this.csvPath = csvPath;
  }

  /**
   * Create a CsvTorgDataSource from a classpath resource.
   *
   * @param resourcePath path relative to classpath (e.g., "designdata/torg/projects.csv")
   * @return new data source instance
   */
  public static CsvTorgDataSource fromResource(String resourcePath) {
    // Create a wrapper that loads from classpath
    return new CsvTorgDataSource(null) {
      @Override
      protected BufferedReader getReader() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
          throw new IOException("Resource not found: " + resourcePath);
        }
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      }
    };
  }

  /**
   * Get a BufferedReader for the CSV data.
   *
   * @return reader for CSV content
   * @throws IOException if the file cannot be read
   */
  protected BufferedReader getReader() throws IOException {
    if (csvPath == null || !Files.exists(csvPath)) {
      throw new IOException("CSV file not found: " + csvPath);
    }
    return Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
  }

  /**
   * Load and parse the CSV file.
   */
  private synchronized void loadData() {
    if (isLoaded) {
      return;
    }

    cache = new HashMap<>();

    try (BufferedReader reader = getReader()) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        logger.warn("Empty CSV file");
        isLoaded = true;
        return;
      }

      String[] headers = parseCSVLine(headerLine);
      Map<String, Integer> headerIndex = new HashMap<>();
      for (int i = 0; i < headers.length; i++) {
        headerIndex.put(headers[i].toUpperCase().trim(), i);
      }

      // Determine format based on headers
      boolean isStandardsFormat = headerIndex.containsKey(COL_DESIGN_CATEGORY);

      if (isStandardsFormat) {
        loadStandardsFormat(reader, headerIndex);
      } else {
        loadMasterFormat(reader, headerIndex);
      }

      isLoaded = true;
    } catch (IOException e) {
      logger.error("Failed to load TORG CSV: " + e.getMessage(), e);
    }
  }

  /**
   * Load standards-focused CSV format where each row is a standard assignment.
   */
  private void loadStandardsFormat(BufferedReader reader, Map<String, Integer> headerIndex)
      throws IOException {
    // Group rows by project ID
    Map<String, List<String[]>> projectRows = new HashMap<>();

    String line;
    while ((line = reader.readLine()) != null) {
      if (line.trim().isEmpty()) {
        continue;
      }

      String[] values = parseCSVLine(line);
      String projectId = getField(values, headerIndex, COL_PROJECT_ID);
      if (projectId != null && !projectId.isEmpty()) {
        List<String[]> rows = projectRows.get(projectId);
        if (rows == null) {
          rows = new ArrayList<>();
          projectRows.put(projectId, rows);
        }
        rows.add(values);
      }
    }

    // Build TORG documents from grouped rows
    for (Map.Entry<String, List<String[]>> entry : projectRows.entrySet()) {
      String projectId = entry.getKey();
      List<String[]> rows = entry.getValue();

      if (rows.isEmpty()) {
        continue;
      }

      // Use first row for project metadata
      String[] firstRow = rows.get(0);
      TechnicalRequirementsDocument.Builder builder = TechnicalRequirementsDocument.builder()
          .projectId(projectId).projectName(getField(firstRow, headerIndex, COL_PROJECT_NAME))
          .companyIdentifier(getField(firstRow, headerIndex, COL_COMPANY))
          .revision(getFieldOrDefault(firstRow, headerIndex, COL_REVISION, "1"))
          .issueDate(getField(firstRow, headerIndex, COL_ISSUE_DATE));

      // Add standards from all rows
      for (String[] row : rows) {
        String category = getField(row, headerIndex, COL_DESIGN_CATEGORY);
        String standardCode = getField(row, headerIndex, COL_STANDARD_CODE);

        if (category != null && standardCode != null) {
          StandardType type = StandardType.fromCode(standardCode);
          if (type != null) {
            builder.addStandard(category, type);
          } else {
            logger.warn("Unknown standard code: " + standardCode);
          }
        }
      }

      // Load environmental conditions if present
      String minAmbient = getField(firstRow, headerIndex, COL_MIN_AMBIENT_TEMP);
      String maxAmbient = getField(firstRow, headerIndex, COL_MAX_AMBIENT_TEMP);
      if (minAmbient != null || maxAmbient != null) {
        double minTemp = parseDouble(minAmbient, -40.0);
        double maxTemp = parseDouble(maxAmbient, 45.0);
        double seawaterTemp = parseDouble(getField(firstRow, headerIndex, COL_SEAWATER_TEMP), 4.0);
        String seismicZone = getFieldOrDefault(firstRow, headerIndex, COL_SEISMIC_ZONE, "0");

        builder.environmentalConditions(new TechnicalRequirementsDocument.EnvironmentalConditions(
            minTemp, maxTemp, seawaterTemp, seismicZone, 0, 0, ""));
      }

      cache.put(projectId, builder.build());
    }
  }

  /**
   * Load master format where each row is a complete TORG (less common).
   */
  private void loadMasterFormat(BufferedReader reader, Map<String, Integer> headerIndex)
      throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      if (line.trim().isEmpty()) {
        continue;
      }

      String[] values = parseCSVLine(line);
      String projectId = getField(values, headerIndex, COL_PROJECT_ID);

      if (projectId == null || projectId.isEmpty()) {
        continue;
      }

      TechnicalRequirementsDocument.Builder builder = TechnicalRequirementsDocument.builder()
          .projectId(projectId).projectName(getField(values, headerIndex, COL_PROJECT_NAME))
          .companyIdentifier(getField(values, headerIndex, COL_COMPANY))
          .revision(getFieldOrDefault(values, headerIndex, COL_REVISION, "1"))
          .issueDate(getField(values, headerIndex, COL_ISSUE_DATE));

      // Environmental conditions
      String minAmbient = getField(values, headerIndex, COL_MIN_AMBIENT_TEMP);
      String maxAmbient = getField(values, headerIndex, COL_MAX_AMBIENT_TEMP);
      if (minAmbient != null || maxAmbient != null) {
        double minTemp = parseDouble(minAmbient, -40.0);
        double maxTemp = parseDouble(maxAmbient, 45.0);
        double seawaterTemp = parseDouble(getField(values, headerIndex, COL_SEAWATER_TEMP), 4.0);
        String seismicZone = getFieldOrDefault(values, headerIndex, COL_SEISMIC_ZONE, "0");

        builder.environmentalConditions(new TechnicalRequirementsDocument.EnvironmentalConditions(
            minTemp, maxTemp, seawaterTemp, seismicZone, 0, 0, ""));
      }

      // Safety factors
      String pressureSF = getField(values, headerIndex, COL_PRESSURE_SF);
      String corrosion = getField(values, headerIndex, COL_CORROSION_ALLOWANCE);
      if (pressureSF != null || corrosion != null) {
        builder.safetyFactors(new TechnicalRequirementsDocument.SafetyFactors(
            parseDouble(pressureSF, 1.1), 10.0, parseDouble(corrosion, 3.0), 0.125, 1.0));
      }

      // Material specs
      String plateMat = getField(values, headerIndex, COL_PLATE_MATERIAL);
      String pipeMat = getField(values, headerIndex, COL_PIPE_MATERIAL);
      if (plateMat != null || pipeMat != null) {
        double minDesignTemp = parseDouble(minAmbient, -46.0);
        builder.materialSpecifications(new TechnicalRequirementsDocument.MaterialSpecifications(
            plateMat != null ? plateMat : "A516-70", pipeMat != null ? pipeMat : "A106-B",
            minDesignTemp, 300.0, minDesignTemp < -29, "ASTM"));
      }

      cache.put(projectId, builder.build());
    }
  }

  @Override
  public Optional<TechnicalRequirementsDocument> loadByProjectId(String projectId) {
    loadData();
    return Optional.ofNullable(cache.get(projectId));
  }

  @Override
  public Optional<TechnicalRequirementsDocument> loadByCompanyAndProject(String companyIdentifier,
      String projectName) {
    loadData();
    for (TechnicalRequirementsDocument torg : cache.values()) {
      if (companyIdentifier.equalsIgnoreCase(torg.getCompanyIdentifier())
          && projectName.equalsIgnoreCase(torg.getProjectName())) {
        return Optional.of(torg);
      }
    }
    return Optional.empty();
  }

  @Override
  public List<String> getAvailableProjectIds() {
    loadData();
    return new ArrayList<>(cache.keySet());
  }

  @Override
  public List<String> getAvailableCompanies() {
    loadData();
    List<String> companies = new ArrayList<>();
    for (TechnicalRequirementsDocument torg : cache.values()) {
      String company = torg.getCompanyIdentifier();
      if (company != null && !companies.contains(company)) {
        companies.add(company);
      }
    }
    return companies;
  }

  // ============== Helper methods ==============

  private String[] parseCSVLine(String line) {
    List<String> fields = new ArrayList<>();
    boolean inQuotes = false;
    StringBuilder current = new StringBuilder();

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);

      if (c == '"') {
        inQuotes = !inQuotes;
      } else if (c == ',' && !inQuotes) {
        fields.add(current.toString().trim());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString().trim());

    return fields.toArray(new String[0]);
  }

  private String getField(String[] values, Map<String, Integer> headerIndex, String column) {
    Integer idx = headerIndex.get(column);
    if (idx == null || idx >= values.length) {
      return null;
    }
    String val = values[idx].trim();
    return val.isEmpty() ? null : val;
  }

  private String getFieldOrDefault(String[] values, Map<String, Integer> headerIndex, String column,
      String defaultValue) {
    String val = getField(values, headerIndex, column);
    return val != null ? val : defaultValue;
  }

  private double parseDouble(String value, double defaultValue) {
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
