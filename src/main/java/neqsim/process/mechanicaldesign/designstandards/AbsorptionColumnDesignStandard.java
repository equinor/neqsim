package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * AbsorptionColumnDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class AbsorptionColumnDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AbsorptionColumnDesignStandard.class);

  private double molecularSieveWaterCapacity = 20;

  /**
   * <p>
   * Constructor for AbsorptionColumnDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public AbsorptionColumnDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    java.sql.ResultSet dataSet = null;
    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      dataSet = database.getResultSet(
          ("SELECT * FROM technicalrequirements WHERE EQUIPMENTTYPE='Absorber' AND Company='"
              + standardName + "'"));
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
