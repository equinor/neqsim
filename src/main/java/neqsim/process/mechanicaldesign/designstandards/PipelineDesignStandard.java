package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignMarginResult;

/**
 * <p>
 * PipelineDesignStandard class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PipelineDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PipelineDesignStandard.class);

  double safetyFactor = 1.0;
  private final MechanicalDesignMarginResult safetyMargins;

  /**
   * <p>
   * Constructor for PipelineDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public PipelineDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    safetyMargins = computeSafetyMargins();

    // double wallT = 0;
    // double maxAllowableStress = equipment.getMaterialDesignStandard().getDivisionClass();
    // double jointEfficiency =
    // equipment.getJointEfficiencyStandard().getJEFactor();

    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      try (java.sql.ResultSet dataSet = database.getResultSet(
          ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Pipeline' AND Company='"
              + resolveCompanyIdentifier() + "'"))) {
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          if (specName.equals("safetyFactor")) {
            safetyFactor = Double.parseDouble(dataSet.getString("MAXVALUE"));
          }
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      logger.error(e.getMessage());
    }
  }

  /**
   * <p>
   * calcPipelineWallThickness.
   * </p>
   *
   * @return a double
   */
  public double calcPipelineWallThickness() {
    if (standardName.equals("StatoilTR")) {
      return 0.11 * safetyFactor;
    } else {
      return 0.01;
    }
  }

  /**
   * Retrieve calculated safety margins for the pipeline.
   *
   * @return margin result.
   */
  public MechanicalDesignMarginResult getSafetyMargins() {
    return safetyMargins;
  }

  private String resolveCompanyIdentifier() {
    String identifier = equipment != null ? equipment.getCompanySpecificDesignStandards() : null;
    if (identifier == null || identifier.isEmpty()) {
      return standardName;
    }
    return identifier;
  }
}
