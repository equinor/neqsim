package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

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

    // double wallT = 0;
    // double maxAllowableStress = equipment.getMaterialDesignStandard().getDivisionClass();
    // double jointEfficiency =
    // equipment.getJointEfficiencyStandard().getJEFactor();

    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      try (java.sql.ResultSet dataSet = database.getResultSet(
          ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Pipeline' AND Company='"
              + standardName + "'"))) {
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
      logger.error(e.getMessage());;
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
}
