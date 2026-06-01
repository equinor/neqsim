package neqsim.process.equipment.failure;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Data source for equipment reliability data (MTBF, MTTR, failure modes).
 *
 * <p>
 * Loads reliability data from CSV files bundled in
 * {@code src/main/resources/reliabilitydata/}. The primary files
 * ({@code equipment_reliability.csv}, {@code failure_modes.csv}) provide a concise lookup
 * table. Supplementary files add depth from multiple public data sources:
 *
 * <h2>Data Sources</h2>
 *
 * <table>
 * <caption>Reliability data sources and access information</caption>
 * <tr><th>Database</th><th>Access</th><th>Coverage</th></tr>
 * <tr><td>OREDA 6th Edition (2015)</td><td>Commercial (consortium license)</td>
 *     <td>Offshore oil &amp; gas equipment failure rates and repair times</td></tr>
 * <tr><td>IOGP Report 434-Series</td><td>Free (iogp.org)</td>
 *     <td>Offshore safety performance indicators and process safety events</td></tr>
 * <tr><td>UK HSE Offshore Hydrocarbon Release Statistics</td><td>Free (hse.gov.uk)</td>
 *     <td>Leak frequencies and hole-size distributions for offshore O&amp;G</td></tr>
 * <tr><td>IEEE 493 Gold Book (2007)</td><td>Standard (purchasable)</td>
 *     <td>Industrial power system equipment reliability</td></tr>
 * <tr><td>CCPS Process Equipment Reliability Data (1989)</td><td>Published handbook</td>
 *     <td>Process vessels, piping, instruments, and valves</td></tr>
 * <tr><td>Lees' Loss Prevention (4th Ed, 2012)</td><td>Published textbook</td>
 *     <td>Generic process industry failure rates</td></tr>
 * <tr><td>ISO 14224</td><td>Standard (~CHF 200)</td>
 *     <td>Taxonomy and data-collection format for reliability data</td></tr>
 * <tr><td>API 689</td><td>Standard (purchasable)</td>
 *     <td>Collection and exchange of reliability data for process equipment</td></tr>
 * <tr><td>SINTEF PDS Data Handbook</td><td>Purchasable (~EUR 500)</td>
 *     <td>Safety-instrumented system failure rates</td></tr>
 * </table>
 *
 * <p>
 * The hardcoded defaults work out of the box; no external database purchase is needed.
 * Users can override data by placing custom CSVs in the resource path.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>
 * {@code
 * ReliabilityDataSource dataSource = ReliabilityDataSource.getInstance();
 *
 * // Get reliability data for a compressor
 * ReliabilityData data = dataSource.getReliabilityData("Compressor", "Centrifugal");
 * System.out.println("MTBF: " + data.getMtbf() + " hours");
 * System.out.println("MTTR: " + data.getMttr() + " hours");
 *
 * // Get failure modes
 * List<FailureModeData> modes = dataSource.getFailureModes("Compressor", "Centrifugal");
 * for (FailureModeData mode : modes) {
 *   System.out.println(mode.getFailureMode() + ": " + mode.getProbability() + "%");
 * }
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ReliabilityDataSource implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(ReliabilityDataSource.class);

  /** Singleton instance. */
  private static volatile ReliabilityDataSource instance;

  /** Equipment reliability data. */
  private Map<String, ReliabilityData> reliabilityData;

  /** Failure mode data. */
  private Map<String, List<FailureModeData>> failureModes;

  /** Flag indicating if data is loaded. */
  private boolean dataLoaded;

  /**
   * Equipment reliability data holder.
   */
  public static class ReliabilityData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String equipmentType;
    private String subType;
    private double mtbf; // Mean time between failures (hours)
    private double mttr; // Mean time to repair (hours)
    private double failureRate; // Failures per million hours
    private double availability;
    private String source;
    private String notes;

    /**
     * Default constructor.
     */
    public ReliabilityData() {}

    /**
     * Creates reliability data.
     *
     * @param equipmentType equipment type
     * @param subType equipment subtype
     * @param mtbf mean time between failures in hours
     * @param mttr mean time to repair in hours
     */
    public ReliabilityData(String equipmentType, String subType, double mtbf, double mttr) {
      this.equipmentType = equipmentType;
      this.subType = subType;
      this.mtbf = mtbf;
      this.mttr = mttr;
      this.failureRate = mtbf > 0 ? 1e6 / mtbf : 0;
      this.availability = mtbf > 0 ? mtbf / (mtbf + mttr) : 0;
    }

    // Getters
    public String getEquipmentType() {
      return equipmentType;
    }

    public String getSubType() {
      return subType;
    }

    public double getMtbf() {
      return mtbf;
    }

    public double getMttr() {
      return mttr;
    }

    public double getFailureRate() {
      return failureRate;
    }

    public double getAvailability() {
      return availability;
    }

    public String getSource() {
      return source;
    }

    public String getNotes() {
      return notes;
    }

    // Setters
    public void setSource(String source) {
      this.source = source;
    }

    public void setNotes(String notes) {
      this.notes = notes;
    }

    /**
     * Gets failures per year.
     *
     * @return failures per year
     */
    public double getFailuresPerYear() {
      return 8760.0 / mtbf;
    }

    @Override
    public String toString() {
      return String.format("%s (%s): MTBF=%.0f hrs, MTTR=%.1f hrs, Availability=%.2f%%",
          equipmentType, subType, mtbf, mttr, availability * 100);
    }
  }

  /**
   * Failure mode data holder.
   */
  public static class FailureModeData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String equipmentType;
    private String subType;
    private String failureMode;
    private double probability; // Percentage (0-100)
    private String severity;
    private double typicalMttr;
    private String detectability;
    private String description;

    /**
     * Default constructor.
     */
    public FailureModeData() {}

    /**
     * Creates failure mode data.
     *
     * @param equipmentType equipment type
     * @param failureMode failure mode name
     * @param probability probability percentage
     */
    public FailureModeData(String equipmentType, String failureMode, double probability) {
      this.equipmentType = equipmentType;
      this.failureMode = failureMode;
      this.probability = probability;
    }

    // Getters
    public String getEquipmentType() {
      return equipmentType;
    }

    public String getSubType() {
      return subType;
    }

    public String getFailureMode() {
      return failureMode;
    }

    public double getProbability() {
      return probability;
    }

    public String getSeverity() {
      return severity;
    }

    public double getTypicalMttr() {
      return typicalMttr;
    }

    public String getDetectability() {
      return detectability;
    }

    public String getDescription() {
      return description;
    }

    // Setters
    public void setSubType(String subType) {
      this.subType = subType;
    }

    public void setSeverity(String severity) {
      this.severity = severity;
    }

    public void setTypicalMttr(double mttr) {
      this.typicalMttr = mttr;
    }

    public void setDetectability(String detectability) {
      this.detectability = detectability;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    /**
     * Converts to EquipmentFailureMode.
     *
     * @param equipmentName equipment name for the failure mode
     * @return equipment failure mode
     */
    public EquipmentFailureMode toEquipmentFailureMode(String equipmentName) {
      EquipmentFailureMode.FailureType type = mapToFailureType(failureMode);

      EquipmentFailureMode.Builder builder =
          EquipmentFailureMode.builder().name(equipmentName + " - " + failureMode)
              .description(description != null ? description : failureMode).type(type)
              .capacityFactor(mapCapacityFactor(type));

      if (typicalMttr > 0) {
        builder.mttr(typicalMttr);
      }

      return builder.build();
    }

    private EquipmentFailureMode.FailureType mapToFailureType(String mode) {
      if (mode == null) {
        return EquipmentFailureMode.FailureType.TRIP;
      }
      String lower = mode.toLowerCase();
      if (lower.contains("degrad") || lower.contains("reduced") || lower.contains("wear")) {
        return EquipmentFailureMode.FailureType.DEGRADED;
      } else if (lower.contains("partial") || lower.contains("intermittent")) {
        return EquipmentFailureMode.FailureType.PARTIAL_FAILURE;
      } else if (lower.contains("maintenance") || lower.contains("scheduled")) {
        return EquipmentFailureMode.FailureType.MAINTENANCE;
      } else {
        return EquipmentFailureMode.FailureType.TRIP;
      }
    }

    private double mapCapacityFactor(EquipmentFailureMode.FailureType type) {
      switch (type) {
        case TRIP:
        case FULL_FAILURE:
        case MAINTENANCE:
        case BYPASSED:
          return 0.0;
        case DEGRADED:
          return 0.5;
        case PARTIAL_FAILURE:
          return 0.7;
        default:
          return 0.0;
      }
    }

    @Override
    public String toString() {
      return String.format("%s - %s: %.1f%% (Severity: %s)", equipmentType, failureMode,
          probability, severity);
    }
  }

  /**
   * Private constructor for singleton.
   */
  private ReliabilityDataSource() {
    reliabilityData = new HashMap<String, ReliabilityData>();
    failureModes = new HashMap<String, List<FailureModeData>>();
    dataLoaded = false;
  }

  /**
   * Gets the singleton instance.
   *
   * @return the data source instance
   */
  public static synchronized ReliabilityDataSource getInstance() {
    if (instance == null) {
      instance = new ReliabilityDataSource();
      instance.loadData();
    }
    return instance;
  }

  /**
   * Loads reliability data from resources.
   */
  /** Supplementary CSV resource paths loaded after the primary files. */
  private static final String[] SUPPLEMENTARY_CSVS = {
      "/reliabilitydata/iogp_equipment.csv",
      "/reliabilitydata/ieee493_equipment.csv",
      "/reliabilitydata/generic_literature.csv",
      "/reliabilitydata/oreda_equipment.csv"
  };

  private void loadData() {
    if (dataLoaded) {
      return;
    }

    try {
      loadReliabilityData();
      loadFailureModeData();
      loadSupplementaryData();
      dataLoaded = true;
    } catch (Exception e) {
      logger.warn("Could not load reliability data from files, using defaults: {}", e.getMessage());
      loadDefaultData();
      dataLoaded = true;
    }
  }

  private void loadReliabilityData() {
    // Try to load from CSV file
    InputStream is = getClass().getResourceAsStream("/reliabilitydata/equipment_reliability.csv");
    if (is == null) {
      logger.debug("Equipment reliability CSV not found, using defaults");
      loadDefaultReliabilityData();
      return;
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      String line;
      boolean header = true;
      while ((line = reader.readLine()) != null) {
        if (header) {
          header = false;
          continue;
        }
        String[] parts = line.split(",");
        if (parts.length >= 4) {
          String equipType = parts[0].trim();
          String subType = parts[1].trim();
          double mtbf = Double.parseDouble(parts[2].trim());
          double mttr = Double.parseDouble(parts[3].trim());

          ReliabilityData data = new ReliabilityData(equipType, subType, mtbf, mttr);
          if (parts.length > 4) {
            data.setSource(parts[4].trim());
          }
          if (parts.length > 5) {
            data.setNotes(parts[5].trim());
          }

          String key = makeKey(equipType, subType);
          reliabilityData.put(key, data);
        }
      }
    } catch (Exception e) {
      logger.warn("Error loading reliability CSV: {}", e.getMessage());
      loadDefaultReliabilityData();
    }
  }

  private void loadFailureModeData() {
    // Try to load from CSV file
    InputStream is = getClass().getResourceAsStream("/reliabilitydata/failure_modes.csv");
    if (is == null) {
      logger.debug("Failure modes CSV not found, using defaults");
      loadDefaultFailureModes();
      return;
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      String line;
      boolean header = true;
      while ((line = reader.readLine()) != null) {
        if (header) {
          header = false;
          continue;
        }
        String[] parts = line.split(",");
        if (parts.length >= 4) {
          String equipType = parts[0].trim();
          String subType = parts[1].trim();
          String failureMode = parts[2].trim();
          double probability = Double.parseDouble(parts[3].trim());

          FailureModeData data = new FailureModeData(equipType, failureMode, probability);
          data.setSubType(subType);
          if (parts.length > 4) {
            data.setSeverity(parts[4].trim());
          }
          if (parts.length > 5) {
            data.setTypicalMttr(Double.parseDouble(parts[5].trim()));
          }

          String key = makeKey(equipType, subType);
          if (!failureModes.containsKey(key)) {
            failureModes.put(key, new ArrayList<FailureModeData>());
          }
          failureModes.get(key).add(data);
        }
      }
    } catch (Exception e) {
      logger.warn("Error loading failure modes CSV: {}", e.getMessage());
      loadDefaultFailureModes();
    }
  }

  /**
   * Loads supplementary reliability CSV files (IOGP, IEEE 493, CCPS/Lees, OREDA).
   *
   * <p>
   * Format: {@code EquipmentType,EquipmentClass,FailureMode,FailureRate,MTBF_hours,
   * MTTR_hours,DataSource,Confidence}. Comment lines starting with {@code #} are skipped.
   * Data is added to the primary maps only when the key does not already exist, so the
   * primary {@code equipment_reliability.csv} takes precedence.
   */
  private void loadSupplementaryData() {
    for (String csvPath : SUPPLEMENTARY_CSVS) {
      loadSupplementaryCsv(csvPath);
    }
  }

  /**
   * Loads a single supplementary reliability CSV.
   *
   * @param resourcePath classpath resource path
   */
  private void loadSupplementaryCsv(String resourcePath) {
    InputStream is = getClass().getResourceAsStream(resourcePath);
    if (is == null) {
      logger.debug("Supplementary CSV not found: {}", resourcePath);
      return;
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      String line;
      int loaded = 0;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split(",");
        if (parts.length < 6) {
          continue;
        }
        String equipType = parts[0].trim();
        String subType = parts[1].trim();
        // parts[2] = FailureMode, parts[3] = FailureRate (per hour)
        double mtbf;
        double mttr;
        try {
          mtbf = Double.parseDouble(parts[4].trim());
          mttr = Double.parseDouble(parts[5].trim());
        } catch (NumberFormatException e) {
          continue;
        }

        String source = parts.length > 6 ? parts[6].trim() : "";

        String key = makeKey(equipType, subType);
        if (!reliabilityData.containsKey(key)) {
          ReliabilityData data = new ReliabilityData(equipType, subType, mtbf, mttr);
          data.setSource(source);
          reliabilityData.put(key, data);
          loaded++;
        }
      }
      if (loaded > 0) {
        logger.debug("Loaded {} entries from {}", loaded, resourcePath);
      }
    } catch (Exception e) {
      logger.debug("Could not load supplementary CSV {}: {}", resourcePath, e.getMessage());
    }
  }

  private void loadDefaultData() {
    loadDefaultReliabilityData();
    loadDefaultFailureModes();
  }

  private void loadDefaultReliabilityData() {
    // Default data from public/published sources for common equipment
    // Values are representative - actual values should come from CSV files

    // Compressors (IOGP/SINTEF offshore statistics)
    addReliabilityData("Compressor", "Centrifugal", 25000, 72, "IOGP/SINTEF");
    addReliabilityData("Compressor", "Reciprocating", 8000, 48, "IOGP/SINTEF");
    addReliabilityData("Compressor", "Screw", 15000, 36, "CCPS 1989");

    // Pumps (CCPS Process Equipment Reliability Data)
    addReliabilityData("Pump", "Centrifugal", 35000, 24, "CCPS 1989");
    addReliabilityData("Pump", "Reciprocating", 15000, 36, "CCPS 1989");
    addReliabilityData("Pump", "Progressive Cavity", 12000, 24, "Lees 2012");

    // Heat Exchangers (CCPS / Lees)
    addReliabilityData("HeatExchanger", "Shell-and-Tube", 200000, 120, "CCPS 1989");
    addReliabilityData("HeatExchanger", "Plate", 150000, 48, "Lees 2012");
    addReliabilityData("HeatExchanger", "Air Cooler", 50000, 24, "IOGP/SINTEF");

    // Separators (IOGP offshore statistics)
    addReliabilityData("Separator", "Two-Phase", 300000, 48, "IOGP/SINTEF");
    addReliabilityData("Separator", "Three-Phase", 250000, 72, "IOGP/SINTEF");
    addReliabilityData("Separator", "Scrubber", 350000, 24, "IOGP/SINTEF");

    // Valves (CCPS)
    addReliabilityData("Valve", "Control", 50000, 8, "CCPS 1989");
    addReliabilityData("Valve", "Safety (PSV)", 100000, 24, "CCPS 1989");
    addReliabilityData("Valve", "On-Off", 150000, 4, "CCPS 1989");

    // Electric Motors (IEEE 493 Gold Book)
    addReliabilityData("Motor", "Electric", 100000, 48, "IEEE 493-2007");

    // Gas Turbines (IOGP offshore statistics)
    addReliabilityData("Turbine", "Gas", 15000, 168, "IOGP/SINTEF");
  }

  private void loadDefaultFailureModes() {
    // Compressor failure modes
    addFailureMode("Compressor", "Centrifugal", "Vibration High", 25, "Medium", 24);
    addFailureMode("Compressor", "Centrifugal", "Seal Failure", 20, "High", 72);
    addFailureMode("Compressor", "Centrifugal", "Bearing Failure", 15, "High", 96);
    addFailureMode("Compressor", "Centrifugal", "Surge", 10, "Medium", 8);
    addFailureMode("Compressor", "Centrifugal", "Fouling", 15, "Low", 48);
    addFailureMode("Compressor", "Centrifugal", "Instrumentation", 15, "Low", 4);

    // Pump failure modes
    addFailureMode("Pump", "Centrifugal", "Seal Leak", 30, "Medium", 24);
    addFailureMode("Pump", "Centrifugal", "Bearing Failure", 20, "High", 36);
    addFailureMode("Pump", "Centrifugal", "Impeller Wear", 15, "Medium", 48);
    addFailureMode("Pump", "Centrifugal", "Cavitation", 15, "Medium", 12);
    addFailureMode("Pump", "Centrifugal", "Motor Failure", 20, "High", 72);

    // Heat Exchanger failure modes
    addFailureMode("HeatExchanger", "Shell-and-Tube", "Tube Leak", 35, "High", 168);
    addFailureMode("HeatExchanger", "Shell-and-Tube", "Fouling", 40, "Low", 72);
    addFailureMode("HeatExchanger", "Shell-and-Tube", "Corrosion", 15, "High", 240);
    addFailureMode("HeatExchanger", "Shell-and-Tube", "Gasket Failure", 10, "Medium", 24);

    // Separator failure modes
    addFailureMode("Separator", "Three-Phase", "Level Control", 35, "Medium", 8);
    addFailureMode("Separator", "Three-Phase", "Internals Damage", 20, "High", 120);
    addFailureMode("Separator", "Three-Phase", "Instrumentation", 30, "Low", 4);
    addFailureMode("Separator", "Three-Phase", "Corrosion", 15, "High", 240);
  }

  private void addReliabilityData(String type, String subType, double mtbf, double mttr,
      String source) {
    ReliabilityData data = new ReliabilityData(type, subType, mtbf, mttr);
    data.setSource(source);
    reliabilityData.put(makeKey(type, subType), data);
  }

  private void addFailureMode(String type, String subType, String mode, double probability,
      String severity, double mttr) {
    FailureModeData data = new FailureModeData(type, mode, probability);
    data.setSubType(subType);
    data.setSeverity(severity);
    data.setTypicalMttr(mttr);

    String key = makeKey(type, subType);
    if (!failureModes.containsKey(key)) {
      failureModes.put(key, new ArrayList<FailureModeData>());
    }
    failureModes.get(key).add(data);
  }

  private String makeKey(String type, String subType) {
    return type + "|" + (subType != null ? subType : "General");
  }

  // Public lookup methods

  /**
   * Gets reliability data for equipment type and subtype.
   *
   * @param equipmentType equipment type (e.g., "Compressor")
   * @param subType equipment subtype (e.g., "Centrifugal")
   * @return reliability data or null if not found
   */
  public ReliabilityData getReliabilityData(String equipmentType, String subType) {
    String key = makeKey(equipmentType, subType);
    ReliabilityData data = reliabilityData.get(key);

    // Try without subtype
    if (data == null) {
      data = reliabilityData.get(makeKey(equipmentType, "General"));
    }

    return data;
  }

  /**
   * Gets reliability data for equipment type.
   *
   * @param equipmentType equipment type
   * @return reliability data or null if not found
   */
  public ReliabilityData getReliabilityData(String equipmentType) {
    return getReliabilityData(equipmentType, null);
  }

  /**
   * Gets failure modes for equipment type and subtype.
   *
   * @param equipmentType equipment type
   * @param subType equipment subtype
   * @return list of failure modes (may be empty)
   */
  public List<FailureModeData> getFailureModes(String equipmentType, String subType) {
    String key = makeKey(equipmentType, subType);
    List<FailureModeData> modes = failureModes.get(key);

    if (modes == null || modes.isEmpty()) {
      modes = failureModes.get(makeKey(equipmentType, "General"));
    }

    return modes != null ? new ArrayList<FailureModeData>(modes) : new ArrayList<FailureModeData>();
  }

  /**
   * Gets failure modes for equipment type.
   *
   * @param equipmentType equipment type
   * @return list of failure modes
   */
  public List<FailureModeData> getFailureModes(String equipmentType) {
    return getFailureModes(equipmentType, null);
  }

  /**
   * Gets all equipment types in the database.
   *
   * @return list of equipment types
   */
  public List<String> getEquipmentTypes() {
    List<String> types = new ArrayList<String>();
    for (String key : reliabilityData.keySet()) {
      String type = key.split("\\|")[0];
      if (!types.contains(type)) {
        types.add(type);
      }
    }
    return types;
  }

  /**
   * Gets all subtypes for an equipment type.
   *
   * @param equipmentType the equipment type
   * @return list of subtypes
   */
  public List<String> getSubTypes(String equipmentType) {
    List<String> subTypes = new ArrayList<String>();
    for (String key : reliabilityData.keySet()) {
      if (key.startsWith(equipmentType + "|")) {
        String subType = key.split("\\|")[1];
        if (!"General".equals(subType) && !subTypes.contains(subType)) {
          subTypes.add(subType);
        }
      }
    }
    return subTypes;
  }

  /**
   * Creates an EquipmentFailureMode from reliability data.
   *
   * @param equipmentName name for the equipment
   * @param equipmentType type to lookup
   * @param subType subtype to lookup
   * @return equipment failure mode with MTTR from database
   */
  public EquipmentFailureMode createFailureMode(String equipmentName, String equipmentType,
      String subType) {
    ReliabilityData data = getReliabilityData(equipmentType, subType);
    if (data == null) {
      return EquipmentFailureMode.trip(equipmentName);
    }

    return EquipmentFailureMode.builder().name(equipmentName + " Trip")
        .description("Equipment trip for " + equipmentType)
        .type(EquipmentFailureMode.FailureType.TRIP).capacityFactor(0.0).mttr(data.getMttr())
        .build();
  }

  /**
   * Returns the distinct data sources referenced in the loaded reliability data.
   *
   * @return list of source identifiers (e.g. "IEEE 493-2007", "CCPS 1989")
   */
  public List<String> getDataSources() {
    List<String> sources = new ArrayList<String>();
    for (ReliabilityData data : reliabilityData.values()) {
      String src = data.getSource();
      if (src != null && !src.isEmpty() && !sources.contains(src)) {
        sources.add(src);
      }
    }
    return sources;
  }

  /**
   * Returns the total number of reliability data entries loaded.
   *
   * @return entry count
   */
  public int getEntryCount() {
    return reliabilityData.size();
  }
}
