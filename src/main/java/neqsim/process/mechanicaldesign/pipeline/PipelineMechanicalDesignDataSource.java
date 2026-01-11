package neqsim.process.mechanicaldesign.pipeline;

import java.sql.ResultSet;
import java.util.Locale;
import java.util.Optional;
import neqsim.util.database.NeqSimProcessDesignDataBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Data source for loading pipeline mechanical design parameters from the NeqSim database.
 *
 * <p>
 * This class reads from the following database tables:
 * </p>
 * <ul>
 * <li>{@code MaterialPipeProperties} - Material grades, SMYS, SMTS per API 5L</li>
 * <li>{@code TechnicalRequirements_Process} - Design factors per company/project</li>
 * <li>{@code TechnicalRequirements_Piping} - Piping-specific requirements</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class PipelineMechanicalDesignDataSource {
  private static final Logger logger =
      LogManager.getLogger(PipelineMechanicalDesignDataSource.class);

  /** Query template for material properties. */
  private static final String MATERIAL_QUERY =
      "SELECT * FROM MaterialPipeProperties WHERE grade='%s'";

  /** Query template for company-specific design factors. */
  private static final String DESIGN_FACTORS_QUERY =
      "SELECT SPECIFICATION, MAXVALUE, MINVALUE FROM TechnicalRequirements_Process "
          + "WHERE EQUIPMENTTYPE='Pipeline' AND Company='%s'";

  /** Query template for piping requirements. */
  private static final String PIPING_QUERY =
      "SELECT SPECIFICATION, MAXVALUE, MINVALUE FROM TechnicalRequirements_Piping "
          + "WHERE Company='%s'";

  /**
   * Pipeline material data holder.
   */
  public static class PipeMaterialData {
    /** Material grade (e.g., "X65"). */
    public final String grade;
    /** Specification number. */
    public final String specificationNumber;
    /** Minimum Yield Strength in MPa. */
    public final double smys;
    /** Minimum Tensile Strength in MPa. */
    public final double smts;
    /** Design factor (F). */
    public final double designFactor;
    /** Joint factor (E). */
    public final double jointFactor;
    /** Temperature derating factor (T). */
    public final double temperatureDerating;

    /**
     * Constructor.
     *
     * @param grade material grade
     * @param specificationNumber specification number
     * @param smys minimum yield strength in MPa
     * @param smts minimum tensile strength in MPa
     * @param designFactor design factor
     * @param jointFactor joint factor
     * @param temperatureDerating temperature derating factor
     */
    public PipeMaterialData(String grade, String specificationNumber, double smys, double smts,
        double designFactor, double jointFactor, double temperatureDerating) {
      this.grade = grade;
      this.specificationNumber = specificationNumber;
      this.smys = smys;
      this.smts = smts;
      this.designFactor = designFactor;
      this.jointFactor = jointFactor;
      this.temperatureDerating = temperatureDerating;
    }
  }

  /**
   * Pipeline design factors holder.
   */
  public static class PipeDesignFactors {
    /** Design factor (F). */
    public double designFactor = 0.72;
    /** Joint factor (E). */
    public double jointFactor = 1.0;
    /** Corrosion allowance in mm. */
    public double corrosionAllowance = 3.0;
    /** Location class. */
    public int locationClass = 1;
    /** Safety factor. */
    public double safetyFactor = 1.0;
    /** Design code. */
    public String designCode = "ASME_B31_8";
    /** Fabrication tolerance. */
    public double fabricationTolerance = 0.875;
  }

  /**
   * Load material properties from database for a given API 5L grade.
   *
   * @param grade material grade (e.g., "X52", "X65", "X70")
   * @return Optional containing material data if found
   */
  public Optional<PipeMaterialData> loadMaterialProperties(String grade) {
    if (grade == null || grade.isEmpty()) {
      return Optional.empty();
    }

    String query = String.format(Locale.ROOT, MATERIAL_QUERY, grade);

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {
      if (dataSet.next()) {
        String specNo = dataSet.getString("specificationNumber");

        // SMYS - typically stored in psi, convert to MPa
        double smys = parseDouble(dataSet.getString("minimumYeildStrength"));
        // Check if value seems to be in psi (> 10000 means psi)
        if (smys > 10000) {
          smys = smys * 0.00689476; // psi to MPa
        }

        // SMTS - estimate if not available
        double smts = smys * 1.15; // Default ratio
        try {
          String smtsStr = dataSet.getString("minimumTensileStrength");
          if (smtsStr != null && !smtsStr.isEmpty()) {
            smts = parseDouble(smtsStr);
            if (smts > 10000) {
              smts = smts * 0.00689476;
            }
          }
        } catch (Exception e) {
          // Column may not exist
        }

        // Design factors - use defaults if not in database
        double designFactor = 0.72;
        double jointFactor = 1.0;
        double tempDerating = 1.0;

        return Optional.of(new PipeMaterialData(grade, specNo, smys, smts, designFactor,
            jointFactor, tempDerating));
      }
    } catch (Exception ex) {
      logger.warn("Could not load material properties for grade: " + grade, ex);
    }

    return Optional.empty();
  }

  /**
   * Load design factors from database for a given company.
   *
   * @param companyIdentifier company identifier (e.g., "Equinor", "Statoil")
   * @return design factors object with loaded or default values
   */
  public PipeDesignFactors loadDesignFactors(String companyIdentifier) {
    PipeDesignFactors factors = new PipeDesignFactors();

    if (companyIdentifier == null || companyIdentifier.isEmpty()) {
      return factors;
    }

    String query = String.format(Locale.ROOT, DESIGN_FACTORS_QUERY, companyIdentifier);

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {
      while (dataSet.next()) {
        String specification = dataSet.getString("SPECIFICATION");
        double maxValue = parseDouble(dataSet.getString("MAXVALUE"));

        if (specification == null) {
          continue;
        }

        switch (specification.toLowerCase(Locale.ROOT)) {
          case "designfactor":
            if (!Double.isNaN(maxValue)) {
              factors.designFactor = maxValue;
            }
            break;
          case "jointfactor":
            if (!Double.isNaN(maxValue)) {
              factors.jointFactor = maxValue;
            }
            break;
          case "corrosionallowance":
            if (!Double.isNaN(maxValue)) {
              factors.corrosionAllowance = maxValue;
            }
            break;
          case "locationclass":
            if (!Double.isNaN(maxValue)) {
              factors.locationClass = (int) maxValue;
            }
            break;
          case "safetyfactor":
            if (!Double.isNaN(maxValue)) {
              factors.safetyFactor = maxValue;
            }
            break;
          case "designcode":
            String codeValue = dataSet.getString("MAXVALUE");
            if (codeValue != null && !codeValue.isEmpty()) {
              factors.designCode = codeValue;
            }
            break;
          case "fabricationtolerance":
            if (!Double.isNaN(maxValue)) {
              factors.fabricationTolerance = maxValue;
            }
            break;
          default:
            break;
        }
      }
    } catch (Exception ex) {
      logger.warn("Could not load design factors for company: " + companyIdentifier, ex);
    }

    // Also try piping-specific requirements
    loadPipingRequirements(companyIdentifier, factors);

    return factors;
  }

  /**
   * Load piping-specific requirements from database.
   *
   * @param companyIdentifier company identifier
   * @param factors design factors to update
   */
  private void loadPipingRequirements(String companyIdentifier, PipeDesignFactors factors) {
    String query = String.format(Locale.ROOT, PIPING_QUERY, companyIdentifier);

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {
      while (dataSet.next()) {
        String specification = dataSet.getString("SPECIFICATION");
        double maxValue = parseDouble(dataSet.getString("MAXVALUE"));

        if (specification == null) {
          continue;
        }

        // Add any piping-specific overrides
        switch (specification.toLowerCase(Locale.ROOT)) {
          case "corrosionallowance":
            if (!Double.isNaN(maxValue)) {
              factors.corrosionAllowance = maxValue;
            }
            break;
          default:
            break;
        }
      }
    } catch (Exception ex) {
      // Piping table may not exist, ignore
      logger.debug("Could not load piping requirements: " + ex.getMessage());
    }
  }

  /**
   * Load parameters from the design standards tables based on design code.
   *
   * @param designCode the design code (e.g., "ASME-B31.8", "DNV-ST-F101")
   * @param equipmentType the equipment type (e.g., "Pipeline", "MultiphasePipe")
   * @param factors design factors to update with loaded values
   */
  public void loadFromStandardsTable(String designCode, String equipmentType,
      PipeDesignFactors factors) {
    if (designCode == null || designCode.isEmpty()) {
      return;
    }

    // Determine which standards table to query based on the design code
    String tableName;
    if (designCode.toUpperCase(Locale.ROOT).contains("API")) {
      tableName = "api_standards";
    } else if (designCode.toUpperCase(Locale.ROOT).contains("ASME")) {
      tableName = "asme_standards";
    } else if (designCode.toUpperCase(Locale.ROOT).contains("DNV")
        || designCode.toUpperCase(Locale.ROOT).contains("ISO")
        || designCode.toUpperCase(Locale.ROOT).contains("EN")) {
      tableName = "dnv_iso_en_standards";
    } else if (designCode.toUpperCase(Locale.ROOT).contains("NORSOK")) {
      tableName = "norsok_standards";
    } else if (designCode.toUpperCase(Locale.ROOT).contains("ASTM")) {
      tableName = "astm_standards";
    } else {
      return; // Unknown standard
    }

    String query = String.format(Locale.ROOT,
        "SELECT SPECIFICATION, MINVALUE, MAXVALUE FROM %s WHERE STANDARD_CODE='%s' AND EQUIPMENTTYPE='%s'",
        tableName, designCode, equipmentType);

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {
      while (dataSet.next()) {
        String specification = dataSet.getString("SPECIFICATION");
        double maxValue = parseDouble(dataSet.getString("MAXVALUE"));

        if (specification == null) {
          continue;
        }

        // Map specification names to design factor fields
        String specLower = specification.toLowerCase(Locale.ROOT);
        if (specLower.contains("designfactor") || specLower.contains("usagefactor")) {
          if (!Double.isNaN(maxValue)) {
            factors.designFactor = maxValue;
          }
        } else if (specLower.contains("jointefficiency") || specLower.contains("jointfactor")) {
          if (!Double.isNaN(maxValue)) {
            factors.jointFactor = maxValue;
          }
        } else if (specLower.contains("corrosionallowance")) {
          if (!Double.isNaN(maxValue)) {
            factors.corrosionAllowance = maxValue;
          }
        } else if (specLower.contains("fabricationtolerance")) {
          if (!Double.isNaN(maxValue)) {
            factors.fabricationTolerance = 1.0 - maxValue; // Convert 0.125 to 0.875
          }
        } else if (specLower.contains("temperaturederat")) {
          if (!Double.isNaN(maxValue)) {
            // Temperature derating is stored in the standard
          }
        }
      }
    } catch (Exception ex) {
      logger.debug("Could not load from standards table " + tableName + ": " + ex.getMessage());
    }
  }

  /**
   * Load all pipeline mechanical design data.
   *
   * @param materialGrade API 5L material grade
   * @param companyIdentifier company identifier
   * @param calculator calculator to update with loaded values
   */
  public void loadIntoCalculator(String materialGrade, String companyIdentifier,
      PipeMechanicalDesignCalculator calculator) {
    loadIntoCalculator(materialGrade, companyIdentifier, null, "Pipeline", calculator);
  }

  /**
   * Load all pipeline mechanical design data including standards-based parameters.
   *
   * @param materialGrade API 5L material grade
   * @param companyIdentifier company identifier
   * @param designCode design code (e.g., "ASME-B31.8", "DNV-ST-F101")
   * @param equipmentType equipment type (e.g., "Pipeline", "MultiphasePipe")
   * @param calculator calculator to update with loaded values
   */
  public void loadIntoCalculator(String materialGrade, String companyIdentifier, String designCode,
      String equipmentType, PipeMechanicalDesignCalculator calculator) {
    if (calculator == null) {
      return;
    }

    // Load material properties from MaterialPipeProperties table
    Optional<PipeMaterialData> materialOpt = loadMaterialProperties(materialGrade);
    if (materialOpt.isPresent()) {
      PipeMaterialData material = materialOpt.get();
      calculator.setSmys(material.smys);
      calculator.setSmts(material.smts);
    } else {
      // Fall back to built-in API 5L data
      calculator.setMaterialGrade(materialGrade);
    }

    // Load design factors from TechnicalRequirements_Process table
    PipeDesignFactors factors = loadDesignFactors(companyIdentifier);

    // Also load from standards tables if design code is specified
    if (designCode != null && !designCode.isEmpty()) {
      loadFromStandardsTable(designCode, equipmentType, factors);
    }

    // Apply loaded factors to calculator
    calculator.setDesignFactor(factors.designFactor);
    calculator.setJointFactor(factors.jointFactor);
    calculator.setCorrosionAllowance(factors.corrosionAllowance / 1000.0); // mm to m
    calculator.setFabricationTolerance(factors.fabricationTolerance);

    // Map design code string to constant
    String code = factors.designCode.toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    if (code.contains("B31_8") || code.contains("B31.8")) {
      calculator.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_8);
      calculator.setLocationClass(factors.locationClass);
    } else if (code.contains("B31_4") || code.contains("B31.4")) {
      calculator.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_4);
    } else if (code.contains("B31_3") || code.contains("B31.3")) {
      calculator.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_3);
    } else if (code.contains("DNV") || code.contains("F101")) {
      calculator.setDesignCode(PipeMechanicalDesignCalculator.DNV_OS_F101);
    }
  }

  private double parseDouble(String value) {
    if (value == null || value.isEmpty()) {
      return Double.NaN;
    }
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }
}
