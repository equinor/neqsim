package neqsim.processSimulation.mechanicalDesign.designStandards;

import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * <p>
 * PipelineDesignStandard class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PipelineDesignStandard extends DesignStandard {
  private static final long serialVersionUID = 1000;

  double safetyFactor = 1.0;

  /**
   * <p>
   * Constructor for PipelineDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
   */
  public PipelineDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    // double wallT = 0;
    // double maxAllowableStress = equipment.getMaterialDesignStandard().getDivisionClass();
    // double jointEfficiency =
    // equipment.getJointEfficiencyStandard().getJEFactor();

    neqsim.util.database.NeqSimTechnicalDesignDatabase database =
        new neqsim.util.database.NeqSimTechnicalDesignDatabase();
    java.sql.ResultSet dataSet = null;
    try {
      try {
        dataSet = database.getResultSet(
            ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Pipeline' AND Company='"
                + standardName + "'"));
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          if (specName.equals("safetyFactor")) {
            safetyFactor = Double.parseDouble(dataSet.getString("MAXVALUE"));
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (dataSet != null) {
          dataSet.close();
        }
      } catch (Exception e) {
        System.out.println("error closing database.....GasScrubberDesignStandard");
        e.printStackTrace();
      }
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
