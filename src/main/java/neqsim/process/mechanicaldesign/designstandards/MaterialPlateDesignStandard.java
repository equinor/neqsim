package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * MaterialPlateDesignStandard class.
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
   * Constructor for MaterialPlateDesignStandard.
   */
  public MaterialPlateDesignStandard() {
  }

  /**
   * Constructor for MaterialPlateDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public MaterialPlateDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    readMaterialDesignStandard("Carbon Steel Plates and Sheets", "SA-516", "55", 1);
  }

  /**
   * Getter for the field <code>divisionClass</code>.
   *
   * @return the divisionClass
   */
  public double getDivisionClass() {
    if (!Double.isFinite(divisionClass) || divisionClass <= 0.0) {
      throw new IllegalStateException("No plate allowable loaded for " + specificationNumber + " grade " + grade);
    }
    return divisionClass;
  }

  /**
   * Setter for the field <code>divisionClass</code>.
   *
   * @param divisionClass the divisionClass to set
   */
  public void setDivisionClass(double divisionClass) {
    if (!Double.isFinite(divisionClass) || divisionClass <= 0.0) {
      throw new IllegalArgumentException("Plate allowable stress must be finite and positive in MPa");
    }
    this.divisionClass = divisionClass;
  }

  String grade = "";

  String materialName = "";
  String specificationNumber = "";
  int divisionClassNumber = 1;
  private double divisionClass = Double.NaN;

  /**
   * readMaterialDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param specNo a {@link java.lang.String} object
   * @param grade a {@link java.lang.String} object
   * @param divClassNo a int
   * @throws IllegalArgumentException if the division or requested material is not available
   * @throws IllegalStateException if the material data cannot be loaded
   */
  public void readMaterialDesignStandard(String name, String specNo, String grade, int divClassNo) {
    divisionClass = Double.NaN;
    materialName = name;
    specificationNumber = specNo;
    this.grade = grade;
    divisionClassNumber = divClassNo;
    if (name == null || name.trim().isEmpty() || specNo == null || specNo.trim().isEmpty() || grade == null
        || grade.trim().isEmpty()) {
      throw new IllegalArgumentException("Plate material, specification and grade must be specified");
    }
    if (divClassNo != 1 && divClassNo != 2) {
      throw new IllegalArgumentException("Plate division must be 1 or 2");
    }

    try (
        neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase();
        java.sql.ResultSet dataSet = database.getResultSet(
            ("SELECT * FROM materialplateproperties WHERE materialName='" + name.replace("'", "''") + "' AND grade='"
                + grade.replace("'", "''") + "' AND specificationNumber='" + specNo.replace("'", "''") + "'"))) {
      if (!dataSet.next()) {
        throw new IllegalArgumentException("Unknown plate material: " + name + ", " + specNo + " grade " + grade);
      }
      double allowable = dataSet.getDouble(divClassNo == 1 ? "divisionClass1" : "divisionClass2") * 0.00689475729;
      if (dataSet.wasNull() || !Double.isFinite(allowable) || allowable <= 0.0 || dataSet.next()) {
        throw new IllegalStateException("Invalid or ambiguous plate allowable for " + specNo + " grade " + grade);
      }
      divisionClass = allowable;
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot load plate allowable for " + specNo + " grade " + grade, ex);
    }
  }
}
