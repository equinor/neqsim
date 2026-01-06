package neqsim.process.mechanicaldesign.torg;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.util.database.NeqSimProcessDesignDataBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Database-based data source for loading Technical Requirements Documents (TORG).
 *
 * <p>
 * This implementation loads TORG data from the NeqSim process design database. It expects the
 * following tables:
 * </p>
 *
 * <h2>TORG_Projects table</h2>
 * 
 * <pre>
 * CREATE TABLE TORG_Projects (
 *   PROJECT_ID VARCHAR(50) PRIMARY KEY,
 *   PROJECT_NAME VARCHAR(200),
 *   COMPANY VARCHAR(100),
 *   REVISION VARCHAR(20),
 *   ISSUE_DATE DATE,
 *   MIN_AMBIENT_TEMP DOUBLE,
 *   MAX_AMBIENT_TEMP DOUBLE,
 *   SEAWATER_TEMP DOUBLE,
 *   SEISMIC_ZONE VARCHAR(10),
 *   CORROSION_ALLOWANCE DOUBLE,
 *   PRESSURE_SAFETY_FACTOR DOUBLE,
 *   DEFAULT_PLATE_MATERIAL VARCHAR(50),
 *   DEFAULT_PIPE_MATERIAL VARCHAR(50)
 * );
 * </pre>
 *
 * <h2>TORG_Standards table</h2>
 * 
 * <pre>
 * CREATE TABLE TORG_Standards (
 *   PROJECT_ID VARCHAR(50),
 *   DESIGN_CATEGORY VARCHAR(100),
 *   STANDARD_CODE VARCHAR(50),
 *   VERSION VARCHAR(20),
 *   PRIORITY INT,
 *   FOREIGN KEY (PROJECT_ID) REFERENCES TORG_Projects(PROJECT_ID)
 * );
 * </pre>
 *
 * <p>
 * Alternatively, this data source can also read from the existing TechnicalRequirements_Process
 * table for backward compatibility.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class DatabaseTorgDataSource implements TorgDataSource {
  private static final Logger logger = LogManager.getLogger(DatabaseTorgDataSource.class);

  private static final String QUERY_PROJECT = "SELECT * FROM TORG_Projects WHERE PROJECT_ID = '%s'";

  private static final String QUERY_PROJECT_BY_COMPANY =
      "SELECT * FROM TORG_Projects WHERE COMPANY = '%s' AND PROJECT_NAME = '%s'";

  private static final String QUERY_STANDARDS =
      "SELECT * FROM TORG_Standards WHERE PROJECT_ID = '%s' ORDER BY PRIORITY";

  private static final String QUERY_ALL_PROJECTS = "SELECT PROJECT_ID FROM TORG_Projects";

  private static final String QUERY_ALL_COMPANIES = "SELECT DISTINCT COMPANY FROM TORG_Projects";

  // Fallback queries for legacy TechnicalRequirements_Process table
  private static final String LEGACY_QUERY =
      "SELECT * FROM TechnicalRequirements_Process WHERE Company = '%s'";

  private boolean useLegacyTable = false;

  /**
   * Create a DatabaseTorgDataSource using the new TORG tables.
   */
  public DatabaseTorgDataSource() {
    this(false);
  }

  /**
   * Create a DatabaseTorgDataSource.
   *
   * @param useLegacyTable if true, use the legacy TechnicalRequirements_Process table
   */
  public DatabaseTorgDataSource(boolean useLegacyTable) {
    this.useLegacyTable = useLegacyTable;
  }

  @Override
  public Optional<TechnicalRequirementsDocument> loadByProjectId(String projectId) {
    if (projectId == null || projectId.isEmpty()) {
      return Optional.empty();
    }

    if (useLegacyTable) {
      return loadFromLegacyTable(projectId);
    }

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      // Load project metadata
      String projectQuery = String.format(QUERY_PROJECT, projectId);
      ResultSet projectRS = database.getResultSet(projectQuery);

      if (!projectRS.next()) {
        return Optional.empty();
      }

      TechnicalRequirementsDocument.Builder builder = buildFromProjectResultSet(projectRS);

      // Load associated standards
      String standardsQuery = String.format(QUERY_STANDARDS, projectId);
      try (ResultSet standardsRS = database.getResultSet(standardsQuery)) {
        while (standardsRS.next()) {
          String category = standardsRS.getString("DESIGN_CATEGORY");
          String standardCode = standardsRS.getString("STANDARD_CODE");

          if (category != null && standardCode != null) {
            StandardType type = StandardType.fromCode(standardCode);
            if (type != null) {
              builder.addStandard(category, type);
            }
          }
        }
      }

      return Optional.of(builder.build());
    } catch (Exception e) {
      logger.error("Failed to load TORG from database: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<TechnicalRequirementsDocument> loadByCompanyAndProject(String companyIdentifier,
      String projectName) {
    if (companyIdentifier == null || projectName == null) {
      return Optional.empty();
    }

    if (useLegacyTable) {
      return loadFromLegacyTable(companyIdentifier);
    }

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      String query = String.format(QUERY_PROJECT_BY_COMPANY, companyIdentifier, projectName);
      ResultSet projectRS = database.getResultSet(query);

      if (!projectRS.next()) {
        return Optional.empty();
      }

      String projectId = projectRS.getString("PROJECT_ID");
      TechnicalRequirementsDocument.Builder builder = buildFromProjectResultSet(projectRS);

      // Load standards
      String standardsQuery = String.format(QUERY_STANDARDS, projectId);
      try (ResultSet standardsRS = database.getResultSet(standardsQuery)) {
        while (standardsRS.next()) {
          String category = standardsRS.getString("DESIGN_CATEGORY");
          String standardCode = standardsRS.getString("STANDARD_CODE");

          if (category != null && standardCode != null) {
            StandardType type = StandardType.fromCode(standardCode);
            if (type != null) {
              builder.addStandard(category, type);
            }
          }
        }
      }

      return Optional.of(builder.build());
    } catch (Exception e) {
      logger.error("Failed to load TORG from database: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  @Override
  public List<String> getAvailableProjectIds() {
    List<String> projectIds = new ArrayList<>();

    if (useLegacyTable) {
      // For legacy table, companies are the identifiers
      return getAvailableCompanies();
    }

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(QUERY_ALL_PROJECTS)) {
      while (rs.next()) {
        projectIds.add(rs.getString("PROJECT_ID"));
      }
    } catch (Exception e) {
      logger.error("Failed to get project IDs: " + e.getMessage(), e);
    }

    return projectIds;
  }

  @Override
  public List<String> getAvailableCompanies() {
    List<String> companies = new ArrayList<>();

    String query = useLegacyTable ? "SELECT DISTINCT Company FROM TechnicalRequirements_Process"
        : QUERY_ALL_COMPANIES;

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(query)) {
      while (rs.next()) {
        String company = rs.getString(1);
        if (company != null && !company.isEmpty()) {
          companies.add(company);
        }
      }
    } catch (Exception e) {
      logger.error("Failed to get companies: " + e.getMessage(), e);
    }

    return companies;
  }

  /**
   * Load from the legacy TechnicalRequirements_Process table.
   */
  private Optional<TechnicalRequirementsDocument> loadFromLegacyTable(String companyIdentifier) {
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      String query = String.format(LEGACY_QUERY, companyIdentifier);
      ResultSet rs = database.getResultSet(query);

      TechnicalRequirementsDocument.Builder builder =
          TechnicalRequirementsDocument.builder().projectId(companyIdentifier)
              .projectName("Legacy TR").companyIdentifier(companyIdentifier).revision("Legacy");

      // Parse legacy data into design limits
      Map<String, DesignLimits> equipmentLimits = new HashMap<>();

      while (rs.next()) {
        String equipmentType = rs.getString("EQUIPMENTTYPE");
        String specification = rs.getString("SPECIFICATION");
        String maxValueStr = rs.getString("MAXVALUE");
        String minValueStr = rs.getString("MINVALUE");

        double maxValue = parseDouble(maxValueStr);
        double minValue = parseDouble(minValueStr);

        DesignLimits limits = equipmentLimits.get(equipmentType);
        if (limits == null) {
          limits = new DesignLimits();
          equipmentLimits.put(equipmentType, limits);
        }

        if ("MaxPressure".equals(specification)) {
          limits.maxPressure = Double.isNaN(maxValue) ? minValue : maxValue;
        } else if ("MinTemperature".equals(specification)) {
          limits.minTemperature = Double.isNaN(minValue) ? maxValue : minValue;
        } else if ("CorrosionAllowance".equals(specification)) {
          limits.corrosionAllowance = Double.isNaN(maxValue) ? minValue : maxValue;
        }
      }

      // Determine applicable standards based on equipment types present
      for (String equipmentType : equipmentLimits.keySet()) {
        List<StandardType> applicable = StandardType.getApplicableStandards(equipmentType);
        if (!applicable.isEmpty()) {
          // Add first applicable standard for each category
          for (StandardType type : applicable) {
            builder.addStandard(type.getDesignStandardCategory(), type);
          }
        }
      }

      // Find minimum temperature across all equipment for material spec
      double minTemp = -40.0;
      for (DesignLimits limits : equipmentLimits.values()) {
        if (!Double.isNaN(limits.minTemperature) && limits.minTemperature < minTemp) {
          minTemp = limits.minTemperature;
        }
      }

      builder.environmentalConditions(minTemp, 45.0);

      return Optional.of(builder.build());
    } catch (Exception e) {
      logger.error("Failed to load from legacy table: " + e.getMessage(), e);
      return Optional.empty();
    }
  }

  private TechnicalRequirementsDocument.Builder buildFromProjectResultSet(ResultSet rs)
      throws java.sql.SQLException {
    TechnicalRequirementsDocument.Builder builder = TechnicalRequirementsDocument.builder()
        .projectId(rs.getString("PROJECT_ID")).projectName(rs.getString("PROJECT_NAME"))
        .companyIdentifier(rs.getString("COMPANY")).revision(rs.getString("REVISION"));

    String issueDate = rs.getString("ISSUE_DATE");
    if (issueDate != null) {
      builder.issueDate(issueDate);
    }

    // Environmental conditions
    double minAmbient = rs.getDouble("MIN_AMBIENT_TEMP");
    double maxAmbient = rs.getDouble("MAX_AMBIENT_TEMP");
    double seawaterTemp = rs.getDouble("SEAWATER_TEMP");
    String seismicZone = rs.getString("SEISMIC_ZONE");

    if (!rs.wasNull()) {
      builder.environmentalConditions(new TechnicalRequirementsDocument.EnvironmentalConditions(
          minAmbient, maxAmbient, seawaterTemp, seismicZone != null ? seismicZone : "0", 0, 0, ""));
    }

    // Safety factors
    double corrosionAllowance = rs.getDouble("CORROSION_ALLOWANCE");
    double pressureSF = rs.getDouble("PRESSURE_SAFETY_FACTOR");
    if (!rs.wasNull()) {
      builder.safetyFactors(new TechnicalRequirementsDocument.SafetyFactors(
          Double.isNaN(pressureSF) ? 1.1 : pressureSF, 10.0,
          Double.isNaN(corrosionAllowance) ? 3.0 : corrosionAllowance, 0.125, 1.0));
    }

    // Material specs
    String plateMaterial = rs.getString("DEFAULT_PLATE_MATERIAL");
    String pipeMaterial = rs.getString("DEFAULT_PIPE_MATERIAL");
    if (plateMaterial != null || pipeMaterial != null) {
      builder.materialSpecifications(new TechnicalRequirementsDocument.MaterialSpecifications(
          plateMaterial != null ? plateMaterial : "A516-70",
          pipeMaterial != null ? pipeMaterial : "A106-B", minAmbient, 300.0, minAmbient < -29,
          "ASTM"));
    }

    return builder;
  }

  private double parseDouble(String value) {
    if (value == null || value.isEmpty()) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  /** Helper class for legacy data loading. */
  private static class DesignLimits {
    double maxPressure = Double.NaN;
    double minTemperature = Double.NaN;
    double corrosionAllowance = Double.NaN;
  }
}
