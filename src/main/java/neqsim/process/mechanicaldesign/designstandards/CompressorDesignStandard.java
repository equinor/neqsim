package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * <p>
 * CompressorDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class CompressorDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorDesignStandard.class);

  private double compressorFactor = 0.11;

  /**
   * <p>
   * Constructor for CompressorDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public CompressorDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    try (neqsim.util.database.NeqSimProcessDesignDataBase database =
        new neqsim.util.database.NeqSimProcessDesignDataBase()) {
      try (java.sql.ResultSet dataSet = database.getResultSet(
          ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Compressor' AND Company='"
              + standardName + "'"))) {
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          if (specName.equals("compressorFactor")) {
            compressorFactor = Double.parseDouble(dataSet.getString("MAXVALUE"));
          }
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

  /**
   * <p>
   * Getter for the field <code>compressorFactor</code>.
   * </p>
   *
   * @return the compressorFactor
   */
  public double getCompressorFactor() {
    return compressorFactor;
  }

  /**
   * <p>
   * Setter for the field <code>compressorFactor</code>.
   * </p>
   *
   * @param compressorFactor the compressorFactor to set
   */
  public void setCompressorFactor(double compressorFactor) {
    this.compressorFactor = compressorFactor;
  }
}
