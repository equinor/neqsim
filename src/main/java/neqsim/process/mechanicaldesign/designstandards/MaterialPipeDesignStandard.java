package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * MaterialPipeDesignStandard class.
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
   * Constructor for MaterialPipeDesignStandard.
   */
  public MaterialPipeDesignStandard() {
  }

  /**
   * Constructor for MaterialPipeDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public MaterialPipeDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);
    readMaterialDesignStandard("Carbon Steel Pipe", "A25");
  }

  /**
   * Getter for the field <code>designFactor</code>.
   *
   * @return the designFactor
   */
  public double getDesignFactor() {
    return designFactor;
  }

  /**
   * Setter for the field <code>designFactor</code>.
   *
   * @param designFactor the designFactor to set
   */
  public void setDesignFactor(double designFactor) {
    this.designFactor = designFactor;
  }

  /**
   * getEfactor.
   *
   * @return the Efactor
   */
  public double getEfactor() {
    return Efactor;
  }

  /**
   * setEfactor.
   *
   * @param Efactor the Efactor to set
   */
  public void setEfactor(double Efactor) {
    this.Efactor = Efactor;
  }

  /**
   * Getter for the field <code>temperatureDeratingFactor</code>.
   *
   * @return the temperatureDeratingFactor
   */
  public double getTemperatureDeratingFactor() {
    return temperatureDeratingFactor;
  }

  /**
   * Setter for the field <code>temperatureDeratingFactor</code>.
   *
   * @param temperatureDeratingFactor the temperatureDeratingFactor to set
   */
  public void setTemperatureDeratingFactor(double temperatureDeratingFactor) {
    this.temperatureDeratingFactor = temperatureDeratingFactor;
  }

  /**
   * Getter for the field <code>minimumYeildStrength</code>.
   *
   * @return the minimumYeildStrength
   */
  public double getMinimumYeildStrength() {
    return minimumYeildStrength;
  }

  /**
   * Setter for the field <code>minimumYeildStrength</code>.
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
   * readMaterialDesignStandard.
   *
   * @param specNo a {@link java.lang.String} object
   * @param grade a {@link java.lang.String} object
   */
  public void readMaterialDesignStandard(String specNo, String grade) {
    this.grade = grade;
    specificationNumber = specNo;

    try (
	neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      try (java.sql.ResultSet dataSet = database
	  .getResultSet(("SELECT * FROM materialpipeproperties WHERE specificationNumber='" + specificationNumber
	      + "' AND grade='" + grade + "'"))) {
	while (dataSet.next()) {
	  minimumYeildStrength = (Double.parseDouble(dataSet.getString("minimumYeildStrength"))) * 0.00689475729;
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
