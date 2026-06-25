package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * AdsorptionDehydrationDesignStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class AdsorptionDehydrationDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AdsorptionDehydrationDesignStandard.class);

  private double molecularSieveWaterCapacity = 20;

  /**
   * Constructor for AdsorptionDehydrationDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public AdsorptionDehydrationDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    try (
        neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase();
        java.sql.ResultSet dataSet = database.getResultSet(
            ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Adsorber Dehydration' AND Company='"
                + standardName + "'"))) {
      while (dataSet.next()) {
        String specName = dataSet.getString("SPECIFICATION");
        if (specName.equals("MolecularSieve3AWaterCapacity")) {
          molecularSieveWaterCapacity = Double.parseDouble(dataSet.getString("MAXVALUE"));
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * Getter for the field <code>molecularSieveWaterCapacity</code>.
   *
   * @return the molecularSieveWaterCapacity
   */
  public double getMolecularSieveWaterCapacity() {
    return molecularSieveWaterCapacity;
  }

  /**
   * Setter for the field <code>molecularSieveWaterCapacity</code>.
   *
   * @param molecularSieveWaterCapacity the molecularSieveWaterCapacity to set
   */
  public void setMolecularSieveWaterCapacity(double molecularSieveWaterCapacity) {
    this.molecularSieveWaterCapacity = molecularSieveWaterCapacity;
  }
}
