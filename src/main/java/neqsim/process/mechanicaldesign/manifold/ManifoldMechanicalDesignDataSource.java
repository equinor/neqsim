package neqsim.process.mechanicaldesign.manifold;

import java.io.Serializable;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.util.database.NeqSimProcessDesignDataBase;

/**
 * Data source for manifold mechanical design parameters from database.
 *
 * <p>
 * This class retrieves design parameters from the NeqSim process design database tables:
 * </p>
 * <ul>
 * <li>TechnicalRequirements_Process - Company-specific requirements</li>
 * <li>asme_standards - ASME B31.3 parameters for topside/onshore</li>
 * <li>dnv_iso_en_standards - DNV-ST-F101 parameters for subsea</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class ManifoldMechanicalDesignDataSource implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger for this class. */
  private static final Logger logger =
      LogManager.getLogger(ManifoldMechanicalDesignDataSource.class);

  /**
   * Default constructor.
   */
  public ManifoldMechanicalDesignDataSource() {}

  /**
   * Load design parameters from database into calculator.
   *
   * @param calc the calculator to load parameters into
   * @param company the company for company-specific standards
   * @param designCode the design code (ASME-B31.3, DNV-ST-F101, etc.)
   * @param equipmentType the equipment type (Manifold)
   */
  public void loadIntoCalculator(ManifoldMechanicalDesignCalculator calc, String company,
      String designCode, String equipmentType) {
    loadCompanyRequirements(calc, company, equipmentType);
    loadStandardsParameters(calc, designCode, equipmentType);
  }

  /**
   * Load company-specific requirements from TechnicalRequirements_Process.
   *
   * @param calc the calculator to load parameters into
   * @param company the company name
   * @param equipmentType the equipment type
   */
  public void loadCompanyRequirements(ManifoldMechanicalDesignCalculator calc, String company,
      String equipmentType) {
    if (company == null || company.isEmpty()) {
      return;
    }

    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      String sql =
          "SELECT SPECIFICATION, MINVALUE, MAXVALUE, UNIT " + "FROM TechnicalRequirements_Process "
              + "WHERE COMPANY = '" + company + "' AND EQUIPMENTTYPE = '" + equipmentType + "'";

      ResultSet rs = database.getResultSet(sql);
      while (rs.next()) {
        String specName = rs.getString("SPECIFICATION");
        double maxValue = rs.getDouble("MAXVALUE");

        applyCompanyParameter(calc, specName, maxValue, null);
      }
      rs.close();
    } catch (Exception ex) {
      logger.warn("Could not load company requirements for {}: {}", company, ex.getMessage());
    }
  }

  /**
   * Load standards-specific parameters.
   *
   * @param calc the calculator to load parameters into
   * @param designCode the design code
   * @param equipmentType the equipment type
   */
  public void loadStandardsParameters(ManifoldMechanicalDesignCalculator calc, String designCode,
      String equipmentType) {
    if (designCode == null || designCode.isEmpty()) {
      return;
    }

    if (designCode.contains("DNV") || designCode.contains("F101")) {
      loadDNVParameters(calc, equipmentType);
    } else if (designCode.contains("ASME") || designCode.contains("B31")) {
      loadASMEParameters(calc, equipmentType);
    }
  }

  /**
   * Load ASME B31.3 parameters from asme_standards table.
   *
   * @param calc the calculator
   * @param equipmentType the equipment type
   */
  public void loadASMEParameters(ManifoldMechanicalDesignCalculator calc, String equipmentType) {
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      String sql =
          "SELECT SPECIFICATION, MINVALUE, MAXVALUE, UNIT, DESCRIPTION " + "FROM asme_standards "
              + "WHERE (EQUIPMENTTYPE = 'Manifold' OR EQUIPMENTTYPE = 'TopsidePiping')";

      ResultSet rs = database.getResultSet(sql);
      while (rs.next()) {
        String spec = rs.getString("SPECIFICATION");
        double minVal = rs.getDouble("MINVALUE");
        double maxVal = rs.getDouble("MAXVALUE");

        applyASMEParameter(calc, spec, minVal, maxVal);
      }
      rs.close();
    } catch (Exception ex) {
      logger.warn("Could not load ASME parameters: {}", ex.getMessage());
    }
  }

  /**
   * Load DNV-ST-F101 parameters from dnv_iso_en_standards table.
   *
   * @param calc the calculator
   * @param equipmentType the equipment type
   */
  public void loadDNVParameters(ManifoldMechanicalDesignCalculator calc, String equipmentType) {
    try (NeqSimProcessDesignDataBase database = new NeqSimProcessDesignDataBase()) {
      String sql = "SELECT SPECIFICATION, MINVALUE, MAXVALUE, UNIT, DESCRIPTION "
          + "FROM dnv_iso_en_standards " + "WHERE STANDARD_CODE = 'DNV-ST-F101' "
          + "AND (EQUIPMENTTYPE = 'Manifold' OR EQUIPMENTTYPE = 'Pipeline' "
          + "OR EQUIPMENTTYPE = 'SubseaEquipment')";

      ResultSet rs = database.getResultSet(sql);
      while (rs.next()) {
        String spec = rs.getString("SPECIFICATION");
        double minVal = rs.getDouble("MINVALUE");
        double maxVal = rs.getDouble("MAXVALUE");

        applyDNVParameter(calc, spec, minVal, maxVal);
      }
      rs.close();
    } catch (Exception ex) {
      logger.warn("Could not load DNV parameters: {}", ex.getMessage());
    }
  }

  /**
   * Apply company-specific parameter to calculator.
   *
   * @param calc the calculator
   * @param paramName parameter name
   * @param numValue numeric value
   * @param textValue text value
   */
  private void applyCompanyParameter(ManifoldMechanicalDesignCalculator calc, String paramName,
      double numValue, String textValue) {
    if (paramName == null) {
      return;
    }

    switch (paramName) {
      case "DesignFactor":
        calc.setDesignFactor(numValue);
        break;
      case "CorrosionAllowance":
        calc.setCorrosionAllowance(numValue / 1000.0); // mm to m
        break;
      case "JointEfficiency":
        calc.setJointEfficiency(numValue);
        break;
      case "ErosionalCFactor":
        calc.setErosionalCFactor(numValue);
        break;
      case "SafetyClassFactor":
        calc.setSafetyClassFactor(numValue);
        break;
      default:
        // Unknown parameter - ignore
        break;
    }
  }

  /**
   * Apply ASME B31.3 parameter to calculator.
   *
   * @param calc the calculator
   * @param spec specification name
   * @param minVal minimum value
   * @param maxVal maximum value
   */
  private void applyASMEParameter(ManifoldMechanicalDesignCalculator calc, String spec,
      double minVal, double maxVal) {
    if (spec == null) {
      return;
    }

    if (spec.contains("JointEfficiency")) {
      calc.setJointEfficiency(maxVal);
    } else if (spec.contains("CorrosionAllowance")) {
      calc.setCorrosionAllowance(maxVal / 1000.0);
    }
  }

  /**
   * Apply DNV-ST-F101 parameter to calculator.
   *
   * @param calc the calculator
   * @param spec specification name
   * @param minVal minimum value
   * @param maxVal maximum value
   */
  private void applyDNVParameter(ManifoldMechanicalDesignCalculator calc, String spec,
      double minVal, double maxVal) {
    if (spec == null) {
      return;
    }

    if (spec.contains("SafetyClassFactor")) {
      calc.setSafetyClassFactor(maxVal);
    } else if (spec.contains("CorrosionAllowance")) {
      calc.setCorrosionAllowance(maxVal / 1000.0);
    } else if (spec.contains("UsageFactor")) {
      calc.setDesignFactor(maxVal);
    }
  }
}
