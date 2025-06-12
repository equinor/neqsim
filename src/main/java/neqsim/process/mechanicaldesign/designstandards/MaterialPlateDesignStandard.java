package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * MaterialPlateDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MaterialPlateDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MaterialPlateDesignStandard.class);

  /**
   * <p>
   * Constructor for MaterialPlateDesignStandard.
   * </p>
   */
  public MaterialPlateDesignStandard() {}

  /**
   * <p>
   * Constructor for MaterialPlateDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public MaterialPlateDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "55", 1);
  }

  /**
   * <p>
   * Getter for the field <code>divisionClass</code>.
   * </p>
   *
   * @return the divisionClass
   */
  public double getDivisionClass() {
    return divisionClass;
  }

  /**
   * <p>
   * Setter for the field <code>divisionClass</code>.
   * </p>
   *
   * @param divisionClass the divisionClass to set
   */
  public void setDivisionClass(double divisionClass) {
    this.divisionClass = divisionClass;
  }

  String grade = "";

  String materialName = "";
  String specificationNumber = "";
  int divisionClassNumber = 1;
  private double divisionClass = 425;

  /**
   * <p>
   * readMaterialDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param specNo a {@link java.lang.String} object
   * @param grade a {@link java.lang.String} object
   * @param divClassNo a int
   */
  public void readMaterialDesignStandard(String name, String specNo, String grade, int divClassNo) {
    materialName = name;
    specificationNumber = specNo;
    divisionClassNumber = divClassNo;

    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      java.sql.ResultSet dataSet = null;
      try {
        dataSet =
            database.getResultSet(("SELECT * FROM materialplateproperties WHERE materialName='"
                + name + "' AND grade='" + grade + "' AND specificationNumber='" + specNo + "'"));
        while (dataSet.next()) {
          if (divClassNo == 1) {
            divisionClass =
                (Double.parseDouble(dataSet.getString("divisionClass1"))) * 0.00689475729; // MPa
          } else {
            divisionClass =
                (Double.parseDouble(dataSet.getString("divisionClass2"))) * 0.00689475729; // MPa
          }
        }

        // gasLoadFactor = Double.parseDouble(dataSet.getString("gasloadfactor"));
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      } finally {
        try {
          if (dataSet != null) {
            dataSet.close();
          }
        } catch (Exception ex) {
          System.out.println("error closing database.....GasScrubberDesignStandard");
          logger.error(ex.getMessage(), ex);
        }
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      logger.error(e.getMessage());
    }
  }
}
