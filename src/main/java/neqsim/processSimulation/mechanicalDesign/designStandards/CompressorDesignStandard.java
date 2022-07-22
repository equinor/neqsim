package neqsim.processSimulation.mechanicalDesign.designStandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.processSimulation.mechanicalDesign.MechanicalDesign;

/**
 * <p>
 * CompressorDesignStandard class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class CompressorDesignStandard extends DesignStandard {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(CompressorDesignStandard.class);

  private double compressorFactor = 0.11;

  /**
   * <p>
   * Constructor for CompressorDesignStandard.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.processSimulation.mechanicalDesign.MechanicalDesign} object
   */
  public CompressorDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    neqsim.util.database.NeqSimTechnicalDesignDatabase database =
        new neqsim.util.database.NeqSimTechnicalDesignDatabase();
    java.sql.ResultSet dataSet = null;
    try {
      try {
        dataSet = database.getResultSet(
            ("SELECT * FROM technicalrequirements_process WHERE EQUIPMENTTYPE='Compressor' AND Company='"
                + standardName + "'"));
        while (dataSet.next()) {
          String specName = dataSet.getString("SPECIFICATION");
          if (specName.equals("compressorFactor")) {
            compressorFactor = Double.parseDouble(dataSet.getString("MAXVALUE"));
          }
        }
      } catch (Exception e) {
        logger.error(e.getMessage());
      }

      // gasLoadFactor = Double.parseDouble(dataSet.getString("gasloadfactor"));
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
