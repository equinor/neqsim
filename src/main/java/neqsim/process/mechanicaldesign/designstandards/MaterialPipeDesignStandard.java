package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * MaterialPipeDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MaterialPipeDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MaterialPipeDesignStandard.class);

  /**
   * <p>
   * Constructor for MaterialPipeDesignStandard.
   * </p>
   */
  public MaterialPipeDesignStandard() {}

  /**
   * <p>
   * Constructor for MaterialPipeDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public MaterialPipeDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    readMaterialDesignStandard("Carbon Steel Pipe", "A25");
  }

  /**
   * <p>
   * Getter for the field <code>designFactor</code>.
   * </p>
   *
   * @return the designFactor
   */
  public double getDesignFactor() {
    return designFactor;
  }

  /**
   * <p>
   * Setter for the field <code>designFactor</code>.
   * </p>
   *
   * @param designFactor the designFactor to set
   */
  public void setDesignFactor(double designFactor) {
    this.designFactor = designFactor;
  }

  /**
   * <p>
   * getEfactor.
   * </p>
   *
   * @return the Efactor
   */
  public double getEfactor() {
    return Efactor;
  }

  /**
   * <p>
   * setEfactor.
   * </p>
   *
   * @param Efactor the Efactor to set
   */
  public void setEfactor(double Efactor) {
    this.Efactor = Efactor;
  }

  /**
   * <p>
   * Getter for the field <code>temperatureDeratingFactor</code>.
   * </p>
   *
   * @return the temperatureDeratingFactor
   */
  public double getTemperatureDeratingFactor() {
    return temperatureDeratingFactor;
  }

  /**
   * <p>
   * Setter for the field <code>temperatureDeratingFactor</code>.
   * </p>
   *
   * @param temperatureDeratingFactor the temperatureDeratingFactor to set
   */
  public void setTemperatureDeratingFactor(double temperatureDeratingFactor) {
    this.temperatureDeratingFactor = temperatureDeratingFactor;
  }

  /**
   * <p>
   * Getter for the field <code>minimumYeildStrength</code>.
   * </p>
   *
   * @return the minimumYeildStrength
   */
  public double getMinimumYeildStrength() {
    return minimumYeildStrength;
  }

  /**
   * <p>
   * Setter for the field <code>minimumYeildStrength</code>.
   * </p>
   *
   * @param minimumYeildStrength the minimumYeildStrength to set
   */
  public void setMinimumYeildStrength(double minimumYeildStrength) {
    this.minimumYeildStrength = minimumYeildStrength;
  }

  String grade = "";
  String specName = "";
  String specificationNumber = "";
  private double minimumYeildStrength = 35000 * 0.00689475729;
  private double designFactor = 0.8;
  private double Efactor = 1.0;
  private double temperatureDeratingFactor = 1.0;

  /**
   * <p>
   * readMaterialDesignStandard.
   * </p>
   *
   * @param specNo a {@link java.lang.String} object
   * @param grade a {@link java.lang.String} object
   */
  public void readMaterialDesignStandard(String specNo, String grade) {
    this.grade = grade;
    specificationNumber = specNo;

    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      try (java.sql.ResultSet dataSet =
          database.getResultSet(("SELECT * FROM materialpipeproperties WHERE specificationNumber='"
              + specificationNumber + "' AND grade='" + grade + "'"))) {
        while (dataSet.next()) {
          minimumYeildStrength =
              (Double.parseDouble(dataSet.getString("minimumYeildStrength"))) * 0.00689475729;
          // design factor table has to be developed
          // Efactor table has to be implemented
          // temperatureDeratingFactor has to be implemented
        }
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      // gasLoadFactor = Double.parseDouble(dataSet.getString("gasloadfactor"));
    } catch (Exception e) {
      // TODO Auto-generated catch block
      logger.error(e.getMessage());
    }
  }
}
