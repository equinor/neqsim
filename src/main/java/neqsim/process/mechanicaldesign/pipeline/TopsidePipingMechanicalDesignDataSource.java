package neqsim.process.mechanicaldesign.pipeline;

import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.util.database.NeqSimProcessDesignDataBase;

/**
 * Data source for topside piping mechanical design parameters.
 *
 * <p>
 * This class provides database access for loading design parameters from:
 * </p>
 * <ul>
 * <li>TechnicalRequirements_Process - Company-specific design values</li>
 * <li>asme_standards - ASME B31.3 standard parameters</li>
 * <li>norsok_standards - NORSOK L-002 piping requirements</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class TopsidePipingMechanicalDesignDataSource {
  /** Logger for this class. */
  private static final Logger logger =
      LogManager.getLogger(TopsidePipingMechanicalDesignDataSource.class);

  /**
   * Default constructor.
   */
  public TopsidePipingMechanicalDesignDataSource() {}

  /**
   * Load design parameters into calculator from database.
   *
   * @param calc the calculator to populate
   * @param company company name for company-specific standards
   * @param designCode design code (e.g., "ASME-B31.3")
   * @param serviceType service type (e.g., "PROCESS_GAS")
   */
  public void loadIntoCalculator(TopsidePipingMechanicalDesignCalculator calc, String company,
      String designCode, String serviceType) {
    // Load from company-specific requirements
    loadDesignParameters(calc, company, serviceType);

    // Load from standards tables
    loadFromStandard(calc, designCode, "TopsidePiping");
    loadFromStandard(calc, designCode, serviceType);
  }

  /**
   * Load design parameters from TechnicalRequirements_Process table.
   *
   * @param calc the calculator to populate
   * @param company company name
   * @param serviceType service type
   */
  public void loadDesignParameters(TopsidePipingMechanicalDesignCalculator calc, String company,
      String serviceType) {
    String sql = "SELECT ParameterName, MinValue, MaxValue, Unit, Standard "
        + "FROM TechnicalRequirements_Process " + "WHERE EquipmentType = 'TopsidePiping'";

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(sql)) {
      while (rs.next()) {
        String param = rs.getString("ParameterName");
        double minVal = rs.getDouble("MinValue");
        double maxVal = rs.getDouble("MaxValue");
        String standard = rs.getString("Standard");

        applyParameter(calc, param, minVal, maxVal, standard);
      }
    } catch (Exception e) {
      logger.warn("Could not load topside piping parameters from database: " + e.getMessage());
    }
  }

  /**
   * Load parameters from standards tables.
   *
   * @param calc the calculator to populate
   * @param standardCode standard code (e.g., "ASME-B31.3")
   * @param equipmentType equipment type
   */
  public void loadFromStandard(TopsidePipingMechanicalDesignCalculator calc, String standardCode,
      String equipmentType) {
    String sql = "SELECT SPECIFICATION, MINVALUE, MAXVALUE, UNIT, DESCRIPTION "
        + "FROM asme_standards WHERE STANDARD_CODE = '" + standardCode + "' AND EQUIPMENTTYPE = '"
        + equipmentType + "'";

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(sql)) {
      while (rs.next()) {
        String param = rs.getString("SPECIFICATION");
        double minVal = rs.getDouble("MINVALUE");
        double maxVal = rs.getDouble("MAXVALUE");

        applyParameter(calc, param, minVal, maxVal, standardCode);
      }
    } catch (Exception e) {
      logger.debug("Could not load from asme_standards: " + e.getMessage());
    }
  }

  /**
   * Load velocity limits from database.
   *
   * @param calc the calculator to populate
   * @param company company name
   * @param serviceType service type
   */
  public void loadVelocityLimits(TopsidePipingMechanicalDesignCalculator calc, String company,
      String serviceType) {
    String sql = "SELECT ParameterName, MinValue, MaxValue, Standard "
        + "FROM TechnicalRequirements_Process "
        + "WHERE EquipmentType = 'TopsidePiping' AND ParameterName LIKE '%Velocity%'";

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(sql)) {
      while (rs.next()) {
        String param = rs.getString("ParameterName");
        double maxVal = rs.getDouble("MaxValue");

        if ("maxGasVelocity".equalsIgnoreCase(param)) {
          calc.setMaxGasVelocity(maxVal);
        } else if ("maxLiquidVelocity".equalsIgnoreCase(param)) {
          calc.setMaxLiquidVelocity(maxVal);
        } else if ("erosionalCFactor".equalsIgnoreCase(param)) {
          calc.setErosionalCFactor(maxVal);
        }
      }
    } catch (Exception e) {
      logger.debug("Could not load velocity limits: " + e.getMessage());
    }
  }

  /**
   * Load vibration parameters from database.
   *
   * @param calc the calculator to populate
   * @param company company name
   */
  public void loadVibrationParameters(TopsidePipingMechanicalDesignCalculator calc,
      String company) {
    // Load from Energy Institute guidelines or company-specific
    String sql = "SELECT ParameterName, MinValue, MaxValue, Standard "
        + "FROM TechnicalRequirements_Process WHERE EquipmentType = 'TopsidePiping'";

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(sql)) {
      while (rs.next()) {
        // Apply vibration-specific parameters if found
        // These would be added to the calculator
      }
    } catch (Exception e) {
      logger.debug("Could not load vibration parameters: " + e.getMessage());
    }
  }

  /**
   * Apply a parameter value to the calculator.
   *
   * @param calc the calculator
   * @param param parameter name
   * @param minVal minimum value
   * @param maxVal maximum value
   * @param standard standard reference
   */
  private void applyParameter(TopsidePipingMechanicalDesignCalculator calc, String param,
      double minVal, double maxVal, String standard) {
    if (param == null) {
      return;
    }
    switch (param.toLowerCase()) {
      case "corrosionallowance":
        calc.setCorrosionAllowance(maxVal / 1000.0); // mm to m
        calc.getAppliedStandards().add(standard + " - Corrosion Allowance");
        break;
      case "jointefficiency":
        calc.setWeldJointEfficiency(maxVal);
        calc.getAppliedStandards().add(standard + " - Joint Efficiency");
        break;
      case "designfactor":
        calc.setDesignFactor(maxVal);
        calc.getAppliedStandards().add(standard + " - Design Factor");
        break;
      case "maxgasvelocity":
        calc.setMaxGasVelocity(maxVal);
        calc.getAppliedStandards().add(standard + " - Max Gas Velocity");
        break;
      case "maxliquidvelocity":
        calc.setMaxLiquidVelocity(maxVal);
        calc.getAppliedStandards().add(standard + " - Max Liquid Velocity");
        break;
      case "erosionalcfactor":
        calc.setErosionalCFactor(maxVal);
        calc.getAppliedStandards().add(standard + " - Erosional C-Factor");
        break;
      case "fabricationtolerance":
        calc.setFabricationTolerance(1.0 - maxVal);
        calc.getAppliedStandards().add(standard + " - Fabrication Tolerance");
        break;
      default:
        // Unknown parameter - ignore
        break;
    }
  }

  /**
   * Load pipe schedule wall thickness from database.
   *
   * @param nominalSize nominal pipe size (e.g., "8")
   * @param schedule schedule (e.g., "40")
   * @return wall thickness in meters
   */
  public double loadPipeScheduleThickness(String nominalSize, String schedule) {
    String sql = "SELECT WallThickness FROM PipeScheduleData WHERE NominalSize = '" + nominalSize
        + "' AND Schedule = '" + schedule + "'";

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(sql)) {
      if (rs.next()) {
        return rs.getDouble("WallThickness") / 1000.0; // mm to m
      }
    } catch (Exception e) {
      logger.debug("Could not load pipe schedule data: " + e.getMessage());
    }

    return 0.0; // Not found
  }

  /**
   * Load material allowable stress from database.
   *
   * @param materialGrade material grade (e.g., "A106-B")
   * @param temperature temperature in Celsius
   * @return allowable stress in MPa
   */
  public double loadAllowableStress(String materialGrade, double temperature) {
    String sql = "SELECT AllowableStress FROM MaterialAllowableStress WHERE MaterialGrade = '"
        + materialGrade + "' AND Temperature <= " + temperature
        + " ORDER BY Temperature DESC LIMIT 1";

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(sql)) {
      if (rs.next()) {
        return rs.getDouble("AllowableStress");
      }
    } catch (Exception e) {
      logger.debug("Could not load allowable stress: " + e.getMessage());
    }

    return 0.0; // Not found
  }

  /**
   * Load flange rating from database.
   *
   * @param flangeClass flange class (e.g., 300)
   * @param temperature temperature in Celsius
   * @return pressure rating in MPa
   */
  public double loadFlangeRating(int flangeClass, double temperature) {
    String sql = "SELECT PressureRating FROM FlangeRatings WHERE FlangeClass = " + flangeClass
        + " AND Temperature >= " + temperature + " ORDER BY Temperature ASC LIMIT 1";

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase();
        ResultSet rs = database.getResultSet(sql)) {
      if (rs.next()) {
        return rs.getDouble("PressureRating");
      }
    } catch (Exception e) {
      logger.debug("Could not load flange rating: " + e.getMessage());
    }

    return 0.0; // Not found
  }
}
