package neqsim.process.mechanicaldesign.pipeline;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import neqsim.util.database.NeqSimProcessDesignDataBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Data source for loading riser mechanical design parameters from the NeqSim database.
 *
 * <p>
 * This class reads from the following database tables:
 * </p>
 * <ul>
 * <li>{@code MaterialPipeProperties} - Material grades, SMYS, SMTS per API 5L</li>
 * <li>{@code TechnicalRequirements_Process} - Riser design factors per company</li>
 * <li>{@code dnv_iso_en_standards} - DNV-OS-F201, DNV-RP-F204, API RP 2RD parameters</li>
 * </ul>
 *
 * <h2>Riser-Specific Standards</h2>
 * <ul>
 * <li>DNV-OS-F201 - Dynamic Risers</li>
 * <li>DNV-RP-F204 - Riser Fatigue</li>
 * <li>DNV-RP-C203 - Fatigue Design of Offshore Structures</li>
 * <li>DNV-RP-C205 - Environmental Conditions and Environmental Loads</li>
 * <li>API RP 2RD - Design of Risers for Floating Production Systems</li>
 * <li>API RP 17B - Recommended Practice for Flexible Pipe</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class RiserMechanicalDesignDataSource {

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(RiserMechanicalDesignDataSource.class);

  /** Query template for company-specific riser design factors. */
  private static final String RISER_DESIGN_FACTORS_QUERY =
      "SELECT SPECIFICATION, MINVALUE, MAXVALUE, UNIT, DOCUMENTID, DESCRIPTION "
          + "FROM TechnicalRequirements_Process WHERE EQUIPMENTTYPE='Riser' AND Company='%s'";

  /** Query template for riser standards. */
  private static final String RISER_STANDARDS_QUERY =
      "SELECT SPECIFICATION, MINVALUE, MAXVALUE, UNIT, DESCRIPTION "
          + "FROM dnv_iso_en_standards WHERE EQUIPMENTTYPE='Riser' AND STANDARD_CODE='%s'";

  /**
   * Riser design parameters holder.
   */
  public static class RiserDesignParameters {
    /** Design factor. */
    public double designFactor = 0.67;

    /** Usage factor per DNV. */
    public double usageFactor = 0.83;

    /** Corrosion allowance in mm. */
    public double corrosionAllowance = 3.0;

    /** Minimum wall thickness in mm. */
    public double minWallThickness = 6.35;

    /** Fatigue design factor. */
    public double fatigueDesignFactor = 3.0;

    /** Dynamic amplification factor. */
    public double dynamicAmplificationFactor = 1.2;

    /** Strouhal number for VIV. */
    public double strouhalNumber = 0.2;

    /** Drag coefficient. */
    public double dragCoefficient = 1.0;

    /** Added mass coefficient. */
    public double addedMassCoefficient = 1.0;

    /** Lift coefficient for VIV. */
    public double liftCoefficient = 0.9;

    /** S-N curve parameter log(a) for seawater with CP. */
    public double snParameterSeawater = 12.164;

    /** S-N curve slope parameter m. */
    public double snSlopeParameter = 3.0;

    /** Top tension safety factor. */
    public double topTensionSafetyFactor = 1.3;

    /** Maximum stress utilization. */
    public double maxUtilization = 0.8;

    /** Stress concentration factor for girth welds. */
    public double stressConcentrationFactor = 1.3;

    /** Seabed friction coefficient. */
    public double seabedFrictionCoefficient = 0.3;

    /** Design standard code. */
    public String designCode = "DNV-OS-F201";

    /** Company name. */
    public String company = "default";
  }

  /**
   * Load riser design parameters from database for a given company.
   *
   * @param company company name (e.g., "Equinor", "default")
   * @return RiserDesignParameters with values from database, or defaults if not found
   */
  public RiserDesignParameters loadDesignParameters(String company) {
    RiserDesignParameters params = new RiserDesignParameters();
    params.company = company;

    String query = String.format(Locale.ROOT, RISER_DESIGN_FACTORS_QUERY, company);

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {

      while (dataSet.next()) {
        String spec = dataSet.getString("SPECIFICATION");
        double minValue = dataSet.getDouble("MINVALUE");
        double maxValue = dataSet.getDouble("MAXVALUE");
        String documentId = dataSet.getString("DOCUMENTID");

        // Use max value for safety factors
        applyParameter(params, spec, minValue, maxValue, documentId);
      }

    } catch (Exception e) {
      logger.warn("Failed to load riser design parameters for company '{}', using defaults: {}",
          company, e.getMessage());
    }

    return params;
  }

  /**
   * Load riser parameters from a specific design standard.
   *
   * @param standardCode the design standard code (e.g., "DNV-OS-F201", "API-RP-2RD")
   * @return map of parameter names to values
   */
  public Map<String, Double> loadFromStandard(String standardCode) {
    Map<String, Double> params = new HashMap<String, Double>();

    String query = String.format(Locale.ROOT, RISER_STANDARDS_QUERY, standardCode);

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet dataSet = database.getResultSet(query)) {

      while (dataSet.next()) {
        String spec = dataSet.getString("SPECIFICATION");
        double maxValue = dataSet.getDouble("MAXVALUE");
        params.put(spec, maxValue);
      }

    } catch (Exception e) {
      logger.warn("Failed to load standard '{}' parameters: {}", standardCode, e.getMessage());
    }

    return params;
  }

  /**
   * Load design parameters into the riser calculator.
   *
   * @param calculator the riser calculator to populate
   * @param company company name for TR-specific values
   * @param designCode the design standard code
   */
  public void loadIntoCalculator(RiserMechanicalDesignCalculator calculator, String company,
      String designCode) {
    // Load company-specific parameters
    RiserDesignParameters params = loadDesignParameters(company);

    // Apply to calculator
    calculator.setDesignFactor(params.designFactor);
    calculator.setCorrosionAllowance(params.corrosionAllowance);
    calculator.setStrouhalNumber(params.strouhalNumber);
    calculator.setDragCoefficient(params.dragCoefficient);
    calculator.setAddedMassCoefficient(params.addedMassCoefficient);
    calculator.setSnParameter(params.snParameterSeawater);
    calculator.setSnSlope(params.snSlopeParameter);

    // Load standard-specific values
    Map<String, Double> standardParams = loadFromStandard(designCode);
    if (!standardParams.isEmpty()) {
      if (standardParams.containsKey("UsageFactor")) {
        calculator.setDesignFactor(standardParams.get("UsageFactor"));
      }
      if (standardParams.containsKey("FatigueDesignFactor")) {
        calculator.setFatigueDesignFactor(standardParams.get("FatigueDesignFactor"));
      }
      if (standardParams.containsKey("DynamicAmplificationFactor")) {
        calculator.setDynamicAmplificationFactor(standardParams.get("DynamicAmplificationFactor"));
      }
      if (standardParams.containsKey("StrouhalNumber")) {
        calculator.setStrouhalNumber(standardParams.get("StrouhalNumber"));
      }
      if (standardParams.containsKey("SCFGirthWeld")) {
        calculator.setStressConcentrationFactor(standardParams.get("SCFGirthWeld"));
      }
    }

    logger.debug("Loaded riser design parameters for company '{}' with standard '{}'", company,
        designCode);
  }

  /**
   * Get available design standards for risers.
   *
   * @return map of standard codes to descriptions
   */
  public Map<String, String> getAvailableStandards() {
    Map<String, String> standards = new HashMap<String, String>();
    standards.put("DNV-OS-F201", "DNV Offshore Standard for Dynamic Risers");
    standards.put("DNV-RP-F204", "DNV Recommended Practice for Riser Fatigue");
    standards.put("DNV-RP-C203", "DNV Recommended Practice for Fatigue Design");
    standards.put("DNV-RP-C205", "DNV Recommended Practice for Environmental Conditions");
    standards.put("API-RP-2RD", "API Recommended Practice for Riser Design");
    standards.put("API-RP-17B", "API Recommended Practice for Flexible Pipe");
    return standards;
  }

  /**
   * Apply a parameter value to the design parameters object.
   *
   * @param params the parameters object
   * @param specification the parameter name
   * @param minValue minimum value
   * @param maxValue maximum value
   * @param documentId the source document ID
   */
  private void applyParameter(RiserDesignParameters params, String specification, double minValue,
      double maxValue, String documentId) {
    String specLower = specification.toLowerCase(Locale.ROOT);

    switch (specLower) {
      case "designfactor":
        params.designFactor = maxValue;
        break;
      case "usagefactor":
        params.usageFactor = maxValue;
        break;
      case "corrosionallowance":
        params.corrosionAllowance = maxValue;
        break;
      case "minwallthickness":
        params.minWallThickness = maxValue;
        break;
      case "fatiguedesignfactor":
        params.fatigueDesignFactor = maxValue;
        break;
      case "dynamicamplificationfactor":
        params.dynamicAmplificationFactor = maxValue;
        break;
      case "strouhalnumber":
        params.strouhalNumber = maxValue;
        break;
      case "dragcoefficient":
        params.dragCoefficient = maxValue;
        break;
      case "addedmasscoefficient":
        params.addedMassCoefficient = maxValue;
        break;
      case "liftcoefficient":
        params.liftCoefficient = maxValue;
        break;
      case "snparameterseawater":
        params.snParameterSeawater = maxValue;
        break;
      case "snslopeparameter":
        params.snSlopeParameter = maxValue;
        break;
      case "toptensionsafetyfactor":
        params.topTensionSafetyFactor = maxValue;
        break;
      case "maxutilization":
        params.maxUtilization = maxValue;
        break;
      case "stressconcentrationfactor":
        params.stressConcentrationFactor = maxValue;
        break;
      case "seabedfrictioncoefficient":
        params.seabedFrictionCoefficient = maxValue;
        break;
      default:
        logger.trace("Unknown riser parameter: {}", specification);
    }

    if (documentId != null && !documentId.isEmpty()) {
      params.designCode = documentId;
    }
  }

  /**
   * Load fatigue-specific parameters from DNV-RP-F204.
   *
   * @param calculator the calculator to populate
   */
  public void loadFatigueParameters(RiserMechanicalDesignCalculator calculator) {
    Map<String, Double> fatigueParams = loadFromStandard("DNV-RP-F204");

    if (fatigueParams.containsKey("FatigueDesignFactor")) {
      calculator.setFatigueDesignFactor(fatigueParams.get("FatigueDesignFactor"));
    }
    if (fatigueParams.containsKey("SNParameterSeawater")) {
      calculator.setSnParameter(fatigueParams.get("SNParameterSeawater"));
    }
    if (fatigueParams.containsKey("SNSlopeParameter")) {
      calculator.setSnSlope(fatigueParams.get("SNSlopeParameter"));
    }
    if (fatigueParams.containsKey("SCFGirthWeld")) {
      calculator.setStressConcentrationFactor(fatigueParams.get("SCFGirthWeld"));
    }
  }

  /**
   * Load VIV-specific parameters from DNV-RP-C205.
   *
   * @param calculator the calculator to populate
   */
  public void loadVIVParameters(RiserMechanicalDesignCalculator calculator) {
    Map<String, Double> vivParams = loadFromStandard("DNV-RP-C205");

    if (vivParams.containsKey("StrouhalNumber")) {
      calculator.setStrouhalNumber(vivParams.get("StrouhalNumber"));
    }
    if (vivParams.containsKey("DragCoefficient")) {
      calculator.setDragCoefficient(vivParams.get("DragCoefficient"));
    }
    if (vivParams.containsKey("AddedMassCoefficient")) {
      calculator.setAddedMassCoefficient(vivParams.get("AddedMassCoefficient"));
    }
    if (vivParams.containsKey("LiftCoefficient")) {
      calculator.setLiftCoefficient(vivParams.get("LiftCoefficient"));
    }
  }
}
