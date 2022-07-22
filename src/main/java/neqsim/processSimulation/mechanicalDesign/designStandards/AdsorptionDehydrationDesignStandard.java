package neqsim.processSimulation.mechanicalDesign.designStandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * <p>
 * AdsorptionDehydrationDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AdsorptionDehydrationDesignStandard extends DesignStandard {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(AdsorptionDehydrationDesignStandard.class);

  private double molecularSieveWaterCapacity = 20;

  /**
   * <p>
   * Constructor for AdsorptionDehydrationDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
   */
  public AdsorptionDehydrationDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    neqsim.util.database.NeqSimTechnicalDesignDatabase database =
        new neqsim.util.database.NeqSimTechnicalDesignDatabase();
    java.sql.ResultSet dataSet = null;
    try {
      try {
        dataSet = database.getResultSet(
            ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Adsorber Dehydration' AND Company='"
                + standardName + "'"));
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          if (specName.equals("MolecularSieve3AWaterCapacity")) {
            molecularSieveWaterCapacity = Double.parseDouble(dataSet.getString("MAXVALUE"));
          }
        }
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
    } finally {
      try {
        if (dataSet != null) {
          dataSet.close();
        }
      } catch (Exception e) {
        System.out.println("error closing database.....GasScrubberDesignStandard");
        logger.error(e.getMessage());
      }
    }
  }

  /**
   * <p>
   * Getter for the field <code>molecularSieveWaterCapacity</code>.
   * </p>
   *
   * @return the molecularSieveWaterCapacity
   */
  public double getMolecularSieveWaterCapacity() {
    return molecularSieveWaterCapacity;
  }

  /**
   * <p>
   * Setter for the field <code>molecularSieveWaterCapacity</code>.
   * </p>
   *
   * @param molecularSieveWaterCapacity the molecularSieveWaterCapacity to set
   */
  public void setMolecularSieveWaterCapacity(double molecularSieveWaterCapacity) {
    this.molecularSieveWaterCapacity = molecularSieveWaterCapacity;
  }
}
