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
    readMaterialDesignStandard("API5L", "A25");
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
    this.designFactorExplicitlySet = true;
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
    this.efactorExplicitlySet = true;
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
    if (!(minimumYeildStrength > 0.0)) {
      throw new IllegalStateException("No pipe material strength loaded for " + specificationNumber + " / " + grade);
    }
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

  /**
   * Indicates whether the material-standard design factor was explicitly overridden.
   *
   * @return true for an explicit setter value
   */
  public boolean isDesignFactorExplicitlySet() {
    return designFactorExplicitlySet;
  }

  /**
   * Indicates whether the material-standard joint factor was explicitly overridden.
   *
   * @return true for an explicit setter value
   */
  public boolean isEfactorExplicitlySet() {
    return efactorExplicitlySet;
  }

  String grade = "";
  String specName = "";
  String specificationNumber = "";
  private double minimumYeildStrength = Double.NaN;
  private double designFactor = 0.8;
  private boolean designFactorExplicitlySet;
  private double Efactor = 1.0;
  private boolean efactorExplicitlySet;
  private double temperatureDeratingFactor = 1.0;

  /**
   * Loads a pipe yield strength by specification number and grade; misses invalidate the prior value.
   *
   * @param specNo a {@link java.lang.String} object
   * @param grade a {@link java.lang.String} object
   * @throws IllegalArgumentException if the selection is empty or has no material row
   * @throws IllegalStateException if the database cannot supply a valid strength
   */
  public void readMaterialDesignStandard(String specNo, String grade) {
    minimumYeildStrength = Double.NaN;
    if (specNo == null || specNo.trim().isEmpty() || grade == null || grade.trim().isEmpty()) {
      throw new IllegalArgumentException("Pipe specification and grade must not be empty");
    }
    this.grade = neqsim.process.mechanicaldesign.pipeline.Api5LGrade.normalize(grade);
    specificationNumber = specNo.trim();
    String query = "SELECT * FROM materialpipeproperties WHERE UPPER(specificationNumber)=UPPER('"
        + specificationNumber.replace("'", "''") + "') AND UPPER(grade)='" + this.grade.replace("'", "''") + "'";
    try (
        neqsim.util.database.NeqSimProcessDesignDataBase database = new neqsim.util.database.NeqSimProcessDesignDataBase();
        java.sql.ResultSet dataSet = database.getResultSet(query)) {
      if (!dataSet.next()) {
        throw new IllegalArgumentException("No pipe material data for " + specificationNumber + " / " + this.grade);
      }
      double strength = dataSet.getDouble("minimumYeildStrength") * 0.00689475729;
      if (!(strength > 0.0) || !Double.isFinite(strength)) {
        throw new IllegalStateException("Invalid pipe yield strength for " + specificationNumber + " / " + this.grade);
      }
      minimumYeildStrength = strength;
    } catch (java.sql.SQLException ex) {
      throw new IllegalStateException("Unable to load pipe material " + specificationNumber + " / " + this.grade, ex);
    }
  }
}
