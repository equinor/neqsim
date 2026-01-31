package neqsim.process.safety.risk.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Imports equipment reliability data from OREDA-format CSV files.
 *
 * <p>
 * OREDA (Offshore and Onshore Reliability Data) is the industry standard for equipment reliability
 * data in the oil and gas industry. This class imports data from CSV files following the OREDA
 * format structure.
 * </p>
 *
 * <h2>CSV Format</h2> The expected CSV format is:
 * 
 * <pre>
 * EquipmentType,EquipmentClass,FailureMode,FailureRate,MTBF_hours,MTTR_hours,DataSource,Confidence
 * Compressor,Centrifugal,All modes,1.14e-4,8772,72,OREDA-2015,High
 * Separator,Two-phase,All modes,5.71e-5,17513,24,OREDA-2015,High
 * </pre>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>
 * OREDADataImporter importer = new OREDADataImporter();
 * importer.loadFromResource("/reliabilitydata/oreda_equipment.csv");
 * 
 * ReliabilityRecord record = importer.getRecord("Compressor", "Centrifugal");
 * double mtbf = record.getMtbfHours();
 * double failureRate = record.getFailureRate();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @since 3.3.0
 * @see <a href="https://www.oreda.com">OREDA Handbook</a>
 */
public class OREDADataImporter implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(OREDADataImporter.class);

  private List<ReliabilityRecord> records;
  private Map<String, List<ReliabilityRecord>> byEquipmentType;
  private Map<String, ReliabilityRecord> byKey;
  private String dataSource;

  /**
   * Reliability record from OREDA data.
   */
  public static class ReliabilityRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String equipmentType;
    private String equipmentClass;
    private String failureMode;
    private double failureRate; // per hour
    private double mtbfHours;
    private double mttrHours;
    private String dataSource;
    private String confidence;
    private String notes;

    /**
     * Creates a reliability record.
     *
     * @param equipmentType equipment type (e.g., "Compressor")
     * @param equipmentClass equipment class (e.g., "Centrifugal")
     * @param failureMode failure mode (e.g., "All modes", "Critical")
     * @param failureRate failure rate per hour
     * @param mtbfHours mean time between failures in hours
     * @param mttrHours mean time to repair in hours
     * @param dataSource data source (e.g., "OREDA-2015")
     * @param confidence confidence level (e.g., "High", "Medium", "Low")
     */
    public ReliabilityRecord(String equipmentType, String equipmentClass, String failureMode,
        double failureRate, double mtbfHours, double mttrHours, String dataSource,
        String confidence) {
      this.equipmentType = equipmentType;
      this.equipmentClass = equipmentClass;
      this.failureMode = failureMode;
      this.failureRate = failureRate;
      this.mtbfHours = mtbfHours;
      this.mttrHours = mttrHours;
      this.dataSource = dataSource;
      this.confidence = confidence;
    }

    /**
     * Gets the equipment type.
     *
     * @return equipment type
     */
    public String getEquipmentType() {
      return equipmentType;
    }

    /**
     * Gets the equipment class.
     *
     * @return equipment class
     */
    public String getEquipmentClass() {
      return equipmentClass;
    }

    /**
     * Gets the failure mode.
     *
     * @return failure mode
     */
    public String getFailureMode() {
      return failureMode;
    }

    /**
     * Gets the failure rate per hour.
     *
     * @return failure rate (failures per hour)
     */
    public double getFailureRate() {
      return failureRate;
    }

    /**
     * Gets the mean time between failures in hours.
     *
     * @return MTBF in hours
     */
    public double getMtbfHours() {
      return mtbfHours;
    }

    /**
     * Gets the mean time to repair in hours.
     *
     * @return MTTR in hours
     */
    public double getMttrHours() {
      return mttrHours;
    }

    /**
     * Gets the data source.
     *
     * @return data source identifier
     */
    public String getDataSource() {
      return dataSource;
    }

    /**
     * Gets the confidence level.
     *
     * @return confidence level
     */
    public String getConfidence() {
      return confidence;
    }

    /**
     * Gets additional notes.
     *
     * @return notes or null
     */
    public String getNotes() {
      return notes;
    }

    /**
     * Sets additional notes.
     *
     * @param notes notes to set
     */
    public void setNotes(String notes) {
      this.notes = notes;
    }

    /**
     * Gets the unique key for this record.
     *
     * @return key in format "EquipmentType|EquipmentClass|FailureMode"
     */
    public String getKey() {
      return equipmentType + "|" + equipmentClass + "|" + failureMode;
    }

    /**
     * Calculates availability based on MTBF and MTTR.
     *
     * @return availability as fraction (0-1)
     */
    public double getAvailability() {
      if (mtbfHours + mttrHours <= 0) {
        return 1.0;
      }
      return mtbfHours / (mtbfHours + mttrHours);
    }

    /**
     * Gets failure rate per year.
     *
     * @return failure rate per year
     */
    public double getFailureRatePerYear() {
      return failureRate * 8760;
    }

    /**
     * Converts record to map for JSON serialization.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("equipmentType", equipmentType);
      map.put("equipmentClass", equipmentClass);
      map.put("failureMode", failureMode);
      map.put("failureRate_perHour", failureRate);
      map.put("failureRate_perYear", getFailureRatePerYear());
      map.put("mtbf_hours", mtbfHours);
      map.put("mttr_hours", mttrHours);
      map.put("availability", getAvailability());
      map.put("dataSource", dataSource);
      map.put("confidence", confidence);
      if (notes != null) {
        map.put("notes", notes);
      }
      return map;
    }

    @Override
    public String toString() {
      return String.format("%s (%s) - MTBF: %.0f hrs, MTTR: %.0f hrs, λ: %.2e /hr", equipmentType,
          equipmentClass, mtbfHours, mttrHours, failureRate);
    }
  }

  /**
   * Creates a new OREDA data importer.
   */
  public OREDADataImporter() {
    this.records = new ArrayList<>();
    this.byEquipmentType = new HashMap<>();
    this.byKey = new HashMap<>();
  }

  /**
   * Loads reliability data from a resource file.
   *
   * @param resourcePath path to resource file (e.g., "/reliabilitydata/oreda.csv")
   * @throws IOException if file cannot be read
   */
  public void loadFromResource(String resourcePath) throws IOException {
    InputStream is = getClass().getResourceAsStream(resourcePath);
    if (is == null) {
      throw new IOException("Resource not found: " + resourcePath);
    }
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      loadFromReader(reader);
    }
    this.dataSource = resourcePath;
    logger.info("Loaded {} reliability records from {}", records.size(), resourcePath);
  }

  /**
   * Loads reliability data from a file path.
   *
   * @param filePath path to CSV file
   * @throws IOException if file cannot be read
   */
  public void loadFromFile(Path filePath) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
      loadFromReader(reader);
    }
    this.dataSource = filePath.toString();
    logger.info("Loaded {} reliability records from {}", records.size(), filePath);
  }

  /**
   * Loads reliability data from a BufferedReader.
   *
   * @param reader BufferedReader to read from
   * @throws IOException if reading fails
   */
  private void loadFromReader(BufferedReader reader) throws IOException {
    String line;
    boolean isHeader = true;
    int lineNumber = 0;

    while ((line = reader.readLine()) != null) {
      lineNumber++;
      line = line.trim();

      // Skip empty lines and comments
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      // Skip header
      if (isHeader) {
        isHeader = false;
        continue;
      }

      try {
        ReliabilityRecord record = parseRecord(line);
        addRecord(record);
      } catch (Exception e) {
        logger.warn("Failed to parse line {}: {} - {}", lineNumber, line, e.getMessage());
      }
    }
  }

  /**
   * Parses a CSV line into a ReliabilityRecord.
   *
   * @param line CSV line
   * @return parsed record
   */
  private ReliabilityRecord parseRecord(String line) {
    String[] parts = line.split(",");
    if (parts.length < 8) {
      throw new IllegalArgumentException("Insufficient columns: " + parts.length);
    }

    String equipmentType = parts[0].trim();
    String equipmentClass = parts[1].trim();
    String failureMode = parts[2].trim();
    double failureRate = parseDouble(parts[3].trim());
    double mtbfHours = parseDouble(parts[4].trim());
    double mttrHours = parseDouble(parts[5].trim());
    String source = parts[6].trim();
    String confidence = parts[7].trim();

    ReliabilityRecord record = new ReliabilityRecord(equipmentType, equipmentClass, failureMode,
        failureRate, mtbfHours, mttrHours, source, confidence);

    // Optional notes column
    if (parts.length > 8) {
      record.setNotes(parts[8].trim());
    }

    return record;
  }

  /**
   * Parses a double value, handling scientific notation.
   *
   * @param value string value
   * @return parsed double
   */
  private double parseDouble(String value) {
    if (value == null || value.isEmpty() || value.equals("-")) {
      return 0.0;
    }
    return Double.parseDouble(value);
  }

  /**
   * Adds a record to the importer.
   *
   * @param record record to add
   */
  public void addRecord(ReliabilityRecord record) {
    records.add(record);
    byKey.put(record.getKey(), record);

    List<ReliabilityRecord> typeList = byEquipmentType.get(record.getEquipmentType());
    if (typeList == null) {
      typeList = new ArrayList<>();
      byEquipmentType.put(record.getEquipmentType(), typeList);
    }
    typeList.add(record);
  }

  /**
   * Gets a specific record by equipment type, class, and failure mode.
   *
   * @param equipmentType equipment type
   * @param equipmentClass equipment class
   * @param failureMode failure mode
   * @return record or null if not found
   */
  public ReliabilityRecord getRecord(String equipmentType, String equipmentClass,
      String failureMode) {
    String key = equipmentType + "|" + equipmentClass + "|" + failureMode;
    return byKey.get(key);
  }

  /**
   * Gets a record by equipment type and class (defaults to "All modes").
   *
   * @param equipmentType equipment type
   * @param equipmentClass equipment class
   * @return record or null if not found
   */
  public ReliabilityRecord getRecord(String equipmentType, String equipmentClass) {
    return getRecord(equipmentType, equipmentClass, "All modes");
  }

  /**
   * Gets all records for an equipment type.
   *
   * @param equipmentType equipment type
   * @return list of records (empty if none found)
   */
  public List<ReliabilityRecord> getRecordsByType(String equipmentType) {
    List<ReliabilityRecord> result = byEquipmentType.get(equipmentType);
    return result != null ? new ArrayList<>(result) : new ArrayList<>();
  }

  /**
   * Gets all unique equipment types.
   *
   * @return list of equipment types
   */
  public List<String> getEquipmentTypes() {
    return new ArrayList<>(byEquipmentType.keySet());
  }

  /**
   * Gets all records.
   *
   * @return list of all records
   */
  public List<ReliabilityRecord> getAllRecords() {
    return new ArrayList<>(records);
  }

  /**
   * Gets the number of records loaded.
   *
   * @return record count
   */
  public int getRecordCount() {
    return records.size();
  }

  /**
   * Gets the data source identifier.
   *
   * @return data source
   */
  public String getDataSource() {
    return dataSource;
  }

  /**
   * Searches for records matching a pattern.
   *
   * @param pattern pattern to match (case-insensitive)
   * @return matching records
   */
  public List<ReliabilityRecord> search(String pattern) {
    List<ReliabilityRecord> results = new ArrayList<>();
    String lowerPattern = pattern.toLowerCase();

    for (ReliabilityRecord record : records) {
      if (record.getEquipmentType().toLowerCase().contains(lowerPattern)
          || record.getEquipmentClass().toLowerCase().contains(lowerPattern)
          || record.getFailureMode().toLowerCase().contains(lowerPattern)) {
        results.add(record);
      }
    }
    return results;
  }

  /**
   * Creates a default importer with built-in OREDA data.
   *
   * @return importer with default data
   */
  public static OREDADataImporter createWithDefaults() {
    OREDADataImporter importer = new OREDADataImporter();

    // Add commonly used equipment reliability data (based on OREDA handbook values)
    // Compressors
    importer.addRecord(new ReliabilityRecord("Compressor", "Centrifugal", "All modes", 1.14e-4,
        8772, 72, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Compressor", "Reciprocating", "All modes", 2.28e-4,
        4386, 96, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Compressor", "Screw", "All modes", 1.71e-4, 5848, 48,
        "OREDA-2015", "Medium"));

    // Pumps
    importer.addRecord(new ReliabilityRecord("Pump", "Centrifugal", "All modes", 1.83e-4, 5464, 24,
        "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Pump", "Reciprocating", "All modes", 3.65e-4, 2740,
        48, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Pump", "Submersible (ESP)", "All modes", 5.71e-4,
        1751, 168, "OREDA-2015", "Medium"));

    // Separators
    importer.addRecord(new ReliabilityRecord("Separator", "Two-phase", "All modes", 5.71e-5, 17513,
        24, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Separator", "Three-phase", "All modes", 6.85e-5,
        14599, 36, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Separator", "Gas scrubber", "All modes", 4.57e-5,
        21882, 16, "OREDA-2015", "High"));

    // Heat exchangers
    importer.addRecord(new ReliabilityRecord("Heat Exchanger", "Shell and tube", "All modes",
        2.28e-5, 43860, 48, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Heat Exchanger", "Plate", "All modes", 3.43e-5, 29155,
        24, "OREDA-2015", "Medium"));
    importer.addRecord(new ReliabilityRecord("Heat Exchanger", "Air cooler", "All modes", 4.57e-5,
        21882, 36, "OREDA-2015", "High"));

    // Valves
    importer.addRecord(new ReliabilityRecord("Valve", "Ball", "All modes", 1.14e-5, 87720, 8,
        "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Valve", "Gate", "All modes", 1.37e-5, 73050, 8,
        "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Valve", "Control", "All modes", 4.57e-5, 21882, 12,
        "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Valve", "Safety/Relief", "All modes", 2.28e-5, 43860,
        4, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Valve", "Check", "All modes", 1.83e-5, 54675, 4,
        "OREDA-2015", "High"));

    // Turbines/Drivers
    importer.addRecord(new ReliabilityRecord("Gas Turbine", "Industrial", "All modes", 1.37e-4,
        7299, 120, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Electric Motor", "Large (>1MW)", "All modes", 4.57e-5,
        21882, 48, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Electric Motor", "Medium", "All modes", 2.28e-5,
        43860, 24, "OREDA-2015", "High"));

    // Piping and vessels
    importer.addRecord(new ReliabilityRecord("Pressure Vessel", "Vertical", "All modes", 1.14e-5,
        87720, 48, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Pressure Vessel", "Horizontal", "All modes", 1.14e-5,
        87720, 48, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Pipeline", "Process piping", "All modes", 5.71e-7,
        1751300, 24, "OREDA-2015", "High"));

    // Subsea equipment
    importer.addRecord(new ReliabilityRecord("Subsea Tree", "Vertical", "All modes", 2.28e-5, 43860,
        720, "OREDA-2015", "Medium"));
    importer.addRecord(new ReliabilityRecord("Subsea Manifold", "Production", "All modes", 1.14e-5,
        87720, 480, "OREDA-2015", "Medium"));
    importer.addRecord(new ReliabilityRecord("Umbilical", "Electro-hydraulic", "All modes", 1.83e-6,
        546750, 720, "OREDA-2015", "Low"));
    importer.addRecord(new ReliabilityRecord("Flexible Riser", "Dynamic", "All modes", 5.71e-6,
        175130, 480, "OREDA-2015", "Medium"));

    // Instrumentation
    importer.addRecord(new ReliabilityRecord("Transmitter", "Pressure", "All modes", 6.85e-6,
        146000, 4, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Transmitter", "Temperature", "All modes", 4.57e-6,
        219000, 4, "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Transmitter", "Flow", "All modes", 9.13e-6, 109500, 8,
        "OREDA-2015", "High"));
    importer.addRecord(new ReliabilityRecord("Transmitter", "Level", "All modes", 6.85e-6, 146000,
        4, "OREDA-2015", "High"));

    importer.dataSource = "OREDA-2015 (Built-in defaults)";
    return importer;
  }
}
